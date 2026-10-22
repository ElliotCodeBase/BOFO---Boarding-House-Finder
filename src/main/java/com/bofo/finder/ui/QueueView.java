package com.bofo.finder.ui;

import com.bofo.finder.service.VerificationService;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Admin: pending verifications; side-by-side ID + selfie via short-lived signed URLs; approve/reject with reason. */
public final class QueueView extends VBox {
    private final VerificationService svc = new VerificationService();
    private final VBox rows = new VBox(10);

    public QueueView() {
        rows.setPadding(new Insets(12, 16, 72, 16));
        getChildren().addAll(Ui.appBar("Verification queue"), Ui.scroll(rows));
        load();
    }

    private void load() {
        rows.getChildren().clear();
        Async.run(svc::pending, (JSONArray a) -> {
            if (a.isEmpty()) rows.getChildren().add(Ui.label("Queue is empty 🎉", "secondary"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject v = a.getJSONObject(i);
                Button open = Ui.btn("Review " + v.optString("id_type") + " · " + v.optString("submitted_at").replace('T', ' ').substring(0, 16), "btn-outline", () -> review(v));
                rows.getChildren().add(open);
            }
        }, e -> rows.getChildren().add(Ui.label("Could not load queue.", "error-text")));
    }

    private void review(JSONObject v) {
        rows.getChildren().clear();
        Async.run(() -> new String[]{svc.signed(v.getString("id_front_url")), svc.signed(v.getString("id_back_url")), svc.signed(v.getString("selfie_url"))}, urls -> {
            HBox side = new HBox(8, Ui.photo(urls[0], 130, 100), Ui.photo(urls[2], 130, 100));
            rows.getChildren().addAll(Ui.label("ID front  |  Selfie", "h2"), side, Ui.label("ID back", "h2"), Ui.photo(urls[1], 270, 170));
            Button ok = Ui.btn("Approve", "btn-primary", () -> decide(v, true, null));
            Button no = Ui.btn("Reject", "btn-danger", () -> {
                TextInputDialog d = new TextInputDialog(); d.setHeaderText("Reason for rejection (shown to the owner)");
                d.showAndWait().ifPresent(r -> { if (!r.isBlank()) decide(v, false, r); });
            });
            rows.getChildren().addAll(Ui.hbox(10, ok, no), Ui.btn("← Back to queue", "chip", this::load));
        }, e -> Nav.toast("Could not load images"));
    }

    private void decide(JSONObject v, boolean approve, String reason) {
        Async.run(() -> { svc.decide(v.getString("id"), approve, reason); return true; },   // RPC writes the audit log atomically
                ok -> { Nav.toast(approve ? "Approved" : "Rejected"); load(); }, e -> Nav.toast("Failed"));
    }
}
