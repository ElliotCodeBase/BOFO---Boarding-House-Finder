package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.service.BofoAiService;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** v2.1.2: what the student says is found locally (English / Tagalog / Hiligaynon / Taglish) and the last chat is kept and wiped. */
public class BofoMemoryTest {
    private static Double budget(String s) { return BofoMemory.parse(s).budget(); }

    @Test public void pricePhrasesInFourLanguages() {
        assertEquals(2000.0, budget("cheapest under 2k with wifi"), 0);
        assertEquals(2000.0, budget("under 2K"), 0);
        assertEquals(3000.0, budget("hanggang 3000"), 0);
        assertEquals(2500.0, budget("dili molapas sa 2,500"), 0);
        assertEquals(2500.0, budget("my budget is 2.5k"), 0);
        assertEquals(1800.0, budget("₱1800 lang"), 0);
        assertEquals(2000.0, budget("2000 pesos"), 0);
        assertEquals(3000.0, budget("below 3,000"), 0);
        assertEquals(2000.0, budget("I'm a girl on a 2k budget, I need wifi"), 0);
    }

    @Test public void numbersThatAreNotPricesAreIgnored() {
        assertNull(budget("within 500 meters of the campus"));
        assertNull(budget("under 10 minutes walk"));
        assertNull(budget("for 2 people"));
        assertNull(budget("nearest boarding house"));
        assertNull(budget(""));
        assertNull(budget(null));
    }

    @Test public void needsAreFound() {
        BofoMemory.Prefs p = BofoMemory.parse("may wifi at libre tubig");
        assertTrue(p.wifi()); assertTrue(p.water()); assertFalse(p.electricity());
        assertTrue(BofoMemory.parse("libre kuryente").electricity());
        assertTrue(BofoMemory.parse("free internet please").wifi());
        BofoMemory.Prefs none = BofoMemory.parse("show on the map");
        assertFalse(none.wifi()); assertFalse(none.water()); assertFalse(none.electricity());
    }

    @Test public void languageIsGuessedFromMarkerWords() {
        assertEquals("tl", BofoMemory.parse("pinakamura nga malapit").lang());
        assertEquals("ceb", BofoMemory.parse("unsa ang pinakabarato duol sa school").lang());
        assertEquals("en", BofoMemory.parse("cheapest with wifi near the campus").lang());
        assertEquals("taglish", BofoMemory.parse("cheapest nga malapit pls").lang());
        assertNull(BofoMemory.parse("ok").lang());                      // too short to tell: never flips the language
    }

    @Test public void chatPreferencesAreRememberedAndSentAsSession() throws Exception {
        BofoMemory.clear();
        BofoMemory.observe("I'm a girl on a 2k budget, I need wifi");
        assertEquals(2000.0, BofoMemory.budget(), 0);
        assertTrue(BofoMemory.wifi());
        JSONObject f = BofoMemory.toSession().getJSONObject("filters_in_effect");
        assertEquals(2000, f.getInt("max_price"));
        assertTrue(f.getBoolean("free_wifi"));
        assertFalse(f.has("free_water"));
        BofoMemory.observe("what about closer to the campus?");           // says nothing new: nothing is lost
        assertEquals(2000.0, BofoMemory.budget(), 0);
        BofoMemory.observe("make it under 3k");                           // a newer price replaces the old one
        assertEquals(3000.0, BofoMemory.budget(), 0);
        BofoMemory.observe("any budget is fine");
        assertNull(BofoMemory.budget());
        BofoMemory.clear();
    }

    @Test public void sessionMergesAppFiltersWithTheChat() throws Exception {
        BofoMemory.clear();
        JSONObject only = BofoMemory.mergeSession(3000.0, false, true, false).getJSONObject("filters_in_effect");
        assertEquals(3000, only.getInt("max_price"));
        assertTrue(only.getBoolean("free_water"));
        BofoMemory.observe("under 2k with wifi");
        JSONObject both = BofoMemory.mergeSession(3000.0, false, true, false).getJSONObject("filters_in_effect");
        assertEquals(2000, both.getInt("max_price"));                     // what the student said wins
        assertTrue(both.getBoolean("free_wifi"));
        assertTrue(both.getBoolean("free_water"));
        BofoMemory.clear();
        assertEquals(0, BofoMemory.mergeSession(null, false, false, false).getJSONObject("filters_in_effect").length());
    }

    @Test public void lastChatIsSavedOnThePhoneAndWipedByClear() throws Exception {
        File dir = Files.createTempDirectory("bofo-mem").toFile();
        DiskCache.setBase(dir);
        BofoMemory.clear();
        BofoMemory.observe("under 2k with wifi");
        BofoMemory.append(true, "under 2k with wifi", null);
        BofoMemory.append(false, "Here is what I found", List.of("b1", "b2"));
        List<BofoAiService.Turn> h = BofoMemory.loadHistory();
        assertEquals(2, h.size());
        assertTrue(h.get(0).mine());
        assertEquals(List.of("b1", "b2"), h.get(1).resultIds());
        String raw = DiskCache.read(BofoMemory.FILE);
        assertNotNull(raw);
        assertTrue(raw.contains("Here is what I found"));
        assertTrue(BofoMemory.FILE.startsWith("user_"));                  // sign-out wipes user_* files too
        DiskCache.deletePrefix("user_");
        assertNull(DiskCache.read(BofoMemory.FILE));
        BofoMemory.append(true, "again", null);
        BofoMemory.clear();
        assertTrue(BofoMemory.loadHistory().isEmpty());
        assertNull(DiskCache.read(BofoMemory.FILE));
        assertNull(BofoMemory.budget());
    }

    @Test public void historyIsCappedAndTextIsClipped() {
        BofoMemory.clear();
        for (int i = 0; i < 30; i++) BofoMemory.append(i % 2 == 0, "turn " + i + " " + "x".repeat(700), null);
        List<BofoAiService.Turn> h = BofoMemory.loadHistory();
        assertEquals(BofoMemory.MAX_TURNS, h.size());
        assertTrue(h.get(0).text().startsWith("turn 10 "));
        assertEquals(BofoMemory.MAX_TEXT, h.get(0).text().length());
        assertEquals("ab", BofoMemory.clip("abcdef", 2));
        BofoMemory.clear();
    }
}
