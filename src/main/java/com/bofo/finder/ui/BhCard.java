package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.Geo;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Listing card: big rounded photo (heart + availability on top), name and price, distance, rating, tags. */
public final class BhCard extends VBox {
    public BhCard(BoardingHouse b, boolean hot, boolean favorite, Consumer<BoardingHouse> open, BiConsumer<Boolean, Runnable> onFav) {
        this(b, hot, favorite, open, onFav, false);
    }

    /** compact = true for the 2-column grid: shorter photo, smaller text, "/mo", two tags at most. Fixed when the card is built (never switched later). */
    public BhCard(BoardingHouse b, boolean hot, boolean favorite, Consumer<BoardingHouse> open, BiConsumer<Boolean, Runnable> onFav, boolean compact) {
        super(compact ? 8 : 12); getStyleClass().add("card"); setPadding(new Insets(compact ? 8 : 12));
        if (compact) {
            getStyleClass().add("card-compact");
            setStyle("-fx-background-color:white;-fx-background-radius:22;-fx-border-color:transparent;-fx-border-radius:22;"
                   + "-fx-effect:dropshadow(gaussian, rgba(45,30,120,0.10), 16, 0, 0, 4);");   // soft lift instead of a thin outline (v1.19.3)
        }

        // photo that follows the card width
        ImageView iv = Ui.coverPhoto(b.photoUrl(), 720);
        iv.setFitHeight(compact ? 120 : 190);
        iv.fitWidthProperty().bind(widthProperty().subtract(Bindings.createDoubleBinding(
                () -> getPadding().getLeft() + getPadding().getRight(), paddingProperty())));   // uses the real (CSS) padding
        Region ph = new Region(); ph.getStyleClass().add("photo-placeholder");
        double h0 = compact ? 120 : 190; ph.setMinHeight(h0); ph.setPrefHeight(h0); ph.setMaxHeight(h0);
        if (!compact) widthProperty().addListener((o, a, w) -> {   // taller photo on wide cards (tablet, single column)
            double h = w.doubleValue() >= 640 ? 260 : w.doubleValue() >= 440 ? 220 : 190;
            iv.setFitHeight(h); ph.setMinHeight(h); ph.setPrefHeight(h); ph.setMaxHeight(h);
        });
        if (compact) widthProperty().addListener((o, a, w) -> {    // photo height follows the card width (narrow phone -> short, wide column -> taller)
            double h = Math.max(100, Math.min(170, (w.doubleValue() - 16) * 0.75));
            iv.setFitHeight(h); ph.setMinHeight(h); ph.setPrefHeight(h); ph.setMaxHeight(h);
        });
        ph.setStyle(compact ? "-fx-background-radius:16;-fx-background-color:linear-gradient(to bottom right,#EEE8FF,#D3C7FF);"
                            : "-fx-background-radius:16;");
        Node homeIcon = Icons.icon(Icons.HOME, 44, compact ? Color.web("#6C4DF5", 0.55) : Color.web("#FFFFFF", 0.85));
        StackPane photoBox = new StackPane(ph, homeIcon, iv);
        Ui.trackLoading(iv, ph, homeIcon);                        // grey pulse while the photo downloads

        Button heart = new Button(favorite ? "\u2665" : "\u2661");
        heart.getStyleClass().add(favorite ? "heart-fab-on" : "heart-fab");
        final boolean[] on = {favorite};
        Runnable paint = () -> {
            heart.setText(on[0] ? "\u2665" : "\u2661");
            heart.getStyleClass().setAll("button", on[0] ? "heart-fab-on" : "heart-fab");
        };
        heart.setOnAction(e -> {
            on[0] = !on[0]; paint.run();
            final boolean attempted = on[0];
            onFav.accept(attempted, () -> { on[0] = !attempted; paint.run(); });   // undo the heart if saving fails
        });
        StackPane.setAlignment(heart, Pos.TOP_RIGHT); StackPane.setMargin(heart, new Insets(10));
        Label avail = Ui.label(b.availableBeds() > 0 ? b.availableBeds() + (b.availableBeds() == 1 ? " bed left" : " beds left") : "Full", b.availableBeds() > 0 ? "pill-green" : "pill-grey");
        avail.setMaxWidth(Region.USE_PREF_SIZE); avail.setWrapText(false);
        StackPane.setAlignment(avail, Pos.TOP_LEFT); StackPane.setMargin(avail, new Insets(12));
        if (!compact || b.availableBeds() <= 0) photoBox.getChildren().add(avail);   // compact card: only say so when it is full
        photoBox.getChildren().add(heart);
        if (compact) {                                                   // amber price chip bottom-left, rating chip bottom-right (v1.18.3)
            Label chip = Ui.label("\u20B1" + String.format("%,.0f", b.price()) + "/mo", "price-chip"); chip.setWrapText(false); chip.setMaxWidth(Region.USE_PREF_SIZE);
            chip.setStyle("-fx-background-color:white;-fx-text-fill:#4B2FD0;-fx-font-size:12.5px;-fx-font-weight:bold;-fx-background-radius:999;-fx-padding:4 10;"
                        + "-fx-effect:dropshadow(gaussian, rgba(23,20,42,0.20), 8, 0, 0, 2);");
            StackPane.setAlignment(chip, Pos.BOTTOM_LEFT); StackPane.setMargin(chip, new Insets(8));
            photoBox.getChildren().add(chip);
            if (b.reviewCount() > 0) {
                Label rate = Ui.label("\u2605 " + String.format("%.1f", b.avgRating()), "rate-chip"); rate.setWrapText(false); rate.setMaxWidth(Region.USE_PREF_SIZE);
                rate.setStyle("-fx-background-color:rgba(23,20,42,0.72);-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:999;-fx-padding:3 8;");
                StackPane.setAlignment(rate, Pos.BOTTOM_RIGHT); StackPane.setMargin(rate, new Insets(8));
                photoBox.getChildren().add(rate);
            }
        }

        // text block: name, then a big price line, then rating + distance on one wrapping row, then tags
        Label name = Ui.label(b.name(), "h2");
        name.setWrapText(true); name.setMaxWidth(Double.MAX_VALUE); if (compact) name.setMaxHeight(40);
        Label price = Ui.label("\u20B1" + String.format("%,.0f", b.price()), "price-big");
        Label unit = Ui.label(compact ? "/mo" : "/ month", "price-unit");
        price.setMinWidth(Region.USE_PREF_SIZE); unit.setMinWidth(Region.USE_PREF_SIZE);
        HBox priceRow = new HBox(5, price, unit); priceRow.setAlignment(Pos.BASELINE_LEFT);
        VBox head = new VBox(4, name, priceRow);

        FlowPane meta = new FlowPane(6, 2); meta.setAlignment(Pos.CENTER_LEFT);
        if (compact) {                                                   // one short line: "\u2605 4.5 \u00B7 1.2 km" or just "1.2 km"
            String km = b.distanceKm() > 0 ? String.format("%.1f km", b.distanceKm()) : "";
            String beds = b.availableBeds() > 0 ? b.availableBeds() + (b.availableBeds() == 1 ? " bed left" : " beds left") : "";
            String line = beds.isEmpty() ? km : km.isEmpty() ? beds : km + " \u00B7 " + beds;
            if (!line.isEmpty()) meta.getChildren().add(Ui.label(line, "secondary"));
        } else if (b.reviewCount() > 0) {
            meta.getChildren().addAll(Ui.label(Ui.stars(b.avgRating()), "stars"),
                    Ui.label(String.format("%.1f (%d)", b.avgRating(), b.reviewCount()), "secondary"));
        } else {
            meta.getChildren().add(Ui.label("New listing", "secondary"));
        }
        if (!compact) meta.getChildren().addAll(Ui.label("\u00B7", "meta-dot"),
                Ui.label(b.distanceKm() > 0 ? Geo.walkText(b.distanceKm()) : b.address(), "secondary"));

        FlowPane tags = new FlowPane(6, 6);
        if (b.ownerVerified()) tags.getChildren().add(Ui.label("Verified owner", "badge-verified"));
        if (b.featured()) tags.getChildren().add(Ui.label("Featured", "badge-hot"));
        if (hot) tags.getChildren().add(Ui.label("Hot", "badge-hot"));
        addAmenityTags(tags, b, 3);

        if (compact) getChildren().addAll(photoBox, compactText(b));                     // price + rating live on the photo; name, place and chips below
        else getChildren().addAll(photoBox, head, meta);
        if (!compact) getChildren().add(tags);                        // the 2-column card stays clean: details are on the detail page
        setOnMouseClicked(e -> {
            Node t = (Node) e.getTarget();
            while (t != null) { if (t == heart) return; t = t.getParent(); }
            open.accept(b);
        });
    }

