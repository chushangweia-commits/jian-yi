package com.jianyi.yixue.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;

/** Local courses and worked cases. Course completion is always an explicit learner action. */
public final class LearningActivity extends Activity {
    private static final int READING_PAPER = 0xfffffdf8;
    private static final int READING_LINE = 0xffd7d0c1;
    private static final int VERMILION = 0xffa74436;
    public static final String EXTRA_PAGE = "page";
    public static final String EXTRA_ITEM_ID = "item_id";
    private static final String EXTRA_REVEAL = "reveal";
    private static final String EXTRA_RETURN_LABEL = "return_label";
    private static final String PREFS = "guest_meihua_learning_v1";
    private static final int MAX_ASSET_BYTES = 1024 * 1024;
    private JSONObject curriculum;
    private JSONArray lessons, cases, sources;
    private SharedPreferences preferences;
    private ScrollView scroll;
    private LinearLayout content;
    private String page = "index", itemId = "", indexTab = "lessons";
    private String backLesson = "";
    private EditText caseThought;
    private Button completedButton;
    private boolean caseRevealed;
    private boolean changingPage;
    private boolean returnToCaller;
    private String callerReturnLabel = "返回上一页";
    private String renderedLearningState = "";

    public static void openLessonFromOriginal(Activity activity, String id) {
        activity.startActivity(new Intent(activity, LearningActivity.class).putExtra(EXTRA_PAGE, "lesson")
            .putExtra(EXTRA_ITEM_ID, id).putExtra(EXTRA_RETURN_LABEL, "返回原典"));
    }

    public static void openCaseFromOriginal(Activity activity, String id) {
        activity.startActivity(new Intent(activity, LearningActivity.class).putExtra(EXTRA_PAGE, "case")
            .putExtra(EXTRA_ITEM_ID, id).putExtra(EXTRA_REVEAL, true).putExtra(EXTRA_RETURN_LABEL, "返回原典"));
    }

    public static void openCaseLibrary(Activity activity) {
        activity.startActivity(new Intent(activity, LearningActivity.class).putExtra(EXTRA_PAGE, "cases"));
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        JianYiUi.applySystemBars(this);
        try {
            curriculum = readAsset();
            lessons = curriculum.getJSONArray("lessons");
            cases = curriculum.getJSONArray("cases");
            sources = curriculum.optJSONArray("sources");
            validateSources();
            validateRecords(lessons, false);
            validateRecords(cases, true);
        } catch (Exception error) {
            errorPage();
            return;
        }
        String requestedPage = getIntent().getStringExtra(EXTRA_PAGE);
        String requestedId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        returnToCaller = "lesson".equals(requestedPage) || "case".equals(requestedPage);
        callerReturnLabel = getIntent().getStringExtra(EXTRA_RETURN_LABEL);
        if (callerReturnLabel == null || callerReturnLabel.length() > 20) callerReturnLabel = "返回上一页";
        int restoreScroll = -1;
        if (savedInstanceState != null) {
            returnToCaller = savedInstanceState.getBoolean("return_to_caller", returnToCaller);
            callerReturnLabel = savedInstanceState.getString("caller_return_label", callerReturnLabel);
            requestedPage = savedInstanceState.getString("page", "index");
            requestedId = savedInstanceState.getString("item_id", "");
            indexTab = savedInstanceState.getString("index_tab", "lessons");
            backLesson = savedInstanceState.getString("back_lesson", "");
            caseRevealed = savedInstanceState.getBoolean("case_revealed", false);
            restoreScroll = savedInstanceState.getInt("scroll_y", 0);
        }
        if ("lesson".equals(requestedPage) && find(lessons, requestedId) != null) {
            showLesson(requestedId);
        } else if ("case".equals(requestedPage) && find(cases, requestedId) != null) {
            showCase(requestedId, savedInstanceState != null ? caseRevealed : getIntent().getBooleanExtra(EXTRA_REVEAL, false));
        } else {
            if ("cases".equals(requestedPage)) indexTab = "cases";
            else if ("study".equals(requestedPage)) indexTab = "study";
            showIndex();
        }
        if (restoreScroll >= 0) restoreScroll(restoreScroll);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (lessons == null || cases == null) return;
        savePosition();
        backLesson = "";
        String id = intent.getStringExtra(EXTRA_ITEM_ID);
        returnToCaller = "lesson".equals(intent.getStringExtra(EXTRA_PAGE)) || "case".equals(intent.getStringExtra(EXTRA_PAGE));
        callerReturnLabel = intent.getStringExtra(EXTRA_RETURN_LABEL);
        if (callerReturnLabel == null || callerReturnLabel.length() > 20) callerReturnLabel = "返回上一页";
        if ("lesson".equals(intent.getStringExtra(EXTRA_PAGE)) && find(lessons, id) != null)
            showLesson(id);
        else if ("case".equals(intent.getStringExtra(EXTRA_PAGE)) && find(cases, id) != null)
            showCase(id, intent.getBooleanExtra(EXTRA_REVEAL, false));
        else {
            if ("cases".equals(intent.getStringExtra(EXTRA_PAGE))) indexTab = "cases";
            else if ("study".equals(intent.getStringExtra(EXTRA_PAGE))) indexTab = "study";
            showIndex();
        }
    }

