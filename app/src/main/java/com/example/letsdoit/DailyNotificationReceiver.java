// src/main/java/com/example/letsdoit/DailyNotificationReceiver.java
package com.example.letsdoit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DailyNotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "DailyNotifReceiver";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.US);

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Daily notification triggered");

        // Reschedule for next day
        NotificationHelper notificationHelper = new NotificationHelper(context);
        notificationHelper.scheduleDailyNotification();

        // Fetch and show pending tasks
        fetchPendingTasks(context);
    }

    private void fetchPendingTasks(Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get today's date info
        long todayMillis = System.currentTimeMillis();
        long dayStart = getDayStartMillis(todayMillis);
        String dayShort = dayFormat.format(new Date(todayMillis)).substring(0, 3).toLowerCase();

        db.collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Task> pendingTasks = new ArrayList<>();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Task task = document.toObject(Task.class);
                            task.setId(document.getId());

                            // Check if task is active today
                            if (!isTaskActiveToday(task, dayShort, dayStart)) {
                                continue;
                            }

                            // Check if task is pending
                            if (!isTaskCompleted(task, dayStart)) {
                                pendingTasks.add(task);
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing task: " + e.getMessage());
                        }
                    }

                    // Show notification if there are pending tasks
                    if (!pendingTasks.isEmpty()) {
                        NotificationHelper notificationHelper = new NotificationHelper(context);
                        notificationHelper.showPendingTasksNotification(pendingTasks);
                    } else {
                        Log.d(TAG, "No pending tasks for today");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching tasks: " + e.getMessage());
                });
    }

    private boolean isTaskActiveToday(Task task, String dayShort, long dayStart) {
        String taskType = task.getTaskType() != null ? task.getTaskType().toLowerCase() : "permanent";

        if (taskType.equals("permanent")) {
            // Check if today is in selected days
            List<String> selectedDays = task.getSelectedDays();
            if (selectedDays != null) {
                for (String day : selectedDays) {
                    if (day.trim().toLowerCase().equals(dayShort)) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            // Additional task - check date range
            try {
                String startDateStr = task.getStartDate();
                String endDateStr = task.getEndDate();

                if (startDateStr == null || startDateStr.isEmpty() ||
                        endDateStr == null || endDateStr.isEmpty()) {
                    return false;
                }

                long startDateMillis = dateFormat.parse(startDateStr).getTime();
                long endDateMillis = dateFormat.parse(endDateStr).getTime();

                long startDayStart = getDayStartMillis(startDateMillis);
                long endDayStart = getDayStartMillis(endDateMillis);

                return dayStart >= startDayStart && dayStart <= endDayStart;
            } catch (Exception e) {
                Log.e(TAG, "Error parsing task dates: " + e.getMessage());
                return false;
            }
        }
    }

    private boolean isTaskCompleted(Task task, long dayStart) {
        // Check if task has any completion status
        boolean isPermanent = task.getTaskType() != null &&
                task.getTaskType().equalsIgnoreCase("permanent");

        // For permanent tasks, check if anyone completed it today
        if (isPermanent) {
            long completedAt = task.getCompletedDateMillis();
            if (completedAt > 0) {
                long dayEnd = dayStart + 86400000L - 1;
                if (completedAt <= dayEnd) {
                    return true;
                }
            }
        }

        // Check global status
        return task.getUserStatus().containsValue("Completed");
    }

    private long getDayStartMillis(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}