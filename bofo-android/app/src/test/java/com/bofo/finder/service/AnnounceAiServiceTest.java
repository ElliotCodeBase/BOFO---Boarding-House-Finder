package com.bofo.finder.service;

import static org.junit.Assert.*;

import com.bofo.finder.model.AnnounceError;
import com.bofo.finder.model.AnnounceType;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.AnnounceRules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.json.JSONObject;
import org.junit.Test;

/** v2.4.1 AI Announcement Writer step 4: the service with a fake transport (no network) and a runner we control. */
public class AnnounceAiServiceTest {
    /** Runs the work at once on the calling thread (the main thread is not needed here). */
    static final AnnounceAiService.Runner NOW = new AnnounceAiService.Runner() {
        @Override public <T> void run(Async.Work<T> work, Consumer<T> ok, Consumer<Throwable> fail) {
            T r; try { r = work.run(); } catch (Throwable e) { fail.accept(e); return; } ok.accept(r);
        }
    };
    /** Keeps the delivery so the test can run it later (= "the answer comes late"). */
    static final class Deferred implements AnnounceAiService.Runner {
        final List<Runnable> queue = new ArrayList<>();
        @Override public <T> void run(Async.Work<T> work, Consumer<T> ok, Consumer<Throwable> fail) {
            queue.add(() -> { T r; try { r = work.run(); } catch (Throwable e) { fail.accept(e); return; } ok.accept(r); });
        }
        void flush() { List<Runnable> q = new ArrayList<>(queue); queue.clear(); for (Runnable r : q) r.run(); }
    }
    static final class Fake implements AnnounceAiService.Transport {
        final List<JSONObject> bodies = new ArrayList<>();
        Object answer = "{\"title\":\"Rent reminder\",\"message\":\"Rent is due Nov 5.\",\"degraded\":false}";
        @Override public JSONObject post(JSONObject body) throws Exception {
            bodies.add(body);
            if (answer instanceof Exception) throw (Exception) answer;
            if (answer instanceof RuntimeException) throw (RuntimeException) answer;
            return new JSONObject((String) answer);
        }
    }
    static final class Got { final List<AnnounceAiService.Result> all = new ArrayList<>(); AnnounceAiService.Result last() { return all.get(all.size() - 1); } }

