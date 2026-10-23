package com.bofo.finder.ui;

import com.bofo.finder.model.Profile;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Validate;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.json.JSONObject;

/** Settings > Personal information (sub-screen of Profile): name, phone, age, sex. Email comes from Google and is read-only. */
public final class PersonalInfoView extends VBox {
    public PersonalInfoView(Runnable back) {
        Profile p = AuthService.get().profile();
        TextField name = new TextField(p.fullName());
        TextField email = new TextField("Comes from your Google account"); email.setEditable(false); email.setDisable(true);
        TextField phone = new TextField(p.phone()); phone.setPromptText("Optional, private");
        TextField age = new TextField(); age.setPromptText("Optional, private");
        ComboBox<String> sex = new ComboBox<>(); sex.getItems().addAll("", "Male", "Female", "Prefer not to say"); sex.setPromptText("Optional, private");
        sex.setMaxWidth(Double.MAX_VALUE);
        Button save = Ui.btn("Save changes", "btn-primary", () -> {
            JSONObject patch = new JSONObject().put("full_name", Validate.clean(name.getText(), 80)).put("phone", Validate.clean(phone.getText(), 20));
            try { if (!age.getText().isBlank()) patch.put("age", Integer.parseInt(age.getText().trim())); } catch (NumberFormatException e) { Nav.toast("Age must be a number"); return; }
            if (sex.getValue() != null && !sex.getValue().isBlank()) patch.put("sex", sex.getValue());
            Async.run(() -> { SupabaseClient.get().update("users", "id=eq." + p.id(), patch); AuthService.get().loadProfile(); return true; },
                    ok -> { Nav.toast("Saved"); back.run(); }, e -> Ui.failToast(e, "Could not save"));
        });
        save.setMaxWidth(Double.MAX_VALUE);
        VBox form = new VBox(6,
                Ui.label("Full name", "secondary"), name,
                Ui.label("Email", "secondary"), email,
                Ui.label("Mobile number", "secondary"), phone,
                Ui.label("Age", "secondary"), age,
                Ui.label("Sex", "secondary"), sex);
        form.getChildren().forEach(n -> { if (n instanceof TextField || n instanceof ComboBox) VBox.setMargin(n, new Insets(0, 0, 10, 0)); });
        Label note = Ui.label("Phone, age and sex are private. They are never shown on your profile.", "secondary"); note.setWrapText(true);
        VBox card = Ui.card(0, form);
        VBox content = new VBox(14, card, note, save); content.setPadding(new Insets(8, 16, 24, 16));
        getChildren().addAll(Ui.backBar("Personal information", back), Ui.scrollCentered(content, 560));
    }
}
