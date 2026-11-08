package com.rexai.controller.notification;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class RexNotificationManager extends NotificationListenerService {
    private static final String TAG = "RexNotificationManager";
    private static final List<NotificationItem> recentNotifications = new ArrayList<>();

    public static class NotificationItem {
        public String packageName;
        public String title;
        public String text;
        public long timestamp;
        public String category; // IMPORTANT, URGENT, NORMAL, FINANCIAL

        public NotificationItem(String pkg, String t, String txt, long time, String cat) {
            this.packageName = pkg;
            this.title = t;
            this.text = txt;
            this.timestamp = time;
            this.category = cat;
        }
    }

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

        if (isSensitive(title, text)) {
            Log.d(TAG, "Secured/Masked sensitive notification from: " + packageName);
            return;
        }

        String category = classifyNotification(packageName, title, text);
        synchronized (recentNotifications) {
            recentNotifications.add(0, new NotificationItem(packageName, title, text, System.currentTimeMillis(), category));
            if (recentNotifications.size() > 50) {
                recentNotifications.remove(recentNotifications.size() - 1);
            }
        }
        Log.d(TAG, "Notification Processed [" + category + "] from " + packageName + ": " + title);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}

    public static List<NotificationItem> getRecentNotifications() {
        synchronized (recentNotifications) {
            return new ArrayList<>(recentNotifications);
        }
    }

    private boolean isSensitive(String title, String text) {
        String combined = (title + " " + text).toLowerCase();
        return combined.contains("otp") || combined.contains("pin") || combined.contains("password") 
                || combined.contains("cvv") || combined.contains("credit card") || combined.contains("debit card");
    }

    private String classifyNotification(String pkg, String title, String text) {
        String combined = (title + " " + text).toLowerCase();
        if (combined.contains("bank") || combined.contains("rs.") || combined.contains("inr") || combined.contains("cred") || combined.contains("debited")) {
            return "FINANCIAL";
        } else if (combined.contains("urgent") || combined.contains("emergency") || combined.contains("alert")) {
            return "URGENT";
        } else if (pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("messages")) {
            return "IMPORTANT";
        }
        return "NORMAL";
    }
}
