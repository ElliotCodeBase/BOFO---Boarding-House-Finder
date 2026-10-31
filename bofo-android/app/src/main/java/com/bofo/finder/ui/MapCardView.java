package com.bofo.finder.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.bofo.finder.R;
import com.bofo.finder.databinding.ViewMapCardBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.util.Geo;
import com.bofo.finder.util.ListRules;
import com.bofo.finder.util.MapGeo;
import com.bofo.finder.util.Money;
import com.google.android.material.slider.Slider;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A BOFO map: boarding-house pins, the radius ring, the campus dot, the live "you are here" dot, a peek card for the tapped pin,
 * a locate button and the map attribution. The MapView is created lazily by {@link #activate}, so a hidden map costs nothing.
 * The owning Fragment pushes data in (setPlaces / setRing / setCampusDot / setUser) and handles {@link Listener}.
 */
public class MapCardView extends FrameLayout implements DefaultLifecycleObserver {

    public interface Listener {
        void openPlace(String id);
        void locate();
        default void expand() { }
        /** Step 4: the radius pill moved (km, 0.5 steps). settled = the finger lifted. */
        default void radiusChanged(float km, boolean settled) { }
    }

    private static final String STYLE_ASSET = "bofo-map-style.json";
    private static final long AMBIENT_CACHE_BYTES = 50L * 1024 * 1024;       // MapLibre's own tile cache, capped
    private static String styleJson;
    private static boolean cacheCapped;

    private final ViewMapCardBinding b;
    private MapView mapView;
    private MapLibreMap map;
    private Style style;
    private LifecycleOwner owner;
    private Listener listener;

    private List<BoardingHouse> places = new ArrayList<>();
    private String selectedId;
    private Double ringLat, ringLng, campusLat, campusLng, userLat, userLng;
    private double ringKm;
    private boolean fitPending, pillShown;
    private long lastGlide;
    private double[][] routePts;        // Stage 6: the drawn route, {lat, lng} pairs
    private boolean routeApprox, fitRoutePending, tapsEnabled = true, blockParentScroll;
    private double[] target;           // {lat, lng, zoom}

    public MapCardView(@NonNull Context c) { this(c, null); }

    public MapCardView(@NonNull Context c, @Nullable AttributeSet a) {
        super(c, a);
        b = ViewMapCardBinding.inflate(LayoutInflater.from(c), this, true);      // 3 arguments: view_map_card.xml has a FrameLayout root, not <merge>
        b.locate.setOnClickListener(v -> { if (listener != null) listener.locate(); });
        b.expand.setOnClickListener(v -> { if (listener != null) listener.expand(); });
        b.radiusSlider.addOnChangeListener((sl, v, fromUser) -> {
            b.radiusValue.setText(getContext().getString(R.string.map_radius_value, v));
            if (fromUser && listener != null) listener.radiusChanged(v, false);
        });
        b.radiusSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider sl) { }
            @Override public void onStopTrackingTouch(@NonNull Slider sl) { if (listener != null) listener.radiusChanged(sl.getValue(), true); }
        });
        b.radiusValue.setText(getContext().getString(R.string.map_radius_value, b.radiusSlider.getValue()));
        b.peekOpen.setOnClickListener(v -> { if (listener != null && selectedId != null) listener.openPlace(selectedId); });
    }

    public void setListener(Listener l) { listener = l; }

    /** Step 4: the radius pill (top-centre). km is clamped to 0.5-5 and snapped to 0.5; setting it never fires the listener. */
    public void setRadiusPill(boolean show, double km) {
        pillShown = show;
        b.radiusPill.setVisibility(show ? View.VISIBLE : View.GONE);
        float v = (float) (Math.round(Math.max(0.5, Math.min(5, km)) * 2) / 2.0);
        if (b.radiusSlider.getValue() != v) b.radiusSlider.setValue(v);
        b.radiusValue.setText(getContext().getString(R.string.map_radius_value, v));
    }

    /** Step 4: while the pill is dragged, ease the camera so the whole (live) circle stays in view. */
    public void glideToRing() { glideToRing(false); }

    /** force = the finger lifted: always frame the final circle. */
    public void glideToRing(boolean force) {
        if (map == null || ringLat == null || ringKm <= 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (!force && now - lastGlide < 160) return;                 // at most ~6 camera moves a second while dragging
        lastGlide = now;
        double[] bb = MapGeo.ringBounds(ringLat, ringLng, ringKm);
        if (bb == null) return;
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(36 * d), top = Math.round((pillShown ? 80 : 36) * d);
        try {
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(bb[2], bb[3], bb[0], bb[1]), pad, top, pad, pad), 150);
        } catch (Exception e) {
            Log.w("BOFO", "Map glide", e);
        }
    }

    public void showExpand(boolean show) { b.expand.setVisibility(show ? View.VISIBLE : View.GONE); }

    /** Creates the MapView (first call only) and ties it to the lifecycle. Call with the Fragment's view lifecycle owner. */
    public void activate(@NonNull LifecycleOwner lifecycleOwner) {
        if (mapView != null) return;
        Context app = getContext().getApplicationContext();
        MapLibre.getInstance(app);
        capCache(app);
        owner = lifecycleOwner;
        mapView = new MapView(getContext());      // default fast surface; the rounded corners are drawn by CornerMaskView
        b.mapHost.addView(mapView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mapView.onCreate(null);
        owner.getLifecycle().addObserver(this);        // replays onStart / onResume if the screen is already showing
        mapView.getMapAsync(this::onMapReady);
    }

    private static void capCache(Context app) {
        if (cacheCapped) return;
        cacheCapped = true;
        try {
            OfflineManager.getInstance(app).setMaximumAmbientCacheSize(AMBIENT_CACHE_BYTES, new OfflineManager.FileSourceCallback() {
                @Override public void onSuccess() { }
                @Override public void onError(@NonNull String message) { Log.w("BOFO", "Map cache size: " + message); }
            });
        } catch (Throwable t) { Log.w("BOFO", "Map cache size not set", t); }
    }

    private void onMapReady(@NonNull MapLibreMap m) {
        map = m;
        m.getUiSettings().setRotateGesturesEnabled(false);
        m.getUiSettings().setTiltGesturesEnabled(false);
        m.getUiSettings().setLogoEnabled(false);          // the attribution text stays visible (licence)
        m.addOnMapClickListener(this::onTap);
        String json = loadStyle(getContext());
        if (json == null) return;
        m.setStyle(new Style.Builder().fromJson(json), st -> { style = st; addLayers(st); render(); post(this::applyCamera); });
    }

    private static String loadStyle(Context c) {
        if (styleJson != null) return styleJson;
        try (InputStream in = c.getAssets().open(STYLE_ASSET)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);      // readAllBytes() needs API 33
            styleJson = new String(out.toByteArray(), StandardCharsets.UTF_8);
            return styleJson;
        } catch (IOException e) {
            Log.e("BOFO", "Map style missing", e);
            return null;
        }
    }

    private void addLayers(Style st) {
        int primary = ContextCompat.getColor(getContext(), R.color.bofo_primary);
        int amber = ContextCompat.getColor(getContext(), R.color.bofo_amber);
        st.addImage("pin", pin(primary));
        st.addImage("pin-star", pin(amber));
        st.addSource(new GeoJsonSource("radius-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("campus-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("user-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("places-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("route-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("route-start-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("route-end-src", MapGeo.EMPTY));
        st.addLayer(new FillLayer("radius-fill", "radius-src").withProperties(
                PropertyFactory.fillColor(primary), PropertyFactory.fillOpacity(0.08f)));
        st.addLayer(new LineLayer("radius-line", "radius-src").withProperties(
                PropertyFactory.lineColor(primary), PropertyFactory.lineWidth(2f), PropertyFactory.lineDasharray(new Float[]{2f, 2f})));
        // Stage 6: route line (white casing + purple line; a straight-line estimate is dashed) and its two end dots, under the pins
        Expression exact = Expression.eq(Expression.get("approx"), Expression.literal(0));
        Expression guess = Expression.eq(Expression.get("approx"), Expression.literal(1));
        st.addLayer(new LineLayer("route-casing", "route-src").withProperties(
                PropertyFactory.lineColor(0xFFFFFFFF), PropertyFactory.lineWidth(9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)).withFilter(exact));
        st.addLayer(new LineLayer("route-line", "route-src").withProperties(
                PropertyFactory.lineColor(primary), PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)).withFilter(exact));
        st.addLayer(new LineLayer("route-dash", "route-src").withProperties(
                PropertyFactory.lineColor(primary), PropertyFactory.lineWidth(4f),
                PropertyFactory.lineDasharray(new Float[]{1.5f, 1.5f})).withFilter(guess));
        st.addLayer(new CircleLayer("route-start", "route-start-src").withProperties(
                PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(0xFF2F6FED),
                PropertyFactory.circleStrokeWidth(3f), PropertyFactory.circleStrokeColor(0xFFFFFFFF)));
        st.addLayer(new CircleLayer("route-end", "route-end-src").withProperties(
                PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(0xFFFFFFFF),
                PropertyFactory.circleStrokeWidth(3f), PropertyFactory.circleStrokeColor(primary)));
        st.addLayer(new CircleLayer("campus-dot", "campus-src").withProperties(
                PropertyFactory.circleRadius(8f), PropertyFactory.circleColor(amber),
                PropertyFactory.circleStrokeWidth(3f), PropertyFactory.circleStrokeColor(0xFFFFFFFF)));
        st.addLayer(new CircleLayer("user-halo", "user-src").withProperties(
                PropertyFactory.circleRadius(16f), PropertyFactory.circleColor(primary), PropertyFactory.circleOpacity(0.18f)));
        st.addLayer(new CircleLayer("user-dot", "user-src").withProperties(
                PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(primary),
                PropertyFactory.circleStrokeWidth(3f), PropertyFactory.circleStrokeColor(0xFFFFFFFF)));
        st.addLayer(new SymbolLayer("places", "places-src").withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(Expression.switchCase(
                        Expression.eq(Expression.get("sel"), Expression.literal(1)), Expression.literal(1.3f), Expression.literal(1f)))));
    }

    /** Teardrop pin with a white edge and a white dot, 30 x 40 dp. */
    private Bitmap pin(int color) {
        float d = getResources().getDisplayMetrics().density;
        int w = Math.round(30 * d), h = Math.round(40 * d);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float r = w / 2f - 2 * d, cx = w / 2f, cy = r + 2 * d;
        Path path = new Path();
        path.moveTo(cx, h - d);
        path.lineTo(cx - r * 0.78f, cy + r * 0.62f);
        path.lineTo(cx + r * 0.78f, cy + r * 0.62f);
        path.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, r, p);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawCircle(cx, cy, r, p);
        c.drawPath(path, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, r * 0.38f, p);
        return bmp;
    }

    // ---- data in -----------------------------------------------------------------------------------------------

    public void setPlaces(List<BoardingHouse> list) {
        places = list == null ? new ArrayList<>() : new ArrayList<>(list);
        if (selectedId != null && findPlace(selectedId) == null) { selectedId = null; b.peek.setVisibility(View.GONE); }
        render();
    }

    /** The radius ring (null centre or km <= 0 removes it). */
    public void setRing(Double lat, Double lng, double km) { ringLat = lat; ringLng = lng; ringKm = km; if (style != null) set("radius-src", MapGeo.circleJson(ringLat, ringLng, ringKm, 64)); }

    /** The campus dot (null removes it). */
    public void setCampusDot(Double lat, Double lng) { campusLat = lat; campusLng = lng; if (style != null) set("campus-src", MapGeo.pointJson(campusLat, campusLng)); }

    /** The live "you are here" dot (null removes it). */
    public void setUser(Double lat, Double lng) { userLat = lat; userLng = lng; if (style != null) set("user-src", MapGeo.pointJson(userLat, userLng)); }

    /** Stage 6: draws a route ({lat, lng} pairs; approximate = dashed straight-line estimate). Fewer than 2 points removes it. */
    public void setRoute(double[][] pts, boolean approximate) {
        routePts = pts != null && pts.length >= 2 ? pts : null;
        routeApprox = approximate;
        render();
    }

    public void clearRoute() { routePts = null; fitRoutePending = false; render(); }

    /** Zooms to the whole route (runs when the map is ready if it is not yet). */
    public void fitRoute() { fitRoutePending = true; post(this::applyCamera); }

    /** false = tapping a pin does nothing (the Directions card shows one place and needs no peek card). */
    public void setTapsEnabled(boolean enabled) { tapsEnabled = enabled; if (!enabled) { selectedId = null; b.peek.setVisibility(View.GONE); } }

    public void showLocate(boolean show) { b.locate.setVisibility(show ? View.VISIBLE : View.GONE); }

    /** true = a drag on the map is never taken over by a parent ScrollView (the map sits inside the place screen's scroll view). */
    public void setParentScrollBlocked(boolean blocked) { blockParentScroll = blocked; }

    /** Shows the pin as selected and opens the peek card. */
    public void select(String id) {
        BoardingHouse p = id == null ? null : findPlace(id);
        selectedId = p == null ? null : id;
        if (p == null) { b.peek.setVisibility(View.GONE); render(); return; }
        b.peekName.setText(p.name());
        StringBuilder info = new StringBuilder(Money.perMonth(p.price())).append("  -  ").append(ListRules.ratingText(p.avgRating(), p.reviewCount()));
        if (p.distanceKm() > 0) info.append("  -  ").append(Geo.fmt(p.distanceKm()));
        b.peekInfo.setText(info);
        b.peek.setVisibility(View.VISIBLE);
        render();
    }

    private BoardingHouse findPlace(String id) {
        for (BoardingHouse p : places) if (p.id().equals(id)) return p;
        return null;
    }

    private void render() {
        if (style == null) return;
        set("places-src", MapGeo.placesJson(places, selectedId));
        set("radius-src", MapGeo.circleJson(ringLat, ringLng, ringKm, 64));
        set("campus-src", MapGeo.pointJson(campusLat, campusLng));
        set("user-src", MapGeo.pointJson(userLat, userLng));
        set("route-src", MapGeo.lineJson(routePts, routeApprox));
        boolean has = routePts != null;
        set("route-start-src", has ? MapGeo.pointJson(routePts[0][0], routePts[0][1]) : MapGeo.EMPTY);
        set("route-end-src", has ? MapGeo.pointJson(routePts[routePts.length - 1][0], routePts[routePts.length - 1][1]) : MapGeo.EMPTY);
    }

    private void set(String id, String json) {
        GeoJsonSource s = style.getSourceAs(id);
        if (s != null) s.setGeoJson(json);
    }

    // ---- camera ------------------------------------------------------------------------------------------------

    /** Zoom to the ring if there is one, else to all pins, else to the default area. */
    public void fit() { fitPending = true; target = null; post(this::applyCamera); }

    public void moveTo(double lat, double lng, double zoom) { target = new double[]{lat, lng, zoom}; fitPending = false; post(this::applyCamera); }

    private void applyCamera() {
        if (map == null) return;                       // runs again from onMapReady once the style is in
        try {
            if (fitRoutePending) {                          // a route to show beats any pending moveTo
                fitRoutePending = false;
                double[] rb = MapGeo.pathBounds(routePts);
                if (rb != null) { target = null; fitBounds(rb, 40); return; }
            }
            if (target != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(target[0], target[1]), target[2]));
                target = null;
                return;
            }
            if (!fitPending) return;
            fitPending = false;
            double[] bb = ringLat != null && ringKm > 0 ? MapGeo.ringBounds(ringLat, ringLng, ringKm) : MapGeo.bounds(places);
            if (bb == null) map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(MapGeo.DEFAULT_LAT, MapGeo.DEFAULT_LNG), 12));
            else fitBounds(bb, 48, ringLat != null && ringKm > 0 && pillShown ? 44 : 0);
        } catch (Exception e) {
            Log.w("BOFO", "Map camera", e);
        }
    }

    /** {minLat, minLng, maxLat, maxLng}: a single point gets zoom 15, anything else is fitted with {@code padDp} around it. */
    private void fitBounds(double[] bb, int padDp) { fitBounds(bb, padDp, 0); }

    private void fitBounds(double[] bb, int padDp, int topExtraDp) {
        if (bb[0] == bb[2] && bb[1] == bb[3]) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(bb[0], bb[1]), 15));
        } else {
            float d = getResources().getDisplayMetrics().density;
            int pad = Math.round(padDp * d), top = Math.round((padDp + topExtraDp) * d);
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(bb[2], bb[3], bb[0], bb[1]), pad, top, pad, pad));
        }
    }

    // ---- taps --------------------------------------------------------------------------------------------------

    private boolean onTap(@NonNull LatLng point) {
        if (map == null || !tapsEnabled) return false;
        PointF pt = map.getProjection().toScreenLocation(point);
        float d = getResources().getDisplayMetrics().density;
        RectF box = new RectF(pt.x - 22 * d, pt.y - 44 * d, pt.x + 22 * d, pt.y + 10 * d);     // pins are anchored at their tip: look above the tap too
        List<Feature> hit = map.queryRenderedFeatures(box, "places");
        if (!hit.isEmpty()) {
            String id = hit.get(0).getStringProperty("id");
            if (id != null) { select(id); return true; }
        }
        if (selectedId != null) select(null);
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (blockParentScroll && getParent() != null) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) getParent().requestDisallowInterceptTouchEvent(false);
        }
        return super.dispatchTouchEvent(ev);
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------

    @Override public void onStart(@NonNull LifecycleOwner o) { if (mapView != null) mapView.onStart(); }
    @Override public void onResume(@NonNull LifecycleOwner o) { if (mapView != null) mapView.onResume(); }
    @Override public void onPause(@NonNull LifecycleOwner o) { if (mapView != null) mapView.onPause(); }
    @Override public void onStop(@NonNull LifecycleOwner o) { if (mapView != null) mapView.onStop(); }

    @Override
    public void onDestroy(@NonNull LifecycleOwner o) {
        o.getLifecycle().removeObserver(this);
        if (mapView != null) { mapView.onDestroy(); b.mapHost.removeView(mapView); }
        mapView = null; map = null; style = null; owner = null;
    }
}
