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
import com.bumptech.glide.Glide;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bofo.finder.R;
import com.bofo.finder.config.AppConfig;
import com.bofo.finder.databinding.ItemMapStripBinding;
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
import org.maplibre.android.camera.CameraPosition;
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
import java.util.Collection;
import java.util.Collections;
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
        /** v2.0.80: "Search this area" was tapped: the visible box (south, west, north, east). */
        default void searchArea(double south, double west, double north, double east) { }
        /** v2.0.82: long-press on a pin or a strip card (Explore compare tray). */
        default void placeLongPressed(String id) { }
        /** v2.0.83: the "Commute" chip of a strip card was tapped. */
        default void commuteTapped(String id) { }
    }

    private static final float PIN_ZOOM = 12.5f;
    private static final float PIN_SEL_SCALE = 1.12f;       // v2.0.80: the selected price pin is a little bigger
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
        setupAttribution();
        setupLocatePress();
        b.peekPhoto.setOutlineProvider(new android.view.ViewOutlineProvider() {       // v2.0.79: rounded photo corners without a background (the icon tile shows through until it loads)
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 16 * v.getResources().getDisplayMetrics().density); }
        });
        b.peekPhoto.setClipToOutline(true);
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
        b.searchArea.setOnClickListener(v -> {            // v2.0.80
            double[] now = viewBox();
            if (now != null) { areaBase = now; if (listener != null) listener.searchArea(now[0], now[1], now[2], now[3]); }
            showAreaPill(false);
        });
        b.nearCampus.setOnClickListener(v -> { if (campusLat != null && campusLng != null) moveTo(campusLat, campusLng, 14.5); });
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
        if (map == null || ringLat == null || ringKm <= 0 || routeShown()) return;       // v2.0.74: the ring is hidden while a path is shown
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
    // v2.0.69: a new MapView starts at lat 0 / lng 0 (open sea = blank map). A screen that asks for it gets the last camera back.
    private static final java.util.Map<String, double[]> SAVED_CAMERA = new java.util.HashMap<>();
    private String cameraKey;

    /** Call before {@link #activate}: the camera is saved under this key when it stops moving or the screen closes, and restored when the map opens again. */
    public void rememberCamera(String key) { cameraKey = key; }

    private void saveCamera() {
        if (cameraKey == null || map == null) return;
        try {
            CameraPosition cp = map.getCameraPosition();
            if (cp.target == null || (cp.target.getLatitude() == 0 && cp.target.getLongitude() == 0)) return;     // never remember the empty start view
            SAVED_CAMERA.put(cameraKey, new double[]{cp.target.getLatitude(), cp.target.getLongitude(), cp.zoom});
        } catch (Throwable t) { Log.w("BOFO", "Camera not saved", t); }
    }

    private static final long BLANK_CHECK_MS = 6000;
    /** v2.0.68: a failed tile source still counts as "fully rendered", so also look at what is on screen: no land / water / road drawn = blank map = load it again. */
    private final Runnable blankCheck = () -> {
        if (map == null || mapView == null || style == null || mapView.getWidth() == 0) return;
        java.util.List<Feature> drawn = map.queryRenderedFeatures(new android.graphics.RectF(0, 0, mapView.getWidth(), mapView.getHeight()),
                "landcover", "park", "landuse", "water", "waterway", "building", "road-minor", "road-major");
        if (drawn.isEmpty()) { if (styleRetries < MAX_STYLE_RETRIES) reloadStyle(); }
        else styleRetries = 0;
    };

    private void scheduleBlankCheck() {
        removeCallbacks(blankCheck);
        postDelayed(blankCheck, BLANK_CHECK_MS);
    }

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
        map.setStyle(new Style.Builder().fromJson(json), st -> { style = st; addLayers(st); render(); scheduleBlankCheck(); });
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
        m.getUiSettings().setLogoEnabled(false);
        m.getUiSettings().setAttributionEnabled(false);   // v2.0.76: MapLibre's own blue (i) is off; our own credit pill / (i) (setupAttribution) is the licence text
        m.addOnMapClickListener(this::onTap);
        m.addOnMapLongClickListener(this::onLongTap);
        double[] saved = cameraKey == null ? null : SAVED_CAMERA.get(cameraKey);
        if (saved != null) m.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(saved[0], saved[1]), saved[2]));     // back where it was
        m.addOnCameraMoveStartedListener(reason -> { camMoving = true; if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) gestureMove = true; removeCallbacks(idleRebuild); if (radar != null) radar.setMoving(true); });
        m.addOnCameraIdleListener(() -> {
            camMoving = false;
            saveCamera();
            removeCallbacks(idleRebuild);
            refreshRadar();                         // exact position again: the ripples come back
            checkArea();
            postDelayed(idleRebuild, 160);          // fling / double-tap / pinch fire several idles in a row: rebuild once, after the hand is off
        });      // pins / clusters / labels depend on the zoom
        String json = loadStyle(getContext());
        if (json == null) return;
        m.setStyle(new Style.Builder().fromJson(json), st -> { style = st; addLayers(st); render(); post(this::applyCamera); scheduleBlankCheck(); });
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
        Expression isDot = Expression.eq(Expression.get("kind"), Expression.literal("dot"));
        Expression isViewed = Expression.eq(Expression.get("viewed"), Expression.literal(1));
        Expression dotColor = Expression.switchCase(sel, Expression.color(DARK_PURPLE), Expression.color(primary));      // v2.0.75: Expression.color, not literal(int) (a plain number drew black dots)
        st.addLayer(new CircleLayer("places-dots-halo", "places-src").withProperties(
                PropertyFactory.circleRadius(Expression.switchCase(sel, Expression.literal(15f * BIG), Expression.literal(10f * BIG))),
                PropertyFactory.circleColor(Expression.switchCase(sel, Expression.color(0xFFC9C0EE), Expression.color(0xFFE2DCFC))), PropertyFactory.circleOpacity(1f))      // v2.0.90: solid tint, not 18 % opacity
                .withFilter(isDot));
        st.addLayer(new CircleLayer("places-dots", "places-src").withProperties(
                PropertyFactory.circleRadius(Expression.switchCase(sel, Expression.literal(8f * BIG), Expression.literal(5.5f * BIG))),
                PropertyFactory.circleColor(Expression.switchCase(sel, Expression.color(DARK_PURPLE), isViewed, Expression.color(0xFFAFA8D8), Expression.color(primary))),
                PropertyFactory.circleStrokeWidth(2.5f), PropertyFactory.circleStrokeColor(0xFFFFFFFF))      // v2.0.86: a place already opened is a solid muted colour (it was 60 % opacity: the map showed through)
                .withFilter(isDot));
        st.addLayer(new SymbolLayer("places-clusters", "places-src").withProperties(
                PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true))
                .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("cluster"))));
        st.addLayer(new SymbolLayer("places", "places-src").withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(Expression.switchCase(sel, Expression.literal(PIN_SEL_SCALE), Expression.literal(1f))))
                .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("pin"))));
        for (String[] l : new String[][]{{"college-labels", "college-labels-src"}, {"place-labels", "labels-src"}})       // v2.0.90: house names sit above college names and block them (they used to overlap)
            st.addLayer(new SymbolLayer(l[0], l[1]).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAnchor(Property.ICON_ANCHOR_TOP),
                    PropertyFactory.iconOffset(new Float[]{0f, 2f}), PropertyFactory.iconAllowOverlap(!l[0].equals("college-labels")), PropertyFactory.iconIgnorePlacement(false), PropertyFactory.iconPadding(l[0].equals("college-labels") ? 6f : 2f)));
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

    /** v2.0.90: a solid shield (flat top, pointed bottom) centred on (cx, cy), {@code r} = half its width. Same outline as ic_shield_verified. */
    private static void shield(Canvas c, Paint p, float cx, float cy, float r, int color) {
        float h = r * 1.1f;
        Path sh = new Path();
        sh.moveTo(cx, cy - h);
        sh.lineTo(cx - r, cy - h * 0.62f);
        sh.lineTo(cx - r, cy + h * 0.05f);
        sh.cubicTo(cx - r, cy + h * 0.62f, cx - r * 0.55f, cy + h * 0.88f, cx, cy + h);
        sh.cubicTo(cx + r * 0.55f, cy + h * 0.88f, cx + r, cy + h * 0.62f, cx + r, cy + h * 0.05f);
        sh.lineTo(cx + r, cy - h * 0.62f);
        sh.close();
        p.setStyle(Paint.Style.FILL); p.setColor(color);
        c.drawPath(sh, p);
    }

    /** v2.0.80: price pin = white pill (thin border, ink text) with a small tail; the selected one is dark indigo with white text.
     *  A verified house gets a check badge in front, a featured house a star behind, in the same bitmap.
     *  v2.0.81: a saved house gets a small red heart on the top-right corner, a verified owner a small check badge on the top-left corner (v2.0.88: it replaced the green "New" dot and the check inside the pill); v2.0.89: an owner who is not verified yet gets a grey "!" badge there. */
    private Bitmap pricePin(String text, boolean selected, boolean verified, boolean featured, boolean saved, boolean fresh, boolean seen) {
        float d = getResources().getDisplayMetrics().density;
        Paint t = textPaint(selected ? 0xFFFFFFFF : seen ? 0xFF4A4568 : 0xFF17142A, 12, true);       // v2.0.86: an opened place is a solid muted pill, not a see-through one
        float badge = 14 * d, gap = 4 * d, padX = 9 * d;
        float tw = t.measureText(text);
        float w = padX * 2 + tw + (featured ? badge + gap : 0);
        float bodyH = 28 * d, tail = 6 * d, mx = 8 * d, mt = 13 * d, mb = 1.5f * d;      // v2.0.90: more room above / left so the shield badge is not cut
        int bw = Math.round(w + 2 * mx), bh = Math.round(mt + bodyH + tail + mb);
        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        RectF body = new RectF(mx, mt, bw - mx, mt + bodyH);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int fill = selected ? DARK_PURPLE : 0xFFFFFFFF, edge = selected ? 0xFFFFFFFF : seen ? 0xFF8F89B8 : 0xFFC9C4E6;      // v2.0.90: solid edge (it was 25 % opaque, so the pin looked see-through on the map)
        Path tailPath = new Path();
        tailPath.moveTo(bw / 2f - 5 * d, body.bottom - d);
        tailPath.lineTo(bw / 2f, body.bottom + tail);
        tailPath.lineTo(bw / 2f + 5 * d, body.bottom - d);
        tailPath.close();
        p.setColor(fill); p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(body, bodyH / 2f, bodyH / 2f, p);
        c.drawPath(tailPath, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d); p.setColor(edge);
        c.drawRoundRect(body, bodyH / 2f, bodyH / 2f, p);
        p.setStyle(Paint.Style.FILL); p.setColor(fill);
        c.drawRect(bw / 2f - 4 * d, body.bottom - 2 * d, bw / 2f + 4 * d, body.bottom + 0.5f * d, p);     // hides the border where the tail joins
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d); p.setColor(edge); p.setStrokeJoin(Paint.Join.ROUND);
        Path sides = new Path();
        sides.moveTo(bw / 2f - 4.6f * d, body.bottom + 0.4f * d);
        sides.lineTo(bw / 2f, body.bottom + tail - 0.5f * d);
        sides.lineTo(bw / 2f + 4.6f * d, body.bottom + 0.4f * d);
        c.drawPath(sides, p);
        float x = mx + padX, cy = body.centerY();
        int primary = ContextCompat.getColor(getContext(), R.color.bofo_primary);
        c.drawText(text, x + tw / 2f, cy - (t.ascent() + t.descent()) / 2f, t);
        x += tw;
        if (featured) {
            x += gap;
            p.setStyle(Paint.Style.FILL); p.setColor(ContextCompat.getColor(getContext(), R.color.bofo_amber));
            Path star = new Path();
            float R = badge / 2f, r = R * 0.45f, sx = x + R, sy = cy + 0.5f * d;
            for (int k = 0; k < 10; k++) {
                double ang = -Math.PI / 2 + k * Math.PI / 5;
                float rad = k % 2 == 0 ? R : r;
                float px = sx + (float) Math.cos(ang) * rad, py = sy + (float) Math.sin(ang) * rad;
                if (k == 0) star.moveTo(px, py); else star.lineTo(px, py);
            }
            star.close();
            c.drawPath(star, p);
        }
        {                                                                    // v2.0.90: shield badge, top-left corner: green + check = verified owner, grey + "!" = not verified yet
            float dx = body.left + 6 * d, dy = body.top + 1 * d, rr = 8.5f * d;
            shield(c, p, dx, dy, rr + 1.6f * d, 0xFFFFFFFF);                 // white ring
            shield(c, p, dx, dy, rr, verified ? 0xFF1E8E5E : 0xFF8F8CA3);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f * d); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(0xFFFFFFFF);
            if (verified) {
                Path ck = new Path();
                ck.moveTo(dx - rr * 0.36f, dy - rr * 0.02f);
                ck.lineTo(dx - rr * 0.08f, dy + rr * 0.28f);
                ck.lineTo(dx + rr * 0.40f, dy - rr * 0.30f);
                c.drawPath(ck, p);
            } else {
                c.drawLine(dx, dy - rr * 0.46f, dx, dy + rr * 0.10f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(dx, dy + rr * 0.44f, 1.1f * d, p);
            }
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.BUTT);
        }
        if (saved) {                                                         // heart, top-right corner
            float s2 = 11 * d, hx = body.right - 4 * d, hy = body.top + 1.5f * d;
            Path h = new Path();
            h.moveTo(hx, hy + s2 * 0.5f);
            h.cubicTo(hx - s2 * 0.95f, hy - s2 * 0.05f, hx - s2 * 0.5f, hy - s2 * 0.8f, hx, hy - s2 * 0.25f);
            h.cubicTo(hx + s2 * 0.5f, hy - s2 * 0.8f, hx + s2 * 0.95f, hy - s2 * 0.05f, hx, hy + s2 * 0.5f);
            h.close();
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.4f * d); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(0xFFFFFFFF);
            c.drawPath(h, p);
            p.setStyle(Paint.Style.FILL); p.setColor(GLOW);
            c.drawPath(h, p);
        }
        return bmp;
    }

    // ---- data in -----------------------------------------------------------------------------------------------

    public void setPlaces(List<BoardingHouse> list) {
        List<BoardingHouse> next = list == null ? new ArrayList<>() : new ArrayList<>(list);
        if (next.equals(places)) return;                     // the list screen reloads often with the same houses: nothing to redraw
        places = next;
        placesVer++;
        if (selectedId != null && findPlace(selectedId) == null) { selectedId = null; b.peek.setVisibility(View.GONE); }
        if (style != null) { rebuildPlaces(true); rebuildStrip(true); }
    }

    /** The radius ring (null centre or km <= 0 removes it). */
    public void setRing(Double lat, Double lng, double km) { ringLat = lat; ringLng = lng; ringKm = km; paintRing(); }

    /** v2.0.73: while a path is shown the map is decluttered: no radius circle, no campus dot, no campus dots / names, no other houses. */
    private boolean routeShown() { return routePts != null; }

    private void paintRing() {
        if (style == null) return;
        set("radius-src", routeShown() ? MapGeo.EMPTY : MapGeo.circleJson(ringLat, ringLng, ringKm, 64));
        paintRingLabel();
    }

    private void paintCampusDot() {
        if (style == null) return;
        set("campus-src", routeShown() ? MapGeo.EMPTY : MapGeo.pointJson(campusLat, campusLng));
    }

    /** A new / cleared path: redraw everything that hides while a path is shown. */
    private void declutter() {
        if (style == null) return;
        paintRing(); paintCampusDot();
        rebuildPlaces(false); rebuildColleges(false);      // their signatures include the path, so only a real change redraws
    }

    /** True for the house the path leads to: the selected one, or the one at the path's end point. */
    private boolean isRouteTarget(BoardingHouse p) {
        if (p.id().equals(selectedId)) return true;
        if (routePts == null) return true;
        double[] e = routePts[routePts.length - 1];
        float[] r = new float[1];
        Location.distanceBetween(e[0], e[1], p.lat(), p.lng(), r);
        return r[0] < 60;
    }

    /** The campus dot (null removes it). */
    public void setCampusDot(Double lat, Double lng) { campusLat = lat; campusLng = lng; paintCampusDot(); }

    /** The live "you are here" dot (null removes it). The first fix grows in from 82 %; the direction of travel comes from the last two fixes. */
    public void setUser(Double lat, Double lng) {
        boolean first = userLat == null && lat != null;
        if (lat != null && userLat != null) {
            float[] r = new float[3];
            Location.distanceBetween(userLat, userLng, lat, lng, r);
            if (r[0] > 8) heading = (double) ((r[1] % 360 + 360) % 360);
        } else if (lat == null) heading = null;
        userLat = lat; userLng = lng;
        if (style != null) { set("user-src", MapGeo.pointJson(userLat, userLng)); set("cone-src", coneJson()); rebuildPlaces(false); }   // houses near the dot slide clear of it (only redone when the phone moved ~10 m)
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
        paintCampusChip();
    }

    /** v2.0.78: while the phone is being found a soft ring grows out of the round button and fades, and the button breathes a little. Nothing is scaled past a parent's edge any more (that clipped the round button into a rounded square). */
    private void startLocatePulse() {
        if (locatePulse != null || !animationsOn()) return;
        b.locateRing.setVisibility(View.VISIBLE);
        locatePulse = ValueAnimator.ofFloat(0f, 1f);
        locatePulse.setDuration(1500);
        locatePulse.setRepeatCount(ValueAnimator.INFINITE);
        locatePulse.setInterpolator(new android.view.animation.DecelerateInterpolator(1.3f));
        locatePulse.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float ring = 1f + 0.4f * t;                                   // 44 dp -> ~62 dp: stays inside the 10 dp margin of the card
            b.locateRing.setScaleX(ring); b.locateRing.setScaleY(ring);
            b.locateRing.setAlpha((1f - t) * (1f - t));
            float breath = 1f + 0.05f * (float) Math.sin(Math.PI * t);
            b.locate.setScaleX(breath); b.locate.setScaleY(breath);
        });
        locatePulse.start();
    }

    private void stopLocatePulse() {
        if (locatePulse == null) return;
        locatePulse.cancel(); locatePulse = null;
        b.locateRing.setVisibility(View.INVISIBLE); b.locateRing.setAlpha(0f);
        // found: a small pop that settles with a little overshoot instead of snapping back
        b.locate.animate().cancel();
        b.locate.animate().scaleX(1.16f).scaleY(1.16f).setDuration(110).setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> b.locate.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new android.view.animation.OvershootInterpolator(3.2f)).start()).start();
    }

    /** v2.0.78: press feedback (shrinks under the finger, springs back with overshoot). Returns false so the click still fires. */
    private void setupLocatePress() {
        b.locate.setOnTouchListener((v, e) -> {
            if (locatePulse != null) return false;
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new android.view.animation.OvershootInterpolator(3.2f)).start();
            return false;
        });
    }

    private Boolean locateOn;
    private ValueAnimator locateTint;

    /** Step 5: purple locate button while the overview is on. v2.0.78: the white and purple backgrounds cross-fade and the icon colour fades with them (it used to swap in one frame). */
    public void setLocateActive(boolean on) {
        if (locateOn != null && locateOn == on) return;
        boolean first = locateOn == null;
        locateOn = on;
        int white = 0xFFFFFFFF, own = 0xFF5B3BF0;
        android.graphics.drawable.Drawable from = ContextCompat.getDrawable(getContext(), on ? R.drawable.bg_map_button : R.drawable.bg_map_button_on);
        android.graphics.drawable.Drawable to = ContextCompat.getDrawable(getContext(), on ? R.drawable.bg_map_button_on : R.drawable.bg_map_button);
        if (locateTint != null) locateTint.cancel();
        if (first || from == null || to == null || !animationsOn()) {
            b.locate.setBackgroundResource(on ? R.drawable.bg_map_button_on : R.drawable.bg_map_button);
            if (on) b.locate.setColorFilter(white); else b.locate.clearColorFilter();
            return;
        }
        android.graphics.drawable.TransitionDrawable td = new android.graphics.drawable.TransitionDrawable(new android.graphics.drawable.Drawable[]{from, to});
        b.locate.setBackground(td);
        td.startTransition(200);
        final int c0 = on ? own : white, c1 = on ? white : own;
        locateTint = ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), c0, c1);
        locateTint.setDuration(200);
        locateTint.addUpdateListener(a -> b.locate.setColorFilter((int) a.getAnimatedValue()));
        locateTint.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) { if (!on) b.locate.clearColorFilter(); }
        });
        locateTint.start();
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

    private boolean animationsOn() { return animationsEnabled(getContext()); }

    /** The phone's "animations off" setting (also used by the Explore compare tray). */
    static boolean animationsEnabled(android.content.Context c) {
        try { return android.provider.Settings.Global.getFloat(c.getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f; }
        catch (Exception e) { return true; }
    }

    /** The ripples live in RadarOverlayView (plain Canvas): the map itself is idle between gestures. */
    private void startRipples() {
        if (radar == null || userLat == null || owner == null || !isAttachedToWindow()) return;
        radar.start(animationsOn());
        refreshRadar();
    }

    private volatile boolean camMoving;
    private final Runnable idleRebuild = () -> { if (!camMoving) { rebuildPlaces(false); rebuildColleges(false); rebuildStrip(false); } };      // false = skip when nothing but the position changed

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
        declutter();
    }

    public void clearRoute() { boolean was = routeShown(); routePts = null; routeLabel = null; fitRoutePending = false; paintRoute(); paintRouteLabel(); if (was) declutter(); }

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
        if (stripOn) {                                           // v2.0.81: Explore shows the swipeable strip instead of the single card
            b.peek.setVisibility(View.GONE);
            syncStrip(selectedId);
            if (style != null) rebuildPlaces(true);
            return;
        }
        fillCard(p, b.peekName, b.peekPrice, b.peekInfo, b.peekPhoto);
        boolean wasShown = b.peek.getVisibility() == View.VISIBLE;
        b.peek.setVisibility(View.VISIBLE);
        if (!wasShown && animationsOn()) {       // v2.0.79: slides up and fades in; moving to another pin just swaps the text
            float dp = getResources().getDisplayMetrics().density;
            b.peek.animate().cancel();
            b.peek.setAlpha(0f); b.peek.setTranslationY(20 * dp);
            b.peek.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        } else { b.peek.animate().cancel(); b.peek.setAlpha(1f); b.peek.setTranslationY(0f); }
        if (style != null) rebuildPlaces(true);
    }

    /** Name, rent, amber rating + distance and photo of one house card (the peek card and the strip cards share it). */
    private void fillCard(BoardingHouse p, android.widget.TextView name, android.widget.TextView price, android.widget.TextView infoView, android.widget.ImageView photoView) {
        name.setText(p.name());
        price.setText(Money.perMonth(p.price()));
        android.text.SpannableStringBuilder info = new android.text.SpannableStringBuilder("\u2605 ").append(ListRules.ratingText(p.avgRating(), p.reviewCount()));
        info.setSpan(new android.text.style.ForegroundColorSpan(0xFFF5A524), 0, 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);     // v2.0.79: amber star
        if (p.distanceKm() > 0) info.append("  \u00B7  ").append(Geo.fmt(p.distanceKm()));
        String gp = com.bofo.finder.util.GenderRules.normalize(p.genderPolicy());                          // v2.0.87: who can stay
        if (!gp.isEmpty()) info.append("  \u00B7  ").append(infoView.getContext().getString(com.bofo.finder.util.GenderRules.labelRes(gp)));
        infoView.setText(info);
        String photo = p.photoUrl();
        if (photo == null || photo.isEmpty()) { Glide.with(photoView).clear(photoView); photoView.setVisibility(View.GONE); }
        else { photoView.setVisibility(View.VISIBLE); Glide.with(photoView).load(photo).centerCrop().into(photoView); }      // the house icon tile shows underneath until it loads
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
        paintRing(); paintCampusDot();
        rebuildColleges(); paintGlow(); paintRouteLabel();
        rebuildStrip(true);
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
        String sig = placesVer + "|" + selectedId + "|" + Math.round(map.getCameraPosition().zoom * 20) + "|" + getWidth() + "x" + getHeight()
                + "|" + (routeShown() ? "r" + routePts.length + "," + Math.round(routePts[routePts.length - 1][0] * 10000) : "-") + "|" + (userLat == null ? "-" : Math.round(userLat * 10000) + "," + Math.round(userLng * 10000)) + "|" + pinStateVer;       // v2.0.72: a pin that was moved off the phone's dot follows the phone
        if (!force && sig.equals(placesSig)) return;
        placesSig = sig;
        float d = getResources().getDisplayMetrics().density;
        int n = places.size();
        PointF[] sp = new PointF[n];
        for (int i = 0; i < n; i++) {
            BoardingHouse p = places.get(i);
            if (!Double.isNaN(p.lat()) && !Double.isNaN(p.lng()) && (!routeShown() || isRouteTarget(p))) sp[i] = map.getProjection().toScreenLocation(new LatLng(p.lat(), p.lng()));   // v2.0.73: only the house the path leads to stays while a path is shown
        }
        // v2.0.72: a house under the phone's dot (or its ripples) is moved just clear of it on screen, so the two circles never sit on top of each other
        final boolean pinMode = map.getCameraPosition().zoom >= PIN_ZOOM;
        final float clr = (pinMode ? 22 : 30) * d;
        final double[][] pos = new double[n][];
        for (int i = 0; i < n; i++) {
            if (sp[i] == null) continue;
            BoardingHouse p = places.get(i);
            pos[i] = clearOfUser(p.lat(), p.lng(), clr);
            if (pos[i][0] != p.lat() || pos[i][1] != p.lng()) sp[i] = map.getProjection().toScreenLocation(new LatLng(pos[i][0], pos[i][1]));
        }
        StringBuilder feats = new StringBuilder(), labels = new StringBuilder();
        if (pinMode) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < n; i++) if (sp[i] != null) { if (places.get(i).id().equals(selectedId)) order.add(0, i); else order.add(i); }
            List<RectF> bodies = new ArrayList<>(), taken = new ArrayList<>();
            final String[] pinKey = new String[n];
            for (int i : order) {                                     // v2.0.80: one price-pin bitmap per (price text, selected, verified, featured), made once and reused
                BoardingHouse p = places.get(i);
                boolean isSel = p.id().equals(selectedId);
                String price = Money.shortPeso(p.price());
                if (price.isEmpty()) price = "\u2014";
                boolean saved = favs.contains(p.id()), fresh = p.reviewCount() <= 0, seen = !isSel && viewed.contains(p.id());      // "New" = nobody rated it yet, the same rule as the list cards (ListRules.ratingText)
                String key = "pp|" + price + "|" + (isSel ? 1 : 0) + (p.ownerVerified() ? 1 : 0) + (p.featured() ? 1 : 0) + (saved ? 1 : 0) + (fresh ? 1 : 0) + (seen ? 1 : 0);
                if (imgs.add(key)) {
                    Bitmap pb = pricePin(price, isSel, p.ownerVerified(), p.featured(), saved, fresh, seen);
                    imgSize.put(key, new float[]{pb.getWidth(), pb.getHeight()});
                    style.addImage(key, pb);
                }
                pinKey[i] = key;
                float[] pw = imgSize.get(key);
                float sc = isSel ? PIN_SEL_SCALE : 1f, w2 = (pw == null ? 52 * d : pw[0]) * sc / 2f, h2 = (pw == null ? 36 * d : pw[1]) * sc;
                bodies.add(new RectF(sp[i].x - w2, sp[i].y - h2, sp[i].x + w2, sp[i].y));
            }
            for (int k = 0; k < order.size(); k++) {
                int i = order.get(k);
                BoardingHouse p = places.get(i);
                boolean isSel = p.id().equals(selectedId);
                if (feats.length() > 0) feats.append(',');
                feats.append(feat(pos[i][0], pos[i][1], "\"kind\":\"pin\",\"id\":\"" + esc(p.id()) + "\",\"icon\":\"" + esc(pinKey[i]) + "\",\"sel\":" + (isSel ? 1 : 0) + ",\"viewed\":" + (!isSel && viewed.contains(p.id()) ? 1 : 0)));
                String text = clip(p.name(), 60);
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
                labels.append(feat(pos[i][0], pos[i][1], "\"icon\":\"" + esc(key) + "\""));
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
                    feats.append(feat(pos[i][0], pos[i][1], "\"kind\":\"dot\",\"id\":\"" + esc(p.id()) + "\",\"sel\":" + (p.id().equals(selectedId) ? 1 : 0) + ",\"viewed\":" + (!p.id().equals(selectedId) && viewed.contains(p.id()) ? 1 : 0)));
                } else {
                    double la = 0, ln = 0, bn = -90, bs = 90, be = -180, bw = 180, low = Double.MAX_VALUE, high = 0;
                    for (int j : m) {
                        BoardingHouse p = places.get(j);
                        if (p.price() > 0) { low = Math.min(low, p.price()); high = Math.max(high, p.price()); }
                        la += p.lat(); ln += p.lng();
                        bn = Math.max(bn, p.lat()); bs = Math.min(bs, p.lat()); be = Math.max(be, p.lng()); bw = Math.min(bw, p.lng());
                    }
                    String from = low == Double.MAX_VALUE ? "" : high > low ? Money.shortPeso(low) + "\u2013" + Money.shortPeso(high) : Money.shortPeso(low);        // v2.0.90: a range "4 · ₱1.2k–₱2k" (one price when they are all the same)
                    String key = "cl|" + m.size() + "|" + from;
                    if (imgs.add(key)) style.addImage(key, bubble(m.size(), from));
                    double[] cpos = clearOfUser(la / m.size(), ln / m.size(), 34 * d);
                    feats.append(feat(cpos[0], cpos[1], String.format(Locale.US,
                            "\"kind\":\"cluster\",\"icon\":\"%s\",\"bn\":%.6f,\"bs\":%.6f,\"be\":%.6f,\"bw\":%.6f", key, bn, bs, be, bw)));
                }
            }
        }
        set("places-src", collection(feats));
        set("labels-src", collection(labels));
    }

    /** {lat, lng} of a point that is at least {@code clearPx} screen pixels away from the phone's dot: nearer points slide straight out to that distance (straight down when exactly on it). */
    private double[] clearOfUser(double lat, double lng, float clearPx) {
        if (userLat == null || map == null || Double.isNaN(lat) || Double.isNaN(lng)) return new double[]{lat, lng};
        PointF u = map.getProjection().toScreenLocation(new LatLng(userLat, userLng));
        PointF q = map.getProjection().toScreenLocation(new LatLng(lat, lng));
        double dx = q.x - u.x, dy = q.y - u.y, dist = Math.hypot(dx, dy);
        if (dist >= clearPx) return new double[]{lat, lng};
        if (dist < 1) { dx = 0; dy = 1; dist = 1; }
        LatLng ll = map.getProjection().fromScreenLocation(new PointF((float) (u.x + dx / dist * clearPx), (float) (u.y + dy / dist * clearPx)));
        return new double[]{ll.getLatitude(), ll.getLongitude()};
    }

    private void rebuildColleges() { rebuildColleges(true); }

    private void rebuildColleges(boolean force) {
        if (style == null || map == null) return;
        boolean showNames = map.getCameraPosition().zoom >= 11f;
        String sig = collegesVer + "|" + showNames + "|" + routeShown();
        if (!force && sig.equals(collegesSig)) return;      // the campus list is the same and the names are still on / off: nothing to send
        collegesSig = sig;
        StringBuilder dots = new StringBuilder(), names = new StringBuilder();
        for (College c : routeShown() ? new ArrayList<College>() : colleges) {      // no campus dots / names while a path is shown
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
        if (ringLat == null || ringKm <= 0 || routeShown()) { set("ringlabel-src", MapGeo.EMPTY); return; }
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

    /** Numbered purple bubble for merged dots; with a price it is a pill "4 · from ₱1.2k" (v2.0.80). */
    private Bitmap bubble(int count, String from) {
        float d = getResources().getDisplayMetrics().density;
        if (!from.isEmpty()) {
            String text = count + " \u00B7 " + from;
            Paint t = textPaint(0xFFFFFFFF, 13, true);
            int w = Math.round(t.measureText(text) + 26 * d), h = Math.round(34 * d);
            Bitmap pb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas pc = new Canvas(pb);
            Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
            pp.setColor(0xFFFFFFFF);
            pc.drawRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, pp);
            pp.setColor(ContextCompat.getColor(getContext(), R.color.bofo_primary));
            pc.drawRoundRect(new RectF(2.5f * d, 2.5f * d, w - 2.5f * d, h - 2.5f * d), h / 2f, h / 2f, pp);
            pc.drawText(text, w / 2f, h / 2f - (t.ascent() + t.descent()) / 2f, t);
            return pb;
        }
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

    // ---- v2.0.80: "Search this area", campus chip, locate button position ---------------------------------------

    private boolean searchAreaOn, gestureMove, areaShown, holdArea;
    private Set<String> viewed = new HashSet<>(), favs = new HashSet<>();      // v2.0.81 pin states
    private int pinStateVer;
    private double[] areaBase;                  // {south, west, north, east, zoom} the list was last matched to
    private String campusChipName;

    /** Explore only: after the person pans / zooms a glass pill "Search this area" slides in; the Listener gets the visible box when it is tapped. */
    public void setSearchAreaEnabled(boolean on) { searchAreaOn = on; if (!on) showAreaPill(false); }

    /** The visible box {south, west, north, east, zoom} (rotation and tilt are off, so it is a plain rectangle), or null before the map has a size. */
    @Nullable private double[] viewBox() {
        if (map == null || mapView == null || mapView.getWidth() == 0 || mapView.getHeight() == 0) return null;
        try {
            LatLng tl = map.getProjection().fromScreenLocation(new PointF(0, 0));
            LatLng br = map.getProjection().fromScreenLocation(new PointF(mapView.getWidth(), mapView.getHeight()));
            return new double[]{br.getLatitude(), tl.getLongitude(), tl.getLatitude(), br.getLongitude(), map.getCameraPosition().zoom};
        } catch (Throwable t) { return null; }
    }

    /** Camera idle: a move by code (fit / locate / campus chip) is the new baseline; a finger move that went further than a third of the map or 1 zoom level shows the pill. */
    private void checkArea() {
        if (!searchAreaOn) return;
        double[] now = viewBox();
        if (now == null) return;
        if (routeShown()) { gestureMove = false; showAreaPill(false); return; }       // never while a path is shown
        if (gestureMove) {                                                            // the finger (or a cluster tap) moved the map: has it left the area the list was made for?
            gestureMove = false;
            if (areaBase == null) { areaBase = now; return; }
            double w = areaBase[3] - areaBase[1], h = areaBase[2] - areaBase[0];
            double dx = Math.abs((now[1] + now[3]) / 2 - (areaBase[1] + areaBase[3]) / 2), dy = Math.abs((now[0] + now[2]) / 2 - (areaBase[0] + areaBase[2]) / 2);
            showAreaPill(dx > w / 3 || dy > h / 3 || Math.abs(now[4] - areaBase[4]) >= 1);
            return;
        }
        if (holdArea) { holdArea = false; return; }                                   // a pin tap easing the camera does not hide or show anything
        areaBase = now;                                                               // moved by code (fit, locate, campus chip): this view is the new baseline
        showAreaPill(false);
    }

    private void showAreaPill(boolean show) {
        if (show == areaShown) return;
        areaShown = show;
        final View v = b.searchArea;
        v.animate().cancel();
        if (!animationsOn()) { v.setVisibility(show ? View.VISIBLE : View.GONE); v.setAlpha(1f); v.setTranslationY(0f); return; }
        float off = -16 * getResources().getDisplayMetrics().density;
        if (show) {
            v.setVisibility(View.VISIBLE); v.setAlpha(0f); v.setTranslationY(off);
            v.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        } else {
            v.animate().alpha(0f).translationY(off).setDuration(160).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> { if (!areaShown) v.setVisibility(View.GONE); }).start();
        }
    }

    /** "Near <campus>" chip, top-left (null hides it). Tapping it recentres the map on the campus. It steps aside while the location pill is showing. */
    public void setCampusChip(@Nullable String campusName) {
        campusChipName = campusName == null || campusName.trim().isEmpty() ? null : campusName.trim();
        if (campusChipName != null) b.nearCampus.setText(getContext().getString(R.string.map_near_campus, campusChipName));
        paintCampusChip();
    }

    private void paintCampusChip() { b.nearCampus.setVisibility(campusChipName != null && b.locPill.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE); }

    /** Lifts the round locate button (and the expand button above it) by this many dp, so a floating button of another screen never covers it. */
    public void setLocateLift(int dp) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) b.locateCol.getLayoutParams();
        int m = Math.round(10 * getResources().getDisplayMetrics().density);
        lp.setMargins(m, m, m, m + Math.round(dp * getResources().getDisplayMetrics().density));
        b.locateCol.setLayoutParams(lp);
    }

    // ---- v2.0.81: pin states + card strip ------------------------------------------------------------------------

    /** Saved houses get a small heart on the pin. */
    public void setFavorites(@Nullable Collection<String> ids) {
        Set<String> next = ids == null ? new HashSet<>() : new HashSet<>(ids);
        if (next.equals(favs)) return;
        favs = next; pinStateVer++;
        if (style != null) rebuildPlaces(true);
    }

    /** Pins of places the person already opened are dimmed (the list lives in AppPrefs). */
    private void refreshViewed() {
        Set<String> next = new HashSet<>(AppPrefs.viewedIds(getContext()));
        if (next.equals(viewed)) return;
        viewed = next; pinStateVer++;
        if (style != null) rebuildPlaces(true);
    }

    private static final int STRIP_MAX = 20;
    private boolean stripOn, stripShown;
    private final List<BoardingHouse> stripItems = new ArrayList<>();
    private StripAdapter stripAdapter;
    private LinearLayoutManager stripLm;
    private PagerSnapHelper stripSnap;
    private long stripLockUntil;
    private String stripSig = "";
    private final Set<String> compareIds = new HashSet<>();
    private int stripStroke0 = -1;
    /** v2.0.83: Commute. commuteBusyId = the chip says "Finding..."; commuteId = a route to the campus is drawn and the strip holds only that house's card. */
    private String commuteId, commuteBusyId, commuteLine;
    private boolean stripLocked;
    private CameraPosition beforeCommute;

    /** v2.0.84: only the cards on screen are re-bound (the others bind from the current state when they scroll in). */
    private void refreshStripRows() {
        if (stripAdapter == null || stripLm == null || stripItems.isEmpty()) return;
        int a = stripLm.findFirstVisibleItemPosition(), z = stripLm.findLastVisibleItemPosition();
        if (a < 0 || z < a) { stripAdapter.notifyItemRangeChanged(0, Math.min(stripItems.size(), 3)); return; }
        stripAdapter.notifyItemRangeChanged(a, z - a + 1);
    }

    /** v2.0.83: this house's Commute chip shows "Finding..." while the route is worked out (null = none). */
    public void setCommuteBusy(@Nullable String id) { commuteBusyId = id; refreshStripRows(); }

    public boolean commuteShown() { return commuteId != null; }

    /**
     * v2.0.83: draws the route from this house to the campus, puts the minutes and mode on the house's card, keeps only that
     * card in the strip and fits the camera to the path (the v2.0.74 rule: a shown path keeps the camera).
     */
    public void showCommute(String id, double[][] path, boolean approximate, String line, @Nullable String badge) {
        BoardingHouse p = id == null ? null : findPlace(id);
        commuteBusyId = null;
        if (p == null || path == null || path.length < 2 || stripAdapter == null) { refreshStripRows(); return; }
        if (map != null && commuteId == null) beforeCommute = map.getCameraPosition();       // clearCommute() eases back to it
        commuteId = id; commuteLine = line; stripLocked = true;
        stripItems.clear(); stripItems.add(p); stripSig = "";
        stripAdapter.notifyDataSetChanged();
        stripLm.scrollToPositionWithOffset(0, 0);
        paintStripVisible();
        select(id);
        setRoute(path, approximate);
        setRouteLabel(badge);
        fitRoute();
    }

    /** v2.0.83: removes the route and the label, brings the strip back and eases the camera to where it was. */
    public void clearCommute() {
        boolean was = commuteId != null || stripLocked;
        commuteId = null; commuteBusyId = null; commuteLine = null; stripLocked = false;
        if (!was) return;
        clearRoute();
        if (map != null && beforeCommute != null) {
            try { map.easeCamera(CameraUpdateFactory.newCameraPosition(beforeCommute), animationsOn() ? 320 : 0); } catch (Throwable ignored) { }
        }
        beforeCommute = null;
        rebuildStrip(true);
        refreshStripRows();
    }

    /** v2.0.82: houses in the compare tray get a coloured outline on their strip card. */
    public void setCompareIds(Set<String> ids) {
        compareIds.clear();
        if (ids != null) compareIds.addAll(ids);
        refreshStripRows();
    }

    /** v2.0.82: the strip dips and comes back (180 ms) when a quick chip changed the houses. The pins just swap: no layer is animated. */
    public void softFade() {
        if (!stripOn || !animationsOn() || b.strip.getVisibility() != View.VISIBLE) return;
        b.strip.animate().cancel();
        b.strip.setAlpha(.35f);
        b.strip.animate().alpha(1f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void popCard(View v) {
        if (!animationsOn()) return;
        v.animate().cancel();
        v.animate().scaleX(.95f).scaleY(.95f).setDuration(90).setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start()).start();
    }

    /** Explore only: a horizontal strip of house cards (the houses in view, nearest the map centre first, at most 20) replaces the single peek card. */
    public void setStripEnabled(boolean on) {
        if (on == stripOn) return;
        stripOn = on;
        if (on) {
            if (stripAdapter == null) setupStrip();
            b.peek.setVisibility(View.GONE);
            refreshViewed();
            rebuildStrip(true);
        } else { b.strip.setVisibility(View.GONE); stripShown = false; }
    }

    private void setupStrip() {
        stripLm = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        stripAdapter = new StripAdapter();
        b.strip.setLayoutManager(stripLm);
        b.strip.setAdapter(stripAdapter);
        b.strip.setItemAnimator(null);
        stripSnap = new PagerSnapHelper();
        stripSnap.attachToRecyclerView(b.strip);
        b.strip.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                View v = stripSnap.findSnapView(stripLm);
                int pos = v == null ? -1 : rv.getChildAdapterPosition(v);
                if (pos < 0 || pos >= stripItems.size()) return;
                BoardingHouse h = stripItems.get(pos);
                if (h.id().equals(selectedId)) return;                       // came from a pin tap or the strip was only re-centred
                holdArea = true;
                stripLockUntil = android.os.SystemClock.uptimeMillis() + 900;
                userSelect(h.id());
                if (map != null && !Double.isNaN(h.lat()) && !Double.isNaN(h.lng()))
                    map.easeCamera(CameraUpdateFactory.newLatLng(new LatLng(h.lat(), h.lng())), animationsOn() ? 320 : 0);
            }
        });
    }

    /** Fills the strip from the houses inside the visible box. force = false is skipped for a moment after a pin tap / swipe. */
    private void rebuildStrip(boolean force) {
        if (!stripOn || stripAdapter == null || map == null || style == null || stripLocked) return;     // v2.0.83: a commute holds the strip on one card
        if (!force && android.os.SystemClock.uptimeMillis() < stripLockUntil) return;
        double[] box = viewBox();
        LatLng c = map.getCameraPosition().target;
        if (box == null || c == null) return;
        final double cl = c.getLatitude(), cn = c.getLongitude(), k = Math.cos(Math.toRadians(cl));
        List<BoardingHouse> in = new ArrayList<>();
        for (BoardingHouse p : places)
            if (!Double.isNaN(p.lat()) && !Double.isNaN(p.lng()) && p.lat() >= box[0] && p.lat() <= box[2] && p.lng() >= box[1] && p.lng() <= box[3]) in.add(p);
        Collections.sort(in, (x, y) -> Double.compare(Math.hypot(x.lat() - cl, (x.lng() - cn) * k), Math.hypot(y.lat() - cl, (y.lng() - cn) * k)));
        if (in.size() > STRIP_MAX) in = new ArrayList<>(in.subList(0, STRIP_MAX));
        BoardingHouse sel = selectedId == null ? null : findPlace(selectedId);
        if (sel != null) { in.remove(sel); in.add(0, sel); if (in.size() > STRIP_MAX) in.remove(in.size() - 1); }       // the selected house always has its card
        StringBuilder sig = new StringBuilder();
        for (BoardingHouse p : in) sig.append(p.id()).append(',').append(p.price()).append(';');
        if (!force && sig.toString().equals(stripSig)) return;
        stripSig = sig.toString();
        stripItems.clear(); stripItems.addAll(in);
        stripAdapter.notifyDataSetChanged();
        stripLm.scrollToPositionWithOffset(0, 0);
        paintStripVisible();
    }

    private void paintStripVisible() {
        boolean show = !stripItems.isEmpty();
        if (show == stripShown) return;
        stripShown = show;
        b.strip.animate().cancel();
        if (!show) { b.strip.setVisibility(View.GONE); return; }
        b.strip.setVisibility(View.VISIBLE);
        if (animationsOn()) {                                   // first show: slides up and fades in (260 ms), like the peek card
            b.strip.setAlpha(0f); b.strip.setTranslationY(20 * getResources().getDisplayMetrics().density);
            b.strip.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        } else { b.strip.setAlpha(1f); b.strip.setTranslationY(0f); }
    }

    /** A pin was tapped (or select() was called from code): bring that house's card to the middle. */
    private void syncStrip(@Nullable String id) {
        if (!stripOn || stripAdapter == null || id == null || (stripLocked && !id.equals(commuteId))) return;
        int idx = -1;
        for (int i = 0; i < stripItems.size(); i++) if (stripItems.get(i).id().equals(id)) { idx = i; break; }
        if (idx < 0) {
            BoardingHouse p = findPlace(id);
            if (p == null) return;
            stripItems.add(0, p);
            if (stripItems.size() > STRIP_MAX) stripItems.remove(stripItems.size() - 1);
            stripAdapter.notifyDataSetChanged();
            stripLm.scrollToPositionWithOffset(0, 0);
            paintStripVisible();
            return;
        }
        View cur = stripSnap.findSnapView(stripLm);
        if (cur != null && b.strip.getChildAdapterPosition(cur) == idx) return;
        View target = stripLm.findViewByPosition(idx);
        if (target == null) { stripLm.scrollToPositionWithOffset(idx, 0); return; }
        int dx = (target.getLeft() + target.getRight()) / 2 - b.strip.getWidth() / 2;
        if (animationsOn()) b.strip.smoothScrollBy(dx, 0, new android.view.animation.DecelerateInterpolator(1.4f), 280); else b.strip.scrollBy(dx, 0);
    }

    private final class StripAdapter extends RecyclerView.Adapter<StripAdapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final ItemMapStripBinding v;
            VH(ItemMapStripBinding v) { super(v.getRoot()); this.v = v; }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int type) {
            ItemMapStripBinding v = ItemMapStripBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            float d = parent.getResources().getDisplayMetrics().density;
            int outer = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();       // one card fills the middle, the next / previous peek out of the padding
            if (outer <= 0) outer = MapCardView.this.getWidth() - Math.round(122 * d);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Math.max(1, outer - Math.round(12 * d)), RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.setMargins(Math.round(6 * d), Math.round(6 * d), Math.round(6 * d), Math.round(12 * d));       // v2.0.85: the strip clips its cards, so the shadow needs room
            v.getRoot().setLayoutParams(lp);
            v.stripPhoto.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline o) { o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 16 * view.getResources().getDisplayMetrics().density); }
            });
            v.stripPhoto.setClipToOutline(true);
            final VH h = new VH(v);
            if (stripStroke0 < 0) stripStroke0 = v.getRoot().getStrokeWidth();
            v.getRoot().setOnLongClickListener(x -> {
                int pos = h.getBindingAdapterPosition();
                if (pos < 0 || pos >= stripItems.size() || listener == null) return false;
                popCard(v.getRoot());
                listener.placeLongPressed(stripItems.get(pos).id());
                return true;
            });
            final View col = (View) v.stripCommute.getParent();                       // v2.0.84: 28 dp chip, 44 dp touch area
            col.post(() -> {
                android.graphics.Rect r = new android.graphics.Rect();
                v.stripCommute.getHitRect(r);
                int pad = Math.round(8 * d);
                r.top -= pad; r.bottom += pad; r.left -= pad; r.right += pad;
                col.setTouchDelegate(new android.view.TouchDelegate(r, v.stripCommute));
            });
            v.stripCommute.setOnClickListener(x -> {
                int pos = h.getBindingAdapterPosition();
                if (pos >= 0 && pos < stripItems.size() && listener != null && commuteBusyId == null) listener.commuteTapped(stripItems.get(pos).id());
            });
            v.stripOpen.setOnClickListener(x -> {
                int pos = h.getBindingAdapterPosition();
                if (pos >= 0 && pos < stripItems.size() && listener != null) listener.openPlace(stripItems.get(pos).id());
            });
            return h;
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            fillCard(stripItems.get(pos), h.v.stripName, h.v.stripPrice, h.v.stripInfo, h.v.stripPhoto);
            boolean inCompare = compareIds.contains(stripItems.get(pos).id());
            com.google.android.material.card.MaterialCardView card = h.v.getRoot();
            card.setStrokeWidth(inCompare ? Math.round(2 * getResources().getDisplayMetrics().density) : Math.max(0, stripStroke0));
            card.setStrokeColor(inCompare ? 0xFF5B3BF0 : 0xFFE7E2FB);
            String hid = stripItems.get(pos).id();                    // v2.0.83: Commute chip: "Commute" / "Finding..." / the result line
            boolean busy = hid.equals(commuteBusyId), shown = hid.equals(commuteId) && commuteLine != null;
            h.v.stripCommute.setText(busy ? getContext().getString(R.string.map_commute_busy) : shown ? commuteLine : getContext().getString(R.string.map_commute));
            h.v.stripCommute.setActivated(shown);
            h.v.stripCommute.setAlpha(busy ? .6f : 1f);
            h.v.stripCommute.setContentDescription(busy ? getContext().getString(R.string.map_commute_cd_busy) : shown ? getContext().getString(R.string.map_commute_cd_result, commuteLine) : getContext().getString(R.string.map_commute_cd));     // v2.0.84
        }

        @Override public int getItemCount() { return stripItems.size(); }
    }

    // ---- camera ------------------------------------------------------------------------------------------------

    /** Zoom to the ring if there is one, else to all pins, else to the default area. */
    public void fit() { if (routeShown()) { fitRoute(); return; } fitPending = true; target = null; post(this::applyCamera); }   // v2.0.74: a shown path keeps the camera

    /** Step 5 overview: zoom out so every pin (and the phone, if known) fills ~90 % of the card. */
    public void overview() { if (routeShown()) { fitRoute(); return; } overviewPending = true; fitPending = false; target = null; post(this::applyCamera); }   // v2.0.74: a shown path keeps the camera

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
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) { fitRoutePending = true; postDelayed(this::applyCamera, 150); return; }     // not laid out yet: try again
        float d = getResources().getDisplayMetrics().density;
        int bottom = Math.round(((b.peek.getVisibility() == View.VISIBLE ? Math.max(b.peek.getHeight() / d, 70) + 26 + 18 : 40)) * d);
        int top = Math.round((pillShown ? 84 : 44) * d), side = Math.min(Math.round(40 * d), Math.round(w * 0.2f));
        int maxV = Math.round(h * 0.7f);                                  // v2.0.74: padding must never eat the card (MapLibre then silently skips the move)
        if (top + bottom > maxV) { float k = maxV / (float) (top + bottom); top = Math.round(top * k); bottom = Math.round(bottom * k); }
        LatLngBounds lb = LatLngBounds.from(bb[2], bb[3], bb[0], bb[1]);
        try {
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(lb, side, top, side, bottom), 450);
        } catch (Exception e) {
            Log.w("BOFO", "Route fit", e);
            try { map.moveCamera(CameraUpdateFactory.newLatLngBounds(lb, Math.round(24 * d))); }
            catch (Exception e2) { map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng((bb[0] + bb[2]) / 2, (bb[1] + bb[3]) / 2), 15)); }
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

    /** v2.0.82: id of the house pin under this point (not clusters or campus dots), or null. */
    @Nullable private String placeIdAt(@NonNull LatLng point) {
        if (map == null) return null;
        PointF pt = map.getProjection().toScreenLocation(point);
        float d = getResources().getDisplayMetrics().density;
        RectF box = new RectF(pt.x - 30 * d, pt.y - 56 * d, pt.x + 30 * d, pt.y + 14 * d);
        for (Feature f : map.queryRenderedFeatures(box, "places", "places-dots")) {
            String kind = f.getStringProperty("kind"), id = f.getStringProperty("id");
            if (id != null && !"cluster".equals(kind) && !"college".equals(kind)) return id;
        }
        return null;
    }

    private boolean onLongTap(@NonNull LatLng point) {
        if (map == null || !tapsEnabled || listener == null) return false;
        String id = placeIdAt(point);
        if (id == null) return false;
        listener.placeLongPressed(id);
        return true;
    }

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
                if (commuteId != null && !id.equals(commuteId)) return true;        // v2.0.83: while a commute is drawn only its house can be picked
                holdArea = true;
                stripLockUntil = android.os.SystemClock.uptimeMillis() + 900;       // the camera easing to the pin must not re-sort the strip under the card
                userSelect(id);
                BoardingHouse p = findPlace(id);
                if (p != null) map.easeCamera(CameraUpdateFactory.newLatLng(new LatLng(p.lat(), p.lng())), 300);
                return true;
            }
        }
        if (selectedId != null && commuteId == null) userSelect(null);       // v2.0.83: a drawn commute keeps its house selected
        return false;
    }

    /** Tap on a numbered bubble: glide to a view of the whole group. */
    private void zoomToCluster(Feature f) {
        gestureMove = true;
        try {
            double n = f.getNumberProperty("bn").doubleValue(), sth = f.getNumberProperty("bs").doubleValue();
            double e = f.getNumberProperty("be").doubleValue(), w = f.getNumberProperty("bw").doubleValue();
            if (n == sth && e == w) map.easeCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(n, e), PIN_ZOOM + 1), 400);
            else map.easeCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(n, e, sth, w), Math.round(60 * getResources().getDisplayMetrics().density)), 400);
        } catch (Exception ex) { Log.w("BOFO", "Cluster zoom", ex); }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); if (radar != null) post(this::refreshRadar); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); startRipples(); }
    @Override protected void onDetachedFromWindow() { stopRipples(); removeCallbacks(attrCollapse); super.onDetachedFromWindow(); }

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
    @Override public void onResume(@NonNull LifecycleOwner o) { if (mapView != null) { mapView.onResume(); if (map != null && style != null) { if (!renderedFully) scheduleRefresh(); styleRetries = 0; scheduleBlankCheck(); } } startRipples(); applyRouteOpacity(); refreshViewed(); }   // picks up the Settings switch; pins of places opened meanwhile dim
    @Override public void onPause(@NonNull LifecycleOwner o) { if (mapView != null) mapView.onPause(); stopRipples(); }
    @Override public void onStop(@NonNull LifecycleOwner o) { if (mapView != null) mapView.onStop(); }

    @Override
    public void onDestroy(@NonNull LifecycleOwner o) {
        o.getLifecycle().removeObserver(this);
        saveCamera();
        removeCallbacks(refreshMap); removeCallbacks(blankCheck);
        stopRipples(); stopLocatePulse();
        if (pop != null) pop.cancel();
        if (found != null) found.cancel();
        if (radar != null) { radar.stop(); b.mapHost.removeView(radar); radar = null; }
        if (mapView != null) { mapView.onDestroy(); b.mapHost.removeView(mapView); }
        mapView = null; map = null; style = null; owner = null;
        placesSig = null; collegesSig = null;
    }

    // ---- v2.0.75: map credit as a small (i) button -----------------------------------------------------------------

    private boolean attrOpen;
    private final Runnable attrCollapse = () -> setAttribution(false);

    /** The licence text stays reachable: it shows for 4 s when the map appears, then folds into a round (i); tapping it opens the text for 5 s. */
    private void setupAttribution() {
        b.attribution.setOnClickListener(v -> {
            removeCallbacks(attrCollapse);
            setAttribution(!attrOpen);
            if (attrOpen) postDelayed(attrCollapse, 5000);
        });
        setAttribution(true);
        postDelayed(attrCollapse, 4000);
    }

    private void setAttribution(boolean open) {
        attrOpen = open;
        android.widget.TextView a = b.attribution;
        float d = getResources().getDisplayMetrics().density;
        android.transition.TransitionManager.beginDelayedTransition(this, new android.transition.AutoTransition().setDuration(160));
        if (open) {
            a.setText(R.string.map_attribution);
            a.setBackgroundResource(R.drawable.bg_attribution);
            a.setTypeface(Typeface.DEFAULT);
            a.setMinWidth(0); a.setMinHeight(0);
            a.setPadding(Math.round(8 * d), Math.round(4 * d), Math.round(8 * d), Math.round(4 * d));
        } else {
            a.setText("i");
            a.setBackgroundResource(R.drawable.bg_attribution_dot);
            a.setTypeface(Typeface.DEFAULT_BOLD);
            a.setGravity(android.view.Gravity.CENTER);
            a.setMinWidth(Math.round(22 * d)); a.setMinHeight(Math.round(22 * d));
            a.setPadding(0, 0, 0, 0);
        }
        a.setContentDescription(getContext().getString(R.string.map_attribution));
    }
}
