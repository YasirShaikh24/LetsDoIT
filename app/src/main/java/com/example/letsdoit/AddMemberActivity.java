// src/main/java/com/example/letsdoit/AddMemberActivity.java
package com.example.letsdoit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
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
    private static final int SMS_PERMISSION_CODE = 100;

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

        // Request SMS permission
        requestSmsPermission();

        btnSendInvite.setOnClickListener(v -> addMemberAndSendSMS());
        btnBack.setOnClickListener(v -> finish());
    }

    private void requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    SMS_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "SMS Permission Denied. Cannot send invitations.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void addMemberAndSendSMS() {
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
            // If no country code, assume India and add +91
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

        // Check SMS permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Please grant SMS permission first", Toast.LENGTH_LONG).show();
            requestSmsPermission();
            return;
        }

        btnSendInvite.setEnabled(false);
        btnSendInvite.setText("Sending...");

        // First, add user to Firestore
        // CRITICAL FIX: Use formattedNumber instead of mobileNumber
        addUserToFirestore(memberName, assignedEmail, formattedNumber, assignedPassword);
    }

    private void addUserToFirestore(String memberName, String assignedEmail, String mobileNumber, String assignedPassword) {
        User newUser = new User(assignedEmail, "user", memberName);

        db.collection("users")
                .add(newUser)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "User added to Firestore successfully");
                    // Send SMS in background thread to avoid UI blocking
                    // mobileNumber parameter here now correctly holds the formatted number
                    new Thread(() -> sendSMS(memberName, mobileNumber, assignedEmail, assignedPassword)).start();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding user to Firestore", e);
                    Toast.makeText(this, "Failed to add user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSendInvite.setEnabled(true);
                    btnSendInvite.setText("Send Invite");
                });
    }

    private void sendSMS(String memberName, String mobileNumber, String assignedEmail, String assignedPassword) {
        try {
            SmsManager smsManager = SmsManager.getDefault();

            // Log the number we're sending to for debugging
            Log.d(TAG, "Attempting to send SMS to: " + mobileNumber);

            // Create the message
            String message = "Welcome " + memberName + " to Let's Do It!\n\n" +
                    "Login Details:\n" +
                    "Email: " + assignedEmail + "\n" +
                    "Password: " + assignedPassword + "\n\n" +
                    "Download: " + APP_LINK + "\n\n" +
                    "Keep credentials secure!";

            // SMS has 160 character limit, so split if needed
            if (message.length() > 160) {
                // Split into multiple parts (divideMessage returns ArrayList<String>)
                java.util.ArrayList<String> parts = smsManager.divideMessage(message);
                Log.d(TAG, "Message split into " + parts.size() + " parts");
                smsManager.sendMultipartTextMessage(mobileNumber, null, parts, null, null);
            } else {
                Log.d(TAG, "Sending single SMS message");
                smsManager.sendTextMessage(mobileNumber, null, message, null, null);
            }

            Log.d(TAG, "SMS sent successfully to: " + mobileNumber);

            runOnUiThread(() -> {
                Toast.makeText(this, "SMS sent to " + mobileNumber, Toast.LENGTH_LONG).show();
                clearForm();
                btnSendInvite.setEnabled(true);
                btnSendInvite.setText("Send Invite");
            });

        } catch (Exception e) {
            Log.e(TAG, "Error sending SMS to " + mobileNumber, e);
            runOnUiThread(() -> {
                Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_LONG).show();
                btnSendInvite.setEnabled(true);
                btnSendInvite.setText("Send Invite");
            });
        }
    }

    private void clearForm() {
        etMemberName.setText("");
        etMobileNumber.setText("");
        etAssignedEmail.setText("");
        etAssignedPassword.setText("");
    }
}