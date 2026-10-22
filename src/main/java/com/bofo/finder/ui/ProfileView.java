package com.bofo.finder.ui;

import com.bofo.finder.app.Main;
import com.bofo.finder.model.Profile;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Validate;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.json.JSONObject;

/** Settings/Profile: edit profile, mascot toggle, switch role, policies, delete account, log out. */
public final class ProfileView extends VBox {
    public ProfileView() {
        Profile p = AuthService.get().profile();
        TextField name = new TextField(p.fullName()), phone = new TextField(p.phone());
        phone.setPromptText("Phone (optional, private)");
        TextField age = new TextField(); age.setPromptText("Age (optional, private)");
        ComboBox<String> sex = new ComboBox<>(); sex.getItems().addAll("", "Male", "Female", "Prefer not to say"); sex.setPromptText("Sex (optional, private)");
        TextField email = new TextField(); email.setEditable(false); email.setText("Email comes from your Google account");
        Button save = Ui.btn("Save profile", "btn-primary", () -> {
            JSONObject patch = new JSONObject().put("full_name", Validate.clean(name.getText(), 80)).put("phone", Validate.clean(phone.getText(), 20));
            try { if (!age.getText().isBlank()) patch.put("age", Integer.parseInt(age.getText().trim())); } catch (NumberFormatException e) { Nav.toast("Age must be a number"); return; }
            if (sex.getValue() != null && !sex.getValue().isBlank()) patch.put("sex", sex.getValue());
            Async.run(() -> SupabaseClient.get().update("users", "id=eq." + p.id(), patch), r -> Nav.toast("Saved"), e -> Nav.toast("Could not save"));
        });

        CheckBox notif = new CheckBox("Notifications"); notif.setSelected(true);
        CheckBox mascot = new CheckBox("Show BOFO mascot"); mascot.setSelected(BofoPanel.enabled());
        mascot.setOnAction(e -> BofoPanel.setEnabled(mascot.isSelected()));

        Button switchGoogle = Ui.btn("Switch Google account", "btn-outline", () -> Main.signOutToLogin());
        Button switchRole = Ui.btn("Switch to " + (p.role() == Role.STUDENT ? "Owner" : "Student") + " mode", "btn-outline", () -> switchRole(p));
        switchRole.setVisible(p.role() != Role.ADMIN); switchRole.setManaged(p.role() != Role.ADMIN);

        Button terms = Ui.btn("Terms of Service", "chip", () -> Nav.toast("Open Terms URL"));
        Button priv = Ui.btn("Privacy Policy", "chip", () -> Nav.toast("Open Privacy URL"));
        Button agree = Ui.btn("Agreements & Guidelines", "chip", () -> Nav.toast("Open Owner Agreement / Community Guidelines"));

        Button delete = Ui.btn("Delete my account and data", "btn-danger", this::confirmDelete);
        Button logout = Ui.btn("Log out", "btn-outline", () -> Main.signOutToLogin());

        String nm = p.fullName() == null || p.fullName().isBlank() ? "Your profile" : p.fullName();
        String roleName = p.role().name().charAt(0) + p.role().name().substring(1).toLowerCase();
        VBox idCard = Ui.card(0, Ui.hbox(14, Ui.avatar(nm, 60), Ui.vbox(2, Ui.label(nm, "title"), Ui.label(roleName + " account", "secondary"))));
        HBox roleRow = Ui.menuRow(Icons.SWAP, "Switch to " + (p.role() == Role.STUDENT ? "Owner" : "Student") + " mode", () -> switchRole(p));
        roleRow.setVisible(p.role() != Role.ADMIN); roleRow.setManaged(p.role() != Role.ADMIN);
        HBox googleRow = Ui.menuRow(Icons.PERSON, "Switch Google account", () -> Main.signOutToLogin());
        HBox termsRow = Ui.menuRow(Icons.DOC, "Terms of Service", () -> Nav.toast("Open Terms URL"));
        HBox privRow = Ui.menuRow(Icons.SHIELD, "Privacy Policy", () -> Nav.toast("Open Privacy URL"));
        HBox agreeRow = Ui.menuRow(Icons.INFO, "Agreements and Guidelines", () -> Nav.toast("Open Owner Agreement / Community Guidelines"));
        HBox logoutRow = Ui.menuRow(Icons.LOGOUT, "Log out", () -> Main.signOutToLogin());
        delete.setMaxWidth(Double.MAX_VALUE);
        VBox form = Ui.padded(14, idCard,
                Ui.card(10, Ui.label("Edit profile", "h2"), name, email, phone, age, sex, save),
                Ui.card(4, Ui.label("General", "h2"), notif, mascot, roleRow, googleRow),
                Ui.card(4, Ui.label("About", "h2"), termsRow, privRow, agreeRow),
                Ui.card(4, logoutRow),
                delete, Ui.label("BOFO Finder v1.0.0", "secondary"));
        form.setPadding(new Insets(8, 16, 110, 16));
        getChildren().addAll(Ui.appBar("Profile"), Ui.scroll(form));
    }

    private void switchRole(Profile p) {
        Role target = p.role() == Role.STUDENT ? Role.OWNER : Role.STUDENT;
        Async.run(() -> { SupabaseClient.get().rpc("switch_role", new JSONObject().put("new_role", target.db())); AuthService.get().loadProfile(); return true; },
                ok -> Main.route(), e -> Nav.toast("Switching failed"));
    }

    private void confirmDelete() {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "This permanently removes your account, listings, chats and verification images. This cannot be undone.", ButtonType.CANCEL, ButtonType.OK);
        a.setTitle("Delete account"); a.setHeaderText("Delete my account and data?");
        a.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b ->
                Async.run(() -> SupabaseClient.get().rpc("delete_my_account", new JSONObject()), r -> Main.signOutToLogin(), e -> Nav.toast("Could not delete. Try again.")));
    }
}
