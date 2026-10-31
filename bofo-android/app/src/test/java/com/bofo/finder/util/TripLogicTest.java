package com.bofo.finder.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The JavaFX dev/TripLogicCheck.java checks, as JUnit tests: time and fare formatting, joining road pieces, matching jeepney lines, options and the recommendation. */
public class TripLogicTest {

    // a straight road going east from (9.30,123.30), one point per ~111 m
    private static double[][] road(double lat, double lng0, int n) {
        double[][] p = new double[n][];
        for (int i = 0; i < n; i++) p[i] = new double[]{lat, lng0 + i * 0.001};
        return p;
    }
    private static double[][] rev(double[][] p) {
        double[][] r = new double[p.length][];
        for (int i = 0; i < p.length; i++) r[i] = p[p.length - 1 - i];
        return r;
    }

    @Test public void timeAndFareFormatting() {
        assertEquals("17 min", Geo.fmtTime(17));
        assertEquals("1 h 5 min", Geo.fmtTime(65));
        assertEquals("21 h 6 min", Geo.fmtTime(1266));
        assertEquals("2 h", Geo.fmtTime(120));
        assertEquals(14, Trip.jeepTraditional(3));
        assertEquals(14, Trip.jeepTraditional(4));
        assertEquals(18, Trip.jeepTraditional(6));
        assertEquals(17, Trip.jeepModern(4));
        assertEquals(29, Trip.jeepModern(9));
        assertEquals("\u20B114\u201317", Trip.peso(14, 17));
        assertEquals("\u20B10", Trip.peso(0, 0));
        assertEquals("", Trip.peso(-1, -1));
    }

    @Test public void chainJoinsPiecesInOneDirection() {
        double[][] w1 = road(9.30, 123.300, 5), w2 = rev(road(9.30, 123.304, 5)), w3 = road(9.30, 123.308, 5);
        double[][] ch = TransitMatch.chain(Arrays.asList(rev(w1), w2, w3));
        assertEquals(13, ch.length);                                    // no duplicated joints
        double lo = Math.min(ch[0][1], ch[ch.length - 1][1]), hi = Math.max(ch[0][1], ch[ch.length - 1][1]);
        assertTrue(Math.abs(lo - 123.300) < 1e-6 && Math.abs(hi - 123.312) < 1e-6);
        boolean mono = true;
        for (int i = 2; i < ch.length; i++) if ((ch[i][1] - ch[i - 1][1]) * (ch[1][1] - ch[0][1]) <= 0) mono = false;
        assertTrue(mono);
    }

    private final double[][] east = road(9.30, 123.300, 31);                 // 3.3 km line
    private final TransitMatch.Line eastLine = new TransitMatch.Line("12A", "Poblacion - Bajumpandan", "", "bus", east);
    private final TransitMatch.Line westLine = new TransitMatch.Line("12B", "Bajumpandan - Poblacion", "", "bus", rev(east));
    private final double[] from = {9.3003, 123.3005}, to = {9.3004, 123.3252};   // about 30 m / 40 m from the road

    @Test public void onlyTheLineGoingTheRightWayMatches() {
        List<TransitMatch.Match> ms = TransitMatch.match(Arrays.asList(eastLine, westLine), from, to, 450, 3);
        assertEquals(1, ms.size());
        assertEquals("12A", ms.get(0).line().ref());
        assertTrue(ms.get(0).walkToM() > 50 && ms.get(0).walkToM() < 80);
        assertTrue(Math.abs(ms.get(0).rideM() - 2747) < 60);
        assertEquals("12B", TransitMatch.match(Arrays.asList(eastLine, westLine), to, from, 450, 3).get(0).line().ref());
    }

