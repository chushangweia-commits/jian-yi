package com.jianyi.yixue.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.Charset;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/** Versioned UTF-8 backup; complete validation precedes any database mutation. */
public final class BackupCodec {
    public static final String FORMAT = "jianyi-meihua-notebook";
    public static final int VERSION = 3, MAX_RECORDS = 10000, MAX_SESSIONS = 10000, MAX_BYTES = 8 * 1024 * 1024;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long MAX_TIME = 253402300799999L;
    private BackupCodec() { }

    /** List-compatible carrier keeps old import callers source-compatible without dropping AI data. */
    public static final class Bundle extends ArrayList<NotebookRecord> {
        public final List<AiConversationRecord> sessions = new ArrayList<AiConversationRecord>();
        public final List<JSONObject> baziCharts = new ArrayList<JSONObject>();
    }

    public static byte[] encode(List<NotebookRecord> records, long exportedAt) throws Exception {
        if (records == null || records.size() > MAX_RECORDS)
            throw new IllegalArgumentException("单份备份最多支持 10000 条，当前记录超出上限。原记录仍保留在本机。");
        Writer writer = new Writer(exportedAt);
        for (NotebookRecord row : records) writer.add(row);
        if (records instanceof Bundle) for (AiConversationRecord session : ((Bundle) records).sessions) writer.addSession(session);
        if (records instanceof Bundle) for (JSONObject chart : ((Bundle) records).baziCharts) writer.addBazi(chart);
        return writer.finish();
    }

    /** Incremental capped encoding also avoids allocating a huge list during export. */
    public static final class Writer {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final HashSet<String> ids = new HashSet<String>();
        private int count;
        private int sessionCount;
        private final HashSet<String> sessionIds = new HashSet<String>();
        private int baziCount;
        private final HashSet<String> baziIds = new HashSet<String>();
        private boolean finished, sessionsStarted, baziStarted;
        public Writer(long exportedAt) throws Exception {
            append("{\"format\":" + JSONObject.quote(FORMAT) + ",\"version\":" + VERSION
                + ",\"exported_at\":" + JSONObject.quote(utc(exportedAt)) + ",\"records\":[");
        }
        public void add(NotebookRecord row) throws Exception {
            if (finished || sessionsStarted || baziStarted) throw new IllegalStateException("备份已生成或已开始写入对话。");
            if (++count > MAX_RECORDS) throw new IllegalArgumentException("单份备份最多支持 10000 条，原记录仍保留在本机。");
            validate(row);
            if (!ids.add(row.id)) throw new IllegalArgumentException("备份中存在重复记录编号。");
            append((count == 1 ? "" : ",") + toJson(row).toString());
        }
        public void addSession(AiConversationRecord session) throws Exception {
            if (finished||baziStarted) throw new IllegalStateException("备份已生成或已开始写入八字命盘。");
            if (++sessionCount > MAX_SESSIONS || !sessionIds.add(session.id)) throw new IllegalArgumentException("AI 对话数量超限或编号重复。");
            AiConversationRecord.validate(session);
            if (!sessionsStarted) { append("],\"ai_sessions\":["); sessionsStarted = true; }
            append((sessionCount == 1 ? "" : ",") + AiConversationRecord.toJson(session).toString());
        }
        public void addBazi(JSONObject item)throws Exception {
            if(finished)throw new IllegalStateException("备份已生成。");
            BaziArchive.validateBackup(item);
            String id=item.getJSONObject("chart").getString("id");
            if(++baziCount>MAX_RECORDS||!baziIds.add(id))throw new IllegalArgumentException("八字命盘数量超限或编号重复。");
            if(!baziStarted){append(sessionsStarted?"],\"bazi_charts\":[":"],\"ai_sessions\":[],\"bazi_charts\":[");baziStarted=true;}
            append((baziCount==1?"":",")+item.toString());
        }
        public byte[] finish() throws IOException {
            if (!finished) { append(baziStarted ? "]}" : sessionsStarted ? "],\"bazi_charts\":[]}" : "],\"ai_sessions\":[],\"bazi_charts\":[]}"); finished = true; }
            return bytes.toByteArray();
        }
        private void append(String text) throws IOException {
            byte[] chunk = text.getBytes(UTF8);
            if (bytes.size() > MAX_BYTES - chunk.length) throw new IOException("备份超过 8 MB，未生成文件。原记录仍保留在本机。");
            bytes.write(chunk);
        }
    }

