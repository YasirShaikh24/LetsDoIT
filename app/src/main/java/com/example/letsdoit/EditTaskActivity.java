// src/main/java/com/example/letsdoit/EditTaskActivity.java
package com.example.letsdoit;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditTaskActivity extends AppCompatActivity {

    private TextInputEditText etTitle, etDescription, etRemarks, etStartDate, etEndDate;
    private TextInputEditText etAssigneeDisplay;
    private RadioGroup rgPriority;
    private RadioGroup rgRequireAiCount;
    private RadioGroup rgTaskType;
    private Button btnSaveTask;
    private LinearLayout llAssignUserSection;
    private LinearLayout llDateRangeSection;

    private FirebaseFirestore db;

    private String taskId;

    private List<String> selectedAssignees = new ArrayList<>();
    // This list holds the display names for the dialog (including "All Team Members")
    private List<String> allUserDisplayNames = new ArrayList<>();
    // This list holds the actual User objects (excluding "All Team Members")
    private List<User> allUsers = new ArrayList<>();

    private static final String TAG = "EditTaskActivity";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_add_activity);

        taskId = getIntent().getStringExtra(ViewActivityFragment.EXTRA_TASK_ID);
        if (taskId == null) {
            Toast.makeText(this, "Error: Invalid Task ID.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        initViews();
        // Load users, and then load task data
        loadUsersForAssignment(this::loadTaskData);
    }

    private void initViews() {
        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);
        etRemarks = findViewById(R.id.et_remarks);
        rgPriority = findViewById(R.id.rg_priority);
        rgRequireAiCount = findViewById(R.id.rg_require_ai_count);
        btnSaveTask = findViewById(R.id.btn_save_task);

        rgTaskType = findViewById(R.id.rg_task_type);
        llDateRangeSection = findViewById(R.id.ll_date_range_section);
        etStartDate = findViewById(R.id.et_start_date);
        etEndDate = findViewById(R.id.et_end_date);

        rgTaskType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_additional) {
                llDateRangeSection.setVisibility(View.VISIBLE);
            } else {
                llDateRangeSection.setVisibility(View.GONE);
            }
        });

        llAssignUserSection = findViewById(R.id.ll_assign_user_section);
        etAssigneeDisplay = findViewById(R.id.et_assigned_to_display);

        btnSaveTask.setText("Update Task");

        etStartDate.setOnClickListener(v -> showDatePicker(etStartDate));
        etEndDate.setOnClickListener(v -> showDatePicker(etEndDate));
        btnSaveTask.setOnClickListener(v -> updateTask());

        llAssignUserSection.setVisibility(View.VISIBLE);
        etAssigneeDisplay.setOnClickListener(v -> showMultiSelectUserDialog());
    }

    private void loadTaskData() {
        db.collection("tasks").document(taskId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    Task task = documentSnapshot.toObject(Task.class);
                    if (task != null) {
                        populateTaskFields(task);
                    } else {
                        Toast.makeText(this, "Task not found.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching task data", e);
                    Toast.makeText(this, "Error loading task data.", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void populateTaskFields(Task task) {
        etTitle.setText(task.getTitle());
        etDescription.setText(task.getDescription());
        etRemarks.setText(task.getRemarks());

        String taskType = task.getTaskType() != null ? task.getTaskType() : "Permanent";
        if (taskType.equalsIgnoreCase("Additional")) {
            ((RadioButton) findViewById(R.id.rb_additional)).setChecked(true);
            llDateRangeSection.setVisibility(View.VISIBLE);
            etStartDate.setText(task.getStartDate());
            etEndDate.setText(task.getEndDate());
        } else {
            ((RadioButton) findViewById(R.id.rb_permanent)).setChecked(true);
            llDateRangeSection.setVisibility(View.GONE);
            etStartDate.setText(task.getStartDate());
            etEndDate.setText(task.getEndDate());
        }

        if (task.isRequireAiCount()) {
            ((RadioButton) findViewById(R.id.rb_ai_count_yes)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.rb_ai_count_no)).setChecked(true);
        }

        String priority = task.getPriority() != null ? task.getPriority().toLowerCase() : "low";
        if (priority.equals("low")) {
            ((RadioButton) findViewById(R.id.rb_low)).setChecked(true);
        } else if (priority.equals("medium")) {
            ((RadioButton) findViewById(R.id.rb_medium)).setChecked(true);
        } else if (priority.equals("high")) {
            ((RadioButton) findViewById(R.id.rb_high)).setChecked(true);
        }

        selectedAssignees.addAll(task.getAssignedTo());

        updateAssigneeDisplay();
    }

    private void loadUsersForAssignment(Runnable onComplete) {
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allUsers.clear();
                    allUserDisplayNames.clear();

                    // 1. Add "All Team Members" option at index 0 for display
                    allUserDisplayNames.add("All Team Members");

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        User user = document.toObject(User.class);
                        allUsers.add(user);
                        // 2. Add only the display name (as requested)
                        String displayName = user.getDisplayName();
                        allUserDisplayNames.add(displayName);
                    }
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load users for assignment: " + e.getMessage());
                    Toast.makeText(this, "Could not load users for assignment.", Toast.LENGTH_SHORT).show();
                    onComplete.run();
                });
    }

    private void showMultiSelectUserDialog() {
        if (allUserDisplayNames.size() <= 1) {
            Toast.makeText(this, "No users found to assign.", Toast.LENGTH_SHORT).show();
            return;
        }

        final CharSequence[] items = allUserDisplayNames.toArray(new CharSequence[0]);
        final boolean[] checkedItems = new boolean[allUserDisplayNames.size()];
        final int actualUserCount = allUsers.size();

        // Initialization: Check individual users first
        boolean allChecked = true;
        for (int i = 0; i < actualUserCount; i++) {
            if (selectedAssignees.contains(allUsers.get(i).getEmail())) {
                checkedItems[i + 1] = true;
            } else {
                allChecked = false;
            }
        }
        // Set initial state of "All" checkbox (index 0)
        checkedItems[0] = allChecked;

        new AlertDialog.Builder(this)
                .setTitle("Select Team Members")
                .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                    if (which == 0) {
                        // "All Team Members" is selected/deselected
                        for (int i = 1; i < checkedItems.length; i++) {
                            checkedItems[i] = isChecked;
                            // Manually update the list view state
                            ((AlertDialog) dialog).getListView().setItemChecked(i, isChecked);
                        }
                    } else {
                        // Individual user selected/deselected
                        if (!isChecked) {
                            // If any user is deselected, deselect "All Team Members"
                            checkedItems[0] = false;
                            ((AlertDialog) dialog).getListView().setItemChecked(0, false);
                        } else {
                            // Check if all individual members are now selected
                            boolean allSelected = true;
                            for (int i = 1; i < checkedItems.length; i++) {
                                if (!checkedItems[i]) {
                                    allSelected = false;
                                    break;
                                }
                            }
                            if (allSelected) {
                                checkedItems[0] = true;
                                ((AlertDialog) dialog).getListView().setItemChecked(0, true);
                            }
                        }
                    }
                })
                .setPositiveButton("OK", (dialog, id) -> {
                    selectedAssignees.clear();
                    if (checkedItems[0]) {
                        // If "All" is selected, add all user emails
                        for (User user : allUsers) {
                            selectedAssignees.add(user.getEmail());
                        }
                    } else {
                        // Otherwise, iterate through individual users (index 1 onwards)
                        for (int i = 1; i < allUserDisplayNames.size(); i++) {
                            if (checkedItems[i]) {
                                // allUsers index is i - 1
                                selectedAssignees.add(allUsers.get(i - 1).getEmail());
                            }
                        }
                    }
                    updateAssigneeDisplay();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateAssigneeDisplay() {
        if (selectedAssignees.isEmpty()) {
            etAssigneeDisplay.setText("No users selected");
        } else if (selectedAssignees.size() == allUsers.size()) {
            etAssigneeDisplay.setText("All Team Members");
        } else {
            List<String> displayNames = new ArrayList<>();
            for (String email : selectedAssignees) {
                for (User user : allUsers) {
                    if (user.getEmail().equals(email)) {
                        displayNames.add(user.getDisplayName());
                        break;
                    }
                }
            }
            etAssigneeDisplay.setText(String.join(", ", displayNames));
        }
    }

    private void showDatePicker(final TextInputEditText dateEditText) {
        final Calendar calendar = Calendar.getInstance();

        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, monthOfYear, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            dateEditText.setText(dateFormat.format(calendar.getTime()));
        };

        new DatePickerDialog(this, dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateTask() {
        String title = etTitle.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String remarks = etRemarks.getText().toString().trim();

        int selectedTaskTypeId = rgTaskType.getCheckedRadioButtonId();
        if (selectedTaskTypeId == -1) {
            Toast.makeText(this, "Please select a task duration type.", Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton selectedTaskTypeButton = findViewById(selectedTaskTypeId);
        String taskType = selectedTaskTypeButton.getText().toString();

        String startDate = "";
        String endDate = "";

        if (taskType.equalsIgnoreCase("Additional")) {
            startDate = etStartDate.getText().toString().trim();
            endDate = etEndDate.getText().toString().trim();
        }


        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            return;
        }

        if (selectedAssignees.isEmpty()) {
            Toast.makeText(this, "Please assign the task to at least one user.", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedPriorityId = rgPriority.getCheckedRadioButtonId();
        if (selectedPriorityId == -1) {
            Toast.makeText(this, "Please select a priority", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedPriorityButton = findViewById(selectedPriorityId);
        String priority = selectedPriorityButton.getText().toString().toLowerCase();

        int selectedAiCountId = rgRequireAiCount.getCheckedRadioButtonId();
        boolean requireAiCount = (selectedAiCountId == R.id.rb_ai_count_yes);

        btnSaveTask.setEnabled(false);
        btnSaveTask.setText("Updating...");

        saveTaskUpdateToFirestore(title, description, priority, remarks, selectedAssignees, startDate, endDate, requireAiCount, taskType);
    }

    private void saveTaskUpdateToFirestore(String title, String description, String priority, String remarks, List<String> assignedTo, String startDate, String endDate, boolean requireAiCount, String taskType) {
        Map<String, Object> taskUpdates = new HashMap<>();
        taskUpdates.put("title", title);
        taskUpdates.put("description", description);
        taskUpdates.put("priority", priority);
        taskUpdates.put("fileUrls", new ArrayList<String>());
        taskUpdates.put("remarks", remarks);
        taskUpdates.put("assignedTo", assignedTo);
        taskUpdates.put("startDate", startDate);
        taskUpdates.put("endDate", endDate);
        taskUpdates.put("requireAiCount", requireAiCount);
        taskUpdates.put("taskType", taskType);

        db.collection("tasks").document(taskId)
                .update(taskUpdates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Task updated successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error updating task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSaveTask.setEnabled(true);
                    btnSaveTask.setText("Update Task");
                });
    }
}