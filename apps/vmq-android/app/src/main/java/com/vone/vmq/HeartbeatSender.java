package com.vone.vmq;

import android.content.Context;
import android.os.PowerManager;
import android.text.TextUtils;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class HeartbeatSender {
    private static final String TAG = "VmqHeartbeat";
    private static final long WAKE_LOCK_TIMEOUT_MS = 45_000L;

    private HeartbeatSender() {}

    public static OkHttpClient newClient(long timeoutSeconds) {
        return new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static Result send(Context context, OkHttpClient client, String reason) {
        Context appContext = context.getApplicationContext();
        PowerManager.WakeLock wakeLock = acquireWakeLock(appContext);
        try {
            String host = SecurePrefs.getHost(appContext);
            String key = SecurePrefs.getKey(appContext);
            if (TextUtils.isEmpty(host) || TextUtils.isEmpty(key)) {
                return Result.skipped("等待配置");
            }

            String timestamp = String.valueOf(new Date().getTime());
            String sign = ProtocolUtil.md5(timestamp + key);
            String protocol = ProtocolUtil.getProtocol(host);
            String cleanHost = ProtocolUtil.cleanHost(host);
            String url = protocol + cleanHost + "/appHeart?t=" + timestamp + "&sign=" + sign;

            Request request = new Request.Builder().url(url).get().build();
            try (Response response = client.newCall(request).execute()) {
                String message = "HTTP " + response.code();
                if (response.isSuccessful()) {
                    LogStore.d(appContext, TAG, "心跳成功: " + message + ", reason=" + reason);
                } else {
                    LogStore.w(appContext, TAG, "心跳异常: " + message + ", reason=" + reason);
                }
                return new Result(response.isSuccessful(), message);
            }
        } catch (IOException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            LogStore.w(appContext, TAG, "心跳失败: " + message + ", reason=" + reason);
            return new Result(false, message);
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private static PowerManager.WakeLock acquireWakeLock(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return null;
        }
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VmqApk:HeartbeatSender"
        );
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        return wakeLock;
    }

    public static final class Result {
        public final boolean ok;
        public final String message;

        private Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        private static Result skipped(String message) {
            return new Result(false, message);
        }
    }
}
