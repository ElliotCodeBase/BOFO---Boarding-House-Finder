package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Locale;

public class GeoTest {
    @Test public void samePointIsZero() { assertEquals(0.0, Geo.km(10, 123, 10, 123), 1e-9); }

    @Test public void manilaToCebuIsAbout570Km() {
        assertEquals(571, Geo.km(14.5995, 120.9842, 10.3157, 123.8854), 6);
    }

    @Test public void distanceIsSymmetric() {
        assertEquals(Geo.km(9.3, 123.3, 10.3, 123.9), Geo.km(10.3, 123.9, 9.3, 123.3), 1e-9);
    }

    @Test public void walkMinutesUsesFiveKmPerHour() {
        assertEquals(60, Geo.walkMinutes(5.0));
        assertEquals(12, Geo.walkMinutes(1.0));
        assertEquals(0, Geo.walkMinutes(0));
    }

    @Test public void fmtTimeNeverPrintsHugeMinutes() {
        assertEquals("1 min", Geo.fmtTime(0));
        assertEquals("17 min", Geo.fmtTime(17));
        assertEquals("1 h 5 min", Geo.fmtTime(65));
        assertEquals("2 h", Geo.fmtTime(120));
        assertEquals("21 h 6 min", Geo.fmtTime(1266));
    }

    @Test public void fmtUsesMetresBelowOneKm() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            assertEquals("850 m", Geo.fmt(0.85));
            assertEquals("1.5 km", Geo.fmt(1.5));
            assertEquals("850 m \u00B7 10 min walk", Geo.walkText(0.85));
            assertEquals("12.0 km away", Geo.walkText(12));
        } finally { Locale.setDefault(old); }
    }

    @Test public void bearingPointsToCompassDirections() {
        assertEquals(0, Geo.bearing(0, 0, 1, 0), 1e-6);      // north
        assertEquals(90, Geo.bearing(0, 0, 0, 1), 1e-6);     // east
        assertEquals(180, Geo.bearing(1, 0, 0, 0), 1e-6);    // south
        assertEquals(270, Geo.bearing(0, 1, 0, 0), 1e-6);    // west
    }

    @Test public void fitZoomShrinksAsTheBoxGrows() {
        int small = Geo.fitZoom(9.30, 123.30, 9.31, 123.31, 400, 700, 3, 18);
        int big = Geo.fitZoom(5, 118, 19, 126, 400, 700, 3, 18);
        assertTrue(small > big);
        assertTrue(big >= 3 && small <= 18);
    }
}
