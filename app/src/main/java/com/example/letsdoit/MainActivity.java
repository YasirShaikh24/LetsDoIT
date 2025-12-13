// main/java/com/example/letsdoit/MainActivity.java
package com.example.letsdoit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private String welcomeMessage;
    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String displayName;

    private NotificationHelper notificationHelper;

    private long currentSelectedDateMillis = -1;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Notification permission granted");
                    scheduleNotifications();
                    // UPDATED time in Toast
                    Toast.makeText(this, "Daily task reminders enabled at 7:00 PM", Toast.LENGTH_LONG).show();
                } else {
                    Log.d(TAG, "Notification permission denied");
                    Toast.makeText(this, "You won't receive task reminders", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        notificationHelper = new NotificationHelper(this);

        displayName = getIntent().getStringExtra(LoginActivity.EXTRA_DISPLAY_NAME);
        loggedInUserEmail = getIntent().getStringExtra(LoginActivity.EXTRA_USER_EMAIL);
        loggedInUserRole = getIntent().getStringExtra(LoginActivity.EXTRA_USER_ROLE);

        if ("admin".equals(loggedInUserRole)) {
            welcomeMessage = "Welcome Admin";
        } else if (displayName != null) {
            welcomeMessage = "Welcome " + displayName;
        } else {
            welcomeMessage = "Dashboard";
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        if (!"admin".equals(loggedInUserRole)) {
            bottomNav.getMenu().removeItem(R.id.navigation_add_activity);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                selectedFragment = HomeFragment.newInstance(welcomeMessage, loggedInUserEmail, loggedInUserRole, displayName);
                if (selectedFragment instanceof HomeFragment) {
                    ((HomeFragment) selectedFragment).setSelectedDateMillis(currentSelectedDateMillis);
                }
            } else if (itemId == R.id.navigation_add_activity) {
                if ("admin".equals(loggedInUserRole)) {
                    selectedFragment = AddActivityFragment.newInstance(loggedInUserEmail, loggedInUserRole);
                }
            } else if (itemId == R.id.navigation_view_activity) {
                selectedFragment = ViewActivityFragment.newInstance(loggedInUserEmail, loggedInUserRole);
                if (selectedFragment instanceof ViewActivityFragment) {
                    ((ViewActivityFragment) selectedFragment).setSelectedDateMillis(currentSelectedDateMillis);
                }
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

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.navigation_home);
        }

        requestNotificationPermission();

        handleNotificationIntent(getIntent());
    }

    public void updateSelectedDate(long dateMillis) {
        this.currentSelectedDateMillis = dateMillis;

        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof HomeFragment) {
            ((HomeFragment) currentFragment).updateDateFromActivity(dateMillis);
        } else if (currentFragment instanceof ViewActivityFragment) {
            ((ViewActivityFragment) currentFragment).updateDateFromActivity(dateMillis);
        }
    }

    public long getCurrentSelectedDateMillis() {
        return currentSelectedDateMillis;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                scheduleNotifications();

                if (notificationHelper.areNotificationsEnabled()) {
                    Log.d(TAG, "Notifications already enabled at: " + notificationHelper.getNotificationTime());
                }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            scheduleNotifications();
        }
    }

    /**
     * UPDATED: Schedule daily notifications at 7:00 PM (19:00)
     */
    private void scheduleNotifications() {
        // CHANGED: Set time to 7:00 PM (19 hours, 0 minutes)
        notificationHelper.scheduleDailyNotification(19, 0);
        Log.d(TAG, "Daily notifications scheduled at 7:00 PM");
    }

    private void handleNotificationIntent(android.content.Intent intent) {
        if (intent != null && intent.getBooleanExtra("open_view_tasks", false)) {
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.navigation_view_activity);
            }
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }
}