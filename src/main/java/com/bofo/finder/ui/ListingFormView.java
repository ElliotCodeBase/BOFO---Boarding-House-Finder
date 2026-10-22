package com.bofo.finder.ui;

import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Capture;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Add listing. Submission is blocked until every required field is filled; server RLS also requires a verified owner. */
public final class ListingFormView extends VBox {
    private final BhService.Draft d = new BhService.Draft();
    private final TextField name = new TextField(), address = new TextField(), price = new TextField(),
            phone = new TextField(), fb = new TextField(), email = new TextField();
    private final ComboBox<String> mode = new ComboBox<>();
    private final CheckBox water = new CheckBox("💧 Free water"), elec = new CheckBox("⚡ Free electricity"), wifi = new CheckBox("📶 Free WiFi");
    private final VBox roomRows = new VBox(6);
    private final Label pinLabel = Ui.label("No pin yet", "secondary"), photoLabel = Ui.label("0 photos", "secondary"), errors = Ui.label("", "error-text");
    private final MapPane map = new MapPane(220);

    public ListingFormView(Runnable onDone) {
        Button back = Ui.btn("←", "heart", onDone); back.setStyle("-fx-text-fill:-bofo-text;-fx-font-size:18px;-fx-background-color:white;-fx-background-radius:999;-fx-min-width:38;-fx-min-height:38;-fx-padding:0;");
        HBox bar = Ui.appBar("New listing"); bar.getChildren().add(0, back);

        name.setPromptText("BH name"); address.setPromptText("Address"); price.setPromptText("Monthly payment (₱)");
        phone.setPromptText("Phone"); fb.setPromptText("Facebook / Messenger link"); email.setPromptText("Email");
        mode.getItems().addAll("per bed", "per room"); mode.getSelectionModel().selectFirst();

        double[] ref = State.ref(); map.show(ref != null ? ref : new double[]{14.5995, 120.9842}, 0, java.util.List.of(), false, null);
        map.enableCenterPin();
        Button setPin = Ui.btn("📍 Set pin at map center", "btn-outline", () -> {
            double[] c = map.center(); d.lat = c[0]; d.lng = c[1]; pinLabel.setText(String.format("Pin: %.5f, %.5f", c[0], c[1]));
        });

        addRoom(4, 0);
        Button addRoomBtn = Ui.btn("+ Add room", "chip", () -> addRoom(4, 0));
        Button cam = Ui.btn("📷 Camera", "chip", () -> Capture.camera().ifPresent(this::addPhoto));
        Button gal = Ui.btn("🖼 Gallery", "chip", () -> Capture.gallery().ifPresent(this::addPhoto));
        Button publish = Ui.btn("Publish listing", "btn-primary", this::submit);
        publish.setMaxWidth(Double.MAX_VALUE);

        VBox form = Ui.padded(10, name, address, Ui.label("Pin location", "h2"), map, setPin, pinLabel,
                Ui.label("Monthly payment", "h2"), Ui.hbox(8, price, mode),
                Ui.label("Rooms (beds / current occupants)", "h2"), roomRows, addRoomBtn,
                Ui.label("Inclusions", "h2"), water, elec, wifi,
                Ui.label("Photos", "h2"), Ui.hbox(8, cam, gal, photoLabel),
                Ui.label("Contact (at least one)", "h2"), phone, fb, email, errors, publish);
        form.setPadding(new Insets(16, 16, 60, 16));
        getChildren().addAll(bar, Ui.scroll(form));
        ((ScrollPane) getChildren().get(1)).setVvalue(0);
        this.onDone = onDone;
    }
    private final Runnable onDone;

    private void addPhoto(javafx.scene.image.Image img) {
        d.photos.add(Capture.png(img, 1280)); photoLabel.setText(d.photos.size() + " photos");
    }

    private void addRoom(int beds, int occ) {
        Spinner<Integer> b = new Spinner<>(1, 20, beds), o = new Spinner<>(0, 20, occ);
        b.setPrefWidth(90); o.setPrefWidth(90);
        roomRows.getChildren().add(Ui.hbox(8, Ui.label("Room " + (roomRows.getChildren().size() + 1), "label"), b, o));
    }

    private void submit() {
        d.name = name.getText(); d.address = address.getText(); d.phone = phone.getText(); d.facebook = fb.getText(); d.email = email.getText();
        d.priceMode = mode.getSelectionModel().getSelectedIndex() == 0 ? "per_bed" : "per_room";
        try { d.price = price.getText().isBlank() ? null : Double.parseDouble(price.getText().trim()); } catch (NumberFormatException e) { d.price = null; }
        d.water = water.isSelected(); d.electricity = elec.isSelected(); d.wifi = wifi.isSelected();
        d.rooms.clear();
        for (var n : roomRows.getChildren()) {
            var h = (HBox) n; int beds = ((Spinner<Integer>) h.getChildren().get(1)).getValue(), occ = ((Spinner<Integer>) h.getChildren().get(2)).getValue();
            d.rooms.add(new BhService.RoomDraft(beds, occ));
        }
        var errs = BhService.validate(d);
        if (!errs.isEmpty()) { errors.setText("• " + String.join("\n• ", errs)); return; }
        if (!AuthService.get().profile().verified()) { errors.setText("Only verified owners can publish. Complete verification first."); return; }
        errors.setText("");
        Async.run(() -> new BhService().publish(AuthService.get().userId(), d), id -> { Nav.toast("Listing published!"); onDone.run(); },
                e -> errors.setText("Publish failed. Make sure you are verified and online."));
    }
}
