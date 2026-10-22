package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Search + filters (price, inclusions) + sort (distance / price / beds) + full map. */
public final class ExploreView extends VBox {
    /** Set by BOFO AI ("cheap BH with free wifi near my college"). Consumed once. */
    public static JSONObject pendingFilters;

    private final BhService bh = new BhService();
    private final TextField search = new TextField();
    private final TextField maxPrice = new TextField();
    private final Ui.Chip water = new Ui.Chip("Free water"), elec = new Ui.Chip("Free electricity"), wifi = new Ui.Chip("Free Wi-Fi");
    private final ComboBox<String> sort = new ComboBox<>();
    private final MapPane map = new MapPane(220);
    private final VBox list = new VBox(10);
    private Set<String> favs = new HashSet<>();
    private final Label count = Ui.label("", "secondary");
    private int seq = 0;

    public ExploreView() {
        search.setPromptText("Search by name or area"); maxPrice.setPromptText("Max price per month");
        maxPrice.setPrefWidth(170);
        search.getStyleClass().add("search-field");
        sort.getItems().addAll("Nearest", "Lowest price", "Most beds"); sort.getSelectionModel().selectFirst();
        Button go = new Button(); go.getStyleClass().add("icon-btn-primary");
        go.setGraphic(Icons.icon(Icons.SEARCH, 20, javafx.scene.paint.Color.WHITE)); go.setOnAction(e -> run());
        if (pendingFilters != null) apply(pendingFilters);
        search.setOnAction(e -> run());
        HBox row1 = Ui.hbox(8, search, go); HBox.setHgrow(search, Priority.ALWAYS);
        FlowPane chips = new FlowPane(8, 8, maxPrice, water, elec, wifi);
        HBox row3 = Ui.hbox(8, count, Ui.grow(), Ui.label("Sort", "secondary"), sort);
        VBox filters = Ui.padded(10, row1, chips, row3);
        list.setPadding(new Insets(8, 16, 90, 16));
        map.onPinTap(b -> Nav.show(new BhDetailView(b.id(), () -> Nav.show(new StudentShell(), true)), true));
        ScrollPane sp = Ui.scroll(new VBox(map, list)); VBox.setVgrow(sp, Priority.ALWAYS);
        getChildren().addAll(Ui.appBar("Explore"), filters, sp);
        Async.run(bh::favoriteIds, f -> { favs = f; run(); }, e -> run());
    }

    private void apply(JSONObject f) {
        pendingFilters = null;
        if (f.has("max_price")) maxPrice.setText(String.valueOf(f.optDouble("max_price")));
        water.set(f.optBoolean("free_water")); elec.set(f.optBoolean("free_electricity")); wifi.set(f.optBoolean("free_wifi"));
        if (f.has("text")) search.setText(f.optString("text"));
        if ("price".equals(f.optString("sort"))) sort.getSelectionModel().select(1);
    }

    private void run() {
        BhService.Filter f = new BhService.Filter();
        try { if (!maxPrice.getText().isBlank()) f.maxPrice = Double.parseDouble(maxPrice.getText().trim()); } catch (NumberFormatException e) { Nav.toast("Enter a valid price"); return; }
        f.water = water.isOn(); f.electricity = elec.isOn(); f.wifi = wifi.isOn(); f.text = search.getText();
        double[] ref = State.ref(); int s = sort.getSelectionModel().getSelectedIndex(); final int my = ++seq;
        Async.run(() -> {
            var items = bh.list(BhService.Tab.NEW, ref == null ? null : ref[0], ref == null ? null : ref[1], null, f);
            items = new java.util.ArrayList<>(items);
            if (s == 1) items.sort(java.util.Comparator.comparingDouble(BoardingHouse::price));
            if (s == 2) items.sort(java.util.Comparator.comparingInt(BoardingHouse::availableBeds).reversed());
            return items;
        }, items -> {
            if (my != seq) return;
            map.show(ref, 0, items, false, null);
            count.setText(items.size() + (items.size() == 1 ? " result" : " results"));
            list.getChildren().clear();
            if (items.isEmpty()) list.getChildren().add(Ui.emptyState("No matches", "Try removing a filter or raising the maximum price."));
            String uid = AuthService.get().userId();
            for (BoardingHouse b : items) list.getChildren().add(new BhCard(b, false, favs.contains(b.id()),
                    x -> Nav.show(new BhDetailView(x.id(), () -> Nav.show(new StudentShell(), true)), true),
                    on -> Async.run(() -> { bh.setFavorite(uid, b.id(), on); return true; }, ok -> {}, e -> Nav.toast("Could not update favorite"))));
        }, e -> Nav.toast("Could not load results"));
    }
}
