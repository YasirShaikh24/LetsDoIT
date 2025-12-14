// src/main/java/com/example/letsdoit/LoginActivity.java
package com.example.letsdoit;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etFullname, etEmail, etPassword;
    private Button btnLogin;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;

    // Animation views
    private View circle1, circle2, circle3, circle4;
    private ImageView appLogo;

    private static final String TAG = "LoginActivity";
    private static final String ADMIN_EMAIL = "mahimhussain444@gmail.com";
    private static final String ADMIN_PASSWORD = "Mahim11";

    public static final String EXTRA_DISPLAY_NAME = "extra_display_name";
    public static final String EXTRA_USER_ROLE = "extra_user_role";
    public static final String EXTRA_USER_EMAIL = "extra_user_email";

    public static final String PREFS_NAME = "LoginPrefs";
    public static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_ROLE = "role";
    private static final String KEY_DISPLAY_NAME = "displayName";
    public static final String KEY_PASSWORD = "password";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Check for saved session and auto-login
        if (sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)) {
            String email = sharedPreferences.getString(KEY_EMAIL, null);
            String role = sharedPreferences.getString(KEY_ROLE, null);
            String displayName = sharedPreferences.getString(KEY_DISPLAY_NAME, null);

            if (email != null && role != null && displayName != null) {
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra(EXTRA_DISPLAY_NAME, displayName);
                intent.putExtra(EXTRA_USER_ROLE, role);
                intent.putExtra(EXTRA_USER_EMAIL, email);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return;
            }
        }

        setContentView(R.layout.activity_login);

        db = FirebaseFirestore.getInstance();

        etFullname = findViewById(R.id.et_fullname);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);

        // Initialize animation views
        circle1 = findViewById(R.id.circle_1);
        circle2 = findViewById(R.id.circle_2);
        circle3 = findViewById(R.id.circle_3);
        circle4 = findViewById(R.id.circle_4);
        appLogo = findViewById(R.id.app_logo);

        btnLogin.setOnClickListener(v -> attemptLogin());

        // Start animations
        startCircleAnimations();
        startLogoAnimation();
        startButtonPulseAnimation();
    }

    private void startCircleAnimations() {
        // Circle 1 - Diagonal bounce (was 4000ms -> now 2500ms)
        ObjectAnimator circle1X = ObjectAnimator.ofFloat(circle1, "translationX", 0f, 60f, 0f);
        ObjectAnimator circle1Y = ObjectAnimator.ofFloat(circle1, "translationY", 0f, 80f, 0f);
        circle1X.setDuration(2500);
        circle1Y.setDuration(2500);
        circle1X.setRepeatCount(ObjectAnimator.INFINITE);
        circle1Y.setRepeatCount(ObjectAnimator.INFINITE);
        circle1X.setInterpolator(new AccelerateDecelerateInterpolator());
        circle1Y.setInterpolator(new AccelerateDecelerateInterpolator());

        // Circle 2 - Circular motion (was 5000ms -> now 3000ms)
        ObjectAnimator circle2X = ObjectAnimator.ofFloat(circle2, "translationX", 0f, -40f, 0f, 40f, 0f);
        ObjectAnimator circle2Y = ObjectAnimator.ofFloat(circle2, "translationY", 0f, 40f, 80f, 40f, 0f);
        circle2X.setDuration(3000);
        circle2Y.setDuration(3000);
        circle2X.setRepeatCount(ObjectAnimator.INFINITE);
        circle2Y.setRepeatCount(ObjectAnimator.INFINITE);
        circle2X.setInterpolator(new AccelerateDecelerateInterpolator());
        circle2Y.setInterpolator(new AccelerateDecelerateInterpolator());

        // Circle 3 - Slow drift (was 6000ms -> now 3500ms)
        ObjectAnimator circle3X = ObjectAnimator.ofFloat(circle3, "translationX", 0f, -30f, 0f);
        ObjectAnimator circle3Y = ObjectAnimator.ofFloat(circle3, "translationY", 0f, -50f, 0f);
        ObjectAnimator circle3Scale = ObjectAnimator.ofFloat(circle3, "scaleX", 1f, 1.1f, 1f);
        ObjectAnimator circle3ScaleY = ObjectAnimator.ofFloat(circle3, "scaleY", 1f, 1.1f, 1f);
        circle3X.setDuration(3500);
        circle3Y.setDuration(3500);
        circle3Scale.setDuration(3500);
        circle3ScaleY.setDuration(3500);
        circle3X.setRepeatCount(ObjectAnimator.INFINITE);
        circle3Y.setRepeatCount(ObjectAnimator.INFINITE);
        circle3Scale.setRepeatCount(ObjectAnimator.INFINITE);
        circle3ScaleY.setRepeatCount(ObjectAnimator.INFINITE);
        circle3X.setInterpolator(new DecelerateInterpolator());
        circle3Y.setInterpolator(new DecelerateInterpolator());

        // Circle 4 - Bounce with rotation (was 3500ms -> now 2000ms)
        ObjectAnimator circle4Y = ObjectAnimator.ofFloat(circle4, "translationY", 0f, -60f, 0f, 30f, 0f);
        ObjectAnimator circle4Rotate = ObjectAnimator.ofFloat(circle4, "rotation", 0f, 360f);
        circle4Y.setDuration(2000);
        circle4Rotate.setDuration(4000); // Keep rotation a bit slower
        circle4Y.setRepeatCount(ObjectAnimator.INFINITE);
        circle4Rotate.setRepeatCount(ObjectAnimator.INFINITE);
        circle4Y.setInterpolator(new BounceInterpolator());
        circle4Rotate.setInterpolator(new AccelerateDecelerateInterpolator());

        // Start all animations
        circle1X.start();
        circle1Y.start();
        circle2X.start();
        circle2Y.start();
        circle3X.start();
        circle3Y.start();
        circle3Scale.start();
        circle3ScaleY.start();
        circle4Y.start();
        circle4Rotate.start();
    }

    private void startLogoAnimation() {
        // Gentle float animation for logo
        ObjectAnimator logoFloat = ObjectAnimator.ofFloat(appLogo, "translationY", 0f, -15f, 0f);
        logoFloat.setDuration(2000);
        logoFloat.setRepeatCount(ObjectAnimator.INFINITE);
        logoFloat.setInterpolator(new AccelerateDecelerateInterpolator());
        logoFloat.start();

        // Subtle rotation
        ObjectAnimator logoRotate = ObjectAnimator.ofFloat(appLogo, "rotation", -5f, 5f, -5f);
        logoRotate.setDuration(4000);
        logoRotate.setRepeatCount(ObjectAnimator.INFINITE);
        logoRotate.setInterpolator(new AccelerateDecelerateInterpolator());
        logoRotate.start();
    }

    private void startButtonPulseAnimation() {
        // Scale pulse animation (increased scale to 1.1f for more bounce)
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(btnLogin, "scaleX", 1f, 1.1f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(btnLogin, "scaleY", 1f, 1.1f, 1f);

        // Elevation pulse
        ObjectAnimator elevation = ObjectAnimator.ofFloat(btnLogin, "elevation", 4f, 12f, 4f);

        // FIX: Set repeat count on individual animators, as AnimatorSet does not have setRepeatCount()
        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);
        elevation.setRepeatCount(ObjectAnimator.INFINITE);

        // Use RESTART mode to repeat the full 1.0 -> 1.1 -> 1.0 cycle
        scaleX.setRepeatMode(ObjectAnimator.RESTART);
        scaleY.setRepeatMode(ObjectAnimator.RESTART);
        elevation.setRepeatMode(ObjectAnimator.RESTART);

        AnimatorSet pulseSet = new AnimatorSet();
        pulseSet.playTogether(scaleX, scaleY, elevation);
        pulseSet.setDuration(1000); // Faster pulse (was 1500ms -> now 1000ms)
        pulseSet.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseSet.start();
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        if (email.equals(ADMIN_EMAIL)) {
            verifyAdminLogin(email, password, true);
        } else {
            verifyUserOrAdminLogin(email, password);
        }
    }

    private void verifyUserOrAdminLogin(String email, String password) {
        db.collection("admins")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        User admin = doc.toObject(User.class);

                        if (admin != null && admin.getPassword() != null && admin.getPassword().equals(password)) {
                            onLoginSuccess(admin.getEmail(), admin.getRole(), admin.getDisplayName(), password);
                        } else {
                            onLoginFailure("Invalid admin credentials");
                        }
                    } else {
                        verifyUserLogin(email, password);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking admin existence with custom email", e);
                    onLoginFailure("Database error: " + e.getMessage());
                });
    }

    private void verifyAdminLogin(String email, String password, boolean isDefaultAdminEmail) {
        if (isDefaultAdminEmail && password.equals(ADMIN_PASSWORD)) {
            db.collection("admins")
                    .whereEqualTo("email", email)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                            User admin = doc.toObject(User.class);
                            if (admin != null) {
                                updateAdminPassword(doc.getReference().getId(), password, admin);
                            }
                        } else {
                            registerAdminInDatabase(email, password);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking admin existence", e);
                        onLoginFailure("Database error: " + e.getMessage());
                    });
        } else {
            db.collection("admins")
                    .whereEqualTo("email", email)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                            User admin = doc.toObject(User.class);

                            if (admin != null && admin.getPassword() != null && admin.getPassword().equals(password)) {
                                onLoginSuccess(admin.getEmail(), admin.getRole(), admin.getDisplayName(), password);
                            } else {
                                onLoginFailure("Invalid admin credentials");
                            }
                        } else {
                            onLoginFailure("Invalid admin credentials");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking admin credentials", e);
                        onLoginFailure("Database error: " + e.getMessage());
                    });
        }
    }

    private void updateAdminPassword(String documentId, String password, User admin) {
        db.collection("admins")
                .document(documentId)
                .update("password", password)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Admin password updated successfully");
                    onLoginSuccess(admin.getEmail(), admin.getRole(), admin.getDisplayName(), password);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating admin password", e);
                    onLoginSuccess(admin.getEmail(), admin.getRole(), admin.getDisplayName(), password);
                });
    }

    private void registerAdminInDatabase(String email, String password) {
        User newAdmin = new User(email, "admin", "Mohsin Mir", password);

        db.collection("admins")
                .add(newAdmin)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Admin registered successfully");
                    onLoginSuccess(newAdmin.getEmail(), newAdmin.getRole(), newAdmin.getDisplayName(), password);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error registering admin", e);
                    onLoginFailure("Admin registration failed: " + e.getMessage());
                });
    }

    private void verifyUserLogin(String email, String password) {
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        User user = doc.toObject(User.class);

                        if (user != null) {
                            String storedPassword = user.getPassword();
                            if (storedPassword != null && storedPassword.equals(password)) {
                                onLoginSuccess(user.getEmail(), user.getRole(), user.getDisplayName(), password);
                            } else {
                                onLoginFailure("Invalid email or password");
                            }
                        } else {
                            onLoginFailure("User data error");
                        }
                    } else {
                        onLoginFailure("Invalid email or password. Contact admin to get access.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking user credentials", e);
                    onLoginFailure("Login failed: " + e.getMessage());
                });
    }

    private void onLoginSuccess(String email, String role, String displayName, String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_ROLE, role);
        editor.putString(KEY_DISPLAY_NAME, displayName);
        editor.putString(KEY_PASSWORD, password);
        editor.apply();

        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
        btnLogin.setEnabled(true);
        btnLogin.setText("Login");

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra(EXTRA_DISPLAY_NAME, displayName);
        intent.putExtra(EXTRA_USER_ROLE, role);
        intent.putExtra(EXTRA_USER_EMAIL, email);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void onLoginFailure(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        btnLogin.setEnabled(true);
        btnLogin.setText("Login");
    }
}