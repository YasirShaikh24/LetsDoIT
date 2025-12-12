// src/main/java/com/example/letsdoit/CalendarDialogFragment.java
package com.example.letsdoit;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CalendarView;
import android.widget.TextView;
import android.widget.LinearLayout; // ADDED Import
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class CalendarDialogFragment extends DialogFragment {

    public interface OnDateSelectedListener {
        void onDateSelected(long dateInMillis, String formattedDate);
    }

    private CalendarView calendarView;
    private TextView tvDialogHeaderDate; // Added TextView for the custom header date
    private OnDateSelectedListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    // Format for the large header date (e.g., Sat, 13 Dec)
    private SimpleDateFormat headerDateFormat = new SimpleDateFormat("EEE, dd MMM", Locale.US);
    // Format for the year (e.g., 2025)
    private SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.US);

    public static CalendarDialogFragment newInstance(@Nullable Long initialDateMillis) {
        CalendarDialogFragment fragment = new CalendarDialogFragment();
        Bundle args = new Bundle();

        if (initialDateMillis != null && initialDateMillis != -1) {
            args.putLong("initial_date", initialDateMillis);
            args.putBoolean("has_initial_date", true);
        } else {
            // If no initial date or -1, use today
            args.putLong("initial_date", System.currentTimeMillis());
            args.putBoolean("has_initial_date", false);
        }

        fragment.setArguments(args);
        return fragment;
    }

    public void setOnDateSelectedListener(OnDateSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        calendarView = view.findViewById(R.id.calendar_view);
        tvDialogHeaderDate = view.findViewById(R.id.tv_dialog_header_date); // Initialize custom header date

        // Set max date to today (prevent future date selection)
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 23);
        today.set(Calendar.MINUTE, 59);
        today.set(Calendar.SECOND, 59);
        today.set(Calendar.MILLISECOND, 999);
        calendarView.setMaxDate(today.getTimeInMillis());

        long initialDate = System.currentTimeMillis();
        // Set the calendar to show the initial date
        if (getArguments() != null) {
            initialDate = getArguments().getLong("initial_date", System.currentTimeMillis());
            calendarView.setDate(initialDate, false, true);
        }

        // Initial header update
        updateHeaderDate(initialDate);

        // Handle date selection
        calendarView.setOnDateChangeListener((view1, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, dayOfMonth, 0, 0, 0);
            selected.set(Calendar.MILLISECOND, 0);

            // Double-check: Prevent future date selection
            Calendar todayCheck = Calendar.getInstance();
            todayCheck.set(Calendar.HOUR_OF_DAY, 0);
            todayCheck.set(Calendar.MINUTE, 0);
            todayCheck.set(Calendar.SECOND, 0);
            todayCheck.set(Calendar.MILLISECOND, 0);

            if (selected.after(todayCheck)) {
                Toast.makeText(getContext(), "Cannot select future dates", Toast.LENGTH_SHORT).show();
                return;
            }

            // Notify listener and dismiss
            if (listener != null) {
                String formattedDate = dateFormat.format(selected.getTime());
                listener.onDateSelected(selected.getTimeInMillis(), formattedDate);
            }

            dismiss();
        });
    }

    // Helper to update the custom header text (day, month, year)
    private void updateHeaderDate(long dateMillis) {
        Date date = new Date(dateMillis);
        String year = yearFormat.format(date);
        String dayMonth = headerDateFormat.format(date);

        // Find the year TextView which is the first child (index 0) of the header's parent LinearLayout
        LinearLayout headerParent = (LinearLayout) tvDialogHeaderDate.getParent();
        if (headerParent != null && headerParent.getChildCount() > 0 && headerParent.getChildAt(0) instanceof TextView) {
            ((TextView) headerParent.getChildAt(0)).setText(year);
        }

        tvDialogHeaderDate.setText(dayMonth);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}