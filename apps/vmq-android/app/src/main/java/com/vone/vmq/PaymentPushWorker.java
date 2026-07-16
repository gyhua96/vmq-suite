package com.vone.vmq;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PaymentPushWorker extends Worker {
    private static final String TAG = "PaymentPushWorker";
    private static final String UNIQUE_WORK_NAME = "payment_push_drain";
    private static final int BATCH_SIZE = 20;

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    public PaymentPushWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PaymentPushWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String host = SecurePrefs.getHost(context);
        String key = SecurePrefs.getKey(context);
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(key)) {
            LogStore.w(context, TAG, "Payment push queue is waiting for host/key config");
            return Result.retry();
        }

        List<PaymentPushQueue.Item> items = PaymentPushQueue.pending(context, BATCH_SIZE);
        if (items.isEmpty()) {
            return Result.success();
        }

        boolean shouldRetry = false;
        for (PaymentPushQueue.Item item : items) {
            try {
                sendOne(context, host, key, item);
                PaymentPushQueue.markSuccess(context, item.id);
                LogStore.i(context, TAG, "Queued payment push succeeded, id=" + item.id);
            } catch (Exception e) {
                shouldRetry = true;
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                PaymentPushQueue.markFailure(context, item.id, message);
                Log.w(TAG, "Queued payment push failed, id=" + item.id + ", " + message, e);
                LogStore.w(context, TAG, "Queued payment push failed, id=" + item.id + ", " + message);
            }
        }

        boolean hasMorePending = !PaymentPushQueue.pending(context, 1).isEmpty();
        return (shouldRetry || hasMorePending) ? Result.retry() : Result.success();
    }

    private void sendOne(Context context, String host, String key, PaymentPushQueue.Item item) throws IOException {
        // 使用本地 SQLite 记录的入库时间戳，确保多次重试由于丢包发生时，参数 't' 和 'sign' 完全保持一致，从而完美保证服务端幂等防重。
        String timestamp = String.valueOf(item.createdAt);
        String url = ProtocolUtil.appPushUrl(host, item.type, item.price, timestamp, key);
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            LogStore.i(context, TAG, "Queued payment push response id=" + item.id
                    + ", HTTP " + response.code() + " " + Redactor.redact(body));
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            try {
                JSONObject json = new JSONObject(body);
                if (json.optInt("code", -1) != 1) {
                    throw new IOException("业务失败: " + json.optString("msg", "unknown"));
                }
            } catch (org.json.JSONException e) {
                throw new IOException("无效业务响应", e);
            }
        }
    }
}
