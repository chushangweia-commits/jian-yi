package com.jianyi.yixue.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;

/** Conversations share the notebook transaction domain; API credentials are stored elsewhere. */
final class AiReadingStore {
    static final int NOTEBOOK_SESSION_LIMIT = 20;
    static final class Session extends AiConversationRecord { }
    private final NotebookStore notebook;
    AiReadingStore(Context context) { notebook=NotebookStore.get(context); }
    void close() { /* Shared database is owned by NotebookStore, never by an Activity. */ }
    Session create(String reference,int upper,int lower,int moving,String method,String draft)throws Exception {
        return create(reference,upper,lower,moving,method,draft,"","");
    }
    Session create(String reference,int upper,int lower,int moving,String method,String draft,String chart,String subject)throws Exception {
        Session s=new Session();s.id=UUID.randomUUID().toString();s.reference=reference==null?"":reference;
        s.upper=upper;s.lower=lower;s.moving=moving;s.method=method==null?"已有卦例":method;
        s.draft=draft==null?"":draft;s.chart=chart==null?"":chart;s.subject=subject==null?"":subject;
        s.createdAt=s.updatedAt=System.currentTimeMillis();save(s);return s;
    }
    Session get(String id)throws Exception{return read(notebook.database(),id);}
    Session latest(String reference)throws Exception {
        if(reference==null||reference.length()==0)return null;
        try(Cursor c=notebook.database().query("readings",null,"reference_id=?",new String[]{reference},null,null,"updated_at DESC","1")){return c.moveToFirst()?row(c):null;}
    }
    List<Session> list(int offset)throws Exception {
        List<Session> result=new ArrayList<Session>();
        try(Cursor c=notebook.database().query("readings",null,null,null,null,null,"updated_at DESC,id",offset+",20")){while(c.moveToNext())result.add(row(c));}return result;
    }
    static String notebookReference(String id){if(id==null||id.trim().length()==0||id.length()>155)throw new IllegalArgumentException("Notebook identity");return "note:"+id;}
    List<Session> forNotebook(String id)throws Exception {
        List<Session> result=new ArrayList<Session>();if(id==null||id.trim().length()==0)return result;
        try(Cursor c=notebook.database().query("readings",null,"reference_id=?",new String[]{notebookReference(id)},null,null,"updated_at DESC,id",String.valueOf(NOTEBOOK_SESSION_LIMIT))){while(c.moveToNext())result.add(row(c));}return result;
    }
    void delete(String id){notebook.database().delete("readings","id=?",new String[]{id});}
    void save(Session s)throws Exception {
        AiConversationRecord.validate(s);SQLiteDatabase db=notebook.database();db.beginTransaction();
        try {
            Session old=read(db,s.id);
            if(old!=null&&(!AiConversationRecord.sameIdentity(old,s)||old.updatedAt!=s.updatedAt
                ||!AiConversationRecord.hasHistoryPrefix(s.history,old.history)||s.history.length()>old.history.length()+2))
                throw new IllegalStateException("对话已在其他页面更新，请重新打开，原记录保留。");
            // Completed six-line answers belong only to the immutable captured notebook question/chart.
            if(s.method.startsWith("六爻")&&s.reference.startsWith("note:")) {
                NotebookRecord note=NotebookStore.read(db,s.reference.substring(5));
                if(note==null||!note.method.startsWith("六爻")||!note.info.equals(s.chart)||!note.question.equals(s.subject))
                    throw new IllegalStateException("卦例问题或排盘已改变，本次回答未写入其他卦例。");
            }
            if(!s.method.startsWith("六爻")&&s.reference.startsWith("note:")&&s.subject.length()>0){
                NotebookRecord note=NotebookStore.read(db,s.reference.substring(5));
                if(note==null||!note.question.equals(s.subject))throw new IllegalStateException("卦例问题已改变，本对话未写入其他问题，请返回卦例重新打开。");
            }
            long previous=s.updatedAt;s.updatedAt=Math.max(System.currentTimeMillis(),old==null?s.createdAt:old.updatedAt+1);
            try {if(old==null)db.insertOrThrow("readings",null,values(s));
                else if(db.update("readings",values(s),"id=? AND updated_at=?",new String[]{s.id,String.valueOf(previous)})!=1)throw new IllegalStateException("对话未能保存。");
            }catch(Exception e){s.updatedAt=previous;throw e;}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static void createTable(SQLiteDatabase db){
        db.execSQL("CREATE TABLE readings (id TEXT PRIMARY KEY, reference_id TEXT NOT NULL, upper_gua INTEGER NOT NULL, lower_gua INTEGER NOT NULL, moving INTEGER NOT NULL, method TEXT NOT NULL, draft TEXT NOT NULL, history TEXT NOT NULL, chart TEXT NOT NULL, subject TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX reading_reference ON readings(reference_id,updated_at)");
    }
    static Session read(SQLiteDatabase db,String id)throws Exception{
        try(Cursor c=db.query("readings",null,"id=?",new String[]{id},null,null,null,"1")){return c.moveToFirst()?row(c):null;}
    }
    static List<AiConversationRecord> all(SQLiteDatabase db)throws Exception {
        List<AiConversationRecord> out=new ArrayList<AiConversationRecord>();
        try(Cursor c=db.query("readings",null,null,null,null,null,"created_at,id")){while(c.moveToNext()){if(out.size()>=BackupCodec.MAX_SESSIONS)throw new IllegalStateException("AI 对话超过备份上限，原记录保留。");out.add(row(c));}}return out;
    }
    static Session row(Cursor c)throws Exception {
        Session s=new Session();s.id=c.getString(c.getColumnIndexOrThrow("id"));s.reference=c.getString(c.getColumnIndexOrThrow("reference_id"));s.upper=c.getInt(c.getColumnIndexOrThrow("upper_gua"));s.lower=c.getInt(c.getColumnIndexOrThrow("lower_gua"));s.moving=c.getInt(c.getColumnIndexOrThrow("moving"));s.method=c.getString(c.getColumnIndexOrThrow("method"));s.draft=c.getString(c.getColumnIndexOrThrow("draft"));
        int chart=c.getColumnIndex("chart"),subject=c.getColumnIndex("subject");s.chart=chart<0?"":c.getString(chart);s.subject=subject<0?"":c.getString(subject);
        String history=c.getString(c.getColumnIndexOrThrow("history"));if(history.length()>40000)throw new IllegalArgumentException("Chat size");s.history=new JSONArray(history);s.createdAt=c.getLong(c.getColumnIndexOrThrow("created_at"));s.updatedAt=c.getLong(c.getColumnIndexOrThrow("updated_at"));AiConversationRecord.validate(s);return s;
    }
    static ContentValues values(AiConversationRecord s){
        ContentValues v=new ContentValues();v.put("id",s.id);v.put("reference_id",s.reference);v.put("upper_gua",s.upper);v.put("lower_gua",s.lower);v.put("moving",s.moving);v.put("method",s.method);v.put("draft",s.draft);v.put("history",s.history.toString());v.put("chart",s.chart);v.put("subject",s.subject);v.put("created_at",s.createdAt);v.put("updated_at",s.updatedAt);return v;
    }
}
