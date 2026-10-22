package com.bofo.finder.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
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
        Rectangle clip = new Rectangle(w, h); clip.setArcWidth(16); clip.setArcHeight(16); iv.setClip(clip);
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
}
