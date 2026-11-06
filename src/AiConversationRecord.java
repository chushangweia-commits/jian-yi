package com.jianyi.yixue.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** Portable completed conversation. Credentials and network configuration are never represented. */
public class AiConversationRecord {
    public String id = "", reference = "", method = "", draft = "", chart = "", subject = "";
    public int upper, lower, moving;
    public long createdAt, updatedAt;
    public JSONArray history = new JSONArray();

    public static void validate(AiConversationRecord s) throws Exception {
        if (s == null) throw new IllegalArgumentException("AI 对话为空。");
        bounded(s.id, 100); bounded(s.reference, 160); bounded(s.method, 100);
        bounded(s.draft, 1000); bounded(s.subject, 2048); bounded(s.chart, 8192);
        if (s.id.length() == 0 || s.method.length() == 0 || count(s.draft) > 500
            || s.upper < 1 || s.upper > 8 || s.lower < 1 || s.lower > 8 || s.moving < 1 || s.moving > 6
            || s.createdAt < 0 || s.updatedAt < s.createdAt || s.updatedAt > 253402300799999L
            || s.history == null || s.history.length() > 16 || s.history.length() % 2 != 0)
            throw new IllegalArgumentException("AI 对话字段不完整。");
        if (s.method.startsWith("六爻") ? s.chart.trim().length() == 0 : s.chart.length() != 0)
            throw new IllegalArgumentException("AI 对话体系与排盘不一致。");
        if (s.method.startsWith("六爻") && (!s.reference.startsWith("note:") || s.subject.trim().length() == 0))
            throw new IllegalArgumentException("六爻对话缺少固定的卦例与问题。");
        for (int i = 0; i < s.history.length(); i++) {
            JSONObject m = s.history.getJSONObject(i);
            String role = i % 2 == 0 ? "user" : "assistant";
            if (m.length() != 2 || !(m.opt("role") instanceof String) || !(m.opt("content") instanceof String)
                || !role.equals(m.getString("role"))) throw new IllegalArgumentException("AI 对话必须是完整问答。");
            boolean meihua=!s.method.startsWith("六爻")&&!s.method.startsWith("大衍筮法:");
            int max=i%2==0?500:(meihua?1000:200);
            String body = m.getString("content"); bounded(body, max*2);
            if (body.trim().length() == 0 || count(body) > max)
                throw new IllegalArgumentException("AI 问答长度不正确。");
        }
    }
    public static JSONObject toJson(AiConversationRecord s) throws Exception {
        validate(s);
        return new JSONObject().put("id",s.id).put("reference",s.reference).put("method",s.method)
            .put("draft",s.draft).put("chart",s.chart).put("subject",s.subject).put("upper",s.upper)
            .put("lower",s.lower).put("moving",s.moving).put("created_at",s.createdAt)
            .put("updated_at",s.updatedAt).put("history",new JSONArray(s.history.toString()));
    }
    public static AiConversationRecord fromJson(JSONObject o) throws Exception {
        AiConversationRecord s = new AiConversationRecord();
        s.id=text(o,"id");s.reference=text(o,"reference");s.method=text(o,"method");
        s.draft=text(o,"draft");s.chart=text(o,"chart");s.subject=text(o,"subject");
        s.upper=integer(o,"upper",1,8);s.lower=integer(o,"lower",1,8);s.moving=integer(o,"moving",1,6);
        s.createdAt=whole(o,"created_at");s.updatedAt=whole(o,"updated_at");
        Object history=o.get("history");if(!(history instanceof JSONArray))throw new IllegalArgumentException("AI 问答格式无效。");
        s.history=new JSONArray(history.toString());validate(s);return s;
    }
    public static boolean sameIdentity(AiConversationRecord a,AiConversationRecord b) {
        return a.id.equals(b.id)&&a.reference.equals(b.reference)&&a.method.equals(b.method)&&a.chart.equals(b.chart)
            &&a.subject.equals(b.subject)&&a.upper==b.upper&&a.lower==b.lower&&a.moving==b.moving&&a.createdAt==b.createdAt;
    }
    public static boolean same(AiConversationRecord a,AiConversationRecord b) {
        return sameIdentity(a,b)&&a.draft.equals(b.draft)&&a.history.toString().equals(b.history.toString())&&a.updatedAt==b.updatedAt;
    }
    static boolean hasHistoryPrefix(JSONArray longer,JSONArray prefix) throws Exception {
        if(longer.length()<prefix.length())return false;
        for(int i=0;i<prefix.length();i++){
            JSONObject a=longer.getJSONObject(i),b=prefix.getJSONObject(i);
            if(!a.getString("role").equals(b.getString("role"))||!a.getString("content").equals(b.getString("content")))return false;
        }
        return true;
    }
    private static int count(String s){return s.codePointCount(0,s.length());}
    private static void bounded(String s,int max){
        if(s==null||s.length()>max)throw new IllegalArgumentException("AI 对话字段过长。");
        for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c==0)throw new IllegalArgumentException("AI 对话字符无效。");if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw new IllegalArgumentException("AI 对话字符无效。");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("AI 对话字符无效。");}
    }
    private static String text(JSONObject o,String key)throws Exception{Object v=o.get(key);if(!(v instanceof String))throw new IllegalArgumentException("AI 字段应为文字。");return (String)v;}
    private static long whole(JSONObject o,String key)throws Exception{Object v=o.get(key);if(!(v instanceof Integer)&&!(v instanceof Long))throw new IllegalArgumentException("AI 时间或卦数应为整数。");long n=((Number)v).longValue();if(n<0||n>253402300799999L)throw new IllegalArgumentException("AI 数值超出范围。");return n;}
    private static int integer(JSONObject o,String key,int min,int max)throws Exception{long n=whole(o,key);if(n<min||n>max)throw new IllegalArgumentException("AI 卦数超出范围。");return (int)n;}
}
