// app/src/main/java/com/example/letsdoit/WelcomeActivity.java
package com.example.letsdoit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class WelcomeActivity extends AppCompatActivity {

    private CardView btnLoginCard;
    private SharedPreferences sharedPreferences;

    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Check if user is already logged in
        if (sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)) {
            // User is logged in, skip welcome screen and go to MainActivity
            String email = sharedPreferences.getString(LoginActivity.KEY_EMAIL, null);
            String role = sharedPreferences.getString(LoginActivity.KEY_ROLE, null);
            String displayName = sharedPreferences.getString("displayName", null);

            if (email != null && role != null && displayName != null) {
                Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
                intent.putExtra(LoginActivity.EXTRA_DISPLAY_NAME, displayName);
                intent.putExtra(LoginActivity.EXTRA_USER_ROLE, role);
                intent.putExtra(LoginActivity.EXTRA_USER_EMAIL, email);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return;
            }
        }

        setContentView(R.layout.activity_welcome);

        btnLoginCard = findViewById(R.id.btn_login_card);

        btnLoginCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to LoginActivity
                Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
                startActivity(intent);

                // Optional: Add smooth transition
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }
}