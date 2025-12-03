// src/main/java/com/example/letsdoit/ViewActivityFragment.java
package com.example.letsdoit;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView; // Required for dynamic tag creation

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
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

public class ViewActivityFragment extends Fragment implements TaskAdapter.TaskActionListener, CalendarDialogFragment.OnDateSelectedListener {

    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private List<Task> taskList;
    private List<Task> filteredTaskList;
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private LinearLayout llEmptyState;
    private TextView tvEmptyState;
    private RadioGroup rgStatusFilter;
    private TextView tvDateIndicator;
    private ImageButton btnCalendar;

    // Search fields
    private TextInputEditText etSearchQuery;
    private ImageButton btnSearchToggle;
    private String currentSearchQuery = "";

    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String currentFilter = "all";

    // Map to store user email to display name for search filtering
    private Map<String, String> userDisplayNameMap = new HashMap<>();

    // Stores the selected date in milliseconds (or -1 for 'Today')
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
        llEmptyState = view.findViewById(R.id.ll_empty_state);
        tvEmptyState = view.findViewById(R.id.tv_empty_state_title);
        rgStatusFilter = view.findViewById(R.id.rg_status_filter);
        tvDateIndicator = view.findViewById(R.id.tv_date_indicator);
        btnCalendar = view.findViewById(R.id.btn_calendar);
        etSearchQuery = view.findViewById(R.id.et_search_query);
        btnSearchToggle = view.findViewById(R.id.btn_search_toggle);
        final TextInputLayout tilSearch = view.findViewById(R.id.til_search);

        db = FirebaseFirestore.getInstance();

        taskList = new ArrayList<>();
        filteredTaskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(filteredTaskList, getContext(), this, loggedInUserRole, loggedInUserEmail);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(taskAdapter);

        setupFilterListeners();
        setupCalendar();
        setupSearch(tilSearch);
        loadTasks();

