package com.jianyi.yixue.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Static, offline study maps for the classical-reading section. */
public final class ClassicDiagramView extends View {
    private final String type;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path arrow = new Path();

    public ClassicDiagramView(Context context, String requested) {
        super(context);
        type = known(requested) ? requested : "learning_path";
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(description());
    }

    private static boolean known(String value) {
        return "learning_path".equals(value) || "classics_map".equals(value)
            || "observation_path".equals(value) || "four_lenses".equals(value)
            || "yilin_bridge".equals(value) || "nayin_memory".equals(value)
            || "body_use".equals(value) || "season_strength".equals(value);
    }

    private String description() {
        if ("body_use".equals(type)) return "体用主客示例：天风姤，五爻动。上卦乾金有动爻，为用、为客；下卦巽木无动爻，为体、为主。金克木，箭头从用金指向体木，表示用克体、体受制。体用不是固定的上卦下卦。";
        if ("season_strength".equals(type)) return "春季木当令的五态示例：木旺、火相、水休、金囚、土死。圆形节点表示五行，节点下的字表示该五行在春季的时令状态。五态不是吉凶等级，也不能单独决定结论。";
        if ("classics_map".equals(type)) return "经典阅读地图：周易经传奠定象与变，观物和易学启蒙补充象数思想，梅花易数落实起卦体用与古例，卦例复盘用于练习。";
        if ("observation_path".equals(type)) return "取象核对流程：先观察事实，再提取特征，列出候选卦象，核对问题情境与体用，最后用结果复盘。";
        if ("four_lenses".equals(type)) return "四层阅读镜头：原文回答古人怎么说，白话解释字面，要点整理结构，应用说明怎样用于卦例。";
        if ("yilin_bridge".equals(type)) return "易林取象桥：卦象经过物象、处境和行动三个层次形成可讨论的提示；易林辞只作取象训练，不替代梅花体用规则。";
        if ("nayin_memory".equals(type)) return "纳音五行速记图：子午旬用银灯架壁钩，五行为金火木土金；辰戌旬用烟满寺钟楼，五行为火水土金木；寅申旬用汉地烧柴湿，五行为水土火木水。每字管相邻两柱。";
        return "进阶学习路径：周易根基、象数思想、梅花原典、古例拆解、个人复盘，依次推进。";
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(dp(320), widthSpec);
        int wanted = "four_lenses".equals(type) ? dp(240) : dp(300);
        if ("observation_path".equals(type) || "learning_path".equals(type) || "classics_map".equals(type)) {
            int rows = "classics_map".equals(type) ? 4 : 5;
            wanted = Math.round(dp(72) + rows * Math.max(dp(46), sp(17) * 1.5f + dp(12)));
        } else if ("body_use".equals(type)) {
            wanted = Math.round(dp(64) + studyUnit() * 5f + bodyRow() * 6f);
        } else if ("season_strength".equals(type)) {
            int columns = seasonColumns(width);
            int rows = (5 + columns - 1) / columns;
            wanted = Math.round(dp(48) + studyUnit() * 2f
                + rows * (seasonRadius() * 2f + studyUnit() * 1.4f));
        }
        setMeasuredDimension(width, resolveSize(wanted, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        canvas.drawColor(JianYiUi.SOFT);
        if ("body_use".equals(type)) drawBodyUse(canvas, width);
        else if ("season_strength".equals(type)) drawSeasonStrength(canvas, width);
        else if ("four_lenses".equals(type)) drawFour(canvas, width, height);
        else if ("nayin_memory".equals(type)) drawNayin(canvas, width, height);
        else if ("classics_map".equals(type)) drawPath(canvas, width, height,
            new String[] {"周易经传", "观物·启蒙", "梅花原典", "古例练习"});
        else if ("observation_path".equals(type)) drawPath(canvas, width, height,
            new String[] {"观察事实", "提取特征", "候选卦象", "情境核对", "事后复盘"});
        else if ("yilin_bridge".equals(type)) drawBridge(canvas, width, height);
        else drawPath(canvas, width, height,
            new String[] {"周易根基", "象数思想", "梅花原典", "古例拆解", "个人复盘"});
    }

    private void drawPath(Canvas canvas, float width, float height, String[] labels) {
        String label = "observation_path".equals(type) ? "取象核对" : "阅读顺序";
        title(canvas, label, width / 2f, dp(27));
        float left = dp(20), right = width - dp(20);
        float top = dp(52), bottom = height - dp(20);
        float row = (bottom - top) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            float y = top + row * i;
            card(canvas, left, y, right, y + row - dp(12), i == labels.length - 1);
            circle(canvas, left + dp(28), y + (row - dp(12)) / 2f, i + 1);
            text(canvas, labels[i], left + dp(58), y + (row - dp(12)) / 2f, 17, JianYiUi.INK, true, Paint.Align.LEFT);
            if (i + 1 < labels.length) downArrow(canvas, width / 2f, y + row - dp(11), y + row + dp(1));
        }
    }

    private float studyUnit() { return Math.max(dp(24), sp(18)); }
    private float bodyRow() { return Math.max(dp(22), sp(16) + dp(8)); }
    private float seasonRadius() { return Math.max(dp(24), sp(18)); }
    private int seasonColumns(float width) {
        return Math.max(1, Math.min(3,
            (int) ((width - dp(32)) / (seasonRadius() * 2f + dp(16)))));
    }

    private void drawBodyUse(Canvas canvas, float width) {
        float unit = studyUnit(), row = bodyRow();
        title(canvas, "体用主客", width / 2f, dp(16) + unit * .5f);
        text(canvas, "天风姤 · 五爻动", width / 2f, dp(16) + unit * 1.5f,
            13, JianYiUi.MUTED, false, Paint.Align.CENTER);
        float top = dp(32) + unit * 2f;
        card(canvas, dp(16), top, width - dp(16), top + row * 3f - dp(4), false);
        card(canvas, dp(16), top + row * 3f + dp(4), width - dp(16), top + row * 6f, true);
        float start = width * .12f, end = width * .43f;
        float middle = (start + end) / 2f, gap = (end - start) * .22f;
        for (int i = 0; i < 6; i++) {
            float y = top + row * (i + .5f);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(5));
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setColor(i == 1 ? 0xffa5534a : JianYiUi.INK);
            if (i == 5) {
                canvas.drawLine(start, y, middle - gap / 2f, y, paint);
                canvas.drawLine(middle + gap / 2f, y, end, y, paint);
            } else canvas.drawLine(start, y, end, y, paint);
            if (i == 1) {
                paint.setStyle(Paint.Style.FILL); canvas.drawCircle(end + dp(7), y, dp(3), paint);
            }
        }
        float x = width * .73f;
        float upper = top + row * 1.5f, lower = top + row * 4.5f;
        text(canvas, "用 · 客", x, upper - unit * .45f, 16, JianYiUi.INK, true, Paint.Align.CENTER);
        text(canvas, "乾金", x, upper + unit * .45f, 14, JianYiUi.MUTED, false, Paint.Align.CENTER);
        text(canvas, "体 · 主", x, lower - unit * .45f, 16, JianYiUi.ACCENT, true, Paint.Align.CENTER);
        text(canvas, "巽木", x, lower + unit * .45f, 14, JianYiUi.MUTED, false, Paint.Align.CENTER);
        float y = top + row * 6f + dp(16) + unit;
        text(canvas, "用金", width * .17f, y, 16, JianYiUi.INK, true, Paint.Align.CENTER);
        text(canvas, "体木", width * .83f, y, 16, JianYiUi.ACCENT, true, Paint.Align.CENTER);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(0xffa5534a);
        float from = width * .30f, to = width * .70f;
        canvas.drawLine(from, y, to, y, paint);
        arrow.reset(); arrow.moveTo(to - dp(6), y - dp(5)); arrow.lineTo(to, y); arrow.lineTo(to - dp(6), y + dp(5));
        canvas.drawPath(arrow, paint);
        text(canvas, "克", width / 2f, y - unit * .55f, 14, 0xffa5534a, true, Paint.Align.CENTER);
        text(canvas, "用克体 · 体受制", width / 2f, y + unit * 1.5f, 14, JianYiUi.MUTED, false, Paint.Align.CENTER);
    }

