package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.College;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Stage 6: offline parts of Router, LocalTransit, TransitRoutes, TripPlanner.quick, DirectionsRules and the route line GeoJSON. Nothing here touches the network. */
public class Stage6LogicTest {

    // ---- Router ---------------------------------------------------------------------------------------------

    /** Standard encoded-polyline writer (precision 6), the inverse of Router.decodePolyline6. */
    private static String encode6(double[][] pts) {
        StringBuilder sb = new StringBuilder();
        long pLat = 0, pLng = 0;
        for (double[] p : pts) {
            long la = Math.round(p[0] * 1e6), ln = Math.round(p[1] * 1e6);
            enc(la - pLat, sb); enc(ln - pLng, sb);
            pLat = la; pLng = ln;
        }
        return sb.toString();
    }
    private static void enc(long v, StringBuilder sb) {
        long x = v < 0 ? ~(v << 1) : (v << 1);
        while (x >= 0x20) { sb.append((char) ((0x20 | (x & 0x1f)) + 63)); x >>= 5; }
        sb.append((char) (x + 63));
    }

    @Test public void polylineDecoderRoundTrips() {
        double[][] pts = {{9.306800, 123.305400}, {9.307500, 123.306100}, {9.305000, 123.300000}, {-1.5, -179.25}};
        List<double[]> back = Router.decodePolyline6(encode6(pts));
        assertEquals(pts.length, back.size());
        for (int i = 0; i < pts.length; i++) {
            assertEquals(pts[i][0], back.get(i)[0], 1e-6);
            assertEquals(pts[i][1], back.get(i)[1], 1e-6);
        }
    }

    @Test public void thinKeepsFirstAndLast() {
        List<double[]> many = new ArrayList<>();
        for (int i = 0; i < 1000; i++) many.add(new double[]{9.0 + i * 1e-4, 123.0});
        double[][] t = Router.thin(many, 400);
        assertEquals(400, t.length);
        assertEquals(many.get(0)[0], t[0][0], 0);
        assertEquals(many.get(999)[0], t[399][0], 0);
        assertEquals(10, Router.thin(many.subList(0, 10), 400).length);
    }

    @Test public void estimateIsAStraightFlaggedLine() {
        Router.Route r = Router.estimate(9.30, 123.30, 9.31, 123.30);
        assertTrue(r.approximate());
        assertEquals(2, r.pts().length);
        assertEquals(Geo.km(9.30, 123.30, 9.31, 123.30) * 1300, r.meters(), 1e-6);
        double[] b = r.bounds();
        assertEquals(9.30, b[0], 1e-9);
        assertEquals(9.31, b[2], 1e-9);
    }

    @Test public void peekReturnsKnownRoadsAndTheReverseWay() {
        assertNull(Router.peek(1.11111, 2.22222, 3.33333, 4.44444));
        double[][] pts = {{1.11111, 2.22222}, {2.0, 3.0}, {3.33333, 4.44444}};
        Router.remember(1.11111, 2.22222, 3.33333, 4.44444, new Router.Route(pts, 1234, false));
        Router.Route same = Router.peek(1.11111, 2.22222, 3.33333, 4.44444);
        assertEquals(1234, same.meters(), 0);
        Router.Route back = Router.peek(3.33333, 4.44444, 1.11111, 2.22222);
        assertEquals(3, back.pts().length);
        assertEquals(3.33333, back.pts()[0][0], 0);
        assertEquals(1.11111, back.pts()[2][0], 0);
        assertFalse(back.approximate());
    }

    // ---- LocalTransit ---------------------------------------------------------------------------------------

    @Test public void localTransitReadsPathsAndBothWays() {
        String json = "{\"lines\":[{\"ref\":\"L1\",\"name\":\"Poblacion - Campus\",\"fareLow\":12,\"fareHigh\":15,\"note\":\"Checked.\","
                + "\"path\":[[9.30,123.30],[9.30,123.31]]}]}";
        List<TransitMatch.Line> out = LocalTransit.parse(json);
        assertEquals(2, out.size());
        assertTrue(out.get(0).local());
        assertEquals("local", out.get(0).kind());
        assertEquals(12, out.get(0).fareLow());
        assertEquals(15, out.get(0).fareHigh());
        assertEquals("Checked.", out.get(0).note());
        assertEquals(123.31, out.get(1).path()[0][1], 1e-9);                                   // the second one runs the other way
    }

