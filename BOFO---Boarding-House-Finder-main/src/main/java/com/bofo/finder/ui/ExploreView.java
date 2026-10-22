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
    private final CheckBox water = new CheckBox("💧 Water"), elec = new CheckBox("⚡ Electricity"), wifi = new CheckBox("📶 WiFi");
    private final ComboBox<String> sort = new ComboBox<>();
    private final MapPane map = new MapPane(220);
    private final VBox list = new VBox(10);
    private Set<String> favs = new HashSet<>();
    private int seq = 0;

    public ExploreView() {
        search.setPromptText("Search boarding houses"); maxPrice.setPromptText("Max ₱/mo");
        maxPrice.setPrefWidth(110);
        sort.getItems().addAll("Nearest", "Lowest price", "Most beds"); sort.getSelectionModel().selectFirst();
        Button go = Ui.btn("Apply", "btn-primary", this::run);
        if (pendingFilters != null) apply(pendingFilters);
        search.setOnAction(e -> run());
        HBox row1 = Ui.hbox(8, search, go); HBox.setHgrow(search, Priority.ALWAYS);
        HBox row2 = Ui.hbox(10, maxPrice, water, elec, wifi);
        HBox row3 = Ui.hbox(8, Ui.label("Sort:", "secondary"), sort);
        VBox filters = Ui.padded(8, row1, row2, row3);
        list.setPadding(new Insets(8, 16, 90, 16));
        map.onPinTap(b -> Nav.show(new BhDetailView(b.id(), () -> Nav.show(new StudentShell(), true)), true));
        ScrollPane sp = Ui.scroll(new VBox(map, list)); VBox.setVgrow(sp, Priority.ALWAYS);
        getChildren().addAll(Ui.appBar("Explore"), filters, sp);
        Async.run(bh::favoriteIds, f -> { favs = f; run(); }, e -> run());
    }

    private void apply(JSONObject f) {
        pendingFilters = null;
        if (f.has("max_price")) maxPrice.setText(String.valueOf(f.optDouble("max_price")));
        water.setSelected(f.optBoolean("free_water")); elec.setSelected(f.optBoolean("free_electricity")); wifi.setSelected(f.optBoolean("free_wifi"));
        if (f.has("text")) search.setText(f.optString("text"));
        if ("price".equals(f.optString("sort"))) sort.getSelectionModel().select(1);
    }

    private void run() {
        BhService.Filter f = new BhService.Filter();
        try { if (!maxPrice.getText().isBlank()) f.maxPrice = Double.parseDouble(maxPrice.getText().trim()); } catch (NumberFormatException e) { Nav.toast("Enter a valid price"); return; }
        f.water = water.isSelected(); f.electricity = elec.isSelected(); f.wifi = wifi.isSelected(); f.text = search.getText();
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
            list.getChildren().clear();
            if (items.isEmpty()) list.getChildren().add(Ui.label("No results. Try loosening your filters.", "secondary"));
            String uid = AuthService.get().userId();
            for (BoardingHouse b : items) list.getChildren().add(new BhCard(b, false, favs.contains(b.id()),
                    x -> Nav.show(new BhDetailView(x.id(), () -> Nav.show(new StudentShell(), true)), true),
                    on -> Async.run(() -> { bh.setFavorite(uid, b.id(), on); return true; }, ok -> {}, e -> Nav.toast("Could not update favorite"))));
        }, e -> Nav.toast("Could not load results"));
    }
}
