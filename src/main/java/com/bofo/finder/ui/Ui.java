package com.bofo.finder.ui;

import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
            if (img == null || img.isError()) { img = new Image(url, w * 2, h * 2, false, true, true); IMG.put(key, img); }   // decoded at 2x, not full size
            iv.setImage(img);
            autoRetry(iv, key, () -> new Image(url, w * 2, h * 2, false, true, true));
        }
        iv.setFitWidth(w); iv.setFitHeight(h); iv.setPreserveRatio(false);
        Rectangle clip = new Rectangle(w, h); clip.setArcWidth(24); clip.setArcHeight(24); iv.setClip(clip);
        return iv;
    }
    /**
     * Photo that fills whatever size the caller gives it (bind fitWidth / set fitHeight) by centre-cropping,
     * so it never looks stretched on phones, tablets or landscape. Rounded corners follow its size.
     */
    public static ImageView coverPhoto(String url, double decodeWidth) {
        ImageView iv = new ImageView(); iv.setPreserveRatio(false); iv.setSmooth(true);
        iv.imageProperty().addListener((o, was, now) -> {                  // also fires when a failed picture is re-loaded
            if (now != null) now.progressProperty().addListener(x -> cropToFill(iv));
            cropToFill(iv);
        });
        if (url != null && !url.isBlank()) {
            String key = url + "@cover" + (int) decodeWidth;
            Image img = IMG.get(key);
            if (img == null || img.isError()) { img = new Image(url, decodeWidth, 0, true, true, true); IMG.put(key, img); }   // width-limited decode, keeps ratio
            iv.setImage(img);
            autoRetry(iv, key, () -> new Image(url, decodeWidth, 0, true, true, true));
        }
        iv.fitWidthProperty().addListener(o -> cropToFill(iv));
        iv.fitHeightProperty().addListener(o -> cropToFill(iv));
        Rectangle clip = new Rectangle(); clip.setArcWidth(32); clip.setArcHeight(32);
        clip.widthProperty().bind(iv.fitWidthProperty()); clip.heightProperty().bind(iv.fitHeightProperty());
        iv.setClip(clip);
        return iv;
    }
    /** If a picture failed to download, load it again the moment the connection comes back. */
    private static void autoRetry(ImageView iv, String key, Supplier<Image> make) {
        javafx.beans.value.ChangeListener<Boolean> back = (o, wasOffline, isOffline) -> {
            Image cur = iv.getImage();
            if (!isOffline && cur != null && cur.isError()) { Image fresh = make.get(); IMG.put(key, fresh); iv.setImage(fresh); }
        };
        iv.sceneProperty().addListener((o, was, scene) -> {
            if (scene != null) Connectivity.OFFLINE.addListener(back); else Connectivity.OFFLINE.removeListener(back);
        });
    }

    /**
     * Backdrop behind a photo: pulses grey while the picture downloads, then settles to the plain tint + icon
     * when there is no picture or it could not be loaded (the real photo covers it once it arrives).
     */
    public static void trackLoading(ImageView iv, Region backdrop, Node icon) {
        javafx.animation.FadeTransition pulse = new javafx.animation.FadeTransition(javafx.util.Duration.millis(900), backdrop);
        pulse.setFromValue(1); pulse.setToValue(0.45); pulse.setCycleCount(javafx.animation.Animation.INDEFINITE); pulse.setAutoReverse(true);
        Runnable update = () -> {
            Image i = iv.getImage();
            boolean loading = i != null && !i.isError() && i.getProgress() < 1;
            backdrop.getStyleClass().removeAll("skeleton", "photo-placeholder");
            backdrop.getStyleClass().add(loading ? "skeleton" : "photo-placeholder");
            icon.setVisible(!loading);
            if (loading && backdrop.getScene() != null) pulse.play(); else { pulse.stop(); backdrop.setOpacity(1); }
        };
        Consumer<Image> hook = i -> { if (i != null) { i.progressProperty().addListener(o -> update.run()); i.errorProperty().addListener(o -> update.run()); } };
        hook.accept(iv.getImage());
        iv.imageProperty().addListener((o, was, now) -> { hook.accept(now); update.run(); });
        backdrop.sceneProperty().addListener(o -> update.run());       // no animation while off screen
        update.run();
    }

    /** Fixed-size photo with the pulsing loading backdrop (detail page photo strip). */
    public static StackPane photoLoading(String url, double w, double h) {
        ImageView iv = photo(url, w, h);
        Region ph = new Region(); ph.setStyle("-fx-background-radius:12;");
        ph.setMinSize(w, h); ph.setPrefSize(w, h); ph.setMaxSize(w, h);
        Node icon = Icons.icon(Icons.HOME, 36, Color.web("#C9BEFA"));
        StackPane box = new StackPane(ph, icon, iv);
        box.setMinSize(w, h); box.setPrefSize(w, h); box.setMaxSize(w, h);
        trackLoading(iv, ph, icon);
        return box;
    }

    /** Toast for a failed action: says "No internet" when that is the reason, otherwise the given message. */
    public static void failToast(Throwable e, String fallback) {
        Nav.toast(Connectivity.isNetworkError(e) ? "No internet connection. Try again when you're back online." : fallback);
    }

    private static void cropToFill(ImageView iv) {
        Image im = iv.getImage(); double fw = iv.getFitWidth(), fh = iv.getFitHeight();
        if (im == null || im.getProgress() < 1 || im.getWidth() <= 0 || im.getHeight() <= 0 || fw <= 0 || fh <= 0) return;
        double target = fw / fh, source = im.getWidth() / im.getHeight();
        if (source > target) { double vw = im.getHeight() * target; iv.setViewport(new Rectangle2D((im.getWidth() - vw) / 2, 0, vw, im.getHeight())); }
        else { double vh = im.getWidth() / target; iv.setViewport(new Rectangle2D(0, (im.getHeight() - vh) / 2, im.getWidth(), vh)); }
    }
    /** Scrollable form column: full width on phones, centred and at most maxW wide on tablets. */
    public static ScrollPane scrollCentered(Region content, double maxW) {
        content.setMaxWidth(maxW);
        StackPane holder = new StackPane(content); holder.setAlignment(Pos.TOP_CENTER);
        return scroll(holder);
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
    private static final String WIFI_OFF = "M12 20h.01M8.5 16.429a5 5 0 0 1 7 0M5 12.859a10 10 0 0 1 5.17-2.69M19 12.859a10 10 0 0 0-2.007-1.523M2 8.82a15 15 0 0 1 4.177-2.643M22 8.82a15 15 0 0 0-11.288-3.764M2 2l20 20";
    private static final String ALERT = "M12 2.5a9.5 9.5 0 1 0 0 19a9.5 9.5 0 1 0 0-19ZM12 8v5M12 16.5h.01";

    /** Thin outline icon drawn on a 24x24 grid and scaled to size. */
    public static Node strokeIcon(String path, double size, Color color) {
        SVGPath p = new SVGPath(); p.setContent(path); p.setFill(null); p.setStroke(color); p.setStrokeWidth(1.8);
        p.setStrokeLineCap(StrokeLineCap.ROUND); p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        double k = size / 24; p.getTransforms().add(new Scale(k, k, 0, 0));
        Pane box = new Pane(p); box.setMinSize(size, size); box.setPrefSize(size, size); box.setMaxSize(size, size);
        box.setMouseTransparent(true); return box;
    }

    /**
     * "No internet connection" (or "Something went wrong") with a Try again button.
     * For network errors it also retries by itself the moment the connection comes back, while it is on screen.
     */
    public static VBox errorState(Throwable err, Runnable retry) {
        boolean net = Connectivity.isNetworkError(err) || Connectivity.OFFLINE.get();
        StackPane badge = new StackPane(new Circle(42, Color.web("#ECE9FF")), strokeIcon(net ? WIFI_OFF : ALERT, 40, Color.web("#5B3BF0")));
        Label t = label(net ? "No internet connection" : "Something went wrong", "h2"); t.setAlignment(Pos.CENTER); t.setStyle("-fx-text-alignment:center;");
        Label b = label(net ? "Check your Wi-Fi or mobile data. We'll reload as soon as you're back online."
                            : "We couldn't load this right now. Please try again.", "secondary");
        b.setAlignment(Pos.CENTER); b.setStyle("-fx-text-alignment:center;");
        Label why = label(describe(err), "secondary"); why.setWrapText(true); why.setMaxWidth(300); why.setOpacity(0.6);
        why.setStyle("-fx-font-size:11px;-fx-text-alignment:center;"); why.setAlignment(Pos.CENTER); why.setVisible(!why.getText().isEmpty()); why.setManaged(!why.getText().isEmpty());
        Button again = new Button("Try again"); again.getStyleClass().add("btn-primary");
        again.setOnAction(e -> { again.setDisable(true); again.setText("Trying..."); retry.run(); });
        VBox v = new VBox(12, badge, t, b, why, again);
        v.setAlignment(Pos.CENTER); v.setPadding(new Insets(36, 24, 36, 24)); v.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(again, new Insets(6, 0, 0, 0));
        if (net) {
            javafx.beans.value.ChangeListener<Boolean> back = (o, wasOffline, isOffline) -> { if (!isOffline && v.getScene() != null) retry.run(); };
            v.sceneProperty().addListener((o, was, scene) -> {
                if (scene != null) Connectivity.OFFLINE.addListener(back); else Connectivity.OFFLINE.removeListener(back);
            });
        }
        return v;
    }

    /** Short real reason ("UnknownHostException: abc.supabase.co"), shown small under the message so a failure can be told apart. */
    private static String describe(Throwable e) {
        if (e == null) return "";
        Throwable r = e; for (int i = 0; i < 8 && r.getCause() != null && r.getCause() != r; i++) r = r.getCause();
        String m = r.getMessage() == null ? "" : r.getMessage().replaceAll("\\s+", " ");
        if ("offline".equals(m)) return "";
        String s = r.getClass().getSimpleName() + (m.isEmpty() ? "" : ": " + m);
        return s.length() > 110 ? s.substring(0, 110) + "..." : s;
    }

    /**
     * Standard loading flow for a list/page: show the skeleton, load off the UI thread, then clear and render
     * (or show the error / no-internet state with a retry). Stale answers from older calls are ignored.
     */
    public static <T> void load(Pane target, Supplier<List<Node>> skeleton, Async.Work<T> work, Consumer<T> render) {
        final Object mine = new Object();
        target.getProperties().put("bofoLoad", mine);
        target.getChildren().setAll(skeleton.get());
        Async.run(work, r -> {
            if (target.getProperties().get("bofoLoad") != mine) return;
            target.getChildren().clear(); render.accept(r);
            if (Connectivity.OFFLINE.get()) {                         // this may be saved data: refresh it once the connection is back
                javafx.beans.value.ChangeListener<Boolean> back = new javafx.beans.value.ChangeListener<>() {
                    @Override public void changed(javafx.beans.value.ObservableValue<? extends Boolean> o, Boolean was, Boolean now) {
                        if (now) return;
                        Connectivity.OFFLINE.removeListener(this);
                        if (target.getScene() != null && target.getProperties().get("bofoLoad") == mine) load(target, skeleton, work, render);
                    }
                };
                Connectivity.OFFLINE.addListener(back);
            }
        }, e -> {
            if (target.getProperties().get("bofoLoad") != mine) return;
            target.getChildren().setAll(errorState(e, () -> load(target, skeleton, work, render)));
        });
    }

    public static Label section(String t) { return label(t, "section"); }

    public static String hex(Color c) {
        return String.format("#%02X%02X%02X", Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }

    /** Top bar of a sub-screen: back arrow + title. */
    public static HBox backBar(String title, Runnable back) {
        Button b = new Button(); b.getStyleClass().add("icon-btn");
        b.setGraphic(strokeIcon(Glyphs.BACK, 20, Color.web("#17142A"))); b.setOnAction(e -> back.run());
        HBox bar = new HBox(10, b, label(title, "h2"), grow()); bar.getStyleClass().add("app-bar"); bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /** Pill-shaped segmented control, e.g. List | Map. */
    public static HBox segmented(String[] names, int selected, java.util.function.IntConsumer onPick) {
        HBox wrap = new HBox(2); wrap.getStyleClass().add("seg-wrap"); wrap.setAlignment(Pos.CENTER); wrap.setMinWidth(Region.USE_PREF_SIZE);
        Button[] bs = new Button[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            bs[i] = new Button(names[i]); bs[i].getStyleClass().add(i == selected ? "seg-on" : "seg");
            bs[i].setMinWidth(Region.USE_PREF_SIZE);                    // never shrink to "..." when the row is crowded
            bs[i].setOnAction(e -> {
                for (int j = 0; j < bs.length; j++) { bs[j].getStyleClass().removeAll("seg", "seg-on"); bs[j].getStyleClass().add(j == idx ? "seg-on" : "seg"); }
                onPick.accept(idx);
            });
            wrap.getChildren().add(bs[i]);
        }
        return wrap;
    }

    /** iOS-style on/off switch with a sliding knob. */
    public static StackPane switchControl(boolean on, Consumer<Boolean> change) {
        Region track = new Region(); track.setMinSize(46, 28); track.setPrefSize(46, 28); track.setMaxSize(46, 28);
        Circle knob = new Circle(11, Color.WHITE); knob.setEffect(new javafx.scene.effect.DropShadow(3, 0, 1, Color.rgb(23, 20, 42, 0.3)));
        StackPane sw = new StackPane(track, knob); sw.setAlignment(Pos.CENTER_LEFT);
        sw.setMinSize(46, 28); sw.setPrefSize(46, 28); sw.setMaxSize(46, 28); sw.setCursor(javafx.scene.Cursor.HAND);
        javafx.beans.property.DoubleProperty p = new javafx.beans.property.SimpleDoubleProperty(on ? 1 : 0);
        Runnable paint = () -> {
            knob.setTranslateX(3 + 18 * p.get());
            track.setStyle("-fx-background-radius:14;-fx-background-color:" + hex(Color.web("#D3D0E6").interpolate(Color.web("#5B3BF0"), p.get())) + ";");
        };
        p.addListener((o, a, b) -> paint.run()); paint.run();
        boolean[] state = {on};
        sw.setOnMouseClicked(e -> {
            state[0] = !state[0];
            new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(180),
                    new javafx.animation.KeyValue(p, state[0] ? 1 : 0, javafx.animation.Interpolator.EASE_BOTH))).play();
            change.accept(state[0]);
        });
        return sw;
    }

    /** GCash-style settings row: outline icon, title, optional value, chevron. */
    public static HBox settingsRow(String glyph, String title, String value, Runnable r) {
        HBox row = new HBox(14, strokeIcon(glyph, 22, Color.web("#5B3BF0")), label(title, "row-label"), grow());
        if (value != null && !value.isBlank()) row.getChildren().add(label(value, "secondary"));
        if (r != null) row.getChildren().add(strokeIcon(Glyphs.CHEVRON, 18, Color.web("#A39DB8")));
        row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("settings-row");
        if (r != null) { row.setCursor(javafx.scene.Cursor.HAND); row.setOnMouseClicked(e -> r.run()); }
        return row;
    }

    /** Settings row with an on/off switch instead of a chevron. */
    public static HBox settingsToggle(String glyph, String title, boolean on, Consumer<Boolean> change) {
        HBox row = new HBox(14, strokeIcon(glyph, 22, Color.web("#5B3BF0")), label(title, "row-label"), grow(), switchControl(on, change));
        row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("settings-row");
        return row;
    }

    /** Section: small grey heading above one white rounded card; rows are separated by thin lines. */
    public static VBox settingsGroup(String heading, Node... rows) {
        VBox card = new VBox(); card.getStyleClass().add("settings-card");
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) { Region line = new Region(); line.getStyleClass().add("hline"); VBox.setMargin(line, new Insets(0, 0, 0, 52)); card.getChildren().add(line); }
            card.getChildren().add(rows[i]);
        }
        Label h = label(heading, "settings-heading");
        return new VBox(8, h, card);
    }
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
        StackPane circle = new StackPane(Icons.icon(iconPath, 18, Color.web("#5B3BF0")));
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