    @Test public void localTransitOneWayAndStops() {
        String json = "{\"lines\":[{\"name\":\"Loop\",\"bothWays\":false,\"stops\":[{\"lat\":9.30,\"lng\":123.30,\"name\":\"A\"},{\"lat\":9.30,\"lng\":123.305,\"name\":\"B\"}]}]}";
        List<TransitMatch.Line> out = LocalTransit.parse(json);
        assertEquals(1, out.size());
        double[][] p = out.get(0).path();
        assertTrue(p.length >= 6);                                                             // 555 m between stops, at most 100 m between points
        for (int i = 1; i < p.length; i++) assertTrue(TransitMatch.m(p[i - 1], p[i]) <= 101);
        assertEquals(-1, out.get(0).fareLow());                                                // no checked fare: the LTFRB formula is used
    }

    @Test public void localTransitSkipsBrokenEntriesAndFiles() {
        String json = "{\"lines\":[{\"name\":\"No points\"},{\"path\":[[9.30,123.30],[9.30,123.31]]},"
                + "{\"name\":\"Out of range\",\"path\":[[95.0,123.30],[9.30,123.31]]},{\"name\":\"One point\",\"path\":[[9.30,123.30]]},"
                + "{\"name\":\"Good\",\"bothWays\":false,\"path\":[[9.30,123.30],[9.30,123.31]]}]}";
        List<TransitMatch.Line> out = LocalTransit.parse(json);
        assertEquals(1, out.size());
        assertEquals("Good", out.get(0).name());
        assertTrue(LocalTransit.parse("not json at all").isEmpty());
        assertTrue(LocalTransit.parse("").isEmpty());
        assertTrue(LocalTransit.parse(null).isEmpty());
        assertTrue(LocalTransit.parse("{\"_help\":\"x\",\"lines\":[]}").isEmpty());            // the file that ships today
    }

    // ---- TransitRoutes --------------------------------------------------------------------------------------

    private static String way(double lat, double lng0, double lng1, boolean withHole) {
        StringBuilder g = new StringBuilder("[");
        g.append("{\"lat\":").append(lat).append(",\"lon\":").append(lng0).append("},");
        if (withHole) g.append("null,");
        g.append("{\"lat\":").append(lat).append(",\"lon\":").append(lng1).append("}]");
        return g.toString();
    }

    @Test public void overpassAnswerBecomesChainedLines() throws Exception {
        String body = "{\"elements\":["
                + "{\"type\":\"relation\",\"tags\":{\"ref\":\"12A\",\"name\":\"Poblacion - Bajumpandan\",\"route\":\"bus\"},\"members\":["
                + "{\"type\":\"way\",\"role\":\"\",\"geometry\":" + way(9.30, 123.300, 123.305, false) + "},"
                + "{\"type\":\"node\",\"role\":\"stop\",\"lat\":9.3,\"lon\":123.3},"
                + "{\"type\":\"way\",\"role\":\"platform\",\"geometry\":" + way(9.31, 123.300, 123.305, false) + "},"
                + "{\"type\":\"way\",\"role\":\"\",\"geometry\":" + way(9.30, 123.310, 123.305, false) + "}]},"
                + "{\"type\":\"relation\",\"tags\":{\"name\":\"Hole\",\"route\":\"bus\"},\"members\":[{\"type\":\"way\",\"role\":\"\",\"geometry\":" + way(9.30, 123.300, 123.305, true) + "}]},"
                + "{\"type\":\"way\",\"id\":5}]}";
        List<TransitMatch.Line> lines = TransitRoutes.parse(body);
        assertEquals(1, lines.size());
        TransitMatch.Line l = lines.get(0);
        assertEquals("12A", l.ref());
        assertEquals("bus", l.kind());
        assertFalse(l.local());
        double[][] p = l.path();
        assertEquals(3, p.length);                                                             // two ways chained, the platform way left out
        assertEquals(123.300, p[0][1], 1e-9);
        assertEquals(123.310, p[2][1], 1e-9);
        assertTrue(TransitRoutes.parse("{\"elements\":[]}").isEmpty());
        assertTrue(TransitRoutes.parse("{}").isEmpty());
    }

    @Test public void deviceCacheRoundTrips() {
        double[][] path = {{9.123456, 123.654321}, {9.2, 123.7}};
        List<TransitMatch.Line> in = Collections.singletonList(new TransitMatch.Line("7", "Seven", "Op", "bus", path));
        List<TransitMatch.Line> out = TransitRoutes.fromJson(TransitRoutes.toJson(in));
        assertEquals(1, out.size());
        assertEquals("Seven", out.get(0).name());
        assertEquals("Op", out.get(0).operator());
        assertEquals(9.12346, out.get(0).path()[0][0], 1e-9);                                  // 5 decimals kept
        assertNull(TransitRoutes.fromJson(null));
        assertNull(TransitRoutes.fromJson("garbage"));
    }

