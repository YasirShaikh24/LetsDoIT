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

    public TaskAdapter(List<Task> taskList, Context context, TaskActionListener listener, String loggedInUserRole) {
        this.taskList = taskList;
        this.context = context;
        this.listener = listener;
        this.loggedInUserRole = loggedInUserRole;
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

        // Status
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

        // AI Count
        if (task.isRequireAiCount()) {
            holder.cardAiCount.setVisibility(View.VISIBLE);
            String aiCountValue = task.getAiCountValue();
            if (aiCountValue != null && !aiCountValue.isEmpty()) {
                holder.tvAiCount.setText("🔢 AI Count: " + aiCountValue);
                holder.cardAiCount.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                holder.tvAiCount.setTextColor(Color.parseColor("#2E7D32"));
            } else {
                holder.tvAiCount.setText("🔢 AI Count: Required (Not submitted)");
                holder.cardAiCount.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                holder.tvAiCount.setTextColor(Color.parseColor("#E65100"));
            }
        } else {
            holder.cardAiCount.setVisibility(View.GONE);
        }

        // Date Range
        String startDate = task.getStartDate();
        String endDate = task.getEndDate();
        if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
            holder.layoutDateRange.setVisibility(View.VISIBLE);
            String dateRangeText = "";
            dateRangeText += (startDate != null && !startDate.isEmpty()) ? startDate : "?";
            dateRangeText += " → ";
            dateRangeText += (endDate != null && !endDate.isEmpty()) ? endDate : "?";
            holder.tvDateRange.setText(dateRangeText);
        } else {
            holder.layoutDateRange.setVisibility(View.GONE);
        }

        // Remarks
        String remarks = task.getRemarks();
        if (remarks != null && !remarks.isEmpty()) {
            holder.tvRemarks.setVisibility(View.VISIBLE);
            holder.tvRemarks.setText("📝 " + remarks);
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

        // Admin Actions
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

        // Card Click for Users
        if ("user".equals(loggedInUserRole)) {
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
        }
    }
}