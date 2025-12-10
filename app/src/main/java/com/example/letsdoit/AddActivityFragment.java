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

import com.google.android.material.bottomnavigation.BottomNavigationView; // Added import
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AddActivityFragment extends Fragment {

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

    private String loggedInUserEmail;
    private String loggedInUserRole;

    private List<String> selectedAssignees = new ArrayList<>();
    // This list holds the display names for the dialog (including "All Team Members")
    private List<String> allUserDisplayNames = new ArrayList<>();
    // This list holds the actual User objects (excluding "All Team Members")
    private List<User> allUsers = new ArrayList<>();

    // NEW: List to track selected days for permanent tasks
    private List<String> selectedDays = new ArrayList<>();

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

        // Removed logic for user auto-assignment on onCreate, now handled dynamically in rgTaskType listener

        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_activity, container, false);

        etTitle = view.findViewById(R.id.et_title);
        etDescription = view.findViewById(R.id.et_description);
        // etRemarks removed
        // rgPriority removed
        rgRequireAiCount = view.findViewById(R.id.rg_require_ai_count);
        btnSaveTask = view.findViewById(R.id.btn_save_task);

        rgTaskType = view.findViewById(R.id.rg_task_type);
        llDateRangeSection = view.findViewById(R.id.ll_date_range_section);
        etStartDate = view.findViewById(R.id.et_start_date);
        etEndDate = view.findViewById(R.id.et_end_date);

        llWeekDaysSection = view.findViewById(R.id.ll_week_days_section); // NEW
        rgWeekDays = view.findViewById(R.id.rg_week_days); // NEW

        setupTaskTypeListener(); // NEW method for cleaner logic
        setupWeekDaysListener(); // NEW method for multi-select logic

        etStartDate.setOnClickListener(v -> showDatePicker(etStartDate));
        etEndDate.setOnClickListener(v -> showDatePicker(etEndDate));

        llAssignUserSection = view.findViewById(R.id.ll_assign_user_section);
        etAssigneeDisplay = view.findViewById(R.id.et_assigned_to_display);

        // Assignment setup is now controlled by the task type listener below
        if ("admin".equals(loggedInUserRole)) {
            loadUsersForAssignment();
            etAssigneeDisplay.setOnClickListener(v -> showMultiSelectUserDialog());
        }

        // Initialize state to Permanent
        rgTaskType.check(R.id.rb_permanent);
        llDateRangeSection.setVisibility(View.GONE);
        llAssignUserSection.setVisibility(View.GONE);
        llWeekDaysSection.setVisibility(View.VISIBLE);
        // Initial autoAssignAllUsers() will be handled by loadUsersForAssignment -> autoAssignAllUsers

        btnSaveTask.setOnClickListener(v -> saveTask());

        return view;
    }

    private void setupTaskTypeListener() {
        rgTaskType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isPermanent = checkedId == R.id.rb_permanent;

            // Date Range / Week Days visibility
            llDateRangeSection.setVisibility(isPermanent ? View.GONE : View.VISIBLE);
            llWeekDaysSection.setVisibility(isPermanent ? View.VISIBLE : View.GONE);
            if (isPermanent) {
                etStartDate.setText("");
                etEndDate.setText("");
            } else {
                // Clear selected days if switching to Additional
                clearSelectedDays();
            }

            // Assignee visibility/selection logic
            if ("admin".equals(loggedInUserRole)) {
                if (isPermanent) {
                    // Permanent: Auto-assign to ALL and hide selection
                    llAssignUserSection.setVisibility(View.GONE);
                    // Force selectedAssignees to contain ALL user emails
                    autoAssignAllUsers();
                } else {
                    // Additional: Show selection and CLEAR/RESET assignees (UPDATED LOGIC)
                    llAssignUserSection.setVisibility(View.VISIBLE);
                    selectedAssignees.clear(); // Clear assigned list
                    updateAssigneeDisplay(); // Show "No users selected"
                }
            } else {
                // Regular user: Always auto-assigned to themselves, so selection is always hidden.
                llAssignUserSection.setVisibility(View.GONE);
                selectedAssignees.clear();
                selectedAssignees.add(loggedInUserEmail);
            }
        });
    }

    private void setupWeekDaysListener() {
        // This implements multi-select by manually managing the 'selectedDays' list and
        // toggling the checked state/RadioGroup logic manually.
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
            // Uncheck all buttons visually
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
                    // Initial state is Permanent, so auto-assign all users.
                    autoAssignAllUsers();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load users for assignment: " + e.getMessage());
                    Toast.makeText(getContext(), "Could not load users for assignment.", Toast.LENGTH_SHORT).show();
                    // Fallback to auto-assign all if list fails to load (might be empty)
                    autoAssignAllUsers();
                });
    }

    // NEW: Helper method to force selection of all users
    private void autoAssignAllUsers() {
        selectedAssignees.clear();
        for (User user : allUsers) {
            selectedAssignees.add(user.getEmail());
        }
        updateAssigneeDisplay(); // Update display to show "All Team Members"
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

    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String remarks = ""; // REMOVED: Default to empty string
        String priority = "medium"; // REMOVED: Default to a fixed value

        int selectedTaskTypeId = rgTaskType.getCheckedRadioButtonId();
        if (selectedTaskTypeId == -1) {
            Toast.makeText(getContext(), "Please select a task duration type.", Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton selectedTaskTypeButton = getView().findViewById(selectedTaskTypeId);
        String taskType = selectedTaskTypeButton.getText().toString();

        String startDate = "";
        String endDate = "";
        List<String> finalSelectedDays = new ArrayList<>();

        if (taskType.equalsIgnoreCase("Additional")) {
            startDate = etStartDate.getText().toString().trim();
            endDate = etEndDate.getText().toString().trim();
        } else {
            // Permanent task validation
            finalSelectedDays.addAll(selectedDays);
            if (finalSelectedDays.isEmpty()) {
                Toast.makeText(getContext(), "Please select at least one active day for the permanent task.", Toast.LENGTH_SHORT).show();
                return;
            }
        }


        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            return;
        }

        // Admin assignment validation (for additional tasks, or if all users were somehow cleared for permanent)
        if ("admin".equals(loggedInUserRole) && selectedAssignees.isEmpty()) {
            Toast.makeText(getContext(), "Please assign the task to at least one user.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Priority validation removed
        // Remarks validation removed

        int selectedAiCountId = rgRequireAiCount.getCheckedRadioButtonId();
        boolean requireAiCount = (selectedAiCountId == R.id.rb_ai_count_yes);

        btnSaveTask.setEnabled(false);
        btnSaveTask.setText("Saving...");

        saveTaskToFirestore(title, description, priority, remarks, selectedAssignees, startDate, endDate, requireAiCount, taskType, finalSelectedDays);
    }

    private void saveTaskToFirestore(String title, String description, String priority, String remarks, List<String> assignedTo, String startDate, String endDate, boolean requireAiCount, String taskType, List<String> selectedDays) {
        // Use the new constructor
        Task task = new Task(title, description, priority, remarks, assignedTo, startDate, endDate, requireAiCount, taskType, selectedDays);

        db.collection("tasks")
                .add(task)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "Task saved successfully!", Toast.LENGTH_SHORT).show();

                    // NEW LOGIC: Redirect to View Activity
                    if (getActivity() instanceof MainActivity) {
                        MainActivity mainActivity = (MainActivity) getActivity();
                        BottomNavigationView bottomNav = mainActivity.findViewById(R.id.bottom_navigation);
                        if (bottomNav != null) {
                            // Switch the selected tab to "View Activity"
                            bottomNav.setSelectedItemId(R.id.navigation_view_activity);
                        }
                    }

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
        // etRemarks removed
        etStartDate.setText("");
        etEndDate.setText("");
        // rgPriority removed

        // Reset all components to Permanent state
        rgTaskType.check(R.id.rb_permanent); // This triggers the listener
        llDateRangeSection.setVisibility(View.GONE);
        llWeekDaysSection.setVisibility(View.VISIBLE);

        rgRequireAiCount.check(R.id.rb_ai_count_no);

        clearSelectedDays(); // Clear selected days list and visuals

        if ("admin".equals(loggedInUserRole)) {
            // Restore default state for admin: Permanent=All Users
            autoAssignAllUsers();
            llAssignUserSection.setVisibility(View.GONE);
        }
    }
}