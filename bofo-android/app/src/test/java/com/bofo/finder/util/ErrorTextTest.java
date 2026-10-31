package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bofo.finder.service.BhService;
import com.bofo.finder.service.SupabaseClient;

import java.io.IOException;
import java.net.UnknownHostException;

public class ErrorTextTest {
    @Test public void tableMissingGetsMigrationHint() {
        String t = ErrorText.full("Could not load", new SupabaseClient.ApiException(404,
                "{\"code\":\"PGRST205\",\"message\":\"Could not find the table\",\"hint\":\"run it\"}"));
        assertTrue(t, t.startsWith("Could not load."));
        assertTrue(t, t.contains("HTTP 404"));
        assertTrue(t, t.contains("(code PGRST205)"));
        assertTrue(t, t.contains("Message: Could not find the table"));
        assertTrue(t, t.contains("Hint: run it"));
        assertTrue(t, t.contains("migration probably wasn't run"));
    }

    @Test public void expiredLoginIsExplained() {
        String t = ErrorText.full("Failed", new SupabaseClient.ApiException(401, "{\"message\":\"JWT expired\"}"));
        assertTrue(t, t.contains("sign-in expired"));
    }

    @Test public void rowLevelSecurityIsExplained() {
        String t = ErrorText.full("Failed", new SupabaseClient.ApiException(403,
                "{\"code\":\"42501\",\"message\":\"new row violates row-level security policy\"}"));
        assertTrue(t, t.contains("row-level security"));
    }

    @Test public void emptyAndNonJsonBodiesAreShownPlainly() {
        assertTrue(ErrorText.full("Failed", new SupabaseClient.ApiException(502, "")).contains("(empty response)"));
        assertTrue(ErrorText.full("Failed", new SupabaseClient.ApiException(500, "<html>Bad gateway</html>")).contains("Response: <html>Bad gateway</html>"));
    }

    @Test public void networkFailureSaysNoConnection() {
        String t = ErrorText.full("Could not load", new IOException("wrapped", new UnknownHostException("x.supabase.co")));
        assertTrue(t, t.contains("No connection to the server"));
        assertTrue(t, t.contains("UnknownHostException"));
    }

    @Test public void otherErrorsShowTheirType() {
        String t = ErrorText.full("Oops", new IllegalStateException("boom"));
        assertTrue(t, t.contains("IllegalStateException: boom"));
    }

    @Test public void publishStepIsNamed() {
        String t = ErrorText.full("Could not publish", new BhService.PublishException("uploading photo 2",
                new SupabaseClient.ApiException(413, "too large")));
        assertTrue(t, t.contains("Could not publish while uploading photo 2."));
        assertTrue(t, t.contains("5 MB"));
    }
}
