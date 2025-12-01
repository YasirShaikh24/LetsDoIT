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
    private RadioGroup rgRequireAiCount;
    private RadioGroup rgTaskType;
    private Button btnSaveTask;
    private LinearLayout llAssignUserSection;
    private LinearLayout llDateRangeSection;

    private FirebaseFirestore db;

    private String loggedInUserEmail;
    private String loggedInUserRole;

    private List<String> selectedAssignees = new ArrayList<>();
    // This list holds the display names for the dialog (including "All Team Members")
    private List<String> allUserDisplayNames = new ArrayList<>();
    // This list holds the actual User objects (excluding "All Team Members")
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
        rgRequireAiCount = view.findViewById(R.id.rg_require_ai_count);
        btnSaveTask = view.findViewById(R.id.btn_save_task);

        rgTaskType = view.findViewById(R.id.rg_task_type);
        llDateRangeSection = view.findViewById(R.id.ll_date_range_section);
        etStartDate = view.findViewById(R.id.et_start_date);
        etEndDate = view.findViewById(R.id.et_end_date);

        rgTaskType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_additional) {
                llDateRangeSection.setVisibility(View.VISIBLE);
            } else {
                llDateRangeSection.setVisibility(View.GONE);
                etStartDate.setText("");
                etEndDate.setText("");
            }
        });

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

                    // 1. Add "All Team Members" option at index 0 for display
                    allUserDisplayNames.add("All Team Members");

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        User user = document.toObject(User.class);
                        allUsers.add(user);
                        // 2. Add only the display name (as requested)
                        String displayName = user.getDisplayName();
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
        if (allUserDisplayNames.size() <= 1) { // Only "All Team Members" or empty list
            Toast.makeText(getContext(), "No users found to assign.", Toast.LENGTH_SHORT).show();
            return;
        }

        final CharSequence[] items = allUserDisplayNames.toArray(new CharSequence[0]);
        final boolean[] checkedItems = new boolean[allUserDisplayNames.size()];
        final int actualUserCount = allUsers.size();

        // Initialization: Check individual users first
        boolean allChecked = true;
        for (int i = 0; i < actualUserCount; i++) {
            // Index i+1 in checkedItems corresponds to user at index i in allUsers
            if (selectedAssignees.contains(allUsers.get(i).getEmail())) {
                checkedItems[i + 1] = true;
            } else {
                allChecked = false;
            }
        }
        // Set initial state of "All" checkbox (index 0)
        checkedItems[0] = allChecked;


        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select Team Members")
                // Use a custom click listener to handle the "All" checkbox logic
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

    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String remarks = etRemarks.getText().toString().trim();

        int selectedTaskTypeId = rgTaskType.getCheckedRadioButtonId();
        if (selectedTaskTypeId == -1) {
            Toast.makeText(getContext(), "Please select a task duration type.", Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton selectedTaskTypeButton = getView().findViewById(selectedTaskTypeId);
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

        int selectedAiCountId = rgRequireAiCount.getCheckedRadioButtonId();
        boolean requireAiCount = (selectedAiCountId == R.id.rb_ai_count_yes);

        btnSaveTask.setEnabled(false);
        btnSaveTask.setText("Saving...");

        saveTaskToFirestore(title, description, priority, remarks, selectedAssignees, startDate, endDate, requireAiCount, taskType);
    }

    private void saveTaskToFirestore(String title, String description, String priority, String remarks, List<String> assignedTo, String startDate, String endDate, boolean requireAiCount, String taskType) {
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

        rgTaskType.check(R.id.rb_permanent);
        llDateRangeSection.setVisibility(View.GONE);

        rgRequireAiCount.check(R.id.rb_ai_count_no);

        if ("admin".equals(loggedInUserRole)) {
            selectedAssignees.clear();
            updateAssigneeDisplay();
        }
    }
}