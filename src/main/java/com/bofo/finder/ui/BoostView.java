package com.bofo.finder.ui;

import com.bofo.finder.app.Main;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.BoostService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Cache;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Owner: make a listing "Featured" (shown first to students, with a ⭐ badge) and pay with GCash. */
public final class BoostView extends VBox {
    private final BoostService svc = new BoostService();
    private final VBox body = new VBox(12);
    private final Label status = Ui.label("", "secondary");
    private final BoardingHouse bh;
    private final Runnable onBack;
    private ScheduledExecutorService poll;
    private String pendingPaymentId;
    private int polls = 0;

    public BoostView(BoardingHouse bh, Runnable onBack) {
        this.bh = bh; this.onBack = onBack;
        Button back = Ui.btn("←", "heart", () -> { stopPoll(); onBack.run(); });
        back.setStyle("-fx-text-fill:white;-fx-font-size:20px;-fx-background-color:transparent;");
        HBox bar = Ui.appBar("Boost listing"); bar.getChildren().add(0, back);
        body.setPadding(new Insets(16, 16, 90, 16));
        getChildren().addAll(bar, Ui.scroll(body));

        body.getChildren().addAll(
                Ui.label(bh.name(), "title"),
                Ui.label("Featured listings appear at the top of Home and Explore (up to 3 per list) with a ⭐ Featured badge, "
                        + "so more students see your boarding house. A boost does not change your rating or reviews.", "label"));
        Async.run(svc::plans, this::showPlans, e -> body.getChildren().add(Ui.label("Could not load plans. Check your connection.", "error-text")));
    }

    private void showPlans(JSONArray plans) {
        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.getJSONObject(i);
            Button buy = Ui.btn("Pay ₱" + String.format("%,.0f", p.getDouble("price_php")) + " with GCash", "btn-cta", () -> {});
            buy.setMaxWidth(Double.MAX_VALUE);
            buy.setOnAction(e -> startCheckout(p.getString("id"), buy));
            VBox card = new VBox(6, Ui.label(p.getString("name"), "h2"), Ui.label("Featured for " + p.getInt("days") + " days", "secondary"), buy);
            card.getStyleClass().add("card"); body.getChildren().add(card);
        }
        body.getChildren().addAll(status,
                Ui.label("You will be redirected to GCash to approve the payment. Your boost starts automatically once the payment is confirmed. "
                        + "Boosts are non-refundable once active, except for payment errors or if BOFO removes your listing. "
                        + "Featured listings must follow the Community Guidelines.", "secondary"),
                Ui.label("Payment history", "h2"));
        loadHistory();
    }

    private void startCheckout(String planId, Button btn) {
        btn.setDisable(true); status.setText("Creating GCash payment…");
        Async.run(() -> svc.checkout(bh.id(), planId), r -> {
            if (r.has("error")) { status.setText(r.optString("error")); btn.setDisable(false); return; }
            pendingPaymentId = r.getString("payment_id");
            status.setText("Complete the payment in GCash, then return here. Ref: " + r.optString("reference"));
            Main.host().showDocument(r.getString("checkout_url"));
            startPoll(btn);
        }, e -> { status.setText("Could not start the payment. Verified owners only, listing must be active."); btn.setDisable(false); });
    }

    /** Watches the payment row (set by the webhook). Stops after ~10 min; the history list always shows the final status. */
    private void startPoll(Button btn) {
        stopPoll(); polls = 0;
        poll = Async.scheduler();
        poll.scheduleWithFixedDelay(() -> {
            try {
                String st = svc.status(pendingPaymentId);
                if (++polls > 200 || !st.equals("pending")) {
                    stopPoll();
                    Platform.runLater(() -> {
                        btn.setDisable(false);
                        if (st.equals("paid")) { Cache.remove("list:"); Nav.toast("Payment received! Your listing is now Featured ⭐"); onBack.run(); }
                        else if (st.equals("pending")) status.setText("Still waiting for payment. You can check history below.");
                        else status.setText("Payment " + st + ". You were not charged.");
                    });
                }
            } catch (Exception ignored) { /* transient network error: next tick retries */ }
        }, 3, 3, TimeUnit.SECONDS);
    }

    private void stopPoll() { if (poll != null) poll.shutdownNow(); }

    private void loadHistory() {
        Async.run(svc::myPayments, a -> {
            if (a.isEmpty()) body.getChildren().add(Ui.label("No payments yet.", "secondary"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject p = a.getJSONObject(i);
                String icon = switch (p.getString("status")) { case "paid" -> "✔"; case "pending" -> "⏳"; case "refunded" -> "↩"; default -> "✖"; };
                body.getChildren().add(Ui.label(icon + " ₱" + String.format("%,.0f", p.getDouble("amount_php")) + " · " + p.getString("plan_id")
                        + " · " + p.getString("created_at").substring(0, 10) + "\n" + p.getString("reference_number"), "secondary"));
            }
        }, e -> {});
    }
}
