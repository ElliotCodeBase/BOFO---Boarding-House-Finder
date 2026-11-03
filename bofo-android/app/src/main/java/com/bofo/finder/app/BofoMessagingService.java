package com.bofo.finder.app;

import androidx.annotation.NonNull;

import com.bofo.finder.service.PushService;
import com.bofo.finder.util.PushNotifier;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * v2.3.0: receives the pushes from the send-push edge function, also while the app is closed (high-priority data messages).
 * The notification is drawn by {@link PushNotifier}; this class only hands the message over.
 */
public class BofoMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        PushService.onNewToken(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        PushNotifier.show(getApplicationContext(), message.getData());
    }
}