    // ---- TripPlanner.quick (no network) ---------------------------------------------------------------------

    @Test public void quickPlanForShortMediumAndFarTrips() {
        Trip.Plan shortTrip = TripPlanner.quick(new double[]{9.30, 123.30}, new double[]{9.3036, 123.30}, "n");           // 0.4 km
        assertEquals(1, shortTrip.options().size());
        assertEquals(Trip.Mode.WALK, shortTrip.options().get(0).mode());
        assertEquals("n", shortTrip.note());

        Trip.Plan mid = TripPlanner.quick(new double[]{9.30, 123.30}, new double[]{9.327, 123.30}, "");                    // 3 km
        List<Trip.Mode> modes = new ArrayList<>();
        for (Trip.Option o : mid.options()) modes.add(o.mode());
        assertTrue(modes.contains(Trip.Mode.TRICYCLE) && modes.contains(Trip.Mode.JEEPNEY));
        assertFalse(modes.contains(Trip.Mode.WALK));                                                                       // 3 km x 1.3 is a long walk
        assertTrue(mid.best() >= 0 && mid.best() < mid.options().size());

        Trip.Plan far = TripPlanner.quick(new double[]{9.30, 123.30}, new double[]{9.60, 123.30}, "");                     // 33 km
        assertEquals(1, far.options().size());
        assertEquals(Trip.Mode.FAR, far.options().get(0).mode());
        assertTrue(far.straightKm() > TripPlanner.FAR_KM);
    }

    @Test public void quickPlanUsesAKnownRoadInsteadOfALine() {
        double[][] road = {{7.0, 7.0}, {7.005, 7.01}, {7.01, 7.02}};
        Router.remember(7.0, 7.0, 7.01, 7.02, new Router.Route(road, 2600, false));
        Trip.Plan p = TripPlanner.quick(new double[]{7.0, 7.0}, new double[]{7.01, 7.02}, "");
        Trip.Option walk = p.options().get(0);
        assertEquals(Trip.Mode.WALK, walk.mode());
        assertEquals(3, walk.path().length);
        assertFalse(walk.approximate());
    }

    // ---- DirectionsRules ------------------------------------------------------------------------------------

    private static final double[] BH = {9.30, 123.30}, ME = {9.31, 123.31}, CAMPUS = {9.32, 123.32};

    @Test public void tabsPickTheRightEnds() {
        assertArrayEquals(ME, DirectionsRules.from(DirectionsRules.TAB_ME_BH, BH, ME), 0);
        assertArrayEquals(BH, DirectionsRules.to(DirectionsRules.TAB_ME_BH, BH, CAMPUS), 0);
        assertArrayEquals(ME, DirectionsRules.from(DirectionsRules.TAB_ME_CAMPUS, BH, ME), 0);
        assertArrayEquals(CAMPUS, DirectionsRules.to(DirectionsRules.TAB_ME_CAMPUS, BH, CAMPUS), 0);
        assertArrayEquals(BH, DirectionsRules.from(DirectionsRules.TAB_BH_CAMPUS, BH, ME), 0);
        assertArrayEquals(CAMPUS, DirectionsRules.to(DirectionsRules.TAB_BH_CAMPUS, BH, CAMPUS), 0);
        assertNull(DirectionsRules.from(DirectionsRules.TAB_ME_BH, BH, null));
        assertTrue(DirectionsRules.startsAtMe(0) && DirectionsRules.startsAtMe(1) && !DirectionsRules.startsAtMe(2));
        assertTrue(!DirectionsRules.needsCampus(0) && DirectionsRules.needsCampus(1) && DirectionsRules.needsCampus(2));
        assertArrayEquals(new int[]{0, 2, 1}, DirectionsRules.SHOWN_ORDER);
    }

