package com.bofo.finder.util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.content.ContextCompat;

import com.bofo.finder.R;
import com.bofo.finder.app.MainActivity;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.PushService;

import java.util.List;
import java.util.Map;

/**
 * v2.3.0: draws the phone notification for a push (called by BofoMessagingService, also while the app is closed).
 * One notification per chat that grows with the newest messages (MessagingStyle); tap = open that chat (see ui/PushRouter).
 */
public final class PushNotifier {
    private PushNotifier() {}

    public static final String ACTION_OPEN = "com.bofo.finder.OPEN_PUSH";
    public static final String EX_KIND = "push_kind", EX_ID = "push_id", EX_TITLE = "push_title", EX_PLACE = "push_place";

    /** The channels every account may get. Also removes the two old admin channels (v2.11.0: admin pushes live in the BOFO Admin app). */
    public static void createCommonChannels(Context ctx) {
        try {
            NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
            nm.createNotificationChannel(channel(ctx, PushRules.CH_CHATS, R.string.push_channel_chats, R.string.push_channel_chats_desc));
            nm.createNotificationChannel(channel(ctx, PushRules.CH_BOARDING, R.string.push_channel_boarding, R.string.push_channel_boarding_desc));
            nm.createNotificationChannel(channel(ctx, PushRules.CH_ANNOUNCE, R.string.push_channel_announce, R.string.push_channel_announce_desc));
            nm.deleteNotificationChannel(PushRules.OLD_CH_REPORTS);
            nm.deleteNotificationChannel(PushRules.OLD_CH_ADMIN);
        } catch (Throwable ignored) { /* channels are re-created on the next push */ }
    }

    private static NotificationChannelCompat channel(Context ctx, String id, int name, int desc) {
        return new NotificationChannelCompat.Builder(id, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(ctx.getString(name)).setDescription(ctx.getString(desc)).build();
    }

    /** d = the data map of the FCM message (see supabase/functions/send-push). Never throws. */
    public static void show(Context ctx, Map<String, String> d) {
        try { showNow(ctx.getApplicationContext(), d); } catch (Throwable t) { android.util.Log.w("BOFO", "push not shown", t); }
    }

    private static void showNow(Context app, Map<String, String> d) {
        final String kind = d.get("kind"), id = d.get("id");
        if (id == null || id.isEmpty()) { dropped("no id"); return; }
        // v2.9.2: the saved role is only written after the first successful sync. A signed-in phone whose role is not saved yet used to drop EVERY push
        // silently. Now such a phone still shows chats.
        String role = PushService.role(app);
        if (role.isEmpty() && AuthService.get().hasSession()) role = "signed-in";
        if (!PushRules.allowedFor(kind, role)) { dropped("kind=" + kind + " not allowed for role '" + role + "' (empty = nobody signed in)"); return; }
        if (PushRules.isOpenNow(kind, id, PushState.openDm, PushState.openGroup)) { dropped("that chat is on screen"); return; }
        final NotificationManagerCompat nm = NotificationManagerCompat.from(app);
        if (!nm.areNotificationsEnabled()) { dropped("notifications are off for BOFO in the phone settings"); return; }

        final boolean ann = "1".equals(d.get("ann"));
        final String chan = PushRules.channel(kind, ann);
        createCommonChannels(app);

        final String tag = PushRules.tag(kind, id);
        final String title = nz(d.get("title")), body = PushRules.preview(d.get("body"), 140), place = nz(d.get("place")), sender = nz(d.get("sender"));
        final long now = System.currentTimeMillis();

        Intent open = new Intent(app, MainActivity.class).setAction(ACTION_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EX_KIND, kind).putExtra(EX_ID, id).putExtra(EX_TITLE, title).putExtra(EX_PLACE, place);
        PendingIntent pi = PendingIntent.getActivity(app, tag.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(app, chan)
                .setSmallIcon(R.drawable.ic_stat_bofo)
                .setColor(ContextCompat.getColor(app, R.color.bofo_primary))
                .setContentIntent(pi).setAutoCancel(true).setWhen(now).setShowWhen(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        b.setCategory(NotificationCompat.CATEGORY_MESSAGE).setStyle(messageStyle(app, tag, PushRules.GROUP.equals(kind), title, sender.isEmpty() ? title : sender, ann ? "\uD83D\uDCCC " + body : body, now));
        if (PushRules.DM.equals(kind) && !place.isEmpty()) b.setSubText(place);
        try { nm.notify(tag, PushRules.NOTIFICATION_ID, b.build()); } catch (SecurityException noPermission) { /* Android 13+: permission was taken away */ }
    }

    /** Takes the messages already shown for this chat and adds the new one (keeps the newest MAX_LINES). */
    private static NotificationCompat.MessagingStyle messageStyle(Context app, String tag, boolean group, String title, String sender, String text, long now) {
        NotificationCompat.MessagingStyle old = null;
        NotificationManager sys = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (sys != null) for (StatusBarNotification s : sys.getActiveNotifications())
            if (tag.equals(s.getTag()) && s.getId() == PushRules.NOTIFICATION_ID) { old = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(s.getNotification()); break; }

        NotificationCompat.MessagingStyle st = new NotificationCompat.MessagingStyle(new Person.Builder().setName(app.getString(R.string.push_you)).build());
        if (group) st.setConversationTitle(title).setGroupConversation(true);
        if (old != null) {
            List<NotificationCompat.MessagingStyle.Message> prev = old.getMessages();
            for (int i = Math.max(0, prev.size() - (PushRules.MAX_LINES - 1)); i < prev.size(); i++) {
                NotificationCompat.MessagingStyle.Message m = prev.get(i);
                st.addMessage(new NotificationCompat.MessagingStyle.Message(m.getText(), m.getTimestamp(), m.getPerson()));
            }
        }
        st.addMessage(text, now, new Person.Builder().setName(sender).build());
        return st;
    }

    /** Logcat filter "BOFO": tells why a push that reached the phone was not drawn. */
    private static void dropped(String why) { android.util.Log.i("BOFO", "push not shown: " + why); }

    private static String nz(String s) { return s == null ? "" : s; }
}
