// src/main/java/com/example/letsdoit/User.java
package com.example.letsdoit;

public class User {
    private String documentId; // NEW FIELD
    private String email;
    private String role; // "admin" or "user"
    private String displayName;
    private String password; // NEW FIELD for password storage

    public User() {
        // Required empty constructor for Firestore
    }

    // UPDATED Constructor
    public User(String email, String role, String displayName, String password) {
        this.email = email;
        this.role = role;
        this.displayName = displayName;
        this.password = password;
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
}