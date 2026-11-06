package com.jianyi.yixue.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Local allow-list for AI reading output.  It does not decide whether a reading is
 * correct; it prevents a response from silently changing divination systems and
 * requires the evidence labels that the UI can link back to local material.
 */
public final class AiAnswerPolicy {
    public static final Policy GENERIC = new Policy(
        "generic", "AI 解读", new String[0], new String[0][], new String[0]);

    public static final Policy MEIHUA = new Policy(
        "meihua", "梅花易数 · 体用互变",
        new String[] {"六爻", "纳甲", "世爻", "应爻", "世应", "六亲", "六神", "旬空", "月建", "日辰",
            "用神", "伏神", "飞神", "月破", "暗动", "进神", "退神", "伏吟", "反吟"},
        new String[][] {
            {"[梅花易数·体用]"},
            {"[周易·本卦动爻]", "[说卦·类象]", "[周易·变卦卦辞]"}
        },
        new String[] {"[梅花易数·体用]", "[周易·本卦动爻]", "[周易·变卦卦辞]", "[说卦·类象]"});

    public static final Policy LIUYAO = new Policy(
        "liuyao", "六爻纳甲 · 用神世应",
        new String[] {"梅花易数", "梅花", "体用", "互卦", "体卦", "用卦", "体生用", "体克用", "用生体", "用克体",
            "体用比和", "外应", "取数起卦"},
        new String[][] {
            {"[六爻·用神]"}, {"[六爻·世应]"}, {"[六爻·月日动变]"}, {"用神"}
        },
        new String[] {"[六爻·用神]", "[六爻·世应]", "[六爻·月日动变]"});

    public static final Policy BAZI = new Policy(
        "bazi", "八字研习 · 子平月令格局",
        new String[] {"梅花易数", "体卦", "用卦", "互卦", "世爻", "应爻", "纳甲", "六神", "旬空", "大衍筮法"},
        new String[][] {{"[本机排盘]"}},
        new String[] {"[本机排盘]", "[子平真诠·月令]"});

    public static Policy bazi(String monthPillar,String dayPillar) {
        return new Policy(
            "bazi", "八字研习 · 子平月令格局",
            BAZI.forbidden,
            new String[][] {{"先说结论："},{"为什么这样说："},{"你可以怎么做："},{"[本机排盘]"}},
            BAZI.evidenceLabels);
    }

    public static Policy baziFollowup() {
        return new Policy("bazi_followup","八字研习 · 子平月令格局",BAZI.forbidden,
            new String[][] {{"[子平真诠·月令]","[本机排盘]"}},BAZI.evidenceLabels);
    }
    private AiAnswerPolicy() { }

    public static Policy meihua(String exactRelation) {
        return new Policy("meihua", "梅花易数 · 体用互变", MEIHUA.forbidden,
            new String[][] {{"[梅花易数·体用]","[周易·本卦动爻]","[周易·变卦卦辞]","[说卦·类象]"}},
            MEIHUA.evidenceLabels);
    }

    public static Policy meihuaInitial(String relation,String base,String mutual,String changed,String line) {
        return new Policy("meihua_initial", "梅花易数 · 体用互变与卦爻",
            MEIHUA.forbidden,
            new String[][] {{"结论："},{"体用："},{"互变："},{"卦爻："},{relation},{base},{mutual},{changed},{line},
                {"[梅花易数·体用]"},{"[周易·本卦动爻]"}},
            MEIHUA.evidenceLabels);
    }

    public static Policy dayan(String baseName, String changedName, String[] movingLineLabels) {
        List<String[]> required = new ArrayList<String[]>();
        required.add(new String[] {"[周易·本卦卦辞]"});
        required.add(new String[] {baseName});
        if (movingLineLabels == null || movingLineLabels.length == 0) {
            required.add(new String[] {"无动爻"});
        } else {
            required.add(new String[] {"[周易·全部实际动爻]"});
            for (String label : movingLineLabels) required.add(new String[] {label});
            required.add(new String[] {"[周易·变卦卦辞]"});
            required.add(new String[] {changedName});
        }
        return new Policy(
            "dayan", "大衍筮法 · 周易卦爻辞",
            new String[] {"梅花易数", "体用", "体卦", "用卦", "体生用", "体克用", "用生体", "用克体", "体用比和", "互卦", "五行生克",
                "纳甲", "世爻", "应爻", "世应", "六亲", "六神", "用神", "月建", "日辰", "旬空", "伏神", "飞神"},
            required.toArray(new String[required.size()][]),
            movingLineLabels == null || movingLineLabels.length == 0
                ? new String[] {"[周易·本卦卦辞]"}
                : new String[] {"[周易·本卦卦辞]", "[周易·全部实际动爻]", "[周易·变卦卦辞]"});
    }

    public static final class Policy {
        public final String id;
        public final String displayName;
        private final String[] forbidden;
        private final String[][] requiredGroups;
        private final String[] evidenceLabels;

