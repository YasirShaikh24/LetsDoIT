// src/main/java/com/example/letsdoit/ViewMembersActivity.java
package com.example.letsdoit;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ViewMembersActivity extends AppCompatActivity implements MemberAdapter.MemberActionListener {

    private RecyclerView recyclerView;
    private MemberAdapter memberAdapter;
    private List<User> memberList;
    private List<User> filteredMemberList;
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private Button btnBackToProfile;
    private Button btnDeleteSelected;

    private TextInputEditText etSearchQuery;
    private String currentSearchQuery = "";

    private Set<String> selectedMemberIds = new HashSet<>();
    private boolean isSelectionMode = false;

    private static final String TAG = "ViewMembersActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_members);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setTitle("Account Directory");
        }

        db = FirebaseFirestore.getInstance();

        recyclerView = findViewById(R.id.recycler_view_members);
        progressBar = findViewById(R.id.progress_bar);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        btnBackToProfile = findViewById(R.id.btn_back_to_profile);
        btnDeleteSelected = findViewById(R.id.btn_delete_selected);
        etSearchQuery = findViewById(R.id.et_search_query);

        btnBackToProfile.setOnClickListener(v -> {
            if (isSelectionMode) {
                exitSelectionMode();
            } else {
                finish();
            }
        });

        btnDeleteSelected.setOnClickListener(v -> showBulkDeleteConfirmation());

        memberList = new ArrayList<>();
        filteredMemberList = new ArrayList<>();
        memberAdapter = new MemberAdapter(filteredMemberList, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(memberAdapter);

        setupSearch();
        loadMembers();
    }

    private void setupSearch() {
        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim().toLowerCase();
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void applyFilter() {
        filteredMemberList.clear();

        if (currentSearchQuery.isEmpty()) {
            filteredMemberList.addAll(memberList);
        } else {
            for (User user : memberList) {
                String displayName = user.getDisplayName() != null ? user.getDisplayName().toLowerCase() : "";
                String email = user.getEmail() != null ? user.getEmail().toLowerCase() : "";

                if (displayName.contains(currentSearchQuery) || email.contains(currentSearchQuery)) {
                    filteredMemberList.add(user);
                }
            }
        }
        memberAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredMemberList.isEmpty()) {
            tvEmptyState.setText("No members found matching the search criteria.");
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return false;
    }

    @Override
    public void onMemberDeleteClick(User user) {
        showDeleteConfirmationDialog(user);
    }

    @Override
    public void onMemberEditClick(User user) {
        showEditMemberDialog(user);
    }

    @Override
    public void onMemberSelectClick(User user) {
        if (!isSelectionMode) {
            enterSelectionMode();
        }

        if (selectedMemberIds.contains(user.getDocumentId())) {
            selectedMemberIds.remove(user.getDocumentId());
        } else {
            selectedMemberIds.add(user.getDocumentId());
        }

        if (selectedMemberIds.isEmpty()) {
            exitSelectionMode();
        } else {
            updateDeleteButtonText();
        }

        memberAdapter.setSelectedIds(selectedMemberIds);
        memberAdapter.notifyDataSetChanged();
    }

    private void enterSelectionMode() {
        isSelectionMode = true;
        btnDeleteSelected.setVisibility(View.VISIBLE);
        btnBackToProfile.setText("Cancel");
        memberAdapter.setSelectionMode(true);
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedMemberIds.clear();
        btnDeleteSelected.setVisibility(View.GONE);
        btnBackToProfile.setText("Back");
        memberAdapter.setSelectionMode(false);
        memberAdapter.setSelectedIds(selectedMemberIds);
        memberAdapter.notifyDataSetChanged();
    }

    private void updateDeleteButtonText() {
        int count = selectedMemberIds.size();
        btnDeleteSelected.setText("Delete (" + count + ")");
    }

    private void showBulkDeleteConfirmation() {
        if (selectedMemberIds.isEmpty()) {
            Toast.makeText(this, "No members selected", Toast.LENGTH_SHORT).show();
            return;
        }

        int count = selectedMemberIds.size();
        new AlertDialog.Builder(this)
                .setTitle("Delete Members")
                .setMessage("Are you sure you want to permanently delete " + count + " member(s)?")
                .setPositiveButton("DELETE", (dialog, which) -> deleteBulkMembers())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteBulkMembers() {
        if (selectedMemberIds.isEmpty()) return;

        progressBar.setVisibility(View.VISIBLE);

        List<String> idsToDelete = new ArrayList<>(selectedMemberIds);
        int totalToDelete = idsToDelete.size();
        final int[] deletedCount = {0};
        final int[] failedCount = {0};

        for (String documentId : idsToDelete) {
            db.collection("users")
                    .document(documentId)
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        deletedCount[0]++;
                        checkBulkDeleteCompletion(deletedCount[0], failedCount[0], totalToDelete);
                    })
                    .addOnFailureListener(e -> {
                        failedCount[0]++;
                        Log.e(TAG, "Error deleting member: " + documentId, e);
                        checkBulkDeleteCompletion(deletedCount[0], failedCount[0], totalToDelete);
                    });
        }
    }

    private void checkBulkDeleteCompletion(int deleted, int failed, int total) {
        if (deleted + failed >= total) {
            progressBar.setVisibility(View.GONE);

            String message = deleted + " member(s) deleted successfully.";
            if (failed > 0) {
                message += " " + failed + " failed.";
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            exitSelectionMode();
            loadMembers();
        }
    }

    private void loadMembers() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    memberList.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            User user = document.toObject(User.class);
                            if (user != null && "user".equalsIgnoreCase(user.getRole())) {
                                user.setDocumentId(document.getId());
                                memberList.add(user);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing user: " + document.getId(), e);
                        }
                    }

                    applyFilter();
                    progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading members", e);
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Error loading members: " + e.getMessage());
                    tvEmptyState.setVisibility(View.VISIBLE);
                });
    }

    private void showDeleteConfirmationDialog(User userToDelete) {
        if (userToDelete.getDocumentId() == null) {
            Toast.makeText(this, "Cannot delete member without ID.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to permanently delete member " + userToDelete.getDisplayName() + "?")
                .setPositiveButton("DELETE", (dialog, which) -> deleteSingleMember(userToDelete))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSingleMember(User user) {
        String collectionPath = "admin".equals(user.getRole()) ? "admins" : "users";

        db.collection(collectionPath)
                .document(user.getDocumentId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Member deleted successfully.", Toast.LENGTH_LONG).show();
                    memberList.remove(user);
                    applyFilter();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting member: ", e);
                    Toast.makeText(this, "Error deleting member: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showEditMemberDialog(User userToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Member Credentials");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        TextView tvName = new TextView(this);
        tvName.setText("Editing: " + (userToEdit.getDisplayName() != null ? userToEdit.getDisplayName() : userToEdit.getEmail()));
        tvName.setTextSize(18);
        tvName.setPadding(0, 0, 0, 30);
        layout.addView(tvName);

        final EditText etEmail = new EditText(this);
        etEmail.setText(userToEdit.getEmail());
        etEmail.setHint("Email Address");
        etEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        etEmail.setBackgroundResource(R.drawable.radio_button_selector);
        etEmail.setPadding(20, 20, 20, 20);
        etEmail.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams)etEmail.getLayoutParams()).setMargins(0, 0, 0, 20);

        final EditText etPassword = new EditText(this);
        etPassword.setText(userToEdit.getPassword());
        etPassword.setHint("Password");
        etPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        etPassword.setBackgroundResource(R.drawable.radio_button_selector);
        etPassword.setPadding(20, 20, 20, 20);
        etPassword.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams)etPassword.getLayoutParams()).setMargins(0, 0, 0, 20);

        layout.addView(etEmail);
        layout.addView(etPassword);

        builder.setView(layout);

        builder.setPositiveButton("Save Changes", (d, which) -> {
            String newEmail = etEmail.getText().toString().trim();
            String newPassword = etPassword.getText().toString().trim();

            if (newEmail.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(this, "Email and Password cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newEmail.equals(userToEdit.getEmail()) || !newPassword.equals(userToEdit.getPassword())) {
                updateMemberInFirestore(userToEdit, newEmail, newPassword);
            } else {
                Toast.makeText(this, "No changes detected.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void updateMemberInFirestore(User originalUser, String newEmail, String newPassword) {
        String collectionPath = "admin".equals(originalUser.getRole()) ? "admins" : "users";

        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("email", newEmail);
        updates.put("password", newPassword);

        db.collection(collectionPath)
                .document(originalUser.getDocumentId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Member updated successfully. Preparing SMS notification...", Toast.LENGTH_LONG).show();

                    originalUser.setEmail(newEmail);
                    originalUser.setPassword(newPassword);
                    applyFilter();

                    sendUpdateNotificationSMS(originalUser, newEmail, newPassword);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating member: ", e);
                    Toast.makeText(this, "Error updating member: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void sendUpdateNotificationSMS(User user, String newEmail, String newPassword) {
        String mobileNumber = user.getMobileNumber();

        if (mobileNumber == null || mobileNumber.isEmpty()) {
            Toast.makeText(this, "Error: Mobile number not saved for this user. Cannot send SMS.", Toast.LENGTH_LONG).show();
            return;
        }

        String message = "Hello " + user.getDisplayName() + ",\n\n" +
                "Your Lets Do IT account credentials have been updated by your administrator.\n\n" +
                "New Credentials:\n" +
                "Email: " + newEmail + "\n" +
                "Password: " + newPassword + "\n\n" +
                "Please use these updated details for your next login. Thank you!";

        Uri uri = Uri.parse("smsto:" + Uri.encode(mobileNumber));
        Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
        intent.putExtra("sms_body", message);

        try {
            startActivity(intent);
            Toast.makeText(this, "SMS notification ready in messaging app!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error opening SMS intent", e);
            Toast.makeText(this, "Could not open messaging app. Please notify user manually.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode();
        } else {
            super.onBackPressed();
        }
    }
}