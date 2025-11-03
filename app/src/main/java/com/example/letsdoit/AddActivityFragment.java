// src/main/java/com/example/letsdoit/AddActivityFragment.java
package com.example.letsdoit;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log; // New Import
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot; // New Import
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AddActivityFragment extends Fragment {

    private EditText etTitle, etDescription, etRemarks;
    private RadioGroup rgPriority, rgAssignedTo;
    private Button btnAttachFiles, btnSaveTask;
    private TextView tvAttachedFiles;
    private LinearLayout llAssignUserSection;

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private List<Uri> selectedFileUris = new ArrayList<>();
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String assignedToEmail;

    private List<User> allUsers = new ArrayList<>(); // List to hold fetched users
    private static final String TAG = "AddActivityFragment";

    // Factory method to create an instance with user data
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

        // Retrieve user data
        if (getArguments() != null) {
            loggedInUserEmail = getArguments().getString(LoginActivity.EXTRA_USER_EMAIL);
            loggedInUserRole = getArguments().getString(LoginActivity.EXTRA_USER_ROLE);
        }

        // Default assignment is to self (the logged-in user)
        assignedToEmail = loggedInUserEmail;

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // [ ... permissionLauncher and filePickerLauncher remain unchanged ... ]

        // Permission launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (!allGranted) {
                        Toast.makeText(getContext(), "Storage permissions are required to attach files", Toast.LENGTH_LONG).show();
                    }
                }
        );

        // File picker launcher
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            for (int i = 0; i < count; i++) {
                                Uri fileUri = data.getClipData().getItemAt(i).getUri();
                                selectedFileUris.add(fileUri);
                            }
                        } else if (data.getData() != null) {
                            selectedFileUris.add(data.getData());
                        }
                        updateAttachedFilesText();
                    }
                }
        );

        requestStoragePermissions();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_activity, container, false);

        etTitle = view.findViewById(R.id.et_title);
        etDescription = view.findViewById(R.id.et_description);
        etRemarks = view.findViewById(R.id.et_remarks);
        rgPriority = view.findViewById(R.id.rg_priority);
        btnAttachFiles = view.findViewById(R.id.btn_attach_files);
        btnSaveTask = view.findViewById(R.id.btn_save_task);
        tvAttachedFiles = view.findViewById(R.id.tv_attached_files);

        llAssignUserSection = view.findViewById(R.id.ll_assign_user_section);
        rgAssignedTo = view.findViewById(R.id.rg_assigned_to);

        // Setup user assignment section for ADMIN only
        if ("admin".equals(loggedInUserRole)) {
            llAssignUserSection.setVisibility(View.VISIBLE);
            loadUsersForAssignment(); // Load users from Firestore
        } else {
            llAssignUserSection.setVisibility(View.GONE);
        }

        btnAttachFiles.setOnClickListener(v -> openFilePicker());
        btnSaveTask.setOnClickListener(v -> saveTask());

        return view;
    }

    private void loadUsersForAssignment() {
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allUsers.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        User user = document.toObject(User.class);
                        allUsers.add(user);
                    }
                    setupAssignmentRadioGroup();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load users for assignment: " + e.getMessage());
                    Toast.makeText(getContext(), "Could not load users for assignment.", Toast.LENGTH_SHORT).show();
                });
    }

    // UPDATED METHOD: Dynamically populate and handle the assignment RadioGroup
    private void setupAssignmentRadioGroup() {
        rgAssignedTo.removeAllViews(); // Clear previous views

        if (allUsers.isEmpty()) {
            TextView tv = new TextView(getContext());
            tv.setText("No users found.");
            rgAssignedTo.addView(tv);
            return;
        }

        // Loop through all fetched users
        for (int i = 0; i < allUsers.size(); i++) {
            User user = allUsers.get(i);
            String userEmail = user.getEmail();
            RadioButton rb = new RadioButton(getContext());

            String buttonText = user.getDisplayName() + " (" + userEmail + ")";
            if (user.getRole().equals("admin")) {
                buttonText += " - ADMIN";
            }

            rb.setText(buttonText);
            rb.setTag(userEmail);
            rb.setId(View.generateViewId()); // Unique ID

            // Set the Admin user as the default selected user
            if (userEmail.equals(loggedInUserEmail)) {
                rb.setChecked(true);
                assignedToEmail = userEmail;
            }

            rgAssignedTo.addView(rb);
        }

        // Listener to update the assignedToEmail variable
        rgAssignedTo.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = group.findViewById(checkedId);
            if (selected != null) {
                assignedToEmail = (String) selected.getTag();
            }
        });
    }

    // [ ... requestStoragePermissions(), openFilePicker(), updateAttachedFilesText() remain unchanged ... ]

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO,
                    android.Manifest.permission.READ_MEDIA_AUDIO
            });
        } else {
            permissionLauncher.launch(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        filePickerLauncher.launch(Intent.createChooser(intent, "Select Files"));
    }

    private void updateAttachedFilesText() {
        if (selectedFileUris.isEmpty()) {
            tvAttachedFiles.setText("No files attached");
        } else {
            tvAttachedFiles.setText(selectedFileUris.size() + " file(s) attached");
        }
    }


    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String remarks = etRemarks.getText().toString().trim();

        // If not admin, ensure assignedTo is the logged-in user's email
        if (!"admin".equals(loggedInUserRole)) {
            assignedToEmail = loggedInUserEmail;
        }

        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            return;
        }

        if (description.isEmpty()) {
            etDescription.setError("Description is required");
            return;
        }

        int selectedPriorityId = rgPriority.getCheckedRadioButtonId();
        if (selectedPriorityId == -1) {
            Toast.makeText(getContext(), "Please select a priority", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedPriorityButton = getView().findViewById(selectedPriorityId);
        String priority = selectedPriorityButton.getText().toString().toLowerCase();

        btnSaveTask.setEnabled(false);
        btnSaveTask.setText("Saving...");

        if (!selectedFileUris.isEmpty()) {
            uploadFilesAndSaveTask(title, description, priority, remarks, assignedToEmail);
        } else {
            saveTaskToFirestore(title, description, priority, new ArrayList<>(), remarks, assignedToEmail);
        }
    }

    private void uploadFilesAndSaveTask(String title, String description, String priority, String remarks, String assignedTo) {
        List<String> fileUrls = new ArrayList<>();
        int[] uploadCount = {0};

        for (Uri fileUri : selectedFileUris) {
            String fileName = UUID.randomUUID().toString();
            StorageReference fileRef = storage.getReference().child("task_files/" + fileName);

            fileRef.putFile(fileUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        fileRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                            fileUrls.add(downloadUri.toString());
                            uploadCount[0]++;

                            if (uploadCount[0] == selectedFileUris.size()) {
                                saveTaskToFirestore(title, description, priority, fileUrls, remarks, assignedTo);
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "File upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        btnSaveTask.setEnabled(true);
                        btnSaveTask.setText("Save Task");
                    });
        }
    }

    private void saveTaskToFirestore(String title, String description, String priority, List<String> fileUrls, String remarks, String assignedTo) {
        Task task = new Task(title, description, priority, fileUrls, remarks, assignedTo);

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
        rgPriority.clearCheck();
        selectedFileUris.clear();
        updateAttachedFilesText();

        // Reset assignment
        if ("admin".equals(loggedInUserRole)) {
            // Find and check the Admin's radio button as default
            for(int i = 0; i < rgAssignedTo.getChildCount(); i++){
                View child = rgAssignedTo.getChildAt(i);
                if(child instanceof RadioButton){
                    RadioButton rb = (RadioButton) child;
                    if(rb.getTag() != null && rb.getTag().equals(loggedInUserEmail)){
                        rb.setChecked(true);
                        assignedToEmail = loggedInUserEmail;
                        break;
                    }
                }
            }
        }
    }
}