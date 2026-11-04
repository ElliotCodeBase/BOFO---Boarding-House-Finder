package com.bofo.finder.service;

import static org.junit.Assert.*;

import com.bofo.finder.model.AnnounceError;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assume;
import org.junit.Test;

/** v2.4.1 AI Announcement Writer step 4: every row of the error mapping (pure class, no network). */
public class AnnounceErrorsTest {
    private static AnnounceErrors.Failure f(int status, String body) { return AnnounceErrors.from(status, body); }
    private static void is(AnnounceError e, int status, String body) { assertEquals(status + " " + body, e, f(status, body).error); }

    @Test public void status401IsSignedOutWhateverTheBody() {
        is(AnnounceError.SIGNED_OUT, 401, "{\"error\":\"unauthorized\"}");
        is(AnnounceError.SIGNED_OUT, 401, "");
        is(AnnounceError.SIGNED_OUT, 401, "<html>");
    }

    @Test public void status403LooksAtTheErrorCode() {
        is(AnnounceError.NOT_OWNER, 403, "{\"error\":\"not_owner\"}");
        is(AnnounceError.AI_DISABLED, 403, "{\"error\":\"ai_disabled\"}");
        is(AnnounceError.NOT_OWNER, 403, "{\"error\":\" not_owner \",\"message\":\"x\"}");
        is(AnnounceError.GENERIC, 403, "{\"error\":\"something_else\"}");
        is(AnnounceError.GENERIC, 403, "");
        is(AnnounceError.GENERIC, 403, "Forbidden");
        is(AnnounceError.GENERIC, 403, "{\"error\":7}");
        is(AnnounceError.GENERIC, 403, null);
    }

    @Test public void status429CarriesRetryAfterSeconds() {
        AnnounceErrors.Failure a = f(429, "{\"error\":\"rate_limited\",\"retry_after\":12}");
        assertEquals(AnnounceError.RATE_LIMITED, a.error); assertEquals(12, a.retryAfterSeconds);
        assertEquals(13, f(429, "{\"retry_after\":12.2}").retryAfterSeconds);          // decimals round up
        assertEquals(45, f(429, "{\"retry_after\":\"45\"}").retryAfterSeconds);        // numeric string
        assertEquals(1, f(429, "{\"retry_after\":1}").retryAfterSeconds);
        assertEquals(3600, f(429, "{\"retry_after\":86400}").retryAfterSeconds);       // daily limit: capped
    }

    @Test public void retryAfterDefaultsTo30WhenAbsentOrBad() {
        for (String body : new String[]{"{\"error\":\"rate_limited\"}", "", "not json", "{}", "{\"retry_after\":0}", "{\"retry_after\":-5}",
                "{\"retry_after\":\"abc\"}", "{\"retry_after\":null}", "{\"retry_after\":true}", "{\"retry_after\":\"\"}", "{\"retry_after\":[3]}"}) {
            AnnounceErrors.Failure x = f(429, body);
            assertEquals(body, AnnounceError.RATE_LIMITED, x.error); assertEquals(body, 30, x.retryAfterSeconds);
        }
        assertEquals(30, f(429, null).retryAfterSeconds);
    }

    @Test public void status400IsInvalidAndKeepsTheServerField() {
        AnnounceErrors.Failure a = f(400, "{\"error\":\"missing_field\",\"message\":\"x\",\"field\":\"due_date\"}");
        assertEquals(AnnounceError.INVALID, a.error); assertEquals("due_date", a.field);
        assertNull(f(400, "{\"error\":\"bad_tone\",\"message\":\"x\"}").field);
        assertNull(f(400, "{\"field\":\"\"}").field);
        assertNull(f(400, "{\"field\":3}").field);
        assertEquals(AnnounceError.INVALID, f(400, "garbage").error); assertNull(f(400, "garbage").field);
        assertEquals(AnnounceError.INVALID, f(400, null).error);
    }

    @Test public void serverErrors() {
        for (int s : new int[]{405, 500, 502, 503, 504, 599}) is(AnnounceError.SERVER, s, "{\"error\":\"server_error\"}");
        is(AnnounceError.SERVER, 502, "");
    }

    @Test public void everythingElseIsGeneric() {
        for (int s : new int[]{300, 402, 404, 408, 409, 413, 422, 499}) is(AnnounceError.GENERIC, s, "{\"error\":\"x\"}");
    }

    @Test public void onlyRateLimitedHasSecondsOnlyInvalidHasAField() {
        assertEquals(0, f(500, "{\"retry_after\":9,\"field\":\"x\"}").retryAfterSeconds);
        assertNull(f(500, "{\"retry_after\":9,\"field\":\"x\"}").field);
        assertNull(f(429, "{\"field\":\"x\"}").field);
    }

    @Test public void exceptionsAreMapped() {
        assertEquals(AnnounceError.RATE_LIMITED, AnnounceErrors.from(new SupabaseClient.ApiException(429, "{\"retry_after\":7}")).error);
        assertEquals(7, AnnounceErrors.from(new SupabaseClient.ApiException(429, "{\"retry_after\":7}")).retryAfterSeconds);
        assertEquals(AnnounceError.NOT_OWNER, AnnounceErrors.from(new SupabaseClient.ApiException(403, "{\"error\":\"not_owner\"}")).error);
        assertEquals(AnnounceError.SIGNED_OUT, AnnounceErrors.from(new SupabaseClient.ApiException(401, null)).error);
        assertEquals(AnnounceError.OFFLINE, AnnounceErrors.from(new IOException("x")).error);
        assertEquals(AnnounceError.OFFLINE, AnnounceErrors.from(new UnknownHostException("host")).error);
        assertEquals(AnnounceError.OFFLINE, AnnounceErrors.from(new SocketTimeoutException("t")).error);
        assertEquals(AnnounceError.OFFLINE, AnnounceErrors.from(new RuntimeException("wrapped", new IOException("io"))).error);
        assertEquals(AnnounceError.GENERIC, AnnounceErrors.from(new IllegalStateException("x")).error);
        assertEquals(AnnounceError.GENERIC, AnnounceErrors.from(new Exception()).error);
        assertEquals(AnnounceError.GENERIC, AnnounceErrors.from(null).error);
    }

    @Test public void everyErrorPointsAtAnExistingString() throws Exception {
        File xml = null;
        for (String p : new String[]{"src/main/res/values/strings_announce_ai.xml", "bofo-android/app/src/main/res/values/strings_announce_ai.xml"})
            if (new File(p).exists()) xml = new File(p);
        Assume.assumeTrue("strings_announce_ai.xml not found (run from the app folder or the repo)", xml != null);
        String s = new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8);
        for (AnnounceError e : AnnounceError.values())
            assertTrue(e + " -> " + e.stringName(), s.contains("<string name=\"" + e.stringName() + "\">"));
        assertEquals("announce_err_not_owner", AnnounceError.NOT_OWNER.stringName());
        assertEquals("announce_err_rate_limited", AnnounceError.RATE_LIMITED.stringName());
        assertTrue(AnnounceError.RATE_LIMITED.canRetry()); assertFalse(AnnounceError.NOT_OWNER.canRetry()); assertFalse(AnnounceError.AI_DISABLED.canRetry());
    }
}
