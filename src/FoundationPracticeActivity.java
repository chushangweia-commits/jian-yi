package com.jianyi.yixue.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Clean-room foundation drills. Everything here is newly implemented and uses
 * only reviewed local data; no original Activity, DEX, reflection or image is used.
 */
public final class FoundationPracticeActivity extends Activity {
    private static final String PREFS = "jianyi_foundation_practice_v1";
    private static final String[] TRACK_IDS = {"bagua", "wuxing", "ganzhi", "hexagrams"};
    private static final String[] TRACK_TITLES = {"八卦 · 名称卦数方位", "五行 · 生克关系", "天干地支 · 基础", "六十四卦 · 识图"};
    private static final String[] TRACK_NOTES = {
        "看卦象，联结名称、先天卦数与后天方位",
        "用五色关系图记住相生与相克",
        "从阴阳五行和次序开始熟悉干支",
        "根据六爻卦图辨认卦名与上下卦"
    };
    private static final String[] TRIGRAMS = {"乾", "兑", "离", "震", "巽", "坎", "艮", "坤"};
    private static final String[] TRIGRAM_CODES = {"111", "110", "101", "100", "011", "010", "001", "000"};
    private static final String[] DIRECTIONS = {"西北", "正西", "正南", "正东", "东南", "正北", "东北", "西南"};
    private static final String[] MNEMONICS = {"乾三连", "兑上缺", "离中虚", "震仰盂", "巽下断", "坎中满", "艮覆碗", "坤六断"};
    private static final String[] PHASES = {"木", "火", "土", "金", "水"};
    private static final String[] STEMS = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] STEM_FACTS = {"阳木", "阴木", "阳火", "阴火", "阳土", "阴土", "阳金", "阴金", "阳水", "阴水"};
    private static final String[] BRANCHES = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] BRANCH_ELEMENTS = {"水", "土", "木", "木", "土", "火", "火", "土", "金", "金", "土", "水"};
    private static final String[] BRANCH_POLARITIES = {"阳", "阴", "阳", "阴", "阳", "阴", "阳", "阴", "阳", "阴", "阳", "阴"};

    private final LinkedHashMap<String, ArrayList<Question>> tracks = new LinkedHashMap<String, ArrayList<Question>>();
    private SharedPreferences preferences;
    private ScrollView scroll;
    private LinearLayout content;
    private String page = "home";
    private String currentTrack = "";
    private int currentIndex;
    private int selectedOption = -1;

    private static final class Question {
        final String id, track, prompt, explanation, visual, visualLabel;
        final String[] options;
        final int answer;
        Question(String id, String track, String prompt, String[] options, int answer,
                 String explanation, String visual, String visualLabel) {
            this.id = id;
            this.track = track;
            this.prompt = prompt;
            this.options = options;
            this.answer = answer;
            this.explanation = explanation;
            this.visual = visual;
            this.visualLabel = visualLabel;
        }
    }

    private static final class Choices {
        final String[] values;
        final int answer;
        Choices(String[] values, int answer) { this.values = values; this.answer = answer; }
    }

    public static void open(Context context) {
        context.startActivity(new Intent(context, FoundationPracticeActivity.class));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildQuestionBank();
        if (state != null) {
            page = state.getString("page", "home");
            currentTrack = state.getString("track", "");
            currentIndex = state.getInt("index", 0);
            selectedOption = state.getInt("selected", -1);
        }
        if ("question".equals(page) && tracks.containsKey(currentTrack)) showQuestion(currentTrack, currentIndex);
        else if ("wrong".equals(page)) showWrongBook();
        else showHome();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("page", page);
        out.putString("track", currentTrack);
        out.putInt("index", currentIndex);
        out.putInt("selected", selectedOption);
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        if (!"home".equals(page)) showHome();
        else finish();
    }

    private void buildQuestionBank() {
        tracks.clear();
        for (String id : TRACK_IDS) tracks.put(id, new ArrayList<Question>());
        buildBaguaQuestions();
        buildWuxingQuestions();
        buildGanzhiQuestions();
        buildHexagramQuestions();
    }

    private void buildBaguaQuestions() {
        ArrayList<Question> rows = tracks.get("bagua");
        for (int i = 0; i < 8; i++) {
            Choices names = fourChoices(TRIGRAMS[i], TRIGRAMS[(i + 1) % 8], TRIGRAMS[(i + 3) % 8], TRIGRAMS[(i + 5) % 8], i % 4);
            rows.add(new Question("bagua_name_" + i, "bagua", "图中的三爻卦叫什么？", names.values, names.answer,
                "这是“" + TRIGRAMS[i] + "”卦。记忆口诀：“" + MNEMONICS[i] + "”。图中爻位从下往上看。",
                "lines", TRIGRAM_CODES[i]));
            String number = String.valueOf(i + 1);
            Choices numbers = fourChoices(number, String.valueOf((i + 1) % 8 + 1), String.valueOf((i + 3) % 8 + 1), String.valueOf((i + 5) % 8 + 1), (i + 1) % 4);
            rows.add(new Question("bagua_number_" + i, "bagua", "“" + TRIGRAMS[i] + "”的先天卦数是？", numbers.values, numbers.answer,
                "先天卦数为“" + TRIGRAMS[i] + (i + 1) + "”。八数依次是：乾一、兑二、离三、震四、巽五、坎六、艮七、坤八。",
                "lines", TRIGRAM_CODES[i]));
            Choices direction = fourChoices(DIRECTIONS[i], DIRECTIONS[(i + 1) % 8], DIRECTIONS[(i + 3) % 8], DIRECTIONS[(i + 6) % 8], (i + 2) % 4);
            rows.add(new Question("bagua_direction_" + i, "bagua", "后天八卦中，“" + TRIGRAMS[i] + "”位于哪个方位？", direction.values, direction.answer,
                "后天八卦中“" + TRIGRAMS[i] + "”居“" + DIRECTIONS[i] + "”。这里问的是后天方位，别与先天卦数混在一起。",
                "lines", TRIGRAM_CODES[i]));
        }
    }

    private void buildWuxingQuestions() {
        ArrayList<Question> rows = tracks.get("wuxing");
        for (int i = 0; i < 5; i++) {
            int born = (i + 1) % 5;
            Choices generating = phaseChoices(PHASES[born], i);
            rows.add(new Question("wuxing_generate_" + i, "wuxing", PHASES[i] + "生什么？", generating.values, generating.answer,
                PHASES[i] + "生" + PHASES[born] + "。相生顺序是：木生火、火生土、土生金、金生水、水生木。",
                "wuxing", PHASES[i] + "生" + PHASES[born]));
            int controlled = (i + 2) % 5;
            Choices controlling = phaseChoices(PHASES[controlled], i + 1);
            rows.add(new Question("wuxing_control_" + i, "wuxing", PHASES[i] + "克什么？", controlling.values, controlling.answer,
                PHASES[i] + "克" + PHASES[controlled] + "。相克顺序是：木克土、土克水、水克火、火克金、金克木。",
                "wuxing", PHASES[i] + "克" + PHASES[controlled]));
        }
    }

    private void buildGanzhiQuestions() {
        ArrayList<Question> rows = tracks.get("ganzhi");
        for (int i = 0; i < STEMS.length; i++) {
            Choices facts = fourChoices(STEM_FACTS[i], STEM_FACTS[(i + 1) % 10], STEM_FACTS[(i + 2) % 10], STEM_FACTS[(i + 4) % 10], i % 4);
            rows.add(new Question("stem_fact_" + i, "ganzhi", "天干“" + STEMS[i] + "”对应哪一项？", facts.values, facts.answer,
                STEMS[i] + "为" + STEM_FACTS[i] + "。天干按阳、阴相间排列，同一五行连续出现两个。",
                "ganzhi", STEMS[i]));
        }
        for (int i = 0; i < BRANCHES.length; i++) {
            Choices element = phaseChoices(BRANCH_ELEMENTS[i], i);
            rows.add(new Question("branch_element_" + i, "ganzhi", "地支“" + BRANCHES[i] + "”的五行是？", element.values, element.answer,
                BRANCHES[i] + "为" + BRANCH_POLARITIES[i] + BRANCH_ELEMENTS[i] + "。先记五行，再把阴阳一起联结。",
                "ganzhi", BRANCHES[i]));
            String next = BRANCHES[(i + 1) % 12];
            Choices sequence = fourChoices(next, BRANCHES[(i + 2) % 12], BRANCHES[(i + 5) % 12], BRANCHES[(i + 8) % 12], (i + 1) % 4);
            rows.add(new Question("branch_next_" + i, "ganzhi", "十二地支次序中，“" + BRANCHES[i] + "”的下一位是？", sequence.values, sequence.answer,
                "次序为：子、丑、寅、卯、辰、巳、午、未、申、酉、戌、亥，之后再回到子。",
                "ganzhi", BRANCHES[i] + " → ?"));
        }
    }

    private void buildHexagramQuestions() {
        ArrayList<Question> rows = tracks.get("hexagrams");
        try {
            InputStream input = getAssets().open("appdata/hexagrams.json");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 512 * 1024) throw new IllegalArgumentException("hexagram asset too large");
                    bytes.write(buffer, 0, count);
                }
            } finally { input.close(); }
            JSONArray catalog = new JSONObject(new String(bytes.toByteArray(), "UTF-8")).getJSONArray("hexagrams");
            if (catalog.length() != 64) throw new IllegalArgumentException("hexagram catalog incomplete");
            for (int i = 0; i < catalog.length(); i++) {
                JSONObject value = catalog.getJSONObject(i);
                JSONObject second = catalog.getJSONObject((i + 1) % 64);
                JSONObject third = catalog.getJSONObject((i + 9) % 64);
                JSONObject fourth = catalog.getJSONObject((i + 23) % 64);
                Choices names = fourChoices(value.getString("full_name"), second.getString("full_name"),
                    third.getString("full_name"), fourth.getString("full_name"), i % 4);
                int upper = value.getInt("upper") - 1;
                int lower = value.getInt("lower") - 1;
                rows.add(new Question("hexagram_" + value.getInt("number"), "hexagrams", "这幅六爻卦图是哪一卦？",
                    names.values, names.answer,
                    "第" + value.getInt("number") + "卦“" + value.getString("full_name") + "”：上卦为"
                        + TRIGRAMS[upper] + "，下卦为" + TRIGRAMS[lower] + "。爻位从下往上读。",
                    "lines", value.getString("code")));
            }
        } catch (Exception error) {
            rows.clear();
        }
    }

    private static Choices phaseChoices(String correct, int slotSeed) {
        int index = 0;
        for (int i = 0; i < PHASES.length; i++) if (PHASES[i].equals(correct)) { index = i; break; }
        return fourChoices(correct, PHASES[(index + 1) % 5], PHASES[(index + 2) % 5], PHASES[(index + 3) % 5], slotSeed % 4);
    }

    private static Choices fourChoices(String correct, String a, String b, String c, int answerSlot) {
        String[] source = {correct, a, b, c};
        String[] result = new String[4];
        int slot = (answerSlot % 4 + 4) % 4;
        for (int i = 0; i < 4; i++) result[(slot + i) % 4] = source[i];
        return new Choices(result, slot);
    }

    private void showHome() {
        page = "home";
        currentTrack = "";
        currentIndex = 0;
        selectedOption = -1;
        buildPage("基础习练", "看图作答 · 错题可重练 · 进度只保存在本机");
        LinearLayout actions = row();
        addWeightedButton(actions, "返回学堂", false, new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        addWeightedButton(actions, "错题记录 · " + wrongQuestionCount(), false, new View.OnClickListener() {
            @Override public void onClick(View view) { showWrongBook(); }
        });
        content.addView(actions);

        int total = totalQuestions(), mastered = masteredQuestions();
        LinearLayout overview = card();
        overview.addView(caption("第一批纯净重建练习"));
        overview.addView(heading("总掌握度  " + mastered + " / " + total, 23));
        overview.addView(progressBar(mastered, total));
        overview.addView(body("答对一题即记为已掌握；答错的题会进入错题记录，之后答对仍保留历史，便于复盘。"));

        String lastTrack = preferences.getString("last_track", TRACK_IDS[0]);
        if (!tracks.containsKey(lastTrack) || tracks.get(lastTrack).isEmpty()) lastTrack = TRACK_IDS[0];
        int lastIndex = preferences.getInt("last_index_" + lastTrack, 0);
        lastIndex = nextUnmastered(lastTrack, lastIndex);
        final String continueTrack = lastTrack;
        final int continueIndex = lastIndex;
        addButton(overview, "继续学习 · " + trackTitle(continueTrack), true, new View.OnClickListener() {
            @Override public void onClick(View view) { showQuestion(continueTrack, continueIndex); }
        });

        for (int i = 0; i < TRACK_IDS.length; i++) {
            final String id = TRACK_IDS[i];
            final ArrayList<Question> questions = tracks.get(id);
            LinearLayout tile = card();
            int done = masteredQuestions(id);
            int attempts = preferences.getInt("attempts_" + id, 0);
            int correct = preferences.getInt("correct_" + id, 0);
            tile.addView(caption(String.format(Locale.CHINA, "%02d  ·  %d题", i + 1, questions.size())));
            tile.addView(heading(TRACK_TITLES[i], 21));
            tile.addView(body(TRACK_NOTES[i]));
            tile.addView(progressBar(done, Math.max(1, questions.size())));
            tile.addView(caption("掌握 " + done + " / " + questions.size() + (attempts == 0 ? " · 尚未作答" : " · 正确率 " + Math.round(correct * 100f / attempts) + "%")));
            if (questions.isEmpty()) {
                tile.addView(caption("本地题库未能载入，请重新安装完整版本。"));
            } else {
                addButton(tile, done == questions.size() ? "抽题复习" : "开始练习", false, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        int saved = preferences.getInt("last_index_" + id, 0);
                        showQuestion(id, nextUnmastered(id, saved));
                    }
                });
            }
        }
    }

    private void showQuestion(final String track, int requestedIndex) {
        ArrayList<Question> questions = tracks.get(track);
        if (questions == null || questions.isEmpty()) { showHome(); return; }
        page = "question";
        currentTrack = track;
        currentIndex = Math.max(0, Math.min(requestedIndex, questions.size() - 1));
        final Question question = questions.get(currentIndex);
        preferences.edit().putString("last_track", track).putInt("last_index_" + track, currentIndex).apply();
        buildPage(trackTitle(track), "第 " + (currentIndex + 1) + " / " + questions.size() + " 题 · 已掌握 " + masteredQuestions(track) + " 题");

        LinearLayout navigation = row();
        addWeightedButton(navigation, "返回目录", false, new View.OnClickListener() {
            @Override public void onClick(View view) { showHome(); }
        });
        addWeightedButton(navigation, "查看错题", false, new View.OnClickListener() {
            @Override public void onClick(View view) { showWrongBook(); }
        });
        content.addView(navigation);
        content.addView(progressBar(masteredQuestions(track), questions.size()), simpleParams(0, 10, 0, 0));

        LinearLayout prompt = card();
        if (question.visual.length() > 0) {
            PracticeVisual visual = new PracticeVisual(this, question.visual, question.visualLabel);
            prompt.addView(visual, new LinearLayout.LayoutParams(-1, dp("wuxing".equals(question.visual) ? 235 : 190)));
        }
        prompt.addView(heading(question.prompt, 23));
        final boolean answered = selectedOption >= 0;
        for (int i = 0; i < question.options.length; i++) {
            final int choice = i;
            String label = (char) ('A' + i) + ".  " + question.options[i];
            if (answered && i == selectedOption) label = "已选 · " + label;
            Button answer = addButton(prompt, label, answered && i == question.answer, new View.OnClickListener() {
                @Override public void onClick(View view) { submit(question, choice); }
            });
            answer.setEnabled(!answered);
        }

        if (answered) {
            boolean correct = selectedOption == question.answer;
            LinearLayout feedback = card();
            feedback.addView(heading(correct ? "答对了" : "再看一眼关键关系", 22));
            feedback.addView(body("正确答案：" + (char) ('A' + question.answer) + ". " + question.options[question.answer]));
            feedback.addView(body(question.explanation));
            if (!correct) {
                addButton(feedback, "再答一次", true, new View.OnClickListener() {
                    @Override public void onClick(View view) { selectedOption = -1; showQuestion(currentTrack, currentIndex); }
                });
            }
            addButton(feedback, correct ? "下一题" : "先看下一题", false, new View.OnClickListener() {
                @Override public void onClick(View view) { moveNext(); }
            });
        } else {
            LinearLayout note = card();
            note.addView(caption("选择答案后立即显示正确项与记忆提示。"));
        }
    }

    private void submit(Question question, int choice) {
        if (selectedOption >= 0) return;
        selectedOption = choice;
        SharedPreferences.Editor edit = preferences.edit().putInt("attempts_" + question.track,
            preferences.getInt("attempts_" + question.track, 0) + 1);
        if (choice == question.answer) {
            edit.putInt("correct_" + question.track, preferences.getInt("correct_" + question.track, 0) + 1);
            edit.putBoolean("mastered_" + question.id, true);
        } else {
            edit.putInt("wrong_" + question.id, preferences.getInt("wrong_" + question.id, 0) + 1);
        }
        if (!edit.commit()) Toast.makeText(this, "练习记录未能保存，请检查剩余空间", Toast.LENGTH_LONG).show();
        showQuestion(currentTrack, currentIndex);
    }

    private void moveNext() {
        ArrayList<Question> questions = tracks.get(currentTrack);
        if (questions == null || questions.isEmpty()) { showHome(); return; }
        selectedOption = -1;
        int candidate = nextUnmastered(currentTrack, (currentIndex + 1) % questions.size());
        if (masteredQuestions(currentTrack) == questions.size()) {
            showTrackComplete(currentTrack);
        } else showQuestion(currentTrack, candidate);
    }

    private void showTrackComplete(final String track) {
        page = "complete";
        buildPage("本组已掌握", trackTitle(track));
        LinearLayout summary = card();
        summary.addView(heading("这一组的题目已经全部答对", 24));
        int attempts = preferences.getInt("attempts_" + track, 0);
        int correct = preferences.getInt("correct_" + track, 0);
        summary.addView(body("累计作答 " + attempts + " 次 · 答对 " + correct + " 次 · 错题历史 " + wrongQuestionCount(track) + " 题。"));
        addButton(summary, "返回练习目录", true, new View.OnClickListener() {
            @Override public void onClick(View view) { showHome(); }
        });
        addButton(summary, "复习第一题", false, new View.OnClickListener() {
            @Override public void onClick(View view) { selectedOption = -1; showQuestion(track, 0); }
        });
    }

    private void showWrongBook() {
        page = "wrong";
        currentTrack = "";
        selectedOption = -1;
        buildPage("错题记录", "保留错题历史；答对后显示为已掌握");
        addButton(content, "返回练习目录", false, new View.OnClickListener() {
            @Override public void onClick(View view) { showHome(); }
        });
        int found = 0;
        for (String track : TRACK_IDS) {
            ArrayList<Question> questions = tracks.get(track);
            if (questions == null) continue;
            for (int index = 0; index < questions.size(); index++) {
                final Question question = questions.get(index);
                int wrong = preferences.getInt("wrong_" + question.id, 0);
                if (wrong <= 0) continue;
                found++;
                final String targetTrack = track;
                final int targetIndex = index;
                LinearLayout item = card();
                item.addView(caption(trackTitle(track) + " · 错过 " + wrong + " 次 · "
                    + (isMastered(question) ? "现已掌握" : "待巩固")));
                item.addView(heading(question.prompt, 20));
                item.addView(body("正确答案：" + question.options[question.answer]));
                item.addView(caption(question.explanation));
                addButton(item, "重练此题", !isMastered(question), new View.OnClickListener() {
                    @Override public void onClick(View view) { showQuestion(targetTrack, targetIndex); }
                });
            }
        }
        if (found == 0) {
            LinearLayout empty = card();
            empty.addView(heading("还没有错题", 23));
            empty.addView(body("开始练习后，答错的题会自动汇总在这里。"));
        }
    }

    private int nextUnmastered(String track, int start) {
        ArrayList<Question> rows = tracks.get(track);
        if (rows == null || rows.isEmpty()) return 0;
        int safe = (start % rows.size() + rows.size()) % rows.size();
        for (int step = 0; step < rows.size(); step++) {
            int index = (safe + step) % rows.size();
            if (!isMastered(rows.get(index))) return index;
        }
        return safe;
    }

    private boolean isMastered(Question question) { return preferences.getBoolean("mastered_" + question.id, false); }
    private int totalQuestions() {
        int total = 0;
        for (ArrayList<Question> rows : tracks.values()) total += rows.size();
        return total;
    }
    private int masteredQuestions() {
        int total = 0;
        for (String id : TRACK_IDS) total += masteredQuestions(id);
        return total;
    }
    private int masteredQuestions(String track) {
        int total = 0;
        ArrayList<Question> rows = tracks.get(track);
        if (rows != null) for (Question question : rows) if (isMastered(question)) total++;
        return total;
    }
    private int wrongQuestionCount() {
        int total = 0;
        for (String id : TRACK_IDS) total += wrongQuestionCount(id);
        return total;
    }
    private int wrongQuestionCount(String track) {
        int total = 0;
        ArrayList<Question> rows = tracks.get(track);
        if (rows != null) for (Question question : rows)
            if (preferences.getInt("wrong_" + question.id, 0) > 0) total++;
        return total;
    }
    private static String trackTitle(String id) {
        for (int i = 0; i < TRACK_IDS.length; i++) if (TRACK_IDS[i].equals(id)) return TRACK_TITLES[i];
        return "基础习练";
    }

    private void buildPage(String title, String subtitle) {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        JianYiUi.applyScenic(scroll,JianYiUi.SCENE_READING);
        scroll.setFitsSystemWindows(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(10), dp(18), dp(30));
        content.addView(JianYiUi.hero(this, title, subtitle));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        JianYiUi.applySystemBars(this);
        JianYiUi.enter(content);
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(16), dp(16), dp(16), dp(16));
        JianYiUi.surface(view);
        LinearLayout.LayoutParams params = simpleParams(0, 12, 0, 0);
        content.addView(view, params);
        return view;
    }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        return row;
    }
    private TextView heading(String value, float size) {
        TextView view = text(value, size, JianYiUi.INK, true);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }
    private TextView body(String value) {
        TextView view = text(value, 16f, JianYiUi.INK, false);
        view.setPadding(0, dp(2), 0, dp(8));
        return view;
    }
    private TextView caption(String value) {
        TextView view = text(value, 13f, JianYiUi.MUTED, false);
        view.setPadding(0, dp(3), 0, dp(7));
        return view;
    }
    private TextView text(String value, float size, int color, boolean title) {
        TextView view = new TextView(this);
        view.setText(value);
        JianYiUi.textStyle(view, size, title);
        view.setTextColor(color);
        view.setLineSpacing(dp(4), 1.04f);
        return view;
    }
    private Button addButton(LinearLayout parent, String label, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        JianYiUi.skinButton(button, primary);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        parent.addView(button, params);
        return button;
    }
    private void addWeightedButton(LinearLayout parent, String label, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        JianYiUi.skinButton(button, primary);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(dp(3), dp(5), dp(3), dp(5));
        parent.addView(button, params);
    }
    private View progressBar(int value, int maximum) {
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(JianYiUi.rounded(this, 0xffe7ece5, 0, 6f));
        int safeMax = Math.max(1, maximum);
        View fill = new View(this);
        fill.setBackground(JianYiUi.rounded(this, JianYiUi.ACCENT, 0, 6f));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(8), Math.max(0.001f, Math.min(1f, value / (float) safeMax))));
        View empty = new View(this);
        track.addView(empty, new LinearLayout.LayoutParams(0, dp(8), Math.max(0.001f, 1f - Math.min(1f, value / (float) safeMax))));
        track.setContentDescription("完成进度 " + value + " / " + maximum);
        return track;
    }
    private LinearLayout.LayoutParams simpleParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }
    private int dp(float value) { return JianYiUi.dp(this, value); }

    private static final class PracticeVisual extends View {
        private final String kind, label;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        PracticeVisual(Context context, String kind, String label) {
            super(context);
            this.kind = kind;
            this.label = label == null ? "" : label;
            setContentDescription("wuxing".equals(kind) ? "五行关系图，当前考查" + this.label
                : "卦图，爻位从下往上读");
        }
        private float dp(float value) { return JianYiUi.dp(getContext(), value); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if ("wuxing".equals(kind)) drawWuxing(canvas);
            else if ("ganzhi".equals(kind)) drawGanzhi(canvas);
            else drawLines(canvas);
        }
        private void drawLines(Canvas canvas) {
            if (!label.matches("[01]{3}|[01]{6}")) return;
            float width = Math.min(getWidth() * 0.72f, dp(250));
            float start = (getWidth() - width) / 2f, end = start + width, center = getWidth() / 2f;
            float gap = Math.max(dp(17), width * 0.10f);
            float spacing = getHeight() / (label.length() + 1f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(7));
            paint.setColor(JianYiUi.INK);
            for (int i = 0; i < label.length(); i++) {
                float y = getHeight() - spacing * (i + 1);
                if (label.charAt(i) == '1') canvas.drawLine(start, y, end, y, paint);
                else {
                    canvas.drawLine(start, y, center - gap, y, paint);
                    canvas.drawLine(center + gap, y, end, y, paint);
                }
            }
        }
        private void drawGanzhi(Canvas canvas) {
            paint.setColor(0xffeef2ea);
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) * .36f, paint);
            paint.setColor(JianYiUi.INK);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            paint.setTextSize(dp(label.length() > 2 ? 30 : 58));
            Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(label, getWidth() / 2f, getHeight() / 2f - (fm.ascent + fm.descent) / 2f, paint);
        }
        private void drawWuxing(Canvas canvas) {
            final int[] colors = {0xff3f8a5a, 0xffc64a3d, 0xffa77a3f, 0xff8f9692, 0xff39759b};
            float cx = getWidth() / 2f, cy = getHeight() / 2f + dp(5);
            float radius = Math.min(getWidth(), getHeight()) * .34f;
            float node = Math.min(dp(29), radius * .28f);
            float[] xs = new float[5], ys = new float[5];
            for (int i = 0; i < 5; i++) {
                double angle = -Math.PI / 2 + i * Math.PI * 2 / 5;
                xs[i] = cx + (float) Math.cos(angle) * radius;
                ys[i] = cy + (float) Math.sin(angle) * radius;
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0xff9aac9f);
            Path cycle = new Path();
            cycle.moveTo(xs[0], ys[0]);
            for (int i = 1; i < 5; i++) cycle.lineTo(xs[i], ys[i]);
            cycle.close();
            canvas.drawPath(cycle, paint);
            Path star = new Path();
            star.moveTo(xs[0], ys[0]);
            int position = 0;
            for (int i = 0; i < 5; i++) { position = (position + 2) % 5; star.lineTo(xs[position], ys[position]); }
            canvas.drawPath(star, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(dp(20));
            for (int i = 0; i < 5; i++) {
                paint.setColor(colors[i]);
                canvas.drawCircle(xs[i], ys[i], node, paint);
                paint.setColor(0xffffffff);
                Paint.FontMetrics fm = paint.getFontMetrics();
                canvas.drawText(PHASES[i], xs[i], ys[i] - (fm.ascent + fm.descent) / 2f, paint);
            }
            paint.setColor(JianYiUi.INK);
            paint.setTextSize(dp(16));
            canvas.drawText(label, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint);
        }
    }
}
