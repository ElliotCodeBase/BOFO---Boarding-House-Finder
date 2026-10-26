package com.bofo.finder.ui;

import com.bofo.finder.model.College;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Capture;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Add listing. Seven numbered cards (basics, pin, price, rooms, amenities, photos, contact) and a bar that stays at the
 * bottom with the progress and the Publish button. Submission is blocked until every required field is filled;
 * server RLS also requires a verified owner.
 */
public final class ListingFormView extends VBox {
    private static final int REQUIRED = 6;       // name, address, pin, price, photo, contact

    private final BhService.Draft d = new BhService.Draft();
    private final TextField name = new TextField(), address = new TextField(), price = new TextField(),
            phone = new TextField(), fb = new TextField(), email = new TextField();
    private int modeIdx = 0;                      // 0 = per bed, 1 = per room
    private final AmenityPicker amenities = new AmenityPicker(true, null);   // owners can pick from the catalog or add their own
    private final VBox roomRows = new VBox(10);
    private final List<RoomRow> rooms = new ArrayList<>();
    private final FlowPane thumbs = new FlowPane(8, 8);
    private final Label pinLabel = Ui.label("", "pin-status"), photoLabel = Ui.label("No photos yet", "secondary"),
            errors = Ui.label("", "notice-bad"), progress = Ui.label("", "secondary");
    private final MapPane map = new MapPane(220);
    private final ProgressBar progressBar = new ProgressBar(0);         // fills as the 6 required details are completed
    private java.util.List<College> colleges = java.util.List.of();      // for "how far is this from campus"
    private final Runnable onDone;

    /** One room: beds + current occupants as − / + steppers, and a remove button when there is more than one room. */
    private static final class RoomRow {
        final Spinner<Integer> beds, occ;
        final Label title = Ui.label("", "room-title");
        final Button remove;
        final VBox node;
        RoomRow(int b, int o, Runnable onRemove) {
            beds = stepper(1, 20, b); occ = stepper(0, b, o);
            beds.valueProperty().addListener((obs, was, now) -> {               // occupants can never be more than beds
                ((SpinnerValueFactory.IntegerSpinnerValueFactory) occ.getValueFactory()).setMax(now);
                if (occ.getValue() > now) occ.getValueFactory().setValue(now);
            });
            remove = new Button(); remove.getStyleClass().setAll("button", "icon-btn", "room-remove");
            remove.setGraphic(Ui.strokeIcon(Glyphs.CLOSE, 14, Color.web("#5F5C75")));
            remove.setOnAction(e -> onRemove.run());
            HBox head = new HBox(8, title, Ui.grow(), remove); head.setAlignment(Pos.CENTER_LEFT);
            HBox steppers = new HBox(10, labeled("Beds", beds), labeled("Occupied now", occ));
            node = new VBox(8, head, steppers); node.getStyleClass().add("room-card");
        }
        private static Spinner<Integer> stepper(int min, int max, int val) {
            Spinner<Integer> s = new Spinner<>(min, max, val);
            s.getStyleClass().addAll("stepper", Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
            s.setMaxWidth(Double.MAX_VALUE); return s;
        }
        private static VBox labeled(String caption, Spinner<Integer> s) {
            VBox v = new VBox(4, Ui.label(caption, "secondary"), s); HBox.setHgrow(v, Priority.ALWAYS); return v;
        }
    }

