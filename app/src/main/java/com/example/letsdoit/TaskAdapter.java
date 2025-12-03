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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    public interface TaskActionListener {
        void onTaskStatusClick(Task task, int position);
        void onTaskEditClick(Task task, int position);
        void onTaskDeleteClick(Task task, int position);
    }

    private List<Task> taskList;
    private Context context;
    private TaskActionListener listener;
    private String loggedInUserRole;
    private String loggedInUserEmail;

    public TaskAdapter(List<Task> taskList, Context context, TaskActionListener listener, String loggedInUserRole, String loggedInUserEmail) {
        this.taskList = taskList;
        this.context = context;
        this.listener = listener;
        this.loggedInUserRole = loggedInUserRole;
        this.loggedInUserEmail = loggedInUserEmail;
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

        // Title and Description
        holder.tvTitle.setText(task.getTitle() != null ? task.getTitle() : "No Title");
        holder.tvDescription.setText(task.getDescription() != null ? task.getDescription() : "No Description");

        // Priority
        String priority = task.getPriority() != null ? task.getPriority().toLowerCase() : "low";
        int priorityColor;
        String priorityText;

        switch (priority) {
            case "high":
                priorityColor = Color.parseColor("#EF5350");
                priorityText = "HIGH";
                break;
            case "medium":
                priorityColor = Color.parseColor("#FFA726");
                priorityText = "MEDIUM";
                break;
            case "low":
            default:
                priorityColor = Color.parseColor("#66BB6A");
                priorityText = "LOW";
                break;
        }

        holder.tvPriority.setText(priorityText);
        holder.tvPriority.setBackgroundTintList(android.content.res.ColorStateList.valueOf(priorityColor));
        holder.priorityStrip.setBackgroundColor(priorityColor);

        // ADMIN VIEW: Show statistics instead of single status
        if ("admin".equals(loggedInUserRole)) {
            holder.tvStatus.setVisibility(View.GONE);
            holder.llStatusStats.setVisibility(View.VISIBLE);

            // Calculate statistics
            int assigned = task.getAssignedTo().size();
            int completed = 0;
            int inProgress = 0;
            int pending = 0;

            Map<String, String> userStatusMap = task.getUserStatus();
            for (String email : task.getAssignedTo()) {
                String userStatus = userStatusMap.get(email);
                if (userStatus == null) userStatus = "Pending";

                switch (userStatus.toLowerCase()) {
                    case "completed":
                        completed++;
                        break;
                    case "in progress":
                        inProgress++;
                        break;
                    case "pending":
                    default:
                        pending++;
                        break;
                }
            }

            // Display statistics
            holder.tvAssignedStat.setText("Assigned: " + assigned);
            holder.tvCompletedStat.setText("Completed: " + completed);
            holder.tvInProgressStat.setText("In Progress: " + inProgress);
            holder.tvPendingStat.setText("Pending: " + pending);

        } else {
            // USER VIEW: Show single status
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.llStatusStats.setVisibility(View.GONE);

            String status = task.getStatus().toLowerCase();
            int statusColor;
            String statusDisplay;

            switch (status) {
                case "pending":
                    statusColor = Color.parseColor("#FFA726");
                    statusDisplay = "PENDING";
                    break;
                case "in progress":
                    statusColor = Color.parseColor("#42A5F5");
                    statusDisplay = "IN PROGRESS";
                    break;
                case "completed":
                    statusColor = Color.parseColor("#66BB6A");
                    statusDisplay = "COMPLETED";
                    break;
                default:
                    statusColor = Color.parseColor("#9E9E9E");
                    statusDisplay = "UNKNOWN";
            }

            holder.tvStatus.setText(statusDisplay);
            holder.tvStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(statusColor));
        }

        // --- AI Count Section ---
        if (task.isRequireAiCount()) {
            holder.cardAiCount.setVisibility(View.VISIBLE);

            if ("user".equals(loggedInUserRole)) {
                // USER VIEW: Show their own AI count
                String aiCountValue = task.getUserAiCount(loggedInUserEmail);
                String userStatus = task.getUserStatus(loggedInUserEmail);

                if (aiCountValue != null && !aiCountValue.isEmpty()) {
                    holder.tvAiCount.setText("箸 AI Count: " + aiCountValue);
                    holder.cardAiCount.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                    holder.tvAiCount.setTextColor(Color.parseColor("#2E7D32"));
                } else if (userStatus != null && userStatus.equalsIgnoreCase("Completed")) {
                    // Task is completed but AI count is missing (shouldn't happen, but handle gracefully)
                    holder.tvAiCount.setText("箸 AI Count: Not recorded");
                    holder.cardAiCount.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                    holder.tvAiCount.setTextColor(Color.parseColor("#E65100"));
                } else {
                    holder.tvAiCount.setText("箸 AI Count: Required (Not submitted)");
                    holder.cardAiCount.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                    holder.tvAiCount.setTextColor(Color.parseColor("#E65100"));
                }
            } else {
                // ADMIN VIEW: Show summary of all users' AI counts
                Map<String, String> userAiCountMap = task.getUserAiCount();
                int submitted = 0;
                int required = task.getAssignedTo().size();

                for (String email : task.getAssignedTo()) {
                    String aiCount = userAiCountMap.get(email);
                    if (aiCount != null && !aiCount.isEmpty()) {
                        submitted++;
                    }
                }

                holder.tvAiCount.setText("箸 AI Count: " + submitted + "/" + required + " submitted");
                if (submitted == required) {
                    holder.cardAiCount.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                    holder.tvAiCount.setTextColor(Color.parseColor("#2E7D32"));
                } else {
                    holder.cardAiCount.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                    holder.tvAiCount.setTextColor(Color.parseColor("#E65100"));
                }
            }
        } else {
            // Task does not require AI Count
            holder.cardAiCount.setVisibility(View.GONE);
        }

        // --- Date Range Section (Moved after AI Count) ---
        if (task.getTaskType() != null && task.getTaskType().equalsIgnoreCase("additional")) {
            String startDate = task.getStartDate();
            String endDate = task.getEndDate();

            if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
                holder.layoutDateRange.setVisibility(View.VISIBLE);
                String dateRangeText = "";
                dateRangeText += (startDate != null && !startDate.isEmpty()) ? startDate : "?";
                dateRangeText += " 竊";
                dateRangeText += (endDate != null && !endDate.isEmpty()) ? endDate : "?";
                holder.tvDateRange.setText(dateRangeText);
            } else {
                holder.layoutDateRange.setVisibility(View.GONE);
            }
        } else {
            holder.layoutDateRange.setVisibility(View.GONE);
        }

        // Remarks
        String remarks = task.getRemarks();
        if (remarks != null && !remarks.isEmpty()) {
            holder.tvRemarks.setVisibility(View.VISIBLE);
            holder.tvRemarks.setText("統 " + remarks);
        } else {
            holder.tvRemarks.setVisibility(View.GONE);
        }

        // Timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.tvTimestamp.setText(sdf.format(new Date(task.getTimestamp())));

        // Files
        if (!task.getFileUrls().isEmpty()) {
            holder.tvFiles.setVisibility(View.VISIBLE);
            holder.tvFiles.setText("📎 " + task.getFileUrls().size());
        } else {
            holder.tvFiles.setVisibility(View.GONE);
        }

        // Admin Actions (Edit/Delete Buttons)
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

        // *** FIX: Card Click Logic - Enable for both Admin and User ***
        // Admin needs to click to open the Task Review Dialog (handled in ViewActivityFragment.java)
        // User needs to click to open the Task Detail/Update Dialog (handled in ViewActivityFragment.java)
        if ("user".equals(loggedInUserRole) || "admin".equals(loggedInUserRole)) {
            holder.cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskStatusClick(task, position);
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

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        View priorityStrip;
        TextView tvTitle, tvDescription, tvPriority, tvStatus;
        TextView tvTimestamp, tvFiles, tvRemarks, tvDateRange, tvAiCount;
        CardView cardAiCount;
        LinearLayout layoutDateRange;
        ImageButton btnEditTask, btnDeleteTask;
        LinearLayout llAdminActions;

        // Admin statistics views
        LinearLayout llStatusStats;
        TextView tvAssignedStat, tvCompletedStat, tvInProgressStat, tvPendingStat;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            priorityStrip = itemView.findViewById(R.id.priority_strip);
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

            llStatusStats = itemView.findViewById(R.id.ll_status_stats);
            tvAssignedStat = itemView.findViewById(R.id.tv_assigned_stat);
            tvCompletedStat = itemView.findViewById(R.id.tv_completed_stat);
            tvInProgressStat = itemView.findViewById(R.id.tv_in_progress_stat);
            tvPendingStat = itemView.findViewById(R.id.tv_pending_stat);
        }
    }
}