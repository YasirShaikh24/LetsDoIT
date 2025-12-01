// src/main/java/com/example/letsdoit/EditTaskActivity.java
package com.example.letsdoit;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
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
    // MODIFIED: From CheckBox to RadioGroup
    private RadioGroup rgRequireAiCount;
    // NEW: RadioGroup for Task Type
    private RadioGroup rgTaskType;
    private Button btnSaveTask;
    private LinearLayout llAssignUserSection;
    // NEW: LinearLayout for Date Range
    private LinearLayout llDateRangeSection;

    private FirebaseFirestore db;

    private String taskId;

    private List<String> selectedAssignees = new ArrayList<>();
    private List<String> allUserDisplayNames = new ArrayList<>();
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
        loadUsersForAssignment(this::loadTaskData);
    }

    private void initViews() {
        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);
        etRemarks = findViewById(R.id.et_remarks);
        rgPriority = findViewById(R.id.rg_priority);
        // MODIFIED: From CheckBox to RadioGroup
        rgRequireAiCount = findViewById(R.id.rg_require_ai_count);
        btnSaveTask = findViewById(R.id.btn_save_task);

        // NEW: Task Type and Date Range Section
        rgTaskType = findViewById(R.id.rg_task_type);
        llDateRangeSection = findViewById(R.id.ll_date_range_section);
        etStartDate = findViewById(R.id.et_start_date);
        etEndDate = findViewById(R.id.et_end_date);

        // Task Type Listener to toggle date range visibility
        rgTaskType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_additional) {
                llDateRangeSection.setVisibility(View.VISIBLE);
            } else {
                llDateRangeSection.setVisibility(View.GONE);
                // Note: We don't clear dates here during editing, as they might be needed later.
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

        // NEW: Set Task Type and toggle date visibility
        String taskType = task.getTaskType() != null ? task.getTaskType() : "Permanent";
        if (taskType.equalsIgnoreCase("Additional")) {
            ((RadioButton) findViewById(R.id.rb_additional)).setChecked(true);
            llDateRangeSection.setVisibility(View.VISIBLE);
            etStartDate.setText(task.getStartDate());
            etEndDate.setText(task.getEndDate());
        } else {
            ((RadioButton) findViewById(R.id.rb_permanent)).setChecked(true);
            llDateRangeSection.setVisibility(View.GONE);
            // Even if hidden, populate fields in case user switches type
            etStartDate.setText(task.getStartDate());
            etEndDate.setText(task.getEndDate());
        }

        // MODIFIED: Set AI Count radio button
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
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        User user = document.toObject(User.class);
                        allUsers.add(user);
                        String displayName = user.getDisplayName() + " (" + user.getEmail() + ")";
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
        if (allUserDisplayNames.isEmpty()) {
            Toast.makeText(this, "No users found to assign.", Toast.LENGTH_SHORT).show();
            return;
        }

        final CharSequence[] items = allUserDisplayNames.toArray(new CharSequence[0]);
        final boolean[] checkedItems = new boolean[allUsers.size()];

        for (int i = 0; i < allUsers.size(); i++) {
            if (selectedAssignees.contains(allUsers.get(i).getEmail())) {
                checkedItems[i] = true;
            } else {
                checkedItems[i] = false;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Team Members")
                .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                .setPositiveButton("OK", (dialog, id) -> {
                    selectedAssignees.clear();
                    for (int i = 0; i < allUsers.size(); i++) {
                        if (checkedItems[i]) {
                            selectedAssignees.add(allUsers.get(i).getEmail());
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

        // NEW: Get Task Type
        int selectedTaskTypeId = rgTaskType.getCheckedRadioButtonId();
        if (selectedTaskTypeId == -1) {
            Toast.makeText(this, "Please select a task duration type.", Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton selectedTaskTypeButton = findViewById(selectedTaskTypeId);
        String taskType = selectedTaskTypeButton.getText().toString();

        String startDate = "";
        String endDate = "";

        // Only use dates if Task Type is "Additional"
        if (taskType.equalsIgnoreCase("Additional")) {
            startDate = etStartDate.getText().toString().trim();
            endDate = etEndDate.getText().toString().trim();
        }


        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            return;
        }

        // DESCRIPTION IS NOW OPTIONAL - Validation removed.

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

        // MODIFIED: Get AI Count radio button state
        int selectedAiCountId = rgRequireAiCount.getCheckedRadioButtonId();
        boolean requireAiCount = (selectedAiCountId == R.id.rb_ai_count_yes);

        btnSaveTask.setEnabled(false);
        btnSaveTask.setText("Updating...");

        // UPDATED: Added taskType argument
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
        taskUpdates.put("taskType", taskType); // NEW

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