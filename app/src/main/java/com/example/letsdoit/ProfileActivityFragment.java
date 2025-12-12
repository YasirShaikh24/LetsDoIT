package com.example.letsdoit;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;
import java.util.regex.Pattern;

public class ProfileActivityFragment extends Fragment {

    private TextView tvDisplayName, tvUserEmail;
    private Button btnLogout, btnAddMember, btnViewMembers;
    private ImageButton btnAdminEdit; // NEW: Button for admin self-edit

    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String displayName;

    private FirebaseFirestore db;

    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String TAG = "ProfileFragment";

    public static ProfileActivityFragment newInstance(String email, String role, String displayName) {
        ProfileActivityFragment fragment = new ProfileActivityFragment();
        Bundle args = new Bundle();
        args.putString(LoginActivity.EXTRA_USER_EMAIL, email);
        args.putString(LoginActivity.EXTRA_USER_ROLE, role);
        args.putString(LoginActivity.EXTRA_DISPLAY_NAME, displayName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            loggedInUserEmail = getArguments().getString(LoginActivity.EXTRA_USER_EMAIL);
            loggedInUserRole = getArguments().getString(LoginActivity.EXTRA_USER_ROLE);
            displayName = getArguments().getString(LoginActivity.EXTRA_DISPLAY_NAME);
        }

        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile_activity, container, false);

        tvDisplayName = view.findViewById(R.id.tv_display_name);
        tvUserEmail = view.findViewById(R.id.tv_user_email);

        btnLogout = view.findViewById(R.id.btn_logout);
        btnAddMember = view.findViewById(R.id.btn_add_member);
        btnViewMembers = view.findViewById(R.id.btn_view_members);
        btnAdminEdit = view.findViewById(R.id.btn_admin_edit); // Initialize new button

        // Set display name (Admin gets "Mohsin Mir" by default)
        if ("admin".equalsIgnoreCase(loggedInUserRole)) {
            if (displayName == null || displayName.trim().isEmpty() || displayName.equalsIgnoreCase("Admin")) {
                displayName = "Mohsin Mir";
            }
        }

        tvDisplayName.setText(displayName != null ? displayName.toUpperCase() : "USER");
        tvUserEmail.setText(loggedInUserEmail != null ? loggedInUserEmail : "No email");

        // Show admin buttons only for admin
        if ("admin".equalsIgnoreCase(loggedInUserRole)) {
            btnViewMembers.setVisibility(View.VISIBLE);
            btnAddMember.setVisibility(View.VISIBLE);
            btnAdminEdit.setVisibility(View.VISIBLE); // Show Admin self-edit button

            btnViewMembers.setOnClickListener(v -> startActivity(new Intent(getActivity(), ViewMembersActivity.class)));
            btnAddMember.setOnClickListener(v -> startActivity(new Intent(getActivity(), AddMemberActivity.class)));
            btnAdminEdit.setOnClickListener(v -> fetchAdminDetailsAndShowDialog()); // NEW listener
        } else {
            btnAdminEdit.setVisibility(View.GONE); // Hide for regular users
        }

        // Logout button
        btnLogout.setOnClickListener(v -> logout());

        return view;
    }

    private void logout() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply();

        Toast.makeText(getContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    // NEW: Fetch admin details to edit
    private void fetchAdminDetailsAndShowDialog() {
        db.collection("admins")
                .whereEqualTo("email", loggedInUserEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        User adminUser = doc.toObject(User.class);
                        adminUser.setDocumentId(doc.getId()); // Store document ID for update
                        showAdminEditDialog(adminUser);
                    } else {
                        Toast.makeText(getContext(), "Admin record not found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch admin details: " + e.getMessage());
                    Toast.makeText(getContext(), "Failed to load admin data.", Toast.LENGTH_SHORT).show();
                });
    }

    // UPDATED: Show Admin self-edit dialog to include email
    private void showAdminEditDialog(User adminUser) {

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Edit Admin Credentials");

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        TextView tvInfo = new TextView(requireContext());
        tvInfo.setText("Current Display Name: " + adminUser.getDisplayName() + "\nRole: Administrator");
        tvInfo.setPadding(0, 0, 0, 30);
        layout.addView(tvInfo);

        final EditText etEmail = new EditText(requireContext());
        etEmail.setText(adminUser.getEmail());
        etEmail.setHint("New Email Address");
        etEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        etEmail.setBackgroundResource(R.drawable.radio_button_selector);
        etEmail.setPadding(20, 20, 20, 20);
        etEmail.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams)etEmail.getLayoutParams()).setMargins(0, 0, 0, 20);
        layout.addView(etEmail);

        final EditText etPassword = new EditText(requireContext());
        etPassword.setText(adminUser.getPassword());
        etPassword.setHint("New Password");
        etPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        etPassword.setBackgroundResource(R.drawable.radio_button_selector);
        etPassword.setPadding(20, 20, 20, 20);
        etPassword.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams)etPassword.getLayoutParams()).setMargins(0, 0, 0, 20);
        layout.addView(etPassword);

        builder.setView(layout);

        builder.setPositiveButton("Update Credentials", (d, which) -> {
            String newEmail = etEmail.getText().toString().trim();
            String newPassword = etPassword.getText().toString().trim();

            if (newEmail.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(getContext(), "Email and Password cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean emailChanged = !newEmail.equals(adminUser.getEmail());
            boolean passwordChanged = !newPassword.equals(adminUser.getPassword());

            if (emailChanged || passwordChanged) {
                updateAdminCredentialsInFirestore(adminUser.getDocumentId(), newEmail, newPassword, emailChanged);
            } else {
                Toast.makeText(getContext(), "No changes detected.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (d, which) -> d.cancel());
        builder.show();
    }

    // UPDATED: Update Admin Email and Password in Firestore and LOGOUT
    private void updateAdminCredentialsInFirestore(String documentId, String newEmail, String newPassword, boolean emailChanged) {
        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("email", newEmail);
        updates.put("password", newPassword);

        db.collection("admins").document(documentId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    // --- LOGOUT SEQUENCE (for admin self-change) ---
                    SharedPreferences prefs = requireActivity().getSharedPreferences(LoginActivity.PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().putBoolean(LoginActivity.KEY_IS_LOGGED_IN, false).apply();

                    Toast.makeText(getContext(), "Credentials updated. Please log in with the new details.", Toast.LENGTH_LONG).show();

                    // Navigate to LoginActivity
                    Intent intent = new Intent(getActivity(), LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                    // --- END LOGOUT SEQUENCE ---
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating admin credentials: " + e.getMessage());
                    Toast.makeText(getContext(), "Failed to update credentials.", Toast.LENGTH_SHORT).show();
                });
    }
}