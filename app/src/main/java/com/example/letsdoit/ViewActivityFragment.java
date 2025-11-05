// src/main/java/com/example/letsdoit/ViewActivityFragment.java
package com.example.letsdoit;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ViewActivityFragment extends Fragment implements TaskAdapter.TaskActionListener {

    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private List<Task> taskList;
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView tvEmptyState;

    private String loggedInUserEmail;
    private String loggedInUserRole;

    private static final String TAG = "ViewActivityFragment";

    public static final String EXTRA_TASK_ID = "extra_task_id";

    public static ViewActivityFragment newInstance(String email, String role) {
        ViewActivityFragment fragment = new ViewActivityFragment();
        Bundle args = new Bundle();
        args.putString(LoginActivity.EXTRA_USER_EMAIL, email);
        args.putString(LoginActivity.EXTRA_USER_ROLE, role);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_view_activity, container, false);

        if (getArguments() != null) {
            loggedInUserEmail = getArguments().getString(LoginActivity.EXTRA_USER_EMAIL);
            loggedInUserRole = getArguments().getString(LoginActivity.EXTRA_USER_ROLE);
        }

        recyclerView = view.findViewById(R.id.recycler_view_tasks);
        progressBar = view.findViewById(R.id.progress_bar);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);

        db = FirebaseFirestore.getInstance();

        taskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(taskList, getContext(), this, loggedInUserRole);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(taskAdapter);

        loadTasks();

        return view;
    }

    private void loadTasks() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        if ("admin".equals(loggedInUserRole)) {
            loadAllTasks();
        } else {
            loadUserTasks();
        }
    }

    private void loadAllTasks() {
        db.collection("tasks")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    taskList.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Task task = document.toObject(Task.class);
                            task.setId(document.getId());
                            taskList.add(task);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing task: " + document.getId(), e);
                        }
                    }

                    taskAdapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);

                    if (taskList.isEmpty()) {
                        tvEmptyState.setText("No tasks in the system.");
                        tvEmptyState.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        tvEmptyState.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading tasks for admin", e);
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Error loading tasks: " + e.getMessage());
                    tvEmptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                });
    }

    private void loadUserTasks() {
        db.collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    taskList.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Task task = document.toObject(Task.class);
                            task.setId(document.getId());

                            List<String> assignedTo = task.getAssignedTo();
                            if (assignedTo != null && assignedTo.contains(loggedInUserEmail)) {
                                taskList.add(task);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing task: " + document.getId(), e);
                        }
                    }

                    Collections.sort(taskList, new Comparator<Task>() {
                        @Override
                        public int compare(Task t1, Task t2) {
                            return Long.compare(t2.getTimestamp(), t1.getTimestamp());
                        }
                    });

                    taskAdapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);

                    if (taskList.isEmpty()) {
                        tvEmptyState.setText("No tasks assigned to you.");
                        tvEmptyState.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        tvEmptyState.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading tasks for user", e);
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Error loading tasks: " + e.getMessage());
                    tvEmptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                });
    }

    @Override
    public void onTaskStatusClick(Task task, int position) {
        showStatusUpdateDialog(task, position);
    }

    @Override
    public void onTaskEditClick(Task task, int position) {
        if (task.getId() != null) {
            Intent intent = new Intent(getActivity(), EditTaskActivity.class);
            intent.putExtra(EXTRA_TASK_ID, task.getId());
            startActivity(intent);
        } else {
            Toast.makeText(getContext(), "Error: Cannot edit task without an ID.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onTaskDeleteClick(Task task, int position) {
        showDeleteConfirmationDialog(task, position);
    }


    private void showStatusUpdateDialog(Task task, int position) {
        final String[] statusOptions = {"Pending", "In Progress", "Completed"};

        int checkedItem = 0;
        String currentStatus = task.getStatus();

        for (int i = 0; i < statusOptions.length; i++) {
            if (statusOptions[i].equalsIgnoreCase(currentStatus)) {
                checkedItem = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Update Task Status")
                .setSingleChoiceItems(statusOptions, checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newStatus = statusOptions[which];
                        updateTaskStatusInFirestore(task, newStatus, position);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateTaskStatusInFirestore(Task task, String newStatus, int position) {
        if (task.getId() == null) {
            Toast.makeText(getContext(), "Error: Task ID not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("status", newStatus);

        db.collection("tasks").document(task.getId())
                .update(update)
                .addOnSuccessListener(aVoid -> {
                    task.setStatus(newStatus);
                    taskAdapter.notifyItemChanged(position);
                    Toast.makeText(getContext(), "Status updated to: " + newStatus, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating status for task " + task.getId(), e);
                    Toast.makeText(getContext(), "Failed to update status: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showDeleteConfirmationDialog(Task task, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteTaskFromFirestore(task, position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTaskFromFirestore(Task task, int position) {
        if (task.getId() == null) {
            Toast.makeText(getContext(), "Error: Task ID not found for deletion.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("tasks").document(task.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    taskList.remove(position);
                    taskAdapter.notifyItemRemoved(position);
                    Toast.makeText(getContext(), "Task deleted successfully!", Toast.LENGTH_SHORT).show();

                    if (taskList.isEmpty()) {
                        tvEmptyState.setText("No tasks in the system.");
                        tvEmptyState.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting task " + task.getId(), e);
                    Toast.makeText(getContext(), "Failed to delete task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }


    @Override
    public void onResume() {
        super.onResume();
        if (loggedInUserEmail != null) {
            loadTasks();
        }
    }
}