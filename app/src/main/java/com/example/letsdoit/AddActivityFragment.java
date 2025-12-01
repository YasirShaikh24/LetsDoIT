// src/main/java/com/example/letsdoit/AddActivityFragment.java
package com.example.letsdoit;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AddActivityFragment extends Fragment {

    private TextInputEditText etTitle, etDescription, etRemarks, etStartDate, etEndDate;
    private TextInputEditText etAssigneeDisplay;
    private RadioGroup rgPriority;
    // MODIFIED: Replaced CheckBox with RadioGroup
    private RadioGroup rgRequireAiCount;
    // NEW: RadioGroup for Task Type
    private RadioGroup rgTaskType;
    private Button btnSaveTask;
    private LinearLayout llAssignUserSection;
    // NEW: LinearLayout for Date Range
    private LinearLayout llDateRangeSection;

    private FirebaseFirestore db;

    private String loggedInUserEmail;
    private String loggedInUserRole;

    private List<String> selectedAssignees = new ArrayList<>();
    private List<String> allUserDisplayNames = new ArrayList<>();
    private List<User> allUsers = new ArrayList<>();

    private static final String TAG = "AddActivityFragment";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    public static AddActivityFragment newInstance(String email, String role) {
        AddActivityFragment fragment = new AddActivityFragment();
        Bundle args = new Bundle();
        args.putString(LoginActivity.EXTRA_USER_EMAIL, email);
        args.putString(LoginActivity.EXTRA_USER_ROLE, role);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            loggedInUserEmail = getArguments().getString(LoginActivity.EXTRA_USER_EMAIL);
            loggedInUserRole = getArguments().getString(LoginActivity.EXTRA_USER_ROLE);
        }

        if (!"admin".equals(loggedInUserRole)) {
            selectedAssignees.add(loggedInUserEmail);
        }

        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_activity, container, false);

        etTitle = view.findViewById(R.id.et_title);
        etDescription = view.findViewById(R.id.et_description);
        etRemarks = view.findViewById(R.id.et_remarks);
        rgPriority = view.findViewById(R.id.rg_priority);
        // MODIFIED: Using RadioGroup
        rgRequireAiCount = view.findViewById(R.id.rg_require_ai_count);
        btnSaveTask = view.findViewById(R.id.btn_save_task);

        // NEW: Task Type and Date Range Section
        rgTaskType = view.findViewById(R.id.rg_task_type);
        llDateRangeSection = view.findViewById(R.id.ll_date_range_section);
        etStartDate = view.findViewById(R.id.et_start_date);
        etEndDate = view.findViewById(R.id.et_end_date);

        // Task Type Listener to toggle date range visibility
        rgTaskType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_additional) {
                llDateRangeSection.setVisibility(View.VISIBLE);
            } else {
                llDateRangeSection.setVisibility(View.GONE);
                // Clear dates when switching to permanent
                etStartDate.setText("");
                etEndDate.setText("");
            }
        });

        // Initialize date visibility based on default checked item (Permanent)
        llDateRangeSection.setVisibility(View.GONE);

        etStartDate.setOnClickListener(v -> showDatePicker(etStartDate));
        etEndDate.setOnClickListener(v -> showDatePicker(etEndDate));

        llAssignUserSection = view.findViewById(R.id.ll_assign_user_section);
        etAssigneeDisplay = view.findViewById(R.id.et_assigned_to_display);

        if ("admin".equals(loggedInUserRole)) {
            llAssignUserSection.setVisibility(View.VISIBLE);
            loadUsersForAssignment();
            etAssigneeDisplay.setOnClickListener(v -> showMultiSelectUserDialog());
        } else {
            llAssignUserSection.setVisibility(View.GONE);
        }

        btnSaveTask.setOnClickListener(v -> saveTask());

        return view;
    }

    private void showDatePicker(final TextInputEditText dateEditText) {
        final Calendar calendar = Calendar.getInstance();

        DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, monthOfYear);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                dateEditText.setText(dateFormat.format(calendar.getTime()));
            }
        };

        new DatePickerDialog(getContext(), dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadUsersForAssignment() {
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
                    updateAssigneeDisplay();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load users for assignment: " + e.getMessage());
                    Toast.makeText(getContext(), "Could not load users for assignment.", Toast.LENGTH_SHORT).show();
                });
    }

    private void showMultiSelectUserDialog() {
        if (allUserDisplayNames.isEmpty()) {
            Toast.makeText(getContext(), "No users found to assign.", Toast.LENGTH_SHORT).show();
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

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select Team Members")
                .setMultiChoiceItems(items, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checkedItems[which] = isChecked;
                    }
                })
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        selectedAssignees.clear();
                        for (int i = 0; i < allUsers.size(); i++) {
                            if (checkedItems[i]) {
                                selectedAssignees.add(allUsers.get(i).getEmail());
                            }
                        }
                        updateAssigneeDisplay();
                    }
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

    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String remarks = etRemarks.getText().toString().trim();

        // NEW: Get Task Type
        int selectedTaskTypeId = rgTaskType.getCheckedRadioButtonId();
        if (selectedTaskTypeId == -1) {
            Toast.makeText(getContext(), "Please select a task duration type.", Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton selectedTaskTypeButton = getView().findViewById(selectedTaskTypeId);
        String taskType = selectedTaskTypeButton.getText().toString(); // "Permanent" or "Additional"

        String startDate = "";
        String endDate = "";

        // Only get dates if Task Type is "Additional" (dates are cleared on switch to Permanent)
        if (taskType.equalsIgnoreCase("Additional")) {
            startDate = etStartDate.getText().toString().trim();
            endDate = etEndDate.getText().toString().trim();
        }


        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            return;
        }

        // DESCRIPTION IS NOW OPTIONAL - Validation removed.

        if ("admin".equals(loggedInUserRole) && selectedAssignees.isEmpty()) {
            Toast.makeText(getContext(), "Please assign the task to at least one user.", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedPriorityId = rgPriority.getCheckedRadioButtonId();
        if (selectedPriorityId == -1) {
            Toast.makeText(getContext(), "Please select a priority", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton priorityButton = getView().findViewById(selectedPriorityId);
        String priority = priorityButton.getText().toString().toLowerCase();

        // MODIFIED: Get AI Count radio button state
        int selectedAiCountId = rgRequireAiCount.getCheckedRadioButtonId();
        // requireAiCount is true only if 'Yes' radio button is checked (R.id.rb_ai_count_yes)
        boolean requireAiCount = (selectedAiCountId == R.id.rb_ai_count_yes);

        btnSaveTask.setEnabled(false);
        btnSaveTask.setText("Saving...");

        // UPDATED: Added taskType argument
        saveTaskToFirestore(title, description, priority, remarks, selectedAssignees, startDate, endDate, requireAiCount, taskType);
    }

    private void saveTaskToFirestore(String title, String description, String priority, String remarks, List<String> assignedTo, String startDate, String endDate, boolean requireAiCount, String taskType) {
        // UPDATED: Added taskType argument
        Task task = new Task(title, description, priority, remarks, assignedTo, startDate, endDate, requireAiCount, taskType);

        db.collection("tasks")
                .add(task)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "Task saved successfully!", Toast.LENGTH_SHORT).show();
                    clearForm();
                    btnSaveTask.setEnabled(true);
                    btnSaveTask.setText("Save Task");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error saving task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSaveTask.setEnabled(true);
                    btnSaveTask.setText("Save Task");
                });
    }

    private void clearForm() {
        etTitle.setText("");
        etDescription.setText("");
        etRemarks.setText("");
        etStartDate.setText("");
        etEndDate.setText("");
        rgPriority.clearCheck();

        // NEW: Reset Task Type to Permanent and hide dates
        rgTaskType.check(R.id.rb_permanent);
        llDateRangeSection.setVisibility(View.GONE);

        // MODIFIED: Reset AI Count to No
        rgRequireAiCount.check(R.id.rb_ai_count_no);

        if ("admin".equals(loggedInUserRole)) {
            selectedAssignees.clear();
            updateAssigneeDisplay();
        }
    }
}