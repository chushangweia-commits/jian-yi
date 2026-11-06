package com.jianyi.yixue.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Private durable notes. All-or-nothing migration/import; no rolling history limit. */
public final class NotebookStore {
    private static NotebookStore instance;
    private final Context context;
    private final Helper helper;
    private static final String[] COLUMNS = {"id", "title", "question", "analysis", "feedback", "tags",
        "favorite", "method", "info", "upper_n", "lower_n", "moving", "source_case_id", "created_at", "updated_at", "revision"};

    private NotebookStore(Context context) {
        this.context = context.getApplicationContext() == null ? context : context.getApplicationContext();
        helper = new Helper(this.context);
    }

    public static synchronized NotebookStore get(Context context) {
        if (instance == null) instance = new NotebookStore(context);
        instance.migrateLegacy();
        instance.migrateAiLegacy();
        return instance;
    }

    public static String capture(Context context, String method, String info, int upper, int lower, int moving) {
        NotebookRecord row = draft(method, info, upper, lower, moving, "");
        return get(context).save(row).id;
    }

    public static NotebookRecord draft(String method, String info, int upper, int lower, int moving, String sourceCaseId) {
        NotebookRecord row = new NotebookRecord();
        row.id = UUID.randomUUID().toString();
        row.method = method == null ? "起卦" : method;
        row.info = info == null ? "" : info;
        row.sourceCaseId = sourceCaseId == null ? "" : sourceCaseId;
        row.upper = upper; row.lower = lower; row.moving = moving;
        row.createdAt = System.currentTimeMillis(); row.updatedAt = row.createdAt;
        row.title = row.method + " · 上" + MeihuaEngine.trigramName(upper) + "下" + MeihuaEngine.trigramName(lower);
        BackupCodec.validate(row); return row;
    }

    public synchronized NotebookRecord save(NotebookRecord value) {
        BackupCodec.validate(value);
        SQLiteDatabase db = helper.getWritableDatabase();
        NotebookRecord row = value.copy();
        db.beginTransaction();
        try {
            NotebookRecord old = read(db, row.id);
            if (old == null) {
                row.updatedAt = Math.max(row.createdAt, System.currentTimeMillis()); row.revision = 1;
                db.insertOrThrow("records", null, values(row));
            } else {
                if (old.revision != row.revision) throw new IllegalStateException("记录已在其他页面更新，请重新打开后编辑。");
                if (old.revision == Integer.MAX_VALUE) throw new IllegalStateException("记录修订次数已达到上限。");
                if (!old.question.equals(row.question) && isQuestionFixed(db, old))
                    throw new IllegalStateException("这条卦例已有 AI 对话，原问题需要保留。补充内容可写入分析、复盘或 AI 追问。");
                // Origin calculation and creation time are kept when editing notes.
                row.createdAt = old.createdAt; row.method = old.method; row.info = old.info;
                row.upper = old.upper; row.lower = old.lower; row.moving = old.moving; row.sourceCaseId = old.sourceCaseId;
                row.updatedAt = Math.max(System.currentTimeMillis(), old.updatedAt + 1); row.revision = old.revision + 1;
                BackupCodec.validate(row);
                if (db.update("records", values(row), "id=?", new String[] {row.id}) != 1)
                    throw new IllegalStateException("记录未能保存。");
            }
            db.setTransactionSuccessful(); return row;
        } finally { db.endTransaction(); }
    }

    public synchronized boolean isQuestionFixed(String id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        NotebookRecord row = read(db, id);
        return row != null && isQuestionFixed(db, row);
    }

    private static boolean isQuestionFixed(SQLiteDatabase db, NotebookRecord row) {
        Cursor cursor = db.query("readings", new String[] {"history"},
            "reference_id=?", new String[] {AiReadingStore.notebookReference(row.id)},
            null, null, null);
        try { while(cursor.moveToNext()){try{if(new org.json.JSONArray(cursor.getString(0)).length()>=2)return true;}catch(Exception corrupt){return true;}}return false; }
        finally { cursor.close(); }
    }

    public synchronized NotebookRecord find(String id) { return read(helper.getReadableDatabase(), id); }

    public synchronized List<NotebookRecord> list() {
        return listPage(0, 0);
    }

