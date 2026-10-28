package com.gathin.tvhome;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Starts this launcher's process after the firmware has killed it, and feeds notifications_button.
 *
 * A notification listener keeps its approval across a force stop, and the system binds it again
 * once the process is gone. That rebind is what brings Home back on TCL and Xiaomi. With
 * notifications_button on, the notifications posted on the TV are listed by NotificationsActivity and
 * counted on Home's bell; nothing is stored or sent anywhere. Enable with
 * {@code cmd notification allow_listener com.gathin.tvhome/.GuardRevival}.
 */
public class GuardRevival extends NotificationListenerService {
    private BroadcastReceiver unlocked;

    /** The bound listener, null while the system has not bound it (or access is not granted). Main thread. */
    private static GuardRevival connected;
    /** Home's bell, run on the main thread whenever a notification comes or goes. */
    static Runnable onChange;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onListenerConnected() {
        connected = this;
        changed();
        UserManager users = getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) {
            if (unlocked != null) return;
            unlocked = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    stopWaiting();
                    GuardKeep.ensureHomeKey(GuardRevival.this);
                }
            };
            registerReceiver(unlocked, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
            if (users.isUserUnlocked()) {
                stopWaiting();
                GuardKeep.ensureHomeKey(this);
            }
            return;
        }
        GuardKeep.ensureHomeKey(this);
    }

    @Override
    public void onListenerDisconnected() {
        if (connected == this) connected = null;
        changed();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        changed();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        changed();
    }

    private static void changed() {
        MAIN.post(() -> {
            Runnable r = onChange;
            if (r != null) r.run();
        });
    }

    /** The panel's Clear all: every notification the user could swipe away elsewhere. */
    static void clearAll() {
        GuardRevival listener = connected;
        if (listener == null) return;
        try {
            listener.cancelAllNotifications();
        } catch (RuntimeException ignored) {
        }
    }

    /** One notification opened from the panel that asked to go once opened (auto-cancel). */
    static void cancel(String key) {
        GuardRevival listener = connected;
        if (listener == null) return;
        try {
            listener.cancelNotification(key);
        } catch (RuntimeException ignored) {
        }
    }

    /** Whether the listener is bound, so the notifications can be read at all. */
    static boolean available() {
        return connected != null;
    }

    /**
     * The notifications worth a row: from other apps, with a title, not a group's summary and not a running
     * service's standing notice ("... is running"). Newest first.
     */
    static List<StatusBarNotification> active(Context self) {
        GuardRevival listener = connected;
        if (listener == null) return Collections.emptyList();
        StatusBarNotification[] all;
        try {
            all = listener.getActiveNotifications();
        } catch (RuntimeException e) {   // access withdrawn, or the binder went away
            return Collections.emptyList();
        }
        List<StatusBarNotification> shown = new ArrayList<>();
        if (all == null) return shown;
        for (StatusBarNotification sbn : all) {
            Notification n = sbn.getNotification();
            if (n == null || self.getPackageName().equals(sbn.getPackageName())) continue;
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) continue;
            if (Notification.CATEGORY_SERVICE.equals(n.category)) continue;
            // Android's own standing notices ("Serial console enabled", debugging on): nothing to open or clear, and
            // shown they would keep the bell's dot on for good.
            if ("android".equals(sbn.getPackageName()) && !sbn.isClearable()) continue;
            CharSequence title = n.extras == null ? null : n.extras.getCharSequence(Notification.EXTRA_TITLE);
            if (title == null || title.toString().trim().isEmpty()) continue;
            shown.add(sbn);
        }
        Collections.sort(shown, (a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return shown;
    }

    private void stopWaiting() {
        if (unlocked == null) return;
        try {
            unregisterReceiver(unlocked);
        } catch (RuntimeException ignored) {
        }
        unlocked = null;
    }

    @Override
    public void onDestroy() {
        if (connected == this) connected = null;
        stopWaiting();
        super.onDestroy();
    }
}
