// src/main/java/com/example/letsdoit/HomeFragment.java
package com.example.letsdoit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import android.widget.ImageView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment implements CalendarDialogFragment.OnDateSelectedListener {

    private static final String ARG_WELCOME_MESSAGE = "welcome_message";
    private static final String ARG_USER_EMAIL = "user_email";
    private static final String ARG_USER_ROLE = "user_role";
    private static final String ARG_DISPLAY_NAME = "display_name";
    private static final String TAG = "HomeFragment";

    private String welcomeMessage;
    private String loggedInUserEmail;
    private String loggedInUserRole;
    private String displayName;

    private TextView tvWelcomeName, tvDateIndicator;
    private TextView tvTotalTasksCount, tvDoneCount, tvNotDoneCount;
    private CardView cardDone, cardNotDone;
    private ProgressBar progressBar;
    private LinearLayout llDashboardContent;
    private ImageView fabCalendar;
    private CardView cardFabCalendar;

    private TextView tvCalendarBanner;
    private TextView tvDonePercentage, tvNotDonePercentage;
    private TaskPieChartView taskPieChartView;

    private FirebaseFirestore db;
    private List<Task> allTasks;
    private List<Task> doneTasks;
    private List<Task> notDoneTasks;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private final SimpleDateFormat dayOfWeekFormat = new SimpleDateFormat("EEEE", Locale.US);

    private long selectedDateMillis = -1;

    private static boolean hasAnimatedOnStart = false;


    public static HomeFragment newInstance(String welcomeMessage, String email, String role, String displayName) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_WELCOME_MESSAGE, welcomeMessage);
        args.putString(ARG_USER_EMAIL, email);
        args.putString(ARG_USER_ROLE, role);
        args.putString(ARG_DISPLAY_NAME, displayName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            welcomeMessage = getArguments().getString(ARG_WELCOME_MESSAGE);
            loggedInUserEmail = getArguments().getString(ARG_USER_EMAIL);
            loggedInUserRole = getArguments().getString(LoginActivity.EXTRA_USER_ROLE);
            displayName = getArguments().getString(ARG_DISPLAY_NAME);
        }
        db = FirebaseFirestore.getInstance();
        allTasks = new ArrayList<>();
        doneTasks = new ArrayList<>();
        notDoneTasks = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tvWelcomeName = view.findViewById(R.id.tv_welcome_name);
        tvDateIndicator = view.findViewById(R.id.tv_date_indicator);
        tvTotalTasksCount = view.findViewById(R.id.tv_total_tasks_count);

        tvDoneCount = view.findViewById(R.id.tv_done_count);
        tvNotDoneCount = view.findViewById(R.id.tv_not_done_count);

        cardDone = view.findViewById(R.id.card_done);
        cardNotDone = view.findViewById(R.id.card_not_done);
        progressBar = view.findViewById(R.id.progress_bar);
        llDashboardContent = view.findViewById(R.id.ll_dashboard_content);
        fabCalendar = view.findViewById(R.id.fab_calendar);
        cardFabCalendar = view.findViewById(R.id.card_fab_calendar);
        tvCalendarBanner = view.findViewById(R.id.tv_calendar_banner);

        tvDonePercentage = view.findViewById(R.id.tv_done_percentage);
        tvNotDonePercentage = view.findViewById(R.id.tv_not_done_percentage);

        taskPieChartView = view.findViewById(R.id.pie_chart_view);

        if (displayName != null && !displayName.isEmpty()) {
            tvWelcomeName.setText(displayName);
        } else if ("admin".equalsIgnoreCase(loggedInUserRole)) {
            tvWelcomeName.setText("Admin");
        } else {
            tvWelcomeName.setText("User");
        }

        updateDateIndicator();

        tvDateIndicator.setOnClickListener(v -> {
            if (selectedDateMillis != -1) {
                selectedDateMillis = -1;
                updateDateIndicator();
                loadTasksForDate();
            }
        });

        fabCalendar.setOnClickListener(v -> showCalendarDialog());

        cardDone.setOnClickListener(v -> showTaskListDialog("Done Tasks", doneTasks));
        cardNotDone.setOnClickListener(v -> showTaskListDialog("Pending Tasks", notDoneTasks));

        return view;
    }

    private void animateFabOnLoad() {
        if (hasAnimatedOnStart) {
            return;
        }

        // Initial state: Hide both FAB card and banner
        cardFabCalendar.setScaleX(0f);
        cardFabCalendar.setScaleY(0f);
        cardFabCalendar.setVisibility(View.VISIBLE);
        tvCalendarBanner.setVisibility(View.GONE);

        // Position banner directly above the calendar button
        final float initialTranslationY = 0f;
        tvCalendarBanner.setTranslationY(0f);
        tvCalendarBanner.setAlpha(0f);

        // Step 1: Animate FAB entrance (scale up to 1.3x)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            cardFabCalendar.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .setDuration(500)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // Step 2: Show banner and animate from above
                            tvCalendarBanner.setVisibility(View.VISIBLE);
                            tvCalendarBanner.setTranslationY(-100f); // Start from above
                            tvCalendarBanner.setAlpha(1f);

                            tvCalendarBanner.animate()
                                    .translationY(0f) // Move to normal position
                                    .setDuration(600)
                                    .setStartDelay(0)
                                    .setListener(null)
                                    .start();

                            // Step 3: FAB scales back to normal
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                cardFabCalendar.animate()
                                        .scaleX(1f)
                                        .scaleY(1f)
                                        .setDuration(500)
                                        .setListener(null)
                                        .start();

                                // Step 4: Banner continues moving up and fades out
                                tvCalendarBanner.animate()
                                        .alpha(0f)
                                        .translationY(-150f) // Continue upward
                                        .setDuration(500)
                                        .setStartDelay(500)
                                        .setListener(new AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                tvCalendarBanner.setVisibility(View.GONE);
                                                tvCalendarBanner.setTranslationY(0f);
                                            }
                                        })
                                        .start();

                            }, 1000);

                        }
                    })
                    .start();
            hasAnimatedOnStart = true;
        }, 500);
    }

    private void showCalendarDialog() {
        CalendarDialogFragment dialogFragment = CalendarDialogFragment.newInstance(
                selectedDateMillis != -1 ? selectedDateMillis : null);
        dialogFragment.setOnDateSelectedListener(this);
        dialogFragment.show(getParentFragmentManager(), "CalendarDialog");
    }

    @Override
    public void onDateSelected(long dateInMillis, String formattedDate) {
        long todayStart = getDayStartMillis(System.currentTimeMillis());
        long selectedDayStart = getDayStartMillis(dateInMillis);

        if (selectedDayStart == todayStart) {
            selectedDateMillis = -1;
        } else {
            selectedDateMillis = dateInMillis;
        }

        updateDateIndicator();
        loadTasksForDate();
    }

    private void updateDateIndicator() {
        if (tvDateIndicator == null) return;

        if (selectedDateMillis == -1) {
            tvDateIndicator.setText("Today");
        } else {
            String formattedDate = dateFormat.format(new Date(selectedDateMillis));
            tvDateIndicator.setText(formattedDate);
        }
    }

    private void loadTasksForDate() {
        progressBar.setVisibility(View.VISIBLE);
        llDashboardContent.setVisibility(View.GONE);

        long filterDateMillis = selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
        long filterDayStart = getDayStartMillis(filterDateMillis);
        String filterDayOfWeek = dayOfWeekFormat.format(new Date(filterDateMillis)).substring(0, 3);

        db.collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allTasks.clear();
                    doneTasks.clear();
                    notDoneTasks.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Task task = document.toObject(Task.class);
                            task.setId(document.getId());

                            boolean isAssignedToUser = false;
                            if ("user".equals(loggedInUserRole)) {
                                List<String> assignedTo = task.getAssignedTo();
                                if (assignedTo != null && assignedTo.contains(loggedInUserEmail)) {
                                    isAssignedToUser = true;
                                }
                            } else {
                                isAssignedToUser = true;
                            }

                            if (!isAssignedToUser) {
                                continue;
                            }

                            boolean isForFilterDate = isTaskActiveOnDate(task, filterDayStart, filterDayOfWeek);

                            if (!isForFilterDate) {
                                continue;
                            }

                            allTasks.add(task);

                            String taskStatus = getTaskStatusForDate(task, filterDayStart);

                            if (taskStatus.equalsIgnoreCase("Completed")) {
                                doneTasks.add(task);
                            } else {
                                notDoneTasks.add(task);
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing task: " + document.getId(), e);
                        }
                    }

                    updateDashboard();
                    progressBar.setVisibility(View.GONE);
                    llDashboardContent.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading tasks", e);
                    progressBar.setVisibility(View.GONE);
                    llDashboardContent.setVisibility(View.VISIBLE);
                    Toast.makeText(getContext(), "Failed to load tasks for this date.", Toast.LENGTH_SHORT).show();
                });
    }

    private boolean isTaskActiveOnDate(Task task, long filterDayStart, String filterDayOfWeek) {
        String taskType = task.getTaskType() != null ? task.getTaskType().toLowerCase() : "permanent";

        if (taskType.equals("permanent")) {
            List<String> selectedDays = task.getSelectedDays();
            boolean isDayActive = false;
            if (selectedDays != null) {
                for (String day : selectedDays) {
                    if (day.equalsIgnoreCase(filterDayOfWeek)) {
                        isDayActive = true;
                        break;
                    }
                }
            }

            if (isDayActive && task.getTimestamp() < filterDayStart + (24 * 60 * 60 * 1000L)) {
                return true;
            }
        } else if (taskType.equals("additional")) {
            try {
                String startDateStr = task.getStartDate();
                String endDateStr = task.getEndDate();

                if (startDateStr != null && !startDateStr.isEmpty() &&
                        endDateStr != null && !endDateStr.isEmpty()) {

                    long startDateMillis = dateFormat.parse(startDateStr).getTime();
                    long endDateMillis = dateFormat.parse(endDateStr).getTime();
                    long startDayStart = getDayStartMillis(startDateMillis);
                    long endDayStart = getDayStartMillis(endDateMillis);

                    if (filterDayStart >= startDayStart && filterDayStart <= endDayStart) {
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing task dates: " + e.getMessage());
            }
        }
        return false;
    }

    private String getTaskStatusForDate(Task task, long filterDayStart) {
        if (task.getUserStatus().containsValue("Completed")) {
            long globalCompletionTime = task.getCompletedDateMillis();

            if (task.getTaskType().equalsIgnoreCase("permanent")) {
                long filterDayEnd = filterDayStart + (24 * 60 * 60 * 1000L) - 1;

                if (globalCompletionTime > 0 && globalCompletionTime <= filterDayEnd) {
                    return "Completed";
                }
                return "Pending";
            }

            return "Completed";
        }

        return "Pending";
    }

    private long getDayStartMillis(long dateMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void updateDashboard() {
        if (getContext() == null) return;

        int totalTasks = allTasks.size();
        int doneCount = doneTasks.size();
        int notDoneCount = notDoneTasks.size();

        tvTotalTasksCount.setText(String.valueOf(totalTasks));
        tvDoneCount.setText(String.valueOf(doneCount));
        tvNotDoneCount.setText(String.valueOf(notDoneCount));

        float doneFraction = 0f;

        if (totalTasks == 0) {
            tvDonePercentage.setText(String.format(Locale.US, "DONE %d", doneCount));
            tvNotDonePercentage.setText(String.format(Locale.US, "PENDING %d", notDoneCount));
        } else {
            doneFraction = (float) doneCount / totalTasks;
            tvDonePercentage.setText(String.format(Locale.US, "DONE %d", doneCount));
            tvNotDonePercentage.setText(String.format(Locale.US, "PENDING %d", notDoneCount));
        }

        if (taskPieChartView != null) {
            taskPieChartView.setTaskPercentages(doneFraction);
        }
    }

    private void showTaskListDialog(String title, List<Task> tasks) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_task_list);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        TextView tvDialogTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView tvEmptyState = dialog.findViewById(R.id.tv_empty_state);
        RecyclerView recyclerView = dialog.findViewById(R.id.recycler_view_tasks);
        TextView btnClose = dialog.findViewById(R.id.btn_close);

        tvDialogTitle.setText(title);

        if (tasks.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            tvEmptyState.setText("No tasks found");
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            SimpleTaskAdapter adapter = new SimpleTaskAdapter(tasks);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(adapter);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private static class SimpleTaskAdapter extends RecyclerView.Adapter<SimpleTaskAdapter.SimpleTaskViewHolder> {

        private List<Task> tasks;

        public SimpleTaskAdapter(List<Task> tasks) {
            this.tasks = tasks;
        }

        @NonNull
        @Override
        public SimpleTaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_simple_task, parent, false);
            return new SimpleTaskViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SimpleTaskAdapter.SimpleTaskViewHolder holder, int position) {
            Task task = tasks.get(position);
            holder.tvTaskTitle.setText(task.getTitle());

            if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                holder.tvTaskDescription.setVisibility(View.VISIBLE);
                holder.tvTaskDescription.setText(task.getDescription());
            } else {
                holder.tvTaskDescription.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return tasks.size();
        }

        static class SimpleTaskViewHolder extends RecyclerView.ViewHolder {
            TextView tvTaskTitle, tvTaskDescription;

            public SimpleTaskViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTaskTitle = itemView.findViewById(R.id.tv_task_title);
                tvTaskDescription = itemView.findViewById(R.id.tv_task_description);
            }
        }
    }

    public void setFabVisibility(boolean visible) {
        if (cardFabCalendar != null) {
            cardFabCalendar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (loggedInUserEmail != null) {
            loadTasksForDate();
        }
        if (cardFabCalendar != null) {
            cardFabCalendar.setVisibility(View.VISIBLE);
            animateFabOnLoad();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (cardFabCalendar != null) {
            cardFabCalendar.setVisibility(View.GONE);
        }
    }
}