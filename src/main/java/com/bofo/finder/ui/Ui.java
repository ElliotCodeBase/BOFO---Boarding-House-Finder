package com.bofo.finder.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/** Tiny UI factory so views stay short. All colors come from bofo.css. */
public final class Ui {
    private Ui() {}

    public static Label label(String t, String... styles) {
        Label l = new Label(t); l.setWrapText(true);
        for (String s : styles) l.getStyleClass().add(s);
        return l;
    }
    public static Button btn(String text, String style, Runnable r) {
        Button b = new Button(text); b.getStyleClass().add(style);
        b.setOnAction(e -> r.run()); return b;
    }
    public static VBox vbox(double gap, Node... n) { VBox v = new VBox(gap, n); return v; }
    public static HBox hbox(double gap, Node... n) { HBox h = new HBox(gap, n); h.setAlignment(Pos.CENTER_LEFT); return h; }
    public static Region grow() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); VBox.setVgrow(r, Priority.ALWAYS); return r; }
    public static ScrollPane scroll(Node n) {
        ScrollPane s = new ScrollPane(n); s.setFitToWidth(true); s.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); return s;
    }
    /** LRU image cache (FX thread only): avoids re-downloading/decoding the same photo while scrolling. */
    private static final java.util.Map<String, Image> IMG = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Image> e) { return size() > 80; }
    };
    public static ImageView photo(String url, double w, double h) {
        ImageView iv = new ImageView();
        if (url != null && !url.isBlank()) {
            String key = url + "@" + (int) w + "x" + (int) h;
            Image img = IMG.get(key);
            if (img == null) { img = new Image(url, w * 2, h * 2, false, true, true); IMG.put(key, img); }   // decoded at 2x, not full size
            iv.setImage(img);
        }
        iv.setFitWidth(w); iv.setFitHeight(h); iv.setPreserveRatio(false);
        Rectangle clip = new Rectangle(w, h); clip.setArcWidth(24); clip.setArcHeight(24); iv.setClip(clip);
        return iv;
    }
    public static String stars(double rating) {
        int full = (int) Math.round(rating);
        return "★".repeat(Math.max(0, Math.min(5, full))) + "☆".repeat(5 - Math.max(0, Math.min(5, full)));
    }
    public static HBox appBar(String title, Node... right) {
        HBox bar = new HBox(8, label(title, "h2"), grow()); bar.getChildren().addAll(right);
        bar.getStyleClass().add("app-bar"); bar.setAlignment(Pos.CENTER_LEFT); return bar;
    }
    public static HBox chipRow(String[] names, int selected, java.util.function.IntConsumer onPick) {
        HBox row = new HBox(8); row.setPadding(new Insets(4, 0, 4, 0));
        Button[] bs = new Button[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            bs[i] = new Button(names[i]);
            bs[i].getStyleClass().add(i == selected ? "chip-selected" : "chip");
            bs[i].setOnAction(e -> {
                for (int j = 0; j < bs.length; j++) { bs[j].getStyleClass().removeAll("chip", "chip-selected"); bs[j].getStyleClass().add(j == idx ? "chip-selected" : "chip"); }
                onPick.accept(idx);
            });
            row.getChildren().add(bs[i]);
        }
        return row;
    }
    public static VBox padded(double gap, Node... n) { VBox v = new VBox(gap, n); v.setPadding(new Insets(16)); return v; }

    /** Friendly empty state: mascot + title + one short sentence. */
    public static VBox emptyState(String title, String body) {
        Label t = label(title, "h2"); t.setAlignment(Pos.CENTER); t.setStyle("-fx-text-alignment:center;");
        Label b = label(body, "secondary"); b.setAlignment(Pos.CENTER); b.setStyle("-fx-text-alignment:center;");
        VBox v = new VBox(8, new Mascot(Mascot.Expression.THINKING, 84), t, b);
        v.setAlignment(Pos.CENTER); v.setPadding(new Insets(28, 20, 28, 20)); v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }
    public static Label section(String t) { return label(t, "section"); }
    /** Applies the BOFO stylesheet to a dialog (dialogs are separate windows and do not inherit it). */
    public static void style(Dialog<?> d) {
        var css = Ui.class.getResource("/com/bofo/finder/bofo.css");
        if (css != null) d.getDialogPane().getStylesheets().add(css.toExternalForm());
    }
    public static VBox card(double gap, Node... n) {
        VBox v = new VBox(gap, n); v.getStyleClass().add("card"); v.setPadding(new Insets(14)); return v;
    }
    public static VBox statCard(String value, String caption) {
        VBox v = new VBox(2, label(value, "stat-value"), label(caption, "secondary"));
        v.getStyleClass().add("card"); v.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(v, Priority.ALWAYS); return v;
    }
    /** Round initials badge (used for people and chats). */
    public static Label avatar(String name, double size) {
        String ini = "B";
        if (name != null && !name.isBlank()) {
            String[] p = name.trim().split("\\s+");
            ini = ("" + p[0].charAt(0) + (p.length > 1 ? p[1].charAt(0) : "")).toUpperCase();
        }
        Label l = new Label(ini); l.getStyleClass().add("avatar");
        l.setMinSize(size, size); l.setPrefSize(size, size); l.setMaxSize(size, size); l.setAlignment(Pos.CENTER);
        l.setStyle("-fx-background-radius:" + (size / 2) + ";-fx-font-size:" + Math.round(size * 0.36) + "px;");
        return l;
    }
    /** On/off filter chip. */
    public static final class Chip extends Button {
        private final javafx.beans.property.BooleanProperty on = new javafx.beans.property.SimpleBooleanProperty(false);
        public Chip(String text) {
            super(text); getStyleClass().add("chip");
            setOnAction(e -> on.set(!on.get()));
            on.addListener((o, a, v) -> { getStyleClass().removeAll("chip", "chip-selected"); getStyleClass().add(v ? "chip-selected" : "chip"); });
        }
        public boolean isOn() { return on.get(); }
        public void set(boolean v) { on.set(v); }
    }

    /** Settings-style row: round icon, label, chevron. */
    public static HBox menuRow(String iconPath, String text, Runnable r) {
        StackPane circle = new StackPane(Icons.icon(iconPath, 18, Color.web("#5B34B8")));
        circle.getStyleClass().add("icon-circle");
        circle.setMinSize(36, 36); circle.setPrefSize(36, 36); circle.setMaxSize(36, 36);
        HBox row = new HBox(12, circle, label(text, "row-label"), grow(), Icons.icon(Icons.CHEVRON, 18, Color.web("#A39DB8")));
        row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("menu-row");
        row.setOnMouseClicked(e -> r.run());
        return row;
    }
    /** "---- Or ----" style divider. */
    public static HBox divider(String text) {
        Region l = new Region(), r = new Region();
        l.getStyleClass().add("hline"); r.getStyleClass().add("hline");
        HBox.setHgrow(l, Priority.ALWAYS); HBox.setHgrow(r, Priority.ALWAYS);
        HBox h = new HBox(10, l, label(text, "secondary"), r); h.setAlignment(Pos.CENTER); return h;
    }
}
