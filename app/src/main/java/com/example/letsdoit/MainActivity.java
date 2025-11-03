// src/main/java/com/example/letsdoit/MainActivity.java
package com.example.letsdoit;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private String welcomeMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Retrieve user data passed from LoginActivity
        String displayName = getIntent().getStringExtra(LoginActivity.EXTRA_DISPLAY_NAME);
        String userRole = getIntent().getStringExtra(LoginActivity.EXTRA_USER_ROLE);

        // Generate the personalized welcome message
        if ("admin".equals(userRole)) {
            welcomeMessage = "Welcome Admin";
        } else if ("user".equals(userRole) && displayName != null) {
            welcomeMessage = "Welcome " + displayName;
        } else {
            // Default message if launched without specific login info
            welcomeMessage = "Home / Dashboard";
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                // Pass the personalized message to the HomeFragment
                selectedFragment = HomeFragment.newInstance(welcomeMessage);
            } else if (itemId == R.id.navigation_add_activity) {
                selectedFragment = new AddActivityFragment();
            } else if (itemId == R.id.navigation_view_activity) {
                selectedFragment = new ViewActivityFragment();
            } else if (itemId == R.id.navigation_profile_activity) {
                selectedFragment = new ProfileActivityFragment();
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