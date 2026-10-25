package com.bofo.finder.ui;

import com.bofo.finder.model.Role;
import com.bofo.finder.service.AuthService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public final class LoginView extends ScrollPane {
    public LoginView(Runnable onSignedIn, String notice) {
        VBox col = new VBox(14); col.setAlignment(Pos.TOP_CENTER); col.setPadding(new Insets(48, 24, 24, 24)); col.setMaxWidth(440);
        Mascot mascot = new Mascot(Mascot.Expression.HAPPY, 120).bob();
        StackPane logo = new StackPane(new Circle(80, Color.web("#ECE9FF")), mascot);
        logo.setMaxSize(160, 160);
        Label title = Ui.label("Welcome to BOFO", "login-title"); center(title);
        Label tag = Ui.label("Find a safe, affordable boarding house near your campus.", "secondary"); center(tag);
        Label err = Ui.label(notice == null ? "" : notice, "error-text"); center(err);

        Label g = new Label("G"); g.getStyleClass().add("g-badge");
        Button google = new Button("Continue with Google"); google.setGraphic(g); google.setGraphicTextGap(10);
        google.getStyleClass().add("btn-primary"); google.setMaxWidth(Double.MAX_VALUE);
        Button cancel = Ui.btn("Cancel", "link", () -> AuthService.get().cancelSignIn());
        cancel.setVisible(false); cancel.setManaged(false);
        google.setOnAction(e -> {
            google.setDisable(true); err.setText(""); google.setText("Waiting for Google...");
            cancel.setVisible(true); cancel.setManaged(true);
            AuthService.get().signInWithGoogle().whenComplete((v, ex) -> javafx.application.Platform.runLater(() -> {
                google.setDisable(false); google.setText("Continue with Google");
                cancel.setVisible(false); cancel.setManaged(false);
                if (ex == null) { onSignedIn.run(); return; }
                Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                if (c instanceof java.util.concurrent.CancellationException || ex instanceof java.util.concurrent.CancellationException) return;   // user backed out
                ex.printStackTrace();
                err.setText("Sign-in failed: " + (c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage()));
            }));
        });
        VBox.setMargin(google, new Insets(18, 0, 0, 0));

        // DEV ONLY (remove before release): force login without Google, pick a role
        Button fs = Ui.btn("Student", "chip", () -> forceLogin(Role.STUDENT, err));
        Button fo = Ui.btn("Owner", "chip", () -> forceLogin(Role.OWNER, err));
        Button fa = Ui.btn("Admin", "chip", () -> forceLogin(Role.ADMIN, err));
        HBox devRow = Ui.hbox(8, fs, fo, fa); devRow.setAlignment(Pos.CENTER);

        Label legal = Ui.label("By continuing you agree to our", "secondary"); center(legal);
        Button terms = Ui.btn("Terms of Service", "link", () -> LegalOverlay.open(LegalOverlay.Doc.TERMS));
        Button priv = Ui.btn("Privacy Policy", "link", () -> LegalOverlay.open(LegalOverlay.Doc.PRIVACY));
        HBox legalRow = Ui.hbox(2, terms, Ui.label("and", "secondary"), priv); legalRow.setAlignment(Pos.CENTER);

        col.getChildren().addAll(logo, title, tag, google, cancel, err, Ui.divider("Developer: test accounts (real sign-in)"), devRow, Ui.grow(), legal, legalRow);
        StackPane holder = new StackPane(col); holder.setAlignment(Pos.TOP_CENTER);
        setContent(holder); setFitToWidth(true); setFitToHeight(true);        // scrolls on short / landscape screens
        setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }

    private static void center(Label l) { l.setAlignment(Pos.CENTER); l.setMaxWidth(Double.MAX_VALUE); l.setStyle("-fx-text-alignment:center;"); }

    /** DEV ONLY: signs in to Supabase with the role's test account (online), then opens the matching home. */
    private static void forceLogin(Role r, Label err) {
        err.setText("Signing in as " + r.db() + "...");
        com.bofo.finder.util.Async.run(() -> { AuthService.get().devSignIn(r); return null; },
                v -> com.bofo.finder.app.Main.route(),
                e -> err.setText(com.bofo.finder.util.Connectivity.isNetworkError(e)
                        ? "No internet connection. Check your network and try again."
                        : "Dev sign-in failed: " + e.getMessage()));
    }
}
