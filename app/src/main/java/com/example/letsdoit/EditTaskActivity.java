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
import java.util.stream.Collectors; // Added for stream use

public class EditTaskActivity extends AppCompatActivity {

    private TextInputEditText etTitle, etDescription, etStartDate, etEndDate; // etRemarks removed
    private TextInputEditText etAssigneeDisplay;
    // private RadioGroup rgPriority; // rgPriority removed
    private RadioGroup rgRequireAiCount;
    private RadioGroup rgTaskType;
    private RadioGroup rgWeekDays; // NEW
    private Button btnSaveTask;
    private LinearLayout llAssignUserSection;
    private LinearLayout llDateRangeSection;
    private LinearLayout llWeekDaysSection; // NEW

    private FirebaseFirestore db;

    private String taskId;

    private List<String> selectedAssignees = new ArrayList<>();
    // This list holds the display names for the dialog (including "All Team Members")
    private List<String> allUserDisplayNames = new ArrayList<>();
    // This list holds the actual User objects (excluding "All Team Members")
    private List<User> allUsers = new ArrayList<>();

    // NEW: List to track selected days for permanent tasks
    private List<String> selectedDays = new ArrayList<>();

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
        // etRemarks removed
        // rgPriority removed
        rgRequireAiCount = findViewById(R.id.rg_require_ai_count);
        btnSaveTask = findViewById(R.id.btn_save_task);

        rgTaskType = findViewById(R.id.rg_task_type);
        llDateRangeSection = findViewById(R.id.ll_date_range_section);
        etStartDate = findViewById(R.id.et_start_date);
        etEndDate = findViewById(R.id.et_end_date);

        llWeekDaysSection = findViewById(R.id.ll_week_days_section); // NEW
        rgWeekDays = findViewById(R.id.rg_week_days); // NEW

        btnSaveTask.setText("Update Task");

        etStartDate.setOnClickListener(v -> showDatePicker(etStartDate));
        etEndDate.setOnClickListener(v -> showDatePicker(etEndDate));
        btnSaveTask.setOnClickListener(v -> updateTask());

        llAssignUserSection = findViewById(R.id.ll_assign_user_section);
        etAssigneeDisplay = findViewById(R.id.et_assigned_to_display);

        llAssignUserSection.setVisibility(View.VISIBLE); // Always visible for admin by default
        etAssigneeDisplay.setOnClickListener(v -> showMultiSelectUserDialog());

