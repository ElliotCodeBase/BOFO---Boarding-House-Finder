package com.bofo.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SavedReplyRulesTest {
    @Test public void builtInsLineUpAndFit() {
        assertEquals(SavedReplyRules.LABELS.length, SavedReplyRules.TEXTS.length);
        for (String t : SavedReplyRules.TEXTS) assertTrue(t, SavedReplyRules.validReason(t) && t.length() <= SavedReplyRules.MAX_LEN);
    }
    @Test public void cleanTrimsCollapsesAndCuts() {
        assertEquals("", SavedReplyRules.clean(null));
        assertEquals("a b c", SavedReplyRules.clean("  a \n b   c "));
        assertEquals(SavedReplyRules.MAX_LEN, SavedReplyRules.clean(new String(new char[400]).replace('\0', 'x')).length());
    }
    @Test public void reasonNeedsFiveCharacters() {
        assertFalse(SavedReplyRules.validReason(" ok "));
        assertTrue(SavedReplyRules.validReason("blurry"));
    }
    @Test public void addPutsNewestFirstAndSkipsBadOnes() {
        List<String> l = SavedReplyRules.add(new ArrayList<>(), "First reply");
        l = SavedReplyRules.add(l, "Second reply");
        assertEquals(Arrays.asList("Second reply", "First reply"), l);
        assertEquals(2, SavedReplyRules.add(l, "  second   REPLY ").size());   // duplicate ignoring case / spacing
        assertEquals(2, SavedReplyRules.add(l, "no").size());                   // too short
        assertEquals(2, SavedReplyRules.add(l, SavedReplyRules.TEXTS[0]).size()); // already built in
        assertEquals(0, SavedReplyRules.add(null, "").size());
    }
    @Test public void addStopsAtTen() {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < 15; i++) l = SavedReplyRules.add(l, "reply number " + i);
        assertEquals(SavedReplyRules.MAX_OWN, l.size());
        assertEquals("reply number 9", l.get(0));
    }
    @Test public void removeIsSafe() {
        List<String> l = Arrays.asList("aaaaa", "bbbbb");
        assertEquals(Arrays.asList("bbbbb"), SavedReplyRules.remove(l, 0));
        assertEquals(2, SavedReplyRules.remove(l, 5).size());
        assertEquals(2, SavedReplyRules.remove(l, -1).size());
        assertEquals(0, SavedReplyRules.remove(null, 0).size());
    }
    @Test public void encodeDecodeRoundTripAndOddStorage() {
        List<String> l = Arrays.asList("with \"quotes\" and, commas", "second one");
        assertEquals(l, SavedReplyRules.decode(SavedReplyRules.encode(l)));
        assertTrue(SavedReplyRules.decode(null).isEmpty());
        assertTrue(SavedReplyRules.decode("").isEmpty());
        assertEquals(1, SavedReplyRules.decode("hello there\nhello there\nx\n\n").size());
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 20; i++) many.append("reply number ").append(i).append('\n');
        assertEquals(SavedReplyRules.MAX_OWN, SavedReplyRules.decode(many.toString()).size());
    }
}
