package com.vone.vmq;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public class KeepAliveReceiver extends BroadcastReceiver {
    private static final String TAG = "VmqKeepAliveReceiver";
    private static final OkHttpClient WATCHDOG_CLIENT = HeartbeatSender.newClient(8);

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? KeepAliveForegroundService.ACTION_WATCHDOG : intent.getAction();
        Log.d(TAG, "keep alive receiver action=" + action);
        LogStore.i(context, TAG, "收到保活看门狗广播: " + action);

        final BroadcastReceiver.PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                HeartbeatSender.Result result = HeartbeatSender.send(appContext, WATCHDOG_CLIENT, action);
                KeepAliveForegroundService.recordHeartbeatState(appContext, result.ok, result.message);
                KeepAliveForegroundService.requestNotificationRebind(appContext);
                PaymentPushWorker.enqueue(appContext);
            } finally {
                KeepAliveForegroundService.scheduleWatchdog(appContext);
                pendingResult.finish();
                executor.shutdown();
            }
        });

        KeepAliveForegroundService.start(context, action);
    }
}
