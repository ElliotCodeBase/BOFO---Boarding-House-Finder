package com.bofo.finder.ui;

import com.bofo.finder.model.College;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Campus search box: type to get autocomplete suggestions (matching part in bold), or tap the arrow to open the whole
 * campus list as a dropdown. Up/Down move through the suggestions, Enter picks the highlighted one (else the best match),
 * Esc closes.
 *
 * <p>{@code syncWithState = true}: the box mirrors {@link State#college} (shows the chosen campus, clearing it clears the
 * choice). {@code false}: a plain search box that empties after a pick (used by the Home search bar).
 */
public final class CampusPicker extends VBox {
    private static final double ROW = 44;
    private static final int MAX_ROWS = 6;

    private final TextField field = new TextField();
    private final ListView<College> list = new ListView<>();
    private final PauseTransition closeLater = new PauseTransition(Duration.millis(140));
    private final boolean sync;
    private final Consumer<College> onPick;
    private final Consumer<String> onEnterText;     // Enter with text that matches no campus (may be null)
    private final Consumer<String> onType;          // the user typed or cleared text: use it as a name/area search (may be null)
    private final ChangeListener<College> stateListener;
    private final HBox box;                          // the search bar itself; the dropdown floats right under it
    private List<College> all = List.of();
    private boolean settingText;                    // true while we fill the field ourselves (not the user typing)

    public CampusPicker(String prompt, boolean syncWithState, Consumer<College> onPick, Consumer<String> onEnterText, Consumer<String> onType) {
        super(6);
        this.sync = syncWithState; this.onPick = onPick; this.onEnterText = onEnterText; this.onType = onType;
        this.stateListener = (o, was, now) -> { if (syncWithState) setField(now == null ? "" : now.name()); };

        field.getStyleClass().add("college-field"); field.setPromptText(prompt);
        HBox.setHgrow(field, Priority.ALWAYS);
        Button clear = new Button(); clear.getStyleClass().add("search-clear");
        clear.setGraphic(Ui.strokeIcon(Glyphs.CLOSE, 14, Color.web("#8D8AA3")));
        clear.visibleProperty().bind(field.textProperty().isNotEmpty()); clear.managedProperty().bind(clear.visibleProperty());
        clear.setOnAction(e -> {
            boolean wasCampus = sync && isChosenText();
            setField("");
            if (wasCampus) State.college.set(null);                                       // X on the campus name clears the campus; X on typed text clears only the text
            if (this.onType != null) this.onType.accept("");
            field.requestFocus(); refresh("");
        });
        Button arrow = new Button("▾"); arrow.getStyleClass().add("college-arrow");
        arrow.setOnAction(e -> { if (list.isVisible()) close(); else refresh(""); });      // arrow = the whole campus list
        javafx.scene.layout.StackPane searchIcon = new javafx.scene.layout.StackPane(Ui.strokeIcon(Glyphs.SEARCH, 18, Color.web("#5B3BF0")));
        searchIcon.getStyleClass().add("search-icon-wrap");
        field.setMinWidth(0); field.setPrefColumnCount(1);              // a text field wants ~12 characters by default: that alone made the screen wider than a small phone
        box = new HBox(8, searchIcon, field, clear, arrow);
        box.setMinWidth(0);
        box.setAlignment(Pos.CENTER_LEFT); box.getStyleClass().add("college-box");
        field.focusedProperty().addListener((o, was, on) -> { box.getStyleClass().remove("college-box-focus"); if (on) box.getStyleClass().add("college-box-focus"); });

        list.getStyleClass().add("college-list"); list.setFixedCellSize(ROW);
        list.setPlaceholder(Ui.label("No campus found", "secondary"));
        list.setCellFactory(lv -> new ListCell<>() {
            { setPrefWidth(0); }                                        // a long name wraps instead of adding a sideways scrollbar
            @Override protected void updateItem(College c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setText(null); setGraphic(null); return; }
                javafx.scene.Node g = highlight(c.name(), field.getText());
                if (g instanceof javafx.scene.layout.Region) {
                    javafx.scene.layout.Region r = (javafx.scene.layout.Region) g;
                    r.prefWidthProperty().bind(list.widthProperty().subtract(44));
                    r.maxWidthProperty().bind(list.widthProperty().subtract(44));
                }
                setText(null); setGraphic(g);
            }
        });
        list.setVisible(false); list.setManaged(false);                 // never managed: it floats over the content below (see layoutChildren)
        list.setOnMouseClicked(e -> { College c = list.getSelectionModel().getSelectedItem(); if (c != null) pick(c); });

        field.textProperty().addListener((o, was, now) -> { if (!settingText) { refresh(now); if (this.onType != null) this.onType.accept(now); } });
        field.focusedProperty().addListener((o, was, on) -> {
            if (on) { field.selectAll(); refresh(sync && isChosenText() ? "" : field.getText()); } else closeLater.playFromStart();
        });
        list.focusedProperty().addListener((o, was, on) -> { if (!on) closeLater.playFromStart(); });
        closeLater.setOnFinished(e -> { if (!field.isFocused() && !list.isFocused() && !list.isHover()) { close(); } });
        field.setOnKeyPressed(e -> {
            KeyCode k = e.getCode();
            if (k == KeyCode.DOWN || k == KeyCode.UP) {
                if (!list.isVisible()) refresh(field.getText());
                int n = list.getItems().size(); if (n == 0) { e.consume(); return; }
                int i = list.getSelectionModel().getSelectedIndex();
                i = k == KeyCode.DOWN ? Math.min(n - 1, i + 1) : Math.max(0, i - 1);
                list.getSelectionModel().select(i); list.scrollTo(i); e.consume();
            } else if (k == KeyCode.ESCAPE) { close(); }
        });
        field.setOnAction(e -> {
            College sel = list.getSelectionModel().getSelectedItem();
            if (sel == null && !list.getItems().isEmpty()) sel = list.getItems().get(0);   // Enter = best match
            if (sel != null && list.isVisible()) pick(sel);
            else if (this.onEnterText != null && !field.getText().isBlank()) { close(); this.onEnterText.accept(field.getText().trim()); }
        });