    @Test public void badMatchesAreRejected() {
        assertTrue(TransitMatch.match(Arrays.asList(eastLine), new double[]{9.31, 123.3005}, new double[]{9.31, 123.3252}, 450, 3).isEmpty());   // road 1 km away
        double[][] gap = new double[][]{{9.30, 123.300}, {9.30, 123.301}, {9.30, 123.321}, {9.30, 123.322}};                                     // 2.2 km jump
        assertTrue(TransitMatch.match(Arrays.asList(new TransitMatch.Line("G", "gap", "", "bus", gap)), new double[]{9.30, 123.300}, new double[]{9.30, 123.322}, 450, 3).isEmpty());
        List<double[]> loop = new ArrayList<>();                                                                                                   // out and back: ride >> straight
        for (int i = 0; i <= 20; i++) loop.add(new double[]{9.30 + i * 0.001, 123.300});
        for (int i = 20; i >= 0; i--) loop.add(new double[]{9.30 + i * 0.001, 123.3005});
        assertTrue(TransitMatch.match(Arrays.asList(new TransitMatch.Line("L", "loop", "", "bus", loop.toArray(new double[0][]))), new double[]{9.3005, 123.300}, new double[]{9.3005, 123.3005}, 450, 3).isEmpty());
    }

    @Test public void rankingAndDuplicates() {
        TransitMatch.Line dup = new TransitMatch.Line("12A", "Poblacion - Bajumpandan", "", "bus", road(9.3008, 123.300, 31));
        assertEquals(1, TransitMatch.match(Arrays.asList(eastLine, dup), from, to, 450, 3).size());                    // same name: keep the best
        TransitMatch.Line other = new TransitMatch.Line("7", "Other", "", "bus", road(9.3025, 123.300, 31));
        assertEquals("12A", TransitMatch.match(Arrays.asList(other, eastLine), from, to, 450, 3).get(0).line().ref());   // closer first
        TransitMatch.Line local = new TransitMatch.Line("L1", "Local", "", "local", road(9.3006, 123.300, 31), "n", 12, 12);
        TransitMatch.Line osm = new TransitMatch.Line("12A", "Poblacion", "", "bus", east);
        assertEquals("L1", TransitMatch.match(Arrays.asList(osm, local), from, to, 450, 3).get(0).line().ref());          // hand-made line outranks OSM
    }

    @Test public void optionsAndTheRecommendation() {
        double[][] p = {{0, 0}, {0, 1}};
        Trip.Option walk800 = Trip.walk(p, 800, false), walk3000 = Trip.walk(p, 3000, false);
        assertEquals(10, walk800.minutes());
        assertEquals(36, walk3000.minutes());
        assertEquals("\u20B10", walk800.fareText());
        assertEquals(1, Trip.best(Arrays.asList(Trip.tricycle(p, 800, false), walk800)));              // a walk of 1 km or less always wins
        Trip.Option jeep = Trip.jeepney("12A", p, 5200, 300, false);
        assertEquals(1, Trip.best(Arrays.asList(Trip.walk(p, 4500, false), jeep)));                    // a long walk loses to a cheap jeepney
        assertTrue(jeep.fareLow() == 16 && jeep.fareHigh() == 20);
        assertEquals("", Trip.far(p, 105500, false).fareText());
        assertEquals("3 h 1 min", Geo.fmtTime(Trip.far(p, 105500, false).minutes()));
        assertEquals(3, Trip.concat(new double[][]{{1, 1}, {2, 2}}, new double[][]{{2, 2}, {3, 3}}).length);
    }

    @Test public void checkedFaresAndSummaries() {
        double[][] p = {{0, 0}, {0, 1}};
        Trip.Option checked = Trip.jeepney("L1", p, 2800, 160, false, 12, 12, "Checked Oct 2026.");
        assertEquals("\u20B112", checked.fareText());
        assertTrue(checked.detail().contains("checked by the BOFO team") && checked.detail().contains("Checked Oct 2026."));
        assertFalse(Trip.jeepney("12A", p, 2800, 160, false).detail().contains("checked by the BOFO team"));
        String s = Trip.tricycle(p, 3400, false).summary();
        assertTrue(s.contains("Tricycle") && s.contains("\u20B110\u201320") && s.contains("3.4 km"));
        assertFalse(Trip.walk(p, 600, false).summary().contains("\u20B1"));
    }
}
