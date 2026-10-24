package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Locator;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.json.JSONObject;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** Default student home: greeting, college selector, map with radius, New/Hot/Best tabs sorted nearest-first. */
public final class HomeView extends VBox {
    private final BhService bh = new BhService();
    private final MapPane map = new MapPane(340);
    private final CardGrid list = new CardGrid(10, 320);
    private final TextField collegeField = new TextField();       // type to search your college ...
    private final ListView<College> suggestions = new ListView<>(); // ... or open the full list with the arrow
    private List<College> allColleges = List.of();
    private boolean settingText;                                  // true while we fill the field ourselves (not the user typing)
    private final javafx.animation.PauseTransition closeLater = new javafx.animation.PauseTransition(javafx.util.Duration.millis(140));
    private final Slider radius = new Slider(0.5, 5, State.radiusKm.get());
    private BhService.Tab tab = BhService.Tab.NEW;
    private Set<String> favs = new HashSet<>();
    private List<BoardingHouse> current = List.of();
    private int seq = 0;                     // request id: ignore responses from outdated requests
    private int shown = 0;                   // cards rendered so far (lazy batches keep the UI light)
    private static final int BATCH = 15;
    private final javafx.animation.PauseTransition debounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(350));

    public HomeView() {
        super(0);
        var prof = AuthService.get().profile();
        String full = prof == null ? "" : prof.fullName();
        String name = full.isBlank() ? "" : full.split(" ")[0];

        Button bell = new Button(); bell.getStyleClass().add("icon-btn");
        bell.setGraphic(Icons.icon(Icons.BELL, 20, Color.web("#17142A")));
        bell.setOnAction(e -> Nav.toast("No new notifications"));
        HBox top = Ui.hbox(12, Ui.avatar(full, 40),
                Ui.vbox(0, Ui.label("Welcome back,", "secondary"), Ui.label(name.isBlank() ? "there" : name, "greet-name")), Ui.grow(), bell);
        top.setPadding(new Insets(14, 16, 0, 16));

        // Search bar: type a campus (autocomplete) or open the dropdown; picking one opens Explore near that campus.
        // Enter on text that is not a campus searches boarding houses by name in Explore.
        CampusPicker searchPill = new CampusPicker("Find boarding houses near campus", false,
                c -> { CampusPicker.remember(c); BofoPanel.filtersListener.accept(new JSONObject()); },
                text -> BofoPanel.filtersListener.accept(new JSONObject().put("text", text)), null);
        VBox.setMargin(searchPill, new Insets(14, 16, 0, 16));

        // ---- college: search box with a dropdown arrow ----
        collegeField.getStyleClass().add("college-field");
        collegeField.setPromptText("Search your college");
        HBox.setHgrow(collegeField, Priority.ALWAYS);
        Button clearBtn = new Button(); clearBtn.getStyleClass().add("search-clear");
        clearBtn.setGraphic(Ui.strokeIcon(Glyphs.CLOSE, 14, Color.web("#8D8AA3")));
        clearBtn.visibleProperty().bind(collegeField.textProperty().isNotEmpty());
        clearBtn.managedProperty().bind(clearBtn.visibleProperty());
        clearBtn.setOnAction(e -> { setField(""); collegeField.requestFocus(); showSuggestions(""); });
        Button arrow = new Button("\u25BE"); arrow.getStyleClass().add("college-arrow");
        arrow.setOnAction(e -> { if (suggestions.isVisible()) closeSuggestions(); else showSuggestions(""); });   // arrow = the whole list
        HBox collegeBox = new HBox(6, Icons.icon(Icons.PIN, 16, Color.web("#5B3BF0")), collegeField, clearBtn, arrow);
        collegeBox.setAlignment(Pos.CENTER_LEFT); collegeBox.getStyleClass().add("college-box");
        HBox.setHgrow(collegeBox, Priority.ALWAYS); collegeBox.setMaxWidth(Double.MAX_VALUE);
        setCollegeText();

        suggestions.getStyleClass().add("college-list");
        suggestions.setFixedCellSize(44);
        suggestions.setPlaceholder(Ui.label("No college found", "secondary"));
        suggestions.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(College c, boolean empty) { super.updateItem(c, empty); setText(empty || c == null ? null : c.name()); }
        });
        suggestions.setVisible(false); suggestions.setManaged(false);
        suggestions.setOnMouseClicked(e -> { College c = suggestions.getSelectionModel().getSelectedItem(); if (c != null) choose(c); });
        collegeField.textProperty().addListener((o, was, now) -> { if (!settingText) showSuggestions(now); });
        collegeField.setOnAction(e -> { if (!suggestions.getItems().isEmpty()) choose(suggestions.getItems().get(0)); });   // Enter = best match
        collegeField.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) { closeSuggestions(); setCollegeText(); } });
        collegeField.focusedProperty().addListener((o, was, on) -> { if (on) { collegeField.selectAll(); showSuggestions(""); } else closeLater.playFromStart(); });
        suggestions.focusedProperty().addListener((o, was, on) -> { if (!on) closeLater.playFromStart(); });
        closeLater.setOnFinished(e -> { if (!collegeField.isFocused() && !suggestions.isFocused() && !suggestions.isHover()) { closeSuggestions(); setCollegeText(); } });
        Button nearMe = new Button(); nearMe.getStyleClass().add("near-btn");                       // round icon button: "show places near me"
        nearMe.setGraphic(Ui.strokeIcon(Glyphs.LOCATE, 20, Color.web("#2F6FED")));
        nearMe.setTooltip(new Tooltip("Near me"));
        nearMe.setOnAction(e -> Locator.get().locate(p -> { if (State.college.get() != null) State.college.set(null); else reload(); }));
        HBox collegeRow = Ui.hbox(8, collegeBox, nearMe); collegeRow.setPadding(new Insets(12, 16, 0, 16));
        VBox.setMargin(suggestions, new Insets(6, 16, 0, 16));

        Label hintText = Ui.label("Location is off, so we can't show places near you. Turn it on, or choose your college.", "secondary");
        HBox.setHgrow(hintText, Priority.ALWAYS);
        HBox locHint = Ui.hbox(10, hintText, Ui.btn("Try again", "link", () -> Locator.get().start()));
        locHint.getStyleClass().add("card");
        locHint.visibleProperty().bind(Locator.get().status.isEqualTo(Locator.Status.UNAVAILABLE).and(State.college.isNull()));
        locHint.managedProperty().bind(locHint.visibleProperty());
        VBox.setMargin(locHint, new Insets(10, 16, 0, 16));

        // radius: a slim floating pill on top of the map (only when a college is chosen), snaps to 0.5 km
        radius.setShowTickLabels(false); radius.setShowTickMarks(false);
        radius.setMajorTickUnit(0.5); radius.setMinorTickCount(0); radius.setSnapToTicks(true); radius.setBlockIncrement(0.5);
        radius.getStyleClass().add("radius-slider");
        radius.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(radius, Priority.ALWAYS);
        radius.skinProperty().addListener((o, was, sk) -> javafx.application.Platform.runLater(this::paintTrack));
        debounce.setOnFinished(e -> reload());
        radius.valueProperty().addListener((o, a, v) -> {
            double km = Math.round(v.doubleValue() * 10) / 10.0;
            State.radiusKm.set(km);
            paintTrack();
            map.previewRadius(km);                  // circle + zoom glide right away; the list reloads once the slider settles
            debounce.playFromStart();
        });
        Label rTitle = Ui.label("Radius", "secondary");
        Label rValue = Ui.label("", "radius-pill"); rValue.setMinWidth(Region.USE_PREF_SIZE);
        rValue.textProperty().bind(State.radiusKm.asString("%.1f km"));
        HBox radiusBar = new HBox(10, rTitle, radius, rValue);
        radiusBar.setAlignment(Pos.CENTER_LEFT); radiusBar.getStyleClass().add("radius-bar");
        radiusBar.setMaxWidth(380); radiusBar.setMaxHeight(Region.USE_PREF_SIZE);
        radiusBar.visibleProperty().bind(State.college.isNotNull());
        radiusBar.managedProperty().bind(radiusBar.visibleProperty());

        HBox chips = Ui.chipRow(new String[]{"New", "Hot", "Best rated"}, 0, i -> { tab = BhService.Tab.values()[i]; reload(); });
        chips.setPadding(new Insets(14, 16, 4, 16));
        Label nearby = Ui.section("Nearby boarding houses"); nearby.setPadding(new Insets(8, 16, 0, 16));
        list.setPadding(new Insets(4, 16, 72, 16));

        StackPane mapWrap = new StackPane(map, radiusBar);
        StackPane.setAlignment(radiusBar, Pos.TOP_CENTER); StackPane.setMargin(radiusBar, new Insets(10, 12, 0, 12));
        Rectangle mc = new Rectangle(); mc.setArcWidth(28); mc.setArcHeight(28);
        mc.widthProperty().bind(mapWrap.widthProperty()); mc.heightProperty().bind(mapWrap.heightProperty());
        mapWrap.setClip(mc);
        VBox.setMargin(mapWrap, new Insets(12, 16, 0, 16));

        VBox content = new VBox(0, top, searchPill, collegeRow, suggestions, locHint, mapWrap, chips, nearby, list);
        map.enablePreview(b -> Nav.show(new BhDetailView(b.id(), () -> Nav.show(new StudentShell(), true)), true));
        ScrollPane sp = Ui.scroll(content); VBox.setVgrow(sp, Priority.ALWAYS);
        getChildren().add(sp);
        Async.run(bh::favoriteIds, f -> { favs = f; reload(); }, e -> reload());
        Async.run(bh::colleges, cs -> { allColleges = cs; map.showColleges(cs); }, e -> {});   // search list + every campus on the map
        startGps();
        ChangeListener<College> l = (o, a, b) -> { setCollegeText(); reload(); };
        State.college.addListener(l);
    }

    /** Colors the filled part of the radius track purple (JavaFX has no built-in filled track). */
    private void paintTrack() {
        javafx.scene.Node t = radius.lookup(".track");
        if (t == null) return;
        double pct = (radius.getValue() - radius.getMin()) / (radius.getMax() - radius.getMin()) * 100;
        t.setStyle(String.format(java.util.Locale.ROOT, "-fx-background-color:linear-gradient(to right,#5B3BF0 %.1f%%,#E4E0F5 %.1f%%);", pct, pct));
    }

    /** Puts the chosen college's name in the search box (empty = none chosen). */
    private void setCollegeText() { setField(State.college.get() == null ? "" : State.college.get().name()); }

    private void setField(String t) { settingText = true; collegeField.setText(t); settingText = false; }

    /** Fills the suggestion list: names that start with the text first, then names that contain it. Empty text = every college. */
    private void showSuggestions(String text) {
        String q = text == null ? "" : text.trim().toLowerCase();
        College chosen = State.college.get();
        if (chosen != null && q.equals(chosen.name().toLowerCase())) q = "";        // the box still shows the chosen name: offer the whole list
        List<College> out = new java.util.ArrayList<>();
        if (q.isEmpty()) out.addAll(allColleges);
        else {
            for (College c : allColleges) if (c.name().toLowerCase().startsWith(q)) out.add(c);
            for (College c : allColleges) { String n = c.name().toLowerCase(); if (!n.startsWith(q) && n.contains(q)) out.add(c); }
        }
        suggestions.getItems().setAll(out);
        suggestions.setPrefHeight(Math.min(Math.max(out.size(), 1), 6) * 44 + 10);
        suggestions.setVisible(true); suggestions.setManaged(true);
    }

    private void closeSuggestions() { suggestions.setVisible(false); suggestions.setManaged(false); }

    private void choose(College c) {
        closeSuggestions();
        State.college.set(c);                                  // the listener below updates the box and reloads
        setCollegeText();
        suggestions.getParent().requestFocus();
        String uid = AuthService.get().userId();
        Async.bg(() -> { try {
            com.bofo.finder.service.SupabaseClient.get().update("users", "id=eq." + uid,
                    new org.json.JSONObject().put("default_college_id", c.id()));
        } catch (Exception ignored) {} });
    }

    /** Asks for location (system dialog on Android). On the first fix the list reloads if it is sorted by "where I am". */
    private void startGps() {
        Locator.get().startWithPrompt();
        boolean[] first = {State.gps.get() == null};
        ChangeListener<double[]> l = (o, a, p) -> {
            if (first[0] && p != null) { first[0] = false; if (State.college.get() == null) reload(); }
        };
        State.gps.addListener(l);
        sceneProperty().addListener((o, was, sc) -> { if (sc == null) State.gps.removeListener(l); });
    }

    private void reload() {
        final int my = ++seq;
        list.getChildren().setAll(Skeleton.cards(4));            // skeleton while loading
        double[] ref = State.ref();
        boolean circle = State.college.get() != null;
        Double rad = circle ? State.radiusKm.get() : null;
        Async.run(() -> bh.list(tab, ref == null ? null : ref[0], ref == null ? null : ref[1], rad, null), items -> {
            if (my != seq) return;                               // a newer request is in flight
            current = items; shown = 0;
            map.show(ref, circle ? State.radiusKm.get() : 0, items, circle, null);
            list.getChildren().clear();
            if (items.isEmpty()) list.getChildren().add(Ui.emptyState("Nothing nearby yet", ref == null ? "Allow location or choose your college to see nearby boarding houses." : "No boarding houses with free beds in this radius. Try a larger radius."));
            renderMore();
        }, err -> { if (my == seq) list.getChildren().setAll(Ui.errorState(err, this::reload)); });   // banner is set globally
    }

    /** Renders the next batch of cards; a "Show more" button appears when more remain. */
    private void renderMore() {
        if (!list.getChildren().isEmpty() && list.getChildren().get(list.getChildren().size() - 1) instanceof Button) list.getChildren().remove(list.getChildren().size() - 1);
        String uid = AuthService.get().userId();
        int end = Math.min(current.size(), shown + BATCH);
        for (int i = shown; i < end; i++) {
            BoardingHouse b = current.get(i);
            list.getChildren().add(new BhCard(b, tab == BhService.Tab.HOT, favs.contains(b.id()),
                    x -> Nav.show(new BhDetailView(x.id(), () -> Nav.show(new StudentShell(), true)), true),
                    (on, undo) -> BhCard.saveFavorite(bh, uid, b.id(), on, undo)));
        }
        shown = end;
        if (shown < current.size()) list.getChildren().add(Ui.btn("Show more (" + (current.size() - shown) + ")", "btn-outline", this::renderMore));
    }
}