        getChildren().addAll(box, list);
        State.college.addListener(stateListener);
        sceneProperty().addListener((o, was, sc) -> { if (sc == null) State.college.removeListener(stateListener); else { State.college.removeListener(stateListener); State.college.addListener(stateListener); } });
        Async.run(() -> new BhService().colleges(), cs -> { all = cs; if (list.isVisible()) refresh(field.isFocused() ? field.getText() : ""); }, e -> {});
        if (sync && State.college.get() != null) setField(State.college.get().name());
    }

    /** The dropdown is not part of the layout (so it never squeezes or pushes the page): it is placed right under the bar. */
    @Override protected void layoutChildren() {
        super.layoutChildren();
        if (list.isVisible()) {
            double w = getWidth();
            list.resizeRelocate(0, box.getLayoutY() + box.getHeight() + 6, w, list.prefHeight(w));
        }
    }

    /** Saves the chosen campus as the user's default (same as the Home college box). Fire-and-forget. */
    static void remember(College c) {
        String uid = com.bofo.finder.service.AuthService.get().userId();
        if (uid == null) return;
        Async.bg(() -> { try {
            com.bofo.finder.service.SupabaseClient.get().update("users", "id=eq." + uid, new org.json.JSONObject().put("default_college_id", c.id()));
        } catch (Exception ignored) {} });
    }

    /** The text field, for callers that need to focus it. */
    public TextField field() { return field; }

    private boolean isChosenText() {
        College c = State.college.get();
        return c != null && field.getText().trim().equalsIgnoreCase(c.name());
    }

    /** What to search boarding houses for: the typed text, or nothing while the box just shows the chosen campus's name. */
    public String searchText() { return isChosenText() ? "" : field.getText().trim(); }

    /** Fills the box from code (e.g. a BOFO AI filter) without triggering typing events. */
    public void setText(String t) { setField(t == null ? "" : t); }

    private void setField(String t) { settingText = true; field.setText(t); settingText = false; }

    private void pick(College c) {
        close();
        setField(sync ? c.name() : "");
        if (sync) State.college.set(c);
        Consumer<College> cb = onPick; if (cb != null) cb.accept(c);
        getParent().requestFocus();
    }

    private void close() { list.setVisible(false); list.getSelectionModel().clearSelection(); }

    private void refresh(String text) {
        List<College> out = match(all, text);
        if (out.isEmpty() && text != null && !text.isBlank()) { close(); return; }        // typed text is a name/area search, not a campus: no empty dropdown
        list.getItems().setAll(out);
        list.getSelectionModel().clearSelection();
        list.setPrefHeight(Math.min(Math.max(out.size(), 1), MAX_ROWS) * ROW + 10);
        list.setVisible(true); requestLayout();
    }

    /** Own autocomplete (no web API; runs on the cached campus list): name starts with the text, then a word starts with it, then the name contains it, then initials match. Empty text = all. */
    static List<College> match(List<College> all, String text) {
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return new ArrayList<>(all);
        List<College> a = new ArrayList<>(), b = new ArrayList<>(), c = new ArrayList<>(), d = new ArrayList<>();
        for (College k : all) {
            String n = k.name().toLowerCase(Locale.ROOT);
            if (n.startsWith(q)) a.add(k);
            else if (n.contains(" " + q) || n.contains("(" + q)) b.add(k);
            else if (n.contains(q)) c.add(k);
            else if (initials(n).startsWith(q.replace(".", "").replace(" ", ""))) d.add(k);   // "norsu" -> Negros Oriental State University
        }
        a.addAll(b); a.addAll(c); a.addAll(d);
        return a;
    }

    /** First letters of the words in a name, skipping small words (of, the, and, de, ...). */
    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.replaceAll("[^\\p{L}\\p{N} ]", " ").split("\\s+"))
            if (!w.isEmpty() && !SMALL.contains(w)) sb.append(w.charAt(0));
        return sb.toString();
    }
    private static final java.util.Set<String> SMALL = java.util.Set.of("of", "the", "and", "de", "del", "for", "in", "at");

    /** Campus name with the part the user typed in bold purple. */
    private static TextFlow highlight(String name, String typed) {
        String q = typed == null ? "" : typed.trim();
        int i = q.isEmpty() ? -1 : name.toLowerCase(Locale.ROOT).indexOf(q.toLowerCase(Locale.ROOT));
        TextFlow tf = new TextFlow();
        if (i < 0) { tf.getChildren().add(plain(name)); return tf; }
        Text mid = new Text(name.substring(i, i + q.length()));
        mid.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-fill:#5B3BF0;");
        tf.getChildren().addAll(plain(name.substring(0, i)), mid, plain(name.substring(i + q.length())));
        return tf;
    }

    private static Text plain(String s) { Text t = new Text(s); t.setStyle("-fx-font-size:14px;-fx-fill:#17142A;"); return t; }
}
