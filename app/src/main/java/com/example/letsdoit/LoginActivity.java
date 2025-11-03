// src/main/java/com/example/letsdoit/LoginActivity.java
package com.example.letsdoit;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private Button btnLogin;
    private FirebaseFirestore db;

    private static final String TAG = "LoginActivity";
    private static final String ADMIN_EMAIL = "mahimhussain444@gmail.com";

    public static final String EXTRA_DISPLAY_NAME = "extra_display_name";
    public static final String EXTRA_USER_ROLE = "extra_user_role";
    public static final String EXTRA_USER_EMAIL = "extra_user_email";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);

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
            checkAdminInDatabase(email);
        } else {
            checkUserInDatabase(email);
        }
    }

    private void checkAdminInDatabase(String email) {
        db.collection("admins")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        // Admin exists
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        User admin = doc.toObject(User.class);
                        if (admin != null) {
                            onLoginSuccess(admin.getEmail(), admin.getRole(), admin.getDisplayName());
                        }
                    } else {
                        // Admin doesn't exist, register them
                        registerNewAdmin(email);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking admin existence", e);
                    onLoginFailure("Database error: " + e.getMessage());
                });
    }

    private void checkUserInDatabase(String email) {
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        // User exists
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            onLoginSuccess(user.getEmail(), user.getRole(), user.getDisplayName());
                        }
                    } else {
                        // User doesn't exist, register them
                        registerNewUser(email);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking user existence", e);
                    onLoginFailure("Database error: " + e.getMessage());
                });
    }

    private void registerNewAdmin(String email) {
        User newAdmin = new User(email, "admin", "Admin");

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

    private void registerNewUser(String email) {
        String displayName = email.split("@")[0];
        User newUser = new User(email, "user", displayName);

        db.collection("users")
                .add(newUser)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "User registered successfully");
                    onLoginSuccess(newUser.getEmail(), newUser.getRole(), newUser.getDisplayName());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error registering user", e);
                    onLoginFailure("User registration failed: " + e.getMessage());
                });
    }

    private void onLoginSuccess(String email, String role, String displayName) {
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