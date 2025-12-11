// src/main/java/com/example/letsdoit/TaskPieChartView.java
package com.example.letsdoit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

public class TaskPieChartView extends View {

    private Paint donePaint;
    private Paint notDonePaint;
    private Paint textPaint; // New paint for drawing text
    private RectF rectF;
    private float donePercentage = 0f; // 0.0 to 1.0

    public TaskPieChartView(Context context) {
        super(context);
        init(context);
    }

    public TaskPieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TaskPieChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        int doneColor = ContextCompat.getColor(context, R.color.chart_done);
        int notDoneColor = ContextCompat.getColor(context, R.color.chart_not_done);

        donePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        donePaint.setColor(doneColor);
        donePaint.setStyle(Paint.Style.FILL);

        notDonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        notDonePaint.setColor(notDoneColor);
        notDonePaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE); // Use white text for visibility on colored slices
        textPaint.setTextSize(40f); // Adjust text size as needed
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        rectF = new RectF();
    }

    public void setTaskPercentages(float doneFraction) {
        // doneFraction is expected to be between 0.0 and 1.0
        this.donePercentage = Math.max(0f, Math.min(1f, doneFraction));
        invalidate(); // Redraw the view
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int size = Math.min(getWidth(), getHeight());

        // Define the bounds of the oval (full circle)
        rectF.set(0, 0, size, size);

        // Calculate fractions and angles
        float notDoneFraction = 1.0f - donePercentage; // CALCULATED HERE: Use this variable

        // --- 1. Draw the "Not Done" (Pending) slice (Background/remaining slice) ---
        float notDoneAngle = notDoneFraction * 360f;

        // Start angle for the Not Done slice is where the Done slice ends
        float notDoneStartAngle = -90f + (donePercentage * 360f);

        // Draw the Not Done slice (This will effectively be the full circle if donePercentage is 0)
        canvas.drawArc(rectF, notDoneStartAngle, notDoneAngle, true, notDonePaint);

        // --- 2. Draw the "Done" slice on top ---
        float doneAngle = donePercentage * 360f;
        float doneStartAngle = -90f; // Start from the top (12 o'clock)

        if (donePercentage > 0) {
            canvas.drawArc(rectF, doneStartAngle, doneAngle, true, donePaint);
        }

        // --- 3. Draw Text (Percentages) ---
        float centerX = rectF.centerX();
        float centerY = rectF.centerY();
        float radius = size / 2.0f;

        // Draw Done Percentage
        if (donePercentage > 0) {
            float textRadius = radius * 0.6f;
            float textAngle = doneStartAngle + (doneAngle / 2.0f);
            float x = centerX + (float) (textRadius * Math.cos(Math.toRadians(textAngle)));
            float y = centerY + (float) (textRadius * Math.sin(Math.toRadians(textAngle)));

            String doneText = String.format(java.util.Locale.US, "%.0f%%", donePercentage * 100);
            canvas.drawText(doneText, x, y - (textPaint.descent() + textPaint.ascent()) / 2, textPaint);
        }

        // Draw Not Done Percentage
        if (notDoneFraction > 0) { // FIXED: Condition uses 'notDoneFraction'
            float textRadius = radius * 0.6f;
            float textAngle = notDoneStartAngle + (notDoneAngle / 2.0f);
            float x = centerX + (float) (textRadius * Math.cos(Math.toRadians(textAngle)));
            float y = centerY + (float) (textRadius * Math.sin(Math.toRadians(textAngle)));

            String notDoneText = String.format(java.util.Locale.US, "%.0f%%", notDoneFraction * 100);
            canvas.drawText(notDoneText, x, y - (textPaint.descent() + textPaint.ascent()) / 2, textPaint);
        }
    }
}