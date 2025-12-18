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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
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

public class ViewActivityFragment extends Fragment
        implements TaskAdapter.TaskActionListener, CalendarDialogFragment.OnDateSelectedListener {

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
    private String currentFilter = "done"; // Default: show Done first

    // Map to store user email to display name
    private Map<String, String> userDisplayNameMap = new HashMap<>();

    // Stores the selected date in milliseconds (or -1 for 'Today')
    private long selectedDateMillis = -1;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private final SimpleDateFormat dateIndicatorFormat =
            new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private final SimpleDateFormat dayOfWeekFormat =
            new SimpleDateFormat("EEEE", Locale.US);

    // For per-day storage docs: yyyy-MM-dd
    private final SimpleDateFormat storageDateKeyFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

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

    // Method to set selected date (called from MainActivity for initial load)
    public void setSelectedDateMillis(long dateMillis) {
        this.selectedDateMillis = dateMillis;
    }

    // Method to update date from MainActivity (for synchronization)
    public void updateDateFromActivity(long dateMillis) {
        this.selectedDateMillis = dateMillis;
        updateDateIndicator();
        loadTasks();
    }

    // Sync date back to MainActivity
    private void syncDateWithActivity() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateSelectedDate(selectedDateMillis);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

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

        taskAdapter = new TaskAdapter(filteredTaskList, getContext(), this,
                loggedInUserRole, loggedInUserEmail);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(taskAdapter);

        setupFilterListeners();
        setupCalendar();
        setupSearch(tilSearch);

        // Ensure "Done" is checked by default
        if (rgStatusFilter != null) {
            rgStatusFilter.check(R.id.rb_filter_done);
        }

        // Get initial date from MainActivity if available
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
            CalendarDialogFragment dialogFragment =
                    CalendarDialogFragment.newInstance(selectedDateMillis != -1 ? selectedDateMillis : null);
            dialogFragment.setOnDateSelectedListener(this);
            dialogFragment.show(getParentFragmentManager(), "CalendarDialog");
        });

        tvDateIndicator.setOnClickListener(v -> {
            if (selectedDateMillis != -1) {
                selectedDateMillis = -1; // Reset to today
                updateDateIndicator();
                loadTasks();
                syncDateWithActivity();
            }
        });
    }

    private void setupSearch(final TextInputLayout tilSearch) {
        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
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
        loadTasks();
        syncDateWithActivity();
    }

    private void updateDateIndicator() {
        if (selectedDateMillis == -1) {
            tvDateIndicator.setText("Today");
        } else {
            String formattedDate =
                    dateIndicatorFormat.format(new Date(selectedDateMillis));
            tvDateIndicator.setText(formattedDate);
        }
    }

    private void setupFilterListeners() {
        rgStatusFilter.setOnCheckedChangeListener((group, checkedId) -> {
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
     * Synchronous status lookup: read /tasks/{taskId}/dailyStatus/{yyyy-MM-dd}
     * and return "Completed" or "Pending" for the selected day.
     */
    private String getTaskStatusOnDate(Task task, long filterDateMillis) {
        long dayStart = getDayStartMillis(filterDateMillis);
        String dateKey = storageDateKeyFormat.format(new Date(dayStart));

        String statusForDay = "Pending";

        try {
            com.google.android.gms.tasks.Task<DocumentSnapshot> t =
                    FirebaseFirestore.getInstance()
                            .collection("tasks")
                            .document(task.getId())
                            .collection("dailyStatus")
                            .document(dateKey)
                            .get();

            DocumentSnapshot snapshot = Tasks.await(t);

            if (snapshot != null && snapshot.exists()) {
                TaskDayStatus dayStatus = snapshot.toObject(TaskDayStatus.class);
                if (dayStatus != null) {
                    String s = dayStatus.getStatus();
                    String ai = dayStatus.getAiCountValue();

                    if ("Completed".equalsIgnoreCase(s)) {
                        if (task.isRequireAiCount() && (ai == null || ai.isEmpty())) {
                            statusForDay = "Pending";
                        } else {
                            statusForDay = "Completed";
                        }
                    } else {
                        statusForDay = "Pending";
                    }
                }
            } else {
                statusForDay = "Pending";
            }

        } catch (Exception e) {
            Log.e(TAG, "Error reading dailyStatus synchronously: " + e.getMessage());
            statusForDay = "Pending";
        }

        return statusForDay;
    }

    private void applyFilter() {
        long filterDateMillis =
                selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;

        // 1. Date filtering
        List<Task> dateFilteredList = applyDateFilter(taskList);

        // 2. Search filtering
        List<Task> searchFilteredList =
                applySearchFilter(dateFilteredList, currentSearchQuery);

        // 3. Sort
        if (!searchFilteredList.isEmpty()) {
            Collections.sort(searchFilteredList, new PriorityComparator());
        }

        filteredTaskList.clear();
        taskAdapter.setUserDisplayNameMap(userDisplayNameMap);

        // 4. Build display list and apply status filter
        for (Task originalTask : searchFilteredList) {
            // CRITICAL FIX: Get the status FIRST before creating display task
            String statusForDay = getTaskStatusOnDate(originalTask, filterDateMillis);
            String statusLowerCase = statusForDay.toLowerCase(Locale.US);

            // Apply filter check BEFORE creating display task
            boolean shouldInclude = false;
            if (currentFilter.equals("done") && statusLowerCase.equals("completed")) {
                shouldInclude = true;
            } else if (currentFilter.equals("not done") && statusLowerCase.equals("pending")) {
                shouldInclude = true;
            }

            // Only create and add task if it passes the filter
            if (shouldInclude) {
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
                taskForDisplay.setUserStatus(originalTask.getUserStatus());
                taskForDisplay.setUserAiCount(originalTask.getUserAiCount());
                taskForDisplay.setUserCompletedDate(originalTask.getUserCompletedDate());

                // Set the calculated status (THIS IS CRITICAL for TaskAdapter)
                taskForDisplay.setStatus(statusForDay);

                // Also set AI count and completion date if completed
                if (statusLowerCase.equals("completed")) {
                    // Read the dailyStatus doc to get AI count and completion time
                    try {
                        long dayStart = getDayStartMillis(filterDateMillis);
                        String dateKey = storageDateKeyFormat.format(new Date(dayStart));

                        com.google.android.gms.tasks.Task<DocumentSnapshot> t =
                                FirebaseFirestore.getInstance()
                                        .collection("tasks")
                                        .document(originalTask.getId())
                                        .collection("dailyStatus")
                                        .document(dateKey)
                                        .get();

                        DocumentSnapshot snapshot = Tasks.await(t);
                        if (snapshot != null && snapshot.exists()) {
                            TaskDayStatus dayStatus = snapshot.toObject(TaskDayStatus.class);
                            if (dayStatus != null) {
                                taskForDisplay.setAiCountValue(dayStatus.getAiCountValue());
                                taskForDisplay.setCompletedDateMillis(dayStatus.getCompletedAt());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading dailyStatus for display: " + e.getMessage());
                    }
                } else {
                    taskForDisplay.setAiCountValue("");
                    taskForDisplay.setCompletedDateMillis(0L);
                }

                filteredTaskList.add(taskForDisplay);
            }
        }

        taskAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private static class PriorityComparator implements Comparator<Task> {
        private int getPriorityValue(String priority) {
            if (priority == null) return 3;
            switch (priority.toLowerCase(Locale.US)) {
                case "high":
                    return 1;
                case "medium":
                    return 2;
                case "low":
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
            if (task.getTitle() != null &&
                    task.getTitle().toLowerCase(Locale.US).contains(query)) {
                searchFilteredList.add(task);
                continue;
            }

            List<String> assignedToEmails = task.getAssignedTo();
            if (assignedToEmails != null) {
                for (String email : assignedToEmails) {
                    String displayName = userDisplayNameMap.get(email);
                    if (displayName != null &&
                            displayName.toLowerCase(Locale.US).contains(query)) {
                        searchFilteredList.add(task);
                        break;
                    }
                }
            }
        }
        return searchFilteredList;
    }

    private List<Task> applyDateFilter(List<Task> unfilteredList) {
        long filterDateMillis =
                selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
        long filterDayStart = getDayStartMillis(filterDateMillis);
        String filterDayOfWeek =
                dayOfWeekFormat.format(new Date(filterDateMillis)).substring(0, 3);

        List<Task> dateFilteredList = new ArrayList<>();

        for (Task task : unfilteredList) {
            String taskType =
                    task.getTaskType() != null
                            ? task.getTaskType().toLowerCase(Locale.US)
                            : "permanent";

            if (taskType.equals("permanent")) {
                boolean isDayActive = false;
                List<String> selectedDays = task.getSelectedDays();
                if (selectedDays != null) {
                    for (String day : selectedDays) {
                        if (day.equalsIgnoreCase(filterDayOfWeek)) {
                            isDayActive = true;
                            break;
                        }
                    }
                }
                if (isDayActive) {
                    dateFilteredList.add(task);
                }

            } else if (taskType.equals("additional")) {
                try {
                    String startDateStr = task.getStartDate();
                    String endDateStr = task.getEndDate();
                    if (startDateStr == null || startDateStr.isEmpty() ||
                            endDateStr == null || endDateStr.isEmpty()) {
                        continue;
                    }

                    long startDateMillis = dateFormat.parse(startDateStr).getTime();
                    long endDateMillis = dateFormat.parse(endDateStr).getTime();
                    long startDayStart = getDayStartMillis(startDateMillis);
                    long endDayStart = getDayStartMillis(endDateMillis);

                    if (filterDayStart >= startDayStart &&
                            filterDayStart <= endDayStart) {
                        dateFilteredList.add(task);
                    }
                } catch (Exception e) {
                    Log.e(TAG,
                            "Error parsing task dates for filtering: " + e.getMessage());
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
                        if (user.getEmail() != null &&
                                user.getDisplayName() != null) {
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
                        if (user.getEmail() != null &&
                                user.getDisplayName() != null) {
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
                    recyclerView.setVisibility(View.VISIBLE);
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

                            boolean shouldDisplay = false;
                            String taskType =
                                    task.getTaskType() != null
                                            ? task.getTaskType().toLowerCase(Locale.US)
                                            : "permanent";
                            List<String> assignedTo = task.getAssignedTo();

                            if (taskType.equals("permanent")) {
                                shouldDisplay = true;
                            } else if (taskType.equals("additional")) {
                                if (assignedTo != null &&
                                        assignedTo.contains(loggedInUserEmail)) {
                                    shouldDisplay = true;
                                }
                            }

                            if (shouldDisplay) {
                                taskList.add(task);
                            }

                        } catch (Exception e) {
                            Log.e(TAG,
                                    "Error parsing task: " + document.getId(), e);
                        }
                    }
                    applyFilter();
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
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
        if ("admin".equals(loggedInUserRole)) {
            return;
        }

        if ("user".equals(loggedInUserRole)) {
            long todayStart = getDayStartMillis(System.currentTimeMillis());
            boolean isTaskEditable = true;

            if (selectedDateMillis != -1 &&
                    getDayStartMillis(selectedDateMillis) < todayStart) {
                isTaskEditable = false;
            }

            if (task.getTaskType().equalsIgnoreCase("additional")) {
                try {
                    String endDateStr = task.getEndDate();
                    if (endDateStr != null && !endDateStr.isEmpty()) {
                        long endDateMillis = dateFormat.parse(endDateStr).getTime();
                        long endDayStart = getDayStartMillis(endDateMillis);

                        String currentDisplayStatus =
                                getTaskStatusOnDate(task,
                                        selectedDateMillis == -1
                                                ? System.currentTimeMillis()
                                                : selectedDateMillis);

                        if (todayStart > endDayStart &&
                                !currentDisplayStatus.equalsIgnoreCase("Completed")) {
                            isTaskEditable = false;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking task active status: " + e.getMessage());
                }
            }

            if (isTaskEditable) {
                Task originalTask = null;
                for (Task t : taskList) {
                    if (t.getId().equals(task.getId())) {
                        originalTask = t;
                        break;
                    }
                }
                if (originalTask != null) {
                    showTaskDetailDialog(originalTask, position);
                } else {
                    Toast.makeText(getContext(),
                            "Error: Original task not found.",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                showReadOnlyDialog(task);
            }
        }
    }

    @Override
    public void onTaskEditClick(Task task, int position) {
        if ("admin".equals(loggedInUserRole) && task.getId() != null) {
            android.content.Intent intent =
                    new android.content.Intent(getActivity(), EditTaskActivity.class);
            intent.putExtra(EXTRA_TASK_ID, task.getId());
            startActivity(intent);
        } else {
            Toast.makeText(getContext(),
                    "Error: Cannot edit task without an ID.",
                    Toast.LENGTH_SHORT).show();
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
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
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

        long currentFilterDate =
                selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
        String calculatedStatus = getTaskStatusOnDate(task, currentFilterDate);
        String statusDisplay =
                calculatedStatus.equalsIgnoreCase("Completed") ? "DONE" : "NOT DONE";
        tvDialogStatus.setText("Status: " + statusDisplay);

        if (task.getTaskType().equalsIgnoreCase("additional")) {
            String startDate = task.getStartDate();
            String endDate = task.getEndDate();
            if ((startDate != null && !startDate.isEmpty()) ||
                    (endDate != null && !endDate.isEmpty())) {
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
            String aiCountValue = task.getAiCountValue();
            if (calculatedStatus.equalsIgnoreCase("Completed") &&
                    (aiCountValue == null || aiCountValue.isEmpty())) {
                tvDialogAiCount.setText("AI Count: Missing (Status demoted to NOT DONE)");
            } else {
                tvDialogAiCount.setText("AI Count: " +
                        (aiCountValue != null && !aiCountValue.isEmpty()
                                ? aiCountValue : "N/A (Required)"));
            }
            tvDialogAiCount.setVisibility(View.VISIBLE);
        } else {
            tvDialogAiCount.setVisibility(View.GONE);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    public void onAdminTaskClick(Task task, int position) {
        showTaskCompletionDetailsDialog(task);
    }

    private void showTaskCompletionDetailsDialog(Task task) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_task_completion_details);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTaskTitle = dialog.findViewById(R.id.tv_task_title);
        TextView tvCompletedBy = dialog.findViewById(R.id.tv_completed_by);
        TextView tvCompletedDate = dialog.findViewById(R.id.tv_completed_date);
        CardView cardAiCount = dialog.findViewById(R.id.tv_ai_count_value);
        Button btnClose = dialog.findViewById(R.id.btn_close);

        tvTaskTitle.setText(task.getTitle());

        String completedByEmail = null;
        long completionTime = task.getCompletedDateMillis();
        String aiCountValue = task.getAiCountValue();

        for (Map.Entry<String, Long> entry :
                task.getUserCompletedDate().entrySet()) {
            if (entry.getValue() != null &&
                    entry.getValue().equals(completionTime)) {
                String userAiCount =
                        task.getUserAiCount().getOrDefault(entry.getKey(), "");
                if (userAiCount.equals(aiCountValue)) {
                    completedByEmail = entry.getKey();
                    break;
                }
            }
        }

        String displayName =
                userDisplayNameMap.getOrDefault(completedByEmail, "Unknown User");
        tvCompletedBy.setText("Completed by: " + displayName);

        SimpleDateFormat sdf =
                new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
        String dateStr = sdf.format(new Date(completionTime));
        tvCompletedDate.setText("📅 " + dateStr);

        if (task.isRequireAiCount()) {
            cardAiCount.setVisibility(View.VISIBLE);
            if (cardAiCount.getChildCount() > 0 &&
                    cardAiCount.getChildAt(0) instanceof TextView) {
                TextView aiCountText = (TextView) cardAiCount.getChildAt(0);
                aiCountText.setText("🔢 AI Count: " +
                        (aiCountValue != null && !aiCountValue.isEmpty()
                                ? aiCountValue : "N/A"));
            }
        } else {
            cardAiCount.setVisibility(View.GONE);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showTaskDetailDialog(Task task, int position) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_task_detail);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
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

        long displayDateMillis =
                selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
        String dayOfWeek = dayOfWeekFormat.format(new Date(displayDateMillis));
        tvCurrentDay.setText("📅 " + dayOfWeek);

        tvDialogTitle.setText(task.getTitle());
        tvDialogDescription.setText(task.getDescription());

        if (task.getTaskType().equalsIgnoreCase("additional")) {
            String startDate = task.getStartDate();
            String endDate = task.getEndDate();
            if ((startDate != null && !startDate.isEmpty()) ||
                    (endDate != null && !endDate.isEmpty())) {
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

        String currentUserStatus =
                task.getUserStatus().getOrDefault(loggedInUserEmail, "Pending")
                        .toLowerCase(Locale.US);
        String currentUserAiCount =
                task.getUserAiCount().getOrDefault(loggedInUserEmail, "");

        boolean isCompletedOnDate =
                getTaskStatusOnDate(task, displayDateMillis)
                        .equalsIgnoreCase("Completed");

        if (isCompletedOnDate) {
            rbDone.setChecked(true);
        } else {
            rbNotDone.setChecked(true);
        }

        if (task.isRequireAiCount()) {
            if (currentUserAiCount != null && !currentUserAiCount.isEmpty()) {
                etAiCount.setText(currentUserAiCount);
                tvAiCountLabel.setText("AI Count (You can edit)");
            } else {
                etAiCount.setText("");
                tvAiCountLabel.setText("Enter AI Count (Required for Completion)");
            }

            rgDialogStatus.setOnCheckedChangeListener((group, checkedId) -> {
                if (!task.isRequireAiCount()) return;
                if (checkedId == R.id.rb_dialog_done) {
                    llAiCountSection.setVisibility(View.VISIBLE);
                } else {
                    llAiCountSection.setVisibility(View.GONE);
                    etAiCount.setError(null);
                }
            });

            if (rbDone.isChecked()) {
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
                Toast.makeText(getContext(),
                        "Please select a status", Toast.LENGTH_SHORT).show();
                return;
            }

            String newStatus;
            if (selectedStatusId == R.id.rb_dialog_not_done) {
                newStatus = "Pending";
            } else if (selectedStatusId == R.id.rb_dialog_done) {
                newStatus = "Completed";
            } else {
                newStatus = "Pending";
            }

            String aiCountInput = "";
            if (task.isRequireAiCount()) {
                aiCountInput = etAiCount.getText().toString().trim();
            }

            if (task.isRequireAiCount() &&
                    newStatus.equalsIgnoreCase("Completed")) {
                if (aiCountInput.isEmpty()) {
                    etAiCount.setError("AI Count is required for completion");
                    Toast.makeText(getContext(),
                            "Please enter AI Count to mark as completed",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (!newStatus.equalsIgnoreCase("Completed")) {
                aiCountInput = "";
            }

            updateTaskInFirestore(task, newStatus, aiCountInput, position, dialog);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Writes per-user maps to task document, and per-day status
     * to /tasks/{id}/dailyStatus/{yyyy-MM-dd}. Does not overwrite history.
     */
    private void updateTaskInFirestore(Task task, String newStatus,
                                       String aiCountValue, int position,
                                       Dialog dialog) {

        if (task.getId() == null) {
            Toast.makeText(getContext(),
                    "Error: Task ID not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        long todayMillis = System.currentTimeMillis();
        long dayStart = getDayStartMillis(todayMillis);
        String dateKey = storageDateKeyFormat.format(new Date(dayStart));

        Map<String, String> userStatusMap = task.getUserStatus();
        userStatusMap.put(loggedInUserEmail, newStatus);

        Map<String, Long> userCompletedDateMap = task.getUserCompletedDate();
        final long finalCompletionTime;
        if (newStatus.equalsIgnoreCase("Completed") &&
                (!task.isRequireAiCount() ||
                        (aiCountValue != null && !aiCountValue.isEmpty()))) {
            finalCompletionTime = System.currentTimeMillis();
        } else {
            finalCompletionTime = 0L;
        }
        userCompletedDateMap.put(loggedInUserEmail, finalCompletionTime);

        Map<String, String> userAiCountMap = task.getUserAiCount();
        userAiCountMap.put(loggedInUserEmail, aiCountValue);

        TaskDayStatus dayStatus = new TaskDayStatus(
                dateKey,
                newStatus,
                aiCountValue,
                finalCompletionTime
        );

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> update = new HashMap<>();
        update.put("userStatus", userStatusMap);
        update.put("userCompletedDate", userCompletedDateMap);
        update.put("userAiCount", userAiCountMap);

        db.collection("tasks").document(task.getId())
                .update(update)
                .addOnSuccessListener(aVoid -> {
                    db.collection("tasks")
                            .document(task.getId())
                            .collection("dailyStatus")
                            .document(dateKey)
                            .set(dayStatus)
                            .addOnSuccessListener(v -> {
                                // UPDATE THE TASK IN taskList WITH FRESH DATA
                                for (Task t : taskList) {
                                    if (t.getId().equals(task.getId())) {
                                        t.setUserStatus(userStatusMap);
                                        t.setUserCompletedDate(userCompletedDateMap);
                                        t.setUserAiCount(userAiCountMap);
                                        break;
                                    }
                                }

                                // FORCE RE-APPLY FILTER TO REBUILD THE LIST
                                applyFilter();

                                String message;
                                if (newStatus.equalsIgnoreCase("Completed") &&
                                        (task.isRequireAiCount() &&
                                                aiCountValue.isEmpty())) {
                                    message = "Task not updated: AI Count is required to mark as DONE.";
                                } else {
                                    message = "Task updated successfully! Status: " +
                                            (newStatus.equalsIgnoreCase("Completed")
                                                    ? "DONE" : "NOT DONE");
                                }

                                Toast.makeText(getContext(), message,
                                        Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG,
                                        "Error writing daily status for task "
                                                + task.getId(), e);
                                Toast.makeText(getContext(),
                                        "Failed to update daily status: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating task " + task.getId(), e);
                    Toast.makeText(getContext(),
                            "Failed to update task: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showDeleteConfirmationDialog(Task taskToDelete, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task? This action cannot be undone.")
                .setPositiveButton("Delete",
                        (dialog, which) -> deleteTaskFromFirestore(taskToDelete, position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTaskFromFirestore(Task taskToDelete, int position) {
        if (taskToDelete.getId() == null) {
            Toast.makeText(getContext(),
                    "Error: Task ID not found for deletion.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("tasks").document(taskToDelete.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    taskList.removeIf(t -> t.getId().equals(taskToDelete.getId()));
                    if (position >= 0 && position < filteredTaskList.size() &&
                            filteredTaskList.get(position).getId()
                                    .equals(taskToDelete.getId())) {
                        filteredTaskList.remove(position);
                        taskAdapter.notifyItemRemoved(position);
                    } else {
                        applyFilter();
                    }

                    updateEmptyState();
                    Toast.makeText(getContext(),
                            "Task deleted successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting task " + taskToDelete.getId(), e);
                    Toast.makeText(getContext(),
                            "Failed to delete task: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
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
