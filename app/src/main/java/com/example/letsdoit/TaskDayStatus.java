package com.example.letsdoit;

public class TaskDayStatus {

    private String dateKey;       // "yyyy-MM-dd"
    private String status;        // "Pending" or "Completed"
    private String aiCountValue;  // per day
    private long completedAt;     // millis when completed, 0 if pending

    public TaskDayStatus() {
        // Required empty constructor for Firestore
    }

    public TaskDayStatus(String dateKey, String status, String aiCountValue, long completedAt) {
        this.dateKey = dateKey;
        this.status = status;
        this.aiCountValue = aiCountValue;
        this.completedAt = completedAt;
    }

    public String getDateKey() {
        return dateKey;
    }

    public void setDateKey(String dateKey) {
        this.dateKey = dateKey;
    }

    public String getStatus() {
        return status != null ? status : "Pending";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAiCountValue() {
        return aiCountValue != null ? aiCountValue : "";
    }

    public void setAiCountValue(String aiCountValue) {
        this.aiCountValue = aiCountValue;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }
}
