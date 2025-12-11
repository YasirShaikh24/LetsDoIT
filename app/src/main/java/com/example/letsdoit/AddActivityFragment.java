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
import android.widget.CheckBox; // <-- ADDED IMPORT
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AddActivityFragment extends Fragment {

    private TextInputEditText etTitle, etDescription, etStartDate, etEndDate;
    private TextInputEditText etAssigneeDisplay;
    private RadioGroup rgRequireAiCount;
    private RadioGroup rgTaskType;
    // REMOVED: private RadioGroup rgWeekDays;

    // ADDED: CheckBox declarations for the days
    private CheckBox cbMonday, cbTuesday, cbWednesday, cbThursday, cbFriday, cbSaturday, cbSunday;

    private Button btnSaveTask;
    private LinearLayout llAssignUserSection;
    private LinearLayout llDateRangeSection;
    private LinearLayout llWeekDaysSection;

    private FirebaseFirestore db;

    private String loggedInUserEmail;
    private String loggedInUserRole;

    private List<String> selectedAssignees = new ArrayList<>();
    private List<String> allUserDisplayNames = new ArrayList<>();
    private List<User> allUsers = new ArrayList<>();

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

        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_activity, container, false);

        etTitle = view.findViewById(R.id.et_title);
        etDescription = view.findViewById(R.id.et_description);
        rgRequireAiCount = view.findViewById(R.id.rg_require_ai_count);
        btnSaveTask = view.findViewById(R.id.btn_save_task);

        rgTaskType = view.findViewById(R.id.rg_task_type);
        llDateRangeSection = view.findViewById(R.id.ll_date_range_section);
        etStartDate = view.findViewById(R.id.et_start_date);
        etEndDate = view.findViewById(R.id.et_end_date);

        llWeekDaysSection = view.findViewById(R.id.ll_week_days_section);
        // REMOVED: rgWeekDays = view.findViewById(R.id.rg_week_days);

        // ADDED: Initialize the CheckBoxes
        cbMonday = view.findViewById(R.id.cb_monday);
        cbTuesday = view.findViewById(R.id.cb_tuesday);
        cbWednesday = view.findViewById(R.id.cb_wednesday);
        cbThursday = view.findViewById(R.id.cb_thursday);
        cbFriday = view.findViewById(R.id.cb_friday);
        cbSaturday = view.findViewById(R.id.cb_saturday);
        cbSunday = view.findViewById(R.id.cb_sunday);

        setupTaskTypeListener();
        setupWeekDaysListener();

        etStartDate.setOnClickListener(v -> showDatePicker(etStartDate));
        etEndDate.setOnClickListener(v -> showDatePicker(etEndDate));

        llAssignUserSection = view.findViewById(R.id.ll_assign_user_section);
        etAssigneeDisplay = view.findViewById(R.id.et_assigned_to_display);

        if ("admin".equals(loggedInUserRole)) {
            loadUsersForAssignment();
            etAssigneeDisplay.setOnClickListener(v -> showMultiSelectUserDialog());
        }

        // Initialize state to Permanent
        rgTaskType.check(R.id.rb_permanent);
        llDateRangeSection.setVisibility(View.GONE);
        llAssignUserSection.setVisibility(View.GONE);
        llWeekDaysSection.setVisibility(View.VISIBLE);

        btnSaveTask.setOnClickListener(v -> saveTask());

        return view;
    }

    private void setupTaskTypeListener() {
        rgTaskType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isPermanent = checkedId == R.id.rb_permanent;

            llDateRangeSection.setVisibility(isPermanent ? View.GONE : View.VISIBLE);
            llWeekDaysSection.setVisibility(isPermanent ? View.VISIBLE : View.GONE);

            if (isPermanent) {
                etStartDate.setText("");
                etEndDate.setText("");
            } else {
                clearSelectedDays();
            }

            if ("admin".equals(loggedInUserRole)) {
                if (isPermanent) {
                    llAssignUserSection.setVisibility(View.GONE);
                    autoAssignAllUsers();
                } else {
                    llAssignUserSection.setVisibility(View.VISIBLE);
                    selectedAssignees.clear();
                    updateAssigneeDisplay();
                }
            } else {
                llAssignUserSection.setVisibility(View.GONE);
                selectedAssignees.clear();
                selectedAssignees.add(loggedInUserEmail);
            }
        });
    }

    private void setupWeekDaysListener() {
        // Create an array of all CheckBoxes for easy iteration
        CheckBox[] checkBoxes = new CheckBox[]{
                cbMonday, cbTuesday, cbWednesday, cbThursday, cbFriday, cbSaturday, cbSunday
        };

        for (CheckBox cb : checkBoxes) {
            // Set click listener for multi-select behavior
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

        // ADDED: Clear all CheckBoxes
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

                    allUserDisplayNames.add("All Team Members");

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        User user = document.toObject(User.class);
                        allUsers.add(user);
                        String displayName = user.getDisplayName();
                        allUserDisplayNames.add(displayName);
                    }
                    autoAssignAllUsers();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load users for assignment: " + e.getMessage());
                    Toast.makeText(getContext(), "Could not load users for assignment.", Toast.LENGTH_SHORT).show();
                    autoAssignAllUsers();
                });
    }

    private void autoAssignAllUsers() {
        selectedAssignees.clear();
        for (User user : allUsers) {
            selectedAssignees.add(user.getEmail());
        }
        updateAssigneeDisplay();
    }

    private void showMultiSelectUserDialog() {
        if (allUserDisplayNames.size() <= 1) {
            Toast.makeText(getContext(), "No users found to assign.", Toast.LENGTH_SHORT).show();
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

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select Team Members")
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

    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String remarks = "";
        String priority = "medium";

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

        if ("admin".equals(loggedInUserRole) && selectedAssignees.isEmpty()) {
            Toast.makeText(getContext(), "Please assign the task to at least one user.", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedAiCountId = rgRequireAiCount.getCheckedRadioButtonId();
        boolean requireAiCount = (selectedAiCountId == R.id.rb_ai_count_yes);

        btnSaveTask.setEnabled(false);
        btnSaveTask.setText("Saving...");

        saveTaskToFirestore(title, description, priority, remarks, selectedAssignees, startDate, endDate, requireAiCount, taskType, finalSelectedDays);
    }

    private void saveTaskToFirestore(String title, String description, String priority, String remarks, List<String> assignedTo, String startDate, String endDate, boolean requireAiCount, String taskType, List<String> selectedDays) {
        Task task = new Task(title, description, priority, remarks, assignedTo, startDate, endDate, requireAiCount, taskType, selectedDays);

        db.collection("tasks")
                .add(task)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "Task saved successfully!", Toast.LENGTH_SHORT).show();

                    if (getActivity() instanceof MainActivity) {
                        MainActivity mainActivity = (MainActivity) getActivity();
                        BottomNavigationView bottomNav = mainActivity.findViewById(R.id.bottom_navigation);
                        if (bottomNav != null) {
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
        etStartDate.setText("");
        etEndDate.setText("");

        rgTaskType.check(R.id.rb_permanent);
        llDateRangeSection.setVisibility(View.GONE);
        llWeekDaysSection.setVisibility(View.VISIBLE);

        rgRequireAiCount.check(R.id.rb_ai_count_no);

        clearSelectedDays();

        if ("admin".equals(loggedInUserRole)) {
            autoAssignAllUsers();
            llAssignUserSection.setVisibility(View.GONE);
        }
    }
}