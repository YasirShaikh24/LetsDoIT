package com.example.letsdoit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

        // 1. Title, Description, Priority
        holder.tvTitle.setText(task.getTitle() != null ? task.getTitle() : "No Title");
        holder.tvDescription.setText(task.getDescription() != null ? task.getDescription() : "No Description");
        String priority = task.getPriority() != null ? task.getPriority().toLowerCase() : "low";

        int priorityColor;
        switch (priority) {
            case "high":
                priorityColor = Color.parseColor("#EF5350");
                holder.tvPriority.setText("!!! HIGH");
                break;
            case "medium":
                priorityColor = Color.parseColor("#FFA726");
                holder.tvPriority.setText("!! MEDIUM");
                break;
            case "low":
            default:
                priorityColor = Color.parseColor("#66BB6A");
                holder.tvPriority.setText("! LOW");
                break;
        }
        GradientDrawable priorityDrawable = (GradientDrawable) holder.tvPriority.getBackground().mutate();
        priorityDrawable.setColor(priorityColor);

        // 2. Status Logic (Enhanced UI/UX with Emojis)
        String status = task.getStatus().toLowerCase();
        int statusColorResId;
        String statusDisplay;

        switch (status) {
            case "pending":
                statusColorResId = R.color.status_pending;
                statusDisplay = "\uD83D\uDD50 PENDING";
                break;
            case "in progress":
                statusColorResId = R.color.status_in_progress;
                statusDisplay = "\uD83D\uDEE0 IN PROGRESS";
                break;
            case "completed":
                statusColorResId = R.color.status_completed;
                statusDisplay = "✅ COMPLETED";
                break;
            default:
                statusColorResId = android.R.color.darker_gray;
                statusDisplay = "❓ UNKNOWN";
        }

        holder.tvStatus.setText(statusDisplay);

        GradientDrawable statusDrawable = (GradientDrawable) holder.tvStatus.getBackground().mutate();
        statusDrawable.setColor(ContextCompat.getColor(context, statusColorResId));

        if ("user".equals(loggedInUserRole)) {
            holder.tvStatus.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskStatusClick(task, position);
                }
            });
            holder.tvStatus.setAlpha(1.0f);
        } else {
            holder.tvStatus.setOnClickListener(null);
            holder.tvStatus.setAlpha(1.0f);
        }

        // NEW: 3. AI Count Display (if task requires AI count and has been completed)
        if (task.isRequireAiCount()) {
            holder.tvAiCount.setVisibility(View.VISIBLE);

            String aiCountValue = task.getAiCountValue();
            if (aiCountValue != null && !aiCountValue.isEmpty()) {
                holder.tvAiCount.setText("🔢 AI Count: " + aiCountValue);
                holder.tvAiCount.setTextColor(Color.parseColor("#4CAF50")); // Green for completed
            } else {
                holder.tvAiCount.setText("🔢 AI Count: Required (Not submitted yet)");
                holder.tvAiCount.setTextColor(Color.parseColor("#FF9800")); // Orange for pending
            }
        } else {
            holder.tvAiCount.setVisibility(View.GONE);
        }

        // 4. Admin Actions (Edit/Delete)
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

        // 5. Task Creation Timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.tvTimestamp.setText("Created: " + sdf.format(new Date(task.getTimestamp())));

        // 6. File Attachments (Returns 0 files since feature is removed)
        if (!task.getFileUrls().isEmpty()) {
            holder.tvFiles.setVisibility(View.VISIBLE);
            holder.tvFiles.setText(task.getFileUrls().size() + " file(s)");
        } else {
            holder.tvFiles.setVisibility(View.GONE);
        }

        // 7. Remarks
        String remarks = task.getRemarks();
        if (remarks != null && !remarks.isEmpty()) {
            holder.tvRemarks.setVisibility(View.VISIBLE);
            holder.tvRemarks.setText("Remarks: " + remarks);
        } else {
            holder.tvRemarks.setVisibility(View.GONE);
        }

        // 8. Start Date and End Date
        String startDate = task.getStartDate();
        String endDate = task.getEndDate();

        if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
            holder.tvDateRange.setVisibility(View.VISIBLE);

            String dateRangeText = "Dates: ";
            dateRangeText += (startDate != null && !startDate.isEmpty()) ? startDate : "?";
            dateRangeText += " - ";
            dateRangeText += (endDate != null && !endDate.isEmpty()) ? endDate : "?";

            holder.tvDateRange.setText(dateRangeText);
        } else {
            holder.tvDateRange.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDescription, tvPriority, tvStatus, tvTimestamp, tvFiles, tvRemarks, tvDateRange;
        TextView tvAiCount; // NEW
        ImageButton btnEditTask, btnDeleteTask;
        LinearLayout llAdminActions;
        CardView cardView;

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
            tvAiCount = itemView.findViewById(R.id.tv_task_ai_count); // NEW

            llAdminActions = itemView.findViewById(R.id.ll_admin_actions);
            btnEditTask = itemView.findViewById(R.id.btn_edit_task);
            btnDeleteTask = itemView.findViewById(R.id.btn_delete_task);
        }
    }
}