package com.bofo.finder.ui;

/** v2.9.0: true while the admin tools are covered and waiting for the 6-digit code. Static so a screen rotation does not unlock. Cleared on unlock and sign-out. */
final class AdminLock {
    static volatile boolean locked;
    private AdminLock() {}
}
