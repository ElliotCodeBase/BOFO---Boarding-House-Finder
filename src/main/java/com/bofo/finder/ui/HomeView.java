package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Locator;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.json.JSONObject;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** Default student home: greeting, college selector, map with radius, New/Hot/Best tabs sorted nearest-first. */
public final class HomeView extends VBox {
    private final BhService bh = new BhService();
    private final MapPane map = new MapPane(360);
    private final CardGrid list = new CardGrid(10, 136, 2).wide();          // 2 columns on phones, 3-4 on wide windows
    private java.util.Set<Integer> adSpots = java.util.Set.of();      // list positions that get an AdSlot (empty while ads are off)
    private final Slider radius = new Slider(0.5, 5, State.radiusKm.get());
    private BhService.Tab tab = BhService.Tab.NEW;
    private Set<String> favs = new HashSet<>();
    private List<BoardingHouse> current = List.of();
    private static String entryUser;                                    // the user whose login already got its first Home
    private boolean firstHome;                                           // true on the first Home of a login: plays the short entrance (rare, so it may have personality)
    private CampusPicker searchBar;
    private Button nearMe;
    private final Label nearbySub = Ui.label("", "secondary", "nearby-sub");     // under the list title: what the list is sorted / limited by
    private final HBox priceRange = new HBox(20);                  // under the list subtitle: lowest / middle / highest rent in range (hidden when there is nothing to show)
    private final Label countPill = Ui.label("", "count-pill");     // "12 places"
    // zoomed-out overview of every boarding house + me: on when the app opens and whenever the search bar is empty,
    // switched off by the location button and back on by pressing it again
    private boolean ovWanted = true;         // the user's choice (the location button)
    private boolean ovFit = true;            // the camera still has to (re)frame the overview (first load, first GPS fix, switched back on)
    private boolean ovShown;                 // was the overview on the last time we looked
    private boolean wasSuppressed;           // was a campus / typed text in the search bar the last time we looked
    private boolean holdOff;                 // true while the location button itself clears the campus (that must not switch the overview back on)
    private int seq = 0;                     // request id: ignore responses from outdated requests
    private int shown = 0;                   // cards rendered so far (lazy batches keep the UI light)
    private static final int BATCH = 15;
    private final javafx.animation.PauseTransition debounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(350));

    public HomeView() {
        super(0);
        String uid0 = AuthService.get().userId();                      // first Home of a login: start with no campus chosen, so the overview shows
        if (uid0 != null && !uid0.equals(entryUser)) { entryUser = uid0; State.college.set(null); firstHome = true; }
        var prof = AuthService.get().profile();
        String full = prof == null ? "" : prof.fullName();
        String name = full.isBlank() ? "" : full.split(" ")[0];

        Button bell = new Button(); bell.getStyleClass().add("hdr-btn"); bell.setGraphic(Icons.icon(Icons.BELL, 20, Color.web("#17142A")));
        bell.setOnAction(e -> Nav.toast("No new notifications"));
        Label avatar = Ui.avatar(full, 46, AuthService.get().avatarUrl()); avatar.getStyleClass().add("hdr-avatar");
        HBox top = Ui.hbox(12, avatar,
                Ui.vbox(0, Ui.label(greeting().replace(",", ""), "greet-hello"), Ui.label(name.isBlank() ? "there" : name, "greet-name")), Ui.grow(), bell);
        top.setPadding(Insets.EMPTY);

        // ONE search bar (merged from the old search pill + college box): type or open the list to pick your campus; the map and list below
        // follow it. Press Enter on text that is not a campus (a boarding house name or an area) to search for it in Explore.
        searchBar = new CampusPicker("Search campus or place", true,
                c -> CampusPicker.remember(c),
                text -> BofoPanel.filtersListener.accept(new JSONObject().put("text", text)), null);
        nearMe = new Button(); nearMe.getStyleClass().add("near-btn");                       // round icon button: "show places near me"
        nearMe.setGraphic(Ui.strokeIcon(Glyphs.LOCATE, 20, Color.web("#2F6FED")));
        nearMe.setTooltip(new Tooltip("Near me"));
        nearMe.setOnAction(e -> onLocateTap());
        map.onLocate(this::onLocateTap);                                  // the round button on the map does the same thing
        searchBar.field().textProperty().addListener((o, was, now) -> { suppressionChanged(); syncOverview(); });   // typing hides the overview, emptying the bar brings it back
        paintOverviewButtons();
        HBox searchRow = new HBox(10, searchBar); searchRow.setAlignment(Pos.TOP_LEFT); HBox.setHgrow(searchBar, Priority.ALWAYS);
        searchRow.setPadding(Insets.EMPTY); searchRow.setFillHeight(false);
        searchRow.setViewOrder(-1);                                  // the campus dropdown floats above the map and list

        Label hintText = Ui.label("Location is off, so we can't show places near you. Turn it on, or choose your college.", "secondary");
        HBox.setHgrow(hintText, Priority.ALWAYS);
        HBox locHint = Ui.hbox(10, hintText, Ui.btn("Try again", "link", () -> Locator.get().start()));
        locHint.getStyleClass().add("card");
        locHint.visibleProperty().bind(Locator.get().status.isEqualTo(Locator.Status.UNAVAILABLE).and(State.college.isNull()));
        locHint.managedProperty().bind(locHint.visibleProperty());
        VBox.setMargin(locHint, new Insets(10, 16, 0, 16));

        // radius: a slim floating pill on top of the map (only when a college is chosen), snaps to 0.5 km
        radius.setShowTickLabels(false); radius.setShowTickMarks(false);
        radius.setMajorTickUnit(0.5); radius.setMinorTickCount(0); radius.setSnapToTicks(true); radius.setBlockIncrement(0.5);
        radius.getStyleClass().add("radius-slider");
        radius.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(radius, Priority.ALWAYS);
        radius.skinProperty().addListener((o, was, sk) -> javafx.application.Platform.runLater(this::paintTrack));
        debounce.setOnFinished(e -> reload());
        radius.valueProperty().addListener((o, a, v) -> {
            double km = Math.round(v.doubleValue() * 10) / 10.0;
            State.radiusKm.set(km);
            paintTrack();
            map.previewRadius(km);                  // circle + zoom glide right away; the list reloads once the slider settles
            debounce.playFromStart();
        });
        Label rTitle = Ui.label("Radius", "secondary");
        Label rValue = Ui.label("", "radius-pill"); rValue.setMinWidth(Region.USE_PREF_SIZE);
        rValue.textProperty().bind(State.radiusKm.asString("%.1f km"));
        HBox radiusBar = new HBox(10, rTitle, radius, rValue);
        radiusBar.setAlignment(Pos.CENTER_LEFT); radiusBar.getStyleClass().add("radius-bar");
        radiusBar.setMaxWidth(380); radiusBar.setMaxHeight(Region.USE_PREF_SIZE);
        radiusBar.visibleProperty().bind(State.college.isNotNull());
        radiusBar.managedProperty().bind(radiusBar.visibleProperty());

        HBox chips = Ui.chipRow(new String[]{"New", "Hot", "Best rated"}, 0, i -> { tab = BhService.Tab.values()[i]; reload(); });
        chips.setPadding(new Insets(18, 16, 0, 16)); chips.getStyleClass().add("home-chips");
        countPill.setMinWidth(Region.USE_PREF_SIZE); countPill.setVisible(false); countPill.managedProperty().bind(countPill.visibleProperty());
        Label nearbyTitle = Ui.section("Nearby boarding houses"); nearbyTitle.getStyleClass().add("home-title");
        priceRange.setAlignment(Pos.BOTTOM_LEFT); priceRange.setPadding(new Insets(8, 0, 0, 0));
        priceRange.managedProperty().bind(priceRange.visibleProperty()); priceRange.setVisible(false);
        priceRange.setMaxWidth(Double.MAX_VALUE);                  // the rent card spans the full width, same edges as the map and search bar
        VBox nearbyCol = Ui.vbox(2, nearbyTitle, nearbySub, priceRange); nearbyCol.setMaxWidth(Double.MAX_VALUE); nearbyCol.setMinWidth(0);
        HBox.setHgrow(nearbyCol, Priority.ALWAYS);
        HBox nearby = new HBox(10, nearbyCol);
        nearby.setAlignment(Pos.CENTER_LEFT); nearby.setPadding(new Insets(16, 16, 6, 16));
        list.setPadding(new Insets(4, 16, 130, 16));   // the floating tab bar is ~100 px tall

        Region mapFrame = new Region(); mapFrame.getStyleClass().add("map-frame"); mapFrame.setMouseTransparent(true);   // thin rounded outline on top of the map
        HBox livePill = locationPill();                                   // top-left "Live" chip; the radius bar takes the top when a campus is chosen
        StackPane mapWrap = new StackPane(map, mapFrame, radiusBar, livePill);
        StackPane.setAlignment(radiusBar, Pos.TOP_CENTER); StackPane.setMargin(radiusBar, new Insets(12, 12, 0, 12));
        StackPane.setAlignment(livePill, Pos.TOP_LEFT); StackPane.setMargin(livePill, new Insets(12, 0, 0, 12));
        Rectangle mc = new Rectangle(); mc.setArcWidth(56); mc.setArcHeight(56);
        mc.widthProperty().bind(mapWrap.widthProperty()); mc.heightProperty().bind(mapWrap.heightProperty());
        mapWrap.setClip(mc);
        mapWrap.getStyleClass().add("home-map");
        VBox.setMargin(mapWrap, new Insets(4, 16, 0, 16));

        VBox header = new VBox(18, top, searchRow); header.getStyleClass().addAll("page-header", "home-header"); header.setViewOrder(-1);   // greeting + search in one branded band
        VBox.setMargin(locHint, new Insets(12, 16, 0, 16));
        VBox content = new VBox(0, header, locHint, mapWrap, chips, nearby, list);
        map.openOnRegion();
        map.enablePreview(b -> Nav.show(new BhDetailView(b.id(), () -> Nav.show(new StudentShell(), true)), true));
        map.enableCampusTap(CampusPicker::chooseFromMap);                 // tap a campus dot on the map = choose that campus
        ScrollPane sp = Ui.scroll(content); VBox.setVgrow(sp, Priority.ALWAYS);
        getChildren().add(sp);
        setMinWidth(0); content.setMinWidth(0); header.setMinWidth(0); top.setMinWidth(0); searchRow.setMinWidth(0);
        searchBar.setMinWidth(0); mapWrap.setMinWidth(0); chips.setMinWidth(0); nearby.setMinWidth(0); sp.setMinWidth(0);
        fitMapHeight(720);                                                // until the first layout: a normal phone
        heightProperty().addListener((o, was, h) -> fitMapHeight(h.doubleValue()));
        if (firstHome) entrance(header, mapWrap, chips, nearby);
        Async.run(bh::favoriteIds, f -> { favs = f; reload(); }, e -> reload());
        Async.run(bh::colleges, cs -> map.showColleges(cs), e -> {});   // every campus on the map
        startGps();
        ChangeListener<College> l = (o, a, b) -> { suppressionChanged(); reload(); };
        State.college.addListener(l);
        sceneProperty().addListener((o, was, sc) -> { State.college.removeListener(l); if (sc != null) State.college.addListener(l); });
    }

    /**
     * Small glass chip on the map's top-left corner that says what the blue dot is doing: "Finding you...", "Live" or "Location off".
     * Bound (weakly) to the Locator and State, so nothing has to be removed when the Home is left.
     */
    private HBox locationPill() {
        Label text = Ui.label("", "live-pill-text");
        text.setWrapText(false);
        var st = Locator.get().status;
        text.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> {
            if (st.get() == Locator.Status.REQUESTING && State.gps.get() == null) return "Finding your location\u2026";
            if (State.gps.get() == null) return "Location is off";
            return State.gpsApprox.get() ? "Approximate location" : "Your location";
        }, st, State.gps, State.gpsApprox));
        HBox pill = new HBox(text);
        pill.setAlignment(Pos.CENTER_LEFT); pill.getStyleClass().add("live-pill");
        pill.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pill.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> State.college.get() == null && (st.get() != Locator.Status.IDLE || State.gps.get() != null), st, State.college, State.gps));
        pill.managedProperty().bind(pill.visibleProperty());
        pill.setMouseTransparent(true);
        return pill;
    }

    /**
     * First Home of a login only (rare, so a little personality is fine): the header, map, chips and list title rise 10 px and fade in,
     * 40 ms apart (total under 300 ms). Transform + opacity only, enter curve; leaves every node exactly where layout put it.
     */
    private void entrance(javafx.scene.Node... parts) {
        for (int i = 0; i < parts.length; i++) {
            javafx.scene.Node n = parts[i];
            n.setOpacity(0); n.setTranslateY(10);
            javafx.animation.Timeline t = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, new javafx.animation.KeyValue(n.opacityProperty(), 0.0), new javafx.animation.KeyValue(n.translateYProperty(), 10.0)),
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(240), new javafx.animation.KeyValue(n.opacityProperty(), 1.0, LiveDot.ENTER), new javafx.animation.KeyValue(n.translateYProperty(), 0.0, LiveDot.ENTER)));
            t.setDelay(javafx.util.Duration.millis(40L * i));
            t.setOnFinished(e -> { n.setOpacity(1); n.setTranslateY(0); });
            t.play();
        }
    }

    /** Map takes ~42% of the window height (200-440 px): a small phone still sees the list under it, a tall window does not get a giant map. */
    private void fitMapHeight(double windowH) {
        if (windowH <= 0) return;
        map.setHeightPx(Math.max(200, Math.min(440, Math.round(windowH * 0.42))));
    }

    private static String greeting() {
        int h = java.time.LocalTime.now().getHour();
        return h < 12 ? "Good morning," : h < 18 ? "Good afternoon," : "Good evening,";
    }

    /**
     * Lowest / middle / highest monthly rent of every place in range, whatever tab is open. "Best rated" only lists places with at least 1 review,
     * so on that tab the range is read from the unfiltered list instead (one extra request) and shown when it arrives.
     */
    private void showPriceRange(List<BoardingHouse> items, int my, double[] ref, Double rad) {
        if (State.college.get() == null) { paintPriceHint(); return; }          // prices are only for the places near the picked campus
        if (items != null) { paintPriceRange(PriceRange.of(items)); return; }
        Async.run(() -> bh.list(BhService.Tab.NEW, ref == null ? null : ref[0], ref == null ? null : ref[1], rad, null),
                all -> { if (my == seq) paintPriceRange(PriceRange.of(all)); }, e -> { if (my == seq) priceRange.setVisible(false); });
    }

    private BoardingHouse glowTarget;                                        // the place whose circle a price is lighting up on the map
    private final java.util.List<Label> priceVals = new java.util.ArrayList<>();
    private final java.util.List<javafx.scene.shape.Circle> priceMarks = new java.util.ArrayList<>();
    private final java.util.List<BoardingHouse> priceTargets = new java.util.ArrayList<>();
    private static final String WHITE_CARD = "-fx-background-color:white;-fx-background-radius:18;-fx-border-color:#ECE8FA;-fx-border-radius:18;-fx-border-width:1;-fx-padding:14 16 14 16;";

    private int priceFont = 20;                                              // 16 when three prices share the row
    private String valStyle(boolean on) { return "-fx-font-size:" + priceFont + "px;-fx-font-weight:bold;-fx-text-fill:" + (on ? "#F43F5E" : "#17142A") + ";"; }

    /** No campus yet: one quiet line instead of prices from every boarding house. Tap it to start typing a campus. */
    private void paintPriceHint() {
        glowTarget = null; map.glowQuiet();
        priceRange.getChildren().clear();
        priceRange.setStyle(WHITE_CARD);
        Label t = Ui.label("Choose a campus to see rent near it", "secondary"); t.setStyle("-fx-font-size:13px;-fx-text-fill:#6B6486;");
        Region gap = new Region(); HBox.setHgrow(gap, Priority.ALWAYS);
        Label go = Ui.label("Choose", "secondary"); go.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#5B3BF0;");
        HBox row = new HBox(8, t, gap, go); row.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(row, Priority.ALWAYS); row.setMaxWidth(Double.MAX_VALUE);
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setOnMouseClicked(e -> searchBar.open());
        priceRange.getChildren().add(row);
        priceRange.setVisible(true);
    }

    private void paintPriceRange(PriceRange r) {
        glowTarget = null; map.glowQuiet();
        priceRange.getChildren().clear(); priceVals.clear(); priceMarks.clear(); priceTargets.clear();
        if (r == null) { priceRange.setVisible(false); return; }
        priceRange.setStyle(WHITE_CARD);
        boolean single = r.single();
        // columns, cheapest to dearest, three at most: Lowest, then Best rated if it is a different place from the lowest and highest, else Middle (3+ places), then Highest.
        // If the best rated place is also the lowest or the highest, that column says so instead of repeating the price.
        java.util.List<String> capL = new java.util.ArrayList<>(); java.util.List<Double> valL = new java.util.ArrayList<>(); java.util.List<BoardingHouse> tgL = new java.util.ArrayList<>();
        BoardingHouse best = r.bestBh;
        boolean bestIsLow = best != null && best.id().equals(r.lowestBh.id()), bestIsHigh = best != null && best.id().equals(r.highestBh.id());
        if (single) { capL.add(best != null ? "Rent \u00B7 Best rated" : "Rent"); valL.add(r.lowest); tgL.add(best != null ? best : r.lowestBh); }
        else {
            capL.add(bestIsLow ? "Lowest \u00B7 Best rated" : "Lowest"); valL.add(r.lowest); tgL.add(r.lowestBh);
            if (best != null && !bestIsLow && !bestIsHigh) { capL.add("Best rated"); valL.add(best.price()); tgL.add(best); }
            else if (r.hasMiddle()) { capL.add("Middle"); valL.add(r.middle); tgL.add(r.middleBh); }
            capL.add(bestIsHigh ? "Highest \u00B7 Best rated" : "Highest"); valL.add(r.highest); tgL.add(r.highestBh);
        }
        String[] caps = capL.toArray(new String[0]);
        double[] vals = valL.stream().mapToDouble(Double::doubleValue).toArray();
        BoardingHouse[] tg = tgL.toArray(new BoardingHouse[0]);

        boolean three = caps.length >= 3;
        priceFont = three ? 16 : 20;
        HBox cols = new HBox(); cols.setMaxWidth(Double.MAX_VALUE); cols.setMinWidth(0);
        for (int i = 0; i < caps.length; i++) {
            final int idx = i;
            int slot = i == 0 ? 0 : i == caps.length - 1 ? 2 : 1;                 // left / middle / right, so the text lines up with the ends of the range line
            Label c = Ui.label(caps[i], "secondary"); c.setStyle("-fx-font-size:" + (three ? 11 : 12) + "px;-fx-font-weight:600;-fx-text-fill:#8A84A8;");
            Label v = Ui.label(PriceRange.peso(vals[i]), "h2"); v.setMinWidth(Region.USE_PREF_SIZE); v.setStyle(valStyle(false));
            Label u = Ui.label("/mo", "price-unit"); u.setMinWidth(Region.USE_PREF_SIZE); u.setStyle("-fx-font-size:" + (three ? 10 : 11) + "px;-fx-text-fill:#8A84A8;");
            HBox val = new HBox(3, v, u); val.setAlignment(slot == 0 ? Pos.BASELINE_LEFT : slot == 1 ? Pos.BASELINE_CENTER : Pos.BASELINE_RIGHT);
            VBox col = new VBox(2, c, val); col.setAlignment(slot == 0 ? Pos.TOP_LEFT : slot == 1 ? Pos.TOP_CENTER : Pos.TOP_RIGHT);
            col.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(col, Priority.ALWAYS); col.setCursor(javafx.scene.Cursor.HAND);
            col.setOnMouseClicked(e -> pickPrice(idx));
            cols.getChildren().add(col);
            priceVals.add(v); priceTargets.add(tg[i]);
        }
        VBox body = new VBox(12, cols); body.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(body, Priority.ALWAYS);

        if (!single) {                                                             // a thin line from the lowest to the highest rent, one marker per price at its real position
            Pane track = new Pane(); track.setMinHeight(16); track.setPrefHeight(16); track.setMaxHeight(16);
            Region line = new Region(); line.setStyle("-fx-background-color:#E4DEFB;-fx-background-radius:2;"); line.setMinHeight(4); line.setPrefHeight(4); line.setMaxHeight(4);
            track.getChildren().add(line);
            for (int i = 0; i < vals.length; i++) {
                final int idx = i;
                javafx.scene.shape.Circle m = new javafx.scene.shape.Circle(7, Color.WHITE); m.setStroke(Color.web("#5B3BF0")); m.setStrokeWidth(3);
                m.setCursor(javafx.scene.Cursor.HAND); m.setOnMouseClicked(e -> pickPrice(idx));
                track.getChildren().add(m); priceMarks.add(m);
            }
            double lo = vals[0], hi = vals[vals.length - 1];
            Runnable lay = () -> {
                double w = track.getWidth(), pad = 8.5;
                line.setLayoutX(pad); line.setLayoutY(6); line.setPrefWidth(Math.max(0, w - 2 * pad));
                for (int i = 0; i < priceMarks.size(); i++) {
                    double f = hi == lo ? 0.5 : (vals[i] - lo) / (hi - lo);
                    priceMarks.get(i).setCenterX(pad + f * Math.max(0, w - 2 * pad)); priceMarks.get(i).setCenterY(8);
                }
            };
            track.widthProperty().addListener((o, a, w) -> lay.run()); lay.run();
            body.getChildren().add(track);
        }
        priceRange.getChildren().add(body);
        priceRange.setVisible(true);
    }

    /** Tap a price (or its marker): that boarding house's circle lights up on the map. Tap it again to turn the light off. */
    private void pickPrice(int i) {
        BoardingHouse t = i < priceTargets.size() ? priceTargets.get(i) : null;
        if (t == null) return;
        boolean off = t == glowTarget;
        glowTarget = off ? null : t;
        map.glow(glowTarget);
        for (int j = 0; j < priceVals.size(); j++) {
            boolean on = !off && j == i;
            priceVals.get(j).setStyle(valStyle(on));
            if (j < priceMarks.size()) {
                javafx.scene.shape.Circle m = priceMarks.get(j);
                m.setFill(on ? Color.web("#F43F5E") : Color.WHITE); m.setStroke(on ? Color.WHITE : Color.web("#5B3BF0"));
                m.setRadius(on ? 8 : 7);
            }
        }
    }

    /** Subtitle + count under "Nearby boarding houses". */
    private void updateHeader(int n, double[] ref) {
        College c = State.college.get();
        String base = c != null ? String.format(java.util.Locale.ROOT, "Within %.1f km of %s", State.radiusKm.get(), c.name())
                : ref != null ? "Closest to you first" : "Choose a campus to see what is close";
        nearbySub.setText(base + " \u00B7 " + (n == 1 ? "1 place" : n + " places"));   // the count lives in the subtitle now (the mascot used to cover the pill)
    }

    /** Colors the filled part of the radius track purple (JavaFX has no built-in filled track). */
    private void paintTrack() {
        javafx.scene.Node t = radius.lookup(".track");
        if (t == null) return;
        double pct = (radius.getValue() - radius.getMin()) / (radius.getMax() - radius.getMin()) * 100;
        t.setStyle(String.format(java.util.Locale.ROOT, "-fx-background-color:linear-gradient(to right,#5B3BF0 %.1f%%,#E4E0F5 %.1f%%);", pct, pct));
    }

    // ---------------------------------------------------------------- zoomed-out overview

    /** The overview is showing when the user has not switched it off and the search bar is empty (no campus, no typed text). */
    private boolean overviewActive() { return ovWanted && State.college.get() == null && searchBar.field().getText().isBlank(); }

    /** A campus or some text in the search bar means "I am looking for something": the overview steps aside. Emptying the bar brings it back. */
    private void suppressionChanged() {
        boolean now = State.college.get() != null || !searchBar.field().getText().isBlank();
        if (wasSuppressed && !now && !holdOff) { ovWanted = true; ovFit = true; }
        wasSuppressed = now;
    }

    /** Highlights both location buttons while the overview is on. */
    private void paintOverviewButtons() {
        boolean on = overviewActive();
        nearMe.getStyleClass().remove("near-btn-on");
        if (on) nearMe.getStyleClass().add("near-btn-on");
        map.setLocateActive(on);
    }

    /** Applies a change of the overview state to the map right away (search text typed or cleared, button pressed). */
    private void syncOverview() {
        boolean on = overviewActive();
        if (on && (!ovShown || ovFit)) { map.showOverview(current, State.gps.get(), 650); ovFit = false; }
        ovShown = on;
        paintOverviewButtons();
    }

    /** The location buttons: overview on -> go to my location (overview off); overview off -> back to the overview. */
    private void onLocateTap() {
        if (overviewActive()) {
            Locator.get().locate(p -> { ovWanted = false; ovShown = false; paintOverviewButtons(); map.goTo(p); });
        } else if (State.college.get() == null && searchBar.field().getText().isBlank()) {
            ovWanted = true; ovFit = true; syncOverview();
        } else {                                                          // a campus is chosen (or text typed): show where I am instead
            Locator.get().locate(p -> {
                ovWanted = false;
                holdOff = true;
                try { if (State.college.get() != null) State.college.set(null); else reload(); } finally { holdOff = false; }
                map.goTo(p);
            });
        }
    }

    /** Asks for location (system dialog on Android). On the first fix the list reloads if it is sorted by "where I am". */
    private void startGps() {
        Locator.get().startWithPrompt();
        boolean[] first = {State.gps.get() == null};
        ChangeListener<double[]> l = (o, a, p) -> {
            if (first[0] && p != null) { first[0] = false; ovFit = true; if (State.college.get() == null) reload(); }
        };
        State.gps.addListener(l);
        sceneProperty().addListener((o, was, sc) -> { if (sc == null) State.gps.removeListener(l); });
    }

    private void reload() {
        final int my = ++seq;
        list.getChildren().setAll(Skeleton.cards(4));            // skeleton while loading
        countPill.setVisible(false);
        double[] ref = State.ref();
        boolean circle = State.college.get() != null;
        Double rad = circle ? State.radiusKm.get() : null;
        Async.run(() -> bh.list(tab, ref == null ? null : ref[0], ref == null ? null : ref[1], rad, null), items -> {
            if (my != seq) return;                               // a newer request is in flight
            current = items; shown = 0; adSpots = AdSlot.spots(items.size());
            updateHeader(items.size(), ref);
            showPriceRange(items, my, ref, rad);
            if (overviewActive()) {                              // zoomed out: all boarding houses + my location (the camera only moves when it needs to)
                map.showOverview(items, State.gps.get(), ovFit ? 700 : -1);
                ovFit = false;
            } else map.show(ref, circle ? State.radiusKm.get() : 0, items, circle, null);
            ovShown = overviewActive(); paintOverviewButtons();
            list.getChildren().clear();
            if (items.isEmpty()) list.getChildren().add(Ui.emptyState("Nothing nearby yet", ref == null ? "Allow location or choose your college to see nearby boarding houses." : "No boarding houses with free beds in this radius. Try a larger radius."));
            renderMore();
        }, err -> { if (my == seq) { priceRange.setVisible(false); list.getChildren().setAll(Ui.errorState(err, this::reload)); } });   // banner is set globally
    }

    /** Renders the next batch of cards; a "Show more" button appears when more remain. */
    private void renderMore() {
        if (!list.getChildren().isEmpty() && list.getChildren().get(list.getChildren().size() - 1) instanceof Button) list.getChildren().remove(list.getChildren().size() - 1);
        String uid = AuthService.get().userId();
        int end = Math.min(current.size(), shown + BATCH);
        for (int i = shown; i < end; i++) {
            BoardingHouse b = current.get(i);
            if (adSpots.contains(i)) list.getChildren().add(AdSlot.placeholder());
            list.getChildren().add(new BhCard(b, tab == BhService.Tab.HOT, favs.contains(b.id()),
                    x -> Nav.show(new BhDetailView(x.id(), () -> Nav.show(new StudentShell(), true)), true),
                    (on, undo) -> BhCard.saveFavorite(bh, uid, b.id(), on, undo), true));
        }
        shown = end;
        if (shown < current.size()) list.getChildren().add(Ui.btn("Show more (" + (current.size() - shown) + ")", "btn-outline", this::renderMore));
    }
}
