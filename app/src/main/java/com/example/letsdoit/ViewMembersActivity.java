// src/main/java/com/example/letsdoit/ViewMembersActivity.java
package com.example.letsdoit;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button; // Changed import from ImageButton to Button
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

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    // Implementation of the single edit listener (NEW)
    @Override
    public void onMemberEditClick(User user) {
        showEditMemberDialog(user);
    }

    private void loadMembers() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        // Load only documents from the "users" collection (regular members)
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    memberList.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            User user = document.toObject(User.class);
                            // Only add users, not admins (Admins are stored separately, but this is a double-check)
                            if (user != null && "user".equalsIgnoreCase(user.getRole())) {
                                // Store the Firestore Document ID
                                user.setDocumentId(document.getId());
                                memberList.add(user);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing user: " + document.getId(), e);
                        }
                    }

                    // Removed loadAdmins() call, as main list should only show users

                    progressBar.setVisibility(View.GONE);

                    if (memberList.isEmpty()) {
                        tvEmptyState.setText("No team members found.");
                        tvEmptyState.setVisibility(View.VISIBLE);
                    }
                    recyclerView.setVisibility(memberList.isEmpty() ? View.GONE : View.VISIBLE);
                    memberAdapter.notifyDataSetChanged(); // Ensure data refresh
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading members", e);
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Error loading members: " + e.getMessage());
                    tvEmptyState.setVisibility(View.VISIBLE);
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

    // NEW: Show edit dialog with improved UI
    private void showEditMemberDialog(User userToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Member Credentials");

        // Use LayoutInflater to inflate a custom view for better styling (assuming you would add a dialog_edit_member.xml in a real scenario)
        // For now, improving the programmatic UI:
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
        etEmail.setBackgroundResource(R.drawable.radio_button_selector); // Use an existing drawable for a box look
        etEmail.setPadding(20, 20, 20, 20);
        etEmail.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams)etEmail.getLayoutParams()).setMargins(0, 0, 0, 20);

        final EditText etPassword = new EditText(this);
        etPassword.setText(userToEdit.getPassword());
        etPassword.setHint("Password");
        etPassword.setBackgroundResource(R.drawable.radio_button_selector); // Use an existing drawable for a box look
        etPassword.setPadding(20, 20, 20, 20);
        etPassword.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams)etPassword.getLayoutParams()).setMargins(0, 0, 0, 20);

        layout.addView(etEmail);
        layout.addView(etPassword);

        builder.setView(layout);

        builder.setPositiveButton("Save Changes", (dialog, which) -> {
            String newEmail = etEmail.getText().toString().trim();
            String newPassword = etPassword.getText().toString().trim();

            if (newEmail.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(this, "Email and Password cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Only update if something changed
            if (!newEmail.equals(userToEdit.getEmail()) || !newPassword.equals(userToEdit.getPassword())) {
                updateMemberInFirestore(userToEdit, newEmail, newPassword);
            } else {
                Toast.makeText(this, "No changes detected.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // NEW: Implement update logic
    private void updateMemberInFirestore(User originalUser, String newEmail, String newPassword) {
        String collectionPath = "admin".equals(originalUser.getRole()) ? "admins" : "users";

        // Prepare update map
        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("email", newEmail);
        updates.put("password", newPassword);

        db.collection(collectionPath)
                .document(originalUser.getDocumentId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Member updated successfully. Preparing SMS notification...", Toast.LENGTH_LONG).show();

                    // Update local object for UI refresh
                    originalUser.setEmail(newEmail);
                    originalUser.setPassword(newPassword);
                    memberAdapter.notifyDataSetChanged();

                    // Proceed to send SMS
                    sendUpdateNotificationSMS(originalUser, newEmail, newPassword);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating member: ", e);
                    Toast.makeText(this, "Error updating member: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // NEW: Prepare and open SMS Intent
    private void sendUpdateNotificationSMS(User user, String newEmail, String newPassword) {
        String mobileNumber = user.getMobileNumber();

        if (mobileNumber == null || mobileNumber.isEmpty()) {
            Toast.makeText(this, "Error: Mobile number not saved for this user. Cannot send SMS.", Toast.LENGTH_LONG).show();
            return;
        }

        // Custom SMS message
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
}