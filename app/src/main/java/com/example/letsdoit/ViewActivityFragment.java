// src/main/java/com/example/letsdoit/ViewActivityFragment.java
package com.example.letsdoit;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class ViewActivityFragment extends Fragment implements TaskAdapter.TaskActionListener {

    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private List<Task> taskList;
    private List<Task> filteredTaskList;
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private RadioGroup rgStatusFilter;

    // NEW Search UI Elements
    private ImageButton btnSearchToggle;
    private TextInputLayout tilSearch;
    private TextInputEditText etSearchQuery;
    private boolean isSearchVisible = false; // To track search bar visibility

    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String currentFilter = "all";
    private String currentSearchQuery = ""; // NEW

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

        // NEW: Initialize Search UI Elements
        btnSearchToggle = view.findViewById(R.id.btn_search_toggle);
        tilSearch = view.findViewById(R.id.til_search);
        etSearchQuery = view.findViewById(R.id.et_search_query);

        db = FirebaseFirestore.getInstance();

        taskList = new ArrayList<>();
        filteredTaskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(filteredTaskList, getContext(), this, loggedInUserRole);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(taskAdapter);

        setupFilterListeners();
        setupSearchListeners(); // NEW
        loadTasks();

        return view;
    }

    private void setupSearchListeners() {
        btnSearchToggle.setOnClickListener(v -> {
            isSearchVisible = !isSearchVisible;
            if (isSearchVisible) {
                tilSearch.setVisibility(View.VISIBLE);
                // Optionally request focus for immediate typing
                etSearchQuery.requestFocus();
            } else {
                tilSearch.setVisibility(View.GONE);
                etSearchQuery.setText(""); // Clear search on hide
            }
        });

        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().toLowerCase(Locale.getDefault()).trim();
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
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
        String currentStatusFilter = currentFilter.equals("all") ? null : currentFilter;

        for (Task task : taskList) {
            String taskStatus = task.getStatus() != null ? task.getStatus().toLowerCase() : "pending";
            String taskTitle = task.getTitle() != null ? task.getTitle().toLowerCase(Locale.getDefault()) : "";

            boolean statusMatches = (currentStatusFilter == null || taskStatus.equals(currentStatusFilter));
            // NEW: Search filters by task title, ignoring case and leading/trailing whitespace
            boolean searchMatches = currentSearchQuery.isEmpty() || taskTitle.contains(currentSearchQuery);

            if (statusMatches && searchMatches) {
                filteredTaskList.add(task);
            }
        }

        taskAdapter.notifyDataSetChanged();

        if (filteredTaskList.isEmpty()) {
            String emptyMessage = "No tasks found.";
            if (!currentSearchQuery.isEmpty()) {
                emptyMessage = "No tasks matching '" + currentSearchQuery + "' found.";
            } else if (!currentFilter.equals("all")) {
                String filterName = currentFilter.substring(0, 1).toUpperCase() + currentFilter.substring(1);
                emptyMessage = "No " + filterName + " tasks found.";
            }

            tvEmptyState.setText(emptyMessage);
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
            showTaskDetailDialog(task, position);
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

    // Custom dialog for task details with AI Count editing
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

        // Populate task details
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

    private void updateTaskInFirestore(Task task, String newStatus, String aiCountValue, int position, Dialog dialog) {
        if (task.getId() == null) {
            Toast.makeText(getContext(), "Error: Task ID not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("status", newStatus);

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
                    if (aiCountValue != null && !aiCountValue.isEmpty()) {
                        task.setAiCountValue(aiCountValue);
                    }

                    // Find and update in taskList
                    for (Task t : taskList) {
                        if (t.getId().equals(task.getId())) {
                            t.setStatus(newStatus);
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
                    taskList.remove(task);
                    // A safe removal from filtered list before calling notifyItemRemoved
                    if (position < filteredTaskList.size() && filteredTaskList.get(position).getId().equals(task.getId())) {
                        filteredTaskList.remove(position);
                        taskAdapter.notifyItemRemoved(position);
                    } else {
                        // If index is compromised, just re-run filter on the new list
                        applyFilter();
                    }

                    Toast.makeText(getContext(), "Task deleted successfully!", Toast.LENGTH_SHORT).show();

                    if (filteredTaskList.isEmpty()) {
                        String filterName = currentFilter.substring(0, 1).toUpperCase() + currentFilter.substring(1);
                        if (currentFilter.equals("all")) {
                            tvEmptyState.setText("No tasks in the system.");
                        } else {
                            tvEmptyState.setText("No " + filterName + " tasks found.");
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