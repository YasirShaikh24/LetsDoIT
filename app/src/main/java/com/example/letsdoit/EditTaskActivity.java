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
import android.widget.CheckBox; // <-- ADDED IMPORT
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

    private TextInputEditText etTitle, etDescription, etStartDate, etEndDate;
    private TextInputEditText etAssigneeDisplay;
    private RadioGroup rgRequireAiCount;
    private RadioGroup rgTaskType;
    // REMOVED: private RadioGroup rgWeekDays;

    // ADDED: CheckBox declarations
    private CheckBox cbMonday, cbTuesday, cbWednesday, cbThursday, cbFriday, cbSaturday, cbSunday;

    private Button btnSaveTask;
    private LinearLayout llAssignUserSection;
    private LinearLayout llDateRangeSection;
    private LinearLayout llWeekDaysSection;

    private FirebaseFirestore db;

    private String taskId;

    private List<String> selectedAssignees = new ArrayList<>();
    private List<String> allUserDisplayNames = new ArrayList<>();
    private List<User> allUsers = new ArrayList<>();

    private List<String> selectedDays = new ArrayList<>();

    private static final String TAG = "EditTaskActivity";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Uses fragment_add_activity.xml which now has CheckBoxes
        setContentView(R.layout.fragment_add_activity);

        taskId = getIntent().getStringExtra(ViewActivityFragment.EXTRA_TASK_ID);
        if (taskId == null) {
            Toast.makeText(this, "Error: Invalid Task ID.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        loadUsersForAssignment(this::loadTaskData);
    }

    private void initViews() {
        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);
        rgRequireAiCount = findViewById(R.id.rg_require_ai_count);
        btnSaveTask = findViewById(R.id.btn_save_task);

        rgTaskType = findViewById(R.id.rg_task_type);
        llDateRangeSection = findViewById(R.id.ll_date_range_section);
        etStartDate = findViewById(R.id.et_start_date);
        etEndDate = findViewById(R.id.et_end_date);

        llWeekDaysSection = findViewById(R.id.ll_week_days_section);
        // REMOVED: rgWeekDays = findViewById(R.id.rg_week_days);

        // ADDED: Initialize CheckBoxes
        cbMonday = findViewById(R.id.cb_monday);
        cbTuesday = findViewById(R.id.cb_tuesday);
        cbWednesday = findViewById(R.id.cb_wednesday);
        cbThursday = findViewById(R.id.cb_thursday);
        cbFriday = findViewById(R.id.cb_friday);
        cbSaturday = findViewById(R.id.cb_saturday);
        cbSunday = findViewById(R.id.cb_sunday);

        btnSaveTask.setText("Update Task");

        etStartDate.setOnClickListener(v -> showDatePicker(etStartDate));
        etEndDate.setOnClickListener(v -> showDatePicker(etEndDate));
        btnSaveTask.setOnClickListener(v -> updateTask());

        llAssignUserSection = findViewById(R.id.ll_assign_user_section);
        etAssigneeDisplay = findViewById(R.id.et_assigned_to_display);

        llAssignUserSection.setVisibility(View.VISIBLE);
        etAssigneeDisplay.setOnClickListener(v -> showMultiSelectUserDialog());

        setupTaskTypeListener();
        setupWeekDaysListener();
    }

    private void setupTaskTypeListener() {
        rgTaskType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isPermanent = checkedId == R.id.rb_permanent;

            llDateRangeSection.setVisibility(isPermanent ? View.GONE : View.VISIBLE);
            llWeekDaysSection.setVisibility(isPermanent ? View.VISIBLE : View.GONE);

            llAssignUserSection.setVisibility(isPermanent ? View.GONE : View.VISIBLE);

            if (isPermanent) {
                autoAssignAllUsers();
            } else {
                clearSelectedDays();
                selectedAssignees.clear();
                updateAssigneeDisplay();
            }
        });
    }

    private void setupWeekDaysListener() {
        // REPLACED: Logic to handle CheckBoxes
        CheckBox[] checkBoxes = new CheckBox[]{
                cbMonday, cbTuesday, cbWednesday, cbThursday, cbFriday, cbSaturday, cbSunday
        };

        for (CheckBox cb : checkBoxes) {
            // Set new click listener for CheckBox
            cb.setOnClickListener(v -> {
                CheckBox clickedCb = (CheckBox) v;
                String day = clickedCb.getText().toString();
                String fullDayName = getFullDayName(day);

                // CheckBox state is automatically toggled on click. Update the selectedDays list based on the new state.
                if (clickedCb.isChecked()) {
                    if (!selectedDays.contains(fullDayName)) {
                        selectedDays.add(fullDayName);
                    }
                } else {
                    selectedDays.remove(fullDayName);
                }

                Log.d(TAG, "Selected days: " + selectedDays.toString());
            });
        }
    }

    private void clearSelectedDays() {
        selectedDays.clear();
        // REPLACED: Logic to clear CheckBoxes
        if (cbMonday != null) {
            cbMonday.setChecked(false);
            cbTuesday.setChecked(false);
            cbWednesday.setChecked(false);
            cbThursday.setChecked(false);
            cbFriday.setChecked(false);
            cbSaturday.setChecked(false);
            cbSunday.setChecked(false);
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
            llAssignUserSection.setVisibility(View.GONE);

            selectedDays.clear();
            selectedDays.addAll(task.getSelectedDays());

            // REPLACED: Logic to apply the checked state to the CheckBoxes
            CheckBox[] checkBoxes = new CheckBox[]{
                    cbMonday, cbTuesday, cbWednesday, cbThursday, cbFriday, cbSaturday, cbSunday
            };

            for (CheckBox cb : checkBoxes) {
                String fullDayName = getFullDayName(cb.getText().toString());
                cb.setChecked(selectedDays.contains(fullDayName));
            }
        }

        if (task.isRequireAiCount()) {
            ((RadioButton) findViewById(R.id.rb_ai_count_yes)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.rb_ai_count_no)).setChecked(true);
        }

        selectedAssignees.clear();
        selectedAssignees.addAll(task.getAssignedTo());

        updateAssigneeDisplay();
    }

    private void loadUsersForAssignment(Runnable onComplete) {
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allUsers.clear();
                    allUserDisplayNames.clear();

                    allUserDisplayNames.add("All Team Members");

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        User user = document.toObject(User.class);
                        allUsers.add(user);
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

        boolean allChecked = true;
        for (int i = 0; i < actualUserCount; i++) {
            if (selectedAssignees.contains(allUsers.get(i).getEmail())) {
                checkedItems[i + 1] = true;
            } else {
                allChecked = false;
            }
        }
        checkedItems[0] = allChecked;

        new AlertDialog.Builder(this)
                .setTitle("Select Team Members")
                .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                    if (which == 0) {
                        for (int i = 1; i < checkedItems.length; i++) {
                            checkedItems[i] = isChecked;
                            ((AlertDialog) dialog).getListView().setItemChecked(i, isChecked);
                        }
                    } else {
                        if (!isChecked) {
                            checkedItems[0] = false;
                            ((AlertDialog) dialog).getListView().setItemChecked(0, false);
                        } else {
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
                        for (User user : allUsers) {
                            selectedAssignees.add(user.getEmail());
                        }
                    } else {
                        for (int i = 1; i < allUserDisplayNames.size(); i++) {
                            if (checkedItems[i]) {
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
        String remarks = "";
        String priority = "medium";

        int selectedTaskTypeId = rgTaskType.getCheckedRadioButtonId();
        if (selectedTaskTypeId == -1) {
            Toast.makeText(this, "Please select a task duration type.", Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton selectedTaskTypeButton = findViewById(selectedTaskTypeId);
        String taskType = selectedTaskTypeButton.getText().toString();

        String startDate = "";
        String endDate = "";
        List<String> finalSelectedDays = new ArrayList<>();

        if (taskType.equalsIgnoreCase("Additional")) {
            startDate = etStartDate.getText().toString().trim();
            endDate = etEndDate.getText().toString().trim();
        } else {
            finalSelectedDays.addAll(selectedDays);
            if (finalSelectedDays.isEmpty()) {
                Toast.makeText(this, "Please select at least one active day for the permanent task.", Toast.LENGTH_SHORT).show();
                return;
            }
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
        taskUpdates.put("priority", priority);
        taskUpdates.put("fileUrls", new ArrayList<String>());
        taskUpdates.put("remarks", remarks);
        taskUpdates.put("assignedTo", assignedTo);
        taskUpdates.put("startDate", startDate);
        taskUpdates.put("endDate", endDate);
        taskUpdates.put("requireAiCount", requireAiCount);
        taskUpdates.put("taskType", taskType);
        taskUpdates.put("selectedDays", selectedDays);

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