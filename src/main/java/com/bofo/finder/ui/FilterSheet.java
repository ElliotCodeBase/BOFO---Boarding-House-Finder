package com.bofo.finder.ui;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Explore filter as a bottom sheet: price per month (quick chips + custom), sort, and amenities
 * (popular chips + a search box for every other amenity in the catalog). Nothing changes in the list until "Show results".
 */
public final class FilterSheet extends StackPane {
    /** What the user picked. maxPrice null = no limit; sort is an index into {@link #SORTS}. */
    public record Result(Double maxPrice, int sort, List<String> amenityIds) {}

    public static final String[] SORTS = {"Nearest", "Lowest price", "Most beds"};
    private static final int[] PRICES = {1000, 1500, 2000, 3000};

    private final AmenityPicker picker = new AmenityPicker(false, null);
    private final TextField maxPrice = new TextField();
    private final Button[] priceChips = new Button[PRICES.length + 1];     // [0] = Any
    private final StackPane sortHolder = new StackPane();
    private final Region scrim = new Region();
    private final VBox sheet = new VBox();
    private final ScrollPane scroll;
    private int sort;
    private Consumer<Result> onApply = r -> {};

    public FilterSheet() {
        setVisible(false); setPickOnBounds(true);

        scrim.setStyle("-fx-background-color:rgba(23,20,42,0.45);");

        // ---- header ----
        Region handle = new Region(); handle.setMinSize(40, 4); handle.setPrefSize(40, 4); handle.setMaxSize(40, 4);
        handle.setStyle("-fx-background-color:#D9D6EA;-fx-background-radius:999;");
        HBox handleRow = new HBox(handle); handleRow.setAlignment(Pos.CENTER); handleRow.setPadding(new Insets(10, 0, 4, 0));
        Button close = new Button(); close.getStyleClass().add("search-clear");
        close.getStyleClass().add("filter-close"); close.setGraphic(Ui.strokeIcon(Glyphs.CLOSE, 16, Color.web("#17142A")));
        close.setOnAction(e -> close());
        HBox head = new HBox(8, Ui.label("Filters", "filter-title"), Ui.grow(), close);
        head.setAlignment(Pos.CENTER_LEFT); head.setPadding(new Insets(4, 16, 8, 20));

        // ---- price ----
        FlowPane quick = new FlowPane(8, 8); quick.setPrefWrapLength(320);
        priceChips[0] = priceChip("Any", null);
        quick.getChildren().add(priceChips[0]);
        for (int i = 0; i < PRICES.length; i++) {
            priceChips[i + 1] = priceChip("\u20B1" + String.format("%,d", PRICES[i]), PRICES[i]);
            quick.getChildren().add(priceChips[i + 1]);
        }
        maxPrice.setPromptText("Or type your own maximum (\u20B1)"); maxPrice.getStyleClass().add("filter-input");
        maxPrice.textProperty().addListener((o, a, b) -> paintPrice());
        VBox price = section("Maximum price per month", Ui.label("Per person, per month.", "secondary"), quick, maxPrice);

        // ---- sort ----
        sortHolder.setMaxWidth(Double.MAX_VALUE);
        VBox sortBox = section("Sort by", sortHolder);

        // ---- amenities ----
        VBox amenities = section("Amenities", Ui.label("Show only places that have all of these.", "secondary"), picker);

        VBox body = new VBox(14, card(price), card(sortBox), card(amenities));
        body.setPadding(new Insets(4, 20, 20, 20));
        scroll = Ui.scroll(body); scroll.getStyleClass().add("sheet-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button apply = Ui.btn("Show results", "btn-primary", this::apply);
        apply.setMaxWidth(Double.MAX_VALUE);
        Button clear = Ui.btn("Reset", "btn-ghost-filter", this::reset);
        HBox foot = new HBox(10, clear, apply); foot.setPadding(new Insets(10, 20, 16, 20)); HBox.setHgrow(apply, Priority.ALWAYS);
        foot.setStyle("-fx-border-color:#E7E8F2;-fx-border-width:1 0 0 0;");

        sheet.getChildren().addAll(handleRow, head, scroll, foot);
        sheet.getStyleClass().add("filter-sheet");
        sheet.setMaxWidth(600); sheet.setMaxHeight(Region.USE_PREF_SIZE);
        sheet.maxHeightProperty().bind(heightProperty().multiply(0.92));
        // The sheet sits at the bottom at its own height (a StackPane would stretch it); the empty space above it closes the sheet.
        Region gap = new Region(); VBox.setVgrow(gap, Priority.ALWAYS);
        gap.setOnMouseClicked(e -> close());
        VBox column = new VBox(gap, sheet); column.setAlignment(Pos.BOTTOM_CENTER);
        getChildren().addAll(scrim, column);
    }

    private static VBox section(String title, javafx.scene.Node... n) {
        VBox v = new VBox(10, Ui.label(title, "h2"));
        v.getChildren().addAll(n);
        return v;
    }

    /** Each filter group sits on its own soft card so the sheet reads as three clear blocks. */
    private static VBox card(VBox inner) { inner.getStyleClass().add("filter-group"); return inner; }

    /** Full-width segmented control: every option gets the same share of the row. */
    private HBox sortSegments() {
        HBox wrap = new HBox(2); wrap.getStyleClass().add("seg-wrap"); wrap.setAlignment(Pos.CENTER);
        Button[] bs = new Button[SORTS.length];
        for (int i = 0; i < SORTS.length; i++) {
            final int idx = i;
            bs[i] = new Button(SORTS[i]); bs[i].getStyleClass().addAll(i == sort ? "seg-on" : "seg", "seg-fit");
            bs[i].setMinWidth(0); bs[i].setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(bs[i], Priority.ALWAYS);
            bs[i].setOnAction(e -> {
                sort = idx;
                for (int j = 0; j < bs.length; j++) { bs[j].getStyleClass().removeAll("seg", "seg-on"); bs[j].getStyleClass().add(j == idx ? "seg-on" : "seg"); }
            });
            wrap.getChildren().add(bs[i]);
        }
        return wrap;
    }

    private Button priceChip(String text, Integer value) {
        Button b = new Button(text); b.getStyleClass().add("amenity-chip");
        b.setOnAction(e -> maxPrice.setText(value == null ? "" : String.valueOf(value)));
        return b;
    }

    private void paintPrice() {
        String t = maxPrice.getText().trim();
        for (int i = 0; i < priceChips.length; i++) {
            boolean on = i == 0 ? t.isEmpty() : t.equals(String.valueOf(PRICES[i - 1]));
            priceChips[i].getStyleClass().removeAll("amenity-chip", "amenity-chip-on");
            priceChips[i].getStyleClass().add(on ? "amenity-chip-on" : "amenity-chip");
        }
    }

    private void paintSort() { sortHolder.getChildren().setAll(sortSegments()); }

    private void reset() { maxPrice.clear(); sort = 0; paintSort(); picker.clearSelection(); }

    /** The amenity picker (Explore reads its catalog for chip labels). */
    public AmenityPicker picker() { return picker; }
    public void onApply(Consumer<Result> r) { onApply = r; }

    /** Opens the sheet showing the current filters. */
    public void open(Double currentMax, int currentSort, Collection<String> amenityIds) {
        maxPrice.setText(currentMax == null ? "" : String.valueOf(currentMax.longValue()));
        sort = currentSort; paintSort(); paintPrice();
        picker.setSelected(amenityIds);
        scroll.setVvalue(0);
        setVisible(true);
        scrim.setOpacity(0); sheet.setTranslateY(500);
        FadeTransition f = new FadeTransition(Duration.millis(160), scrim); f.setToValue(1); f.play();
        TranslateTransition t = new TranslateTransition(Duration.millis(240), sheet); t.setToY(0); t.play();
    }

    public boolean isOpen() { return isVisible(); }

    public void close() {
        if (!isVisible()) return;
        FadeTransition f = new FadeTransition(Duration.millis(140), scrim); f.setToValue(0); f.play();
        TranslateTransition t = new TranslateTransition(Duration.millis(200), sheet); t.setToY(sheet.getHeight() + 20);
        t.setOnFinished(e -> setVisible(false)); t.play();
    }

    private void apply() {
        Double max = null;
        String t = maxPrice.getText().trim();
        if (!t.isEmpty()) {
            try { max = Double.parseDouble(t); if (max <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException e) { Nav.toast("Enter a valid price"); return; }
        }
        Result r = new Result(max, sort, picker.selectedIds());
        close();
        onApply.accept(r);
    }
}
