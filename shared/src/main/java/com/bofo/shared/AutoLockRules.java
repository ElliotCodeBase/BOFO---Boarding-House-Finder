package com.bofo.shared;

/** v2.9.0: when the admin tools lock again after the app was in the background (pure, unit tested). */
public final class AutoLockRules {
    public static final long LIMIT_MS = 5 * 60 * 1000L;
    private AutoLockRules() {}

    /**
     * @param enabled      the Settings switch (admin only, default on)
     * @param stoppedAtMs  when the app went to the background, 0 = not recorded
     * @param nowMs        current time
     * A clock set back to before the stop counts as "lock" (fail closed).
     */
    public static boolean shouldLock(boolean enabled, long stoppedAtMs, long nowMs) {
        if (!enabled || stoppedAtMs <= 0) return false;
        return nowMs < stoppedAtMs || nowMs - stoppedAtMs >= LIMIT_MS;
    }
}
