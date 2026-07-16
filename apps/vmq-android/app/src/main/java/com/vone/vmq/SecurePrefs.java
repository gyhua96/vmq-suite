package com.vone.vmq;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class SecurePrefs {
    private static final String TAG = "SecurePrefs";
    private static final String NAME = "vone_secure";
    private static final String LEGACY_NAME = "vone";

    private SecurePrefs() {}

    public static String getHost(Context context) {
        try {
            migrateLegacyIfNeeded(context);
            String host = prefs(context).getString("host", "");
            return ProtocolUtil.isSecureHost(host) ? host : "";
        } catch (IllegalStateException e) {
            Log.e(TAG, "Secure storage unavailable", e);
            return "";
        }
    }

    public static String getKey(Context context) {
        try {
            migrateLegacyIfNeeded(context);
            return prefs(context).getString("key", "");
        } catch (IllegalStateException e) {
            Log.e(TAG, "Secure storage unavailable", e);
            return "";
        }
    }

    public static void save(Context context, String host, String key) {
        try {
            if (!ProtocolUtil.isSecureHost(host) || host.trim().isEmpty() || key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("只允许保存 HTTPS 服务地址和非空通讯密钥");
            }
            prefs(context).edit().putString("host", host.trim()).putString("key", key.trim()).apply();
            clearLegacy(context);
        } catch (RuntimeException e) {
            Log.e(TAG, "Secure storage unavailable; refusing to persist configuration", e);
        }
    }

    private static SharedPreferences prefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            throw new IllegalStateException("EncryptedSharedPreferences unavailable", e);
        }
    }

    private static void migrateLegacyIfNeeded(Context context) {
        SharedPreferences secure = prefs(context);
        if (secure.contains("key")) return;
        SharedPreferences legacy = context.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE);
        String host = legacy.getString("host", "");
        String key = legacy.getString("key", "");
        if ((host != null && host.length() > 0) || (key != null && key.length() > 0)) {
            secure.edit().putString("host", host).putString("key", key).apply();
            clearLegacy(context);
        }
    }

    private static void clearLegacy(Context context) {
        context.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
