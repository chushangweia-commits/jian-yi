package com.jianyi.yixue.app;

/** Deterministic teaching facts only: bottom-first transformations and five-element relations. */
public final class ResultLearning {
    private static final String[] ELEMENTS = {"", "金", "金", "火", "木", "木", "水", "土", "土"};
    private static final String[] VIRTUES = {"", "乾健 · 健行", "兑悦 · 和悦", "离丽 · 附着与明", "震动 · 发动", "巽入 · 进入与顺", "坎陷 · 险中求通", "艮止 · 知止", "坤顺 · 承载"};
    private static final String[] CYCLE = {"木", "火", "土", "金", "水"};
    private ResultLearning() { }

    public static String element(int trigram) { validateTrigram(trigram); return ELEMENTS[trigram]; }
    public static String virtue(int trigram) { validateTrigram(trigram); return VIRTUES[trigram]; }

    public static String bodyUseRelation(int body, int use) {
        String a = element(body), b = element(use);
        if (a.equals(b)) return "比和（体用同属" + a + "）";
        if (cycleStep(b, a, 1)) return "用生体（" + b + "生" + a + "）";
        if (cycleStep(a, b, 1)) return "体生用（" + a + "生" + b + "）";
        if (cycleStep(b, a, 2)) return "用克体（" + b + "克" + a + "）";
        return "体克用（" + a + "克" + b + "）";
    }

    /** 错卦 reverses yin/yang in every position without moving the positions. */
    public static String oppositeCode(String bottomFirstCode) {
        validateCode(bottomFirstCode);
        StringBuilder result = new StringBuilder(6);
        for (int i = 0; i < 6; i++) result.append(bottomFirstCode.charAt(i) == '1' ? '0' : '1');
        return result.toString();
    }

    /** 综卦 reverses all six positions: original first line becomes the uppermost. */
    public static String reversedCode(String bottomFirstCode) {
        validateCode(bottomFirstCode);
        return new StringBuilder(bottomFirstCode).reverse().toString();
    }

    private static boolean cycleStep(String a, String b, int step) {
        for (int i = 0; i < CYCLE.length; i++) if (CYCLE[i].equals(a)) return CYCLE[(i + step) % 5].equals(b);
        throw new IllegalArgumentException("unknown element");
    }
    private static void validateTrigram(int value) {
        if (value < 1 || value > 8) throw new IllegalArgumentException("trigram must be 1..8");
    }
    private static void validateCode(String value) {
        if (value == null || value.length() != 6) throw new IllegalArgumentException("code must have six bits");
        for (int i = 0; i < 6; i++) if (value.charAt(i) != '0' && value.charAt(i) != '1')
            throw new IllegalArgumentException("code must be binary");
    }
}
