package com.jianyi.yixue.app;

import android.content.res.AssetManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Immutable, process-local search of the bundled reading library. Loaded only on a worker. */
final class OfflineSearchIndex {
    static final String[] TYPES = {"topic", "lesson", "case", "classic", "hexagram", "guide"};
    private static volatile OfflineSearchIndex cached;
    private static final String TRADITIONAL = "萬與專業東絲兩嚴個豐臨為麗舉義烏樂喬習鄉書買亂爭於虧雲亙亞產親億僅從倉儀們價眾優會傘偉傳傷倫偽體餘傾儲兒兌黨蘭關興養獸內冊寫軍農馮沖決況冷凍淨淒涼減凜幾鳳憑凱擊劃劉則剛創別刪剝劇勸辦務動勵勢勞勝勻匯區醫華協單賣盧佔衛卻歷厭厲縣參雙發變敘疊葉號嘆嚇呂嗎員聽啟吳問啞喚嘗嘯喬喪喬團園圍圖圓聖場壞塊堅壇壯聲處備複夠夢夾奪奮奧婦媽嬰孫學寧寶實審寫寬對導將尋壽塵盡層屬岡島嶺峽崗幣帥師帳帶幫幹庫應廟廢廣廬開異張彌彎歸當錄徑後徹徵憶懷態慣憂慮懼戀戍戰戶撲執擴掃揚擇擔拋挾搖損換據擬摯擁擺搖攝敵敗數齊齋斷無時曆暢晉曉暈暫術朮權條來極構樞樣樹橋機橫檢樓櫃欄標歐歡歲歿殘殼毀氣漢湯溝溫濟澤潛濁測潔淺濤潤濃濕灣滅燈靈災爐點煉熱爭牽犧狀獨獲獵獻獸現環璣璇產畝畫疇當疊瘡瘋療盧盜監盤眾著睜矯礎禮禍禪離種積稱穀窮竅竄竊競筆筍筮籌籠籤籙糧糾紀約紅紂紋納純紗紙級紛紜紡索終組細絆絕統絲經綁結給絡絞繼續緒綠維綜綱緊線練緩緣編緯縣縱總績織繩繫續纏網罰羅羣聞聯聰聖腎腸腦腳脫膽臟臺舉艱艷藝節範莊華萊萬葉葦著蔥蔣蔭藥蘇處虛蟲蠱蠶術補裝裡複見觀規覺覽觸言訂計訊討訓託記訣訟訪設許論詐評詞詠詢試詩話該詳語誤誥說誰課調談諒諸謂謀謎謙講謝謠謹譜識譯議讚讓豈豬貝貞負財貢貧貨貪貫責貴費貿賊賈賓賜賞賠賢賣賦質賴贈贊趙趨跡踐蹤躍車軌軍軒軟轉軸輕載輔輝輩輪輯輸轅轟辦辭邊遙遞遠遷選遺邁還鄰鄭釋釐鏡鑒針鈍鈔鈞鐘鋼錄錢錯錦鍵鍊鎮鎖鐵鑄鑑長門閃閉問閏閑間閔閣閱閩闔闕闖關闢隊陽陰陳陣陸險隨隱隸難雜雞離霧霽靜頂頃項順須預頗領頭頻題顏額顯顧風飛飯飲飽餓餘館馬馴駁駐駕駙騎驗驚驛魚鳥鳴鵝雞鶴鹹鹽麗麥黃黨龍龜";
    private static final String SIMPLIFIED = "万与专业东丝两严个丰临为丽举义乌乐乔习乡书买乱争于亏云亘亚产亲亿仅从仓仪们价众优会伞伟传伤伦伪体余倾储儿兑党兰关兴养兽内册写军农冯冲决况冷冻净凄凉减凛几凤凭凯击划刘则刚创别删剥剧劝办务动励势劳胜匀汇区医华协单卖卢占卫却历厌厉县参双发变叙叠叶号叹吓吕吗员听启吴问哑唤尝啸乔丧乔团园围图圆圣场坏块坚坛壮声处备复够梦夹夺奋奥妇妈婴孙学宁宝实审写宽对导将寻寿尘尽层属冈岛岭峡岗币帅师帐带帮干库应庙废广庐开异张弥弯归当录径后彻征忆怀态惯忧虑惧恋戍战户扑执扩扫扬择担抛挟摇损换据拟挚拥摆摇摄敌败数齐斋断无时历畅晋晓晕暂术术权条来极构枢样树桥机横检楼柜栏标欧欢岁殁残壳毁气汉汤沟温济泽潜浊测洁浅涛润浓湿湾灭灯灵灾炉点炼热争牵牺状独获猎献兽现环玑璇产亩画畴当叠疮疯疗卢盗监盘众着睁矫础礼祸禅离种积称谷穷窍窜窃竞笔笋筮筹笼签箓粮纠纪约红纣纹纳纯纱纸级纷纭纺索终组细绊绝统丝经绑结给络绞继续绪绿维综纲紧线练缓缘编纬县纵总绩织绳系续缠网罚罗群闻联聪圣肾肠脑脚脱胆脏台举艰艳艺节范庄华莱万叶苇着葱蒋荫药苏处虚虫蛊蚕术补装里复见观规觉览触言订计讯讨训托记诀讼访设许论诈评词咏询试诗话该详语误诰说谁课调谈谅诸谓谋谜谦讲谢谣谨谱识译议赞让岂猪贝贞负财贡贫货贪贯责贵费贸贼贾宾赐赏赔贤卖赋质赖赠赞赵趋迹践踪跃车轨军轩软转轴轻载辅辉辈轮辑输辕轰办辞边遥递远迁选遗迈还邻郑释厘镜鉴针钝钞钧钟钢录钱错锦键链镇锁铁铸鉴长门闪闭问闰闲间闵阁阅闽阖阙闯关辟队阳阴陈阵陆险随隐隶难杂鸡离雾霁静顶顷项顺须预颇领头频题颜额显顾风飞饭饮饱饿余馆马驯驳驻驾驸骑验惊驿鱼鸟鸣鹅鸡鹤咸盐丽麦黄党龙龟";
    private static final HashMap<Character, Character> FOLD = new HashMap<Character, Character>();
    static {
        if (TRADITIONAL.length() != SIMPLIFIED.length()) throw new IllegalStateException("search normalization map");
        for (int i = 0; i < TRADITIONAL.length(); i++) FOLD.put(TRADITIONAL.charAt(i), SIMPLIFIED.charAt(i));
        FOLD.put('旡', '无'); FOLD.put('无', '无'); FOLD.put('羣', '群'); FOLD.put('説', '说'); FOLD.put('兊', '兑'); FOLD.put('漁', '渔');
    }

