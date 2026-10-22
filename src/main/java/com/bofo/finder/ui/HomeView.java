package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
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
    private final MapPane map = new MapPane(240);
    private final VBox list = new VBox(10);
    private final Button collegeBtn = new Button();
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
        bell.setGraphic(Icons.icon(Icons.BELL, 20, Color.web("#1B1630")));
        bell.setOnAction(e -> Nav.toast("No new notifications"));
        HBox top = Ui.hbox(12, Ui.avatar(full, 44),
                Ui.vbox(0, Ui.label("Welcome back,", "secondary"), Ui.label(name.isBlank() ? "there" : name, "greet-name")), Ui.grow(), bell);
        top.setPadding(new Insets(16, 16, 0, 16));

        HBox searchPill = Ui.hbox(10, Icons.icon(Icons.SEARCH, 20, Color.web("#8A84A0")), Ui.label("Find boarding houses near campus", "secondary"));
        searchPill.getStyleClass().add("search-pill");
        searchPill.setOnMouseClicked(e -> BofoPanel.filtersListener.accept(new JSONObject()));   // opens the Explore tab
        VBox.setMargin(searchPill, new Insets(14, 16, 0, 16));

        collegeBtn.getStyleClass().add("college-chip");
        collegeBtn.setGraphic(Icons.icon(Icons.PIN, 16, Color.web("#5B34B8")));
        collegeBtn.setOnAction(e -> pickCollege());
        setCollegeText();
        HBox collegeRow = Ui.hbox(8, collegeBtn); collegeRow.setPadding(new Insets(12, 16, 0, 16));

        radius.setShowTickLabels(false);
        debounce.setOnFinished(e -> reload());
        radius.valueProperty().addListener((o, a, v) -> { State.radiusKm.set(Math.round(v.doubleValue() * 10) / 10.0); debounce.playFromStart(); });   // reload once after the slider settles
        Label rLabel = Ui.label("", "secondary");
        rLabel.textProperty().bind(State.radiusKm.asString("Search radius: %.1f km"));
        VBox radiusCard = Ui.card(4, rLabel, radius);
        VBox.setMargin(radiusCard, new Insets(12, 16, 0, 16));

        HBox chips = Ui.chipRow(new String[]{"New", "Hot", "Best rated"}, 0, i -> { tab = BhService.Tab.values()[i]; reload(); });
        chips.setPadding(new Insets(14, 16, 4, 16));
        Label nearby = Ui.section("Nearby boarding houses"); nearby.setPadding(new Insets(8, 16, 0, 16));
        list.setPadding(new Insets(4, 16, 110, 16));

        StackPane mapWrap = new StackPane(map);
        Rectangle mc = new Rectangle(); mc.setArcWidth(44); mc.setArcHeight(44);
        mc.widthProperty().bind(mapWrap.widthProperty()); mc.heightProperty().bind(mapWrap.heightProperty());
        mapWrap.setClip(mc);
        VBox.setMargin(mapWrap, new Insets(12, 16, 0, 16));

        VBox content = new VBox(0, top, searchPill, collegeRow, mapWrap, radiusCard, chips, nearby, list);
        map.onPinTap(b -> Nav.show(new BhDetailView(b.id(), () -> Nav.show(new StudentShell(), true)), true));
        ScrollPane sp = Ui.scroll(content); VBox.setVgrow(sp, Priority.ALWAYS);
        getChildren().add(sp);
        Async.run(bh::favoriteIds, f -> { favs = f; reload(); }, e -> reload());
        if (State.ref() == null) startGps();
        ChangeListener<College> l = (o, a, b) -> { setCollegeText(); reload(); };
        State.college.addListener(l);
    }

    private void setCollegeText() {
        collegeBtn.setText((State.college.get() == null ? "Choose your college" : State.college.get().name()) + "   \u25BE");
    }

    private void startGps() {
        try {
            com.gluonhq.attach.position.PositionService.create().ifPresent(ps -> {
                ps.start();
                ps.positionProperty().addListener((o, a, p) -> {
                    if (p != null && State.gps.get() == null) { State.gps.set(new double[]{p.getLatitude(), p.getLongitude()}); reload(); }
                });
            });
        } catch (Throwable ignored) { /* permission denied or unsupported: user must pick a college */ }
    }

    private void pickCollege() {
        Async.run(bh::colleges, cs -> {
            ChoiceDialog<College> d = new ChoiceDialog<>(State.college.get(), cs);
            d.setTitle("Choose your college"); d.setHeaderText("See boarding houses near your campus");
            d.showAndWait().ifPresent(c -> {
                State.college.set(c);
                String uid = AuthService.get().userId();
                Async.bg(() -> { try {
                    com.bofo.finder.service.SupabaseClient.get().update("users", "id=eq." + uid,
                            new org.json.JSONObject().put("default_college_id", c.id()));
                } catch (Exception ignored) {} });
            });
        });
    }

    private void reload() {
        final int my = ++seq;
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
            State.offline.set(com.bofo.finder.util.Cache.servedStale);
        }, err -> { if (my == seq) State.offline.set(true); });
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
                    on -> Async.run(() -> { bh.setFavorite(uid, b.id(), on); return true; }, ok -> {}, e -> Nav.toast("Could not update favorite"))));
        }
        shown = end;
        if (shown < current.size()) list.getChildren().add(Ui.btn("Show more (" + (current.size() - shown) + ")", "btn-outline", this::renderMore));
    }
}
