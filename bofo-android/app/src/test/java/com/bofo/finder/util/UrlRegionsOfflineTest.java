package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.json.JSONArray;

public class UrlRegionsOfflineTest {
    @Test public void urlEncodeUsesPercent20ForSpaces() {
        assertEquals("a%20b%26c", Url.encode("a b&c"));
        assertEquals("%C3%A9", Url.encode("\u00E9"));
        assertEquals("2026-01-01T10%3A00%3A00%2B00%3A00", Url.encode("2026-01-01T10:00:00+00:00"));
    }

    @Test public void regionsLookUpByKeyAndName() {
        assertEquals(18, Regions.ALL.size());
        assertEquals("Negros Island Region (NIR)", Regions.byKey("NIR").name());
        assertEquals("VII", Regions.byName("Region VII - Central Visayas").key());
        assertNull(Regions.byKey("nope"));
        assertNull(Regions.byKey(" "));
        assertNull(Regions.byName(null));
    }

    @Test public void networkErrorsAreRecognisedThroughCauses() {
        assertTrue(Connectivity.isNetworkError(new IOException("x")));
        assertTrue(Connectivity.isNetworkError(new RuntimeException(new java.net.SocketTimeoutException())));
        assertFalse(Connectivity.isNetworkError(new IllegalArgumentException("bad")));
        assertFalse(Connectivity.isNetworkError(null));
    }

    @Test public void offlineServesTheSavedCopyOnNetworkErrorOnly() throws Exception {
        File dir = Files.createTempDirectory("bofo-test").toFile();
        DiskCache.setBase(dir);
        JSONArray fresh = new JSONArray().put("a").put("b");
        assertEquals(2, Offline.array("test_list", () -> fresh).length());          // online: saved + returned
        JSONArray saved = Offline.array("test_list", () -> { throw new IOException("no network"); });
        assertEquals("b", saved.getString(1));                                       // offline: the saved copy
        try {
            Offline.array("test_list", () -> { throw new IllegalStateException("server bug"); });
            fail("a non-network error must not be hidden by the saved copy");
        } catch (IllegalStateException expected) { /* ok */ }
        try {
            Offline.array("never_saved", () -> { throw new IOException("no network"); });
            fail("nothing saved: the error must reach the screen");
        } catch (IOException expected) { /* ok */ }
    }

    @Test public void diskCacheWritesReadsAndTrims() throws Exception {
        DiskCache.setBase(Files.createTempDirectory("bofo-test2").toFile());
        assertNull(DiskCache.read("x"));
        assertEquals(Long.MAX_VALUE, DiskCache.ageMs("x"));
        DiskCache.write("x", "h\u00E9llo");
        assertEquals("h\u00E9llo", DiskCache.read("x"));
        assertTrue(DiskCache.ageMs("x") < 5000);
        for (int i = 0; i < 5; i++) { DiskCache.write("item_" + i, "v"); Thread.sleep(15); }
        DiskCache.trim("item_", 2);
        int left = 0;
        for (int i = 0; i < 5; i++) if (DiskCache.read("item_" + i) != null) left++;
        assertEquals(2, left);
        DiskCache.deletePrefix("item_");
        assertNull(DiskCache.read("item_4"));
        assertEquals("h\u00E9llo", DiskCache.read("x"));
    }
}
