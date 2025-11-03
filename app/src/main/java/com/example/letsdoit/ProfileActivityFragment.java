// src/main/java/com/example/letsdoit/ProfileActivityFragment.java
package com.example.letsdoit;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileActivityFragment extends Fragment {

    private TextView tvUserEmail, tvUserRole, tvDisplayName;
    private Button btnLogout;

    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String displayName;

    // Factory method to create an instance with user data
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

        // Retrieve user data from arguments
        if (getArguments() != null) {
            loggedInUserEmail = getArguments().getString(LoginActivity.EXTRA_USER_EMAIL);
            loggedInUserRole = getArguments().getString(LoginActivity.EXTRA_USER_ROLE);
            displayName = getArguments().getString(LoginActivity.EXTRA_DISPLAY_NAME);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile_activity, container, false);

        // Initialize views
        tvDisplayName = view.findViewById(R.id.tv_display_name);
        tvUserEmail = view.findViewById(R.id.tv_user_email);
        tvUserRole = view.findViewById(R.id.tv_user_role);
        btnLogout = view.findViewById(R.id.btn_logout);

        // Display user information
        if (displayName != null && !displayName.isEmpty()) {
            tvDisplayName.setText(displayName);
        } else {
            tvDisplayName.setText("User");
        }

        if (loggedInUserEmail != null) {
            tvUserEmail.setText(loggedInUserEmail);
        }

        if (loggedInUserRole != null) {
            String roleDisplay = loggedInUserRole.substring(0, 1).toUpperCase() + loggedInUserRole.substring(1);
            tvUserRole.setText("Role: " + roleDisplay);
        }

        // Logout button click listener
        btnLogout.setOnClickListener(v -> logout());

        return view;
    }

    private void logout() {
        Toast.makeText(getContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();

        // Navigate back to LoginActivity
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        // Finish the current activity
        if (getActivity() != null) {
            getActivity().finish();
        }
    }
}