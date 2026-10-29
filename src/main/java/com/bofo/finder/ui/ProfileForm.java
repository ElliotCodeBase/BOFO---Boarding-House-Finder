package com.bofo.finder.ui;

import com.bofo.finder.model.Profile;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Capture;
import com.bofo.finder.util.Regions;
import com.bofo.finder.util.Validate;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import org.json.JSONObject;

/**
 * The profile fields shared by the first-login setup screen and Settings > Personal information:
 * profile picture, username, full name, mobile number, age, sex and region (the map opens on the region).
 * Everything is optional; {@link #save} validates, uploads a new picture, updates public.users and reloads the profile.
 */
public final class ProfileForm extends VBox {
    private final Profile p;
    private final Label avatar;
    private final TextField username = new TextField(), name = new TextField(), phone = new TextField(), age = new TextField();
    private final ComboBox<String> sex = new ComboBox<>(), region = new ComboBox<>();
    private byte[] newPhoto;                       // set when the user picked a new picture; uploaded on save

    public ProfileForm(Profile p, boolean showEmail) {
        super(6);
        this.p = p;
        String shown = p.fullName() == null || p.fullName().isBlank() ? p.username() : p.fullName();
        avatar = Ui.avatar(shown, 88, p.photoUrl());
        Button cam = Ui.btn("Camera", "chip", () -> Capture.camera().ifPresent(this::picked));
        Button gal = Ui.btn("Gallery", "chip", () -> Capture.gallery().ifPresent(this::picked));
        VBox picBox = new VBox(8, avatar, Ui.hbox(8, cam, gal));
        picBox.setAlignment(Pos.CENTER); picBox.setPadding(new Insets(0, 0, 8, 0));

        username.setText(p.username()); username.setPromptText("3-20 letters, numbers, _ or .");
        name.setText(p.fullName());
        phone.setText(p.phone()); phone.setPromptText("Optional, private");
        age.setText(p.age() == null ? "" : String.valueOf(p.age())); age.setPromptText("Optional, private");
        sex.getItems().addAll("", "Male", "Female", "Prefer not to say"); sex.setPromptText("Optional, private");
        sex.setValue(p.sex() == null ? "" : p.sex()); sex.setMaxWidth(Double.MAX_VALUE);
        region.getItems().add("");
        Regions.ALL.forEach(r -> region.getItems().add(r.name()));
        region.setPromptText("Pick your region");
        Regions.Region cur = Regions.byKey(p.region());
        region.setValue(cur == null ? "" : cur.name()); region.setMaxWidth(Double.MAX_VALUE);

        getChildren().add(picBox);
        add("Username", username);
        add("Full name", name);
        if (showEmail) {
            TextField email = new TextField("Comes from your Google account"); email.setEditable(false); email.setDisable(true);
            add("Email", email);
        }
        add("Mobile number", phone);
        add("Age", age);
        add("Sex", sex);
        add("Region", region);
        Label note = Ui.label("The map opens on your region every time. Phone, age and sex are private.", "secondary");
        note.setWrapText(true);
        getChildren().add(note);
    }

    private void add(String title, javafx.scene.Node field) {
        getChildren().addAll(Ui.label(title, "secondary"), field);
        VBox.setMargin(field, new Insets(0, 0, 10, 0));
    }

    /** Centre-crops the picked image to a square, shrinks it to 512 px and previews it in the round avatar. */
    private void picked(Image img) {
        try {
            int w = (int) img.getWidth(), h = (int) img.getHeight(), s = Math.min(w, h);
            if (s <= 0) return;
            Image square = new WritableImage(img.getPixelReader(), (w - s) / 2, (h - s) / 2, s, s);
            newPhoto = Capture.png(square, 512);
            Ui.setAvatarImage(avatar, square, 88);
        } catch (Exception e) { Nav.toast("Could not use that photo"); }
    }

    /** Validates and saves; {@code ok} runs on the FX thread after a successful save. */
    public void save(Runnable ok) {
        String user = username.getText() == null ? "" : username.getText().trim();
        if (!user.isEmpty() && !user.matches("[A-Za-z0-9_.]{3,20}")) { Nav.toast("Username: 3-20 letters, numbers, _ or ."); return; }
        Integer ageVal = null;
        if (!Validate.blank(age.getText())) {
            try { ageVal = Integer.parseInt(age.getText().trim()); } catch (NumberFormatException e) { Nav.toast("Age must be a number"); return; }
            if (ageVal < 10 || ageVal > 100) { Nav.toast("Age must be between 10 and 100"); return; }
        }
        final Integer ageFinal = ageVal;
        final String uid = p.id();
        final boolean nameChanged = !user.equalsIgnoreCase(p.username() == null ? "" : p.username());
        Regions.Region reg = Regions.byName(region.getValue());
        String sexVal = sex.getValue() == null ? "" : sex.getValue();

        Async.run(() -> {
            SupabaseClient api = SupabaseClient.get();
            if (!user.isEmpty() && nameChanged) {
                var rows = api.rpc("username_available", new JSONObject().put("u", user)).optJSONArray("data");
                if (rows != null && !rows.isEmpty() && !rows.getJSONObject(0).optBoolean("available", true)) throw new IllegalStateException("taken");
            }
            JSONObject patch = new JSONObject()
                    .put("full_name", Validate.clean(name.getText(), 80))
                    .put("phone", Validate.clean(phone.getText(), 20))
                    .put("username", user.isEmpty() ? JSONObject.NULL : user)
                    .put("age", ageFinal == null ? JSONObject.NULL : ageFinal)
                    .put("sex", sexVal.isBlank() ? JSONObject.NULL : sexVal)
                    .put("region", reg == null ? JSONObject.NULL : reg.key());
            if (newPhoto != null) {
                api.upload("avatars", uid + "/avatar.png", newPhoto, "image/png");
                patch.put("photo_url", api.publicUrl("avatars", uid + "/avatar.png") + "?v=" + System.currentTimeMillis());
            }
            api.update("users", "id=eq." + uid, patch);
            AuthService.get().loadProfile();
            return true;
        }, done -> ok.run(), e -> {
            Throwable t = e; boolean taken = false;
            for (; t != null; t = t.getCause()) {
                if (t instanceof IllegalStateException && "taken".equals(t.getMessage())) taken = true;
                if (t instanceof SupabaseClient.ApiException ae && ae.status == 409) taken = true;
                if (t.getCause() == t) break;
            }
            if (taken) Nav.toast("That username is taken. Try another one.");
            else Ui.failToast(e, "Could not save");
        });
    }
}
