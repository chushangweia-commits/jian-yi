package com.jianyi.yixue.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Colored generation-and-control diagram inspired by traditional five-phase charts. */
public final class FivePhaseRelationView extends View {
    private static final String[] NAMES = {"火", "土", "金", "水", "木"};
    private static final int[] COLORS = {0xffc85245, 0xffa97a3d, 0xffc29b3f, 0xff477a9b, 0xff3f7b5b};
    // Node order on the circle is 火、土、金、水、木. Generation follows the circle.
    private static final int[] CONTROL = {2, 3, 4, 0, 1}; // 火克金、土克水、金克木、水克火、木克土.
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final float[] xs = new float[5], ys = new float[5];

    public FivePhaseRelationView(Context context) {
        super(context);
        setFocusable(false); setClickable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("五行相生相克彩色图。外圈相生：木生火、火生土、土生金、金生水、水生木。内层相克：木克土、土克水、水克火、火克金、金克木。木绿色、火红色、土棕黄色、金金黄色、水蓝色。箭头表示作用方向。此图表示关系，不表示方位。  ");
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(dp(340), widthSpec);
        int height = Math.round(Math.max(dp(330), width * .96f));
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        canvas.drawColor(JianYiUi.SOFT);
        label(canvas, "五行相生 · 相克", width / 2f, dp(27), 18, JianYiUi.INK, true, Paint.Align.CENTER);
        label(canvas, "外圈相生　内层相克　箭头表示作用方向", width / 2f, dp(51), 13, JianYiUi.MUTED, false, Paint.Align.CENTER);
        float cx = width / 2f, cy = dp(60) + (height - dp(70)) / 2f;
        float radius = Math.min(width * .34f, (height - dp(110)) * .43f);
        float node = Math.max(dp(27), Math.min(dp(38), radius * .28f));
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(-90 + i * 72);
            xs[i] = cx + (float)Math.cos(angle) * radius;
            ys[i] = cy + (float)Math.sin(angle) * radius;
        }
        // Draw control first so it remains behind the nodes.
        for (int i = 0; i < 5; i++) arrow(canvas, xs[i], ys[i], xs[CONTROL[i]], ys[CONTROL[i]], node + dp(4), 0xff8c6c62, dp(2), false);
        for (int i = 0; i < 5; i++) arrow(canvas, xs[i], ys[i], xs[(i + 1) % 5], ys[(i + 1) % 5], node + dp(7), COLORS[i], dp(4), true);
        for (int i = 0; i < 5; i++) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(COLORS[i]); canvas.drawCircle(xs[i], ys[i], node, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(0x55ffffff); canvas.drawCircle(xs[i], ys[i], node - dp(2), paint);
            label(canvas, NAMES[i], xs[i], ys[i], 22, 0xffffffff, true, Paint.Align.CENTER);
        }
        legend(canvas, dp(18), height - dp(25), 0xff3f7b5b, "粗彩线：相生");
        legend(canvas, width / 2f + dp(8), height - dp(25), 0xff8c6c62, "细内线：相克");
    }

    private void arrow(Canvas canvas, float x1, float y1, float x2, float y2, float inset, int color, float stroke, boolean curved) {
        float dx = x2 - x1, dy = y2 - y1, length = (float)Math.sqrt(dx * dx + dy * dy);
        if (length < 1f) return;
        float ux = dx / length, uy = dy / length;
        float sx = x1 + ux * inset, sy = y1 + uy * inset, ex = x2 - ux * inset, ey = y2 - uy * inset;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(stroke); paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(color);
        if (curved) {
            float mx = (sx + ex) / 2f, my = (sy + ey) / 2f;
            float bend = dp(13), nx = -uy, ny = ux;
            path.reset(); path.moveTo(sx, sy); path.quadTo(mx + nx * bend, my + ny * bend, ex, ey); canvas.drawPath(path, paint);
            float tx = ex - ux * dp(9) + nx * bend * .10f, ty = ey - uy * dp(9) + ny * bend * .10f;
            arrowHead(canvas, ex, ey, tx, ty, color, stroke);
        } else {
            canvas.drawLine(sx, sy, ex, ey, paint); arrowHead(canvas, ex, ey, sx, sy, color, stroke);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void arrowHead(Canvas canvas, float ex, float ey, float fromX, float fromY, int color, float stroke) {
        float dx = ex - fromX, dy = ey - fromY, len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        float ux = dx / len, uy = dy / len, size = dp(8) + stroke;
        path.reset(); path.moveTo(ex, ey);
        path.lineTo(ex - ux * size - uy * size * .55f, ey - uy * size + ux * size * .55f);
        path.lineTo(ex - ux * size + uy * size * .55f, ey - uy * size - ux * size * .55f); path.close();
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); canvas.drawPath(path, paint);
    }

    private void legend(Canvas canvas, float x, float y, int color, String text) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); rect.set(x, y - dp(3), x + dp(24), y + dp(3)); canvas.drawRoundRect(rect, dp(3), dp(3), paint);
        label(canvas, text, x + dp(32), y, 12, JianYiUi.MUTED, false, Paint.Align.LEFT);
    }

    private void label(Canvas canvas, String value, float x, float y, float size, int color, boolean bold, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setTextSize(sp(size)); paint.setTextAlign(align);
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        Paint.FontMetrics fm = paint.getFontMetrics(); canvas.drawText(value, x, y - (fm.ascent + fm.descent) / 2f, paint);
    }

    private int dp(float value) { return JianYiUi.dp(getContext(), value); }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
