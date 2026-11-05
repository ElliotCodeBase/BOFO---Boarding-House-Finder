package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

/** v2.9.3: the first and last segment of a road route (you -> road, road -> house) are drawn dashed. */
public class RouteConnectorsTest {
    private static int count(String s, String what) { int n = 0, i = 0; while ((i = s.indexOf(what, i)) >= 0) { n++; i += what.length(); } return n; }

    @Test public void roadRouteGetsTwoDashedEndsAndOneSolidMiddle() {
        double[][] pts = {{9.3000, 123.3000}, {9.3004, 123.3000}, {9.3004, 123.3020}, {9.3010, 123.3020}};
        String j = MapGeo.routeJson(pts, false);
        assertEquals(2, count(j, "\"approx\":1"));
        assertEquals(1, count(j, "\"approx\":0"));
    }

    @Test public void estimateAndShortPathsAreUnchanged() {
        double[][] two = {{9.3, 123.3}, {9.31, 123.31}};
        assertEquals(MapGeo.lineJson(two, false), MapGeo.routeJson(two, false));
        double[][] three = {{9.3, 123.3}, {9.3004, 123.3}, {9.3010, 123.3}};
        assertEquals(MapGeo.lineJson(three, true), MapGeo.routeJson(three, true));
    }

    @Test public void tinyEndSegmentsStaySolid() {
        double[][] pts = {{9.30000, 123.30000}, {9.300001, 123.30000}, {9.3010, 123.3010}, {9.3020, 123.3020}};
        String j = MapGeo.routeJson(pts, false);
        assertEquals(1, count(j, "\"approx\":1"));       // only the long end segment is dashed
    }

    @Test public void endsOfARealRouteStayExactlyOnYouAndTheHouse() {
        double[][] road = {{9.3004, 123.3000}, {9.3004, 123.3020}};
        double[][] all = Router.withEnds(9.3000, 123.2999, 9.3010, 123.3021, road);
        assertEquals(4, all.length);
        assertArrayEquals(new double[]{9.3000, 123.2999}, all[0], 0);
        assertArrayEquals(road[0], all[1], 0);
        assertArrayEquals(new double[]{9.3010, 123.3021}, all[3], 0);
    }
}