    public ListingFormView(Runnable onDone) {
        this.onDone = onDone;
        Button back = Ui.btn("←", "heart", onDone); back.getStyleClass().setAll("button", "icon-btn");
        HBox bar = Ui.appBar("New listing"); bar.getChildren().add(0, back);

        name.setPromptText("BH name"); address.setPromptText("Address (street, barangay, city)"); price.setPromptText("Monthly payment (₱)");
        phone.setPromptText("Phone"); fb.setPromptText("Facebook / Messenger link"); email.setPromptText("Email");

        // ---- pin ----
        double[] ref = State.ref(); map.show(ref != null ? ref : new double[]{14.5995, 120.9842}, 0, java.util.List.of(), false, null);
        map.enableCenterPin();
        Async.run(() -> new BhService().colleges(), cs -> colleges = cs, e -> {});
        pinLabel.setText("No pin yet. Move the map so the pin sits on your place, then tap Set pin.");
        Button setPin = Ui.btn("Set pin at map center", "btn-outline", () -> {
            double[] c = map.center(); d.lat = c[0]; d.lng = c[1]; setPinText(String.format("Pin set: %.5f, %.5f", c[0], c[1]), true);
            routeToNearestCollege(c); refresh();
        });
        setPin.setGraphic(Ui.strokeIcon(Glyphs.PIN, 16, Color.web("#17142A"))); setPin.setGraphicTextGap(8);
        setPin.setMaxWidth(Double.MAX_VALUE);

        // ---- price ----
        HBox perRow = Ui.hbox(10, Ui.label("Charged", "secondary"), Ui.segmented(new String[]{"Per bed", "Per room"}, 0, i -> modeIdx = i));

        // ---- rooms ----
        addRoom(4, 0);
        Button addRoomBtn = Ui.btn("+ Add room", "chip", () -> addRoom(4, 0));

        // ---- photos ----
        Button cam = Ui.btn("+ Camera", "chip", () -> Capture.camera().ifPresent(this::addPhoto));
        Button gal = Ui.btn("+ Gallery", "chip", () -> Capture.gallery().ifPresent(this::addPhoto));

        // ---- cards ----
        VBox cBasics = Ui.card(10, header(1, "Basics", "What students will see first."), name, address);
        StackPane mapBox = new StackPane(map);                                  // rounded corners for the map
        Rectangle mapClip = new Rectangle(); mapClip.setArcWidth(36); mapClip.setArcHeight(36);
        mapClip.widthProperty().bind(mapBox.widthProperty()); mapClip.heightProperty().bind(mapBox.heightProperty()); mapBox.setClip(mapClip);
        VBox cPin = Ui.card(10, header(2, "Pin location", "Students use this to see how far you are from campus."), mapBox, setPin, pinLabel);
        VBox cPrice = Ui.card(10, header(3, "Monthly payment", null), price, perRow);
        VBox cRooms = Ui.card(10, header(4, "Rooms", "Beds in each room and how many are taken now."), roomRows, addRoomBtn);
        VBox cAmen = Ui.card(10, header(5, "Amenities", "Pick what your place offers. Not in the list? Type it and tap Add: students will be able to find it in the Explore filter."), amenities);
        VBox cPhotos = Ui.card(10, header(6, "Photos", "Add at least one. Clear, bright photos get more chats."), Ui.hbox(8, cam, gal, photoLabel), thumbs);
        VBox cContact = Ui.card(10, header(7, "Contact", "Add at least one way for students to reach you."), phone, fb, email);

        for (VBox c : new VBox[]{cBasics, cPin, cPrice, cRooms, cAmen, cPhotos, cContact}) c.getStyleClass().add("form-card");
        VBox form = Ui.padded(14, cBasics, cPin, cPrice, cRooms, cAmen, cPhotos, cContact);
        form.setPadding(new Insets(8, 16, 24, 16));
        ScrollPane scroll = Ui.scrollCentered(form, 560);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // ---- bottom bar (always visible) ----
        Button publish = Ui.btn("Publish listing", "btn-primary", this::submit);
        publish.setMaxWidth(Double.MAX_VALUE);
        setErrors("");
        progressBar.getStyleClass().add("form-progress"); progressBar.setMaxWidth(Double.MAX_VALUE);
        VBox barInner = new VBox(8, errors, new VBox(6, progress, progressBar), publish); barInner.setMaxWidth(560);
        StackPane barHolder = new StackPane(barInner); barHolder.getStyleClass().add("sticky-bar");
        barHolder.setPadding(new Insets(10, 16, 12, 16));

        for (TextField t : new TextField[]{name, address, price, phone, fb, email}) t.textProperty().addListener((o, a, b) -> refresh());
        refresh();

        getChildren().addAll(bar, scroll, barHolder);
        scroll.setVvalue(0);
    }

    /** Numbered section title with an optional one-line hint. */
    private static Node header(int n, String title, String hint) {
        Label num = new Label(String.valueOf(n)); num.getStyleClass().add("sec-num");
        num.setMinSize(26, 26); num.setPrefSize(26, 26); num.setMaxSize(26, 26); num.setAlignment(Pos.CENTER);
        VBox t = new VBox(2, Ui.label(title, "h2")); if (hint != null) t.getChildren().add(Ui.label(hint, "secondary"));
        HBox h = new HBox(10, num, t); h.setAlignment(Pos.TOP_LEFT); return h;
    }

    private void setPinText(String s, boolean ok) {
        pinLabel.setText(s);
        pinLabel.getStyleClass().remove("pin-status-ok");
        if (ok) pinLabel.getStyleClass().add("pin-status-ok");
    }

    private void setErrors(String s) {
        errors.setText(s);
        boolean show = s != null && !s.isBlank();
        errors.setVisible(show); errors.setManaged(show);
    }

    /** Counts the required parts that are done and shows it above the Publish button. */
    private void refresh() {
        int done = 0;
        if (!name.getText().isBlank()) done++;
        if (!address.getText().isBlank()) done++;
        if (d.lat != null && d.lng != null) done++;
        try { if (!price.getText().isBlank() && Double.parseDouble(price.getText().trim()) > 0) done++; } catch (NumberFormatException ignored) { }
        if (!d.photos.isEmpty()) done++;
        if (!phone.getText().isBlank() || !fb.getText().isBlank() || !email.getText().isBlank()) done++;
        progress.setText(done >= REQUIRED ? "All required details are filled in" : done + " of " + REQUIRED + " required details done");
        progress.getStyleClass().remove("progress-done");
        if (done >= REQUIRED) progress.getStyleClass().add("progress-done");
        progressBar.setProgress(Math.min(1.0, done / (double) REQUIRED));
        progressBar.getStyleClass().remove("form-progress-done");
        if (done >= REQUIRED) progressBar.getStyleClass().add("form-progress-done");
    }

