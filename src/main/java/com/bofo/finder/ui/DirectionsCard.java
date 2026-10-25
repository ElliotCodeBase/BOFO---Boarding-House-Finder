package com.bofo.finder.ui;

import com.bofo.finder.app.Main;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Geo;
import com.bofo.finder.util.Locator;
import com.bofo.finder.util.Router;
import com.bofo.finder.util.Trip;
import com.bofo.finder.util.TripPlanner;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Directions" card on the boarding house page. Three tabs pick the trip: current location to the BH, current location to the campus, BH to the campus.
 * For each trip it lists the real ways to go ({@link TripPlanner}: walk, tricycle, jeepney lines from public commuter data,
 * bus/van when it is far) with time and fare, recommends one, and draws the chosen one on the map.
 *
 * <p>Layout rule: nothing in this card may have a minimum width bigger than the screen (that is what pushed the old card off the
 * right edge): tabs share the width equally and may wrap, texts wrap, buttons take the full row.
 */
final class DirectionsCard extends VBox {
    private static final String[] TABS = {"Me \u2192 BH", "Me \u2192 Campus", "BH \u2192 Campus"};   // short enough to stay on one line
    private static final int PLAN_SECONDS = 12;                      // longest wait for the real plan; then an offline estimate is shown
    private static final double MOVE_REPLAN_KM = 0.08;               // re-plan when you walked this far (only trips that start at you)

    private final BoardingHouse bh;
    private final MapPane map = new MapPane(250);
    private final Button[] tabButtons = new Button[3];
    private final VBox results = new VBox(10);
    private College campus;
    private int tab = -1, token, chosen;
    private Trip.Plan plan;
    private double[] planFrom;                                         // where the current plan starts (to notice when you moved)
    private boolean planning;
    private final Label heroIcon = new Label(), heroHead = Ui.label("", "dir-head"), heroMeta = Ui.label("", "secondary");

    private final ChangeListener<double[]> gpsMoved = (o, was, now) -> {
        if (now == null) return;
        boolean startsAtMe = tab == 0 || tab == 1;
        if (!startsAtMe) return;
        if (planFrom == null) { select(tab); return; }                 // first fix arrived while we were waiting for it
        if (!planning && Geo.km(planFrom[0], planFrom[1], now[0], now[1]) > MOVE_REPLAN_KM) select(tab);
    };

    static { AppStorage.init(); }                                      // jeepney lines are saved on the device, so trips can be planned offline later

    DirectionsCard(BoardingHouse bh) {
        super(10);
        this.bh = bh;
        getStyleClass().add("card");
        setMinWidth(0);

        Label title = Ui.section("Directions");
        Label hint = Ui.label("Choose where you start and where you are going.", "secondary");
        hint.setMinWidth(0); hint.setMaxWidth(Double.MAX_VALUE);

        GridPane tabRow = new GridPane();
        tabRow.getStyleClass().add("seg-wrap"); tabRow.setHgap(2); tabRow.setMinWidth(0); tabRow.setMaxWidth(Double.MAX_VALUE);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints cc = new ColumnConstraints(); cc.setPercentWidth(100.0 / 3); cc.setHgrow(Priority.ALWAYS); cc.setFillWidth(true);
            tabRow.getColumnConstraints().add(cc);
            final int idx = i;
            Button t = new Button(TABS[i]);
            t.getStyleClass().addAll("seg", "seg-fit");
            t.setMinWidth(0); t.setMaxWidth(Double.MAX_VALUE); t.setWrapText(true); t.setTextAlignment(TextAlignment.CENTER); t.setTextOverrun(OverrunStyle.CLIP);
            t.setOnAction(e -> select(idx));
            tabButtons[i] = t;
            tabRow.add(t, i, 0);
        }

        map.hideLocateButton();
        map.show(new double[]{bh.lat(), bh.lng()}, 0, List.of(bh), false, null);
        StackPane mapBox = MapPane.framed(map);
        mapBox.setMinWidth(0); mapBox.setMaxWidth(Double.MAX_VALUE);

        results.setMinWidth(0);
        getChildren().addAll(title, hint, tabRow, mapBox, results);

