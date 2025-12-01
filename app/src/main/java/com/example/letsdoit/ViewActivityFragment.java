// src/main/java/com/example/letsdoit/ViewActivityFragment.java
package com.example.letsdoit;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ADDED CalendarDialogFragment.OnDateSelectedListener
public class ViewActivityFragment extends Fragment implements TaskAdapter.TaskActionListener, CalendarDialogFragment.OnDateSelectedListener {

    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    // taskList holds the actual data from Firestore, including current status
    private List<Task> taskList;
    // filteredTaskList holds cloned tasks with historical status set for display
    private List<Task> filteredTaskList;
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private RadioGroup rgStatusFilter;
    private TextView tvDateIndicator; // NEW
    private ImageButton btnCalendar; // NEW

    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String currentFilter = "all";

    // NEW FIELD: Stores the selected date in milliseconds (or -1 for 'Today')
    private long selectedDateMillis = -1;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private final SimpleDateFormat dateIndicatorFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    private static final String TAG = "ViewActivityFragment";

    public static final String EXTRA_TASK_ID = "extra_task_id";

    public static ViewActivityFragment newInstance(String email, String role) {
        ViewActivityFragment fragment = new ViewActivityFragment();
        Bundle args = new Bundle();
        args.putString(LoginActivity.EXTRA_USER_EMAIL, email);
        args.putString(LoginActivity.EXTRA_USER_ROLE, role);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_view_activity, container, false);

        if (getArguments() != null) {
            loggedInUserEmail = getArguments().getString(LoginActivity.EXTRA_USER_EMAIL);
            loggedInUserRole = getArguments().getString(LoginActivity.EXTRA_USER_ROLE);
        }

