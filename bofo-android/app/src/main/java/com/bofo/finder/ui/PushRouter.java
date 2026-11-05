package com.bofo.finder.ui;

import android.content.Intent;
import android.view.View;

import com.bofo.finder.util.PushNotifier;

import java.lang.ref.WeakReference;

/**
 * v2.3.0: a tap on a notification arrives as an Intent for MainActivity. It is kept here until the shell (tabs) is on screen,
 * then ShellFragment.openFromPush() opens the chat / group chat / report list. Cleared at sign-out.
 */
public final class PushRouter {
    private PushRouter() {}

    private static volatile String kind, id, title, place;
    private static WeakReference<ShellFragment> shell;

    /** Reads (and removes) the push extras of an Intent. True when it was a notification tap. */
    public static boolean stash(Intent i) {
        if (i == null) return false;
        if (PushNotifier.ACTION_OPEN.equals(i.getAction()) && i.hasExtra(PushNotifier.EX_KIND)) {       // drawn by PushNotifier (app was open)
            kind = i.getStringExtra(PushNotifier.EX_KIND);
            id = i.getStringExtra(PushNotifier.EX_ID);
            title = i.getStringExtra(PushNotifier.EX_TITLE);
            place = i.getStringExtra(PushNotifier.EX_PLACE);
            i.setAction(null);                                   // never open the same notification twice (rotation, re-launch)
            i.removeExtra(PushNotifier.EX_KIND);
            return true;
        }
        // v2.9.2: drawn by the phone's system from the server's hybrid message: the data fields arrive as plain extras (click_action = ACTION_OPEN).
        String k = i.getStringExtra("kind");
        if (k != null && i.hasExtra("id") && com.bofo.finder.util.PushRules.known(k)) {
            kind = k;
            id = i.getStringExtra("id");
            title = i.getStringExtra("title");
            place = i.getStringExtra("place");
            if (PushNotifier.ACTION_OPEN.equals(i.getAction())) i.setAction(null);
            i.removeExtra("kind"); i.removeExtra("id");
            return true;
        }
        return false;
    }

    public static void clear() { kind = null; id = null; title = null; place = null; }

    static void shellResumed(ShellFragment s) {
        shell = new WeakReference<>(s);
        View v = s.getView();
        if (v != null) v.post(() -> consume(s));             // after the resume finished, so navigating from here is safe
    }

    static void shellPaused(ShellFragment s) {
        if (shell != null && shell.get() == s) shell = null;
    }

    /** The shell is already on screen: open now. False when it is not (the caller brings it back first). */
    public static boolean deliverNow() {
        ShellFragment s = shell == null ? null : shell.get();
        if (s == null || s.getView() == null) return false;
        consume(s);
        return true;
    }

    private static void consume(ShellFragment s) {
        final String k = kind, i = id, t = title, p = place;
        if (k == null || i == null) return;
        clear();
        s.openFromPush(k, i, t == null ? "" : t, p == null ? "" : p);
    }
}
