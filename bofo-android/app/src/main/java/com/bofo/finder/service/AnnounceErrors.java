package com.bofo.finder.service;

import com.bofo.finder.model.AnnounceError;
import com.bofo.finder.util.Connectivity;

import org.json.JSONObject;

/**
 * v2.4.1 (AI Announcement Writer, step 4): the ONE place that turns an Edge Function failure into an {@link AnnounceError}. Pure (no Android,
 * no network, no logging). Status contract = section F of BOFO-AI-ANNOUNCE-STATUS.md:
 * 401 -> SIGNED_OUT, 403 not_owner / ai_disabled, 429 -> RATE_LIMITED (+ retry_after), 400 -> INVALID (+ field), 405 / >= 500 -> SERVER,
 * network failure -> OFFLINE, anything else -> GENERIC.
 */
public final class AnnounceErrors {
    private AnnounceErrors() {}

    /** Used when the server sends no usable retry_after. */
    public static final int DEFAULT_RETRY_AFTER = 30;
    /** A retry_after above this is capped (the daily limit can be hours away: the UI only shows "try again later"). */
    public static final int MAX_RETRY_AFTER = 3600;

    /** The mapped failure: the error, the seconds to wait (only RATE_LIMITED, else 0) and the server's field key (only INVALID, else null). */
    public static final class Failure {
        public final AnnounceError error;
        public final int retryAfterSeconds;
        public final String field;
        public Failure(AnnounceError error, int retryAfterSeconds, String field) {
            this.error = error; this.retryAfterSeconds = retryAfterSeconds; this.field = field;
        }
    }

    /** Any exception from the service call. Never throws. */
    public static Failure from(Throwable t) {
        if (t instanceof SupabaseClient.ApiException) {
            SupabaseClient.ApiException e = (SupabaseClient.ApiException) t;
            return from(e.status, e.body);
        }
        if (Connectivity.isNetworkError(t)) return new Failure(AnnounceError.OFFLINE, 0, null);
        return new Failure(AnnounceError.GENERIC, 0, null);
    }

    /** An HTTP failure: status + raw body (may be empty / not JSON). Never throws. */
    public static Failure from(int status, String body) {
        JSONObject j = parse(body);
        switch (status) {
            case 401: return new Failure(AnnounceError.SIGNED_OUT, 0, null);
            case 403: {
                String code = str(j, "error");
                if ("not_owner".equals(code)) return new Failure(AnnounceError.NOT_OWNER, 0, null);
                if ("ai_disabled".equals(code)) return new Failure(AnnounceError.AI_DISABLED, 0, null);
                return new Failure(AnnounceError.GENERIC, 0, null);
            }
            case 429: return new Failure(AnnounceError.RATE_LIMITED, retryAfter(j), null);
            case 400: {
                String f = str(j, "field");
                return new Failure(AnnounceError.INVALID, 0, f.isEmpty() ? null : f);
            }
            case 405: return new Failure(AnnounceError.SERVER, 0, null);
            default:
                return new Failure(status >= 500 ? AnnounceError.SERVER : AnnounceError.GENERIC, 0, null);
        }
    }

    /** retry_after as whole seconds: a positive number (decimals round up) or a numeric string; absent / bad / <= 0 -> 30; capped at 3600. */
    static int retryAfter(JSONObject j) {
        if (j == null) return DEFAULT_RETRY_AFTER;
        Object v = j.opt("retry_after");
        double d = Double.NaN;
        if (v instanceof Number) d = ((Number) v).doubleValue();
        else if (v instanceof String) { try { d = Double.parseDouble(((String) v).trim()); } catch (NumberFormatException ignored) { } }
        if (Double.isNaN(d) || Double.isInfinite(d) || d <= 0) return DEFAULT_RETRY_AFTER;
        return (int) Math.min(MAX_RETRY_AFTER, Math.ceil(d));
    }

    private static JSONObject parse(String body) {
        if (body == null || body.isEmpty()) return null;
        try { return new JSONObject(body); } catch (Throwable notJson) { return null; }
    }

    private static String str(JSONObject j, String key) {
        if (j == null) return "";
        Object v = j.opt(key);
        return v instanceof String ? ((String) v).trim() : "";
    }
}
