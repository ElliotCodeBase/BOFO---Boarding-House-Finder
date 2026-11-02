package com.bofo.finder.service;

import static org.junit.Assert.*;

import com.bofo.finder.util.BofoMemory;
import com.bofo.finder.util.BrowseState;

import org.json.JSONObject;
import org.junit.Test;

/** v2.1.2: the request field "session" (static helper only: no network, no client). */
public class BofoAiServiceTest {
    @Test public void sessionHasWhatTheStudentSaidAndTheAppFilters() throws Exception {
        BofoMemory.clear();
        BrowseState.maxPrice = 4000.0;
        try {
            JSONObject f = BofoAiService.session("cheapest under 2k with wifi").getJSONObject("filters_in_effect");
            assertEquals(2000, f.getInt("max_price"));
            assertTrue(f.getBoolean("free_wifi"));
            BrowseState.maxPrice = (double) BrowseState.PRICE_MAX;           // slider at the end = no limit
            BofoMemory.clear();
            assertFalse(BofoAiService.session("show them").getJSONObject("filters_in_effect").has("max_price"));
        } finally { BrowseState.maxPrice = null; BofoMemory.clear(); }
    }
}
