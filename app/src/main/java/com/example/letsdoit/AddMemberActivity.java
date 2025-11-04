// src/main/java/com/example/letsdoit/AddMemberActivity.java
package com.example.letsdoit;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;

public class AddMemberActivity extends AppCompatActivity {

    private EditText etMemberName, etMobileNumber, etAssignedEmail, etAssignedPassword;
    private Button btnSendInvite, btnBack;
    private FirebaseFirestore db;

    private static final String TAG = "AddMemberActivity";
    private static final String APP_LINK = "https://play.google.com/store/apps/details?id=com.example.letsdoit";
    // SMS_PERMISSION_CODE and related checks are no longer strictly needed for this approach, but are kept minimal.
    // However, for launching the intent, no permission is needed.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_member);

        db = FirebaseFirestore.getInstance();

        etMemberName = findViewById(R.id.et_member_name);
        etMobileNumber = findViewById(R.id.et_mobile_number);
        etAssignedEmail = findViewById(R.id.et_assigned_email);
        etAssignedPassword = findViewById(R.id.et_assigned_password);
        btnSendInvite = findViewById(R.id.btn_send_invite);
        btnBack = findViewById(R.id.btn_back);

        // No need to request SMS permission anymore.

        btnSendInvite.setOnClickListener(v -> addMemberAndPrepareSMS());
        btnBack.setOnClickListener(v -> finish());
    }

    // Removed requestSmsPermission and onRequestPermissionsResult as direct SMS sending is removed.

    private void addMemberAndPrepareSMS() {
        String memberName = etMemberName.getText().toString().trim();
        String mobileNumber = etMobileNumber.getText().toString().trim();
        String assignedEmail = etAssignedEmail.getText().toString().trim();
        String assignedPassword = etAssignedPassword.getText().toString().trim();

        // Validation
        if (memberName.isEmpty()) {
            etMemberName.setError("Name is required");
            return;
        }

        if (mobileNumber.isEmpty()) {
            etMobileNumber.setError("Mobile number is required");
            return;
        }

        // Format mobile number - ensure it has country code
        String formattedNumber = mobileNumber;
        if (!mobileNumber.startsWith("+")) {
            // If no country code, assume India and add +91 for better reliability in SMS app
            if (mobileNumber.length() == 10) {
                formattedNumber = "+91" + mobileNumber;
            } else {
                // For numbers not 10 digits and without a +, just prepend a + (less reliable)
                formattedNumber = "+" + mobileNumber;
            }
        }

        // Validate formatted number
        // The regex checks for a leading '+' followed by 10 to 15 digits
        if (!formattedNumber.matches("^\\+[0-9]{10,15}$")) {
            etMobileNumber.setError("Invalid mobile number format");
            Toast.makeText(this, "Use format: 9876543210 or +919876543210", Toast.LENGTH_LONG).show();
            return;
        }

        if (assignedEmail.isEmpty()) {
            etAssignedEmail.setError("Assigned email is required");
            return;
        }

        if (assignedPassword.isEmpty()) {
            etAssignedPassword.setError("Assigned password is required");
            return;
        }

        btnSendInvite.setEnabled(false);
        btnSendInvite.setText("Adding Member...");

        // First, add user to Firestore
        // Use formattedNumber as the SMS recipient, although it's not strictly saved.
        addUserToFirestore(memberName, assignedEmail, formattedNumber, assignedPassword);
    }

    private void addUserToFirestore(String memberName, String assignedEmail, String mobileNumber, String assignedPassword) {
        User newUser = new User(assignedEmail, "user", memberName);

        db.collection("users")
                .add(newUser)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "User added to Firestore successfully");
                    // Prepare and open the SMS Intent
                    prepareAndOpenSmsIntent(memberName, mobileNumber, assignedEmail, assignedPassword);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding user to Firestore", e);
                    Toast.makeText(this, "Failed to add user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSendInvite.setEnabled(true);
                    btnSendInvite.setText("Send SMS Invite");
                });
    }

    // NEW METHOD: Prepares the message and opens the native SMS app
    private void prepareAndOpenSmsIntent(String memberName, String mobileNumber, String assignedEmail, String assignedPassword) {
        String message = "Do It!\n\n" + // Mimic the style in the image
                "Login Details:\n" +
                "Email: " + assignedEmail + "\n" +
                "Password: " + assignedPassword + "\n\n" +
                "Download:\n" + APP_LINK + "\n\n" +
                "Keep credentials secure!";

        // Use Uri.encode to ensure message is correctly formatted for SMS intent
        Uri uri = Uri.parse("smsto:" + Uri.encode(mobileNumber));
        Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
        intent.putExtra("sms_body", message);

        try {
            startActivity(intent);
            Toast.makeText(this, "Invitation ready to send in messaging app!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error opening SMS intent", e);
            Toast.makeText(this, "Could not open messaging app. Please send manually.", Toast.LENGTH_LONG).show();
            // Optionally, copy message to clipboard here, but an app without SMS capability is rare.
        } finally {
            clearForm();
            btnSendInvite.setEnabled(true);
            btnSendInvite.setText("Send SMS Invite");
        }
    }

    // Removed the old sendSMS method.

    private void clearForm() {
        etMemberName.setText("");
        etMobileNumber.setText("");
        etAssignedEmail.setText("");
        etAssignedPassword.setText("");
    }
}