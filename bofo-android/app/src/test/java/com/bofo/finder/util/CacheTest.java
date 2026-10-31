package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class CacheTest {
    @Test public void freshValueIsServedWithoutReloading() throws Exception {
        Cache.clear();
        AtomicInteger calls = new AtomicInteger();
        assertEquals("a", Cache.get("k1", 60_000, () -> { calls.incrementAndGet(); return "a"; }));
        assertEquals("a", Cache.get("k1", 60_000, () -> { calls.incrementAndGet(); return "b"; }));
        assertEquals(1, calls.get());
        assertFalse(Cache.servedStale);
    }

    @Test public void staleValueIsServedWhenTheNetworkFails() throws Exception {
        Cache.clear();
        Cache.get("k2", 60_000, () -> "old");
        String v = Cache.get("k2", 0, () -> { throw new java.io.IOException("offline"); });   // ttl 0 = expired, reload fails
        assertEquals("old", v);
        assertTrue(Cache.servedStale);
    }

    @Test(expected = java.io.IOException.class)
    public void failsWhenNothingWasCached() throws Exception {
        Cache.clear();
        Cache.get("k3", 60_000, () -> { throw new java.io.IOException("offline"); });
    }

    @Test public void removeDropsByPrefix() throws Exception {
        Cache.clear();
        Cache.get("list:a", 60_000, () -> 1);
        Cache.get("other", 60_000, () -> 2);
        Cache.remove("list:");
        assertEquals(Integer.valueOf(9), Cache.get("list:a", 60_000, () -> 9));
        assertEquals(Integer.valueOf(2), Cache.get("other", 60_000, () -> 7));
    }
}
