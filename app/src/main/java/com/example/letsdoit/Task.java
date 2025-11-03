// src/main/java/com/example/letsdoit/Task.java
package com.example.letsdoit;

import java.util.ArrayList;
import java.util.List;

public class Task {
    private String id;
    private String title;
    private String description;
    private String priority;
    private List<String> fileUrls;
    private String remarks;
    private String assignedTo; // NEW FIELD
    private long timestamp;

    public Task() {
        // Required empty constructor for Firestore
    }

    // UPDATED CONSTRUCTOR
    public Task(String title, String description, String priority, List<String> fileUrls, String remarks, String assignedTo) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.fileUrls = fileUrls != null ? fileUrls : new ArrayList<>();
        this.remarks = remarks;
        this.assignedTo = assignedTo; // Initialize NEW FIELD
        this.timestamp = System.currentTimeMillis();
    }

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

    public List<String> getFileUrls() {
        return fileUrls;
    }

    public void setFileUrls(List<String> fileUrls) {
        this.fileUrls = fileUrls;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    // NEW GETTER AND SETTER
    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}