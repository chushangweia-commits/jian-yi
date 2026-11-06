package com.jianyi.yixue.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Native home and navigation for the standalone JianYi application. */
public final class JianYiHome {
    private static final String TITLE = "简易";
    private static final int PAPER = JianYiUi.PAPER;
    private static final int INK = JianYiUi.INK;
    private static final int MUTED = JianYiUi.MUTED;
    private static final int ACCENT = JianYiUi.ACCENT;
    private static final int LINE = JianYiUi.LINE;
    private static final int WHITE = 0xffffffff;

    private static WeakReference<Activity> dashboard = new WeakReference<Activity>(null);
    private static String dashboardPage = "home";

    private JianYiHome() { }

    public static void render(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        dashboard = new WeakReference<Activity>(activity);
        showPage(activity, "home");
    }

    /** Refreshes the dashboard after returning from a child Activity. */
    public static void onResume(Activity activity) {
        if (activity == null || activity.isFinishing() || dashboard.get() != activity) return;
        if ("library".equals(dashboardPage) || "meihua".equals(dashboardPage)) return;
        showPage(activity, dashboardPage);
    }

    public static boolean onBackPressed(Activity activity) {
        if (dashboard.get() == activity && !"home".equals(dashboardPage)) {
            showPage(activity, "home");
            return true;
        }
        return false;
    }

    private static void openLibrary(Activity activity) {
        dashboardPage = "library";
        StudyModules.showLibrary(activity);
    }

    private static void openMeihua(Activity activity) {
        dashboardPage = "meihua";
        StudyModules.showMeihua(activity);
    }

    private static void showPage(final Activity activity, String page) {
        if (activity == null || activity.isFinishing()) return;
        if ("practice".equals(page)) {
            dashboardPage = "home";
            FoundationPracticeActivity.open(activity);
            return;
        }
        dashboardPage = page;
        if ("casting".equals(page)) {
            showCasting(activity);
            return;
        }
        showHome(activity);
    }

    // Warm paper and restrained vermilion recall the selected annotated-classic direction.
    private static final int SCROLL_PAPER = 0xfff8f3e8;
    private static final int SCROLL_BORDER = 0xffded2bd;
    private static final int VERMILION = 0xffa94836;

    private static void showHome(final Activity activity) {
        dashboardPage = "home";
        LinearLayout root = dashboardScreen(activity, "首页");
        root.addView(scrollCover(activity, "简易", "观象 · 明理", "慢慢学，把易理带进生活。"));
        sectionLabel(activity, root, "研习目录", "按兴趣开始");
        LinearLayout directory = paperGroup(activity);
        directory.addView(editorialRow(activity, "一", "梅花学习", "入门课程与案例", new View.OnClickListener() {
            @Override public void onClick(View view) { activity.startActivity(new Intent(activity, LearningActivity.class)); }
        }));
        addHairline(activity, directory);
        directory.addView(editorialRow(activity, "二", "进阶资料", "象数专题与原典", new View.OnClickListener() {
            @Override public void onClick(View view) { MeihuaReferenceActivity.open(activity); }
        }));
        addHairline(activity, directory);
        directory.addView(editorialRow(activity, "三", "八字研习", "排盘、解读与学习", new View.OnClickListener() {
            @Override public void onClick(View view) { BaziActivity.open(activity); }
        }));
        addHairline(activity, directory);
        directory.addView(editorialRow(activity, "四", "起卦", "梅花、六爻与大衍", new View.OnClickListener() {
            @Override public void onClick(View view) { showPage(activity, "casting"); }
        }));
        root.addView(directory);

        sectionLabel(activity, root, "阅读与记录", "随时翻阅");
        LinearLayout reading = paperGroup(activity);
        reading.addView(editorialRow(activity, "卦", "六十四卦", "卦爻辞与详解", new View.OnClickListener() {
            @Override public void onClick(View view) { openLibrary(activity); }
        }));
        addHairline(activity, reading);
        reading.addView(editorialRow(activity, "记", "卦例本", "记录与复盘", new View.OnClickListener() {
            @Override public void onClick(View view) { NotebookActivity.openList(activity); }
        }));
        addHairline(activity, reading);
        reading.addView(editorialRow(activity, "习", "基础习练", "八卦、五行与干支", new View.OnClickListener() {
            @Override public void onClick(View view) { showPage(activity, "practice"); }
        }));
        root.addView(reading);
        quote(activity, root, "一阴一阳之谓道。", "系辞上传");

        sectionLabel(activity, root, "工具与设置", "按需调整");
        LinearLayout settings = paperGroup(activity);
        final SharedPreferences visual = activity.getSharedPreferences("guest_visual_settings_v1", Context.MODE_PRIVATE);
        String animationLabel = visual.getBoolean("animations_enabled", true) ? "动效已开启，点击关闭" : "动效已关闭，点击开启";
        settings.addView(editorialRow(activity, "联", "AI 联网设置", "使用时由你选择联网", new View.OnClickListener() {
            @Override public void onClick(View view) { AiReadingActivity.settings(activity); }
        }));
        addHairline(activity, settings);
        settings.addView(editorialRow(activity, "动", "动画设置", animationLabel, new View.OnClickListener() {
            @Override public void onClick(View view) {
                visual.edit().putBoolean("animations_enabled", !visual.getBoolean("animations_enabled", true)).apply();
                showPage(activity, "home");
            }
        }));
        root.addView(settings);
    }

