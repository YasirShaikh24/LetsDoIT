// src/main/java/com/example/letsdoit/User.java
package com.example.letsdoit;

public class User {
    private String email;
    private String role; // "admin" or "user"
    private String displayName;

    public User() {
        // Required empty constructor for Firestore
    }

    public User(String email, String role, String displayName) {
        this.email = email;
        this.role = role;
        this.displayName = displayName;
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
}