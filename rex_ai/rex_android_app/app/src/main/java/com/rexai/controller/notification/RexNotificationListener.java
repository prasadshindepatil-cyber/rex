package com.rexai.controller.notification;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.rex.nova.helpers.RexNotificationTextHelper;

public class RexNotificationListener extends NotificationListenerService {
    private static final String TAG = "RexNotificationListener";
    public static final String ACTION_NOTIFICATION_RECEIVED = "com.rexai.NOTIFICATION_RECEIVED";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String packageName = sbn.getPackageName();
        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        CharSequence titleSeq = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textSeq = extras.getCharSequence(Notification.EXTRA_TEXT);

        String title = titleSeq != null ? titleSeq.toString() : "";
        String text = textSeq != null ? textSeq.toString() : "";

        // Clean text using RexNotificationTextHelper
        title = RexNotificationTextHelper.cleanText(title);
        text = RexNotificationTextHelper.cleanText(text);

        if (isSensitive(title, text)) {
            Log.d(TAG, "Sensitive notification secured from: " + packageName);
            return;
        }

        Log.d(TAG, "Notification from " + packageName + " | Title: " + title + " | Text: " + text);

        // Send Broadcast to Dashboard / Manager
        Intent intent = new Intent(ACTION_NOTIFICATION_RECEIVED);
        intent.putExtra("package", packageName);
        intent.putExtra("title", title);
        intent.putExtra("text", text);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        // Forward to RexNotificationManager if available
        try {
            RexNotificationManager.getInstance().handleIncomingNotification(packageName, title, text);
        } catch (Exception e) {
            Log.e(TAG, "Error forwarding to RexNotificationManager", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}

    private boolean isSensitive(String title, String text) {
        String combined = (title + " " + text).toLowerCase();
        return combined.contains("otp") || combined.contains("pin") || combined.contains("password") 
                || combined.contains("cvv") || combined.contains("credit card") || combined.contains("debit card");
    }
}
