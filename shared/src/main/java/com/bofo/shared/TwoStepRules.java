package com.bofo.shared;

/** Two-step sign-in setup (authenticator app), pure helpers: code and key formatting, and a sanity check of the otpauth link the server returns. Unit-tested. */
public final class TwoStepRules {
    private TwoStepRules() {}

    /** Digits only, at most 6 (people paste "123 456"). */
    public static String cleanCode(String s) {
        if (s == null) return "";
        String d = s.replaceAll("[^0-9]", "");
        return d.length() > 6 ? d.substring(0, 6) : d;
    }

    public static boolean codeReady(String s) { return cleanCode(s).length() == 6; }

    /** "jbswy3dpehpk3pxp" -> "JBSW Y3DP EHPK 3PXP" so it is easy to type by hand. */
    public static String groupSecret(String secret) {
        if (secret == null) return "";
        String t = secret.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) { if (i > 0 && i % 4 == 0) b.append(' '); b.append(t.charAt(i)); }
        return b.toString();
    }

    /** Only open a link that is really an authenticator link. */
    public static boolean validUri(String uri) { return uri != null && uri.length() < 2048 && uri.startsWith("otpauth://totp/"); }
}