    @Test public void whatIsMissingAndWhichTabOpensFirst() {
        assertEquals(DirectionsRules.Missing.NONE, DirectionsRules.missing(0, true, false));
        assertEquals(DirectionsRules.Missing.NEED_LOCATION, DirectionsRules.missing(0, false, true));
        assertEquals(DirectionsRules.Missing.NEED_CAMPUS, DirectionsRules.missing(2, false, false));
        assertEquals(DirectionsRules.Missing.NONE, DirectionsRules.missing(2, false, true));              // BH -> Campus needs no location
        assertEquals(DirectionsRules.Missing.NEED_CAMPUS_AND_LOCATION, DirectionsRules.missing(1, false, false));
        assertEquals(DirectionsRules.Missing.NEED_CAMPUS, DirectionsRules.missing(1, true, false));
        assertEquals(DirectionsRules.Missing.NEED_LOCATION, DirectionsRules.missing(1, false, true));
        assertEquals(0, DirectionsRules.defaultTab(true, true));
        assertEquals(0, DirectionsRules.defaultTab(false, false));
        assertEquals(2, DirectionsRules.defaultTab(false, true));
    }

    @Test public void nearestCampusAndMoving() {
        List<College> cs = Arrays.asList(new College("a", "Far U", 9.60, 123.30), new College("b", "Near U", 9.31, 123.30));
        assertEquals("Near U", DirectionsRules.nearestCampus(cs, 9.30, 123.30).name());
        assertNull(DirectionsRules.nearestCampus(Collections.singletonList(new College("a", "Far U", 9.60, 123.30)), 9.30, 123.30));   // 33 km
        assertNull(DirectionsRules.nearestCampus(new ArrayList<College>(), 9.30, 123.30));
        assertNull(DirectionsRules.nearestCampus(null, 9.30, 123.30));
        assertFalse(DirectionsRules.movedEnough(new double[]{9.30, 123.30}, 9.3005, 123.30));             // 55 m
        assertTrue(DirectionsRules.movedEnough(new double[]{9.30, 123.30}, 9.3010, 123.30));              // 111 m
        assertFalse(DirectionsRules.movedEnough(null, 9.3010, 123.30));
    }

    @Test public void summaryTextsAndOsmLink() {
        double[][] p = {{0, 0}, {0, 1}};
        assertEquals("10 min walk", DirectionsRules.heroHead(Trip.walk(p, 800, false)));
        assertEquals("800 m", DirectionsRules.heroMeta(Trip.walk(p, 800, false)));
        assertEquals("13 min by tricycle", DirectionsRules.heroHead(Trip.tricycle(p, 3400, false)));
        assertEquals("3.4 km  \u00B7  \u20B110\u201320", DirectionsRules.heroMeta(Trip.tricycle(p, 3400, false)));
        assertTrue(DirectionsRules.heroMeta(Trip.tricycle(p, 3400, true)).endsWith("estimate"));
        assertTrue(DirectionsRules.heroHead(Trip.jeepney("12A", p, 5200, 300, false)).endsWith("by jeepney"));
        assertTrue(DirectionsRules.heroHead(Trip.far(p, 105500, false)).endsWith("by bus or van"));
        assertEquals("https://www.openstreetmap.org/directions?engine=fossgis_osrm_foot&route=9.300000%2C123.300000%3B9.310000%2C123.310000",
                DirectionsRules.osmUrl(Trip.Mode.WALK, new double[]{9.30, 123.30}, new double[]{9.31, 123.31}));
        assertTrue(DirectionsRules.osmUrl(Trip.Mode.JEEPNEY, BH, CAMPUS).contains("fossgis_osrm_car"));
    }

    // ---- MapGeo route line ----------------------------------------------------------------------------------

    @Test public void routeLineJsonAndBounds() {
        double[][] pts = {{9.30, 123.30}, {9.31, 123.32}, {9.29, 123.31}};
        String solid = MapGeo.lineJson(pts, false);
        assertTrue(solid.contains("\"LineString\""));
        assertTrue(solid.contains("[123.300000,9.300000],[123.320000,9.310000],[123.310000,9.290000]"));    // GeoJSON order is lng, lat
        assertTrue(solid.contains("\"approx\":0"));
        assertTrue(MapGeo.lineJson(pts, true).contains("\"approx\":1"));
        assertEquals(MapGeo.EMPTY, MapGeo.lineJson(null, false));
        assertEquals(MapGeo.EMPTY, MapGeo.lineJson(new double[][]{{9.3, 123.3}}, false));
        double[] b = MapGeo.pathBounds(pts);
        assertEquals(9.29, b[0], 1e-9);
        assertEquals(123.30, b[1], 1e-9);
        assertEquals(9.31, b[2], 1e-9);
        assertEquals(123.32, b[3], 1e-9);
        assertNull(MapGeo.pathBounds(new double[0][]));
        assertNull(MapGeo.pathBounds(null));
    }
}
