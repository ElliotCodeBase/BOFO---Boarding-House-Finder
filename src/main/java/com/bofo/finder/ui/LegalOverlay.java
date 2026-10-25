package com.bofo.finder.ui;

import com.bofo.finder.config.AppConfig;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * In-app reader for the legal documents (Privacy Policy, Terms of Service, Agreements and Guidelines).
 * Slides up as a bottom sheet over the current screen (and over the BOFO mascot); X, the dimmed area, the
 * "Close" button or Esc close it. No browser, no hosted page: the text lives in {@link LegalText}.
 */
public final class LegalOverlay extends StackPane {
    public enum Doc {
        PRIVACY("Privacy Policy", "policy.privacy.version"),
        TERMS("Terms of Service", "policy.terms.version"),
        AGREEMENTS("Agreements and Guidelines", "policy.owner.version");
        final String title, versionKey;
        Doc(String title, String versionKey) { this.title = title; this.versionKey = versionKey; }
    }

    private static LegalOverlay current;

    /** Opens the document over whatever screen is showing. A second tap while one is open does nothing. */
    public static void open(Doc doc) {
        if (current != null) return;
        current = new LegalOverlay(doc);
        Nav.pushOverlay(current);
        current.play();
    }

    private final VBox sheet = new VBox();
    private final Region scrim = new Region();
    private final javafx.event.EventHandler<javafx.scene.input.KeyEvent> esc = e -> { if (e.getCode() == KeyCode.ESCAPE) close(); };
    private boolean closing;

    private LegalOverlay(Doc doc) {
        scrim.setStyle("-fx-background-color:rgba(23,20,42,0.45);");

        Region handle = new Region(); handle.setMinSize(40, 4); handle.setPrefSize(40, 4); handle.setMaxSize(40, 4);
        handle.setStyle("-fx-background-color:#D9D6EA;-fx-background-radius:999;");
        HBox handleRow = new HBox(handle); handleRow.setAlignment(Pos.CENTER); handleRow.setPadding(new Insets(10, 0, 4, 0));

        Button x = new Button(); x.getStyleClass().addAll("search-clear", "filter-close");
        x.setGraphic(Ui.strokeIcon(Glyphs.CLOSE, 16, Color.web("#17142A")));
        x.setOnAction(e -> close());
        Label title = Ui.label(doc.title, "filter-title"); title.setMinWidth(0);
        HBox head = new HBox(8, title, Ui.grow(), x);
        head.setAlignment(Pos.CENTER_LEFT); head.setPadding(new Insets(4, 16, 4, 20));

        String v = AppConfig.get(doc.versionKey).trim();
        Label meta = Ui.label("Version " + (v.isEmpty() ? "1.0" : v) + "  \u00B7  " + LegalText.UPDATED, "secondary");
        VBox metaBox = new VBox(meta); metaBox.setPadding(new Insets(0, 20, 8, 20));

        VBox body = new VBox(8);
        body.setPadding(new Insets(4, 20, 20, 20));
        for (String[] s : LegalText.of(doc)) {
            if (s[0] != null && !s[0].isEmpty()) {
                Label h = Ui.label(s[0], "h2", "legal-h"); VBox.setMargin(h, new Insets(10, 0, 0, 0));
                body.getChildren().add(h);
            }
            body.getChildren().add(Ui.label(s[1], "legal-p"));
        }
        ScrollPane scroll = Ui.scroll(body); scroll.getStyleClass().add("sheet-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button done = Ui.btn("Close", "btn-primary", this::close); done.setMaxWidth(Double.MAX_VALUE);
        HBox foot = new HBox(done); foot.setPadding(new Insets(10, 20, 16, 20)); HBox.setHgrow(done, Priority.ALWAYS);
        foot.setStyle("-fx-border-color:#E7E8F2;-fx-border-width:1 0 0 0;");

        sheet.getChildren().addAll(handleRow, head, metaBox, scroll, foot);
        sheet.getStyleClass().add("filter-sheet");
        sheet.setMaxWidth(640); sheet.setMaxHeight(Region.USE_PREF_SIZE);
        sheet.maxHeightProperty().bind(heightProperty().multiply(0.92));
        sheet.setOnMouseClicked(e -> e.consume());

        // The empty space above the sheet closes it; the sheet sits at the bottom at its own height.
        Region gap = new Region(); VBox.setVgrow(gap, Priority.ALWAYS);
        gap.setOnMouseClicked(e -> close());
        VBox column = new VBox(gap, sheet); column.setAlignment(Pos.BOTTOM_CENTER);
        scrim.setOnMouseClicked(e -> close());
        getChildren().addAll(scrim, column);
        setPickOnBounds(true);
    }

    private void play() {
        if (getScene() != null) getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, esc);
        scrim.setOpacity(0); sheet.setTranslateY(600);
        FadeTransition f = new FadeTransition(Duration.millis(160), scrim); f.setToValue(1); f.play();
        TranslateTransition t = new TranslateTransition(Duration.millis(240), sheet); t.setToY(0); t.play();
    }

    private void close() {
        if (closing) return;
        closing = true;
        if (getScene() != null) getScene().removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, esc);
        FadeTransition f = new FadeTransition(Duration.millis(140), scrim); f.setToValue(0); f.play();
        TranslateTransition t = new TranslateTransition(Duration.millis(200), sheet); t.setToY(sheet.getHeight() + 20);
        t.setOnFinished(e -> { Nav.popOverlay(this); current = null; });
        t.play();
    }
}
