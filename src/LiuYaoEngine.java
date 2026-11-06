package com.jianyi.yixue.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure local Wenwang-gua chart calculation. Arrays and line positions are bottom-first.
 * School-dependent conclusions are labelled as candidates or clues.
 */
public final class LiuYaoEngine {
    private static final String[] TRIGRAMS={"","乾","兑","离","震","巽","坎","艮","坤"};
    private static final String[] ELEMENT={"","金","金","火","木","木","水","土","土"};
    private static final String[] BRANCH={"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
    private static final String[] BRANCH_ELEMENT={"水","土","木","木","土","火","火","土","金","金","土","水"};
    private static final String[] GODS={"青龙","朱雀","勾陈","螣蛇","白虎","玄武"};
    private static final String[] STEM={"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
    private static final String[] RELATIVES={"兄弟","子孙","妻财","官鬼","父母"};
    private static final String[][] INNER_BRANCH={{},{"子","寅","辰"},{"巳","卯","丑"},{"卯","丑","亥"},{"子","寅","辰"},{"丑","亥","酉"},{"寅","辰","午"},{"辰","午","申"},{"未","巳","卯"}};
    private static final String[][] OUTER_BRANCH={{},{"午","申","戌"},{"亥","酉","未"},{"酉","未","巳"},{"午","申","戌"},{"未","巳","卯"},{"申","戌","子"},{"戌","子","寅"},{"丑","亥","酉"}};
    private static final String[] INNER_STEM={"","甲","丁","己","庚","辛","戊","丙","乙"};
    private static final String[] OUTER_STEM={"","壬","丁","己","庚","辛","戊","丙","癸"};
    private static final String[][] HARMONY_GROUPS={{"申","子","辰"},{"亥","卯","未"},{"寅","午","戌"},{"巳","酉","丑"}};
    private static final String[] HARMONY_ELEMENTS={"水","木","火","金"};

    private LiuYaoEngine(){}

    public static final class Line {
        public int position, value;
        public boolean yang, moving, voided, monthBroken, dayClashed, darkMovingCandidate;
        public String stemBranch, branch, element, relative, god, marker;
        public String changedStemBranch, changedBranch, changedElement, changedRelative;
        public String monthState, changedMonthState, monthRelation, dayRelation;
        public String transformation, hiddenStemBranch="", hiddenElement="", hiddenRelative="";
        public String ruleFacts="", timingHint="";
    }

    public static final class Result {
        public int upper,lower,changedUpper,changedLower,stage,shi,ying;
        public String palace,palaceElement,monthGanZhi,dayGanZhi,kong;
        public String monthBranch,dayBranch,monthElement;
        public String threeHarmonySummary,timingSummary;
        public String ruleScope="旺相休囚按月支五行；暗动仅标记月令旺/相、非空破的静爻日冲；三合只识别支齐，不自动断成局；应期只列填实、冲空、合破和值临等触发支。墓绝采用水土同宫口径。";
        public Line[] lines=new Line[6];
    }

    public static final class UseGodHint {
        public final String primary,secondary,basis;
        public final boolean needsConfirmation;
        private UseGodHint(String primary,String secondary,String basis,boolean needsConfirmation){
            this.primary=primary;this.secondary=secondary;this.basis=basis;this.needsConfirmation=needsConfirmation;
        }
        public String display(){
            return "主看："+primary+(secondary.length()>0?"　辅看："+secondary:"")+"\n依据："+basis
                +(needsConfirmation?"\n请先确认人物关系或事项类别，再采用该用神。":"");
        }
    }

    /**
     * Transparent topic-to-use-god guidance. It never replaces the user's confirmation.
     * More specific person/health rules are evaluated before generic money or career words.
     */
    public static UseGodHint suggestUseGod(String question){
        String q=question==null?"":question.trim();
        if(containsAny(q,new String[]{"妻子","老婆","女友","女朋友"}))return useGod("妻财","世爻、应爻","男问伴侣，传统以妻财为主并核对世应。",false);
        if(containsAny(q,new String[]{"丈夫","老公","男友","男朋友"}))return useGod("官鬼","世爻、应爻","女问伴侣，传统以官鬼为主并核对世应。",false);
        if(containsAny(q,new String[]{"婚姻","感情","恋爱","对象","姻缘"}))return useGod("妻财或官鬼","世爻、应爻","伴侣类问题须先确认问卦者角色，不能仅凭“感情”自动定一个六亲。",true);
        if(containsAny(q,new String[]{"父亲","母亲","父母","爷爷","奶奶","长辈","老师","师长"}))return useGod("父母","世爻、应爻","所问对象属于父母长辈或师长类。",false);
        if(containsAny(q,new String[]{"孩子","儿子","女儿","子女","怀孕","生产","宠物"}))return useGod("子孙","世爻、应爻","所问对象属于子女、孕育或所养之物类。",false);
        if(containsAny(q,new String[]{"兄弟","姐妹","哥哥","弟弟","姐姐","妹妹","朋友","同事","竞争","合伙"}))return useGod("兄弟","世爻、应爻","所问对象属于同辈、朋友、同事或竞争合伙类。",false);
        if(containsAny(q,new String[]{"健康","疾病","生病","手术","康复","身体","病情"}))return useGod("世爻","官鬼（病因）、子孙（医药）","问本人健康先看世爻；官鬼与子孙只作病因、医药辅助层。",false);
        if(containsAny(q,new String[]{"考试","录取","证书","合同","文件","文书","房子","房屋","车辆","车子","学业"}))return useGod("父母","官鬼（名次/职位）","文书、学业、房屋车辆类先取父母；考试求名可辅看官鬼。",false);
        if(containsAny(q,new String[]{"工作","求职","入职","升职","晋升","职位","事业","官司","诉讼"}))return useGod("官鬼","世爻、父母","职位事业或诉讼类以官鬼为主，文书材料辅看父母。",false);
        if(containsAny(q,new String[]{"钱","财","收入","工资","生意","买卖","投资","收益","货物","回款"}))return useGod("妻财","世爻、兄弟","财务与交易类以妻财为主，兄弟只作竞争或耗财关系辅助。",false);
        if(containsAny(q,new String[]{"自己","本人","出行","行程","去留","选择","计划"}))return useGod("世爻","应爻","问自身处境、去留或行动，先看世爻及其与应爻关系。",false);
        return useGod("待确认","世爻、应爻","问题关键词不足以可靠映射六亲，请从所问人物或事项类别手动确认。",true);
    }

    private static UseGodHint useGod(String primary,String secondary,String basis,boolean needsConfirmation){
        return new UseGodHint(primary,secondary,basis,needsConfirmation);
    }

    public static Result calculate(int[] values,String monthGanZhi,String dayGanZhi){
        if(values==null||values.length!=6)throw new IllegalArgumentException("六爻数据不完整");
        int[] base=new int[6],changed=new int[6];
        for(int i=0;i<6;i++){
            if(values[i]<6||values[i]>9)throw new IllegalArgumentException("爻值应为6至9");
            base[i]=(values[i]==7||values[i]==9)?1:0;changed[i]=base[i];
            if(values[i]==6||values[i]==9)changed[i]^=1;
        }
        Result r=new Result();
        r.lower=MeihuaEngine.trigramId(Arrays.copyOfRange(base,0,3));
        r.upper=MeihuaEngine.trigramId(Arrays.copyOfRange(base,3,6));
        r.changedLower=MeihuaEngine.trigramId(Arrays.copyOfRange(changed,0,3));
        r.changedUpper=MeihuaEngine.trigramId(Arrays.copyOfRange(changed,3,6));
        int[] palaceStage=findPalace(base);int palaceId=palaceStage[0];r.stage=palaceStage[1];
        r.palace=TRIGRAMS[palaceId]+"宫";r.palaceElement=ELEMENT[palaceId];
        int[] shi={6,1,2,3,4,5,4,3};r.shi=shi[r.stage];r.ying=((r.shi+2)%6)+1;
        r.monthGanZhi=validGanZhi(monthGanZhi,"月建");r.dayGanZhi=validGanZhi(dayGanZhi,"日辰");
        r.monthBranch=last(r.monthGanZhi);r.dayBranch=last(r.dayGanZhi);r.monthElement=branchElement(r.monthBranch);
        r.kong=kong(r.dayGanZhi);
        int dayStem=indexOf(STEM,r.dayGanZhi.substring(0,1));
        int godStart=dayStem<=1?0:dayStem<=3?1:dayStem==4?2:dayStem==5?3:dayStem<=7?4:5;
        String[] baseSB=naJia(r.lower,r.upper), changedSB=naJia(r.changedLower,r.changedUpper);
        for(int i=0;i<6;i++){
            Line line=new Line();line.position=i+1;line.value=values[i];line.yang=base[i]==1;line.moving=base[i]!=changed[i];line.stemBranch=baseSB[i];
            line.branch=last(baseSB[i]);line.element=branchElement(line.branch);line.relative=relative(r.palaceElement,line.element);line.god=GODS[(godStart+i)%6];
            line.marker=i+1==r.shi?"世":i+1==r.ying?"应":"";line.changedStemBranch=changedSB[i];line.changedBranch=last(changedSB[i]);
            line.changedElement=branchElement(line.changedBranch);line.changedRelative=relative(r.palaceElement,line.changedElement);
            line.monthState=seasonalState(r.monthElement,line.element);line.changedMonthState=seasonalState(r.monthElement,line.changedElement);
            line.monthRelation=temporalRelation(line.branch,r.monthBranch,"月建");line.dayRelation=temporalRelation(line.branch,r.dayBranch,"日辰");
            line.voided=r.kong.indexOf(line.branch)>=0;line.monthBroken=isClash(line.branch,r.monthBranch);line.dayClashed=isClash(line.branch,r.dayBranch);
            line.darkMovingCandidate=!line.moving&&line.dayClashed&&!line.voided&&!line.monthBroken&&("旺".equals(line.monthState)||"相".equals(line.monthState));
            line.transformation=line.moving?transformation(line.branch,line.changedBranch,line.element):"";
            r.lines[i]=line;
        }
        attachHiddenSpirits(r,palaceId);
        for(int i=0;i<6;i++){
            Line line=r.lines[i];line.ruleFacts=ruleFacts(line,r.monthBranch,r.dayBranch);
            line.timingHint=timingHint(line);
        }
        r.threeHarmonySummary=threeHarmonySummary(r);
        r.timingSummary=timingSummary(r);
        return r;
    }

    private static void attachHiddenSpirits(Result r,int palaceId){
        boolean[] present=new boolean[RELATIVES.length];
        for(Line line:r.lines)present[indexOf(RELATIVES,line.relative)]=true;
        String[] palaceLines=naJia(palaceId,palaceId);
        for(int i=0;i<6;i++){
            String hiddenElement=branchElement(last(palaceLines[i]));
            String hiddenRelative=relative(r.palaceElement,hiddenElement);
            if(!present[indexOf(RELATIVES,hiddenRelative)]){
                r.lines[i].hiddenStemBranch=palaceLines[i];r.lines[i].hiddenElement=hiddenElement;r.lines[i].hiddenRelative=hiddenRelative;
            }
        }
    }

    private static String ruleFacts(Line line,String monthBranch,String dayBranch){
        List<String> facts=new ArrayList<String>();
        facts.add("月令"+line.monthState);
        if(line.monthRelation.length()>0)facts.add(line.monthRelation);
        if(line.dayRelation.length()>0)facts.add(line.dayRelation);
        if(line.voided)facts.add("旬空");
        if(line.monthBroken)facts.add("月破");
        if(line.dayClashed)facts.add("日冲");
        if(line.darkMovingCandidate)facts.add("暗动候选");
        String monthStage=tombOrAbsolute(line.element,monthBranch,"月");if(monthStage.length()>0)facts.add(monthStage);
        String dayStage=tombOrAbsolute(line.element,dayBranch,"日");if(dayStage.length()>0)facts.add(dayStage);
        if(line.transformation.length()>0)facts.add(line.transformation+"；变爻月令"+line.changedMonthState);
        if(line.hiddenRelative.length()>0)facts.add("伏神"+line.hiddenRelative+line.hiddenStemBranch+line.hiddenElement+"（飞神"+line.relative+line.stemBranch+"）");
        return join(facts," · ");
    }

    private static String timingHint(Line line){
        List<String> hints=new ArrayList<String>();
        if(line.voided)hints.add(line.branch+"填实或"+opposite(line.branch)+"冲空");
        if(line.monthBroken)hints.add(combineMate(line.branch)+"合破、"+line.branch+"填实或换月复核");
        if(line.moving){
            hints.add(line.branch+"值临/"+combineMate(line.branch)+"相合");
            if(!line.changedBranch.equals(line.branch))hints.add("变支"+line.changedBranch+"值临");
        }
        if(line.darkMovingCandidate)hints.add("日冲已触发暗动候选");
        return join(hints,"，");
    }

    private static String timingSummary(Result r){
        Set<String> hints=new LinkedHashSet<String>();
        for(Line line:r.lines)if(line.timingHint.length()>0)hints.add(positionName(line.position)+"（"+line.branch+"）："+line.timingHint);
        if(hints.isEmpty())return "本盘没有旬空、月破或动爻触发项；具体应期仍须结合占题与用神。";
        return join(new ArrayList<String>(hints),"\n")+"\n仅为触发支提示，不直接等同吉凶日期。";
    }

    private static String threeHarmonySummary(Result r){
        Set<String> base=new LinkedHashSet<String>();for(Line line:r.lines)base.add(line.branch);
        List<String> found=new ArrayList<String>();
        for(int i=0;i<HARMONY_GROUPS.length;i++){
            String[] group=HARMONY_GROUPS[i];int baseCount=0;for(String branch:group)if(base.contains(branch))baseCount++;
            if(baseCount==3){found.add(join(Arrays.asList(group),"")+"三支在本盘齐备（"+HARMONY_ELEMENTS[i]+"局线索）");continue;}
            Set<String> together=new LinkedHashSet<String>(base);together.add(r.monthBranch);together.add(r.dayBranch);
            boolean complete=true;for(String branch:group)if(!together.contains(branch))complete=false;
            if(complete&&baseCount>0){
                List<String> supplied=new ArrayList<String>();
                if(!base.contains(r.monthBranch)&&contains(group,r.monthBranch))supplied.add("月建"+r.monthBranch);
                if(!base.contains(r.dayBranch)&&contains(group,r.dayBranch))supplied.add("日辰"+r.dayBranch);
                found.add(join(Arrays.asList(group),"")+"由盘支与"+join(supplied,"、")+"补齐（"+HARMONY_ELEMENTS[i]+"局线索）");
            }
        }
        return found.isEmpty()?"未见三合三支齐备；不以两支直接判成局。":join(found,"\n")+"\n支齐只是结构事实，能否成局仍须结合动静、月日与用神。";
    }

    private static String transformation(String from,String to,String element){
        List<String> labels=new ArrayList<String>();
        if(isAdvance(from,to))labels.add("化进神");else if(isAdvance(to,from))labels.add("化退神");
        if(from.equals(to))labels.add("化同支（伏吟线索）");
        else{
            if(isCombine(from,to))labels.add("化合");
            if(isClash(from,to))labels.add("化冲（反吟线索）");
            if(isHarm(from,to))labels.add("化害");
            if(isPunishment(from,to))labels.add("化刑线索");
        }
        String stage=tombOrAbsolute(element,to,"化");if(stage.length()>0)labels.add(stage);
        return labels.isEmpty()?"动而不属进退、伏反、合冲刑害或墓绝":join(labels,"、");
    }

    private static String temporalRelation(String line,String temporal,String label){
        List<String> relations=new ArrayList<String>();
        if(line.equals(temporal))relations.add("临"+label);
        else{
            if(isCombine(line,temporal))relations.add("与"+label+"六合");
            if(isClash(line,temporal))relations.add("与"+label+"六冲");
            if(isHarm(line,temporal))relations.add("与"+label+"六害");
            if(isPunishment(line,temporal))relations.add("与"+label+"有刑线索");
        }
        if(line.equals(temporal)&&isSelfPunishment(line))relations.add("同支自刑线索");
        return join(relations,"、");
    }

    private static String seasonalState(String monthElement,String lineElement){
        if(monthElement.equals(lineElement))return"旺";
        if(produces(monthElement,lineElement))return"相";
        if(produces(lineElement,monthElement))return"休";
        if(controls(lineElement,monthElement))return"囚";
        return"死";
    }

    private static String tombOrAbsolute(String element,String branch,String prefix){
        String tomb="",absolute="";
        if("木".equals(element)){tomb="未";absolute="申";}
        else if("火".equals(element)){tomb="戌";absolute="亥";}
        else if("金".equals(element)){tomb="丑";absolute="寅";}
        else if("水".equals(element)||"土".equals(element)){tomb="辰";absolute="巳";}
        if(branch.equals(tomb))return prefix+"墓";
        if(branch.equals(absolute))return prefix+"绝";
        return"";
    }

    private static boolean isAdvance(String from,String to){
        return pair(from,to,"亥子")||pair(from,to,"寅卯")||pair(from,to,"巳午")||pair(from,to,"申酉")
            ||pair(from,to,"丑辰")||pair(from,to,"辰未")||pair(from,to,"未戌")||pair(from,to,"戌丑");
    }
    private static boolean isCombine(String a,String b){return unordered(a,b,"子丑")||unordered(a,b,"寅亥")||unordered(a,b,"卯戌")||unordered(a,b,"辰酉")||unordered(a,b,"巳申")||unordered(a,b,"午未");}
    private static boolean isClash(String a,String b){return unordered(a,b,"子午")||unordered(a,b,"丑未")||unordered(a,b,"寅申")||unordered(a,b,"卯酉")||unordered(a,b,"辰戌")||unordered(a,b,"巳亥");}
    private static boolean isHarm(String a,String b){return unordered(a,b,"子未")||unordered(a,b,"丑午")||unordered(a,b,"寅巳")||unordered(a,b,"卯辰")||unordered(a,b,"申亥")||unordered(a,b,"酉戌");}
    private static boolean isPunishment(String a,String b){
        if(a.equals(b))return isSelfPunishment(a);
        return bothIn(a,b,"寅巳申")||bothIn(a,b,"丑戌未")||unordered(a,b,"子卯");
    }
    private static boolean isSelfPunishment(String branch){return"辰午酉亥".indexOf(branch)>=0;}
    private static boolean pair(String a,String b,String value){return a.equals(value.substring(0,1))&&b.equals(value.substring(1,2));}
    private static boolean unordered(String a,String b,String value){return pair(a,b,value)||pair(b,a,value);}
    private static boolean bothIn(String a,String b,String group){return group.indexOf(a)>=0&&group.indexOf(b)>=0;}
    private static String opposite(String branch){return BRANCH[(indexOf(BRANCH,branch)+6)%12];}
    private static String combineMate(String branch){
        for(String pair:new String[]{"子丑","寅亥","卯戌","辰酉","巳申","午未"}){
            if(branch.equals(pair.substring(0,1)))return pair.substring(1,2);
            if(branch.equals(pair.substring(1,2)))return pair.substring(0,1);
        }
        return"";
    }

    private static int[] findPalace(int[] bits){
        for(int id=1;id<=8;id++)for(int stage=0;stage<8;stage++)if(Arrays.equals(bits,palaceBits(id,stage)))return new int[]{id,stage};
        throw new IllegalArgumentException("无法确定卦宫");
    }
    private static int[] palaceBits(int id,int stage){
        int[] tri=MeihuaEngine.trigramBits(id),b={tri[0],tri[1],tri[2],tri[0],tri[1],tri[2]};
        if(stage>=1)b[0]^=1;if(stage>=2)b[1]^=1;if(stage>=3)b[2]^=1;if(stage>=4)b[3]^=1;if(stage>=5)b[4]^=1;
        if(stage>=6)b[3]^=1;if(stage==7){b[0]=tri[0];b[1]=tri[1];b[2]=tri[2];}return b;
    }
    private static String[] naJia(int lower,int upper){String[] out=new String[6];for(int i=0;i<3;i++){out[i]=INNER_STEM[lower]+INNER_BRANCH[lower][i];out[i+3]=OUTER_STEM[upper]+OUTER_BRANCH[upper][i];}return out;}
    private static String relative(String palace,String line){if(palace.equals(line))return"兄弟";if(produces(palace,line))return"子孙";if(produces(line,palace))return"父母";if(controls(line,palace))return"官鬼";return"妻财";}
    private static boolean produces(String a,String b){return("木火土金水木").contains(a+b);}
    private static boolean controls(String a,String b){return("木土水火金木土水火金").contains(a+b);}
    private static String branchElement(String b){return BRANCH_ELEMENT[indexOf(BRANCH,b)];}
    private static String last(String value){return value.substring(value.length()-1);}
    private static String validGanZhi(String value,String label){
        if(value==null||value.length()<2)throw new IllegalArgumentException(label+"干支不完整");
        String normalized=value.substring(0,2);indexOf(STEM,normalized.substring(0,1));indexOf(BRANCH,normalized.substring(1,2));return normalized;
    }
    private static int indexOf(String[] values,String value){for(int i=0;i<values.length;i++)if(values[i].equals(value))return i;throw new IllegalArgumentException("未知干支或六亲："+value);}
    private static String kong(String day){int gan=indexOf(STEM,day.substring(0,1)),zhi=indexOf(BRANCH,day.substring(1,2));int start=(zhi-gan+12)%12;return BRANCH[(start+10)%12]+BRANCH[(start+11)%12];}
    private static boolean contains(String[] values,String value){for(String item:values)if(item.equals(value))return true;return false;}
    private static boolean containsAny(String value,String[] keywords){for(String keyword:keywords)if(value.indexOf(keyword)>=0)return true;return false;}
    private static String join(List<String> values,String separator){StringBuilder out=new StringBuilder();for(String value:values){if(value==null||value.length()==0)continue;if(out.length()>0)out.append(separator);out.append(value);}return out.toString();}
    private static String positionName(int position){return new String[]{"","初爻","二爻","三爻","四爻","五爻","上爻"}[position];}
    public static String stageName(int stage){return new String[]{"本宫","一世","二世","三世","四世","五世","游魂","归魂"}[stage];}
}
