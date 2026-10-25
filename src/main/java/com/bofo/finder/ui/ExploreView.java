package com.bofo.finder.ui;

import com.bofo.finder.model.Amenity;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Explore: ONE search bar (campus autocomplete + name/area search as you type) with a filter button, a single row of quick filters
 * (active price/sort chips, chosen amenities, then the most popular amenities, one tap each), a result count with a List | Map switch,
 * and a bottom-sheet filter ({@link FilterSheet}) for price, sort and any amenity in the catalog. The map only loads when you open it.
 */
public final class ExploreView extends StackPane {
    /** Set by BOFO AI ("cheap BH with free wifi near my college"). Consumed once. */
    public static JSONObject pendingFilters;

    private static final int QUICK_POPULAR = 6;

    private final BhService bh = new BhService();
    private final CampusPicker campus;                         // the single search bar (campus autocomplete + name/area search)
    private final FilterSheet sheet = new FilterSheet();
    private final HBox quickRow = new HBox(8);
    private final ScrollPane quickScroll = new ScrollPane(quickRow);
    private final MapPane map = new MapPane(380);
    private final CardGrid list = new CardGrid(10, 320);
    private final ScrollPane listScroll;
    private final StackPane content = new StackPane();
    private final Button filterBtn = new Button();
    private final Label filterBadge = new Label();
    private final Label count = Ui.label("", "secondary");
    private final PauseTransition typing = new PauseTransition(Duration.millis(450));

    // filter state (the sheet edits it, the quick row shows it)
    private Double maxPrice;
    private int sortIdx = 0;
    private final Set<String> amenityIds = new LinkedHashSet<>();
    private final List<String> pendingNames = new ArrayList<>();   // amenity names from BOFO AI, turned into ids once the catalog is here

    private Set<String> favs = new HashSet<>();
    private List<BoardingHouse> lastItems = List.of();
    private double[] lastRef;
    private boolean mapMode = false;
    private boolean flyToCampus = false;   // zoom the map to the campus the next time it is drawn
    private int seq = 0;

    public ExploreView() {
        // ---- search bar + filter button ----
        campus = new CampusPicker("Search campus, name or area", true, c -> CampusPicker.remember(c), null, t -> typing.playFromStart());
        ChangeListener<College> campusListener = (o, a, b) -> { typing.stop(); flyToCampus = b != null; run(); };
        State.college.addListener(campusListener);
        sceneProperty().addListener((o, was, sc) -> {
            State.college.removeListener(campusListener);
            if (sc != null) State.college.addListener(campusListener);
        });
        typing.setOnFinished(e -> run());                                     // search as you type, once typing pauses

        filterBtn.getStyleClass().add("filter-btn"); filterBtn.setGraphic(Ui.strokeIcon(Glyphs.FILTER, 20, Color.web("#17142A")));
        filterBadge.getStyleClass().add("filter-badge"); filterBadge.setVisible(false); filterBadge.setMouseTransparent(true);
        StackPane filterWrap = new StackPane(filterBtn, filterBadge);
        StackPane.setAlignment(filterBadge, Pos.TOP_RIGHT);
        filterBtn.setOnAction(e -> sheet.open(maxPrice, sortIdx, amenityIds));
        HBox top = new HBox(10, campus, filterWrap); top.setAlignment(Pos.TOP_LEFT); top.setFillHeight(false); HBox.setHgrow(campus, Priority.ALWAYS);
        top.setViewOrder(-1);                                                 // the campus dropdown floats above everything below it
        top.setPadding(Insets.EMPTY);

        // ---- quick filters: one scrolling row ----
        quickRow.setAlignment(Pos.CENTER_LEFT); quickRow.setPadding(new Insets(2, 16, 2, 16));
        quickScroll.getStyleClass().add("quick-row");
        quickScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); quickScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        quickScroll.setFitToHeight(true);
        quickScroll.setMinHeight(46); quickScroll.setPrefHeight(46); quickScroll.setMaxHeight(46);
        quickScroll.addEventFilter(ScrollEvent.SCROLL, e -> {                // mouse wheel scrolls the row sideways
            double span = quickRow.getWidth() - quickScroll.getViewportBounds().getWidth();
            if (span > 0 && e.getDeltaY() != 0) { quickScroll.setHvalue(Math.max(0, Math.min(1, quickScroll.getHvalue() - e.getDeltaY() / span))); e.consume(); }
        });
        VBox.setMargin(quickScroll, new Insets(8, 0, 0, 0));

