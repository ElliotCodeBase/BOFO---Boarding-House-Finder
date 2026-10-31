package com.bofo.finder.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Ways to get from A to B, with time and fare, for the Directions card. Pure logic (no network, no JavaFX) so it can
 * be unit tested: {@link TripPlanner} fills it with real routes, this class turns distances into minutes and pesos.
 *
 * <p><b>Fares</b> (official, LTFRB order effective 19 March 2026): traditional jeepney P14 for the first 4 km + P2.00 per
 * further km; modern jeepney P17 + P2.40 per km. Students, seniors and PWD pay 20% less with an ID.
 * Tricycle fares are set by each city, so they are shown as the commonly paid range in Dumaguete (P10-20 shared,
 * P50-100 when you hire it alone) and labelled as a guess elsewhere. Times are estimates from typical speeds.
 */
public final class Trip {
    private Trip() {}

    public enum Mode {
        WALK("\uD83D\uDEB6"), TRICYCLE("\uD83D\uDEF5"), JEEPNEY("\uD83D\uDE8C"), FAR("\uD83D\uDE90");
        public final String icon;
        Mode(String icon) { this.icon = icon; }
    }

    /** One way of travelling. {@code path} = {lat,lng} points from start to end. fare -1 = unknown. */
    public record Option(Mode mode, String title, String detail, double meters, int minutes, int fareLow, int fareHigh,
                         double[][] path, boolean fromData, boolean approximate) {
        public String fareText() { return peso(fareLow, fareHigh); }
        /** Text on the little badge in the middle of the drawn route. */
        public String badge() { return Geo.fmt(meters / 1000.0) + " \u00B7 " + Geo.fmtTime(minutes); }
        /** One short line for the small map card, e.g. "Jeepney 20 min · P14-17 · 3.0 km". */
        public String summary() {
            String word = switch (mode) { case WALK -> "Walk"; case TRICYCLE -> "Tricycle"; case JEEPNEY -> "Jeepney"; case FAR -> "Bus or van"; };
            return mode.icon + " " + word + " " + Geo.fmtTime(minutes) + (fareLow < 0 || mode == Mode.WALK ? "" : " \u00B7 " + fareText()) + " \u00B7 " + Geo.fmt(meters / 1000.0);
        }
    }

    /** All options, which one is recommended, and a message to show above them (empty = nothing to say). */
    public record Plan(List<Option> options, int best, double straightKm, String note) {}

    // ---------------------------------------------------------------- fares

    public static final int JEEP_TRAD_BASE = 14, JEEP_MODERN_BASE = 17;       // first 4 km
    public static final double JEEP_TRAD_KM = 2.0, JEEP_MODERN_KM = 2.4;       // each further km
    public static final int TRIKE_SHARED_LOW = 10, TRIKE_SHARED_HIGH = 20, TRIKE_SPECIAL_LOW = 50, TRIKE_SPECIAL_HIGH = 100;

    public static int jeepTraditional(double km) { return (int) Math.round(JEEP_TRAD_BASE + JEEP_TRAD_KM * Math.max(0, km - 4)); }
    public static int jeepModern(double km) { return (int) Math.round(JEEP_MODERN_BASE + JEEP_MODERN_KM * Math.max(0, km - 4)); }

    public static String peso(int low, int high) {
        if (low < 0) return "";
        return low == high ? "\u20B1" + low : "\u20B1" + low + "\u2013" + high;
    }

    // ---------------------------------------------------------------- time estimates (minutes, at least 1)

    private static int mins(double m) { return Math.max(1, (int) Math.round(m)); }
    public static int walkMinutes(double km) { return mins(Geo.walkMinutes(km)); }
    /** Tricycle: a short wait for it to fill or arrive, then about 20 km/h on town streets. */
    public static int tricycleMinutes(double km) { return mins(3 + km / 20.0 * 60); }
    /** Jeepney: walk to the stop, about 6 min wait, about 14 km/h with stops, walk from the stop (80 m per minute). */
    public static int jeepneyMinutes(double rideKm, double walkMeters) { return mins(6 + rideKm / 14.0 * 60 + walkMeters / 80.0); }
    /** Bus or van between towns, about 35 km/h with stops. */
    public static int farMinutes(double km) { return mins(km / 35.0 * 60); }

    // ---------------------------------------------------------------- option builders

    public static Option walk(double[][] path, double meters, boolean approx) {
        double km = meters / 1000.0;
        String detail = km < 0.05 ? "You are already here." : km <= 1.2 ? "Short enough to walk, and it costs nothing."
                : km <= 2.5 ? "A comfortable walk in good weather." : "A long walk. Fine for a day out, tiring every day.";
        return new Option(Mode.WALK, "Walk", detail, meters, km < 0.05 ? 1 : walkMinutes(km), 0, 0, path, false, approx);
    }