    private static Map<String, String> map(String... kv) { Map<String, String> m = new LinkedHashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]); return m; }
    private static AnnounceType t(String id) { return AnnounceType.byId(id); }
    private static final Map<String, String> RENT = map("due_date", "Nov 5", "amount", "P2,500", "how_to_pay", "GCash 0917 000 0000");

    private static AnnounceAiService.Call rent(AnnounceAiService s, Map<String, String> fields, Got got) {
        return s.generate(t("rent_due"), AnnounceType.Lang.EN, AnnounceType.Tone.FIRM, AnnounceType.Length.NORMAL, false, true, "Mrs. Reyes", fields, null, got.all::add);
    }

    @Test public void validDetailsSendExactlyTheBackendKeysOnceAndReturnTheDraft() throws Exception {
        Fake net = new Fake(); Got got = new Got();
        rent(new AnnounceAiService(net, NOW), RENT, got);
        assertEquals(1, net.bodies.size());
        JSONObject b = net.bodies.get(0);
        Set<String> keys = new HashSet<>(); for (Iterator<String> it = b.keys(); it.hasNext(); ) keys.add(it.next());
        assertEquals(new HashSet<>(AnnounceRules.REQUEST_KEYS).size() - 1, keys.size());                 // all keys but the optional "adjust"
        assertTrue(AnnounceRules.REQUEST_KEYS.containsAll(keys)); assertFalse(keys.contains("adjust"));
        assertEquals("rent_due", b.getString("type")); assertEquals("en", b.getString("lang")); assertEquals("firm", b.getString("tone"));
        assertEquals("Nov 5", b.getJSONObject("fields").getString("due_date"));
        assertEquals("Mrs. Reyes", b.getJSONObject("closing").getString("name"));
        assertEquals(1, got.all.size()); assertTrue(got.last().ok());
        assertEquals("Rent reminder", got.last().draft.title); assertFalse(got.last().draft.degraded); assertNull(got.last().error);
    }

    @Test public void adjustIsSentWhenGiven() throws Exception {
        Fake net = new Fake(); Got got = new Got();
        new AnnounceAiService(net, NOW).generate(t("rent_due"), null, null, null, true, false, null, RENT, AnnounceType.Adjust.SHORTER, got.all::add);
        assertEquals("shorter", net.bodies.get(0).getString("adjust"));
        assertTrue(net.bodies.get(0).getBoolean("emoji"));
    }

    @Test public void degradedDraftIsASuccess() throws Exception {
        Fake net = new Fake(); net.answer = "{\"title\":\"T\",\"message\":\"M\",\"notes\":\"Add the amount\",\"degraded\":true}"; Got got = new Got();
        rent(new AnnounceAiService(net, NOW), RENT, got);
        assertTrue(got.last().ok()); assertTrue(got.last().draft.degraded); assertEquals("Add the amount", got.last().draft.notes);
    }

    @Test public void invalidDetailsNeverReachTheNetwork() throws Exception {
        Fake net = new Fake(); Got got = new Got(); AnnounceAiService s = new AnnounceAiService(net, NOW);
        rent(s, map("due_date", "Nov 5"), got);                                                          // amount + how_to_pay missing
        assertEquals(AnnounceError.INVALID_DETAILS, got.last().error);
        assertEquals(AnnounceRules.Problem.MISSING, got.last().problem.code); assertEquals("amount", got.last().field);
        rent(s, map("due_date", "  ", "amount", "1", "how_to_pay", "x"), got);                           // blank = missing
        assertEquals("due_date", got.last().problem.field);
        rent(s, map("due_date", "Nov 5", "amount", "1", "how_to_pay", "x", "bogus", "y"), got);          // unknown key
        assertEquals(AnnounceRules.Problem.UNKNOWN, got.last().problem.code); assertEquals("bogus", got.last().field);
        StringBuilder big = new StringBuilder(); for (int i = 0; i < 201; i++) big.append('x');
        rent(s, map("due_date", big.toString(), "amount", "1", "how_to_pay", "x"), got);
        assertEquals(AnnounceRules.Problem.TOO_LONG, got.last().problem.code);
        s.generate(t("gate_lock_change"), null, null, null, false, false, null, new HashMap<>(), null, got.all::add);   // oneOf not met
        assertEquals(AnnounceRules.Problem.ONE_OF, got.last().problem.code);
        rent(s, null, got);                                                                              // null map
        assertEquals(AnnounceError.INVALID_DETAILS, got.last().error);
        s.generate(null, null, null, null, false, false, null, RENT, null, got.all::add);                // no type
        assertEquals(AnnounceError.GENERIC, got.last().error);
        assertEquals(7, got.all.size());
        assertEquals("no request may be sent for invalid details", 0, net.bodies.size());
        assertFalse(got.last().ok()); assertNull(got.last().draft);
    }

    @Test public void invalidDetailsAreReportedAtOnceEvenWithAParkedRunner() throws Exception {
        Deferred d = new Deferred(); Fake net = new Fake(); Got got = new Got();
        rent(new AnnounceAiService(net, d), map(), got);
        assertEquals(1, got.all.size()); assertEquals(0, d.queue.size()); assertEquals(0, net.bodies.size());
    }

    @Test public void everyFailureBecomesATypedResultWithoutRetrying() throws Exception {
        Object[][] rows = {
                {new SupabaseClient.ApiException(401, "{\"error\":\"unauthorized\"}"), AnnounceError.SIGNED_OUT},
                {new SupabaseClient.ApiException(403, "{\"error\":\"not_owner\"}"), AnnounceError.NOT_OWNER},
                {new SupabaseClient.ApiException(403, "{\"error\":\"ai_disabled\"}"), AnnounceError.AI_DISABLED},
                {new SupabaseClient.ApiException(429, "{\"error\":\"rate_limited\",\"retry_after\":17}"), AnnounceError.RATE_LIMITED},
                {new SupabaseClient.ApiException(400, "{\"error\":\"missing_field\",\"field\":\"amount\"}"), AnnounceError.INVALID},
                {new SupabaseClient.ApiException(502, "{\"error\":\"generation_failed\"}"), AnnounceError.SERVER},
                {new SupabaseClient.ApiException(500, ""), AnnounceError.SERVER},
                {new SupabaseClient.ApiException(405, ""), AnnounceError.SERVER},
                {new SupabaseClient.ApiException(404, ""), AnnounceError.GENERIC},
                {new IOException("no route"), AnnounceError.OFFLINE},
                {new IllegalStateException("boom"), AnnounceError.GENERIC},
        };
        for (Object[] row : rows) {
            Fake net = new Fake(); net.answer = row[0]; Got got = new Got();
            rent(new AnnounceAiService(net, NOW), RENT, got);
            assertEquals(row[0].toString(), row[1], got.last().error);
            assertEquals("exactly one request, no retry", 1, net.bodies.size());
            assertEquals(1, got.all.size()); assertNull(got.last().draft);
        }
        Fake net = new Fake(); net.answer = new SupabaseClient.ApiException(429, "{\"retry_after\":17}"); Got got = new Got();
        rent(new AnnounceAiService(net, NOW), RENT, got);
        assertEquals(17, got.last().retryAfterSeconds);
        net.answer = new SupabaseClient.ApiException(400, "{\"field\":\"amount\"}");
        rent(new AnnounceAiService(net, NOW), RENT, got);
        assertEquals("amount", got.last().field);
    }

    @Test public void aBodyWithoutTitleOrMessageIsAGenericError() throws Exception {
        for (String body : new String[]{"{}", "{\"title\":\"T\"}", "{\"message\":\"M\"}", "{\"title\":3,\"message\":\"M\"}"}) {
            Fake net = new Fake(); net.answer = body; Got got = new Got();
            rent(new AnnounceAiService(net, NOW), RENT, got);
            assertEquals(body, AnnounceError.GENERIC, got.last().error);
        }
    }

    @Test public void aCancelledCallNeverCallsBack() throws Exception {
        Deferred d = new Deferred(); Fake net = new Fake(); Got got = new Got();
        AnnounceAiService s = new AnnounceAiService(net, d);
        AnnounceAiService.Call c = rent(s, RENT, got);
        c.cancel(); assertTrue(c.isCancelled());
        d.flush();
        assertEquals(0, got.all.size());
    }

    @Test public void cancelAllDropsLateResultsOfAnyRequest() throws Exception {
        Deferred d = new Deferred(); Got got = new Got();
        AnnounceAiService s = new AnnounceAiService(new Fake(), d);
        rent(s, RENT, got); s.cancelAll();
        d.flush();
        assertEquals(0, got.all.size());
        rent(s, RENT, got); d.flush();                                       // a request started after cancelAll works again
        assertEquals(1, got.all.size()); assertTrue(got.last().ok());
    }

    @Test public void aNewerRequestSupersedesAnOlderOne() throws Exception {
        Deferred d = new Deferred(); Got got = new Got();
        AnnounceAiService s = new AnnounceAiService(new Fake(), d);
        rent(s, RENT, got); rent(s, RENT, got);
        d.flush();
        assertEquals("only the latest answer is delivered", 1, got.all.size());
    }

    @Test public void lateFailuresAreDroppedToo() throws Exception {
        Deferred d = new Deferred(); Fake net = new Fake(); net.answer = new IOException("x"); Got got = new Got();
        AnnounceAiService s = new AnnounceAiService(net, d);
        rent(s, RENT, got); s.cancelAll(); d.flush();
        assertEquals(0, got.all.size());
    }

    @Test public void nullCallbackIsHarmless() throws Exception {
        Fake net = new Fake();
        new AnnounceAiService(net, NOW).generate(t("rent_due"), null, null, null, false, false, null, RENT, null, null);
        new AnnounceAiService(net, NOW).generate(t("rent_due"), null, null, null, false, false, null, map(), null, null);
        assertEquals(1, net.bodies.size());
    }

    @Test public void theDefaultServiceCanBeBuilt() {
        // v2.11.1: the old version waited for a callback on the Android main thread (Async.post uses a Looper), which a plain JVM test does not have,
        // so it always timed out. The real network wiring is checked on a device; here we only prove the default constructor does not crash.
        assertNotNull(new AnnounceAiService());
    }
}
