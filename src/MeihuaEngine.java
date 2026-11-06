package com.jianyi.yixue.app;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Pure arithmetic for the explicitly selected Meihua conventions.
 * Trigram IDs: 1乾 2兑 3离 4震 5巽 6坎 7艮 8坤.
 * All bit arrays/codes are BOTTOM-first, 1=yang and 0=yin.
 * A hexagram code is lower trigram's three bits then upper trigram's three bits.
 * This engine computes diagrams, mutual diagrams, and body/use assignment only.
 * It makes no prediction and does not convert solar dates to lunar dates.
 */
public final class MeihuaEngine {
    public static final int MAX_NUMBER_DIGITS = 128;
    private static final BigInteger EIGHT = BigInteger.valueOf(8);
    private static final BigInteger SIX = BigInteger.valueOf(6);
    private static final BigInteger MAX_NUMBER = BigInteger.TEN.pow(MAX_NUMBER_DIGITS).subtract(BigInteger.ONE);
    private static final String[] NAMES = {"", "乾", "兑", "离", "震", "巽", "坎", "艮", "坤"};
    private static final int[][] BITS = {
        {}, {1,1,1}, {1,1,0}, {1,0,1}, {1,0,0},
        {0,1,1}, {0,1,0}, {0,0,1}, {0,0,0}
    };

    private MeihuaEngine() { }

    /** Each array is a per-result copy; modifying it never modifies the engine. */
    public static final class Result {
        public final int upper, lower, moving;
        public final String baseCode, changedCode, mutualCode;
        public final int changedUpper, changedLower, mutualUpper, mutualLower;
        public final int bodyTrigram, useTrigram;
        public final int[] base, changed, mutual, body, use;

        private Result(int upper, int lower, int moving, int[] baseBits) {
            this.upper = upper;
            this.lower = lower;
            this.moving = moving;
            this.base = baseBits.clone();
            this.changed = baseBits.clone();
            this.changed[moving - 1] ^= 1;
            this.mutual = new int[] {
                baseBits[1], baseBits[2], baseBits[3],
                baseBits[2], baseBits[3], baseBits[4]
            };
            this.baseCode = code(this.base);
            this.changedCode = code(this.changed);
            this.mutualCode = code(this.mutual);
            this.changedLower = trigramId(Arrays.copyOfRange(this.changed, 0, 3));
            this.changedUpper = trigramId(Arrays.copyOfRange(this.changed, 3, 6));
            this.mutualLower = trigramId(Arrays.copyOfRange(this.mutual, 0, 3));
            this.mutualUpper = trigramId(Arrays.copyOfRange(this.mutual, 3, 6));
            // The trigram containing the moving line is use; the other is body.
            this.useTrigram = moving <= 3 ? lower : upper;
            this.bodyTrigram = moving <= 3 ? upper : lower;
            this.use = trigramBits(this.useTrigram);
            this.body = trigramBits(this.bodyTrigram);
        }

        public int[] baseBits() { return base.clone(); }
        public int[] changedBits() { return changed.clone(); }
        public int[] mutualBits() { return mutual.clone(); }
        public int[] bodyBits() { return body.clone(); }
        public int[] useBits() { return use.clone(); }
    }

    /** Inputs are traditional numeric lunar fields, not Gregorian month/day. */
    public static Result time(int yearBranch, int lunarMonth, int lunarDay, int hourBranch) {
        range(yearBranch, 1, 12, "yearBranch");
        range(lunarMonth, 1, 12, "lunarMonth");
        range(lunarDay, 1, 30, "lunarDay");
        range(hourBranch, 1, 12, "hourBranch");
        int dateTotal = yearBranch + lunarMonth + lunarDay;
        int total = dateTotal + hourBranch;
        return fromTrigrams(modOne(dateTotal, 8), modOne(total, 8), modOne(total, 6));
    }

