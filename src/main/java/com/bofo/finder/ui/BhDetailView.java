package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Geo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** BH detail: photo hero, price + status, quick facts, amenities, rooms, directions (map + ways to get there), reviews, chat. */
public final class BhDetailView extends VBox {
    private final BhService bh = new BhService();
    private final VBox body = new VBox(14);

    public BhDetailView(String id, Runnable onBack) {
        Button back = Ui.btn("←", "heart", onBack); back.getStyleClass().setAll("button", "icon-btn");
        getChildren().addAll(Ui.appBar("Boarding house", back), Ui.scroll(body));
        VBox.setVgrow(getChildren().get(1), Priority.ALWAYS);
        body.setPadding(new Insets(4, 0, 96, 0));           // the floating mascot sits over the bottom-right corner: let the last card scroll above it
        Ui.load(body, Skeleton::detail, () -> bh.detail(id), this::render);
    }

    private void render(JSONObject d) {
        BoardingHouse b = BoardingHouse.from(d);
        double[] ref = State.ref();
        double km = ref == null ? 0 : Geo.km(ref[0], ref[1], b.lat(), b.lng());

        /* ---- 1. hero: photos, or a friendly placeholder when the owner added none ---- */
        JSONArray photos = d.getJSONArray("photos");
        Node hero;
        if (photos.length() == 0) {
            Region bg = new Region(); bg.getStyleClass().add("hero-empty"); bg.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            Label hint = Ui.label("No photos yet", "secondary"); hint.setTextAlignment(TextAlignment.CENTER);
            VBox inner = new VBox(8, Icons.icon(Icons.HOME, 52, Color.web("#B9ACF7")), hint); inner.setAlignment(Pos.CENTER);
            StackPane ph = new StackPane(bg, inner); ph.setPrefHeight(210); ph.setMinHeight(210);
            VBox.setMargin(ph, new Insets(0, 16, 0, 16));
            hero = ph;
        } else {
            double w = photos.length() == 1 ? 340 : 300;
            HBox strip = new HBox(10); strip.setPadding(new Insets(0, 16, 0, 16));
            for (int i = 0; i < photos.length(); i++) strip.getChildren().add(Ui.photoLoading(photos.getJSONObject(i).getString("url"), w, 220));
            ScrollPane sp = new ScrollPane(strip);
            sp.getStyleClass().add("quick-row");
            sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            sp.setFitToHeight(true); sp.setMinHeight(224); sp.setPrefHeight(224); sp.setMaxHeight(224);
            sp.addEventFilter(ScrollEvent.SCROLL, e -> {               // mouse wheel scrolls the photos sideways
                double span = strip.getWidth() - sp.getViewportBounds().getWidth();
                if (span > 0 && e.getDeltaY() != 0) { sp.setHvalue(Math.max(0, Math.min(1, sp.getHvalue() - e.getDeltaY() / span))); e.consume(); }
            });
            hero = sp;
        }

        /* ---- 2. headline: name, address, price, status pills ---- */
        Label name = Ui.label(b.name(), "title");
        HBox where = Ui.hbox(6, Ui.strokeIcon(Glyphs.PIN, 16, Color.web("#8D8AA3")), Ui.label(b.address(), "secondary"));
        Label price = new Label("\u20B1" + String.format("%,.0f", b.price()));
        price.setStyle("-fx-font-size:28px;-fx-font-weight:bold;-fx-text-fill:#5B3BF0;");
        Label per = Ui.label("/ month \u00B7 " + d.optString("price_mode", "per_bed").replace("per_", "per ").replace('_', ' '), "secondary");
        HBox priceRow = new HBox(8, price, per); priceRow.setAlignment(Pos.BASELINE_LEFT);
        FlowPane pills = new FlowPane(8, 8);
        if (b.ownerVerified()) pills.getChildren().add(Ui.label("\u2713 Verified owner", "badge-verified"));
        int free = b.availableBeds();
        pills.getChildren().add(Ui.label(free > 0 ? free + (free == 1 ? " bed available" : " beds available") : "Fully booked", free > 0 ? "pill-ok" : "pill-full"));
        if (ref != null) pills.getChildren().add(Ui.label(Geo.walkText(km), "tag"));
        VBox head = new VBox(8, name, where, priceRow, pills);
        head.setPadding(new Insets(0, 16, 0, 16));

        /* ---- 3. quick facts ---- */
        JSONArray rooms = d.getJSONArray("rooms");
        HBox stats = new HBox(10,
                stat(String.valueOf(free), free == 1 ? "Bed free" : "Beds free"),
                stat(String.valueOf(rooms.length()), rooms.length() == 1 ? "Room" : "Rooms"),
                stat(b.reviewCount() == 0 ? "New" : String.format("%.1f \u2605", b.avgRating()), b.reviewCount() == 0 ? "No reviews" : b.reviewCount() + (b.reviewCount() == 1 ? " review" : " reviews")));
        stats.setPadding(new Insets(0, 16, 0, 16));

        /* ---- 4. amenities ---- */
        JSONArray amenityList = d.optJSONArray("amenity_list");
        FlowPane amenityPills = new FlowPane(8, 8);
        if (amenityList != null) for (int i = 0; i < amenityList.length(); i++) {
            JSONObject a = amenityList.getJSONObject(i).optJSONObject("amenities");
            if (a != null) amenityPills.getChildren().add(Ui.label(com.bofo.finder.model.Amenity.from(a).label(), "amenity-pill"));
        }
        if (amenityPills.getChildren().isEmpty()) {                       // places without amenity rows keep the old three flags
            if (b.water()) amenityPills.getChildren().add(Ui.label("\uD83D\uDCA7 Free water", "amenity-pill"));
            if (b.electricity()) amenityPills.getChildren().add(Ui.label("\u26A1 Free electricity", "amenity-pill"));
            if (b.wifi()) amenityPills.getChildren().add(Ui.label("\uD83D\uDCF6 Free Wi-Fi", "amenity-pill"));
        }

        /* ---- 5. rooms ---- */
        VBox roomRows = new VBox(0);
        for (int i = 0; i < rooms.length(); i++) {
            JSONObject r = rooms.getJSONObject(i);
            int beds = r.getInt("beds"), taken = r.getInt("occupants"), left = Math.max(0, beds - taken);
            Label title = Ui.label("Room " + (i + 1), "h2"); title.setStyle("-fx-font-size:15px;");
            Label sub = Ui.label(beds + (beds == 1 ? " bed" : " beds") + " \u00B7 " + taken + " taken", "secondary");
            VBox text = new VBox(2, title, sub); HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(10, text, Ui.label(left > 0 ? left + " free" : "Full", left > 0 ? "pill-ok" : "pill-full"));
            row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(10, 0, 10, 0));
            if (i > 0) row.setStyle("-fx-border-color:#E7E8F2;-fx-border-width:1 0 0 0;");
            roomRows.getChildren().add(row);
        }

