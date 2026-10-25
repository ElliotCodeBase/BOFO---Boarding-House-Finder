package com.bofo.finder.ui;

import com.bofo.finder.model.Amenity;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Fuzzy;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.*;

/**
 * Amenity chips with a search box. Used in two places:
 * <ul>
 *   <li>Explore filter ({@code allowCreate = false}): popular amenities as chips, a search box to find any other amenity
 *       in the catalog (including ones owners added) and add it to the filter. Unknown text just says "no match".</li>
 *   <li>Owner listing form ({@code allowCreate = true}): same, plus an "Add \"text\"" chip that creates a brand-new amenity
 *       (a leading emoji becomes its icon, e.g. "🏊 Swimming pool").</li>
 * </ul>
 */
public final class AmenityPicker extends VBox {
    private static final int COLLAPSED = 10, MAX_HITS = 24;

    private final BhService svc = new BhService();
    private final boolean allowCreate;
    private final Runnable onChange;
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, Amenity> byId = new HashMap<>();
    private final TextField search = new TextField();
    private final VBox selectedBox = new VBox(6);
    private final FlowPane selectedChips = new FlowPane(8, 8);
    private final Label heading = Ui.label("Popular amenities", "secondary");
    private final FlowPane chips = new FlowPane(8, 8);
    private final Button more = new Button();
    private final Label note = Ui.label("", "secondary");
    private List<Amenity> catalog = List.of();
    private boolean loaded, failed, expanded, busy;
    private Runnable whenLoaded;

