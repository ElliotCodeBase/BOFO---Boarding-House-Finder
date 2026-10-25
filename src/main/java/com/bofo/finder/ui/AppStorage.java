package com.bofo.finder.ui;

import com.bofo.finder.util.DiskCache;

/** Points {@link DiskCache} at the app's private storage folder (the same place Locator keeps its settings). Safe to call many times. */
public final class AppStorage {
    private AppStorage() {}
    private static boolean done;

    public static synchronized void init() {
        if (done) return;
        done = true;
        try {
            DiskCache.setBase(com.gluonhq.attach.storage.StorageService.create()
                    .flatMap(com.gluonhq.attach.storage.StorageService::getPrivateStorage).orElse(null));
        } catch (Throwable noStorage) { /* desktop without Attach storage: ~/.bofo is used */ }
    }
}
