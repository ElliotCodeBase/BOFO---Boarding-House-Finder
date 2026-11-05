package com.bofo.finder.util;

import com.bofo.finder.service.SupabaseClient;

/** v2.4.14: admin screens show a short sentence instead of "HTTP 409 (code 23505)". Drop-in for ErrorText.full(headline, e) on the admin screens. */
public final class AdminErrorRules {
    private AdminErrorRules() {}

    /** A plain reason for a server answer, or "" when there is nothing useful to add. */
    public static String reason(int status, String body) {
        String b = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
        if (b.contains("nothing to undo")) return b.contains("newer waiting") ? "A newer waiting report from the same person already exists." : "There is nothing left to undo. Someone may have changed it already.";
        if (b.contains("already handled") || b.contains("no longer pending")) return "Someone already handled this.";
        if (b.contains("this id is banned")) return "This ID belongs to a banned person.";
        if (b.contains("does not match")) return "The ID number does not match the one the owner typed.";
        if (b.contains("admins only") || status == 401 || status == 403) return "Only a signed-in admin can do this. Sign in again if it keeps happening.";
        if (b.contains("not found") || status == 404) return "It no longer exists.";
        if (b.contains("not banned")) return "This account is not banned.";
        if (status == 409) return "That was already done.";
        if (status == 429) return "Too many tries. Wait a moment and try again.";
        if (status >= 500) return "The server had a problem. Try again in a minute.";
        return "";
    }

    /** "Could not save the decision. Someone already handled this." (no codes, no HTTP numbers). */
    public static String plain(String headline, Throwable e) {
        String h = headline == null ? "" : headline.trim();
        if (!h.isEmpty() && !h.endsWith(".")) h += ".";
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SupabaseClient.ApiException a) {
                String r = reason(a.status, a.body);
                return r.isEmpty() ? h + " Please try again." : h + " " + r;
            }
            if (t.getCause() == t) break;
        }
        return h + " Please try again.";
    }
}
