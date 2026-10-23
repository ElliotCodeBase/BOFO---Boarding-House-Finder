package com.bofo.finder.ui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/** Simple 24x24 vector icons drawn as JavaFX SVGPath (no image files, no emojis). */
public final class Icons {
    private Icons() {}
    public static final String PLUS = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
    public static final String HOME = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";
    public static final String EXPLORE = "M12 10.9c-.61 0-1.1.49-1.1 1.1s.49 1.1 1.1 1.1c.61 0 1.1-.49 1.1-1.1s-.49-1.1-1.1-1.1zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm2.19 12.19L6 18l3.81-8.19L18 6l-3.81 8.19z";
    public static final String HEART = "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z";
    public static final String CHAT = "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z";
    public static final String PERSON = "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
    public static final String DASH = "M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z";
    public static final String LIST = "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z";
    public static final String SHIELD = "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-2 16l-4-4 1.41-1.41L10 14.17l6.59-6.59L18 9l-8 8z";
    public static final String WALLET = "M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z";
    public static final String FLAG = "M14.4 6L14 4H5v17h2v-7h5.6l.4 2h7V6z";
    public static final String SEARCH = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";
    public static final String BELL = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z";
    public static final String CHEVRON = "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z";
    public static final String PIN = "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z";
    public static final String SWAP = "M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z";
    public static final String INFO = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z";
    public static final String DOC = "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z";
    public static final String LOGOUT = "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z";

    // ---- Dock icons: {silhouette, inner detail}, drawn on a 24x24 grid with a 1.8 stroke ----
    // Idle = outline only. Active = silhouette filled, detail drawn in the dock colour (cut-out look).
    private static final String NONE = "M0 0";
    private static final String[] O_HOME = {"M3 10.5L12 3l9 7.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z", "M9.5 21v-6a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v6"};
    private static final String[] O_EXPLORE = {"M12 2.5a9.5 9.5 0 1 0 0 19a9.5 9.5 0 1 0 0-19Z", "M15.8 8.2l-2.2 5.4-5.4 2.2 2.2-5.4Z"};
    private static final String[] O_HEART = {"M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z", NONE};
    private static final String[] O_CHAT = {"M7.9 20A9 9 0 1 0 4 16.1L2 22Z", "M8 10h8M8 14h5"};
    private static final String[] O_PERSON = {"M12 3a4.5 4.5 0 1 0 0 9a4.5 4.5 0 0 0 0-9ZM4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1Z", NONE};
    private static final String[] O_DASH = {"M4 4h6v7H4ZM14 4h6v4h-6ZM14 12h6v8h-6ZM4 15h6v5H4Z", NONE};
    private static final String[] O_LIST = {"M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z", "M8 8h8M8 12h8M8 16h5"};
    private static final String[] O_SHIELD = {"M12 2.5L20 5.5V11c0 5-3.4 8.7-8 10.5C7.4 19.7 4 16 4 11V5.5Z", "M8.5 12l2.5 2.5 4.5-5"};
    private static final String[] O_CARD = {"M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z", "M3 10h18M7 15h3"};
    private static final String[] O_SEARCH = {"M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0-16Z", "M21 21l-4.3-4.3"};
    private static final String[] O_PLUS_CIRCLE = {"M12 2.5a9.5 9.5 0 1 0 0 19a9.5 9.5 0 1 0 0-19Z", "M12 8v8M8 12h8"};
    private static final String[] O_USER_CIRCLE = {"M12 2.5a9.5 9.5 0 1 0 0 19a9.5 9.5 0 1 0 0-19Z", "M12 7a3.2 3.2 0 1 0 0 6.4a3.2 3.2 0 1 0 0-6.4ZM6.2 19a6.5 6.5 0 0 1 11.6 0"};
    private static final String[] O_FLAG = {"M5 4h13.5L16 9l2.5 5H5Z", "M5 3v18"};

    /** Outline icon pair {silhouette, detail} for a dock tab label. */
    public static String[] outlineFor(String label) {
        String s = label.toLowerCase();
        if (s.startsWith("home")) return O_HOME;
        if (s.startsWith("explore") || s.startsWith("search")) return O_SEARCH;
        if (s.startsWith("create") || s.startsWith("add")) return O_PLUS_CIRCLE;
        if (s.startsWith("saved")) return O_HEART;
        if (s.startsWith("chats")) return O_CHAT;
        if (s.startsWith("profile")) return O_USER_CIRCLE;
        if (s.startsWith("dashboard")) return O_DASH;
        if (s.startsWith("listings")) return O_LIST;
        if (s.startsWith("verify") || s.startsWith("queue")) return O_SHIELD;
        if (s.startsWith("payments")) return O_CARD;
        if (s.startsWith("reports")) return O_FLAG;
        return O_LIST;
    }

    public static StackPane icon(String path, double size, Color color) {
        SVGPath p = new SVGPath(); p.setContent(path); p.setFill(color);
        double k = size / 24.0; p.setScaleX(k); p.setScaleY(k);
        StackPane box = new StackPane(p);
        box.setMinSize(size, size); box.setPrefSize(size, size); box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }

    public static void setColor(Node n, Color c) {
        if (n instanceof StackPane) {
            StackPane sp = (StackPane) n;
            if (!sp.getChildren().isEmpty() && sp.getChildren().get(0) instanceof SVGPath) ((SVGPath) sp.getChildren().get(0)).setFill(c);
        }
    }

    public static String forLabel(String label) {
        String s = label.toLowerCase();
        if (s.startsWith("home")) return HOME;
        if (s.startsWith("explore")) return EXPLORE;
        if (s.startsWith("saved")) return HEART;
        if (s.startsWith("chats")) return CHAT;
        if (s.startsWith("profile")) return PERSON;
        if (s.startsWith("dashboard")) return DASH;
        if (s.startsWith("listings")) return LIST;
        if (s.startsWith("verify") || s.startsWith("queue")) return SHIELD;
        if (s.startsWith("payments")) return WALLET;
        if (s.startsWith("reports")) return FLAG;
        return LIST;
    }
}
