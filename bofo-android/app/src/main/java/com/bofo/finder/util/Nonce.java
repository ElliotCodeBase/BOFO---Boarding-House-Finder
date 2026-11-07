package com.bofo.finder.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** v2.12.1 (S-07): Google sign-in nonce. Google gets the SHA-256 (hex) of a random value, Supabase gets the RAW value and checks them against each other.
 *  Same pattern as the Admin app (MainActivity.google). Plain Java so it is unit-tested. */
public final class Nonce {
    private Nonce() {}

    /** 32 random bytes, URL-safe base64, no padding. */
    public static String newRaw() {
        byte[] r = new byte[32];
        new SecureRandom().nextBytes(r);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(r);
    }

    /** Lower-case hex SHA-256 of the raw value (what goes to Google). */
    public static String sha256Hex(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