        /* ---- 6. directions: map + the real ways to get here (walk, tricycle, jeepney, bus/van) with time and fare ---- */
        DirectionsCard directions = new DirectionsCard(b);
        VBox.setMargin(directions, new Insets(0, 16, 0, 16));

        /* ---- 7. reviews ---- */
        VBox reviewList = new VBox(0);
        Label reviewSummary = Ui.label(b.reviewCount() == 0 ? "No reviews yet" : String.format("%.1f \u2605 \u00B7 %d %s", b.avgRating(), b.reviewCount(), b.reviewCount() == 1 ? "review" : "reviews"), "secondary");
        VBox reviews = new VBox(10, Ui.section("Reviews"), reviewSummary, reviewList);
        Async.run(() -> bh.reviews(b.id()), arr -> {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.getJSONObject(i);
                VBox item = new VBox(3, Ui.label(Ui.stars(r.getInt("rating")), "stars"));
                String comment = r.optString("comment", "");
                if (!comment.isBlank()) item.getChildren().add(Ui.label(comment, "label"));
                if (!r.isNull("owner_reply")) item.getChildren().add(Ui.label("Owner: " + r.getString("owner_reply"), "secondary"));
                item.setPadding(new Insets(10, 0, 10, 0));
                if (i > 0) item.setStyle("-fx-border-color:#E7E8F2;-fx-border-width:1 0 0 0;");
                reviewList.getChildren().add(item);
            }
        }, e -> {});
        if (AuthService.get().profile().role() == com.bofo.finder.model.Role.STUDENT) reviews.getChildren().add(reviewForm(b.id()));

        /* ---- page ---- */
        body.getChildren().addAll(hero, head, stats);
        if (!amenityPills.getChildren().isEmpty()) body.getChildren().add(card(Ui.section("Amenities"), amenityPills));
        if (rooms.length() > 0) body.getChildren().add(card(Ui.section("Rooms"), roomRows));
        body.getChildren().addAll(directions, card(reviews));

        Button chatBtn = Ui.btn("Chat with owner", "btn-primary", () -> openChat(b));
        chatBtn.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(chatBtn, Priority.ALWAYS);
        HBox actions = Ui.hbox(10, chatBtn); actions.getStyleClass().add("action-bar");
        getChildren().add(actions);   // sticky bottom bar
    }

    /** White rounded section card with the page's side margins. */
    private static VBox card(Node... n) {
        VBox c = new VBox(10, n); c.getStyleClass().add("card");
        VBox.setMargin(c, new Insets(0, 16, 0, 16));
        return c;
    }

    /** One of the three small fact tiles under the price. */
    private static VBox stat(String value, String caption) {
        Label v = Ui.label(value, "h2"); v.setTextAlignment(TextAlignment.CENTER);
        Label c = Ui.label(caption, "secondary"); c.setTextAlignment(TextAlignment.CENTER);
        VBox t = new VBox(2, v, c); t.setAlignment(Pos.CENTER); t.getStyleClass().add("stat-tile");
        t.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(t, Priority.ALWAYS);
        return t;
    }

    private javafx.scene.Node reviewForm(String bhId) {
        ComboBox<Integer> stars = new ComboBox<>(); stars.getItems().addAll(5, 4, 3, 2, 1); stars.getSelectionModel().selectFirst();
        TextArea text = new TextArea(); text.setPromptText("Write a review (optional)"); text.setPrefRowCount(2);
        Button post = Ui.btn("Post review", "btn-cta", () -> {
            String uid = AuthService.get().userId();
            Async.run(() -> { bh.upsertReview(bhId, uid, stars.getValue(), text.getText()); return true; },
                    ok -> Nav.toast("Thanks for your review!"), e -> Ui.failToast(e, "You can only review a place once."));
        });
        return new VBox(6, Ui.label("Your rating", "h2"), stars, text, post);
    }

    private void openChat(BoardingHouse b) {
        Async.run(() -> new ChatService().openConversation(b.id()), cid ->
                Nav.show(new ChatView(cid, b.name(), b.photoUrl(), b.ownerId(), () -> Nav.show(new StudentShell(), true)), false),
                e -> Ui.failToast(e, "Chat unavailable (owner may not be verified)"));
    }
}
