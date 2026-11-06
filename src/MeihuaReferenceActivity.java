package com.jianyi.yixue.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.InputFilter;
import android.text.InputType;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local searchable reference, with native reading cards and inert source metadata. */
public final class MeihuaReferenceActivity extends Activity {
    private static final int READING_PAPER = 0xfffffdf8;
    private static final int READING_LINE = 0xffd7d0c1;
    private static final int VERMILION = 0xffa74436;
    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_TOPIC_ID = "topic_id";
    public static final String EXTRA_FULLTEXT_ID = "fulltext_id";
    public static final String EXTRA_FULLTEXT_SECTION = "fulltext_section";
    private static final String EXTRA_LIBRARY = "library";
    private static final String EXTRA_GUIDE = "search_guide";
    private static final ExecutorService SEARCH_WORKER = Executors.newSingleThreadExecutor();
    private static final String PREFS = "guest_meihua_reference_v1";
    private static final int MAX_ASSET_BYTES = 2 * 1024 * 1024;
    private static final String[] GROUP_IDS = {
        "classics", "philosophy", "imagery",
        "bagua", "divination", "structure", "seasonal", "external", "timing"
    };
    private static final String[] BOOK_IDS = {"MEIHUA_1", "MEIHUA_2", "MEIHUA_3", "MEIHUA_4", "MEIHUA_5",
        "SHUOGUA", "XICI_UP", "XICI_DOWN", "YQWD", "GUANWU_11", "GUANWU_12", "YIXUE_UP", "YIXUE_DOWN"};
    private static final String[] BOOK_LABELS = {"梅花易数·卷一", "梅花易数·卷二", "梅花易数·卷三", "梅花易数·卷四", "梅花易数·卷五",
        "说卦传", "系辞上传", "系辞下传", "渔樵问对", "观物内篇·卷十一", "观物内篇·卷十二", "易学启蒙通释·卷上", "易学启蒙通释·卷下"};
    private JSONObject reference;
    private JSONArray groups, topics, sources;
    private final HashMap<String, String> searchable = new HashMap<String, String>();
    private SharedPreferences preferences;
    private ScrollView scroll;
    private LinearLayout content;
    private EditText search;
    private View resultsAnchor;
    private String query = "", filter = "", topicId = "";
    private String fullTextId = "";
    private int fullTextSection = -1;
    private boolean bookmarksOnly;
    private int indexScroll;
    private boolean showingError;
    private int searchGeneration;
    private boolean externalReader;
    private String searchType = "";
    private OfflineSearchIndex.Results globalResults;
    private LinearLayout globalResultsPanel;
    private int visibleSearchResults;
    private static final int SEARCH_PAGE_SIZE = 24;
    private long indexReadingAt;
    private final ArrayList<TextView> readerBodies = new ArrayList<TextView>();
    private final ArrayList<Boolean> readerClassics = new ArrayList<Boolean>();
    private boolean libraryPage;
    private String libraryTab = "books";
    private int libraryScroll;
    private long libraryMarksRevision;

    public static void open(Activity activity) {
        activity.startActivity(new Intent(activity, MeihuaReferenceActivity.class));
    }

    public static void openLibrary(Activity activity) {
        activity.startActivity(new Intent(activity, MeihuaReferenceActivity.class).putExtra(EXTRA_LIBRARY, true));
    }

    public static void open(Activity activity, String query) {
        activity.startActivity(new Intent(activity, MeihuaReferenceActivity.class)
            .putExtra(EXTRA_QUERY, query == null ? "" : query));
    }

    public static void openTopic(Activity activity, String topicId) {
        activity.startActivity(new Intent(activity, MeihuaReferenceActivity.class)
            .putExtra(EXTRA_TOPIC_ID, topicId == null ? "" : topicId));
    }

