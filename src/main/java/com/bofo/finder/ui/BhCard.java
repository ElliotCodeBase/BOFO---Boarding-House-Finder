package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.util.Geo;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.function.Consumer;

public final class BhCard extends HBox {
    public BhCard(BoardingHouse b, boolean hot, boolean favorite, Consumer<BoardingHouse> open, Consumer<Boolean> onFav) {
        super(10); getStyleClass().add("card");
        VBox info = new VBox(3);
        Label name = Ui.label(b.name(), "h2");
        Label price = Ui.label("₱" + String.format("%,.0f", b.price()) + " / mo", "price");
        HBox meta = Ui.hbox(8,
                Ui.label(b.distanceKm() > 0 ? Geo.fmt(b.distanceKm()) + " · " + Geo.walkMinutes(b.distanceKm()) + " min walk" : b.address(), "secondary"));
        HBox rating = Ui.hbox(6, Ui.label(Ui.stars(b.avgRating()), "stars"), Ui.label(String.format("%.1f (%d)", b.avgRating(), b.reviewCount()), "secondary"));
        HBox incl = Ui.hbox(6);
        if (b.water()) incl.getChildren().add(new Label("💧"));
        if (b.electricity()) incl.getChildren().add(new Label("⚡"));
        if (b.wifi()) incl.getChildren().add(new Label("📶"));
        incl.getChildren().add(Ui.label(b.availableBeds() + " bed" + (b.availableBeds() == 1 ? "" : "s") + " left", "secondary"));
        HBox badges = Ui.hbox(6);
        if (b.featured()) badges.getChildren().add(Ui.label("⭐ Featured", "badge-hot"));
        if (hot) badges.getChildren().add(Ui.label("🔥 Hot", "badge-hot"));
        if (b.ownerVerified()) badges.getChildren().add(Ui.label("✔ Verified Owner", "badge-verified"));
        info.getChildren().addAll(name, price, meta, rating, incl, badges);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button heart = new Button(favorite ? "♥" : "♡");
        heart.getStyleClass().add(favorite ? "heart-on" : "heart");
        final boolean[] on = {favorite};
        heart.setOnAction(e -> {
            on[0] = !on[0]; heart.setText(on[0] ? "♥" : "♡");
            heart.getStyleClass().setAll("button", on[0] ? "heart-on" : "heart");
            onFav.accept(on[0]);
        });
        VBox right = new VBox(heart);
        getChildren().addAll(Ui.photo(b.photoUrl(), 96, 110), info, right);
        setPadding(new Insets(10));
        setOnMouseClicked(e -> { if (e.getTarget() != heart) open.accept(b); });
    }
}
