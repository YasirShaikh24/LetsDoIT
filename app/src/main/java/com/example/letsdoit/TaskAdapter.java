package com.example.letsdoit;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> taskList;
    private Context context;

    public TaskAdapter(List<Task> taskList, Context context) {
        this.taskList = taskList;
        this.context = context;
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

        holder.tvTitle.setText(task.getTitle());
        holder.tvDescription.setText(task.getDescription());
        holder.tvPriority.setText(task.getPriority().toUpperCase());

        switch (task.getPriority().toLowerCase()) {
            case "high":
                holder.tvPriority.setBackgroundColor(Color.parseColor("#EF5350"));
                break;
            case "medium":
                holder.tvPriority.setBackgroundColor(Color.parseColor("#FFA726"));
                break;
            case "low":
                holder.tvPriority.setBackgroundColor(Color.parseColor("#66BB6A"));
                break;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.tvTimestamp.setText(sdf.format(new Date(task.getTimestamp())));

        if (task.getFileUrls() != null && !task.getFileUrls().isEmpty()) {
            holder.tvFiles.setVisibility(View.VISIBLE);
            holder.tvFiles.setText(task.getFileUrls().size() + " file(s)");
        } else {
            holder.tvFiles.setVisibility(View.GONE);
        }

        if (task.getRemarks() != null && !task.getRemarks().isEmpty()) {
            holder.tvRemarks.setVisibility(View.VISIBLE);
            holder.tvRemarks.setText("Remarks: " + task.getRemarks());
        } else {
            holder.tvRemarks.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDescription, tvPriority, tvTimestamp, tvFiles, tvRemarks;
        CardView cardView;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            tvTitle = itemView.findViewById(R.id.tv_task_title);
            tvDescription = itemView.findViewById(R.id.tv_task_description);
            tvPriority = itemView.findViewById(R.id.tv_task_priority);
            tvTimestamp = itemView.findViewById(R.id.tv_task_timestamp);
            tvFiles = itemView.findViewById(R.id.tv_task_files);
            tvRemarks = itemView.findViewById(R.id.tv_task_remarks);
        }
    }
}