// src/main/java/com/example/letsdoit/HomeFragment.java
package com.example.letsdoit;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    private String loggedInUserEmail, loggedInUserRole, displayName;

    private TextView tvWelcomeName, tvDateIndicator;
    private TextView tvTotalTasksCount, tvDoneCount, tvNotDoneCount;
    private TextView tvDonePercentage, tvNotDonePercentage;
    private CardView cardTotalTasks, cardDone, cardNotDone, cardPieChart, cardFabCalendar;
    private ProgressBar progressBar;
    private LinearLayout llDashboardContent;
    private ImageView fabCalendar;
    private TaskPieChartView taskPieChartView;

    private FirebaseFirestore db;
    private List<Task> allTasks = new ArrayList<>();
    private List<Task> doneTasks = new ArrayList<>();
    private List<Task> notDoneTasks = new ArrayList<>();

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.US);

    private long selectedDateMillis = -1;
    private static boolean hasAnimated = false;

    public static HomeFragment newInstance(String msg, String email, String role, String name) {
        HomeFragment f = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_WELCOME_MESSAGE, msg);
        args.putString(ARG_USER_EMAIL, email);
        args.putString(ARG_USER_ROLE, role);
        args.putString(ARG_DISPLAY_NAME, name);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            loggedInUserEmail = getArguments().getString(ARG_USER_EMAIL);
            loggedInUserRole = getArguments().getString(ARG_USER_ROLE);
            displayName = getArguments().getString(ARG_DISPLAY_NAME);
        }

        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, parent, false);

        tvWelcomeName = v.findViewById(R.id.tv_welcome_name);
        tvDateIndicator = v.findViewById(R.id.tv_date_indicator);
        tvTotalTasksCount = v.findViewById(R.id.tv_total_tasks_count);
        tvDoneCount = v.findViewById(R.id.tv_done_count);
        tvNotDoneCount = v.findViewById(R.id.tv_not_done_count);

        cardTotalTasks = v.findViewById(R.id.card_total_tasks);
        cardDone = v.findViewById(R.id.card_done);
        cardNotDone = v.findViewById(R.id.card_not_done);
        cardPieChart = v.findViewById(R.id.card_pie_chart);
        cardFabCalendar = v.findViewById(R.id.card_fab_calendar);

        progressBar = v.findViewById(R.id.progress_bar);
        llDashboardContent = v.findViewById(R.id.ll_dashboard_content);

        fabCalendar = v.findViewById(R.id.fab_calendar);
        tvDonePercentage = v.findViewById(R.id.tv_done_percentage);
        tvNotDonePercentage = v.findViewById(R.id.tv_not_done_percentage);
        taskPieChartView = v.findViewById(R.id.pie_chart_view);

        tvWelcomeName.setText(displayName != null ? displayName : "User");

        tvDateIndicator.setOnClickListener(vv -> {
            selectedDateMillis = -1;
            updateDateLabel();
            loadTasks();
            syncDateWithActivity();
        });

        fabCalendar.setOnClickListener(vv -> showCalendarDialog());

        cardDone.setOnClickListener(vv -> showList("Done Tasks", doneTasks));
        cardNotDone.setOnClickListener(vv -> showList("Pending Tasks", notDoneTasks));

        // FIXED: Set content to VISIBLE initially to prevent blinking
        llDashboardContent.setVisibility(View.VISIBLE);
        llDashboardContent.setAlpha(0f);

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle saved) {
        super.onViewCreated(v, saved);

        if (getActivity() instanceof MainActivity) {
            long activityDate = ((MainActivity) getActivity()).getCurrentSelectedDateMillis();
            if (activityDate != -1) {
                selectedDateMillis = activityDate;
            }
        }

        updateDateLabel();
        loadTasks();
    }

    public void setSelectedDateMillis(long dateMillis) {
        this.selectedDateMillis = dateMillis;
    }

    public void updateDateFromActivity(long dateMillis) {
        this.selectedDateMillis = dateMillis;
        updateDateLabel();
        loadTasks();
    }

    private void syncDateWithActivity() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateSelectedDate(selectedDateMillis);
        }
    }

    private void showCalendarDialog() {
        CalendarDialogFragment d = CalendarDialogFragment.newInstance(
                selectedDateMillis != -1 ? selectedDateMillis : null);
        d.setOnDateSelectedListener(this);
        d.show(getParentFragmentManager(), "CalendarDialog");
    }

    @Override
    public void onDateSelected(long ms, String f) {
        selectedDateMillis = ms == getDayStartMillis(System.currentTimeMillis()) ? -1 : ms;
        updateDateLabel();
        loadTasks();
        syncDateWithActivity();
    }

    private void updateDateLabel() {
        tvDateIndicator.setText(selectedDateMillis == -1
                ? "Today"
                : dateFormat.format(new Date(selectedDateMillis)));
    }

    private void loadTasks() {
        progressBar.setVisibility(View.VISIBLE);
        llDashboardContent.setAlpha(0f);

        long filterDate = selectedDateMillis == -1 ? System.currentTimeMillis() : selectedDateMillis;
        long dayStart = getDayStartMillis(filterDate);
        String dayShort = dayFormat.format(new Date(filterDate)).toLowerCase();

        db.collection("tasks").get().addOnSuccessListener(snap -> {

            allTasks.clear();
            doneTasks.clear();
            notDoneTasks.clear();

            for (QueryDocumentSnapshot doc : snap) {
                try {
                    Task t = doc.toObject(Task.class);
                    t.setId(doc.getId());

                    String type = t.getTaskType() != null ? t.getTaskType().toLowerCase() : "permanent";

                    // User assignment filter (remains the same)
                    if (!"admin".equalsIgnoreCase(loggedInUserRole)) {
                        if (type.equals("additional")) {
                            List<String> assigned = t.getAssignedTo();
                            if (assigned == null || !assigned.contains(loggedInUserEmail)) continue;
                        }
                    }

                    if (!isTaskActive(t, dayShort, dayStart)) continue;

                    allTasks.add(t);

                    if (getStatus(t, dayStart).equals("Completed")) doneTasks.add(t);
                    else notDoneTasks.add(t);

                } catch (Exception ignored) {}
            }

            updateDashboard();

            progressBar.setVisibility(View.GONE);

            // SMOOTH FADE IN - NO BLINKING
            llDashboardContent.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            if (!hasAnimated) animateCards();
        });
    }

    private boolean isTaskActive(Task t, String dayShort, long dayStart) {

        String type = t.getTaskType() != null ? t.getTaskType().toLowerCase() : "permanent";

        if (type.equals("permanent")) {
            List<String> days = t.getSelectedDays();
            if (days != null) {
                for (String d : days) {
                    if (d.trim().toLowerCase().equals(dayShort)) return true;
                }
            }
            return false;
        }

        try {
            String s = t.getStartDate();
            String e = t.getEndDate();

            boolean hasS = s != null && !s.isEmpty();
            boolean hasE = e != null && !e.isEmpty();

            long start = hasS ? getDayStartMillis(dateFormat.parse(s).getTime()) : dayStart;
            long end = hasE ? getDayStartMillis(dateFormat.parse(e).getTime()) : dayStart;

            return dayStart >= start && dayStart <= end;
        }
        catch (Exception ex) {
            return false;
        }
    }

    /**
     * UPDATED: Determines status for the dashboard.
     * Now relies only on global task fields for synchronous status across all users.
     */
    private String getStatus(Task t, long dayStart) {

        boolean isPerm = t.getTaskType().equalsIgnoreCase("permanent");
        long completedAt = t.getCompletedDateMillis(); // Global
        String aiCount = t.getAiCountValue(); // Global

        String effectiveStatus = "Pending";

        // 1. Check if the task is globally completed based on the task's main status field
        if (t.getStatus() != null && t.getStatus().equalsIgnoreCase("Completed")) {

            // 2. Check AI Count requirement based on the global AI Count
            if (t.isRequireAiCount() && (aiCount == null || aiCount.isEmpty())) {
                return "Pending";
            }

            if (isPerm) {
                // 3a. Permanent task: Check if global completion time falls within the day
                long dayEnd = dayStart + 86400000L - 1;
                if (completedAt > 0 && completedAt <= dayEnd) {
                    effectiveStatus = "Completed";
                }
            } else {
                // 3b. Additional task: Always completed if global status says so (AI check passed above)
                effectiveStatus = "Completed";
            }
        }

        return effectiveStatus;
    }

    private void updateDashboard() {

        int total = allTasks.size();
        int done = doneTasks.size();
        int pending = notDoneTasks.size();

        animate(tvTotalTasksCount, total);
        animate(tvDoneCount, done);
        animate(tvNotDoneCount, pending);

        tvDonePercentage.setText("DONE " + done);
        tvNotDonePercentage.setText("PENDING " + pending);

        float frac = total == 0 ? 0 : (float) done / total;
        taskPieChartView.setTaskPercentages(frac);
    }

    private void animate(TextView tv, int target) {
        android.animation.ValueAnimator a =
                android.animation.ValueAnimator.ofInt(0, target);
        a.setDuration(800);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(v -> tv.setText(String.valueOf(v.getAnimatedValue())));
        a.start();
    }

    private void animateCards() {
        hasAnimated = true;

        cardTotalTasks.setAlpha(0f); cardTotalTasks.setTranslationY(50f);
        cardPieChart.setAlpha(0f); cardPieChart.setTranslationY(50f);
        cardDone.setAlpha(0f); cardDone.setTranslationY(50f);
        cardNotDone.setAlpha(0f); cardNotDone.setTranslationY(50f);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            cardTotalTasks.animate().alpha(1f).translationY(0f).setDuration(400)
                    .setInterpolator(new DecelerateInterpolator()).start();

            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    cardPieChart.animate().alpha(1f).translationY(0f).setDuration(400)
                            .setInterpolator(new DecelerateInterpolator()).start(), 100);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                cardDone.animate().alpha(1f).translationY(0f).setDuration(400)
                        .setInterpolator(new DecelerateInterpolator()).start();
                cardNotDone.animate().alpha(1f).translationY(0f).setDuration(400)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }, 200);

        }, 100);
    }

    private void showList(String title, List<Task> list) {

        Dialog d = new Dialog(requireContext());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_task_list);
        d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        TextView tv = d.findViewById(R.id.tv_dialog_title);
        TextView empty = d.findViewById(R.id.tv_empty_state);
        RecyclerView rv = d.findViewById(R.id.recycler_view_tasks);
        TextView close = d.findViewById(R.id.btn_close);

        tv.setText(title);

        if (list.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
        } else {
            empty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            rv.setAdapter(new TaskAdapter(list));
        }

        close.setOnClickListener(v12 -> d.dismiss());
        d.show();
    }

    private long getDayStartMillis(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.Holder> {

        List<Task> list;

        TaskAdapter(List<Task> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_simple_task, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int pos) {
            Task t = list.get(pos);
            h.title.setText(t.getTitle());
            if (t.getDescription() != null && !t.getDescription().isEmpty()) {
                h.desc.setVisibility(View.VISIBLE);
                h.desc.setText(t.getDescription());
            } else {
                h.desc.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            TextView title, desc;

            Holder(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.tv_task_title);
                desc = v.findViewById(R.id.tv_task_description);
            }
        }
    }
}