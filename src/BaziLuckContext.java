package com.jianyi.yixue.app;

import com.nlf.calendar.EightChar;
import com.nlf.calendar.Solar;
import com.nlf.calendar.eightchar.DaYun;
import com.nlf.calendar.eightchar.Yun;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Rebuild timing only from a narrowly recognised app-generated input description. */
public final class BaziLuckContext {
    private static final String DATE="([0-9]{4})-([0-9]{2})-([0-9]{2}) ([0-9]{2}):([0-9]{2})";
    private static final String BOUNDARY="(晚子时按当天（23:00不换日）|子初换日（23:00换日）|晚子时日柱按当天)";
    private static final String NEW_RULE="年月按北京时间节气，日时按所选时间";
    private static final Pattern CURRENT=Pattern.compile("^北京时间 "+DATE+" · (.+?) · ("+NEW_RULE+"|节气交接) · "+BOUNDARY+"$");
    private static final Pattern LEGACY=Pattern.compile("^公历 "+DATE+" · 东八区 · 节气交接 · "+BOUNDARY+"$");
    private static final Pattern SOLAR=Pattern.compile("^经度 (-?[0-9]{1,3}\\.[0-9]{4})° · 经度差与均时差合计 [+-][0-9]+ 分钟 · 真太阳时 [0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}$");
    private BaziLuckContext() {}

    /** Never persists, changes the chart, or replaces its saved text. */
    public static JSONObject build(BaziEngine.Chart chart,String source,String gender,String savedFortune) {
        return buildAt(chart,source,gender,savedFortune,System.currentTimeMillis());
    }