        setupTaskTypeListener(); // NEW method for cleaner logic
        setupWeekDaysListener(); // NEW method for multi-select logic
    }

    private void setupTaskTypeListener() {
        rgTaskType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isPermanent = checkedId == R.id.rb_permanent;

            llDateRangeSection.setVisibility(isPermanent ? View.GONE : View.VISIBLE);
            llWeekDaysSection.setVisibility(isPermanent ? View.VISIBLE : View.GONE);

            // Assignment visibility/selection logic for Admin (Edit is Admin-only)
            llAssignUserSection.setVisibility(isPermanent ? View.GONE : View.VISIBLE);

            if (isPermanent) {
                // For Permanent, auto-set assignedTo to All
                autoAssignAllUsers();
            } else {
                // For Additional, clear selected days
                clearSelectedDays();
                // NEW: Clear assignees when switching to Additional
                selectedAssignees.clear();
                updateAssigneeDisplay();
            }

            // Note: Date/Day inputs are populated during loadTaskData, this listener only handles view switching.
        });
    }

    // Same multi-select logic as AddActivityFragment
    private void setupWeekDaysListener() {
        for (int i = 0; i < rgWeekDays.getChildCount(); i++) {
            RadioButton rb = (RadioButton) rgWeekDays.getChildAt(i);
            rb.setOnClickListener(v -> {
                RadioButton clickedRb = (RadioButton) v;
                String day = clickedRb.getText().toString(); // e.g., "M", "T", "W"
                String fullDayName = getFullDayName(day);

                // Force toggle state and update list
                if (selectedDays.contains(fullDayName)) {
                    selectedDays.remove(fullDayName);
                    clickedRb.setChecked(false);
                } else {
                    selectedDays.add(fullDayName);
                    clickedRb.setChecked(true);
                }

                // Since we are inside a RadioGroup, clear all other checks and re-set the clicked state
                // to make it behave like a multi-select visual component.
                int id = clickedRb.getId();
                rgWeekDays.clearCheck();
                clickedRb.setId(id);
                clickedRb.setChecked(selectedDays.contains(fullDayName));
            });
        }
    }

    private void clearSelectedDays() {
        selectedDays.clear();
        if (rgWeekDays != null) {
            for (int i = 0; i < rgWeekDays.getChildCount(); i++) {
                RadioButton rb = (RadioButton) rgWeekDays.getChildAt(i);
                rb.setChecked(false);
            }
        }
    }

    private String getFullDayName(String dayAbbr) {
        switch (dayAbbr) {
            case "M": return "Mon";
            case "T": return "Tue";
            case "W": return "Wed";
            case "Th": return "Thu";
            case "F": return "Fri";
            case "Sa": return "Sat";
            case "Su": return "Sun";
            default: return "";
        }
    }

    private void autoAssignAllUsers() {
        selectedAssignees.clear();
        for (User user : allUsers) {
            selectedAssignees.add(user.getEmail());
        }
        updateAssigneeDisplay();
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
        // etRemarks removed

        String taskType = task.getTaskType() != null ? task.getTaskType() : "Permanent";

        if (taskType.equalsIgnoreCase("Additional")) {
            ((RadioButton) findViewById(R.id.rb_additional)).setChecked(true);
            llWeekDaysSection.setVisibility(View.GONE);
            llDateRangeSection.setVisibility(View.VISIBLE);
            llAssignUserSection.setVisibility(View.VISIBLE);
            etStartDate.setText(task.getStartDate());
            etEndDate.setText(task.getEndDate());
        } else {
            ((RadioButton) findViewById(R.id.rb_permanent)).setChecked(true);
            llWeekDaysSection.setVisibility(View.VISIBLE);
            llDateRangeSection.setVisibility(View.GONE);
            llAssignUserSection.setVisibility(View.GONE); // Hidden for permanent
            // Populate selected days
            selectedDays.clear();
            selectedDays.addAll(task.getSelectedDays());
            for (int i = 0; i < rgWeekDays.getChildCount(); i++) {
                RadioButton rb = (RadioButton) rgWeekDays.getChildAt(i);
                String fullDayName = getFullDayName(rb.getText().toString());
                if (selectedDays.contains(fullDayName)) {
                    rb.setChecked(true);
                }
            }
        }

        if (task.isRequireAiCount()) {
            ((RadioButton) findViewById(R.id.rb_ai_count_yes)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.rb_ai_count_no)).setChecked(true);
        }

        // Priority logic removed

        selectedAssignees.clear(); // Clear before adding to avoid duplicates if autoAssignAllUsers() ran earlier
        selectedAssignees.addAll(task.getAssignedTo());

        // If permanent is selected, autoAssignAllUsers will run in setupTaskTypeListener's check
        // when the radio button is set. We call updateAssigneeDisplay() regardless.
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
        } else if (selectedAssignees.size() == allUsers.size() && allUsers.size() > 0) {
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
        String remarks = ""; // REMOVED: Default to empty string
        String priority = "medium"; // REMOVED: Default to fixed value

        int selectedTaskTypeId = rgTaskType.getCheckedRadioButtonId();
        if (selectedTaskTypeId == -1) {
            Toast.makeText(this, "Please select a task duration type.", Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton selectedTaskTypeButton = findViewById(selectedTaskTypeId);
        String taskType = selectedTaskTypeButton.getText().toString();

        String startDate = "";
        String endDate = "";
        List<String> finalSelectedDays = new ArrayList<>(); // Use this list for the update

        if (taskType.equalsIgnoreCase("Additional")) {
            startDate = etStartDate.getText().toString().trim();
            endDate = etEndDate.getText().toString().trim();
        } else {
            // Permanent task validation and assignment
            finalSelectedDays.addAll(selectedDays);
            if (finalSelectedDays.isEmpty()) {
                Toast.makeText(this, "Please select at least one active day for the permanent task.", Toast.LENGTH_SHORT).show();
                return;
            }
            // Overwrite selectedAssignees to be ALL users for Permanent tasks
            autoAssignAllUsers();
        }


        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            return;
        }

        if (selectedAssignees.isEmpty()) {
            Toast.makeText(this, "Please assign the task to at least one user.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Priority check removed

        int selectedAiCountId = rgRequireAiCount.getCheckedRadioButtonId();
        boolean requireAiCount = (selectedAiCountId == R.id.rb_ai_count_yes);

        btnSaveTask.setEnabled(false);
        btnSaveTask.setText("Updating...");

        saveTaskUpdateToFirestore(title, description, priority, remarks, selectedAssignees, startDate, endDate, requireAiCount, taskType, finalSelectedDays);
    }

    private void saveTaskUpdateToFirestore(String title, String description, String priority, String remarks, List<String> assignedTo, String startDate, String endDate, boolean requireAiCount, String taskType, List<String> selectedDays) {
        Map<String, Object> taskUpdates = new HashMap<>();
        taskUpdates.put("title", title);
        taskUpdates.put("description", description);
        taskUpdates.put("priority", priority); // Keep for compatibility, defaulted to "medium"
        taskUpdates.put("fileUrls", new ArrayList<String>());
        taskUpdates.put("remarks", remarks); // Keep for compatibility, defaulted to ""
        taskUpdates.put("assignedTo", assignedTo);
        taskUpdates.put("startDate", startDate);
        taskUpdates.put("endDate", endDate);
        taskUpdates.put("requireAiCount", requireAiCount);
        taskUpdates.put("taskType", taskType);
        taskUpdates.put("selectedDays", selectedDays); // NEW FIELD

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