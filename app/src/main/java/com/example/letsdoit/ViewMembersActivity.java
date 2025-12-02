// src/main/java/com/example/letsdoit/ViewMembersActivity.java
package com.example.letsdoit;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button; // Changed import from ImageButton to Button
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ViewMembersActivity extends AppCompatActivity implements MemberAdapter.MemberDeleteListener {

    private RecyclerView recyclerView;
    private MemberAdapter memberAdapter;
    private List<User> memberList;
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private Button btnBackToProfile; // CHANGED: Type is now Button

    private static final String TAG = "ViewMembersActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_members);

        // Hide the default ActionBar (Toolbar) back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setTitle("Team Members");
        }

        db = FirebaseFirestore.getInstance();

        recyclerView = findViewById(R.id.recycler_view_members);
        progressBar = findViewById(R.id.progress_bar);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        btnBackToProfile = findViewById(R.id.btn_back_to_profile); // CHANGED: Find the Button

        // Set click listener for the custom back button to go back to Profile Fragment (via MainActivity)
        btnBackToProfile.setOnClickListener(v -> finish());

        memberList = new ArrayList<>();
        memberAdapter = new MemberAdapter(memberList, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        recyclerView.setAdapter(memberAdapter);

        loadMembers();
    }

    // Since we are using a custom button, we override this to do nothing or rely on the system's default behavior
    @Override
    public boolean onSupportNavigateUp() {
        return false;
    }

    // Implementation of the single deletion listener
    @Override
    public void onMemberDeleteClick(User user) {
        showDeleteConfirmationDialog(user);
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
                    }
                    recyclerView.setVisibility(memberList.isEmpty() ? View.GONE : View.VISIBLE);
                    memberAdapter.notifyDataSetChanged(); // Ensure data refresh
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading admins", e);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Could not fully load team list.", Toast.LENGTH_SHORT).show();
                });
    }

    // Show confirmation dialog before deletion
    private void showDeleteConfirmationDialog(User userToDelete) {
        if (userToDelete.getDocumentId() == null) {
            Toast.makeText(this, "Cannot delete member without ID.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to permanently delete member " + userToDelete.getEmail() + "?")
                .setPositiveButton("DELETE", (dialog, which) -> deleteSingleMember(userToDelete))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Implement single permanent deletion
    private void deleteSingleMember(User user) {
        String collectionPath = "admin".equals(user.getRole()) ? "admins" : "users";

        db.collection(collectionPath)
                .document(user.getDocumentId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Member deleted successfully.", Toast.LENGTH_LONG).show();

                    // Manually remove from list and notify adapter for instant UI update
                    memberList.remove(user);
                    memberAdapter.notifyDataSetChanged();

                    if (memberList.isEmpty()) {
                        tvEmptyState.setText("No members found.");
                        tvEmptyState.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting member: ", e);
                    Toast.makeText(this, "Error deleting member: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}