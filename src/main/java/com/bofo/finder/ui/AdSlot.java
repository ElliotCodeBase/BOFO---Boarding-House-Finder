package com.bofo.finder.ui;

import javafx.geometry.Insets;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * An advert cell for the listing grid. It takes the place of ONE card (or a whole row when fullWidth is true).
 * Ads are OFF until ENABLED is set to true. Home and Explore already ask {@link #spots(int)} where to put them,
 * so switching on only needs ENABLED = true and a real ad source in {@link #placeholder()}.
 */
public final class AdSlot extends VBox {
    /** Master switch. false = no ad is ever shown. */
    public static final boolean ENABLED = false;

    private final boolean full;

    public AdSlot(boolean fullWidth, String title, String body, String cta, Runnable onClick) {
        super(8); full = fullWidth;
        getStyleClass().addAll("card", "ad-card"); setPadding(new Insets(10));
        Label badge = Ui.label("Ad", "tag"); badge.setMaxWidth(Region.USE_PREF_SIZE);
        Region art = new Region(); art.getStyleClass().add("photo-placeholder");
        art.setMinHeight(fullWidth ? 140 : 110); art.setPrefHeight(fullWidth ? 140 : 110); art.setMaxHeight(fullWidth ? 140 : 110);
        getChildren().addAll(badge, art, Ui.label(title, "h2"), Ui.label(body, "secondary"));
        if (cta != null && !cta.isBlank()) getChildren().add(Ui.label(cta, "ad-cta"));
        if (onClick != null) { setOnMouseClicked(e -> onClick.run()); setCursor(javafx.scene.Cursor.HAND); }
    }

    public boolean fullWidth() { return full; }

    /** The ad that is shown at a spot. Replace with the real ad source later. */
    public static AdSlot placeholder() {
        return new AdSlot(false, "Your ad here", "Sponsored space", "Learn more", null);
    }

    /** Random list positions (never the first two) where an ad goes in front of the card at that index; about one ad per 8 cards. */
    public static Set<Integer> spots(int items) {
        Set<Integer> s = new HashSet<>();
        if (!ENABLED || items < 4) return s;
        Random r = new Random();
        int want = Math.max(1, items / 8);
        for (int tries = 0; s.size() < want && tries < 50; tries++) s.add(2 + r.nextInt(items - 2));
        return s;
    }
}
