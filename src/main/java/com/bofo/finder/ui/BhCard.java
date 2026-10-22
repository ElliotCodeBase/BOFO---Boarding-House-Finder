package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.util.Geo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.function.Consumer;

/** Listing card: big rounded photo (heart + availability on top), name and price, distance, rating, tags. */
public final class BhCard extends VBox {
    public BhCard(BoardingHouse b, boolean hot, boolean favorite, Consumer<BoardingHouse> open, Consumer<Boolean> onFav) {
        super(10); getStyleClass().add("card"); setPadding(new Insets(12));

        // photo that follows the card width
        ImageView iv = Ui.photo(b.photoUrl(), 340, 190);
        Rectangle clip = (Rectangle) iv.getClip();
        iv.fitWidthProperty().bind(widthProperty().subtract(24));
        clip.widthProperty().bind(iv.fitWidthProperty()); clip.setArcWidth(24); clip.setArcHeight(24);
        Region ph = new Region(); ph.getStyleClass().add("photo-placeholder");
        ph.setMinHeight(190); ph.setPrefHeight(190); ph.setMaxHeight(190);
        StackPane photoBox = new StackPane(ph, Icons.icon(Icons.HOME, 44, Color.web("#B9A9E3")), iv);

        Button heart = new Button(favorite ? "\u2665" : "\u2661");
        heart.getStyleClass().add(favorite ? "heart-fab-on" : "heart-fab");
        final boolean[] on = {favorite};
        heart.setOnAction(e -> {
            on[0] = !on[0]; heart.setText(on[0] ? "\u2665" : "\u2661");
            heart.getStyleClass().setAll("button", on[0] ? "heart-fab-on" : "heart-fab");
            onFav.accept(on[0]);
        });
        StackPane.setAlignment(heart, Pos.TOP_RIGHT); StackPane.setMargin(heart, new Insets(10));
        Label avail = Ui.label(b.availableBeds() > 0 ? b.availableBeds() + (b.availableBeds() == 1 ? " bed left" : " beds left") : "Full", b.availableBeds() > 0 ? "pill-green" : "pill-grey");
        avail.setMaxWidth(Region.USE_PREF_SIZE); avail.setWrapText(false);
        StackPane.setAlignment(avail, Pos.TOP_LEFT); StackPane.setMargin(avail, new Insets(12));
        photoBox.getChildren().addAll(avail, heart);

        // text block
        Label name = Ui.label(b.name(), "h2");
        Label price = Ui.label("\u20B1" + String.format("%,.0f", b.price()) + " / mo", "price");
        price.setWrapText(false); price.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = new HBox(8, name, Ui.grow(), price); top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(name, Priority.ALWAYS);

        Label where = Ui.label(b.distanceKm() > 0 ? Geo.fmt(b.distanceKm()) + " \u00B7 " + Geo.walkMinutes(b.distanceKm()) + " min walk" : b.address(), "secondary");
        HBox rating = b.reviewCount() > 0
                ? Ui.hbox(6, Ui.label(Ui.stars(b.avgRating()), "stars"), Ui.label(String.format("%.1f (%d)", b.avgRating(), b.reviewCount()), "secondary"))
                : Ui.hbox(6, Ui.label("No reviews yet", "secondary"));

        FlowPane tags = new FlowPane(6, 6);
        if (b.water()) tags.getChildren().add(Ui.label("Water", "tag"));
        if (b.electricity()) tags.getChildren().add(Ui.label("Power", "tag"));
        if (b.wifi()) tags.getChildren().add(Ui.label("Wi-Fi", "tag"));
        if (b.featured()) tags.getChildren().add(Ui.label("Featured", "badge-hot"));
        if (hot) tags.getChildren().add(Ui.label("Hot", "badge-hot"));
        if (b.ownerVerified()) tags.getChildren().add(Ui.label("Verified owner", "badge-verified"));

        getChildren().addAll(photoBox, top, where, rating, tags);
        setOnMouseClicked(e -> {
            Node t = (Node) e.getTarget();
            while (t != null) { if (t == heart) return; t = t.getParent(); }
            open.accept(b);
        });
    }
}