    private static void showCasting(final Activity activity) {
        dashboardPage = "casting";
        LinearLayout root = dashboardScreen(activity, "起卦");
        root.addView(scrollCover(activity, "选择起卦方式", "静心而问", "先想清楚想了解的问题，再选择适合的方法。"));
        sectionLabel(activity, root, "三种起卦方式", "各自独立排盘");
        LinearLayout methods = paperGroup(activity);
        methods.addView(editorialRow(activity, "梅", "梅花易数", "时间、数字、测字与手动排盘", new View.OnClickListener() {
            @Override public void onClick(View view) { openMeihua(activity); }
        }));
        addHairline(activity, methods);
        methods.addView(editorialRow(activity, "爻", "六爻起卦", "铜钱起卦与手动录入", new View.OnClickListener() {
            @Override public void onClick(View view) { activity.startActivity(new Intent(activity, LiuYaoActivity.class)); }
        }));
        addHairline(activity, methods);
        methods.addView(editorialRow(activity, "策", "大衍筮法", "四十九策，循古法起卦", new View.OnClickListener() {
            @Override public void onClick(View view) { activity.startActivity(new Intent(activity, DayanActivity.class)); }
        }));
        root.addView(methods);
        quote(activity, root, "大衍之数五十，其用四十有九。", "系辞上传");
        sectionLabel(activity, root, "起卦前", "先定心，再发问");
        LinearLayout note = paperGroup(activity);
        TextView noteText = text(activity, "一次专注一件事，写下想了解的问题与关注的时间范围。", 15, INK, false);
        noteText.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 8));
        note.addView(noteText);
        root.addView(note);
        root.addView(button(activity, "打开卦例本", new View.OnClickListener() {
            @Override public void onClick(View view) { NotebookActivity.openList(activity); }
        }, false));
        root.addView(button(activity, "AI 联网设置 · DeepSeek", new View.OnClickListener() {
            @Override public void onClick(View view) { AiReadingActivity.settings(activity); }
        }, false));
    }

    private static View scrollCover(Activity activity, String title, String kicker, String subtitle) {
        LinearLayout cover = paperGroup(activity);
        cover.setOrientation(LinearLayout.HORIZONTAL);
        cover.setGravity(Gravity.CENTER_VERTICAL);
        cover.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 14), dp(activity, 18));
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text(activity, kicker, 13, VERMILION, true);
        eyebrow.setLetterSpacing(0.12f);
        column.addView(eyebrow);
        TextView name = text(activity, title, 25, INK, true);
        name.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        name.setTextSize("简易".equals(title) ? 30 : 22);
        column.addView(name);
        TextView line = text(activity, subtitle, 14, MUTED, false);
        line.setLineSpacing(dp(activity, 2), 1f);
        column.addView(line);
        cover.addView(column, new LinearLayout.LayoutParams(0, -2, 1f));
        View diagram = new JianYiUi.TaijiView(activity);
        LinearLayout.LayoutParams diagramParams = new LinearLayout.LayoutParams(dp(activity, 104), dp(activity, 104));
        diagramParams.leftMargin = dp(activity, 8);
        cover.addView(diagram, diagramParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, 10);
        cover.setLayoutParams(params);
        return cover;
    }

    private static LinearLayout paperGroup(Activity activity) {
        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(surface(SCROLL_PAPER, SCROLL_BORDER, activity));
        return group;
    }

    private static void sectionLabel(Activity activity, LinearLayout root, String title, String hint) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(dp(activity, 2), dp(activity, 20), dp(activity, 2), dp(activity, 8));
        TextView name = text(activity, title, 19, INK, true);
        name.setTypeface(Typeface.create("serif", Typeface.BOLD));
        name.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(name);
        TextView note = text(activity, hint, 12, MUTED, false);
        note.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        row.addView(note);
        root.addView(row);
    }

    private static View editorialRow(Activity activity, String number, String title, String subtitle, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 15), dp(activity, 10), dp(activity, 15), dp(activity, 10));
        row.setMinimumHeight(dp(activity, 68));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(click);
        row.setContentDescription(title + "，" + subtitle);
        JianYiUi.pressFeedback(row);
        TextView marker = text(activity, number, 15, VERMILION, true);
        marker.setGravity(Gravity.CENTER);
        marker.setBackground(surface(0xfff1e5d5, 0xffdfbcb0, activity));
        marker.setLayoutParams(new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36)));
        marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(marker);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, -2, 1f);
        columnParams.leftMargin = dp(activity, 13);
        TextView name = text(activity, title, 17, INK, true);
        name.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        column.addView(name);
        TextView detail = text(activity, subtitle, 13, MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(activity, 2);
        detail.setLayoutParams(detailParams);
        column.addView(detail);
        row.addView(column, columnParams);
        TextView arrow = text(activity, "›", 20, VERMILION, false);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow);
        return row;
    }

    private static void addHairline(Activity activity, LinearLayout group) {
        View line = new View(activity);
        line.setBackgroundColor(SCROLL_BORDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 1));
        params.leftMargin = dp(activity, 64);
        params.rightMargin = dp(activity, 15);
        group.addView(line, params);
    }

    private static void quote(Activity activity, LinearLayout root, String value, String attribution) {
        LinearLayout block = paperGroup(activity);
        block.setPadding(dp(activity, 16), dp(activity, 13), dp(activity, 16), dp(activity, 10));
        TextView sentence = text(activity, value, 16, INK, false);
        sentence.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        block.addView(sentence);
        TextView source = text(activity, "《" + attribution + "》", 12, VERMILION, false);
        source.setGravity(Gravity.RIGHT);
        block.addView(source);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, 16);
        root.addView(block, params);
    }

    private static LinearLayout dashboardScreen(final Activity activity, final String selected) {
        LinearLayout frame = new LinearLayout(activity);
        frame.setOrientation(LinearLayout.VERTICAL);
        JianYiUi.applyScenic(frame,"首页".equals(selected)?JianYiUi.SCENE_HOME:"起卦".equals(selected)?JianYiUi.SCENE_DIVINATION:JianYiUi.SCENE_READING);
        frame.setFitsSystemWindows(true);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), dp(activity, 24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        frame.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        View divider = new View(activity);
        divider.setBackgroundColor(LINE);
        frame.addView(divider, new LinearLayout.LayoutParams(-1, dp(activity, 1)));

        LinearLayout navigation = new LinearLayout(activity);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        navigation.setBackgroundColor(WHITE);
        final String[] labels = {"首页", "阅读", "起卦", "练习"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button item = button(activity, labels[i], new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (index == 0) showPage(activity, "home");
                    else if (index == 1) openLibrary(activity);
                    else if (index == 2) showPage(activity, "casting");
                    else showPage(activity, "practice");
                }
            }, labels[i].equals(selected));
            item.setTextSize(14);
            item.setPadding(dp(activity, 6), dp(activity, 8), dp(activity, 6), dp(activity, 8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(activity, 48), 1f);
            params.setMargins(dp(activity, 3), 0, dp(activity, 3), 0);
            navigation.addView(item, params);
        }
        frame.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        activity.setContentView(frame);
        JianYiUi.applySystemBars(activity);
        JianYiUi.enter(content);
        return content;
    }

    private static View featureCard(final Activity activity, String title, String detail,
                                    String emblem, boolean dark, View.OnClickListener action) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 15), dp(activity, 13), dp(activity, 15), dp(activity, 13));
        card.setBackground(surface(dark ? INK : 0xf2ffffff, dark ? 0 : LINE, activity));
        card.setMinimumHeight(dp(activity, 112));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(action);
        card.setContentDescription(title);
        JianYiUi.pressFeedback(card);

        TextView symbol = text(activity, emblem, 18, dark ? 0xffd9c69c : JianYiUi.GOLD, false);
        symbol.setTextSize(20);
        symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(symbol);
        TextView name = text(activity, title, 19, dark ? WHITE : INK, true);
        name.setTextSize(18);
        card.addView(name);
        TextView copy = text(activity, detail, 13, dark ? 0xffd5e2d8 : MUTED, false);
        copy.setTextSize(12);
        copy.setLineSpacing(dp(activity, 2), 1f);
        card.addView(copy);
        if (Build.VERSION.SDK_INT >= 23) {
            GradientDrawable mask = surface(WHITE, 0, activity);
            card.setForeground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(dark ? 0x25ffffff : 0x1f2e6b5b), null, mask));
        }
        return card;
    }

    private static void heading(Activity activity, LinearLayout root, String value) {
        TextView heading = text(activity, value, 21, INK, true);
        heading.setPadding(0, dp(activity, 18), 0, dp(activity, 3));
        root.addView(heading);
    }

    private static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(bold ? Math.min(size, 24) : Math.min(size, 16));
        view.setTextColor(color);
        view.setLineSpacing(dp(activity, 3), 1f);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(activity, 10);
        view.setLayoutParams(params);
        return view;
    }

    private static Button button(Activity activity, String value, View.OnClickListener listener, boolean primary) {
        Button view = new Button(activity);
        view.setText(value);
        view.setAllCaps(false);
        view.setTextSize(18);
        view.setTextColor(primary ? WHITE : INK);
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        view.setMinHeight(dp(activity, 56));
        view.setMinimumHeight(dp(activity, 56));
        view.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12));
        view.setBackground(surface(primary ? ACCENT : WHITE, primary ? 0 : LINE, activity));
        JianYiUi.skinButton(view, primary);
        JianYiUi.pressFeedback(view);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(activity, 8);
        view.setLayoutParams(params);
        view.setOnClickListener(listener);
        return view;
    }

    private static GradientDrawable surface(int color, int stroke, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, 12));
        if (stroke != 0) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
