// src/main/java/com/example/letsdoit/Task.java
package com.example.letsdoit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Task {
    private String id;
    private String title;
    private String description;
    private String priority;
    private String status;

    // List fields - fileUrls removed
    private List<String> assignedTo;

    // String fields
    private String remarks;
    private String startDate;
    private String endDate;

    // Field for backward compatibility (Firestore deserializes old single-string field here)
    private String assignedToEmail;

    private long timestamp;

    public Task() {
        // Required empty constructor for Firestore
    }

    // Constructor for creating NEW tasks - fileUrls argument removed
    public Task(String title, String description, String priority, String remarks, List<String> assignedTo, String startDate, String endDate) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.remarks = remarks;
        this.assignedTo = assignedTo != null ? assignedTo : new ArrayList<>();
        this.startDate = startDate;
        this.endDate = endDate;
        this.timestamp = System.currentTimeMillis();
        this.status = "Pending"; // Initialize new tasks as "Pending"
    }

    // --- NEW GETTER & SETTER for Status ---
    public String getStatus() {
        // Default to "Pending" for old tasks without a status field
        return status != null ? status : "Pending";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // --- GETTERS & SETTERS (Existing below) ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    // CRITICAL: Always return non-null list for fileUrls - MODIFIED to return empty list
    public List<String> getFileUrls() {
        return new ArrayList<>();
    }

    public void setFileUrls(List<String> fileUrls) {
        // Method kept for Firestore backward compatibility but does nothing
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    // **CRITICAL FIX: Robust Getter for assignedTo**
    public List<String> getAssignedTo() {
        if (assignedTo != null && !assignedTo.isEmpty()) {
            // New format (List)
            return assignedTo;
        } else if (assignedToEmail != null && !assignedToEmail.isEmpty()) {
            // Old format (String) - convert and return as a single-element list
            return Collections.singletonList(assignedToEmail);
        }
        return new ArrayList<>(); // Always return empty list if both are null/empty
    }

    public void setAssignedTo(List<String> assignedTo) {
        this.assignedTo = assignedTo;
    }

    // TEMPORARY SETTER: Used by Firestore to populate old documents
    public void setAssignedToEmail(String assignedToEmail) {
        this.assignedToEmail = assignedToEmail;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}