    /** Injected absolute instant enables deterministic tests of the right-open boundaries. */
    public static JSONObject buildAt(BaziEngine.Chart chart,String source,String gender,String savedFortune,long nowMillis) {
        try {
            if(chart==null)return unavailable("命盘数据不完整，请重新打开或重新排盘。");
            if(source==null)return unavailable("当前命盘缺少可核对的出生日期、具体时分及性别，请补充后重新排盘。");
            Matcher currentSource=CURRENT.matcher(source),legacy=LEGACY.matcher(source);
            boolean modern=currentSource.matches(),old=legacy.matches();
            if(!modern&&!old)return unavailable("这张命盘缺少可核对的出生日期、具体时分或换日口径。请补充出生日期、具体时分和性别后重新排盘。");
            final int genderCode;
            if("男".equals(gender))genderCode=1;
            else if("女".equals(gender))genderCode=0;
            else return unavailable("当前未指定用于起运顺逆的性别，请重新输入出生资料并选择后排盘。");
            Matcher m=modern?currentSource:legacy;
            int year=Integer.parseInt(m.group(1)),month=Integer.parseInt(m.group(2)),day=Integer.parseInt(m.group(3));
            int hour=Integer.parseInt(m.group(4)),minute=Integer.parseInt(m.group(5));
            if(year<1900||year>2100)return unavailable("出生日期超出当前排盘支持范围，请核对后重新排盘。");
            GregorianCalendar date=new GregorianCalendar(TimeZone.getTimeZone("GMT+08:00"),Locale.CHINA);
            date.clear();date.setLenient(false);date.set(year,month-1,day,hour,minute,0);date.getTime();
            String selection=modern?m.group(6):"未校正真太阳时";
            String method=modern?m.group(7):"节气交接";
            String boundary=modern?m.group(8):m.group(6);
            int sect=boundary.startsWith("子初")?1:2;
            String basis="北京时间";
            BaziEngine.SolarAdjustment adjusted=null;
            if(!"未校正真太阳时".equals(selection)) {
                Matcher solar=SOLAR.matcher(selection);
                if(!solar.matches())return unavailable("旧盘的校时信息无法核对，请确认出生地经度后重新排盘。");
                if(!NEW_RULE.equals(method))return unavailable("旧版真太阳时排盘的节气口径已调整，请重新排盘后查看大运。");
                double longitude=Double.parseDouble(solar.group(1));
                adjusted=BaziEngine.solarTime(year,month,day,hour,minute,longitude);
                if(!selection.equals(adjusted.detail()))return unavailable("保存的校时数据与重新计算结果不一致，请重新排盘。");
                basis="真太阳时（年月按北京时间节气，日时按校正时间）";
            }
            BaziEngine.Chart expected=BaziEngine.fromSolarAdjusted(year,month,day,hour,minute,sect,adjusted);
            for(int i=0;i<4;i++)if(!expected.pillars[i].equals(chart.pillars[i]))
                return unavailable("出生资料重新计算的四柱与保存命盘不同，请核对出生资料并重新排盘。");

            BaziEngine.Fortune computed=BaziEngine.fortune(year,month,day,hour,minute,genderCode,sect);
            boolean hasSaved=savedFortune!=null&&!savedFortune.trim().isEmpty();
            if(hasSaved&&!computed.text().equals(savedFortune))
                return unavailable("保存的大运与当前算法重新计算结果不一致，原记录已保留；请核对资料后重新排盘。");

            EightChar eight=Solar.fromYmdHms(year,month,day,hour,minute,0).getLunar().getEightChar();
            eight.setSect(sect);
            Yun yun=eight.getYun(genderCode,2);
            if(yun.isForward()!=computed.forward||!yun.getStartSolar().toYmdHms().equals(computed.startSolar))
                return unavailable("起运数据核对失败，请重新排盘。");
            DaYun[] all=yun.getDaYun(9);
            JSONArray cycles=new JSONArray();
            for(DaYun cycle:all) {
                int i=cycle.getIndex();if(i<=0)continue;if(i>8)break;
                BaziEngine.Chart relation=BaziEngine.chart("甲子","甲子",chart.pillars[2],cycle.getGanZhi());
                Solar start=yun.getStartSolar().nextYear((i-1)*10);
                Solar next=yun.getStartSolar().nextYear(i*10);
                cycles.put(new JSONObject().put("index",i).put("ganzhi",cycle.getGanZhi())
                    .put("start_time",start.toYmdHms()).put("next_start_time",next.toYmdHms())
                    .put("year_index_start",cycle.getStartYear()).put("year_index_end",cycle.getEndYear())
                    .put("nominal_age_start",cycle.getStartAge()).put("nominal_age_end",cycle.getEndAge())
                    .put("stem_ten_god",relation.stemGods[3]).put("branch_hidden_stems_ten_gods",relation.hiddenGods[3]));
            }
            if(cycles.length()!=8)return unavailable("大运步数不完整，请重新排盘。");
            String asOf=beijingTime(nowMillis);
            JSONObject current=new JSONObject().put("as_of_beijing",asOf);
            String first=cycles.getJSONObject(0).getString("start_time");
            String last=cycles.getJSONObject(7).getString("next_start_time");
            if(asOf.compareTo(first)<0)current.put("status","before_start").put("description","当前系统时间早于本机计算的起运时间。");
            else if(asOf.compareTo(last)>=0)current.put("status","after_eighth").put("description","当前系统时间已超出本机列出的八步大运，未计算后续大运。");
            else {
                boolean found=false;
                for(int i=0;i<cycles.length();i++){JSONObject cycle=cycles.getJSONObject(i);if(asOf.compareTo(cycle.getString("start_time"))>=0&&asOf.compareTo(cycle.getString("next_start_time"))<0){current.put("status","within_cycle").put("description","根据本机交运时刻判断当前所处大运。").put("index",cycle.getInt("index")).put("ganzhi",cycle.getString("ganzhi")).put("start_time",cycle.getString("start_time")).put("next_start_time",cycle.getString("next_start_time"));found=true;break;}}
                if(!found)current.put("status","unknown").put("description","当前大运未能核对，不作推断。");
            }
            JSONObject offset=new JSONObject().put("years",computed.startYear).put("months",computed.startMonth)
                .put("days",computed.startDay).put("hours",computed.startHour);
            return new JSONObject().put("available",true).put("reason","")
                .put("birth_beijing",String.format(Locale.CHINA,"%04d-%02d-%02d %02d:%02d",year,month,day,hour,minute))
                .put("time_basis",basis).put("day_boundary",boundary)
                .put("method_note","按实际北京时间出生时刻与前后节的分钟差计算，采用三天折一年；年、月、日、小时为本机换算口径。")
                .put("direction",computed.forward?"顺排":"逆排").put("start_offset",offset)
                .put("first_start_time",computed.startSolar)
                .put("boundary_rule","每步按本机交运时刻起算十年；start_time 含起点，next_start_time 为下一步开始时刻（不含终点），均为北京时间的模型计算结果，不是确定事件时间。")
                .put("age_rule","年龄按大运起始公历年减出生公历年加一，为按年计的虚岁索引，并非实际周岁；年份区间是索引，不代表每年1月1日切运。")
                .put("saved_fortune_checked",hasSaved).put("cycles",cycles).put("current_cycle",current);
        } catch(Exception e) {
            return unavailable("出生日期、时间或已有大运无法核对，请重新排盘后再试。");
        }
    }

    private static String beijingTime(long timeMillis) {
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA);
        format.setTimeZone(TimeZone.getTimeZone("GMT+08:00"));return format.format(new Date(timeMillis));
    }

    private static JSONObject unavailable(String reason) {
        JSONObject out=new JSONObject();
        try{out.put("available",false);out.put("reason",reason);}catch(Exception ignored){}
        return out;
    }
}
