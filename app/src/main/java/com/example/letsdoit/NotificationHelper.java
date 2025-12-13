// main/java/com/example/letsdoit/NotificationHelper.java
package com.example.letsdoit;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Calendar;
import java.util.List;

public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID = "daily_tasks_channel";
    private static final String CHANNEL_NAME = "Daily Task Reminders";
    private static final String CHANNEL_DESC = "Notifications for pending daily tasks";
    private static final int NOTIFICATION_ID = 1001;

    private static final String PREFS_NAME = "NotificationPrefs";
    private static final String KEY_NOTIFICATION_ENABLED = "notification_enabled";
    private static final String KEY_NOTIFICATION_TIME_HOUR = "notification_hour";
    private static final String KEY_NOTIFICATION_TIME_MINUTE = "notification_minute";

    private Context context;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        createNotificationChannel();
    }

    /**
     * Creates notification channel for Android O and above
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(CHANNEL_DESC);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Schedule daily notification at specified time (default: 7:00 PM)
     * FIXED: Now uses EXACT alarm that triggers at precise time
     */
    public void scheduleDailyNotification() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Get notification time from preferences or use default (7:00 PM)
        int hour = prefs.getInt(KEY_NOTIFICATION_TIME_HOUR, 19); // Default to 19 (7 PM)
        int minute = prefs.getInt(KEY_NOTIFICATION_TIME_MINUTE, 0); // Default to 0 minutes

        scheduleDailyNotification(hour, minute);
    }

    /**
     * Schedule daily notification at custom time
     * CRITICAL FIX: Use setExactAndAllowWhileIdle for EXACT timing on modern Android
     */
    public void scheduleDailyNotification(int hour, int minute) {
        // First, cancel any existing alarm
        cancelDailyNotification();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, DailyNotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set the alarm to start at specified time
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If the time has already passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        // CRITICAL FIX: Use setExactAndAllowWhileIdle for EXACT timing
        // This works even when device is in Doze mode
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ (API 23+): Use setExactAndAllowWhileIdle
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                // Android 5.x and below: Use setExact
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }

            Log.d(TAG, "EXACT daily notification scheduled for " + hour + ":" + String.format("%02d", minute));
            Log.d(TAG, "Next alarm: " + calendar.getTime().toString());
            Log.d(TAG, "Time until alarm: " + (calendar.getTimeInMillis() - System.currentTimeMillis()) / 1000 + " seconds");

            // Save to preferences
            saveNotificationSettings(true, hour, minute);
        }
    }

    /**
     * Cancel scheduled daily notification
     */
    public void cancelDailyNotification() {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, DailyNotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            Log.d(TAG, "Daily notification cancelled");
        }

        saveNotificationSettings(false, 19, 0); // Default to 7:00 PM
    }

    /**
     * NEW METHOD: Trigger notification immediately for testing
     * This shows pending tasks notification right now without waiting for scheduled time
     */
    public void triggerNotificationNow() {
        Log.d(TAG, "Triggering notification immediately for testing");

        // Broadcast the intent to trigger the receiver immediately
        Intent intent = new Intent(context, DailyNotificationReceiver.class);
        context.sendBroadcast(intent);
    }

    /**
     * MODIFIED: Show notification with list of pending tasks for specific user
     * Added userEmail parameter to personalize notification
     */
    public void showPendingTasksNotification(List<Task> pendingTasks, String userEmail) {
        if (pendingTasks == null || pendingTasks.isEmpty()) {
            Log.d(TAG, "No pending tasks to notify for user: " + userEmail);
            return;
        }

        // Build notification content with user personalization
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("📋 Your Pending Tasks")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500});

        // Create big text style for multiple tasks
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle("📋 " + pendingTasks.size() + " Pending Task" + (pendingTasks.size() > 1 ? "s" : "") + " for You");

        // Add each task as a line (max 7 lines visible)
        int maxLines = Math.min(pendingTasks.size(), 7);
        for (int i = 0; i < maxLines; i++) {
            Task task = pendingTasks.get(i);
            String taskLine = "• " + task.getTitle();
            inboxStyle.addLine(taskLine);
        }

        // If more than 7 tasks, show summary
        if (pendingTasks.size() > 7) {
            inboxStyle.addLine("+ " + (pendingTasks.size() - 7) + " more tasks...");
        }

        inboxStyle.setSummaryText("Tap to view your tasks");
        builder.setStyle(inboxStyle);

        // Set content text for collapsed view
        if (pendingTasks.size() == 1) {
            builder.setContentText("• " + pendingTasks.get(0).getTitle());
        } else {
            builder.setContentText(pendingTasks.size() + " tasks pending. Tap to view.");
        }

        // Create intent to open app when notification is tapped
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("open_view_tasks", true);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.setContentIntent(pendingIntent);

        // Show notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Notification shown with " + pendingTasks.size() + " pending tasks for user: " + userEmail);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show notification: " + e.getMessage());
        }
    }

    /**
     * Check if notifications are enabled
     */
    public boolean areNotificationsEnabled() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_NOTIFICATION_ENABLED, false);
    }

    /**
     * Save notification settings to preferences
     */
    private void saveNotificationSettings(boolean enabled, int hour, int minute) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_NOTIFICATION_ENABLED, enabled);
        editor.putInt(KEY_NOTIFICATION_TIME_HOUR, hour);
        editor.putInt(KEY_NOTIFICATION_TIME_MINUTE, minute);
        editor.apply();
    }

    /**
     * Get notification time
     */
    public String getNotificationTime() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int hour = prefs.getInt(KEY_NOTIFICATION_TIME_HOUR, 19); // Default to 19 (7 PM)
        int minute = prefs.getInt(KEY_NOTIFICATION_TIME_MINUTE, 0); // Default to 0
        return String.format("%02d:%02d", hour, minute);
    }
}