package com.jianyi.yixue.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** API keys never join the notebook, backups, saved conversations, or app logs. */
public final class AiSettings {
    private static final String PREFERENCES = "jianyi_ai_secret";
    private static final String CIPHERTEXT = "encrypted_key_v1";
    private static final int MAX_ENVELOPE_CHARACTERS = 2048;

    private AiSettings() { }

    public static synchronized boolean hasKey(Context context) {
        if (Build.VERSION.SDK_INT < 23) return false;
        try {
            String encrypted = preferences(context).getString(CIPHERTEXT, "");
            return encrypted != null && !encrypted.isEmpty();
        } catch (Exception unavailable) {
            return false;
        }
    }

    public static synchronized void saveKey(Context context, String value) throws Exception {
        requireSupported();
        String key = validateKey(value);
        try {
            persist(context, key);
        } catch (Exception unavailable) {
            throw new Exception("API Key未能安全保存，请确认设备锁屏与系统密钥库可用后重试。");
        }
    }

    public static synchronized String readKey(Context context) throws Exception {
        requireSupported();
        try {
            String encrypted = preferences(context).getString(CIPHERTEXT, "");
            if (encrypted != null && !encrypted.isEmpty()) {
                if (encrypted.length() > MAX_ENVELOPE_CHARACTERS) throw new Exception();
                return validateKey(decrypt(getEncryptionKey(context, false), encrypted));
            }
            return "";
        } catch (Exception unavailable) {
            throw new Exception("已保存的API Key无法解密，请在设置中删除后重新填写。");
        }
    }

    public static synchronized void clearKey(Context context) throws Exception {
        requireSupported();
        try {
            if (!preferences(context).edit().remove(CIPHERTEXT).commit()) throw new Exception();
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            String alias = alias(context);
            if (store.containsAlias(alias)) store.deleteEntry(alias);
        } catch (Exception unavailable) {
            throw new Exception("API Key未能完全删除，请稍后重试。");
        }
    }

    /** Compatibility status for older builds. New packages never contain a key. */
    public static synchronized boolean hasEmbeddedKey(Context context) {
        return false;
    }

    private static void persist(Context context, String key) throws Exception {
        String encrypted = encrypt(getEncryptionKey(context, true), key);
        if (!preferences(context).edit().putString(CIPHERTEXT, encrypted).commit()) throw new Exception();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String alias(Context context) {
        return context.getPackageName() + ".deepseek.api_key.v1";
    }

    private static void requireSupported() throws Exception {
        if (Build.VERSION.SDK_INT < 23) {
            throw new Exception("此设备不支持安全保存API Key，联网解答需要Android 6.0或以上版本。");
        }
    }

    private static String validateKey(String value) throws Exception {
        if (value == null || value.length() > 512) throw new Exception("请输入有效的API Key（最长512个英文字符）。");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 32 || c > 126) throw new Exception("API Key只能包含可打印的英文字符，不能包含换行。");
        }
        String key = value.trim();
        if (key.isEmpty()) throw new Exception("请输入API Key。");
        return key;
    }

    private static SecretKey getEncryptionKey(Context context, boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        String alias = alias(context);
        if (store.containsAlias(alias)) {
            return (SecretKey) store.getKey(alias, null);
        }
        if (!create) throw new Exception();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static String encrypt(SecretKey key, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return "1." + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                + "." + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    }

    private static String decrypt(SecretKey key, String envelope) throws Exception {
        String[] fields = envelope.split("\\.", -1);
        if (fields.length != 3 || !"1".equals(fields[0])) throw new Exception();
        byte[] iv = Base64.decode(fields[1], Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(fields[2], Base64.NO_WRAP);
        if (iv.length != 12 || ciphertext.length < 16 || ciphertext.length > 528) throw new Exception();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