    static final class Document {
        final String type, id, title, location, text, normalizedTitle, normalizedText;
        final int section;
        Document(String type, String id, int section, String title, String location, String text) {
            this.type = type; this.id = id; this.section = section; this.title = title;
            this.location = location; this.text = text;
            normalizedTitle = normalize(title);
            normalizedText = normalize(title + "\n" + location + "\n" + text);
        }
    }

    static final class Hit {
        final Document document;
        final String snippet;
        final int score;
        Hit(Document document, String snippet, int score) { this.document = document; this.snippet = snippet; this.score = score; }
    }

    static final class Results {
        final ArrayList<Hit> hits;
        final LinkedHashMap<String, Integer> counts = new LinkedHashMap<String, Integer>();
        final int indexed;
        Results(ArrayList<Hit> hits, int indexed) {
            this.hits = hits; this.indexed = indexed;
            for (String type : TYPES) counts.put(type, 0);
            for (Hit hit : hits) counts.put(hit.document.type, counts.get(hit.document.type) + 1);
        }
        ArrayList<Hit> ofType(String type) {
            if (type == null || type.length() == 0) return hits;
            ArrayList<Hit> result = new ArrayList<Hit>();
            for (Hit hit : hits) if (type.equals(hit.document.type)) result.add(hit);
            return result;
        }
    }