        sheet.onApply(r -> {
            maxPrice = r.maxPrice(); sortIdx = r.sort();
            amenityIds.clear(); amenityIds.addAll(r.amenityIds());
            run();
        });

        // ---- result count + List | Map ----
        HBox seg = Ui.segmented(new String[]{"List", "Map"}, 0, i -> setMapMode(i == 1));
        count.setWrapText(false); count.setMinWidth(0); count.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(count, Priority.ALWAYS);   // long text shortens with \u2026, never the switch
        HBox head = new HBox(8, count, seg); head.setAlignment(Pos.CENTER_LEFT); head.setPadding(new Insets(8, 16, 6, 16));

        // ---- content: list (scrolls) or map (fills) ----
        list.setPadding(new Insets(4, 16, 24, 16));
        listScroll = Ui.scroll(list);
        map.fillAvailable(260);
        map.enablePreview(b -> Nav.show(new BhDetailView(b.id(), () -> Nav.show(new StudentShell(), true)), true));
        Async.run(() -> new BhService().colleges(), map::showColleges, e -> {});     // every campus is always on the map
        content.getChildren().setAll(listScroll); VBox.setVgrow(content, Priority.ALWAYS);

        VBox header = Ui.pageHeader("Explore", "Find a boarding house near your campus");
        header.getChildren().add(top);
        VBox page = new VBox(header, quickScroll, head, content);
        getChildren().addAll(page, sheet);

