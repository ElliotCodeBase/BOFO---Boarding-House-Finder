package com.bofo.finder.util;

import com.bofo.finder.model.College;

import java.util.List;
import java.util.Locale;

/**
 * The rules behind the Directions card, with no Android in them so they run in unit tests:
 * which trip each tab means, what is missing, when to plan again, the big summary texts and the OpenStreetMap link.
 * Tabs: 0 = Me -> BH, 1 = Me -> Campus, 2 = BH -> Campus (ids as in the JavaFX card; they are shown in the order {@link #SHOWN_ORDER}).
 */
public final class DirectionsRules {
    private DirectionsRules() {}

    public static final int TAB_ME_BH = 0, TAB_ME_CAMPUS = 1, TAB_BH_CAMPUS = 2;
    /** Left to right on screen: Me -> BH, BH -> Campus (centre), Me -> Campus. */
    public static final int[] SHOWN_ORDER = {0, 2, 1};
    /** Re-plan a trip that starts at you when you moved this far (km). */
    public static final double MOVE_REPLAN_KM = 0.08;
    /** The campus nearest to a boarding house counts only within this distance (km). */
    public static final double NEAREST_CAMPUS_MAX_KM = 15;
    /** Longest wait (seconds) for the real plan; then an offline estimate is shown. */
    public static final int PLAN_SECONDS = 12;

    public enum Missing { NONE, NEED_CAMPUS, NEED_CAMPUS_AND_LOCATION, NEED_LOCATION }

    public static boolean startsAtMe(int tab) { return tab != TAB_BH_CAMPUS; }

    public static boolean needsCampus(int tab) { return tab == TAB_ME_CAMPUS || tab == TAB_BH_CAMPUS; }

    /** Start point {lat,lng} of a tab (null when it starts at you and your position is not known). */
    public static double[] from(int tab, double[] bh, double[] me) { return tab == TAB_BH_CAMPUS ? bh : me; }

    /** End point {lat,lng} of a tab (null when it ends at the campus and none is known). */
    public static double[] to(int tab, double[] bh, double[] campus) { return tab == TAB_ME_BH ? bh : campus; }

    /** First tab shown: Me -> BH when your position is known or there is no campus, else BH -> Campus (needs no location). */
    public static int defaultTab(boolean haveMe, boolean haveCampus) { return haveMe || !haveCampus ? TAB_ME_BH : TAB_BH_CAMPUS; }

    public static Missing missing(int tab, boolean haveMe, boolean haveCampus) {
        if (needsCampus(tab) && !haveCampus) return tab == TAB_ME_CAMPUS && !haveMe ? Missing.NEED_CAMPUS_AND_LOCATION : Missing.NEED_CAMPUS;
        if (startsAtMe(tab) && !haveMe) return Missing.NEED_LOCATION;
        return Missing.NONE;
    }

    /** The campus nearest to the boarding house, or null when there is none within {@link #NEAREST_CAMPUS_MAX_KM}. */
    public static College nearestCampus(List<College> colleges, double bhLat, double bhLng) {
        College best = null; double bd = Double.MAX_VALUE;
        if (colleges != null) for (College c : colleges) {
            double d = Geo.km(c.lat(), c.lng(), bhLat, bhLng);
            if (d < bd) { bd = d; best = c; }
        }
        return best != null && bd <= NEAREST_CAMPUS_MAX_KM ? best : null;
    }

    /** True when you are far enough from where the current plan started that it should be worked out again. */
    public static boolean movedEnough(double[] planFrom, double lat, double lng) {
        return planFrom != null && Geo.km(planFrom[0], planFrom[1], lat, lng) > MOVE_REPLAN_KM;
    }

    /** Big line of the chosen way, e.g. "17 min walk", "21 min by jeepney". */
    public static String heroHead(Trip.Option o) {
        String how;
        switch (o.mode()) {
            case WALK: how = "walk"; break;
            case TRICYCLE: how = "by tricycle"; break;
            case JEEPNEY: how = "by jeepney"; break;
            default: how = "by bus or van"; break;
        }
        return Geo.fmtTime(o.minutes()) + " " + how;
    }

    /** Small line under it, e.g. "3.0 km  ·  ₱14–17  ·  estimate". */
    public static String heroMeta(Trip.Option o) {
        String fare = o.mode() == Trip.Mode.WALK || o.fareText().isEmpty() ? "" : "  \u00B7  " + o.fareText();
        return Geo.fmt(o.meters() / 1000.0) + fare + (o.approximate() ? "  \u00B7  estimate" : "");
    }

    /** openstreetmap.org directions page for the same trip (foot engine for a walk, car engine otherwise). */
    public static String osmUrl(Trip.Mode mode, double[] a, double[] b) {
        String engine = mode == Trip.Mode.WALK ? "fossgis_osrm_foot" : "fossgis_osrm_car";
        return String.format(Locale.ROOT, "https://www.openstreetmap.org/directions?engine=%s&route=%.6f%%2C%.6f%%3B%.6f%%2C%.6f",
                engine, a[0], a[1], b[0], b[1]);
    }
}
