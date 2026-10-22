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

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** Default student home: greeting, college selector, map with radius, New/Hot/Best tabs sorted nearest-first. */
public final class HomeView extends VBox {
    private final BhService bh = new BhService();
    private final MapPane map = new MapPane(240);
    private final VBox list = new VBox(10);
    private final Label collegeLabel = Ui.label("", "h2");
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
        String name = AuthService.get().profile() == null ? "" : AuthService.get().profile().fullName().split(" ")[0];
        Button change = Ui.btn("Change college", "btn-cta", this::pickCollege);
        Button bell = Ui.btn("🔔", "heart", () -> Nav.toast("No new notifications"));
        bell.setStyle("-fx-text-fill:white;-fx-font-size:18px;-fx-background-color:transparent;");
        HBox bar = Ui.appBar("Hi, " + name + " 👋", bell);
        setCollegeText();
        HBox collegeRow = Ui.hbox(8, collegeLabel, Ui.grow(), change); collegeRow.setPadding(new Insets(10, 16, 4, 16));

        radius.setShowTickLabels(false);
        debounce.setOnFinished(e -> reload());
        radius.valueProperty().addListener((o, a, v) -> { State.radiusKm.set(Math.round(v.doubleValue() * 10) / 10.0); debounce.playFromStart(); });   // reload once after the slider settles
        Label rLabel = Ui.label("", "secondary");
        rLabel.textProperty().bind(State.radiusKm.asString("Radius: %.1f km"));
        HBox rRow = Ui.hbox(8, rLabel, radius); HBox.setHgrow(radius, Priority.ALWAYS); rRow.setPadding(new Insets(0, 16, 4, 16));

        HBox chips = Ui.chipRow(new String[]{"New", "🔥 Hot", "⭐ Best"}, 0, i -> { tab = BhService.Tab.values()[i]; reload(); });
        chips.setPadding(new Insets(4, 16, 4, 16));
        list.setPadding(new Insets(4, 16, 90, 16));
        VBox content = new VBox(0, map, collegeRow, rRow, chips, list);
        map.onPinTap(b -> Nav.show(new BhDetailView(b.id(), () -> Nav.show(new StudentShell(), true)), true));
        ScrollPane sp = Ui.scroll(content); VBox.setVgrow(sp, Priority.ALWAYS);
        getChildren().addAll(bar, sp);
        Async.run(bh::favoriteIds, f -> { favs = f; reload(); }, e -> reload());
        if (State.ref() == null) startGps();
        ChangeListener<College> l = (o, a, b) -> { setCollegeText(); reload(); };
        State.college.addListener(l);
    }

    private void setCollegeText() {
        collegeLabel.setText(State.college.get() == null ? "Choose a college to see distances" : "📍 " + State.college.get().name());
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
            if (items.isEmpty()) list.getChildren().add(new Mascot(Mascot.Expression.THINKING, 90));
            if (items.isEmpty()) list.getChildren().add(Ui.label(ref == null ? "Allow location or choose a college to see nearby boarding houses." : "No boarding houses with free beds in this radius. Try enlarging it.", "secondary"));
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
