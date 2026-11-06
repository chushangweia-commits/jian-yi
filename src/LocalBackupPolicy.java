package com.jianyi.yixue.app;

/** Only Android's on-device document stores are accepted for notebook backups. */
public final class LocalBackupPolicy {
    private LocalBackupPolicy() { }

    public static boolean allows(String scheme, String authority) {
        if (!"content".equals(scheme) || authority == null) return false;
        int separator = authority.indexOf('@');
        if (separator >= 0) {
            if (separator == 0) return false;
            for (int i = 0; i < separator; i++) {
                char c = authority.charAt(i);
                if (c < '0' || c > '9') return false;
            }
            authority = authority.substring(separator + 1);
        }
        return "com.android.externalstorage.documents".equals(authority)
            || "com.android.providers.downloads.documents".equals(authority)
            || "com.android.providers.media.documents".equals(authority);
    }
}
