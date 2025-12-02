// src/main/java/com/example/letsdoit/ProfileActivityFragment.java
package com.example.letsdoit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private TextView tvUserEmail, tvDisplayName; // tvUserRole removed
    private Button btnLogout, btnAddMember, btnViewMembers;

    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String displayName;

    // SharedPreferences Constants (Must match LoginActivity)
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";

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
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile_activity, container, false);

        tvDisplayName = view.findViewById(R.id.tv_display_name);
        tvUserEmail = view.findViewById(R.id.tv_user_email);
        // tvUserRole removed from here

        btnLogout = view.findViewById(R.id.btn_logout);
        btnAddMember = view.findViewById(R.id.btn_add_member);
        btnViewMembers = view.findViewById(R.id.btn_view_members);

        // Logic to set admin name to "Mohsin Mir" by default
        if ("admin".equals(loggedInUserRole)) {
            if (displayName == null || displayName.isEmpty() || displayName.equalsIgnoreCase("Admin")) {
                displayName = "Mohsin Mir";
            }
        }

        if (displayName != null && !displayName.isEmpty()) {
            tvDisplayName.setText(displayName.toUpperCase());
        } else {
            tvDisplayName.setText("USER");
        }

        if (loggedInUserEmail != null) {
            tvUserEmail.setText(loggedInUserEmail);
        }

        // Role display logic removed

        // Show "View Members" and "Add Members" button only for admin
        if ("admin".equals(loggedInUserRole)) {
            // 1. View Members
            btnViewMembers.setVisibility(View.VISIBLE);
            btnViewMembers.setOnClickListener(v -> openViewMembersActivity());

            // 2. Add Member
            btnAddMember.setVisibility(View.VISIBLE);
            btnAddMember.setOnClickListener(v -> openAddMemberActivity());

        } else {
            btnAddMember.setVisibility(View.GONE);
            btnViewMembers.setVisibility(View.GONE);
        }

        // 3. Logout (always visible)
        btnLogout.setOnClickListener(v -> logout());

        return view;
    }

    private void openAddMemberActivity() {
        Intent intent = new Intent(getActivity(), AddMemberActivity.class);
        startActivity(intent);
    }

    private void openViewMembersActivity() {
        Intent intent = new Intent(getActivity(), ViewMembersActivity.class);
        startActivity(intent);
    }

    private void logout() {
        // Clear SharedPreferences to end the session
        if (getActivity() != null) {
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_IS_LOGGED_IN, false);
            editor.apply();
        }

        Toast.makeText(getContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        if (getActivity() != null) {
            getActivity().finish();
        }
    }
}