    public static List<NotebookRecord> decode(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("请选择非空的简易卦例 JSON 备份（最大 8 MB）。");
        String json;
        try { json = UTF8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw new IllegalArgumentException("备份不是有效的 UTF-8 文件。"); }
        if (json.length() > 0 && json.charAt(0) == '\ufeff') json = json.substring(1);
        checkDepth(json);
        JSONTokener tokener = new JSONTokener(json);
        Object value = tokener.nextValue();
        if (!(value instanceof JSONObject) || tokener.nextClean() != 0)
            throw new IllegalArgumentException("备份格式不正确，文件末尾包含多余内容。");
        JSONObject root = (JSONObject) value;
        if (!FORMAT.equals(string(root, "format"))) throw new IllegalArgumentException("这不是简易卦例备份。");
        int version = integer(root, "version", 1, Integer.MAX_VALUE);
        if (version < 1 || version > VERSION)
            throw new IllegalArgumentException("暂不支持此备份版本，请使用匹配版本的简易。");
        String exported = string(root, "exported_at");
        SimpleDateFormat date = utcFormat(); date.setLenient(false);
        ParsePosition cursor = new ParsePosition(0);
        Date parsed = date.parse(exported, cursor);
        if (parsed == null || cursor.getIndex() != exported.length() || !date.format(parsed).equals(exported))
            throw new IllegalArgumentException("备份日期格式不正确。");
        Object raw = root.get("records");
        if (!(raw instanceof JSONArray)) throw new IllegalArgumentException("备份缺少记录列表。");
        JSONArray rows = (JSONArray) raw;
        if (rows.length() > MAX_RECORDS) throw new IllegalArgumentException("单份备份最多支持 10000 条，文件未导入。");
        Bundle result = new Bundle();
        HashSet<String> ids = new HashSet<String>();
        for (int i = 0; i < rows.length(); i++) {
            try {
                Object row = rows.get(i);
                if (!(row instanceof JSONObject)) throw new IllegalArgumentException("记录应为对象。");
                NotebookRecord record = fromJson((JSONObject) row);
                validate(record);
                if (!ids.add(record.id)) throw new IllegalArgumentException("重复的记录编号。");
                result.add(record);
            } catch (Exception e) { throw new IllegalArgumentException("第 " + (i + 1) + " 条记录无效：" + e.getMessage()); }
        }
        if (version >= 2) {
            Object sessions = root.get("ai_sessions");
            if (!(sessions instanceof JSONArray) || ((JSONArray) sessions).length() > MAX_SESSIONS)
                throw new IllegalArgumentException("AI 对话列表无效或超过上限。");
            HashSet<String> sessionIds = new HashSet<String>();
            JSONArray all = (JSONArray) sessions;
            for (int i = 0; i < all.length(); i++) {
                AiConversationRecord session = AiConversationRecord.fromJson(all.getJSONObject(i));
                if (!sessionIds.add(session.id)) throw new IllegalArgumentException("AI 对话编号重复。");
                result.sessions.add(session);
            }
        }
        if(version>=3){
            Object valueCharts=root.get("bazi_charts");
            if(!(valueCharts instanceof JSONArray)||((JSONArray)valueCharts).length()>MAX_RECORDS)throw new IllegalArgumentException("八字命盘列表无效或超过上限。");
            JSONArray charts=(JSONArray)valueCharts;HashSet<String> chartIds=new HashSet<String>();
            for(int i=0;i<charts.length();i++){JSONObject item=charts.getJSONObject(i);BaziArchive.validateBackup(item);String id=item.getJSONObject("chart").getString("id");if(!chartIds.add(id))throw new IllegalArgumentException("八字命盘编号重复。");result.baziCharts.add(item);}
        }
        return result;
    }