    /** null hour excludes hour; a supplied hour is the branch ordinal 1..12. */
    public static Result numberTwo(BigInteger first, BigInteger second, Integer optionalHourBranch) {
        positiveNumber(first, "first");
        positiveNumber(second, "second");
        if (optionalHourBranch != null) range(optionalHourBranch.intValue(), 1, 12, "hourBranch");
        BigInteger movingTotal = first.add(second);
        if (optionalHourBranch != null) movingTotal = movingTotal.add(BigInteger.valueOf(optionalHourBranch.intValue()));
        return fromTrigrams(modOne(first, EIGHT), modOne(second, EIGHT), modOne(movingTotal, SIX));
    }

    public static Result numberTwo(BigInteger first, BigInteger second) {
        return numberTwo(first, second, null);
    }

    /**
     * Three positive numbers: first/second select upper/lower independently.
     * false uses the third number for the moving line; true uses the sum of all three.
     * This explicit convention does not replace the existing two-number rule.
     */
    public static Result numberThree(BigInteger first, BigInteger second, BigInteger third, boolean sumThree) {
        positiveNumber(first, "first");
        positiveNumber(second, "second");
        positiveNumber(third, "third");
        BigInteger movingTotal = sumThree ? first.add(second).add(third) : third;
        return fromTrigrams(modOne(first, EIGHT), modOne(second, EIGHT), modOne(movingTotal, SIX));
    }

    /** Strict decimal intake for UI callers, including the length limit before conversion. */
    public static BigInteger parsePositive(String raw) {
        if (raw == null) throw new IllegalArgumentException("number is required");
        String value = raw.trim();
        if (value.length() == 0 || value.length() > MAX_NUMBER_DIGITS)
            throw new IllegalArgumentException("number must contain 1..128 decimal digits");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') throw new IllegalArgumentException("number must contain decimal digits only");
        }
        BigInteger result = new BigInteger(value);
        positiveNumber(result, "number");
        return result;
    }

    /** Public pure constructor useful for diagrams and exhaustive regression. */
    public static Result fromTrigrams(int upper, int lower, int moving) {
        range(upper, 1, 8, "upper");
        range(lower, 1, 8, "lower");
        range(moving, 1, 6, "moving");
        int[] bits = new int[6];
        System.arraycopy(BITS[lower], 0, bits, 0, 3);
        System.arraycopy(BITS[upper], 0, bits, 3, 3);
        return new Result(upper, lower, moving, bits);
    }

    public static int[] trigramBits(int id) {
        range(id, 1, 8, "trigram");
        return BITS[id].clone();
    }

    public static String trigramName(int id) {
        range(id, 1, 8, "trigram");
        return NAMES[id];
    }

    public static int trigramId(int[] bottomFirstBits) {
        if (bottomFirstBits == null || bottomFirstBits.length != 3)
            throw new IllegalArgumentException("a trigram needs exactly 3 bits");
        for (int bit : bottomFirstBits) if (bit != 0 && bit != 1)
            throw new IllegalArgumentException("bits must be 0 or 1");
        for (int id = 1; id <= 8; id++) if (Arrays.equals(BITS[id], bottomFirstBits)) return id;
        throw new IllegalArgumentException("unknown trigram");
    }

    private static void positiveNumber(BigInteger value, String label) {
        if (value == null || value.signum() <= 0)
            throw new IllegalArgumentException(label + " must be positive");
        if (value.compareTo(MAX_NUMBER) > 0)
            throw new IllegalArgumentException(label + " must not exceed 128 decimal digits");
    }

    private static int modOne(int value, int modulus) {
        int remainder = value % modulus;
        return remainder == 0 ? modulus : remainder;
    }

    private static int modOne(BigInteger value, BigInteger modulus) {
        int remainder = value.mod(modulus).intValue();
        return remainder == 0 ? modulus.intValue() : remainder;
    }

    private static String code(int[] bits) {
        StringBuilder out = new StringBuilder(bits.length);
        for (int bit : bits) out.append(bit);
        return out.toString();
    }

    private static void range(int value, int min, int max, String label) {
        if (value < min || value > max)
            throw new IllegalArgumentException(label + " must be " + min + ".." + max);
    }
}
