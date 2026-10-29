package com.bofo.finder.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Builds the list of ways to get from A to B: real road routes from {@link Router}, jeepney lines from public commuter
 * data ({@link TransitRoutes}), and fares / times from {@link Trip}. Blocking: call from {@link Async}. Never throws.
 * <ul>
 *   <li>Up to 3.5 km on foot: a walking option.</li>
 *   <li>Over 600 m: a tricycle option (when the road trip is 6 km or less) and the best real jeepney lines (up to 2),
 *       each as walk to the stop + ride on the line + walk from the stop. No mapped line: a clearly labelled estimate.</li>
 *   <li>Over 25 km in a straight line: only "bus or van". We never say "21 hours walk" any more.</li>
 * </ul>
 */
public final class TripPlanner {
    private TripPlanner() {}

    /** Farther than this (straight line) the trip is a bus/van ride, not a commute. */
    public static final double FAR_KM = 25;
    private static final double MAX_STOP_WALK_M = 450;

    public static Trip.Plan plan(double[] from, double[] to) {
        double straight = Geo.km(from[0], from[1], to[0], to[1]);
        try {
            if (straight > FAR_KM) return far(from, to, straight);
            Router.Route walk = Router.walk(from[0], from[1], to[0], to[1]);
            double wKm = walk.meters() / 1000.0;
            boolean serverDown = walk.approximate() && !Router.lastError().isEmpty();     // route server unreachable: do not make every leg wait for it again
            List<Trip.Option> opts = new ArrayList<>();
            StringBuilder note = new StringBuilder();
            if (wKm <= 3.5) opts.add(Trip.walk(walk.pts(), walk.meters(), walk.approximate()));
            if (wKm >= 0.6) {
                Router.Route drive = serverDown ? Router.estimate(from[0], from[1], to[0], to[1]) : Router.drive(from[0], from[1], to[0], to[1]);
                double dM = Math.max(drive.meters(), straight * 1000);
                if (dM <= 6000) opts.add(Trip.tricycle(drive.pts(), dM, drive.approximate()));
                List<TransitMatch.Line> lines = new ArrayList<>(LocalTransit.lines());              // hand-made lines first (checked by a person) ...
                lines.addAll(TransitRoutes.around(from, to));                                        // ... plus what OpenStreetMap volunteers mapped
                List<TransitMatch.Match> found = TransitMatch.match(lines, from, to, MAX_STOP_WALK_M, 2);
                for (TransitMatch.Match m : found) {
                    Router.Route in = serverDown ? Router.estimate(from[0], from[1], m.boardPoint()[0], m.boardPoint()[1]) : Router.walk(from[0], from[1], m.boardPoint()[0], m.boardPoint()[1]);
                    Router.Route out = serverDown ? Router.estimate(m.alightPoint()[0], m.alightPoint()[1], to[0], to[1]) : Router.walk(m.alightPoint()[0], m.alightPoint()[1], to[0], to[1]);
                    double[][] path = Trip.concat(in.pts(), m.ridePath(), out.pts());
                    opts.add(Trip.jeepney(m.line().label(), path, m.rideM(), in.meters() + out.meters(), in.approximate() || out.approximate(),
                            m.line().fareLow(), m.line().fareHigh(), m.line().note()));
                }
                if (found.isEmpty() && dM >= 1500) opts.add(Trip.jeepneyGuess(drive.pts(), dM, drive.approximate()));
                if (!found.isEmpty()) {
                    boolean anyOsm = found.stream().anyMatch(m -> !m.line().local());
                    String src = TransitRoutes.lastSource();
                    if (anyOsm) addNote(note, src.equals("old") ? "You are offline: jeepney lines are from a copy saved on this device and may be out of date."
                            : "Jeepney lines come from OpenStreetMap volunteers and may be out of date.");
                } else if (TransitRoutes.lastSource().equals("none") && !TransitRoutes.lastError().isEmpty())
                    addNote(note, "Could not load jeepney lines (offline?). Times and fares below are estimates.");
            }
            if (opts.isEmpty()) return far(from, to, straight);
            if (serverDown) addNote(note, "Could not reach the route server, so distances are rough estimates.");
            return new Trip.Plan(opts, Trip.best(opts), straight, note.toString());
        } catch (Throwable t) {
            System.err.println("[TripPlanner] " + t);
            Router.Route est = Router.estimate(from[0], from[1], to[0], to[1]);
            List<Trip.Option> one = List.of(Trip.walk(est.pts(), est.meters(), true));
            return new Trip.Plan(one, 0, straight, "Could not work out the route. Showing a straight-line estimate.");
        }
    }


    /**
     * Like {@link #plan} but never makes the person wait longer than {@code seconds}: the route servers can take a minute
     * of retries when they are slow or blocked (that was the card stuck on "Finding the best way..."). On timeout the
     * answer is an offline estimate; the real work keeps running in the background and fills the caches, so the next try is fast.
     */
    public static Trip.Plan planWithin(double[] from, double[] to, int seconds) {
        Future<Trip.Plan> f = com.bofo.finder.util.Async.fanout().submit(() -> plan(from, to));
        try { return f.get(seconds, TimeUnit.SECONDS); }
        catch (Throwable t) { return quick(from, to, "The route servers are slow right now, so distances are rough estimates. Tap the tab again in a moment for exact roads."); }
    }

    /** Offline plan from straight-line estimates only (no network). Never throws. */
    public static Trip.Plan quick(double[] from, double[] to, String note) {
        double straight = Geo.km(from[0], from[1], to[0], to[1]);
        Router.Route est = Router.estimate(from[0], from[1], to[0], to[1]);
        if (straight > FAR_KM) return new Trip.Plan(List.of(Trip.far(est.pts(), est.meters(), true)), 0, straight, note);
        Router.Route known = Router.peek(from[0], from[1], to[0], to[1]);                    // a road we already have beats a straight line
        Router.Route w = known != null ? known : est;
        double m = Math.max(w.meters(), straight * 1000);
        List<Trip.Option> opts = new ArrayList<>();
        if (m / 1000.0 <= 3.5 || m < 600) opts.add(Trip.walk(w.pts(), m, w.approximate()));
        if (m >= 600 && m <= 6000) opts.add(Trip.tricycle(w.pts(), m, w.approximate()));
        if (m >= 1500) opts.add(Trip.jeepneyGuess(w.pts(), m, w.approximate()));
        if (opts.isEmpty()) opts.add(Trip.walk(w.pts(), m, w.approximate()));
        return new Trip.Plan(opts, Trip.best(opts), straight, note);
    }

    private static void addNote(StringBuilder sb, String text) { if (sb.length() > 0) sb.append(' '); sb.append(text); }

    private static Trip.Plan far(double[] from, double[] to, double straight) {
        Router.Route drive = Router.drive(from[0], from[1], to[0], to[1]);
        double m = Math.max(drive.meters(), straight * 1000);
        List<Trip.Option> one = List.of(Trip.far(drive.pts(), m, drive.approximate()));
        String note = "This is about " + Geo.fmt(straight) + " away in a straight line, too far to walk. "
                + "If that is not where you are right now, check your location or look at the route from the BH to the campus instead.";
        return new Trip.Plan(one, 0, straight, note);
    }
}
