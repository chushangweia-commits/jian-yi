package com.jianyi.yixue.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;

/**
 * Small native, offline knowledge emblems; the symbols do not depend on animation.
 * Variants: Hetu / Luoshu / Wuxing (case insensitive). Unknown variants use Luoshu.
 * Designed for a 92 dp hero or a 180 dp explanatory panel. The larger panel adds
 * labels without changing the underlying geometry or the number of dots.
 *
 * River/Lo diagrams use the traditional south-up, east-left orientation.
 * Hollow dots are odd (yang), filled dots even (yin); this follows the National
 * Palace Museum's description of its Ming "河圖" ink cake. The ten-number river
 * diagram follows the arrangement recorded in Zhang Yuchu's 河圖原, and the
 * nine-number Lo square also matches the original local exercise's layout.
 * The five-phase emblem is a generation sequence, not a geographic diagram.
 */
public final class KnowledgeMotifView extends View {
    public static final String HETU = "Hetu";
    public static final String LUOSHU = "Luoshu";
    public static final String WUXING = "Wuxing";
    private static final long ANIMATION_MILLIS = 4800L;

    // Rows, top to bottom; columns, left to right. Every row, column and diagonal is 15.
    private static final int[] LO_NUMBERS = {4, 9, 2, 3, 5, 7, 8, 1, 6};
    // Inner/outer pairs at north (bottom), south (top), east (left), west (right), centre.
    private static final int[][] RIVER_NUMBERS = {{1, 6}, {2, 7}, {3, 8}, {4, 9}, {5, 10}};
    private static final String[] PHASE_NAMES = {"木", "火", "土", "金", "水"};
    private static final int[] PHASE_COLORS = {
        0xff2e6b5b, 0xffa86554, 0xff9d824f, 0xffb69658, 0xff507c91
    };

    private final String variant;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private final RectF oval = new RectF();
    private final Rect visibleBounds = new Rect();
    private final Path arrow = new Path();
    private final SharedPreferences visualSettings;
    private final ContentObserver scaleObserver;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesListener;
    private final ViewTreeObserver.OnScrollChangedListener scrollListener;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener;
    private ViewTreeObserver watchedTree;
    private ValueAnimator animation;
    private boolean attached;
    private boolean observing;
    private boolean played;
    private float progress = 1f;
    private long lastFrameMillis;