    @Override protected void onPause() {
        savePosition();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (lessons == null || preferences == null || scroll == null || renderedLearningState.length() == 0
            || !("lesson".equals(page) || "index".equals(page))
            || renderedLearningState.equals(learningState())) return;
        int position = scroll.getScrollY();
        savePosition();
        if ("lesson".equals(page)) showLesson(itemId); else showIndex();
        restoreScroll(position);
    }

    private String learningState() {
        StringBuilder state = new StringBuilder(preferences.getString("last_lesson", ""));
        for (int i = 0; i < lessons.length(); i++) {
            String id = lessons.optJSONObject(i).optString("id");
            state.append('|').append(id).append(':').append(preferences.getBoolean("complete_" + id, false))
                .append(':').append(preferences.getInt("answer_" + id, -1));
        }
        return state.toString();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        savePosition();
        out.putString("page", page);
        out.putString("item_id", itemId);
        out.putString("index_tab", indexTab);
        out.putString("back_lesson", backLesson);
        out.putBoolean("case_revealed", caseRevealed);
        out.putBoolean("return_to_caller", returnToCaller);
        out.putString("caller_return_label", callerReturnLabel);
        out.putInt("scroll_y", scroll == null ? 0 : scroll.getScrollY());
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        if ("case".equals(page) && backLesson.length() != 0) {
            String previous = backLesson;
            savePosition();
            backLesson = "";
            showLesson(previous);
        } else if (returnToCaller) {
            savePosition(); finish();
        } else if (!"index".equals(page) && lessons != null) {
            savePosition();
            showIndex();
        } else finish();
    }

