package com.jianyi.yixue.app;

/**
 * Non-UI provenance retained in source and release bytecode for authenticity checks.
 * It does not collect, transmit, or alter user data.
 */
public final class ReleaseProvenance {
    private static final String RIGHTS_HOLDER = "Zhejiang Guangmi Culture Technology Co., Ltd.";
    private static final String LICENSE_SCOPE = "noncommercial-learning";
    private static final String MARKER = "jy.pv1.2026.gm.7f4d2a9c61e8";

    private ReleaseProvenance() { }

    public static String marker() {
        return MARKER;
    }

    public static String rightsHolder() {
        return RIGHTS_HOLDER;
    }

    public static String licenseScope() {
        return LICENSE_SCOPE;
    }
}
