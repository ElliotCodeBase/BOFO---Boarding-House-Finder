package com.bofo.finder.ui;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.VerificationService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Capture;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.json.JSONObject;

/**
 * Owner verification: status -> consent -> ID front/back (camera) -> live selfie (camera ONLY, no gallery) -> submit.
 * Manual admin review; no ML. Blur/size check is a cheap pixel heuristic.
 */
public final class VerificationView extends VBox {
    private final VerificationService svc = new VerificationService();
    private final VBox body = new VBox(12);
    private byte[] front, back, selfie;
    private ComboBox<String> idType;

    public VerificationView() {
        body.setPadding(new Insets(16, 16, 90, 16));
        getChildren().addAll(Ui.appBar("Verification"), Ui.scroll(body));
        Async.run(() -> svc.myLatest(AuthService.get().userId()), this::showStatus,
                e -> body.getChildren().add(Ui.label("Could not load status.", "error-text")));
    }

    private void showStatus(JSONObject v) {
        body.getChildren().clear();
        String s = v.optString("status", "unverified");
        switch (s) {
            case "verified" -> body.getChildren().add(Ui.label("✔ Verified Owner. Students see your badge.", "badge-verified"));
            case "pending" -> body.getChildren().add(Ui.label("⏳ Pending review. We'll notify you when an admin decides.", "card"));
            case "rejected" -> {
                body.getChildren().addAll(Ui.label("✖ Rejected", "error-text"), Ui.label("Reason: " + v.optString("rejection_reason", "Not specified"), "card"),
                        Ui.btn("Resubmit", "btn-primary", this::consent));
            }
            default -> {
                body.getChildren().addAll(Ui.label("Verify your identity to publish listings.", "h2"),
                        Ui.label("An admin will manually compare your valid ID with a live selfie.", "secondary"), Ui.btn("Start verification", "btn-primary", this::consent));
            }
        }
    }

    private void consent() {
        body.getChildren().clear();
        CheckBox agree = new CheckBox("I consent to the collection and processing of my ID and selfie for identity verification, and I confirm the ID is mine and genuine.");
        agree.setWrapText(true);
        Button next = Ui.btn("Continue", "btn-primary", this::capture);
        next.disableProperty().bind(agree.selectedProperty().not());
        body.getChildren().addAll(Ui.label("Before we scan", "h2"),
                Ui.label("Why: to confirm owners are real people. What: front and back of your ID, and one live selfie. Who can see it: only authorized admins. Retention: deleted after review per our Privacy Policy. You may request access, correction or deletion at any time (Data Privacy Act of 2012).", "label"),
                agree, next);
    }

    private void capture() {
        body.getChildren().clear();
        idType = new ComboBox<>(); idType.getItems().addAll("Driver's License", "UMID", "Passport", "PhilID (National ID)", "Postal ID", "Voter's ID"); idType.getSelectionModel().selectFirst();
        Label err = Ui.label("", "error-text");
        ImageView pf = preview(), pb = preview(), ps = preview();
        Button submit = Ui.btn("Submit for review", "btn-cta", () -> {});
        submit.setDisable(true);
        Runnable check = () -> submit.setDisable(front == null || back == null || selfie == null);
        body.getChildren().addAll(Ui.label("Capture your ID", "h2"), Ui.label("Fit the card inside the frame. Avoid glare and blur.", "secondary"), idType,
                step("ID – front", pf, img -> front = grab(img, pf, err), check),
                step("ID – back", pb, img -> back = grab(img, pb, err), check),
                step("Live selfie (camera only)", ps, img -> selfie = grab(img, ps, err), check),
                err, submit);
        submit.setOnAction(e -> { submit.setDisable(true);
            Async.run(() -> { svc.submit(AuthService.get().userId(), idType.getValue(), front, back, selfie, AppConfig.get("policy.owner.version")); return true; },
                    ok -> { Nav.toast("Submitted! Status: pending"); showStatus(new JSONObject().put("status", "pending")); },
                    ex -> { err.setText("Upload failed: " + ex.getMessage()); submit.setDisable(false); }); });
    }

    private ImageView preview() { ImageView iv = new ImageView(); iv.setFitWidth(280); iv.setFitHeight(170); iv.setPreserveRatio(true); return iv; }

    private VBox step(String title, ImageView pv, java.util.function.Consumer<Image> onShot, Runnable changed) {
        StackPane frame = new StackPane(pv);
        frame.setPrefSize(290, 180); frame.setMinHeight(180);
        frame.setStyle("-fx-border-color:#5B34B8;-fx-border-style:dashed;-fx-border-width:2;-fx-border-radius:12;");  // on-screen frame guide
        Button shoot = Ui.btn("📷 Take photo / Retake", "btn-outline", () -> Capture.camera().ifPresent(img -> { onShot.accept(img); changed.run(); }));
        return new VBox(6, Ui.label(title, "h2"), frame, shoot);
    }

    private byte[] grab(Image img, ImageView pv, Label err) {
        if (!VerificationService.looksSharp(img)) { err.setText("That photo looks blurry or too small. Please retake."); pv.setImage(null); return null; }
        err.setText(""); pv.setImage(img); return Capture.png(img, 1600);
    }
}
