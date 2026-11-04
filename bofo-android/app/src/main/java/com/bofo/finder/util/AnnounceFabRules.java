package com.bofo.finder.util;

/**
 * v2.4.7: the rules of the floating "Announce" button in the house group chat (plain JVM, unit tested). The fragment only draws the answers.
 */
public final class AnnounceFabRules {
    private AnnounceFabRules() { }

    /** What a tap on the button does. */
    public enum Tap {
        /** AI is off: only one choice, so open the manual Announce form at once. */
        OPEN_FORM,
        /** AI is on: show the two choices (Write with AI / Write it myself). */
        SPEED_DIAL
    }

    /**
     * The button is for the owner only, and is hidden while the keyboard is open, while the message box has focus, while the chat is loading,
     * when the load failed (retry screen) and while the phone is offline. A loaded chat with no messages still shows it: that is when an owner
     * most wants to write the first announcement.
     */
    public static boolean visible(boolean owner, boolean keyboardOpen, boolean inputFocused, boolean loaded, boolean loadFailed, boolean offline) {
        return owner && loaded && !loadFailed && !offline && !keyboardOpen && !inputFocused;
    }

    public static Tap onTap(boolean aiOn) { return aiOn ? Tap.SPEED_DIAL : Tap.OPEN_FORM; }

    /**
     * Extended (icon + label) or shrunk (icon only) while the list scrolls: down = shrink, up = extend, no movement keeps the current state,
     * and the top of the list always extends.
     */
    public static boolean extended(boolean current, int dy, boolean atTop) {
        if (atTop || dy < 0) return true;
        if (dy > 0) return false;
        return current;
    }

    /** The speed-dial can only be open while the button is visible; used to close a stuck scrim when the button disappears. */
    public static boolean keepDialOpen(boolean dialOpen, boolean fabVisible) { return dialOpen && fabVisible; }
}
