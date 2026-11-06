package com.jianyi.yixue.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * Readable teaching diagrams, rather than decorative emblems. All diagrams are static.
 * Use MATCH_PARENT / WRAP_CONTENT: the measured height grows for large system text.
 * Traditional direction diagrams place south at the top and east at the left.
 */
public final class KnowledgeDiagramView extends View {
    private static final String[] DIRECTIONS = {"南", "西南", "西", "西北", "北", "东北", "东", "东南"};
    // Clockwise from the top. The surrounding names and line symbols never rotate.
    private static final String[] LATER_HEAVEN = {"离", "坤", "兑", "乾", "坎", "艮", "震", "巽"};
    private static final String[] EARLIER_HEAVEN = {"乾", "巽", "坎", "艮", "坤", "震", "离", "兑"};
    private static final String[] TRIGRAM_NAMES = {"乾", "兑", "离", "震", "巽", "坎", "艮", "坤"};
    // Three bits from the bottom line to the top line, 1 = solid, 0 = broken.
    private static final int[][] TRIGRAM_BITS = {
        {1,1,1}, {1,1,0}, {1,0,1}, {1,0,0},
        {0,1,1}, {0,1,0}, {0,0,1}, {0,0,0}
    };
    private static final int[][] COMPASS_CELLS = {
        {1,0}, {2,0}, {2,1}, {2,2}, {1,2}, {0,2}, {0,1}, {0,0}
    };
    private static final String[] BRANCHES = {"午", "未", "申", "酉", "戌", "亥", "子", "丑", "寅", "卯", "辰", "巳"};
    private static final String[] BRANCH_DIRECTIONS = {
        "南", "西南偏南", "西南偏西", "西", "西北偏西", "西北偏北",
        "北", "东北偏北", "东北偏东", "东", "东南偏东", "东南偏南"
    };
    private static final String[] LINE_NAMES = {"初爻", "二爻", "三爻", "四爻", "五爻", "上爻"};
    private static final int[] TRAVELLER = {0,0,1,1,0,1}; // 艮 below, 离 above: 火山旅, bottom first.

    private final String type;
    private final boolean earlierHeaven;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics font = new Paint.FontMetrics();
    private final RectF rect = new RectF();
    private final Typeface normal = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    public KnowledgeDiagramView(Context context, String requestedType, String mode) {
        super(context);
        type = "bagua_compass".equals(requestedType) || "branches".equals(requestedType)
            || "hexagrams".equals(requestedType) ? requestedType : "polarity";
        earlierHeaven = "xiantian".equalsIgnoreCase(mode);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(description());
    }

    private String description() {
        if ("bagua_compass".equals(type)) {
            StringBuilder text = new StringBuilder(earlierHeaven ? "先天八卦传统方位图。" : "后天八卦传统方位图。");
            text.append("上南下北，左东右西。从顶部顺时针：");
            String[] names = earlierHeaven ? EARLIER_HEAVEN : LATER_HEAVEN;
            for (int i = 0; i < names.length; i++) {
                text.append(names[i]).append("在").append(DIRECTIONS[i]).append("，");
                int[] bits = bitsFor(names[i]);
                text.append("从下往上为");
                for (int bit : bits) text.append(bit == 1 ? "阳" : "阴");
                text.append("爻；");
            }
            return text.toString();
        }
        if ("branches".equals(type)) return "十二地支传统方位示意，上南下北、左东右西。顶部午在南，顺时针依次午、未、申、酉、戌、亥、子、丑、寅、卯、辰、巳；底部子在北，左侧卯在东，右侧酉在西。这是传统方位示意，不表示精确指南针角度，也不是时辰钟表。";
        if ("hexagrams".equals(type)) return "六爻结构示例：火山旅，上卦离为火，下卦艮为山。爻位从下往上数，依次为初爻阴、二爻阴、三爻阳、四爻阳、五爻阴、上爻阳。上下卦各有三爻。";
        return "阴阳爻对照：阳爻是一条完整的横线，阴爻是中间断开的两段横线。爻画中的阴阳是一种符号分类。";
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }

