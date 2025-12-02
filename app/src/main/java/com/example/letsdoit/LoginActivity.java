// src/main/java/com/example/letsdoit/LoginActivity.java
package com.example.letsdoit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private Button btnLogin;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;

    private static final String TAG = "LoginActivity";
    private static final String ADMIN_EMAIL = "mahimhussain444@gmail.com";
    private static final String ADMIN_PASSWORD = "Mahim11"; // Admin password

    public static final String EXTRA_DISPLAY_NAME = "extra_display_name";
    public static final String EXTRA_USER_ROLE = "extra_user_role";
    public static final String EXTRA_USER_EMAIL = "extra_user_email";

    // SharedPreferences Constants
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROLE = "role";
    private static final String KEY_DISPLAY_NAME = "displayName";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // CHECK FOR SAVED SESSION AND AUTO-LOGIN
        if (sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)) {
            String email = sharedPreferences.getString(KEY_EMAIL, null);
            String role = sharedPreferences.getString(KEY_ROLE, null);
            String displayName = sharedPreferences.getString(KEY_DISPLAY_NAME, null);

            if (email != null && role != null && displayName != null) {
                // Bypass login and go directly to MainActivity
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

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);

        // Removed manual email pre-fill to allow for platform-level autofill (password manager)

        btnLogin.setOnClickListener(v -> attemptLogin());
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

        // Check if admin first
        if (email.equals(ADMIN_EMAIL)) {
            verifyAdminLogin(email, password);
        } else {
            verifyUserLogin(email, password);
        }
    }

    private void verifyAdminLogin(String email, String password) {
        // First check hardcoded admin credentials
        if (password.equals(ADMIN_PASSWORD)) {
            // Check if admin exists in database
            db.collection("admins")
                    .whereEqualTo("email", email)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            // Admin exists in database
                            DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                            User admin = doc.toObject(User.class);
                            if (admin != null) {
                                String dbPassword = admin.getPassword();

                                // If password field exists in database, verify it
                                if (dbPassword != null && !dbPassword.isEmpty()) {
                                    if (dbPassword.equals(password)) {
                                        onLoginSuccess(admin.getEmail(), admin.getRole(), admin.getDisplayName());
                                    } else {
                                        onLoginFailure("Invalid admin credentials");
                                    }
                                } else {
                                    // Password field missing in database, update it
                                    Log.d(TAG, "Password field missing, updating admin document");
                                    updateAdminPassword(doc.getReference().getId(), password, admin);
                                }
                            }
                        } else {
                            // Admin doesn't exist in database, create admin account
                            registerAdminInDatabase(email, password);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking admin existence", e);
                        onLoginFailure("Database error: " + e.getMessage());
                    });
        } else {
            onLoginFailure("Invalid admin credentials");
        }
    }

    private void updateAdminPassword(String documentId, String password, User admin) {
        db.collection("admins")
                .document(documentId)
                .update("password", password)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Admin password updated successfully");
                    onLoginSuccess(admin.getEmail(), admin.getRole(), admin.getDisplayName());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating admin password", e);
                    // Still allow login since hardcoded password matched
                    onLoginSuccess(admin.getEmail(), admin.getRole(), admin.getDisplayName());
                });
    }

    private void registerAdminInDatabase(String email, String password) {
        User newAdmin = new User(email, "admin", "Admin", password);

        db.collection("admins")
                .add(newAdmin)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Admin registered successfully");
                    onLoginSuccess(newAdmin.getEmail(), newAdmin.getRole(), newAdmin.getDisplayName());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error registering admin", e);
                    onLoginFailure("Admin registration failed: " + e.getMessage());
                });
    }

    private void verifyUserLogin(String email, String password) {
        // Query users collection for exact email match
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        // User found with this email
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        User user = doc.toObject(User.class);

                        if (user != null) {
                            // Verify password matches exactly
                            String storedPassword = user.getPassword();
                            if (storedPassword != null && storedPassword.equals(password)) {
                                // Credentials match - allow login
                                onLoginSuccess(user.getEmail(), user.getRole(), user.getDisplayName());
                            } else {
                                // Password doesn't match
                                onLoginFailure("Invalid email or password");
                            }
                        } else {
                            onLoginFailure("User data error");
                        }
                    } else {
                        // No user found with this email
                        onLoginFailure("Invalid email or password. Contact admin to get access.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking user credentials", e);
                    onLoginFailure("Login failed: " + e.getMessage());
                });
    }

    private void onLoginSuccess(String email, String role, String displayName) {
        // SAVE LOGIN SESSION
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_ROLE, role);
        editor.putString(KEY_DISPLAY_NAME, displayName);
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