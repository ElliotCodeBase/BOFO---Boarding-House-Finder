package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.BoardingHouse;

import java.util.List;
import org.junit.Test;

/** v2.0.83: commute wording / option choice and the offline Explore copy (round trip + filters on the phone). */
public class CommuteOfflineTest {
    private static BoardingHouse bh(String id, double lat, double lng, double price, int beds, boolean verified) {
        return new BoardingHouse(id, "o", "House " + id, "Daro", lat, lng, price, beds, false, false, true, 4.5, 3, "http://x/" + id + ".jpg", verified, 0, false, List.of("Aircon"));
    }

    @Test public void commuteLineHasModeAndMinutes() {
        double[][] p = {{9.3, 123.3}, {9.31, 123.31}};
        Trip.Option walk = Trip.walk(p, 900, false);
        assertTrue(CommuteRules.line(walk).contains("Walk"));
        assertTrue(CommuteRules.line(walk).contains(Geo.fmtTime(walk.minutes())));
        assertFalse(CommuteRules.isRide(Trip.Mode.WALK));
        assertTrue(CommuteRules.isRide(Trip.Mode.JEEPNEY));
    }

    @Test public void bestOptionAndDrawable() {
        assertNull(CommuteRules.best(null));
        double[][] p = {{9.3, 123.3}, {9.31, 123.31}};
        Trip.Option w = Trip.walk(p, 900, false);
        Trip.Plan plan = new Trip.Plan(List.of(w), 0, 1.0, "");
        assertSame(w, CommuteRules.best(plan));
        assertTrue(CommuteRules.drawable(w));
        assertFalse(CommuteRules.drawable(Trip.walk(new double[][]{{9.3, 123.3}}, 10, false)));
    }

    @Test public void savedSearchComesBackWithTheCardFields() {
        List<BoardingHouse> back = OfflineExplore.fromJson(OfflineExplore.toJson(List.of(bh("a", 9.3, 123.3, 1800, 2, true))).toString());
        assertEquals(1, back.size());
        BoardingHouse h = back.get(0);
        assertEquals("a", h.id()); assertEquals(1800, h.price(), 0); assertEquals(2, h.availableBeds());
        assertTrue(h.ownerVerified()); assertEquals("http://x/a.jpg", h.photoUrl()); assertEquals(9.3, h.lat(), 1e-9);
    }

    @Test public void damagedFileMeansNothingSaved() { assertTrue(OfflineExplore.fromJson("{not json").isEmpty()); }

    @Test public void chipsWorkOnTheSavedCopy() {
        List<BoardingHouse> all = List.of(bh("a", 9.30, 123.30, 1800, 2, true), bh("b", 9.31, 123.31, 2500, 0, false), bh("c", 9.32, 123.32, 1200, 1, false));
        assertEquals(2, OfflineExplore.apply(all, "", null, 2000.0, List.of(), false, false, 0, null, null, null).size());
        assertEquals("a", OfflineExplore.apply(all, "", null, null, List.of(), true, false, 0, null, null, null).get(0).id());
        assertEquals(2, OfflineExplore.apply(all, "", null, null, List.of(), false, true, 0, null, null, null).size());
        assertEquals("c", OfflineExplore.apply(all, "house c", null, null, List.of(), false, false, 0, null, null, null).get(0).id());
        assertEquals("c", OfflineExplore.apply(all, "", null, null, List.of(), false, false, ExploreRules.SORT_PRICE, null, null, null).get(0).id());
    }

    @Test public void nearestUsesTheCampusAndBoxKeepsInside() {
        List<BoardingHouse> all = List.of(bh("far", 9.40, 123.40, 1000, 1, false), bh("near", 9.301, 123.301, 1000, 1, false));
        List<BoardingHouse> l = OfflineExplore.apply(all, "", null, null, List.of(), false, false, 0, 9.30, 123.30, null);
        assertEquals("near", l.get(0).id());
        assertTrue(l.get(0).distanceKm() < l.get(1).distanceKm());
        assertEquals(1, OfflineExplore.apply(all, "", null, null, List.of(), false, false, 0, null, null, new double[]{9.29, 123.29, 9.31, 123.31}).size());
    }
}
