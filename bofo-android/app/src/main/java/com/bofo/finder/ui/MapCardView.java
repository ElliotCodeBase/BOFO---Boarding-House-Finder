package com.bofo.finder.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Typeface;
import android.graphics.Shader;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.location.Location;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.bofo.finder.R;
import com.bofo.finder.config.AppConfig;
import com.bofo.finder.databinding.ViewMapCardBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.util.AppPrefs;
import com.bofo.finder.util.Connectivity;
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
import org.maplibre.android.maps.MapLibreMapOptions;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.Layer;
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
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;

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
        /** Step 6: an orange campus dot was tapped. */
        default void campusTapped(String id) { }
        /** Step 8: the person tapped a pin (id) or closed / left the preview (null). Not called for select() from code. */
        default void placeSelected(@Nullable String id) { }
    }

    private static final float PIN_ZOOM = 12.5f;
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

    // ---- Step 5: radar dot, location pill, overview ----
    /** What the glass pill says. NONE hides it. */
    public enum Loc { NONE, FINDING, EXACT, APPROX, OFF }
    private static final int BLUE = 0xFF2F6FED;
    private Loc locState = Loc.NONE;
    private ValueAnimator pop, found, locatePulse;
    private float popT = 1f, foundT = -1f;
    private long lastPopPaint;
    private RadarOverlayView radar;        // ripples + found ring drawn on a plain Canvas above the map (the map stays idle)
    private int placesVer, collegesVer;    // bumped when the data changes: lets a pure pan skip the rebuild
    private String placesSig, collegesSig;
    private Double heading;                // degrees, from the last two fixes (null until the phone has moved)
    private boolean overviewPending;

    // ---- Step 6: clusters, labels, campus dots, price glow ----
    private final java.util.Map<String, float[]> imgSize = new java.util.HashMap<>();   // label bitmap sizes: style.getImage() copies the whole bitmap
    /** Every circle on the map (dots, user dot, route ends, number bubbles) is drawn this many times bigger than the original size. */
    private static final float BIG = 1.5f;
    private final Set<String> imgs = new HashSet<>();      // bitmaps already added to the style (labels, cluster bubbles)
    private List<College> colleges = new ArrayList<>();
    private String glowId, routeLabel;
    private static final int DARK_PURPLE = 0xFF2A1C8F, GLOW = 0xFFF43F5E, CAMPUS_TEXT = 0xFFB45309;

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
        b.peekClose.setOnClickListener(v -> userSelect(null));
        b.peekOpen.setOnClickListener(v -> { if (listener != null && selectedId != null) listener.openPlace(selectedId); });
    }

    public void setListener(Listener l) { listener = l; }

    /** Step 4: the radius pill (top-centre). km is clamped to 0.5-5 and snapped to 0.5; setting it never fires the listener. */
    public void setRadiusPill(boolean show, double km) {
        pillShown = show;
        b.radiusPill.setVisibility(show ? View.VISIBLE : View.GONE);
        paintLocPill();
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
        if ("off".equalsIgnoreCase(AppConfig.get("map.mode").trim())) return;      // lag test: no map at all (assets/config.properties: map.mode=off)
        Context app = getContext().getApplicationContext();
        MapLibre.getInstance(app);
        capCache(app);
        owner = lifecycleOwner;
        mapView = new MapView(getContext(), MapLibreMapOptions.createFromAttributes(getContext(), null)
                .pixelRatio(getResources().getDisplayMetrics().density));   // 3x screens draw 2.25x the pixels for no visible gain; the rounded corners are drawn by CornerMaskView
        b.mapHost.addView(mapView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        radar = new RadarOverlayView(getContext(), getResources().getDisplayMetrics().density);   // same unit as the map's pixelRatio
        b.mapHost.addView(radar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mapView.setMaximumFps(60);          // 120 Hz phones (realme 14 Pro+) would otherwise render the map up to 120 times a second while panning: double the GPU work, no visible gain
        mapView.onCreate(null);
        owner.getLifecycle().addObserver(this);        // replays onStart / onResume if the screen is already showing
        mapView.getMapAsync(this::onMapReady);
        // v2.0.67: blank / half-drawn map heals itself: tiles that did not arrive are asked for again, and the map reloads the moment the internet is back
        mapView.addOnDidFailLoadingMapListener(msg -> scheduleRefresh());
        mapView.addOnDidFinishRenderingMapListener(fully -> {
            renderedFully = fully;
            if (fully) { styleRetries = 0; removeCallbacks(refreshMap); }
            else if (style != null && !camMoving) scheduleRefresh();
        });
        Connectivity.OFFLINE.observe(owner, off -> {
            boolean isOff = Boolean.TRUE.equals(off);
            if (wasOffline && !isOff) { styleRetries = 0; reloadStyle(); }
            wasOffline = isOff;
        });
    }

    private boolean renderedFully, wasOffline;
    private int styleRetries;
    private static final int MAX_STYLE_RETRIES = 6;
    private static final long REFRESH_DELAY_MS = 5000;
    private final Runnable refreshMap = this::reloadStyle;

    private void scheduleRefresh() {
        if (map == null || styleRetries >= MAX_STYLE_RETRIES) return;
        removeCallbacks(refreshMap);
        postDelayed(refreshMap, REFRESH_DELAY_MS);
    }

    /** Sets the same style again: MapLibre then asks for every tile again. Pins, ring and route are redrawn; the camera stays where it is. */
    private void reloadStyle() {
        if (map == null) return;
        String json = loadStyle(getContext());
        if (json == null) return;
        styleRetries++;
        map.setStyle(new Style.Builder().fromJson(json), st -> { style = st; addLayers(st); render(); });
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
        m.setMinZoomPreference(6);                         // same range as the JavaFX map (it fits down to zoom 6; the tiles stop at 14)
        m.setMaxZoomPreference(16);
        m.setPrefetchZoomDelta(1);                         // default 4 loads lower zoom levels in advance on every zoom: the stutter the JavaFX flyTo also avoids
        m.getUiSettings().setRotateGesturesEnabled(false);
        m.getUiSettings().setTiltGesturesEnabled(false);
        m.getUiSettings().setLogoEnabled(false);          // the attribution text stays visible (licence)
        m.addOnMapClickListener(this::onTap);
        m.addOnCameraMoveStartedListener(reason -> { camMoving = true; removeCallbacks(idleRebuild); if (radar != null) radar.setMoving(true); });
        m.addOnCameraIdleListener(() -> {
            camMoving = false;
            removeCallbacks(idleRebuild);
            refreshRadar();                         // exact position again: the ripples come back
            postDelayed(idleRebuild, 160);          // fling / double-tap / pinch fire several idles in a row: rebuild once, after the hand is off
        });      // pins / clusters / labels depend on the zoom
        String json = loadStyle(getContext());
        if (json == null) return;
        m.setStyle(new Style.Builder().fromJson(json), st -> { style = st; addLayers(st); render(); post(this::applyCamera); });
    }

    /** Lag test only (map.mode=raster): one raster layer of ready-made picture tiles, like the JavaFX app shows. OpenStreetMap tiles are for TESTING; pick a licensed raster plan before release. */
    private static final String RASTER_TEST_STYLE = "{\"version\":8,\"name\":\"BOFO raster test\",\"transition\":{\"duration\":0,\"delay\":0},"
            + "\"sources\":{\"raster-test\":{\"type\":\"raster\",\"tiles\":[\"https://tile.openstreetmap.org/{z}/{x}/{y}.png\"],\"tileSize\":256,\"maxzoom\":19,"
            + "\"attribution\":\"\\u00a9 OpenStreetMap contributors\"}},"
            + "\"layers\":[{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":\"#F5F6FC\"}},{\"id\":\"raster-test\",\"type\":\"raster\",\"source\":\"raster-test\"}]}";

    private static String loadStyle(Context c) {
        if ("raster".equalsIgnoreCase(AppConfig.get("map.mode").trim())) return RASTER_TEST_STYLE;
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
        imgs.clear();                                     // a new style starts without our label bitmaps
        int primary = ContextCompat.getColor(getContext(), R.color.bofo_primary);
        int amber = ContextCompat.getColor(getContext(), R.color.bofo_amber);
        st.addImage("pin", pin(primary));
        st.addImage("pin-star", pin(amber));
        st.addSource(new GeoJsonSource("radius-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("campus-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("user-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("cone-src", MapGeo.EMPTY));
        st.addImage("user-cone", cone());
        st.addSource(new GeoJsonSource("places-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("labels-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("colleges-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("college-labels-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("ringlabel-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("glow-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("routelabel-src", MapGeo.EMPTY));
        st.addImage("pin-sel", pin(DARK_PURPLE));
        st.addSource(new GeoJsonSource("route-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("route-start-src", MapGeo.EMPTY));
        st.addSource(new GeoJsonSource("route-end-src", MapGeo.EMPTY));
        st.addLayer(new FillLayer("radius-fill", "radius-src").withProperties(
                PropertyFactory.fillColor(primary), PropertyFactory.fillOpacity(0.06f)));
        st.addLayer(new LineLayer("radius-glow", "radius-src").withProperties(PropertyFactory.lineColor(primary), PropertyFactory.lineWidth(8f), PropertyFactory.lineOpacity(0.10f), PropertyFactory.lineBlur(4f))); st.addLayer(new LineLayer("radius-line", "radius-src").withProperties(
                PropertyFactory.lineColor(primary), PropertyFactory.lineWidth(1.6f), PropertyFactory.lineOpacity(0.85f)));
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
                PropertyFactory.circleRadius(7f * BIG), PropertyFactory.circleColor(0xFF2F6FED),
                PropertyFactory.circleStrokeWidth(3f), PropertyFactory.circleStrokeColor(0xFFFFFFFF)));
        st.addLayer(new CircleLayer("route-end", "route-end-src").withProperties(
                PropertyFactory.circleRadius(8f * BIG), PropertyFactory.circleColor(0xFFE5484D),
                PropertyFactory.circleStrokeWidth(3f), PropertyFactory.circleStrokeColor(0xFFFFFFFF)));        // red destination
        // Step 6: price glow (a house lit from the rent card), then every campus as a small orange dot (tap = choose it)
        st.addLayer(new CircleLayer("places-glow", "glow-src").withProperties(
                PropertyFactory.circleRadius(18f * BIG), PropertyFactory.circleColor(GLOW), PropertyFactory.circleOpacity(0.32f),
                PropertyFactory.circleStrokeColor(GLOW), PropertyFactory.circleStrokeWidth(2f), PropertyFactory.circleStrokeOpacity(0.8f)));
        st.addLayer(new CircleLayer("colleges-dot", "colleges-src").withProperties(
                PropertyFactory.circleRadius(6f * BIG), PropertyFactory.circleColor(amber),
                PropertyFactory.circleStrokeWidth(2.5f), PropertyFactory.circleStrokeColor(0xFFFFFFFF)));
        st.addLayer(new CircleLayer("campus-dot", "campus-src").withProperties(
                PropertyFactory.circleRadius(8f * BIG), PropertyFactory.circleColor(amber),
                PropertyFactory.circleStrokeWidth(3f), PropertyFactory.circleStrokeColor(0xFFFFFFFF)));
        // Step 5: the radar dot (LiveDot): the view cone, then shadow + white ring + blue core
        // (the two ripple rings and the "found you" ring are drawn by RadarOverlayView: changing a map layer every frame re-renders the whole map)
        st.addLayer(new SymbolLayer("user-cone", "cone-src").withProperties(
                PropertyFactory.iconImage("user-cone"), PropertyFactory.iconRotate(Expression.get("b")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true)));
        st.addLayer(new CircleLayer("user-shadow", "user-src").withProperties(
                PropertyFactory.circleRadius(13f * BIG), PropertyFactory.circleColor(0xFF17142A), PropertyFactory.circleOpacity(0.22f)));
        st.addLayer(new CircleLayer("user-ring", "user-src").withProperties(
                PropertyFactory.circleRadius(10f * BIG), PropertyFactory.circleColor(0xFFFFFFFF)));
        st.addLayer(new CircleLayer("user-dot", "user-src").withProperties(
                PropertyFactory.circleRadius(7f * BIG), PropertyFactory.circleColor(BLUE)));
        // Step 6: zoomed out = small dots that merge into numbered bubbles; zoomed in = full pins with names (rebuildPlaces decides, per zoom)
        Expression sel = Expression.eq(Expression.get("sel"), Expression.literal(1));
        st.addLayer(new CircleLayer("places-dots", "places-src").withProperties(
                PropertyFactory.circleRadius(Expression.switchCase(sel, Expression.literal(8f * BIG), Expression.literal(5f * BIG))),
                PropertyFactory.circleColor(Expression.switchCase(sel, Expression.literal(DARK_PURPLE), Expression.literal(primary))),
                PropertyFactory.circleStrokeWidth(2f), PropertyFactory.circleStrokeColor(0xFFFFFFFF))
                .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("dot"))));
        st.addLayer(new SymbolLayer("places-clusters", "places-src").withProperties(
                PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true))
                .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("cluster"))));
        st.addLayer(new SymbolLayer("places", "places-src").withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(Expression.switchCase(sel, Expression.literal(1.3f * 1.25f), Expression.literal(1.25f))))
                .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("pin"))));
        for (String[] l : new String[][]{{"place-labels", "labels-src"}, {"college-labels", "college-labels-src"}})
            st.addLayer(new SymbolLayer(l[0], l[1]).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAnchor(Property.ICON_ANCHOR_TOP),
                    PropertyFactory.iconOffset(new Float[]{0f, 2f}), PropertyFactory.iconAllowOverlap(!l[0].equals("college-labels")), PropertyFactory.iconIgnorePlacement(!l[0].equals("college-labels")), PropertyFactory.iconPadding(l[0].equals("college-labels") ? 3f : 0f)));
        st.addLayer(new SymbolLayer("route-label", "routelabel-src").withProperties(
                PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconOffset(new Float[]{0f, -12f}), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true)));
        st.addLayer(new SymbolLayer("ring-label", "ringlabel-src").withProperties(
                PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true)));
    }

    /** View cone: a soft blue wedge pointing up from the centre of a 64 dp square (rotated to the direction of travel by the layer). */
    private Bitmap cone() {
        float d = getResources().getDisplayMetrics().density;
        int n = Math.round(64 * d);
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float cx = n / 2f, r = n / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx, cx, r, new int[]{0x662F6FED, 0x002F6FED}, new float[]{0.15f, 1f}, Shader.TileMode.CLAMP));
        c.drawArc(new RectF(0, 0, n, n), -90 - 32, 64, true, p);        // 64 degree wedge around "up"
        return bmp;
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
        List<BoardingHouse> next = list == null ? new ArrayList<>() : new ArrayList<>(list);
        if (next.equals(places)) return;                     // the list screen reloads often with the same houses: nothing to redraw
        places = next;
        placesVer++;
        if (selectedId != null && findPlace(selectedId) == null) { selectedId = null; b.peek.setVisibility(View.GONE); }
        if (style != null) rebuildPlaces(true);
    }

    /** The radius ring (null centre or km <= 0 removes it). */
    public void setRing(Double lat, Double lng, double km) { ringLat = lat; ringLng = lng; ringKm = km; if (style != null) { set("radius-src", MapGeo.circleJson(ringLat, ringLng, ringKm, 64)); paintRingLabel(); } }

    /** The campus dot (null removes it). */
    public void setCampusDot(Double lat, Double lng) { campusLat = lat; campusLng = lng; if (style != null) set("campus-src", MapGeo.pointJson(campusLat, campusLng)); }

    /** The live "you are here" dot (null removes it). The first fix grows in from 82 %; the direction of travel comes from the last two fixes. */
    public void setUser(Double lat, Double lng) {
        boolean first = userLat == null && lat != null;
        if (lat != null && userLat != null) {
            float[] r = new float[3];
            Location.distanceBetween(userLat, userLng, lat, lng, r);
            if (r[0] > 8) heading = (double) ((r[1] % 360 + 360) % 360);
        } else if (lat == null) heading = null;
        userLat = lat; userLng = lng;
        if (style != null) { set("user-src", MapGeo.pointJson(userLat, userLng)); set("cone-src", coneJson()); }
        refreshRadar();
        if (lat == null) { stopRipples(); return; }
        if (first) popIn();
        startRipples();
        paintUser();
    }

    private String coneJson() {
        if (userLat == null || heading == null) return MapGeo.EMPTY;
        return String.format(Locale.US, "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]},\"properties\":{\"b\":%.1f}}", userLng, userLat, heading);
    }

    /** Step 5: the glass pill ("Finding your location…", "Your location", "Approximate location", "Location is off"). Hidden while the radius pill is up. */
    public void setLocState(Loc s) {
        locState = s == null ? Loc.NONE : s;
        paintLocPill();
        if (locState == Loc.FINDING) startLocatePulse(); else stopLocatePulse();
    }

    private void paintLocPill() {
        int res = locState == Loc.FINDING ? R.string.loc_pill_finding : locState == Loc.EXACT ? R.string.loc_pill_exact
                : locState == Loc.APPROX ? R.string.loc_pill_approx : locState == Loc.OFF ? R.string.loc_pill_off : 0;
        if (res != 0) b.locPill.setText(res);
        b.locPill.setVisibility(res != 0 && !pillShown ? View.VISIBLE : View.GONE);
    }

    private void startLocatePulse() {
        if (locatePulse != null) return;
        locatePulse = ObjectAnimator.ofPropertyValuesHolder(b.locate,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.14f), PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.14f), PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.65f));
        locatePulse.setDuration(650);
        locatePulse.setRepeatMode(ValueAnimator.REVERSE);
        locatePulse.setRepeatCount(ValueAnimator.INFINITE);
        locatePulse.start();
    }

    private void stopLocatePulse() {
        if (locatePulse == null) return;
        locatePulse.cancel(); locatePulse = null;
        b.locate.setScaleX(1f); b.locate.setScaleY(1f); b.locate.setAlpha(1f);
    }

    /** Step 5: purple locate button while the overview is on. */
    public void setLocateActive(boolean on) {
        b.locate.setBackgroundResource(on ? R.drawable.bg_map_button_on : R.drawable.bg_map_button);
        if (on) b.locate.setColorFilter(0xFFFFFFFF); else b.locate.clearColorFilter();
    }

    /** Step 5: the locate button found the phone: one bigger "found you" ring and a small pop. */
    public void foundYou() {
        if (userLat == null || !animationsOn()) return;
        if (found != null) found.cancel();
        found = ValueAnimator.ofFloat(0f, 1f);
        found.setDuration(750);
        found.addUpdateListener(a -> { foundT = (float) a.getAnimatedValue(); if (radar != null) radar.setFound(foundT); });
        found.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) { foundT = -1f; if (radar != null) radar.setFound(-1f); }
        });
        found.start();
    }

    private void popIn() {
        if (pop != null) pop.cancel();
        if (style == null || !animationsOn()) { popT = 1f; if (radar != null) radar.setPop(1f); return; }
        popT = 0f;
        pop = ValueAnimator.ofFloat(0f, 1f);
        pop.setDuration(320);
        pop.addUpdateListener(a -> {
            popT = (float) a.getAnimatedValue();
            if (radar != null) radar.setPop(popT);
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastPopPaint >= 33) { lastPopPaint = now; paintUser(); }      // 3 layer updates per frame re-render the map: ~30 fps is plenty for a 0.3 s pop
        });
        pop.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) { if (pop == an) { popT = 1f; if (radar != null) radar.setPop(1f); paintUser(); } }
        });
        pop.start();
    }

    private boolean animationsOn() {
        try { return android.provider.Settings.Global.getFloat(getContext().getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f; }
        catch (Exception e) { return true; }
    }

    /** The ripples live in RadarOverlayView (plain Canvas): the map itself is idle between gestures. */
    private void startRipples() {
        if (radar == null || userLat == null || owner == null || !isAttachedToWindow()) return;
        radar.start(animationsOn());
        refreshRadar();
    }

    private volatile boolean camMoving;
    private final Runnable idleRebuild = () -> { if (!camMoving) { rebuildPlaces(false); rebuildColleges(false); } };      // false = skip when nothing but the position changed

    /** Puts the overlay on the dot's current screen spot (cheap: one projection call). Called when the map is idle, a fix arrives or the card is resized. */
    private void refreshRadar() {
        if (radar == null) return;
        if (map == null || userLat == null) { radar.setCenter(null); radar.setMoving(false); return; }
        radar.setCenter(map.getProjection().toScreenLocation(new LatLng(userLat, userLng)));
        radar.setMoving(false);
    }

    private void stopRipples() {
        if (radar != null) radar.stop();
        if (style != null) paintUser();
    }

    private void layerCircle(String id, float radius, float opacity) {
        Layer l = style.getLayer(id);
        if (l != null) l.setProperties(PropertyFactory.circleRadius(radius), PropertyFactory.circleOpacity(opacity));
    }

    /** Draws the dot (shadow, white ring, blue core) for the first-fix pop. Ripples and the found ring are RadarOverlayView's job. */
    private void paintUser() {
        if (style == null) return;
        if (userLat == null) {
            for (String id : new String[]{"user-shadow", "user-ring", "user-dot"}) layerCircle(id, 1f, 0f);
            return;
        }
        float scale = 0.82f + 0.18f * popT;
        float a = popT;
        layerCircle("user-shadow", 13f * BIG * scale, 0.22f * a);
        layerCircle("user-ring", 10f * BIG * scale, a);
        layerCircle("user-dot", 7f * BIG * scale, a);
    }

    /** Stage 6: draws a route ({lat, lng} pairs; approximate = dashed straight-line estimate). Fewer than 2 points removes it. */
    public void setRoute(double[][] pts, boolean approximate) {
        routePts = pts != null && pts.length >= 2 ? pts : null;
        routeApprox = approximate;
        paintRoute(); paintRouteLabel();
    }

    public void clearRoute() { routePts = null; routeLabel = null; fitRoutePending = false; paintRoute(); paintRouteLabel(); }

    /** Step 8: "1.7 km · 21 min" chip just above the end of the route (null removes it). */
    public void setRouteLabel(@Nullable String text) { routeLabel = text; paintRouteLabel(); }

    private void paintRouteLabel() {
        if (style == null) return;
        if (routeLabel == null || routePts == null) { set("routelabel-src", MapGeo.EMPTY); return; }
        String key = "rt|" + routeLabel;
        if (imgs.add(key)) style.addImage(key, chip(routeLabel));
        double[] e = routePts[routePts.length - 1];
        set("routelabel-src", collection(new StringBuilder(feat(e[0], e[1], "\"icon\":\"" + esc(key) + "\""))));
    }

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
        if (p == null) { b.peek.setVisibility(View.GONE); if (style != null) rebuildPlaces(true); return; }
        b.peekName.setText(p.name());
        b.peekPrice.setText(Money.perMonth(p.price()));
        StringBuilder info = new StringBuilder("\u2605 ").append(ListRules.ratingText(p.avgRating(), p.reviewCount()));
        if (p.distanceKm() > 0) info.append("  \u00B7  ").append(Geo.fmt(p.distanceKm()));
        b.peekInfo.setText(info);
        b.peek.setVisibility(View.VISIBLE);
        if (style != null) rebuildPlaces(true);
    }

    private void userSelect(String id) {
        select(id);
        if (listener != null) listener.placeSelected(selectedId);
    }

    @Nullable public String selectedId() { return selectedId; }

    private BoardingHouse findPlace(String id) {
        for (BoardingHouse p : places) if (p.id().equals(id)) return p;
        return null;
    }

    private void render() {
        if (style == null) return;
        rebuildPlaces();
        set("radius-src", MapGeo.circleJson(ringLat, ringLng, ringKm, 64));
        set("campus-src", MapGeo.pointJson(campusLat, campusLng));
        paintRingLabel(); rebuildColleges(); paintGlow(); paintRouteLabel();
        set("user-src", MapGeo.pointJson(userLat, userLng));
        set("cone-src", coneJson());
        paintUser();
        paintRoute();
        refreshRadar();
    }

    /** Only the route sources (a new / cleared route no longer re-renders pins, colleges and the radar). */
    private void paintRoute() {
        if (style == null) return;
        set("route-src", MapGeo.lineJson(routePts, routeApprox));
        boolean has = routePts != null;
        set("route-start-src", has ? MapGeo.pointJson(routePts[0][0], routePts[0][1]) : MapGeo.EMPTY);
        set("route-end-src", has ? MapGeo.pointJson(routePts[routePts.length - 1][0], routePts[routePts.length - 1][1]) : MapGeo.EMPTY);
        applyRouteOpacity();
    }

    /** Settings > "See-through path": the route line (casing, line, dashed estimate) is 80% transparent (opacity 0.2) instead of solid. */
    private void applyRouteOpacity() {
        if (style == null) return;
        float o = AppPrefs.seeThroughPath(getContext()) ? 0.2f : 1f;
        for (String id : new String[]{"route-casing", "route-line", "route-dash"}) {
            LineLayer l = style.getLayerAs(id);
            if (l != null) l.setProperties(PropertyFactory.lineOpacity(o));
        }
    }

    private void set(String id, String json) {
        GeoJsonSource s = style.getSourceAs(id);
        if (s != null) s.setGeoJson(json);
    }

    /** Step 6: every campus (orange dots + names when zoomed in). Tap one = Listener.campusTapped. */
    public void setColleges(List<College> list) {
        List<College> next = list == null ? new ArrayList<>() : new ArrayList<>(list);
        if (next.equals(colleges)) return;
        colleges = next;
        collegesVer++;
        rebuildColleges(true);
    }

    /** Step 6: lights one house's circle (the rent card's Lowest / Middle / Highest); null turns it off. */
    public void setGlow(String id) { glowId = id; paintGlow(); }

    private void paintGlow() {
        if (style == null) return;
        BoardingHouse p = glowId == null ? null : findPlace(glowId);
        set("glow-src", p == null ? MapGeo.EMPTY : MapGeo.pointJson(p.lat(), p.lng()));
    }

    private static String esc(String t) { return t.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static String feat(double lat, double lng, String props) {
        return String.format(Locale.US, "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]},\"properties\":{%s}}", lng, lat, props);
    }

    private static String collection(StringBuilder feats) { return "{\"type\":\"FeatureCollection\",\"features\":[" + feats + "]}"; }

    private static String clip(String t, int n) { t = t == null ? "" : t.trim(); return t.length() > n ? t.substring(0, n - 1) + "\u2026" : t; }

    /** Pins zoomed in (names that do not collide), small dots zoomed out; dots closer than 30 dp merge into a numbered bubble (tap = zoom to the group). */
    private void rebuildPlaces() { rebuildPlaces(true); }

    /** force = false (after a camera move): skipped when the data, selection, zoom and card size are all unchanged, because a pure pan moves every pin the same way (clusters and label clashes do not change). */
    private void rebuildPlaces(boolean force) {
        if (style == null || map == null) return;
        String sig = placesVer + "|" + selectedId + "|" + Math.round(map.getCameraPosition().zoom * 20) + "|" + getWidth() + "x" + getHeight();
        if (!force && sig.equals(placesSig)) return;
        placesSig = sig;
        float d = getResources().getDisplayMetrics().density;
        int n = places.size();
        PointF[] sp = new PointF[n];
        for (int i = 0; i < n; i++) {
            BoardingHouse p = places.get(i);
            if (!Double.isNaN(p.lat()) && !Double.isNaN(p.lng())) sp[i] = map.getProjection().toScreenLocation(new LatLng(p.lat(), p.lng()));
        }
        StringBuilder feats = new StringBuilder(), labels = new StringBuilder();
        if (map.getCameraPosition().zoom >= PIN_ZOOM) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < n; i++) if (sp[i] != null) { if (places.get(i).id().equals(selectedId)) order.add(0, i); else order.add(i); }
            List<RectF> bodies = new ArrayList<>(), taken = new ArrayList<>();
            for (int i : order) bodies.add(new RectF(sp[i].x - 15 * d, sp[i].y - 40 * d, sp[i].x + 15 * d, sp[i].y));
            for (int k = 0; k < order.size(); k++) {
                int i = order.get(k);
                BoardingHouse p = places.get(i);
                boolean isSel = p.id().equals(selectedId);
                if (feats.length() > 0) feats.append(',');
                feats.append(feat(p.lat(), p.lng(), "\"kind\":\"pin\",\"id\":\"" + esc(p.id()) + "\",\"icon\":\"" + (isSel ? "pin-sel" : p.featured() ? "pin-star" : "pin") + "\",\"sel\":" + (isSel ? 1 : 0)));
                String text = clip(p.name(), 22);
                if (text.isEmpty()) continue;
                String key = "lb|" + text;
                if (imgs.add(key)) {
                    Bitmap lb = label(text, 0xFF17142A, 11);
                    imgSize.put(key, new float[]{lb.getWidth(), lb.getHeight()});
                    style.addImage(key, lb);
                }
                float[] wh = imgSize.get(key);
                float w = wh == null ? 80 * d : wh[0], h = wh == null ? 16 * d : wh[1];
                RectF r = new RectF(sp[i].x - w / 2, sp[i].y + 2 * d, sp[i].x + w / 2, sp[i].y + 2 * d + h);
                boolean clash = false;
                for (RectF t : taken) if (RectF.intersects(t, r)) { clash = true; break; }
                if (!clash) for (int j = 0; j < bodies.size() && !clash; j++) if (j != k && RectF.intersects(bodies.get(j), r)) clash = true;
                if (clash && !isSel) continue;                                    // colliding labels are hidden
                taken.add(r);
                if (labels.length() > 0) labels.append(',');
                labels.append(feat(p.lat(), p.lng(), "\"icon\":\"" + esc(key) + "\""));
            }
        } else {
            boolean[] used = new boolean[n];
            for (int i = 0; i < n; i++) {
                if (sp[i] == null || used[i]) continue;
                List<Integer> m = new ArrayList<>();
                m.add(i); used[i] = true;
                for (int j = i + 1; j < n; j++)
                    if (sp[j] != null && !used[j] && Math.hypot(sp[i].x - sp[j].x, sp[i].y - sp[j].y) < 30 * d) { m.add(j); used[j] = true; }
                if (feats.length() > 0) feats.append(',');
                if (m.size() == 1) {
                    BoardingHouse p = places.get(i);
                    feats.append(feat(p.lat(), p.lng(), "\"kind\":\"dot\",\"id\":\"" + esc(p.id()) + "\",\"sel\":" + (p.id().equals(selectedId) ? 1 : 0)));
                } else {
                    double la = 0, ln = 0, bn = -90, bs = 90, be = -180, bw = 180;
                    for (int j : m) {
                        BoardingHouse p = places.get(j);
                        la += p.lat(); ln += p.lng();
                        bn = Math.max(bn, p.lat()); bs = Math.min(bs, p.lat()); be = Math.max(be, p.lng()); bw = Math.min(bw, p.lng());
                    }
                    String key = "cl|" + m.size();
                    if (imgs.add(key)) style.addImage(key, bubble(m.size()));
                    feats.append(feat(la / m.size(), ln / m.size(), String.format(Locale.US,
                            "\"kind\":\"cluster\",\"icon\":\"%s\",\"bn\":%.6f,\"bs\":%.6f,\"be\":%.6f,\"bw\":%.6f", key, bn, bs, be, bw)));
                }
            }
        }
        set("places-src", collection(feats));
        set("labels-src", collection(labels));
    }

    private void rebuildColleges() { rebuildColleges(true); }

    private void rebuildColleges(boolean force) {
        if (style == null || map == null) return;
        boolean showNames = map.getCameraPosition().zoom >= 11f;
        String sig = collegesVer + "|" + showNames;
        if (!force && sig.equals(collegesSig)) return;      // the campus list is the same and the names are still on / off: nothing to send
        collegesSig = sig;
        StringBuilder dots = new StringBuilder(), names = new StringBuilder();
        for (College c : colleges) {
            if (dots.length() > 0) dots.append(',');
            dots.append(feat(c.lat(), c.lng(), "\"kind\":\"college\",\"id\":\"" + esc(c.id()) + "\""));
            if (!showNames) continue;
            String text = clip(c.name(), 26), key = "cn|" + text;
            if (imgs.add(key)) style.addImage(key, label(text, CAMPUS_TEXT, 11));
            if (names.length() > 0) names.append(',');
            names.append(feat(c.lat(), c.lng(), "\"icon\":\"" + esc(key) + "\""));
        }
        set("colleges-src", collection(dots));
        set("college-labels-src", collection(names));
    }

    /** "500 m" / "4.5 km" chip on the east edge of the radius circle. */
    private void paintRingLabel() {
        if (style == null) return;
        if (ringLat == null || ringKm <= 0) { set("ringlabel-src", MapGeo.EMPTY); return; }
        String text = ringKm < 1 ? Math.round(ringKm * 1000) + " m" : String.format(Locale.US, "%.1f km", ringKm), key = "rl|" + text;
        if (imgs.add(key)) style.addImage(key, chip(text));
        double[] e = MapGeo.destination(ringLat, ringLng, ringKm, 90);
        set("ringlabel-src", collection(new StringBuilder(feat(e[0], e[1], "\"icon\":\"" + key + "\""))));
    }

    // ---- bitmaps (the map style has no fonts, so text is drawn here) ----

    private Paint textPaint(int color, float sp, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

    /** Name under a pin / campus dot: dark bold text with a white halo. */
    private Bitmap label(String text, int color, float sp) {
        float d = getResources().getDisplayMetrics().density;
        Paint p = textPaint(color, sp, true);
        int w = Math.round(p.measureText(text) + 8 * d), h = Math.round(p.getTextSize() + 6 * d);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float y = h / 2f - (p.ascent() + p.descent()) / 2f;
        Paint halo = textPaint(0xFFFFFFFF, sp, true);
        halo.setStyle(Paint.Style.STROKE); halo.setStrokeWidth(3.5f * d); halo.setStrokeJoin(Paint.Join.ROUND);
        c.drawText(text, w / 2f, y, halo);
        c.drawText(text, w / 2f, y, p);
        return bmp;
    }

    /** Numbered purple bubble for merged dots. */
    private Bitmap bubble(int count) {
        float d = getResources().getDisplayMetrics().density;
        int sz = Math.round((count >= 100 ? 42 : count >= 10 ? 38 : 34) * BIG * d);
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(sz / 2f, sz / 2f, sz / 2f, p);
        p.setColor(ContextCompat.getColor(getContext(), R.color.bofo_primary));
        c.drawCircle(sz / 2f, sz / 2f, sz / 2f - 2.5f * d, p);
        Paint t = textPaint(0xFFFFFFFF, 13 * BIG, true);
        c.drawText(String.valueOf(count), sz / 2f, sz / 2f - (t.ascent() + t.descent()) / 2f, t);
        return bmp;
    }

    /** White pill with purple text (radius edge label). */
    private Bitmap chip(String text) {
        float d = getResources().getDisplayMetrics().density;
        Paint t = textPaint(0xFFFFFFFF, 11, true);
        int w = Math.round(t.measureText(text) + 16 * d), h = Math.round(t.getTextSize() + 10 * d);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setColor(0xFF17142A); c.drawRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d); p.setColor(0xFF17142A);
        c.drawRoundRect(new RectF(d / 2, d / 2, w - d / 2, h - d / 2), h / 2f, h / 2f, p);
        c.drawText(text, w / 2f, h / 2f - (t.ascent() + t.descent()) / 2f, t);
        return bmp;
    }

    // ---- camera ------------------------------------------------------------------------------------------------

    /** Zoom to the ring if there is one, else to all pins, else to the default area. */
    public void fit() { fitPending = true; target = null; post(this::applyCamera); }

    /** Step 5 overview: zoom out so every pin (and the phone, if known) fills ~90 % of the card. */
    public void overview() { overviewPending = true; fitPending = false; target = null; post(this::applyCamera); }

    /** Owner form pin picker: the map centre now (lat, lng), or null before the map is ready. */
    @Nullable public double[] center() {
        if (map == null || map.getCameraPosition().target == null) return null;
        return new double[]{map.getCameraPosition().target.getLatitude(), map.getCameraPosition().target.getLongitude()};
    }
    public void moveTo(double lat, double lng, double zoom) { target = new double[]{lat, lng, zoom}; fitPending = false; overviewPending = false; post(this::applyCamera); }

    private void applyCamera() {
        if (map == null) return;                       // runs again from onMapReady once the style is in
        try {
            if (fitRoutePending) {                          // a route to show beats any pending moveTo
                fitRoutePending = false;
                double[] rb = MapGeo.pathBounds(routePts);
                if (rb != null) { target = null; fitRouteBounds(rb); return; }
            }
            if (overviewPending) {
                overviewPending = false;
                target = null;
                double[] bb = MapGeo.bounds(places);
                if (userLat != null) bb = bb == null ? new double[]{userLat, userLng, userLat, userLng}
                        : new double[]{Math.min(bb[0], userLat), Math.min(bb[1], userLng), Math.max(bb[2], userLat), Math.max(bb[3], userLng)};
                if (bb == null) map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(MapGeo.DEFAULT_LAT, MapGeo.DEFAULT_LNG), 12));
                else if (bb[0] == bb[2] && bb[1] == bb[3]) map.easeCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(bb[0], bb[1]), 15), 500);
                else {
                    int pad = Math.round(0.05f * Math.min(getWidth(), getHeight()));        // 5 % each side = ~90 % of the card
                    map.easeCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(bb[2], bb[3], bb[0], bb[1]), pad), 600);
                }
                return;
            }
            if (target != null) {
                map.easeCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(target[0], target[1]), target[2]), 500);
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

    /** Fits the whole route, leaving room at the bottom for the preview card (and at the top for the radius pill). Glides there. */
    private void fitRouteBounds(double[] bb) {
        if (bb[0] == bb[2] && bb[1] == bb[3]) { fitBounds(bb, 40); return; }
        float d = getResources().getDisplayMetrics().density;
        int bottom = Math.round(((b.peek.getVisibility() == View.VISIBLE ? Math.max(b.peek.getHeight() / d, 70) + 26 + 18 : 40)) * d);
        int top = Math.round((pillShown ? 84 : 44) * d), side = Math.round(40 * d);
        map.easeCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(bb[2], bb[3], bb[0], bb[1]), side, top, side, bottom), 450);
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
        RectF box = new RectF(pt.x - 30 * d, pt.y - 56 * d, pt.x + 30 * d, pt.y + 14 * d);     // pins are anchored at their tip: look above the tap too
        List<Feature> hit = map.queryRenderedFeatures(box, "places", "places-dots", "places-clusters", "colleges-dot");
        for (Feature f : hit) {
            String kind = f.getStringProperty("kind"), id = f.getStringProperty("id");
            if ("cluster".equals(kind)) { zoomToCluster(f); return true; }
            if ("college".equals(kind) && id != null) { if (listener != null) listener.campusTapped(id); return true; }
            if (id != null) {
                userSelect(id);
                BoardingHouse p = findPlace(id);
                if (p != null) map.easeCamera(CameraUpdateFactory.newLatLng(new LatLng(p.lat(), p.lng())), 300);
                return true;
            }
        }
        if (selectedId != null) userSelect(null);
        return false;
    }

    /** Tap on a numbered bubble: glide to a view of the whole group. */
    private void zoomToCluster(Feature f) {
        try {
            double n = f.getNumberProperty("bn").doubleValue(), sth = f.getNumberProperty("bs").doubleValue();
            double e = f.getNumberProperty("be").doubleValue(), w = f.getNumberProperty("bw").doubleValue();
            if (n == sth && e == w) map.easeCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(n, e), PIN_ZOOM + 1), 400);
            else map.easeCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(n, e, sth, w), Math.round(60 * getResources().getDisplayMetrics().density)), 400);
        } catch (Exception ex) { Log.w("BOFO", "Cluster zoom", ex); }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); if (radar != null) post(this::refreshRadar); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); startRipples(); }
    @Override protected void onDetachedFromWindow() { stopRipples(); super.onDetachedFromWindow(); }

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
    @Override public void onResume(@NonNull LifecycleOwner o) { if (mapView != null) { mapView.onResume(); if (map != null && style != null && !renderedFully) scheduleRefresh(); } startRipples(); applyRouteOpacity(); }   // picks up the Settings switch
    @Override public void onPause(@NonNull LifecycleOwner o) { if (mapView != null) mapView.onPause(); stopRipples(); }
    @Override public void onStop(@NonNull LifecycleOwner o) { if (mapView != null) mapView.onStop(); }

    @Override
    public void onDestroy(@NonNull LifecycleOwner o) {
        o.getLifecycle().removeObserver(this);
        removeCallbacks(refreshMap);
        stopRipples(); stopLocatePulse();
        if (pop != null) pop.cancel();
        if (found != null) found.cancel();
        if (radar != null) { radar.stop(); b.mapHost.removeView(radar); radar = null; }
        if (mapView != null) { mapView.onDestroy(); b.mapHost.removeView(mapView); }
        mapView = null; map = null; style = null; owner = null;
        placesSig = null; collegesSig = null;
    }
}
