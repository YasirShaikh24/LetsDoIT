// src/main/java/com/example/letsdoit/BootCompletedReceiver.java
package com.example.letsdoit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Receiver to reschedule notifications after device reboot
 * UPDATED: Now properly reschedules repeating alarm
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device booted - rescheduling notifications");

            NotificationHelper notificationHelper = new NotificationHelper(context);

            // Only reschedule if notifications were previously enabled
            if (notificationHelper.areNotificationsEnabled()) {
                // FIXED: Use scheduleDailyNotification() which now properly sets repeating alarm
                notificationHelper.scheduleDailyNotification();
                Log.d(TAG, "Notifications rescheduled after boot at: " + notificationHelper.getNotificationTime());
            } else {
                Log.d(TAG, "Notifications not enabled, skipping reschedule");
            }
        }
    }
}