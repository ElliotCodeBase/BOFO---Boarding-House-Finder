package com.bofo.finder.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * The one pop-up of the whole app (replaces the grey Windows-style dialogs): a rounded card on a dimmed screen
 * with an icon, a bold title, a soft message and pill buttons. It lives inside the app window, so it looks the
 * same on a phone and on a PC and never opens a second window.
 *
 *   Popup.confirm(Popup.Kind.DANGER, "Delete this chat?", "...", "Delete", () -> ...);
 *   Popup.alert(Popup.Kind.ERROR, "Delete failed", "...");
 *   Popup.input("Reason for rejection", "Shown to the owner", "Write the reason...", "Reject", reason -> ...);
 */
public final class Popup {
    public enum Kind { INFO, QUESTION, DANGER, ERROR, SUCCESS }

    private Popup() {}

    /** Yes / cancel. onYes runs only when the person taps the main button. */
    public static void confirm(Kind kind, String title, String message, String yesLabel, Runnable onYes) {
        ask(kind, title, message, yesLabel, "Cancel", yes -> { if (yes) onYes.run(); });
    }

    /** Two choices with your own labels. The answer is true for the main button, false for the other one (or a tap outside). */
    public static void ask(Kind kind, String title, String message, String yesLabel, String noLabel, Consumer<Boolean> answer) {
        run(() -> show(kind, title, message, yesLabel, noLabel, false, null, null, answer, null));
    }

    /** One OK button. Long error text is selectable so it can be copied. */
    public static void alert(Kind kind, String title, String message) {
        run(() -> show(kind, title, message, "OK", null, kind == Kind.ERROR && message != null && message.length() > 120, null, null, a -> {}, null));
    }

    /** Asks for a short text. The main button stays off until something is typed. */
    public static void input(String title, String message, String hint, String yesLabel, Consumer<String> onText) {
        run(() -> show(Kind.QUESTION, title, message, yesLabel, "Cancel", false, hint, onText, a -> {}, null));
    }

    /** One row of an action sheet. */
    public record Action(String label, boolean danger, Runnable run) {
        public Action(String label, Runnable run) { this(label, false, run); }
    }

    /** Bottom action sheet (replaces the grey right-click / long-press menus). Tap a row to run it, tap outside or Esc to close. */
    public static void menu(String title, Action... actions) {
        run(() -> {
            StackPane overlay = new StackPane(); overlay.setPickOnBounds(true); overlay.setFocusTraversable(true);
            Region scrim = new Region(); scrim.getStyleClass().add("popup-scrim");

            VBox sheet = new VBox(0); sheet.getStyleClass().add("popup-sheet");
            sheet.setMaxWidth(420); sheet.setMaxHeight(Region.USE_PREF_SIZE);
            Region handle = new Region(); handle.getStyleClass().add("popup-handle");
            handle.setMinSize(40, 4); handle.setPrefSize(40, 4); handle.setMaxSize(40, 4);
            HBox handleRow = new HBox(handle); handleRow.setAlignment(Pos.CENTER); handleRow.setPadding(new Insets(0, 0, 8, 0));
            sheet.getChildren().add(handleRow);
            if (title != null && !title.isBlank()) {
                Label t = new Label(title); t.getStyleClass().add("popup-sheet-title"); t.setMaxWidth(Double.MAX_VALUE);
                sheet.getChildren().add(t);
            }

            boolean[] done = {false};
            Runnable[] closer = new Runnable[1];
            closer[0] = () -> {
                if (done[0]) return; done[0] = true;
                TranslateTransition tt = new TranslateTransition(Duration.millis(140), sheet); tt.setToY(sheet.getHeight() + 40);
                FadeTransition ft = new FadeTransition(Duration.millis(140), scrim); ft.setToValue(0);
                tt.setOnFinished(e -> Nav.popOverlay(overlay)); tt.play(); ft.play();
            };
            for (Action a : actions) {
                Button b = new Button(a.label()); b.getStyleClass().add("popup-sheet-row");
                if (a.danger()) b.getStyleClass().add("popup-sheet-row-danger");
                b.setMaxWidth(Double.MAX_VALUE); b.setAlignment(Pos.CENTER_LEFT);
                b.setOnAction(e -> { closer[0].run(); Platform.runLater(a.run()); });
                sheet.getChildren().add(b);
            }
            Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("popup-btn", "popup-btn-ghost");
            cancel.setMaxWidth(Double.MAX_VALUE); cancel.setCancelButton(true); cancel.setOnAction(e -> closer[0].run());
            VBox.setMargin(cancel, new Insets(10, 0, 0, 0));
            sheet.getChildren().add(cancel);

            scrim.setOnMouseClicked(e -> closer[0].run());
            overlay.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) { closer[0].run(); e.consume(); } });
            overlay.getChildren().addAll(scrim, sheet);
            StackPane.setAlignment(sheet, Pos.BOTTOM_CENTER);
            StackPane.setMargin(sheet, new Insets(0, 10, 10, 10));

