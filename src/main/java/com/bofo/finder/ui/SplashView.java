package com.bofo.finder.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Loading screen: BOFO jumps up and down in a loop (sprite animation) while the saved session is checked. */
public final class SplashView extends VBox {
    private final Label status = Ui.label("Loading…", "secondary");
    private final SpriteView jump = SpriteView.bofoJump(220);

    public SplashView() {
        super(10); setAlignment(Pos.CENTER);
        setStyle("-fx-background-color:-bofo-bg;");
        Label name = Ui.label("BOFO", "title"); name.setStyle("-fx-text-fill:-bofo-primary;-fx-font-size:36px;-fx-font-weight:bold;");
        Label sub = new Label("Boarding House Finder"); sub.setStyle("-fx-text-fill:-bofo-primary-dark;-fx-font-weight:bold;");
        getChildren().addAll(jump, name, sub, status);
    }
    public void setStatus(String s) { status.setText(s); }
}
