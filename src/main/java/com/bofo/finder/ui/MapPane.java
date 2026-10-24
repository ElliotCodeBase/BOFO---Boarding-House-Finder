package com.bofo.finder.ui;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Geo;
import com.bofo.finder.util.Locator;
import com.bofo.finder.util.Router;
import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Map built on Gluon Maps. The base map is BOFO's own style drawn from OpenFreeMap vector tiles
 * ({@link com.bofo.finder.util.VectorTileRetriever}), or the old picture tiles when map.renderer=raster. On top:
 * <ul>
 *   <li><b>You</b>: a blue dot that glides between GPS fixes, pulses, and shows a direction arrow once you move. The round
 *       button (bottom right) flies to you and keeps the camera following you until you drag the map.</li>
 *   <li><b>Pins</b>: purple pins that pop in. With {@link #enablePreview} a tap selects a pin, shows a small card at the
 *       bottom and draws the walking route to it (from the chosen college, else the nearest college, else from you).</li>
 *   <li><b>Route</b>: a real walking path (see {@link Router}) that draws itself, with distance and minutes on a badge.
 *       If the routing server cannot be reached the path is a dashed straight line.</li>
 *   <li><b>Campuses</b>: every college is always on the map as a small dot with its name.</li>
 *   <li>A college circle with a radius badge when asked for.</li>
 * </ul>
 * Attribution text comes from {@link AppConfig#mapAttribution()}.
 */
public final class MapPane extends StackPane {
    private static final Color PURPLE = Color.web("#5B3BF0"), PURPLE_DARK = Color.web("#3A22B8"), BLUE = Color.web("#2F6FED");
    private static final double CARD_SPACE = 104;                 // room the preview card takes at the bottom (px)

    private final MapView map = new MapView();
    private final PinLayer layer = new PinLayer();
    private final Button locate = new Button();
    private Consumer<BoardingHouse> onPin = b -> {};
    private boolean centered;      // true once the map has been pointed at something (a college or the user's first GPS fix)

    // camera
    private double camLat = Double.NaN, camLng = Double.NaN;
    private Timeline cam;
    private boolean follow;
    private boolean centerKnown;           // false after the user drags / pinches the map: our remembered centre is then out of date
    private double zoomVal = Double.NaN;       // zoom we last set ourselves; NaN after the user pinched / scrolled (then the next move just jumps)

    // selection / preview card / route
    private boolean preview;
    private Consumer<BoardingHouse> onDetails;
    private BoardingHouse selected;
    private MapCard card;
    private List<College> colleges = List.of();
    private int routeToken;
    private double[] curOrigin;
    private String originName = "your location";
    private boolean originIsCollege;

    private final ChangeListener<double[]> gpsMoved = (o, was, now) -> {
        if (now != null && !centered) { centered = true; setCenterNow(now[0], now[1]); applyZoom(16); }   // open on the user
        layer.moveMe(now);
    };

    public MapPane(double height) {
        setMinHeight(height); setPrefHeight(height); setMaxHeight(height);
        applyZoom(15); map.addLayer(layer);
        Label attr = new Label(AppConfig.mapAttribution());
        attr.setStyle("-fx-font-size:9px;-fx-text-fill:#6B6580;-fx-background-color:rgba(255,255,255,0.7);-fx-padding:1 4;");
        attr.setMouseTransparent(true);
        StackPane.setAlignment(attr, Pos.BOTTOM_LEFT);

        locate.getStyleClass().add("map-fab");
        locate.setGraphic(Ui.strokeIcon(Glyphs.LOCATE, 22, BLUE));
        locate.setOnAction(e -> Locator.get().locate(p -> { setFollow(true); flyTo(p[0], p[1], 16, 700); }));
        StackPane.setAlignment(locate, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(locate, new Insets(0, 10, 22, 0));
        getChildren().addAll(map, attr, locate);

        // touching the map stops a running camera move; dragging or zooming it ends "follow me"
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> { if (cam != null) cam.stop(); });
        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> { setFollow(false); centerKnown = false; });
        addEventFilter(ScrollEvent.SCROLL, e -> { setFollow(false); zoomVal = Double.NaN; centerKnown = false; });
        addEventFilter(javafx.scene.input.ZoomEvent.ANY, e -> { zoomVal = Double.NaN; centerKnown = false; });
        addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {              // tap on empty map = close the card
            if (selected != null && e.isStillSincePress() && !inside(e.getTarget(), locate) && !inside(e.getTarget(), card)) deselect();
        });

        // keep the blue dot in sync with GPS while this map is on screen
        sceneProperty().addListener((o, was, sc) -> {
            if (sc != null) {
                State.gps.addListener(gpsMoved);
                double[] g = State.gps.get();
                if (g != null) gpsMoved.changed(null, null, g);
                layer.setActive(true);
            } else {
                State.gps.removeListener(gpsMoved);
                layer.setActive(false);
                if (cam != null) cam.stop();
            }
        });
    }

    /** Plain tap on a pin (used when no preview card is wanted). */
    public void onPinTap(Consumer<BoardingHouse> c) { this.onPin = c; }

    /**
     * Tap a pin to see a card and the walking route to it. {@code details} runs when the card is tapped
     * (null = no details page, e.g. an owner looking at their own listings).
     */
    public void enablePreview(Consumer<BoardingHouse> details) { this.preview = true; this.onDetails = details; }

    /** Campuses used as the route start when no college is chosen (owner screens): the nearest one to the tapped BH. */
    public void setColleges(List<College> list) { this.colleges = list == null ? List.of() : list; layer.setMarks(this.colleges); }

    /** Draws every campus on the map (dot + name) without changing where routes start. Used by Home and Explore. */
    public void showColleges(List<College> list) { layer.setMarks(list == null ? List.of() : list); }

    /** Called live while the radius slider moves: the circle and the zoom glide to the new radius right away. */
    public void previewRadius(double km) {
        if (selected != null || layer.center == null || !layer.circleOn) return;
        layer.radius = km;
        layer.animateRing(km, 280);
        if (getScene() != null && map.getWidth() > 0) flyTo(layer.center[0], layer.center[1], zoomForRadius(km, layer.center[0]), 280);
    }

    /** Zoom (continuous) at which a circle of this radius fills about 62% of the smaller side of the map. */
    private double zoomForRadius(double km, double lat) {
        double side = Math.min(Math.max(map.getWidth(), 280), Math.max(map.getHeight(), 220));
        double metersPerPx = (2 * km * 1000) / (side * 0.62);
        double z = Math.log(156543.03392 * Math.cos(Math.toRadians(lat)) / metersPerPx) / Math.log(2);
        return Math.max(11, Math.min(17, z));
    }

    /** Hides the round "find me" button (for small maps where it would be in the way). */
    public void hideLocateButton() { locate.setVisible(false); locate.setManaged(false); }

    /** Let the map grow to fill the space it is given (full-screen map mode), with a sensible minimum. */
    public void fillAvailable(double minHeight) { setMinHeight(minHeight); setPrefHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE); setMaxHeight(Double.MAX_VALUE); }

    /** Current map center {lat, lng}. Used for the owner's "drop pin" (a fixed crosshair sits over the center). */
    public double[] center() { javafx.geometry.Point2D s = map.localToScene(map.getWidth() / 2, map.getHeight() / 2); MapPoint p = map.getMapPosition(s.getX(), s.getY()); return new double[]{p.getLatitude(), p.getLongitude()}; }

    /** Adds a fixed crosshair pin in the middle of the map for pin-dropping. */
    public void enableCenterPin() {
        Label pin = new Label("📍"); pin.setStyle("-fx-font-size:30px;"); pin.setMouseTransparent(true);
        pin.setTranslateY(-16); getChildren().add(pin);
    }

    /** The map inside a rounded frame (same look as the Home screen). */
    public static StackPane framed(MapPane m) {
        StackPane wrap = new StackPane(m);
        Rectangle mc = new Rectangle(); mc.setArcWidth(28); mc.setArcHeight(28);
        mc.widthProperty().bind(wrap.widthProperty()); mc.heightProperty().bind(wrap.heightProperty());
        wrap.setClip(mc);
        return wrap;
    }

    /**
     * Shows pins. {@code center} is where the map is pointed (and the middle of the college circle); when {@code routeTo}
     * is also given, a walking route from {@code center} to {@code routeTo} is drawn (BH detail screen).
     */
    public void show(double[] center, double radiusKm, List<BoardingHouse> pins, boolean showCircle, double[] routeTo) {
        boolean live = getScene() != null && map.getWidth() > 0;
        boolean glide = live && centered && center != null && radiusKm > 0 && selected == null && routeTo == null;   // map already on screen: glide, do not jump
        if (center != null) { centered = true; if (!glide) setCenterNow(center[0], center[1]); }
        else if (!centered && State.gps.get() != null) { centered = true; setCenterNow(State.gps.get()[0], State.gps.get()[1]); }
        List<BoardingHouse> shown = pins.size() > 60 ? pins.subList(0, 60) : pins;      // cap pins for smooth panning
        layer.set(center, radiusKm, shown, showCircle);
        if (radiusKm > 0 && selected == null) {
            double lat = center != null ? center[0] : Double.isNaN(camLat) ? 10.3 : camLat;
            double z = zoomForRadius(radiusKm, lat);
            if (glide) flyTo(center[0], center[1], z, 450); else { applyZoom(z); setCenterNow(center != null ? center[0] : camLat, center != null ? center[1] : camLng); }
        }
        if (routeTo != null && center != null) {
            boolean fromCollege = State.college.get() != null;
            originName = fromCollege ? State.college.get().name() : "your location"; originIsCollege = fromCollege;
            startRoute(center, routeTo, originName, fromCollege, null, true, null);
        } else if (selected != null) {
            final String id = selected.id();
            if (shown.stream().noneMatch(b -> b.id().equals(id))) deselect();
            else {
                layer.markSelected(id);
                double[] o = originFor(selected);                                       // college changed? redraw from the new start
                if (o == null) layer.clearRoute();
                else if (curOrigin == null || Geo.km(o[0], o[1], curOrigin[0], curOrigin[1]) > 0.01)
                    startRoute(o, new double[]{selected.lat(), selected.lng()}, originName, originIsCollege, selected, true, null);
            }
        }
    }

    /** Zooms out/in so every pin is visible (owner dashboard). */
    public void fitPins(List<BoardingHouse> pins) {
        if (pins.isEmpty()) return;
        double a = 90, b = 180, c = -90, d = -180;
        for (BoardingHouse p : pins) { a = Math.min(a, p.lat()); c = Math.max(c, p.lat()); b = Math.min(b, p.lng()); d = Math.max(d, p.lng()); }
        fitBounds(new double[]{a, b, c, d}, 16, 0);
    }

    /** Draws an animated walking route between two points (owner: dropped pin to the nearest college). */
    public void showRoute(double[] from, double[] to, String label, boolean moveCamera, Consumer<Router.Route> done) {
        selected = null; layer.markSelected(null); hideCard();
        originName = label == null || label.isBlank() ? "your location" : label;
        originIsCollege = !"your location".equals(originName);
        startRoute(from, to, originName, originIsCollege, null, moveCamera, done);
    }

    public void clearRoute() { routeToken++; curOrigin = null; layer.clearRoute(); }

    // ---------------------------------------------------------------- camera

    private void applyZoom(double z) { zoomVal = z; map.setZoom(z); }

    private void setCenterNow(double lat, double lng) { camLat = lat; camLng = lng; centerKnown = true; map.setCenter(lat, lng); }

    private double[] currentCenter() {
        try { if (getScene() != null && map.getWidth() > 0) return center(); } catch (Throwable ignored) { }
        return Double.isNaN(camLat) ? null : new double[]{camLat, camLng};
    }

    /** Glides the camera to a point and zoom together (zoom <= 0 keeps the current zoom). The centre is set after the zoom on every frame, so it never drifts. */
    private void flyTo(double lat, double lng, double zoom, int ms) {
        if (cam != null) cam.stop();
        // start from the centre WE last set (reading it back from the map gave a wrong point, so the camera slid in from the sea);
        // after the user dragged the map we do not know it, so the centre snaps and only the zoom glides
        double[] from = centerKnown && !Double.isNaN(camLat) ? new double[]{camLat, camLng} : new double[]{lat, lng};
        double z0 = zoomVal;
        if (ms <= 0 || (zoom > 0 && Double.isNaN(z0))) { if (zoom > 0) applyZoom(zoom); setCenterNow(lat, lng); return; }
        DoubleProperty t = new SimpleDoubleProperty(0);
        t.addListener((o, a, v) -> {
            double k = v.doubleValue();
            if (zoom > 0) applyZoom(z0 + (zoom - z0) * k);
            setCenterNow(from[0] + (lat - from[0]) * k, from[1] + (lng - from[1]) * k);
        });
        cam = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(t, 0)),
                new KeyFrame(Duration.millis(ms), new KeyValue(t, 1, Interpolator.EASE_BOTH)));
        cam.setOnFinished(e -> { if (zoom > 0) applyZoom(zoom); setCenterNow(lat, lng); });
        cam.play();
    }

    /** Glide so the box {minLat,minLng,maxLat,maxLng} fits; leaves room for the preview card when one is showing. */
    private void fitBounds(double[] bb, int maxZoom, int ms) {
        double cardPx = card != null ? CARD_SPACE : 0;
        double w = Math.max(map.getWidth(), 280), h = Math.max(map.getHeight(), 220);
        int z = Geo.fitZoom(bb[0], bb[1], bb[2], bb[3], w * 0.78, Math.max(80, (h - cardPx) * 0.66), 11, maxZoom);
        double lat = (bb[0] + bb[2]) / 2, lng = (bb[1] + bb[3]) / 2;
        lat -= 360.0 / (256.0 * (1 << z)) * Math.cos(Math.toRadians(lat)) * cardPx / 2.0;     // shift up so the card does not cover the route
        if (ms <= 0) { applyZoom(z); setCenterNow(lat, lng); } else flyTo(lat, lng, z, ms);
    }

    private void setFollow(boolean on) {
        if (follow == on) return;
        follow = on;
        locate.getStyleClass().remove("map-fab-on");
        if (on) locate.getStyleClass().add("map-fab-on");
    }

    // ---------------------------------------------------------------- selection + route

    private void pinTapped(BoardingHouse b) {
        if (!preview) { onPin.accept(b); return; }
        if (selected != null && selected.id().equals(b.id())) return;
        selected = b; layer.markSelected(b.id());
        showCard(b);
        double[] o = originFor(b);
        double[] dest = {b.lat(), b.lng()};
        if (o == null) {
            routeToken++; layer.clearRoute(); curOrigin = null;
            card.setRouteInfo("Turn on location or choose a college to see the route");
            flyTo(dest[0], dest[1], 16, 650);
            return;
        }
        startRoute(o, dest, originName, originIsCollege, b, true, null);
    }

    /** Where the walk starts: the chosen college, else the nearest campus (owner screens), else the user. Sets originName. */
    private double[] originFor(BoardingHouse b) {
        College c = State.college.get();
        if (c == null && !colleges.isEmpty()) {
            double best = Double.MAX_VALUE;
            for (College k : colleges) { double d = Geo.km(k.lat(), k.lng(), b.lat(), b.lng()); if (d < best) { best = d; c = k; } }
        }
        if (c != null) { originName = c.name(); originIsCollege = true; return new double[]{c.lat(), c.lng()}; }
        double[] g = State.gps.get();
        if (g != null) { originName = "your location"; originIsCollege = false; return g; }
        return null;
    }

    private void startRoute(double[] from, double[] to, String label, boolean fromCollege, BoardingHouse forBh, boolean moveCamera, Consumer<Router.Route> done) {
        final int my = ++routeToken;
        curOrigin = from;
        if (card != null && forBh != null) card.setRouteInfo("Finding the best walking route\u2026");
        Async.run(() -> Router.walk(from[0], from[1], to[0], to[1]), r -> {
            if (my != routeToken) return;                       // the selection changed while the route was loading
            layer.setRoute(r, label, fromCollege);
            if (moveCamera) fitBounds(r.bounds(), 17, 850);
            if (card != null && forBh != null) card.setRouteInfo(routeText(r, label));
            if (done != null) done.accept(r);
        }, e -> {});
    }

    private static String routeText(Router.Route r, String label) {
        double km = r.meters() / 1000.0;
        return Geo.fmt(km) + " \u00B7 " + Math.max(1, Geo.walkMinutes(km)) + " min walk" + (r.approximate() ? " (straight line)" : "") + " from " + label;
    }

    private void deselect() {
        selected = null; routeToken++; curOrigin = null;
        layer.markSelected(null); layer.clearRoute(); hideCard();
    }

    private void showCard(BoardingHouse b) {
        if (card != null) getChildren().remove(card);
        MapCard c = new MapCard(b, onDetails == null ? null : () -> onDetails.accept(b), this::deselect);
        card = c;
        StackPane.setAlignment(c, Pos.BOTTOM_CENTER);
        StackPane.setMargin(c, new Insets(0, 10, 20, 10));
        c.setOpacity(0); c.setTranslateY(46);
        getChildren().add(c);
        new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(c.opacityProperty(), 0.0), new KeyValue(c.translateYProperty(), 46.0)),
                new KeyFrame(Duration.millis(260), new KeyValue(c.opacityProperty(), 1.0, Interpolator.EASE_OUT), new KeyValue(c.translateYProperty(), 0.0, Interpolator.EASE_OUT))).play();
        liftButton(-(CARD_SPACE - 8));
    }

    private void hideCard() {
        MapCard old = card;
        card = null;
        liftButton(0);
        if (old == null) return;
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(old.opacityProperty(), old.getOpacity()), new KeyValue(old.translateYProperty(), old.getTranslateY())),
                new KeyFrame(Duration.millis(180), new KeyValue(old.opacityProperty(), 0.0, Interpolator.EASE_IN), new KeyValue(old.translateYProperty(), 46.0, Interpolator.EASE_IN)));
        t.setOnFinished(e -> getChildren().remove(old));
        t.play();
    }

    private void liftButton(double y) {
        new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(locate.translateYProperty(), locate.getTranslateY())),
                new KeyFrame(Duration.millis(240), new KeyValue(locate.translateYProperty(), y, Interpolator.EASE_BOTH))).play();
    }

    private static boolean inside(Object target, Node ancestor) {
        if (ancestor == null || !(target instanceof Node)) return false;
        for (Node n = (Node) target; n != null; n = n.getParent()) if (n == ancestor) return true;
        return false;
    }

    // ---------------------------------------------------------------- the drawing layer

    private final class PinLayer extends MapLayer {
        // college circle
        private double[] center; private double radius; private boolean circleOn;
        private final DoubleProperty ringKm = new SimpleDoubleProperty(0);
        private Timeline ringAnim;
        private final Circle ring = new Circle();
        private final Circle collegeDot = new Circle(7, PURPLE_DARK);
        private final Label radiusBadge = Ui.label("", "map-badge");
        // pins
        private final Group pinsGroup = new Group();
        private Map<String, Group> pinMap = new LinkedHashMap<>();
        // route
        private double[][] rpts; private double[] cum; private boolean routeFromCollege;
        private final DoubleProperty rProg = new SimpleDoubleProperty(0);
        private Timeline rAnim;
        private final Polyline casing = new Polyline(), line = new Polyline();
        private final Circle originDot = new Circle(6.5, PURPLE_DARK), destDot = new Circle(5.5, PURPLE);
        private final Label originTag = Ui.label("", "map-origin-tag"), routeBadge = Ui.label("", "map-route-badge");
        // all campuses (dot + name)
        private final Group collegeGroup = new Group();
        private List<College> marks = List.of();
        // you
        private final Group me = new Group();
        private final Circle halo = new Circle(13, Color.web("#2F6FED", 0.22)), meDot = new Circle(7.5, BLUE);
        private final Polygon arrow = new Polygon(0.0, -5.2, 3.6, 3.0, 0.0, 1.2, -3.6, 3.0);
        private double meLat = Double.NaN, meLng = Double.NaN, fromLat, fromLng, toLat, toLng, lastLat = Double.NaN, lastLng = Double.NaN;
        private final DoubleProperty meT = new SimpleDoubleProperty(1);
        private Timeline meAnim, pulse;
        private boolean active;

        PinLayer() {
            ring.setFill(Color.web("#5B3BF0", 0.14)); ring.setStroke(PURPLE); ring.setStrokeWidth(1.5); ring.setMouseTransparent(true);
            collegeDot.setStroke(Color.WHITE); collegeDot.setStrokeWidth(2); collegeDot.setMouseTransparent(true);
            radiusBadge.setWrapText(false); radiusBadge.setMouseTransparent(true);
            ringKm.addListener((o, a, v) -> markDirty());

            casing.setStroke(Color.web("#FFFFFF", 0.95)); casing.setStrokeWidth(8);
            line.setStroke(PURPLE); line.setStrokeWidth(4.5);
            for (Polyline p : new Polyline[]{casing, line}) { p.setStrokeLineCap(StrokeLineCap.ROUND); p.setStrokeLineJoin(StrokeLineJoin.ROUND); p.setMouseTransparent(true); }
            collegeGroup.setMouseTransparent(true);
            for (Circle c : new Circle[]{originDot, destDot}) { c.setStroke(Color.WHITE); c.setStrokeWidth(2.5); c.setMouseTransparent(true); }
            originTag.setWrapText(false); originTag.setMouseTransparent(true);
            routeBadge.setWrapText(false); routeBadge.setMouseTransparent(true); routeBadge.setOpacity(0);
            rProg.addListener((o, a, v) -> markDirty());

            halo.setMouseTransparent(true);
            meDot.setStroke(Color.WHITE); meDot.setStrokeWidth(3);
            arrow.setFill(Color.WHITE); arrow.setVisible(false);
            me.getChildren().addAll(halo, meDot, arrow); me.setMouseTransparent(true); me.setVisible(false);
            meT.addListener((o, a, v) -> {                              // glide between two GPS fixes
                double t = v.doubleValue();
                meLat = fromLat + (toLat - fromLat) * t; meLng = fromLng + (toLng - fromLng) * t;
                if (follow) setCenterNow(meLat, meLng);                 // "follow me" mode keeps the camera on the glide
                markDirty();
            });
            pulse = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(halo.scaleXProperty(), 0.7), new KeyValue(halo.scaleYProperty(), 0.7), new KeyValue(halo.opacityProperty(), 1.0)),
                    new KeyFrame(Duration.millis(1700), new KeyValue(halo.scaleXProperty(), 2.1, Interpolator.EASE_OUT), new KeyValue(halo.scaleYProperty(), 2.1, Interpolator.EASE_OUT),
                            new KeyValue(halo.opacityProperty(), 0.0, Interpolator.EASE_IN)));
            pulse.setCycleCount(Timeline.INDEFINITE);

            getChildren().addAll(ring, radiusBadge, casing, line, originDot, destDot, originTag, collegeGroup, collegeDot, pinsGroup, routeBadge, me);
        }

        void refresh() { markDirty(); }

        /** The pulse only runs while the map is on screen (saves battery). */
        void setActive(boolean on) {
            active = on;
            if (on && !Double.isNaN(meLat)) pulse.play(); else pulse.pause();
        }

        /** Zoom we last set (NaN when unknown). */
        double zoomNow() { return zoomVal; }

        void animateRing(double want, int ms) {
            if (ringAnim != null) ringAnim.stop();
            ringAnim = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(ringKm, ringKm.get())),
                    new KeyFrame(Duration.millis(ms), new KeyValue(ringKm, want, Interpolator.EASE_BOTH)));
            ringAnim.play();
        }

        /** Every campus gets a small dot and its name, always visible (names appear from a medium zoom). */
        void setMarks(List<College> list) {
            marks = list;
            collegeGroup.getChildren().clear();
            for (College c : list) {
                Group g = new Group();
                Circle dot = new Circle(0, 0, 5.5, Color.web("#4B33C9")); dot.setStroke(Color.WHITE); dot.setStrokeWidth(2);
                Label name = Ui.label(c.name(), "map-college-tag"); name.setWrapText(false);
                name.setLayoutX(9); name.setLayoutY(-9);
                g.getChildren().addAll(dot, name);
                g.getProperties().put("dot", dot); g.getProperties().put("name", name);
                g.setUserData(c); g.setMouseTransparent(true); g.setVisible(false);
                collegeGroup.getChildren().add(g);
            }
            markDirty();
        }

        // ---- you ----

        void moveMe(double[] now) {
            if (now == null) {                                           // location switched off
                meLat = Double.NaN; meLng = Double.NaN; lastLat = Double.NaN; lastLng = Double.NaN;
                if (meAnim != null) meAnim.stop();
                arrow.setVisible(false); me.setVisible(false); pulse.pause(); markDirty(); return;
            }
            if (!Double.isNaN(lastLat) && Geo.km(lastLat, lastLng, now[0], now[1]) > 0.004) {       // moved 4 m or more: point the arrow
                setHeading(Geo.bearing(lastLat, lastLng, now[0], now[1]));
                lastLat = now[0]; lastLng = now[1];
            } else if (Double.isNaN(lastLat)) { lastLat = now[0]; lastLng = now[1]; }
            if (Double.isNaN(meLat)) {                                   // first fix: appear in place, start pulsing
                meLat = now[0]; meLng = now[1]; fromLat = toLat = meLat; fromLng = toLng = meLng;
                if (active) pulse.play();
                markDirty(); return;
            }
            if (Math.abs(meLat - now[0]) < 1e-7 && Math.abs(meLng - now[1]) < 1e-7) { markDirty(); return; }
            fromLat = meLat; fromLng = meLng; toLat = now[0]; toLng = now[1];
            if (meAnim != null) meAnim.stop();
            meT.set(0);
            meAnim = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(meT, 0)),
                    new KeyFrame(Duration.millis(700), new KeyValue(meT, 1, Interpolator.EASE_BOTH)));
            meAnim.play();
        }

        private void setHeading(double deg) {
            double cur = arrow.getRotate();
            double target = cur + (((deg - cur) % 360 + 540) % 360 - 180);       // turn the short way round
            arrow.setVisible(true);
            new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(arrow.rotateProperty(), cur)),
                    new KeyFrame(Duration.millis(300), new KeyValue(arrow.rotateProperty(), target, Interpolator.EASE_BOTH))).play();
        }

        // ---- pins ----

        void set(double[] c, double r, List<BoardingHouse> p, boolean circle) {
            center = c; radius = r; circleOn = circle;
            double want = circle && c != null ? r : 0;
            if (Math.abs(ringKm.get() - want) > 1e-6) animateRing(want, 450);   // the circle grows / shrinks to the new radius
            Map<String, Group> next = new LinkedHashMap<>();
            int fresh = 0;
            for (BoardingHouse b : p) {
                Group g = pinMap.get(b.id());
                if (g == null) g = makePin(b, Math.min(fresh++, 12) * 28);     // only new pins pop in, staggered
                else g.setUserData(b);
                next.put(b.id(), g);
            }
            pinMap = next;
            pinsGroup.getChildren().setAll(next.values());
            markDirty();
        }

        private Group makePin(BoardingHouse b, int delayMs) {
            final Group g = new Group();
            Circle hit = new Circle(0, -16, 20, Color.TRANSPARENT);          // big invisible target: easy to tap
            Polygon tail = new Polygon(0.0, 0.0, -6.5, -15.0, 6.5, -15.0); tail.setFill(PURPLE);
            Circle head = new Circle(0, -21, 10, PURPLE); head.setStroke(Color.WHITE); head.setStrokeWidth(2.5);
            Circle dot = new Circle(0, -21, 3.4, Color.WHITE);
            g.getChildren().addAll(hit, tail, head, dot);
            Scale sc = new Scale(0.001, 0.001, 0, 0);                        // pivot = the tip of the pin
            g.getTransforms().add(sc);
            g.getProperties().put("sc", sc); g.getProperties().put("head", head); g.getProperties().put("tail", tail);
            g.setUserData(b);
            g.setOnMouseClicked(e -> { e.consume(); pinTapped((BoardingHouse) g.getUserData()); });
            g.setOpacity(0);
            new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(sc.xProperty(), 0.001), new KeyValue(sc.yProperty(), 0.001), new KeyValue(g.opacityProperty(), 0.0)),
                    new KeyFrame(Duration.millis(delayMs), new KeyValue(sc.xProperty(), 0.001), new KeyValue(sc.yProperty(), 0.001), new KeyValue(g.opacityProperty(), 0.0)),
                    new KeyFrame(Duration.millis(delayMs + 180), new KeyValue(sc.xProperty(), 1.18, Interpolator.EASE_OUT), new KeyValue(sc.yProperty(), 1.18, Interpolator.EASE_OUT), new KeyValue(g.opacityProperty(), 1.0, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(delayMs + 300), new KeyValue(sc.xProperty(), 1.0, Interpolator.EASE_BOTH), new KeyValue(sc.yProperty(), 1.0, Interpolator.EASE_BOTH))).play();
            return g;
        }

        /** Selected pin grows and turns dark purple; null clears the selection. */
        void markSelected(String id) {
            for (Map.Entry<String, Group> en : pinMap.entrySet()) {
                boolean on = en.getKey().equals(id);
                Group g = en.getValue();
                Scale sc = (Scale) g.getProperties().get("sc");
                Color fill = on ? PURPLE_DARK : PURPLE;
                ((Circle) g.getProperties().get("head")).setFill(fill); ((Polygon) g.getProperties().get("tail")).setFill(fill);
                double to = on ? 1.3 : 1.0;
                new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(sc.xProperty(), sc.getX()), new KeyValue(sc.yProperty(), sc.getY())),
                        new KeyFrame(Duration.millis(170), new KeyValue(sc.xProperty(), to, Interpolator.EASE_BOTH), new KeyValue(sc.yProperty(), to, Interpolator.EASE_BOTH))).play();
                if (on) g.toFront();
            }
        }

        // ---- route ----

        void setRoute(Router.Route r, String label, boolean fromCollege) {
            rpts = r.pts(); routeFromCollege = fromCollege;
            cum = new double[rpts.length];
            double total = 0;
            for (int i = 1; i < rpts.length; i++) { total += Geo.km(rpts[i - 1][0], rpts[i - 1][1], rpts[i][0], rpts[i][1]); cum[i] = total; }
            for (int i = 1; i < cum.length; i++) cum[i] = total <= 0 ? 1 : cum[i] / total;       // 0..1 along the path
            boolean approx = r.approximate();                                                    // no routing server: dashed straight line
            casing.setOpacity(approx ? 0 : 1);
            line.setStrokeLineCap(approx ? StrokeLineCap.BUTT : StrokeLineCap.ROUND);
            if (approx) line.getStrokeDashArray().setAll(11.0, 9.0); else line.getStrokeDashArray().clear();
            originTag.setText(fromCollege ? label : "");
            double km = r.meters() / 1000.0;
            routeBadge.setText(Geo.fmt(km) + " \u00B7 " + Math.max(1, Geo.walkMinutes(km)) + " min");
            routeBadge.setOpacity(0);
            if (rAnim != null) rAnim.stop();
            rProg.set(0);
            double dur = Math.min(1500, 650 + r.meters() / 3.0);                                  // longer walks draw a little slower
            rAnim = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(rProg, 0.0), new KeyValue(routeBadge.opacityProperty(), 0.0)),
                    new KeyFrame(Duration.millis(dur), new KeyValue(rProg, 1.0, Interpolator.EASE_BOTH), new KeyValue(routeBadge.opacityProperty(), 0.0)),
                    new KeyFrame(Duration.millis(dur + 260), new KeyValue(routeBadge.opacityProperty(), 1.0, Interpolator.EASE_OUT)));
            rAnim.play();
            markDirty();
        }

        void clearRoute() {
            if (rAnim != null) rAnim.stop();
            rpts = null; cum = null; routeBadge.setOpacity(0); markDirty();
        }

        // ---- layout: every map move / animation frame lands here ----

        @Override protected void layoutLayer() {
            boolean ringOn = circleOn && center != null;
            ring.setVisible(ringOn); collegeDot.setVisible(ringOn);
            boolean badgeOn = ringOn && ringKm.get() > 0.01;
            radiusBadge.setVisible(badgeOn);
            if (center != null) {
                Point2D c = getMapPoint(center[0], center[1]);
                collegeDot.setCenterX(c.getX()); collegeDot.setCenterY(c.getY());
                double rk = ringKm.get();
                if (rk > 0) {
                    double dLng = rk / (111.32 * Math.cos(Math.toRadians(center[0])));
                    Point2D e = getMapPoint(center[0], center[1] + dLng);
                    double rp = Math.abs(e.getX() - c.getX());
                    ring.setCenterX(c.getX()); ring.setCenterY(c.getY()); ring.setRadius(rp);
                    if (badgeOn) {                                                  // distance badge on the edge of the circle, like a radius handle
                        radiusBadge.setText(Geo.fmt(radius)); radiusBadge.autosize();
                        radiusBadge.setTranslateX(c.getX() + rp * 0.64 - radiusBadge.getWidth() / 2);
                        radiusBadge.setTranslateY(c.getY() - rp * 0.77 - radiusBadge.getHeight() / 2);
                    }
                }
            }

            double vw = map.getWidth(), vh = map.getHeight(), zNow = zoomNow();
            boolean names = Double.isNaN(zNow) || zNow >= 12.8;
            College chosen = State.college.get();
            for (Node n : collegeGroup.getChildren()) {
                College c = (College) n.getUserData();
                Point2D p = getMapPoint(c.lat(), c.lng());
                boolean vis = p.getX() > -80 && p.getX() < vw + 80 && p.getY() > -30 && p.getY() < vh + 30;
                n.setVisible(vis);
                if (!vis) continue;
                n.setTranslateX(p.getX()); n.setTranslateY(p.getY());
                boolean mine = chosen != null && java.util.Objects.equals(chosen.id(), c.id());
                Circle dot = (Circle) n.getProperties().get("dot"); Label name = (Label) n.getProperties().get("name");
                dot.setRadius(mine ? 7.5 : 5.5);
                name.setVisible(names || mine);
                name.setStyle(mine ? "-fx-font-weight:bold;-fx-text-fill:#2A1690;" : "");
            }

            boolean meOn = !Double.isNaN(meLat);
            me.setVisible(meOn);
            if (meOn) { Point2D u = getMapPoint(meLat, meLng); me.setTranslateX(u.getX()); me.setTranslateY(u.getY()); }

            for (Node n : pinsGroup.getChildren()) {
                BoardingHouse b = (BoardingHouse) n.getUserData();
                Point2D p = getMapPoint(b.lat(), b.lng());
                n.setTranslateX(p.getX()); n.setTranslateY(p.getY());
            }

            boolean hasRoute = rpts != null && rpts.length > 1;
            casing.setVisible(hasRoute); line.setVisible(hasRoute); destDot.setVisible(hasRoute);
            originDot.setVisible(hasRoute && !ringOn);
            originTag.setVisible(hasRoute && routeFromCollege && !originTag.getText().isEmpty() && marks.isEmpty());
            routeBadge.setVisible(hasRoute);
            if (!hasRoute) return;

            double p = rProg.get();
            List<Double> xy = new ArrayList<>(rpts.length * 2 + 4);                 // only the part drawn so far: the line "walks" to the BH
            Point2D a0 = getMapPoint(rpts[0][0], rpts[0][1]);
            xy.add(a0.getX()); xy.add(a0.getY());
            for (int i = 1; i < rpts.length; i++) {
                if (cum[i] <= p) { Point2D q = getMapPoint(rpts[i][0], rpts[i][1]); xy.add(q.getX()); xy.add(q.getY()); }
                else {
                    double span = cum[i] - cum[i - 1], f = span <= 0 ? 0 : (p - cum[i - 1]) / span;
                    Point2D q = getMapPoint(rpts[i - 1][0] + (rpts[i][0] - rpts[i - 1][0]) * f, rpts[i - 1][1] + (rpts[i][1] - rpts[i - 1][1]) * f);
                    xy.add(q.getX()); xy.add(q.getY());
                    break;
                }
            }
            casing.getPoints().setAll(xy); line.getPoints().setAll(xy);

            originDot.setCenterX(a0.getX()); originDot.setCenterY(a0.getY());
            double[] end = rpts[rpts.length - 1];
            Point2D z = getMapPoint(end[0], end[1]);
            destDot.setCenterX(z.getX()); destDot.setCenterY(z.getY());
            destDot.setOpacity(p >= 0.98 ? 1 : 0);
            if (originTag.isVisible()) { originTag.autosize(); originTag.setTranslateX(a0.getX() + 10); originTag.setTranslateY(a0.getY() - 30); }
            int mid = 1;
            while (mid < cum.length - 1 && cum[mid] < 0.5) mid++;                    // badge sits on the middle of the walk
            double span = cum[mid] - cum[mid - 1], f = span <= 0 ? 0 : Math.max(0, Math.min(1, (0.5 - cum[mid - 1]) / span));
            Point2D m = getMapPoint(rpts[mid - 1][0] + (rpts[mid][0] - rpts[mid - 1][0]) * f, rpts[mid - 1][1] + (rpts[mid][1] - rpts[mid - 1][1]) * f);
            routeBadge.autosize();
            routeBadge.setTranslateX(m.getX() - routeBadge.getWidth() / 2);
            routeBadge.setTranslateY(m.getY() - routeBadge.getHeight() - 8);
        }
    }
}
