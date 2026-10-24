package com.bofo.finder.ui;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.util.Locator;
import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Map built on Gluon Maps. The base map is the plain style from {@link com.bofo.finder.util.MinimalTileRetriever}
 * (roads, water, parks, building shapes; no labels or shop icons). On top: purple pins, a light-purple college circle,
 * an optional route line, a blue "you are here" dot and a round "find me" button (bottom right).
 */
public final class MapPane extends StackPane {
    private final MapView map = new MapView();
    private final PinLayer layer = new PinLayer();
    private final Button locate = new Button();
    private Consumer<BoardingHouse> onPin = b -> {};
    private boolean centered;      // true once the map has been pointed at something (a college or the user's first GPS fix)

    public MapPane(double height) {
        setMinHeight(height); setPrefHeight(height); setMaxHeight(height);
        map.setZoom(15); map.addLayer(layer);
        Label attr = new Label(AppConfig.mapAttribution());
        attr.setStyle("-fx-font-size:9px;-fx-text-fill:#6B6580;-fx-background-color:rgba(255,255,255,0.7);-fx-padding:1 4;");
        attr.setMouseTransparent(true);
        StackPane.setAlignment(attr, Pos.BOTTOM_LEFT);

        locate.getStyleClass().add("map-fab");
        locate.setGraphic(Ui.strokeIcon(Glyphs.LOCATE, 22, Color.web("#2F6FED")));
        locate.setOnAction(e -> Locator.get().locate(p -> { map.setCenter(p[0], p[1]); map.setZoom(16); }));
        StackPane.setAlignment(locate, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(locate, new Insets(0, 10, 22, 0));
        getChildren().addAll(map, attr, locate);

        // keep the blue dot in sync with GPS while this map is on screen
        ChangeListener<double[]> gpsMoved = (o, was, now) -> {
            if (now != null && !centered) { centered = true; map.setCenter(now[0], now[1]); map.setZoom(16); }   // open on the user
            layer.refresh();
        };
        sceneProperty().addListener((o, was, sc) -> {
            if (sc != null) {
                State.gps.addListener(gpsMoved);
                double[] g = State.gps.get();
                if (g != null && !centered) gpsMoved.changed(null, null, g);
            } else State.gps.removeListener(gpsMoved);
        });
    }

    public void onPinTap(Consumer<BoardingHouse> c) { this.onPin = c; }

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

    public void show(double[] center, double radiusKm, List<BoardingHouse> pins, boolean showCircle, double[] routeTo) {
        if (center != null) { centered = true; map.setCenter(center[0], center[1]); }
        else if (!centered && State.gps.get() != null) { centered = true; map.setCenter(State.gps.get()[0], State.gps.get()[1]); }
        layer.set(center, radiusKm, pins.size() > 60 ? pins.subList(0, 60) : pins, showCircle, routeTo);   // cap pins for smooth panning
        if (radiusKm > 0) map.setZoom(radiusKm <= 0.6 ? 16 : radiusKm <= 1.2 ? 15 : radiusKm <= 2.5 ? 14 : 13);
    }

    private final class PinLayer extends MapLayer {
        private double[] center, routeTo; private double radius; private boolean circleOn;
        private final List<BoardingHouse> pins = new ArrayList<>();
        private final Circle ring = new Circle();
        private final Circle collegeDot = new Circle(7, Color.web("#3A22B8"));
        private final Circle meHalo = new Circle(16, Color.web("#2F6FED", 0.18));
        private final Circle meDot = new Circle(7, Color.web("#2F6FED"));
        private final Line route = new Line();
        private final List<Node> pinNodes = new ArrayList<>();

        PinLayer() {
            ring.setFill(Color.web("#5B3BF0", 0.14)); ring.setStroke(Color.web("#5B3BF0")); ring.setStrokeWidth(1.5);
            collegeDot.setStroke(Color.WHITE); collegeDot.setStrokeWidth(2);
            meDot.setStroke(Color.WHITE); meDot.setStrokeWidth(2.5);
            meHalo.setMouseTransparent(true); meDot.setMouseTransparent(true);
            route.setStroke(Color.web("#3A22B8")); route.setStrokeWidth(4);
            getChildren().addAll(ring, route, collegeDot, meHalo, meDot);
        }

        void refresh() { markDirty(); }

        void set(double[] c, double r, List<BoardingHouse> p, boolean circle, double[] rt) {
            center = c; radius = r; circleOn = circle; routeTo = rt;
            getChildren().removeAll(pinNodes); pinNodes.clear(); pins.clear(); pins.addAll(p);
            for (BoardingHouse b : p) {
                Polygon pin = new Polygon(0, 0, -7, -18, 7, -18);                    // teardrop-ish marker
                Circle head = new Circle(0, -22, 9, Color.web("#5B3BF0")); head.setStroke(Color.WHITE); head.setStrokeWidth(2);
                StackPane g = new StackPane(); g.getChildren().addAll(pin, head);
                pin.setFill(Color.web("#5B3BF0")); pin.setTranslateY(0);
                g.setPickOnBounds(false);
                g.setOnMouseClicked(e -> onPin.accept(b));
                g.setUserData(b); pinNodes.add(g); getChildren().add(g);
            }
            markDirty();
        }

        @Override protected void layoutLayer() {
            ring.setVisible(circleOn && center != null); collegeDot.setVisible(center != null && circleOn);
            if (center != null) {
                Point2D c = getMapPoint(center[0], center[1]);
                collegeDot.setCenterX(c.getX()); collegeDot.setCenterY(c.getY());
                if (radius > 0) {
                    double dLng = radius / (111.32 * Math.cos(Math.toRadians(center[0])));
                    Point2D e = getMapPoint(center[0], center[1] + dLng);
                    ring.setCenterX(c.getX()); ring.setCenterY(c.getY()); ring.setRadius(Math.abs(e.getX() - c.getX()));
                }
            }
            double[] me = State.gps.get();                                   // blue "you are here" dot
            meDot.setVisible(me != null); meHalo.setVisible(me != null);
            if (me != null) {
                Point2D u = getMapPoint(me[0], me[1]);
                meDot.setCenterX(u.getX()); meDot.setCenterY(u.getY()); meHalo.setCenterX(u.getX()); meHalo.setCenterY(u.getY());
            }
            for (Node n : pinNodes) {
                BoardingHouse b = (BoardingHouse) n.getUserData();
                Point2D p = getMapPoint(b.lat(), b.lng());
                n.setTranslateX(p.getX()); n.setTranslateY(p.getY());
            }
            route.setVisible(routeTo != null && center != null);
            if (routeTo != null && center != null) {
                Point2D a = getMapPoint(center[0], center[1]), b = getMapPoint(routeTo[0], routeTo[1]);
                route.setStartX(a.getX()); route.setStartY(a.getY()); route.setEndX(b.getX()); route.setEndY(b.getY());
            }
        }
    }
}
