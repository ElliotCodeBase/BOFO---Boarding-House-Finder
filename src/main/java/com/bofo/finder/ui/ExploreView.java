package com.bofo.finder.ui;

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
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Explore: one search bar (campus autocomplete + name/area search as you type) with a filter button, removable active-filter chips, a collapsible filter
 * panel (max price + sort), and a List | Map switch. The map only loads when you open it, so the list gets the whole screen.
 */
public final class ExploreView extends VBox {
    /** Set by BOFO AI ("cheap BH with free wifi near my college"). Consumed once. */
    public static JSONObject pendingFilters;

    private static final String[] SORTS = {"Nearest", "Lowest price", "Most beds"};

    private final BhService bh = new BhService();
    private CampusPicker campus;                              // the single search bar (campus autocomplete + name/area search)
    private final FlowPane activeRow = new FlowPane(8, 8);     // filters that are ON, each removable with its x; hidden when none
    private final TextField maxPrice = new TextField();
    private final Ui.Chip water = new Ui.Chip("Free water"), elec = new Ui.Chip("Free electricity"), wifi = new Ui.Chip("Free Wi-Fi");
    private final Button[] sortBtns = new Button[SORTS.length];
    private int sortIdx = 0;
    private final MapPane map = new MapPane(380);
    private final CardGrid list = new CardGrid(10, 320);
    private final ScrollPane listScroll;
    private final StackPane content = new StackPane();
    private final VBox panel = new VBox(10);
    private final Button filterBtn = new Button();
    private final Label filterBadge = new Label();
    private final Label count = Ui.label("", "secondary");
    private final PauseTransition typing = new PauseTransition(Duration.millis(450));
    private Set<String> favs = new HashSet<>();
    private List<BoardingHouse> lastItems = List.of();
    private double[] lastRef;
    private boolean mapMode = false;
    private boolean flyToCampus = false;   // zoom the map to the campus the next time it is drawn
    private int seq = 0;

    public ExploreView() {
        // ---- ONE search bar: type a campus (autocomplete + dropdown arrow) OR a boarding house name / area; plus the filter button ----
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
        filterBtn.setOnAction(e -> togglePanel());
        HBox top = new HBox(10, campus, filterWrap); top.setAlignment(Pos.TOP_LEFT); HBox.setHgrow(campus, Priority.ALWAYS);
        top.setPadding(new Insets(0, 16, 0, 16));

        // ---- active filters: only shown while something is turned on; tap one to remove it ----
        activeRow.setPadding(new Insets(12, 16, 0, 16)); activeRow.setVisible(false); activeRow.setManaged(false);
        FlowPane amenities = new FlowPane(8, 8, water, elec, wifi);       // turned on here (inside the filter panel)
        for (Ui.Chip c : new Ui.Chip[]{water, elec, wifi}) c.addEventHandler(javafx.event.ActionEvent.ACTION, e -> javafx.application.Platform.runLater(this::run));

        // ---- collapsible filter panel: max price + sort ----
        maxPrice.setPromptText("No limit"); maxPrice.setOnAction(e -> run());
        maxPrice.focusedProperty().addListener((o, was, now) -> { if (!now) run(); });
        FlowPane sorts = new FlowPane(8, 8); 
        for (int i = 0; i < SORTS.length; i++) {
            final int idx = i;
            sortBtns[i] = new Button(SORTS[i]); sortBtns[i].setOnAction(e -> { sortIdx = idx; paintSort(); run(); });
            sorts.getChildren().add(sortBtns[i]);
        }
        paintSort();
        Button reset = Ui.btn("Reset filters", "link", this::resetFilters);
        HBox panelHead = new HBox(8, Ui.label("Filters", "h2"), Ui.grow(), reset); panelHead.setAlignment(Pos.CENTER_LEFT);
        panel.getChildren().addAll(panelHead, Ui.label("Amenities", "secondary"), amenities, Ui.label("Maximum price per month", "secondary"), maxPrice, Ui.label("Sort by", "secondary"), sorts);
        panel.getStyleClass().add("filter-panel"); panel.setVisible(false); panel.setManaged(false);
        VBox.setMargin(panel, new Insets(12, 16, 0, 16));

        // ---- result count + List | Map ----
        HBox seg = Ui.segmented(new String[]{"List", "Map"}, 0, i -> setMapMode(i == 1));
        HBox head = new HBox(8, count, Ui.grow(), seg); head.setAlignment(Pos.CENTER_LEFT); head.setPadding(new Insets(14, 16, 6, 16));

        // ---- content: list (scrolls) or map (fills) ----
        list.setPadding(new Insets(4, 16, 24, 16));
        listScroll = Ui.scroll(list);
        map.fillAvailable(260);
        map.enablePreview(b -> Nav.show(new BhDetailView(b.id(), () -> Nav.show(new StudentShell(), true)), true));
        Async.run(() -> new BhService().colleges(), map::showColleges, e -> {});     // every campus is always on the map
        content.getChildren().setAll(listScroll); VBox.setVgrow(content, Priority.ALWAYS);

        getChildren().addAll(Ui.appBar("Explore"), top, activeRow, panel, head, content);
        if (pendingFilters != null) apply(pendingFilters);
        refreshBadge();
        Async.run(bh::favoriteIds, f -> { favs = f; run(); }, e -> run());
    }