    private JSONObject readAsset() throws Exception {
        InputStream stream = getAssets().open("appdata/meihua-learning.json");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_ASSET_BYTES) throw new IllegalArgumentException("asset too large");
                bytes.write(buffer, 0, count);
            }
        } finally { stream.close(); }
        return new JSONObject(new String(bytes.toByteArray(), "UTF-8"));
    }

    private void validateRecords(JSONArray records, boolean workedCases) throws Exception {
        if (records.length() == 0 || records.length() > 200) throw new IllegalArgumentException("empty curriculum");
        HashSet<String> ids = new HashSet<String>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            String id = record.getString("id");
            if (!id.matches("[A-Za-z0-9_-]{1,64}") || !ids.add(id)
                || record.getString("title").trim().length() == 0)
                throw new IllegalArgumentException("invalid record");
            JSONArray sourceIds = record.getJSONArray("source_ids");
            if (sourceIds.length() == 0) throw new IllegalArgumentException("missing sources");
            for (int j = 0; j < sourceIds.length(); j++)
                if (find(sources, sourceIds.getString(j)) == null) throw new IllegalArgumentException("unknown source");
            JSONArray sections = record.getJSONArray(workedCases ? "steps" : "sections");
            if (sections.length() == 0 || sections.length() > 40) throw new IllegalArgumentException("invalid sections");
            for (int j = 0; j < sections.length(); j++) {
                JSONObject section = sections.getJSONObject(j);
                if (section.optString("title").length() == 0 || prose(section.opt("paragraphs")).length() == 0)
                    throw new IllegalArgumentException("empty section");
            }
            if (!workedCases) {
                validateOriginalLinks(record);
                JSONObject quiz = record.getJSONObject("quiz");
                JSONArray options = quiz.getJSONArray("options");
                int answer = quiz.getInt("answer_index");
                if (quiz.optString("question").length() == 0 || options.length() < 2 || options.length() > 6
                    || answer < 0 || answer >= options.length() || quiz.optString("explanation").length() == 0)
                    throw new IllegalArgumentException("invalid quiz");
            } else {
                JSONObject computed = record.optJSONObject("computed");
                if (computed != null) validateComputed(computed);
            }
        }
    }

    private void validateOriginalLinks(JSONObject lesson) throws Exception {
        JSONArray links = lesson.optJSONArray("original_links");
        if (links == null) return;
        if (links.length() > 4) throw new IllegalArgumentException("too many original links");
        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.getJSONObject(i);
            String bookId = link.getString("book_id");
            if (!bookId.matches("[A-Z0-9_]{1,40}")) throw new IllegalArgumentException("invalid original book");
            InputStream stream = getAssets().open("appdata/classic-fulltext-" + bookId + ".json");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[4096]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_ASSET_BYTES) throw new IllegalArgumentException("original too large");
                    bytes.write(buffer, 0, count);
                }
            } finally { stream.close(); }
            JSONArray chapters = new JSONObject(new String(bytes.toByteArray(), "UTF-8")).getJSONArray("sections");
            int section = link.getInt("section");
            if (section < 0 || section >= chapters.length()
                || !link.getString("title").equals(chapters.getJSONObject(section).getString("title")))
                throw new IllegalArgumentException("original link mismatch");
        }
    }

    private void validateSources() throws Exception {
        if (sources == null || sources.length() == 0 || sources.length() > 200)
            throw new IllegalArgumentException("missing sources");
        HashSet<String> ids = new HashSet<String>();
        for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.getJSONObject(i);
            String id = source.getString("id");
            String url = source.optString("url");
            Uri uri = Uri.parse(url);
            boolean validUrl = source.optBoolean("local", false) && url.length() == 0
                || "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
            if (!id.matches("[A-Za-z0-9_-]{1,64}") || !ids.add(id)
                || source.getString("title").trim().length() == 0
                || !validUrl)
                throw new IllegalArgumentException("invalid source");
        }
    }

    private void validateComputed(JSONObject computed) throws Exception {
        MeihuaEngine.Result result = MeihuaEngine.fromTrigrams(computed.getInt("upper"),
            computed.getInt("lower"), computed.getInt("moving"));
        if (!result.baseCode.equals(computed.getString("base_code"))
            || !result.changedCode.equals(computed.getString("changed_code"))
            || !result.mutualCode.equals(computed.getString("mutual_code"))
            || result.bodyTrigram != computed.getInt("body_trigram")
            || result.useTrigram != computed.getInt("use_trigram"))
            throw new IllegalArgumentException("case diagram mismatch");
    }

    private void errorPage() {
        buildPage("梅花学堂", "学习资料未能载入");
        LinearLayout card = card();
        card.addView(body("请重新安装完整版本。原有起卦和练习可以继续使用。"));
        addButton(content, "返回首页", false, new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
    }

    private void showIndex() {
        page = "index";
        itemId = "";
        caseThought = null;
        caseRevealed = false;
        buildPage("梅花学堂", "从基础到分析，让每次起卦都有理解");
        TextView back = caption("‹ 返回首页");
        back.setTextColor(JianYiUi.INK); back.setMinHeight(dp(48)); back.setGravity(Gravity.CENTER_VERTICAL);
        back.setFocusable(true); back.setContentDescription("返回首页");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        content.addView(back); addLearningProgress(); addLearningEntries();
        LinearLayout tabs = row();
        addWeightedButton(tabs, "入门路线", "lessons".equals(indexTab), new View.OnClickListener() {
            @Override public void onClick(View view) { indexTab = "lessons"; showIndex(); }
        });
        addWeightedButton(tabs, "专题研习", "study".equals(indexTab), new View.OnClickListener() {
            @Override public void onClick(View view) { indexTab = "study"; showIndex(); }
        });
        addWeightedButton(tabs, "案例课堂", "cases".equals(indexTab), new View.OnClickListener() {
            @Override public void onClick(View view) { indexTab = "cases"; showIndex(); }
        });
        content.addView(tabs);
        if ("cases".equals(indexTab)) showCaseIndex(); else showLessonIndex("study".equals(indexTab));
        renderedLearningState = learningState();
    }

    private void addLearningProgress() {
        int completed = 0;
        for (int i = 0; i < lessons.length(); i++)
            if (preferences.getBoolean("complete_" + lessons.optJSONObject(i).optString("id"), false)) completed++;
        LinearLayout progress = card();
        progress.addView(heading("已完成 " + completed + " / " + lessons.length() + " 课", 23));
        progress.addView(body("每课读一小段、做一道题。学完后由你标记完成，随时可以复习。"));
        String lastId = preferences.getString("last_lesson", "");
        JSONObject last = find(lessons, lastId);
        if (last == null) last = lessons.optJSONObject(0);
        if (preferences.getBoolean("complete_" + last.optString("id"), false) && completed < lessons.length()) {
            int previous = indexOf(lessons, last.optString("id"));
            for (int distance = 1; distance <= lessons.length(); distance++) {
                JSONObject candidate = lessons.optJSONObject((previous + distance) % lessons.length());
                if (!preferences.getBoolean("complete_" + candidate.optString("id"), false)) { last = candidate; break; }
            }
        }
        final String continueId = last.optString("id");
        addButton(progress, (completed == lessons.length() ? "继续复习 · " : "继续学习 · ") + last.optString("title"), true, new View.OnClickListener() {
            @Override public void onClick(View view) { openLesson(continueId); }
        });
    }

    private void addLearningEntries() {
        LinearLayout first = row();
        addLearningEntry(first, "原典书库", "按目录阅读原文", new View.OnClickListener() {
            @Override public void onClick(View view) { MeihuaReferenceActivity.openLibrary(LearningActivity.this); }
        });
        addLearningEntry(first, "知识查找", "进阶主题与图解", new View.OnClickListener() {
            @Override public void onClick(View view) { MeihuaReferenceActivity.open(LearningActivity.this); }
        });
        content.addView(first);
        LinearLayout second = row();
        addLearningEntry(second, "基础练习", "看图答题与错题复习", new View.OnClickListener() {
            @Override public void onClick(View view) { FoundationPracticeActivity.open(LearningActivity.this); }
        });
        addLearningEntry(second, "卦例笔记", "记录判断与复盘", new View.OnClickListener() {
            @Override public void onClick(View view) { openNotebook(null, null); }
        });
        content.addView(second);
    }

    private void addLearningEntry(LinearLayout row, String title, String summary, View.OnClickListener listener) {
        LinearLayout entry = detachedCard(); entry.setMinimumHeight(dp(100));
        entry.addView(heading(title, 19)); entry.addView(caption(summary));
        entry.setClickable(true); entry.setFocusable(true);
        entry.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        entry.setContentDescription(title + "，" + summary); entry.setOnClickListener(listener);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -1, 1f);
        layout.setMargins(dp(3), dp(6), dp(3), dp(6)); row.addView(entry, layout);
    }

    private void showLessonIndex(boolean advanced) {
        String level = "";
        for (int i = 0; i < lessons.length(); i++) {
            final JSONObject lesson = lessons.optJSONObject(i);
            if (lesson.optBoolean("advanced", false) != advanced) continue;
            String nextLevel = lesson.optString("level", "入门课程");
            if (!nextLevel.equals(level)) {
                level = nextLevel;
                content.addView(sectionLabel(level));
            }
            LinearLayout item = card();
            final String id = lesson.optString("id");
            String status = preferences.getBoolean("complete_" + id, false) ? "已完成" : "待学习";
            item.addView(caption(String.format(java.util.Locale.CHINA, "%02d", i + 1) + "  ·  " + status));
            item.addView(heading(lesson.optString("title"), 21));
            TextView summary = body(lesson.optString("summary")); summary.setTextIsSelectable(false); item.addView(summary);
            item.addView(caption("已完成".equals(status) ? "复习课程　›" : "打开课程　›"));
            item.setClickable(true); item.setFocusable(true); item.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            item.setContentDescription("学习第" + (i + 1) + "课 " + lesson.optString("title") + "，" + status);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { openLesson(id); }
            });
        }
    }

    private void showCaseIndex() {
        LinearLayout introduction = card();
        introduction.addView(heading("先自己想，再看分步分析", 22));
        introduction.addView(body("精讲古例按原文、取数、本互变、体用、取象和复盘逐步阅读；教学练习可以先独立作答，再对照计算。"));
        boolean featured = false;
        for (int i = 0; i < cases.length(); i++) {
            JSONObject item = cases.optJSONObject(i);
            if (!item.optBoolean("featured", false)) continue;
            if (!featured) { content.addView(sectionLabel("精讲古例")); featured = true; }
            addCaseIndexTile(item);
        }
        if (featured) content.addView(sectionLabel("巩固练习"));
        for (int i = 0; i < cases.length(); i++) {
            JSONObject item = cases.optJSONObject(i);
            if (!item.optBoolean("featured", false)) addCaseIndexTile(item);
        }
    }

    private void addCaseIndexTile(final JSONObject item) {
        final String id = item.optString("id");
        LinearLayout tile = card();
        tile.addView(caption(caseKind(item)));
        tile.addView(heading(item.optString("title"), 22));
        tile.addView(body(item.optString("summary", item.optString("scenario"))));
        tile.addView(caption("先思考，再对照图解与分步讲解　›"));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription("阅读案例 " + item.optString("title"));
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { backLesson = ""; openCase(id); }
        });
    }

    private void openLesson(String id) { savePosition(); showLesson(id); }
    private void openCase(String id) { savePosition(); showCase(id, false); }

    private void showLesson(final String id) {
        final JSONObject lesson = find(lessons, id);
        if (lesson == null) { showIndex(); return; }
        page = "lesson";
        itemId = id;
        caseThought = null;
        caseRevealed = false;
        indexTab = lesson.optBoolean("advanced", false) ? "study" : "lessons";
        commit(preferences.edit().putString("last_lesson", id));
        buildPage(lesson.optBoolean("advanced", false) ? "专题研习" : "入门课程",
            "第 " + (indexOf(lessons, id) + 1) + " 课 · " + lesson.optString("level"));
        LinearLayout navigation = row();
        addWeightedButton(navigation, returnToCaller ? callerReturnLabel : "返回学习路线", false, new View.OnClickListener() {
            @Override public void onClick(View view) { onBackPressed(); }
        });
        if (!returnToCaller) addWeightedButton(navigation, "返回首页", false, new View.OnClickListener() {
            @Override public void onClick(View view) { savePosition(); finish(); }
        });
        content.addView(navigation);
        LinearLayout overview = card();
        overview.addView(heading(lesson.optString("title"), 26));
        overview.addView(body(lesson.optString("summary")));
        addLessonVisual(overview, id);
        JSONArray objectives = lesson.optJSONArray("objectives");
        if (objectives != null && objectives.length() > 0) {
            overview.addView(caption("学完这课，你能"));
            for (int i = 0; i < objectives.length(); i++)
                overview.addView(body("• " + objectives.optString(i)));
        }
        JSONArray sections = lesson.optJSONArray("sections");
        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            addTextSection(content, section.optString("title"), section.opt("paragraphs"), false);
        }
        addLessonOriginals(lesson);
        content.addView(sectionLabel("做一道小练习"));
        addQuiz(lesson);
        addRelatedCases(id);
        LinearLayout complete = card();
        complete.addView(heading("按自己的节奏学习", 21));
        complete.addView(body("掌握后可以标记完成；这不会替你提交练习答案。"));
        final int nextIndex = indexOf(lessons, id) + 1;
        final JSONObject nextLesson = nextIndex < lessons.length() ? lessons.optJSONObject(nextIndex) : null;
        completedButton = addButton(complete, "", true, new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean value = !preferences.getBoolean("complete_" + id, false);
                if (commit(preferences.edit().putBoolean("complete_" + id, value))) {
                    updateCompletionButton(id);
                    if (value) {
                        Toast.makeText(LearningActivity.this, nextLesson == null ? "本轮课程已全部完成" : "已完成，继续下一课", Toast.LENGTH_SHORT).show();
                        completedButton.postDelayed(new Runnable() {
                            @Override public void run() {
                                if (nextLesson == null) showIndex();
                                else openLesson(nextLesson.optString("id"));
                            }
                        }, 260);
                    } else Toast.makeText(LearningActivity.this, "已恢复为待学习", Toast.LENGTH_SHORT).show();
                }
            }
        });
        updateCompletionButton(id);
        if (nextLesson != null) {
            addButton(complete, "下一课 · " + nextLesson.optString("title"), false, new View.OnClickListener() {
                @Override public void onClick(View view) { openLesson(nextLesson.optString("id")); }
            });
        }
        addSources(lesson);
        restoreScroll(preferences.getInt("scroll_lesson_" + id, 0));
        renderedLearningState = learningState();
    }

    private void addLessonOriginals(JSONObject lesson) {
        JSONArray links = lesson.optJSONArray("original_links");
        if (links == null || links.length() == 0) return;
        LinearLayout panel = card(); panel.addView(heading("对照原典", 21));
        panel.addView(caption("按篇章打开本机原文；返回后接着读本课。"));
        for (int i = 0; i < links.length(); i++) {
            final JSONObject link = links.optJSONObject(i);
            addButton(panel, link.optString("title") + "　›", false, new View.OnClickListener() {
                @Override public void onClick(View view) {
                    savePosition(); MeihuaReferenceActivity.openFullTextSection(LearningActivity.this,
                        link.optString("book_id"), link.optInt("section"));
                }
            });
        }
    }

    private void addLessonVisual(LinearLayout parent, String id) {
        View visual = null;
        if ("L02".equals(id)) visual = new KnowledgeDiagramView(this, "polarity", "");
        else if ("L03".equals(id)) visual = new TrigramMnemonicView(this);
        else if ("L05".equals(id) || "L11".equals(id) || "L15".equals(id)) visual = new FivePhaseRelationView(this);
        else if ("L06".equals(id)) visual = new KnowledgeDiagramView(this, "branches", "");
        else if ("L09".equals(id) || "L10".equals(id)) visual = new KnowledgeDiagramView(this, "hexagrams", "");
        else if ("L17".equals(id)) visual = new ClassicDiagramView(this, "observation_path");
        else if ("L19".equals(id)) visual = new ClassicDiagramView(this, "body_use");
        else if ("L20".equals(id)) visual = new ClassicDiagramView(this, "season_strength");
        else if ("L21".equals(id)) visual = new ClassicDiagramView(this, "observation_path");
        if (visual == null) return;
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, dp(8), 0, dp(14));
        parent.addView(visual, layout);
    }

    private void updateCompletionButton(String id) {
        if (completedButton != null) completedButton.setText(preferences.getBoolean("complete_" + id, false)
            ? "已完成 · 点击恢复为待学习" : "我已掌握，标记完成");
    }

    private void addQuiz(final JSONObject lesson) {
        final JSONObject quiz = lesson.optJSONObject("quiz");
        final String id = lesson.optString("id");
        final LinearLayout quizCard = card();
        quizCard.addView(heading(quiz.optString("question"), 21));
        final LinearLayout answers = column();
        quizCard.addView(answers);
        final LinearLayout explanation = column();
        quizCard.addView(explanation);
        final int correct = quiz.optInt("answer_index", -1);
        final JSONArray options = quiz.optJSONArray("options");
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = new Runnable() {
            @Override public void run() {
                answers.removeAllViews();
                explanation.removeAllViews();
                final int selected = preferences.getInt("answer_" + id, -1);
                for (int i = 0; i < options.length(); i++) {
                    final int option = i;
                    String prefix = selected == option ? "已选 · " : "";
                    Button answer = addButton(answers, prefix + (char) ('A' + i) + ". " + options.optString(i),
                        selected == option, new View.OnClickListener() {
                            @Override public void onClick(View view) {
                                if (commit(preferences.edit().putInt("answer_" + id, option))) refresh[0].run();
                            }
                        });
                    if (selected >= 0) answer.setEnabled(false);
                }
                if (selected >= 0) {
                    explanation.addView(heading(selected == correct ? "答对了" : "再想想这个知识点", 20));
                    explanation.addView(body("正确答案：" + (char) ('A' + correct) + ". " + options.optString(correct)));
                    explanation.addView(body(quiz.optString("explanation")));
                    addButton(explanation, "重新作答", false, new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            if (commit(preferences.edit().remove("answer_" + id))) refresh[0].run();
                        }
                    });
                } else explanation.addView(caption("先选择答案，再展开讲解。"));
            }
        };
        refresh[0].run();
    }

    private void addRelatedCases(final String lessonId) {
        boolean labelAdded = false;
        for (int i = 0; i < cases.length(); i++) {
            final JSONObject worked = cases.optJSONObject(i);
            JSONArray linked = worked.optJSONArray("lesson_ids");
            if (!contains(linked, lessonId)) continue;
            if (!labelAdded) { content.addView(sectionLabel("用案例巩固")); labelAdded = true; }
            addButton(content, worked.optString("title") + " · " + caseKind(worked), false, new View.OnClickListener() {
                @Override public void onClick(View view) { backLesson = lessonId; openCase(worked.optString("id")); }
            });
        }
    }

    private void showCase(final String id, boolean reveal) {
        final JSONObject worked = find(cases, id);
        if (worked == null) { showIndex(); return; }
        page = "case";
        itemId = id;
        indexTab = "cases";
        caseRevealed = reveal;
        buildPage("案例课堂", caseKind(worked) + " · 先思考，再对照");
        LinearLayout navigation = row();
        addWeightedButton(navigation, backLesson.length() > 0 ? "返回课程" : returnToCaller ? callerReturnLabel : "返回案例目录", false, new View.OnClickListener() {
            @Override public void onClick(View view) { onBackPressed(); }
        });
        if (!returnToCaller) addWeightedButton(navigation, "返回首页", false, new View.OnClickListener() {
            @Override public void onClick(View view) { savePosition(); finish(); }
        });
        content.addView(navigation);
        LinearLayout scenario = card();
        scenario.addView(heading(worked.optString("title"), 26));
        scenario.addView(caption(caseKind(worked)));
        scenario.addView(body(worked.optString("scenario")));
        if (worked.optString("source_note").length() > 0) scenario.addView(caption(worked.optString("source_note")));
        final String originalBook = worked.optString("original_book_id");
        final int originalSection = worked.optInt("original_section_index", -1);
        if (originalBook.length() > 0 && originalSection >= 0) {
            addButton(scenario, "对照本例原典", false, new View.OnClickListener() {
                @Override public void onClick(View view) {
                    savePosition();
                    MeihuaReferenceActivity.openFullTextSection(LearningActivity.this, originalBook, originalSection);
                }
            });
        }
        scenario.addView(caption("起卦方式：" + methodLabel(worked.optString("method"))));
        String inputNotes = prose(worked.opt("inputs_notes"));
        if (inputNotes.length() > 0) scenario.addView(body(inputNotes));
        LinearLayout think = card();
        think.addView(heading("先写下你的思路", 22));
        JSONArray questions = worked.optJSONArray("reflection_questions");
        if (questions != null) for (int i = 0; i < questions.length(); i++)
            think.addView(body((i + 1) + ". " + questions.optString(i)));
        caseThought = new EditText(this);
        caseThought.setHint("你准备怎样取数、分体用、理解卦象？");
        caseThought.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        caseThought.setFilters(new InputFilter[] {new InputFilter.LengthFilter(8000)});
        caseThought.setGravity(Gravity.TOP);
        caseThought.setMinLines(3);
        caseThought.setText(preferences.getString("thought_" + id, ""));
        JianYiUi.styleInput(caseThought);
        think.addView(caseThought, params());
        think.addView(caption("这里的草稿会保存在本机。也可以直接查看讲解，再回来补充。"));
        final LinearLayout walkthrough = column();
        content.addView(walkthrough);
        final Button revealButton = addButton(think, reveal ? "收起分步讲解" : "查看分步讲解", true, null);
        revealButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                savePosition();
                hideKeyboard();
                caseRevealed = !caseRevealed;
                walkthrough.removeAllViews();
                if (caseRevealed) populateCase(worked, walkthrough);
                revealButton.setText(caseRevealed ? "收起分步讲解" : "查看分步讲解");
                if (caseRevealed) revealTarget(walkthrough);
            }
        });
        if (reveal) populateCase(worked, walkthrough);
        LinearLayout note = card();
        note.addView(heading("把思路留下来", 22));
        note.addView(body("保存为教学卦例笔记后，可以继续补充分析、观察与复盘。"));
        addButton(note, "保存为卦例笔记", false, new View.OnClickListener() {
            @Override public void onClick(View view) {
                savePosition();
                openNotebook(worked, caseThought == null ? "" : caseThought.getText().toString());
            }
        });
        addSources(worked);
        restoreScroll(preferences.getInt("scroll_case_" + id, 0));
    }

    private void populateCase(JSONObject worked, LinearLayout target) {
        target.addView(sectionLabel("分步讲解"));
        JSONObject computed = worked.optJSONObject("computed");
        if (computed != null) {
            LinearLayout diagrams = detachedCard();
            diagrams.addView(heading("先看成卦与体用", 22));
            LinearLayout diagramRow = row();
            addDiagram(diagramRow, "本卦", computed.optString("base_name"), computed.optString("base_code"), computed.optInt("moving"));
            addDiagram(diagramRow, "互卦", computed.optString("mutual_name"), computed.optString("mutual_code"), 0);
            addDiagram(diagramRow, "变卦", computed.optString("changed_name"), computed.optString("changed_code"), 0);
            diagrams.addView(diagramRow);
            diagrams.addView(body("动爻：第 " + computed.optInt("moving") + " 爻 · 体卦："
                + MeihuaEngine.trigramName(computed.optInt("body_trigram")) + " · 用卦："
                + MeihuaEngine.trigramName(computed.optInt("use_trigram"))));
            if (computed.optString("relation").length() > 0) diagrams.addView(caption(computed.optString("relation")));
            target.addView(diagrams, cardParams());
        }
        JSONArray steps = worked.optJSONArray("steps");
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            String title = step.optString("title").replaceFirst("^\\d+[\\s·、.]+", "");
            addTextSection(target, (i + 1) + " · " + title, step.opt("paragraphs"), false);
        }
    }

    private void addDiagram(LinearLayout row, String label, String name, String code, int moving) {
        LinearLayout tile = column();
        tile.setPadding(dp(5), 0, dp(5), 0);
        TextView title = caption(label);
        title.setGravity(Gravity.CENTER);
        tile.addView(title);
        tile.addView(new HexagramView(code, moving), new LinearLayout.LayoutParams(-1, dp(115)));
        TextView detail = body(name.length() > 0 ? name : trigramCaption(code));
        detail.setTextSize(15f);
        detail.setGravity(Gravity.CENTER);
        tile.addView(detail);
        row.addView(tile, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    private String trigramCaption(String code) {
        if (!code.matches("[01]{6}")) return "";
        return MeihuaEngine.trigramName(MeihuaEngine.trigramId(bits(code.substring(3)))) + "上"
            + MeihuaEngine.trigramName(MeihuaEngine.trigramId(bits(code.substring(0, 3)))) + "下";
    }

    private void openNotebook(JSONObject worked, String thought) {
        if (worked == null) { NotebookActivity.openList(this); return; }
        JSONObject computed = worked.optJSONObject("computed");
        if (computed == null) {
            Toast.makeText(this, "此案例缺少成卦信息，暂不能预填笔记", Toast.LENGTH_LONG).show();
            return;
        }
        String info = caseKind(worked) + " · " + methodLabel(worked.optString("method")) + "\n" + prose(worked.opt("inputs_notes"));
        NotebookActivity.launchCaseDraft(this, worked.optString("title"), worked.optString("scenario"),
            "教学案例", info, computed.optInt("upper"), computed.optInt("lower"), computed.optInt("moving"),
            worked.optString("id"), thought == null ? "" : thought);
    }

    private void addSources(JSONObject record) {
        JSONArray references = record.optJSONArray("source_ids");
        if ((references == null || references.length() == 0) && record.optString("source_note").length() == 0) return;
        final LinearLayout panel = card();
        final LinearLayout details = column();
        if (record.optString("source_note").length() != 0) details.addView(body(record.optString("source_note")));
        if (references != null) for (int i = 0; i < references.length(); i++) {
            final JSONObject reference = find(sources, references.optString(i));
            if (reference == null) continue;
            details.addView(body(reference.optString("title")));
            final String url = reference.optString("url");
            if (url.length() > 0) details.addView(caption(url));
        }
        details.setVisibility(View.GONE);
        final Button toggle = addButton(panel, "出处与校记 · 展开", false, null);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean open = details.getVisibility() != View.VISIBLE;
                details.setVisibility(open ? View.VISIBLE : View.GONE);
                toggle.setText(open ? "出处与校记 · 收起" : "出处与校记 · 展开");
            }
        });
        panel.addView(details);
    }

    private void addTextSection(LinearLayout target, String title, Object paragraphs, boolean initiallyFolded) {
        final LinearLayout panel = detachedCard();
        panel.addView(chapterHeading(title));
        final LinearLayout text = column();
        if (paragraphs instanceof JSONArray) {
            JSONArray values = (JSONArray) paragraphs;
            for (int i = 0; i < values.length(); i++) text.addView(body(values.optString(i)));
        } else text.addView(body(prose(paragraphs)));
        String all = prose(paragraphs);
        if (initiallyFolded || all.length() > 550) {
            final String expansionKey = "section_open_" + page + "_" + itemId + "_" + Integer.toHexString(title.hashCode());
            boolean wasExpanded = preferences.getBoolean(expansionKey, false);
            text.setVisibility(wasExpanded ? View.VISIBLE : View.GONE);
            final Button toggle = addButton(panel, wasExpanded ? "收起讲解" : "展开讲解", false, null);
            toggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    boolean open = text.getVisibility() != View.VISIBLE;
                    text.setVisibility(open ? View.VISIBLE : View.GONE);
                    toggle.setText(open ? "收起讲解" : "展开讲解");
                    commit(preferences.edit().putBoolean(expansionKey, open));
                }
            });
        }
        panel.addView(text);
        target.addView(panel, cardParams());
    }

    private void buildPage(String title, String subtitle) {
        changingPage = true;
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        JianYiUi.applyScenic(scroll,JianYiUi.SCENE_READING);
        scroll.setFitsSystemWindows(true);
        content = column();
        content.setPadding(dp(18), dp(10), dp(18), dp(24));
        content.setFocusableInTouchMode(true);
        content.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        content.addView(JianYiUi.hero(this, title, subtitle));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        JianYiUi.applySystemBars(this);
        content.requestFocus();
        JianYiUi.enter(content);
        changingPage = false;
    }

    private void savePosition() {
        if (preferences == null || scroll == null || changingPage || itemId.length() == 0) return;
        SharedPreferences.Editor editor = preferences.edit().putInt("scroll_" + page + "_" + itemId, scroll.getScrollY());
        if ("case".equals(page) && caseThought != null) editor.putString("thought_" + itemId, caseThought.getText().toString());
        commit(editor);
    }

    private boolean commit(SharedPreferences.Editor editor) {
        boolean saved;
        try { saved = editor.commit(); }
        catch (RuntimeException error) { saved = false; }
        if (!saved) Toast.makeText(this, "本机保存失败，请检查剩余空间后重试", Toast.LENGTH_LONG).show();
        return saved;
    }

    private void restoreScroll(final int value) {
        final ScrollView target = scroll;
        target.post(new Runnable() {
            @Override public void run() { if (scroll == target) target.scrollTo(0, Math.max(0, value)); }
        });
    }

    private void revealTarget(final View target) {
        final ScrollView parent = scroll;
        target.post(new Runnable() {
            @Override public void run() {
                if (scroll == parent && target.getParent() == content) {
                    int top = Math.max(0, target.getTop() - dp(12));
                    if (JianYiUi.animationsEnabled(LearningActivity.this)) parent.smoothScrollTo(0, top);
                    else parent.scrollTo(0, top);
                }
            }
        });
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager manager = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null && getCurrentFocus() != null) manager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        content.requestFocus();
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setBaselineAligned(false);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private LinearLayout card() {
        LinearLayout view = detachedCard();
        content.addView(view, cardParams());
        return view;
    }
    private LinearLayout detachedCard() {
        LinearLayout view = column();
        view.setPadding(dp(16), dp(16), dp(16), dp(16));
        view.setBackground(JianYiUi.rounded(this, READING_PAPER, READING_LINE, 6));
        return view;
    }
    private LinearLayout chapterHeading(String title) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        View seal = new View(this);
        seal.setBackgroundColor(VERMILION);
        LinearLayout.LayoutParams mark = new LinearLayout.LayoutParams(dp(3), dp(18));
        mark.rightMargin = dp(10);
        row.addView(seal, mark);
        TextView text = heading(title, 22);
        text.setPadding(0, dp(4), 0, dp(8));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }
    private TextView heading(String value, float size) {
        TextView view = label(value, size, JianYiUi.INK, true);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }
    private TextView body(String value) {
        TextView view = label(value, 17f, JianYiUi.INK, false);
        view.setPadding(0, dp(3), 0, dp(9));
        view.setTextIsSelectable(true);
        return view;
    }
    private TextView caption(String value) {
        TextView view = label(value, 13f, JianYiUi.MUTED, false);
        view.setPadding(0, dp(5), 0, dp(9));
        return view;
    }
    private TextView sectionLabel(String value) {
        TextView view = heading(value, 23);
        view.setPadding(dp(3), dp(22), dp(3), dp(9));
        return view;
    }
    private TextView label(String value, float size, int color, boolean title) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        JianYiUi.textStyle(view, size, title);
        view.setTextColor(color);
        view.setLineSpacing(dp(5), 1.05f);
        return view;
    }
    private Button addButton(LinearLayout parent, String text, boolean primary, View.OnClickListener listener) {
        Button view = new Button(this);
        view.setText(text);
        JianYiUi.skinButton(view, primary);
        view.setOnClickListener(listener);
        LinearLayout.LayoutParams layout = params();
        layout.topMargin = dp(8);
        parent.addView(view, layout);
        return view;
    }
    private void addWeightedButton(LinearLayout parent, String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        JianYiUi.skinButton(button, primary);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -2, 1f);
        layout.setMargins(dp(3), dp(5), dp(3), dp(5));
        parent.addView(button, layout);
    }
    private LinearLayout.LayoutParams params() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams layout = params();
        layout.topMargin = dp(12);
        return layout;
    }
    private int dp(float value) { return JianYiUi.dp(this, value); }

    private static JSONObject find(JSONArray array, String id) {
        if (array == null || id == null) return null;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) return item;
        }
        return null;
    }
    private static int indexOf(JSONArray array, String id) {
        for (int i = 0; i < array.length(); i++) if (id.equals(array.optJSONObject(i).optString("id"))) return i;
        return -1;
    }
    private static boolean contains(JSONArray array, String id) {
        if (array == null) return false;
        for (int i = 0; i < array.length(); i++) if (id.equals(array.optString(i))) return true;
        return false;
    }
    private static String prose(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof JSONArray) {
            JSONArray values = (JSONArray) value;
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < values.length(); i++) {
                if (i != 0) result.append("\n\n");
                result.append(values.optString(i));
            }
            return result.toString();
        }
        return String.valueOf(value);
    }
    private static String caseKind(JSONObject worked) {
        String kind = worked.optString("kind");
        return "classic".equals(kind) || "ancient".equals(kind) || "classic_case".equals(kind) || "古籍例".equals(kind)
            ? "古籍卦例" : "教学示例";
    }
    private static String methodLabel(String method) {
        if ("numbers".equals(method)) return "数字起卦";
        if ("time".equals(method)) return "时间起卦";
        if ("manual".equals(method)) return "按卦象取数";
        return method;
    }
    private static int[] bits(String code) {
        int[] result = new int[code.length()];
        for (int i = 0; i < result.length; i++) result[i] = code.charAt(i) == '1' ? 1 : 0;
        return result;
    }

    private final class HexagramView extends View {
        private final String code;
        private final int moving;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        HexagramView(String code, int moving) {
            super(LearningActivity.this);
            this.code = code;
            this.moving = moving;
            setContentDescription("卦图，爻从下往上数" + (moving > 0 ? "，第 " + moving + " 爻为动爻" : ""));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!code.matches("[01]{6}")) return;
            float start = getWidth() * 0.10f, end = getWidth() * 0.90f;
            float gap = getWidth() * 0.12f;
            float center = getWidth() / 2f;
            float spacing = getHeight() / 7f;
            paint.setStrokeWidth(dp(5));
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 6; i++) {
                float y = getHeight() - spacing * (i + 1);
                paint.setColor(i + 1 == moving ? 0xff9a3412 : JianYiUi.INK);
                if (code.charAt(i) == '1') canvas.drawLine(start, y, end, y, paint);
                else {
                    canvas.drawLine(start, y, center - gap, y, paint);
                    canvas.drawLine(center + gap, y, end, y, paint);
                }
            }
        }
    }
}