    public AmenityPicker(boolean allowCreate, Runnable onChange) {
        super(10);
        this.allowCreate = allowCreate; this.onChange = onChange == null ? () -> {} : onChange;

        search.getStyleClass().add("college-field");
        search.setPromptText(allowCreate ? "Search or add your own (e.g. \uD83C\uDFCA Swimming pool)" : "Search amenities (e.g. aircon, laundry)");
        search.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length() <= 44 ? c : null));
        HBox.setHgrow(search, Priority.ALWAYS);
        Button clear = new Button(); clear.getStyleClass().add("search-clear");
        clear.setGraphic(Ui.strokeIcon(Glyphs.CLOSE, 14, Color.web("#8D8AA3")));
        clear.visibleProperty().bind(search.textProperty().isNotEmpty()); clear.managedProperty().bind(clear.visibleProperty());
        clear.setOnAction(e -> { search.clear(); search.requestFocus(); });
        HBox box = new HBox(6, Ui.strokeIcon(Glyphs.SEARCH, 18, Color.web("#5B3BF0")), search, clear);
        box.setAlignment(Pos.CENTER_LEFT); box.getStyleClass().add("college-box");
        search.focusedProperty().addListener((o, was, on) -> { box.getStyleClass().remove("college-box-focus"); if (on) box.getStyleClass().add("college-box-focus"); });
        search.textProperty().addListener((o, a, b) -> render());
        search.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) enter(); });

        selectedBox.getChildren().addAll(Ui.label("Selected", "secondary"), selectedChips);
        selectedBox.setManaged(false); selectedBox.setVisible(false);
        more.getStyleClass().add("link"); more.setOnAction(e -> { expanded = !expanded; render(); });
        note.setWrapText(true);
        getChildren().addAll(box, selectedBox, heading, chips, more, note);

        Async.run(svc::amenities, list -> { setCatalog(list); }, this::loadFailed);
        render();
    }

    /* ---------------- public API ---------------- */
    /** Ids of the chosen amenities, in the order they were chosen. */
    public List<String> selectedIds() { return new ArrayList<>(selected); }
    public List<Amenity> selectedAmenities() { List<Amenity> l = new ArrayList<>(); for (String id : selected) { Amenity a = byId.get(id); if (a != null) l.add(a); } return l; }
    public void setSelected(Collection<String> ids) { selected.clear(); selected.addAll(ids); render(); }
    public void clearSelection() { selected.clear(); search.clear(); render(); }
    public List<Amenity> catalog() { return catalog; }
    /** Runs now if the catalog is already here, else right after it loads (used to map BOFO AI words to amenities). */
    public void whenLoaded(Runnable r) { if (loaded) r.run(); else whenLoaded = r; }

    /* ---------------- internals ---------------- */
    private void setCatalog(List<Amenity> list) {
        catalog = list; byId.clear(); for (Amenity a : list) byId.put(a.id(), a);
        loaded = true; failed = false; render();
        if (whenLoaded != null) { Runnable r = whenLoaded; whenLoaded = null; r.run(); }
    }

    private void loadFailed(Throwable e) {
        System.err.println("[AmenityPicker] couldn't load the amenity list (is supabase/migrations/005_amenities.sql run?): " + e);
        failed = true; render();
    }

    /** What can be suggested: students only see amenities that at least one boarding house has; owners see the whole catalog. */
    public List<Amenity> available() {
        if (allowCreate) return catalog;
        List<Amenity> l = new ArrayList<>();
        for (Amenity a : catalog) if (a.uses() > 0) l.add(a);
        return l;
    }

    private record Hit(Amenity a, int score) {}

    /** Best matches first (exact words, nicknames, small typos), no API. */
    private static List<Hit> search(String q, List<Amenity> pool) {
        List<Hit> hits = new ArrayList<>();
        for (Amenity a : pool) { int sc = Fuzzy.score(q, a.name()); if (sc > 0) hits.add(new Hit(a, sc)); }
        hits.sort(Comparator.comparingInt(Hit::score).reversed().thenComparing(Comparator.comparingInt((Hit h) -> h.a().uses()).reversed()));
        return hits;
    }

    private void toggle(String id) {
        if (!selected.remove(id)) selected.add(id);
        render(); onChange.run();
    }

    private Button chip(Amenity a, boolean on, boolean withX) { return chip(a, on, withX, false); }

    private Button chip(Amenity a, boolean on, boolean withX, boolean count) {
        Button b = new Button(a.label() + (count && a.uses() > 0 ? "  (" + a.uses() + ")" : "") + (withX ? "  \u2715" : ""));
        b.getStyleClass().add(on ? "amenity-chip-on" : "amenity-chip");
        b.setOnAction(e -> toggle(a.id()));
        return b;
    }

    private Amenity exact(String q) {
        for (Amenity a : available()) if (a.name().equalsIgnoreCase(q)) return a;
        return null;
    }

    private void render() {
        chips.getChildren().clear(); selectedChips.getChildren().clear(); note.setText("");
        more.setVisible(false); more.setManaged(false);
        boolean haveSel = false;
        for (String id : selected) { Amenity a = byId.get(id); if (a != null) { selectedChips.getChildren().add(chip(a, true, true)); haveSel = true; } }
        selectedBox.setVisible(haveSel); selectedBox.setManaged(haveSel);

        if (!loaded) {
            heading.setVisible(false); heading.setManaged(false);
            if (failed) {
                Button retry = Ui.btn("Amenities aren't available right now. Tap to retry", "link", () -> {
                    failed = false; render(); Async.run(svc::amenities, this::setCatalog, this::loadFailed);
                });
                chips.getChildren().add(retry);
            } else note.setText("Loading amenities\u2026");
            return;
        }
        String q = search.getText().trim();
        boolean searching = !q.isEmpty();
        List<Amenity> pool = available();
        heading.setVisible(true); heading.setManaged(true);

        if (!searching) {
            heading.setText(expanded ? "All amenities" : "Popular amenities");
            int shown = 0, hidden = 0;
            for (Amenity a : pool) {
                if (selected.contains(a.id())) continue;                     // already in the "Selected" row
                if (expanded || shown < COLLAPSED) { chips.getChildren().add(chip(a, false, false)); shown++; } else hidden++;
            }
            if (hidden > 0 || (expanded && pool.size() > COLLAPSED)) {
                more.setText(expanded ? "Show fewer" : "Show all amenities (" + hidden + " more)");
                more.setVisible(true); more.setManaged(true);
            }
            if (pool.isEmpty()) {
                heading.setVisible(false); heading.setManaged(false);
                note.setText(allowCreate ? "No amenities yet. Type one above and tap Add." : "No boarding house has listed amenities yet. Check back soon.");
            }
            return;
        }

        List<Hit> hits = search(q, pool);
        if (!hits.isEmpty()) {
            heading.setText(hits.get(0).score() >= 80 ? "Matching amenities" : "Did you mean");     // below 80 = nickname or typo
            for (Hit h : hits.subList(0, Math.min(MAX_HITS, hits.size())))
                chips.getChildren().add(chip(h.a(), selected.contains(h.a().id()), false, !allowCreate));
        } else {
            heading.setVisible(false); heading.setManaged(false);
        }
        if (allowCreate && exact(q) == null) {
            Button add = new Button("\uFF0B Add \u201C" + q + "\u201D"); add.getStyleClass().add("amenity-add");
            add.setDisable(busy);
            add.setOnAction(e -> createCustom(q));
            chips.getChildren().add(add);
        }
        if (hits.isEmpty()) {
            if (allowCreate) note.setText("Not in the list yet. Tap Add to create it for everyone.");
            else {
                List<Hit> unused = search(q, catalog);                       // known amenity, but no boarding house has it right now
                note.setText(unused.isEmpty()
                        ? "No boarding house has \u201C" + q + "\u201D yet. Try a similar word, like \u201Caircon\u201D or \u201Claundry\u201D."
                        : "No boarding house has \u201C" + unused.get(0).a().name() + "\u201D yet. Try another amenity.");
            }
        }
    }

    /** Enter key: toggle the exact match, else (owners) create it, else (students) take the best match. */
    private void enter() {
        String q = search.getText().trim();
        if (q.isEmpty()) return;
        Amenity ex = exact(q);
        if (ex != null) { if (!selected.contains(ex.id())) toggle(ex.id()); search.clear(); return; }
        if (allowCreate) { createCustom(q); return; }
        List<Hit> hits = search(q, available());
        if (!hits.isEmpty()) { Amenity best = hits.get(0).a(); if (!selected.contains(best.id())) toggle(best.id()); search.clear(); }
    }

    private void createCustom(String text) {
        if (busy) return;
        String name = text.trim(), icon = null;
        int cp = name.codePointAt(0);
        if (cp > 0xFFFF || Character.getType(cp) == Character.OTHER_SYMBOL) {       // a leading emoji becomes the icon
            int end = Character.charCount(cp);
            while (end < name.length() && (name.codePointAt(end) == 0xFE0F || name.codePointAt(end) == 0x200D)) end += Character.charCount(name.codePointAt(end));
            icon = name.substring(0, end); name = name.substring(end).trim();
        }
        name = name.replaceAll("\\s+", " ");
        if (name.length() < 2) { Nav.toast("Amenity name is too short"); return; }
        Amenity ex = exact(name);
        if (ex != null) { if (!selected.contains(ex.id())) toggle(ex.id()); search.clear(); return; }
        final String n = name, ic = icon;
        busy = true; render();
        Async.run(() -> svc.getOrCreateAmenity(n, ic), a -> {
            busy = false;
            if (!byId.containsKey(a.id())) { List<Amenity> l = new ArrayList<>(catalog); l.add(a); catalog = l; byId.put(a.id(), a); }
            selected.add(a.id()); search.clear(); render(); onChange.run();
        }, e -> { busy = false; render(); Ui.failToast(e, "Couldn't add that amenity. Check that you're verified and online."); });
    }
}