    public KnowledgeMotifView(Context context, String requestedVariant) {
        super(context);
        variant = HETU.equalsIgnoreCase(requestedVariant) ? HETU
            : WUXING.equalsIgnoreCase(requestedVariant) ? WUXING : LUOSHU;
        setContentDescription(descriptionFor(variant));
        // The surrounding heading carries the context; this ornament adds no focus stop.
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        setClickable(false);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        visualSettings = context.getSharedPreferences("guest_visual_settings_v1", Context.MODE_PRIVATE);
        scaleObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) { syncAnimation(); }
        };
        preferencesListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
                if ("animations_enabled".equals(key)) syncAnimation();
            }
        };
        scrollListener = new ViewTreeObserver.OnScrollChangedListener() {
            @Override public void onScrollChanged() { syncAnimation(); }
        };
        layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() { syncAnimation(); }
        };
    }

    private static String descriptionFor(String variant) {
        if (HETU.equals(variant)) return "河图点阵装饰，上南下北、左东右西：一六居北、二七居南、三八居东、四九居西、五十居中；奇数空心、偶数实心";
        if (WUXING.equals(variant)) return "五行相生装饰：沿顺时针依次为木生火、火生土、土生金、金生水、水生木；不表示方位";
        return "洛书点阵装饰，上南下北、左东右西：上排四九二，中排三五七，下排八一六；横竖及两条对角线之和均为十五；奇数空心、偶数实心";
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int preferred = JianYiUi.dp(getContext(), 92);
        setMeasuredDimension(resolveSize(preferred + getPaddingLeft() + getPaddingRight(), widthSpec),
            resolveSize(preferred + getPaddingTop() + getPaddingBottom(), heightSpec));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        watchedTree = getViewTreeObserver();
        watchedTree.addOnScrollChangedListener(scrollListener);
        watchedTree.addOnGlobalLayoutListener(layoutListener);
        visualSettings.registerOnSharedPreferenceChangeListener(preferencesListener);
        try {
            getContext().getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, scaleObserver);
            observing = true;
        } catch (RuntimeException ignored) { observing = false; }
        syncAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        stopAnimation();
        if (watchedTree != null && watchedTree.isAlive()) {
            watchedTree.removeOnScrollChangedListener(scrollListener);
            watchedTree.removeOnGlobalLayoutListener(layoutListener);
        }
        watchedTree = null;
        visualSettings.unregisterOnSharedPreferenceChangeListener(preferencesListener);
        if (observing) {
            try { getContext().getContentResolver().unregisterContentObserver(scaleObserver); }
            catch (RuntimeException ignored) { }
            observing = false;
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        syncAnimation();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        syncAnimation();
    }

    private void syncAnimation() {
        // Visibility callbacks can occur before this subclass's constructor has finished.
        if (!attached) return;
        if (!isShown() || getWindowVisibility() != VISIBLE || !getGlobalVisibleRect(visibleBounds)
            || !JianYiUi.animationsEnabled(getContext())) {
            stopAnimation();
            return;
        }
        if (played || animation != null) return;
        played = true;
        progress = 0f;
        animation = ValueAnimator.ofFloat(0f, 1f);
        animation.setDuration(ANIMATION_MILLIS);
        animation.setRepeatCount(0);
        animation.setInterpolator(new LinearInterpolator());
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator animator) {
                long now = SystemClock.uptimeMillis();
                if (now - lastFrameMillis < 40L) return;
                lastFrameMillis = now;
                progress = ((Float) animator.getAnimatedValue()).floatValue();
                invalidate();
            }
        });
        animation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animator) {
                if (animation == animator) {
                    animation.removeAllUpdateListeners();
                    animation.removeAllListeners();
                    animation = null;
                    progress = 1f;
                    invalidate();
                }
            }
        });
        animation.start();
    }

    private void stopAnimation() {
        if (animation != null) {
            ValueAnimator previous = animation;
            animation = null;
            previous.removeAllUpdateListeners();
            previous.removeAllListeners();
            previous.cancel();
        }
        progress = 1f;
        lastFrameMillis = 0L;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min(width, height);
        if (size <= 0f) return;
        boolean detailed = size / getResources().getDisplayMetrics().density >= 140f;
        canvas.save();
        canvas.translate(getPaddingLeft() + width / 2f, getPaddingTop() + height / 2f);
        canvas.scale(size / 92f, size / 92f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(JianYiUi.SOFT);
        canvas.drawCircle(0f, 0f, 44f, paint);
        if (HETU.equals(variant)) drawRiver(canvas, detailed);
        else if (WUXING.equals(variant)) drawPhases(canvas, detailed);
        else drawLo(canvas, detailed);
        paint.setAlpha(255);
        canvas.restore();
    }

    private void drawRiver(Canvas canvas, boolean detailed) {
        // All 55 dots remain visible, including throughout the entrance accent.
        canvas.save();
        if (detailed) canvas.scale(0.84f, 0.84f); // Reserve a clear outer lane for direction labels.
        dots(canvas, RIVER_NUMBERS[0][0], 0f, 23f);
        dots(canvas, RIVER_NUMBERS[0][1], 0f, 34f);
        dots(canvas, RIVER_NUMBERS[1][0], 0f, -23f);
        dots(canvas, RIVER_NUMBERS[1][1], 0f, -34f);
        dots(canvas, RIVER_NUMBERS[2][0], -23f, 0f);
        dots(canvas, RIVER_NUMBERS[2][1], -34f, 0f);
        dots(canvas, RIVER_NUMBERS[3][0], 21f, 0f);
        dots(canvas, RIVER_NUMBERS[3][1], 34f, 0f);
        dots(canvas, RIVER_NUMBERS[4][0], 0f, 0f);
        // Ten forms a two-by-five frame around the central five.
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 5; col++) dot(canvas, (col - 2) * 4.4f, row == 0 ? -9f : 9f, false);
        }
        canvas.restore();
        drawEntranceAccent(canvas);
        if (detailed) {
            label(canvas, "南", 0f, -39.7f, 5.2f, JianYiUi.MUTED);
            label(canvas, "北", 0f, 39.7f, 5.2f, JianYiUi.MUTED);
            label(canvas, "东", -39.7f, 0f, 5.2f, JianYiUi.MUTED);
            label(canvas, "西", 39.7f, 0f, 5.2f, JianYiUi.MUTED);
        }
    }

    private void drawLo(Canvas canvas, boolean detailed) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.55f);
        paint.setColor(JianYiUi.LINE);
        // An understated nine-palace grid, not an invented connection between numbers.
        for (int i = 0; i < 2; i++) {
            float offset = i == 0 ? -13f : 13f;
            canvas.drawLine(offset, -37f, offset, 37f, paint);
            canvas.drawLine(-37f, offset, 37f, offset, paint);
        }
        for (int cell = 0; cell < LO_NUMBERS.length; cell++) {
            float x = (cell % 3 - 1) * 26f;
            float y = (cell / 3 - 1) * 26f;
            dots(canvas, LO_NUMBERS[cell], x, y);
            if (detailed) label(canvas, String.valueOf(LO_NUMBERS[cell]), x + 9f, y + 8.5f, 4.6f, JianYiUi.MUTED);
        }
        drawEntranceAccent(canvas);
    }

    /** Traditional dice-like dot shapes; every call draws exactly n non-overlapping dots. */
    private void dots(Canvas canvas, int number, float x, float y) {
        boolean hollow = (number & 1) != 0;
        if (number == 1) { dot(canvas, x, y, hollow); return; }
        if (number == 2) {
            dot(canvas, x - 2.2f, y, hollow); dot(canvas, x + 2.2f, y, hollow); return;
        }
        if (number == 3) {
            for (int i = -1; i <= 1; i++) dot(canvas, x, y + i * 4.4f, hollow);
            return;
        }
        if (number == 4 || number == 5) {
            for (int row = -1; row <= 1; row += 2) {
                for (int col = -1; col <= 1; col += 2) dot(canvas, x + col * 3.1f, y + row * 3.1f, hollow);
            }
            if (number == 5) dot(canvas, x, y, hollow);
            return;
        }
        int cols = number == 6 || number == 8 ? 2 : 3;
        int rows = number == 8 ? 4 : 3;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                // Seven is a 3-by-3 group with the left/right middle points omitted.
                if (number == 7 && row == 1 && col != 1) continue;
                dot(canvas, x + (col - (cols - 1) / 2f) * 4.4f,
                    y + (row - (rows - 1) / 2f) * 4.4f, hollow);
            }
        }
    }

    private void dot(Canvas canvas, float x, float y, boolean hollow) {
        paint.setColor(JianYiUi.INK);
        paint.setStrokeWidth(0.85f);
        paint.setStyle(hollow ? Paint.Style.STROKE : Paint.Style.FILL);
        canvas.drawCircle(x, y, hollow ? 1.5f : 1.65f, paint);
    }

    /** The accent moves on a separate border; the positional diagrams never rotate. */
    private void drawEntranceAccent(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.75f);
        paint.setColor(JianYiUi.GOLD);
        paint.setAlpha(105);
        canvas.drawCircle(0f, 0f, 43.3f, paint);
        if (progress < 1f) {
            float fade = (float) Math.sin(progress * Math.PI);
            paint.setAlpha((int) (160f * fade));
            paint.setStrokeWidth(1.4f);
            oval.set(-43.3f, -43.3f, 43.3f, 43.3f);
            canvas.drawArc(oval, -90f + progress * 360f, 22f, false, paint);
        }
        paint.setAlpha(255);
    }

    private void drawPhases(Canvas canvas, boolean detailed) {
        // Clockwise: wood -> fire -> earth -> metal -> water -> wood.
        // This is the generation order, deliberately not the five directional positions.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f);
        paint.setColor(JianYiUi.LINE);
        canvas.drawCircle(0f, 0f, 30f, paint);
        for (int phase = 0; phase < PHASE_NAMES.length; phase++) {
            float degrees = -90f + phase * 72f;
            float x = (float) Math.cos(Math.toRadians(degrees)) * 30f;
            float y = (float) Math.sin(Math.toRadians(degrees)) * 30f;
            arrowAt(canvas, degrees + 36f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(JianYiUi.WHITE);
            canvas.drawCircle(x, y, 9.6f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(PHASE_COLORS[phase]);
            canvas.drawCircle(x, y, 9.6f, paint);
            label(canvas, PHASE_NAMES[phase], x, y, 10f, JianYiUi.INK);
        }
        if (detailed) label(canvas, "相生", 0f, 0f, 7f, JianYiUi.MUTED);
        else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(JianYiUi.GOLD);
            canvas.drawCircle(0f, 0f, 1.4f, paint);
        }
        if (progress < 1f) {
            float degrees = -90f + progress * 360f;
            float x = (float) Math.cos(Math.toRadians(degrees)) * 30f;
            float y = (float) Math.sin(Math.toRadians(degrees)) * 30f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(JianYiUi.GOLD);
            paint.setAlpha((int) (140f * Math.sin(progress * Math.PI)));
            canvas.drawCircle(x, y, 3.4f, paint);
            paint.setAlpha(255);
        }
    }

    private void arrowAt(Canvas canvas, float degrees) {
        canvas.save();
        canvas.rotate(degrees);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(JianYiUi.MUTED);
        paint.setStrokeWidth(0.8f);
        arrow.reset();
        // At (30, 0), clockwise motion is downwards in screen coordinates.
        arrow.moveTo(27.8f, -1.8f);
        arrow.lineTo(30f, 1.4f);
        arrow.lineTo(32.2f, -1.8f);
        canvas.drawPath(arrow, paint);
        canvas.restore();
    }

    private void label(Canvas canvas, String text, float x, float y, float textSize, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(textSize);
        paint.getFontMetrics(metrics);
        canvas.drawText(text, x, y - (metrics.ascent + metrics.descent) / 2f, paint);
    }
}
