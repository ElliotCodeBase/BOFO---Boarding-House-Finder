package com.bofo.admin;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.bofo.shared.PushRules;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Runs only while the app is open (when it is closed or in the background the phone itself draws the notification from the "notification" block
 * the server sends, and a tap opens MainActivity with the same data as extras). Here we draw the same thing, lock-screen safe.
 * A push the signed-in account may not see (PushRules.allowed) is dropped. Chat pushes never reach this app (server sends them to user-app tokens only).
 */
public final class AdminMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) { PushRegistrar.onNewToken(this, token); }

    @Override public void onMessageReceived(RemoteMessage m) {
        Map<String, String> d = m.getData();
        String kind = d.get("kind");
        Access a = AdminApp.access();
        if (a == null || !PushRules.allowed(kind, a.perms)) return;
        String title = d.get("title") == null ? "BOFO Admin" : d.get("title");
        String body = d.get("body") == null ? "" : d.get("body");

        Intent open = new Intent(this, MainActivity.class).setAction(PushRules.ACTION_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("kind", kind).putExtra("place", d.get("place") == null ? "" : d.get("place"));
        PendingIntent pi = PendingIntent.getActivity(this, (kind + d.get("id")).hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // The lock screen shows only the generic public version.
        android.app.Notification pub = new NotificationCompat.Builder(this, PushRules.channelFor(kind)).setSmallIcon(R.drawable.ic_admin)
                .setContentTitle("BOFO Admin").setContentText("You have something to review").build();
        android.app.Notification n = new NotificationCompat.Builder(this, PushRules.channelFor(kind)).setSmallIcon(R.drawable.ic_admin)
                .setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(pub).build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(("admin:" + kind + ":" + d.get("id")).hashCode(), n);       // same item again = replaced, not stacked
    }
}
