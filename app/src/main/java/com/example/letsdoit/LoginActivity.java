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
    private ImageButton btnBack;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;

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
        btnBack = findViewById(R.id.btn_back);

        // etFullname views are now hidden in XML (visibility="gone")

        btnLogin.setOnClickListener(v -> attemptLogin());

        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
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
            verifyAdminLogin(email, password);
        } else {
            verifyUserLogin(email, password);
        }
    }

    private void verifyAdminLogin(String email, String password) {
        if (password.equals(ADMIN_PASSWORD)) {
            db.collection("admins")
                    .whereEqualTo("email", email)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                            User admin = doc.toObject(User.class);
                            if (admin != null) {
                                String dbPassword = admin.getPassword();

                                if (dbPassword != null && !dbPassword.isEmpty()) {
                                    updateAdminPassword(doc.getReference().getId(), password, admin);
                                } else {
                                    updateAdminPassword(doc.getReference().getId(), password, admin);
                                }
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
                        Log.e(TAG, "Error checking admin existence", e);
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