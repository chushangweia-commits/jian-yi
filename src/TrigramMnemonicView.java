package com.jianyi.yixue.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Eight trigram shapes paired with the traditional mnemonic, fully offline. */
public final class TrigramMnemonicView extends View {
    private static final String[] NAMES = {"乾", "坤", "兑", "巽", "离", "坎", "震", "艮"};
    private static final String[] WORDS = {"三连", "六断", "上缺", "下断", "中虚", "中满", "仰盂", "覆碗"};
    // Bottom line first.
    private static final int[][] BITS = {
        {1,1,1}, {0,0,0}, {1,1,0}, {0,1,1}, {1,0,1}, {0,1,0}, {1,0,0}, {0,0,1}
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public TrigramMnemonicView(Context context) {
        super(context);
        setFocusable(false); setClickable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("八卦取象歌图解：乾三连，坤六断，兑上缺，巽下断，离中虚，坎中满，震仰盂，艮覆碗。每句配对应三爻卦象，爻从下往上读。");
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(dp(320), widthSpec);
        float cellWidth = Math.max(dp(120), (width - dp(44)) / 2f);
        int height = Math.round(dp(24) + 4f * Math.max(dp(98), cellWidth * .56f) + dp(18));
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), gap = dp(8), left = dp(14), top = dp(18);
        float cellW = (width - dp(28) - gap) / 2f;
        float cellH = (getHeight() - top - dp(14) - 3f * gap) / 4f;
        for (int i = 0; i < 8; i++) {
            float x = left + (i % 2) * (cellW + gap), y = top + (i / 2) * (cellH + gap);
            rect.set(x, y, x + cellW, y + cellH);
            paint.setStyle(Paint.Style.FILL); paint.setColor(i % 2 == 0 ? JianYiUi.WHITE : 0xfff3f0e7);
            canvas.drawRoundRect(rect, dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(JianYiUi.LINE);
            canvas.drawRoundRect(rect, dp(12), dp(12), paint);
            float iconX = x + cellW * .28f, centerY = y + cellH / 2f;
            for (int line = 0; line < 3; line++) yao(canvas, iconX, centerY + (1 - line) * dp(9), cellW * .33f, BITS[i][line] == 1);
            label(canvas, NAMES[i] + WORDS[i], x + cellW * .53f, centerY, Math.min(18f, cellW / getResources().getDisplayMetrics().scaledDensity / 7f));
        }
    }

    private void yao(Canvas canvas, float x, float y, float width, boolean solid) {
        float h = dp(4), gap = Math.max(dp(7), width * .22f);
        paint.setStyle(Paint.Style.FILL); paint.setColor(JianYiUi.ACCENT);
        if (solid) {
            rect.set(x - width / 2f, y - h / 2f, x + width / 2f, y + h / 2f); canvas.drawRoundRect(rect, h, h, paint);
        } else {
            rect.set(x - width / 2f, y - h / 2f, x - gap / 2f, y + h / 2f); canvas.drawRoundRect(rect, h, h, paint);
            rect.set(x + gap / 2f, y - h / 2f, x + width / 2f, y + h / 2f); canvas.drawRoundRect(rect, h, h, paint);
        }
    }

    private void label(Canvas canvas, String value, float x, float y, float size) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(JianYiUi.INK); paint.setTextSize(sp(size)); paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Paint.FontMetrics fm = paint.getFontMetrics(); canvas.drawText(value, x, y - (fm.ascent + fm.descent) / 2f, paint);
    }

    private int dp(float value) { return JianYiUi.dp(getContext(), value); }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
