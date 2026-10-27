package com.bofo.finder.ui;

import com.bofo.finder.util.Locator;
import com.bofo.finder.app.Main;
import com.bofo.finder.util.Locator;
import com.bofo.finder.model.Profile;
import com.bofo.finder.util.Locator;
import com.bofo.finder.model.Role;
import com.bofo.finder.util.Locator;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.util.Locator;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Locator;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Locator;
import javafx.geometry.Insets;
import com.bofo.finder.util.Locator;
import javafx.geometry.Pos;
import com.bofo.finder.util.Locator;
import javafx.scene.Node;
import com.bofo.finder.util.Locator;
import javafx.scene.control.*;
import com.bofo.finder.util.Locator;
import javafx.scene.layout.*;
import com.bofo.finder.util.Locator;
import org.json.JSONObject;

/**
 * Profile + Settings in the GCash style: a coloured header with avatar, name and status chips, then grouped
 * white cards of icon rows with chevrons (Account, Preferences, Security & privacy, About), a Log out button
 * and a quiet "Delete my account" link at the bottom. Personal information opens as its own sub-screen.
 */
public final class ProfileView extends VBox {
    public ProfileView() {
        Profile p = AuthService.get().profile();
        boolean owner = p.role() == Role.OWNER, admin = p.role() == Role.ADMIN;
        String nm = p.fullName() == null || p.fullName().isBlank() ? "Your profile" : p.fullName();
        String roleName = p.role().name().charAt(0) + p.role().name().substring(1).toLowerCase();

        // ---- header (gradient, rounded bottom) ----
        Label name = Ui.label(nm, "profile-name"); name.setWrapText(true);
        HBox chips = Ui.hbox(6, Ui.label(roleName, "profile-chip"));
        if (owner) chips.getChildren().add(Ui.label(statusText(p), "profile-chip"));
        VBox who = Ui.vbox(6, name, chips);
        HBox idRow = Ui.hbox(14, Ui.avatar(nm, 72), who); idRow.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(12, Ui.label("Profile", "profile-title"), idRow);
        header.getStyleClass().add("profile-header");

        // ---- Account ----
        Node personal = Ui.settingsRow(Glyphs.USER, "Personal information", p.phone(), () -> Nav.show(new PersonalInfoView(ProfileView::backToProfile), true));
        VBox account = Ui.settingsGroup("Account", personal);
        VBox accountCard = (VBox) account.getChildren().get(1);
        if (owner) addRow(accountCard, Ui.settingsRow(Glyphs.SHIELD_CHECK, "Verification", statusText(p), () -> Nav.show(new VerificationView(ProfileView::backToProfile), true)));
        if (!admin) addRow(accountCard, Ui.settingsRow(Glyphs.SWAP, "Switch to " + (p.role() == Role.STUDENT ? "Owner" : "Student") + " mode", null, () -> switchRole(p)));

        // ---- Preferences ----
        Node notif = Ui.settingsToggle(Glyphs.BELL, "Notifications", true, on -> {});
        Node mascot = Ui.settingsToggle(Glyphs.SMILE, "Show BOFO mascot", "The floating helper on your screens", BofoPanel.enabled(), BofoPanel::setEnabled);
        Node loc = Ui.settingsToggle(Glyphs.LOCATE, "Use my location", "Sorts places by distance and plans your trips", Locator.get().enabled.get(), on -> Locator.get().setEnabled(on));
        VBox prefs = Ui.settingsGroup("Preferences", notif, mascot, loc);

        // ---- Security & privacy ----
        VBox security = Ui.settingsGroup("Security and privacy",
                Ui.settingsRow(Glyphs.USERS, "Switch Google account", null, Main::signOutToLogin),
                Ui.settingsRow(Glyphs.LOCK, "Privacy Policy", null, () -> LegalOverlay.open(LegalOverlay.Doc.PRIVACY)),
                Ui.settingsRow(Glyphs.FILE, "Terms of Service", null, () -> LegalOverlay.open(LegalOverlay.Doc.TERMS)),
                Ui.settingsRow(Glyphs.INFO, "Agreements and Guidelines", null, () -> LegalOverlay.open(LegalOverlay.Doc.AGREEMENTS)));

        // ---- About ----
        Label brand = Ui.label("BOFO Finder", "settings-brand");
        Label ver = Ui.label("Version " + com.bofo.finder.config.AppConfig.appVersion(), "settings-ver");
        VBox about = new VBox(4, new Mascot(Mascot.Expression.HAPPY, 54), brand, ver);
        about.setAlignment(Pos.CENTER); about.setPadding(new Insets(10, 0, 0, 0));

        Button logout = Ui.btn("Log out", "btn-logout", Main::signOutToLogin); logout.setMaxWidth(Double.MAX_VALUE);
        Button delete = Ui.btn("Delete my account and data", "link-danger", this::confirmDelete);
        delete.setMaxWidth(Double.MAX_VALUE);

        VBox body = new VBox(18, account, prefs, security, logout, delete);
        body.setPadding(new Insets(16, 16, 24, 16)); body.setAlignment(Pos.TOP_CENTER);
        VBox.setMargin(logout, new Insets(4, 0, 0, 0));
        body.getChildren().remove(about); body.getChildren().add(about);
        VBox page = new VBox(header, body);
        getChildren().add(Ui.scrollCentered(page, 560));
    }

    private static void addRow(VBox card, Node row) {
        Region line = new Region(); line.getStyleClass().add("hline"); VBox.setMargin(line, new Insets(0, 16, 0, 68));
        card.getChildren().addAll(line, row);
    }

    private static String statusText(Profile p) {
        if (p.verified()) return "Verified";
        String s = p.verificationStatus() == null ? "" : p.verificationStatus().toLowerCase();
        return s.equals("pending") ? "Pending review" : s.equals("rejected") ? "Rejected" : "Not verified";
    }

    /** Returns to the Profile tab after a sub-screen (Personal information, Verification). */
    static void backToProfile() {
        Shell.pendingTab = AuthService.get().profile().role() == Role.ADMIN ? 3 : 4;
        Main.route();
    }

    private void switchRole(Profile p) {
        Role target = p.role() == Role.STUDENT ? Role.OWNER : Role.STUDENT;
        Async.run(() -> { SupabaseClient.get().rpc("switch_role", new JSONObject().put("new_role", target.db())); AuthService.get().loadProfile(); return true; },
                ok -> Main.route(), e -> Ui.failToast(e, "Switching failed"));
    }

    private void confirmDelete() {
        Popup.confirm(Popup.Kind.DANGER, "Delete my account and data?", "This permanently removes your account, listings, chats and verification images. This cannot be undone.", "Delete", () ->
                Async.run(() -> SupabaseClient.get().function("delete-account", new JSONObject()), r -> Main.signOutToLogin(), e -> Ui.failToast(e, "Could not delete. Try again.")));
    }
}
