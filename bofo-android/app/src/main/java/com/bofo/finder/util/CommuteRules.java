package com.bofo.finder.util;

/**
 * v2.0.83: the "Commute" action on an Explore map card. Pure logic (no Android): picks the option of a {@link Trip.Plan}
 * to draw and words it as one short line (mode + minutes). The route itself comes from {@link TripPlanner}
 * ({@code Router}, {@code TransitRoutes}, {@code LocalTransit}, {@code TransitMatch}), the same code the Directions card uses.
 */
public final class CommuteRules {
    private CommuteRules() {}

    /** The recommended option of the plan, or null when there is none. */
    public static Trip.Option best(Trip.Plan plan) {
        if (plan == null || plan.options() == null || plan.options().isEmpty()) return null;
        int i = plan.best();
        return plan.options().get(i >= 0 && i < plan.options().size() ? i : 0);
    }

    /** True for every mode except walking (the card says "ride" for these). */
    public static boolean isRide(Trip.Mode mode) { return mode != Trip.Mode.WALK; }

    /** One short line for the card, e.g. "\uD83D\uDEB6 Walk 12 min" or "\uD83D\uDE8C Jeepney 20 min" (the line number is left out: the card is narrow). */
    public static String line(Trip.Option o) {
        if (o == null) return "";
        String word = switch (o.mode()) { case WALK -> "Walk"; case TRICYCLE -> "Tricycle"; case JEEPNEY -> "Jeepney"; case FAR -> "Bus or van"; };
        return o.mode().icon + " " + word + " " + Geo.fmtTime(o.minutes());
    }

    /** A route can be drawn only with at least two points. */
    public static boolean drawable(Trip.Option o) { return o != null && o.path() != null && o.path().length >= 2; }
}
