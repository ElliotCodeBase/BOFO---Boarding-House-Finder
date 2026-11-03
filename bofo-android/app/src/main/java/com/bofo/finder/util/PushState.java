package com.bofo.finder.util;

/** v2.3.0: which chat is on screen right now (set in onResume, cleared in onPause), so a message for it makes no banner. */
public final class PushState {
    private PushState() {}
    public static volatile String openDm;       // conversation id
    public static volatile String openGroup;    // boarding house id
}