    public synchronized List<NotebookRecord> listPage(int offset, int limit) {
        if (offset < 0 || limit < 0) throw new IllegalArgumentException("分页范围不正确。");
        ArrayList<NotebookRecord> result = new ArrayList<NotebookRecord>();
        Cursor cursor = helper.getReadableDatabase().query("records", COLUMNS, null, null, null, null,
            "favorite DESC, created_at DESC, id ASC", limit == 0 ? null : offset + "," + limit);
        try { while (cursor.moveToNext()) result.add(row(cursor)); }
        finally { cursor.close(); }
        return result;
    }

    public synchronized byte[] exportBackup() throws Exception {
        migrateAiLegacy();
        BaziArchive.migrateRecent(context);
        BackupCodec.Writer writer = new BackupCodec.Writer(System.currentTimeMillis());
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor cursor = db.query("records", COLUMNS, null, null, null, null, "created_at ASC,id ASC");
            try { while (cursor.moveToNext()) writer.add(row(cursor)); }
            finally { cursor.close(); }
            for (AiConversationRecord session : AiReadingStore.all(db)) writer.addSession(session);
            for (org.json.JSONObject chart : BaziArchive.exportAll(context,db)) writer.addBazi(chart);
            byte[] bytes = writer.finish(); db.setTransactionSuccessful(); return bytes;
        } finally { db.endTransaction(); }
    }

    public synchronized int count() {
        Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records", null);
        try { return cursor.moveToFirst() ? cursor.getInt(0) : 0; }
        finally { cursor.close(); }
    }

    public synchronized void delete(String id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            if (db.delete("records", "id=?", new String[] {id}) != 1) throw new IllegalStateException("记录不存在或已被删除。");
            db.delete("readings", "reference_id=?", new String[] {AiReadingStore.notebookReference(id)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized ImportPlan previewImport(List<NotebookRecord> rows) {
        validateImport(rows);
        BaziArchive.migrateRecent(context);
        SQLiteDatabase db = helper.getReadableDatabase();
        int fresh = 0, same = 0, conflict = 0;
        for (NotebookRecord row : rows) {
            NotebookRecord existing = read(db, row.id);
            if (existing == null) fresh++;
            else if (BackupCodec.same(existing, row)) same++;
            else conflict++;
        }
        int[] chats = previewSessions(db, rows);
        int[] bazi=BaziArchive.processImport(context,db,rows,false);
        return new ImportPlan(fresh, same, conflict, chats[0], chats[1], chats[2],bazi[0],bazi[1],bazi[2]);
    }

    public synchronized ImportPlan importRecords(List<NotebookRecord> rows) {
        // Defense in depth for direct callers; never trust a preview or partially parsed list.
        validateImport(rows);
        BaziArchive.migrateRecent(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        int added = 0, same = 0, conflict = 0;
        db.beginTransaction();
        try {
            for (NotebookRecord row : rows) {
                NotebookRecord existing = read(db, row.id);
                if (existing == null) { db.insertOrThrow("records", null, values(row)); added++; }
                else if (BackupCodec.same(existing, row)) same++;
                else conflict++;
            }
            int[] chats = importSessions(db, rows);
            int[] bazi=BaziArchive.processImport(context,db,rows,true);
            db.setTransactionSuccessful(); return new ImportPlan(added, same, conflict, chats[0], chats[1], chats[2],bazi[0],bazi[1],bazi[2]);
        } finally { db.endTransaction(); }
    }

    public static final class ImportPlan {
        public final int added, identical, conflicts, sessionsAdded, sessionsIdentical, sessionsConflicts, baziAdded, baziIdentical, baziConflicts;
        ImportPlan(int added, int identical, int conflicts, int sessionsAdded, int sessionsIdentical, int sessionsConflicts,int baziAdded,int baziIdentical,int baziConflicts) {
            this.added = added; this.identical = identical; this.conflicts = conflicts;
            this.sessionsAdded = sessionsAdded; this.sessionsIdentical = sessionsIdentical; this.sessionsConflicts = sessionsConflicts;
            this.baziAdded=baziAdded;this.baziIdentical=baziIdentical;this.baziConflicts=baziConflicts;
        }
        public int skipped() { return identical + conflicts; }
    }

    private static void validateImport(List<NotebookRecord> rows) {
        if (rows == null || rows.size() > BackupCodec.MAX_RECORDS) throw new IllegalArgumentException("导入记录数量超出上限。");
        java.util.HashSet<String> ids = new java.util.HashSet<String>();
        for (NotebookRecord row : rows) {
            BackupCodec.validate(row);
            if (!ids.add(row.id)) throw new IllegalArgumentException("导入文件包含重复记录编号。");
        }
        if (rows instanceof BackupCodec.Bundle) {
            java.util.HashSet<String> chatIds = new java.util.HashSet<String>();
            List<AiConversationRecord> sessions = ((BackupCodec.Bundle) rows).sessions;
            if (sessions.size() > BackupCodec.MAX_SESSIONS) throw new IllegalArgumentException("AI 对话数量超过上限。");
            for (AiConversationRecord session : sessions) {
                try { AiConversationRecord.validate(session); }
                catch (Exception e) { throw new IllegalArgumentException("AI 对话无效：" + e.getMessage(), e); }
                if (!chatIds.add(session.id)) throw new IllegalArgumentException("AI 对话编号重复。");
            }
            java.util.HashSet<String> baziIds=new java.util.HashSet<String>();
            if(((BackupCodec.Bundle)rows).baziCharts.size()>BackupCodec.MAX_RECORDS)throw new IllegalArgumentException("八字命盘数量超限。");
            for(org.json.JSONObject chart:((BackupCodec.Bundle)rows).baziCharts)try{BaziArchive.validateBackup(chart);if(!baziIds.add(chart.getJSONObject("chart").getString("id")))throw new IllegalArgumentException("八字命盘编号重复。");}catch(Exception e){throw new IllegalArgumentException("八字命盘无效："+e.getMessage(),e);}
        }
    }

    SQLiteDatabase database() { return helper.getWritableDatabase(); }

    private static int[] previewSessions(SQLiteDatabase db, List<NotebookRecord> rows) {
        return processSessions(db, rows, false);
    }
    private static int[] importSessions(SQLiteDatabase db, List<NotebookRecord> rows) {
        return processSessions(db, rows, true);
    }
    private static int[] processSessions(SQLiteDatabase db, List<NotebookRecord> rows, boolean write) {
        int[] counts = new int[3];
        if (!(rows instanceof BackupCodec.Bundle)) return counts;
        java.util.HashSet<String> conflictRefs = new java.util.HashSet<String>();
        for (NotebookRecord incoming : rows) {
            NotebookRecord existing = read(db, incoming.id);
            if (existing != null && !BackupCodec.same(existing, incoming)) conflictRefs.add(AiReadingStore.notebookReference(incoming.id));
        }
        for (AiConversationRecord incoming : ((BackupCodec.Bundle) rows).sessions) {
            try {
                AiConversationRecord existing = AiReadingStore.read(db, incoming.id);
                if (conflictRefs.contains(incoming.reference)) { counts[2]++; continue; }
                if (incoming.method.startsWith("六爻")) {
                    String linkedId = incoming.reference.substring(5);
                    NotebookRecord linked = read(db, linkedId);
                    if (linked == null) for (NotebookRecord candidate : rows)
                        if (candidate.id.equals(linkedId)) { linked = candidate; break; }
                    if (linked == null || !linked.method.startsWith("六爻") || !linked.question.equals(incoming.subject)
                        || !linked.info.equals(incoming.chart))
                        throw new IllegalArgumentException("六爻对话与备份卦例的问题或排盘不一致。");
                }
                if (existing == null) {
                    if (write) db.insertOrThrow("readings", null, AiReadingStore.values(incoming));
                    counts[0]++;
                } else if (AiConversationRecord.same(existing, incoming)) counts[1]++;
                else counts[2]++;
            } catch (Exception e) { throw new IllegalStateException("AI 对话未导入，整份备份已回滚：" + e.getMessage(), e); }
        }
        return counts;
    }

    /** One transaction copies the old separate chat DB. The source is never changed or deleted. */
    private synchronized void migrateAiLegacy() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Cursor flag = db.rawQuery("SELECT value FROM meta WHERE key='ai_v1_migrated'", null);
        try { if (flag.moveToFirst()) return; } finally { flag.close(); }
        java.io.File oldFile = context.getDatabasePath("jianyi_ai_readings_v1.db");
        SQLiteDatabase old = null; Cursor chats = null;
        db.beginTransaction();
        try {
            if (oldFile.exists()) {
                old = SQLiteDatabase.openDatabase(oldFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                chats = old.query("readings", null, null, null, null, null, "created_at,id");
                while (chats.moveToNext()) {
                    AiReadingStore.Session session = AiReadingStore.row(chats);
                    AiReadingStore.Session existing = AiReadingStore.read(db, session.id);
                    if (existing == null) db.insertOrThrow("readings", null, AiReadingStore.values(session));
                    else if (!AiConversationRecord.same(existing, session)) throw new IllegalStateException("旧对话编号存在冲突。");
                }
            }
            ContentValues marker = new ContentValues(); marker.put("key", "ai_v1_migrated"); marker.put("value", "1");
            db.insertOrThrow("meta", null, marker); db.setTransactionSuccessful();
        } catch (Exception e) { throw new IllegalStateException("AI 对话迁移未完成，旧记录完整保留，请重试。", e); }
        finally { if (chats != null) chats.close(); if (old != null) old.close(); db.endTransaction(); }
    }

    private synchronized void migrateLegacy() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Cursor flag = db.rawQuery("SELECT value FROM meta WHERE key='legacy_v1_migrated'", null);
        try { if (flag.moveToFirst()) return; } finally { flag.close(); }
        String json = context.getSharedPreferences("guest_meihua_history_v1", Context.MODE_PRIVATE).getString("entries", "[]");
        List<NotebookRecord> rows;
        try { rows = BackupCodec.decodeLegacy(json); }
        catch (Exception e) { throw new IllegalStateException("旧起卦记录未迁移：" + e.getMessage() + "。原记录保留未修改。", e); }
        db.beginTransaction();
        try {
            // Marker shares this transaction, so an interrupted attempt can safely retry.
            for (NotebookRecord row : rows) {
                if (read(db, row.id) == null) db.insertOrThrow("records", null, values(row));
            }
            ContentValues meta = new ContentValues(); meta.put("key", "legacy_v1_migrated"); meta.put("value", "1");
            db.insertOrThrow("meta", null, meta);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    static NotebookRecord read(SQLiteDatabase db, String id) {
        if (id == null) return null;
        Cursor cursor = db.query("records", COLUMNS, "id=?", new String[] {id}, null, null, null);
        try { return cursor.moveToFirst() ? row(cursor) : null; }
        finally { cursor.close(); }
    }
    private static NotebookRecord row(Cursor cursor) {
        NotebookRecord r = new NotebookRecord();
        r.id = cursor.getString(0); r.title = cursor.getString(1); r.question = cursor.getString(2);
        r.analysis = cursor.getString(3); r.feedback = cursor.getString(4); r.tags = cursor.getString(5);
        r.favorite = cursor.getInt(6) != 0; r.method = cursor.getString(7); r.info = cursor.getString(8);
        r.upper = cursor.getInt(9); r.lower = cursor.getInt(10); r.moving = cursor.getInt(11);
        r.sourceCaseId = cursor.getString(12); r.createdAt = cursor.getLong(13); r.updatedAt = cursor.getLong(14); r.revision = cursor.getInt(15);
        return r;
    }
    private static ContentValues values(NotebookRecord r) {
        ContentValues v = new ContentValues();
        v.put("id", r.id); v.put("title", r.title); v.put("question", r.question); v.put("analysis", r.analysis);
        v.put("feedback", r.feedback); v.put("tags", r.tags); v.put("favorite", r.favorite ? 1 : 0);
        v.put("method", r.method); v.put("info", r.info); v.put("upper_n", r.upper); v.put("lower_n", r.lower);
        v.put("moving", r.moving); v.put("source_case_id", r.sourceCaseId); v.put("created_at", r.createdAt);
        v.put("updated_at", r.updatedAt); v.put("revision", r.revision); return v;
    }
    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) { super(context, "jianyi_meihua_notebook.db", null, 3); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,question TEXT NOT NULL," +
                "analysis TEXT NOT NULL,feedback TEXT NOT NULL,tags TEXT NOT NULL,favorite INTEGER NOT NULL CHECK(favorite IN (0,1))," +
                "method TEXT NOT NULL,info TEXT NOT NULL,upper_n INTEGER NOT NULL CHECK(upper_n BETWEEN 1 AND 8)," +
                "lower_n INTEGER NOT NULL CHECK(lower_n BETWEEN 1 AND 8),moving INTEGER NOT NULL CHECK(moving BETWEEN 1 AND 6)," +
                "source_case_id TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,revision INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX record_order ON records(favorite,created_at)");
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL)");
            AiReadingStore.createTable(db);
            BaziArchive.createTable(db);
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if(oldVersion<1||newVersion>3)throw new IllegalStateException("卦例数据库版本无法升级，原数据未修改。");
            if(oldVersion<2)AiReadingStore.createTable(db);
            if(oldVersion<3)BaziArchive.createTable(db);
        }
    }
}