    private final ArrayList<Document> documents;
    private OfflineSearchIndex(ArrayList<Document> documents) { this.documents = documents; }

    static synchronized OfflineSearchIndex load(AssetManager assets) throws Exception {
        if (cached != null) return cached;
        ArrayList<Document> docs = new ArrayList<Document>();
        JSONObject advanced = read(assets, "meihua-advanced.json");
        JSONArray topics = advanced.getJSONArray("topics");
        HashMap<String, String> groups = new HashMap<String, String>();
        JSONArray groupList = advanced.getJSONArray("groups");
        for (int i = 0; i < groupList.length(); i++) groups.put(groupList.getJSONObject(i).getString("id"), groupList.getJSONObject(i).getString("title"));
        for (int i = 0; i < topics.length(); i++) {
            JSONObject topic = topics.getJSONObject(i);
            docs.add(new Document("topic", topic.getString("id"), -1, topic.getString("title"),
                "梅花进阶 · " + groups.get(topic.getString("group")), prose(topic)));
        }
        JSONObject learning = read(assets, "meihua-learning.json");
        for (String type : new String[] {"lesson", "case"}) {
            JSONArray values = learning.getJSONArray("lesson".equals(type) ? "lessons" : "cases");
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.getJSONObject(i);
                docs.add(new Document(type, value.getString("id"), -1, value.getString("title"),
                    "lesson".equals(type) ? value.optString("level", "学习课程") + " · 第 " + (i + 1) + " 课" : "案例课堂", prose(value)));
            }
        }
        String[] files = assets.list("appdata");
        if (files == null) throw new IllegalArgumentException("reading catalog missing");
        java.util.Arrays.sort(files);
        for (String file : files) if (file.startsWith("classic-fulltext-") && file.endsWith(".json")) {
            JSONObject book = read(assets, file);
            JSONArray sections = book.getJSONArray("sections");
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.getJSONObject(i);
                docs.add(new Document("classic", book.getString("id"), i, section.getString("title"),
                    book.getString("title") + " · 第 " + (i + 1) + " 篇", prose(section)));
            }
        }
        JSONArray names = read(assets, "hexagrams.json").getJSONArray("hexagrams");
        JSONObject classical = read(assets, "zhouyi-classics.json");
        JSONObject summaries = read(assets, "zhouyi-summaries.json");
        JSONObject details = read(assets, "zhouyi-details.json");
        for (int i = 0; i < names.length(); i++) {
            JSONObject hexagram = names.getJSONObject(i);
            int number = hexagram.getInt("number");
            String text = prose(hexagram) + "\n" + prose(byNumber(classical, number)) + "\n" + prose(byNumber(summaries, number));
            docs.add(new Document("hexagram", hexagram.getString("code"), 0,
                hexagram.getString("full_name") + " · 卦爻辞", "六十四卦 · 第 " + number + " 卦 · 经传与摘要", text));
            JSONObject detail = byNumber(details, number);
            docs.add(new Document("hexagram", hexagram.getString("code"), 1,
                hexagram.getString("full_name") + " · 详细解析", "六十四卦 · 第 " + number + " 卦 · 来源：易安居", prose(detail)));
        }
        for (String file : new String[] {"knowledge-foundation.json", "knowledge-advanced-guides.json"}) {
            JSONArray guides = read(assets, file).getJSONArray("guides");
            for (int i = 0; i < guides.length(); i++) {
                JSONObject guide = guides.getJSONObject(i);
                docs.add(new Document("guide", guide.getJSONArray("names").getString(0), -1,
                    guide.getString("title"), "知识图解 · " + guide.getString("category"), prose(guide)));
            }
        }
        cached = new OfflineSearchIndex(docs);
        return cached;
    }

    Results search(String query) {
        final String[] terms = terms(query);
        ArrayList<Hit> hits = new ArrayList<Hit>();
        if (terms.length == 0) return new Results(hits, documents.size());
        for (Document doc : documents) {
            boolean match = true;
            int score = 0;
            for (String term : terms) {
                int first = doc.normalizedText.indexOf(term);
                if (first < 0) { match = false; break; }
                score += doc.normalizedTitle.equals(term) ? 300 : doc.normalizedTitle.contains(term) ? 100 : 10;
                if (first < 150) score += 5;
            }
            if (match) hits.add(new Hit(doc, snippet(doc.text, terms, 110), score));
        }
        Collections.sort(hits, new Comparator<Hit>() {
            @Override public int compare(Hit a, Hit b) {
                int rank = b.score - a.score;
                if (rank != 0) return rank;
                int type = typeIndex(a.document.type) - typeIndex(b.document.type);
                if (type != 0) return type;
                int title = a.document.title.compareTo(b.document.title);
                return title != 0 ? title : a.document.section - b.document.section;
            }
        });
        return new Results(hits, documents.size());
    }

    static String typeName(String type) {
        if ("topic".equals(type)) return "进阶专题";
        if ("lesson".equals(type)) return "学习课程";
        if ("case".equals(type)) return "案例课堂";
        if ("classic".equals(type)) return "原典篇章";
        if ("hexagram".equals(type)) return "六十四卦";
        return "知识图解";
    }

    private static int typeIndex(String type) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(type)) return i;
        return TYPES.length;
    }

    static String normalize(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            Character simplified = FOLD.get(c);
            result.append(simplified == null ? c : simplified.charValue());
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }

    static String[] terms(String query) {
        String normalized = normalize(query).replace('　', ' ').trim();
        normalized = normalized.replace("渔樵问答", "渔樵问对");
        if (normalized.length() == 0) return new String[0];
        return normalized.split("\\s+");
    }

    private static String snippet(String value, String[] terms, int size) {
        String text = value.replaceAll("[\\s　]+", " ").trim();
        String folded = normalize(text);
        int first = Integer.MAX_VALUE;
        for (String term : terms) {
            int found = folded.indexOf(term);
            if (found >= 0 && found < first) first = found;
        }
        int start = first == Integer.MAX_VALUE ? 0 : Math.max(0, first - 30);
        int end = Math.min(text.length(), start + size);
        return (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : "");
    }

    private static JSONObject byNumber(JSONObject root, int number) throws Exception {
        JSONArray values = root.getJSONArray("hexagrams");
        for (int i = 0; i < values.length(); i++) if (values.getJSONObject(i).getInt("number") == number) return values.getJSONObject(i);
        throw new IllegalArgumentException("missing hexagram " + number);
    }

    private static String prose(Object value) {
        StringBuilder result = new StringBuilder();
        appendProse(result, value);
        return result.toString().trim();
    }

    private static void appendProse(StringBuilder target, Object value) {
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.length() > 0 && !text.startsWith("http")) target.append(text).append('\n');
        } else if (value instanceof JSONArray) {
            JSONArray values = (JSONArray) value;
            for (int i = 0; i < values.length(); i++) appendProse(target, values.opt(i));
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.contains("url") || key.equals("cross_validation") || key.equals("source_notes")
                    || key.equals("sources") || key.equals("diagram") || key.equals("visual")
                    || key.equals("fixed_revision_url") || key.equals("source_issues") || key.equals("source_title")
                    || key.equals("id") || key.equals("group") || key.equals("kind") || key.equals("method")
                    || key.equals("code") || key.endsWith("_code") || key.equals("teaching_edition")
                    || key.equals("meaning_kind") || key.equals("meaning_attribution") || key.equals("original_book_id")
                    || key.equals("reading_diagram")) continue;
                appendProse(target, object.opt(key));
            }
        }
    }

    private static JSONObject read(AssetManager assets, String name) throws Exception {
        InputStream stream = assets.open("appdata/" + name);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > 4 * 1024 * 1024) throw new IllegalArgumentException("search asset too large");
                bytes.write(buffer, 0, count);
            }
        } finally { stream.close(); }
        return new JSONObject(new String(bytes.toByteArray(), "UTF-8"));
    }
}
