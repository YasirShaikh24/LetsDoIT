// src/main/java/com/example/letsdoit/MainActivity.java
package com.example.letsdoit;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private String welcomeMessage;
    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String displayName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Retrieve user data passed from LoginActivity
        displayName = getIntent().getStringExtra(LoginActivity.EXTRA_DISPLAY_NAME);
        loggedInUserEmail = getIntent().getStringExtra(LoginActivity.EXTRA_USER_EMAIL);
        loggedInUserRole = getIntent().getStringExtra(LoginActivity.EXTRA_USER_ROLE);

        // Generate the personalized welcome message
        if ("admin".equals(loggedInUserRole)) {
            welcomeMessage = "Welcome Admin";
        } else if (displayName != null) {
            // All other users
            welcomeMessage = "Welcome " + displayName;
        } else {
            // Default message
            welcomeMessage = "Home / Dashboard";
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                selectedFragment = HomeFragment.newInstance(welcomeMessage);
            } else if (itemId == R.id.navigation_add_activity) {
                selectedFragment = AddActivityFragment.newInstance(loggedInUserEmail, loggedInUserRole);
            } else if (itemId == R.id.navigation_view_activity) {
                selectedFragment = ViewActivityFragment.newInstance(loggedInUserEmail, loggedInUserRole);
            } else if (itemId == R.id.navigation_profile_activity) {
                selectedFragment = ProfileActivityFragment.newInstance(loggedInUserEmail, loggedInUserRole, displayName);
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        });

        // Set the Dashboard/Home Fragment as the default selected screen (this triggers the listener above)
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.navigation_home);
        }
    }
}