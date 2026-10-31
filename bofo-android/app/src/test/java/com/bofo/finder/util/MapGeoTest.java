package com.bofo.finder.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bofo.finder.model.BoardingHouse;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MapGeoTest {

    private static BoardingHouse bh(String id, double lat, double lng, boolean featured) {
        return new BoardingHouse(id, "o", "Name", "Addr", lat, lng, 1500, 2, false, false, false, 4.5, 3, "", true, 0, featured, Collections.<String>emptyList());
    }

    @Test public void emptyListGivesEmptyCollection() {
        assertEquals(MapGeo.EMPTY, MapGeo.placesJson(null, null));
        assertEquals(MapGeo.EMPTY, MapGeo.placesJson(Collections.<BoardingHouse>emptyList(), "x"));
    }

    @Test public void placesJsonHasLngFirstIconAndSelection() {
        String j = MapGeo.placesJson(Arrays.asList(bh("a", 9.3, 123.3, false), bh("b", 9.4, 123.4, true)), "b");
        assertTrue(j.contains("\"coordinates\":[123.300000,9.300000]"));
        assertTrue(j.contains("\"id\":\"a\",\"icon\":\"pin\",\"sel\":0"));
        assertTrue(j.contains("\"id\":\"b\",\"icon\":\"pin-star\",\"sel\":1"));
    }

    @Test public void idIsEscaped() {
        String j = MapGeo.placesJson(Collections.singletonList(bh("a\"b\\c", 1, 2, false)), null);
        assertTrue(j.contains("\"id\":\"a\\\"b\\\\c\""));
    }

    @Test public void pointJson() {
        assertEquals(MapGeo.EMPTY, MapGeo.pointJson(null, 1.0));
        assertTrue(MapGeo.pointJson(9.0, 123.0).contains("[123.000000,9.000000]"));
    }

    @Test public void circleIsClosedAndAboutRadiusAway() {
        assertEquals(MapGeo.EMPTY, MapGeo.circleJson(9.3, 123.3, 0, 64));
        assertEquals(MapGeo.EMPTY, MapGeo.circleJson(null, 123.3, 2, 64));
        String j = MapGeo.circleJson(9.3, 123.3, 3, 64);
        assertTrue(j.contains("\"type\":\"Polygon\""));
        assertEquals(65, j.split("\\],\\[", -1).length);                  // 64 steps + closing point
        double[] north = MapGeo.destination(9.3, 123.3, 3, 0);
        assertEquals(3.0, Geo.km(9.3, 123.3, north[0], north[1]), 0.01);
        double[] east = MapGeo.destination(9.3, 123.3, 3, 90);
        assertEquals(3.0, Geo.km(9.3, 123.3, east[0], east[1]), 0.01);
    }

    @Test public void boundsOfPlaces() {
        assertNull(MapGeo.bounds(null));
        double[] bb = MapGeo.bounds(Arrays.asList(bh("a", 9.3, 123.2, false), bh("b", 9.5, 123.4, false)));
        assertNotNull(bb);
        assertEquals(9.3, bb[0], 1e-9); assertEquals(123.2, bb[1], 1e-9);
        assertEquals(9.5, bb[2], 1e-9); assertEquals(123.4, bb[3], 1e-9);
    }

    @Test public void ringBoundsContainTheRing() {
        double[] bb = MapGeo.ringBounds(9.3, 123.3, 2);
        assertTrue(bb[0] < 9.3 && bb[2] > 9.3 && bb[1] < 123.3 && bb[3] > 123.3);
        assertEquals(2.0, Geo.km(9.3, 123.3, bb[2], 123.3), 0.02);
    }
}