        private Policy(String id, String displayName, String[] forbidden, String[][] requiredGroups, String[] evidenceLabels) {
            this.id = id;
            this.displayName = displayName;
            this.forbidden = forbidden;
            this.requiredGroups = requiredGroups;
            this.evidenceLabels = evidenceLabels;
        }

        /** Empty means the answer passed every deterministic local check. */
        public String issue(String answer) {
            if (answer == null || answer.trim().length() == 0) return "回答为空";
            int length = answer.codePointCount(0, answer.length());
            int limit=("bazi".equals(id)||"bazi_followup".equals(id)||"meihua".equals(id)||"meihua_initial".equals(id))?1000:AiReadingContext.MAX_ANSWER_CHARS;
            if (length > limit) return "超过"+limit+"字";
            if ("bazi".equals(id)||"bazi_followup".equals(id)) {
                String[] internal={"input_profile","trusted_chart","chart_evidence","offline_insights","reviewed_rules","luck_cycles_in_ai_context","time_basis","luck_context","luck_data","start_solar"};
                for(String term:internal)if(answer.contains(term))return "回答出现内部数据字段";
            }
            if ("meihua_initial".equals(id)) {
                String[] headings={"结论：","体用：","互变：","卦爻："};
                if(!answer.trim().startsWith(headings[0]))return "首答需以结论开头";
                int previous=-1;
                for(String heading:headings){int position=answer.indexOf(heading);if(position<0||position<=previous)return "首答段落缺失或顺序不正确";previous=position;}
            }
            for (String term : forbidden) if (answer.contains(term)) return "混入其他体系术语“" + term + "”";
            for (String[] alternatives : requiredGroups) {
                boolean found = false;
                for (String term : alternatives) if (answer.contains(term)) { found = true; break; }
                if (!found) return "缺少依据“" + alternatives[0] + "”";
            }
            return "";
        }

        public String retryInstruction(String issue) {
            StringBuilder labels = new StringBuilder();
            for (String label : evidenceLabels) {
                if (labels.length() > 0) labels.append('、');
                labels.append(label);
            }
            if ("meihua_initial".equals(id)) {
                return "上一条回答未通过格式检查（"+issue+"）。请依据最初提供的固定排盘和经文重新回答，"
                    +"以“结论：”开头，再依次写“体用：”“互变：”“卦爻：”，明确回答所问，写出本卦、互卦、变卦的准确全名、原始体用生克关系和实际动爻标签，"
                    +"简述本卦卦辞与动爻爻辞相关含义。不能混入其他体系，末尾保留[梅花易数·体用][周易·本卦动爻]，"
                    +"含标点最多1000个Unicode字符。只输出修改后的回答。";
            }
            if ("meihua".equals(id)) {
                return "上一条追问回答未通过格式检查（"+issue+"）。请沿用固定卦象，直接回答追问，"
                    +"只使用实际提供的梅花易数排盘和经文，末尾列出本次实际使用的依据标签，含标点最多1000个Unicode字符。只输出修改后的回答。";
            }
            if ("bazi".equals(id)) {
                return "上一条回答未通过本机的格式检查（" + issue + "）。请只依据本轮提供的固定四柱与参考资料重写一次："
                    + "不得改变年、月、日、时四柱，不得混入梅花、六爻或大衍术语；只作子平月令格局的学习分析；"
                    + "依次使用“先说结论：”“为什么这样说：”“你可以怎么做：”三个小标题，用日常语言回答具体问题并引用本盘事实。只有真正需要时才补充需要确认的事项，不得把已选择的时间口径说成缺失；"
                    + "不要输出内部字段名、复述规则或堆叠术语。若本机提供起运与大运，应按所问使用这些资料；没有时解释真实缺项，不自行推算。流年及大运与原局的完整关系未由本机核定，不得说已核定。以350至650字为宜，含标点不超过1000个Unicode字符。末尾写[本机排盘]，仅确实使用所附规则时加[子平真诠·月令]。只输出重写后的用户答复。";
            }
            if ("bazi_followup".equals(id)) return "上一条追问回答未通过格式检查（"+issue+"）。沿用本轮固定命盘和实际提供的资料，直接回答追问；本机有大运时才使用，没有时说明真实原因，不自行推算。不要输出内部字段名，不强迫重复首答或索要真太阳时。最多1000字，末尾附本次实际使用的[本机排盘]或[子平真诠·月令]。";
            return "上一条回答未通过本机的“" + displayName + "”核验（" + issue + "）。请只依据最初提供的可信排盘和资料重写一次："
                + "不得解释或提及被禁术语；不得改变卦、爻、体用或排盘；大衍须逐一写出全部实际动爻；"
                + "回答含标点不超过200个Unicode字符。末尾只使用这些固定依据标签：" + labels + "。只输出重写后的用户答复。";
        }
    }
}
