package com.vone.vmq;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public class KeepAliveForegroundService extends Service {
    private static final String TAG = "VmqKeepAlive";
    private static final String CHANNEL_ID = "keep_alive_channel";
    private static final int NOTIFICATION_ID = 1101;
    private static final int WATCHDOG_REQUEST_CODE = 2101;

    public static final String ACTION_START = "com.vone.vmq.action.KEEP_ALIVE_START";
    public static final String ACTION_WATCHDOG = "com.vone.vmq.action.KEEP_ALIVE_WATCHDOG";
    public static final String ACTION_HEALTH_CHECK = "com.vone.vmq.action.KEEP_ALIVE_HEALTH_CHECK";

    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    private static final long WATCHDOG_INTERVAL_MS = 2 * 60 * 1000L;
    private static final long WAKE_LOCK_TIMEOUT_MS = 3 * 60 * 1000L;
    private static final long REBIND_MIN_INTERVAL_MS = 15_000L;

    private static final String STATE_PREFS = "vmq_keep_alive_state";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_LAST_HEARTBEAT_AT = "last_heartbeat_at";
    private static final String KEY_LAST_HEARTBEAT_OK = "last_heartbeat_ok";
    private static final String KEY_LAST_HEARTBEAT_MESSAGE = "last_heartbeat_message";

    private static volatile boolean running = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object wakeLockGuard = new Object();

    private OkHttpClient okHttpClient;
    private ExecutorService executor;
    private Thread heartbeatThread;
    private volatile boolean heartbeatLoopRunning = false;
    private volatile long lastRebindAt = 0L;
    private PowerManager.WakeLock guardWakeLock;
    private BroadcastReceiver deviceStateReceiver;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    public static void start(Context context) {
        start(context, ACTION_START);
    }

    public static void start(Context context, String action) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, KeepAliveForegroundService.class);
        intent.setAction(TextUtils.isEmpty(action) ? ACTION_START : action);
        try {
            ContextCompat.startForegroundService(appContext, intent);
        } catch (Exception e) {
            Log.e(TAG, "start keep alive service failed: " + e.getMessage(), e);
            LogStore.e(appContext, TAG, "启动保活服务失败: " + e.getMessage(), e);
        }
    }

    public static void requestNotificationRebind(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        try {
            ComponentName component = new ComponentName(context, NeNotificationService2.class);
            NotificationListenerService.requestRebind(component);
            LogStore.i(context, TAG, "已请求通知监听服务重新绑定");
        } catch (Exception e) {
            Log.e(TAG, "request notification listener rebind failed: " + e.getMessage(), e);
            LogStore.w(context, TAG, "请求通知监听重新绑定失败: " + e.getMessage());
        }
    }

    public static void scheduleWatchdog(Context context) {
        Context appContext = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        long triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS;
        PendingIntent pendingIntent = watchdogPendingIntent(appContext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        }
    }

    public static boolean isMarkedRunning(Context context) {
        return running || context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RUNNING, false);
    }

    public static long getLastHeartbeatAt(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_HEARTBEAT_AT, 0L);
    }

    public static boolean getLastHeartbeatOk(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LAST_HEARTBEAT_OK, false);
    }

    public static String getLastHeartbeatMessage(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_HEARTBEAT_MESSAGE, "");
    }

    public static boolean isPowerSaveMode(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        PowerManager powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        return powerManager != null && powerManager.isPowerSaveMode();
    }

    public static boolean isDeviceIdleMode(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        PowerManager powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        return powerManager != null && powerManager.isDeviceIdleMode();
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    public static void recordHeartbeatState(Context context, boolean ok, String message) {
        context.getApplicationContext()
                .getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RUNNING, true)
                .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
                .putBoolean(KEY_LAST_HEARTBEAT_OK, ok)
                .putString(KEY_LAST_HEARTBEAT_MESSAGE, message)
                .apply();
    }

    private static PendingIntent watchdogPendingIntent(Context context) {
        Intent intent = new Intent(context, KeepAliveReceiver.class);
        intent.setAction(ACTION_WATCHDOG);
        return PendingIntent.getBroadcast(
                context,
                WATCHDOG_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();
        okHttpClient = HeartbeatSender.newClient(15);
        executor = Executors.newSingleThreadExecutor();
        running = true;
        markRunning(true);

        startForegroundCompat("启动中");
        registerDeviceStateReceiver();
        registerNetworkCallback();
        startHeartbeatLoop();
        scheduleWatchdog(this);
        MuteAudioHelper.start(this);
        LogStore.i(this, TAG, "保活前台服务已创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String reason = intent != null && !TextUtils.isEmpty(intent.getAction())
                ? intent.getAction()
                : ACTION_START;
        running = true;
        markRunning(true);
        startForegroundCompat("运行中");
        startHeartbeatLoop();
        triggerHealthCheck(reason);
        scheduleWatchdog(this);
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        LogStore.w(this, TAG, "任务被移除，已安排保活看门狗");
        scheduleWatchdog(this);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        LogStore.w(this, TAG, "保活前台服务销毁，安排看门狗尝试恢复");
        running = false;
        markRunning(false);
        heartbeatLoopRunning = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        unregisterNetworkCallback();
        unregisterDeviceStateReceiver();
        releaseGuardWakeLock();
        MuteAudioHelper.stop();
        scheduleWatchdog(this);
        super.onDestroy();
    }

    private void startHeartbeatLoop() {
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            return;
        }
        heartbeatLoopRunning = true;
        heartbeatThread = new Thread(() -> {
            LogStore.i(KeepAliveForegroundService.this, TAG, "保活心跳线程启动");
            while (heartbeatLoopRunning) {
                triggerHealthCheck("periodic");
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "vmq-keep-alive-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void triggerHealthCheck(final String reason) {
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null || currentExecutor.isShutdown()) {
            return;
        }
        currentExecutor.execute(() -> runHealthCheck(reason));
    }

    private void runHealthCheck(String reason) {
        renewGuardWakeLock();
        requestRebindIfNeeded();
        PaymentPushWorker.enqueue(this);
        HeartbeatSender.Result result = HeartbeatSender.send(this, okHttpClient, reason);
        updateHeartbeatState(result.ok, result.message);
        scheduleWatchdog(this);
    }

    private void requestRebindIfNeeded() {
        long now = System.currentTimeMillis();
        if (NeNotificationService2.isConnected || now - lastRebindAt < REBIND_MIN_INTERVAL_MS) {
            return;
        }
        lastRebindAt = now;
        requestNotificationRebind(this);
    }

    private void updateHeartbeatState(boolean ok, String message) {
        recordHeartbeatState(this, ok, message);
        if (running) {
            mainHandler.post(() -> {
                if (running) {
                    startForegroundCompat(ok ? "心跳正常" : message);
                }
            });
        }
    }

    private void markRunning(boolean value) {
        getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_RUNNING, value)
                .apply();
    }

    @SuppressLint("WakelockTimeout")
    private void renewGuardWakeLock() {
        synchronized (wakeLockGuard) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) {
                return;
            }
            if (guardWakeLock == null) {
                guardWakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "VmqApk:KeepAliveGuard"
                );
                guardWakeLock.setReferenceCounted(false);
            }
            if (guardWakeLock.isHeld()) {
                guardWakeLock.release();
            }
            guardWakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        }
    }

    private void releaseGuardWakeLock() {
        synchronized (wakeLockGuard) {
            if (guardWakeLock != null && guardWakeLock.isHeld()) {
                guardWakeLock.release();
            }
            guardWakeLock = null;
        }
    }

    private void registerDeviceStateReceiver() {
        if (deviceStateReceiver != null) {
            return;
        }
        deviceStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent == null ? "device_state" : intent.getAction();
                logPowerState(context, action);
                triggerHealthCheck(action);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(deviceStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(deviceStateReceiver, filter);
        }
    }

    private void unregisterDeviceStateReceiver() {
        if (deviceStateReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(deviceStateReceiver);
        } catch (Exception ignored) {
        }
        deviceStateReceiver = null;
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null || networkCallback != null) {
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                LogStore.i(KeepAliveForegroundService.this, TAG, "网络恢复，触发保活检查");
                triggerHealthCheck("network_available");
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            LogStore.w(this, TAG, "注册网络状态监听失败: " + e.getMessage());
        }
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager == null || networkCallback == null) {
            return;
        }
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {
        }
        networkCallback = null;
    }

    private void logPowerState(Context context, String action) {
        LogStore.i(
                context,
                TAG,
                "收到设备状态变化: " + action
                        + "，省电模式=" + isPowerSaveMode(context)
                        + "，Doze=" + isDeviceIdleMode(context)
                        + "，忽略电池优化=" + isIgnoringBatteryOptimizations(context)
                        + "，精准闹钟=" + canScheduleExactAlarms(context)
        );
    }

    @SuppressLint("InlinedApi")
    private void startForegroundCompat(String status) {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("V免签保活服务运行中")
                .setContentText("状态：" + status + "，持续心跳并守护通知监听")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("V免签保活服务运行中：持续发送心跳、恢复离线队列，并在通知监听断开时请求重新绑定。"))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "保活心跳服务",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("常驻显示：持续心跳并守护收款通知监听");
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
