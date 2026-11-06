package com.jianyi.yixue.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local chart archive. Import never overwrites an existing chart or chat. */
public final class BaziArchive {
    private static final String AI_PREFS="jianyi_bazi_ai_v1";
    private BaziArchive() {}
    public static final class Record {
        public String id,source,gender,place,fortune,label="";
        public String[] pillars;
        public long createdAt,updatedAt;
    }
    static void createTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS bazi_charts (id TEXT PRIMARY KEY NOT NULL, data TEXT NOT NULL, imported_chats TEXT NOT NULL DEFAULT '')");
    }
    private static SQLiteDatabase database(Context c){return NotebookStore.get(c).database();}

    public static Record save(Context c,BaziEngine.Chart chart,String source,String gender,String place,String fortune) {
        if(chart==null)throw new IllegalArgumentException("命盘为空");
        Record r=new Record();r.pillars=chart.pillars.clone();r.source=safe(source);r.gender=safe(gender);r.place=safe(place);r.fortune=safe(fortune);
        r.id=BaziInputContext.storageKey(chart.key(),r.source,r.gender,r.place);
        SQLiteDatabase db=database(c);long now=System.currentTimeMillis();db.beginTransaction();
        try {
            Record existing=read(db,r.id);r.createdAt=existing==null?now:existing.createdAt;r.updatedAt=existing==null?now:Math.max(now,existing.updatedAt);if(existing!=null)r.label=existing.label;
            JSONObject data=recordJson(r);ContentValues v=new ContentValues();v.put("id",r.id);v.put("data",data.toString());
            if(existing==null){v.put("imported_chats","");db.insertOrThrow("bazi_charts",null,v);}
            else db.update("bazi_charts",v,"id=?",new String[]{r.id});
            db.setTransactionSuccessful();return r;
        } catch(Exception e){throw new IllegalStateException("命盘未能保存，原有记录保留。",e);}
        finally{db.endTransaction();}
    }
    /** Moves nothing; copies the legacy recent chart into the archive once. */
    public static void migrateRecent(Context c) {
        SharedPreferences p=c.getSharedPreferences("jianyi_bazi_last_v1",Context.MODE_PRIVATE);
        if(!p.contains("p0"))return;
        try {
            BaziEngine.Chart chart=BaziEngine.chart(p.getString("p0",""),p.getString("p1",""),p.getString("p2",""),p.getString("p3",""));
            String source=p.getString("source","已保存四柱"),gender=p.getString("gender","性别不指定"),place=p.getString("place",""),fortune=p.getString("fortune","");
            String id=BaziInputContext.storageKey(chart.key(),source,gender,place);
            if(read(database(c),id)==null)save(c,chart,source,gender,place,fortune);
        } catch(Exception e){throw new IllegalStateException("旧命盘暂未迁移，原记录仍保留。",e);}
    }
    public static List<Record> list(Context c) {
        migrateRecent(c);ArrayList<Record> out=new ArrayList<Record>();
        Cursor cursor=database(c).query("bazi_charts",new String[]{"data"},null,null,null,null,"rowid DESC");
        try{while(cursor.moveToNext())out.add(parseRecord(new JSONObject(cursor.getString(0))));}
        catch(Exception e){throw new IllegalStateException("命盘列表读取失败，原记录仍保留。",e);}finally{cursor.close();}
        return out;
    }
    public static Record find(Context c,String id){return read(database(c),id);}
    /** A display label never changes the chart identity or associated AI conversation. */
    public static void rename(Context c,String id,String label) {
        String name=safe(label).trim();bounded(name,128);SQLiteDatabase db=database(c);db.beginTransaction();
        try{Record r=read(db,id);if(r==null)throw new IllegalArgumentException("命盘不存在。");r.label=name;r.updatedAt=Math.max(System.currentTimeMillis(),r.updatedAt);ContentValues v=new ContentValues();v.put("data",recordJson(r).toString());db.update("bazi_charts",v,"id=?",new String[]{id});db.setTransactionSuccessful();}
        catch(Exception e){throw new IllegalStateException("称呼未能保存，原命盘保留。",e);}finally{db.endTransaction();}
    }
    private static Record read(SQLiteDatabase db,String id){
        Cursor cursor=db.query("bazi_charts",new String[]{"data"},"id=?",new String[]{id},null,null,null);
        try{return cursor.moveToFirst()?parseRecord(new JSONObject(cursor.getString(0))):null;}
        catch(Exception e){throw new IllegalStateException("命盘读取失败。",e);}finally{cursor.close();}
    }
    private static String safe(String s){return s==null?"":s;}
    private static void bounded(String s,int max){if(s==null||s.length()>max)throw new IllegalArgumentException("八字记录过长。");for(int i=0;i<s.length();i++){char x=s.charAt(i);if(x==0)throw new IllegalArgumentException("八字记录含无效字符。");if(Character.isHighSurrogate(x)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw new IllegalArgumentException("八字记录字符无效。");}else if(Character.isLowSurrogate(x))throw new IllegalArgumentException("八字记录字符无效。");}}
    static JSONObject recordJson(Record r)throws Exception {
        if(r==null||r.pillars==null||r.pillars.length!=4)throw new IllegalArgumentException("八字四柱无效。");
        BaziEngine.Chart chart=BaziEngine.chart(r.pillars[0],r.pillars[1],r.pillars[2],r.pillars[3]);
        bounded(r.source,2048);bounded(r.gender,128);bounded(r.place,512);bounded(r.fortune,8192);bounded(r.label,128);
        if(!BaziInputContext.storageKey(chart.key(),r.source,r.gender,r.place).equals(r.id))throw new IllegalArgumentException("命盘身份不一致。");
        if(r.createdAt<0||r.updatedAt<r.createdAt||r.updatedAt>253402300799999L)throw new IllegalArgumentException("命盘时间无效。");
        JSONArray p=new JSONArray();for(String x:r.pillars)p.put(x);
        return new JSONObject().put("id",r.id).put("pillars",p).put("source",r.source).put("gender",r.gender).put("place",r.place).put("fortune",r.fortune).put("label",r.label).put("created_at",r.createdAt).put("updated_at",r.updatedAt);
    }
    static Record parseRecord(JSONObject o)throws Exception {
        Record r=new Record();r.id=o.getString("id");JSONArray p=o.getJSONArray("pillars");if(p.length()!=4)throw new IllegalArgumentException("八字四柱无效。");r.pillars=new String[4];for(int i=0;i<4;i++)r.pillars[i]=p.getString(i);
        r.source=o.getString("source");r.gender=o.getString("gender");r.place=o.getString("place");r.fortune=o.getString("fortune");r.label=o.has("label")?o.getString("label"):"";r.createdAt=o.getLong("created_at");r.updatedAt=o.getLong("updated_at");recordJson(r);return r;
    }
    static void validateChats(JSONObject chats)throws Exception {
        if(chats==null)throw new IllegalArgumentException("八字对话无效。");
        for(java.util.Iterator<String> it=chats.keys();it.hasNext();){String k=it.next();if(!k.equals("current")&&!k.equals("sessions")&&!k.equals("draft")&&!k.equals("legacy"))throw new IllegalArgumentException("八字对话字段无效。");}
        if(chats.has("current"))checkHistory(chats.getJSONArray("current"));
        if(chats.has("legacy"))checkHistory(chats.getJSONArray("legacy"));
        if(chats.has("draft")){String d=chats.getString("draft");bounded(d,1000);}
        if(chats.has("sessions")){
            JSONArray sessions=chats.getJSONArray("sessions");if(sessions.length()>100)throw new IllegalArgumentException("八字对话数量超限。");
            for(int i=0;i<sessions.length();i++){JSONObject s=sessions.getJSONObject(i);if(s.length()!=2||!s.has("saved_at")||!s.has("history"))throw new IllegalArgumentException("八字对话格式无效。");long t=s.getLong("saved_at");if(t<0||t>253402300799999L)throw new IllegalArgumentException("八字对话时间无效。");checkHistory(s.getJSONArray("history"));}
        }
    }
    private static void checkHistory(JSONArray h)throws Exception {
        if(h.length()>16||h.length()%2!=0)throw new IllegalArgumentException("八字对话格式无效。");
        for(int i=0;i<h.length();i++){JSONObject m=h.getJSONObject(i);if(m.length()!=2||!m.getString("role").equals(i%2==0?"user":"assistant"))throw new IllegalArgumentException("八字对话顺序无效。");String body=m.getString("content");int max=i%2==0?500:1000;bounded(body,max*2);if(body.trim().isEmpty()||body.codePointCount(0,body.length())>max)throw new IllegalArgumentException("八字问答长度无效。");}
    }
    static JSONObject exportRecord(Context c,SQLiteDatabase db,String data,String staged)throws Exception {
        Record r=parseRecord(new JSONObject(data));JSONObject chats=staged==null||staged.isEmpty()?new JSONObject():new JSONObject(staged);
        SharedPreferences p=c.getSharedPreferences(AI_PREFS,Context.MODE_PRIVATE);
        String[] names={"current","sessions","draft","legacy"};String chart=r.pillars[0]+r.pillars[1]+r.pillars[2]+r.pillars[3];
        String[] keys={r.id,"sessions_"+r.id,"draft_"+r.id,chart};
        for(int i=0;i<keys.length;i++)if(p.contains(keys[i])){
            String raw=p.getString(keys[i],"");Object value=i==2?raw:new JSONArray(raw);
            if(chats.has(names[i])&&!chats.get(names[i]).toString().equals(value.toString()))throw new IllegalStateException("八字导入对话与本机记录冲突，请先处理后备份。");
            chats.put(names[i],value);
        }
        validateChats(chats);return new JSONObject().put("chart",recordJson(r)).put("chats",chats);
    }
    static void validateBackup(JSONObject item)throws Exception {
        if(item.length()!=2||!item.has("chart")||!item.has("chats"))throw new IllegalArgumentException("八字备份格式无效。");
        parseRecord(item.getJSONObject("chart"));validateChats(item.getJSONObject("chats"));
    }
    static List<JSONObject> exportAll(Context c,SQLiteDatabase db)throws Exception {
        ArrayList<JSONObject> out=new ArrayList<JSONObject>();Cursor cursor=db.query("bazi_charts",new String[]{"data","imported_chats"},null,null,null,null,"rowid ASC");
        try{while(cursor.moveToNext())out.add(exportRecord(c,db,cursor.getString(0),cursor.getString(1)));}finally{cursor.close();}return out;
    }
    static int[] processImport(Context c,SQLiteDatabase db,List<NotebookRecord> rows,boolean write) {
        int[] counts=new int[3];if(!(rows instanceof BackupCodec.Bundle))return counts;
        SharedPreferences prefs=c.getSharedPreferences(AI_PREFS,Context.MODE_PRIVATE);
        for(JSONObject item:((BackupCodec.Bundle)rows).baziCharts)try{
            validateBackup(item);Record incoming=parseRecord(item.getJSONObject("chart"));
            Record old=read(db,incoming.id);JSONObject incomingChats=item.getJSONObject("chats");
            String stage="";if(old!=null){Cursor cursor=db.query("bazi_charts",new String[]{"imported_chats"},"id=?",new String[]{incoming.id},null,null,null);try{if(cursor.moveToFirst())stage=cursor.getString(0);}finally{cursor.close();}}
            JSONObject pending=stage==null||stage.isEmpty()?new JSONObject():new JSONObject(stage);
            boolean conflict=old!=null&&(!old.fortune.equals(incoming.fortune)||!old.label.equals(incoming.label));
            String chart=incoming.pillars[0]+incoming.pillars[1]+incoming.pillars[2]+incoming.pillars[3];
            String[] names={"current","sessions","draft","legacy"};String[] keys={incoming.id,"sessions_"+incoming.id,"draft_"+incoming.id,chart};
            boolean additions=false;
            for(int i=0;i<names.length;i++)if(incomingChats.has(names[i])){
                String value=i==2?incomingChats.getString(names[i]):incomingChats.getJSONArray(names[i]).toString();
                if(prefs.contains(keys[i])&&!prefs.getString(keys[i],"").equals(value))conflict=true;
                if(pending.has(names[i])&&!pending.get(names[i]).toString().equals(value))conflict=true;
                if(!prefs.contains(keys[i])&&!pending.has(names[i])){pending.put(names[i],incomingChats.get(names[i]));additions=true;}
            }
            if(conflict){counts[2]++;continue;}
            if(old==null){if(write){ContentValues v=new ContentValues();v.put("id",incoming.id);v.put("data",recordJson(incoming).toString());v.put("imported_chats",pending.length()==0?"":pending.toString());db.insertOrThrow("bazi_charts",null,v);}counts[0]++;}
            else if(additions){if(write){ContentValues v=new ContentValues();v.put("imported_chats",pending.toString());db.update("bazi_charts",v,"id=?",new String[]{incoming.id});}counts[0]++;}
            else counts[1]++;
        }catch(Exception e){throw new IllegalStateException("八字命盘未导入，整份备份已回滚："+e.getMessage(),e);}
        return counts;
    }
    /** Merge only missing preference keys. Conflicting imported values remain staged. */
    public static void restoreChats(Context c,String id) {
        if(id==null)return;SQLiteDatabase db=database(c);Cursor cursor=db.query("bazi_charts",new String[]{"data","imported_chats"},"id=?",new String[]{id},null,null,null);
        String data=null,staged=null;try{if(cursor.moveToFirst()){data=cursor.getString(0);staged=cursor.getString(1);}}finally{cursor.close();}
        if(staged==null||staged.isEmpty())return;
        try {
            Record r=parseRecord(new JSONObject(data));JSONObject pending=new JSONObject(staged);validateChats(pending);
            SharedPreferences p=c.getSharedPreferences(AI_PREFS,Context.MODE_PRIVATE);SharedPreferences.Editor e=p.edit();
            String[] names={"current","sessions","draft","legacy"};String[] keys={id,"sessions_"+id,"draft_"+id,r.pillars[0]+r.pillars[1]+r.pillars[2]+r.pillars[3]};
            ArrayList<String> moved=new ArrayList<String>();
            for(int i=0;i<names.length;i++)if(pending.has(names[i])){
                String value=i==2?pending.getString(names[i]):pending.getJSONArray(names[i]).toString();
                if(!p.contains(keys[i])){e.putString(keys[i],value);moved.add(names[i]);}
                else if(p.getString(keys[i],"").equals(value))moved.add(names[i]);
            }
            if(!e.commit())throw new IllegalStateException("八字对话恢复未完成。");
            for(String name:moved)pending.remove(name);ContentValues v=new ContentValues();v.put("imported_chats",pending.length()==0?"":pending.toString());db.update("bazi_charts",v,"id=?",new String[]{id});
        }catch(Exception e){throw new IllegalStateException("八字对话恢复未完成，原始备份保留。",e);}
    }
}
