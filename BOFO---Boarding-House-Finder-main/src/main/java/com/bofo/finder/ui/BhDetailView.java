package com.bofo.finder.ui;

import com.bofo.finder.app.Main;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Geo;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** BH detail: photos, rooms, inclusions, map + route line from the college, contact, chat, reviews. */
public final class BhDetailView extends VBox {
    private final BhService bh = new BhService();
    private final VBox body = new VBox(12);

    public BhDetailView(String id, Runnable onBack) {
        Button back = Ui.btn("←", "heart", onBack); back.setStyle("-fx-text-fill:white;-fx-font-size:20px;-fx-background-color:transparent;");
        getChildren().addAll(Ui.appBar("Boarding house", back), Ui.scroll(body));
        VBox.setVgrow(getChildren().get(1), Priority.ALWAYS);
        body.setPadding(new Insets(0, 0, 40, 0));
        Async.run(() -> bh.detail(id), this::render, e -> body.getChildren().add(Ui.label("Could not load this listing.", "error-text")));
    }

    private void render(JSONObject d) {
        BoardingHouse b = BoardingHouse.from(d);
        JSONArray photos = d.getJSONArray("photos");
        HBox strip = new HBox(8); strip.setPadding(new Insets(0, 16, 0, 16));
        for (int i = 0; i < photos.length(); i++) strip.getChildren().add(Ui.photo(photos.getJSONObject(i).getString("url"), 260, 180));
        ScrollPane photoScroll = new ScrollPane(strip); photoScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); photoScroll.setPrefHeight(190);

        double[] ref = State.ref();
        double km = ref == null ? 0 : Geo.km(ref[0], ref[1], b.lat(), b.lng());
        VBox info = Ui.padded(8,
                Ui.label(b.name(), "title"), Ui.label(b.address(), "secondary"),
                Ui.label("₱" + String.format("%,.0f", b.price()) + " / month (" + d.optString("price_mode", "per_bed").replace('_', ' ') + ")", "price"),
                Ui.hbox(8, Ui.label(Ui.stars(b.avgRating()), "stars"), Ui.label(String.format("%.1f · %d reviews", b.avgRating(), b.reviewCount()), "secondary")),
                b.ownerVerified() ? Ui.label("✔ Verified Owner", "badge-verified") : new Label(),
                Ui.label((b.water() ? "💧 Free water   " : "") + (b.electricity() ? "⚡ Free electricity   " : "") + (b.wifi() ? "📶 Free WiFi" : ""), "label"),
                Ui.label(b.availableBeds() + " beds available", "h2"));
        if (ref != null) info.getChildren().add(Ui.label("📍 " + Geo.fmt(km) + " from " + (State.college.get() != null ? State.college.get().name() : "you") + " · ~" + Geo.walkMinutes(km) + " min walk", "secondary"));

        JSONArray rooms = d.getJSONArray("rooms");
        VBox roomBox = new VBox(4);
        for (int i = 0; i < rooms.length(); i++) {
            JSONObject r = rooms.getJSONObject(i);
            roomBox.getChildren().add(Ui.label("Room " + (i + 1) + ": " + r.getInt("beds") + " beds · " + r.getInt("occupants") + " occupied", "label"));
        }

        MapPane map = new MapPane(200);
        map.show(ref != null ? ref : new double[]{b.lat(), b.lng()}, 0, java.util.List.of(b), false, ref == null ? null : new double[]{b.lat(), b.lng()});

        JSONObject contact = d.getJSONArray("contact").isEmpty() ? new JSONObject() : d.getJSONArray("contact").getJSONObject(0);
        HBox actions = Ui.hbox(10,
                Ui.btn("📞 Call", "btn-outline", () -> { String p = contact.optString("phone"); if (!p.isBlank()) Main.host().showDocument("tel:" + p); else Nav.toast("No phone listed"); }),
                Ui.btn("💬 Chat with owner", "btn-primary", () -> openChat(b)));
        actions.setPadding(new Insets(0, 16, 0, 16));

        VBox reviews = new VBox(8); reviews.setPadding(new Insets(0, 16, 0, 16));
        reviews.getChildren().add(Ui.label("Reviews", "h2"));
        Async.run(() -> bh.reviews(b.id()), arr -> {
            if (arr.isEmpty()) reviews.getChildren().add(Ui.label("No reviews yet.", "secondary"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.getJSONObject(i);
                VBox card = new VBox(2, Ui.label(Ui.stars(r.getInt("rating")), "stars"), Ui.label(r.optString("comment", ""), "label"));
                if (!r.isNull("owner_reply")) card.getChildren().add(Ui.label("Owner: " + r.getString("owner_reply"), "secondary"));
                card.getStyleClass().add("card"); reviews.getChildren().add(card);
            }
        }, e -> {});
        if (AuthService.get().profile().role() == com.bofo.finder.model.Role.STUDENT) reviews.getChildren().add(reviewForm(b.id()));

        body.getChildren().addAll(photoScroll, info, wrap(roomBox), map, actions, reviews);
    }

    private VBox wrap(VBox v) { v.setPadding(new Insets(0, 16, 0, 16)); return v; }

    private javafx.scene.Node reviewForm(String bhId) {
        ComboBox<Integer> stars = new ComboBox<>(); stars.getItems().addAll(5, 4, 3, 2, 1); stars.getSelectionModel().selectFirst();
        TextArea text = new TextArea(); text.setPromptText("Write a review (optional)"); text.setPrefRowCount(2);
        Button post = Ui.btn("Post review", "btn-cta", () -> {
            String uid = AuthService.get().userId();
            Async.run(() -> { bh.upsertReview(bhId, uid, stars.getValue(), text.getText()); return true; },
                    ok -> Nav.toast("Thanks for your review!"), e -> Nav.toast("You can only review a place once."));
        });
        return new VBox(6, Ui.label("Your rating", "h2"), stars, text, post);
    }

    private void openChat(BoardingHouse b) {
        Async.run(() -> new ChatService().openConversation(b.id()), cid ->
                Nav.show(new ChatView(cid, b.name(), b.photoUrl(), b.ownerId(), () -> Nav.show(new StudentShell(), true)), true),
                e -> Nav.toast("Chat unavailable (owner may not be verified)"));
    }
}
