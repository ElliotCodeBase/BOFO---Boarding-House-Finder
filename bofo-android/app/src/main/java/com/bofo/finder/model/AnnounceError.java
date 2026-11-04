package com.bofo.finder.model;

/**
 * v2.4.1 (AI Announcement Writer, step 4): every way "Write for me" can fail, as the UI needs to show it. {@link #stringName()} is the
 * name of an existing string resource in strings_announce_ai.xml (resolve it with Resources.getIdentifier). No Android classes.
 */
public enum AnnounceError {
    /** 401: the session expired. */
    SIGNED_OUT("announce_err_signed_out"),
    /** 403 not_owner: only owners with a boarding house. */
    NOT_OWNER("announce_err_not_owner"),
    /** 403 ai_disabled: the admin switched BOFO AI off. */
    AI_DISABLED("announce_err_ai_disabled"),
    /** 429: too many drafts; the string takes the seconds (%1$d). */
    RATE_LIMITED("announce_err_rate_limited"),
    /** 400 from the server (details not valid); the offending field key may be known. */
    INVALID("announce_err_invalid"),
    /** The app's own check failed BEFORE any network call; the UI shows announce_err_missing / one_of / too_long from the Problem. */
    INVALID_DETAILS("announce_err_invalid"),
    /** 405, 5xx. */
    SERVER("announce_err_server"),
    /** No network / timeout. */
    OFFLINE("announce_err_offline"),
    /** Anything else (also an unreadable 200 body). */
    GENERIC("announce_err_generic");

    private final String stringName;
    AnnounceError(String stringName) { this.stringName = stringName; }

    /** String resource name of the message for the owner. */
    public String stringName() { return stringName; }

    /** True when pressing "Try again" with the same details can help (the UI may still wait for the retry-after seconds). */
    public boolean canRetry() { return this == RATE_LIMITED || this == SERVER || this == OFFLINE || this == GENERIC; }
}
