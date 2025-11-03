// src/main/java/com/example/letsdoit/LoginActivity.java
package com.example.letsdoit;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private Button btnLogin;

    // Constants for the two specific users
    private static final String ADMIN_EMAIL = "mahimhussain444@gmail.com";
    private static final String ADMIN_PASS = "Mahim11";
    private static final String ADMIN_DISPLAY_NAME = "Admin";
    private static final String USER_EMAIL = "yasirshaikh5440@gmail.com";
    private static final String USER_PASS = "yasir24";
    private static final String USER_DISPLAY_NAME = "Yasir";

    // Keys for Intent Extras
    public static final String EXTRA_DISPLAY_NAME = "extra_display_name";
    public static final String EXTRA_USER_ROLE = "extra_user_role";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

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

        String displayName = null;
        String userRole = null;

        // --- SPECIFIC LOGIN LOGIC ---
        if (email.equals(ADMIN_EMAIL) && password.equals(ADMIN_PASS)) {
            displayName = ADMIN_DISPLAY_NAME;
            userRole = "admin";
        } else if (email.equals(USER_EMAIL) && password.equals(USER_PASS)) {
            displayName = USER_DISPLAY_NAME;
            userRole = "user";
        }
        // --------------------------

        if (displayName != null) {
            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();

            // Navigate to MainActivity and pass user data
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra(EXTRA_DISPLAY_NAME, displayName);
            intent.putExtra(EXTRA_USER_ROLE, userRole);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show();
        }
    }
}