    private void textPaint(float size, int color, boolean strong) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(sp(size));
        paint.setTypeface(strong ? medium : normal);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    private float textHeight(float size) {
        textPaint(size, JianYiUi.INK, false);
        paint.getFontMetrics(font);
        return font.descent - font.ascent;
    }

    private float lineHeight(float size) { return textHeight(size) + dp(4); }

    private void label(Canvas canvas, String value, float x, float centerY, float size, int color, boolean strong) {
        textPaint(size, color, strong);
        paint.getFontMetrics(font);
        canvas.drawText(value, x, centerY - (font.ascent + font.descent) / 2f, paint);
    }

    private List<String> lines(String value, float maxWidth, float size) {
        textPaint(size, JianYiUi.INK, false);
        ArrayList<String> result = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (int start = 0; start < value.length();) {
            int next = start + Character.charCount(value.codePointAt(start));
            String nextCharacter = value.substring(start, next);
            if ("\n".equals(nextCharacter)) {
                result.add(line.toString());
                line.setLength(0);
            } else {
                String candidate = line.toString() + nextCharacter;
                if (line.length() > 0 && paint.measureText(candidate) > maxWidth) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                line.append(nextCharacter);
            }
            start = next;
        }
        if (line.length() > 0 || result.isEmpty()) result.add(line.toString());
        return result;
    }

    private float wrappedHeight(String value, float width) { return lines(value, width, 14).size() * lineHeight(14); }

    private float wrapped(Canvas canvas, String value, float x, float top, float width, int color, boolean strong) {
        float height = lineHeight(14);
        for (String line : lines(value, width, 14)) {
            label(canvas, line, x, top + height / 2f, 14, color, strong);
            top += height;
        }
        return top;
    }

    private float interiorWidth(float width) { return Math.max(dp(1), width - dp(24)); }
    private boolean compassList(float width) {
        textPaint(14, JianYiUi.INK, false);
        return (interiorWidth(width) - dp(12)) / 3f < paint.measureText("西北") + dp(18);
    }
    private float compassCellHeight() { return Math.max(dp(68), textHeight(16) + textHeight(14) + dp(36)); }
    private float compassRowHeight() { return Math.max(dp(48), textHeight(16) + dp(16)); }
    private String compassHeading() { return earlierHeaven ? "先天八卦 · 传统方位" : "后天八卦 · 传统方位"; }
    private String compassFooter() { return "上南下北，左东右西"; }

    private boolean branchesList(float width) {
        // A large-font fallback is a reading list, instead of shrinking or colliding labels.
        return interiorWidth(width) < sp(14) * 9.2f;
    }

    private float branchRadius(float width) { return Math.max(dp(36), Math.min(dp(108), (interiorWidth(width) - sp(16) - dp(8)) / 2f)); }

    private boolean polarityStacked(float width) {
        textPaint(14, JianYiUi.INK, false);
        return (interiorWidth(width) - dp(12)) / 2f < paint.measureText("中间断开") + dp(20);
    }

    private float polarityCardHeight() { return Math.max(dp(108), textHeight(16) + textHeight(14) + dp(56)); }

    private boolean hexagramLabelsBelow(float width) {
        textPaint(14, JianYiUi.INK, false);
        return interiorWidth(width) < paint.measureText("上爻上卦离火") + dp(104);
    }