    /** Draws the walking route from the nearest college to the dropped pin and shows the distance under the map. */
    private void routeToNearestCollege(double[] pin) {
        College best = null; double bestKm = Double.MAX_VALUE;
        for (College c : colleges) { double km = com.bofo.finder.util.Geo.km(c.lat(), c.lng(), pin[0], pin[1]); if (km < bestKm) { bestKm = km; best = c; } }
        if (best == null) return;
        final College college = best;
        map.showRoute(new double[]{college.lat(), college.lng()}, pin, college.name(), false, r -> {
            double km = r.meters() / 1000.0;
            setPinText(String.format("Pin set: %.5f, %.5f", pin[0], pin[1]) + "  \u00B7  " + com.bofo.finder.util.Geo.fmt(km) + " from " + college.name()
                    + (km <= 3 ? " (about " + com.bofo.finder.util.Geo.fmtTime(Math.max(1, com.bofo.finder.util.Geo.walkMinutes(km))) + " walk)" : ""), true);
        });
    }

    private void addPhoto(Image img) {
        byte[] png = Capture.png(img, 1280);
        d.photos.add(png);
        thumbs.getChildren().add(thumb(img, png));
        photosChanged();
    }

    /** Square, rounded preview of a photo with a small x to remove it. */
    private StackPane thumb(Image img, byte[] png) {
        double w = img.getWidth(), h = img.getHeight(), s = Math.max(1, Math.min(w, h));
        ImageView iv = new ImageView(img);
        iv.setViewport(new Rectangle2D((w - s) / 2, (h - s) / 2, s, s));
        iv.setFitWidth(76); iv.setFitHeight(76); iv.setPreserveRatio(true); iv.setSmooth(true);
        Rectangle clip = new Rectangle(76, 76); clip.setArcWidth(24); clip.setArcHeight(24); iv.setClip(clip);
        Button x = new Button(); x.getStyleClass().setAll("button", "thumb-x");
        x.setGraphic(Ui.strokeIcon(Glyphs.CLOSE, 10, Color.WHITE));
        StackPane tile = new StackPane(iv, x);
        tile.setMinSize(76, 76); tile.setPrefSize(76, 76); tile.setMaxSize(76, 76);
        StackPane.setAlignment(x, Pos.TOP_RIGHT); StackPane.setMargin(x, new Insets(4));
        x.setOnAction(e -> { d.photos.remove(png); thumbs.getChildren().remove(tile); photosChanged(); });
        return tile;
    }

    private void photosChanged() {
        int n = d.photos.size();
        photoLabel.setText(n == 0 ? "No photos yet" : n + (n == 1 ? " photo" : " photos"));
        refresh();
    }

    private void addRoom(int beds, int occ) {
        RoomRow[] ref = new RoomRow[1];
        ref[0] = new RoomRow(beds, occ, () -> { rooms.remove(ref[0]); roomRows.getChildren().remove(ref[0].node); renumberRooms(); });
        rooms.add(ref[0]); roomRows.getChildren().add(ref[0].node); renumberRooms();
    }

    private void renumberRooms() {
        for (int i = 0; i < rooms.size(); i++) {
            rooms.get(i).title.setText("Room " + (i + 1));
            boolean many = rooms.size() > 1;
            rooms.get(i).remove.setVisible(many); rooms.get(i).remove.setManaged(many);
        }
    }

    private boolean has(String amenityName) {
        return amenities.selectedAmenities().stream().anyMatch(a -> a.name().equalsIgnoreCase(amenityName));
    }

    private void submit() {
        d.name = name.getText(); d.address = address.getText(); d.phone = phone.getText(); d.facebook = fb.getText(); d.email = email.getText();
        d.priceMode = modeIdx == 0 ? "per_bed" : "per_room";
        try { d.price = price.getText().isBlank() ? null : Double.parseDouble(price.getText().trim()); } catch (NumberFormatException e) { d.price = null; }
        d.amenityIds = amenities.selectedIds();
        d.water = has("Free water"); d.electricity = has("Free electricity"); d.wifi = has("Free Wi-Fi");   // old columns stay in sync
        d.rooms.clear();
        for (RoomRow r : rooms) d.rooms.add(new BhService.RoomDraft(r.beds.getValue(), r.occ.getValue()));
        var errs = BhService.validate(d);
        if (!errs.isEmpty()) { setErrors("• " + String.join("\n• ", errs)); return; }
        if (!AuthService.get().profile().verified()) { setErrors("Only verified owners can publish. Complete verification first."); return; }
        setErrors("");
        Async.run(() -> new BhService().publish(AuthService.get().userId(), d), id -> { Nav.toast("Listing published!"); onDone.run(); },
                e -> setErrors("Publish failed. Make sure you are verified and online."));
    }
}
