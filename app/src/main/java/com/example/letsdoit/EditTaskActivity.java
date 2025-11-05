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
// Removed Firebase Storage Imports

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
    private Button btnSaveTask;
    private LinearLayout llAssignUserSection;

    private FirebaseFirestore db;
    // Removed all file attachment members

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
        btnSaveTask = findViewById(R.id.btn_save_task);
        etStartDate = findViewById(R.id.et_start_date);
        etEndDate = findViewById(R.id.et_end_date);
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
        etStartDate.setText(task.getStartDate());
        etEndDate.setText(task.getEndDate());

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
        String startDate = etStartDate.getText().toString().trim();
        String endDate = etEndDate.getText().toString().trim();

        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            return;
        }

        if (description.isEmpty()) {
            etDescription.setError("Description is required");
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

        btnSaveTask.setEnabled(false);
        btnSaveTask.setText("Updating...");

        saveTaskUpdateToFirestore(title, description, priority, remarks, selectedAssignees, startDate, endDate);
    }

    // Updated method signature (Removed fileUrls list)
    private void saveTaskUpdateToFirestore(String title, String description, String priority, String remarks, List<String> assignedTo, String startDate, String endDate) {
        Map<String, Object> taskUpdates = new HashMap<>();
        taskUpdates.put("title", title);
        taskUpdates.put("description", description);
        taskUpdates.put("priority", priority);
        taskUpdates.put("fileUrls", new ArrayList<String>()); // Explicitly clear fileUrls
        taskUpdates.put("remarks", remarks);
        taskUpdates.put("assignedTo", assignedTo);
        taskUpdates.put("startDate", startDate);
        taskUpdates.put("endDate", endDate);

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