    private void drawSeasonStrength(Canvas canvas, float width) {
        float unit = studyUnit(), radius = seasonRadius();
        title(canvas, "旺相休囚死", width / 2f, dp(16) + unit * .5f);
        text(canvas, "以春木当令为例", width / 2f, dp(16) + unit * 1.5f, 13, JianYiUi.MUTED, false, Paint.Align.CENTER);
        String[] phases = {"木", "火", "水", "金", "土"};
        String[] states = {"旺", "相", "休", "囚", "死"};
        int[] colors = {0xff3f7b5b, 0xffc85245, 0xff477a9b, 0xffa17c25, 0xffa97a3d};
        int columns = seasonColumns(width);
        float top = dp(32) + unit * 2f, rowHeight = radius * 2f + unit * 1.4f;
        for (int i = 0; i < phases.length; i++) {
            int row = i / columns, column = i % columns;
            int count = Math.min(columns, phases.length - row * columns);
            float x = dp(16) + (width - dp(32)) * (column + .5f) / count;
            float y = top + radius + row * rowHeight;
            paint.setStyle(Paint.Style.FILL); paint.setColor(i == 0 ? colors[i] : JianYiUi.WHITE);
            canvas.drawCircle(x, y, radius, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(colors[i]);
            canvas.drawCircle(x, y, radius, paint);
            if (i == 0) { paint.setStrokeWidth(dp(1)); canvas.drawCircle(x, y, radius + dp(4), paint); }
            text(canvas, phases[i], x, y, 18, i == 0 ? JianYiUi.WHITE : colors[i], true, Paint.Align.CENTER);
            text(canvas, states[i], x, y + radius + unit * .55f, 16, JianYiUi.INK, true, Paint.Align.CENTER);
        }
    }

    private void drawFour(Canvas canvas, float width, float height) {
        title(canvas, "四层阅读方法 · 示意图", width / 2f, dp(27));
        String[] names = {"原文", "白话", "要点", "应用"};
        String[] notes = {"古人怎么说", "字面说什么", "结构是什么", "卦例怎么用"};
        float gap = dp(8), left = dp(16), top = dp(55);
        float cellW = (width - dp(32) - gap) / 2f;
        float cellH = (height - top - dp(16) - gap) / 2f;
        for (int i = 0; i < 4; i++) {
            float x = left + (i % 2) * (cellW + gap);
            float y = top + (i / 2) * (cellH + gap);
            card(canvas, x, y, x + cellW, y + cellH, i == 3);
            text(canvas, names[i], x + cellW / 2f, y + cellH * .38f, 18, JianYiUi.INK, true, Paint.Align.CENTER);
            text(canvas, notes[i], x + cellW / 2f, y + cellH * .68f, 13, JianYiUi.MUTED, false, Paint.Align.CENTER);
        }
    }

    private void drawBridge(Canvas canvas, float width, float height) {
        title(canvas, "取象步骤 · 示意图", width / 2f, dp(27));
        String[] names = {"卦象", "物象", "处境", "行动"};
        float y = height * .48f, usable = width - dp(40), step = usable / 4f;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); paint.setColor(JianYiUi.LINE);
        canvas.drawLine(dp(20) + step / 2f, y, width - dp(20) - step / 2f, y, paint);
        for (int i = 0; i < names.length; i++) {
            float x = dp(20) + step * (i + .5f);
            paint.setStyle(Paint.Style.FILL); paint.setColor(i == 3 ? JianYiUi.ACCENT : JianYiUi.WHITE);
            canvas.drawCircle(x, y, dp(28), paint);
            text(canvas, names[i], x, y, 15, i == 3 ? JianYiUi.WHITE : JianYiUi.INK, true, Paint.Align.CENTER);
        }
        text(canvas, "易林帮助扩展物象语言；最终仍回到问题、体用与事实。", width / 2f, height - dp(34), 13, JianYiUi.MUTED, false, Paint.Align.CENTER);
    }

