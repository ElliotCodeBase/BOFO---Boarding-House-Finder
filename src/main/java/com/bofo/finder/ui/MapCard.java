package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/**
 * Small card that slides up over the map when a boarding-house pin is tapped: photo, name, rating, distance
 * (filled in when the route arrives) and amenity tags. Tapping the card opens the details; X closes it.
 */
final class MapCard extends HBox {
    private final Label info = Ui.label("", "secondary");

    /** @param details opens the BH page (null = no chevron, card is not tappable, e.g. for owners viewing their own listings) */
    MapCard(BoardingHouse b, Runnable details, Runnable close) {
        super(10);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("map-card");
        setStyle("-fx-padding:8 10 8 8;");                     // slim strip: the map stays visible above it
        setMaxWidth(440); setMaxHeight(Region.USE_PREF_SIZE);

        StackPane photo = Ui.photoLoading(b.photoUrl(), 48, 48);

        Label name = Ui.label(b.name(), "h2"); name.setWrapText(false); name.setMaxWidth(Double.MAX_VALUE);
        Label price = Ui.label("\u20B1" + String.format("%,.0f", b.price()) + "/mo", "price"); price.setWrapText(false);
        price.setStyle("-fx-font-size:13px;"); price.setMinWidth(Region.USE_PREF_SIZE);

        Node rating = b.reviewCount() > 0
                ? Ui.hbox(6, Ui.label(Ui.stars(b.avgRating()), "stars"), Ui.label(String.format("%.1f (%d)", b.avgRating(), b.reviewCount()), "secondary"))
                : Ui.label("No reviews yet", "secondary");

        // name gets the full width; price and rating share the next line; the route line wraps instead of being cut off ("Bus or van 4 h 4 min ...")
        HBox priceRow = new HBox(8, price, rating); priceRow.setAlignment(Pos.CENTER_LEFT);
        info.setWrapText(true); info.setMaxWidth(Double.MAX_VALUE); info.setMinHeight(Region.USE_PREF_SIZE);

        VBox text = new VBox(2, name, priceRow);   // route line + tags live on the details page; the map already shows distance and time
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button x = new Button(); x.getStyleClass().add("search-clear");
        x.setGraphic(Ui.strokeIcon(Glyphs.CLOSE, 12, Color.web("#8D8AA3")));
        x.setOnAction(e -> close.run());
        HBox side = new HBox(2, x);
        side.setAlignment(Pos.CENTER_RIGHT);
        if (details != null) {
            side.getChildren().add(Ui.strokeIcon(Glyphs.CHEVRON, 18, Color.web("#5B3BF0")));
            setOnMouseClicked(e -> {
                for (Node t = e.getTarget() instanceof Node n ? n : null; t != null; t = t.getParent()) if (t == x) return;
                e.consume(); details.run();
            });
        } else setStyle(getStyle() + "-fx-cursor:default;");
        getChildren().addAll(photo, text, side);
    }

    /** Distance / walking time line; shown under the rating. */
    void setRouteInfo(String s) { info.setText(s); info.setTooltip(new javafx.scene.control.Tooltip(s)); }
}
