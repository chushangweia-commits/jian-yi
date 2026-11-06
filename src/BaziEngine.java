package com.jianyi.yixue.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import com.nlf.calendar.EightChar;
import com.nlf.calendar.Solar;
import com.nlf.calendar.eightchar.Yun;
import com.nlf.calendar.eightchar.DaYun;

/** Deterministic manual-four-pillars calculator. It does not infer birth time or fortune. */
public final class BaziEngine {
    public static final String[] STEMS={"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
    public static final String[] BRANCHES={"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
    private static final String[] ELEMENT={"木","木","火","火","土","土","金","金","水","水"};
    private static final String[] BRANCH_ELEMENT={"水","土","木","木","土","火","火","土","金","金","土","水"};
    private static final String[] POS={"年柱","月柱","日柱","时柱"};
    private static final boolean[] YANG={true,false,true,false,true,false,true,false,true,false};
    private static final String[][] HIDDEN={{"癸"},{"己","癸","辛"},{"甲","丙","戊"},{"乙"},{"戊","乙","癸"},{"丙","戊","庚"},{"丁","己"},{"己","丁","乙"},{"庚","壬","戊"},{"辛"},{"戊","辛","丁"},{"壬","甲"}};

    private BaziEngine() {}

    public static String[] cycle(){String[] out=new String[60];for(int i=0;i<60;i++)out[i]=STEMS[i%10]+BRANCHES[i%12];return out;}

    public static Chart chart(String year,String month,String day,String hour) {
        String[] p={valid(year),valid(month),valid(day),valid(hour)};
        int dayStem=stemIndex(p[2].substring(0,1));
        Chart c=new Chart();c.pillars=p;c.dayMaster=STEMS[dayStem];c.dayElement=ELEMENT[dayStem];
        c.stemGods=new String[4];c.hiddenGods=new String[4];
        for(int i=0;i<4;i++){
            int stem=stemIndex(p[i].substring(0,1));c.stemGods[i]=i==2?"日主":tenGod(dayStem,stem);
            int branch=branchIndex(p[i].substring(1));StringBuilder h=new StringBuilder();
            for(String x:HIDDEN[branch]){if(h.length()>0)h.append('、');h.append(x).append('(').append(tenGod(dayStem,stemIndex(x))).append(')');}
            c.hiddenGods[i]=h.toString();
        }
        int monthBranch=branchIndex(p[1].substring(1));String main=HIDDEN[monthBranch][0];c.monthMain=main;c.monthGod=tenGod(dayStem,stemIndex(main));
        c.elementSummary=elementSummary(p);c.relationships=relationships(p);
        return c;
    }

    /** Exact solar-term pillars in the bundled calendar library (GMT+8). */
    public static Chart fromSolar(int year,int month,int day,int hour,int minute) {
        return fromSolar(year,month,day,hour,minute,2);
    }

    /** sect=1 changes the day at 23:00; sect=2 keeps late Zi hour on the civil day. */
    public static Chart fromSolar(int year,int month,int day,int hour,int minute,int sect) {
        EightChar e=Solar.fromYmdHms(year,month,day,hour,minute,0).getLunar().getEightChar();
        e.setSect(sect==1?1:2);
        return chart(e.getYear(),e.getMonth(),e.getDay(),e.getTime());
    }

    /** Compare solar terms at the real Beijing instant; use corrected clock time for day/hour pillars. */
    public static Chart fromSolarAdjusted(int year,int month,int day,int hour,int minute,int sect,SolarAdjustment adjusted) {
        Chart actual=fromSolar(year,month,day,hour,minute,sect);
        if(adjusted==null)return actual;
        Chart local=fromSolar(adjusted.year,adjusted.month,adjusted.day,adjusted.hour,adjusted.minute,sect);
        return chart(actual.pillars[0],actual.pillars[1],local.pillars[2],local.pillars[3]);
    }

    private static String elementSummary(String[] p){int[] count=new int[5];String order="木火土金水";for(String x:p){count[order.indexOf(ELEMENT[stemIndex(x.substring(0,1))])]++;count[order.indexOf(BRANCH_ELEMENT[branchIndex(x.substring(1))])]++;}return "木"+count[0]+" · 火"+count[1]+" · 土"+count[2]+" · 金"+count[3]+" · 水"+count[4];}
    private static List<String> relationships(String[] p){ArrayList<String> out=new ArrayList<String>();String[] stemPairs={"甲己","乙庚","丙辛","丁壬","戊癸"};String[] branchPairs={"子丑六合","寅亥六合","卯戌六合","辰酉六合","巳申六合","午未六合","子午六冲","丑未六冲","寅申六冲","卯酉六冲","辰戌六冲","巳亥六冲","子未六害","丑午六害","寅巳六害","卯辰六害","申亥六害","酉戌六害"};for(int i=0;i<4;i++)for(int j=i+1;j<4;j++){String stems=p[i].substring(0,1)+p[j].substring(0,1);for(String pair:stemPairs)if(samePair(stems,pair))out.add(POS[i]+"与"+POS[j]+"天干相合（"+pair+"合）");String branches=p[i].substring(1)+p[j].substring(1);for(String pair:branchPairs){String key=pair.substring(0,2);if(samePair(branches,key))out.add(POS[i]+"与"+POS[j]+"地支"+pair.substring(2)+"（"+key+"）");}}String all=p[0].substring(1)+p[1].substring(1)+p[2].substring(1)+p[3].substring(1);String[] triples={"申子辰三合水局","亥卯未三合木局","寅午戌三合火局","巳酉丑三合金局","寅卯辰三会木方","巳午未三会火方","申酉戌三会金方","亥子丑三会水方"};for(String t:triples){String keys=t.substring(0,3);if(all.indexOf(keys.substring(0,1))>=0&&all.indexOf(keys.substring(1,2))>=0&&all.indexOf(keys.substring(2,3))>=0)out.add(t);}if(out.size()==0)out.add("四柱表层未形成六合、六冲、六害或完整三合三会；不代表没有其他作用关系");return out;}
    private static boolean samePair(String value,String pair){return value.equals(pair)||value.equals(new StringBuilder(pair).reverse().toString());}

    /** Civil-year label for the picker, sampled after Li Chun to avoid boundary ambiguity. */
    public static String civilYearGanZhi(int year) {
        EightChar e=Solar.fromYmdHms(year,7,1,12,0,0).getLunar().getEightChar();
        return e.getYear();
    }

    /** Apparent solar time from China Standard Time using longitude and the equation of time. */
    public static SolarAdjustment solarTime(int year,int month,int day,int hour,int minute,double longitude){if(Double.isNaN(longitude)||Double.isInfinite(longitude)||longitude< -180D||longitude>180D)throw new IllegalArgumentException("经度超出范围");TimeZone zone=TimeZone.getTimeZone("GMT+08:00");GregorianCalendar c=new GregorianCalendar(zone,Locale.CHINA);c.setLenient(false);c.set(year,month-1,day,hour,minute,0);c.set(Calendar.MILLISECOND,0);c.getTime();int n=c.get(Calendar.DAY_OF_YEAR);double b=2D*Math.PI*(n-81D)/364D;double equation=9.87D*Math.sin(2D*b)-7.53D*Math.cos(b)-1.5D*Math.sin(b);int correction=(int)Math.round(4D*(longitude-120D)+equation);c.add(Calendar.MINUTE,correction);SolarAdjustment a=new SolarAdjustment();a.year=c.get(Calendar.YEAR);a.month=c.get(Calendar.MONTH)+1;a.day=c.get(Calendar.DAY_OF_MONTH);a.hour=c.get(Calendar.HOUR_OF_DAY);a.minute=c.get(Calendar.MINUTE);a.longitude=longitude;a.equationMinutes=equation;a.correctionMinutes=correction;return a;}

    /** Minute-precision Jie-based luck-cycle calculation: 4320 minutes equals one year. */
    public static Fortune fortune(int year,int month,int day,int hour,int minute,int gender,int daySect){if(gender!=0&&gender!=1)throw new IllegalArgumentException("性别参数不正确");EightChar e=Solar.fromYmdHms(year,month,day,hour,minute,0).getLunar().getEightChar();e.setSect(daySect==1?1:2);Yun y=e.getYun(gender,2);Fortune f=new Fortune();f.forward=y.isForward();f.startYear=y.getStartYear();f.startMonth=y.getStartMonth();f.startDay=y.getStartDay();f.startHour=y.getStartHour();f.startSolar=y.getStartSolar().toYmdHms();ArrayList<String> rows=new ArrayList<String>();DaYun[] cycles=y.getDaYun(9);for(DaYun d:cycles){if(d.getIndex()==0)continue;rows.add(d.getGanZhi()+"　"+d.getStartYear()+"—"+d.getEndYear()+"　"+d.getStartAge()+"—"+d.getEndAge()+"岁");if(rows.size()==8)break;}f.cycles=rows;return f;}

    public static List<Rule> rules(android.content.Context context,Chart chart)throws Exception{
        byte[] raw;try(InputStream in=context.getAssets().open("appdata/bazi-ziping-rules.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);raw=out.toByteArray();}
        JSONObject root=new JSONObject(new String(raw,StandardCharsets.UTF_8));JSONArray rows=root.getJSONArray("rules");ArrayList<Rule> result=new ArrayList<Rule>();
        for(int i=0;i<rows.length();i++){JSONObject o=rows.getJSONObject(i);String applies=o.getString("applies");if("all".equals(applies)||contains(applies,chart.monthGod))result.add(new Rule(o));}
        return result;
    }

    /** Plain-language observations derived only from facts already present in the chart. */
    public static List<Insight> insights(Chart c){
        ArrayList<Insight> out=new ArrayList<Insight>();
        out.add(new Insight("month","先从一件生活中的事看起",theme(c.monthGod),
            "这是传统命理给出的观察角度，可以拿自己的实际情况对照；它不是对你性格或经历的判定。",
            "月柱"+c.pillars[1]+"；月支本气"+c.monthMain+"，相对日主"+c.dayMaster+"为"+c.monthGod+"。",
            "《子平真诠》先看出生月份所对应的“月令”，这只是阅读全盘的入口，还不能单独判断好坏。",
            themeQuestion(c.monthGod)));
        // Match the same comparison used by Chart.evidence() and json(): the day stem
        // is the reference, so report it separately rather than as an exposed match.
        StringBuilder positions=new StringBuilder();for(int i=0;i<4;i++)if(i!=2&&c.monthMain.equals(c.pillars[i].substring(0,1))){if(positions.length()>0)positions.append('、');positions.append(POS[i]);}
        boolean exposed=positions.length()>0,sameDay=c.monthMain.equals(c.dayMaster);
        String exposureSummary=exposed?"月份地支里最主要的字“"+c.monthMain+"”，也出现在"+positions+"的上方"+(sameDay?"；日柱上方也是这个字。":"。"):
            sameDay?"月份地支里最主要的字“"+c.monthMain+"”，与日柱上方的字相同；年、月、时柱上方没有同字。":"月份地支里最主要的字“"+c.monthMain+"”，没有出现在四柱上方。";
        String exposureMeaning=exposed?"年、月、时柱上方的同字是一条阅读线索；还要结合其他字与全盘，不能单凭这一项下结论。":
            sameDay?"日柱上方的字是分析参照，不计入年、月、时柱的同字匹配；仍需要结合全盘理解。":"这个字仍在月份地支中；上方没有同字，不等于缺少这项信息，还需要结合全盘理解。";
        out.add(new Insight("exposure",exposed?"出生月份的线索，在哪里出现":"出生月份的线索，需要继续找",
            exposureSummary,exposureMeaning,
            "四柱天干依次为"+c.pillars[0].substring(0,1)+"、"+c.pillars[1].substring(0,1)+"、"+c.pillars[2].substring(0,1)+"、"+c.pillars[3].substring(0,1)+"；月令本气为"+c.monthMain+"。",
            "这里只比较字的位置；日干是分析参照，即使字相同也不自动等于格神透出，更不能仅凭此断强弱或吉凶。",
            "这些字之间有哪些关系？请用生活中的例子解释。"));
        int[] counts=surfaceCounts(c.pillars);String order="木火土金水";int max=0;for(int n:counts)if(n>max)max=n;StringBuilder many=new StringBuilder(),missing=new StringBuilder();for(int i=0;i<5;i++){if(counts[i]==max){if(many.length()>0)many.append('、');many.append(order.charAt(i));}if(counts[i]==0){if(missing.length()>0)missing.append('、');missing.append(order.charAt(i));}}
        String distribution="表层出现较多的是"+many+"（各"+max+"处）"+(missing.length()>0?"；未直接出现的是"+missing:"；五行在表层均有出现")+"。";
        out.add(new Insight("elements","先认五行分布，不急着判强弱",distribution,
            "这是把四柱上方的四个字与下方四个字的主五行分别数一遍，用来认识排盘的组成。",
            "四个天干与四个地支主五行合计："+c.elementSummary+"。",
            "这里未加入藏干权重、月令旺衰、通根和制化，因此不是喜忌或强弱评分。",
            "这个五行统计是怎么来的？有什么不能据此判断？"));
        String relation=c.relationships.get(0);boolean none=relation.indexOf("未形成")>=0;
        out.add(new Insight("relations",none?"表层没有醒目的合冲组合":"先留意一条明确的干支关系",
            none?"目前没有发现本版列出的合、冲、害或完整三合三会组合。":relation+"。",
            none?"就像这次没有找到特定配对，不代表四柱中没有其他值得研究的关系。":"这是对两个或多个干支组合的传统命名。先把它当作一条需要结合全盘核对的线索，不必看到“合”就认为顺利，看到“冲”或“害”就担心。",
            "本机逐柱配对结果："+join(c.relationships,"；")+"。",
            "合不必然好，冲也不必然坏；是否成立、如何表现仍要看全局条件。",
            "我正在考虑工作与合作，这条组合有哪些需要结合现实核对的地方？"));
        return out;
    }

    private static int[] surfaceCounts(String[] pillars){int[] count=new int[5];String order="木火土金水";for(String x:pillars){count[order.indexOf(ELEMENT[stemIndex(x.substring(0,1))])]++;count[order.indexOf(BRANCH_ELEMENT[branchIndex(x.substring(1))])]++;}return count;}
    private static String join(List<String> rows,String separator){StringBuilder out=new StringBuilder();for(String row:rows){if(out.length()>0)out.append(separator);out.append(row);}return out.toString();}
    private static String theme(String god){if("比肩".equals(god))return "可以从独立做决定与和别人协作之间如何取舍开始。";if("劫财".equals(god))return "可以从与人合作时如何分工、分配资源开始。";if("食神".equals(god))return "可以从怎样运用自己的技能、安排工作节奏开始。";if("伤官".equals(god))return "可以从提出新想法时，怎样兼顾表达与规则开始。";if("正财".equals(god))return "可以从如何安排日常工作、时间和已有资源开始。";if("偏财".equals(god))return "可以从面对新机会时，如何比较收益、成本和风险开始。";if("正官".equals(god))return "可以从如何承担责任、安排长期目标和规则开始。";if("七杀".equals(god))return "可以从面对压力时，如何分清轻重、安排下一步开始。";if("正印".equals(god))return "可以从怎样学习新知识、获得他人支持开始。";return "可以从怎样筛选信息、建立适合自己的学习方法开始。";}
    private static String themeQuestion(String god){if("比肩".equals(god)||"劫财".equals(god))return "面对目前的合作，我该怎样考虑分工和边界？";if("食神".equals(god)||"伤官".equals(god))return "我想发挥已有技能或新想法，可以先从哪一步做起？";if("正财".equals(god)||"偏财".equals(god))return "面对眼前的工作或机会，我应如何安排时间与资源？";if("正官".equals(god)||"七杀".equals(god))return "面对目前的责任或压力，我该如何安排下一步？";return "我想提升一项能力，该怎样选择学习方法和支持？";}

    private static boolean contains(String csv,String value){for(String item:csv.split(","))if(item.equals(value))return true;return false;}
    public static String tenGod(int day,int other){int relation=relation(ELEMENT[day],ELEMENT[other]);boolean same=YANG[day]==YANG[other];if(relation==0)return same?"比肩":"劫财";if(relation==1)return same?"食神":"伤官";if(relation==2)return same?"偏财":"正财";if(relation==3)return same?"七杀":"正官";return same?"偏印":"正印";}
    private static int relation(String me,String other){String order="木火土金水";int a=order.indexOf(me),b=order.indexOf(other);if(a==b)return 0;if((a+1)%5==b)return 1;if((a+2)%5==b)return 2;if((b+2)%5==a)return 3;return 4;}
    private static String valid(String value){if(value==null||value.length()!=2)throw new IllegalArgumentException("四柱格式不正确");String[] cycle=cycle();for(String x:cycle)if(x.equals(value))return value;throw new IllegalArgumentException("干支组合不在六十甲子中");}
    private static int stemIndex(String x){for(int i=0;i<STEMS.length;i++)if(STEMS[i].equals(x))return i;throw new IllegalArgumentException();}
    private static int branchIndex(String x){for(int i=0;i<BRANCHES.length;i++)if(BRANCHES[i].equals(x))return i;throw new IllegalArgumentException();}

    public static final class Chart {
        public String[] pillars,stemGods,hiddenGods;public String dayMaster,dayElement,monthMain,monthGod,elementSummary;public List<String> relationships;
        public String key(){return pillars[0]+pillars[1]+pillars[2]+pillars[3];}
        public String summary(){return "年 "+pillars[0]+"　月 "+pillars[1]+"　日 "+pillars[2]+"　时 "+pillars[3]+"\n日主 "+dayMaster+dayElement+" · 月令本气 "+monthMain+"（"+monthGod+"）";}
        public String evidence(){String[] pos={"年柱","月柱","日柱","时柱"};StringBuilder out=new StringBuilder("日柱").append(pillars[2]).append("，日主").append(dayMaster).append(dayElement).append("。月柱").append(pillars[1]).append("，月令本气").append(monthMain).append("为").append(monthGod).append("。\n");for(int i=0;i<4;i++)out.append(pos[i]).append(pillars[i]).append("：天干").append(stemGods[i]).append("；藏干").append(hiddenGods[i]).append("。\n");StringBuilder exposed=new StringBuilder();for(int i=0;i<4;i++)if(i!=2&&monthMain.equals(pillars[i].substring(0,1))){if(exposed.length()>0)exposed.append('、');exposed.append(pos[i]).append("天干");}boolean sameDay=monthMain.equals(dayMaster);out.append("月令本气").append(monthMain).append(exposed.length()>0?"见于"+exposed:sameDay?"仅与日干相同，年、月、时天干未见同字":"未见于四柱天干");if(exposed.length()>0&&sameDay)out.append("，也与日干相同");out.append("；日干为参照点，不计入年、月、时天干的透出匹配。此项仅比较字面，不能单独确认格局。\n表层五行计数：").append(elementSummary).append("。\n干支关系：");for(String r:relationships)out.append(r).append("；");return out.toString();}
        /** month_main_exposed compares year/month/hour stems only; the day stem is the reference, not an independent exposed stem. */
        public JSONObject json()throws Exception{JSONArray p=new JSONArray(),s=new JSONArray(),h=new JSONArray(),r=new JSONArray();for(int i=0;i<4;i++){p.put(pillars[i]);s.put(stemGods[i]);h.put(hiddenGods[i]);}for(String x:relationships)r.put(x);return new JSONObject().put("system","bazi").put("pillars",p).put("day_master",dayMaster+dayElement).put("stem_ten_gods",s).put("hidden_stems",h).put("month_main",monthMain).put("month_ten_god",monthGod).put("surface_element_count",elementSummary).put("stem_branch_relations",r).put("month_main_exposed",monthMain.equals(pillars[0].substring(0,1))||monthMain.equals(pillars[1].substring(0,1))||monthMain.equals(pillars[3].substring(0,1))).put("month_main_matches_day_master",monthMain.equals(dayMaster)).put("month_main_exposed_meaning","年、月、时天干中有同字，仅作字面核对，不含日干，不代表自动成格");}
    }
    public static final class Rule {public final String id,title,text,source;Rule(JSONObject o)throws Exception{id=o.getString("id");title=o.getString("title");text=o.getString("text");source=o.getString("source");}}
    public static final class Insight {public final String id,title,summary,meaning,evidence,boundary,question;Insight(String i,String t,String s,String m,String e,String b,String q){id=i;title=t;summary=s;meaning=m;evidence=e;boundary=b;question=q;}public JSONObject json()throws Exception{return new JSONObject().put("id",id).put("title",title).put("plain_observation",summary).put("meaning",meaning).put("chart_evidence",evidence).put("boundary",boundary).put("suggested_question",question);}}
    public static final class Fortune {public boolean forward;public int startYear,startMonth,startDay,startHour;public String startSolar;public List<String> cycles;public String startText(){return (forward?"顺排":"逆排")+" · 出生后约"+startYear+"年"+startMonth+"月"+startDay+"天"+startHour+"小时起运 · 交运时间 "+startSolar;}public String text(){StringBuilder out=new StringBuilder(startText());for(String x:cycles)out.append('\n').append(x);return out.toString();}}
    public static final class SolarAdjustment {public int year,month,day,hour,minute,correctionMinutes;public double longitude,equationMinutes;public String time(){return String.format(Locale.CHINA,"%04d-%02d-%02d %02d:%02d",year,month,day,hour,minute);}public String detail(){return String.format(Locale.CHINA,"经度 %.4f° · 经度差与均时差合计 %+d 分钟 · 真太阳时 %s",longitude,correctionMinutes,time());}}
}
