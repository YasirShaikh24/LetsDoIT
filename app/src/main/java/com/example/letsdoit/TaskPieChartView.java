package com.example.letsdoit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

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
        // UPDATED: Lighter, softer colors matching UI
        int doneColor = Color.parseColor("#A8E6CF"); // Light mint green
        int notDoneColor = Color.parseColor("#FFB6C1"); // Light pink

        donePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        donePaint.setColor(doneColor);
        donePaint.setStyle(Paint.Style.FILL);

        notDonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        notDonePaint.setColor(notDoneColor);
        notDonePaint.setStyle(Paint.Style.FILL);

        // UPDATED: Text paint with BLACK color
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(56f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(4f, 0f, 2f, Color.parseColor("#30000000"));

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#15000000"));
        shadowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL));

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

        int padding = 12;
        rectF.set(padding, padding, size - padding, size - padding);

        float notDoneFraction = 1.0f - donePercentage;

        canvas.drawCircle(centerX, centerY + 3, (size - padding * 2) / 2f, shadowPaint);

        float notDoneAngle = notDoneFraction * 360f;
        float notDoneStartAngle = -90f + (donePercentage * 360f);
        if (notDoneFraction > 0) {
            canvas.drawArc(rectF, notDoneStartAngle, notDoneAngle, true, notDonePaint);
        }

        float doneAngle = donePercentage * 360f;
        float doneStartAngle = -90f;
        if (donePercentage > 0) {
            canvas.drawArc(rectF, doneStartAngle, doneAngle, true, donePaint);
        }

        float radius = (size - padding * 2) / 2.0f;

        if (donePercentage > 0.08f) {
            float textRadius = radius * 0.55f;
            float textAngle = doneStartAngle + (doneAngle / 2.0f);
            float x = centerX + (float) (textRadius * Math.cos(Math.toRadians(textAngle)));
            float y = centerY + (float) (textRadius * Math.sin(Math.toRadians(textAngle)));

            String doneText = String.format(java.util.Locale.US, "%.0f%%", donePercentage * 100);
            canvas.drawText(doneText, x, y - (textPaint.descent() + textPaint.ascent()) / 2, textPaint);
        }

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