    public static Option tricycle(double[][] path, double meters, boolean approx) {
        double km = meters / 1000.0;
        return new Option(Mode.TRICYCLE, "Tricycle",
                "Shared " + peso(TRIKE_SHARED_LOW, TRIKE_SHARED_HIGH) + " per person, or " + peso(TRIKE_SPECIAL_LOW, TRIKE_SPECIAL_HIGH)
                        + " if you hire it alone. Cities set their own fares, so ask first.",
                meters, tricycleMinutes(km), TRIKE_SHARED_LOW, TRIKE_SHARED_HIGH, path, false, approx);
    }

    /** A jeepney line that really passes both ends (from OpenStreetMap). */
    public static Option jeepney(String line, double[][] path, double rideMeters, double walkMeters, boolean approx) {
        return jeepney(line, path, rideMeters, walkMeters, approx, -1, -1, "");
    }

    /** Same, with a fare someone checked on the ground (fareLow &gt;= 0 replaces the LTFRB formula) and a note to show. */
    public static Option jeepney(String line, double[][] path, double rideMeters, double walkMeters, boolean approx, int fareLow, int fareHigh, String note) {
        double rideKm = rideMeters / 1000.0, totalM = rideMeters + walkMeters;
        boolean checked = fareLow >= 0;
        String detail = "Ride about " + Geo.fmt(rideKm) + ", walk about " + Math.round(walkMeters / 10.0) * 10 + " m to and from the stops. "
                + (checked ? "Fare checked by the BOFO team." : "Modern jeepneys cost more.") + " Students pay 20% less with an ID."
                + (note == null || note.trim().isEmpty() ? "" : " " + note.trim());
        return new Option(Mode.JEEPNEY, line.trim().isEmpty() ? "Jeepney" : "Jeepney " + line, detail,
                totalM, jeepneyMinutes(rideKm, walkMeters),
                checked ? fareLow : jeepTraditional(rideKm), checked ? Math.max(fareLow, fareHigh) : jeepModern(rideKm), path, true, approx);
    }

    /** No mapped line found: price and time are estimated from the road distance. */
    public static Option jeepneyGuess(double[][] path, double meters, boolean approx) {
        double km = meters / 1000.0;
        return new Option(Mode.JEEPNEY, "Jeepney or multicab (estimate)",
                "We have no mapped line for this trip yet. Ask a driver or the terminal which one passes. Students pay 20% less with an ID.",
                meters, jeepneyMinutes(km, 160), jeepTraditional(km), jeepModern(km), path, false, approx);
    }

    public static Option far(double[][] path, double meters, boolean approx) {
        double km = meters / 1000.0;
        return new Option(Mode.FAR, "Bus or van",
                "Too far to walk or take a tricycle. Ask at the terminal for today's fare and the next trip.",
                meters, farMinutes(km), -1, -1, path, false, approx);
    }

    // ---------------------------------------------------------------- picking the recommended option

    /**
     * Index of the recommended option. A walk of 1 km or less always wins. Otherwise: lowest "cost" where 1 minute = 1 point,
     * P1 = 0.4 point (a student's time is worth more than a peso), and a walk longer than 2 km is penalised 12 points per extra km.
     */
    public static int best(List<Option> options) {
        int bestIdx = 0; double bestCost = Double.MAX_VALUE;
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            double km = o.meters() / 1000.0;
            if (o.mode() == Mode.WALK && km <= 1.0) return i;
            double fare = o.fareLow() < 0 ? 0 : (o.fareLow() + o.fareHigh()) / 2.0;
            double cost = o.minutes() + 0.4 * fare + (o.mode() == Mode.WALK && km > 2 ? (km - 2) * 12 : 0);
            if (cost < bestCost) { bestCost = cost; bestIdx = i; }
        }
        return bestIdx;
    }

    /** Joins {lat,lng} paths end to end (a duplicated joint is dropped). */
    public static double[][] concat(double[][]... parts) {
        List<double[]> out = new ArrayList<>();
        for (double[][] p : parts) {
            if (p == null) continue;
            for (double[] pt : p) {
                if (!out.isEmpty()) { double[] last = out.get(out.size() - 1); if (Math.abs(last[0] - pt[0]) < 1e-7 && Math.abs(last[1] - pt[1]) < 1e-7) continue; }
                out.add(pt);
            }
        }
        return out.toArray(new double[0][]);
    }
}
