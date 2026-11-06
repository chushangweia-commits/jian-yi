package com.jianyi.yixue.app;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONObject;

/** Read-only view of the conversations attached to this notebook's stable identity. */
final class AiNotebookPanel {
    private AiNotebookPanel() { }

    static void render(Activity activity, LinearLayout parent, List<AiReadingStore.Session> sessions) {
        parent.removeAllViews();
        LinearLayout panel = panel(activity, parent);
        panel.addView(text(activity, "AI 解读与追问", 19, true));
        int completed = 0;
        for (AiReadingStore.Session session : sessions) completed += session.history.length() / 2;
        if (completed == 0) {
            panel.addView(text(activity, "还没有已完成的 AI 解读。点击上方“AI 解卦”发送问题，完成后会自动显示在这里。", 15, false));
        } else {
            panel.addView(text(activity, "这条卦例的解读和追问会自动保留在这里，点开对话查看。", 15, false));
        }
        for (AiReadingStore.Session session : sessions) addSession(activity, panel, session);
        if (sessions.size() >= AiReadingStore.NOTEBOOK_SESSION_LIMIT) {
            panel.addView(text(activity, "这里显示最近20次对话；更早的记录可在 AI 解卦的“本机对话”中查看。", 13, false));
        }
    }

    static void showUnavailable(Activity activity, LinearLayout parent) {
        parent.removeAllViews();
        LinearLayout panel = panel(activity, parent);
        panel.addView(text(activity, "AI 解读与追问", 19, true));
        panel.addView(text(activity, "AI 对话暂时未能读取，请返回这条卦例重试。笔记内容仍保留。", 15, false));
    }

    private static void addSession(final Activity activity, LinearLayout panel, final AiReadingStore.Session session) {
        final int rounds = session.history.length() / 2;
        panel.addView(text(activity, date(session.updatedAt) + " · " + rounds + " 轮问答", 13, false));
        if(session.subject.length()>0)panel.addView(text(activity,"创建对话时的卦例问题："+session.subject,14,true));
        if(rounds>0){JSONObject initial=session.history.optJSONObject(0);if(initial!=null)panel.addView(text(activity,"本次首次提问："+initial.optString("content",""),14,true));}
        final LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        JSONObject loadedFacts = null;
        final boolean liuyao = session.method.startsWith("六爻");
        try { if (!liuyao) loadedFacts = AiReadingContext.facts(activity, session.upper, session.lower, session.moving, session.method); }
        catch (Exception ignored) { }
        final JSONObject facts = loadedFacts;
        if (liuyao) body.addView(text(activity, "解卦体系 · 六爻纳甲\n依据此卦例保存的用神、世应、月日动变排盘。", 15, true));
        if (facts != null) {
            body.addView(text(activity, "解卦体系 · " + AiReadingContext.systemName(activity, facts), 15, true));
            body.addView(text(activity, AiReadingContext.basisSummary(activity, facts), 14, false));
            final JSONObject base = facts.optJSONObject("base"), changed = facts.optJSONObject("changed");
            if (base != null) body.addView(action(activity, "查看本卦原文 · " + base.optString("name"), new View.OnClickListener() {
                @Override public void onClick(View view) { StudyModules.showClassicByCode(activity, base.optString("code")); }
            }));
            if (changed != null && !changed.optString("code").equals(base == null ? "" : base.optString("code"))) {
                body.addView(action(activity, "查看变卦原文 · " + changed.optString("name"), new View.OnClickListener() {
                    @Override public void onClick(View view) { StudyModules.showClassicByCode(activity, changed.optString("code")); }
                }));
            }
        }
        if (rounds == 0) {
            body.addView(text(activity, "这次对话尚未完成回答。", 15, false));
            if (session.draft.trim().length() > 0) body.addView(text(activity, "待发送问题：\n" + session.draft, 16, false));
        } else {
            for (int i = 0; i < session.history.length(); i += 2) {
                JSONObject question = session.history.optJSONObject(i);
                JSONObject answer = session.history.optJSONObject(i + 1);
                body.addView(text(activity, "第 " + (i / 2 + 1) + " 轮 · 你问", 16, true));
                body.addView(text(activity, question.optString("content", ""), 16, false));
                body.addView(text(activity, "AI 解读", 16, true));
                String answerText = answer.optString("content", "");
                body.addView(text(activity, answerText, 16, false));
                if (facts != null) body.addView(text(activity, AiReadingContext.verificationText(activity, facts, answerText), 12, true));
                else if (liuyao) body.addView(text(activity, AiAnswerPolicy.LIUYAO.issue(answerText).length() == 0 ? "本机已核验 · 六爻体系与依据标签" : "旧回答未通过当前六爻核验", 12, true));
            }
        }
        if (session.reference.startsWith("note:") && liuyao) body.addView(action(activity, "继续这次六爻对话", new View.OnClickListener() {
            @Override public void onClick(View v) { LiuYaoActivity.open(activity, session.reference.substring(5), session.id); }
        }));
        final Button toggle = new Button(activity);
        final String collapsed = rounds == 0 ? "查看待完成对话" : "展开这次对话 · " + rounds + " 轮";
        toggle.setText(collapsed); JianYiUi.skinButton(toggle, false);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean expand = body.getVisibility() != View.VISIBLE;
                body.setVisibility(expand ? View.VISIBLE : View.GONE);
                toggle.setText(expand ? "收起这次对话" : collapsed);
            }
        });
        LinearLayout.LayoutParams buttonLayout = new LinearLayout.LayoutParams(-1, -2);
        buttonLayout.bottomMargin = JianYiUi.dp(activity, 10);
        panel.addView(toggle, buttonLayout);
        panel.addView(body);
    }

    private static LinearLayout panel(Activity activity, LinearLayout parent) {
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        JianYiUi.surface(panel);
        int padding = JianYiUi.dp(activity, 16); panel.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.bottomMargin = JianYiUi.dp(activity, 14); parent.addView(panel, layout);
        return panel;
    }

    private static TextView text(Activity activity, String value, int size, boolean heading) {
        TextView text = new TextView(activity); text.setText(value); JianYiUi.textStyle(text, size, heading);
        text.setLineSpacing(JianYiUi.dp(activity, 5), 1);
        text.setPadding(0, 0, 0, JianYiUi.dp(activity, 12));
        if (!heading) { text.setTextColor(JianYiUi.MUTED); text.setTextIsSelectable(true); }
        return text;
    }

    private static Button action(Activity activity, String label, View.OnClickListener listener) {
        Button button = new Button(activity); button.setText(label); JianYiUi.skinButton(button, false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.bottomMargin = JianYiUi.dp(activity, 8); button.setLayoutParams(layout);
        return button;
    }

    private static String date(long at) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return format.format(new Date(at));
    }
}
