// src/main/java/com/example/letsdoit/DailyNotificationReceiver.java
package com.example.letsdoit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

    // SharedPreferences Constants (Must match LoginActivity)
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROLE = "role";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Daily notification triggered at: " + new Date().toString());

        // CRITICAL: Reschedule for tomorrow since we use setExactAndAllowWhileIdle (not repeating)
        NotificationHelper notificationHelper = new NotificationHelper(context);
        notificationHelper.scheduleDailyNotification();
        Log.d(TAG, "Rescheduled notification for tomorrow");

        // NEW: Check if user is logged in
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false);

        if (!isLoggedIn) {
            Log.d(TAG, "No user logged in - skipping notification");
            return;
        }

        // NEW: Get logged-in user details
        String loggedInUserEmail = prefs.getString(KEY_EMAIL, null);
        String loggedInUserRole = prefs.getString(KEY_ROLE, null);

        if (loggedInUserEmail == null || loggedInUserEmail.isEmpty()) {
            Log.d(TAG, "User email not found - skipping notification");
            return;
        }

        Log.d(TAG, "Checking tasks for logged-in user: " + loggedInUserEmail + " (Role: " + loggedInUserRole + ")");

        // Fetch and show pending tasks for this specific user
        fetchPendingTasksForUser(context, loggedInUserEmail, loggedInUserRole);
    }

    // MODIFIED: Now accepts user email and role as parameters
    private void fetchPendingTasksForUser(Context context, String userEmail, String userRole) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get today's date info
        long todayMillis = System.currentTimeMillis();
        long dayStart = getDayStartMillis(todayMillis);
        String dayShort = dayFormat.format(new Date(todayMillis)).substring(0, 3).toLowerCase();

        Log.d(TAG, "Checking tasks for: " + new Date(todayMillis) + " (day: " + dayShort + ")");

        db.collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Task> pendingTasks = new ArrayList<>();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Task task = document.toObject(Task.class);
                            task.setId(document.getId());

                            // NEW: Filter tasks based on user role and assignment
                            if (!isTaskVisibleToUser(task, userEmail, userRole)) {
                                Log.d(TAG, "Task not visible to user: " + task.getTitle());
                                continue;
                            }

                            // Check if task is active today
                            if (!isTaskActiveToday(task, dayShort, dayStart)) {
                                continue;
                            }

                            // NEW: Check if task is pending for THIS specific user
                            if (!isTaskPendingForUser(task, dayStart, userEmail, userRole)) {
                                Log.d(TAG, "Task completed by user: " + task.getTitle());
                                continue;
                            }

                            pendingTasks.add(task);
                            Log.d(TAG, "Pending task found for user: " + task.getTitle());

                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing task: " + e.getMessage());
                        }
                    }

                    // Show notification if there are pending tasks for THIS user
                    if (!pendingTasks.isEmpty()) {
                        Log.d(TAG, "Showing notification for " + pendingTasks.size() + " pending tasks (user: " + userEmail + ")");
                        NotificationHelper notificationHelper = new NotificationHelper(context);
                        notificationHelper.showPendingTasksNotification(pendingTasks, userEmail);
                    } else {
                        Log.d(TAG, "No pending tasks for this user - no notification shown");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching tasks: " + e.getMessage());
                });
    }

    // NEW: Check if task is visible to the logged-in user
    private boolean isTaskVisibleToUser(Task task, String userEmail, String userRole) {
        String taskType = task.getTaskType() != null ? task.getTaskType().toLowerCase() : "permanent";

        // Admin sees all tasks
        if ("admin".equalsIgnoreCase(userRole)) {
            return true;
        }

        // For regular users:
        if (taskType.equals("permanent")) {
            // All users see permanent tasks
            return true;
        } else {
            // Additional tasks: only if explicitly assigned
            List<String> assignedTo = task.getAssignedTo();
            return assignedTo != null && assignedTo.contains(userEmail);
        }
    }

    // NEW: Check if task is pending for the specific user
    private boolean isTaskPendingForUser(Task task, long dayStart, String userEmail, String userRole) {
        boolean isPermanent = task.getTaskType() != null &&
                task.getTaskType().equalsIgnoreCase("permanent");

        if ("admin".equalsIgnoreCase(userRole)) {
            // Admin: Check global completion status
            // For permanent tasks, check if completed today
            if (isPermanent) {
                long completedAt = task.getCompletedDateMillis();
                if (completedAt > 0) {
                    long dayEnd = dayStart + 86400000L - 1;
                    if (completedAt >= dayStart && completedAt <= dayEnd) {
                        return false; // Completed today
                    }
                }
            }
            // Check if any user has completed it
            return !task.getUserStatus().containsValue("Completed");
        } else {
            // Regular user: Check their personal status
            String userStatus = task.getUserStatus(userEmail);

            if (isPermanent) {
                // For permanent tasks, check if completed by anyone today
                if (task.getUserStatus().containsValue("Completed")) {
                    long completedAt = task.getCompletedDateMillis();
                    if (completedAt > 0) {
                        long dayEnd = dayStart + 86400000L - 1;
                        if (completedAt >= dayStart && completedAt <= dayEnd) {
                            return false; // Completed today by someone
                        }
                    }
                }
            }

            // Otherwise check user's personal status
            return !userStatus.equalsIgnoreCase("Completed");
        }
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