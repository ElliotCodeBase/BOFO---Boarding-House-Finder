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
        Label avatar = Ui.avatar(full, 46); avatar.getStyleClass().add("hdr-avatar");
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
        HBox nearby = new HBox(10, Ui.vbox(2, nearbyTitle, nearbySub, priceRange));
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
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4);
        Label text = Ui.label("", "live-pill-text");
        text.setWrapText(false);
        var st = Locator.get().status;
        text.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> {
            if (st.get() == Locator.Status.REQUESTING && State.gps.get() == null) return "Finding you\u2026";
            if (State.gps.get() == null) return "Location off";
            return State.gpsApprox.get() ? "Approximate" : "Live";
        }, st, State.gps, State.gpsApprox));
        dot.fillProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(() -> {
            if (State.gps.get() == null) return st.get() == Locator.Status.REQUESTING ? Color.web("#FFB400") : Color.web("#B4B1C6");
            return State.gpsApprox.get() ? Color.web("#FFB400") : Color.web("#2FBF71");
        }, st, State.gps, State.gpsApprox));
        HBox pill = new HBox(7, dot, text);
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
     * Lowest / middle / highest monthly rent of every place in range, whatever tab is open. "Best rated" only lists places with 3+ reviews,
     * so on that tab the range is read from the unfiltered list instead (one extra request) and shown when it arrives.
     */
    private void showPriceRange(List<BoardingHouse> items, int my, double[] ref, Double rad) {
        if (items != null) { paintPriceRange(PriceRange.of(items)); return; }
        Async.run(() -> bh.list(BhService.Tab.NEW, ref == null ? null : ref[0], ref == null ? null : ref[1], rad, null),
                all -> { if (my == seq) paintPriceRange(PriceRange.of(all)); }, e -> { if (my == seq) priceRange.setVisible(false); });
    }

    private BoardingHouse glowTarget;                                        // the place whose circle a price chip is lighting up on the map

    private void paintPriceRange(PriceRange r) {
        glowTarget = null; map.glow(null);
        priceRange.getChildren().clear();
        if (r == null) { priceRange.setVisible(false); return; }
        priceRange.setStyle("-fx-background-color:#EFEBFF;-fx-background-radius:16;-fx-padding:12 16 12 16;"); if (r.single()) priceRange.getChildren().add(priceCell("Rent", r.lowest, r.lowestBh));
        else {
            priceRange.getChildren().add(priceCell("Lowest", r.lowest, r.lowestBh));
            if (r.hasMiddle()) priceRange.getChildren().add(priceCell("Middle", r.middle, r.middleBh));
            priceRange.getChildren().add(priceCell("Highest", r.highest, r.highestBh));
        }
        priceRange.setVisible(true);
    }

    private VBox priceCell(String caption, double value, BoardingHouse target) {
        Label v = Ui.label(PriceRange.peso(value), "h2"); v.setMinWidth(Region.USE_PREF_SIZE); v.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:" + ("Middle".equals(caption) ? "#5B3BF0" : "#17142A") + ";");
        Label u = Ui.label("/mo", "price-unit"); u.setMinWidth(Region.USE_PREF_SIZE); u.setStyle("-fx-font-size:11px;-fx-text-fill:#8A84A8;");
        HBox val = new HBox(3, v, u); val.setAlignment(Pos.BASELINE_LEFT);
        Label c = Ui.label(caption.toUpperCase(), "secondary"); c.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#8A84A8;"); VBox cell = new VBox(2, c, val); cell.setMaxWidth(Double.MAX_VALUE); javafx.scene.layout.HBox.setHgrow(cell, javafx.scene.layout.Priority.ALWAYS); cell.setCursor(javafx.scene.Cursor.HAND); if (target != null) cell.setOnMouseClicked(e -> {
            boolean off = target == glowTarget;                              // tap the lit chip again to turn the light off
            glowTarget = off ? null : target;
            map.glow(glowTarget);
            for (javafx.scene.Node n : priceRange.getChildren()) n.setStyle("-fx-background-color:transparent;-fx-background-radius:12;");
            if (!off) cell.setStyle("-fx-background-color:white;-fx-background-radius:12;-fx-border-color:#F43F5E;-fx-border-radius:12;-fx-border-width:2;");
        }); return cell;
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
            showPriceRange(tab == BhService.Tab.BEST ? null : items, my, ref, rad);
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
