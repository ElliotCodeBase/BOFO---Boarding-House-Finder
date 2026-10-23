package com.bofo.finder.ui;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.util.Async;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** First login: accept Terms + Privacy (required), then choose Student or Owner. Admin can never be chosen here. */
public final class RoleView extends VBox {
    public RoleView(Runnable onDone) {
        super(14); setAlignment(Pos.CENTER); setPadding(new javafx.geometry.Insets(24)); setMaxWidth(480);
        Label title = Ui.label("Welcome to BOFO!", "title");
        CheckBox agree = new CheckBox("I agree to the Terms of Service and Privacy Policy");
        agree.setWrapText(true);
        Label err = Ui.label("", "error-text");
        Button student = Ui.btn("I'm a Student", "btn-primary", () -> {});
        Button owner = Ui.btn("I'm a Boarding House Owner", "btn-outline", () -> {});
        student.setMaxWidth(Double.MAX_VALUE); owner.setMaxWidth(Double.MAX_VALUE);
        student.disableProperty().bind(agree.selectedProperty().not());
        owner.disableProperty().bind(agree.selectedProperty().not());
        student.setOnAction(e -> pick(Role.STUDENT, err, onDone));
        owner.setOnAction(e -> pick(Role.OWNER, err, onDone));
        getChildren().addAll(title, Ui.label("Choose how you'll use the app. Owners must verify their identity before posting.", "secondary"),
                agree, student, owner, err);
    }
    private void pick(Role r, Label err, Runnable done) {
        AuthService a = AuthService.get();
        Async.run(() -> { a.createProfile(r, a.googleName(), a.googlePhoto(),
                        AppConfig.get("policy.terms.version"), AppConfig.get("policy.privacy.version")); return true; },
                ok -> done.run(), ex -> err.setText("Could not save your role. Check your connection."));
    }
}