        return view;
    }

    private void setupCalendar() {
        // Initializes/resets selectedDateMillis to -1 (Today)
        selectedDateMillis = -1;
        updateDateIndicator();

        btnCalendar.setOnClickListener(v -> {
            CalendarDialogFragment dialogFragment = CalendarDialogFragment.newInstance(selectedDateMillis != -1 ? selectedDateMillis : null);
            dialogFragment.setOnDateSelectedListener(this);
            dialogFragment.show(getParentFragmentManager(), "CalendarDialog");
        });

        // NEW: Clicking the date indicator text snaps back to today
        tvDateIndicator.setOnClickListener(v -> {
            if (selectedDateMillis != -1) {
                selectedDateMillis = -1; // Reset to today
                updateDateIndicator();
                applyFilter();
            }
        });
    }

    private void setupSearch(final TextInputLayout tilSearch) {
        btnSearchToggle.setOnClickListener(v -> {
            if (tilSearch.getVisibility() == View.GONE) {
                tilSearch.setVisibility(View.VISIBLE);
                etSearchQuery.requestFocus();
            } else {
                tilSearch.setVisibility(View.GONE);
                etSearchQuery.setText("");
                currentSearchQuery = "";
                applyFilter();
            }
        });

        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim().toLowerCase();
                applyFilter();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // MODIFIED: Logic ensures selectedDateMillis is set to -1 if 'Today' is selected
    @Override
    public void onDateSelected(long dateInMillis, String formattedDate) {
        long todayStart = getDayStartMillis(System.currentTimeMillis());
        long selectedDayStart = getDayStartMillis(dateInMillis);

        // If the user selected the actual current day, reset to -1 so the indicator shows "📅 Today"
        if (selectedDayStart == todayStart) {
            selectedDateMillis = -1;
        } else {
            selectedDateMillis = dateInMillis;
        }

        updateDateIndicator();
        applyFilter();
    }

    private void updateDateIndicator() {
        if (selectedDateMillis == -1) {
            // SHOW 'Today' text for the current day
            tvDateIndicator.setText("📅 Today");
        } else {
            // SHOW formatted date for past days
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

    private long getDayStartMillis(long dateMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    // Task status logic remains the same for user view, as requested.
    private String getTaskStatusOnDate(Task task, long filterDateMillis) {
        // Get the user-specific status
        String currentUserStatus = task.getUserStatus(loggedInUserEmail);

        long actualFilterDateMillis = filterDateMillis == -1 ? System.currentTimeMillis() : filterDateMillis;
        long filterDayStart = getDayStartMillis(actualFilterDateMillis);
        long filterDayEnd = filterDayStart + (24 * 60 * 60 * 1000L) - 1;
        long completionTime = task.getUserCompletedDate(loggedInUserEmail);
        long todayStart = getDayStartMillis(System.currentTimeMillis());

        if (task.getTaskType().equalsIgnoreCase("permanent")) {
            // Permanent Task Logic: Daily Status Reset per user

            // A. Check for COMPLETED status on the viewed day
            if (completionTime > filterDayStart && completionTime <= filterDayEnd) {
                return "Completed";
            }

            // B. Check for IN PROGRESS status on the viewed day (Only TODAY)
            if (filterDayStart == todayStart && currentUserStatus.equalsIgnoreCase("in progress")) {
                return "In Progress";
            }

            // C. Default to Pending
            return "Pending";
        }

        // --- Logic for ADDITIONAL tasks ---

        // If viewing Today or Future, use current user status
        if (filterDateMillis == -1 || getDayStartMillis(filterDateMillis) >= getDayStartMillis(System.currentTimeMillis())) {
            return currentUserStatus;
        }

        // --- Historical Status Logic (Past Date) ---

        if (currentUserStatus.equalsIgnoreCase("Completed")) {
            // If completion happened AFTER the viewed day ended
            if (completionTime > 0 && completionTime > filterDayEnd) {
                return "Pending";
            }
            return "Completed";
        }

        return "Pending";
    }

    private void applyFilter() {
        List<Task> dateFilteredList = applyDateFilter(taskList);
        List<Task> searchFilteredList = applySearchFilter(dateFilteredList, currentSearchQuery);

        // --- NEW: Priority Sort after Date and Search Filter ---
        if (!searchFilteredList.isEmpty()) {
            Collections.sort(searchFilteredList, new PriorityComparator());
        }
        // --------------------------------------------------------

        filteredTaskList.clear();

        for (Task originalTask : searchFilteredList) {
            // Clone the task for display
            Task taskForDisplay = new Task();
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

            // CRITICAL FIX: Copy over per-user status maps for BOTH admin and user views
            taskForDisplay.setUserStatus(originalTask.getUserStatus());
            taskForDisplay.setUserAiCount(originalTask.getUserAiCount());
            taskForDisplay.setUserCompletedDate(originalTask.getUserCompletedDate());

            long filterDateMillis = selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
            String historicalStatus = getTaskStatusOnDate(originalTask, filterDateMillis);
            taskForDisplay.setStatus(historicalStatus);

            String taskStatus = taskForDisplay.getStatus() != null ? taskForDisplay.getStatus().toLowerCase() : "pending";

            if (currentFilter.equals("all") || taskStatus.equals(currentFilter)) {
                filteredTaskList.add(taskForDisplay);
            }
        }
        taskAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    /**
     * Comparator to sort Tasks by Priority: High > Medium > Low.
     * Uses timestamp as a secondary sort to keep the insertion order of same-priority tasks.
     */
    private static class PriorityComparator implements Comparator<Task> {
        private int getPriorityValue(String priority) {
            if (priority == null) return 3; // Default to lowest if null
            switch (priority.toLowerCase()) {
                case "high":
                    return 1;
                case "medium":
                    return 2;
                case "low":
                    return 3;
                default:
                    return 3;
            }
        }

        @Override
        public int compare(Task t1, Task t2) {
            int p1 = getPriorityValue(t1.getPriority());
            int p2 = getPriorityValue(t2.getPriority());

            // Primary sort by priority (Ascending order of value: 1, 2, 3 -> High, Medium, Low)
            int priorityCompare = Integer.compare(p1, p2);
            if (priorityCompare != 0) {
                return priorityCompare;
            }

            // Secondary sort by timestamp (Descending: Newest tasks first for same priority)
            return Long.compare(t2.getTimestamp(), t1.getTimestamp());
        }
    }

    private List<Task> applySearchFilter(List<Task> dateFilteredList, String query) {
        if (query.isEmpty()) {
            return dateFilteredList;
        }

        List<Task> searchFilteredList = new ArrayList<>();

        for (Task task : dateFilteredList) {
            // 1. Check Task Title
            if (task.getTitle() != null && task.getTitle().toLowerCase().contains(query)) {
                searchFilteredList.add(task);
                continue;
            }

            // 2. Check Assigned User Names
            List<String> assignedToEmails = task.getAssignedTo();
            if (assignedToEmails != null) {
                for (String email : assignedToEmails) {
                    String displayName = userDisplayNameMap.get(email);
                    if (displayName != null && displayName.toLowerCase().contains(query)) {
                        searchFilteredList.add(task);
                        break;
                    }
                }
            }
        }
        return searchFilteredList;
    }

    /**
     * Filters tasks based on the currently selected date.
     * Logic for 'additional' tasks ensures they only show if the selected date is within their range (inclusive of the end date).
     */
    private List<Task> applyDateFilter(List<Task> unfilteredList) {
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

                    // Calculate the start of the task's start and end days
                    long startDayStart = getDayStartMillis(startDateMillis);
                    long endDayStart = getDayStartMillis(endDateMillis); // Correctly declared here

                    // Check if the current filter day is between the task's start day and end day (inclusive)
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

    private void updateEmptyState() {
        if (filteredTaskList.isEmpty()) {
            String filterName = currentFilter.substring(0, 1).toUpperCase() + currentFilter.substring(1);
            if (currentFilter.equals("all")) {
                tvEmptyState.setText("No tasks found for this date.");
            } else {
                tvEmptyState.setText("No " + filterName + " tasks found for this date.");
            }
            llEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            llEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void loadTasks() {
        progressBar.setVisibility(View.VISIBLE);
        llEmptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        loadAllUsers();
    }

    private void loadAllUsers() {
        userDisplayNameMap.clear();

        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        User user = document.toObject(User.class);
                        if (user.getEmail() != null && user.getDisplayName() != null) {
                            userDisplayNameMap.put(user.getEmail(), user.getDisplayName());
                        }
                    }
                    loadAdminsForMap();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load user list for map: " + e.getMessage());
                    loadAdminsForMap();
                });
    }

    private void loadAdminsForMap() {
        db.collection("admins")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        User user = document.toObject(User.class);
                        if (user.getEmail() != null && user.getDisplayName() != null) {
                            userDisplayNameMap.put(user.getEmail(), user.getDisplayName());
                        }
                    }
                    startLoadingTasks();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load admin list for map: " + e.getMessage());
                    startLoadingTasks();
                });
    }

    private void startLoadingTasks() {
        if ("admin".equals(loggedInUserRole)) {
            loadAllTasks();
        } else {
            loadUserTasks();
        }
    }

    private void loadAllTasks() {
        // Removed orderBy for Firestore query to handle priority sorting in Java
        db.collection("tasks")
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
                    llEmptyState.setVisibility(View.VISIBLE);
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

                    // Priority sort applied in applyFilter()
                    applyFilter();
                    progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading tasks for user", e);
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Error loading tasks: " + e.getMessage());
                    llEmptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                });
    }

    @Override
    public void onTaskStatusClick(Task task, int position) {
        // --- NEW ADMIN LOGIC (Admin clicks open the review dialog) ---
        if ("admin".equals(loggedInUserRole)) {
            Task originalTask = taskList.stream()
                    .filter(t -> t.getId().equals(task.getId()))
                    .findFirst().orElse(null);

            if (originalTask != null) {
                showAdminTaskReviewDialog(originalTask);
            } else {
                Toast.makeText(getContext(), "Error: Original task data not found.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        // --- END NEW ADMIN LOGIC ---

        // Existing User Logic
        if ("user".equals(loggedInUserRole)) {
            long todayStart = getDayStartMillis(System.currentTimeMillis());
            boolean isTaskEditable = true;

            // Rule 1: Cannot edit status if viewing a past date.
            if (selectedDateMillis != -1 && getDayStartMillis(selectedDateMillis) < todayStart) {
                isTaskEditable = false;
            }

            // Rule 2: Cannot edit status for Additional task if deadline passed and it's not completed.
            if (task.getTaskType().equalsIgnoreCase("additional")) {
                try {
                    String endDateStr = task.getEndDate();
                    if (endDateStr != null && !endDateStr.isEmpty()) {
                        long endDateMillis = dateFormat.parse(endDateStr).getTime();
                        long endDayStart = getDayStartMillis(endDateMillis);

                        if (todayStart > endDayStart && !task.getStatus().equalsIgnoreCase("Completed")) {
                            isTaskEditable = false;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking task active status: " + e.getMessage());
                }
            }


            if (isTaskEditable) {
                Task originalTask = taskList.stream()
                        .filter(t -> t.getId().equals(task.getId()))
                        .findFirst().orElse(null);

                if (originalTask != null) {
                    showTaskDetailDialog(originalTask, position);
                } else {
                    Toast.makeText(getContext(), "Error: Original task not found.", Toast.LENGTH_SHORT).show();
                }

            } else {
                showReadOnlyDialog(task);
            }
        }
    }

    @Override
    public void onTaskEditClick(Task task, int position) {
        if ("admin".equals(loggedInUserRole) && task.getId() != null) {
            // Edit opens a new Activity, which triggers an update via onResume when returning.
            android.content.Intent intent = new android.content.Intent(getActivity(), EditTaskActivity.class);
            intent.putExtra(EXTRA_TASK_ID, task.getId());
            startActivity(intent);
        } else {
            Toast.makeText(getContext(), "Error: Cannot edit task without an ID.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onTaskDeleteClick(Task taskToDelete, int position) {
        showDeleteConfirmationDialog(taskToDelete, position);
    }

    // Existing showReadOnlyDialog method (kept as-is for user side logic)
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

        // Show user-specific status
        String userStatus = task.getUserStatus(loggedInUserEmail);
        tvDialogStatus.setText("Status: " + userStatus.toUpperCase());

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
            String aiCountValue = task.getUserAiCount(loggedInUserEmail);
            tvDialogAiCount.setText("AI Count: " + (aiCountValue != null && !aiCountValue.isEmpty() ? aiCountValue : "N/A (Required)"));
            tvDialogAiCount.setVisibility(View.VISIBLE);
        } else {
            tvDialogAiCount.setVisibility(View.GONE);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }


    // Existing showTaskDetailDialog method (kept as-is for user side logic)
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
        LinearLayout llAiCountSection = dialog.findViewById(R.id.ll_ai_count_section);
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
            // Get user-specific AI count
            String existingAiCount = task.getUserAiCount(loggedInUserEmail);
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

        // Get user-specific status
        String currentUserStatus = task.getUserStatus(loggedInUserEmail).toLowerCase();
        if (currentUserStatus.equals("pending")) {
            rbPending.setChecked(true);
        } else if (currentUserStatus.equals("in progress")) {
            rbInProgress.setChecked(true);
        } else if (currentUserStatus.equals("completed")) {
            rbCompleted.setChecked(true);
        }

        // --- NEW LOGIC: Control AI Count visibility based on status selection ---
        rgDialogStatus.setOnCheckedChangeListener((group, checkedId) -> {
            if (!task.isRequireAiCount()) return; // Only apply logic if AI count is required

            if (checkedId == R.id.rb_dialog_completed) {
                llAiCountSection.setVisibility(View.VISIBLE);
                // Optionally set initial count if it exists
                String currentCount = task.getUserAiCount(loggedInUserEmail);
                if (currentCount != null && !currentCount.isEmpty()) {
                    etAiCount.setText(currentCount);
                }
            } else {
                // When switching away from COMPLETED, hide the input and clear any error or text
                llAiCountSection.setVisibility(View.GONE);
                etAiCount.setError(null);
                // NOTE: We don't clear the text field itself here, only the error, to allow user to see what they typed if they switch back.
                // The crucial clearing happens in updateTaskInFirestore.
            }
        });

        // Set initial visibility based on the current status
        if (task.isRequireAiCount()) {
            if (!currentUserStatus.equals("completed")) {
                llAiCountSection.setVisibility(View.GONE);
            } else {
                llAiCountSection.setVisibility(View.VISIBLE);
            }
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
                // If status is not Completed, the aiCountInput should be cleared on submission regardless of text field state
                if (!newStatus.equalsIgnoreCase("✅ Completed")) {
                    aiCountInput = "";
                }
            }

            String cleanStatus = newStatus.replace("⏱️", "").replace("🔧", "").replace("✅", "").trim();
            updateTaskInFirestore(task, cleanStatus, aiCountInput, position, dialog);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // Existing updateTaskInFirestore method (kept as-is for user side logic)
    private void updateTaskInFirestore(Task task, String newStatus, String aiCountValue, int position, Dialog dialog) {
        if (task.getId() == null) {
            Toast.makeText(getContext(), "Error: Task ID not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> update = new HashMap<>();

        // Update user-specific status
        Map<String, String> userStatusMap = task.getUserStatus();
        userStatusMap.put(loggedInUserEmail, newStatus);
        update.put("userStatus", userStatusMap);

        // Update user-specific completion date
        Map<String, Long> userCompletedDateMap = task.getUserCompletedDate();
        final long finalCompletionTime;
        if (newStatus.equalsIgnoreCase("Completed")) {
            finalCompletionTime = System.currentTimeMillis();
            userCompletedDateMap.put(loggedInUserEmail, finalCompletionTime);
        } else {
            finalCompletionTime = 0;
            userCompletedDateMap.put(loggedInUserEmail, 0L);
        }
        update.put("userCompletedDate", userCompletedDateMap);

        // Update user-specific AI count
        Map<String, String> userAiCountMap = task.getUserAiCount();
        if (task.isRequireAiCount()) {
            if (newStatus.equalsIgnoreCase("Completed")) {
                // Store the submitted AI Count value
                userAiCountMap.put(loggedInUserEmail, aiCountValue);
            } else {
                // If status is not completed (Pending/In Progress), the AI count must be cleared
                userAiCountMap.put(loggedInUserEmail, "");
            }
            update.put("userAiCount", userAiCountMap);
        }

        db.collection("tasks").document(task.getId())
                .update(update)
                .addOnSuccessListener(aVoid -> {
                    // Update local task object
                    task.setUserStatus(loggedInUserEmail, newStatus);
                    task.setUserCompletedDate(loggedInUserEmail, finalCompletionTime);
                    if (task.isRequireAiCount()) {
                        // Use the updated aiCountValue which is cleared if status is not Completed
                        task.setUserAiCount(loggedInUserEmail, newStatus.equalsIgnoreCase("Completed") ? aiCountValue : "");
                    }

                    // Update in taskList
                    for (Task t : taskList) {
                        if (t.getId().equals(task.getId())) {
                            t.setUserStatus(loggedInUserEmail, newStatus);
                            t.setUserCompletedDate(loggedInUserEmail, finalCompletionTime);
                            if (task.isRequireAiCount()) {
                                t.setUserAiCount(loggedInUserEmail, newStatus.equalsIgnoreCase("Completed") ? aiCountValue : "");
                            }
                            break;
                        }
                    }

                    applyFilter();

                    String message = "Task updated successfully!";
                    if (task.isRequireAiCount() && newStatus.equalsIgnoreCase("Completed") && aiCountValue != null && !aiCountValue.isEmpty()) {
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

    private void showDeleteConfirmationDialog(Task taskToDelete, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteTaskFromFirestore(taskToDelete, position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTaskFromFirestore(Task taskToDelete, int position) {
        if (taskToDelete.getId() == null) {
            Toast.makeText(getContext(), "Error: Task ID not found for deletion.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("tasks").document(taskToDelete.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // 1. Remove the original task from the master list (by ID)
                    // Note: Task objects are clones, so removeIf is safer than .remove(taskToDelete)
                    taskList.removeIf(t -> t.getId().equals(taskToDelete.getId()));

                    // 2. Remove the clone directly from the display list and notify RecyclerView for instant UI feedback
                    if (position >= 0 && position < filteredTaskList.size() && filteredTaskList.get(position).getId().equals(taskToDelete.getId())) {
                        filteredTaskList.remove(position);
                        taskAdapter.notifyItemRemoved(position);
                    } else {
                        // Fallback to full list update if position/identity check fails
                        applyFilter();
                    }

                    // 3. Update empty state and overall filters/UI
                    updateEmptyState();

                    Toast.makeText(getContext(), "Task deleted successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting task " + taskToDelete.getId(), e);
                    Toast.makeText(getContext(), "Failed to delete task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (loggedInUserEmail != null) {
            // This ensures instant refresh after returning from EditTaskActivity.
            loadTasks();
        }
    }

    // --- UPDATED ADMIN REVIEW DIALOG METHODS ---

    private void showAdminTaskReviewDialog(Task task) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_admin_task_review);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCancelable(true);

        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_task_title);
        TextView tvAssignedInfo = dialog.findViewById(R.id.tv_assigned_info);
        LinearLayout llCompleted = dialog.findViewById(R.id.ll_completed_users);
        LinearLayout llInProgress = dialog.findViewById(R.id.ll_in_progress_users);
        LinearLayout llPending = dialog.findViewById(R.id.ll_pending_users);
        Button btnClose = dialog.findViewById(R.id.btn_close_review);

        // 1. Set Task Title
        tvTitle.setText(task.getTitle());

        // 2. Set "Assigned to All" visibility
        boolean isAssignedToAll = task.getAssignedTo().size() == userDisplayNameMap.size();

        if (isAssignedToAll && task.getAssignedTo().size() > 0) {
            tvAssignedInfo.setVisibility(View.VISIBLE);
            tvAssignedInfo.setText("Assigned To All");
        } else {
            tvAssignedInfo.setVisibility(View.GONE);
        }

        // 3. Populate User Sections
        llCompleted.removeAllViews();
        llInProgress.removeAllViews();
        llPending.removeAllViews();

        Map<String, List<String>> statusGroups = new HashMap<>();
        statusGroups.put("completed", new ArrayList<>());
        statusGroups.put("in progress", new ArrayList<>());
        statusGroups.put("pending", new ArrayList<>());

        // Group assigned users by status
        for (String email : task.getAssignedTo()) {
            String status = task.getUserStatus(email);
            String displayName = userDisplayNameMap.get(email);

            String cleanStatus = (status != null && !status.isEmpty()) ? status.toLowerCase() : "pending";
            if (displayName == null) displayName = email;

            if (cleanStatus.contains("completed")) {
                statusGroups.get("completed").add(displayName);
            } else if (cleanStatus.contains("in progress")) {
                statusGroups.get("in progress").add(displayName);
            } else {
                statusGroups.get("pending").add(displayName);
            }
        }

        // Define the color scheme for the new UI tags
        final int greenText = ContextCompat.getColor(requireContext(), R.color.status_completed);
        // Using a light green color that is slightly different from the default background
        final int lightGreenBg = Color.parseColor("#E8F5E9");

        final int blueText = ContextCompat.getColor(requireContext(), R.color.status_in_progress);
        final int lightBlueBg = Color.parseColor("#E3F2FD");

        // NEW: Red color scheme for Pending (as requested)
        final int redText = Color.parseColor("#D32F2F"); // Dark Red (same as delete icon)
        final int lightRedBg = Color.parseColor("#FFEBEE"); // Light Red/Pinkish background

        // Add users to the LinearLayout sections as individual tags
        addStatusTags(llCompleted, statusGroups.get("completed"), greenText, lightGreenBg);
        addStatusTags(llInProgress, statusGroups.get("in progress"), blueText, lightBlueBg);
        // Using the new red scheme for Pending
        addStatusTags(llPending, statusGroups.get("pending"), redText, lightRedBg);

        // 4. Set Close Button Listener
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Dynamically creates a CardView tag for each name and adds them to the container.
     * This achieves the best UI for inline, wrapping tags.
     */
    private void addStatusTags(LinearLayout container, List<String> names, int textColor, int backgroundColor) {

        if (names.isEmpty()) {
            TextView tv = new TextView(getContext());
            tv.setText("None");
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            tv.setTextSize(14f);
            tv.setPadding(4, 0, 0, 0);
            container.addView(tv);
            return;
        }

        // Since the parent LinearLayout in XML has horizontal orientation, adding multiple views
        // with margin simulates a tag cloud (Flexbox-like wrapping, depending on device layout properties).
        for (String name : names) {

            // 1. Create the TextView (User Name)
            TextView tvName = new TextView(getContext());
            tvName.setText(name);
            tvName.setTextColor(textColor);
            tvName.setTextSize(14f); // INCREASED SIZE from 12f to 14f
            tvName.setTypeface(Typeface.DEFAULT_BOLD);
            tvName.setPadding(12, 6, 12, 6);

            // 2. Create the CardView (Background Box)
            CardView card = new CardView(getContext());
            card.setRadius(8f); // Rounded corners
            card.setCardElevation(0f);
            card.setCardBackgroundColor(backgroundColor);
            card.addView(tvName);

            // 3. Set layout parameters with margins for spacing
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            // Margin to separate the tags inline (right) and vertically (bottom)
            // Use 8dp for both to achieve the tag cloud effect
            int margin = (int) (8 * getResources().getDisplayMetrics().density);
            cardParams.setMargins(0, 0, margin, margin);

            card.setLayoutParams(cardParams);

            // 4. Add the CardView to the parent container
            container.addView(card);
        }
    }
}