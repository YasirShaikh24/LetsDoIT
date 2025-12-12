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
    private Paint textPaint;
    private Paint shadowPaint;
    private RectF rectF;
    private float donePercentage = 0f;

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
        // Premium colors
        int doneColor = Color.parseColor("#4AFFB8"); // Mint green
        int notDoneColor = Color.parseColor("#FF6B9D"); // Pink

        donePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        donePaint.setColor(doneColor);
        donePaint.setStyle(Paint.Style.FILL);

        notDonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        notDonePaint.setColor(notDoneColor);
        notDonePaint.setStyle(Paint.Style.FILL);

        // Text paint with shadow
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(56f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(8f, 0f, 4f, Color.parseColor("#40000000"));

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#20000000"));
        shadowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.NORMAL));

        rectF = new RectF();

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setTaskPercentages(float doneFraction) {
        this.donePercentage = Math.max(0f, Math.min(1f, doneFraction));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int size = Math.min(getWidth(), getHeight());
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        // Add padding for shadow
        int padding = 12;
        rectF.set(padding, padding, size - padding, size - padding);

        float notDoneFraction = 1.0f - donePercentage;

        // Draw shadow circle
        canvas.drawCircle(centerX, centerY + 4, (size - padding * 2) / 2f, shadowPaint);

        // Draw Not Done slice (background)
        float notDoneAngle = notDoneFraction * 360f;
        float notDoneStartAngle = -90f + (donePercentage * 360f);
        if (notDoneFraction > 0) {
            canvas.drawArc(rectF, notDoneStartAngle, notDoneAngle, true, notDonePaint);
        }

        // Draw Done slice
        float doneAngle = donePercentage * 360f;
        float doneStartAngle = -90f;
        if (donePercentage > 0) {
            canvas.drawArc(rectF, doneStartAngle, doneAngle, true, donePaint);
        }

        float radius = (size - padding * 2) / 2.0f;

        // Draw Done Percentage
        if (donePercentage > 0.08f) {
            float textRadius = radius * 0.55f;
            float textAngle = doneStartAngle + (doneAngle / 2.0f);
            float x = centerX + (float) (textRadius * Math.cos(Math.toRadians(textAngle)));
            float y = centerY + (float) (textRadius * Math.sin(Math.toRadians(textAngle)));

            String doneText = String.format(java.util.Locale.US, "%.0f%%", donePercentage * 100);
            canvas.drawText(doneText, x, y - (textPaint.descent() + textPaint.ascent()) / 2, textPaint);
        }

        // Draw Not Done Percentage
        if (notDoneFraction > 0.08f) {
            float textRadius = radius * 0.55f;
            float textAngle = notDoneStartAngle + (notDoneAngle / 2.0f);
            float x = centerX + (float) (textRadius * Math.cos(Math.toRadians(textAngle)));
            float y = centerY + (float) (textRadius * Math.sin(Math.toRadians(textAngle)));

            String notDoneText = String.format(java.util.Locale.US, "%.0f%%", notDoneFraction * 100);
            canvas.drawText(notDoneText, x, y - (textPaint.descent() + textPaint.ascent()) / 2, textPaint);
        }
    }
}