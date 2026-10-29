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

        /* ---- 7. reviews: who rated (photo, name, stars, date, comment) + a form to rate ---- */
        VBox reviewList = new VBox(0);
        Label reviewSummary = Ui.label(b.reviewCount() == 0 ? "No reviews yet" : String.format("%.1f \u2605 \u00B7 %d %s", b.avgRating(), b.reviewCount(), b.reviewCount() == 1 ? "review" : "reviews"), "secondary");
        VBox reviews = new VBox(10, Ui.section("Reviews"), reviewSummary, reviewList);
        boolean student = AuthService.get().profile().role() == com.bofo.finder.model.Role.STUDENT;
        ReviewForm form = student ? new ReviewForm(b.id()) : null;
        Runnable[] reload = new Runnable[1];
        reload[0] = () -> Async.run(() -> bh.reviews(b.id()), arr -> {
            paintReviews(arr, reviewList, reviewSummary, form);
            if (form != null) form.loaded(arr);
        }, e -> {});
        if (form != null) { form.onPosted = reload[0]; reviews.getChildren().add(form); }
        else reviews.getChildren().add(Ui.label("Only students can rate a boarding house.", "secondary"));
        reload[0].run();

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

    /** The list of reviews and the "4.5 * 12 reviews" line, rebuilt from what the server returned. */
    private static void paintReviews(JSONArray arr, VBox list, Label summary, ReviewForm form) {
        list.getChildren().clear();
        String me = AuthService.get().userId();
        double sum = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.getJSONObject(i);
            sum += r.optInt("rating", 0);
            boolean mine = me != null && me.equals(r.optString("student_id", ""));
            String nm = r.isNull("reviewer_name") || r.optString("reviewer_name", "").isBlank() ? "Student" : r.getString("reviewer_name");
            String photo = r.isNull("reviewer_photo") ? "" : r.optString("reviewer_photo", "");
            Label who = Ui.label(mine ? nm + " (you)" : nm, "h2"); who.setStyle("-fx-font-size:14px;");
            Label when = Ui.label(when(r.optString("created_at", "")), "secondary");
            HBox line = new HBox(8, Ui.label(Ui.stars(r.optInt("rating", 0)), "stars"), when); line.setAlignment(Pos.CENTER_LEFT);
            VBox who2 = new VBox(1, who, line); who2.setMinWidth(0);
            HBox top = new HBox(10, Ui.avatar(nm, 38, photo), who2); top.setAlignment(Pos.CENTER_LEFT);
            VBox item = new VBox(6, top);
            String comment = r.isNull("comment") ? "" : r.optString("comment", "");
            if (!comment.isBlank()) { Label c = Ui.label(comment, "label"); c.setWrapText(true); item.getChildren().add(c); }
            if (!r.isNull("owner_reply") && !r.optString("owner_reply", "").isBlank()) {
                Label rep = Ui.label("Owner: " + r.getString("owner_reply"), "secondary"); rep.setWrapText(true); item.getChildren().add(rep);
            }
            item.setPadding(new Insets(12, 0, 12, 0));
            if (i > 0) item.setStyle("-fx-border-color:#E7E8F2;-fx-border-width:1 0 0 0;");
            list.getChildren().add(item);
        }
        int n = arr.length();
        summary.setText(n == 0 ? "No reviews yet" : String.format(java.util.Locale.ROOT, "%.1f \u2605 \u00B7 %d %s", Math.round(sum / n * 10) / 10.0, n, n == 1 ? "review" : "reviews"));
    }

    /** "2026-10-29T05:27:26+00:00" -> "Oct 29, 2026" (empty when it cannot be read). */
    private static String when(String iso) {
        try { return java.time.OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH)); }
        catch (Exception e) { return ""; }
    }

    /** Short, real reason a request failed (so "could not post" can be told apart from "no permission"). */
    private static String reason(Throwable e) {
        Throwable r = e; for (int i = 0; i < 8 && r.getCause() != null && r.getCause() != r; i++) r = r.getCause();
        String m = r.getMessage() == null ? r.getClass().getSimpleName() : r.getMessage().replaceAll("\\s+", " ");
        return m.length() > 140 ? m.substring(0, 140) + "\u2026" : m;
    }

    /** Five tappable stars + optional comment + Post button. Posting again edits your review; it fills in with your old one when it exists. */
    private final class ReviewForm extends VBox {
        private final String bhId;
        private final Label[] star = new Label[5];
        private final Label hint = Ui.label("Tap a star to rate", "secondary");
        private final TextArea text = new TextArea();
        private final Button post = Ui.btn("Post review", "btn-cta", () -> {});
        private int picked;
        private boolean existing, busy;
        Runnable onPosted = () -> {};

        ReviewForm(String bhId) {
            super(8);
            this.bhId = bhId;
            HBox row = new HBox(6); row.setAlignment(Pos.CENTER_LEFT);
            for (int i = 0; i < 5; i++) {
                final int v = i + 1;
                Label s = new Label("\u2605"); s.setCursor(javafx.scene.Cursor.HAND);
                s.setOnMouseClicked(e -> pick(v));
                s.setPadding(new Insets(2, 3, 2, 3));
                star[i] = s; row.getChildren().add(s);
            }
            text.setPromptText("Write a review (optional)"); text.setPrefRowCount(2); text.setWrapText(true);
            post.setMaxWidth(Double.MAX_VALUE);
            post.setOnAction(e -> submit());
            getChildren().addAll(Ui.label("Your rating", "h2"), row, hint, text, post);
            setPadding(new Insets(6, 0, 0, 0));
            paint();
        }

        private void pick(int v) { picked = v; paint(); }

        private void paint() {
            for (int i = 0; i < 5; i++) {
                star[i].setStyle("-fx-font-size:30px;-fx-text-fill:" + (i < picked ? "#F5A524" : "#D4D0E6") + ";");
            }
            String[] words = {"Tap a star to rate", "Poor", "Fair", "Good", "Very good", "Excellent"};
            hint.setText(words[picked]);
            post.setDisable(picked == 0 || busy);
            post.setText(busy ? "Posting..." : existing ? "Update review" : "Post review");
        }

        /** The reviews arrived: if one of them is mine, show it in the form so it can be edited. */
        void loaded(JSONArray arr) {
            String me = AuthService.get().userId();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.getJSONObject(i);
                if (me != null && me.equals(r.optString("student_id", ""))) {
                    existing = true;
                    if (picked == 0) picked = r.optInt("rating", 0);
                    if (text.getText().isEmpty() && !r.isNull("comment")) text.setText(r.optString("comment", ""));
                }
            }
            paint();
        }

        private void submit() {
            if (picked == 0 || busy) return;
            String uid = AuthService.get().userId();
            busy = true; paint();
            final int stars = picked; final String comment = text.getText();
            Async.run(() -> { bh.upsertReview(bhId, uid, stars, comment); return true; },
                    ok -> { busy = false; existing = true; paint(); Nav.toast("Thanks for your review!"); onPosted.run(); },
                    e -> { busy = false; paint(); Ui.failToast(e, "Could not post your review: " + reason(e)); });
        }
    }

    private void openChat(BoardingHouse b) {
        Async.run(() -> new ChatService().openConversation(b.id()), cid ->
                Nav.show(new ChatView(cid, b.name(), b.photoUrl(), b.ownerId(), false, () -> Nav.show(new StudentShell(), true)), false),
                e -> Ui.failToast(e, "Chat unavailable (owner may not be verified)"));
    }
}
