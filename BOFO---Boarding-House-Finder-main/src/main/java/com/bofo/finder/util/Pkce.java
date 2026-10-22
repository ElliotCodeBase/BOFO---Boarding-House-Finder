package com.bofo.finder.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class Pkce {
    private Pkce() {}
    public static String verifier() {
        byte[] b = new byte[48]; new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    public static String challenge(String verifier) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
