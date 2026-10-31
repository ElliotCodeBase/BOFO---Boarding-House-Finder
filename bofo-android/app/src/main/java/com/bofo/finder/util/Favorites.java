package com.bofo.finder.util;

import androidx.lifecycle.MutableLiveData;

import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** The signed-in student's saved places (ids), shared by Home, Explore, Saved and the detail screen. Main thread only, except load/toggle which use Async. */
public final class Favorites {
    private Favorites() {}
    public static final MutableLiveData<Set<String>> IDS = new MutableLiveData<>(new HashSet<>());
    private static boolean loaded;

    public static boolean has(String id) { Set<String> s = IDS.getValue(); return s != null && s.contains(id); }

    /** Loads once per sign-in; later calls just run done. Failure keeps the list empty and lets the next call retry. */
    public static void load(Runnable done) {
        if (loaded) { if (done != null) done.run(); return; }
        Async.run(() -> new BhService().favoriteIds(), (Set<String> ids) -> {
            loaded = true;
            IDS.setValue(new HashSet<>(ids));
            if (done != null) done.run();
        }, e -> { if (done != null) done.run(); });
    }

    /** Optimistic: the heart changes at once; if the server refuses, it goes back and onError gets the message. */
    public static void toggle(String bhId, Consumer<String> onError) {
        final boolean on = !has(bhId);
        set(bhId, on);
        final String uid = AuthService.get().userId();
        Async.run(() -> { new BhService().setFavorite(uid, bhId, on); return true; }, ok -> { },
                e -> { set(bhId, !on); if (onError != null) onError.accept(Connectivity.isNetworkError(e) ? "No connection: not saved." : "Could not update your saved places."); });
    }

    private static void set(String id, boolean on) {
        Set<String> s = new HashSet<>(IDS.getValue() == null ? new HashSet<String>() : IDS.getValue());
        if (on) s.add(id); else s.remove(id);
        IDS.setValue(s);
    }

    /** New sign-in: forget the previous user's saved places. */
    public static void reset() { loaded = false; IDS.setValue(new HashSet<>()); }
}
