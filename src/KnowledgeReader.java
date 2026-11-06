package com.jianyi.yixue.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Topic-specific, native teaching pages. All prose and diagrams are packaged locally. */
public final class KnowledgeReader {
    private static final int READING_PAPER = 0xfffffdf8;
    private static final int READING_SOFT = 0xfff0ece3;
    private static final int READING_LINE = 0xffd7d0c1;
    private static final int VERMILION = 0xffa74436;
    private static final String[] ASSETS = {"knowledge-foundation.json", "knowledge-advanced-guides.json"};
    private static final String[] ELEMENTS = {"木", "火", "土", "金", "水"};
    private static final String[] STEMS = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] BRANCHES = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] ANIMALS = {"鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"};
    private static final String[] BRANCH_ELEMENTS = {"水", "土", "木", "木", "土", "火", "火", "土", "金", "金", "土", "水"};
    private static final String[] BRANCH_DIRECTIONS = {"正北", "东北", "东北", "正东", "东南", "东南", "正南", "西南", "西南", "正西", "西北", "西北"};
    private static final String[] HOURS = {"23—01 时", "01—03 时", "03—05 时", "05—07 时", "07—09 时", "09—11 时", "11—13 时", "13—15 时", "15—17 时", "17—19 时", "19—21 时", "21—23 时"};
    private static final String[] TRIGRAM_ELEMENTS = {"", "金", "金", "火", "木", "木", "水", "土", "土"};
    private static final String[] TRIGRAM_IMAGES = {"", "天", "泽", "火", "雷", "风", "水", "山", "地"};
    private static final String[] TRIGRAM_DIRECTIONS = {"", "西北", "正西", "正南", "正东", "东南", "正北", "东北", "西南"};
    private static final int[] HOUTIAN_NUMBERS = {0, 6, 7, 9, 3, 4, 1, 8, 2};
    private static final String[] POLARITY = {"", "阳卦", "阴卦", "阴卦", "阳卦", "阴卦", "阳卦", "阳卦", "阴卦"};
    private static final HashSet<String> VISUALS = new HashSet<String>();
    private static Catalog catalog;
    static {
        for (String type : new String[] {"bagua", "bagua_compass", "elements", "stems", "branches",
            "pairs", "triples", "sequence", "steps", "hexagrams", "hetu_luoshu", "polarity"}) VISUALS.add(type);
    }
    private KnowledgeReader() { }

    private static final class Catalog {
        final Map<String, JSONObject> guides = new LinkedHashMap<String, JSONObject>();
        final Map<String, JSONObject> sources = new HashMap<String, JSONObject>();
    }

    private static synchronized Catalog load(Context context) throws Exception {
        if (catalog != null) return catalog;
        Catalog result = new Catalog();
        HashSet<String> ids = new HashSet<String>();
        for (String asset : ASSETS) {
            InputStream input = context.getAssets().open("appdata/" + asset);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 2 * 1024 * 1024) throw new IllegalArgumentException("guide asset too large");
                    bytes.write(buffer, 0, count);
                }
            } finally { input.close(); }
            JSONObject root = new JSONObject(new String(bytes.toByteArray(), "UTF-8"));
            if (root.getInt("schema_version") != 1) throw new IllegalArgumentException("guide version");
            JSONArray sources = root.getJSONArray("sources");
            for (int i = 0; i < sources.length(); i++) {
                JSONObject source = sources.getJSONObject(i);
                String id = required(source, "id");
                JSONObject old = result.sources.get(id);
                if (old != null && !old.toString().equals(source.toString())) throw new IllegalArgumentException("conflicting source");
                result.sources.put(id, source);
            }
            JSONArray guides = root.getJSONArray("guides");
            for (int i = 0; i < guides.length(); i++) {
                JSONObject guide = guides.getJSONObject(i);
                if (!ids.add(required(guide, "id")) || !VISUALS.contains(required(guide, "visual"))) throw new IllegalArgumentException("guide identity");
                required(guide, "title"); required(guide, "lead"); required(guide, "category");
                JSONArray names = guide.getJSONArray("names");
                if (names.length() == 0) throw new IllegalArgumentException("guide alias");
                for (int j = 0; j < names.length(); j++) {
                    String name = normalize(names.getString(j));
                    if (name.length() == 0 || (result.guides.containsKey(name) && result.guides.get(name) != guide)) throw new IllegalArgumentException("duplicate guide alias: " + name);
                    result.guides.put(name, guide);
                }
                JSONArray sections = guide.getJSONArray("sections");
                if (sections.length() == 0) throw new IllegalArgumentException("empty guide");
                for (int j = 0; j < sections.length(); j++) {
                    JSONObject section = sections.getJSONObject(j); required(section, "title");
                    JSONArray paragraphs = section.getJSONArray("paragraphs");
                    if (paragraphs.length() == 0) throw new IllegalArgumentException("empty section");
                    for (int k = 0; k < paragraphs.length(); k++) if (paragraphs.getString(k).trim().length() == 0) throw new IllegalArgumentException("empty paragraph");
                }
            }
        }
        if (ids.size() != 46) throw new IllegalArgumentException("46 guides required");
        for (JSONObject guide : result.guides.values()) {
            JSONArray sourceIds = guide.getJSONArray("source_ids");
            if (sourceIds.length() == 0) throw new IllegalArgumentException("source required");
            for (int i = 0; i < sourceIds.length(); i++) if (!result.sources.containsKey(sourceIds.getString(i))) throw new IllegalArgumentException("missing source");
        }
        catalog = result;
        return catalog;
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().replaceAll("[\\s　]+", ""); }
    private static String required(JSONObject object, String key) throws Exception {
        String value = object.getString(key).trim();
        if (value.length() == 0) throw new IllegalArgumentException(key);
        return value;
    }

    public static void show(final Activity activity, String requested) {
        if (activity == null || activity.isFinishing()) return;
        LinearLayout shell = column(activity);
        JianYiUi.applyScenic(shell,JianYiUi.SCENE_READING);
        shell.setFitsSystemWindows(true);
        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 18), dp(activity, 8));
        final boolean fromSearch = activity instanceof MeihuaReferenceActivity;
        Button back = button(activity, fromSearch ? "‹ 返回搜索" : "‹ 返回练习", false, new View.OnClickListener() {
            @Override public void onClick(View view) { activity.finish(); }
        });
        back.setContentDescription(fromSearch ? "知识点返回搜索" : "知识点返回练习");
        toolbar.addView(back, new LinearLayout.LayoutParams(-2, -2));
        TextView label = label(activity, "知识图解", 13, JianYiUi.MUTED, false);
        label.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        toolbar.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        shell.addView(toolbar);
        final ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout root = column(activity);
        root.setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 18), dp(activity, 32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        activity.setContentView(shell);
        JianYiUi.applySystemBars(activity);
        try {
            Catalog data = load(activity);
            JSONObject guide = data.guides.get(normalize(requested));
            if (guide == null) {
                root.addView(label(activity, requested == null ? "知识图解" : requested, 27, JianYiUi.INK, true));
                root.addView(label(activity, "从相关主题继续学习", 17, JianYiUi.MUTED, false));
                root.addView(button(activity, "查阅进阶资料", true, new View.OnClickListener() {
                    @Override public void onClick(View view) { MeihuaReferenceActivity.open(activity); }
                }));
                return;
            }
            header(activity, root, guide);
            visual(activity, root, guide);
            if (guide.optString("memory_tip").trim().length() > 0) {
                LinearLayout tip = card(activity, JianYiUi.SOFT);
                tip.addView(label(activity, "一句话记住", 13, JianYiUi.MUTED, false));
                tip.addView(label(activity, guide.getString("memory_tip"), 17, JianYiUi.INK, true));
                root.addView(tip);
            }
            JSONArray groups = guide.optJSONArray("groups");
            String kind = guide.getString("visual");
            if (groups != null && groups.length() > 0 && !usesGroupsAsVisual(kind)) {
                LinearLayout grouped = column(activity);
                addGroups(activity, grouped, groups);
                expandable(activity, root, "完整分组速查 · " + groups.length() + " 组", grouped, false);
            }
            JSONArray sections = guide.getJSONArray("sections");
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.getJSONObject(i);
                LinearLayout content = column(activity);
                JSONArray paragraphs = section.getJSONArray("paragraphs");
                for (int j = 0; j < paragraphs.length(); j++) content.addView(label(activity, paragraphs.getString(j), 16, JianYiUi.INK, false));
                expandable(activity, root, section.getString("title"), content, i == 0);
            }
            JSONObject example = guide.optJSONObject("example");
            if (example != null) {
                LinearLayout box = card(activity, JianYiUi.WHITE);
                box.addView(label(activity, "用一个例子理解", 13, JianYiUi.MUTED, false));
                box.addView(label(activity, example.optString("title"), 19, JianYiUi.INK, true));
                box.addView(label(activity, example.optString("text"), 16, JianYiUi.INK, false));
                root.addView(box);
            }
            final String query = guide.optString("related_query", "").trim();
            if (query.length() > 0) root.addView(button(activity, "继续查阅 · " + query, false, new View.OnClickListener() {
                @Override public void onClick(View view) { MeihuaReferenceActivity.open(activity, query); }
            }));
            if ("hexagrams".equals(kind)) root.addView(button(activity, "打开六十四卦解析", false, new View.OnClickListener() {
                @Override public void onClick(View view) { StudyModules.showLibrary(activity); }
            }));
            LinearLayout sources = column(activity);
            JSONArray sourceIds = guide.getJSONArray("source_ids");
            for (int i = 0; i < sourceIds.length(); i++) {
                JSONObject source = data.sources.get(sourceIds.getString(i));
                sources.addView(label(activity, source.optString("title"), 15, JianYiUi.INK, true));
                sources.addView(label(activity, source.optString("url"), 13, JianYiUi.MUTED, false));
            }
            expandable(activity, root, "资料出处", sources, false);
            JianYiUi.enter(root);
        } catch (Exception invalid) {
            root.removeAllViews();
            root.addView(label(activity, "知识图解暂未载入", 26, JianYiUi.INK, true));
            root.addView(label(activity, "请返回后重试，或重新安装完整版本。", 17, JianYiUi.MUTED, false));
        }
    }

    private static void header(Activity activity, LinearLayout root, JSONObject guide) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = column(activity);
        words.addView(label(activity, guide.optString("category") + "  /  知识图解", 13, VERMILION, false));
        words.addView(label(activity, guide.optString("title"), 27, JianYiUi.INK, true));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        String type = guide.optString("visual");
        View emblem = new KnowledgeMotifView(activity, "elements".equals(type) || "stems".equals(type) ? "Wuxing" : "Luoshu");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58));
        params.leftMargin = dp(activity, 12);
        row.addView(emblem, params);
        root.addView(row);
        TextView lead = label(activity, guide.optString("lead"), 16, JianYiUi.MUTED, false);
        lead.setPadding(0, dp(activity, 8), 0, dp(activity, 14));
        root.addView(lead);
        View rule = new View(activity);
        rule.setBackgroundColor(READING_LINE);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(-1, dp(activity, 1));
        ruleParams.bottomMargin = dp(activity, 8);
        root.addView(rule, ruleParams);
    }

    private static boolean usesGroupsAsVisual(String type) {
        return "pairs".equals(type) || "triples".equals(type) || "sequence".equals(type) || "steps".equals(type);
    }

    private static void visual(final Activity activity, LinearLayout root, JSONObject guide) throws Exception {
        String type = guide.getString("visual"), mode = guide.optString("visual_mode", "");
        String guideId = guide.optString("id");
        if ("F14".equals(guideId) || "advanced_bazi_05".equals(guideId)) {
            groupTabs(activity, root, guide.getJSONArray("groups"), "按五行查纳音 · 两柱配一名", 5, true); return;
        }
        if ("advanced_liuyao_02".equals(guideId)) {
            groupTabs(activity, root, guide.getJSONArray("groups"), "点一个六兽，看它的传统类象", 3, false); return;
        }
        if ("advanced_bazi_09".equals(guideId)) {
            groupTabs(activity, root, guide.getJSONArray("groups"), "以日主为中心，先分五种关系", 5, false); return;
        }
        if ("bagua".equals(type)) { bagua(activity, root, mode, guide.optString("title")); return; }
        if ("stems".equals(type)) { stems(activity, root, mode); return; }
        if ("elements".equals(type)) { elements(activity, root); return; }
        if ("branches".equals(type)) { branches(activity, root, guide); return; }
        if ("bagua_compass".equals(type)) { compass(activity, root, mode); return; }
        if ("hetu_luoshu".equals(type)) { hetuLuoshu(activity, root); return; }
        if ("polarity".equals(type) || "hexagrams".equals(type)) {
            LinearLayout frame = card(activity, JianYiUi.WHITE);
            frame.addView(label(activity, "polarity".equals(type) ? "先认两种爻" : "从下向上，认识六个爻位", 18, JianYiUi.INK, true));
            frame.addView(new KnowledgeDiagramView(activity, type, mode), new LinearLayout.LayoutParams(-1, -2));
            root.addView(frame);
            return;
        }
        JSONArray groups = guide.optJSONArray("groups");
        if (groups != null && groups.length() > 0) {
            if ("steps".equals(type) || "sequence".equals(type)) {
                for (int i = 0; i < groups.length(); i++) {
                    JSONObject group = groups.getJSONObject(i);
                    LinearLayout box = card(activity, JianYiUi.WHITE);
                    box.addView(label(activity, String.format(java.util.Locale.ROOT, "%02d", i + 1) + "  " + group.optString("title"), 19, JianYiUi.INK, true));
                    groupItems(activity, box, group.optJSONArray("items"));
                    if (group.optString("note").length() > 0) box.addView(label(activity, group.optString("note"), 14, JianYiUi.MUTED, false));
                    root.addView(box);
                }
            } else addGroups(activity, root, groups);
        }
    }

    private static void groupTabs(final Activity activity, LinearLayout root, final JSONArray groups,
        String heading, int columns, boolean oneCharacterLabels) throws Exception {
        LinearLayout box = card(activity, JianYiUi.WHITE);
        box.addView(label(activity, heading, 18, JianYiUi.INK, true));
        final LinearLayout detail = column(activity);
        final ArrayList<Button> buttons = new ArrayList<Button>();
        final Runnable[] choose = new Runnable[groups.length()];
        for (int start = 0; start < groups.length(); start += columns) {
            LinearLayout tabs = new LinearLayout(activity);
            for (int i = start; i < Math.min(start + columns, groups.length()); i++) {
                final int selected = i; final JSONObject group = groups.getJSONObject(i);
                choose[i] = new Runnable() {
                    @Override public void run() {
                        for (int j = 0; j < buttons.size(); j++) skinTab(buttons.get(j), j == selected);
                        detail.removeAllViews();
                        detail.addView(label(activity, group.optString("title"), 21, JianYiUi.INK, true));
                        try { groupItems(activity, detail, group.optJSONArray("items")); }
                        catch (Exception invalid) { detail.addView(label(activity, "请返回后重试。", 16, JianYiUi.MUTED, false)); }
                        if (group.optString("note").length() > 0) detail.addView(label(activity, group.optString("note"), 14, JianYiUi.MUTED, false));
                    }
                };
                String title = group.optString("title");
                String shortLabel = title.split("[·：]", 2)[0].trim();
                if (oneCharacterLabels && shortLabel.length() > 0) shortLabel = shortLabel.substring(0, 1);
                Button button = compactButton(activity, shortLabel, new View.OnClickListener() {
                    @Override public void onClick(View view) { choose[selected].run(); }
                });
                button.setContentDescription("查看分组" + title);
                buttons.add(button); addWeighted(activity, tabs, button, i > start);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(activity, 7); box.addView(tabs, params);
        }
        box.addView(detail); choose[0].run(); root.addView(box);
    }

    private static void bagua(final Activity activity, LinearLayout root, String mode, String title) {
        final LinearLayout box = card(activity, JianYiUi.WHITE);
        if ("elements".equals(mode) || title.contains("五行")) {
            box.addView(label(activity, "按五行，把八卦分成五组", 18, JianYiUi.INK, true));
            box.addView(label(activity, "点击一个五行，看它对应哪些卦。", 14, JianYiUi.MUTED, false));
            final LinearLayout tabs = new LinearLayout(activity), detail = column(activity);
            final ArrayList<Button> buttons = new ArrayList<Button>();
            final Runnable[] choose = new Runnable[ELEMENTS.length];
            for (int i = 0; i < ELEMENTS.length; i++) {
                final int chosen = i;
                choose[i] = new Runnable() {
                    @Override public void run() {
                        detail.removeAllViews();
                        for (int j = 0; j < buttons.size(); j++) skinTab(buttons.get(j), j == chosen);
                        for (int id = 1; id <= 8; id++) if (ELEMENTS[chosen].equals(TRIGRAM_ELEMENTS[id])) detail.addView(trigramCard(activity, id, "五行属" + ELEMENTS[chosen]));
                    }
                };
                Button tab = compactButton(activity, ELEMENTS[i], new View.OnClickListener() {
                    @Override public void onClick(View view) { choose[chosen].run(); }
                });
                tab.setContentDescription("查看" + ELEMENTS[i] + "对应的八卦");
                buttons.add(tab); addWeighted(activity, tabs, tab, i > 0);
            }
            box.addView(tabs); box.addView(detail); choose[0].run();
        } else if ("polarity".equals(mode) || title.contains("阴阳")) {
            box.addView(label(activity, "阳卦与阴卦，各有四个", 18, JianYiUi.INK, true));
            box.addView(label(activity, "不是按阳爻多少，简单地投票决定。", 14, JianYiUi.MUTED, false));
            for (String kind : new String[] {"阳卦", "阴卦"}) {
                box.addView(label(activity, kind, 16, JianYiUi.MUTED, true));
                for (int id = 1; id <= 8; id++) if (kind.equals(POLARITY[id])) box.addView(trigramCard(activity, id, kind));
            }
        } else {
            final boolean numbers = "numbers".equals(mode);
            box.addView(label(activity, numbers ? "先天卦数 · 从 1 记到 8" : "点一个卦，图、名、物象一起记", 18, JianYiUi.INK, true));
            final LinearLayout detail = column(activity);
            final ArrayList<Button> tabs = new ArrayList<Button>(); final Runnable[] choose = new Runnable[8];
            for (int row = 0; row < 2; row++) {
                LinearLayout cells = new LinearLayout(activity);
                for (int col = 0; col < 4; col++) {
                    final int id = row * 4 + col + 1;
                    choose[id - 1] = new Runnable() {
                        @Override public void run() {
                            for (int j = 0; j < tabs.size(); j++) skinTab(tabs.get(j), j == id - 1);
                            detail.removeAllViews();
                            detail.addView(trigramCard(activity, id, numbers ? "先天数 " + id : POLARITY[id]));
                            detail.addView(facts(activity, new String[][] {
                                {"五行", TRIGRAM_ELEMENTS[id]}, {"后天方位", TRIGRAM_DIRECTIONS[id]}
                            }));
                        }
                    };
                    Button tab = compactButton(activity, (numbers ? id + " " : "") + MeihuaEngine.trigramName(id), new View.OnClickListener() {
                        @Override public void onClick(View view) { choose[id - 1].run(); }
                    });
                    tab.setContentDescription("查看" + MeihuaEngine.trigramName(id) + "卦的图解");
                    tabs.add(tab); addWeighted(activity, cells, tab, col > 0);
                }
                LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
                layout.topMargin = dp(activity, 7); box.addView(cells, layout);
            }
            box.addView(detail); choose[0].run();
        }
        root.addView(box);
    }

    private static View trigramCard(Activity activity, int id, String caption) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 10), dp(activity, 12), dp(activity, 10), dp(activity, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, 10); row.setLayoutParams(params);
        row.setBackground(JianYiUi.rounded(activity, JianYiUi.SOFT, 0, 14));
        row.addView(new TrigramIcon(activity, id), new LinearLayout.LayoutParams(dp(activity, 54), dp(activity, 44)));
        LinearLayout text = column(activity);
        text.setPadding(dp(activity, 14), 0, 0, 0);
        text.addView(label(activity, MeihuaEngine.trigramName(id) + " · " + TRIGRAM_IMAGES[id], 22, JianYiUi.INK, true));
        text.addView(label(activity, caption, 14, JianYiUi.MUTED, false));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private static void stems(Activity activity, LinearLayout root, String mode) {
        LinearLayout box = card(activity, JianYiUi.WHITE);
        if ("polarity".equals(mode)) {
            box.addView(label(activity, "十天干按顺序，一阳一阴交替", 18, JianYiUi.INK, true));
            for (int j = 0; j < 2; j++) {
                LinearLayout group = card(activity, JianYiUi.SOFT);
                group.addView(label(activity, j == 0 ? "阳干 · 排在单数位" : "阴干 · 排在双数位", 18, JianYiUi.INK, true));
                for (int i = j; i < STEMS.length; i += 2) {
                    group.addView(label(activity, STEMS[i] + "  ·  " + ELEMENTS[i / 2], 21, JianYiUi.INK, true));
                }
                box.addView(group);
            }
            root.addView(box); return;
        }
        box.addView(label(activity, "五行各两个，一阳配一阴", 18, JianYiUi.INK, true));
        box.addView(label(activity, "先记一组里的两个字，再分前阳、后阴。", 14, JianYiUi.MUTED, false));
        for (int i = 0; i < ELEMENTS.length; i++) {
            LinearLayout group = card(activity, JianYiUi.SOFT);
            group.addView(label(activity, ELEMENTS[i], 18, JianYiUi.INK, true));
            LinearLayout pair = new LinearLayout(activity);
            for (int j = 0; j < 2; j++) {
                LinearLayout item = column(activity);
                item.setGravity(Gravity.CENTER);
                item.addView(label(activity, STEMS[i * 2 + j], 28, JianYiUi.INK, true));
                item.addView(label(activity, j == 0 ? "阳" : "阴", 14, JianYiUi.MUTED, false));
                addWeighted(activity, pair, item, j > 0);
            }
            group.addView(pair); box.addView(group);
        }
        root.addView(box);
    }

    private static void elements(final Activity activity, LinearLayout root) {
        LinearLayout box = card(activity, JianYiUi.WHITE);
        box.addView(label(activity, "顺着箭头读：相生", 18, JianYiUi.INK, true));
        KnowledgeMotifView motif = new KnowledgeMotifView(activity, "Wuxing");
        LinearLayout.LayoutParams chart = new LinearLayout.LayoutParams(dp(activity, 220), dp(activity, 220));
        chart.gravity = Gravity.CENTER_HORIZONTAL; box.addView(motif, chart);
        final LinearLayout tabs = new LinearLayout(activity), detail = column(activity);
        final ArrayList<Button> buttons = new ArrayList<Button>(); final Runnable[] choose = new Runnable[5];
        for (int i = 0; i < 5; i++) {
            final int index = i;
            choose[i] = new Runnable() {
                @Override public void run() {
                    for (int j = 0; j < buttons.size(); j++) skinTab(buttons.get(j), j == index);
                    detail.removeAllViews();
                    detail.addView(label(activity, "以“" + ELEMENTS[index] + "”为中心看关系", 18, JianYiUi.INK, true));
                    detail.addView(facts(activity, new String[][] {
                        {"生我", ELEMENTS[(index + 4) % 5]}, {"我生", ELEMENTS[(index + 1) % 5]},
                        {"克我", ELEMENTS[(index + 3) % 5]}, {"我克", ELEMENTS[(index + 2) % 5]}
                    }));
                }
            };
            Button tab = compactButton(activity, ELEMENTS[i], new View.OnClickListener() {
                @Override public void onClick(View view) { choose[index].run(); }
            });
            tab.setContentDescription("以" + ELEMENTS[i] + "查看生克关系");
            buttons.add(tab); addWeighted(activity, tabs, tab, i > 0);
        }
        box.addView(tabs); box.addView(detail); choose[0].run(); root.addView(box);
    }

    private static void branches(final Activity activity, LinearLayout root, final JSONObject guide) {
        String mode = guide.optString("visual_mode", "");
        final boolean hiddenStems = "advanced_bazi_01".equals(guide.optString("id"));
        LinearLayout box = card(activity, JianYiUi.WHITE);
        if ("elements".equals(mode) || "polarity".equals(mode)) {
            boolean yinYang = "polarity".equals(mode);
            box.addView(label(activity, yinYang ? "按顺序交替分阴阳" : "先记四季，再记四个土支", 18, JianYiUi.INK, true));
            String[] categories = yinYang ? new String[] {"阳支", "阴支"} : ELEMENTS;
            for (int group = 0; group < categories.length; group++) {
                LinearLayout part = card(activity, JianYiUi.SOFT);
                part.addView(label(activity, categories[group], 20, JianYiUi.INK, true));
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < BRANCHES.length; i++) {
                    if (yinYang ? i % 2 != group : !categories[group].equals(BRANCH_ELEMENTS[i])) continue;
                    if (names.length() > 0) names.append(" · "); names.append(BRANCHES[i]);
                }
                part.addView(label(activity, names.toString(), 23, JianYiUi.INK, true));
                box.addView(part);
            }
            root.addView(box); return;
        }
        boolean zodiac = "zodiac".equals(mode);
        box.addView(label(activity, hiddenStems ? "地支是容器，点开看藏干" : zodiac ? "一个地支，对应一个生肖" : "十二地支 · 放在方位里记", 18, JianYiUi.INK, true));
        if (!zodiac && !hiddenStems) {
            box.addView(label(activity, "传统方位示意：南上北下、东左西右。", 14, JianYiUi.MUTED, false));
            box.addView(new KnowledgeDiagramView(activity, "branches", mode), new LinearLayout.LayoutParams(-1, -2));
        }
        box.addView(label(activity, "点一个地支，查看它的对应关系。", 14, JianYiUi.MUTED, false));
        final LinearLayout detail = column(activity);
        final ArrayList<Button> buttons = new ArrayList<Button>(); final Runnable[] choose = new Runnable[12];
        for (int row = 0; row < 3; row++) {
            LinearLayout cells = new LinearLayout(activity);
            for (int col = 0; col < 4; col++) {
                final int index = row * 4 + col;
                choose[index] = new Runnable() {
                    @Override public void run() {
                        for (int j = 0; j < buttons.size(); j++) skinTab(buttons.get(j), j == index);
                        detail.removeAllViews();
                        detail.addView(label(activity, BRANCHES[index] + " · " + ANIMALS[index], 23, JianYiUi.INK, true));
                        if (hiddenStems) {
                            detail.addView(label(activity, "藏干", 13, JianYiUi.MUTED, false));
                            detail.addView(label(activity, hiddenStemsFor(guide, BRANCHES[index]), 25, JianYiUi.INK, true));
                        }
                        detail.addView(facts(activity, new String[][] {
                            {"五行", BRANCH_ELEMENTS[index]}, {"阴阳", index % 2 == 0 ? "阳" : "阴"},
                            {"方位", BRANCH_DIRECTIONS[index]}, {"时段", HOURS[index]}
                        }));
                    }
                };
                Button tab = compactButton(activity, zodiac ? BRANCHES[index] + "\n" + ANIMALS[index] : BRANCHES[index], new View.OnClickListener() {
                    @Override public void onClick(View view) { choose[index].run(); }
                });
                tab.setContentDescription("查看地支" + BRANCHES[index] + "的对应关系");
                buttons.add(tab); addWeighted(activity, cells, tab, col > 0);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(activity, 7); box.addView(cells, params);
        }
        box.addView(detail); choose[0].run(); root.addView(box);
    }

    private static String hiddenStemsFor(JSONObject guide, String branch) {
        JSONArray groups = guide.optJSONArray("groups");
        if (groups == null) return "—";
        for (int i = 0; i < groups.length(); i++) {
            JSONArray items = groups.optJSONObject(i).optJSONArray("items");
            if (items == null) continue;
            for (int j = 0; j < items.length(); j++) {
                String item = items.optString(j);
                if (item.startsWith(branch + " → ")) return item.substring((branch + " → ").length());
            }
        }
        return "—";
    }

    private static void compass(final Activity activity, LinearLayout root, String mode) {
        LinearLayout box = card(activity, JianYiUi.WHITE);
        final boolean[] earlier = {"xiantian".equals(mode)};
        final LinearLayout chart = column(activity);
        LinearLayout tabs = new LinearLayout(activity);
        final Button[] buttons = new Button[2];
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                chart.removeAllViews();
                for (int i = 0; i < 2; i++) JianYiUi.skinButton(buttons[i], earlier[0] == (i == 0));
                chart.addView(label(activity, earlier[0] ? "先天八卦方位" : "后天八卦方位", 18, JianYiUi.INK, true));
                chart.addView(label(activity, "南在上、北在下；图的左侧是东，右侧是西。", 14, JianYiUi.MUTED, false));
                chart.addView(new KnowledgeDiagramView(activity, "bagua_compass", earlier[0] ? "xiantian" : "houtian"), new LinearLayout.LayoutParams(-1, -2));
                if (!earlier[0]) chart.addView(label(activity, "后天九宫数：坎1、坤2、震3、巽4、中5、乾6、兑7、艮8、离9。", 15, JianYiUi.MUTED, false));
            }
        };
        for (int i = 0; i < 2; i++) {
            final boolean first = i == 0;
            buttons[i] = button(activity, first ? "先天图" : "后天图", false, new View.OnClickListener() {
                @Override public void onClick(View view) { earlier[0] = first; refresh.run(); }
            });
            addWeighted(activity, tabs, buttons[i], i > 0);
        }
        box.addView(tabs); box.addView(chart); refresh.run(); root.addView(box);
    }

    private static void hetuLuoshu(final Activity activity, LinearLayout root) {
        LinearLayout box = card(activity, JianYiUi.WHITE);
        final LinearLayout content = column(activity);
        LinearLayout tabs = new LinearLayout(activity);
        final Button[] buttons = new Button[2]; final boolean[] hetu = {true};
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                content.removeAllViews();
                for (int i = 0; i < 2; i++) JianYiUi.skinButton(buttons[i], hetu[0] == (i == 0));
                content.addView(label(activity, hetu[0] ? "河图 · 两数为一组" : "洛书 · 九宫各线十五", 18, JianYiUi.INK, true));
                View diagram = new KnowledgeMotifView(activity, hetu[0] ? "Hetu" : "Luoshu");
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 235), dp(activity, 235));
                params.gravity = Gravity.CENTER_HORIZONTAL; content.addView(diagram, params);
                content.addView(label(activity, hetu[0] ? "北一六、南二七、东三八、西四九、中央五十。奇数空心，偶数实心。" : "4  9  2\n3  5  7\n8  1  6\n横、竖、两条对角线相加都为 15。", 16, JianYiUi.INK, false));
            }
        };
        for (int i = 0; i < 2; i++) {
            final boolean first = i == 0;
            buttons[i] = button(activity, first ? "河图" : "洛书", false, new View.OnClickListener() {
                @Override public void onClick(View view) { hetu[0] = first; refresh.run(); }
            });
            addWeighted(activity, tabs, buttons[i], i > 0);
        }
        box.addView(tabs); box.addView(content); refresh.run(); root.addView(box);
    }

    private static void addGroups(Activity activity, LinearLayout parent, JSONArray groups) throws Exception {
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            LinearLayout box = card(activity, JianYiUi.WHITE);
            box.addView(label(activity, group.optString("title"), 19, JianYiUi.INK, true));
            groupItems(activity, box, group.optJSONArray("items"));
            if (group.optString("note").length() > 0) box.addView(label(activity, group.optString("note"), 14, JianYiUi.MUTED, false));
            parent.addView(box);
        }
    }

    private static void groupItems(Activity activity, LinearLayout parent, JSONArray items) throws Exception {
        if (items == null) return;
        for (int i = 0; i < items.length(); i++) {
            TextView item = label(activity, items.getString(i), 17, JianYiUi.INK, false);
            item.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
            item.setBackground(JianYiUi.rounded(activity, JianYiUi.SOFT, 0, 12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(activity, 7); parent.addView(item, params);
        }
    }

    private static View facts(Activity activity, String[][] entries) {
        LinearLayout result = column(activity);
        for (int i = 0; i < entries.length; i += 2) {
            LinearLayout row = new LinearLayout(activity);
            for (int j = i; j < Math.min(i + 2, entries.length); j++) {
                LinearLayout entry = card(activity, JianYiUi.SOFT);
                entry.addView(label(activity, entries[j][0], 13, JianYiUi.MUTED, false));
                entry.addView(label(activity, entries[j][1], 18, JianYiUi.INK, true));
                addWeighted(activity, row, entry, j > i);
            }
            result.addView(row);
        }
        return result;
    }

    private static void expandable(final Activity activity, LinearLayout parent, final String title,
        final View body, boolean initiallyOpen) {
        LinearLayout frame = card(activity, JianYiUi.WHITE);
        final boolean[] open = {initiallyOpen};
        final Button toggle = button(activity, "", false, null);
        toggle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        final Runnable update = new Runnable() {
            @Override public void run() {
                toggle.setText((open[0] ? "−  " : "+  ") + title);
                toggle.setContentDescription((open[0] ? "收起" : "展开") + title);
                body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
            }
        };
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { open[0] = !open[0]; update.run(); }
        });
        frame.addView(toggle); frame.addView(body); update.run(); parent.addView(frame);
    }

    private static int dp(Context context, float value) { return JianYiUi.dp(context, value); }
    private static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL); return layout;
    }
    private static LinearLayout card(Context context, int color) {
        LinearLayout box = column(context);
        box.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 16));
        int paperColor = color == JianYiUi.WHITE ? READING_PAPER : color == JianYiUi.SOFT ? READING_SOFT : color;
        box.setBackground(JianYiUi.rounded(context, paperColor, READING_LINE, 6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(context, 9); params.bottomMargin = dp(context, 9); box.setLayoutParams(params);
        return box;
    }
    private static TextView label(Context context, String text, int size, int color, boolean heading) {
        TextView view = new TextView(context); view.setText(text);
        JianYiUi.textStyle(view, size, heading); view.setTextColor(color);
        view.setLineSpacing(dp(context, heading ? 4 : 6), 1.04f);
        view.setPadding(0, dp(context, 4), 0, dp(context, 4));
        if (heading && Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
        return view;
    }
    private static Button button(Context context, String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(context); button.setText(text); JianYiUi.skinButton(button, primary);
        button.setOnClickListener(listener);
        button.setSingleLine(false); button.setEllipsize(null);
        return button;
    }
    private static Button compactButton(Context context, String text, View.OnClickListener listener) {
        Button button = button(context, text, false, listener);
        skinTab(button, false);
        return button;
    }
    private static void skinTab(Button button, boolean selected) {
        Context context = button.getContext();
        JianYiUi.skinButton(button, selected);
        button.setPadding(dp(context, 3), dp(context, 9), dp(context, 3), dp(context, 9));
        button.setMinWidth(0); button.setMinimumWidth(0); button.setTextSize(16);
    }
    private static void addWeighted(Context context, LinearLayout row, View child, boolean gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        if (gap) params.leftMargin = dp(context, 7);
        row.addView(child, params);
    }

    /** Trigram icon takes bottom-first engine bits, and draws the top line first. */
    private static final class TrigramIcon extends View {
        private final int[] bits;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF lineBounds = new RectF();
        TrigramIcon(Context context, int id) {
            super(context); bits = MeihuaEngine.trigramBits(id);
            setContentDescription(MeihuaEngine.trigramName(id) + "卦图");
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas) {
            float width = getWidth(), height = getHeight();
            paint.setColor(JianYiUi.INK);
            for (int line = 0; line < 3; line++) {
                float y = height * (.12f + line * .31f), thick = height * .12f;
                if (bits[2 - line] == 1) {
                    lineBounds.set(width * .08f, y, width * .92f, y + thick);
                    canvas.drawRoundRect(lineBounds, thick / 2f, thick / 2f, paint);
                }
                else {
                    lineBounds.set(width * .08f, y, width * .42f, y + thick);
                    canvas.drawRoundRect(lineBounds, thick / 2f, thick / 2f, paint);
                    lineBounds.set(width * .58f, y, width * .92f, y + thick);
                    canvas.drawRoundRect(lineBounds, thick / 2f, thick / 2f, paint);
                }
            }
        }
    }
}
