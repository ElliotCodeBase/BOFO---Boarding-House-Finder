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
        getChildren().addAll(Ui.pageHeader("Verification queue", null), Ui.scroll(rows));
        load();
    }

    private void load() {
        Ui.load(rows, () -> Skeleton.rows(5), svc::pending, (JSONArray a) -> {
            if (a.isEmpty()) rows.getChildren().add(Ui.label("Queue is empty 🎉", "secondary"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject v = a.getJSONObject(i);
                Button open = Ui.btn("Review " + v.optString("id_type") + " · " + v.optString("submitted_at").replace('T', ' ').substring(0, 16), "btn-outline", () -> review(v));
                rows.getChildren().add(open);
            }
        });
    }

    private void review(JSONObject v) {
        Ui.load(rows, () -> Skeleton.rows(3), () -> new String[]{svc.signed(v.getString("id_front_url")), svc.signed(v.getString("id_back_url")), svc.signed(v.getString("selfie_url"))}, urls -> {
            javafx.scene.image.ImageView front = Ui.coverPhoto(urls[0], 640), selfie = Ui.coverPhoto(urls[2], 640), back = Ui.coverPhoto(urls[1], 960);
            front.setFitHeight(110); selfie.setFitHeight(110); back.setFitHeight(170);
            HBox side = new HBox(8, front, selfie);
            double pad = rows.getPadding().getLeft() + rows.getPadding().getRight();
            front.fitWidthProperty().bind(rows.widthProperty().subtract(pad + 8).divide(2));
            selfie.fitWidthProperty().bind(rows.widthProperty().subtract(pad + 8).divide(2));
            back.fitWidthProperty().bind(rows.widthProperty().subtract(pad));
            rows.getChildren().addAll(Ui.label("ID front  |  Selfie", "h2"), side, Ui.label("ID back", "h2"), back);
            Button ok = Ui.btn("Approve", "btn-primary", () -> decide(v, true, null));
            Button no = Ui.btn("Reject", "btn-danger", () -> {
                TextInputDialog d = new TextInputDialog(); d.setHeaderText("Reason for rejection (shown to the owner)");
                d.showAndWait().ifPresent(r -> { if (!r.isBlank()) decide(v, false, r); });
            });
            rows.getChildren().addAll(Ui.hbox(10, ok, no), Ui.btn("← Back to queue", "chip", this::load));
        });
    }

    private void decide(JSONObject v, boolean approve, String reason) {
        Async.run(() -> { svc.decide(v.getString("id"), approve, reason); return true; },   // RPC writes the audit log atomically
                ok -> { Nav.toast(approve ? "Approved" : "Rejected"); load(); }, e -> Ui.failToast(e, "Failed"));
    }
}
