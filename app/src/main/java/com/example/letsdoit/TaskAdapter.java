// src/main/java/com/example/letsdoit/TaskAdapter.java
package com.example.letsdoit;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    public interface TaskActionListener {
        void onTaskStatusClick(Task task, int position);
        void onTaskEditClick(Task task, int position);
        void onTaskDeleteClick(Task task, int position);
        void onAdminTaskClick(Task task, int position); // NEW: For admin click on done tasks
    }

    private List<Task> taskList;
    private Context context;
    private TaskActionListener listener;
    private String loggedInUserRole;
    private String loggedInUserEmail;

    private Map<String, String> userDisplayNameMap = new java.util.HashMap<>();

    public TaskAdapter(List<Task> taskList, Context context, TaskActionListener listener, String loggedInUserRole, String loggedInUserEmail) {
        this.taskList = taskList;
        this.context = context;
        this.listener = listener;
        this.loggedInUserRole = loggedInUserRole;
        this.loggedInUserEmail = loggedInUserEmail;
    }

    public void setUserDisplayNameMap(Map<String, String> map) {
        this.userDisplayNameMap = map;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = taskList.get(position);

        // --- 1. Title and Description ---
        holder.tvTitle.setText(task.getTitle() != null ? task.getTitle() : "No Title");
        holder.tvDescription.setText(task.getDescription() != null ? task.getDescription() : "No Description");
        holder.tvDescription.setVisibility(View.VISIBLE);

        // Hide priority stub
        holder.tvPriority.setVisibility(View.GONE);

        // --- Determine Status, Completion Time, and AI Count Source ---
        String effectiveStatus = task.getStatus();
        String aiCountValueForDisplay;
        String completedUserEmail = null;

        if ("admin".equals(loggedInUserRole)) {
            aiCountValueForDisplay = task.getAiCountValue();

            if (effectiveStatus != null && effectiveStatus.equalsIgnoreCase("Completed")) {
                long globalCompletedDate = task.getCompletedDateMillis();
                String globalAiCount = task.getAiCountValue();

                for (Map.Entry<String, Long> entry : task.getUserCompletedDate().entrySet()) {
                    if (entry.getValue().equals(globalCompletedDate)) {
                        String userAiCount = task.getUserAiCount().getOrDefault(entry.getKey(), "");
                        if (userAiCount.equals(globalAiCount)) {
                            completedUserEmail = entry.getKey();
                            break;
                        }
                    }
                }
            }
        } else {
            aiCountValueForDisplay = task.getUserAiCount(loggedInUserEmail);
            completedUserEmail = loggedInUserEmail;
        }

        // --- 2. Status Tag ---
        String taskStatus = effectiveStatus != null ? effectiveStatus.toLowerCase() : "pending";
        int statusColor;
        String statusDisplay;

        if (taskStatus.equals("completed")) {
            statusColor = Color.parseColor("#66BB6A");
            statusDisplay = "DONE";
        } else {
            statusColor = Color.parseColor("#FFA726");
            statusDisplay = "NOT DONE";
        }

        holder.tvStatus.setText(statusDisplay);
        holder.tvStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(statusColor));
        holder.tvStatus.setVisibility(View.VISIBLE);

        // --- 3. AI Count Section ---
        if (task.isRequireAiCount()) {
            holder.cardAiCount.setVisibility(View.VISIBLE);

            if (effectiveStatus.equalsIgnoreCase("Completed")) {
                String displayName = userDisplayNameMap.getOrDefault(completedUserEmail, "a team member");

                String aiCountText = "🔢 AI Count: " + (aiCountValueForDisplay != null && !aiCountValueForDisplay.isEmpty() ? aiCountValueForDisplay : "N/A");

                if ("admin".equals(loggedInUserRole) && task.getTaskType().equalsIgnoreCase("Permanent") && completedUserEmail != null) {
                    aiCountText += " (by " + displayName + ")";
                }

                holder.tvAiCount.setText(aiCountText);
                holder.cardAiCount.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                holder.tvAiCount.setTextColor(Color.parseColor("#2E7D32"));
            } else {
                holder.tvAiCount.setText("🔢 AI Count: Required (Not Submitted)");
                holder.cardAiCount.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                holder.tvAiCount.setTextColor(Color.parseColor("#E65100"));
            }

        } else {
            holder.cardAiCount.setVisibility(View.GONE);
        }

        // --- 4. Date Range Section ---
        if (task.getTaskType() != null && task.getTaskType().equalsIgnoreCase("additional")) {
            String startDate = task.getStartDate();
            String endDate = task.getEndDate();

            if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
                holder.layoutDateRange.setVisibility(View.VISIBLE);
                String dateRangeText = "";
                dateRangeText += (startDate != null && !startDate.isEmpty()) ? startDate : "?";
                dateRangeText += " - ";
                dateRangeText += (endDate != null && !endDate.isEmpty()) ? endDate : "?";
                holder.tvDateRange.setText(dateRangeText);
            } else {
                holder.layoutDateRange.setVisibility(View.GONE);
            }
        } else {
            holder.layoutDateRange.setVisibility(View.GONE);
        }

        // --- 5. Remarks, Timestamp, Files ---
        String remarks = task.getRemarks();
        if (remarks != null && !remarks.isEmpty()) {
            holder.tvRemarks.setVisibility(View.VISIBLE);
            holder.tvRemarks.setText("📝 " + remarks);
        } else {
            holder.tvRemarks.setVisibility(View.GONE);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.tvTimestamp.setText(sdf.format(new Date(task.getTimestamp())));

        holder.tvFiles.setVisibility(View.GONE);

        // --- 6. Admin Actions ---
        if ("admin".equals(loggedInUserRole)) {
            holder.llAdminActions.setVisibility(View.VISIBLE);

            holder.btnEditTask.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskEditClick(task, position);
                }
            });

            holder.btnDeleteTask.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskDeleteClick(task, position);
                }
            });
        } else {
            holder.llAdminActions.setVisibility(View.GONE);
        }

        // --- 7. Card Click Logic ---
        if ("user".equals(loggedInUserRole)) {
            holder.cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskStatusClick(task, position);
                }
            });
            holder.cardView.setClickable(true);
            holder.cardView.setFocusable(true);
        } else {
            // UPDATED: Admin can click on DONE tasks to see who completed them
            if (taskStatus.equals("completed")) {
                holder.cardView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAdminTaskClick(task, position);
                    }
                });
                holder.cardView.setClickable(true);
                holder.cardView.setFocusable(true);
            } else {
                holder.cardView.setOnClickListener(null);
                holder.cardView.setClickable(false);
                holder.cardView.setFocusable(false);
            }
        }
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvTitle, tvDescription, tvPriority, tvStatus;
        TextView tvTimestamp, tvFiles, tvRemarks, tvDateRange, tvAiCount;
        CardView cardAiCount;
        LinearLayout layoutDateRange;
        ImageButton btnEditTask, btnDeleteTask;
        LinearLayout llAdminActions;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            tvTitle = itemView.findViewById(R.id.tv_task_title);
            tvDescription = itemView.findViewById(R.id.tv_task_description);
            tvPriority = itemView.findViewById(R.id.tv_task_priority);
            tvStatus = itemView.findViewById(R.id.tv_task_status);
            tvTimestamp = itemView.findViewById(R.id.tv_task_timestamp);
            tvFiles = itemView.findViewById(R.id.tv_task_files);
            tvRemarks = itemView.findViewById(R.id.tv_task_remarks);
            tvDateRange = itemView.findViewById(R.id.tv_task_date_range);
            tvAiCount = itemView.findViewById(R.id.tv_task_ai_count);
            cardAiCount = itemView.findViewById(R.id.card_ai_count);
            layoutDateRange = itemView.findViewById(R.id.layout_date_range);

            llAdminActions = itemView.findViewById(R.id.ll_admin_actions);
            btnEditTask = itemView.findViewById(R.id.btn_edit_task);
            btnDeleteTask = itemView.findViewById(R.id.btn_delete_task);
        }
    }
}