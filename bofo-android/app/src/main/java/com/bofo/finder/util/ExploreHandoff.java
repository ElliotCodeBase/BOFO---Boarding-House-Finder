package com.bofo.finder.util;

/** Text typed in the Home search bar that is not a campus: Explore picks it up once when it opens (original: BofoPanel.filtersListener text). Main thread only. */
public final class ExploreHandoff {
    private ExploreHandoff() {}
    private static String pending;

    public static void set(String text) { pending = text == null || text.isBlank() ? null : text.trim(); }

    /** Returns the waiting text once, then forgets it. */
    public static String take() { String t = pending; pending = null; return t; }
}
