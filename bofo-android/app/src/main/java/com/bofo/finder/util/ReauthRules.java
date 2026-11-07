package com.bofo.finder.util;

/** v2.12.1 (S-14): rules for "prove it is you again" before the account is deleted. Plain Java so it is unit-tested. */
public final class ReauthRules {
    private ReauthRules() {}

    /** The fresh sign-in must belong to the account that is signed in now; otherwise nothing is deleted and the session is not replaced. */
    public static boolean sameAccount(String currentUserId, String signedInUserId) {
        return currentUserId != null && signedInUserId != null && !currentUserId.trim().isEmpty()
                && currentUserId.trim().equalsIgnoreCase(signedInUserId.trim());
    }

    /** Digits only, 6 to 8 of them (Supabase e-mail codes are 6 or 8 digits). Returns null when it is not a usable code. */
    public static String cleanCode(String typed) {
        if (typed == null) return null;
        String d = typed.replaceAll("\\D", "");
        return d.length() >= 6 && d.length() <= 8 ? d : null;
    }
}
