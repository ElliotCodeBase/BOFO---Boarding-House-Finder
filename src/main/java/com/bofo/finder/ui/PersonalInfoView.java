package com.bofo.finder.ui;

import com.bofo.finder.service.AuthService;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

/** Settings > Personal information (sub-screen of Profile): picture, username, name, phone, age, sex, region. Email comes from Google and is read-only. */
public final class PersonalInfoView extends VBox {
    public PersonalInfoView(Runnable back) {
        ProfileForm form = new ProfileForm(AuthService.get().profile(), true);
        Button save = Ui.btn("Save changes", "btn-primary", () -> form.save(() -> { Nav.toast("Saved"); back.run(); }));
        save.setMaxWidth(Double.MAX_VALUE);
        VBox content = new VBox(14, Ui.card(0, form), save); content.setPadding(new Insets(8, 16, 24, 16));
        getChildren().addAll(Ui.backBar("Personal information", back), Ui.scrollCentered(content, 560));
    }
}
