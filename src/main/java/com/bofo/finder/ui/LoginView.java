package com.bofo.finder.ui;

import com.bofo.finder.model.Role;
import com.bofo.finder.service.AuthService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public final class LoginView extends VBox {
    public LoginView(Runnable onSignedIn, String notice) {
        super(14); setAlignment(Pos.TOP_CENTER); setPadding(new Insets(48, 24, 24, 24));
        Mascot mascot = new Mascot(Mascot.Expression.HAPPY, 120).bob();
        StackPane logo = new StackPane(new Circle(80, Color.web("#EFEAFB")), mascot);
        logo.setMaxSize(160, 160);
        Label title = Ui.label("Welcome to BOFO", "login-title"); center(title);
        Label tag = Ui.label("Find a safe, affordable boarding house near your campus.", "secondary"); center(tag);
        Label err = Ui.label(notice == null ? "" : notice, "error-text"); center(err);

        Label g = new Label("G"); g.getStyleClass().add("g-badge");
        Button google = new Button("Continue with Google"); google.setGraphic(g); google.setGraphicTextGap(10);
        google.getStyleClass().add("btn-primary"); google.setMaxWidth(Double.MAX_VALUE);
        google.setOnAction(e -> {
            google.setDisable(true); err.setText("");
            AuthService.get().signInWithGoogle().whenComplete((v, ex) -> javafx.application.Platform.runLater(() -> {
                google.setDisable(false);
                if (ex != null) err.setText("Sign-in failed. Please try again."); else onSignedIn.run();
            }));
        });
        VBox.setMargin(google, new Insets(18, 0, 0, 0));

        // DEV ONLY (remove before release): force login without Google, pick a role
        Button fs = Ui.btn("Student", "chip", () -> forceLogin(Role.STUDENT));
        Button fo = Ui.btn("Owner", "chip", () -> forceLogin(Role.OWNER));
        Button fa = Ui.btn("Admin", "chip", () -> forceLogin(Role.ADMIN));
        HBox devRow = Ui.hbox(8, fs, fo, fa); devRow.setAlignment(Pos.CENTER);

        Label legal = Ui.label("By continuing you agree to our", "secondary"); center(legal);
        Button terms = Ui.btn("Terms of Service", "link", () -> Nav.toast("Open Terms (host the document, then HostServices.showDocument)"));
        Button priv = Ui.btn("Privacy Policy", "link", () -> Nav.toast("Open Privacy Policy"));
        HBox legalRow = Ui.hbox(2, terms, Ui.label("and", "secondary"), priv); legalRow.setAlignment(Pos.CENTER);

        getChildren().addAll(logo, title, tag, google, err, Ui.divider("Developer: force login"), devRow, Ui.grow(), legal, legalRow);
    }

    private static void center(Label l) { l.setAlignment(Pos.CENTER); l.setMaxWidth(Double.MAX_VALUE); l.setStyle("-fx-text-alignment:center;"); }

    private static void forceLogin(Role r) {
        AuthService.get().devForce(r);
        com.bofo.finder.app.Main.route();
    }
}