    /** 2-column card text: bold name, one grey line for the place ("near St. Paul University"), then distance / beds chips. Never wraps, so every card is the same height. */
    private static VBox compactText(BoardingHouse b) {
        String full = b.name() == null ? "" : b.name().trim(), title = full, where = "";
        int i = full.toLowerCase().indexOf(" near ");
        if (i > 0) { title = full.substring(0, i).trim(); where = full.substring(i + 1).trim(); }
        else if (b.address() != null && !b.address().isBlank()) where = b.address().trim();
        Label t = Ui.label(title, "h2"); oneLine(t); t.setStyle("-fx-font-size:14.5px;-fx-font-weight:bold;");
        VBox box = new VBox(3, t);
        if (!where.isEmpty()) { Label w = Ui.label(where, "secondary"); oneLine(w); w.setStyle("-fx-font-size:11.5px;"); box.getChildren().add(w); }
        FlowPane chips = new FlowPane(6, 4);
        if (b.distanceKm() > 0) chips.getChildren().add(chip(String.format("%.1f km", b.distanceKm()), "#F1EEFF", "#5B3BF0"));
        if (b.availableBeds() > 0) chips.getChildren().add(chip(b.availableBeds() + (b.availableBeds() == 1 ? " bed left" : " beds left"), "#E6F6EC", "#1E8A4C"));
        if (!chips.getChildren().isEmpty()) { VBox.setMargin(chips, new Insets(4, 0, 0, 0)); box.getChildren().add(chips); }
        return box;
    }
    private static void oneLine(Label l) { l.setWrapText(false); l.setMinWidth(0); l.setPrefWidth(0); l.setMaxWidth(Double.MAX_VALUE); }
    private static Label chip(String text, String bg, String fg) {
        Label c = new Label(text); c.setWrapText(false); c.setMinWidth(Region.USE_PREF_SIZE);
        c.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";-fx-background-radius:999;-fx-padding:3 9;-fx-font-size:10.5px;-fx-font-weight:bold;");
        return c;
    }

    /** First few amenity names as small tags, then "+N". Falls back to the old water / power / Wi-Fi flags if a place has no amenity rows yet. */
    static void addAmenityTags(FlowPane tags, BoardingHouse b, int max) {
        java.util.List<String> names = b.amenities();
        if (names.isEmpty()) {
            if (b.water()) tags.getChildren().add(Ui.label("Water", "tag"));
            if (b.electricity()) tags.getChildren().add(Ui.label("Power", "tag"));
            if (b.wifi()) tags.getChildren().add(Ui.label("Wi-Fi", "tag"));
            return;
        }
        for (int i = 0; i < Math.min(max, names.size()); i++) tags.getChildren().add(Ui.label(names.get(i), "tag"));
        if (names.size() > max) tags.getChildren().add(Ui.label("+" + (names.size() - max), "tag"));
    }

    /** Standard save / unsave handler: if it fails the heart is undone and the toast says why (offline or server). */
    public static void saveFavorite(BhService bh, String uid, String bhId, boolean on, Runnable undo) {
        Async.run(() -> { bh.setFavorite(uid, bhId, on); return true; }, ok -> {}, e -> {
            undo.run();
            Nav.toast(Connectivity.isNetworkError(e)
                    ? "No internet connection. Couldn't " + (on ? "save" : "remove") + " this place."
                    : "Could not update favorite");
        });
    }
}
