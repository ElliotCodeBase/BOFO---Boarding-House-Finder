package com.bofo.finder.ui;

import com.bofo.finder.service.AuthService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class LoginView extends VBox {
    public LoginView(Runnable onSignedIn, String notice) {
        super(16); setAlignment(Pos.CENTER); setPadding(new javafx.geometry.Insets(28));
        Mascot mascot = new Mascot(Mascot.Expression.HAPPY, 130).bob();
        Label title = Ui.label("BOFO", "title");
        Label tag = Ui.label("Find your boarding house near campus.", "secondary");
        Label err = Ui.label(notice == null ? "" : notice, "error-text");
        Button google = new Button("Continue with Google"); google.getStyleClass().add("btn-google"); google.setMaxWidth(Double.MAX_VALUE);
        google.setOnAction(e -> {
            google.setDisable(true); err.setText("");
            AuthService.get().signInWithGoogle().whenComplete((v, ex) -> javafx.application.Platform.runLater(() -> {
                google.setDisable(false);
                if (ex != null) err.setText("Sign-in failed. Please try again."); else onSignedIn.run();
            }));
        });
        Label legal = Ui.label("By continuing you agree to the Terms of Service and Privacy Policy.", "secondary");
        legal.setAlignment(Pos.CENTER);
        Button terms = Ui.btn("Terms", "chip", () -> Nav.toast("Open Terms (host the document, then HostServices.showDocument)"));
        Button priv = Ui.btn("Privacy Policy", "chip", () -> Nav.toast("Open Privacy Policy"));
        getChildren().addAll(mascot, title, tag, google, err, legal, Ui.hbox(8, terms, priv));
    }
}
