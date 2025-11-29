// src/main/java/com/example/letsdoit/ViewMembersActivity.java
package com.example.letsdoit;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ViewMembersActivity extends AppCompatActivity implements MemberAdapter.MemberDeleteListener {

    private RecyclerView recyclerView;
    private MemberAdapter memberAdapter;
    private List<User> memberList;
    private Set<User> selectedMembers; // Set to track selected members
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private Button btnDeleteMode; // Changed to Button
    private Button btnConfirmDelete; // NEW
    private TextView tvSelectHeader; // NEW

    private boolean isDeleteMode = false; // NEW state flag

    private static final String TAG = "ViewMembersActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_members);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Team Members");
        }

        db = FirebaseFirestore.getInstance();
        selectedMembers = new HashSet<>(); // Initialize Set

        recyclerView = findViewById(R.id.recycler_view_members);
        progressBar = findViewById(R.id.progress_bar);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        btnDeleteMode = findViewById(R.id.btn_delete_mode); // NEW
        btnConfirmDelete = findViewById(R.id.btn_confirm_delete); // NEW
        tvSelectHeader = findViewById(R.id.tv_select_header); // NEW

        memberList = new ArrayList<>();
        // Updated adapter constructor
        memberAdapter = new MemberAdapter(memberList, selectedMembers, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        // Add a divider for better table visibility
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
                layoutManager.getOrientation());
        recyclerView.addItemDecoration(dividerItemDecoration);

        recyclerView.setAdapter(memberAdapter);

        // NEW: Set click listeners
        btnDeleteMode.setOnClickListener(v -> toggleDeleteMode());
        btnConfirmDelete.setOnClickListener(v -> showDeleteConfirmationDialog());
        updateDeleteModeUI(); // Initialize UI state

        loadMembers();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // NEW: Toggle delete mode functionality
    private void toggleDeleteMode() {
        isDeleteMode = !isDeleteMode;
        selectedMembers.clear(); // Clear selections on mode toggle
        updateDeleteModeUI();
        memberAdapter.toggleDeleteMode(isDeleteMode); // Tell adapter to update views
    }

    // NEW: Update UI elements based on delete mode
    private void updateDeleteModeUI() {
        if (isDeleteMode) {
            btnDeleteMode.setText("CANCEL");
            // Use existing drawables/colors
            btnDeleteMode.setBackground(ContextCompat.getDrawable(this, R.drawable.button_secondary_background));
            btnDeleteMode.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

            btnConfirmDelete.setVisibility(View.VISIBLE);
            tvSelectHeader.setVisibility(View.VISIBLE);
            updateConfirmButtonState();
        } else {
            btnDeleteMode.setText("DELETE");
            btnDeleteMode.setBackground(ContextCompat.getDrawable(this, R.drawable.button_background)); // Red button
            btnDeleteMode.setTextColor(ContextCompat.getColor(this, R.color.white));

            btnConfirmDelete.setVisibility(View.GONE);
            tvSelectHeader.setVisibility(View.GONE);
        }
    }

    // NEW: Update confirmation button text and state
    private void updateConfirmButtonState() {
        int count = selectedMembers.size();
        if (count > 0) {
            btnConfirmDelete.setText("DELETE " + count + " SELECTED MEMBER(S)");
            btnConfirmDelete.setEnabled(true);
        } else {
            btnConfirmDelete.setText("SELECT MEMBERS TO DELETE");
            btnConfirmDelete.setEnabled(false);
        }
    }

    // NEW: Implementation of the selection listener
    @Override
    public void onMemberSelectionChanged(User user, boolean isSelected) {
        if (isSelected) {
            selectedMembers.add(user);
        } else {
            selectedMembers.remove(user);
        }
        updateConfirmButtonState();
    }

    private void loadMembers() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        // Load all documents from the "users" collection (regular members)
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    memberList.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            User user = document.toObject(User.class);
                            // Store the Firestore Document ID
                            user.setDocumentId(document.getId());
                            memberList.add(user);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing user: " + document.getId(), e);
                        }
                    }

                    loadAdmins();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading members", e);
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Error loading members: " + e.getMessage());
                    tvEmptyState.setVisibility(View.VISIBLE);
                });
    }

    // Separately load admins
    private void loadAdmins() {
        db.collection("admins")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            User user = document.toObject(User.class);
                            if (user != null && !memberList.stream().anyMatch(m -> m.getEmail().equals(user.getEmail()))) {
                                // Store the Firestore Document ID
                                user.setDocumentId(document.getId());
                                memberList.add(user);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing admin: " + document.getId(), e);
                        }
                    }

                    progressBar.setVisibility(View.GONE);

                    if (memberList.isEmpty()) {
                        tvEmptyState.setText("No members found.");
                        tvEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        // Ensure delete mode state is correctly reflected after data load
                        memberAdapter.toggleDeleteMode(isDeleteMode);
                    }
                    recyclerView.setVisibility(memberList.isEmpty() ? View.GONE : View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading admins", e);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Could not fully load team list.", Toast.LENGTH_SHORT).show();
                });
    }

    // NEW: Show confirmation dialog before deletion
    private void showDeleteConfirmationDialog() {
        if (selectedMembers.isEmpty()) {
            Toast.makeText(this, "Please select at least one member to delete.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to permanently delete " + selectedMembers.size() + " selected member(s)?")
                .setPositiveButton("DELETE", (dialog, which) -> deleteSelectedMembers())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // NEW: Implement batch permanent deletion
    private void deleteSelectedMembers() {
        if (selectedMembers.isEmpty()) return;

        btnConfirmDelete.setEnabled(false);
        btnConfirmDelete.setText("Deleting...");
        progressBar.setVisibility(View.VISIBLE);

        List<com.google.android.gms.tasks.Task<Void>> deleteTasks = new ArrayList<>();

        for (User user : selectedMembers) {
            // Determine collection based on role. Deletion must be permanent from both 'users' and 'admins' collection.
            String collectionPath = "admin".equals(user.getRole()) ? "admins" : "users";
            CollectionReference collectionRef = db.collection(collectionPath);

            if (user.getDocumentId() != null) {
                // Delete document by its ID
                deleteTasks.add(collectionRef.document(user.getDocumentId()).delete());
            } else {
                Log.e(TAG, "Cannot delete user without document ID: " + user.getEmail());
            }
        }

        // Wait for all delete tasks to complete
        Tasks.whenAllSuccess(deleteTasks)
                .addOnSuccessListener(results -> {
                    Toast.makeText(this, selectedMembers.size() + " member(s) deleted successfully.", Toast.LENGTH_LONG).show();
                    // Reset state and reload data
                    toggleDeleteMode();
                    loadMembers();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting members: ", e);
                    Toast.makeText(this, "Error deleting members. Some deletions may have failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Reset state to cancel and reload data
                    toggleDeleteMode();
                    loadMembers();
                });
    }
}