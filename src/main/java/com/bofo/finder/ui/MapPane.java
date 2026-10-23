package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Map built on Gluon Maps (OSM tiles): purple pins, light-purple college circle, optional route line.
 * See README "Map decision" for why this is the default and how to swap in Google Maps.
 */
public final class MapPane extends StackPane {
    private final MapView map = new MapView();
    private final PinLayer layer = new PinLayer();
    private Consumer<BoardingHouse> onPin = b -> {};

    public MapPane(double height) {
        setMinHeight(height); setPrefHeight(height); setMaxHeight(height);
        map.setZoom(15); map.addLayer(layer);
        Label attr = new Label("© OpenStreetMap contributors");
        attr.setStyle("-fx-font-size:9px;-fx-text-fill:#6B6580;-fx-background-color:rgba(255,255,255,0.7);-fx-padding:1 4;");
        StackPane.setAlignment(attr, javafx.geometry.Pos.BOTTOM_RIGHT);
        getChildren().addAll(map, attr);
    }

    public void onPinTap(Consumer<BoardingHouse> c) { this.onPin = c; }

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
        if (center != null) map.setCenter(center[0], center[1]);
        layer.set(center, radiusKm, pins.size() > 60 ? pins.subList(0, 60) : pins, showCircle, routeTo);   // cap pins for smooth panning
        if (radiusKm > 0) map.setZoom(radiusKm <= 0.6 ? 16 : radiusKm <= 1.2 ? 15 : radiusKm <= 2.5 ? 14 : 13);
    }

    private final class PinLayer extends MapLayer {
        private double[] center, routeTo; private double radius; private boolean circleOn;
        private final List<BoardingHouse> pins = new ArrayList<>();
        private final Circle ring = new Circle();
        private final Circle collegeDot = new Circle(7, Color.web("#3A22B8"));
        private final Line route = new Line();
        private final List<Node> pinNodes = new ArrayList<>();

        PinLayer() {
            ring.setFill(Color.web("#5B3BF0", 0.14)); ring.setStroke(Color.web("#5B3BF0")); ring.setStrokeWidth(1.5);
            collegeDot.setStroke(Color.WHITE); collegeDot.setStrokeWidth(2);
            route.setStroke(Color.web("#3A22B8")); route.setStrokeWidth(4);
            getChildren().addAll(ring, route, collegeDot);
        }

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
