package com.bofo.finder.ui;

import com.bofo.finder.service.BoostService;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import org.json.JSONArray;
import org.json.JSONObject;

/** Admin: all boost payments. Refund = bookkeeping + ends the boost (refund the money in the PayMongo dashboard). */
public final class PaymentsView extends VBox {
    private final BoostService svc = new BoostService();
    private final VBox rows = new VBox(8);

    public PaymentsView() {
        rows.setPadding(new Insets(12, 16, 72, 16));
        getChildren().addAll(Ui.appBar("Payments"), Ui.scroll(rows));
        load();
    }

    private void load() {
        Ui.load(rows, () -> Skeleton.rows(6), svc::allPayments, (JSONArray a) -> {
            double total = 0;
            for (int i = 0; i < a.length(); i++) if ("paid".equals(a.getJSONObject(i).getString("status"))) total += a.getJSONObject(i).getDouble("amount_php");
            rows.getChildren().add(Ui.label("Paid (last 100): ₱" + String.format("%,.2f", total), "h2"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject p = a.getJSONObject(i);
                VBox c = new VBox(4, Ui.label(p.getString("status").toUpperCase() + " · ₱" + String.format("%,.2f", p.getDouble("amount_php")) + " · " + p.getString("plan_id"), "label"),
                        Ui.label(p.getString("reference_number") + " · " + p.getString("created_at").substring(0, 16).replace('T', ' '), "secondary"));
                if ("paid".equals(p.getString("status"))) {
                    c.getChildren().add(Ui.btn("Mark refunded", "btn-outline", () -> {
                        TextInputDialog d = new TextInputDialog(); d.setHeaderText("Refund reason (logged)");
                        d.showAndWait().ifPresent(r -> { if (!r.isBlank()) Async.run(() -> { svc.refund(p.getString("id"), r); return true; }, ok -> load(), e -> Ui.failToast(e, "Failed")); });
                    }));
                }
                c.getStyleClass().add("card"); rows.getChildren().add(c);
            }
        });
    }
}
