package io.github.samaliyan.hush;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional (Notification access): takes away the messaging app's notification of a removed message.
 * When the messaging app shows a new notification, the message is already stored, so a cleaning pass
 * runs first; then every notification whose text belongs to a removed message is cancelled.
 * Android has already played the sound by then — see the "Sound only for real messages" setting.
 */
public final class NotifListener extends NotificationListenerService {
    private static final Set<String> MESSAGING = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.messaging"));
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static volatile NotifListener live;

    @Override public void onListenerConnected() {
        live = this;
        WORK.execute(this::cancelRemoved);
    }

    @Override public void onListenerDisconnected() {
        if (live == this) live = null;
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isMessaging(sbn.getPackageName())) return;
        boolean removed = belongsToRemoved(sbn);
        if (removed) {
            cancel(sbn);
            return;
        }
        WORK.execute(() -> {
            try {
                Cleaner.autoPass(this);
            } catch (Exception ignored) {
                // permission taken away meanwhile
            }
            cancelRemoved();
        });
    }

    /** Called after a cleaning pass removed messages. */
    static void cancelMatching() {
        final NotifListener l = live;
        if (l != null) WORK.execute(l::cancelRemoved);
    }

    static boolean connected() {
        return live != null;
    }

    private void cancelRemoved() {
        StatusBarNotification[] all;
        try {
            all = getActiveNotifications();
        } catch (Exception e) {
            android.util.Log.w("Hush", "active notifications: " + e);
            return;
        }
        if (all == null) return;
        for (StatusBarNotification sbn : all) {
            if (isMessaging(sbn.getPackageName()) && belongsToRemoved(sbn)) cancel(sbn);
        }
    }

    /**
     * The notification is about a removed text: same text, or — when Android hides the text from Hush — a
     * single-message notification with the date of a removed text. A group summary is never matched.
     */
    private static boolean belongsToRemoved(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || (n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return false;
        if (Cleaner.wasRemoved(lastText(n))) return true;
        return messageCount(n) <= 1 && Cleaner.wasRemovedAt(n.when);
    }

    @SuppressWarnings("deprecation")
    private static int messageCount(Notification n) {
        Parcelable[] msgs = n.extras == null ? null : n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        return msgs == null ? 1 : msgs.length;
    }

    private void cancel(StatusBarNotification sbn) {
        try {
            cancelNotification(sbn.getKey());
            android.util.Log.i("Hush", "notification cancelled: " + sbn.getKey());
        } catch (Exception e) {
            android.util.Log.w("Hush", "cancel failed: " + e);
        }
    }

    private boolean isMessaging(String pkg) {
        if (pkg == null) return false;
        if (MESSAGING.contains(pkg)) return true;
        String def = new SmsStore(this).defaultSmsApp();
        return pkg.equals(def);
    }

    /** The newest message text of a notification (messaging style first, then the plain text). */
    @SuppressWarnings("deprecation")
    static CharSequence lastText(Notification n) {
        if (n == null) return null;
        Bundle x = n.extras;
        if (x == null) return null;
        Parcelable[] msgs = x.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null && msgs.length > 0 && msgs[msgs.length - 1] instanceof Bundle) {
            CharSequence t = ((Bundle) msgs[msgs.length - 1]).getCharSequence("text");
            if (t != null) return t;
        }
        CharSequence big = x.getCharSequence(Notification.EXTRA_BIG_TEXT);
        return big != null ? big : x.getCharSequence(Notification.EXTRA_TEXT);
    }
}
