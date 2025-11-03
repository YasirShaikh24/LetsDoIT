// src/main/java/com/example/letsdoit/ViewActivityFragment.java
package com.example.letsdoit;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ViewActivityFragment extends Fragment {

    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private List<Task> taskList;
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView tvEmptyState;

    private String loggedInUserEmail;
    private String loggedInUserRole;

    private static final String TAG = "ViewActivityFragment";

    // Factory method to create an instance with user data
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

        // Retrieve user data
        if (getArguments() != null) {
            loggedInUserEmail = getArguments().getString(LoginActivity.EXTRA_USER_EMAIL);
            loggedInUserRole = getArguments().getString(LoginActivity.EXTRA_USER_ROLE);
        }

        recyclerView = view.findViewById(R.id.recycler_view_tasks);
        progressBar = view.findViewById(R.id.progress_bar);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);

        db = FirebaseFirestore.getInstance();

        taskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(taskList, getContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(taskAdapter);

        loadTasks();

        return view;
    }

    private void loadTasks() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        CollectionReference tasksRef = db.collection("tasks");

        // UPDATED LOGIC: Different approach for Admin vs Regular User
        if ("admin".equals(loggedInUserRole)) {
            // Admin sees all tasks - simple query with ordering
            tasksRef.orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        handleTasksSuccess(queryDocumentSnapshots, "No tasks in the system.");
                    })
                    .addOnFailureListener(e -> {
                        handleTasksFailure(e);
                    });
        } else {
            // Regular user - fetch tasks assigned to them
            // Method 1: Try with composite query (requires index)
            tasksRef.whereEqualTo("assignedTo", loggedInUserEmail)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        handleTasksSuccess(queryDocumentSnapshots, "No tasks assigned to you.");
                    })
                    .addOnFailureListener(e -> {
                        // If index error, fall back to client-side filtering and sorting
                        Log.w(TAG, "Composite query failed, using fallback method", e);
                        loadTasksWithClientSideFiltering();
                    });
        }
    }

    // Fallback method: Load all tasks and filter/sort on client side
    private void loadTasksWithClientSideFiltering() {
        db.collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    taskList.clear();

                    // Filter tasks for the logged-in user
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Task task = document.toObject(Task.class);
                        task.setId(document.getId());

                        // Only add tasks assigned to this user
                        if (loggedInUserEmail.equals(task.getAssignedTo())) {
                            taskList.add(task);
                        }
                    }

                    // Sort by timestamp (newest first) on client side
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
                    handleTasksFailure(e);
                });
    }

    private void handleTasksSuccess(com.google.firebase.firestore.QuerySnapshot queryDocumentSnapshots, String emptyMessage) {
        taskList.clear();
        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
            Task task = document.toObject(Task.class);
            task.setId(document.getId());
            taskList.add(task);
        }

        taskAdapter.notifyDataSetChanged();
        progressBar.setVisibility(View.GONE);

        if (taskList.isEmpty()) {
            tvEmptyState.setText(emptyMessage);
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void handleTasksFailure(Exception e) {
        Log.e(TAG, "Error loading tasks", e);
        progressBar.setVisibility(View.GONE);
        tvEmptyState.setText("Error loading tasks: " + e.getMessage());
        tvEmptyState.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (loggedInUserEmail != null) {
            loadTasks();
        }
    }
}