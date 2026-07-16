package com.vone.vmq;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 通知监听服务 - 2026 优化版
 *
 * 【原理说明】
 * 本 App 通过 Android 的 NotificationListenerService 系统 API 监听所有 App 通知。
 * 当微信/支付宝推送收款通知时，从通知的 title/text 中提取金额，
 * 然后通过 HTTP 回调上报到自建的 v免签服务器。
 *
 * 【保活方案 - 2026 年有效方案】
 * 1. startForeground() 前台服务：显示持久通知，系统优先级最高
 * 2. WakeLock：防止 CPU 休眠中断通知处理
 * 3. onListenerDisconnected() 中调用 requestRebind() 自动重连
 * 4. BootReceiver 开机自启
 * 5. 心跳线程维持服务存活，定期向服务器上报心跳
 *
 * 【注意】
 * Android 13+ 需要用户在"通知使用权"页面手动授权
 * 部分国内 ROM（MIUI/ColorOS/HarmonyOS）还需要额外开启"自启动"和"后台运行"权限
 *
 * 【微信收款通知说明 - 2026年版】
 * - 微信收款助手标题通常为: "微信收款助手" 或 "微信支付"
 * - 内容通常为: "微信支付收款X.XX元" 或 "收款X元已到账"
 * - 微信商业版标题: "微信收款商业版"，内容包含金额
 *
 * 【支付宝收款通知说明 - 2026年版】
 * - 支付宝标题通常为: "收钱码收款通知" 或 "支付宝"
 * - 内容通常为: "xxxxx通过扫码向你付款X.XX元" 或 "你已收款X.XX元"
 */
public class NeNotificationService2 extends NotificationListenerService {
    private static final String TAG = "VmqNotification";
    private static final String CHANNEL_ID = "payment_monitor_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int PUSH_MAX_RETRY_COUNT = 3;
    private static final long PUSH_RETRY_BASE_DELAY_MS = 5_000L;
    private static final long PUSH_WAKE_LOCK_TIMEOUT_MS = 2 * 60 * 1000L;
    private static final long HEARTBEAT_WAKE_LOCK_TIMEOUT_MS = 45_000L;

    // 微信和支付宝的包名（2026年未变化）
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";

    /**
     * 微信收款：判断标题 - 涵盖所有微信收款场景
     * 微信收款助手、微信支付、收款小助手、微信收款商业版
     */
    private static final String[] WECHAT_TITLE_KEYWORDS = {
        "微信收款助手", "微信支付", "收款小助手",
        "微信收款商业版", "微信支付凭证",
        "收款到账"
    };

    /**
     * 微信收款：内容关键词（在标题匹配后再校验内容）
     */
    private static final String[] WECHAT_CONTENT_KEYWORDS = {
        "收款", "到账", "付款成功", "支付成功", "元"
    };

    /**
     * 支付宝收款：标题关键词
     */
    private static final String[] ALIPAY_TITLE_KEYWORDS = {
        "收钱码收款通知", "支付宝收款", "支付宝", "收款通知",
        "到账通知"
    };

    /**
     * 支付宝收款：内容关键词
     */
    private static final String[] ALIPAY_CONTENT_KEYWORDS = {
        "通过扫码向你付款", "成功收款", "收钱码收款",
        "向你付款", "你已收款", "到账", "收款成功",
        "付款成功", "转账成功"
    };

    /**
     * 金额提取正则 - 按优先级排列
     * 覆盖场景：
     *   "收款1.50元" / "¥1.50" / "￥1.50" / "到账 150.00元"
     *   "共收款人民币1.00元" / "付款1元" / "1.50元整"
     */
    private static final Pattern[] MONEY_PATTERNS = {
        // 优先匹配含货币符号的精确金额
        Pattern.compile("[¥￥]\\s*([0-9]{1,8}(?:\\.[0-9]{1,2})?)"),
        // 匹配"收款/到账/付款 + 数字 + 元"
        Pattern.compile("(?:收款|到账|付款|转账|实收)[^0-9]{0,5}([0-9]{1,8}(?:\\.[0-9]{1,2})?)\\s*元"),
        // 匹配"数字元" 或 "数字.xx元"
        Pattern.compile("([0-9]{1,8}\\.[0-9]{1,2})\\s*元"),
        // 兜底：匹配合理数字
        Pattern.compile("([0-9]{1,6}(?:\\.[0-9]{1,2})?)\\s*(?:元|CNY)")
    };

