package com.bofo.admin;

import android.app.Application;

import com.bofo.shared.Api;

public final class AdminApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Api.get().init(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, BuildConfig.VERSION_NAME, "admin");   // x-bofo-client: admin
        AdminAuth.init(this);
    }
}