    public static byte[] readBounded(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("文件无法打开。");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (count == 0) continue;
            if (bytes.size() > MAX_BYTES - count) throw new IOException("文件超过 8 MB，未导入。");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    public static void validate(NotebookRecord r) {
        if (r == null) throw new IllegalArgumentException("记录为空。");
        text(r.id, 36, "记录编号");
        try { if (!UUID.fromString(r.id).toString().equals(r.id)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("记录编号格式不正确。"); }
        text(r.title, 128, "标题");
        if (r.title.trim().length() == 0) throw new IllegalArgumentException("请填写标题。");
        text(r.question, 2048, "所问事项"); text(r.analysis, 16000, "事前分析");
        text(r.feedback, 16000, "后续观察"); text(r.tags, 1000, "标签");
        text(r.method, 128, "起卦方式"); text(r.info, 8192, "取数依据");
        text(r.sourceCaseId, 256, "案例编号");
        if (r.method.trim().length() == 0) throw new IllegalArgumentException("起卦方式为空。");
        if (r.upper < 1 || r.upper > 8 || r.lower < 1 || r.lower > 8 || r.moving < 1 || r.moving > 6)
            throw new IllegalArgumentException("卦数应为 1–8，动爻应为 1–6。");
        if (r.createdAt < 0 || r.createdAt > MAX_TIME || r.updatedAt < r.createdAt || r.updatedAt > MAX_TIME)
            throw new IllegalArgumentException("记录时间不正确。");
        if (r.revision < 1) throw new IllegalArgumentException("记录修订编号不正确。");
    }

    public static String legacyId(int index, long at, String method, String info, int upper, int lower, int moving) {
        String key = "jianyi-legacy-v1:" + index + ":" + at + ":" + method.length() + ":" + method
            + ":" + info.length() + ":" + info + ":" + upper + ":" + lower + ":" + moving;
        return UUID.nameUUIDFromBytes(key.getBytes(UTF8)).toString();
    }

    public static boolean same(NotebookRecord a, NotebookRecord b) {
        return a.id.equals(b.id) && a.title.equals(b.title) && a.question.equals(b.question)
            && a.analysis.equals(b.analysis) && a.feedback.equals(b.feedback) && a.tags.equals(b.tags)
            && a.method.equals(b.method) && a.info.equals(b.info) && a.sourceCaseId.equals(b.sourceCaseId)
            && a.upper == b.upper && a.lower == b.lower && a.moving == b.moving && a.favorite == b.favorite
            && a.createdAt == b.createdAt && a.updatedAt == b.updatedAt && a.revision == b.revision;
    }

    static List<NotebookRecord> decodeLegacy(String json) throws Exception {
        if (json == null || json.getBytes(UTF8).length > MAX_BYTES) throw new IllegalArgumentException("旧起卦记录过大。");
        JSONTokener tokener = new JSONTokener(json);
        Object raw = tokener.nextValue();
        if (!(raw instanceof JSONArray) || tokener.nextClean() != 0) throw new IllegalArgumentException("旧起卦记录格式不正确。");
        JSONArray rows = (JSONArray) raw;
        if (rows.length() > MAX_RECORDS) throw new IllegalArgumentException("旧起卦记录超过迁移上限。");
        ArrayList<NotebookRecord> result = new ArrayList<NotebookRecord>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            NotebookRecord r = new NotebookRecord();
            r.createdAt = whole(row, "at", 0, MAX_TIME); r.updatedAt = r.createdAt;
            r.method = row.has("method") ? string(row, "method") : "起卦";
            r.info = row.has("info") ? string(row, "info") : "";
            r.upper = integer(row, "upper", 1, 8); r.lower = integer(row, "lower", 1, 8); r.moving = integer(row, "moving", 1, 6);
            r.id = legacyId(i, r.createdAt, r.method, r.info, r.upper, r.lower, r.moving);
            r.title = r.method + " · 旧记录 " + (rows.length() - i);
            validate(r); result.add(r);
        }
        return result;
    }

    static JSONObject toJson(NotebookRecord r) throws JSONException {
        return new JSONObject().put("id", r.id).put("title", r.title).put("question", r.question)
            .put("analysis", r.analysis).put("feedback", r.feedback).put("tags", r.tags).put("favorite", r.favorite)
            .put("method", r.method).put("info", r.info).put("upper", r.upper).put("lower", r.lower)
            .put("moving", r.moving).put("source_case_id", r.sourceCaseId)
            .put("created_at", r.createdAt).put("updated_at", r.updatedAt).put("revision", r.revision);
    }

    private static NotebookRecord fromJson(JSONObject row) throws Exception {
        NotebookRecord r = new NotebookRecord();
        r.id = string(row, "id"); r.title = string(row, "title"); r.question = string(row, "question");
        r.analysis = string(row, "analysis"); r.feedback = string(row, "feedback"); r.tags = string(row, "tags");
        r.method = string(row, "method"); r.info = string(row, "info"); r.sourceCaseId = string(row, "source_case_id");
        r.upper = integer(row, "upper", 1, 8); r.lower = integer(row, "lower", 1, 8); r.moving = integer(row, "moving", 1, 6);
        r.revision = integer(row, "revision", 1, Integer.MAX_VALUE);
        r.createdAt = whole(row, "created_at", 0, MAX_TIME); r.updatedAt = whole(row, "updated_at", 0, MAX_TIME);
        Object favorite = row.get("favorite");
        if (!(favorite instanceof Boolean)) throw new IllegalArgumentException("收藏字段应为布尔值。");
        r.favorite = ((Boolean) favorite).booleanValue();
        return r;
    }

    private static String string(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException(key + " 应为文字。");
        return (String) value;
    }
    private static long whole(JSONObject object, String key, long min, long max) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) throw new IllegalArgumentException(key + " 应为整数。");
        long n = ((Number) value).longValue();
        if (n < min || n > max) throw new IllegalArgumentException(key + " 超出范围。");
        return n;
    }
    private static int integer(JSONObject object, String key, int min, int max) throws JSONException {
        return (int) whole(object, key, min, max);
    }
    private static void text(String value, int max, String name) {
        if (value == null || value.length() > max) throw new IllegalArgumentException(name + "最多支持 " + max + " 个字符，请调整后重试。");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == 0 || (c >= 0xd800 && c <= 0xdfff &&
                !(c <= 0xdbff && i + 1 < value.length() && value.charAt(++i) >= 0xdc00 && value.charAt(i) <= 0xdfff)))
                throw new IllegalArgumentException(name + "含无效字符。");
        }
    }
    private static String utc(long at) { return utcFormat().format(new Date(at)); }
    private static void checkDepth(String text) {
        int depth = 0; boolean quoted = false, escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 32) throw new IllegalArgumentException("备份结构嵌套过深。"); }
            else if (c == '}' || c == ']') depth--;
        }
    }
    private static SimpleDateFormat utcFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC")); return format;
    }
}
