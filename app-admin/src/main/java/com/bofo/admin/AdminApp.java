package com.bofo.admin;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import com.bofo.shared.Api;
import com.bofo.shared.PushRules;

public final class AdminApp extends Application {
    private static volatile Access access;
    /** The signed-in account's role and permissions (null when signed out or locked out). Read by the messaging service. */
    static Access access() { return access; }
    static void setAccess(Access a) { access = a; }

    @Override public void onCreate() {
        super.onCreate();
        Api.get().init(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, BuildConfig.VERSION_NAME, "admin");   // x-bofo-client: admin
        AdminAuth.init(this);
        CsvShare.clean(this);   // no export left in the cache from an earlier run
        channels();
    }

    /** The server names these channels in the notification block, so they must exist before the first push arrives. */
    private void channels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel admin = new NotificationChannel(PushRules.CHANNEL_ADMIN, "Waiting for you", NotificationManager.IMPORTANCE_HIGH);
        admin.setDescription("IDs to review, approvals and the daily reminder");
        admin.setLockscreenVisibility(android.app.Notification.VISIBILITY_PRIVATE);
        NotificationChannel rep = new NotificationChannel(PushRules.CHANNEL_REPORTS, "New reports", NotificationManager.IMPORTANCE_HIGH);
        rep.setDescription("A user report is waiting");
        rep.setLockscreenVisibility(android.app.Notification.VISIBILITY_PRIVATE);
        nm.createNotificationChannel(admin);
        nm.createNotificationChannel(rep);
    }
}
