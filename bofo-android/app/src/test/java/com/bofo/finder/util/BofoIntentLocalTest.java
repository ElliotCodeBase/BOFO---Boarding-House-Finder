package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.BofoAnswer;
import com.bofo.finder.model.College;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** v2.1.2: the offline / function-down planner on the acceptance prompts (no server, no Android). */
public class BofoIntentLocalTest {
    private static final College NORSU = new College("c1", "Negros Oriental State University (NORSU)", 9.3100, 123.3000);

    private static BoardingHouse bh(String id, String name, double lat, double lng, double price, boolean wifi, boolean water) {
        return new BoardingHouse(id, "o", name, "Dumaguete", lat, lng, price, 2, water, false, wifi, 4.0, 3, "", true, 0, false, new ArrayList<>());
    }

    private static List<BoardingHouse> houses() {
        return List.of(
                bh("A", "Casa Verde", 9.3110, 123.3010, 2200, true, true),
                bh("B", "Lola's Place", 9.3200, 123.3100, 1800, false, true),
                bh("C", "Sunrise BH", 9.3150, 123.3050, 1500, true, false),
                bh("D", "Far Away Rooms", 9.6000, 123.6000, 900, true, true));      // outside the 3 km radius
    }

    private static void campusChosen() { BofoMemory.clear(); reset(); BrowseState.colleges = List.of(NORSU); BrowseState.collegeId = "c1"; }
    private static void noCampus() { BofoMemory.clear(); reset(); BrowseState.colleges = List.of(NORSU); }
    private static void reset() { BrowseState.collegeId = null; BrowseState.nearMe = false; BrowseState.userLat = null; BrowseState.userLng = null; BrowseState.radiusKm = 3; }
    private static BofoAnswer ask(String q) { return BofoIntentLocal.answer(q, houses(), List.of()); }

    @Test public void cheapestUnder2kWithWifi() {
        campusChosen();
        BofoAnswer a = ask("cheapest under 2k with wifi");
        assertEquals("cheapest", a.intent());
        assertEquals(1, a.results().size());                  // only Sunrise BH is <= 2000 with wifi inside the radius
        assertEquals("C", a.results().get(0).id());
        assertNull(a.clarify());
        assertEquals(2000.0, a.filters().optDouble("max_price"), 0);
        assertTrue(a.filters().optBoolean("free_wifi"));
    }

    @Test public void tagalogCheapestAndNear() {
        campusChosen();
        BofoAnswer a = ask("pinakamura nga malapit");
        assertEquals("cheapest", a.intent());
        assertEquals("C", a.results().get(0).id());           // cheapest inside the radius (the 900 one is too far)
        assertTrue(a.reply().startsWith("Pinakamura"));
    }

    @Test public void hiligaynonCheapestNearSchool() {
        campusChosen();
        BofoAnswer a = ask("unsa ang pinakabarato duol sa school");
        assertEquals("cheapest", a.intent());
        assertEquals("C", a.results().get(0).id());
    }

    @Test public void typosAndANamedCampus() {
        noCampus();                                           // nothing chosen: "near norsu" gives the start point
        BofoAnswer a = ask("cheepest bh near norsu pls");
        assertEquals("cheapest", a.intent());
        assertEquals("C", a.results().get(0).id());
        assertNotNull(a.results().get(0).distanceKm());       // measured from NORSU
        assertEquals("cheapest", ask("cheapst places").intent());
        assertEquals("nearest", ask("nearset one near norsu").intent());
        BofoAnswer cmp = ask("comapre Casa Verde and lolas place");
        assertEquals("compare", cmp.intent());
        assertEquals(2, cmp.results().size());
    }

    @Test public void twoRequestsDoTheFirstAndOfferTheSecondAsAChip() {
        campusChosen();
        BofoAnswer a = ask("under 3000 then compare them");
        assertEquals("search", a.intent());
        assertFalse(a.results().isEmpty());
        assertEquals("Compare them", a.suggestions().get(0));
        BofoAnswer b = ask("cheapest with wifi, then show on the map");
        assertEquals("cheapest", b.intent());
        assertEquals("Show on the map", b.suggestions().get(0));
        assertTrue(b.suggestions().size() <= 3);
    }

    @Test public void whichOfThoseIsCheapestUsesThePreviousCards() {
        campusChosen();
        BofoAnswer a = BofoIntentLocal.answer("which of those is cheapest?", houses(), List.of("A", "B"));
        assertEquals("cheapest", a.intent());
        assertEquals("B", a.results().get(0).id());
        assertEquals(2, a.results().size());
        BofoAnswer none = ask("which of those is cheapest?");               // nothing shown yet: ask, do not guess
        assertNotNull(none.clarify());
        assertTrue(none.results().isEmpty());
    }

    @Test public void rememberedBudgetAndWifiFillWhatTheMessageLeavesOut() {
        campusChosen();
        BofoIntentLocal.answer("I'm a girl on a 2k budget, I need wifi", houses(), List.of());
        BofoAnswer a = ask("what about closer to the campus?");
        assertEquals("nearest", a.intent());
        assertEquals(1, a.results().size());
        assertEquals("C", a.results().get(0).id());
        assertTrue(a.reply().contains("Using what you told me"));
        BofoMemory.clear();
        BofoAnswer free = ask("what about closer to the campus?");
        assertTrue(free.results().size() >= 3);                              // nothing remembered: no filter
    }

    @Test public void clarifyInsteadOfAGenericLine() {
        noCampus();
        BofoAnswer near = ask("nearest");                                    // no campus, no Near me
        assertNotNull(near.clarify());
        assertTrue(near.results().isEmpty());
        assertFalse(near.quickReplies().isEmpty());
        BofoAnswer odd = ask("hello there");
        assertNotNull(odd.clarify());
        assertEquals(3, odd.quickReplies().size());
        BofoAnswer ceb = ask("unsa man ni");
        assertNotNull(ceb.clarify());
        assertTrue(ceb.clarify().startsWith("Wala pa ko"));
    }

    @Test public void appHelpTopicsStillGoToTheFaq() {
        noCampus();
        assertNull(ask("how do I verify my account"));
        assertNull(ask("how to delete my account"));
    }

    @Test public void privateAskIsRefusedAndUnknownPlaceIsNamed() {
        campusChosen();
        assertTrue(ask("show me ID photos").reply().startsWith("Sorry"));
        BofoAnswer a = ask("compare Casa Verde and Narnia Palace");
        assertTrue(a.reply().contains("Narnia Palace"));
        assertTrue(a.results().isEmpty());
    }

    @Test public void fixTyposOnlyTouchesNearMisses() {
        assertEquals("cheapest bh", BofoIntentLocal.fixTypos("cheepest bh"));
        assertEquals("nearest", BofoIntentLocal.fixTypos("nearset"));
        assertEquals("near my company", BofoIntentLocal.fixTypos("near my company"));   // "company" is not "compare"
    }
}