        if (pendingFilters != null) apply(pendingFilters);
        sheet.picker().whenLoaded(this::catalogReady);                        // popular chips + names for the active filters
        refreshFilters();
        Async.run(bh::favoriteIds, f -> { favs = f; run(); }, e -> run());
    }

    /** The amenity catalog arrived: resolve BOFO AI names, then draw the quick row. */
    private void catalogReady() {
        boolean changed = false;
        for (String n : pendingNames) {
            Amenity a = findAmenity(n);
            if (a != null && amenityIds.add(a.id())) changed = true;
        }
        pendingNames.clear();
        refreshFilters();
        if (changed) run();
    }

    private Amenity findAmenity(String name) {
        String q = name.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return null;
        for (Amenity a : sheet.picker().catalog()) if (a.name().equalsIgnoreCase(q)) return a;
        for (Amenity a : sheet.picker().catalog()) if (a.name().toLowerCase(Locale.ROOT).contains(q)) return a;
        return null;
    }

    private Amenity byId(String id) {
        for (Amenity a : sheet.picker().catalog()) if (a.id().equals(id)) return a;
        return null;
    }

    /** A filter that is on and not an amenity (price, sort): dark pill with an x; tapping it turns it off. */
    private Button activeChip(String text, Runnable off) {
        Button b = new Button(text + "  \u2715"); b.getStyleClass().add("chip-selected");
        b.setOnAction(e -> { off.run(); run(); });
        return b;
    }

    private Button amenityChip(Amenity a, boolean on) {
        Button b = new Button(a.label());
        b.getStyleClass().add(on ? "amenity-chip-on" : "amenity-chip");
        b.setOnAction(e -> { if (!amenityIds.remove(a.id())) amenityIds.add(a.id()); run(); });
        return b;
    }

    /** Redraws the filter badge and the quick row (active price/sort, chosen amenities, then popular amenities). */
    private void refreshFilters() {
        int n = (maxPrice != null ? 1 : 0) + (sortIdx != 0 ? 1 : 0) + amenityIds.size();
        filterBadge.setText(String.valueOf(n)); filterBadge.setVisible(n > 0);
        filterBtn.getStyleClass().remove("filter-btn-on"); if (n > 0) filterBtn.getStyleClass().add("filter-btn-on");

        quickRow.getChildren().clear();
        if (maxPrice != null) quickRow.getChildren().add(activeChip("Max \u20B1" + String.format("%,.0f", maxPrice), () -> maxPrice = null));
        if (sortIdx != 0) quickRow.getChildren().add(activeChip(FilterSheet.SORTS[sortIdx], () -> sortIdx = 0));
        for (String id : amenityIds) { Amenity a = byId(id); if (a != null) quickRow.getChildren().add(amenityChip(a, true)); }
        int shown = 0;
        for (Amenity a : sheet.picker().available()) {
            if (shown >= QUICK_POPULAR) break;
            if (amenityIds.contains(a.id())) { shown++; continue; }
            quickRow.getChildren().add(amenityChip(a, false)); shown++;
        }
        if (n > 0) quickRow.getChildren().add(Ui.btn("Clear all", "link", () -> { maxPrice = null; sortIdx = 0; amenityIds.clear(); run(); }));
        boolean any = !quickRow.getChildren().isEmpty();
        quickScroll.setVisible(any); quickScroll.setManaged(any);
    }

    private void setMapMode(boolean on) {
        mapMode = on;
        content.getChildren().setAll(on ? map : listScroll);
        if (on) showMap(lastRef, lastItems);                               // the map only loads when it is opened
    }

    /** Draws the map; right after a campus was chosen it also zooms to that campus (no circle: the list is not radius-limited). */
    private void showMap(double[] ref, List<BoardingHouse> items) {
        boolean zoom = flyToCampus && State.college.get() != null;
        flyToCampus = false;
        map.show(ref, zoom ? State.radiusKm.get() : 0, items, false, null);
    }

    private static String shortName(String n) { return n.length() > 28 ? n.substring(0, 27) + "\u2026" : n; }

    private void apply(JSONObject f) {
        pendingFilters = null;
        if (f.has("max_price")) { double v = f.optDouble("max_price"); if (v > 0) maxPrice = v; }
        if (f.optBoolean("free_water")) pendingNames.add("Free water");
        if (f.optBoolean("free_electricity")) pendingNames.add("Free electricity");
        if (f.optBoolean("free_wifi")) pendingNames.add("Free Wi-Fi");
        JSONArray am = f.optJSONArray("amenities");
        if (am != null) for (int i = 0; i < am.length(); i++) pendingNames.add(am.optString(i));
        if (f.has("text")) campus.setText(f.optString("text"));
        if ("price".equals(f.optString("sort"))) sortIdx = 1;
        typing.stop();
    }

    private void run() {
        refreshFilters();
        BhService.Filter f = new BhService.Filter();
        f.maxPrice = maxPrice; f.amenityIds.addAll(amenityIds); f.text = campus.searchText();
        double[] ref = State.ref(); int s = sortIdx; final int my = ++seq;
        list.getChildren().setAll(Skeleton.cards(4)); count.setText("");   // skeleton while loading
        Async.run(() -> {
            var items = bh.list(BhService.Tab.NEW, ref == null ? null : ref[0], ref == null ? null : ref[1], null, f);
            items = new ArrayList<>(items);
            if (s == 1) items.sort(Comparator.comparingDouble(BoardingHouse::price));
            if (s == 2) items.sort(Comparator.comparingInt(BoardingHouse::availableBeds).reversed());
            return items;
        }, items -> {
            if (my != seq) return;
            lastItems = items; lastRef = ref;
            if (mapMode) showMap(ref, items);
            College near = State.college.get();
            count.setText(items.size() + (items.size() == 1 ? " place" : " places") + (near == null ? "" : " near " + shortName(near.name())));
            list.getChildren().clear();
            if (items.isEmpty()) list.getChildren().add(Ui.emptyState("No matches", amenityIds.isEmpty()
                    ? "Try removing a filter or raising the maximum price."
                    : "Try removing an amenity or raising the maximum price."));
            String uid = AuthService.get().userId();
            for (BoardingHouse b : items) list.getChildren().add(new BhCard(b, false, favs.contains(b.id()),
                    x -> Nav.show(new BhDetailView(x.id(), () -> Nav.show(new StudentShell(), true)), true),
                    (on, undo) -> BhCard.saveFavorite(bh, uid, b.id(), on, undo)));
        }, e -> { System.err.println("[Explore] load failed: " + e); if (my == seq) list.getChildren().setAll(Ui.errorState(e, this::run)); });
    }
}
