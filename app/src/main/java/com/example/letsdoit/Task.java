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
    private String taskType; // NEW FIELD for Permanent/Additional

    // NEW: AI Count fields
    private boolean requireAiCount; // Radio button state (on/off)
    private String aiCountValue; // The alphanumeric AI count entered by user

    // NEW: Field for historical status tracking (completion only)
    private long completedDateMillis;

    // Field for backward compatibility (Firestore deserializes old single-string field here)
    private String assignedToEmail;

    private long timestamp;

    public Task() {
        // Required empty constructor for Firestore
        this.requireAiCount = false; // Default to OFF
        this.aiCountValue = "";
        this.taskType = "Permanent"; // Default for backward compatibility
        this.completedDateMillis = 0; // Initialize
    }

    // Constructor for creating NEW tasks - added taskType
    public Task(String title, String description, String priority, String remarks, List<String> assignedTo, String startDate, String endDate, boolean requireAiCount, String taskType) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.remarks = remarks;
        this.assignedTo = assignedTo != null ? assignedTo : new ArrayList<>();
        this.startDate = startDate;
        this.endDate = endDate;
        this.requireAiCount = requireAiCount;
        this.aiCountValue = "";
        this.timestamp = System.currentTimeMillis();
        this.status = "Pending"; // Initialize new tasks as "Pending"
        this.taskType = taskType; // Set the task type
        this.completedDateMillis = 0; // Initialize
    }

    // --- GETTERS & SETTERS ---

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

    public String getStatus() {
        return status != null ? status : "Pending";
    }

    public void setStatus(String status) {
        this.status = status;
    }

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

    public List<String> getAssignedTo() {
        if (assignedTo != null && !assignedTo.isEmpty()) {
            return assignedTo;
        } else if (assignedToEmail != null && !assignedToEmail.isEmpty()) {
            return Collections.singletonList(assignedToEmail);
        }
        return new ArrayList<>();
    }

    public void setAssignedTo(List<String> assignedTo) {
        this.assignedTo = assignedTo;
    }

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

    // NEW: AI Count Getters & Setters
    public boolean isRequireAiCount() {
        return requireAiCount;
    }

    public void setRequireAiCount(boolean requireAiCount) {
        this.requireAiCount = requireAiCount;
    }

    public String getAiCountValue() {
        return aiCountValue != null ? aiCountValue : "";
    }

    public void setAiCountValue(String aiCountValue) {
        this.aiCountValue = aiCountValue;
    }

    // NEW: Task Type Getters & Setters
    public String getTaskType() {
        return taskType != null ? taskType : "Permanent";
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    // NEW: Completed Date Getters & Setters
    public long getCompletedDateMillis() {
        return completedDateMillis;
    }

    public void setCompletedDateMillis(long completedDateMillis) {
        this.completedDateMillis = completedDateMillis;
    }
}