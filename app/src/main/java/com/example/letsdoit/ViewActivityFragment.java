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
import android.widget.LinearLayout; // Import LinearLayout
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
    private List<Task> taskList; // Holds actual data from Firestore
    private List<Task> filteredTaskList; // Holds clones with adjusted status for display
    private FirebaseFirestore db;
    private ProgressBar progressBar;

    // FIX: Changed tvEmptyState from TextView to LinearLayout for container
    private LinearLayout llEmptyState;
    // NEW: Added a TextView for the message inside the container
    private TextView tvEmptyStateTitle;

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

        // FIX: Assigning the correct views based on updated XML IDs
        llEmptyState = view.findViewById(R.id.ll_empty_state);
        tvEmptyStateTitle = view.findViewById(R.id.tv_empty_state_title);

        rgStatusFilter = view.findViewById(R.id.rg_status_filter);
        tvDateIndicator = view.findViewById(R.id.tv_date_indicator);
        btnCalendar = view.findViewById(R.id.btn_calendar);
        etSearchQuery = view.findViewById(R.id.et_search_query);
        btnSearchToggle = view.findViewById(R.id.btn_search_toggle);
        final TextInputLayout tilSearch = view.findViewById(R.id.til_search);

        db = FirebaseFirestore.getInstance();

        taskList = new ArrayList<>();
        filteredTaskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(filteredTaskList, getContext(), this, loggedInUserRole);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(taskAdapter);

        setupFilterListeners();
        setupCalendar();
        setupSearch(tilSearch);
        loadTasks();

        return view;
    }

    private void setupCalendar() {
        selectedDateMillis = -1;
        updateDateIndicator();

        btnCalendar.setOnClickListener(v -> {
            CalendarDialogFragment dialogFragment = CalendarDialogFragment.newInstance(selectedDateMillis != -1 ? selectedDateMillis : null);
            dialogFragment.setOnDateSelectedListener(this);
            dialogFragment.show(getParentFragmentManager(), "CalendarDialog");
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

    @Override
    public void onDateSelected(long dateInMillis, String formattedDate) {
        selectedDateMillis = dateInMillis;
        updateDateIndicator();
        applyFilter();
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

    private String getTaskStatusOnDate(Task task, long filterDateMillis) {
        String currentStatus = task.getStatus();

        if (filterDateMillis == -1 || getDayStartMillis(filterDateMillis) >= getDayStartMillis(System.currentTimeMillis())) {
            return currentStatus;
        }

        long filterDayStart = getDayStartMillis(filterDateMillis);
        long filterDayEnd = filterDayStart + (24 * 60 * 60 * 1000L) - 1;

        if (task.getTaskType().equalsIgnoreCase("permanent")) {
            long completionTime = task.getCompletedDateMillis();

            if (completionTime > filterDayStart && completionTime <= filterDayEnd) {
                return "Completed";
            }
            return "Pending";
        }

        if (currentStatus.equalsIgnoreCase("Completed")) {
            long completionTime = task.getCompletedDateMillis();

            if (completionTime > 0 && completionTime > filterDayEnd) {
                return "Pending";
            }
            return "Completed";
        }

        return "Pending";
    }

    private int getPriorityRank(String priority) {
        if (priority == null) return 0;
        switch (priority.toLowerCase()) {
            case "high":
                return 3;
            case "medium":
                return 2;
            case "low":
            default:
                return 1;
        }
    }

    private void applyFilter() {
        List<Task> dateFilteredList = applyDateFilter(taskList);
        List<Task> searchFilteredList = applySearchFilter(dateFilteredList, currentSearchQuery);

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

            long filterDateMillis = selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
            String historicalStatus = getTaskStatusOnDate(originalTask, filterDateMillis);
            taskForDisplay.setStatus(historicalStatus);

            String taskStatus = taskForDisplay.getStatus() != null ? taskForDisplay.getStatus().toLowerCase() : "pending";

            if (currentFilter.equals("all") || taskStatus.equals(currentFilter)) {
                filteredTaskList.add(taskForDisplay);
            }
        }

        // ENHANCEMENT: Custom sorting by Priority (High -> Medium -> Low) and then by Timestamp (Descending)
        Collections.sort(filteredTaskList, (t1, t2) -> {
            // 1. Priority Comparison (High > Medium > Low) - Descending order
            int p1 = getPriorityRank(t1.getPriority());
            int p2 = getPriorityRank(t2.getPriority());

            if (p1 != p2) {
                return Integer.compare(p2, p1);
            }

            // 2. Secondary sort: Timestamp (Newer tasks first, DESCENDING)
            // This is a stable sort: if priorities are equal, the original timestamp sort from Firestore is maintained.
            return Long.compare(t2.getTimestamp(), t1.getTimestamp());
        });

        taskAdapter.notifyDataSetChanged();
        updateEmptyState();
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

    private List<Task> applyDateFilter(List<Task> unfilteredList) {
        long filterDateMillis = selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
        long filterDayStart = getDayStartMillis(filterDateMillis);

        List<Task> dateFilteredList = new ArrayList<>();

        for (Task task : unfilteredList) {
            String taskType = task.getTaskType().toLowerCase();

            if (taskType.equals("permanent")) {
                if (task.getTimestamp() < filterDayStart + (24 * 60 * 60 * 1000L)) {
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
            String filterName = currentFilter.substring(0, 1).toUpperCase() + currentFilter.substring(1);
            if (currentFilter.equals("all")) {
                tvEmptyStateTitle.setText("No tasks found for this date."); // FIX: Setting text on the inner TextView
            } else {
                tvEmptyStateTitle.setText("No " + filterName + " tasks found for this date."); // FIX: Setting text on the inner TextView
            }
            llEmptyState.setVisibility(View.VISIBLE); // FIX: Setting visibility on the outer LinearLayout
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
                    tvEmptyStateTitle.setText("Error loading tasks: " + e.getMessage()); // FIX: Setting text on the inner TextView
                    llEmptyState.setVisibility(View.VISIBLE); // FIX: Setting visibility on the outer LinearLayout
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

                    // Original sort by timestamp - kept for consistency before filter is applied
                    Collections.sort(taskList, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                    applyFilter();
                    progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading tasks for user", e);
                    progressBar.setVisibility(View.GONE);
                    tvEmptyStateTitle.setText("Error loading tasks: " + e.getMessage()); // FIX: Setting text on the inner TextView
                    llEmptyState.setVisibility(View.VISIBLE); // FIX: Setting visibility on the outer LinearLayout
                    recyclerView.setVisibility(View.GONE);
                });
    }

    @Override
    public void onTaskStatusClick(Task task, int position) {
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

        final long finalCompletionTime;
        if (newStatus.equalsIgnoreCase("Completed")) {
            finalCompletionTime = System.currentTimeMillis();
            update.put("completedDateMillis", finalCompletionTime);
        } else {
            finalCompletionTime = 0;
            update.put("completedDateMillis", 0);
        }

        if (task.isRequireAiCount()) {
            if (aiCountValue != null && !aiCountValue.isEmpty()) {
                update.put("aiCountValue", aiCountValue);
            }
        }

        db.collection("tasks").document(task.getId())
                .update(update)
                .addOnSuccessListener(aVoid -> {
                    task.setStatus(newStatus);
                    task.setCompletedDateMillis(finalCompletionTime);
                    if (aiCountValue != null && !aiCountValue.isEmpty()) {
                        task.setAiCountValue(aiCountValue);
                    }

                    for (Task t : taskList) {
                        if (t.getId().equals(task.getId())) {
                            t.setStatus(newStatus);
                            t.setCompletedDateMillis(finalCompletionTime);
                            if (aiCountValue != null && !aiCountValue.isEmpty()) {
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
                    // Find and remove the task from the main list as well
                    taskList.removeIf(t -> t.getId().equals(task.getId()));
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