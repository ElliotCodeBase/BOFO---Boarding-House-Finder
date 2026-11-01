package com.bofo.finder.app;

import android.app.Application;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.DiskCache;
import com.bofo.finder.util.LocalTransit;
import com.bofo.finder.util.Outbox;
import com.bofo.finder.util.ThemeMode;

/** Application entry point: one-time initialisation of config and on-device storage. */
public class BofoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // v2.0.117: orange (default) or classic purple. "Night" is only the switch, the app is never dark (see ThemeMode). Must run before any activity is created.
        ThemeMode.applySaved(this);
        AppConfig.init(this);
        DiskCache.setBase(getFilesDir());
        LocalTransit.init(this);         // hand-made jeepney lines bundled in assets/local-transit.json
        AuthService.init(this);          // restores the saved session and wires the token into SupabaseClient / Outbox
        // Stage 7: chat messages written offline are sent by themselves when the connection returns (any screen, even with no chat open).
        Connectivity.OFFLINE.observeForever(off -> { if (off != null && !off) Outbox.flushAsync(); });
    }
}
