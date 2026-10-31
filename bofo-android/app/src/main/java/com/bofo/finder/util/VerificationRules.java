package com.bofo.finder.util;

import com.bofo.finder.config.AppConfig;

/** Pure rules of the owner verification screen (8d, unit tested): ID types, status, what is missing, JPEG / PNG detection. */
public final class VerificationRules {
    private VerificationRules() {}

    /** What the screen shows. {@code UNVERIFIED} also covers "no request yet". */
    public enum State { UNVERIFIED, PENDING, VERIFIED, REJECTED }

    /** Slots of the form, in screen order. */
    public static final int FRONT = 0, BACK = 1, SELFIE = 2;

    /** Shown in the ID type picker; stored as typed in owner_verifications.id_type (no database check on it). */
    public static final String[] ID_TYPES = {
            "National ID (PhilSys)", "Driver's license", "Passport", "UMID", "Postal ID", "Voter's ID", "PRC ID", "Other government ID"
    };

    /** Database text (users / owner_verifications status) -> state. Anything unknown counts as not verified. */
    public static State state(String status) {
        if (status == null) return State.UNVERIFIED;
        switch (status.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "pending": return State.PENDING;
            case "verified": return State.VERIFIED;
            case "rejected": return State.REJECTED;
            default: return State.UNVERIFIED;
        }
    }

    /** The form is open for new owners and after a rejection; never while a request waits or after approval. */
    public static boolean canSubmit(State s) { return s == State.UNVERIFIED || s == State.REJECTED; }

    /** Owner profile says verified (admin-granted or approved) OR the latest request was approved. */
    public static State effective(boolean profileVerified, String latestStatus) {
        return profileVerified ? State.VERIFIED : state(latestStatus);
    }

    /** First empty slot (FRONT, BACK, SELFIE) or -1 when all three photos are there. */
    public static int firstMissing(byte[] front, byte[] back, byte[] selfie) {
        if (front == null || front.length == 0) return FRONT;
        if (back == null || back.length == 0) return BACK;
        if (selfie == null || selfie.length == 0) return SELFIE;
        return -1;
    }

    public static boolean isJpeg(byte[] d) { return d != null && d.length > 2 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8; }

    /** File extension for the upload, from the bytes (the Android app sends JPEG, the desktop sent PNG). */
    public static String ext(byte[] d) { return isJpeg(d) ? ".jpg" : ".png"; }

    public static String mime(byte[] d) { return isJpeg(d) ? "image/jpeg" : "image/png"; }

    /** Version recorded in user_agreements for document_type verification_consent. */
    public static String consentVersion() {
        String v = AppConfig.get("policy.verification.version").trim();
        return v.isEmpty() ? "1.0" : v;
    }

    /** The id type to store: the picked one, or the first when the index is out of range. */
    public static String idType(int index) { return ID_TYPES[index >= 0 && index < ID_TYPES.length ? index : 0]; }
}
