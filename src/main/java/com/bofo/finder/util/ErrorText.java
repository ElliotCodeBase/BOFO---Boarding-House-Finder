package com.bofo.finder.util;

import com.bofo.finder.service.BhService;
import com.bofo.finder.service.SupabaseClient;
import org.json.JSONObject;

/** Turns any failure into the FULL, readable story: what step failed, the server's own message, code, details and hint. */
public final class ErrorText {
    private ErrorText() {}

    public static String full(String headline, Throwable e) {
        String step = null;
        SupabaseClient.ApiException api = null;
        Throwable root = e;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof BhService.PublishException pe && step == null) step = pe.step;
            if (t instanceof SupabaseClient.ApiException ae && api == null) api = ae;
            root = t;
            if (t.getCause() == t) break;
        }
        StringBuilder sb = new StringBuilder(headline);
        if (step != null) sb.append(" while ").append(step);
        sb.append('.');

        if (api != null) {
            sb.append("\n\nServer replied: HTTP ").append(api.status);
            JSONObject j = parse(api.body);
            String code = j == null ? "" : first(j, "code", "statusCode", "error_code");
            if (!code.isEmpty()) sb.append("  (code ").append(code).append(')');
            if (j != null) {
                line(sb, "Message", first(j, "message", "msg", "error_description", "error"));
                line(sb, "Details", first(j, "details"));
                line(sb, "Hint", first(j, "hint"));
                String err = first(j, "error");
                if (!err.isEmpty() && !err.equals(first(j, "message", "msg", "error_description"))) line(sb, "Error", err);
            } else {
                String raw = api.body.isBlank() ? "(empty response)" : api.body.strip();
                line(sb, "Response", raw.length() > 1500 ? raw.substring(0, 1500) + "…" : raw);
            }
            String why = explain(api.status, code, api.body);
            if (!why.isEmpty()) sb.append("\n\nLikely cause: ").append(why);
        } else if (Connectivity.isNetworkError(e)) {
            sb.append("\n\nNo connection to the server (").append(root.getClass().getSimpleName());
            if (root.getMessage() != null) sb.append(": ").append(root.getMessage());
            sb.append("). Check the internet and try again.");
        } else {
            sb.append("\n\n").append(root.getClass().getSimpleName());
            if (root.getMessage() != null) sb.append(": ").append(root.getMessage());
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, String label, String v) {
        if (v != null && !v.isBlank()) sb.append('\n').append(label).append(": ").append(v.strip());
    }

    private static JSONObject parse(String body) {
        if (body == null) return null;
        String b = body.strip();
        if (!b.startsWith("{")) return null;
        try { return new JSONObject(b); } catch (Exception ex) { return null; }
    }

    private static String first(JSONObject j, String... keys) {
        for (String k : keys) {
            if (j.has(k) && !j.isNull(k)) { String v = String.valueOf(j.get(k)); if (!v.isBlank()) return v; }
        }
        return "";
    }

    private static String explain(int status, String code, String body) {
        String b = body == null ? "" : body.toLowerCase();
        if ("AccessDenied".equalsIgnoreCase(code))
            return "the storage bucket's policy refused the file. Run migration 007_photo_storage_read.sql in Supabase (bh-photos needs a read policy).";
        if ("42501".equals(code) || b.contains("row-level security"))
            return "the database refused this account's permission (row-level security). The account must be a verified owner or an admin.";
        if (status == 401 || b.contains("jwt")) return "your sign-in expired. Sign out and sign in again.";
        if (status == 413 || b.contains("exceeded the maximum") || b.contains("too large")) return "a photo is too big (the limit is 5 MB per photo).";
        if (b.contains("mime")) return "a photo has a file type the server doesn't accept (PNG or JPEG only).";
        if ("23505".equals(code)) return "this already exists (duplicate).";
        if ("23503".equals(code)) return "a linked record is missing (for example an amenity that no longer exists).";
        if ("23514".equals(code) || "23502".equals(code)) return "a value is missing or out of the allowed range.";
        if (status == 404 || "42P01".equals(code) || "PGRST205".equals(code)) return "a table or storage bucket is missing. A database migration probably wasn't run.";
        if (status >= 500) return "the server had an internal problem. Try again in a moment.";
        return "";
    }
}
