// src/main/java/com/example/letsdoit/LoginActivity.java
package com.example.letsdoit;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.TaskCompletionSource; // New Import
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot; // New Import
import com.google.firebase.firestore.FirebaseFirestore; // New Import
import com.google.firebase.firestore.QuerySnapshot; // New Import

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword; // NOTE: Password is not stored/checked in this mock setup.
    private Button btnLogin;
    private FirebaseFirestore db; // Firestore instance

    private static final String TAG = "LoginActivity";
    private static final String ADMIN_EMAIL = "mahimhussain444@gmail.com";

    // Keys for Intent Extras (moved from static fields to class body)
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

        // NOTE: Since this is a mock login without Firebase Authentication,
        // we skip password validation but enforce non-empty fields.

        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            return;
        }

        // Disable button during network call
        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        checkUserInDatabase(email);
    }

    private void checkUserInDatabase(String email) {
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        // User exists, retrieve their data
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            onLoginSuccess(user.getEmail(), user.getRole(), user.getDisplayName());
                        } else {
                            onLoginFailure("User data format error.");
                        }
                    } else {
                        // User does not exist, register them
                        registerNewUser(email);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking user existence", e);
                    onLoginFailure("Database error during login: " + e.getMessage());
                });
    }

    // Automatic registration logic
    private void registerNewUser(String email) {
        String role;
        String displayName;

        if (email.equals(ADMIN_EMAIL)) {
            role = "admin";
            displayName = "Admin";
        } else {
            // All other new users are regular users
            role = "user";
            displayName = email.split("@")[0]; // Use part before @ as display name
        }

        User newUser = new User(email, role, displayName);

        db.collection("users")
                .add(newUser)
                .addOnSuccessListener(documentReference -> {
                    onLoginSuccess(newUser.getEmail(), newUser.getRole(), newUser.getDisplayName());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error registering new user", e);
                    onLoginFailure("Registration failed: " + e.getMessage());
                });
    }


    private void onLoginSuccess(String email, String role, String displayName) {
        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
        btnLogin.setEnabled(true);
        btnLogin.setText("Login");

        // Navigate to MainActivity and pass user data
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