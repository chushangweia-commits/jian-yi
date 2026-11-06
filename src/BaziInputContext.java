package com.jianyi.yixue.app;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Conservative classification for both recent records and the older text-only records. */
public final class BaziInputContext {
    private BaziInputContext() {}
    public static String timeBasis(String source) {
        String s=source==null?"":source;
        if(s.contains("手动四柱"))return "manual_four_pillars";
        if(s.contains("未校正真太阳时"))return "beijing_standard";
        if(s.contains("真太阳时")&&s.contains("经度"))return "apparent_solar";
        if(s.contains("公历")&&s.contains("东八区"))return "legacy_beijing";
        return "unknown";
    }
    public static String display(String basis) {
        if("beijing_standard".equals(basis)||"legacy_beijing".equals(basis))return "按北京时间排盘；这是已选择的时间口径。";
        if("apparent_solar".equals(basis))return "已按填写的经度进行真太阳时校正。";
        if("manual_four_pillars".equals(basis))return "使用手动提供的四柱；出生时间口径由录入者确认。";
        return "旧记录没有完整保存时间口径；本次按保存的四柱解读。";
    }
    public static boolean needsSolarRecalculation(String source) {
        return "apparent_solar".equals(timeBasis(source)) && (source==null||!source.contains("年月按北京时间节气"));
    }
    /** Advisory checks only: 23:00 sect conventions can affect the day/hour pairing. */
    public static String manualWarnings(String[] p) {
        if(p==null||p.length!=4)return "";
        String stems="甲乙丙丁戊己庚辛壬癸",branches="子丑寅卯辰巳午未申酉戌亥";
        int year=stems.indexOf(p[0].charAt(0)),monthStem=stems.indexOf(p[1].charAt(0));
        int monthBranch=branches.indexOf(p[1].charAt(1)),day=stems.indexOf(p[2].charAt(0));
        int hourStem=stems.indexOf(p[3].charAt(0)),hourBranch=branches.indexOf(p[3].charAt(1));
        if(year<0||monthStem<0||monthBranch<0||day<0||hourStem<0||hourBranch<0)return "";
        StringBuilder out=new StringBuilder();
        if((year%5*2+2+(monthBranch-2+12)%12)%10!=monthStem)
            out.append("年柱和月柱的天干配对与常用排盘口径不同。");
        if((day%5*2+hourBranch)%10!=hourStem){
            if(out.length()>0)out.append("\n");
            out.append("日柱和时柱的天干配对与常用排盘口径不同；子时换日等口径可能影响这一项。");
        }
        return out.toString();
    }
    public static String storageKey(String chart,String source,String gender,String place) {
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            String[] fields={chart,source,gender,place};
            for(String field:fields){byte[] bytes=(field==null?"":field).getBytes(StandardCharsets.UTF_8);
                md.update((byte)(bytes.length>>>24));md.update((byte)(bytes.length>>>16));md.update((byte)(bytes.length>>>8));md.update((byte)bytes.length);md.update(bytes);}
            StringBuilder out=new StringBuilder("context_");for(byte b:md.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
        } catch(Exception e){throw new IllegalStateException(e);}
    }
}