    public static void openFullTextSection(Activity activity, String bookId, int section) {
        activity.startActivity(new Intent(activity, MeihuaReferenceActivity.class)
            .putExtra(EXTRA_FULLTEXT_ID, bookId).putExtra(EXTRA_FULLTEXT_SECTION, section));
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        JianYiUi.applySystemBars(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        libraryPage = getIntent().getBooleanExtra(EXTRA_LIBRARY, false);
        libraryTab = preferences.getString("library_tab", "books");
        libraryScroll = Math.max(0, preferences.getInt("library_scroll", 0));
        try {
            reference = readAsset();
            groups = reference.getJSONArray("groups");
            sources = reference.getJSONArray("sources");
            topics = reference.getJSONArray("topics");
            validateReference();
        } catch (Exception error) {
            showError();
            return;
        }
        query = normalizeQuery(preferences.getString("query", ""));
        filter = validFilter(preferences.getString("filter", ""));
        bookmarksOnly = preferences.getBoolean("bookmarks_only", false);
        indexScroll = Math.max(0, preferences.getInt("index_scroll", 0));
        String requested = getIntent().getStringExtra(EXTRA_TOPIC_ID);
        String requestedFullText = getIntent().getStringExtra(EXTRA_FULLTEXT_ID);
        fullTextSection = getIntent().getIntExtra(EXTRA_FULLTEXT_SECTION, -1);
        if (getIntent().hasExtra(EXTRA_QUERY)) {
            query = normalizeQuery(getIntent().getStringExtra(EXTRA_QUERY));
            searchType = "";
            filter = "";
            bookmarksOnly = false;
            indexScroll = 0;
            commit(preferences.edit().putBoolean("bookmarks_only", false));
        }
        int restore = -1;
        if (saved != null) {
            libraryPage = saved.getBoolean("library_page", libraryPage);
            libraryTab = saved.getString("library_tab", libraryTab);
            libraryScroll = Math.max(0, saved.getInt("library_scroll", libraryScroll));
            query = normalizeQuery(saved.getString("query", query));
            filter = validFilter(saved.getString("filter", filter));
            requested = saved.getString("topic_id", "");
            requestedFullText = saved.getString("fulltext_id", "");
            fullTextSection = saved.getInt("fulltext_section", -1);
            indexScroll = Math.max(0, saved.getInt("index_scroll", indexScroll));
            restore = Math.max(0, saved.getInt("scroll_y", 0));
        }
        if (showRequestedExternalReader(getIntent())) return;
        if (isKnownFullText(requestedFullText)) {
            if (fullTextSection >= 0) showFullTextSection(requestedFullText, fullTextSection);
            else showFullText(requestedFullText);
        }
        else if (find(topics, requested) != null) showTopic(requested);
        else if (libraryPage) showLibrary(libraryScroll);
        else showIndex(indexScroll);
        if (restore >= 0) restoreScroll(restore);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        savePosition();
        scroll = null;
        content = null;
        setIntent(intent);
        libraryPage = intent.getBooleanExtra(EXTRA_LIBRARY, false);
        if (showingError) return;
        if (intent.hasExtra(EXTRA_QUERY)) {
            query = normalizeQuery(intent.getStringExtra(EXTRA_QUERY));
            searchType = "";
            filter = "";
            bookmarksOnly = false;
            indexScroll = 0;
            commit(preferences.edit().putBoolean("bookmarks_only", false));
        }
        String id = intent.getStringExtra(EXTRA_TOPIC_ID);
        String full = intent.getStringExtra(EXTRA_FULLTEXT_ID);
        int requestedSection = intent.getIntExtra(EXTRA_FULLTEXT_SECTION, -1);
        if (showRequestedExternalReader(intent)) return;
        if (isKnownFullText(full)) {
            if (requestedSection >= 0) showFullTextSection(full, requestedSection);
            else showFullText(full);
        }
        else if (find(topics, id) != null) showTopic(id);
        else if (libraryPage) showLibrary(libraryScroll);
        else showIndex(indexScroll);
    }

    @Override protected void onPause() {
        savePosition();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!showingError && libraryPage && preferences != null && scroll != null
            && (indexReadingAt != preferences.getLong("read_last_at", 0L)
            || libraryMarksRevision != preferences.getLong("chapter_marks_revision", 0L))) {
            savePosition(); showLibrary(libraryScroll); return;
        }
        if (!showingError && !externalReader && preferences != null && scroll != null
            && !libraryPage && fullTextId.length() == 0 && topicId.length() == 0 && query.length() == 0
            && filter.length() == 0 && !bookmarksOnly
            && indexReadingAt != preferences.getLong("read_last_at", 0L)) {
            savePosition(); showIndex(indexScroll);
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        savePosition();
        out.putBoolean("library_page", libraryPage);
        out.putString("library_tab", libraryTab);
        out.putInt("library_scroll", libraryScroll);
        out.putString("query", query);
        out.putString("filter", filter);
        out.putString("topic_id", topicId);
        out.putString("fulltext_id", fullTextId);
        out.putInt("fulltext_section", fullTextSection);
        out.putInt("index_scroll", indexScroll);
        out.putInt("scroll_y", scroll == null ? 0 : scroll.getScrollY());
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        savePosition();
        if (externalReader) finish();
        else if (!showingError && fullTextId.length() != 0 && fullTextSection >= 0) {
            if (getIntent().getIntExtra(EXTRA_FULLTEXT_SECTION, -1) >= 0) finish();
            else showFullText(fullTextId);
        }
        else if (!showingError && fullTextId.length() != 0) finish();
        else if (!showingError && topicId.length() != 0) {
            savePosition();
            if (getIntent().hasExtra(EXTRA_TOPIC_ID)) finish();
            else showIndex(indexScroll);
        } else finish();
    }

    @Override protected void onDestroy() {
        searchGeneration++;
        super.onDestroy();
    }

    private boolean showRequestedExternalReader(Intent intent) {
        String guide = intent.getStringExtra(EXTRA_GUIDE);
        if (guide != null && guide.length() > 0 && guide.length() < 160) {
            externalReader = true;
            KnowledgeReader.show(this, guide);
            return true;
        }
        externalReader = false;
        return false;
    }

    private JSONObject readAsset() throws Exception {
        InputStream input = getAssets().open("appdata/meihua-advanced.json");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_ASSET_BYTES)
                    throw new IllegalArgumentException("reference asset too large");
                bytes.write(buffer, 0, count);
            }
        } finally { input.close(); }
        return new JSONObject(new String(bytes.toByteArray(), "UTF-8"));
    }

    private void validateReference() throws Exception {
        Object version = reference.get("schema_version");
        if (!(version instanceof Number) || ((Number) version).doubleValue() != 1d)
            throw new IllegalArgumentException("unsupported reference schema");
        requiredText(reference, "title", 128);
        requiredText(reference, "attribution", 10000);
        if (groups.length() != GROUP_IDS.length || sources.length() == 0 || sources.length() > 100
            || topics.length() == 0 || topics.length() > 500)
            throw new IllegalArgumentException("invalid reference inventory");
        HashSet<String> groupIds = new HashSet<String>();
        HashSet<String> sourceIds = new HashSet<String>();
        HashSet<String> topicIds = new HashSet<String>();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            String id = requiredId(group);
            if (!isKnownGroup(id) || !groupIds.add(id)) throw new IllegalArgumentException("invalid group");
            requiredText(group, "title", 128);
        }
        for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.getJSONObject(i);
            if (!sourceIds.add(requiredId(source))) throw new IllegalArgumentException("duplicate source");
            requiredText(source, "title", 512);
            optionalText(source, "url", 2048);
            optionalText(source, "license", 4096);
            optionalText(source, "note", 10000);
        }
        HashSet<String> populated = new HashSet<String>();
        for (int i = 0; i < topics.length(); i++) {
            JSONObject topic = topics.getJSONObject(i);
            String id = requiredId(topic);
            String group = requiredText(topic, "group", 64);
            if (!topicIds.add(id) || !groupIds.contains(group))
                throw new IllegalArgumentException("invalid topic identity");
            populated.add(group);
            StringBuilder haystack = new StringBuilder();
            haystack.append(id).append('\n');
            haystack.append(requiredText(topic, "title", 512)).append('\n');
            haystack.append(requiredText(topic, "summary", 10000)).append('\n');
            JSONArray keywords = topic.getJSONArray("keywords");
            if (keywords.length() > 100) throw new IllegalArgumentException("too many keywords");
            for (int j = 0; j < keywords.length(); j++)
                haystack.append(arrayText(keywords, j, 256)).append('\n');
            JSONArray sections = topic.getJSONArray("sections");
            if (sections.length() == 0 || sections.length() > 60)
                throw new IllegalArgumentException("invalid topic sections");
            for (int j = 0; j < sections.length(); j++) {
                JSONObject section = sections.getJSONObject(j);
                haystack.append(requiredText(section, "title", 512)).append('\n');
                optionalDiagram(section);
                JSONArray paragraphs = section.getJSONArray("paragraphs");
                if (paragraphs.length() == 0 || paragraphs.length() > 100)
                    throw new IllegalArgumentException("invalid paragraph inventory");
                for (int k = 0; k < paragraphs.length(); k++)
                    haystack.append(arrayText(paragraphs, k, 16000)).append('\n');
            }
            optionalDiagram(topic);
            JSONArray ids = topic.getJSONArray("source_ids");
            if (ids.length() == 0 || ids.length() > sources.length())
                throw new IllegalArgumentException("invalid topic sources");
            HashSet<String> seen = new HashSet<String>();
            for (int j = 0; j < ids.length(); j++) {
                String sourceId = arrayText(ids, j, 64);
                if (!sourceIds.contains(sourceId) || !seen.add(sourceId))
                    throw new IllegalArgumentException("unknown topic source");
                haystack.append(sourceId).append('\n');
                JSONObject source = find(sources, sourceId);
                if (source != null) haystack.append(source.optString("title")).append('\n');
            }
            searchable.put(id, haystack.toString().toLowerCase(Locale.ROOT));
        }
        if (populated.size() != GROUP_IDS.length) throw new IllegalArgumentException("empty reference group");
    }

    private void showIndex(int position) {
        libraryPage = false;
        indexReadingAt = preferences.getLong("read_last_at", 0L);
        searchGeneration++;
        fullTextId = "";
        topicId = "";
        fullTextSection = -1;
        buildPage("梅花进阶", "类象、占法与分析，按主题查阅");
        addNavigation(false);
        ArrayList<JSONObject> matches = matchingTopics();
        LinearLayout searchPanel = card();
        searchPanel.addView(heading("找一个知识点", 22));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setFilters(new InputFilter[] {new InputFilter.LengthFilter(120)});
        search.setHint("搜课程、案例、原典原句、卦名……");
        search.setContentDescription("离线全库搜索");
        JianYiUi.styleInput(search);
        search.setText(query);
        search.setSelection(search.length());
        searchPanel.addView(search, params());
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView view, int action, KeyEvent event) {
                boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
                if (action == EditorInfo.IME_ACTION_SEARCH || enter) { applySearch(); return true; }
                return false;
            }
        });
        LinearLayout searchActions = row();
        weightedButton(searchActions, "搜索", true, new View.OnClickListener() {
            @Override public void onClick(View view) { applySearch(); }
        });
        weightedButton(searchActions, "清空", false, new View.OnClickListener() {
            @Override public void onClick(View view) {
                hideKeyboard(); query = ""; filter = ""; bookmarksOnly = false; indexScroll = 0;
                commit(preferences.edit().putString("query", "").putString("filter", "")
                    .putBoolean("bookmarks_only", false).putInt("index_scroll", 0));
                showIndex(0);
                Toast.makeText(MeihuaReferenceActivity.this, "已清空搜索条件", Toast.LENGTH_SHORT).show();
            }
        });
        searchPanel.addView(searchActions);
        searchPanel.addView(caption("离线查找专题、课程、案例、原典、卦解和知识图解。支持常用繁简字；多个词用空格分开。"));
        addButton(searchPanel, "全部主题", filter.length() == 0, new View.OnClickListener() {
            @Override public void onClick(View view) { applyFilter(""); }
        });
        addButton(searchPanel, bookmarksOnly ? "专题书签 · 已开启（" + bookmarkCount() + "）" : "专题书签（" + bookmarkCount() + "）",
            bookmarksOnly, new View.OnClickListener() {
            @Override public void onClick(View view) {
                bookmarksOnly = !bookmarksOnly;
                query = "";
                filter = "";
                indexScroll = 0;
                commit(preferences.edit().putString("query", "").putString("filter", "")
                    .putBoolean("bookmarks_only", bookmarksOnly).putInt("index_scroll", 0));
                showIndex(0);
                scrollToResults();
            }
        });
        for (int i = 0; i < GROUP_IDS.length; i += 3) {
            LinearLayout filters = row();
            for (int j = i; j < i + 3 && j < GROUP_IDS.length; j++) {
                final String id = GROUP_IDS[j];
                weightedButton(filters, groupTitle(id), id.equals(filter), new View.OnClickListener() {
                    @Override public void onClick(View view) { applyFilter(id); }
                });
            }
            searchPanel.addView(filters);
        }
        if (query.length() != 0 && filter.length() == 0 && !bookmarksOnly) {
            resultsAnchor = sectionLabel("全库搜索 · " + query);
            content.addView(resultsAnchor);
            globalResultsPanel = column();
            content.addView(globalResultsPanel, params());
            globalResultsPanel.addView(caption("正在整理离线资料，请稍候……"));
            requestGlobalSearch(query, position);
            content.requestFocus();
            restoreScroll(position);
            return;
        }
        boolean focused = filter.length() != 0 || query.length() != 0 || bookmarksOnly;
        if (!focused) { addContinueReadingCard(content, ""); addLibraryShortcut(); }
        String scope = filter.length() == 0 ? "全部主题" : groupTitle(filter);
        resultsAnchor = sectionLabel(scope + " · " + matches.size() + " 篇");
        content.addView(resultsAnchor);
        if (query.length() != 0) content.addView(caption("搜索：" + query + " · 标题、关键词与正文均可匹配"));
        addGroupOverview(filter);
        if (matches.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(heading("暂时没有匹配的主题", 21));
            empty.addView(body("试着缩短关键词，或切换到全部主题。多个关键词之间用空格分开，会同时查找。"));
        }
        for (JSONObject topic : matches) addTopicCard(topic);
        if (focused) addLibraryShortcut();
        addSourcePanel(content, null);
        content.requestFocus();
        restoreScroll(position);
    }

    private void addClassicsLibrary() {
        int[] starts = {0, 5, 8, 13};
        String[] titles = {"梅花原典", "易传", "象数进阶"};
        for (int group = 0; group < titles.length; group++) {
            content.addView(sectionLabel(titles[group]));
            for (int i = starts[group]; i < starts[group + 1]; i += 2) {
                LinearLayout books = row();
                for (int j = i; j < i + 2; j++) {
                    LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -1, 1f);
                    layout.setMargins(dp(3), dp(5), dp(3), dp(5));
                    if (j >= starts[group + 1]) { books.addView(new View(this), layout); continue; }
                    final String id = BOOK_IDS[j];
                    LinearLayout tile = detachedCard();
                    tile.setMinimumHeight(dp(120));
                    tile.addView(heading(BOOK_LABELS[j], 19));
                    try {
                        JSONObject book = readJsonAsset("appdata/classic-fulltext-" + id + ".json", 1024 * 1024);
                        JSONArray sections = book.getJSONArray("sections");
                        tile.addView(caption(sections.length() + " 篇章"));
                        int last = preferences.getInt("read_last_section_" + id, -1);
                        tile.addView(caption(last >= 0 && last < sections.length() ? "上次：" + preview(sections.getJSONObject(last).optString("title"), 16) : "查看目录　›"));
                    } catch (Exception ignored) { tile.addView(caption("查看目录　›")); }
                    tile.setClickable(true); tile.setFocusable(true);
                    tile.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    tile.setContentDescription("打开原典 " + BOOK_LABELS[j]);
                    tile.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            savePosition(); startActivity(new Intent(MeihuaReferenceActivity.this, MeihuaReferenceActivity.class).putExtra(EXTRA_FULLTEXT_ID, id));
                        }
                    });
                    books.addView(tile, layout);
                }
                content.addView(books);
            }
        }
    }

    private void addLibraryShortcut() {
        LinearLayout entry = card();
        entry.addView(heading("原典书架", 22));
        entry.addView(caption("13 份原典与分卷 · 按书目阅读、继续上次章节"));
        entry.addView(caption("打开书架　›"));
        entry.setClickable(true); entry.setFocusable(true);
        entry.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        entry.setContentDescription("打开原典书架");
        entry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { savePosition(); openLibrary(MeihuaReferenceActivity.this); }
        });
    }

    private void showLibrary(int position) {
        searchGeneration++;
        libraryPage = true; fullTextId = ""; fullTextSection = -1; topicId = ""; search = null;
        indexReadingAt = preferences.getLong("read_last_at", 0L);
        libraryMarksRevision = preferences.getLong("chapter_marks_revision", 0L);
        buildPage("原典书架", "读原文、留书签，接着上次读下去");
        LinearLayout navigation = row();
        weightedButton(navigation, "返回上一页", false, new View.OnClickListener() {
            @Override public void onClick(View view) { savePosition(); finish(); }
        });
        weightedButton(navigation, "查找原文", false, new View.OnClickListener() {
            @Override public void onClick(View view) { savePosition(); open(MeihuaReferenceActivity.this, ""); }
        });
        content.addView(navigation);
        final ArrayList<JSONObject> marks = chapterBookmarks();
        LinearLayout tabs = row();
        weightedButton(tabs, "书目", !"bookmarks".equals(libraryTab), new View.OnClickListener() {
            @Override public void onClick(View view) { libraryTab = "books"; libraryScroll = 0; showLibrary(0); }
        });
        weightedButton(tabs, "章节书签（" + marks.size() + "）", "bookmarks".equals(libraryTab), new View.OnClickListener() {
            @Override public void onClick(View view) { libraryTab = "bookmarks"; libraryScroll = 0; showLibrary(0); }
        });
        content.addView(tabs);
        if ("bookmarks".equals(libraryTab)) {
            if (marks.isEmpty()) {
                LinearLayout empty = card(); empty.addView(heading("把想再读的篇章留在这里", 21));
                empty.addView(body("进入原典章节，点“书签”即可收藏；下次从这里直接回到那一篇。"));
            }
            for (final JSONObject mark : marks) {
                LinearLayout tile = card(); tile.addView(caption(mark.optString("book_title") + " · 第 " + (mark.optInt("section") + 1) + " 篇"));
                tile.addView(heading(mark.optString("title"), 21)); tile.addView(caption("继续阅读　›"));
                tile.setClickable(true); tile.setFocusable(true); tile.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                tile.setContentDescription("阅读章节书签 " + mark.optString("title"));
                tile.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) { savePosition(); openFullTextSection(MeihuaReferenceActivity.this, mark.optString("book"), mark.optInt("section")); }
                });
            }
        } else { addContinueReadingCard(content, ""); addClassicsLibrary(); }
        commit(preferences.edit().putString("library_tab", libraryTab));
        content.requestFocus(); restoreScroll(position);
    }

    private ArrayList<JSONObject> chapterBookmarks() {
        ArrayList<JSONObject> result = new ArrayList<JSONObject>();
        for (String id : BOOK_IDS) {
            try {
                JSONObject book = readJsonAsset("appdata/classic-fulltext-" + id + ".json", 1024 * 1024);
                JSONArray sections = book.getJSONArray("sections");
                for (int i = 0; i < sections.length(); i++) if (isChapterBookmarked(id, i))
                    result.add(new JSONObject().put("book", id).put("book_title", book.optString("title"))
                        .put("section", i).put("title", sections.getJSONObject(i).optString("title")));
            } catch (Exception ignored) { }
        }
        return result;
    }

    private boolean isChapterBookmarked(String id, int index) {
        return preferences.getBoolean("chapter_bookmark_" + id + "_" + index, false);
    }

    private void addContinueReadingCard(LinearLayout parent, final String requestedBook) {
        final boolean inBook = requestedBook.length() != 0;
        final String id = inBook ? requestedBook : preferences.getString("read_last_book", "");
        if (!isKnownFullText(id)) return;
        final int index = preferences.getInt("read_last_section_" + id, -1);
        try {
            JSONObject book = readJsonAsset("appdata/classic-fulltext-" + id + ".json", 1024 * 1024);
            JSONArray sections = book.getJSONArray("sections");
            if (index < 0 || index >= sections.length()) return;
            String title = sections.getJSONObject(index).optString("title", "第" + (index + 1) + "篇");
            LinearLayout panel = detachedCard();
            panel.addView(caption("上次读到 · 第 " + (index + 1) + " / " + sections.length() + " 篇"));
            if (!inBook) panel.addView(heading(book.optString("title"), 21));
            panel.addView(body(title)); panel.addView(caption("继续阅读　›"));
            panel.setClickable(true); panel.setFocusable(true);
            panel.setContentDescription("继续阅读 " + book.optString("title") + " " + title);
            panel.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    savePosition();
                    if (inBook) showFullTextSection(id, index);
                    else openFullTextSection(MeihuaReferenceActivity.this, id, index);
                }
            });
            parent.addView(panel, cardParams());
        } catch (Exception ignored) { }
    }

    private void rememberChapter(String id, int index) {
        commit(preferences.edit().putString("read_last_book", id)
            .putInt("read_last_section_" + id, index).putLong("read_last_at", System.currentTimeMillis()));
    }

    private void addFullTextButton(LinearLayout parent, final String id, String label) {
        addButton(parent, label, false, new View.OnClickListener() {
            @Override public void onClick(View view) {
                savePosition();
                startActivity(new Intent(MeihuaReferenceActivity.this, MeihuaReferenceActivity.class)
                    .putExtra(EXTRA_FULLTEXT_ID, id));
            }
        });
    }

    private void applySearch() {
        String submitted = normalizeQuery(search == null ? query : search.getText().toString());
        if (submitted.length() == 0) {
            Toast.makeText(this, "请输入要查找的知识点", Toast.LENGTH_SHORT).show();
            if (search != null) search.requestFocus();
            return;
        }
        if (!submitted.equals(query)) searchType = "";
        query = submitted;
        hideKeyboard();
        filter = "";
        bookmarksOnly = false;
        indexScroll = 0;
        commit(preferences.edit().putString("query", query).putString("filter", "")
            .putBoolean("bookmarks_only", false).putInt("index_scroll", 0));
        showIndex(0);
        scrollToResults();
    }

    private void requestGlobalSearch(final String submitted, final int restorePosition) {
        final int generation = searchGeneration;
        final LinearLayout target = globalResultsPanel;
        final android.content.res.AssetManager assets = getApplicationContext().getAssets();
        SEARCH_WORKER.execute(new Runnable() {
            @Override public void run() {
                OfflineSearchIndex.Results loaded = null;
                try { loaded = OfflineSearchIndex.load(assets).search(submitted); }
                catch (Exception ignored) { }
                final OfflineSearchIndex.Results result = loaded;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || generation != searchGeneration || target != globalResultsPanel
                            || !submitted.equals(query) || topicId.length() != 0 || fullTextId.length() != 0) return;
                        target.removeAllViews();
                        if (result == null) {
                            target.addView(body("离线搜索暂未载入，请稍后重新搜索；分类和原典目录仍可使用。"));
                            return;
                        }
                        globalResults = result;
                        visibleSearchResults = SEARCH_PAGE_SIZE;
                        addGlobalSearchControls(target, result);
                        if (restorePosition > 0) restoreScroll(restorePosition);
                        else scrollToResults();
                    }
                });
            }
        });
    }

    private void addGlobalSearchControls(final LinearLayout target, final OfflineSearchIndex.Results result) {
        target.addView(heading("找到 " + result.hits.size() + " 条结果", 23));
        StringBuilder counts = new StringBuilder();
        for (String type : OfflineSearchIndex.TYPES) {
            if (counts.length() > 0) counts.append(" · ");
            counts.append(OfflineSearchIndex.typeName(type)).append(' ').append(result.counts.get(type));
        }
        target.addView(caption(counts.toString()));
        target.addView(caption("已查找 " + result.indexed + " 篇离线内容 · 按标题相关程度排序 · 原典按篇章计数"));
        if (result.hits.isEmpty()) {
            target.addView(body("试着缩短关键词，或拆成两个词。例如“潜龙勿用”“渔樵问答”“梅花 体用”。多个词须同时出现。"));
            return;
        }
        final ArrayList<String> typeIds = new ArrayList<String>();
        final ArrayList<String> labels = new ArrayList<String>();
        typeIds.add(""); labels.add("全部类型（" + result.hits.size() + "）");
        for (String type : OfflineSearchIndex.TYPES) if (result.counts.get(type) > 0) {
            typeIds.add(type); labels.add(OfflineSearchIndex.typeName(type) + "（" + result.counts.get(type) + "）");
        }
        if (!typeIds.contains(searchType)) searchType = "";
        final Spinner scope = new Spinner(this);
        scope.setContentDescription("按搜索结果类型筛选");
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scope.setAdapter(adapter);
        scope.setSelection(typeIds.indexOf(searchType));
        target.addView(scope, params());
        final LinearLayout hits = column();
        target.addView(hits, params());
        scope.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = typeIds.get(position);
                if (!selected.equals(searchType)) { searchType = selected; visibleSearchResults = SEARCH_PAGE_SIZE; }
                renderGlobalHits(hits);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        renderGlobalHits(hits);
    }

    private void renderGlobalHits(final LinearLayout target) {
        target.removeAllViews();
        ArrayList<OfflineSearchIndex.Hit> hits = globalResults.ofType(searchType);
        int visible = Math.min(visibleSearchResults, hits.size());
        target.addView(caption("显示 " + visible + " / " + hits.size() + " 条 · 点击结果打开对应内容"));
        for (int i = 0; i < visible; i++) {
            final OfflineSearchIndex.Hit hit = hits.get(i);
            final OfflineSearchIndex.Document doc = hit.document;
            LinearLayout tile = detachedCard();
            tile.addView(caption(OfflineSearchIndex.typeName(doc.type) + " · " + doc.location));
            TextView title = heading(doc.title, 21);
            title.setText(highlight(doc.title));
            tile.addView(title);
            TextView snippet = body(hit.snippet);
            snippet.setText(highlight(hit.snippet));
            tile.addView(snippet);
            tile.addView(caption("打开阅读　›"));
            tile.setClickable(true); tile.setFocusable(true);
            tile.setContentDescription("打开" + OfflineSearchIndex.typeName(doc.type) + " " + doc.title);
            tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { openGlobalHit(doc); }
            });
            target.addView(tile, cardParams());
        }
        if (visible < hits.size()) {
            addButton(target, "继续显示 · 还有 " + (hits.size() - visible) + " 条", false, new View.OnClickListener() {
                @Override public void onClick(View view) {
                    int position = scroll.getScrollY();
                    visibleSearchResults += SEARCH_PAGE_SIZE;
                    renderGlobalHits(target);
                    restoreScroll(position);
                }
            });
        }
    }

    private CharSequence highlight(String value) {
        SpannableString text = new SpannableString(value);
        String normalized = OfflineSearchIndex.normalize(value);
        for (String term : OfflineSearchIndex.terms(query)) {
            if (term.length() == 0) continue;
            int offset = 0;
            while ((offset = normalized.indexOf(term, offset)) >= 0) {
                int end = Math.min(value.length(), offset + term.length());
                text.setSpan(new BackgroundColorSpan(JianYiUi.SOFT), offset, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(JianYiUi.ACCENT), offset, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                offset = end;
            }
        }
        return text;
    }

    private void openGlobalHit(OfflineSearchIndex.Document doc) {
        savePosition(); hideKeyboard();
        if ("topic".equals(doc.type)) {
            startActivity(new Intent(this, MeihuaReferenceActivity.class).putExtra(EXTRA_TOPIC_ID, doc.id));
        } else if ("classic".equals(doc.type)) {
            openFullTextSection(this, doc.id, doc.section);
        } else if ("lesson".equals(doc.type) || "case".equals(doc.type)) {
            startActivity(new Intent(this, LearningActivity.class)
                .putExtra(LearningActivity.EXTRA_PAGE, doc.type).putExtra(LearningActivity.EXTRA_ITEM_ID, doc.id));
        } else if ("hexagram".equals(doc.type)) {
            if (doc.section == 1) StudyModules.showDetailedByCode(this, doc.id);
            else StudyModules.showClassicByCode(this, doc.id);
        } else if ("guide".equals(doc.type)) {
            startActivity(new Intent(this, MeihuaReferenceActivity.class).putExtra(EXTRA_GUIDE, doc.id));
        }
    }

    private void scrollToResults() {
        final ScrollView currentScroll = scroll;
        final View currentAnchor = resultsAnchor;
        if (currentScroll == null || currentAnchor == null) return;
        currentScroll.post(new Runnable() {
            @Override public void run() {
                if (scroll == currentScroll) {
                    currentScroll.smoothScrollTo(0, Math.max(0, currentAnchor.getTop() - dp(12)));
                }
            }
        });
    }

    private void applyFilter(String id) {
        hideKeyboard();
        query = "";
        filter = validFilter(id);
        bookmarksOnly = false;
        indexScroll = 0;
        searchType = "";
        commit(preferences.edit().putString("query", "").putString("filter", filter)
            .putBoolean("bookmarks_only", false).putInt("index_scroll", 0));
        showIndex(0);
        int count = matchingTopics().size();
        scrollToResults();
        Toast.makeText(this, groupTitle(filter) + " · " + count + " 篇", Toast.LENGTH_SHORT).show();
    }

    private void addGroupOverview(String id) {
        if ("bagua".equals(id)) {
            LinearLayout overview = card();
            overview.addView(heading("八卦类象先看这一图", 23));
            overview.addView(body("先认卦形与卦德，再把方位、五行、人物和万物类象放回具体情境。卦象是一组观察线索，不是给事物贴上的永久标签。"));
            addDiagram(overview, "bagua_mnemonic");
            overview.addView(caption("爻从下往上读 · 口诀与卦形对应记忆"));
        } else if ("imagery".equals(id)) {
            LinearLayout overview = card();
            overview.addView(heading("取象先从事实开始", 23));
            overview.addView(body("先写下看见、听见和正在发生的事实，再提取形、色、声、动、位等特征，列出候选卦象并说明理由，最后结合问题与体用取舍。"));
            addDiagram(overview, "observation_path");
            overview.addView(caption("事实 → 特征 → 候选卦象 → 体用判断 → 事后复盘"));
        }
    }

    private ArrayList<JSONObject> matchingTopics() {
        ArrayList<JSONObject> matches = new ArrayList<JSONObject>();
        String[] terms = query.toLowerCase(Locale.ROOT).split("\\s+");
        for (int i = 0; i < topics.length(); i++) {
            JSONObject topic = topics.optJSONObject(i);
            if (bookmarksOnly && !isBookmarked(topic.optString("id"))) continue;
            if (filter.length() != 0 && !filter.equals(topic.optString("group"))) continue;
            String haystack = searchable.get(topic.optString("id"));
            boolean matchesAll = haystack != null;
            for (String term : terms)
                if (term.length() != 0 && (haystack == null || haystack.indexOf(term) < 0)) { matchesAll = false; break; }
            if (matchesAll) matches.add(topic);
        }
        return matches;
    }

    private void addTopicCard(final JSONObject topic) {
        LinearLayout panel = card();
        panel.addView(caption(groupTitle(topic.optString("group"))));
        panel.addView(heading(topic.optString("title"), 23));
        panel.addView(body(topic.optString("summary")));
        JSONArray keywords = topic.optJSONArray("keywords");
        if (keywords != null && keywords.length() != 0) {
            StringBuilder tags = new StringBuilder();
            for (int i = 0; i < Math.min(5, keywords.length()); i++) {
                if (i != 0) tags.append(" · ");
                tags.append(keywords.optString(i));
            }
            panel.addView(caption(tags.toString()));
        }
        panel.addView(caption("打开阅读　›"));
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setContentDescription("阅读 " + topic.optString("title"));
        panel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                savePosition();
                hideKeyboard();
                showTopic(topic.optString("id"));
            }
        });
    }

    private void showTopic(String id) {
        savePosition();
        libraryPage = false;
        final JSONObject topic = find(topics, id);
        if (topic == null) { showIndex(indexScroll); return; }
        topicId = id;
        fullTextId = "";
        fullTextSection = -1;
        search = null;
        buildPage("梅花进阶", groupTitle(topic.optString("group")) + " · 主题阅读");
        addNavigation(true);
        LinearLayout opening = card();
        opening.addView(heading(topic.optString("title"), 26));
        opening.addView(caption("课程导读 · 图解与学习提示，不等同于原典全文"));
        opening.addView(body(topic.optString("summary")));
        addDiagram(opening, topic.optString("diagram"));
        addClassicTabs(opening, id);
        if (!isExpandedImageryTopic(id)) addFullTextLinks(opening, id);
        if ("C06".equals(id)) addButton(opening, "图解古例课堂 · 5个精讲", true, new View.OnClickListener() {
            @Override public void onClick(View view) { LearningActivity.openCaseLibrary(MeihuaReferenceActivity.this); }
        });
        final Button bookmark = addButton(opening, isBookmarked(id) ? "★ 已加入书签" : "☆ 加入书签", false, null);
        bookmark.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean saved = !isBookmarked(topicId);
                commit(preferences.edit().putBoolean("bookmark_" + topicId, saved));
                bookmark.setText(saved ? "★ 已加入书签" : "☆ 加入书签");
            }
        });
        JSONArray sections = topic.optJSONArray("sections");
        boolean directReading = shouldShowSectionsDirectly(sections);
        opening.addView(caption("共 " + sections.length() + " 节 · "
            + (directReading ? "正文已展开，出处见页末" : "点击章节卡片展开阅读")));
        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            addReadingSection(section, i, directReading);
        }
        if (isExpandedImageryTopic(id)) addFullTextLinks(card(), id);
        addRelatedClassics(id);
        addTopicLessons(topic);
        addSourcePanel(content, topic.optJSONArray("source_ids"));
        LinearLayout ending = card();
        ending.addView(heading("把理解写下来", 22));
        ending.addView(body("结合自己的卦例，记录你用到的类象、体用与后续观察。"));
        addButton(ending, "卦例笔记", false, new View.OnClickListener() {
            @Override public void onClick(View view) { NotebookActivity.openList(MeihuaReferenceActivity.this); }
        });
        addButton(ending, "返回主题列表", true, new View.OnClickListener() {
            @Override public void onClick(View view) { savePosition(); showIndex(indexScroll); }
        });
        restoreScroll(preferences.getInt("topic_scroll_" + id, 0));
    }

    private void addClassicTabs(LinearLayout parent, String id) {
        final String[][] pages;
        if ("P01".equals(id)) pages = new String[][] {
            {"原文节选", "樵者问渔者曰：子以何道而得鱼？曰：吾以六物具而得鱼。六物者，竿也、纶也、浮也、沉也、钩也、饵也。\n\n鱼利食而见害，人利鱼而蒙利。利斯害也，害斯利也。君子见利而思害，小人见利而忘害。"},
            {"白话", "樵者问渔者凭什么方法得鱼。渔者列出竿、线、浮子、沉子、钩和饵，说明结果来自条件配合。鱼把饵看成利益，却可能因此受害；人想得鱼，也要付出时间和工具。"},
            {"要点", "一、利与害依主体、位置和时间而变。\n二、体用不是物品的永久标签。\n三、只看眼前利益容易忽略代价。\n四、判断前先列清参与者和关系。"},
            {"应用", "面对同一件事，分别写出自己、对方与环境的利益和代价。再看谁是所问主体、什么正在作用，最后才结合体用生克；不要从一句古文直接跳到吉凶。"}
        };
        else if ("P02".equals(id)) pages = new String[][] {
            {"原文节选", "夫所以谓之观物者，非以目观之也；非观之以目，而观之以心也；非观之以心，而观之以理也。\n\n以物观物，性也；以我观物，情也。性公而明，情偏而暗。"},
            {"白话", "观物不止是眼睛看到表面，也不止凭自己的感受，而要进一步考察事物的道理和关系。尽量从事物本身出发，较为公正清楚；只从个人好恶出发，容易偏狭。"},
            {"要点", "一、事实、感受和解释要分开。\n二、先观察性质、位置、变化和作用。\n三、动静、刚柔都要放在关系中理解。\n四、注明哪些是原文，哪些是自己的推论。"},
            {"应用", "取象时先记现场事实，再提取形状、声音、方位和动作，列出两个可能卦象及理由。复盘时检查当初忽略的事实，也记录与判断不一致的结果。"}
        };
        else return;
        parent.addView(heading("四层阅读", 21));
        final LinearLayout page = column();
        final Button[] tabs = new Button[4];
        LinearLayout first = row(), second = row();
        for (int i = 0; i < pages.length; i++) {
            final int selected = i;
            tabs[i] = new Button(this);
            tabs[i].setText(pages[i][0]);
            JianYiUi.skinButton(tabs[i], i == 0);
            tabs[i].setContentDescription("切换到" + pages[i][0]);
            tabs[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    page.removeAllViews();
                    page.addView(heading(pages[selected][0], 20));
                    page.addView(body(pages[selected][1]));
                    for (int j = 0; j < tabs.length; j++) JianYiUi.skinButton(tabs[j], j == selected);
                }
            });
            LinearLayout target = i < 2 ? first : second;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 2 == 0) p.rightMargin = dp(6);
            target.addView(tabs[i], p);
        }
        parent.addView(first); parent.addView(second);
        page.addView(heading(pages[0][0], 20)); page.addView(body(pages[0][1]));
        parent.addView(page);
    }

    private void addFullTextLinks(LinearLayout parent, String id) {
        ArrayList<String[]> links = new ArrayList<String[]>();
        if ("P01".equals(id)) links.add(new String[] {"YQWD", "阅读《渔樵问对》全文"});
        else if ("P02".equals(id)) {
            links.add(new String[] {"GUANWU_11", "阅读观物内篇 · 卷十一"});
            links.add(new String[] {"GUANWU_12", "阅读观物内篇 · 卷十二"});
        } else if ("P03".equals(id)) {
            links.add(new String[] {"YIXUE_UP", "阅读《易学启蒙通释》卷上"});
            links.add(new String[] {"YIXUE_DOWN", "阅读《易学启蒙通释》卷下"});
        } else if ("P04".equals(id)) {
            links.add(new String[] {"SHUOGUA", "阅读《说卦传》全文"});
            links.add(new String[] {"XICI_UP", "阅读《系辞上传》全文"});
            links.add(new String[] {"XICI_DOWN", "阅读《系辞下传》全文"});
        } else if ("C01".equals(id) || "C02".equals(id) || "C03".equals(id) || "C04".equals(id) || "C05".equals(id)) {
            links.add(new String[] {"MEIHUA_1", "阅读《梅花易数》卷一"});
            links.add(new String[] {"MEIHUA_2", "阅读《梅花易数》卷二"});
            links.add(new String[] {"MEIHUA_3", "阅读《梅花易数》卷三"});
            links.add(new String[] {"MEIHUA_4", "阅读《梅花易数》卷四"});
            links.add(new String[] {"MEIHUA_5", "阅读《梅花易数》卷五"});
        } else if (isExpandedImageryTopic(id)) {
            JSONObject topic = find(topics, id);
            JSONArray sourceIds = topic == null ? null : topic.optJSONArray("source_ids");
            if (sourceIds != null && contains(sourceIds, "YIZHUAN")) {
                links.add(new String[] {"SHUOGUA", "阅读《说卦传》全文"});
                links.add(new String[] {"XICI_DOWN", "阅读《系辞下传》全文"});
            }
            if (sourceIds != null && contains(sourceIds, "MH1"))
                links.add(new String[] {"MEIHUA_1", "阅读《梅花易数》卷一"});
            if (sourceIds != null && contains(sourceIds, "MH2"))
                links.add(new String[] {"MEIHUA_2", "阅读《梅花易数》卷二"});
        }
        if (links.isEmpty()) return;
        parent.addView(heading("原典全文", 21));
        parent.addView(body("全文与课程导读分开呈现。进入书目后先看篇章目录，再进入独立阅读页查看原文与白话释义。"));
        for (String[] link : links) {
            final String target = link[0];
            addButton(parent, link[1], false, new View.OnClickListener() {
                @Override public void onClick(View view) {
                    startActivity(new Intent(MeihuaReferenceActivity.this, MeihuaReferenceActivity.class)
                        .putExtra(EXTRA_FULLTEXT_ID, target));
                }
            });
        }
    }

    private static boolean isExpandedImageryTopic(String id) {
        if (id == null) return false;
        return id.matches("B0[0-8]") || id.matches("I0[4-9]") || id.matches("I1[01]");
    }

    private void showFullText(String id) {
        savePosition();
        libraryPage = false;
        try {
            final JSONObject book = readJsonAsset("appdata/classic-fulltext-" + id + ".json", 1024 * 1024);
            if (book.optInt("schema_version") != 1 || !id.equals(book.optString("id"))) throw new IllegalArgumentException("invalid full text");
            fullTextId = id;
            fullTextSection = -1;
            topicId = "";
            search = null;
            buildReaderPage(book.optString("title"), "原典全文 · 离线阅读");
            LinearLayout navigation = row();
            weightedButton(navigation, "返回上一页", false, new View.OnClickListener() {
                @Override public void onClick(View view) { finish(); }
            });
            weightedButton(navigation, "回到顶部", false, new View.OnClickListener() {
                @Override public void onClick(View view) { scroll.smoothScrollTo(0, 0); }
            });
            content.addView(navigation);
            LinearLayout intro = card();
            intro.addView(heading(book.optString("title"), 26));
            intro.addView(caption("收录状态：" + book.optString("status") + " · 公版原典离线整理"));
            intro.addView(heading("白话导读", 20));
            intro.addView(body(book.optString("plain_guide")));
            intro.addView(caption(book.optString("edition_note")));
            JSONArray values = book.getJSONArray("sections");
            intro.addView(caption("共 " + values.length() + " 篇章 · 从目录进入独立阅读页"));
            addContinueReadingCard(content, id);
            content.addView(sectionLabel("篇章目录"));
            for (int i = 0; i < values.length(); i++) addFullTextDirectoryItem(id, values.getJSONObject(i), i);
            LinearLayout source = card();
            source.addView(heading("底本来源", 20));
            source.addView(body("公版古籍电子本。应用离线保存正文，去除了网页模板和编辑标记；有扫描底本时同时列出以便校核。"));
            source.addView(caption(book.optString("source_url")));
            if (book.optString("scan_url").length() != 0) source.addView(caption("扫描底本：" + book.optString("scan_url")));
            addButton(source, "返回上一页", true, new View.OnClickListener() {
                @Override public void onClick(View view) { finish(); }
            });
            restoreScroll(preferences.getInt("fulltext_scroll_" + id, 0));
        } catch (Exception failure) { showError(); }
    }

    private void addFullTextDirectoryItem(final String bookId, JSONObject section, final int index) {
        LinearLayout panel = card();
        String title = section.optString("title", "第" + (index + 1) + "篇");
        panel.addView(caption("第 " + (index + 1) + " 篇"
            + (preferences.getInt("read_last_section_" + bookId, -1) == index ? " · 上次读到" : "")));
        panel.addView(heading(title, 21));
        String meaning = section.optString("meaning");
        if (meaning.length() != 0) panel.addView(body(preview(meaning, 66)));
        panel.addView(caption(meaning.length() == 0 ? "阅读全文　›"
            : "authored_reading_aid".equals(section.optString("meaning_kind")) ? "原文、白话与词义　›" : "原文与本篇导读　›"));
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setContentDescription("阅读第" + (index + 1) + "篇 " + title);
        panel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showFullTextSection(bookId, index); }
        });
    }

    private void showFullTextSection(final String id, final int index) {
        savePosition();
        libraryPage = false;
        try {
            final JSONObject book = readJsonAsset("appdata/classic-fulltext-" + id + ".json", 1024 * 1024);
            final JSONArray values = book.getJSONArray("sections");
            if (index < 0 || index >= values.length()) { showFullText(id); return; }
            final JSONObject section = values.getJSONObject(index);
            fullTextId = id;
            fullTextSection = index;
            topicId = "";
            search = null;
            buildReaderPage(book.optString("title"), "第 " + (index + 1) + " / " + values.length() + " 篇");
            addReaderNavigation(id, index, values.length());
            LinearLayout typography = row();
            typography.addView(caption("阅读设置"), new LinearLayout.LayoutParams(0, -2, 1f));
            final Button bookmark = new Button(this);
            bookmark.setText(isChapterBookmarked(id, index) ? "★ 已收藏" : "☆ 书签");
            bookmark.setContentDescription(isChapterBookmarked(id, index) ? "移除章节书签" : "添加章节书签");
            JianYiUi.skinButton(bookmark, false);
            bookmark.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    boolean marked = !isChapterBookmarked(id, index);
                    commit(preferences.edit().putBoolean("chapter_bookmark_" + id + "_" + index, marked)
                        .putLong("chapter_marks_revision", preferences.getLong("chapter_marks_revision", 0L) + 1));
                    bookmark.setText(marked ? "★ 已收藏" : "☆ 书签");
                    bookmark.setContentDescription(marked ? "移除章节书签" : "添加章节书签");
                }
            });
            typography.addView(bookmark, new LinearLayout.LayoutParams(-2, -2));
            Button fontSize = new Button(this); fontSize.setText("字号");
            fontSize.setContentDescription("设置阅读字号"); JianYiUi.skinButton(fontSize, false);
            fontSize.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { showReaderSizeDialog(); }
            });
            typography.addView(fontSize, new LinearLayout.LayoutParams(-2, -2)); content.addView(typography);
            LinearLayout article = card();
            TextView chapterNumber = caption("第 " + (index + 1) + " 篇");
            chapterNumber.setTextColor(VERMILION);
            article.addView(chapterNumber);
            article.addView(heading(section.optString("title", "第" + (index + 1) + "篇"), 27));
            boolean hasWorkedCase = addWorkedCaseLink(article, id, index, section.optString("title"));
            addLinkedLessons(article, id, index, section.optString("title"));
            String meaning = section.optString("meaning");
            if (meaning.trim().length() > 0) {
                article.addView(heading("authored_reading_aid".equals(section.optString("meaning_kind")) ? "白话与词义" : "本篇导读", 20));
                for (String paragraph : meaning.split("\\n\\s*\\n"))
                    article.addView(articleBody(paragraph.trim(), false));
                if (section.optString("meaning_attribution").length() > 0)
                    article.addView(caption(section.optString("meaning_attribution")));
            } else {
                article.addView(caption(hasWorkedCase ? "本篇的取数、卦图与分步分析，见上方图解古例。"
                    : "本篇暂未配逐篇释义。可先阅读原文，或返回目录查看全书导读。"));
            }
            String readingDiagram = section.optString("reading_diagram");
            if (readingDiagram.length() != 0) {
                addDiagram(article, readingDiagram);
                String diagramCaption = section.optString("reading_diagram_caption");
                if (diagramCaption.length() != 0) article.addView(caption(diagramCaption));
            }
            article.addView(heading("原文", 20));
            JSONArray originals = section.getJSONArray("original");
            for (int i = 0; i < originals.length(); i++) article.addView(articleBody(originals.getString(i), true));
            addReaderNavigation(id, index, values.length());
            rememberChapter(id, index);
            restoreScroll(preferences.getInt("fulltext_scroll_" + id + "_" + index, 0));
        } catch (Exception failure) { showError(); }
    }

    private void addReaderNavigation(final String id, final int index, final int total) {
        LinearLayout navigation = row();
        weightedButton(navigation, "返回目录", false, new View.OnClickListener() {
            @Override public void onClick(View view) { savePosition(); showFullText(id); }
        });
        if (index > 0) weightedButton(navigation, "上一篇", false, new View.OnClickListener() {
            @Override public void onClick(View view) { savePosition(); showFullTextSection(id, index - 1); }
        });
        if (index + 1 < total) weightedButton(navigation, "下一篇", true, new View.OnClickListener() {
            @Override public void onClick(View view) { savePosition(); showFullTextSection(id, index + 1); }
        });
        content.addView(navigation);
    }

    private boolean addWorkedCaseLink(LinearLayout parent, String bookId, int index, String sectionTitle) {
        try {
            JSONArray cases = readJsonAsset("appdata/meihua-learning.json", 1024 * 1024).getJSONArray("cases");
            for (int i = 0; i < cases.length(); i++) {
                JSONObject worked = cases.getJSONObject(i);
                if (!worked.optBoolean("featured") || !bookId.equals(worked.optString("original_book_id"))
                    || index != worked.optInt("original_section_index", -1)
                    || !sectionTitle.equals(worked.optString("original_section_title"))) continue;
                final String caseId = worked.getString("id");
                addButton(parent, "图解古例 · 看本互变与分步讲解", true, new View.OnClickListener() {
                    @Override public void onClick(View view) { savePosition(); LearningActivity.openCaseFromOriginal(MeihuaReferenceActivity.this, caseId); }
                });
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void addLinkedLessons(LinearLayout parent, String bookId, int index, String title) {
        try {
            JSONArray lessons = readJsonAsset("appdata/meihua-learning.json", 1024 * 1024).getJSONArray("lessons");
            for (int i = 0; i < lessons.length(); i++) {
                final JSONObject lesson = lessons.getJSONObject(i);
                JSONArray links = lesson.optJSONArray("original_links");
                if (links == null) continue;
                for (int j = 0; j < links.length(); j++) {
                    JSONObject link = links.getJSONObject(j);
                    if (bookId.equals(link.optString("book_id")) && index == link.optInt("section", -1)
                        && title.equals(link.optString("title"))) {
                        addButton(parent, "配套研习 · " + lesson.optString("title"), false, new View.OnClickListener() {
                            @Override public void onClick(View view) {
                                savePosition(); LearningActivity.openLessonFromOriginal(MeihuaReferenceActivity.this, lesson.optString("id"));
                            }
                        });
                        break;
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    private void addTopicLessons(JSONObject topic) {
        JSONArray ids = topic.optJSONArray("lesson_ids");
        if (ids == null || ids.length() == 0) return;
        try {
            JSONArray lessons = readJsonAsset("appdata/meihua-learning.json", 1024 * 1024).getJSONArray("lessons");
            LinearLayout panel = card(); panel.addView(heading("接着做一课研习", 21));
            for (int i = 0; i < ids.length(); i++) {
                final JSONObject lesson = find(lessons, ids.optString(i));
                if (lesson == null) continue;
                addButton(panel, lesson.optString("title") + "　›", false, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        savePosition(); startActivity(new Intent(MeihuaReferenceActivity.this, LearningActivity.class)
                            .putExtra(LearningActivity.EXTRA_PAGE, "lesson").putExtra(LearningActivity.EXTRA_ITEM_ID, lesson.optString("id")));
                    }
                });
            }
        } catch (Exception ignored) { }
    }

    private JSONObject readJsonAsset(String path, int limit) throws Exception {
        InputStream input = getAssets().open(path);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > limit) throw new IllegalArgumentException("asset too large");
                bytes.write(buffer, 0, count);
            }
        } finally { input.close(); }
        return new JSONObject(new String(bytes.toByteArray(), "UTF-8"));
    }

    private static boolean isKnownFullText(String id) {
        for (String known : BOOK_IDS) if (known.equals(id)) return true;
        return false;
    }

    private void addRelatedClassics(String id) {
        if (!("P01".equals(id) || "P02".equals(id))) return;
        LinearLayout related = card();
        related.addView(heading("继续读相关原典", 21));
        related.addView(body("以下是可点击的离线资料入口。"));
        addButton(related, "周易经传 · 《说卦》《系辞》", false, new View.OnClickListener() {
            @Override public void onClick(View view) { showTopic("P04"); }
        });
        addButton(related, "梅花原典 · 全书阅读地图", false, new View.OnClickListener() {
            @Override public void onClick(View view) { showTopic("C01"); }
        });
    }

    private static boolean shouldShowSectionsDirectly(JSONArray sections) {
        if (sections == null || sections.length() == 0 || sections.length() > 6) return false;
        int characters = 0;
        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            if (section == null) return false;
            JSONArray paragraphs = section.optJSONArray("paragraphs");
            if (paragraphs == null) return false;
            for (int j = 0; j < paragraphs.length(); j++) {
                characters += paragraphs.optString(j).length();
                if (characters > 1800) return false;
            }
        }
        return true;
    }

    private void addReadingSection(JSONObject section, int index, boolean directReading) {
        LinearLayout panel = card();
        final String title = section.optString("title");
        panel.addView(caption("第 " + (index + 1) + " 节"));
        panel.addView(heading(title, 23));
        final LinearLayout paragraphs = column();
        JSONArray values = section.optJSONArray("paragraphs");
        for (int i = 0; i < values.length(); i++) paragraphs.addView(body(values.optString(i)));
        addDiagram(paragraphs, section.optString("diagram"));
        if (directReading) {
            panel.addView(paragraphs);
            return;
        }
        final String key = "section_" + topicId + "_" + index;
        boolean expanded = preferences.getBoolean(key, index == 0);
        paragraphs.setVisibility(expanded ? View.VISIBLE : View.GONE);
        final Button toggle = addButton(panel, expanded ? "收起讲解" : "展开讲解", false, null);
        toggle.setContentDescription((expanded ? "收起 " : "展开 ") + title);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean opening = paragraphs.getVisibility() != View.VISIBLE;
                paragraphs.setVisibility(opening ? View.VISIBLE : View.GONE);
                toggle.setText(opening ? "收起讲解" : "展开讲解");
                toggle.setContentDescription((opening ? "收起 " : "展开 ") + title);
                commit(preferences.edit().putBoolean(key, opening));
            }
        });
        panel.addView(paragraphs);
    }

    private void addSourcePanel(LinearLayout parent, JSONArray selected) {
        LinearLayout panel = detachedCard();
        panel.addView(heading("资料来源", 21));
        final LinearLayout details = column();
        details.addView(body(reference.optString("attribution")));
        for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.optJSONObject(i);
            if (selected != null && !contains(selected, source.optString("id"))) continue;
            details.addView(heading(source.optString("title"), 18));
            String license = source.optString("license");
            String note = source.optString("note");
            String url = source.optString("url");
            if (license.length() != 0) details.addView(caption(license));
            if (note.length() != 0) details.addView(body(note));
            if (url.length() != 0) details.addView(caption(url));
            final String sourceId = source.optString("id");
            addButton(details, "查看引用此出处的主题", false, new View.OnClickListener() {
                @Override public void onClick(View view) {
                    savePosition(); query = sourceId; filter = ""; bookmarksOnly = false; indexScroll = 0;
                    commit(preferences.edit().putBoolean("bookmarks_only", false));
                    showIndex(0);
                }
            });
        }
        final String key = "sources_open_" + (topicId.length() == 0 ? "index" : topicId);
        boolean expanded = preferences.getBoolean(key, false);
        details.setVisibility(expanded ? View.VISIBLE : View.GONE);
        final Button toggle = addButton(panel, expanded ? "收起来源" : "查看来源", false, null);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean opening = details.getVisibility() != View.VISIBLE;
                details.setVisibility(opening ? View.VISIBLE : View.GONE);
                toggle.setText(opening ? "收起来源" : "查看来源");
                commit(preferences.edit().putBoolean(key, opening));
            }
        });
        panel.addView(details);
        parent.addView(panel, cardParams());
    }

    private boolean isBookmarked(String id) {
        return id != null && id.length() != 0 && preferences.getBoolean("bookmark_" + id, false);
    }

    private int bookmarkCount() {
        int count = 0;
        for (int i = 0; i < topics.length(); i++) if (isBookmarked(topics.optJSONObject(i).optString("id"))) count++;
        return count;
    }

    private void addNavigation(boolean detail) {
        LinearLayout navigation = row();
        weightedButton(navigation, detail ? "返回列表" : "返回上一页", false, new View.OnClickListener() {
            @Override public void onClick(View view) {
                savePosition(); hideKeyboard();
                if (topicId.length() != 0) onBackPressed(); else finish();
            }
        });
        weightedButton(navigation, "卦例笔记", false, new View.OnClickListener() {
            @Override public void onClick(View view) {
                savePosition(); hideKeyboard(); NotebookActivity.openList(MeihuaReferenceActivity.this);
            }
        });
        content.addView(navigation);
    }

    private void showError() {
        showingError = true;
        buildPage("梅花进阶", "资料暂时未能载入");
        LinearLayout panel = card();
        panel.addView(body("请覆盖安装完整版本后再试，保留原有笔记和学习进度。"));
        addButton(panel, "返回上一页", true, new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
    }

    private void buildPage(String title, String subtitle) {
        readerBodies.clear(); readerClassics.clear();
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
    }

    private void buildReaderPage(String title, String subtitle) {
        readerBodies.clear(); readerClassics.clear();
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        JianYiUi.applyScenic(scroll,JianYiUi.SCENE_READING);
        scroll.setFitsSystemWindows(true);
        content = column();
        content.setPadding(dp(22), dp(20), dp(22), dp(34));
        TextView bookTitle = heading(title, 29);
        bookTitle.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL));
        content.addView(bookTitle);
        content.addView(caption(subtitle));
        View rule = new View(this); rule.setBackgroundColor(VERMILION);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(dp(48), dp(2));
        ruleParams.bottomMargin = dp(18); content.addView(rule, ruleParams);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll); JianYiUi.applySystemBars(this); JianYiUi.enter(content);
    }

    private TextView articleBody(String text, boolean classic) {
        TextView result = label(text, (classic ? 18f : 16f) + readerSizeStep() * 2f,
            classic ? JianYiUi.INK : JianYiUi.MUTED, false);
        readerBodies.add(result); readerClassics.add(Boolean.valueOf(classic));
        if (!classic) {
            int colon = text.indexOf('：');
            if (colon > 0 && colon < 10) {
                SpannableString marked = new SpannableString(text);
                marked.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, colon + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                result.setText(marked);
            }
        }
        result.setTypeface(android.graphics.Typeface.create(classic ? "serif" : "sans-serif", android.graphics.Typeface.NORMAL));
        result.setLineSpacing(dp(classic ? 9 : 6), 1.05f);
        result.setTextIsSelectable(true);
        result.setPadding(0, dp(4), 0, dp(18));
        return result;
    }

    private int readerSizeStep() { return Math.max(0, Math.min(2, preferences.getInt("reader_size_step", 0))); }

    private void showReaderSizeDialog() {
        final int selected = readerSizeStep();
        new AlertDialog.Builder(this).setTitle("阅读字号")
            .setSingleChoiceItems(new String[] {"标准 · 原文 18", "较大 · 原文 20", "大号 · 原文 22"}, selected,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss(); if (which != selected) resizeReaderText(which);
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void resizeReaderText(final int step) {
        if (scroll == null || content == null || readerBodies.isEmpty()) return;
        final ScrollView currentScroll = scroll; final LinearLayout currentContent = content;
        int position = currentScroll.getScrollY(); TextView visible = null; int offset = 0;
        for (TextView paragraph : readerBodies) {
            Rect bounds = new Rect(); paragraph.getDrawingRect(bounds);
            currentContent.offsetDescendantRectToMyCoords(paragraph, bounds);
            if (bounds.bottom > position) { visible = paragraph; offset = position - bounds.top; break; }
        }
        final TextView anchor = visible; final int beforeAnchor = Math.min(0, offset);
        final float withinAnchor = anchor == null ? 0f : Math.max(0, offset) / (float) Math.max(1, anchor.getHeight());
        final float pageFraction = position / (float) Math.max(1, currentContent.getHeight() - currentScroll.getHeight());
        commit(preferences.edit().putInt("reader_size_step", Math.max(0, Math.min(2, step))));
        currentContent.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (currentContent.getViewTreeObserver().isAlive()) currentContent.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (scroll != currentScroll || content != currentContent) return;
                int target;
                if (anchor != null) {
                    Rect bounds = new Rect(); anchor.getDrawingRect(bounds);
                    currentContent.offsetDescendantRectToMyCoords(anchor, bounds);
                    target = bounds.top + beforeAnchor + Math.round(withinAnchor * anchor.getHeight());
                } else target = Math.round(pageFraction * Math.max(0, currentContent.getHeight() - currentScroll.getHeight()));
                currentScroll.scrollTo(0, Math.max(0, target)); savePosition();
            }
        });
        for (int i = 0; i < readerBodies.size(); i++)
            readerBodies.get(i).setTextSize((readerClassics.get(i).booleanValue() ? 18f : 16f) + step * 2f);
    }

    private static String preview(String value, int max) {
        String compact = value == null ? "" : value.replace('\n', ' ').trim();
        return compact.length() <= max ? compact : compact.substring(0, max) + "…";
    }

    private void savePosition() {
        if (externalReader || showingError || preferences == null || scroll == null) return;
        SharedPreferences.Editor editor = preferences.edit();
        if (fullTextId.length() != 0) editor.putInt("fulltext_scroll_" + fullTextId
            + (fullTextSection >= 0 ? "_" + fullTextSection : ""), scroll.getScrollY());
        else if (libraryPage) { libraryScroll = scroll.getScrollY(); editor.putInt("library_scroll", libraryScroll); }
        else if (topicId.length() == 0) {
            if (search != null) query = normalizeQuery(search.getText().toString());
            indexScroll = scroll.getScrollY();
        } else editor.putInt("topic_scroll_" + topicId, scroll.getScrollY());
        editor.putString("query", query).putString("filter", filter).putInt("index_scroll", indexScroll);
        commit(editor);
    }

    private void restoreScroll(final int value) {
        final ScrollView current = scroll;
        current.post(new Runnable() {
            @Override public void run() { if (scroll == current) current.scrollTo(0, Math.max(0, value)); }
        });
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focused = getCurrentFocus();
        if (manager != null && focused != null) manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        if (content != null && scroll != null) {
            int position = scroll.getScrollY();
            content.requestFocus();
            scroll.scrollTo(0, position);
            restoreScroll(position);
        }
    }

    private void commit(SharedPreferences.Editor editor) {
        boolean saved;
        try { saved = editor.commit(); }
        catch (RuntimeException failure) { saved = false; }
        if (!saved) Toast.makeText(this, "阅读位置保存失败，请检查剩余空间", Toast.LENGTH_LONG).show();
    }

    private String groupTitle(String id) {
        JSONObject group = find(groups, id);
        return group == null ? "全部主题" : group.optString("title");
    }

    private static String normalizeQuery(String value) {
        String query = value == null ? "" : value.replace('\u3000', ' ').trim();
        return query.length() > 120 ? query.substring(0, 120) : query;
    }

    private static boolean isKnownGroup(String id) {
        for (String group : GROUP_IDS) if (group.equals(id)) return true;
        return false;
    }

    private static String validFilter(String value) { return isKnownGroup(value) ? value : ""; }

    private static String requiredId(JSONObject value) throws Exception {
        String id = requiredText(value, "id", 64);
        if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("invalid id");
        return id;
    }

    private static String requiredText(JSONObject value, String key, int limit) throws Exception {
        Object text = value.get(key);
        if (!(text instanceof String) || ((String) text).trim().length() == 0 || ((String) text).length() > limit)
            throw new IllegalArgumentException("invalid text " + key);
        return (String) text;
    }

    private static void optionalText(JSONObject value, String key, int limit) throws Exception {
        if (!value.has(key)) return;
        Object text = value.get(key);
        if (!(text instanceof String) || ((String) text).length() > limit)
            throw new IllegalArgumentException("invalid optional text " + key);
    }

    private static void optionalDiagram(JSONObject value) throws Exception {
        optionalText(value, "diagram", 64);
        String diagram = value.optString("diagram");
        if (diagram.length() != 0 && !diagram.matches("[a-z0-9_]{1,64}"))
            throw new IllegalArgumentException("invalid diagram");
    }

    private void addDiagram(LinearLayout parent, String diagram) {
        if (diagram == null || diagram.length() == 0) return;
        View visual;
        if ("hetu".equals(diagram)) visual = new KnowledgeMotifView(this, "Hetu");
        else if ("luoshu".equals(diagram)) visual = new KnowledgeMotifView(this, "Luoshu");
        else if ("wuxing".equals(diagram)) visual = new FivePhaseRelationView(this);
        else if ("bagua_mnemonic".equals(diagram)) visual = new TrigramMnemonicView(this);
        else if ("bagua_xiantian".equals(diagram)) visual = new KnowledgeDiagramView(this, "bagua_compass", "xiantian");
        else if ("bagua_houtian".equals(diagram)) visual = new KnowledgeDiagramView(this, "bagua_compass", "houtian");
        else if ("branches".equals(diagram)) visual = new KnowledgeDiagramView(this, "branches", "");
        else if ("hexagram".equals(diagram)) visual = new KnowledgeDiagramView(this, "hexagrams", "");
        else if ("polarity".equals(diagram)) visual = new KnowledgeDiagramView(this, "polarity", "");
        else visual = new ClassicDiagramView(this, diagram);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, dp(8), 0, dp(14));
        parent.addView(visual, layout);
    }

    private static String arrayText(JSONArray values, int index, int limit) throws Exception {
        Object text = values.get(index);
        if (!(text instanceof String) || ((String) text).trim().length() == 0 || ((String) text).length() > limit)
            throw new IllegalArgumentException("invalid array text");
        return (String) text;
    }

    private static JSONObject find(JSONArray values, String id) {
        if (values == null || id == null) return null;
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value != null && id.equals(value.optString("id"))) return value;
        }
        return null;
    }

    private static boolean contains(JSONArray values, String id) {
        for (int i = 0; i < values.length(); i++) if (id.equals(values.optString(i))) return true;
        return false;
    }

    private LinearLayout column() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    private LinearLayout row() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.HORIZONTAL);
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setBaselineAligned(false);
        return result;
    }

    private LinearLayout detachedCard() {
        LinearLayout result = column();
        result.setPadding(dp(15), dp(13), dp(15), dp(13));
        result.setBackground(JianYiUi.rounded(this, READING_PAPER, READING_LINE, 6));
        return result;
    }

    private LinearLayout card() {
        LinearLayout result = detachedCard();
        content.addView(result, cardParams());
        return result;
    }

    private TextView label(String text, float size, int color, boolean title) {
        TextView result = new TextView(this);
        result.setText(text == null ? "" : text);
        JianYiUi.textStyle(result, size, title);
        result.setTextColor(color);
        result.setLineSpacing(dp(4), 1.02f);
        result.setFocusable(false);
        return result;
    }

    private TextView heading(String text, float size) {
        TextView result = label(text, size, JianYiUi.INK, true);
        result.setPadding(0, 0, 0, dp(8));
        return result;
    }

    private TextView body(String text) {
        TextView result = label(text, 16f, JianYiUi.INK, false);
        result.setPadding(0, dp(2), 0, dp(8));
        return result;
    }

    private TextView caption(String text) {
        TextView result = label(text, 13f, JianYiUi.MUTED, false);
        result.setPadding(0, dp(5), 0, dp(9));
        return result;
    }

    private TextView sectionLabel(String text) {
        TextView result = heading(text, 22);
        result.setPadding(dp(3), dp(22), dp(3), dp(3));
        return result;
    }

    private Button addButton(LinearLayout parent, String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        JianYiUi.skinButton(button, primary);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams layout = params();
        layout.topMargin = dp(8);
        parent.addView(button, layout);
        return button;
    }

    private void weightedButton(LinearLayout parent, String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        JianYiUi.skinButton(button, primary);
        button.setPadding(dp(6), dp(10), dp(6), dp(10));
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
}
