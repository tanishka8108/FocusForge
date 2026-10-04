package com.example.focusforge;

import android.content.Context;
import android.graphics.Canvas;                                 // The "drawing surface" we paint shapes and text onto
import android.graphics.LinearGradient;                          // Creates a smooth color blend for the bars
import android.graphics.Paint;                                   // Defines HOW something is drawn (color, size, style)
import android.graphics.RectF;                                   // A rectangle defined with decimal-precision coordinates
import android.graphics.Shader;                                  // Base type for gradients/patterns used by Paint
import android.util.AttributeSet;                                 // Carries any XML attributes set on this view
import android.view.View;                                         // Base class for ALL drawable UI components

import androidx.core.content.ContextCompat;

// A custom View means we're drawing this chart completely by hand using
// Canvas/Paint, instead of using a pre-built widget or a charting library.
// This gives full control but means we're responsible for all the math.
public class WeeklyBarChartView extends View {

    private int[] values = new int[]{0, 0, 0, 0, 0, 0, 0};       // Minutes per day, defaults to all zero
    private String[] labels = new String[]{"", "", "", "", "", "", ""}; // Day labels, e.g. "Mon"

    // Paint objects are reused across every draw call rather than recreated
    // each time - creating new Paint objects inside onDraw() would hurt performance.
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);   // ANTI_ALIAS_FLAG smooths jagged edges
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int colorAccentBlue;                            // Cached color values (looked up once, not every frame)
    private final int colorSuccess;

    // Constructor required for any View that can be inflated from XML -
    // Android calls this version automatically when it sees this view
    // referenced in a layout file.
    public WeeklyBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        colorAccentBlue = ContextCompat.getColor(context, R.color.accent_blue);
        colorSuccess = ContextCompat.getColor(context, R.color.success);

        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextSize(dp(11));                           // Convert 11dp into actual pixels for this device
        labelPaint.setTextAlign(Paint.Align.CENTER);              // Text draws centered around its x-coordinate

        valuePaint.setColor(ContextCompat.getColor(context, R.color.text_soft));
        valuePaint.setTextSize(dp(11));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);                         // Slight bold effect without needing a bold font file
    }

    // Helper: converts a "dp" (density-independent pixel) value into actual
    // screen pixels, since raw pixel counts look different sizes on different
    // screen densities.
    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    /** Pass 7 values (minutes) and 7 short day labels, oldest first, today last. */
    public void setData(int[] minutesByDay, String[] dayLabels) {
        if (minutesByDay != null && minutesByDay.length == 7) {  // Validate before accepting new data
            this.values = minutesByDay;
        }
        if (dayLabels != null && dayLabels.length == 7) {
            this.labels = dayLabels;
        }
        invalidate();                                             // Tells Android "redraw this view" - triggers onDraw() again
    }

    // Called automatically by Android whenever this view needs to be (re)drawn -
    // on first layout, and again every time invalidate() is called.
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();                                   // This view's current on-screen pixel width
        int height = getHeight();                                 // ...and height
        if (width == 0 || height == 0) return;                    // Nothing to draw yet - view hasn't been measured

        int max = 1;                                               // Start at 1 (not 0) to avoid a divide-by-zero later
        for (int v : values) max = Math.max(max, v);               // Find the tallest bar's value, to scale everything against

        float labelSpace = dp(20);                                 // Reserve space at the bottom for day labels
        float valueSpace = dp(18);                                 // Reserve space at the top for the number above each bar
        float chartTop = dp(4);
        float chartBottom = height - labelSpace;
        float chartHeight = chartBottom - chartTop - valueSpace;   // The actual usable vertical space for bars

        int barCount = values.length;                              // Always 7
        float slotWidth = (float) width / barCount;                // How much horizontal space each day gets
        float barWidth = slotWidth * 0.45f;                        // Bar itself is narrower than its slot (leaves gaps)

        for (int i = 0; i < barCount; i++) {                       // Loop once per day
            float centerX = slotWidth * i + slotWidth / 2f;        // Horizontal center point for this day's bar

            float barHeightRatio = values[i] / (float) max;        // What fraction of the tallest bar is this one?
            float barHeight = chartHeight * barHeightRatio;        // Scale that ratio into actual pixel height
            float top = chartBottom - barHeight;                   // Bars grow UPWARD from the bottom, so calculate the top edge

            // Build a vertical gradient for this specific bar, blue at top fading to green at bottom
            barPaint.setShader(new LinearGradient(
                    centerX, top, centerX, chartBottom,
                    colorAccentBlue, colorSuccess,
                    Shader.TileMode.CLAMP                          // CLAMP = stretch the two end colors rather than repeat
            ));

            RectF barRect = new RectF(                             // Define this bar's rectangle boundaries
                    centerX - barWidth / 2f,                       // Left edge
                    Math.max(chartTop + valueSpace, top),           // Top edge, but never higher than the reserved value space
                    centerX + barWidth / 2f,                       // Right edge
                    chartBottom                                    // Bottom edge
            );
            float radius = dp(6);                                  // Rounded-corner radius
            canvas.drawRoundRect(barRect, radius, radius, barPaint); // Actually paint the bar onto the canvas

            if (values[i] > 0) {                                   // Only show a number label if there's actually data
                canvas.drawText(String.valueOf(values[i]), centerX, barRect.top - dp(4), valuePaint);
            }

            canvas.drawText(labels[i], centerX, height - dp(4), labelPaint); // Draw this day's label at the very bottom
        }
    }
}