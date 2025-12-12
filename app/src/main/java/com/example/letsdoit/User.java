package com.example.letsdoit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private String documentId; // NEW FIELD
    private String email;
    private String role; // "admin" or "user"
    private String displayName;
    private String password; // NEW FIELD for password storage
    private String mobileNumber; // ADDED FIELD

    public User() {
        // Required empty constructor for Firestore
    }

    // UPDATED Constructor (V1)
    public User(String email, String role, String displayName, String password) {
        this.email = email;
        this.role = role;
        this.displayName = displayName;
        this.password = password;
        this.mobileNumber = ""; // Initialize default
    }

    // UPDATED Constructor (V2 - Add mobile number)
    public User(String email, String role, String displayName, String password, String mobileNumber) {
        this.email = email;
        this.role = role;
        this.displayName = displayName;
        this.password = password;
        this.mobileNumber = mobileNumber;
    }

    // NEW Getter and Setter for documentId
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    // NEW Getter and Setter for password
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // NEW Getter and Setter for mobileNumber
    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }
}