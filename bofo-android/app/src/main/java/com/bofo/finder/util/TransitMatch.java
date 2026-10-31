package com.bofo.finder.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds which public-transport lines (jeepney / multicab / bus routes mapped in OpenStreetMap) really take you from
 * near A to near B, in the direction of travel. Pure logic, no network: {@link TransitRoutes} downloads the lines.
 */
public final class TransitMatch {
    private TransitMatch() {}

    /** One line. {@code path} = {lat,lng} points in the direction the vehicle travels. */
    public record Line(String ref, String name, String operator, String kind, double[][] path, String note, int fareLow, int fareHigh) {
        /** A line from OpenStreetMap: no verified fare (the LTFRB formula is used) and no note. */
        public Line(String ref, String name, String operator, String kind, double[][] path) { this(ref, name, operator, kind, path, "", -1, -1); }
        /** True for lines typed in by the BOFO team (local-transit.json): these are checked by a person, so they rank first. */
        public boolean local() { return "local".equals(kind); }
        public String label() {
            String r = ref == null ? "" : ref.trim(), n = name == null ? "" : name.trim();
            if (!r.isEmpty() && !n.isEmpty() && !n.contains(r)) return r + " \u00B7 " + n;
            if (r.isEmpty()) return n;
            return n.isEmpty() ? r : n;
        }
    }

    /** A line that works for the trip: board at path[board], get off at path[alight]. */
    public record Match(Line line, int board, int alight, double walkToM, double walkFromM, double rideM) {
        public double[][] ridePath() { return Arrays.copyOfRange(line.path(), board, alight + 1); }
        public double[] boardPoint() { return line.path()[board]; }
        public double[] alightPoint() { return line.path()[alight]; }
        /** Lower is better: walking counts fully, riding a bit (a long ride is slower and costs more). */
        public double cost() { return (walkToM + walkFromM + rideM * 0.15) * (line.local() ? 0.8 : 1.0); }
    }

    /** Metres between two points (fast flat-earth formula, accurate to well under 1% over a few km). */
    public static double m(double[] a, double[] b) {
        double dy = (b[0] - a[0]) * 111_320.0, dx = (b[1] - a[1]) * 111_320.0 * Math.cos(Math.toRadians((a[0] + b[0]) / 2));
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double[] last(double[][] p) { return p[p.length - 1]; }

    private static double[][] reversed(double[][] p) {
        double[][] r = new double[p.length][];
        for (int i = 0; i < p.length; i++) r[i] = p[p.length - 1 - i];
        return r;
    }

    /** Joins road pieces (as listed in an OSM route relation) into one path, flipping any piece that runs the wrong way. */
    public static double[][] chain(List<double[][]> pieces) {
        List<double[][]> ps = new ArrayList<>();
        for (double[][] p : pieces) if (p != null && p.length >= 2) ps.add(p);
        if (ps.isEmpty()) return new double[0][];
        double[][] first = ps.get(0);
        if (ps.size() > 1) {                                                   // the first piece must end where the second begins
            double[][] nxt = ps.get(1);
            double endToNext = Math.min(m(last(first), nxt[0]), m(last(first), last(nxt)));
            double startToNext = Math.min(m(first[0], nxt[0]), m(first[0], last(nxt)));
            if (startToNext < endToNext) first = reversed(first);
        }
        List<double[]> out = new ArrayList<>(Arrays.asList(first));
        for (int k = 1; k < ps.size(); k++) {
            double[][] w = ps.get(k);
            double[] end = out.get(out.size() - 1);
            if (m(end, last(w)) < m(end, w[0])) w = reversed(w);
            for (int i = m(end, w[0]) < 3 ? 1 : 0; i < w.length; i++) out.add(w[i]);
        }
        return out.toArray(new double[0][]);
    }

    /**
     * Lines that pass within {@code maxWalkM} of both {@code from} and {@code to}, getting to {@code to} AFTER {@code from}
     * in the direction of travel. Best first, one per line name, at most {@code max}. A ride that jumps over a gap in the
     * data (consecutive points more than 1.5 km apart) or wanders more than 3x the straight way is dropped.
     */
    public static List<Match> match(List<Line> lines, double[] from, double[] to, double maxWalkM, int max) {
        Map<String, Match> bestPerLine = new HashMap<>();
        for (Line line : lines) {
            double[][] p = line.path();
            if (p.length < 2) continue;
            int iFrom = -1, iTo = -1; double dFrom = Double.MAX_VALUE, dTo = Double.MAX_VALUE;
            for (int i = 0; i < p.length; i++) {
                double a = m(p[i], from), b = m(p[i], to);
                if (a < dFrom) { dFrom = a; iFrom = i; }
                if (b < dTo) { dTo = b; iTo = i; }
            }
            if (dFrom > maxWalkM || dTo > maxWalkM || iFrom >= iTo) continue;
            double ride = 0; boolean gap = false;
            for (int i = iFrom + 1; i <= iTo; i++) { double d = m(p[i - 1], p[i]); if (d > 1500) gap = true; ride += d; }
            double straight = m(p[iFrom], p[iTo]);
            if (gap || ride < 250 || ride > straight * 3 + 500) continue;
            Match mt = new Match(line, iFrom, iTo, dFrom, dTo, ride);
            String key = line.label().toLowerCase();
            Match old = bestPerLine.get(key);
            if (old == null || mt.cost() < old.cost()) bestPerLine.put(key, mt);
        }
        List<Match> out = new ArrayList<>(bestPerLine.values());
        out.sort(Comparator.comparingDouble(Match::cost));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }
}