    private void drawNayin(Canvas canvas, float width, float height) {
        title(canvas, "六旬纳音五行 · 三句速记", width / 2f, dp(27));
        String[] leads = {"子午", "辰戌", "寅申"};
        String[] words = {"银 灯 架 壁 钩", "烟 满 寺 钟 楼", "汉 地 烧 柴 湿"};
        String[] phases = {"金 火 木 土 金", "火 水 土 金 木", "水 土 火 木 水"};
        float left = dp(16), right = width - dp(16), top = dp(56), row = (height - top - dp(14)) / 3f;
        for (int i = 0; i < 3; i++) {
            float y = top + i * row; card(canvas, left, y, right, y + row - dp(8), false);
            text(canvas, leads[i], left + dp(18), y + row * .34f, 17, JianYiUi.ACCENT, true, Paint.Align.LEFT);
            text(canvas, words[i], left + dp(68), y + row * .34f, 17, JianYiUi.INK, true, Paint.Align.LEFT);
            text(canvas, phases[i], left + dp(68), y + row * .66f, 13, JianYiUi.MUTED, false, Paint.Align.LEFT);
        }
    }

    private void card(Canvas canvas, float left, float top, float right, float bottom, boolean accent) {
        box.set(left, top, right, bottom);
        paint.setStyle(Paint.Style.FILL); paint.setColor(accent ? 0xffe2eee9 : JianYiUi.WHITE);
        canvas.drawRoundRect(box, dp(13), dp(13), paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(JianYiUi.LINE);
        canvas.drawRoundRect(box, dp(13), dp(13), paint);
    }

    private void circle(Canvas canvas, float x, float y, int number) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(JianYiUi.ACCENT); canvas.drawCircle(x, y, dp(16), paint);
        text(canvas, String.valueOf(number), x, y, 14, JianYiUi.WHITE, true, Paint.Align.CENTER);
    }

    private void downArrow(Canvas canvas, float x, float from, float to) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(JianYiUi.ACCENT);
        canvas.drawLine(x, from, x, to, paint);
        arrow.reset(); arrow.moveTo(x - dp(4), to - dp(4)); arrow.lineTo(x, to); arrow.lineTo(x + dp(4), to - dp(4));
        canvas.drawPath(arrow, paint);
    }

    private void title(Canvas canvas, String value, float x, float y) {
        text(canvas, value, x, y, 17, JianYiUi.INK, true, Paint.Align.CENTER);
    }

    private void text(Canvas canvas, String value, float x, float centerY, float size, int color, boolean bold, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setTextSize(sp(size)); paint.setTextAlign(align);
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(value, x, centerY - (fm.ascent + fm.descent) / 2f, paint);
    }

    private int dp(float value) { return JianYiUi.dp(getContext(), value); }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
