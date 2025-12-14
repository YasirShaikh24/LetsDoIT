// src/main/java/com/example/letsdoit/LoginActivity.java
package com.example.letsdoit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etFullname, etEmail, etPassword;
    private Button btnLogin;
    // REMOVED: private ImageButton btnBack;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;

    private static final String TAG = "LoginActivity";
    // NOTE: Keep these hardcoded for initial admin setup/recovery, but the flow will now check the database first.
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
        // REMOVED: btnBack = findViewById(R.id.btn_back);

        // etFullname views are now hidden in XML (visibility="gone")

        btnLogin.setOnClickListener(v -> attemptLogin());

        // REMOVED: btnBack.setOnClickListener(v -> {
        // REMOVED:     finish();
        // REMOVED:     overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        // REMOVED: });
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

        // Check if the input email is the original ADMIN_EMAIL
        if (email.equals(ADMIN_EMAIL)) {
            verifyAdminLogin(email, password, true); // original admin email
        } else {
            // Assume it is a regular user or an admin with a changed email
            verifyUserOrAdminLogin(email, password);
        }
    }

    // NEW METHOD: Check if the login is for a regular user or an admin with a non-default email
    private void verifyUserOrAdminLogin(String email, String password) {
        // 1. Check in 'admins' collection
        db.collection("admins")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        // Found in admins collection - proceed with admin login check
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        User admin = doc.toObject(User.class);

                        if (admin != null && admin.getPassword() != null && admin.getPassword().equals(password)) {
                            onLoginSuccess(admin.getEmail(), admin.getRole(), admin.getDisplayName(), password);
                        } else {
                            onLoginFailure("Invalid admin credentials");
                        }
                    } else {
                        // Not found in admins collection - check in 'users'
                        verifyUserLogin(email, password);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking admin existence with custom email", e);
                    onLoginFailure("Database error: " + e.getMessage());
                });
    }

    // MODIFIED: Merged admin login logic (for original email and initial setup)
    private void verifyAdminLogin(String email, String password, boolean isDefaultAdminEmail) {
        // Logic for the original hardcoded admin email (mahimhussain444@gmail.com)
        if (isDefaultAdminEmail && password.equals(ADMIN_PASSWORD)) {
            // Password matches hardcoded default. Ensure it's registered in Firestore.
            db.collection("admins")
                    .whereEqualTo("email", email)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                            User admin = doc.toObject(User.class);
                            if (admin != null) {
                                // Update password in Firestore if it was empty/default
                                updateAdminPassword(doc.getReference().getId(), password, admin);
                            }
                        } else {
                            // Register the default admin if they don't exist
                            registerAdminInDatabase(email, password);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking admin existence", e);
                        onLoginFailure("Database error: " + e.getMessage());
                    });
        } else {
            // Standard check against Firestore for the original admin email
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