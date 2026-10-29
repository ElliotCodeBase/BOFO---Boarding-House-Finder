package com.bofo.finder.ui;

import com.bofo.finder.service.AuthService;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Shown once, right after a new user picks Student / Owner: username, picture, number, age, sex and region. All optional. */
public final class ProfileSetupView extends VBox {
    public ProfileSetupView(Runnable done) {
        ProfileForm form = new ProfileForm(AuthService.get().profile(), false);
        Label title = Ui.label("Set up your profile", "title");
        Label sub = Ui.label("Tell us a little about you. You can change all of this later in Profile > Personal information.", "secondary");
        Button cont = Ui.btn("Continue", "btn-primary", () -> form.save(done));
        cont.setMaxWidth(Double.MAX_VALUE);
        Button skip = Ui.btn("Skip for now", "link", done);
        skip.setMaxWidth(Double.MAX_VALUE);
        VBox content = new VBox(14, title, sub, Ui.card(0, form), cont, skip);
        content.setPadding(new Insets(24, 16, 32, 16));
        getChildren().add(Ui.scrollCentered(content, 520));
    }
}
