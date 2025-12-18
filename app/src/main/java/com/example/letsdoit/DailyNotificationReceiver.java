// src/main/java/com/example/letsdoit/DailyNotificationReceiver.java

package com.example.letsdoit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
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

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private final SimpleDateFormat dayFormat =
            new SimpleDateFormat("EEE", Locale.US);

    // For daily-status docs: yyyy-MM-dd
    private final SimpleDateFormat storageDateKeyFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    // SharedPreferences Constants (Must match LoginActivity)
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROLE = "role";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Daily notification triggered at: " + new Date().toString());

        // Reschedule for tomorrow (setExactAndAllowWhileIdle, not repeating)
        NotificationHelper notificationHelper = new NotificationHelper(context);
        notificationHelper.scheduleDailyNotification();
        Log.d(TAG, "Rescheduled notification for tomorrow");

        // Check if user is logged in
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false);
        if (!isLoggedIn) {
            Log.d(TAG, "No user logged in - skipping notification");
            return;
        }

        // Get logged-in user details
        String loggedInUserEmail = prefs.getString(KEY_EMAIL, null);
        String loggedInUserRole = prefs.getString(KEY_ROLE, null);

        if (loggedInUserEmail == null || loggedInUserEmail.isEmpty()) {
            Log.d(TAG, "User email not found - skipping notification");
            return;
        }

        Log.d(TAG, "Checking tasks for logged-in user: "
                + loggedInUserEmail + " (Role: " + loggedInUserRole + ")");

        // Fetch and show pending tasks for this specific user
        fetchPendingTasksForUser(context, loggedInUserEmail, loggedInUserRole);
    }

    /**
     * Fetch tasks visible to this user, filter by “active today”, then check each
     * task's dailyStatus/{yyyy-MM-dd} doc to decide if it is pending.
     */
    private void fetchPendingTasksForUser(Context context,
                                          String userEmail,
                                          String userRole) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        long todayMillis = System.currentTimeMillis();
        long dayStart = getDayStartMillis(todayMillis);
        String dayShort =
                dayFormat.format(new Date(todayMillis))
                        .substring(0, 3).toLowerCase(Locale.US);
        String dateKey = storageDateKeyFormat.format(new Date(dayStart));

        Log.d(TAG, "Checking tasks for: " + new Date(todayMillis)
                + " (day: " + dayShort + ", dateKey: " + dateKey + ")");

        db.collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    List<Task> visibleAndActiveTasks = new ArrayList<>();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Task task = document.toObject(Task.class);
                            task.setId(document.getId());

                            // 1. Visibility for this user
                            if (!isTaskVisibleToUser(task, userEmail, userRole)) {
                                Log.d(TAG, "Task not visible to user: " + task.getTitle());
                                continue;
                            }

                            // 2. Active today?
                            if (!isTaskActiveToday(task, dayShort, dayStart)) {
                                continue;
                            }

                            visibleAndActiveTasks.add(task);

                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing task: " + e.getMessage());
                        }
                    }

                    if (visibleAndActiveTasks.isEmpty()) {
                        Log.d(TAG, "No visible/active tasks for this user today");
                        return;
                    }

                    // Now, for each task, read its dailyStatus/{dateKey} doc to know if it is pending
                    List<Task> pendingTasks = new ArrayList<>();

                    List<com.google.android.gms.tasks.Task<DocumentSnapshot>> statusTasks =
                            new ArrayList<>();

                    for (Task task : visibleAndActiveTasks) {
                        com.google.android.gms.tasks.Task<DocumentSnapshot> statusTask =
                                db.collection("tasks")
                                        .document(task.getId())
                                        .collection("dailyStatus")
                                        .document(dateKey)
                                        .get()
                                        .addOnSuccessListener(snapshot -> {
                                            boolean isPending = isTaskPendingOnDate(task, snapshot);
                                            if (isPending) {
                                                pendingTasks.add(task);
                                                Log.d(TAG, "Pending task found for user: "
                                                        + task.getTitle());
                                            } else {
                                                Log.d(TAG, "Task completed already today: "
                                                        + task.getTitle());
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            // On failure reading status, be conservative and treat as pending.
                                            Log.e(TAG,
                                                    "Error reading dailyStatus for task "
                                                            + task.getId() + ": " + e.getMessage());
                                            pendingTasks.add(task);
                                        });

                        statusTasks.add(statusTask);
                    }

                    // When all dailyStatus reads are done, show notification if needed.
                    Tasks.whenAllComplete(statusTasks)
                            .addOnSuccessListener(all -> {
                                if (!pendingTasks.isEmpty()) {
                                    Log.d(TAG, "Showing notification for "
                                            + pendingTasks.size()
                                            + " pending tasks (user: " + userEmail + ")");
                                    NotificationHelper notificationHelper =
                                            new NotificationHelper(context);
                                    notificationHelper.showPendingTasksNotification(
                                            pendingTasks, userEmail);
                                } else {
                                    Log.d(TAG,
                                            "No pending tasks for this user - no notification shown");
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG,
                                        "Error waiting for daily status tasks: "
                                                + e.getMessage());
                            });

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching tasks: " + e.getMessage());
                });
    }

    /**
     * A task is pending on that date if its dailyStatus doc:
     * - does not exist, or
     * - exists with status != "Completed", or
     * - requires AI count but aiCountValue is empty.
     */
    private boolean isTaskPendingOnDate(Task task, DocumentSnapshot snapshot) {
        if (snapshot != null && snapshot.exists()) {
            TaskDayStatus dayStatus = snapshot.toObject(TaskDayStatus.class);
            if (dayStatus == null) {
                return true;
            }

            String status = dayStatus.getStatus();
            String aiCount = dayStatus.getAiCountValue();

            if ("Completed".equalsIgnoreCase(status)) {
                if (task.isRequireAiCount()) {
                    // If AI count required but missing, treat as pending
                    return aiCount == null || aiCount.isEmpty();
                }
                // Completed and AI count ok => not pending
                return false;
            }

            // Any non-completed status => pending
            return true;
        }

        // No record for that date => Pending (not yet touched)
        return true;
    }

    /**
     * Check if task is visible for this user.
     */
    private boolean isTaskVisibleToUser(Task task, String userEmail, String userRole) {
        String taskType =
                task.getTaskType() != null
                        ? task.getTaskType().toLowerCase(Locale.US)
                        : "permanent";

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

    /**
     * Check if task is active on "today" based on type.
     */
    private boolean isTaskActiveToday(Task task, String dayShort, long dayStart) {
        String taskType =
                task.getTaskType() != null
                        ? task.getTaskType().toLowerCase(Locale.US)
                        : "permanent";

        if (taskType.equals("permanent")) {
            // Check if today is in selected days
            List<String> selectedDays = task.getSelectedDays();
            if (selectedDays != null) {
                for (String day : selectedDays) {
                    if (day != null &&
                            day.trim().toLowerCase(Locale.US).equals(dayShort)) {
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
                if (startDateStr == null || startDateStr.isEmpty()
                        || endDateStr == null || endDateStr.isEmpty()) {
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
