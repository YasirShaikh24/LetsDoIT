// src/main/java/com/example/letsdoit/ViewActivityFragment.java
package com.example.letsdoit;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ViewActivityFragment extends Fragment implements TaskAdapter.TaskActionListener {

    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private List<Task> taskList;
    private List<Task> filteredTaskList;
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView tvEmptyState, tvDateIndicator;
    private RadioGroup rgStatusFilter;
    private ImageButton btnCalendar;

    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String currentFilter = "all";

    // Date filtering - null means "Today"
    private Long selectedDateMillis = null;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

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
        btnCalendar = view.findViewById(R.id.btn_calendar);
        tvDateIndicator = view.findViewById(R.id.tv_date_indicator);

        db = FirebaseFirestore.getInstance();

        taskList = new ArrayList<>();
        filteredTaskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(filteredTaskList, getContext(), this, loggedInUserRole);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(taskAdapter);

        setupFilterListeners();
        setupCalendarButton();
        setupDateIndicatorClick();
        updateDateIndicator();
        loadTasks();

        return view;
    }

    private void setupDateIndicatorClick() {
        // Click on date indicator to reset to today
        tvDateIndicator.setOnClickListener(v -> {
            selectedDateMillis = null;
            updateDateIndicator();
            applyFilter();
            Toast.makeText(getContext(), "Reset to Today", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupCalendarButton() {
        btnCalendar.setOnClickListener(v -> showCalendarDialog());
    }

    private void showCalendarDialog() {
        // If selectedDateMillis is null (Today), pass today's date to calendar
        Long dateToShow = selectedDateMillis;
        if (dateToShow == null) {
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            dateToShow = today.getTimeInMillis();
        }

        CalendarDialogFragment calendarDialog = CalendarDialogFragment.newInstance(dateToShow);
        calendarDialog.setOnDateSelectedListener(new CalendarDialogFragment.OnDateSelectedListener() {
            @Override
            public void onDateSelected(long dateInMillis, String formattedDate) {
                // Check if selected date is today
                Calendar selected = Calendar.getInstance();
                selected.setTimeInMillis(dateInMillis);
                selected.set(Calendar.HOUR_OF_DAY, 0);
                selected.set(Calendar.MINUTE, 0);
                selected.set(Calendar.SECOND, 0);
                selected.set(Calendar.MILLISECOND, 0);

                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR_OF_DAY, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MILLISECOND, 0);

                if (selected.getTimeInMillis() == today.getTimeInMillis()) {
                    // Selected today - set to null
                    selectedDateMillis = null;
                    Toast.makeText(getContext(), "Showing tasks for Today", Toast.LENGTH_SHORT).show();
                } else {
                    // Selected past date
                    selectedDateMillis = dateInMillis;
                    Toast.makeText(getContext(), "Showing tasks for: " + formattedDate, Toast.LENGTH_SHORT).show();
                }

                updateDateIndicator();
                applyFilter();
            }
        });
        calendarDialog.show(getParentFragmentManager(), "calendar_dialog");
    }

    private void updateDateIndicator() {
        if (selectedDateMillis == null) {
            tvDateIndicator.setText("📅 Today");
            tvDateIndicator.setTextColor(getResources().getColor(R.color.primary_purple));
        } else {
            String dateStr = dateFormat.format(selectedDateMillis);
            tvDateIndicator.setText("📅 " + dateStr);
            tvDateIndicator.setTextColor(getResources().getColor(R.color.text_secondary));
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

    private void applyFilter() {
        filteredTaskList.clear();

        for (Task task : taskList) {
            // Get the task status for the selected date
            String taskStatusForDate = getTaskStatusForDate(task, selectedDateMillis);

            boolean matchesStatus = currentFilter.equals("all") ||
                    (taskStatusForDate != null && taskStatusForDate.toLowerCase().equals(currentFilter));

            boolean matchesDate = isTaskVisibleForSelectedDate(task);

            if (matchesStatus && matchesDate) {
                filteredTaskList.add(task);
            }
        }

        taskAdapter.notifyDataSetChanged();

        if (filteredTaskList.isEmpty()) {
            String filterName = currentFilter.substring(0, 1).toUpperCase() + currentFilter.substring(1);
            String dateInfo = selectedDateMillis == null ? "today" : dateFormat.format(selectedDateMillis);

            if (currentFilter.equals("all")) {
                tvEmptyState.setText("No tasks found for " + dateInfo);
            } else {
                tvEmptyState.setText("No " + filterName + " tasks found for " + dateInfo);
            }
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Get the task status as it was on the selected date
     */
    private String getTaskStatusForDate(Task task, Long dateMillis) {
        if (dateMillis == null) {
            // For today, return current status
            return task.getStatus();
        }

        // For past dates, we return the current status
        // The real-time logic is: if a task was completed today,
        // it should still show as "Pending" or "In Progress" for past dates
        // However, since we don't store historical status changes,
        // we'll show current status for now
        // To implement true historical status, you'd need to store status change timestamps

        return task.getStatus();
    }

    /**
     * Check if task should be visible for the selected date
     */
    private boolean isTaskVisibleForSelectedDate(Task task) {
        Calendar selectedCal = Calendar.getInstance();

        if (selectedDateMillis == null) {
            // Today - show all active tasks
            selectedCal.set(Calendar.HOUR_OF_DAY, 0);
            selectedCal.set(Calendar.MINUTE, 0);
            selectedCal.set(Calendar.SECOND, 0);
            selectedCal.set(Calendar.MILLISECOND, 0);
        } else {
            // Past date
            selectedCal.setTimeInMillis(selectedDateMillis);
            selectedCal.set(Calendar.HOUR_OF_DAY, 0);
            selectedCal.set(Calendar.MINUTE, 0);
            selectedCal.set(Calendar.SECOND, 0);
            selectedCal.set(Calendar.MILLISECOND, 0);
        }

        try {
            // Task must be created on or before the selected date
            Calendar taskCreatedCal = Calendar.getInstance();
            taskCreatedCal.setTimeInMillis(task.getTimestamp());
            taskCreatedCal.set(Calendar.HOUR_OF_DAY, 0);
            taskCreatedCal.set(Calendar.MINUTE, 0);
            taskCreatedCal.set(Calendar.SECOND, 0);
            taskCreatedCal.set(Calendar.MILLISECOND, 0);

            if (taskCreatedCal.after(selectedCal)) {
                return false; // Task created after selected date
            }

            // Check date range
            String startDate = task.getStartDate();
            String endDate = task.getEndDate();

            // If no date range specified, show task if created before/on selected date
            if ((startDate == null || startDate.isEmpty()) && (endDate == null || endDate.isEmpty())) {
                return true;
            }

            // Check if selected date falls within task date range
            if (startDate != null && !startDate.isEmpty()) {
                try {
                    Calendar startCal = Calendar.getInstance();
                    startCal.setTime(dateFormat.parse(startDate));
                    startCal.set(Calendar.HOUR_OF_DAY, 0);
                    startCal.set(Calendar.MINUTE, 0);
                    startCal.set(Calendar.SECOND, 0);
                    startCal.set(Calendar.MILLISECOND, 0);

                    if (selectedCal.before(startCal)) {
                        return false; // Selected date before start date
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing start date: " + e.getMessage());
                }
            }

            if (endDate != null && !endDate.isEmpty()) {
                try {
                    Calendar endCal = Calendar.getInstance();
                    endCal.setTime(dateFormat.parse(endDate));
                    endCal.set(Calendar.HOUR_OF_DAY, 23);
                    endCal.set(Calendar.MINUTE, 59);
                    endCal.set(Calendar.SECOND, 59);
                    endCal.set(Calendar.MILLISECOND, 999);

                    if (selectedCal.after(endCal)) {
                        return false; // Selected date after end date
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing end date: " + e.getMessage());
                }
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error in date filtering: " + e.getMessage());
            return true;
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
        if ("user".equals(loggedInUserRole)) {
            // Check if viewing past date
            if (selectedDateMillis != null) {
                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR_OF_DAY, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MILLISECOND, 0);

                Calendar selected = Calendar.getInstance();
                selected.setTimeInMillis(selectedDateMillis);
                selected.set(Calendar.HOUR_OF_DAY, 0);
                selected.set(Calendar.MINUTE, 0);
                selected.set(Calendar.SECOND, 0);
                selected.set(Calendar.MILLISECOND, 0);

                if (selected.before(today)) {
                    // Past date - Show read-only
                    showReadOnlyTaskDialog(task);
                    return;
                }
            }
            // Today - Allow editing
            showTaskDetailDialog(task, position);
        }
    }

    @Override
    public void onTaskEditClick(Task task, int position) {
        if ("admin".equals(loggedInUserRole)) {
            if (task.getId() != null) {
                android.content.Intent intent = new android.content.Intent(getActivity(), EditTaskActivity.class);
                intent.putExtra(EXTRA_TASK_ID, task.getId());
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Error: Cannot edit task without an ID.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onTaskDeleteClick(Task task, int position) {
        showDeleteConfirmationDialog(task, position);
    }

    private void showReadOnlyTaskDialog(Task task) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_task_readonly);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCancelable(true);

        TextView tvDialogTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView tvDialogDescription = dialog.findViewById(R.id.tv_dialog_description);
        TextView tvDialogDates = dialog.findViewById(R.id.tv_dialog_dates);
        TextView tvDialogRemarks = dialog.findViewById(R.id.tv_dialog_remarks);
        TextView tvDialogStatus = dialog.findViewById(R.id.tv_dialog_status);
        TextView tvDialogAiCount = dialog.findViewById(R.id.tv_dialog_ai_count);
        Button btnClose = dialog.findViewById(R.id.btn_close);

        tvDialogTitle.setText(task.getTitle());
        tvDialogDescription.setText(task.getDescription());

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

        String status = task.getStatus();
        tvDialogStatus.setText("Status: " + (status != null ? status.toUpperCase() : "PENDING"));

        if (task.isRequireAiCount()) {
            String aiCountValue = task.getAiCountValue();
            if (aiCountValue != null && !aiCountValue.isEmpty()) {
                tvDialogAiCount.setText("AI Count: " + aiCountValue);
            } else {
                tvDialogAiCount.setText("AI Count: Not submitted");
            }
            tvDialogAiCount.setVisibility(View.VISIBLE);
        } else {
            tvDialogAiCount.setVisibility(View.GONE);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showTaskDetailDialog(Task task, int position) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_task_detail);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCancelable(true);

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

        tvDialogTitle.setText(task.getTitle());
        tvDialogDescription.setText(task.getDescription());

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
            llAiCountSection.setVisibility(View.VISIBLE);
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

        String currentStatus = task.getStatus().toLowerCase();
        if (currentStatus.equals("pending")) {
            rbPending.setChecked(true);
        } else if (currentStatus.equals("in progress")) {
            rbInProgress.setChecked(true);
        } else if (currentStatus.equals("completed")) {
            rbCompleted.setChecked(true);
        }

        btnSubmit.setOnClickListener(v -> {
            int selectedStatusId = rgDialogStatus.getCheckedRadioButtonId();
            if (selectedStatusId == -1) {
                Toast.makeText(getContext(), "Please select a status", Toast.LENGTH_SHORT).show();
                return;
            }

            RadioButton selectedRadioButton = dialog.findViewById(selectedStatusId);
            String newStatus = selectedRadioButton.getText().toString();

            String aiCountInput = "";
            if (task.isRequireAiCount()) {
                aiCountInput = etAiCount.getText().toString().trim();
                if (newStatus.equalsIgnoreCase("✅ Completed")) {
                    if (aiCountInput.isEmpty()) {
                        etAiCount.setError("AI Count is required for completion");
                        Toast.makeText(getContext(), "Please enter AI Count to mark as completed", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }

            String cleanStatus = newStatus.replace("⏱️", "").replace("🔧", "").replace("✅", "").trim();
            updateTaskInFirestore(task, cleanStatus, aiCountInput, position, dialog);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void updateTaskInFirestore(Task task, String newStatus, String aiCountValue, int position, Dialog dialog) {
        if (task.getId() == null) {
            Toast.makeText(getContext(), "Error: Task ID not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("status", newStatus);

        if (task.isRequireAiCount()) {
            if (aiCountValue != null && !aiCountValue.isEmpty()) {
                update.put("aiCountValue", aiCountValue);
            } else if (newStatus.equalsIgnoreCase("completed")) {
                update.put("aiCountValue", "");
            }
        }

        db.collection("tasks").document(task.getId())
                .update(update)
                .addOnSuccessListener(aVoid -> {
                    task.setStatus(newStatus);
                    if (task.isRequireAiCount() && aiCountValue != null) {
                        task.setAiCountValue(aiCountValue);
                    }

                    for (Task t : taskList) {
                        if (t.getId().equals(task.getId())) {
                            t.setStatus(newStatus);
                            if (task.isRequireAiCount() && aiCountValue != null) {
                                t.setAiCountValue(aiCountValue);
                            }
                            break;
                        }
                    }

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
                    taskList.remove(task);
                    filteredTaskList.remove(position);
                    taskAdapter.notifyItemRemoved(position);
                    Toast.makeText(getContext(), "Task deleted successfully!", Toast.LENGTH_SHORT).show();

                    if (filteredTaskList.isEmpty()) {
                        String filterName = currentFilter.substring(0, 1).toUpperCase() + currentFilter.substring(1);
                        String dateInfo = selectedDateMillis == null ? "today" : dateFormat.format(selectedDateMillis);
                        if (currentFilter.equals("all")) {
                            tvEmptyState.setText("No tasks for " + dateInfo);
                        } else {
                            tvEmptyState.setText("No " + filterName + " tasks for " + dateInfo);
                        }
                        tvEmptyState.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    }
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