        // the chosen campus, else the campus nearest to this boarding house (loaded once)
        campus = State.college.get();
        if (campus != null) map.showColleges(List.of(campus));
        else Async.run(() -> new BhService().colleges(), list -> {
            College best = null; double bd = Double.MAX_VALUE;
            for (College c : list) { double d = Geo.km(c.lat(), c.lng(), bh.lat(), bh.lng()); if (d < bd) { bd = d; best = c; } }
            if (best != null && bd <= 15) { campus = best; map.showColleges(List.of(best)); }
            if (needsCampus(tab) && plan == null) select(tab);                              // a tab was waiting for the campus
            else if (plan != null && plan.straightKm() > TripPlanner.FAR_KM) render(tab);   // now the "show it from the campus" button can appear
        }, e -> { });

        // follow your GPS only while this card is on screen
        sceneProperty().addListener((o, was, sc) -> { if (sc != null) State.gps.addListener(gpsMoved); else State.gps.removeListener(gpsMoved); });

        select(State.gps.get() != null || campus == null ? 0 : 2);
    }

    // ---------------------------------------------------------------- trips

    private static boolean needsCampus(int t) { return t == 1 || t == 2; }

    // tab 0: current location -> BH, tab 1: current location -> campus, tab 2: BH -> campus
    private double[] fromPoint(int t) { return t == 2 ? new double[]{bh.lat(), bh.lng()} : State.gps.get(); }
    private double[] toPoint(int t) { return t == 0 ? new double[]{bh.lat(), bh.lng()} : (campus == null ? null : new double[]{campus.lat(), campus.lng()}); }
    private String fromName(int t) { return t == 2 ? bh.name() : "Current location"; }
    private String toName(int t) { return t == 0 ? bh.name() : (campus == null ? "campus" : campus.name()); }

    private void select(int t) {
        tab = t;
        for (int i = 0; i < 3; i++) { tabButtons[i].getStyleClass().removeAll("seg", "seg-on"); tabButtons[i].getStyleClass().add(i == t ? "seg-on" : "seg"); }
        double[] from = fromPoint(t), to = toPoint(t);
        final int my = ++token;
        plan = null; planFrom = null;
        map.clearRoute();                                              // never leave the previous tab's line on the map (that was the "BH -> Campus does not change" bug)
        if (from == null || to == null) { results.getChildren().setAll(tripHeader(t), missingInput(t)); return; }
        planning = true;
        planFrom = from;
        results.getChildren().setAll(tripHeader(t), loading());
        previewLine(t, from, to);                                      // draw this tab's trip right now; the real plan replaces it a moment later
        Async.run(() -> TripPlanner.plan(from, to), p -> {
            if (my != token) return;
            planning = false; plan = p; chosen = p.best();
            render(t);
        }, e -> {
            if (my != token) return;
            planning = false;
            Button retry = Ui.btn("Try again", "btn-tint", () -> select(t));
            retry.setMaxWidth(Double.MAX_VALUE);
            results.getChildren().setAll(tripHeader(t), note("Could not work out the route. Check your connection and try again.", "bad"), retry);
        });
    }

    /** Instant, no-network preview: a saved road if we have one, else a straight guide line, and the camera moves to the trip. */
    private void previewLine(int t, double[] from, double[] to) {
        Router.Route r = Router.peek(from[0], from[1], to[0], to[1]);
        if (r == null) r = Router.estimate(from[0], from[1], to[0], to[1]);
        map.showPath(r.pts(), r.meters(), r.approximate(), Geo.fmt(r.meters() / 1000.0), "", true);
    }

    private Node loading() {
        VBox box = new VBox(8, Skeleton.block(0, 58, 16), Skeleton.block(0, 44, 16));
        Label l = Ui.label("Finding the best way\u2026", "secondary"); l.setMinWidth(0);
        box.getChildren().add(l);
        box.setMinWidth(0);
        return box;
    }

    /** From -> to, as two dots joined by a line (replaces the plain "A -> B" text). */
    private Node tripHeader(int t) {
        Circle a = new Circle(5.5, t == 2 ? Color.web("#3A22B8") : Color.web("#2F6FED"));
        Circle b = new Circle(5.5, Color.WHITE); b.setStroke(Color.web("#5B3BF0")); b.setStrokeWidth(2.5);
        Region link = new Region(); link.setMinSize(2, 12); link.setPrefSize(2, 12); link.setMaxSize(2, 12); link.setStyle("-fx-background-color:#D6D2F5;-fx-background-radius:2;");
        VBox.setMargin(link, new Insets(0, 0, 0, 4));
        VBox v = new VBox(2, tripRow(a, "From", fromName(t)), link, tripRow(b, "To", toName(t)));
        v.getStyleClass().add("dir-trip"); v.setMinWidth(0);
        return v;
    }

    private static HBox tripRow(Circle dot, String what, String name) {
        StackPane d = new StackPane(dot); d.setMinWidth(12); d.setPrefWidth(12);
        Label w = Ui.label(what, "dir-trip-what"); w.setMinWidth(34); w.setWrapText(false);
        Label n = Ui.label(name, "dir-trip-name"); n.setMinWidth(0);
        HBox h = new HBox(8, d, w, n); h.setAlignment(Pos.CENTER_LEFT); h.setMinWidth(0);
        HBox.setHgrow(n, Priority.ALWAYS);
        return h;
    }

    /** Shown when a tab has no start or end yet (no GPS fix, or no campus known). */
    private Node missingInput(int t) {
        VBox box = new VBox(8);
        if (needsCampus(t) && campus == null) {
            box.getChildren().add(note("Choose a campus on the Home screen to see this route.", "warn"));
            if (t == 1 && State.gps.get() == null) box.getChildren().add(locateButton());
        } else {
            box.getChildren().addAll(note("BOFO does not know where you are yet. Turn on location to see the way from you.", "warn"), locateButton());
            if (campus != null) { Button b = Ui.btn("Show BH \u2192 Campus instead", "btn-tint", () -> select(2)); b.setMaxWidth(Double.MAX_VALUE); b.setMinWidth(0); box.getChildren().add(b); }   // this one does not need your location
        }
        return box;
    }

    private Button locateButton() {
        Button b = Ui.btn("Use my location", "btn-cta", () -> Locator.get().locate(p -> select(tab)));
        b.setMaxWidth(Double.MAX_VALUE); b.setMinWidth(0);
        return b;
    }

    // ---------------------------------------------------------------- results

    private void render(int t) {
        List<Node> out = new ArrayList<>();
        out.add(tripHeader(t));

        // big summary of the chosen way (filled by choose())
        heroIcon.getStyleClass().setAll("dir-hero-icon"); heroIcon.setMinSize(48, 48); heroIcon.setAlignment(Pos.CENTER);
        heroHead.setMinWidth(0); heroMeta.setMinWidth(0);
        VBox heroText = new VBox(2, heroHead, heroMeta); heroText.setMinWidth(0); heroText.setAlignment(Pos.CENTER_LEFT);
        HBox hero = new HBox(12, heroIcon, heroText); hero.getStyleClass().add("dir-hero"); hero.setAlignment(Pos.CENTER_LEFT); hero.setMinWidth(0);
        HBox.setHgrow(heroText, Priority.ALWAYS);
        out.add(hero);

        if (!plan.note().isBlank()) out.add(note(plan.note(), plan.straightKm() > TripPlanner.FAR_KM ? "warn" : "ok"));
        if (t != 2 && State.gpsApprox.get())
            out.add(note("Your location comes from this computer (Wi-Fi or internet address) and can be off by several km. A phone uses GPS and is much closer.", "warn"));

        List<HBox> rows = new ArrayList<>();
        if (plan.options().size() > 1) {
            Label ways = Ui.label("Ways to go", "dir-section");
            VBox list = new VBox(8);
            for (int i = 0; i < plan.options().size(); i++) {
                final int idx = i;
                HBox row = optionRow(plan.options().get(i), i == plan.best());
                row.setOnMouseClicked(e -> choose(idx, rows));
                rows.add(row); list.getChildren().add(row);
            }
            out.add(ways); out.add(list);
        } else {
            Label detail = Ui.label(plan.options().get(0).detail(), "secondary"); detail.setMinWidth(0);
            out.add(detail);
        }

        if (plan.straightKm() > TripPlanner.FAR_KM && t != 2 && campus != null) {
            Button b = Ui.btn("Show BH \u2192 Campus instead", "btn-tint", () -> select(2));
            b.setMaxWidth(Double.MAX_VALUE); b.setMinWidth(0);
            out.add(b);
        }

        Button open = Ui.btn("Open in OpenStreetMap", "btn-link", () -> openExternal(t));
        open.setMaxWidth(Double.MAX_VALUE); open.setMinWidth(0);
        Label src = Ui.label("Lines: \u00A9 OpenStreetMap contributors. Fares: LTFRB, effective 19 March 2026. Times are estimates.", "dir-src");
        src.setMinWidth(0);
        out.add(open); out.add(src);

        results.getChildren().setAll(out);
        choose(chosen, rows);
    }

    private void choose(int idx, List<HBox> rows) {
        chosen = idx;
        Trip.Option o = plan.options().get(idx);
        heroIcon.setText(o.mode().icon);
        heroHead.setText(Geo.fmtTime(o.minutes()) + " " + switch (o.mode()) {
            case WALK -> "walk"; case TRICYCLE -> "by tricycle"; case JEEPNEY -> "by jeepney"; case FAR -> "by bus or van";
        });
        String fare = o.mode() == Trip.Mode.WALK || o.fareText().isEmpty() ? "" : "  \u00B7  " + o.fareText();
        heroMeta.setText(Geo.fmt(o.meters() / 1000.0) + fare + (o.approximate() ? "  \u00B7  estimate" : ""));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).getStyleClass().removeAll("dir-opt", "dir-opt-on");
            rows.get(i).getStyleClass().add(i == idx ? "dir-opt-on" : "dir-opt");
        }
        map.showPath(o.path(), o.meters(), o.approximate(), o.badge(), "", true);
    }

    private HBox optionRow(Trip.Option o, boolean best) {
        Label icon = new Label(o.mode().icon); icon.getStyleClass().add("dir-opt-icon"); icon.setMinSize(38, 38); icon.setAlignment(Pos.CENTER);
        Label name = Ui.label(o.title(), "dir-opt-title"); name.setMinWidth(0);
        Label detail = Ui.label(o.detail(), "secondary"); detail.setMinWidth(0);
        VBox text = new VBox(2, name, detail); text.setMinWidth(0);
        if (best) { Label pill = Ui.label("Best for you", "pill-ok"); pill.setStyle("-fx-font-size:11px;-fx-padding:2 8;"); text.getChildren().add(0, pill); }
        HBox.setHgrow(text, Priority.ALWAYS);
        Label time = Ui.label(Geo.fmtTime(o.minutes()), "dir-opt-title"); time.setWrapText(false); time.setMinWidth(Region.USE_PREF_SIZE);
        VBox right = new VBox(2, time);
        right.setAlignment(Pos.TOP_RIGHT); right.setMinWidth(Region.USE_PREF_SIZE);
        if (!o.fareText().isEmpty()) {
            Label fare = Ui.label(o.fareText(), "secondary"); fare.setWrapText(false); fare.setMinWidth(Region.USE_PREF_SIZE);
            right.getChildren().add(fare);
        }
        HBox row = new HBox(10, icon, text, right);
        row.setAlignment(Pos.TOP_LEFT); row.setMinWidth(0); row.setCursor(javafx.scene.Cursor.HAND);
        row.getStyleClass().add("dir-opt");
        return row;
    }

    /** Small tinted message with a symbol in front: kind = ok, warn or bad. */
    private static Node note(String text, String kind) {
        Label sym = new Label(switch (kind) { case "ok" -> "\u2713"; case "bad" -> "!"; default -> "i"; });
        sym.getStyleClass().addAll("dir-note-sym", "dir-note-sym-" + kind); sym.setMinSize(20, 20); sym.setAlignment(Pos.CENTER);
        Label l = Ui.label(text, "dir-note-text"); l.setMinWidth(0); l.setMaxWidth(Double.MAX_VALUE);
        HBox h = new HBox(10, sym, l); h.getStyleClass().addAll("dir-note", "dir-note-" + kind); h.setAlignment(Pos.TOP_LEFT); h.setMinWidth(0);
        HBox.setHgrow(l, Priority.ALWAYS);
        return h;
    }

    private void openExternal(int t) {
        double[] a = fromPoint(t), b = toPoint(t);
        if (a == null || b == null) return;
        Trip.Mode m = plan == null ? Trip.Mode.WALK : plan.options().get(chosen).mode();
        String engine = m == Trip.Mode.WALK ? "fossgis_osrm_foot" : "fossgis_osrm_car";
        String url = String.format(Locale.ROOT, "https://www.openstreetmap.org/directions?engine=%s&route=%.6f%%2C%.6f%%3B%.6f%%2C%.6f", engine, a[0], a[1], b[0], b[1]);
        try { if (Main.host() != null) { Main.host().showDocument(url); return; } } catch (Throwable ignored) { }
        Nav.toast("Could not open the browser");
    }
}