            Nav.pushOverlay(overlay);
            scrim.setOpacity(0); sheet.setTranslateY(400);
            FadeTransition in = new FadeTransition(Duration.millis(160), scrim); in.setToValue(1); in.play();
            TranslateTransition up = new TranslateTransition(Duration.millis(200), sheet); up.setToY(0); up.play();
            cancel.requestFocus();
        });
    }

    // ------------------------------------------------------------------------------------------------------

    private static void run(Runnable r) { if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r); }

    private static void show(Kind kind, String title, String message, String yesLabel, String noLabel, boolean selectable,
                             String hint, Consumer<String> onText, Consumer<Boolean> answer, Object unused) {
        StackPane overlay = new StackPane();
        overlay.setPickOnBounds(true);
        overlay.setFocusTraversable(true);

        Region scrim = new Region(); scrim.getStyleClass().add("popup-scrim");

        // icon badge
        String glyph; String tone;
        switch (kind) {
            case DANGER:  glyph = "!"; tone = "popup-icon-danger"; break;
            case ERROR:   glyph = "✕"; tone = "popup-icon-danger"; break;
            case SUCCESS: glyph = "✓"; tone = "popup-icon-ok"; break;
            case QUESTION: glyph = "?"; tone = "popup-icon-info"; break;
            default:      glyph = "i"; tone = "popup-icon-info";
        }
        Label icon = new Label(glyph); icon.getStyleClass().addAll("popup-icon", tone);
        icon.setMinSize(56, 56); icon.setPrefSize(56, 56); icon.setMaxSize(56, 56); icon.setAlignment(Pos.CENTER);

        Label t = new Label(title); t.getStyleClass().add("popup-title"); t.setWrapText(true); t.setAlignment(Pos.CENTER);
        t.setMaxWidth(Double.MAX_VALUE); t.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox card = new VBox(12);
        card.getStyleClass().add("popup-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(340); card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setPadding(new Insets(26, 22, 20, 22));
        card.getChildren().addAll(icon, t);

        if (message != null && !message.isBlank()) {
            if (selectable) {
                TextArea ta = new TextArea(message); ta.setEditable(false); ta.setWrapText(true); ta.setPrefRowCount(5);
                ta.getStyleClass().add("popup-text-area");
                card.getChildren().add(ta);
            } else {
                Label m = new Label(message); m.getStyleClass().add("popup-message"); m.setWrapText(true);
                m.setTextAlignment(javafx.scene.text.TextAlignment.CENTER); m.setAlignment(Pos.CENTER); m.setMaxWidth(Double.MAX_VALUE);
                card.getChildren().add(m);
            }
        }

        TextField field = null;
        if (onText != null) {
            field = new TextField(); field.setPromptText(hint == null ? "" : hint); field.getStyleClass().add("popup-input");
            card.getChildren().add(field);
        }

        boolean[] done = {false};
        Runnable close = () -> {
            FadeTransition ft = new FadeTransition(Duration.millis(120), overlay); ft.setToValue(0);
            ft.setOnFinished(e -> Nav.popOverlay(overlay)); ft.play();
        };
        Consumer<Boolean> finish = yes -> {
            if (done[0]) return; done[0] = true;
            close.run();
            Platform.runLater(() -> answer.accept(yes));
        };

        Button main = new Button(yesLabel);
        main.getStyleClass().addAll("popup-btn", kind == Kind.DANGER || kind == Kind.ERROR && noLabel != null ? "popup-btn-danger" : "popup-btn-primary");
        main.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(main, Priority.ALWAYS); main.setDefaultButton(true);
        final TextField fieldF = field;
        main.setOnAction(e -> {
            if (fieldF != null) {
                String text = fieldF.getText() == null ? "" : fieldF.getText().trim();
                if (text.isEmpty()) return;
                if (done[0]) return; done[0] = true; close.run();
                Platform.runLater(() -> onText.accept(text));
            } else finish.accept(true);
        });
        if (field != null) {
            main.setDisable(true);
            Button mainRef = main;
            field.textProperty().addListener((o, a, b) -> mainRef.setDisable(b == null || b.isBlank()));
            field.setOnAction(e -> main.fire());
        }

        HBox buttons = new HBox(10); buttons.setAlignment(Pos.CENTER);
        VBox.setMargin(buttons, new Insets(8, 0, 0, 0));
        if (noLabel != null) {
            Button no = new Button(noLabel); no.getStyleClass().addAll("popup-btn", "popup-btn-ghost");
            no.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(no, Priority.ALWAYS); no.setCancelButton(true);
            no.setOnAction(e -> finish.accept(false));
            buttons.getChildren().addAll(no, main);
        } else buttons.getChildren().add(main);
        card.getChildren().add(buttons);

        scrim.setOnMouseClicked(e -> { if (noLabel != null) finish.accept(false); });
        overlay.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE && noLabel != null) { finish.accept(false); e.consume(); } });
        overlay.getChildren().addAll(scrim, card);
        StackPane.setAlignment(card, Pos.CENTER);
        StackPane.setMargin(card, new Insets(0, 24, 0, 24));

        // the stylesheet is attached to the scene already; popups just add the classes
        overlay.setOpacity(0);
        Nav.pushOverlay(overlay);
        FadeTransition in = new FadeTransition(Duration.millis(160), overlay); in.setToValue(1); in.play();
        card.setScaleX(0.92); card.setScaleY(0.92);
        ScaleTransition st = new ScaleTransition(Duration.millis(180), card); st.setToX(1); st.setToY(1); st.play();
        if (field != null) field.requestFocus(); else main.requestFocus();
    }
}