        recyclerView = view.findViewById(R.id.recycler_view_tasks);
        progressBar = view.findViewById(R.id.progress_bar);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);
        rgStatusFilter = view.findViewById(R.id.rg_status_filter);
        tvDateIndicator = view.findViewById(R.id.tv_date_indicator);
        btnCalendar = view.findViewById(R.id.btn_calendar);

        db = FirebaseFirestore.getInstance();

        taskList = new ArrayList<>();
        filteredTaskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(filteredTaskList, getContext(), this, loggedInUserRole);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(taskAdapter);

        setupFilterListeners();
        setupCalendar();
        loadTasks();

        return view;
    }

    private void setupCalendar() {
        // Initialize with Today's date
        selectedDateMillis = -1;
        updateDateIndicator();

        btnCalendar.setOnClickListener(v -> {
            // Pass the currently selected date to the dialog, if one exists
            CalendarDialogFragment dialogFragment = CalendarDialogFragment.newInstance(selectedDateMillis != -1 ? selectedDateMillis : null);
            dialogFragment.setOnDateSelectedListener(this);
            dialogFragment.show(getParentFragmentManager(), "CalendarDialog");
        });
    }

    // Implementation of date selection listener
    @Override
    public void onDateSelected(long dateInMillis, String formattedDate) {
        // Set the selected date and reload/re-filter tasks
        selectedDateMillis = dateInMillis;
        updateDateIndicator();
        applyFilter();
    }

    // Helper to update the TextView displaying the selected date
    private void updateDateIndicator() {
        if (selectedDateMillis == -1) {
            tvDateIndicator.setText("📅 Today");
        } else {
            String formattedDate = dateIndicatorFormat.format(new Date(selectedDateMillis));
            tvDateIndicator.setText("📅 " + formattedDate);
        }
    }

    private void setupFilterListeners() {
        rgStatusFilter.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_filter_all) {
                currentFilter = "all";
            } else if (checkedId == R.id.rb_filter_pending) {
                currentFilter = "pending";
            } else if (checkedId == R.id.rb_filter_in_progress) {
                currentFilter = "in progress";
            } else if (checkedId == R.id.rb_filter_completed) {
                currentFilter = "completed";
            }
            applyFilter();
        });
    }

    // Helper to get the start of the day for filtering
    private long getDayStartMillis(long dateMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    // NEW: Helper method to get the task status as it was on the filter date
    private String getTaskStatusOnDate(Task task, long filterDateMillis) {
        String currentStatus = task.getStatus();

        // If filtering for Today or Future, current status is always correct
        if (filterDateMillis == -1 || getDayStartMillis(filterDateMillis) >= getDayStartMillis(System.currentTimeMillis())) {
            return currentStatus;
        }

        // --- Historical Status Logic (Past Date Selected) ---
        long filterDayStart = getDayStartMillis(filterDateMillis);
        long filterDayEnd = filterDayStart + (24 * 60 * 60 * 1000L) - 1; // End of filter day

        if (task.getTaskType().equalsIgnoreCase("permanent")) {
            long completionTime = task.getCompletedDateMillis();

            // Permanent Task Logic: Status is ONLY "Completed" if completed on this specific date.
            if (completionTime > filterDayStart && completionTime <= filterDayEnd) {
                return "Completed";
            }
            // Otherwise, for any other past date, its historical status is always "Pending"
            return "Pending";
        }

        // --- Additional Task Logic (Historical status is fixed until a future change) ---
        if (currentStatus.equalsIgnoreCase("Completed")) {
            long completionTime = task.getCompletedDateMillis();

            // If completion happened AFTER the filter day ended, the status on filter day was Pending/In Progress.
            if (completionTime > 0 && completionTime > filterDayEnd) {
                return "Pending";
            }
            // Otherwise, it was completed on or before the filter date.
            return "Completed";
        }

        // If the current status is Pending/In Progress, we assume "Pending" for past dates.
        return "Pending";
    }


    // MODIFIED: applyFilter now applies date filtering and updates status for display
    private void applyFilter() {
        // 1. Apply Date Filter
        List<Task> dateFilteredList = applyDateFilter(taskList);

        // 2. Prepare for Status Filter and Display
        filteredTaskList.clear();

        long filterDateMillis = selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;

        for (Task originalTask : dateFilteredList) {
            // Clone the task to modify its status for display without affecting the original list
            Task taskForDisplay = new Task();
            // Manually copy all necessary fields for display and internal logic
            taskForDisplay.setId(originalTask.getId());
            taskForDisplay.setTitle(originalTask.getTitle());
            taskForDisplay.setDescription(originalTask.getDescription());
            taskForDisplay.setPriority(originalTask.getPriority());
            taskForDisplay.setRemarks(originalTask.getRemarks());
            taskForDisplay.setAssignedTo(originalTask.getAssignedTo());
            taskForDisplay.setStartDate(originalTask.getStartDate());
            taskForDisplay.setEndDate(originalTask.getEndDate());
            taskForDisplay.setRequireAiCount(originalTask.isRequireAiCount());
            taskForDisplay.setAiCountValue(originalTask.getAiCountValue());
            taskForDisplay.setTimestamp(originalTask.getTimestamp());
            taskForDisplay.setTaskType(originalTask.getTaskType());
            taskForDisplay.setCompletedDateMillis(originalTask.getCompletedDateMillis());

            // CRITICAL STEP: Get the historical status
            String historicalStatus = getTaskStatusOnDate(originalTask, filterDateMillis);
            taskForDisplay.setStatus(historicalStatus);

            // 3. Apply Status Filter
            if (currentFilter.equals("all")) {
                filteredTaskList.add(taskForDisplay);
            } else {
                String taskStatus = taskForDisplay.getStatus() != null ? taskForDisplay.getStatus().toLowerCase() : "pending";
                if (taskStatus.equals(currentFilter)) {
                    filteredTaskList.add(taskForDisplay);
                }
            }
        }

        taskAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    // MODIFIED: applyDateFilter for Permanent/Additional logic
    private List<Task> applyDateFilter(List<Task> unfilteredList) {
        // If selectedDateMillis is -1, use Today's date.
        long filterDateMillis = selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
        long filterDayStart = getDayStartMillis(filterDateMillis);

        List<Task> dateFilteredList = new ArrayList<>();

        for (Task task : unfilteredList) {
            String taskType = task.getTaskType().toLowerCase();

            if (taskType.equals("permanent")) {
                // Permanent tasks are visible if they were created before the end of the filter day.
                if (task.getTimestamp() < filterDayStart + (24 * 60 * 60 * 1000L)) {
                    dateFilteredList.add(task);
                }
            } else if (taskType.equals("additional")) {
                // Additional tasks are visible STRICTLY ONLY within their date range.
                try {
                    String startDateStr = task.getStartDate();
                    String endDateStr = task.getEndDate();

                    if (startDateStr == null || startDateStr.isEmpty() || endDateStr == null || endDateStr.isEmpty()) {
                        continue;
                    }

                    long startDateMillis = dateFormat.parse(startDateStr).getTime();
                    long endDateMillis = dateFormat.parse(endDateStr).getTime();

                    long startDayStart = getDayStartMillis(startDateMillis);
                    long endDayStart = getDayStartMillis(endDateMillis);

                    // A task is visible if the selected day is within the start/end range (inclusive)
                    if (filterDayStart >= startDayStart && filterDayStart <= endDayStart) {
                        dateFilteredList.add(task);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing task dates for filtering: " + e.getMessage());
                }
            }
        }

        return dateFilteredList;
    }

    // Extracted Empty State logic (kept as is)
    private void updateEmptyState() {
        if (filteredTaskList.isEmpty()) {
            String filterName = currentFilter.substring(0, 1).toUpperCase() + currentFilter.substring(1);
            if (currentFilter.equals("all")) {
                tvEmptyState.setText("No tasks found for this date.");
            } else {
                tvEmptyState.setText("No " + filterName + " tasks found for this date.");
            }
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void loadTasks() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        if ("admin".equals(loggedInUserRole)) {
            loadAllTasks();
        } else {
            loadUserTasks();
        }
    }

    private void loadAllTasks() {
        db.collection("tasks")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    taskList.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Task task = document.toObject(Task.class);
                            task.setId(document.getId());
                            taskList.add(task);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing task: " + document.getId(), e);
                        }
                    }

                    applyFilter();
                    progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading tasks for admin", e);
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Error loading tasks: " + e.getMessage());
                    tvEmptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                });
    }

    private void loadUserTasks() {
        db.collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    taskList.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Task task = document.toObject(Task.class);
                            task.setId(document.getId());

                            List<String> assignedTo = task.getAssignedTo();
                            if (assignedTo != null && assignedTo.contains(loggedInUserEmail)) {
                                taskList.add(task);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing task: " + document.getId(), e);
                        }
                    }

                    Collections.sort(taskList, new Comparator<Task>() {
                        @Override
                        public int compare(Task t1, Task t2) {
                            return Long.compare(t2.getTimestamp(), t1.getTimestamp());
                        }
                    });

                    applyFilter();
                    progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading tasks for user", e);
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Error loading tasks: " + e.getMessage());
                    tvEmptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                });
    }

    @Override
    public void onTaskStatusClick(Task task, int position) {
        // Show custom dialog for users
        if ("user".equals(loggedInUserRole)) {
            long todayStart = getDayStartMillis(System.currentTimeMillis());
            boolean isTaskActiveToday = true;

            // Rule 1: Cannot edit status if viewing a past date.
            if (selectedDateMillis != -1 && getDayStartMillis(selectedDateMillis) < todayStart) {
                isTaskActiveToday = false;
            }

            // Rule 2: Cannot edit status for Additional task if deadline passed and it's not completed.
            if (task.getTaskType().equalsIgnoreCase("additional")) {
                try {
                    String endDateStr = task.getEndDate();
                    if (endDateStr != null && !endDateStr.isEmpty()) {
                        long endDateMillis = dateFormat.parse(endDateStr).getTime();
                        long endDayStart = getDayStartMillis(endDateMillis);

                        if (todayStart > endDayStart && !task.getStatus().equalsIgnoreCase("Completed")) {
                            isTaskActiveToday = false;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking task active status: " + e.getMessage());
                }
            }


            if (isTaskActiveToday) {
                // Get the *original* task from the main list to update Firestore.
                Task originalTask = taskList.stream()
                        .filter(t -> t.getId().equals(task.getId()))
                        .findFirst().orElse(null);

                if (originalTask != null) {
                    showTaskDetailDialog(originalTask, position);
                } else {
                    Toast.makeText(getContext(), "Error: Original task not found.", Toast.LENGTH_SHORT).show();
                }

            } else {
                // If the task is past due or viewing a past date, show a read-only dialog
                showReadOnlyDialog(task);
            }
        }
    }

    @Override
    public void onTaskEditClick(Task task, int position) {
        // Admin edit functionality - open EditTaskActivity
        if ("admin".equals(loggedInUserRole) && task.getId() != null) {
            android.content.Intent intent = new android.content.Intent(getActivity(), EditTaskActivity.class);
            intent.putExtra(EXTRA_TASK_ID, task.getId());
            startActivity(intent);
        } else {
            Toast.makeText(getContext(), "Error: Cannot edit task without an ID.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onTaskDeleteClick(Task task, int position) {
        showDeleteConfirmationDialog(task, position);
    }

    // Read-only dialog (kept as is)
    private void showReadOnlyDialog(Task task) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_task_readonly);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCancelable(true);

        TextView tvDialogTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView tvDialogDescription = dialog.findViewById(R.id.tv_dialog_description);
        TextView tvDialogStatus = dialog.findViewById(R.id.tv_dialog_status);
        TextView tvDialogDates = dialog.findViewById(R.id.tv_dialog_dates);
        TextView tvDialogRemarks = dialog.findViewById(R.id.tv_dialog_remarks);
        TextView tvDialogAiCount = dialog.findViewById(R.id.tv_dialog_ai_count);
        Button btnClose = dialog.findViewById(R.id.btn_close);

        tvDialogTitle.setText(task.getTitle());
        tvDialogDescription.setText(task.getDescription());
        tvDialogStatus.setText("Status: " + task.getStatus().toUpperCase());

        String startDate = task.getStartDate();
        String endDate = task.getEndDate();
        if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
            String dateText = "Dates: ";
            dateText += (startDate != null && !startDate.isEmpty()) ? startDate : "?";
            dateText += " - ";
            dateText += (endDate != null && !endDate.isEmpty()) ? endDate : "?";
            tvDialogDates.setText(dateText);
            tvDialogDates.setVisibility(View.VISIBLE);
        } else {
            tvDialogDates.setVisibility(View.GONE);
        }

        String remarks = task.getRemarks();
        if (remarks != null && !remarks.isEmpty()) {
            tvDialogRemarks.setText("Remarks: " + remarks);
            tvDialogRemarks.setVisibility(View.VISIBLE);
        } else {
            tvDialogRemarks.setVisibility(View.GONE);
        }

        if (task.isRequireAiCount()) {
            String aiCountValue = task.getAiCountValue();
            tvDialogAiCount.setText("AI Count: " + (aiCountValue != null && !aiCountValue.isEmpty() ? aiCountValue : "N/A (Required)"));
            tvDialogAiCount.setVisibility(View.VISIBLE);
        } else {
            tvDialogAiCount.setVisibility(View.GONE);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }


    // Custom dialog for task details with AI Count editing (kept as is, but operates on original task)
    private void showTaskDetailDialog(Task task, int position) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_task_detail);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCancelable(true);

        // Find views
        TextView tvDialogTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView tvDialogDescription = dialog.findViewById(R.id.tv_dialog_description);
        TextView tvDialogDates = dialog.findViewById(R.id.tv_dialog_dates);
        TextView tvDialogRemarks = dialog.findViewById(R.id.tv_dialog_remarks);
        android.widget.LinearLayout llAiCountSection = dialog.findViewById(R.id.ll_ai_count_section);
        TextView tvAiCountLabel = dialog.findViewById(R.id.tv_ai_count_label);
        TextInputEditText etAiCount = dialog.findViewById(R.id.et_ai_count);
        RadioGroup rgDialogStatus = dialog.findViewById(R.id.rg_dialog_status);
        RadioButton rbPending = dialog.findViewById(R.id.rb_dialog_pending);
        RadioButton rbInProgress = dialog.findViewById(R.id.rb_dialog_in_progress);
        RadioButton rbCompleted = dialog.findViewById(R.id.rb_dialog_completed);
        Button btnSubmit = dialog.findViewById(R.id.btn_submit);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);

        // Populate task details (using current status from original task)
        tvDialogTitle.setText(task.getTitle());
        tvDialogDescription.setText(task.getDescription());

        // Dates
        String startDate = task.getStartDate();
        String endDate = task.getEndDate();
        if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
            String dateText = "Dates: ";
            dateText += (startDate != null && !startDate.isEmpty()) ? startDate : "?";
            dateText += " - ";
            dateText += (endDate != null && !endDate.isEmpty()) ? endDate : "?";
            tvDialogDates.setText(dateText);
            tvDialogDates.setVisibility(View.VISIBLE);
        } else {
            tvDialogDates.setVisibility(View.GONE);
        }

        // Remarks
        String remarks = task.getRemarks();
        if (remarks != null && !remarks.isEmpty()) {
            tvDialogRemarks.setText("Remarks: " + remarks);
            tvDialogRemarks.setVisibility(View.VISIBLE);
        } else {
            tvDialogRemarks.setVisibility(View.GONE);
        }

        // AI Count section - ALWAYS show if task requires AI Count
        if (task.isRequireAiCount()) {
            llAiCountSection.setVisibility(View.VISIBLE);

            // Pre-fill if already exists (for editing)
            String existingAiCount = task.getAiCountValue();
            if (existingAiCount != null && !existingAiCount.isEmpty()) {
                etAiCount.setText(existingAiCount);
                tvAiCountLabel.setText("AI Count (You can edit)");
            } else {
                etAiCount.setText("");
                tvAiCountLabel.setText("Enter AI Count (Required for Completion)");
            }
        } else {
            llAiCountSection.setVisibility(View.GONE);
        }

        // Set current status
        String currentStatus = task.getStatus().toLowerCase();
        if (currentStatus.equals("pending")) {
            rbPending.setChecked(true);
        } else if (currentStatus.equals("in progress")) {
            rbInProgress.setChecked(true);
        } else if (currentStatus.equals("completed")) {
            rbCompleted.setChecked(true);
        }

        // Submit button
        btnSubmit.setOnClickListener(v -> {
            int selectedStatusId = rgDialogStatus.getCheckedRadioButtonId();
            if (selectedStatusId == -1) {
                Toast.makeText(getContext(), "Please select a status", Toast.LENGTH_SHORT).show();
                return;
            }

            RadioButton selectedRadioButton = dialog.findViewById(selectedStatusId);
            String newStatus = selectedRadioButton.getText().toString();

            // Get AI Count input (if field is visible)
            String aiCountInput = "";
            if (task.isRequireAiCount()) {
                aiCountInput = etAiCount.getText().toString().trim();

                // Validate AI Count if status is "Completed"
                if (newStatus.equalsIgnoreCase("✅ Completed")) {
                    if (aiCountInput.isEmpty()) {
                        etAiCount.setError("AI Count is required for completion");
                        Toast.makeText(getContext(), "Please enter AI Count to mark as completed", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }

            // Clean status text (remove emojis)
            String cleanStatus = newStatus.replace("⏱️", "").replace("🔧", "").replace("✅", "").trim();

            // Update task with new status and AI Count
            updateTaskInFirestore(task, cleanStatus, aiCountInput, position, dialog);
        });

        // Cancel button
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // MODIFIED: updateTaskInFirestore to handle completedDateMillis (Fix applied here)
    private void updateTaskInFirestore(Task task, String newStatus, String aiCountValue, int position, Dialog dialog) {
        if (task.getId() == null) {
            Toast.makeText(getContext(), "Error: Task ID not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("status", newStatus);

        final long finalCompletionTime; // DECLARED AS FINAL (Fix)
        if (newStatus.equalsIgnoreCase("Completed")) {
            finalCompletionTime = System.currentTimeMillis();
            update.put("completedDateMillis", finalCompletionTime);
        } else {
            finalCompletionTime = 0;
            // If status is changed away from Completed, clear the completion date
            update.put("completedDateMillis", 0);
        }

        // Always update AI Count if the field was visible and has value
        if (task.isRequireAiCount()) {
            if (aiCountValue != null && !aiCountValue.isEmpty()) {
                update.put("aiCountValue", aiCountValue);
            }
        }

        db.collection("tasks").document(task.getId())
                .update(update)
                .addOnSuccessListener(aVoid -> {
                    // Update local task object
                    task.setStatus(newStatus);
                    task.setCompletedDateMillis(finalCompletionTime); // Reference to final variable
                    if (aiCountValue != null && !aiCountValue.isEmpty()) {
                        task.setAiCountValue(aiCountValue);
                    }

                    // Find and update in taskList
                    for (Task t : taskList) {
                        if (t.getId().equals(task.getId())) {
                            t.setStatus(newStatus);
                            t.setCompletedDateMillis(finalCompletionTime); // Reference to final variable
                            if (aiCountValue != null && !aiCountValue.isEmpty()) {
                                t.setAiCountValue(aiCountValue);
                            }
                            break;
                        }
                    }

                    // Reapply filter to update the view
                    applyFilter();

                    String message = "Task updated successfully!";
                    if (task.isRequireAiCount() && aiCountValue != null && !aiCountValue.isEmpty()) {
                        message += " AI Count: " + aiCountValue;
                    }
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating task " + task.getId(), e);
                    Toast.makeText(getContext(), "Failed to update task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showDeleteConfirmationDialog(Task task, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteTaskFromFirestore(task, position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTaskFromFirestore(Task task, int position) {
        if (task.getId() == null) {
            Toast.makeText(getContext(), "Error: Task ID not found for deletion.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("tasks").document(task.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Update the main task list
                    taskList.remove(task);

                    // Re-run filter to correctly update UI/empty state
                    applyFilter();

                    Toast.makeText(getContext(), "Task deleted successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting task " + task.getId(), e);
                    Toast.makeText(getContext(), "Failed to delete task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (loggedInUserEmail != null) {
            loadTasks();
        }
    }
}