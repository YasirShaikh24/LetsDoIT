// src/main/java/com/example/letsdoit/Task.java
package com.example.letsdoit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Task {
    private String id;
    private String title;
    private String description;
    private String priority;
    private String status; // Keep for backward compatibility

    private List<String> assignedTo;

    // NEW: Per-user status tracking
    // Map: email -> status (Pending/In Progress/Completed)
    private Map<String, String> userStatus;

    // NEW: Per-user AI count tracking
    // Map: email -> aiCountValue
    private Map<String, String> userAiCount;

    // NEW: Per-user completion time tracking
    // Map: email -> completedDateMillis
    private Map<String, Long> userCompletedDate;

    private String remarks;
    private String startDate;
    private String endDate;
    private String taskType;

    // NEW FIELD: Stores selected days (e.g., ["mon", "wed", "fri"])
    private List<String> selectedDays;

    private boolean requireAiCount;
    private String aiCountValue; // Keep for backward compatibility

    private long completedDateMillis; // Keep for backward compatibility
    private String assignedToEmail; // Keep for backward compatibility

    private long timestamp;

    public Task() {
        this.requireAiCount = false;
        this.aiCountValue = "";
        this.taskType = "Permanent";
        this.completedDateMillis = 0;
        this.userStatus = new HashMap<>();
        this.userAiCount = new HashMap<>();
        this.userCompletedDate = new HashMap<>();
        this.selectedDays = new ArrayList<>(); // Initialize new field
    }

    public Task(String title, String description, String priority, String remarks,
                List<String> assignedTo, String startDate, String endDate,
                boolean requireAiCount, String taskType) {
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
        this.status = "Pending";
        this.taskType = taskType;
        this.completedDateMillis = 0;

        // Initialize per-user status tracking
        this.userStatus = new HashMap<>();
        this.userAiCount = new HashMap<>();
        this.userCompletedDate = new HashMap<>();
        this.selectedDays = new ArrayList<>(); // Initialize new field

        // Set initial status for all assigned users
        if (assignedTo != null) {
            for (String email : assignedTo) {
                this.userStatus.put(email, "Pending");
                this.userAiCount.put(email, "");
                this.userCompletedDate.put(email, 0L);
            }
        }
    }

    // NEW CONSTRUCTOR for the new logic
    public Task(String title, String description, String priority, String remarks,
                List<String> assignedTo, String startDate, String endDate,
                boolean requireAiCount, String taskType, List<String> selectedDays) {
        this(title, description, priority, remarks, assignedTo, startDate, endDate, requireAiCount, taskType);
        this.selectedDays = selectedDays != null ? selectedDays : new ArrayList<>();
    }


    // --- NEW GETTER & SETTER for selectedDays ---
    public List<String> getSelectedDays() {
        return selectedDays != null ? selectedDays : new ArrayList<>();
    }

    public void setSelectedDays(List<String> selectedDays) {
        this.selectedDays = selectedDays;
    }


    // --- NEW METHODS FOR PER-USER STATUS ---

    public String getUserStatus(String userEmail) {
        if (userStatus != null && userStatus.containsKey(userEmail)) {
            return userStatus.get(userEmail);
        }
        return "Pending"; // Default if not found
    }

    public void setUserStatus(String userEmail, String status) {
        if (userStatus == null) {
            userStatus = new HashMap<>();
        }
        userStatus.put(userEmail, status);
    }

    public String getUserAiCount(String userEmail) {
        if (userAiCount != null && userAiCount.containsKey(userEmail)) {
            return userAiCount.get(userEmail);
        }
        return "";
    }

    public void setUserAiCount(String userEmail, String aiCount) {
        if (userAiCount == null) {
            userAiCount = new HashMap<>();
        }
        userAiCount.put(userEmail, aiCount);
    }

    public long getUserCompletedDate(String userEmail) {
        if (userCompletedDate != null && userCompletedDate.containsKey(userEmail)) {
            Long date = userCompletedDate.get(userEmail);
            return date != null ? date : 0L;
        }
        return 0L;
    }

    public void setUserCompletedDate(String userEmail, long completedDate) {
        if (userCompletedDate == null) {
            userCompletedDate = new HashMap<>();
        }
        userCompletedDate.put(userEmail, completedDate);
    }

    // Getters and setters for the Maps
    public Map<String, String> getUserStatus() {
        return userStatus != null ? userStatus : new HashMap<>();
    }

    public void setUserStatus(Map<String, String> userStatus) {
        this.userStatus = userStatus;
    }

    public Map<String, String> getUserAiCount() {
        return userAiCount != null ? userAiCount : new HashMap<>();
    }

    public void setUserAiCount(Map<String, String> userAiCount) {
        this.userAiCount = userAiCount;
    }

    public Map<String, Long> getUserCompletedDate() {
        return userCompletedDate != null ? userCompletedDate : new HashMap<>();
    }

    public void setUserCompletedDate(Map<String, Long> userCompletedDate) {
        this.userCompletedDate = userCompletedDate;
    }

    // --- EXISTING GETTERS & SETTERS (keep for compatibility) ---

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

    public String getTaskType() {
        return taskType != null ? taskType : "Permanent";
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public long getCompletedDateMillis() {
        return completedDateMillis;
    }

    public void setCompletedDateMillis(long completedDateMillis) {
        this.completedDateMillis = completedDateMillis;
    }
}