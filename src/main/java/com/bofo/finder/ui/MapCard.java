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
        super(12);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("map-card");
        setMaxWidth(440); setMaxHeight(Region.USE_PREF_SIZE);

        StackPane photo = Ui.photoLoading(b.photoUrl(), 76, 76);

        Label name = Ui.label(b.name(), "h2"); name.setWrapText(false); name.setMaxWidth(Double.MAX_VALUE);
        Label price = Ui.label("\u20B1" + String.format("%,.0f", b.price()) + "/mo", "price"); price.setWrapText(false);
        price.setStyle("-fx-font-size:13px;"); price.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = new HBox(8, name, price); top.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(name, Priority.ALWAYS);

        Node rating = b.reviewCount() > 0
                ? Ui.hbox(6, Ui.label(Ui.stars(b.avgRating()), "stars"), Ui.label(String.format("%.1f (%d)", b.avgRating(), b.reviewCount()), "secondary"))
                : Ui.label("No reviews yet", "secondary");

        info.setWrapText(false); info.setMaxWidth(Double.MAX_VALUE);
        FlowPane tags = new FlowPane(6, 4);
        if (b.water()) tags.getChildren().add(Ui.label("Water", "tag"));
        if (b.electricity()) tags.getChildren().add(Ui.label("Power", "tag"));
        if (b.wifi()) tags.getChildren().add(Ui.label("Wi-Fi", "tag"));

        VBox text = new VBox(3, top, rating, info, tags);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button x = new Button(); x.getStyleClass().add("search-clear");
        x.setGraphic(Ui.strokeIcon(Glyphs.CLOSE, 14, Color.web("#8D8AA3")));
        x.setOnAction(e -> close.run());
        VBox side = new VBox(x);
        side.setAlignment(Pos.TOP_RIGHT);
        if (details != null) {
            Region gap = new Region(); VBox.setVgrow(gap, Priority.ALWAYS);
            side.getChildren().addAll(gap, Ui.strokeIcon(Glyphs.CHEVRON, 20, Color.web("#5B3BF0")));
            side.setAlignment(Pos.TOP_RIGHT);
            setOnMouseClicked(e -> {
                for (Node t = e.getTarget() instanceof Node n ? n : null; t != null; t = t.getParent()) if (t == x) return;
                e.consume(); details.run();
            });
        } else setStyle("-fx-cursor:default;");
        getChildren().addAll(photo, text, side);
    }

    /** Distance / walking time line; shown under the rating. */
    void setRouteInfo(String s) { info.setText(s); }
}
