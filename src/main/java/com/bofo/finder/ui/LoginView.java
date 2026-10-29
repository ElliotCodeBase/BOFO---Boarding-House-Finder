package com.bofo.finder.ui;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Validate;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Sign-in screen, all inside the app (works on phones, no browser tab):
 *  1. Email -> "Email me a code" -> type the 6-digit code (like the Claude app)   [default]
 *  2. Email + password (sign in / create account)
 *  3. Continue with Google (Google's sheet on Android when set up, browser otherwise)
 */
public final class LoginView extends ScrollPane {
    private enum Step { START, CODE, PASSWORD }

    private final Runnable onSignedIn;
    private final AuthService auth = AuthService.get();
    private final VBox body = new VBox(12);
    private final Label err;
    private String email = "";
    private boolean createMode;                   // password step: false = sign in, true = create account
    private boolean passwordSignUpPending;        // code step came from "Create account"
    private Timeline cooldown;

    public LoginView(Runnable onSignedIn, String notice) {
        this.onSignedIn = onSignedIn;
        VBox col = new VBox(14); col.setAlignment(Pos.TOP_CENTER); col.setPadding(new Insets(36, 24, 24, 24)); col.setMaxWidth(440);
        Mascot mascot = new Mascot(Mascot.Expression.HAPPY, 96).bob();
        StackPane logo = new StackPane(new Circle(64, Color.web("#ECE9FF")), mascot);
        logo.setMaxSize(128, 128);
        Label title = Ui.label("Welcome to BOFO", "login-title"); center(title);
        Label tag = Ui.label("Find a safe, affordable boarding house near your campus.", "secondary"); center(tag);
        err = Ui.label(notice == null ? "" : notice, "error-text"); center(err);
        body.setFillWidth(true);

        Label legal = Ui.label("By continuing you agree to our", "secondary"); center(legal);
        Button terms = Ui.btn("Terms of Service", "link", () -> LegalOverlay.open(LegalOverlay.Doc.TERMS));
        Button priv = Ui.btn("Privacy Policy", "link", () -> LegalOverlay.open(LegalOverlay.Doc.PRIVACY));
        HBox legalRow = Ui.hbox(2, terms, Ui.label("and", "secondary"), priv); legalRow.setAlignment(Pos.CENTER);

        col.getChildren().addAll(logo, title, tag, body, err);
        if (hasDevAccounts()) col.getChildren().addAll(Ui.divider("Developer: test accounts"), devRow());
        col.getChildren().addAll(Ui.grow(), legal, legalRow);

        StackPane holder = new StackPane(col); holder.setAlignment(Pos.TOP_CENTER);
        setContent(holder); setFitToWidth(true); setFitToHeight(true);        // scrolls on short / landscape screens and above the keyboard
        setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        show(Step.START);
    }

    /* ---------------------------------- steps ---------------------------------- */
    private void show(Step s) {
        err.setText("");
        body.getChildren().setAll(switch (s) { case START -> startStep(); case CODE -> codeStep(); case PASSWORD -> passwordStep(); });
    }

    /** Step 1: Google + email. */
    private VBox startStep() {
        TextField em = emailField();
        Button send = Ui.btn("Email me a code", "btn-primary", () -> {});
        send.setMaxWidth(Double.MAX_VALUE);
        Runnable go = () -> {
            String e = em.getText().trim();
            if (!validEmail(e)) { err.setText("Enter a valid email address."); return; }
            email = e; passwordSignUpPending = false;
            busy(send, "Sending...");
            Async.run(() -> { auth.requestEmailCode(email); return true; },
                    ok -> { unbusy(send, "Email me a code"); startCooldown(); show(Step.CODE); },
                    ex -> { unbusy(send, "Email me a code"); err.setText(AuthService.friendlyError(ex)); });
        };
        send.setOnAction(e -> go.run()); em.setOnAction(e -> go.run());

        Button pw = Ui.btn("Use a password instead", "link", () -> { email = em.getText().trim(); createMode = false; show(Step.PASSWORD); });
        HBox pwRow = new HBox(pw); pwRow.setAlignment(Pos.CENTER);

        VBox v = new VBox(12, googleButton(), Ui.divider("or"), em, send, pwRow);
        return v;
    }

    /** Step 2: type the 6-digit code. */
    private VBox codeStep() {
        Label head = Ui.label("Check your email", "h2"); center(head);
        Label sub = Ui.label("We sent a code to " + email + ". Type it below.", "secondary"); center(sub);
        TextField code = new TextField(); code.setPromptText("Code");
        code.setMaxWidth(Double.MAX_VALUE); code.setAlignment(Pos.CENTER);
        code.setStyle("-fx-font-size: 26px;");
        code.textProperty().addListener((o, a, b) -> { String d = b.replaceAll("[^0-9]", ""); if (d.length() > 10) d = d.substring(0, 10); if (!d.equals(b)) code.setText(d); });
        Button verify = Ui.btn("Sign in", "btn-primary", () -> {});
        verify.setMaxWidth(Double.MAX_VALUE);
        Runnable go = () -> {
            String c = code.getText().trim();
            if (c.length() < 6) { err.setText("Type the code from the email (at least 6 digits)."); return; }
            busy(verify, "Checking...");
            Async.run(() -> { auth.verifyEmailCode(email, c); return true; },
                    ok -> { err.setText(""); onSignedIn.run(); },
                    ex -> { unbusy(verify, "Sign in"); err.setText(AuthService.friendlyError(ex)); code.requestFocus(); });
        };
        verify.setOnAction(e -> go.run()); code.setOnAction(e -> go.run());

        resend = Ui.btn("Send a new code", "link", () -> {
            if (cooldownLeft > 0) return;
            err.setText(""); startCooldown();
            Async.run(() -> { auth.requestEmailCode(email); return true; }, ok -> Nav.toast("New code sent"),
                    ex -> { cooldownLeft = 0; updateResend(); err.setText(AuthService.friendlyError(ex)); });
        });
        updateResend();
        Button change = Ui.btn("Use a different email", "link", () -> show(Step.START));
        HBox links = Ui.hbox(8, resend, change); links.setAlignment(Pos.CENTER);
        Platform.runLater(code::requestFocus);
        return new VBox(12, head, sub, code, verify, links);
    }

    /** Step 3 (optional): email + password. */
    private VBox passwordStep() {
        Label head = Ui.label(createMode ? "Create your account" : "Sign in with your password", "h2"); center(head);
        TextField name = new TextField(); name.setPromptText("Your name (optional)"); name.setMaxWidth(Double.MAX_VALUE);
        TextField em = emailField(); em.setText(email);
        PasswordField pw = new PasswordField(); pw.setPromptText(createMode ? "Password (8+ characters)" : "Password"); pw.setMaxWidth(Double.MAX_VALUE);
        Button go = Ui.btn(createMode ? "Create account" : "Sign in", "btn-primary", () -> {});
        go.setMaxWidth(Double.MAX_VALUE);
        Runnable submit = () -> {
            String e = em.getText().trim(), p = pw.getText(), n = Validate.clean(name.getText(), 60);
            if (!validEmail(e)) { err.setText("Enter a valid email address."); return; }
            if (p.isEmpty()) { err.setText("Enter your password."); return; }
            if (createMode && p.length() < 8) { err.setText("Use at least 8 characters for the password."); return; }
            email = e;
            String label = createMode ? "Create account" : "Sign in";
            busy(go, "Please wait...");
            if (createMode) {
                Async.run(() -> auth.signUpWithPassword(e, p, n),
                        in -> { if (in) { onSignedIn.run(); } else { unbusy(go, label); passwordSignUpPending = true; startCooldown(); show(Step.CODE); } },
                        ex -> { unbusy(go, label); err.setText(AuthService.friendlyError(ex)); });
            } else {
                Async.run(() -> { auth.signInWithPassword(e, p); return true; },
                        ok -> onSignedIn.run(),
                        ex -> { unbusy(go, label); err.setText(AuthService.friendlyError(ex)); });
            }
        };
        go.setOnAction(e -> submit.run()); pw.setOnAction(e -> submit.run());

        Button toggle = Ui.btn(createMode ? "I already have an account" : "Create a new account", "link", () -> { email = em.getText().trim(); createMode = !createMode; show(Step.PASSWORD); });
        Button codeInstead = Ui.btn("Email me a code instead", "link", () -> { email = em.getText().trim(); show(Step.START); });
        HBox links = Ui.hbox(8, toggle, codeInstead); links.setAlignment(Pos.CENTER);
        VBox v = new VBox(12, head);
        if (createMode) v.getChildren().add(name);
        v.getChildren().addAll(em, pw, go, links);
        return v;
    }

    /* ---------------------------------- Google ---------------------------------- */
    private Button googleButton() {
        Label g = new Label("G"); g.getStyleClass().add("g-badge");
        Button google = new Button("Continue with Google"); google.setGraphic(g); google.setGraphicTextGap(10);
        google.getStyleClass().add("btn-outline"); google.setMaxWidth(Double.MAX_VALUE);
        google.setOnAction(e -> {
            google.setDisable(true); err.setText(""); google.setText("Waiting for Google...");
            auth.signInWithGoogle().whenComplete((v, ex) -> Platform.runLater(() -> {
                google.setDisable(false); google.setText("Continue with Google");
                if (ex == null) { onSignedIn.run(); return; }
                Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                if (c instanceof java.util.concurrent.CancellationException || ex instanceof java.util.concurrent.CancellationException) return;   // user backed out
                err.setText("Google sign-in failed: " + AuthService.friendlyError(c));
            }));
        });
        return google;
    }

    /* ---------------------------------- helpers ---------------------------------- */
    private Button resend;
    private int cooldownLeft;
    private void startCooldown() {
        cooldownLeft = 45; if (cooldown != null) cooldown.stop();
        cooldown = new Timeline(new KeyFrame(Duration.seconds(1), e -> { if (cooldownLeft > 0) cooldownLeft--; updateResend(); }));
        cooldown.setCycleCount(45); cooldown.play(); updateResend();
    }
    private void updateResend() {
        if (resend == null) return;
        resend.setText(cooldownLeft > 0 ? "Send a new code (" + cooldownLeft + "s)" : "Send a new code");
        resend.setDisable(cooldownLeft > 0);
    }

    private static TextField emailField() {
        TextField f = new TextField(); f.setPromptText("Email address"); f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }
    private static boolean validEmail(String e) { return e.length() <= 254 && e.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$"); }
    private static void busy(Button b, String t) { b.setDisable(true); b.setText(t); }
    private static void unbusy(Button b, String t) { b.setDisable(false); b.setText(t); }
    private static void center(Label l) { l.setAlignment(Pos.CENTER); l.setMaxWidth(Double.MAX_VALUE); l.setStyle("-fx-text-alignment:center;"); }

    /* ---------------------------------- DEV ONLY ---------------------------------- */
    /** Test-account buttons show only when config.local.properties has dev.*.email / .password, so release builds never show them. */
    private static boolean hasDevAccounts() {
        for (Role r : new Role[]{Role.STUDENT, Role.OWNER, Role.ADMIN}) if (!AppConfig.devEmail(r.db()).isEmpty()) return true;
        return false;
    }
    private HBox devRow() {
        Button fs = Ui.btn("Student", "chip", () -> forceLogin(Role.STUDENT));
        Button fo = Ui.btn("Owner", "chip", () -> forceLogin(Role.OWNER));
        Button fa = Ui.btn("Admin", "chip", () -> forceLogin(Role.ADMIN));
        HBox r = Ui.hbox(8, fs, fo, fa); r.setAlignment(Pos.CENTER); return r;
    }
    private void forceLogin(Role r) {
        err.setText("Signing in as " + r.db() + "...");
        Async.run(() -> { auth.devSignIn(r); return null; },
                v -> com.bofo.finder.app.Main.route(),
                e -> err.setText(AuthService.friendlyError(e)));
    }
}