    private void togglePanel() {
        boolean show = !panel.isVisible();
        panel.setManaged(show); panel.setVisible(show);
        if (show) { panel.setOpacity(0); javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(Duration.millis(180), panel); fade.setToValue(1); fade.play(); }
    }

    /** A filter that is on, shown as a dark pill with an x; tapping it turns that filter off and reloads. */
    private Button activeChip(String text, Runnable off) {
        Button b = new Button(text + "  \u2715"); b.getStyleClass().add("chip-selected");
        b.setOnAction(e -> { off.run(); run(); });
        return b;
    }

    private void paintSort() {
        for (int i = 0; i < sortBtns.length; i++) {
            sortBtns[i].getStyleClass().removeAll("chip", "chip-selected");
            sortBtns[i].getStyleClass().add(i == sortIdx ? "chip-selected" : "chip");
        }
    }

    private void resetFilters() {
        water.set(false); elec.set(false); wifi.set(false); maxPrice.clear(); sortIdx = 0; paintSort(); run();
    }

    /** Number shown on the filter button: active chips + price limit + non-default sort. */
    private void refreshBadge() {
        int n = (water.isOn() ? 1 : 0) + (elec.isOn() ? 1 : 0) + (wifi.isOn() ? 1 : 0) + (maxPrice.getText().isBlank() ? 0 : 1) + (sortIdx != 0 ? 1 : 0);
        filterBadge.setText(String.valueOf(n)); filterBadge.setVisible(n > 0);
        activeRow.getChildren().clear();
        if (water.isOn()) activeRow.getChildren().add(activeChip("Free water", () -> water.set(false)));
        if (elec.isOn()) activeRow.getChildren().add(activeChip("Free electricity", () -> elec.set(false)));
        if (wifi.isOn()) activeRow.getChildren().add(activeChip("Free Wi-Fi", () -> wifi.set(false)));
        if (!maxPrice.getText().isBlank()) activeRow.getChildren().add(activeChip("Max \u20B1" + maxPrice.getText().trim(), () -> maxPrice.clear()));
        boolean any = !activeRow.getChildren().isEmpty();
        activeRow.setVisible(any); activeRow.setManaged(any);
        filterBtn.getStyleClass().remove("filter-btn-on"); if (n > 0) filterBtn.getStyleClass().add("filter-btn-on");
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
        if (f.has("max_price")) maxPrice.setText(String.valueOf((long) f.optDouble("max_price")));
        water.set(f.optBoolean("free_water")); elec.set(f.optBoolean("free_electricity")); wifi.set(f.optBoolean("free_wifi"));
        if (f.has("text")) campus.setText(f.optString("text"));
        if ("price".equals(f.optString("sort"))) { sortIdx = 1; paintSort(); }
        typing.stop();
    }

    private void run() {
        refreshBadge();
        BhService.Filter f = new BhService.Filter();
        try { if (!maxPrice.getText().isBlank()) f.maxPrice = Double.parseDouble(maxPrice.getText().trim()); } catch (NumberFormatException e) { Nav.toast("Enter a valid price"); return; }
        f.water = water.isOn(); f.electricity = elec.isOn(); f.wifi = wifi.isOn(); f.text = campus.searchText();
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
            if (items.isEmpty()) list.getChildren().add(Ui.emptyState("No matches", "Try removing a filter or raising the maximum price."));
            String uid = AuthService.get().userId();
            for (BoardingHouse b : items) list.getChildren().add(new BhCard(b, false, favs.contains(b.id()),
                    x -> Nav.show(new BhDetailView(x.id(), () -> Nav.show(new StudentShell(), true)), true),
                    (on, undo) -> BhCard.saveFavorite(bh, uid, b.id(), on, undo)));
        }, e -> { if (my == seq) list.getChildren().setAll(Ui.errorState(e, this::run)); });
    }
}