    private float diagramHeight(float width) {
        float inner = interiorWidth(width);
        if ("bagua_compass".equals(type)) {
            float grid = compassList(width) ? compassRowHeight() * 8f : compassCellHeight() * 3f + dp(12);
            return Math.max(dp(270), dp(24) + wrappedHeight(compassHeading(), inner) + dp(12)
                + grid + dp(12) + wrappedHeight(compassFooter(), inner));
        }
        if ("branches".equals(type)) {
            float diagram = branchesList(width) ? 12f * Math.max(dp(42), textHeight(16) + dp(12))
                : 2f * branchRadius(width) + textHeight(16) + dp(14);
            return Math.max(dp(280), dp(24) + wrappedHeight("十二地支 · 传统方位示意", inner)
                + dp(12) + diagram + dp(12)
                + wrappedHeight("上南下北，左东右西\n不表示精确指南针角度", inner));
        }
        if ("hexagrams".equals(type)) {
            float row = Math.max(dp(24), textHeight(14) + dp(6));
            return Math.max(dp(180), dp(24) + wrappedHeight("火山旅（六爻示例）", inner)
                + dp(12) + 6f * row + dp(10)
                + (hexagramLabelsBelow(width) ? wrappedHeight("上卦离为火 · 下卦艮为山", inner) + dp(6) : 0)
                + wrappedHeight("爻位从下往上数", inner));
        }
        float cards = polarityCardHeight() * (polarityStacked(width) ? 2f : 1f);
        return Math.max(dp(130), dp(24) + cards + (polarityStacked(width) ? dp(12) : 0));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(Math.round(dp(320)) + getPaddingLeft() + getPaddingRight(), widthSpec);
        float innerWidth = Math.max(dp(1), width - getPaddingLeft() - getPaddingRight());
        int height = Math.round(diagramHeight(innerWidth)) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0) return;
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop() + Math.max(0f, (height - diagramHeight(width)) / 2f));
        if ("bagua_compass".equals(type)) drawCompass(canvas, width);
        else if ("branches".equals(type)) drawBranches(canvas, width);
        else if ("hexagrams".equals(type)) drawHexagram(canvas, width);
        else drawPolarity(canvas, width);
        canvas.restore();
    }

    private void card(Canvas canvas, float left, float top, float right, float bottom, boolean soft) {
        rect.set(left, top, right, bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(soft ? JianYiUi.SOFT : JianYiUi.WHITE);
        canvas.drawRoundRect(rect, dp(12), dp(12), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(JianYiUi.LINE);
        canvas.drawRoundRect(rect, dp(12), dp(12), paint);
    }

    private int[] bitsFor(String name) {
        for (int i = 0; i < TRIGRAM_NAMES.length; i++) if (TRIGRAM_NAMES[i].equals(name)) return TRIGRAM_BITS[i];
        return TRIGRAM_BITS[7];
    }

    private void yao(Canvas canvas, float centerX, float y, float width, boolean solid, int color) {
        float thickness = dp(3);
        float left = centerX - width / 2f, right = centerX + width / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        if (solid) {
            rect.set(left, y - thickness / 2f, right, y + thickness / 2f);
            canvas.drawRoundRect(rect, dp(1), dp(1), paint);
        } else {
            float gap = Math.max(dp(6), width * .19f);
            rect.set(left, y - thickness / 2f, centerX - gap / 2f, y + thickness / 2f);
            canvas.drawRoundRect(rect, dp(1), dp(1), paint);
            rect.set(centerX + gap / 2f, y - thickness / 2f, right, y + thickness / 2f);
            canvas.drawRoundRect(rect, dp(1), dp(1), paint);
        }
    }

    private void trigram(Canvas canvas, String name, float centerX, float centerY) {
        int[] bits = bitsFor(name);
        for (int bottom = 0; bottom < 3; bottom++) {
            yao(canvas, centerX, centerY + (1 - bottom) * dp(6), dp(30), bits[bottom] == 1, JianYiUi.ACCENT);
        }
    }

    private void drawCompass(Canvas canvas, float width) {
        float inner = interiorWidth(width);
        float top = wrapped(canvas, compassHeading(), width / 2f, dp(12), inner, JianYiUi.INK, true) + dp(12);
        String[] names = earlierHeaven ? EARLIER_HEAVEN : LATER_HEAVEN;
        if (compassList(width)) {
            float row = compassRowHeight();
            for (int i = 0; i < names.length; i++) {
                float centerY = top + row * (i + .5f);
                if (i % 2 == 0) card(canvas, dp(12), top + row * i, width - dp(12), top + row * (i + 1), true);
                trigram(canvas, names[i], dp(35), centerY);
                label(canvas, names[i], width * .43f, centerY, 16, JianYiUi.INK, true);
                label(canvas, DIRECTIONS[i], width - dp(28) - sp(14), centerY, 14, JianYiUi.MUTED, false);
            }
            top += row * 8f;
        } else {
            float cellWidth = (inner - dp(12)) / 3f;
            float cellHeight = compassCellHeight();
            for (int i = 0; i < names.length; i++) {
                float left = dp(12) + COMPASS_CELLS[i][0] * (cellWidth + dp(6));
                float cellTop = top + COMPASS_CELLS[i][1] * (cellHeight + dp(6));
                float centerX = left + cellWidth / 2f;
                card(canvas, left, cellTop, left + cellWidth, cellTop + cellHeight, false);
                label(canvas, names[i], centerX, cellTop + dp(7) + textHeight(16) / 2f, 16, JianYiUi.INK, true);
                trigram(canvas, names[i], centerX, cellTop + dp(18) + textHeight(16));
                label(canvas, DIRECTIONS[i], centerX, cellTop + cellHeight - dp(7) - textHeight(14) / 2f, 14, JianYiUi.MUTED, false);
            }
            float centerX = width / 2f;
            float middleTop = top + cellHeight + dp(6);
            label(canvas, earlierHeaven ? "先天" : "后天", centerX, middleTop + cellHeight * .36f, 14, JianYiUi.INK, true);
            label(canvas, "方位", centerX, middleTop + cellHeight * .70f, 14, JianYiUi.MUTED, false);
            top += cellHeight * 3f + dp(12);
        }
        wrapped(canvas, compassFooter(), width / 2f, top + dp(12), inner, JianYiUi.MUTED, false);
    }

    private void drawBranches(Canvas canvas, float width) {
        float inner = interiorWidth(width);
        float top = wrapped(canvas, "十二地支 · 传统方位示意", width / 2f, dp(12), inner, JianYiUi.INK, true) + dp(12);
        if (branchesList(width)) {
            float row = Math.max(dp(42), textHeight(16) + dp(12));
            for (int i = 0; i < BRANCHES.length; i++) {
                if (i % 2 == 0) card(canvas, dp(12), top + row * i, width - dp(12), top + row * (i + 1), true);
                label(canvas, BRANCHES[i], dp(30), top + row * (i + .5f), 16, JianYiUi.INK, true);
                label(canvas, BRANCH_DIRECTIONS[i], width * .60f, top + row * (i + .5f), 14, JianYiUi.MUTED, false);
            }
            top += row * 12f;
        } else {
            float radius = branchRadius(width);
            float centerX = width / 2f;
            float centerY = top + textHeight(16) / 2f + dp(7) + radius;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(JianYiUi.LINE);
            canvas.drawCircle(centerX, centerY, radius, paint);
            float nodeRadius = Math.max(dp(14), textHeight(16) * .62f);
            for (int i = 0; i < BRANCHES.length; i++) {
                double angle = Math.toRadians(i * 30d - 90d);
                float x = centerX + (float) Math.cos(angle) * radius;
                float y = centerY + (float) Math.sin(angle) * radius;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(JianYiUi.PAPER);
                canvas.drawCircle(x, y, nodeRadius, paint);
                label(canvas, BRANCHES[i], x, y, 16, JianYiUi.INK, true);
            }
            float offset = radius * .49f;
            label(canvas, "南", centerX, centerY - offset, 14, JianYiUi.MUTED, false);
            label(canvas, "西", centerX + offset, centerY, 14, JianYiUi.MUTED, false);
            label(canvas, "北", centerX, centerY + offset, 14, JianYiUi.MUTED, false);
            label(canvas, "东", centerX - offset, centerY, 14, JianYiUi.MUTED, false);
            label(canvas, "地支", centerX, centerY, 14, JianYiUi.INK, true);
            top += 2f * radius + textHeight(16) + dp(14);
        }
        wrapped(canvas, "上南下北，左东右西\n不表示精确指南针角度", width / 2f, top + dp(12), inner, JianYiUi.MUTED, false);
    }

    private void drawPolarityCard(Canvas canvas, float left, float top, float width, float height, boolean solid) {
        card(canvas, left, top, left + width, top + height, !solid);
        float nameY = top + dp(12) + textHeight(16) / 2f;
        label(canvas, solid ? "阳爻" : "阴爻", left + width / 2f, nameY, 16, JianYiUi.INK, true);
        yao(canvas, left + width / 2f, top + height / 2f + dp(2), Math.min(dp(100), width - dp(28)), solid, JianYiUi.ACCENT);
        label(canvas, solid ? "完整一线" : "中间断开", left + width / 2f,
            top + height - dp(12) - textHeight(14) / 2f, 14, JianYiUi.MUTED, false);
    }

    private void drawPolarity(Canvas canvas, float width) {
        float cardHeight = polarityCardHeight();
        if (polarityStacked(width)) {
            drawPolarityCard(canvas, dp(12), dp(12), interiorWidth(width), cardHeight, true);
            drawPolarityCard(canvas, dp(12), dp(24) + cardHeight, interiorWidth(width), cardHeight, false);
        } else {
            float cardWidth = (interiorWidth(width) - dp(12)) / 2f;
            drawPolarityCard(canvas, dp(12), dp(12), cardWidth, cardHeight, true);
            drawPolarityCard(canvas, dp(24) + cardWidth, dp(12), cardWidth, cardHeight, false);
        }
    }

    private void drawHexagram(Canvas canvas, float width) {
        float inner = interiorWidth(width);
        float top = wrapped(canvas, "火山旅（六爻示例）", width / 2f, dp(12), inner, JianYiUi.INK, true) + dp(12);
        float row = Math.max(dp(24), textHeight(14) + dp(6));
        boolean below = hexagramLabelsBelow(width);
        textPaint(14, JianYiUi.INK, false);
        float labelWidth = paint.measureText("上爻") + dp(12);
        float rightWidth = below ? 0f : paint.measureText("上卦") + dp(28);
        float barLeft = dp(12) + labelWidth + dp(12);
        float barWidth = Math.max(dp(28), Math.min(dp(118), inner - labelWidth - rightWidth - dp(24)));
        float barCenter = barLeft + barWidth / 2f;
        card(canvas, dp(12), top, width - dp(12), top + row * 3f, true);
        card(canvas, dp(12), top + row * 3f, width - dp(12), top + row * 6f, false);
        for (int bottom = 0; bottom < 6; bottom++) {
            float centerY = top + (5 - bottom + .5f) * row;
            label(canvas, LINE_NAMES[bottom], dp(12) + labelWidth / 2f, centerY, 14, JianYiUi.MUTED, false);
            yao(canvas, barCenter, centerY, barWidth, TRAVELLER[bottom] == 1, JianYiUi.ACCENT);
        }
        if (!below) {
            float rightCenter = width - dp(12) - rightWidth / 2f;
            float line = lineHeight(14);
            label(canvas, "上卦", rightCenter, top + row * 1.5f - line * .52f, 14, JianYiUi.MUTED, false);
            label(canvas, "离火", rightCenter, top + row * 1.5f + line * .52f, 14, JianYiUi.INK, true);
            label(canvas, "下卦", rightCenter, top + row * 4.5f - line * .52f, 14, JianYiUi.MUTED, false);
            label(canvas, "艮山", rightCenter, top + row * 4.5f + line * .52f, 14, JianYiUi.INK, true);
        }
        top += row * 6f + dp(10);
        if (below) top = wrapped(canvas, "上卦离为火 · 下卦艮为山", width / 2f, top, inner, JianYiUi.INK, false) + dp(6);
        wrapped(canvas, "爻位从下往上数", width / 2f, top, inner, JianYiUi.MUTED, false);
    }
}