    private String host = "";
    private String key = "";
    private Thread heartbeatThread = null;
    private PowerManager.WakeLock mWakeLock = null;
    private OkHttpClient okHttpClient;
    private volatile boolean isRunning = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 用于提供给保活服务或其它组件做监听连接状态判定
    public static volatile boolean isConnected = false;

    /**
     * 防重复推送：使用 LRU 缓存记录最近处理的通知
     * key = pkg+title+content 的 hash，value = 时间戳
     * 5秒内相同通知不重复推送
     */
    private final Map<String, Long> recentNotifications = new LinkedHashMap<String, Long>(50, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > 100;
        }
    };
    private static final long DEDUP_WINDOW_MS = 5000; // 5秒去重窗口

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        LogStore.i(this, TAG, "通知监听服务创建");

        // 初始化 OkHttpClient（连接池 + 超时）
        okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

        // 启动前台服务（保活核心手段）
        startForegroundServiceCompat();
        KeepAliveForegroundService.start(this);

        // Drain any queued payment pushes when the listener process is alive.
        PaymentPushWorker.enqueue(this);
    }

    /**
     * 启动前台服务（Android 5.0+ 最有效的保活手段）
     * Android 14 (API 34) 需要声明 foregroundServiceType
     */
    @SuppressLint("InlinedApi")
    private void startForegroundServiceCompat() {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在监听收款通知")
            .setContentText("V免签监控端常驻运行中：监听微信 / 支付宝收款通知")
            .setStyle(new NotificationCompat.BigTextStyle().bigText("V免签监控端常驻运行中：正在监听微信 / 支付宝收款通知，用于识别收款并推送到已配置服务器。"))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build();

        // Android 10 (Q) 以上需要指定 foregroundServiceType
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "收款监控服务", NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("常驻显示：正在监听微信和支付宝收款通知");
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 申请 CPU WakeLock（防止深度休眠时错过通知）
     */
    @SuppressLint("WakelockTimeout")
    private void acquireWakeLock() {
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                mWakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "VmqApk:PaymentWakeLock"
                );
                mWakeLock.acquire();
                Log.d(TAG, "WakeLock 已申请");
                LogStore.i(this, TAG, "WakeLock 已申请");
            }
        } else if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
            Log.d(TAG, "WakeLock 已重新申请");
            LogStore.i(this, TAG, "WakeLock 已重新申请");
        }
    }

    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            mWakeLock = null;
            Log.d(TAG, "WakeLock 已释放");
            LogStore.i(this, TAG, "WakeLock 已释放");
        }
    }

    private PowerManager.WakeLock acquirePushWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            return null;
        }
        PowerManager.WakeLock wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VmqApk:PaymentPushWakeLock"
        );
        wakeLock.acquire(PUSH_WAKE_LOCK_TIMEOUT_MS);
        return wakeLock;
    }

    private void releaseWakeLockSafely(PowerManager.WakeLock wakeLock) {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private PowerManager.WakeLock acquireHeartbeatWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            return null;
        }
        PowerManager.WakeLock wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VmqApk:HeartbeatWakeLock"
        );
        wakeLock.acquire(HEARTBEAT_WAKE_LOCK_TIMEOUT_MS);
        return wakeLock;
    }

    private void sendAppHeart(final String h, final String k, final int attempt) {
        if (TextUtils.isEmpty(h) || TextUtils.isEmpty(k)) {
            return;
        }
        final PowerManager.WakeLock heartbeatWakeLock = acquireHeartbeatWakeLock();
        String t = String.valueOf(new Date().getTime());
        String sign = md5(t + k);
        String protocol = ProtocolUtil.getProtocol(h);
        String cleanHost = ProtocolUtil.cleanHost(h);
        Request req = new Request.Builder()
            .url(protocol + cleanHost + "/appHeart?t=" + t + "&sign=" + sign)
            .get().build();

        Log.d(TAG, "开始发送心跳, attempt=" + attempt);
        okHttpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "心跳失败: " + e.getMessage() + ", attempt=" + attempt);
                LogStore.w(NeNotificationService2.this, TAG, "心跳失败: " + e.getMessage() + ", 第" + attempt + "次");
                releaseWakeLockSafely(heartbeatWakeLock);
                
                // 失败补偿重试：如果第一次心跳失败，5秒后尝试补发一次
                if (attempt < 2 && isRunning) {
                    mainHandler.postDelayed(() -> sendAppHeart(h, k, attempt + 1), 5_000L);
                }
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    int code = response.code();
                    Log.d(TAG, "心跳响应: HTTP " + code + ", attempt=" + attempt);
                    LogStore.d(NeNotificationService2.this, TAG, "心跳响应: HTTP " + code + ", 第" + attempt + "次");
                    
                    // 如果服务器返回非 2xx/3xx，也可以尝试在 5 秒后补偿一次
                    if (!response.isSuccessful() && attempt < 2 && isRunning) {
                        mainHandler.postDelayed(() -> sendAppHeart(h, k, attempt + 1), 5_000L);
                    }
                } finally {
                    response.close();
                    releaseWakeLockSafely(heartbeatWakeLock);
                }
            }
        });
    }

    /**
     * 心跳线程（每30秒向服务器上报一次心跳，同时维持服务存活）
     */
    public void initAppHeart() {
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            return;
        }

        isRunning = true;
        
        // 绑定成功或初始化时，立刻发送一次心跳
        String h = SecurePrefs.getHost(NeNotificationService2.this);
        String k = SecurePrefs.getKey(NeNotificationService2.this);
        sendAppHeart(h, k, 1);

        heartbeatThread = new Thread(() -> {
            Log.d(TAG, "心跳线程启动");
            LogStore.i(this, TAG, "心跳线程启动");
            while (isRunning) {
                try {
                    // 自检重连：如果发现监听连接断开，主动请求重连
                    if (!isConnected) {
                        Log.w(TAG, "心跳线程自检：发现通知监听未连接，正尝试重新绑定...");
                        LogStore.w(NeNotificationService2.this, TAG, "心跳线程自检：发现通知监听未连接，正尝试重新绑定...");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            try {
                                requestRebind(new android.content.ComponentName(NeNotificationService2.this, NeNotificationService2.class));
                            } catch (Exception e) {
                                Log.e(TAG, "心跳线程触发重连失败: " + e.getMessage());
                            }
                        }
                    }

                    // 周期性确保 WakeLock 依旧有效
                    acquireWakeLock();

                    String hostStr = SecurePrefs.getHost(NeNotificationService2.this);
                    String keyStr = SecurePrefs.getKey(NeNotificationService2.this);

                    sendAppHeart(hostStr, keyStr, 1);

                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    Log.d(TAG, "心跳线程中断");
                    LogStore.i(this, TAG, "心跳线程中断");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "心跳异常: " + e.getMessage());
                    LogStore.e(this, TAG, "心跳异常: " + e.getMessage(), e);
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    // ===================== 通知处理核心逻辑 =====================

    private volatile int notificationCount = 0; // 通知计数器

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        notificationCount++;
        Log.d(TAG, "═══ 第 " + notificationCount + " 条通知到达 ═══ 来自: " + sbn.getPackageName());
        LogStore.d(this, TAG, "第 " + notificationCount + " 条通知到达，来自: " + sbn.getPackageName());
        try {
            processNotification(sbn);
        } catch (Exception e) {
            Log.e(TAG, "处理通知异常: " + e.getMessage(), e);
            LogStore.e(this, TAG, "处理通知异常: " + e.getMessage(), e);
        }
    }

    private void processNotification(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        Notification notification = sbn.getNotification();
        if (notification == null) {
            Log.w(TAG, "━━ 通知为空 [" + pkg + "] ━━");
            return;
        }

        Bundle extras = notification.extras;
        if (extras == null) {
            Log.w(TAG, "━━ 通知 extras 为空 [" + pkg + "] ━━");
            return;
        }

        // 获取通知标题和内容（兼容多种格式）
        String title = safeGetString(extras, Notification.EXTRA_TITLE);
        String content = safeGetString(extras, Notification.EXTRA_TEXT);
        String bigText = safeGetString(extras, Notification.EXTRA_BIG_TEXT);
        String subText = safeGetString(extras, Notification.EXTRA_SUB_TEXT);
        String infoText = safeGetString(extras, Notification.EXTRA_INFO_TEXT);
        String summaryText = safeGetString(extras, Notification.EXTRA_SUMMARY_TEXT);
        String titleBig = safeGetString(extras, Notification.EXTRA_TITLE_BIG);

        // 合并所有文本，提高匹配成功率
        String fullText = mergeText(title, content, bigText, subText);

        // 防重复：同一通知 5 秒内不重复处理
        String deduKey = pkg + "|" + title + "|" + content;
        long now = System.currentTimeMillis();
        Long lastTime = recentNotifications.get(deduKey);
        if (lastTime != null && (now - lastTime) < DEDUP_WINDOW_MS) {
            Log.d(TAG, "⏭ 去重跳过 [" + pkg + "] " + title);
            LogStore.d(this, TAG, "去重跳过通知 [" + pkg + "] " + title);
            return;
        }
        recentNotifications.put(deduKey, now);

        // ==================== 详细日志打印 ====================
        // 判断通知来源分类
        String sourceTag;
        if (WECHAT_PACKAGE.equals(pkg)) {
            sourceTag = "💬微信";
        } else if (ALIPAY_PACKAGE.equals(pkg)) {
            sourceTag = "💰支付宝";
        } else if (getPackageName().equals(pkg)) {
            sourceTag = "📱本App";
        } else {
            sourceTag = "📦其他";
        }

        // 获取通知渠道和分类信息
        String channelId = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = notification.getChannelId() != null ? notification.getChannelId() : "";
        }
        String category = notification.category != null ? notification.category : "";
        long postTime = sbn.getPostTime();
        int notificationId = sbn.getId();
        String tag = sbn.getTag() != null ? sbn.getTag() : "";
        boolean isOngoing = sbn.isOngoing();
        boolean isClearable = sbn.isClearable();

        Log.i(TAG, "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.i(TAG, "┃ " + sourceTag + " 新通知捕获");
        Log.i(TAG, "┣━ 包名:       " + pkg);
        Log.i(TAG, "┣━ 通知ID:     " + notificationId + (TextUtils.isEmpty(tag) ? "" : " (tag=" + tag + ")"));
        Log.i(TAG, "┣━ 标题:       " + (TextUtils.isEmpty(title) ? "(空)" : Redactor.redact(title)));
        Log.i(TAG, "┣━ 内容:       " + (TextUtils.isEmpty(content) ? "(空)" : "[已脱敏]"));
        if (!TextUtils.isEmpty(bigText)) {
            Log.i(TAG, "┣━ 大文本:     [已脱敏]");
        }
        if (!TextUtils.isEmpty(subText)) {
            Log.i(TAG, "┣━ 子文本:     [已脱敏]");
        }
        if (!TextUtils.isEmpty(titleBig) && !titleBig.equals(title)) {
            Log.i(TAG, "┣━ 大标题:     " + titleBig);
        }
        if (!TextUtils.isEmpty(infoText)) {
            Log.i(TAG, "┣━ 信息文本:   " + infoText);
        }
        if (!TextUtils.isEmpty(summaryText)) {
            Log.i(TAG, "┣━ 摘要文本:   " + summaryText);
        }
        if (!TextUtils.isEmpty(channelId)) {
            Log.i(TAG, "┣━ 渠道ID:     " + channelId);
        }
        if (!TextUtils.isEmpty(category)) {
            Log.i(TAG, "┣━ 分类:       " + category);
        }
        Log.i(TAG, "┣━ 发布时间:   " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.CHINA).format(new Date(postTime)));
        Log.i(TAG, "┣━ 持续通知:   " + isOngoing + " | 可清除: " + isClearable);

        // 打印 extras 中所有 key，帮助排查未知字段
        StringBuilder extrasKeys = new StringBuilder();
        for (String k : extras.keySet()) {
            extrasKeys.append(k).append(", ");
        }
        Log.d(TAG, "┣━ extras全部key: " + extrasKeys.toString());

        Log.i(TAG, "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        if (WECHAT_PACKAGE.equals(pkg) || ALIPAY_PACKAGE.equals(pkg) || getPackageName().equals(pkg)) {
            LogStore.i(this, TAG, sourceTag + " 通知：标题=" + (TextUtils.isEmpty(title) ? "(无标题)" : Redactor.redact(title)) + "，内容=[已脱敏]");
        }

        // ==================== 业务处理 ====================
        // 读取最新配置
        host = SecurePrefs.getHost(this);
        key = SecurePrefs.getKey(this);

        if (ALIPAY_PACKAGE.equals(pkg)) {
            Log.d(TAG, "▶ 进入支付宝通知处理流程...");
            handleAlipayNotification(title, fullText);
        } else if (WECHAT_PACKAGE.equals(pkg)) {
            Log.d(TAG, "▶ 进入微信通知处理流程...");
            handleWechatNotification(title, fullText);
        } else if (getPackageName().equals(pkg)) {
            // 本 App 测试推送
            if (fullText.contains("这是一条测试推送信息")) {
                Log.d(TAG, "▶ 捕获到本 App 测试推送，监听正常");
                LogStore.i(this, TAG, "捕获到本 App 测试推送，通知监听正常");
                showToast("✅ 监听正常！通知捕获成功");
            }
        } else {
            Log.d(TAG, "▶ 非支付相关通知，仅记录日志 [" + pkg + "]");
        }
    }

    /**
     * 处理支付宝通知
     *
     * 2026年支付宝收款通知示例：
     *   标题: "收钱码收款通知"  内容: "张三通过扫码向你付款50.00元"
     *   标题: "支付宝"         内容: "你已收款100元，余额：xxx"
     *   标题: "到账通知"       内容: "到账50.00元，来自：xxx"
     */
    private void handleAlipayNotification(String title, String fullText) {
        // 先检查标题
        boolean titleMatch = containsAny(title, ALIPAY_TITLE_KEYWORDS);
        // 再检查内容
        boolean contentMatch = containsAny(fullText, ALIPAY_CONTENT_KEYWORDS);

        Log.d(TAG, "  [支付宝] 标题匹配=" + titleMatch + " 内容匹配=" + contentMatch);

        if (!titleMatch && !contentMatch) {
            Log.d(TAG, "  [支付宝] ❌ 标题和内容均未匹配收款关键词，跳过");
            return;
        }

        // 至少标题或内容有一项匹配，再确认内容确实含收款信息
        if (!containsAny(fullText, new String[]{"收款", "到账", "付款", "元"})) {
            Log.d(TAG, "  [支付宝] ❌ 二次验证失败，内容不含收款/到账/付款/元，跳过");
            return;
        }

        String money = extractMoney(fullText);
        if (money != null) {
            Log.i(TAG, "  [支付宝] ✅ 收款金额提取成功 ¥" + money);
            LogStore.i(this, TAG, "支付宝收款金额提取成功 ¥" + money);
            appPush(2, money);
        } else {
            Log.w(TAG, "  [支付宝] ⚠️ 疑似收款但未提取到金额，原文: " + fullText);
            LogStore.w(this, TAG, "支付宝疑似收款但未提取到金额");
        }
    }

    /**
     * 处理微信通知
     *
     * 2026年微信收款通知示例：
     *   标题: "微信收款助手"   内容: "微信支付收款1.50元，共1笔"
     *   标题: "微信支付"       内容: "收款1.50元"
     *   标题: "微信收款商业版" 内容: "成功收款¥50.00"
     */
    private void handleWechatNotification(String title, String fullText) {
        // 微信通知较多，必须标题匹配才处理（避免普通聊天误触发）
        boolean titleMatch = containsAny(title, WECHAT_TITLE_KEYWORDS);
        Log.d(TAG, "  [微信] 标题匹配=" + titleMatch + " 标题内容=\"" + title + "\"");

        if (!titleMatch) {
            Log.d(TAG, "  [微信] ❌ 标题未匹配收款关键词（普通聊天消息），跳过");
            return;
        }

        // 内容必须包含收款相关词
        boolean contentMatch = containsAny(fullText, WECHAT_CONTENT_KEYWORDS);
        Log.d(TAG, "  [微信] 内容匹配=" + contentMatch);

        if (!contentMatch) {
            Log.d(TAG, "  [微信] ❌ 内容未匹配收款关键词，跳过");
            return;
        }

        String money = extractMoney(fullText);
        if (money != null) {
            Log.i(TAG, "  [微信] ✅ 收款金额提取成功 ¥" + money);
            LogStore.i(this, TAG, "微信收款金额提取成功 ¥" + money);
            appPush(1, money);
        } else {
            Log.w(TAG, "  [微信] ⚠️ 疑似收款但未提取到金额，原文: " + fullText);
            LogStore.w(this, TAG, "微信疑似收款但未提取到金额");
        }
    }

    // ===================== 金额提取 =====================

    /**
     * 从通知文本中提取金额
     * 按优先级逐个尝试正则，返回第一个合法金额
     */
    private String extractMoney(String text) {
        if (TextUtils.isEmpty(text)) return null;

        Log.d(TAG, "  [金额提取] 开始从文本中提取金额...");
        for (int i = 0; i < MONEY_PATTERNS.length; i++) {
            Pattern pattern = MONEY_PATTERNS[i];
            Matcher matcher = pattern.matcher(text);
            // 遍历所有匹配，优先取最后一个（通常金额在句尾）
            String lastMatch = null;
            while (matcher.find()) {
                String candidate = matcher.group(1);
                Log.d(TAG, "  [金额提取] 正则#" + i + " 匹配到候选值: " + candidate);
                if (isValidMoney(candidate)) {
                    lastMatch = candidate;
                }
            }
            if (lastMatch != null) {
                Log.d(TAG, "  [金额提取] ✅ 最终金额: " + lastMatch + " (来自正则#" + i + ")");
                return lastMatch;
            }
        }
        Log.w(TAG, "  [金额提取] ❌ 所有正则均未匹配到合法金额");
        return null;
    }

    /**
     * 验证金额合理性：0.01 ~ 999999.99
     */
    private boolean isValidMoney(String money) {
        if (TextUtils.isEmpty(money)) return false;
        try {
            double val = Double.parseDouble(money);
            return val >= 0.01 && val <= 999999.99;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ===================== HTTP 推送 =====================

    /**
     * 推送收款信息到 v免签 服务器
     *
     * @param type  1=微信, 2=支付宝
     * @param price 金额字符串，保持通知中的小数位用于旧签名兼容和服务端幂等
     */
    public void appPush(int type, String price) {
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(key)) {
            Log.e(TAG, "推送失败: 未配置 host/key");
            LogStore.e(this, TAG, "推送失败: 未配置 host/key");
            showToast("未配置服务地址，请先扫码配置");
            return;
        }
        if (!isValidMoney(price)) {
            Log.e(TAG, "推送失败: 金额非法 " + price);
            LogStore.e(this, TAG, "推送失败: 金额非法 " + price);
            return;
        }

        String priceText = price.trim();
        long id = PaymentPushQueue.enqueue(this, type, priceText);
        PaymentPushWorker.enqueue(this);
        LogStore.i(this, TAG, "Payment push queued, id=" + id + ", type=" + type + ", price=" + priceText);
        showToast("收款已加入可靠上报队列，稍后自动同步");
    }

    private void sendAppPush(final int type, final String price, final String url,
                             final int attempt, final PowerManager.WakeLock pushWakeLock) {
        Log.d(TAG, "准备推送收款信息，type=" + type + ", price=" + price + ", host=" + host + ", attempt=" + attempt);
        LogStore.i(this, TAG, "准备推送收款信息，type=" + type + ", price=" + price + ", host=" + host + ", 第" + attempt + "次");

        Request request = new Request.Builder().url(url).get().build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "推送失败，准备判断是否重试: attempt=" + attempt + ", error=" + e.getMessage(), e);
                LogStore.w(NeNotificationService2.this, TAG, "推送失败，第" + attempt + "次: " + e.getMessage());
                retryAppPushOrFail(type, price, url, attempt, pushWakeLock, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String result = response.body() != null ? response.body().string() : "";
                    int responseCode = response.code();
                    Log.d(TAG, "推送响应: HTTP " + responseCode + ", attempt=" + attempt);
                    LogStore.i(NeNotificationService2.this, TAG, "推送响应: HTTP " + responseCode + " " + Redactor.redact(result));

                    if (response.isSuccessful() && isBusinessSuccess(result)) {
                        String payType = type == 1 ? "微信" : "支付宝";
                        showToast("✅ " + payType + "收款 ¥" + price + " 已推送");
                        releaseWakeLockSafely(pushWakeLock);
                        return;
                    }

                    if (responseCode >= 500 || response.isSuccessful()) {
                        retryAppPushOrFail(type, price, url, attempt, pushWakeLock, "HTTP " + responseCode);
                        return;
                    }

                    LogStore.e(NeNotificationService2.this, TAG, "推送失败，不重试: HTTP " + responseCode);
                    showToast("❌ 推送失败: HTTP " + responseCode);
                    releaseWakeLockSafely(pushWakeLock);
                } finally {
                    response.close();
                }
            }
        });
    }

    private void retryAppPushOrFail(final int type, final String price, final String url,
                                    final int attempt, final PowerManager.WakeLock pushWakeLock, String reason) {
        if (attempt >= PUSH_MAX_RETRY_COUNT) {
            Log.e(TAG, "推送最终失败: " + reason);
            LogStore.e(this, TAG, "推送最终失败，已重试" + attempt + "次: " + reason);
            showToast("❌ 推送失败，检查网络: " + reason);
            releaseWakeLockSafely(pushWakeLock);
            return;
        }

        long delayMs = PUSH_RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
        LogStore.w(this, TAG, "推送将在 " + (delayMs / 1000) + " 秒后重试");
        mainHandler.postDelayed(() -> sendAppPush(type, price, url, attempt + 1, pushWakeLock), delayMs);
    }

    private boolean isBusinessSuccess(String body) {
        try {
            return new JSONObject(body).optInt("code", -1) == 1;
        } catch (JSONException e) {
            return false;
        }
    }

    // ===================== 生命周期 =====================

    @Override
    public void onListenerConnected() {
        Log.d(TAG, "NotificationListener 已连接");
        LogStore.i(this, TAG, "NotificationListener 已连接");
        isConnected = true;
        startForegroundServiceCompat();
        KeepAliveForegroundService.start(this, KeepAliveForegroundService.ACTION_HEALTH_CHECK);
        PaymentPushWorker.enqueue(this);
        
        showToast("V免签监听服务已启动 ✅");
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        Log.w(TAG, "Foreground service timeout");
        LogStore.w(this, TAG, "Foreground service timeout, foreground notification removed");
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @Override
    public void onListenerDisconnected() {
        Log.w(TAG, "NotificationListener 断开，尝试重连...");
        LogStore.w(this, TAG, "NotificationListener 断开，尝试重连");
        isConnected = false;
        KeepAliveForegroundService.start(this, KeepAliveForegroundService.ACTION_HEALTH_CHECK);

        // Android 7.0+ 支持主动请求重连
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(new android.content.ComponentName(this, NeNotificationService2.class));
            mainHandler.postDelayed(() ->
                requestRebind(new android.content.ComponentName(
                    NeNotificationService2.this,
                    NeNotificationService2.class
                )),
                10_000L
            );
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        Notification notification = sbn.getNotification();
        String title = "";
        if (notification != null && notification.extras != null) {
            title = safeGetString(notification.extras, Notification.EXTRA_TITLE);
        }
        Log.d(TAG, "🗑 通知已移除 [" + pkg + "] 标题: " + title + " id=" + sbn.getId());
        if (WECHAT_PACKAGE.equals(pkg) || ALIPAY_PACKAGE.equals(pkg) || getPackageName().equals(pkg)) {
            LogStore.d(this, TAG, "通知已移除 [" + pkg + "] 标题: " + title + " id=" + sbn.getId());
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");
        LogStore.w(this, TAG, "通知监听服务销毁");
        isConnected = false;
        isRunning = false;
        KeepAliveForegroundService.start(this, KeepAliveForegroundService.ACTION_HEALTH_CHECK);
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        releaseWakeLock();
        super.onDestroy();
    }

    // ===================== 工具方法 =====================

    private String safeGetString(Bundle extras, String key) {
        if (extras == null) return "";
        CharSequence cs = extras.getCharSequence(key);
        return cs != null ? cs.toString().trim() : "";
    }

    private String mergeText(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!TextUtils.isEmpty(p)) {
                sb.append(p).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private boolean containsAny(String text, String[] keywords) {
        if (TextUtils.isEmpty(text)) return false;
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show()
        );
    }

    public static String md5(String string) {
        return ProtocolUtil.md5(string);
    }
}
