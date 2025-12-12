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
import androidx.cardview.widget.CardView;

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
    private String currentSearchQuery = "";

    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String currentFilter = "done"; // MODIFIED: Default filter to "done"

    // Map to store user email to display name for search filtering AND TaskAdapter display
    private Map<String, String> userDisplayNameMap = new HashMap<>();

    // Stores the selected date in milliseconds (or -1 for 'Today')
    private long selectedDateMillis = -1;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private final SimpleDateFormat dateIndicatorFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private final SimpleDateFormat dayOfWeekFormat = new SimpleDateFormat("EEEE", Locale.US);

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

    // ADDED: Method to set selected date (called from MainActivity for initial load)
    public void setSelectedDateMillis(long dateMillis) {
        this.selectedDateMillis = dateMillis;
    }

    // ADDED: Method to update date from MainActivity (for synchronization)
    public void updateDateFromActivity(long dateMillis) {
        this.selectedDateMillis = dateMillis;
        updateDateIndicator();
        loadTasks(); // Load tasks for the newly selected date
    }

    // ADDED: Method to sync date back to MainActivity
    private void syncDateWithActivity() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateSelectedDate(selectedDateMillis);
        }
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

        // Ensure "Done" is checked by default
        if (rgStatusFilter != null) {
            rgStatusFilter.check(R.id.rb_filter_done);
        }

        // ADDED: Get initial date from MainActivity if available
        if (getActivity() instanceof MainActivity) {
            long activityDate = ((MainActivity) getActivity()).getCurrentSelectedDateMillis();
            if (activityDate != -1) {
                selectedDateMillis = activityDate;
            }
        }

        loadTasks();

        return view;
    }

    private void setupCalendar() {
        updateDateIndicator();

        btnCalendar.setOnClickListener(v -> {
            CalendarDialogFragment dialogFragment = CalendarDialogFragment.newInstance(selectedDateMillis != -1 ? selectedDateMillis : null);
            dialogFragment.setOnDateSelectedListener(this);
            dialogFragment.show(getParentFragmentManager(), "CalendarDialog");
        });

        tvDateIndicator.setOnClickListener(v -> {
            if (selectedDateMillis != -1) {
                selectedDateMillis = -1; // Reset to today
                updateDateIndicator();
                applyFilter();
                // ADDED: Sync with MainActivity
                syncDateWithActivity();
            }
        });
    }

    private void setupSearch(final TextInputLayout tilSearch) {
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

    @Override
    public void onDateSelected(long dateInMillis, String formattedDate) {
        long todayStart = getDayStartMillis(System.currentTimeMillis());
        long selectedDayStart = getDayStartMillis(dateInMillis);

        if (selectedDayStart == todayStart) {
            selectedDateMillis = -1;
        } else {
            selectedDateMillis = dateInMillis;
        }

        updateDateIndicator();
        applyFilter();
        // ADDED: Sync with MainActivity
        syncDateWithActivity();
    }

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
            // Filter IDs reflect the new order: Done (left) and Not Done (right)
            if (checkedId == R.id.rb_filter_done) {
                currentFilter = "done";
            } else if (checkedId == R.id.rb_filter_not_done) {
                currentFilter = "not done";
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

    /**
     * MODIFIED: Returns the task status for the current user/date, respecting the task type's
     * completion rules (global for Permanent, personal for Additional) AND the AI Count requirement.
     */
    private String getTaskStatusOnDate(Task task, long filterDateMillis) {

        long filterDayStart = getDayStartMillis(filterDateMillis);
        boolean isPermanent = task.getTaskType() != null && task.getTaskType().equalsIgnoreCase("permanent");

        // Determine the status and the corresponding completion time to check for the filter date.
        String statusToCheck;
        long completionTimeToCheck;
        String aiCountValue;

        if ("admin".equals(loggedInUserRole)) {
            // Admin sees the global status and global completion time/AI count
            // Global status logic relies on Task.getStatus() which is "Completed" if anyone is completed.
            statusToCheck = task.getUserStatus().containsValue("Completed") ? "Completed" : "Pending";
            completionTimeToCheck = task.getCompletedDateMillis();
            aiCountValue = task.getAiCountValue();
        } else {
            // Regular user sees their specific status and completion time/AI count
            statusToCheck = task.getUserStatus(loggedInUserEmail);
            completionTimeToCheck = task.getUserCompletedDate(loggedInUserEmail);
            aiCountValue = task.getUserAiCount(loggedInUserEmail);
        }

        // 1. Check if the task is completed from the required perspective
        if (statusToCheck.equalsIgnoreCase("Completed")) {

            // CRITICAL BUG FIX: If AI Count is required but missing, demote status to Pending
            if (task.isRequireAiCount() && (aiCountValue == null || aiCountValue.isEmpty())) {
                return "Pending";
            }

            if (isPermanent) {
                // For Permanent tasks, apply the historical date check
                long filterDayEnd = filterDayStart + (24 * 60 * 60 * 1000L) - 1;

                if (completionTimeToCheck > 0 && completionTimeToCheck <= filterDayEnd) {
                    return "Completed";
                }
                // If globally completed, but after the filter date, it's considered pending for this date.
                return "Pending";
            }

            return "Completed";
        }

        // 2. If not completed based on task type rules, default is always Pending (Not Done)
        return "Pending";
    }


    private void applyFilter() {
        // MODIFIED: Admin must use date filtering in the View Task screen to ensure tasks show on the correct day.
        List<Task> dateFilteredList = applyDateFilter(taskList);

        List<Task> searchFilteredList = applySearchFilter(dateFilteredList, currentSearchQuery);

        if (!searchFilteredList.isEmpty()) {
            Collections.sort(searchFilteredList, new PriorityComparator());
        }

        filteredTaskList.clear();

        // CRITICAL FIX: Update the adapter with the user map before applying filter
        taskAdapter.setUserDisplayNameMap(userDisplayNameMap);

        for (Task originalTask : searchFilteredList) {
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
            taskForDisplay.setTimestamp(originalTask.getTimestamp());
            taskForDisplay.setTaskType(originalTask.getTaskType());
            taskForDisplay.setSelectedDays(originalTask.getSelectedDays());

            // CRITICAL: Copy over per-user status maps AND legacy fields for global checks
            taskForDisplay.setUserStatus(originalTask.getUserStatus());
            taskForDisplay.setUserAiCount(originalTask.getUserAiCount());
            taskForDisplay.setUserCompletedDate(originalTask.getUserCompletedDate());
            taskForDisplay.setCompletedDateMillis(originalTask.getCompletedDateMillis());
            taskForDisplay.setAiCountValue(originalTask.getAiCountValue());

            // Set the task's display status based on the global/personal rule
            long filterDateMillis = selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
            String displayStatus = getTaskStatusOnDate(originalTask, filterDateMillis);
            taskForDisplay.setStatus(displayStatus); // This is what the adapter uses for coloring/text

            String taskStatus = taskForDisplay.getStatus() != null ? displayStatus.toLowerCase() : "pending";

            // Filter logic: "done" = completed, "not done" = pending
            if (currentFilter.equals("done")) {
                if (taskStatus.equals("completed")) {
                    filteredTaskList.add(taskForDisplay);
                }
            } else if (currentFilter.equals("not done")) {
                if (taskStatus.equals("pending")) {
                    filteredTaskList.add(taskForDisplay);
                }
            }
        }
        taskAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private static class PriorityComparator implements Comparator<Task> {
        private int getPriorityValue(String priority) {
            if (priority == null) return 3;
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

            int priorityCompare = Integer.compare(p1, p2);
            if (priorityCompare != 0) {
                return priorityCompare;
            }

            return Long.compare(t2.getTimestamp(), t1.getTimestamp());
        }
    }

    private List<Task> applySearchFilter(List<Task> dateFilteredList, String query) {
        if (query.isEmpty()) {
            return dateFilteredList;
        }

        List<Task> searchFilteredList = new ArrayList<>();

        for (Task task : dateFilteredList) {
            if (task.getTitle() != null && task.getTitle().toLowerCase().contains(query)) {
                searchFilteredList.add(task);
                continue;
            }

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

    private List<Task> applyDateFilter(List<Task> unfilteredList) {
        long filterDateMillis = selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
        long filterDayStart = getDayStartMillis(filterDateMillis);
        String filterDayOfWeek = dayOfWeekFormat.format(new Date(filterDateMillis)).substring(0, 3);

        List<Task> dateFilteredList = new ArrayList<>();

        for (Task task : unfilteredList) {
            String taskType = task.getTaskType().toLowerCase();

            if (taskType.equals("permanent")) {
                boolean isDayActive = false;
                List<String> selectedDays = task.getSelectedDays();
                for (String day : selectedDays) {
                    if (day.equalsIgnoreCase(filterDayOfWeek)) {
                        isDayActive = true;
                        break;
                    }
                }

                if (isDayActive) {
                    dateFilteredList.add(task);
                }
            } else if (taskType.equals("additional")) {
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
            String filterName;
            if (currentFilter.equals("done")) {
                filterName = "Done";
            } else if (currentFilter.equals("not done")) {
                filterName = "Not Done";
            } else {
                filterName = "All";
            }
            tvEmptyState.setText("No " + filterName + " tasks found for this date.");

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

                            // MODIFIED: Visibility logic based on Task Type
                            boolean shouldDisplay = false;
                            String taskType = task.getTaskType() != null ? task.getTaskType().toLowerCase() : "permanent";
                            List<String> assignedTo = task.getAssignedTo();

                            if (taskType.equals("permanent")) {
                                // User sees all Permanent tasks
                                shouldDisplay = true;
                            } else if (taskType.equals("additional")) {
                                // User sees Additional tasks only if explicitly assigned
                                if (assignedTo != null && assignedTo.contains(loggedInUserEmail)) {
                                    shouldDisplay = true;
                                }
                            }

                            if (shouldDisplay) {
                                taskList.add(task);
                            }
                            // END MODIFIED BLOCK
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing task: " + document.getId(), e);
                        }
                    }

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
        // ADMIN LOGIC: Do nothing as requested
        if ("admin".equals(loggedInUserRole)) {
            return;
        }

        // USER LOGIC
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

                        // Note: Task.java getUserStatus now handles the task type aware completion check
                        if (todayStart > endDayStart && !task.getUserStatus(loggedInUserEmail).equalsIgnoreCase("Completed")) {
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

        // Use global/user-specific status (Task.java now handles the logic)
        String taskStatus = task.getUserStatus(loggedInUserEmail);
        String statusDisplay = taskStatus.equalsIgnoreCase("Completed") ? "DONE" : "NOT DONE";
        tvDialogStatus.setText("Status: " + statusDisplay);

        // Date visibility logic: Only display dates for Additional tasks.
        if (task.getTaskType().equalsIgnoreCase("additional")) {
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
            // Use global/user-specific AI count (Task.java now handles the logic)
            String aiCountValue = task.getUserAiCount(loggedInUserEmail);
            tvDialogAiCount.setText("AI Count: " + (aiCountValue != null && !aiCountValue.isEmpty() ? aiCountValue : "N/A (Required)"));
            tvDialogAiCount.setVisibility(View.VISIBLE);
        } else {
            tvDialogAiCount.setVisibility(View.GONE);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }


    // MODIFIED: showTaskDetailDialog for User side
    private void showTaskDetailDialog(Task task, int position) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_task_detail);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCancelable(true);

        TextView tvDialogTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView tvCurrentDay = dialog.findViewById(R.id.tv_current_day);
        TextView tvDialogDescription = dialog.findViewById(R.id.tv_dialog_description);
        TextView tvDialogDates = dialog.findViewById(R.id.tv_dialog_dates);
        TextView tvDialogRemarks = dialog.findViewById(R.id.tv_dialog_remarks);
        LinearLayout llAiCountSection = dialog.findViewById(R.id.ll_ai_count_section);
        TextView tvAiCountLabel = dialog.findViewById(R.id.tv_ai_count_label);
        TextInputEditText etAiCount = dialog.findViewById(R.id.et_ai_count);
        RadioGroup rgDialogStatus = dialog.findViewById(R.id.rg_dialog_status);
        RadioButton rbDone = dialog.findViewById(R.id.rb_dialog_done);
        RadioButton rbNotDone = dialog.findViewById(R.id.rb_dialog_not_done);
        Button btnSubmit = dialog.findViewById(R.id.btn_submit);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);

        // Display Current Day
        long displayDateMillis = selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
        String dayOfWeek = dayOfWeekFormat.format(new Date(displayDateMillis));
        tvCurrentDay.setText("📅 " + dayOfWeek);

        tvDialogTitle.setText(task.getTitle());
        tvDialogDescription.setText(task.getDescription());

        // Display Date Range if Additional
        if (task.getTaskType().equalsIgnoreCase("additional")) {
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
        } else {
            tvDialogDates.setVisibility(View.GONE);
        }

        String remarks = task.getRemarks();
        // The remarks view is kept in the XML but is not populated here, as per user's preference to prioritize UI space.
        /*
        if (remarks != null && !remarks.isEmpty()) {
            tvDialogRemarks.setText("Remarks: " + remarks);
            tvDialogRemarks.setVisibility(View.VISIBLE);
        } else {
            tvDialogRemarks.setVisibility(View.GONE);
        }
        */

        // Get current user's actual status and AI count for form initialization
        String currentUserStatus = task.getUserStatus(loggedInUserEmail).toLowerCase();
        String currentUserAiCount = task.getUserAiCount(loggedInUserEmail);

        // --- AI Count Section Setup ---
        if (task.isRequireAiCount()) {
            // Visibility is controlled by XML layout and listener.
            if (currentUserAiCount != null && !currentUserAiCount.isEmpty()) {
                etAiCount.setText(currentUserAiCount);
                tvAiCountLabel.setText("AI Count (You can edit)");
            } else {
                etAiCount.setText("");
                tvAiCountLabel.setText("Enter AI Count (Required for Completion)");
            }
        }

        // Set initial radio button state (uses per-user status to determine dialog appearance)
        if (currentUserStatus.equals("completed")) {
            rbDone.setChecked(true);
        } else {
            rbNotDone.setChecked(true);
        }

        // Control AI Count visibility based on status selection
        rgDialogStatus.setOnCheckedChangeListener((group, checkedId) -> {
            if (!task.isRequireAiCount()) return;

            if (checkedId == R.id.rb_dialog_done) {
                llAiCountSection.setVisibility(View.VISIBLE);
            } else {
                llAiCountSection.setVisibility(View.GONE);
                etAiCount.setError(null);
            }
        });

        // Set initial AI count visibility based on current status
        if (task.isRequireAiCount()) {
            if (currentUserStatus.equals("completed")) {
                llAiCountSection.setVisibility(View.VISIBLE);
            } else {
                llAiCountSection.setVisibility(View.GONE);
            }
        } else {
            llAiCountSection.setVisibility(View.GONE);
        }

        btnSubmit.setOnClickListener(v -> {
            int selectedStatusId = rgDialogStatus.getCheckedRadioButtonId();
            if (selectedStatusId == -1) {
                Toast.makeText(getContext(), "Please select a status", Toast.LENGTH_SHORT).show();
                return;
            }

            RadioButton selectedRadioButton = dialog.findViewById(selectedStatusId);
            String newStatusText = selectedRadioButton.getText().toString();
            String newStatus;

            // UPDATED LOGIC: Check if the button text contains the word "Done"
            if (newStatusText.contains("Done")) {
                newStatus = "Completed";
            } else {
                newStatus = "Pending"; // Corresponds to Not Done
            }

            String aiCountInput = "";
            if (task.isRequireAiCount()) {
                aiCountInput = etAiCount.getText().toString().trim();

                if (newStatus.equalsIgnoreCase("Completed")) {
                    if (aiCountInput.isEmpty()) {
                        etAiCount.setError("AI Count is required for completion");
                        Toast.makeText(getContext(), "Please enter AI Count to mark as completed", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                // If status is not Completed, AI count is cleared on submission
                if (!newStatus.equalsIgnoreCase("Completed")) {
                    aiCountInput = "";
                }
            }

            // The submission updates the current user's per-user status only.
            updateTaskInFirestore(task, newStatus, aiCountInput, position, dialog);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void updateTaskInFirestore(Task task, String newStatus, String aiCountValue, int position, Dialog dialog) {
        if (task.getId() == null) {
            Toast.makeText(getContext(), "Error: Task ID not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Prepare updates for the current user's entries in the per-user maps
        Map<String, String> userStatusMap = task.getUserStatus();
        userStatusMap.put(loggedInUserEmail, newStatus);

        Map<String, Long> userCompletedDateMap = task.getUserCompletedDate();
        final long finalCompletionTime = newStatus.equalsIgnoreCase("Completed") ? System.currentTimeMillis() : 0L;
        userCompletedDateMap.put(loggedInUserEmail, finalCompletionTime);

        Map<String, String> userAiCountMap = task.getUserAiCount();
        userAiCountMap.put(loggedInUserEmail, aiCountValue);

        // 2. Determine the new global/fallback status, date, and AI count for Permanent Tasks
        String globalStatus = "Pending";
        long globalCompletedDate = 0L;
        String globalAiCount = "";

        // Find the "Completed" status, prioritize permanent tasks completed today
        for (String email : task.getAssignedTo()) {
            if (userStatusMap.containsKey(email) && userStatusMap.get(email).equalsIgnoreCase("Completed")) {
                globalStatus = "Completed";
                globalCompletedDate = userCompletedDateMap.getOrDefault(email, 0L);
                globalAiCount = userAiCountMap.getOrDefault(email, "");

                // CRITICAL FIX: Only use the AI count if it is NOT EMPTY
                if (globalAiCount != null && !globalAiCount.isEmpty()) {
                    break; // Use the first completed user's data as the global marker if AI count is present
                } else if (task.getTaskType().equalsIgnoreCase("additional")) {
                    // For additional task, we break on first completed regardless of AI count status if we are setting the legacy fields
                    break;
                }
            }
        }

        // FIX: Capture local variables as final before the lambda to avoid the compiler error.
        final String finalGlobalStatus = globalStatus;
        final long finalGlobalCompletedDate = globalCompletedDate;
        final String finalGlobalAiCount = globalAiCount;


        // 3. Prepare Firestore document update
        Map<String, Object> update = new HashMap<>();
        update.put("userStatus", userStatusMap);
        update.put("userCompletedDate", userCompletedDateMap);
        update.put("userAiCount", userAiCountMap);

        // Also update the fallback/legacy fields (only for Permanent tasks)
        // For additional tasks, the fallback fields are updated with the submitting user's value
        if (task.getTaskType().equalsIgnoreCase("Permanent")) {
            update.put("status", finalGlobalStatus);
            update.put("completedDateMillis", finalGlobalCompletedDate);
            update.put("aiCountValue", finalGlobalAiCount);
        } else {
            // For additional tasks, the single user's status/date/count is the "global" for now
            update.put("status", newStatus);
            update.put("completedDateMillis", finalCompletionTime);
            update.put("aiCountValue", aiCountValue);
        }

        db.collection("tasks").document(task.getId())
                .update(update)
                .addOnSuccessListener(aVoid -> {
                    // Update local task object for instant UI refresh
                    task.setUserStatus(userStatusMap);
                    task.setUserCompletedDate(userCompletedDateMap);
                    task.setUserAiCount(userAiCountMap);

                    // Update fallback fields (inside lambda, using final variables)
                    if (task.getTaskType().equalsIgnoreCase("Permanent")) {
                        task.setStatus(finalGlobalStatus);
                        task.setCompletedDateMillis(finalGlobalCompletedDate);
                        task.setAiCountValue(finalGlobalAiCount);
                    } else {
                        task.setStatus(newStatus);
                        task.setCompletedDateMillis(finalCompletionTime);
                        task.setAiCountValue(aiCountValue);
                    }

                    // Update in master taskList
                    for (Task t : taskList) {
                        if (t.getId().equals(task.getId())) {
                            t.setUserStatus(userStatusMap);
                            t.setUserCompletedDate(userCompletedDateMap);
                            t.setUserAiCount(userAiCountMap);

                            if (task.getTaskType().equalsIgnoreCase("Permanent")) {
                                t.setStatus(finalGlobalStatus);
                                t.setCompletedDateMillis(finalGlobalCompletedDate);
                                t.setAiCountValue(finalGlobalAiCount);
                            } else {
                                t.setStatus(newStatus);
                                t.setCompletedDateMillis(finalCompletionTime);
                                t.setAiCountValue(aiCountValue);
                            }
                            break;
                        }
                    }

                    applyFilter();

                    String message = "Task updated successfully! Status: " + (newStatus.equalsIgnoreCase("Completed") ? "DONE" : "NOT DONE");
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
                    taskList.removeIf(t -> t.getId().equals(taskToDelete.getId()));
                    if (position >= 0 && position < filteredTaskList.size() && filteredTaskList.get(position).getId().equals(taskToDelete.getId())) {
                        filteredTaskList.remove(position);
                        taskAdapter.notifyItemRemoved(position);
                    } else {
                        applyFilter();
                    }

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
            loadTasks();
        }
    }
}