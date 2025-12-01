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
    private OnDateSelectedListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    public static CalendarDialogFragment newInstance(@Nullable Long initialDateMillis) {
        CalendarDialogFragment fragment = new CalendarDialogFragment();
        if (initialDateMillis != null && initialDateMillis != -1) {
            Bundle args = new Bundle();
            args.putLong("initial_date", initialDateMillis);
            fragment.setArguments(args);
        }
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

        // Set max date to today
        Calendar today = Calendar.getInstance();
        calendarView.setMaxDate(today.getTimeInMillis());

        // Use initial date argument if available to show the previously selected date
        if (getArguments() != null && getArguments().containsKey("initial_date")) {
            long initialDate = getArguments().getLong("initial_date");
            calendarView.setDate(initialDate, false, false);
        }

        calendarView.setOnDateChangeListener((view1, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, dayOfMonth, 0, 0, 0);
            selected.set(Calendar.MILLISECOND, 0);

            // Prevent future date selection
            Calendar todayCheck = Calendar.getInstance();
            todayCheck.set(Calendar.HOUR_OF_DAY, 0);
            todayCheck.set(Calendar.MINUTE, 0);
            todayCheck.set(Calendar.SECOND, 0);
            todayCheck.set(Calendar.MILLISECOND, 0);

            if (selected.after(todayCheck)) {
                // If it's a future date, select today instead
                selected = todayCheck;
                Toast.makeText(getContext(), "Cannot select future dates", Toast.LENGTH_SHORT).show();
            }

            // Immediately apply the selected date and dismiss
            if (listener != null) {
                String formattedDate = dateFormat.format(selected.getTime());
                listener.onDateSelected(selected.getTimeInMillis(), formattedDate);
            }
            dismiss();
        });
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