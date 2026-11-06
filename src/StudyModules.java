package com.jianyi.yixue.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** New local study modules. No original account, server token or entitlement is created. */
public final class StudyModules {
    private static final int PAPER = JianYiUi.PAPER;
    private static final int INK = JianYiUi.INK;
    private static final int MUTED = JianYiUi.MUTED;
    private static final int ACCENT = JianYiUi.GOLD;
    private static final int LINE = JianYiUi.LINE;
    private static final int WHITE = JianYiUi.WHITE;
    private static final int MOVING = 0xff9a3412;
    private static final String PREFS = "guest_meihua_history_v1";
    private static final TimeZone BEIJING = TimeZone.getTimeZone("GMT+08:00");
    private static final String[] BRANCHES = {"", "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static List<Hexagram> catalog;
    private static JSONObject detailedSourceCatalog;
    private StudyModules() { }

    private static final class Hexagram {
        final int number;
        final String shortName, fullName, upper, lower, code, url;
        Hexagram(JSONObject object) throws Exception {
            number = object.getInt("number");
            shortName = object.getString("short_name");
            fullName = object.getString("full_name");
            upper = MeihuaEngine.trigramName(object.getInt("upper"));
            lower = MeihuaEngine.trigramName(object.getInt("lower"));
            code = object.getString("code");
            url = object.getString("url");
            if (number < 1 || number > 64 || shortName.length() == 0 || fullName.length() == 0
                || !code.matches("[01]{6}") || !allowedUrl(url)) throw new IllegalArgumentException("invalid catalog entry");
            int[] lines = bits(code);
            if (MeihuaEngine.trigramId(java.util.Arrays.copyOfRange(lines, 3, 6)) != object.getInt("upper")
                || MeihuaEngine.trigramId(java.util.Arrays.copyOfRange(lines, 0, 3)) != object.getInt("lower"))
                throw new IllegalArgumentException("catalog diagram mismatch");
        }
        String title() { return "第 " + number + " 卦 · " + fullName; }
    }

    private static synchronized List<Hexagram> loadCatalog(Context context) throws Exception {
        if (catalog != null) return catalog;
        InputStream stream = context.getAssets().open("appdata/hexagrams.json");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > 256 * 1024) throw new IllegalArgumentException("catalog too large");
                bytes.write(buffer, 0, count);
            }
        } finally { stream.close(); }
        JSONArray records = new JSONObject(new String(bytes.toByteArray(), "UTF-8")).getJSONArray("hexagrams");
        if (records.length() != 64) throw new IllegalArgumentException("catalog must contain 64 hexagrams");
        ArrayList<Hexagram> entries = new ArrayList<Hexagram>();
        HashSet<Integer> numbers = new HashSet<Integer>();
        HashSet<String> codes = new HashSet<String>();
        for (int i = 0; i < records.length(); i++) {
            Hexagram entry = new Hexagram(records.getJSONObject(i));
            if (!numbers.add(Integer.valueOf(entry.number)) || !codes.add(entry.code))
                throw new IllegalArgumentException("duplicate catalog entry");
            entries.add(entry);
        }
        java.util.Collections.sort(entries, new java.util.Comparator<Hexagram>() {
            @Override public int compare(Hexagram a, Hexagram b) { return a.number - b.number; }
        });
        catalog = entries;
        return catalog;
    }

    /** Native searchable catalog and diagrams remain usable without any network. */
    public static void showLibrary(final Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { libraryOnMain(activity); }
        });
    }

    /** Opens the exact offline Zhouyi record without leaving the current result page. */
    public static void showClassicByCode(final Activity activity, final String code) {
        if (activity == null || code == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                Hexagram entry = hexForCode(activity, code);
                if (entry == null) {
                    Toast.makeText(activity, "本卦原典暂未能载入。", Toast.LENGTH_SHORT).show();
                    return;
                }
                showOfflineHexagram(activity, entry);
            }
        });
    }

    /** Opens the full, locally packaged interpretation for a search result. */
    public static void showDetailedByCode(final Activity activity, final String code) {
        if (activity == null || code == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                Hexagram entry = hexForCode(activity, code);
                if (entry == null) {
                    Toast.makeText(activity, "卦象解析暂未能载入。", Toast.LENGTH_SHORT).show();
                    return;
                }
                showDetailedHexagram(activity, entry, 0);
            }
        });
    }

    private static void libraryOnMain(final Activity activity) {
        final LinearLayout root = page(activity, "六十四卦", "古文、卦图与易安居完整详解");
        homeButton(activity, root);
        root.addView(text(activity, "搜索卦序、卦名或上下卦，读卦辞与爻辞，循象明理。", 17, MUTED, false));
        final LinearLayout matrix = column(activity);
        root.addView(matrix);
        final EditText search = input(activity, "例如：乾、坎、既济、63", false);
        search.setSingleLine(true);
        root.addView(search);
        final TextView count = text(activity, "", 15, MUTED, false);
        root.addView(count);
        final LinearLayout rows = column(activity);
        root.addView(rows);
        final List<Hexagram> entries;
        try { entries = loadCatalog(activity); }
        catch (Exception error) {
            root.addView(text(activity, "六十四卦目录未能载入。请重新安装完整版本；梅花起卦仍可使用。", 18, MOVING, false));
            return;
        }
        addHexagramMatrix(activity, matrix, entries);
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                String query = search.getText().toString().trim();
                rows.removeAllViews();
                int matched = 0;
                for (final Hexagram entry : entries) {
                    String terms = entry.number + " " + entry.shortName + " " + entry.fullName + " " + entry.upper + " " + entry.lower;
                    if (query.length() > 0 && !terms.contains(query)) continue;
                    matched++;
                    rows.addView(button(activity, entry.title() + "\n上卦：" + entry.upper + "　下卦：" + entry.lower,
                        new View.OnClickListener() {
                            @Override public void onClick(View view) { showHexagram(activity, entry); }
                        }));
                }
                count.setText("共 " + matched + " 卦 / 64 卦");
                if (matched == 0) rows.addView(text(activity, "没有找到。可试试单个卦名，例如“离”。", 18, MUTED, false));
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh.run(); }
            @Override public void afterTextChanged(Editable editable) { }
        });
        refresh.run();
    }

    private static void addHexagramMatrix(final Activity activity, LinearLayout target, List<Hexagram> entries) {
        target.addView(text(activity,"六十四卦总表",22,INK,true));
        target.addView(text(activity,"横向为上卦，纵向为下卦；点击卦名直接进入。",14,MUTED,false));
        final String[] names={"乾","兑","离","震","巽","坎","艮","坤"};
        LinearLayout header=new LinearLayout(activity);header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(matrixCell(activity,"下＼上",false,null),new LinearLayout.LayoutParams(0,dp(activity,44),1.25f));
        for(String name:names)header.addView(matrixCell(activity,name,false,null),new LinearLayout.LayoutParams(0,dp(activity,44),1f));target.addView(header);
        for(final String lower:names){LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);row.addView(matrixCell(activity,lower,false,null),new LinearLayout.LayoutParams(0,dp(activity,46),1.25f));
            for(final String upper:names){Hexagram found=null;for(Hexagram item:entries)if(upper.equals(item.upper)&&lower.equals(item.lower)){found=item;break;}final Hexagram entry=found;row.addView(matrixCell(activity,entry==null?"—":entry.shortName,true,new View.OnClickListener(){@Override public void onClick(View v){if(entry!=null)showHexagram(activity,entry);}}),new LinearLayout.LayoutParams(0,dp(activity,46),1f));}target.addView(row);
        }
    }

    private static TextView matrixCell(Context context,String value,boolean active,View.OnClickListener listener){
        TextView cell=text(context,value,active?14:12,active?INK:MUTED,active);cell.setGravity(Gravity.CENTER);cell.setPadding(dp(context,1),0,dp(context,1),0);cell.setClickable(active);cell.setFocusable(active);cell.setOnClickListener(listener);
        GradientDrawable bg=new GradientDrawable();bg.setColor(active?WHITE:0xffeef2ea);bg.setStroke(dp(context,1),LINE);bg.setCornerRadius(dp(context,5));cell.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-1);p.setMargins(dp(context,1),dp(context,1),dp(context,1),dp(context,1));cell.setLayoutParams(p);return cell;
    }

    private static void showHexagram(final Activity activity, final Hexagram entry) {
        LinearLayout content = column(activity);
        content.setFitsSystemWindows(true);
        content.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));
        content.addView(text(activity, "上卦：" + entry.upper + "　下卦：" + entry.lower, 20, INK, true));
        content.addView(new LinesView(activity, bits(entry.code), 0), new LinearLayout.LayoutParams(-1, dp(activity, 190)));
        content.addView(text(activity, "六爻从下向上数：最下为初爻，最上为上爻。实线为阳爻，断线为阴爻。", 18, MUTED, false));
        content.addView(button(activity, "解析", new View.OnClickListener() {
            @Override public void onClick(View view) { showOfflineHexagram(activity, entry); }
        }));
        addDetailedReadingButton(activity, content, entry, 0);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFitsSystemWindows(true);
        scroll.addView(content);
        showDialog(activity, JianYiUi.dialogBuilder(activity).setTitle(entry.title()).setView(scroll)
            .setNegativeButton("返回", null).create());
    }

    private static JSONObject readStudyAsset(Context context, String asset) throws Exception {
        InputStream stream = context.getAssets().open(asset);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final int maximum = "appdata/zhouyi-details.json".equals(asset) ? 8 * 1024 * 1024 : 1024 * 1024;
        try {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > maximum) throw new IllegalArgumentException("study asset too large");
                bytes.write(buffer, 0, count);
            }
        } finally { stream.close(); }
        return new JSONObject(new String(bytes.toByteArray(), "UTF-8"));
    }

    /** Accept prose strings, paragraph arrays or named text objects without inventing missing text. */
    private static String studyText(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                String part = studyText(array.opt(i));
                if (part.length() > 0) { if (result.length() > 0) result.append("\n\n"); result.append(part); }
            }
            return result.toString();
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String[] proseKeys = {"text", "content", "body", "value", "paragraphs"};
            for (String key : proseKeys) {
                if (object.has(key) && object.opt(key) != JSONObject.NULL) return studyText(object.opt(key));
            }
            return studyMetadata(object);
        }
        return String.valueOf(value).trim();
    }

    private static String studyMetadata(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                String part = studyMetadata(array.opt(i));
                if (part.length() > 0) { if (result.length() > 0) result.append("\n\n"); result.append(part); }
            }
            return result.toString();
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            StringBuilder result = new StringBuilder();
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String part = studyMetadata(object.opt(key));
                if (part.length() == 0) continue;
                if (result.length() > 0) result.append("\n");
                result.append(metadataLabel(key)).append("：").append(part);
            }
            return result.toString();
        }
        String plain = String.valueOf(value).trim();
        if ("same".equals(plain)) return "文本一致";
        if ("punctuation_spacing".equals(plain)) return "标点或分隔差异";
        if ("orthography".equals(plain)) return "字形差异";
        if ("text_variant".equals(plain)) return "正文异文（保留底本）";
        if ("verified_two_sources".equals(plain)) return "已对照两处来源；差异见各段异文";
        return plain;
    }

    private static String metadataLabel(String key) {
        if ("url".equals(key) || "source_url".equals(key)) return "来源链接";
        if ("urls".equals(key) || "source_urls".equals(key)) return "来源链接";
        if ("revision".equals(key) || "oldid".equals(key) || "revision_id".equals(key)) return "底本修订版本";
        if ("title".equals(key) || "name".equals(key) || "label".equals(key)) return "名称";
        if ("notes".equals(key) || "variant_notes".equals(key)) return "校记";
        if ("status".equals(key)) return "核对状态";
        if ("method".equals(key)) return "核对方法";
        if ("sources".equals(key)) return "来源";
        if ("section".equals(key)) return "段落";
        if ("sections".equals(key)) return "段落核对";
        if ("differences".equals(key)) return "差异记录";
        if ("primary".equals(key)) return "底本";
        if ("secondary".equals(key)) return "对校本";
        if ("context".equals(key)) return "上下文";
        if ("source_notes".equals(key)) return "底本原注";
        if ("license_url".equals(key)) return "来源许可说明";
        return key;
    }

    private static Object firstValue(JSONObject object, String... keys) {
        for (String key : keys) if (object.has(key) && object.opt(key) != JSONObject.NULL) return object.opt(key);
        return null;
    }

    private static void addStudySection(Activity activity, LinearLayout target, String heading, Object value) {
        String content = studyText(value);
        if (content.length() == 0) return;
        target.addView(text(activity, heading, 22, INK, true));
        target.addView(text(activity, content, 19, INK, false));
    }

    private static void addExpandable(final Activity activity, LinearLayout target, String label, String details) {
        if (details.length() == 0) return;
        if (!label.contains("来源") && !label.contains("校记")) {
            target.addView(text(activity, label, 19, INK, true));
            final TextView direct = text(activity, details, 17, MUTED, false);
            direct.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 12));
            direct.setBackground(round(activity, PAPER, 14, 0));
            target.addView(direct);
            return;
        }
        final TextView body = text(activity, details, 17, MUTED, false);
        body.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        body.setBackground(round(activity, PAPER, 14, 0));
        body.setVisibility(View.GONE);
        final Button toggle = button(activity, "展开" + label, null);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean show = body.getVisibility() != View.VISIBLE;
                body.setVisibility(show ? View.VISIBLE : View.GONE);
                toggle.setText((show ? "收起" : "展开") + label);
                if (show) JianYiUi.enter(body);
            }
        });
        target.addView(toggle); target.addView(body);
    }

    private static String lineLabel(int position, Hexagram entry) {
        boolean yang = entry.code.charAt(position - 1) == '1';
        if (position == 1) return yang ? "初九" : "初六";
        if (position == 6) return yang ? "上九" : "上六";
        return (yang ? "九" : "六") + new String[] {"", "", "二", "三", "四", "五"}[position];
    }

    private static void addClassicLines(Activity activity, LinearLayout target, Object value, Hexagram entry, boolean extras) {
        if (!(value instanceof JSONArray)) { addStudySection(activity, target, extras ? "用九 / 用六" : "爻辞与小象", value); return; }
        JSONArray lines = (JSONArray) value;
        if (lines.length() == 0) return;
        target.addView(text(activity, extras ? "用九 / 用六" : "爻辞与小象（由初爻至上爻）", 22, INK, true));
        for (int i = 0; i < lines.length(); i++) {
            Object raw = lines.opt(i);
            if (!(raw instanceof JSONObject)) {
                if (!extras && i < 6) target.addView(text(activity, lineLabel(i + 1, entry), 20, INK, true));
                String prose = studyText(raw);
                if (prose.length() > 0) target.addView(text(activity, prose, 19, INK, false));
                continue;
            }
            JSONObject line = (JSONObject) raw;
            int position = line.optInt("position", i + 1);
            String label = line.optString("label", "");
            if (label.length() == 0 && !extras && position >= 1 && position <= 6) label = lineLabel(position, entry);
            if (label.length() == 0) label = extras ? "附文" : "爻辞";
            target.addView(text(activity, label, 20, INK, true));
            String prose = studyText(firstValue(line, "text", "content", "line", "judgment"));
            if (prose.length() > 0) target.addView(text(activity, prose, 19, INK, false));
            String xiang = studyText(firstValue(line, "xiang", "image", "small_image"));
            if (xiang.length() > 0) target.addView(text(activity, "小象：" + xiang, 18, MUTED, false));
        }
    }

    private static void showOfflineHexagram(final Activity activity, final Hexagram entry) {
        LinearLayout content = column(activity);
        content.setFitsSystemWindows(true);
        content.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 16));
        content.addView(text(activity, "《周易》离线原文 · 底本繁体", 22, INK, true));
        addDetailedReadingButton(activity, content, entry, 0);
        try {
            JSONObject root = readStudyAsset(activity, "appdata/zhouyi-classics.json");
            JSONArray entries = root.getJSONArray("hexagrams");
            JSONObject classic = null;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.optJSONObject(i);
                if (item != null && item.optInt("number", 0) == entry.number) { classic = item; break; }
            }
            if (classic == null) throw new IllegalArgumentException("missing hexagram");
            addStudySection(activity, content, "卦辞", firstValue(classic, "judgment", "gua_ci", "guaci"));
            addZhouyiSummary(activity, content, entry.number);
            addClassicLines(activity, content, firstValue(classic, "lines", "line_texts"), entry, false);
            addClassicLines(activity, content, firstValue(classic, "extra_lines", "extra"), entry, true);
            addStudySection(activity, content, "彖传", firstValue(classic, "tuan", "tuan_text"));
            addStudySection(activity, content, "大象", firstValue(classic, "image", "xiang", "great_image"));
            String sources = "";
            Object rootSources = firstValue(root, "sources", "source_urls");
            if (rootSources != null) sources += "底本来源\n" + studyMetadata(rootSources) + "\n\n";
            Object entrySources = firstValue(classic, "sources", "source_urls");
            if (entrySources != null) sources += "本卦来源\n" + studyMetadata(entrySources) + "\n\n";
            String[] rootNotes = {"edition_note", "description", "text_notice", "text_note", "license_url"};
            for (String key : rootNotes) {
                String note = studyText(root.opt(key));
                if (note.length() > 0) sources += note + "\n\n";
            }
            String sourceNotes = studyMetadata(classic.opt("source_notes"));
            if (sourceNotes.length() > 0) sources += "底本原注\n" + sourceNotes + "\n\n";
            Object validation = firstValue(classic, "cross_validation", "variant_notes", "notes");
            if (validation != null) sources += "核对与校记\n" + studyMetadata(validation);
            addExpandable(activity, content, "来源与校记", sources.trim());
        } catch (Exception error) {
            content.addView(text(activity, "本卦原文未能载入，请重新安装完整版本。", 18, MOVING, false));
        }
        ScrollView scroll = new ScrollView(activity); scroll.setFitsSystemWindows(true); scroll.addView(content);
        showDialog(activity, JianYiUi.dialogBuilder(activity).setTitle(entry.title() + " · 离线原文").setView(scroll)
            .setPositiveButton("返回卦图", null).create());
    }

    /** The authorized article collection is immutable for the lifetime of this installed build. */
    private static synchronized JSONObject detailedSources(Context context) throws Exception {
        if (detailedSourceCatalog == null) {
            JSONObject loaded = readStudyAsset(context, "appdata/zhouyi-details.json");
            if (loaded.getJSONArray("hexagrams").length() != 64) throw new IllegalArgumentException("incomplete articles");
            detailedSourceCatalog = loaded;
        }
        return detailedSourceCatalog;
    }

    private static void showDetailedHexagram(final Activity activity, final Hexagram entry, int moving) {
        try { new DetailedReader(activity, entry, moving).show(); }
        catch (Exception error) {
            LinearLayout content = column(activity);
            content.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12));
            content.addView(detailText(activity, "本卦详细解析未能载入，请重新安装完整版本。", 18, MOVING, false));
            showDialog(activity, JianYiUi.dialogBuilder(activity).setTitle(entry.title() + " · 易安居详解")
                .setView(content).setPositiveButton("返回", null).create());
        }
    }

    /** A compact overview and a visible six-line grid precede the long-form source text. */
    private static final class DetailedReader {
        final Activity activity;
        final Hexagram entry;
        final JSONObject root, article;
        final JSONObject[] lines = new JSONObject[7];
        final Button[] tabs = new Button[7];
        final int moving;
        final LinearLayout content, body;
        final ScrollView scroll;
        final JSONArray overview = new JSONArray();

        DetailedReader(Activity activity, Hexagram entry, int moving) throws Exception {
            this.activity = activity;
            this.entry = entry;
            this.moving = moving >= 1 && moving <= 6 ? moving : 0;
            root = detailedSources(activity);
            JSONObject found = null;
            JSONArray articles = root.getJSONArray("hexagrams");
            for (int i = 0; i < articles.length(); i++) {
                JSONObject candidate = articles.optJSONObject(i);
                if (candidate != null && candidate.optInt("number", 0) == entry.number) { found = candidate; break; }
            }
            if (found == null || !entry.code.equals(found.optString("code", ""))
                || found.getJSONArray("paragraphs").length() == 0) throw new IllegalArgumentException("invalid article");
            article = found;
            JSONArray sections = article.getJSONArray("sections");
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.optJSONObject(i);
                if (section == null || !"whole".equals(section.optString("kind", ""))) continue;
                overview.put(section.optString("title", ""));
                JSONArray paragraphs = section.optJSONArray("paragraphs");
                if (paragraphs != null) for (int n = 0; n < paragraphs.length(); n++) overview.put(paragraphs.opt(n));
            }
            if (overview.length() == 0) throw new IllegalArgumentException("missing overview");
            JSONArray sourceLines = article.optJSONArray("lines");
            if (sourceLines != null) {
                for (int i = 0; i < sourceLines.length(); i++) {
                    JSONObject line = sourceLines.optJSONObject(i);
                    if (line == null) continue;
                    int position = line.optInt("position", 0);
                    JSONArray paragraphs = line.optJSONArray("paragraphs");
                    if (position >= 1 && position <= 6 && paragraphs != null && paragraphs.length() > 0) lines[position] = line;
                }
            }
            content = column(activity);
            content.setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 14), dp(activity, 8));
            content.setFocusableInTouchMode(true);
            content.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            tabs[0] = detailTab(activity, "卦意总览", new View.OnClickListener() {
                @Override public void onClick(View view) { select(0); }
            });
            tabs[0].setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            content.addView(tabs[0]);
            for (int row = 0; row < 2; row++) {
                LinearLayout grid = new LinearLayout(activity);
                grid.setBaselineAligned(false);
                for (int col = 0; col < 3; col++) {
                    final int position = row * 3 + col + 1;
                    String label = lineLabel(position, entry) + (position == this.moving ? " · 动" : "");
                    tabs[position] = detailTab(activity, label, new View.OnClickListener() {
                        @Override public void onClick(View view) { select(position); }
                    });
                    tabs[position].setEnabled(lines[position] != null);
                    grid.addView(tabs[position]);
                }
                content.addView(grid);
            }
            body = column(activity);
            scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            scroll.addView(body);
            content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            select(this.moving != 0 && lines[this.moving] != null ? this.moving : 0);
        }

        void select(int position) {
            for (int i = 0; i < tabs.length; i++) {
                Button tab = tabs[i];
                JianYiUi.skinButton(tab, i == position);
                if (i > 0 && i != position && i == moving) tab.setBackground(round(activity, PAPER, 15, ACCENT));
                tab.setSelected(i == position);
                tab.setContentDescription((i == 0 ? "卦意总览" : lineLabel(i, entry))
                    + (i == moving && i != 0 ? "，动爻" : "") + (i == position ? "，已选中" : ""));
            }
            JSONObject line = position == 0 ? null : lines[position];
            String heading = position == 0 ? "卦意总览" : lineLabel(position, entry) + (position == moving ? " · 动爻" : " · 爻意");
            renderDetailedParagraphs(activity, body, article, position == 0 ? overview : line.optJSONArray("paragraphs"),
                heading, root.optString("attribution", "来源自易安居（zhouyi.cc）"), entry, line);
            resetTop();
        }

        void resetTop() {
            content.requestFocus();
            scroll.post(new Runnable() {
                @Override public void run() { content.requestFocus(); scroll.scrollTo(0, 0); }
            });
        }

        void show() {
            final AlertDialog dialog = JianYiUi.dialogBuilder(activity).setTitle(entry.title() + " · 易安居详解")
                .setView(content).setPositiveButton("返回", null).create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override public void onShow(DialogInterface ignored) {
                    if (dialog.getWindow() != null) dialog.getWindow().setLayout(dialog.getWindow().getAttributes().width,
                        Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.92f));
                    resetTop();
                }
            });
            showDialog(activity, dialog);
        }
    }

    private static Button detailTab(Context context, String label, View.OnClickListener action) {
        Button view = readerButton(context, label, action);
        view.setTextSize(16);
        view.setMinHeight(dp(context, 46));
        view.setMinimumHeight(dp(context, 46));
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        return view;
    }

    private static TextView detailText(Context context, String prose, int size, int color, boolean bold) {
        TextView view = text(context, prose, size, color, bold);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(context, 4), 1.14f);
        view.setPadding(0, dp(context, bold ? 5 : 3), 0, dp(context, bold ? 7 : 8));
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        return view;
    }

    private static final class DetailBlock {
        final String sourceTitle, title, kind;
        final ArrayList<String> paragraphs = new ArrayList<String>();
        DetailBlock(String sourceTitle, String title, String kind) {
            this.sourceTitle = sourceTitle; this.title = title; this.kind = kind;
        }
    }

    private static DetailBlock detailHeading(String prose) {
        if ("白话文解释".equals(prose)) return new DetailBlock(prose, "白话解读", "plain");
        if ("《断易天机》解".equals(prose)) return new DetailBlock(prose, "卦意解读", "tianji");
        if ("北宋易学家邵雍解".equals(prose)) return new DetailBlock(prose, "解卦参考", "shao");
        if ("台湾国学大儒傅佩荣解".equals(prose)) return new DetailBlock(prose, "应用参考", "application");
        if ("传统解卦".equals(prose)) return new DetailBlock(prose, "传统解卦", "tradition");
        if (prose.length() > 32) return null;
        if (prose.endsWith("哲学含义")) return new DetailBlock(prose, "哲学与补充", "philosophy");
        if (prose.endsWith("变卦")) return new DetailBlock(prose, "爻变参考", "change");
        if (prose.matches("(初[九六]|[九六][二三四五]|上[九六]|用[九六])爻辞")) return new DetailBlock(prose, "爻辞与象辞", "original");
        if (prose.endsWith("原文")) return new DetailBlock(prose, "卦辞与象辞", "original");
        return null;
    }

    private static void renderDetailedParagraphs(final Activity activity, LinearLayout target, final JSONObject article,
        JSONArray paragraphs, String heading, String attribution, final Hexagram entry, JSONObject line) {
        target.removeAllViews();
        target.addView(detailText(activity, heading, 21, INK, true));
        if (line == null && article.optInt("number", 0) == 11) {
            target.addView(detailText(activity, "源文校记：原网页开篇混入豫卦内容，以下保留原文。泰卦经文请见古文页。", 17, MOVING, false));
        }
        if (line != null && "source_conflict".equals(line.optString("mapping_status", ""))) {
            target.addView(detailText(activity, line.optString("source_notice", "源文校记：本节存在其他卦或爻的内容，请结合对应古文核对。"), 17, MOVING, false));
        }
        HashSet<String> sectionHeadings = new HashSet<String>();
        JSONArray sections = article.optJSONArray("sections");
        if (sections != null) {
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.optJSONObject(i);
                if (section != null) sectionHeadings.add(section.optString("title", ""));
            }
        }
        ArrayList<DetailBlock> blocks = new ArrayList<DetailBlock>();
        DetailBlock active = new DetailBlock("", "补充解读", "supplement");
        for (int i = 0; i < paragraphs.length(); i++) {
            String prose = studyText(paragraphs.opt(i));
            if (prose.length() == 0 || sectionHeadings.contains(prose)) continue;
            DetailBlock next = detailHeading(prose);
            if (next != null) {
                if (active.paragraphs.size() > 0) blocks.add(active);
                active = next;
            } else active.paragraphs.add(prose);
        }
        if (active.paragraphs.size() > 0) blocks.add(active);
        for (DetailBlock block : blocks) addDetailCard(activity, target, block);
        final HashSet<String> rawHeadings = sectionHeadings;
        addDetailDisclosure(activity, target, "完整原文", new DetailContent() {
            @Override public void build(LinearLayout detail) {
                JSONArray complete = article.optJSONArray("paragraphs");
                if (complete == null) return;
                for (int i = 0; i < complete.length(); i++) {
                    String prose = studyText(complete.opt(i));
                    if (prose.length() > 0) detail.addView(detailText(activity, prose, rawHeadings.contains(prose) ? 21 : 17, INK, rawHeadings.contains(prose)));
                }
            }
        });
        target.addView(detailText(activity, attribution, 13, MUTED, false));
        addDetailDisclosure(activity, target, "来源与校记", new DetailContent() {
            @Override public void build(LinearLayout detail) {
                String notes = studyMetadata(article.opt("source_issues"));
                if (notes.length() > 0) detail.addView(detailText(activity, notes, 16, MUTED, false));
                detail.addView(detailText(activity, article.optString("source_url", entry.url), 13, MUTED, false));
            }
        });
    }

    private static void addDetailCard(final Activity activity, LinearLayout target, final DetailBlock block) {
        LinearLayout group = card(activity);
        group.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        group.addView(detailText(activity, block.title, 21, INK, true));
        if (block.sourceTitle.length() > 0 && !block.sourceTitle.equals(block.title))
            group.addView(detailText(activity, block.sourceTitle, 13, MUTED, false));
        boolean preview = "original".equals(block.kind) || "plain".equals(block.kind) || "tianji".equals(block.kind)
            || "shao".equals(block.kind) || ("tradition".equals(block.kind) && block.paragraphs.get(0).length() <= 140);
        int start = 0;
        if (preview) { addDetailField(activity, group, block.paragraphs.get(0)); start = 1; }
        if (start < block.paragraphs.size()) {
            final int remaining = start;
            addDetailDisclosure(activity, group, block.title, new DetailContent() {
                @Override public void build(LinearLayout detail) {
                    for (int i = remaining; i < block.paragraphs.size(); i++) addDetailField(activity, detail, block.paragraphs.get(i));
                }
            });
        }
        target.addView(group);
    }

    /** Keep the website's prose intact while giving its labelled applications a visual hierarchy. */
    private static void addDetailField(Activity activity, LinearLayout target, String prose) {
        int full = prose.indexOf('：'), ascii = prose.indexOf(':');
        int colon = full < 0 ? ascii : ascii < 0 ? full : Math.min(full, ascii);
        String label = colon > 0 && colon <= 5 ? prose.substring(0, colon) : "";
        if (("|时运|财运|家宅|身体|大象|运势|事业|经商|求名|婚恋|决策|家运|疾病|胎孕|子女|周转|买卖|等人|寻人|失物|外出|考试|诉讼|求事|改行|开业|").contains("|" + label + "|")) {
            target.addView(detailText(activity, label, 15, ACCENT, true));
            target.addView(detailText(activity, prose.substring(colon + 1).trim(), 18, INK, false));
        } else target.addView(detailText(activity, prose, 18, INK, false));
    }

    private interface DetailContent { void build(LinearLayout detail); }

    /** Expensive long-form views are created only when the reader opens their section. */
    private static void addDetailDisclosure(final Activity activity, LinearLayout target, final String label, final DetailContent builder) {
        final LinearLayout detail = column(activity);
        detail.setVisibility(View.GONE);
        final boolean[] built = {false};
        final Button toggle = button(activity, "展开" + label, null);
        toggle.setTextSize(15);
        toggle.setMinHeight(dp(activity, 44));
        toggle.setMinimumHeight(dp(activity, 44));
        toggle.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        toggle.setFocusable(false); toggle.setFocusableInTouchMode(false);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean show = detail.getVisibility() != View.VISIBLE;
                if (show && !built[0]) { builder.build(detail); built[0] = true; }
                detail.setVisibility(show ? View.VISIBLE : View.GONE);
                toggle.setText((show ? "收起" : "展开") + label);
                if (show) JianYiUi.enter(detail);
            }
        });
        target.addView(toggle);
        target.addView(detail);
    }

    /** Called by the original eight-trigram selector; index order intentionally follows that selector. */
    public static void showBaguaReference(final Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { baguaReferenceOnMain(activity); }
        });
    }

    private static void baguaReferenceOnMain(final Activity activity) {
        LinearLayout root = page(activity, "八卦类象", "《说卦传》古文参考 · 离线阅读");
        root.addView(button(activity, "返回八卦选择", new View.OnClickListener() {
            @Override public void onClick(View view) { activity.finish(); }
        }));
        root.addView(text(activity, "本页是依据《说卦传》古文重建的参考，不是原服务器的八张图表。", 18, MUTED, false));
        try {
            int index = 0;
            String value = activity.getIntent().getStringExtra("index");
            if (value != null && value.matches("[1-8]")) index = Integer.parseInt(value);
            if (index == 0) index = activity.getIntent().getIntExtra("index", 0);
            if (index < 1 || index > 8) throw new IllegalArgumentException("invalid original trigram index");
            JSONObject data = readStudyAsset(activity, "appdata/bagua-shuogua-reference.json");
            JSONArray trigrams = data.getJSONArray("trigrams");
            JSONObject item = null;
            for (int i = 0; i < trigrams.length(); i++) {
                JSONObject candidate = trigrams.optJSONObject(i);
                if (candidate != null && candidate.optInt("index", 0) == index) { item = candidate; break; }
            }
            if (item == null) throw new IllegalArgumentException("missing trigram reference");
            root.addView(text(activity, item.optString("name", "") + " " + item.optString("symbol", ""), 38, INK, true));
            addStudySection(activity, root, "卦性", item.opt("quality"));
            addStudySection(activity, root, "动物", item.opt("animal"));
            addStudySection(activity, root, "身体", item.opt("body"));
            addStudySection(activity, root, "家属", item.opt("family"));
            addStudySection(activity, root, "《说卦传》类象原文", item.opt("source_excerpt"));
            String notes = studyMetadata(item.opt("variant_notes"));
            String sourceUrls = studyMetadata(item.opt("source_urls"));
            String edition = studyText(data.opt("edition_note"));
            String details = edition + (sourceUrls.length() == 0 ? "" : "\n\n来源链接\n" + sourceUrls)
                + (notes.length() == 0 ? "" : "\n\n异文校记\n" + notes);
            addExpandable(activity, root, "来源与校记", details.trim());
        } catch (Exception error) {
            root.addView(text(activity, "本卦离线参考未能载入。请返回八卦选择，或重新安装完整版本。", 18, MOVING, false));
        }
    }

    private static boolean allowedUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                && ("www.zhouyi.cc".equalsIgnoreCase(host) || "zhouyi.cc".equalsIgnoreCase(host))
                && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getUserInfo() == null;
        } catch (Exception ignored) { return false; }
    }

    private static Button readerButton(Context context, String label, View.OnClickListener action) {
        Button view = button(context, label, action);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 44));
        view.setMinimumHeight(dp(context, 44));
        view.setPadding(dp(context, 6), dp(context, 4), dp(context, 6), dp(context, 4));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -2, 1);
        layout.setMargins(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
        view.setLayoutParams(layout);
        return view;
    }

    public static void showMeihua(final Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { new MeihuaScreen(activity).show(); }
        });
    }

    private static final class MeihuaScreen {
        final Activity activity;
        LinearLayout form, result;
        ScrollView scroll;
        Button timeTab, numberTab, threeTab, characterTab, manualTab, historyTab;
        Calendar selected = Calendar.getInstance(BEIJING, Locale.CHINA);
        boolean dateBoundary23;
        boolean includeNumberHour;
        String first = "", second = "", third = "";
        boolean sumThree;
        String askedQuestion = "";
        EditText questionInput;
        MeihuaScreen(Activity value) { activity = value; }

        void show() {
            LinearLayout root = page(activity, "梅花易数起卦", "时间 / 两数 / 三数 · 取法可核对");
            scroll = (ScrollView) root.getParent();
            homeButton(activity, root);
            LinearLayout questionCard = card(activity);
            questionCard.addView(text(activity, "静心定问 · 一事一问", 16, ACCENT, true));
            questionCard.addView(text(activity, "01 定问", 22, INK, true));
            questionCard.addView(text(activity, "一次围绕一件事，写清你关心的范围。问题会和卦象一起保存。", 15, MUTED, false));
            questionInput = input(activity, "所问事项（必填，最多500字）", false);
            questionInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            questionInput.setMinLines(2); questionInput.setMaxLines(4); questionInput.setGravity(Gravity.TOP);
            questionInput.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(1000)});
            questionInput.setText(askedQuestion);
            final TextView questionCount = text(activity, "0 / 500 字", 13, MUTED, false);
            questionInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                    askedQuestion = value.toString();
                    int size = AiReadingContext.charCount(askedQuestion);
                    questionCount.setText(size + " / 500 字");
                    questionCount.setTextColor(size > 500 ? MOVING : MUTED);
                    if (result != null) result.removeAllViews();
                }
                @Override public void afterTextChanged(Editable editable) { }
            });
            questionCard.addView(questionInput); questionCard.addView(questionCount);
            root.addView(questionCard);
            root.addView(text(activity, "02 起卦", 22, INK, true));
            root.addView(text(activity, "选择一种方法，填写本次所需参数。", 15, MUTED, false));
            LinearLayout modes = new LinearLayout(activity);
            modes.setOrientation(LinearLayout.HORIZONTAL);
            modes.setBaselineAligned(false);
            timeTab = button(activity, "时间起卦", new View.OnClickListener() {
                @Override public void onClick(View view) { timeForm(); }
            });
            numberTab = button(activity, "两数起卦", new View.OnClickListener() {
                @Override public void onClick(View view) { numberForm(); }
            });
            threeTab = button(activity, "三数起卦", new View.OnClickListener() {
                @Override public void onClick(View view) { threeForm(); }
            });
            characterTab = button(activity, "测字起卦", new View.OnClickListener() {
                @Override public void onClick(View view) { characterForm(); }
            });
            manualTab = button(activity, "手动排盘", new View.OnClickListener() {
                @Override public void onClick(View view) { manualForm(); }
            });
            historyTab = button(activity, "卦例笔记", new View.OnClickListener() {
                @Override public void onClick(View view) { showHistory(); }
            });
            addMode(modes, timeTab); addMode(modes, numberTab); addMode(modes, threeTab);
            root.addView(modes);
            LinearLayout moreModes = new LinearLayout(activity); moreModes.setOrientation(LinearLayout.HORIZONTAL);
            addMode(moreModes, characterTab); addMode(moreModes, manualTab);
            root.addView(moreModes);
            root.addView(historyTab);
            form = card(activity); root.addView(form);
            result = column(activity); root.addView(result);
            timeForm();
        }

        void addMode(LinearLayout modes, Button button) {
            styleMode(button);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -2, 1);
            layout.setMargins(dp(activity, 3), dp(activity, 6), dp(activity, 3), dp(activity, 12));
            modes.addView(button, layout);
        }

        void styleMode(Button button) {
            button.setGravity(Gravity.CENTER);
            button.setTextSize(14);
            button.setMinHeight(dp(activity, 56));
            button.setMinimumHeight(dp(activity, 56));
            button.setPadding(dp(activity, 5), dp(activity, 8), dp(activity, 5), dp(activity, 8));
        }

        void selectMode(int selectedMode) {
            JianYiUi.skinButton(timeTab, selectedMode == 0);
            JianYiUi.skinButton(numberTab, selectedMode == 1);
            JianYiUi.skinButton(threeTab, selectedMode == 2);
            JianYiUi.skinButton(characterTab, selectedMode == 3);
            JianYiUi.skinButton(manualTab, selectedMode == 4);
            JianYiUi.skinButton(historyTab, false);
            styleMode(timeTab); styleMode(numberTab); styleMode(threeTab); styleMode(characterTab); styleMode(manualTab);
            timeTab.setSelected(selectedMode == 0); numberTab.setSelected(selectedMode == 1);
            threeTab.setSelected(selectedMode == 2);
            characterTab.setSelected(selectedMode == 3); manualTab.setSelected(selectedMode == 4);
        }

        void characterForm() {
            form.removeAllViews(); result.removeAllViews(); selectMode(3);
            form.addView(text(activity, "测字起卦 · 笔画取数", 22, INK, true));
            form.addView(text(activity, "写下所测文字，再按字形拆成上/左与下/右两部分填写笔画。异体字、繁简字笔画不同，以你实际书写的字形为准。", 16, MUTED, false));
            final EditText word=input(activity,"所测文字",false); final EditText firstPart=input(activity,"上部或左部笔画数",true); final EditText secondPart=input(activity,"下部或右部笔画数",true);
            word.setSingleLine(true); form.addView(word); form.addView(firstPart); form.addView(secondPart);
            form.addView(text(activity,"取法：第一部分除8取上卦，第二部分除8取下卦，两部分笔画总数除6取动爻。余0分别取8和6。",15,MUTED,false));
            form.addView(primaryButton(activity,"生成排盘",new View.OnClickListener(){@Override public void onClick(View v){
                if(!requireQuestion())return;String glyph=word.getText().toString().trim();
                try{if(glyph.length()==0)throw new IllegalArgumentException();BigInteger a=MeihuaEngine.parsePositive(firstPart.getText().toString()),b=MeihuaEngine.parsePositive(secondPart.getText().toString());MeihuaEngine.Result value=MeihuaEngine.numberTwo(a,b);
                    String info="所测文字："+glyph+"\n字形拆分：上/左 "+a+" 画，下/右 "+b+" 画\n上卦："+a+" 除8取 "+value.upper+"（"+MeihuaEngine.trigramName(value.upper)+"）\n下卦："+b+" 除8取 "+value.lower+"（"+MeihuaEngine.trigramName(value.lower)+"）\n动爻：("+a+"+"+b+")除6取第 "+value.moving+" 爻";
                    saveAndRender(value,"测字起卦 · "+glyph,info);revealResult();
                }catch(Exception e){error("请填写所测文字，并输入两部分大于0的整数笔画数。");}
            }}));JianYiUi.enter(form);
        }

        void manualForm() {
            form.removeAllViews(); result.removeAllViews(); selectMode(4);
            form.addView(text(activity,"手动排盘",22,INK,true));
            form.addView(text(activity,"适合已经在纸上或别处起好卦的情况。选择本卦上下卦和动爻，应用只负责排盘、保存与AI解读。",16,MUTED,false));
            final String[] trigrams={"乾 · 天","兑 · 泽","离 · 火","震 · 雷","巽 · 风","坎 · 水","艮 · 山","坤 · 地"};
            final String[] moving={"初爻","二爻","三爻","四爻","五爻","上爻"};
            final Spinner upper=new Spinner(activity),lower=new Spinner(activity),line=new Spinner(activity);
            upper.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,trigrams));lower.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,trigrams));line.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,moving));
            form.addView(text(activity,"上卦",17,INK,true));form.addView(upper);form.addView(text(activity,"下卦",17,INK,true));form.addView(lower);form.addView(text(activity,"动爻",17,INK,true));form.addView(line);
            form.addView(primaryButton(activity,"生成排盘",new View.OnClickListener(){@Override public void onClick(View v){if(!requireQuestion())return;int u=upper.getSelectedItemPosition()+1,l=lower.getSelectedItemPosition()+1,m=line.getSelectedItemPosition()+1;MeihuaEngine.Result value=MeihuaEngine.fromTrigrams(u,l,m);String info="手动录入\n上卦："+MeihuaEngine.trigramName(u)+"\n下卦："+MeihuaEngine.trigramName(l)+"\n动爻：第 "+m+" 爻\n说明：卦象由用户自行起得，应用未重新取数。";saveAndRender(value,"手动排盘",info);revealResult();}}));JianYiUi.enter(form);
        }

        void timeForm() {
            form.removeAllViews();
            result.removeAllViews();
            selectMode(0);
            form.addView(text(activity, "时间起卦 · 北京时间 UTC+8", 22, INK, true));
            form.addView(text(activity, "以农历年、月、日与时支取数。先选定时间，再起一卦。", 17, MUTED, false));
            form.addView(button(activity, "选定时间：" + dateString(selected), new View.OnClickListener() {
                @Override public void onClick(View view) { pickDate(); }
            }));
            form.addView(button(activity, "使用当前北京时间", new View.OnClickListener() {
                @Override public void onClick(View view) { selected = Calendar.getInstance(BEIJING, Locale.CHINA); timeForm(); }
            }));
            final CheckBox boundary = check(activity, "23:00 换日（默认关闭，默认 00:00 换日）", dateBoundary23);
            boundary.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.CompoundButton view, boolean checked) { dateBoundary23 = checked; result.removeAllViews(); }
            });
            form.addView(boundary);
            form.addView(text(activity, "取法：年支、农历月日取上卦，加时支取下卦与动爻；余 0 时卦取坤、爻取上爻。", 15, MUTED, false));
            form.addView(primaryButton(activity, "生成排盘", new View.OnClickListener() {
                @Override public void onClick(View view) { calculateTime(); }
            }));
            JianYiUi.enter(form);
        }

        void pickDate() {
            DatePickerDialog dialog = new DatePickerDialog(activity, new DatePickerDialog.OnDateSetListener() {
                @Override public void onDateSet(DatePicker picker, final int year, final int month, final int day) {
                    showDialog(activity, new TimePickerDialog(activity, new TimePickerDialog.OnTimeSetListener() {
                        @Override public void onTimeSet(TimePicker picker, int hour, int minute) {
                            selected.set(year, month, day, hour, minute, 0);
                            selected.set(Calendar.MILLISECOND, 0);
                            timeForm();
                        }
                    }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), true));
                }
            }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH));
            Calendar lower = Calendar.getInstance(BEIJING); lower.clear(); lower.set(1900, 0, 1);
            Calendar upper = Calendar.getInstance(BEIJING); upper.clear(); upper.set(2100, 11, 31, 23, 59, 59);
            dialog.getDatePicker().setMinDate(lower.getTimeInMillis());
            dialog.getDatePicker().setMaxDate(upper.getTimeInMillis());
            showDialog(activity, dialog);
        }

        void calculateTime() {
            if (!requireQuestion()) return;
            try {
                Calendar numericDate = (Calendar) selected.clone();
                int hour = selected.get(Calendar.HOUR_OF_DAY);
                boolean shifted = dateBoundary23 && hour == 23;
                if (shifted) numericDate.add(Calendar.DAY_OF_MONTH, 1);
                Lunar lunar = Solar.fromYmdHms(numericDate.get(Calendar.YEAR), numericDate.get(Calendar.MONTH) + 1,
                    numericDate.get(Calendar.DAY_OF_MONTH), numericDate.get(Calendar.HOUR_OF_DAY),
                    numericDate.get(Calendar.MINUTE), numericDate.get(Calendar.SECOND)).getLunar();
                int yearBranch = lunar.getYearZhiIndex() + 1;
                int month = Math.abs(lunar.getMonth());
                int day = lunar.getDay();
                int hourBranch = ((hour + 1) / 2) % 12 + 1;
                MeihuaEngine.Result value = MeihuaEngine.time(yearBranch, month, day, hourBranch);
                int dateTotal = yearBranch + month + day;
                int total = dateTotal + hourBranch;
                String info = "北京时间：" + dateString(selected) + "（UTC+8）\n"
                    + "农历取数日期：" + lunar.getYear() + " 年 " + (lunar.getMonth() < 0 ? "闰" : "") + month + " 月 " + day + " 日\n"
                    + "年支：" + BRANCHES[yearBranch] + "（" + yearBranch + "）　时支：" + BRANCHES[hourBranch] + "（" + hourBranch + "）\n"
                    + "换日约定：" + (dateBoundary23 ? "23:00 换日" : "00:00 换日") + (shifted ? "；本次已用次日农历" : "") + "\n"
                    + (lunar.getMonth() < 0 ? "闰月约定：按本月月数 " + month + " 取数\n" : "")
                    + "上卦：（" + yearBranch + "+" + month + "+" + day + "）=" + dateTotal + "；除 8 取余 → " + value.upper + "（" + MeihuaEngine.trigramName(value.upper) + "）\n"
                    + "下卦：" + dateTotal + "+" + hourBranch + "=" + total + "；除 8 取余 → " + value.lower + "（" + MeihuaEngine.trigramName(value.lower) + "）\n"
                    + "动爻：" + total + " 除 6 取余 → 第 " + value.moving + " 爻";
                saveAndRender(value, "时间起卦", info);
                revealResult();
            } catch (Exception error) { error("时间未能计算，请重新选择 1900–2100 年内的日期。"); }
        }

        void numberForm() {
            form.removeAllViews(); result.removeAllViews();
            selectMode(1);
            form.addView(text(activity, "数字起卦 · 两个正整数", 22, INK, true));
            form.addView(text(activity, "第一个数取上卦，第二个数取下卦。两数之和取动爻；可选择再加时支数。每个数最多 128 位。", 18, MUTED, false));
            final EditText a = input(activity, "第一个数（大于 0）", true);
            final EditText b = input(activity, "第二个数（大于 0）", true);
            a.setText(first); b.setText(second);
            a.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { first = s.toString(); result.removeAllViews(); }
                @Override public void afterTextChanged(Editable editable) { }
            });
            b.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { second = s.toString(); result.removeAllViews(); }
                @Override public void afterTextChanged(Editable editable) { }
            });
            form.addView(text(activity, "第一个数 · 上卦", 18, INK, true)); form.addView(a);
            form.addView(text(activity, "第二个数 · 下卦", 18, INK, true)); form.addView(b);
            final CheckBox include = check(activity, "动爻加时支数（默认不加）", includeNumberHour);
            form.addView(include);
            final Calendar numberTime = Calendar.getInstance(BEIJING, Locale.CHINA);
            final int[] hourValue = {((numberTime.get(Calendar.HOUR_OF_DAY) + 1) / 2) % 12 + 1};
            final Button hourButton = button(activity, "时支：" + BRANCHES[hourValue[0]] + "（" + hourValue[0] + "）· 点此更改", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    final String[] options = new String[12];
                    for (int i = 1; i <= 12; i++) options[i - 1] = BRANCHES[i] + "（" + i + "）";
                    showDialog(activity, JianYiUi.dialogBuilder(activity).setTitle("选择用于动爻的时支数").setSingleChoiceItems(options, hourValue[0] - 1,
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                hourValue[0] = which + 1;
                                ((Button) form.findViewWithTag("hour_branch")).setText("时支：" + options[which] + " · 点此更改");
                                result.removeAllViews(); dialog.dismiss();
                            }
                        }).setNegativeButton("返回", null).create());
                }
            });
            hourButton.setTag("hour_branch"); hourButton.setVisibility(includeNumberHour ? View.VISIBLE : View.GONE); form.addView(hourButton);
            include.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.CompoundButton view, boolean checked) {
                    includeNumberHour = checked; hourButton.setVisibility(checked ? View.VISIBLE : View.GONE); result.removeAllViews();
                }
            });
            form.addView(text(activity, "卦数：乾一、兑二、离三、震四、巽五、坎六、艮七、坤八；余 0 时卦取坤、爻取上爻。", 15, MUTED, false));
            form.addView(primaryButton(activity, "生成排盘", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (!requireQuestion()) return;
                    first = a.getText().toString().trim(); second = b.getText().toString().trim();
                    try {
                        BigInteger one = MeihuaEngine.parsePositive(first), two = MeihuaEngine.parsePositive(second);
                        Integer optional = include.isChecked() ? Integer.valueOf(hourValue[0]) : null;
                        MeihuaEngine.Result value = MeihuaEngine.numberTwo(one, two, optional);
                        BigInteger total = one.add(two);
                        if (optional != null) total = total.add(BigInteger.valueOf(optional.intValue()));
                        String info = "第一个数：" + one + "\n第二个数：" + two + "\n"
                            + "上卦：第一个数除 8 取余 → " + value.upper + "（" + MeihuaEngine.trigramName(value.upper) + "）\n"
                            + "下卦：第二个数除 8 取余 → " + value.lower + "（" + MeihuaEngine.trigramName(value.lower) + "）\n"
                            + "动爻：两数之和" + (optional == null ? "（不加时支）" : "+" + BRANCHES[optional.intValue()] + "时支数 " + optional)
                            + "=" + total + "；除 6 取余 → 第 " + value.moving + " 爻";
                        saveAndRender(value, "数字起卦", info); revealResult();
                    } catch (IllegalArgumentException invalid) { error("请输入两个大于 0 的正整数，每个数最多 128 位；不接受小数、负数或符号。"); }
                }
            }));
            JianYiUi.enter(form);
        }

        void threeForm() {
            form.removeAllViews(); result.removeAllViews();
            selectMode(2);
            form.addView(text(activity, "三数起卦 · 明确选择取法", 22, INK, true));
            form.addView(text(activity, "前两个数分别取上卦、下卦。动爻可用第三个数，或用三个数之和；每个数须为正整数，最多 128 位。", 17, MUTED, false));
            final EditText a = input(activity, "三数 · 第一个数（大于 0）", true);
            final EditText b = input(activity, "三数 · 第二个数（大于 0）", true);
            final EditText c = input(activity, "三数 · 第三个数（大于 0）", true);
            a.setText(first); b.setText(second); c.setText(third);
            EditText[] inputs = {a, b, c};
            String[] labels = {"第一个数 · 上卦", "第二个数 · 下卦", "第三个数 · 动爻取数"};
            for (int i = 0; i < inputs.length; i++) {
                final int field = i;
                inputs[i].addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
                    @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                        if (field == 0) first = value.toString();
                        else if (field == 1) second = value.toString();
                        else third = value.toString();
                        result.removeAllViews();
                    }
                    @Override public void afterTextChanged(Editable editable) { }
                });
                form.addView(text(activity, labels[i], 18, INK, true)); form.addView(inputs[i]);
            }
            form.addView(text(activity, "本次动爻取法", 18, INK, true));
            final android.widget.RadioGroup convention = new android.widget.RadioGroup(activity);
            convention.setOrientation(LinearLayout.VERTICAL);
            android.widget.RadioButton direct = new android.widget.RadioButton(activity);
            direct.setId(0x5101); direct.setText("第三数取动爻（默认）");
            android.widget.RadioButton summed = new android.widget.RadioButton(activity);
            summed.setId(0x5102); summed.setText("三数求和取动爻");
            for (android.widget.RadioButton choice : new android.widget.RadioButton[] {direct, summed}) {
                choice.setTextSize(17); choice.setTextColor(INK); choice.setMinHeight(dp(activity, 48));
                if (Build.VERSION.SDK_INT >= 21) choice.setButtonTintList(android.content.res.ColorStateList.valueOf(INK));
                convention.addView(choice);
            }
            convention.check(sumThree ? summed.getId() : direct.getId());
            convention.setOnCheckedChangeListener(new android.widget.RadioGroup.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.RadioGroup group, int id) {
                    sumThree = id == 0x5102; result.removeAllViews();
                }
            });
            form.addView(convention);
            form.addView(text(activity, "取法：前两数分别定上下卦；动爻按你选择的第三数或三数之和计算。余 0 时卦取坤、爻取上爻。", 15, MUTED, false));
            form.addView(primaryButton(activity, "生成排盘", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (!requireQuestion()) return;
                    first = a.getText().toString().trim(); second = b.getText().toString().trim(); third = c.getText().toString().trim();
                    try {
                        BigInteger one = MeihuaEngine.parsePositive(first), two = MeihuaEngine.parsePositive(second), three = MeihuaEngine.parsePositive(third);
                        MeihuaEngine.Result value = MeihuaEngine.numberThree(one, two, three, sumThree);
                        BigInteger total = sumThree ? one.add(two).add(three) : three;
                        String mode = sumThree ? "三数求和取动爻" : "第三数取动爻";
                        String formula = sumThree ? one + "+" + two + "+" + three + "=" + total : three.toString();
                        String info = "三数取法：" + mode + "（不加时支）\n第一个数：" + one + "\n第二个数：" + two + "\n第三个数：" + three + "\n"
                            + "上卦：" + one + " 除 8 取余 → " + value.upper + "（" + MeihuaEngine.trigramName(value.upper) + "）\n"
                            + "下卦：" + two + " 除 8 取余 → " + value.lower + "（" + MeihuaEngine.trigramName(value.lower) + "）\n"
                            + "动爻：" + formula + "；除 6 取余 → 第 " + value.moving + " 爻\n余 0 约定：卦取 8，爻取 6。";
                        saveAndRender(value, "三数起卦 · " + mode, info); revealResult();
                    } catch (IllegalArgumentException invalid) { error("请输入三个大于 0 的正整数，每个数最多 128 位；不接受小数、负数或符号。"); }
                }
            }));
            JianYiUi.enter(form);
        }

        void error(String message) {
            result.removeAllViews();
            LinearLayout notice = card(activity);
            notice.addView(text(activity, "请检查输入", 20, MOVING, true));
            notice.addView(text(activity, message, 17, MOVING, false));
            result.addView(notice);
            revealResult();
        }

        void revealResult() {
            View focus = activity.getCurrentFocus();
            if (focus != null) {
                InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                focus.clearFocus();
            }
            result.setFocusableInTouchMode(true);
            result.requestFocus();
            JianYiUi.enter(result);
            result.post(new Runnable() {
                @Override public void run() {
                    if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())
                        || scroll == null || result.getParent() == null || scroll.getParent() == null) return;
                    int top = Math.max(0, result.getTop() - dp(activity, 16));
                    if (animationsEnabled(activity)) scroll.smoothScrollTo(0, top);
                    else scroll.scrollTo(0, top);
                }
            });
        }

        boolean requireQuestion() {
            askedQuestion = questionInput.getText().toString().trim();
            if (askedQuestion.length() == 0 || AiReadingContext.charCount(askedQuestion) > 500) {
                questionInput.setError("起卦前请填写1至500字的问题");
                questionInput.requestFocus();
                error("请先填写这次所问的事项，最多500字。");
                return false;
            }
            return true;
        }

        void saveAndRender(MeihuaEngine.Result value, String method, String info) {
            String question = askedQuestion.trim();
            View focus = activity.getCurrentFocus();
            if (focus != null) ((android.view.inputmethod.InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(focus.getWindowToken(), 0);
            String savedId = "";
            try {
                NotebookRecord note = NotebookStore.draft(method, info, value.upper, value.lower, value.moving, "");
                note.question = question;
                savedId = NotebookStore.get(activity).save(note).id;
            } catch (Exception failed) { }
            final String id = savedId;
            renderResult(result, value, method, info, question, id);
            if (id.length() > 0) {
                result.addView(button(activity, "查看卦例", new View.OnClickListener() {
                    @Override public void onClick(View view) { NotebookActivity.open(activity, id); }
                }));
                result.addView(text(activity, "已自动保存到卦例。AI 解读和追问也会写入这条记录。", 15, MUTED, false));
            } else {
                result.addView(text(activity, "结果已计算，但卦例保存失败。请先保留本次结果，稍后重试；本次 AI 入口暂不可用。", 18, MOVING, false));
            }
        }

        void showHistory() {
            NotebookActivity.openList(activity);
        }
    }

    /** Shows an existing calculation without adding another notebook entry. */
    public static void showNotebookResult(final Activity activity, int upper, int lower, int moving,
                                          String method, String info) {
        showNotebookResult(activity, upper, lower, moving, method, info, "", "");
    }

    public static void showNotebookResult(final Activity activity, int upper, int lower, int moving,
                                          String method, String info, String noteId, String question) {
        if (activity == null || activity.isFinishing()) return;
        try {
            LinearLayout detail = column(activity);
            detail.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 16));
            renderResult(detail, MeihuaEngine.fromTrigrams(upper, lower, moving),
                method == null ? "卦例" : method, info == null ? "" : info,
                question == null ? "" : question, noteId == null ? "" : noteId);
            final ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            scroll.setFocusableInTouchMode(true);
            scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            scroll.addView(detail);
            AlertDialog dialog = JianYiUi.dialogBuilder(activity).setTitle("卦象与解析").setView(scroll)
                .setPositiveButton("返回笔记", null).create();
            showDialog(activity, dialog);
            scroll.requestFocus();
            scroll.post(new Runnable() { @Override public void run() { scroll.scrollTo(0, 0); } });
        } catch (RuntimeException invalid) {
            Toast.makeText(activity, "卦例数据无效，暂时无法显示卦象。", Toast.LENGTH_LONG).show();
        }
    }

    /** Offline Zhouyi reading for the independent yarrow-stalk method; no Meihua body/use is applied. */
    public static void showDayanResult(final Activity activity, int upper, int lower, int[] lineValues,
                                       final String noteId, final String question) {
        if (activity == null || activity.isFinishing() || lineValues == null || lineValues.length != 6) return;
        try {
            final int[] values = lineValues.clone();
            int[] baseBits = new int[6], changedBits = new int[6];
            int firstMoving = 0;
            StringBuilder movingNames = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                baseBits[i] = values[i] == 7 || values[i] == 9 ? 1 : 0;
                changedBits[i] = baseBits[i];
                if (values[i] == 6 || values[i] == 9) {
                    changedBits[i] ^= 1;
                    if (firstMoving == 0) firstMoving = i + 1;
                    if (movingNames.length() > 0) movingNames.append('、');
                    movingNames.append(i + 1).append("爻");
                }
            }
            StringBuilder baseCodeBuilder = new StringBuilder(), changedCodeBuilder = new StringBuilder();
            for (int bit : baseBits) baseCodeBuilder.append(bit);
            for (int bit : changedBits) changedCodeBuilder.append(bit);
            final String baseCode = baseCodeBuilder.toString(), changedCode = changedCodeBuilder.toString();
            final int aiMoving = firstMoving == 0 ? 1 : firstMoving;
            LinearLayout detail = column(activity); detail.setPadding(dp(activity,16),dp(activity,8),dp(activity,16),dp(activity,20));
            detail.addView(text(activity,"大衍筮法 · 离线卦解",18,ACCENT,true));
            if (question != null && question.trim().length() > 0) {
                LinearLayout asked=card(activity); asked.addView(text(activity,"所问事项",19,INK,true));
                asked.addView(text(activity,question.trim(),17,INK,false)); detail.addView(asked);
            }
            LinearLayout base=card(activity); detail.addView(base);
            base.addView(text(activity,"本卦 · "+hexName(activity,baseCode),26,INK,true));
            base.addView(text(activity,"动爻："+(movingNames.length()==0?"无":movingNames.toString())+"。以下依据《周易》卦辞、爻辞阅读，不使用梅花体用。",16,MUTED,false));
            base.addView(new LinesView(activity,baseBits,0),new LinearLayout.LayoutParams(-1,dp(activity,190)));
            addResultClassic(activity,base,baseCode,firstMoving,true);
            Hexagram baseEntry=hexForCode(activity,baseCode);
            for(int i=0;i<6;i++) if(values[i]==6||values[i]==9) addDetailedReadingButton(activity,base,baseEntry,i+1);
            addFullReadingButton(activity,base,baseCode,"阅读本卦全文");
            LinearLayout changed=card(activity); detail.addView(changed);
            changed.addView(text(activity,"变卦 · "+hexName(activity,changedCode),23,INK,true));
            changed.addView(text(activity,movingNames.length()==0?"本次没有动爻，本卦不变。":"将全部老阴、老阳动爻反转后得到。",16,MUTED,false));
            changed.addView(new LinesView(activity,changedBits,0),new LinearLayout.LayoutParams(-1,dp(activity,190)));
            addResultClassic(activity,changed,changedCode,0,false); addFullReadingButton(activity,changed,changedCode,"阅读变卦全文");
            if(noteId!=null&&noteId.length()>0) detail.addView(primaryButton(activity,"AI 解卦 · 200字内（需联网）",new View.OnClickListener(){public void onClick(View v){AiReadingActivity.launch(activity,upper,lower,aiMoving,AiReadingContext.dayanMethod(values),"note:"+noteId,question);}}));
            final ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.addView(detail);
            showDialog(activity,JianYiUi.dialogBuilder(activity).setTitle("大衍卦解").setView(scroll).setPositiveButton("返回结果",null).create());
            scroll.post(new Runnable(){public void run(){scroll.scrollTo(0,0);}});
        } catch (Exception invalid) { Toast.makeText(activity,"卦解数据未能载入，请从卦例本重试。",Toast.LENGTH_LONG).show(); }
    }

    private static void renderResult(LinearLayout target, final MeihuaEngine.Result value, String method, String info,
                                     final String question, final String noteId) {
        final Activity activity = (Activity) target.getContext();
        target.removeAllViews();
        target.addView(text(activity, method + " · 已生成", 16, ACCENT, true));
        if (question != null && question.length() > 0) {
            LinearLayout asked = card(activity);
            asked.addView(text(activity, "所问事项", 19, INK, true));
            asked.addView(text(activity, question, 17, INK, false));
            target.addView(asked);
        }
        target.addView(text(activity, "03 排盘", 22, INK, true));
        LinearLayout overview = card(activity);
        overview.addView(text(activity, "本卦 · 互卦 · 变卦", 19, INK, true));
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setBaselineAligned(false);
        addMiniHex(activity, row, "本卦", value.baseCode, value.baseBits(), value.moving);
        addMiniHex(activity, row, "互卦", value.mutualCode, value.mutualBits(), 0);
        addMiniHex(activity, row, "变卦", value.changedCode, value.changedBits(), 0);
        overview.addView(row);
        overview.addView(text(activity, "第 " + value.moving + " 爻动（由下向上数） · 本卦上 "
            + MeihuaEngine.trigramName(value.upper) + " 下 " + MeihuaEngine.trigramName(value.lower)
            + " · 变卦上 " + MeihuaEngine.trigramName(value.changedUpper) + " 下 "
            + MeihuaEngine.trigramName(value.changedLower), 16, MUTED, false));
        target.addView(overview);
        addLearningEntry(activity, target, value);

        final String evidenceMethod = method == null ? "梅花易数" : method;
        final String evidenceInfo = info == null ? "" : info;
        target.addView(button(activity, "查看详细依据", new View.OnClickListener() {
            @Override public void onClick(View view) { showMeihuaEvidence(activity, value, evidenceMethod, evidenceInfo); }
        }));

        target.addView(text(activity, "04 解读", 22, INK, true));
        target.addView(text(activity, "排盘与本机依据可离线查看；AI 解读仅在你主动点击时联网。", 15, MUTED, false));
        final String aiMethod = method;
        if (noteId != null && noteId.length() > 0) target.addView(primaryButton(activity, "AI 解卦（需联网）", new View.OnClickListener() {
            @Override public void onClick(View view) {
                AiReadingActivity.launch(activity, value.upper, value.lower, value.moving, aiMethod, "note:" + noteId, question);
            }
        }));
    }

    private static void addMiniHex(Activity activity, LinearLayout row, String label, String code, int[] lines, int moving) {
        LinearLayout item = column(activity);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(dp(activity, 5), dp(activity, 7), dp(activity, 5), dp(activity, 8));
        item.setBackground(round(activity, PAPER, 14, LINE));
        TextView heading = text(activity, label, 14, ACCENT, true); heading.setGravity(Gravity.CENTER);
        TextView name = text(activity, hexName(activity, code), 15, INK, true); name.setGravity(Gravity.CENTER); name.setMaxLines(2);
        item.addView(heading); item.addView(name);
        item.addView(new MiniLinesView(activity, lines, moving), new LinearLayout.LayoutParams(-1, dp(activity, 112)));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -2, 1);
        layout.setMargins(dp(activity, 3), dp(activity, 5), dp(activity, 3), dp(activity, 5));
        row.addView(item, layout);
    }

    private static void addLearningEntry(final Activity activity, LinearLayout target, final MeihuaEngine.Result value) {
        LinearLayout entry = card(activity); target.addView(entry);
        entry.addView(text(activity, "核心判断", 21, INK, true));
        entry.addView(text(activity, "体 · " + MeihuaEngine.trigramName(value.bodyTrigram) + "（" + ResultLearning.element(value.bodyTrigram)
            + "）　用 · " + MeihuaEngine.trigramName(value.useTrigram) + "（" + ResultLearning.element(value.useTrigram) + "）", 18, ACCENT, true));
        entry.addView(text(activity, ResultLearning.bodyUseRelation(value.bodyTrigram, value.useTrigram), 19, INK, true));
        entry.addView(text(activity, "第 " + value.moving + " 爻在" + (value.moving <= 3 ? "下卦" : "上卦")
            + "，动爻所在卦为用，另一卦为体。此处只呈现可核对的体用五行关系。", 15, MUTED, false));
    }

    private static void showMeihuaEvidence(final Activity activity, final MeihuaEngine.Result value,
                                           String method, String info) {
        LinearLayout content = column(activity);
        content.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 12));

        LinearLayout calculation = card(activity); content.addView(calculation);
        calculation.addView(text(activity, "取数与计算 · " + method, 21, INK, true));
        calculation.addView(text(activity, info.length() == 0 ? "本次未记录取数过程。" : info, 16, INK, false));
        calculation.addView(text(activity, "体用规则：动爻所在的三爻卦为用，另一卦为体。五行配卦为乾兑金、离火、震巽木、坎水、艮坤土。", 15, MUTED, false));
        calculation.addView(button(activity, "演示成卦与变爻", new View.OnClickListener() {
            @Override public void onClick(View view) { showFormation(activity, value); }
        }));

        LinearLayout base = card(activity); content.addView(base);
        base.addView(text(activity, "本卦 · " + hexName(activity, value.baseCode), 23, INK, true));
        base.addView(new LinesView(activity, value.baseBits(), value.moving), new LinearLayout.LayoutParams(-1, dp(activity, 190)));
        addResultClassic(activity, base, value.baseCode, value.moving, true);
        addDetailedReadingButton(activity, base, hexForCode(activity, value.baseCode), value.moving);
        addReadingButton(activity, base, value.baseCode, "查看本卦资料");
        addFullReadingButton(activity, base, value.baseCode, "本卦解析");

        LinearLayout mutual = card(activity); content.addView(mutual);
        mutual.addView(text(activity, "互卦 · " + hexName(activity, value.mutualCode), 22, INK, true));
        mutual.addView(text(activity, "二三四爻成下卦，三四五爻成上卦。", 15, MUTED, false));
        mutual.addView(new LinesView(activity, value.mutualBits(), 0), new LinearLayout.LayoutParams(-1, dp(activity, 190)));
        addResultClassic(activity, mutual, value.mutualCode, 0, false);
        addFullReadingButton(activity, mutual, value.mutualCode, "互卦解析");

        LinearLayout changed = card(activity); content.addView(changed);
        changed.addView(text(activity, "变卦 · " + hexName(activity, value.changedCode), 22, INK, true));
        changed.addView(text(activity, "本卦第 " + value.moving + " 爻阴阳反转，其余五爻不变。", 15, MUTED, false));
        changed.addView(new LinesView(activity, value.changedBits(), 0), new LinearLayout.LayoutParams(-1, dp(activity, 190)));
        addResultClassic(activity, changed, value.changedCode, 0, false);
        addDetailedReadingButton(activity, changed, hexForCode(activity, value.changedCode), 0);
        addReadingButton(activity, changed, value.changedCode, "查看变卦资料");
        addFullReadingButton(activity, changed, value.changedCode, "变卦解析");

        LinearLayout references = card(activity); content.addView(references);
        references.addView(text(activity, "继续核对", 20, INK, true));
        references.addView(text(activity, "卦德：上卦" + MeihuaEngine.trigramName(value.upper) + "为“" + ResultLearning.virtue(value.upper)
            + "”；下卦" + MeihuaEngine.trigramName(value.lower) + "为“" + ResultLearning.virtue(value.lower) + "”。", 16, MUTED, false));
        references.addView(button(activity, "学习体用与五行", new View.OnClickListener() {
            @Override public void onClick(View view) { MeihuaReferenceActivity.open(activity, "体用"); }
        }));
        references.addView(button(activity, "查阅类象与分类占法", new View.OnClickListener() {
            @Override public void onClick(View view) { MeihuaReferenceActivity.open(activity, "类象"); }
        }));

        final ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true);
        scroll.setFocusableInTouchMode(true); scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        scroll.addView(content);
        showDialog(activity, JianYiUi.dialogBuilder(activity).setTitle("详细依据").setView(scroll)
            .setPositiveButton("返回结果", null).create());
        scroll.requestFocus(); scroll.post(new Runnable() { @Override public void run() { scroll.scrollTo(0, 0); } });
    }

    private static void showResultLearning(Activity activity, MeihuaEngine.Result value) {
        LinearLayout content = column(activity);
        content.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 8));
        addResultLearning(activity, content, value);
        final ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true);
        scroll.setFocusableInTouchMode(true); scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); scroll.addView(content);
        showDialog(activity, JianYiUi.dialogBuilder(activity).setTitle("起卦学习解析").setView(scroll)
            .setPositiveButton("返回结果", null).create());
        scroll.requestFocus(); scroll.post(new Runnable() { @Override public void run() { scroll.scrollTo(0, 0); } });
    }

    private static void addResultLearning(final Activity activity, LinearLayout target, final MeihuaEngine.Result value) {
        final String bodyElement = ResultLearning.element(value.bodyTrigram), useElement = ResultLearning.element(value.useTrigram);
        LinearLayout learning = card(activity); target.addView(learning);
        learning.addView(text(activity, "学习解析", 23, INK, true));
        learning.addView(text(activity, "体 " + MeihuaEngine.trigramName(value.bodyTrigram) + bodyElement + " · 用 "
            + MeihuaEngine.trigramName(value.useTrigram) + useElement + "\n" + ResultLearning.bodyUseRelation(value.bodyTrigram, value.useTrigram), 19, ACCENT, true));
        learning.addView(text(activity, "先核对取数与爻位，再循体用、卦德与经文理解卦象。", 16, MUTED, false));
        addExpandable(activity, learning, "体用如何分 · 五行关系依据", "六爻由下向上数。第 " + value.moving + " 爻在" + (value.moving <= 3 ? "下卦" : "上卦")
            + "，因此" + MeihuaEngine.trigramName(value.useTrigram) + "为用，另一组三爻的" + MeihuaEngine.trigramName(value.bodyTrigram) + "为体。\n\n"
            + "五行配卦：乾兑金、离火、震巽木、坎水、艮坤土。\n相生：木→火→土→金→水→木。\n相克：木→土→水→火→金→木。\n\n"
            + "本次体属" + bodyElement + "，用属" + useElement + "，按上述关系得到：" + ResultLearning.bodyUseRelation(value.bodyTrigram, value.useTrigram)
            + "。这是可核对的五行关系，尚未包含月令、外应和所问事项，也不会自动换算成吉凶分数。" );
        addExpandable(activity, learning, "八卦卦德 · 从本卦入手", "上卦 " + MeihuaEngine.trigramName(value.upper) + "：" + ResultLearning.virtue(value.upper)
            + "\n下卦 " + MeihuaEngine.trigramName(value.lower) + "：" + ResultLearning.virtue(value.lower)
            + "\n\n八卦卦德：乾健、坤顺、震动、巽入、坎陷、离丽、艮止、兑悦，取自《说卦》。这些是理解卦象的线索，须结合全卦与爻辞阅读。" );
        addTransformation(activity, learning, value.baseCode, false);
        addTransformation(activity, learning, value.baseCode, true);
        learning.addView(button(activity, "学习体用与五行", new View.OnClickListener() {
            @Override public void onClick(View view) { MeihuaReferenceActivity.open(activity, "体用"); }
        }));
        learning.addView(button(activity, "查阅类象与分类占法", new View.OnClickListener() {
            @Override public void onClick(View view) { MeihuaReferenceActivity.open(activity, "类象"); }
        }));
    }

    private static void addTransformation(final Activity activity, LinearLayout target, final String baseCode, final boolean reversed) {
        final String transformed = reversed ? ResultLearning.reversedCode(baseCode) : ResultLearning.oppositeCode(baseCode);
        final String label = reversed ? "综卦" : "错卦";
        addDetailDisclosure(activity, target, label + " · " + hexName(activity, transformed), new DetailContent() {
            @Override public void build(LinearLayout detail) {
                detail.addView(detailText(activity, reversed ? "把六个爻位整体倒序：初爻与上爻互换，二与五互换，三与四互换。阴阳本身不反转。"
                    : "保持六个爻位不动，逐爻将阳变阴、阴变阳。", 17, MUTED, false));
                detail.addView(new LinesView(activity, bits(transformed), 0), new LinearLayout.LayoutParams(-1, dp(activity, 190)));
                if (transformed.equals(baseCode)) detail.addView(detailText(activity, "本卦倒序后与原卦相同。", 16, MUTED, false));
                addFullReadingButton(activity, detail, transformed, label + "解析");
                detail.addView(button(activity, "学习错综卦关系", new View.OnClickListener() {
                    @Override public void onClick(View view) { MeihuaReferenceActivity.open(activity, "错卦"); }
                }));
            }
        });
    }

    private static void showFormation(Activity activity, MeihuaEngine.Result value) {
        LinearLayout content = column(activity);
        content.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 16));
        content.addView(text(activity, "六爻由下向上组成，本次第 " + value.moving + " 爻发动。", 17, MUTED, false));
        content.addView(text(activity, "本卦 · " + hexName(activity, value.baseCode), 21, INK, true));
        content.addView(new LinesView(activity, value.baseBits(), value.moving, true, null), new LinearLayout.LayoutParams(-1, dp(activity, 190)));
        content.addView(text(activity, "变卦 · " + hexName(activity, value.changedCode), 21, INK, true));
        content.addView(new LinesView(activity, value.changedBits(), value.moving, true, value.baseBits()), new LinearLayout.LayoutParams(-1, dp(activity, 190)));
        content.addView(text(activity, "只反转动爻的阴阳，其余五爻保持原样。", 17, MUTED, false));
        if (!animationsEnabled(activity)) content.addView(text(activity, "当前已关闭动画，展示成卦与变爻后的卦图。", 14, MUTED, false));
        final ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true);
        scroll.setFocusableInTouchMode(true); scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); scroll.addView(content);
        showDialog(activity, JianYiUi.dialogBuilder(activity).setTitle("成卦与变爻").setView(scroll).setPositiveButton("返回结果", null).create());
        scroll.requestFocus(); scroll.post(new Runnable() { @Override public void run() { scroll.scrollTo(0, 0); } });
    }

    private static JSONObject classicForNumber(Context context, int number) throws Exception {
        JSONArray entries = readStudyAsset(context, "appdata/zhouyi-classics.json").getJSONArray("hexagrams");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item != null && item.optInt("number", 0) == number) return item;
        }
        return null;
    }

    private static void addResultClassic(Activity activity, LinearLayout target, String code, int moving, boolean expanded) {
        Hexagram entry = hexForCode(activity, code);
        if (entry == null) return;
        try {
            JSONObject classic = classicForNumber(activity, entry.number);
            if (classic == null) throw new IllegalArgumentException("missing classic");
            addStudySection(activity, target, "卦辞 · 底本繁体", firstValue(classic, "judgment", "gua_ci", "guaci"));
            if (moving > 0) {
                JSONArray lines = classic.optJSONArray("lines");
                Object selected = null;
                if (lines != null) {
                    for (int i = 0; i < lines.length(); i++) {
                        JSONObject line = lines.optJSONObject(i);
                        if (line != null && line.optInt("position", i + 1) == moving) { selected = line; break; }
                        if (line == null && i + 1 == moving) { selected = lines.opt(i); break; }
                    }
                }
                if (selected instanceof JSONObject) {
                    JSONObject line = (JSONObject) selected;
                    String label = line.optString("label", lineLabel(moving, entry));
                    addStudySection(activity, target, "动爻 · 第 " + moving + " 爻 · " + label, firstValue(line, "text", "content"));
                    addStudySection(activity, target, "动爻小象", firstValue(line, "xiang", "image"));
                } else if (selected != null) {
                    addStudySection(activity, target, "动爻 · 第 " + moving + " 爻 · " + lineLabel(moving, entry), selected);
                } else target.addView(text(activity, "动爻经文暂未载入，请从本卦全文按初爻至上爻核对。", 18, MUTED, false));
            }
            if (expanded) {
                String tuan = studyText(firstValue(classic, "tuan", "tuan_text"));
                String image = studyText(firstValue(classic, "image", "xiang", "great_image"));
                addExpandable(activity, target, "本卦彖传与大象", (tuan.length() == 0 ? "" : "彖传\n" + tuan)
                    + (image.length() == 0 ? "" : "\n\n大象\n" + image));
            }
        } catch (Exception ignored) {
            target.addView(text(activity, "经文未能载入。起卦结果已计算，可从资料按钮查看本卦资料。", 18, MUTED, false));
        }
        addZhouyiSummary(activity, target, entry.number);
    }

    private static void addZhouyiSummary(Activity activity, LinearLayout target, int number) {
        try {
            JSONObject root = readStudyAsset(activity, "appdata/zhouyi-summaries.json");
            Object collection = firstValue(root, "hexagrams", "summaries", "entries");
            if (!(collection instanceof JSONArray)) return;
            JSONArray records = (JSONArray) collection;
            for (int i = 0; i < records.length(); i++) {
                JSONObject summary = records.optJSONObject(i);
                if (summary == null || summary.optInt("number", 0) != number) continue;
                String prose = studyText(firstValue(summary, "summary", "summary_paragraphs", "text", "sections", "points"));
                if ("missing".equals(summary.optString("status")) || prose.length() == 0 || prose.length() > 1200) return;
                String source = studyText(firstValue(summary, "source_url", "url"));
                String notice = root.optString("notice", "简短学习摘要，非网站详解全文。");
                String issues = studyMetadata(summary.opt("source_issues"));
                String details = prose + "\n\n" + notice + (source.length() == 0 ? "" : "\n\n来源：" + source)
                    + (issues.length() == 0 ? "" : "\n\n来源校记\n" + issues);
                addExpandable(activity, target, "易安居学习摘要", details);
                return;
            }
        } catch (Exception ignored) { /* Optional modern summaries do not affect classic text or calculations. */ }
    }

    private static Hexagram hexForCode(Context context, String code) {
        try { for (Hexagram entry : loadCatalog(context)) if (entry.code.equals(code)) return entry; }
        catch (Exception ignored) { }
        return null;
    }
    private static String hexName(Context context, String code) {
        Hexagram entry = hexForCode(context, code);
        return entry == null ? "上 " + MeihuaEngine.trigramName(MeihuaEngine.trigramId(new int[] {code.charAt(3)-'0', code.charAt(4)-'0', code.charAt(5)-'0'}))
            + " 下 " + MeihuaEngine.trigramName(MeihuaEngine.trigramId(new int[] {code.charAt(0)-'0', code.charAt(1)-'0', code.charAt(2)-'0'})) : entry.fullName;
    }
    private static void addReadingButton(final Activity activity, LinearLayout target, String code, String label) {
        final Hexagram entry = hexForCode(activity, code);
        if (entry == null) { target.addView(text(activity, "目录未载入，暂不能关联该卦资料。", 18, MUTED, false)); return; }
        target.addView(button(activity, label + " · " + entry.shortName, new View.OnClickListener() {
            @Override public void onClick(View view) { showHexagram(activity, entry); }
        }));
    }
    private static void addDetailedReadingButton(final Activity activity, LinearLayout target, final Hexagram entry, final int moving) {
        if (entry == null) return;
        target.addView(primaryButton(activity, "易安居详细解析 · " + entry.shortName + (moving > 0 ? "（" + lineLabel(moving, entry) + "）" : ""), new View.OnClickListener() {
            @Override public void onClick(View view) { showDetailedHexagram(activity, entry, moving); }
        }));
    }
    private static void addFullReadingButton(final Activity activity, LinearLayout target, String code, String label) {
        final Hexagram entry = hexForCode(activity, code);
        if (entry == null) return;
        target.addView(button(activity, label + " · " + entry.shortName, new View.OnClickListener() {
            @Override public void onClick(View view) { showOfflineHexagram(activity, entry); }
        }));
    }
    private static SharedPreferences preferences(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static JSONArray history(Context context) {
        try { return new JSONArray(preferences(context).getString("entries", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    private static String dateString(Calendar value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        format.setTimeZone(BEIJING); return format.format(value.getTime());
    }
    private static int[] bits(String code) {
        int[] result = new int[code.length()];
        for (int i = 0; i < result.length; i++) result[i] = code.charAt(i) == '1' ? 1 : 0;
        return result;
    }
    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static LinearLayout column(Context context) {
        LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private static GradientDrawable round(Context context, int color, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color); background.setCornerRadius(dp(context, radius));
        if (stroke != 0) background.setStroke(dp(context, 1), stroke);
        return background;
    }
    private static LinearLayout card(Context context) {
        LinearLayout view = column(context);
        view.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 16));
        view.setBackground(round(context, 0xf2ffffff, 20, LINE));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, dp(context, 8), 0, dp(context, 12));
        view.setLayoutParams(layout);
        return view;
    }
    private static void showDialog(Activity activity, AlertDialog dialog) {
        JianYiUi.showDialog(activity, dialog);
    }
    private static LinearLayout page(Activity activity, String title, String subtitle) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFitsSystemWindows(true);
        scroll.setFillViewport(true); JianYiUi.applyScenic(scroll,JianYiUi.SCENE_READING);
        LinearLayout root = column(activity);
        root.setPadding(dp(activity, 18), dp(activity, 12), dp(activity, 18), dp(activity, 32));
        scroll.addView(root);
        activity.setContentView(scroll);
        JianYiUi.applySystemBars(activity);
        root.addView(JianYiUi.hero(activity, title, subtitle));
        JianYiUi.enter(root);
        return root;
    }
    private static void homeButton(final Activity activity, LinearLayout root) {
        root.addView(button(activity, "返回首页", new View.OnClickListener() {
            @Override public void onClick(View view) { JianYiHome.render(activity); }
        }));
    }
    private static TextView text(Context context, String content, int size, int color, boolean bold) {
        TextView view = new TextView(context); view.setText(content); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(context, bold ? 10 : 6), 0, dp(context, 6));
        view.setLineSpacing(dp(context, 4), 1.08f);
        view.setTypeface(Typeface.create(bold ? "serif" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setTextIsSelectable(true); return view;
    }
    private static Button button(Context context, String label, View.OnClickListener action) {
        Button view = new Button(context); view.setText(label); view.setTextSize(17); view.setTextColor(INK); view.setAllCaps(false);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START); view.setMinHeight(dp(context, 52));
        view.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        JianYiUi.skinButton(view, false);
        view.setTextSize(17);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setMinHeight(dp(context, 52));
        view.setMinimumHeight(dp(context, 52));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2); layout.setMargins(0, dp(context, 6), 0, dp(context, 6)); view.setLayoutParams(layout);
        view.setOnClickListener(action); return view;
    }
    private static Button primaryButton(Context context, String label, View.OnClickListener action) {
        Button button = button(context, label, action);
        JianYiUi.skinButton(button, true);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return button;
    }
    private static EditText input(Context context, String hint, boolean numeric) {
        EditText view = new EditText(context); view.setHint(hint); view.setTextSize(18); view.setTextColor(INK); view.setHintTextColor(MUTED);
        view.setMinHeight(dp(context, 56)); view.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        JianYiUi.styleInput(view);
        if (numeric) {
            // Offer a number keyboard without silently stripping pasted signs or punctuation.
            view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            view.setRawInputType(InputType.TYPE_CLASS_NUMBER);
            // An overlong paste stays invalid, rather than becoming a different valid 128-digit number.
            view.setFilters(new InputFilter[] {new InputFilter.LengthFilter(MeihuaEngine.MAX_NUMBER_DIGITS + 1)});
        } else view.setInputType(InputType.TYPE_CLASS_TEXT);
        view.setContentDescription(hint); return view;
    }
    private static CheckBox check(Context context, String label, boolean checked) {
        CheckBox view = new CheckBox(context); view.setText(label); view.setTextSize(16); view.setTextColor(INK);
        if (Build.VERSION.SDK_INT >= 21) view.setButtonTintList(android.content.res.ColorStateList.valueOf(INK));
        view.setMinHeight(dp(context, 52)); view.setPadding(0, dp(context, 8), 0, dp(context, 8)); view.setChecked(checked); return view;
    }

    private static boolean animationsEnabled(Context context) {
        return JianYiUi.animationsEnabled(context);
    }

    /** Compact, label-free diagram used by the three-part result overview. */
    private static final class MiniLinesView extends View {
        final int[] bottomFirst;
        final int moving;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        MiniLinesView(Context context, int[] bits, int movingLine) {
            super(context);
            bottomFirst = bits.clone(); moving = movingLine;
            setBackground(round(context, WHITE, 10, 0));
            setMinimumHeight(dp(context, 112));
            StringBuilder description = new StringBuilder("卦图：");
            for (int i = 0; i < bottomFirst.length; i++) {
                description.append("第").append(i + 1).append("爻").append(bottomFirst[i] == 1 ? "阳" : "阴");
                if (i + 1 == moving) description.append("动");
                description.append('；');
            }
            setContentDescription(description.toString());
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float left = 10 * density, right = getWidth() - 10 * density;
            float spacing = getHeight() / 7f;
            paint.setStrokeWidth(4.5f * density); paint.setStrokeCap(Paint.Cap.ROUND);
            for (int row = 0; row < bottomFirst.length; row++) {
                int index = bottomFirst.length - 1 - row;
                float y = spacing * (row + 1);
                paint.setColor(index + 1 == moving ? MOVING : INK);
                if (bottomFirst[index] == 1) canvas.drawLine(left, y, right, y, paint);
                else {
                    float middle = (left + right) / 2f, gap = Math.max(5 * density, (right - left) * .12f);
                    canvas.drawLine(left, y, middle - gap, y, paint);
                    canvas.drawLine(middle + gap, y, right, y, paint);
                }
            }
        }
    }

    /** Paint order is top-first; data, narration and finite entrance order are bottom-first. */
    private static final class LinesView extends View {
        final int[] bottomFirst, transitionFrom;
        final int moving;
        final boolean animate;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF highlight = new RectF();
        ValueAnimator animator;
        float progress = 1f;
        boolean played;
        LinesView(Context context, int[] bits, int line) { this(context, bits, line, false, null); }
        LinesView(Context context, int[] bits, int line, boolean animate, int[] transitionFrom) {
            super(context); bottomFirst = bits.clone(); moving = line; this.animate = animate;
            this.transitionFrom = transitionFrom == null ? null : transitionFrom.clone();
            setBackground(round(context, PAPER, 14, 0)); setMinimumHeight(dp(context, 190));
            StringBuilder description = new StringBuilder("卦图，从初爻到上爻：");
            for (int i = 0; i < bottomFirst.length; i++) description.append("第 ").append(i + 1).append(" 爻 ").append(bottomFirst[i] == 1 ? "阳" : "阴").append(i + 1 == moving ? "，动爻" : "").append("；");
            setContentDescription(description.toString()); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!played && animate && animationsEnabled(getContext())) {
                played = true; progress = 0f;
                animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(1400L); animator.setStartDelay(100L);
                animator.setInterpolator(new android.view.animation.LinearInterpolator());
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator value) {
                        if (!animationsEnabled(getContext())) { finishAnimation(); return; }
                        progress = ((Float) value.getAnimatedValue()).floatValue(); invalidate();
                    }
                });
                animator.start();
            } else { played = true; progress = 1f; invalidate(); }
        }
        private void finishAnimation() {
            ValueAnimator running = animator; animator = null;
            if (running != null) { running.removeAllUpdateListeners(); running.cancel(); }
            progress = 1f; invalidate();
        }
        @Override protected void onDetachedFromWindow() { finishAnimation(); super.onDetachedFromWindow(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            paint.setTextSize(18 * getResources().getDisplayMetrics().scaledDensity);
            float labelWidth = paint.measureText("动 第6爻") + 24 * density;
            float left = 26 * density, right = getWidth() - Math.max(116 * density, labelWidth);
            if (right < left + 70 * density) right = getWidth() * .62f;
            float width = right - left, spacing = getHeight() / 7f;
            for (int row = 0; row < bottomFirst.length; row++) {
                int index = bottomFirst.length - 1 - row;
                float phase = Math.max(0f, Math.min(1f, progress / .68f * 6f - index));
                float y = spacing * (row + 1);
                boolean movingLine = index + 1 == moving;
                if (movingLine) {
                    float glow = progress < .68f ? 0f : (float) Math.sin((progress - .68f) / .32f * Math.PI);
                    paint.setColor(ACCENT); paint.setAlpha(Math.round(18 + 32 * glow)); paint.setStyle(Paint.Style.FILL);
                    highlight.set(left - 12 * density, y - 13 * density, getWidth() - 12 * density, y + 13 * density);
                    canvas.drawRoundRect(highlight, 10 * density, 10 * density, paint);
                }
                paint.setColor(movingLine ? ACCENT : INK); paint.setAlpha(Math.round(255 * phase));
                paint.setStrokeWidth(8 * density); paint.setStrokeCap(Paint.Cap.ROUND);
                float drawnWidth = width * (.65f + .35f * phase);
                float drawnLeft = left + (width - drawnWidth) / 2f, drawnRight = drawnLeft + drawnWidth;
                float gap = bottomFirst[index] == 1 ? 0f : .16f;
                if (transitionFrom != null && transitionFrom[index] != bottomFirst[index]) {
                    float change = Math.max(0f, Math.min(1f, (progress - .68f) / .32f));
                    float previousGap = transitionFrom[index] == 1 ? 0f : .16f;
                    gap = previousGap + (gap - previousGap) * change;
                }
                if (gap <= .001f) canvas.drawLine(drawnLeft, y, drawnRight, y, paint);
                else {
                    float middle = (drawnLeft + drawnRight) / 2f;
                    canvas.drawLine(drawnLeft, y, middle - drawnWidth * gap / 2f, y, paint);
                    canvas.drawLine(middle + drawnWidth * gap / 2f, y, drawnRight, y, paint);
                }
                paint.setTextSize(16 * getResources().getDisplayMetrics().scaledDensity); paint.setTypeface(Typeface.DEFAULT);
                canvas.drawText((movingLine ? "动 " : "") + "第" + (index + 1) + "爻", right + 12 * density, y + 6 * density, paint);
            }
            paint.setAlpha(255);
        }
    }
}
