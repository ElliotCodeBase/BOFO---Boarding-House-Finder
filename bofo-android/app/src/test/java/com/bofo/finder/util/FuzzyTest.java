package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class FuzzyTest {
    @Test public void normKeepsLowerCaseLettersAndDigitsOnly() {
        assertEquals("freewifi", Fuzzy.norm("Free Wi-Fi"));
        assertEquals("24hrcctv", Fuzzy.norm(" 24-hr  CCTV! "));
    }

    @Test public void exactAndPrefixMatchesScoreHighest() {
        assertEquals(100, Fuzzy.score("laundry", "Laundry"));
        assertEquals(95, Fuzzy.score("laund", "Laundry Area"));
        assertEquals(95, Fuzzy.score("aircon", "Air Conditioning"));
    }

    @Test public void wordInsideNameScoresEighty() {
        assertEquals(80, Fuzzy.score("wifi", "Free Wi-Fi"));
        assertEquals(90, Fuzzy.score("wi", "Free Wi-Fi"));      // start of a word
    }

    @Test public void nicknamesFindTheRealAmenity() {
        assertEquals(85, Fuzzy.score("ac", "Air Conditioning"));
        assertEquals(85, Fuzzy.score("internet", "Free Wi-Fi"));
        assertEquals(85, Fuzzy.score("cr", "Private Bathroom"));
    }

    @Test public void smallTyposAreSuggestionsBelowRealMatches() {
        int typo = Fuzzy.score("laundy", "Laundry");
        assertTrue(typo > 0 && typo < 80);
        assertTrue(Fuzzy.score("kitchn", "Kitchen") > 0);
    }

    @Test public void unrelatedOrEmptyQueriesScoreZero() {
        assertEquals(0, Fuzzy.score("", "Wifi"));
        assertEquals(0, Fuzzy.score("zzzz", "Wifi"));
        assertEquals(0, Fuzzy.score("abc", "Wifi"));         // 3 letters: no typo tolerance
    }
}
