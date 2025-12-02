// src/main/java/com/example/letsdoit/HomeFragment.java
package com.example.letsdoit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {

    private static final String ARG_WELCOME_MESSAGE = "welcome_message";
    private String welcomeMessage;

    // Factory method to create an instance of the fragment with arguments
    public static HomeFragment newInstance(String welcomeMessage) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_WELCOME_MESSAGE, welcomeMessage);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            welcomeMessage = getArguments().getString(ARG_WELCOME_MESSAGE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        TextView welcomeTextView = view.findViewById(R.id.tv_welcome_message);

        // Use the personalized welcome message
        if (welcomeMessage != null) {
            welcomeTextView.setText(welcomeMessage);
        } else {
            // Fallback - Updated from "Home / Dashboard" to "Dashboard"
            welcomeTextView.setText("Dashboard");
        }

        return view;
    }
}