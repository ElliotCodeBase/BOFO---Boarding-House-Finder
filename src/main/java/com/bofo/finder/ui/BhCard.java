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
        super(12); getStyleClass().add("card"); setPadding(new Insets(12));

        // photo that follows the card width
        ImageView iv = Ui.coverPhoto(b.photoUrl(), 720);
        iv.setFitHeight(190);
        iv.fitWidthProperty().bind(widthProperty().subtract(Bindings.createDoubleBinding(
                () -> getPadding().getLeft() + getPadding().getRight(), paddingProperty())));   // uses the real (CSS) padding
        Region ph = new Region(); ph.getStyleClass().add("photo-placeholder");
        ph.setMinHeight(190); ph.setPrefHeight(190); ph.setMaxHeight(190);
        widthProperty().addListener((o, a, w) -> {                // taller photo on wide cards (tablet, single column)
            double h = w.doubleValue() >= 640 ? 260 : w.doubleValue() >= 440 ? 220 : 190;
            iv.setFitHeight(h); ph.setMinHeight(h); ph.setPrefHeight(h); ph.setMaxHeight(h);
        });
        ph.setStyle("-fx-background-radius:16;");
        Node homeIcon = Icons.icon(Icons.HOME, 44, Color.web("#C9BEFA"));
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
        photoBox.getChildren().addAll(avail, heart);

        // text block: name, then a big price line, then rating + distance on one wrapping row, then tags
        Label name = Ui.label(b.name(), "h2");
        name.setWrapText(true); name.setMaxWidth(Double.MAX_VALUE);
        Label price = Ui.label("\u20B1" + String.format("%,.0f", b.price()), "price-big");
        Label unit = Ui.label("/ month", "price-unit");
        price.setMinWidth(Region.USE_PREF_SIZE); unit.setMinWidth(Region.USE_PREF_SIZE);
        HBox priceRow = new HBox(5, price, unit); priceRow.setAlignment(Pos.BASELINE_LEFT);
        VBox head = new VBox(4, name, priceRow);

        FlowPane meta = new FlowPane(6, 2); meta.setAlignment(Pos.CENTER_LEFT);
        if (b.reviewCount() > 0) {
            meta.getChildren().addAll(Ui.label(Ui.stars(b.avgRating()), "stars"),
                    Ui.label(String.format("%.1f (%d)", b.avgRating(), b.reviewCount()), "secondary"));
        } else {
            meta.getChildren().add(Ui.label("New listing", "secondary"));
        }
        meta.getChildren().addAll(Ui.label("\u00B7", "meta-dot"),
                Ui.label(b.distanceKm() > 0 ? Geo.walkText(b.distanceKm()) : b.address(), "secondary"));

        FlowPane tags = new FlowPane(6, 6);
        if (b.ownerVerified()) tags.getChildren().add(Ui.label("Verified owner", "badge-verified"));
        if (b.featured()) tags.getChildren().add(Ui.label("Featured", "badge-hot"));
        if (hot) tags.getChildren().add(Ui.label("Hot", "badge-hot"));
        addAmenityTags(tags, b, 3);

        getChildren().addAll(photoBox, head, meta, tags);
        setOnMouseClicked(e -> {
            Node t = (Node) e.getTarget();
            while (t != null) { if (t == heart) return; t = t.getParent(); }
            open.accept(b);
        });
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
