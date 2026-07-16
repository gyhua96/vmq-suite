package com.vone.vmq;

import android.Manifest;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.vone.qrcode.R;
import com.vone.vmq.util.Constant;
import com.google.zxing.activity.CaptureActivity;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity{


    private TextView txthost;
    private TextView txtkey;
    private TextView txtKeepAliveStatus;

    private boolean isOk = false;
    private static String TAG = "MainActivity";

    private static String host;
    private static String key;

    int id = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LogStore.i(this, TAG, "主界面启动");



        txthost = (TextView) findViewById(R.id.txt_host);
        txtkey = (TextView) findViewById(R.id.txt_key);
        txtKeepAliveStatus = (TextView) findViewById(R.id.txt_keep_alive_status);

        requestPostNotificationPermissionIfNeeded();
        KeepAliveForegroundService.start(this);



        //检测通知使用权是否启用
        if (!isNotificationListenersEnabled()) {
            //跳转到通知使用权页面
            gotoNotificationAccessSetting();
        }
        //重启监听服务
        toggleNotificationListenerService(this);



        //读入保存的配置数据并显示
        host = SecurePrefs.getHost(this);
        key = SecurePrefs.getKey(this);

        if (host!=null && key!=null && host!="" && key!=""){
            txthost.setText(" 通知地址："+host);
            txtkey.setText(" 通讯密钥："+ key);
            isOk = true;
        }


        Toast.makeText(MainActivity.this, "v免签开源免费免签系统 v1.8.1", Toast.LENGTH_SHORT).show();
        updateKeepAliveStatus();


    }

    @Override
    protected void onResume() {
        super.onResume();
        updateKeepAliveStatus();
    }



    //扫码配置
    public void startQrCode(View v) {
        LogStore.i(this, TAG, "用户点击扫码配置");
        // 申请相机权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, Constant.REQ_PERM_CAMERA);
            return;
        }
        // 申请文件读写权限（部分朋友遇到相册选图需要读写权限的情况，这里一并写一下）
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, Constant.REQ_PERM_EXTERNAL_STORAGE);
            return;
        }
        // 二维码扫码
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        startActivityForResult(intent, Constant.REQ_QR_CODE);
    }
    //手动配置
    public void doInput(View v){
        LogStore.i(this, TAG, "用户打开手动配置弹窗");
        final EditText inputServer = new EditText(this);
        inputServer.setSingleLine(true);
        inputServer.setHint("示例：192.168.1.100:8080/通信密钥");
        inputServer.setTextColor(getResources().getColor(R.color.modernTextPrimary));
        inputServer.setHintTextColor(getResources().getColor(R.color.modernTextMuted));
        inputServer.setBackgroundResource(R.drawable.bg_edit_text);
        inputServer.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout dialogView = new LinearLayout(this);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setPadding(dp(4), dp(4), dp(4), dp(2));

        TextView tip = new TextView(this);
        tip.setText("请输入网站后台展示的配置数据，格式为：服务器地址/通信密钥。");
        tip.setTextColor(getResources().getColor(R.color.modernTextSecondary));
        tip.setTextSize(14);
        tip.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tipLp.setMargins(0, 0, 0, dp(12));
        dialogView.addView(tip, tipLp);
        dialogView.addView(inputServer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.ModernDialog)
                .setTitle("手动配置")
                .setView(dialogView)
                .setNegativeButton("取消", null)
                .create();
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, "保存并检测", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                String scanResult = inputServer.getText().toString();

                ProtocolUtil.Config config = ProtocolUtil.parseConfig(scanResult);
                if (config == null){
                    LogStore.w(MainActivity.this, TAG, "手动配置失败：配置格式错误");
                    Toast.makeText(MainActivity.this, "数据错误，请您输入网站上显示的配置数据!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String t = String.valueOf(new Date().getTime());
                String sign = md5(t+config.key);


                OkHttpClient okHttpClient = new OkHttpClient();
                String protocol = ProtocolUtil.getProtocol(config.host);
                String cleanHost = ProtocolUtil.cleanHost(config.host);
                Request request = new Request.Builder().url(protocol+cleanHost+"/appHeart?t="+t+"&sign="+sign).method("GET",null).build();
                Call call = okHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        LogStore.e(MainActivity.this, TAG, "手动配置心跳检测失败", e);
                    }
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String body = response.body() != null ? response.body().string() : "";
                        Log.d(TAG, "onResponse: "+ Redactor.redact(body));
                        LogStore.i(MainActivity.this, TAG, "手动配置心跳检测响应：" + body);
                        isOk = true;

                    }
                });
                if (config.host.indexOf("localhost")>=0){
                    LogStore.w(MainActivity.this, TAG, "手动配置包含 localhost，已拒绝保存");
                    Toast.makeText(MainActivity.this, "配置信息错误，本机调试请访问 本机局域网IP:8080(如192.168.1.101:8080) 获取配置信息进行配置!", Toast.LENGTH_LONG).show();

                    return;
                }
                //将扫描出的信息显示出来
                txthost.setText(" 通知地址："+config.host);
                txtkey.setText(" 通讯密钥："+config.key);
                host = config.host;
                key = config.key;

                SecurePrefs.save(MainActivity.this, host, key);
                KeepAliveForegroundService.start(MainActivity.this, KeepAliveForegroundService.ACTION_HEALTH_CHECK);
                updateKeepAliveStatus();
                LogStore.i(MainActivity.this, TAG, "手动配置已保存，host=" + host);

            }
        });
        dialog.show();
        applyModernDialog(dialog);

    }
    //检测心跳
    public void doStart(View view) {
        LogStore.i(this, TAG, "用户点击检测心跳");
        
        // 每次检测前，先从 SecurePrefs 中拉取最新值，防止全局静态变量在生命周期中被置空或污染
        host = SecurePrefs.getHost(this);
        key = SecurePrefs.getKey(this);
        
        if (host == null || key == null || host.isEmpty() || key.isEmpty()) {
            isOk = false;
            LogStore.w(this, TAG, "检测心跳失败：尚未配置服务器或配置为空");
            Toast.makeText(MainActivity.this, "请您先配置!", Toast.LENGTH_SHORT).show();
            return;
        }
        isOk = true;

        String t = String.valueOf(new Date().getTime());
        String sign = md5(t+key);
        LogStore.i(MainActivity.this, TAG, "点击心跳参数: host=" + host + ", key=" + key + ", t=" + t + ", sign=" + sign);

        OkHttpClient okHttpClient = new OkHttpClient();
        String protocol = ProtocolUtil.getProtocol(host);
        String cleanHost = ProtocolUtil.cleanHost(host);
        Request request = new Request.Builder().url(protocol+cleanHost+"/appHeart?t="+t+"&sign="+sign).method("GET",null).build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                LogStore.e(MainActivity.this, TAG, "心跳检测失败", e);
                Looper.prepare();
                Toast.makeText(MainActivity.this, "心跳状态错误，请检查配置是否正确!", Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                LogStore.i(MainActivity.this, TAG, "心跳检测返回：" + body);
                Looper.prepare();
                Toast.makeText(MainActivity.this, "心跳返回："+body, Toast.LENGTH_LONG).show();
                Looper.loop();
            }
        });
    }
    //检测监听
    public void checkPush(View v){
        LogStore.i(this, TAG, "用户点击检测监听，发送测试通知");

        Notification mNotification;
        NotificationManager mNotificationManager;
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("1",
                    "Channel1", NotificationManager.IMPORTANCE_DEFAULT);
            channel.enableLights(true);
            channel.setLightColor(Color.GREEN);
            channel.setShowBadge(true);
            mNotificationManager.createNotificationChannel(channel);

            Notification.Builder builder = new Notification.Builder(this,"1");

            mNotification = builder
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setTicker("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .setContentTitle("V免签测试推送")
                    .setContentText("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .build();
        }else{
            mNotification = new Notification.Builder(MainActivity.this)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setTicker("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .setContentTitle("V免签测试推送")
                    .setContentText("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .build();
        }

        //Toast.makeText(MainActivity.this, "已推送信息，如果权限，那么将会有下一条提示！", Toast.LENGTH_SHORT).show();



        mNotificationManager.notify(id++, mNotification);
    }

    public void showRuntimeLogs(View v) {
        final String logs = LogStore.readLogs(this);

        TextView logText = new TextView(this);
        logText.setText(logs);
        logText.setTextColor(getResources().getColor(R.color.modernTextPrimary));
        logText.setTextSize(12);
        logText.setTextIsSelectable(true);
        logText.setLineSpacing(dp(2), 1.0f);
        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        logText.setBackgroundResource(R.drawable.bg_info_row);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.addView(logText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        scrollView.setPadding(dp(2), dp(4), dp(2), dp(2));

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.ModernDialog)
                .setTitle("运行日志")
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .setNeutralButton("复制", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("V免签运行日志", logs));
                            Toast.makeText(MainActivity.this, "运行日志已复制", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        LogStore.clearLogs(MainActivity.this);
                        Toast.makeText(MainActivity.this, "运行日志已清空", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
        applyModernDialog(dialog);
    }

    public void openBatteryOptimizationSettings(View v) {
        showGuideDialog(
                "关闭电池优化",
                "建议路径：设置 → 电池 → 应用耗电管理/不受限的应用 → 找到 V免签 → 设为允许后台运行或不受限制。\n\n鸿蒙省电模式会限制应用后台活动，如果这里没有放行，锁屏后心跳可能被系统延迟到亮屏才恢复。",
                new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                            if (powerManager != null
                                    && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                                Intent requestIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                requestIntent.setData(Uri.parse("package:" + getPackageName()));
                                if (tryStartActivity(requestIntent)) {
                                    return;
                                }
                            }
                        }
                        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(intent);
                    }
                }
        );
    }

    public void openPowerSaveSettings(View v) {
        boolean powerSaveMode = KeepAliveForegroundService.isPowerSaveMode(this);
        showGuideDialog(
                "关闭系统省电模式",
                (powerSaveMode ? "当前检测到系统省电模式已开启。\n\n" : "")
                        + "省电模式会限制应用后台活动、降低系统运行性能，锁屏后可能导致心跳和通知监听被延迟。监听专用手机建议关闭系统省电模式；如果必须开启，请同时把 V免签设为不受限制、允许自启动和允许后台运行。",
                new Runnable() {
                    @Override
                    public void run() {
                        if (!tryStartActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                                && !tryStartActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY))) {
                            tryStartActivity(new Intent(Settings.ACTION_SETTINGS));
                        }
                    }
                }
        );
    }

    public void showLockBackgroundGuide(View v) {
        showGuideDialog(
                "锁定后台",
                "建议路径：打开最近任务列表 → 找到 V免签卡片 → 点击锁定图标。\n\n这是系统桌面/任务管理器能力，通常无法由 App 直接跳转，请按提示手动操作。",
                null
        );
    }

    public void openAutoStartSettings(View v) {
        showGuideDialog(
                "允许自启动",
                "建议路径：设置 → 应用和服务 → 应用启动管理 → V免签 → 手动管理 → 同时开启允许自启动、允许关联启动、允许后台活动。\n\n点击“去设置”会优先尝试打开华为/鸿蒙和常见国产 ROM 的自启动页面；如果失败，会打开本应用详情页。",
                new Runnable() {
                    @Override
                    public void run() {
                        if (!tryStartActivity(new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                                && !tryStartActivity(new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")))
                                && !tryStartActivity(new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
                                && !tryStartActivity(new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
                                && !tryStartActivity(new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                                && !tryStartActivity(new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))) {
                            openAppDetails();
                        }
                    }
                }
        );
    }

    public void openPaymentNotificationSettings(View v) {
        showGuideDialog(
                "确认通知开启",
                "请确认以下通知权限均已开启：\n\n1. V免签：用于显示常驻监听状态。\n2. 微信：必须允许通知，否则无法收到收款通知。\n3. 支付宝：必须允许通知，否则无法收到收款通知。\n\n点击“去设置”会打开系统通知设置页。",
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                            startActivity(intent);
                        } catch (Exception e) {
                            tryStartActivity(new Intent("android.settings.NOTIFICATION_SETTINGS"));
                        }
                    }
                }
        );
    }

    public void openExactAlarmSettings(View v) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, "当前系统不需要单独开启精准闹钟权限", Toast.LENGTH_SHORT).show();
            return;
        }
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null && alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(this, "精准闹钟权限已开启", Toast.LENGTH_SHORT).show();
            return;
        }
        showGuideDialog(
                "允许精准闹钟",
                "建议开启“闹钟和提醒/精准闹钟”权限，用于锁屏和省电模式下尽量准时触发保活看门狗。\n\n不同系统入口名称可能略有差异；如果无法直接打开，请进入应用详情页手动开启。",
                new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            if (tryStartActivity(intent)) {
                                return;
                            }
                        }
                        openAppDetails();
                    }
                }
        );
    }







    //各种权限的判断
    private void toggleNotificationListenerService(Context context) {
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(context, NeNotificationService2.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        pm.setComponentEnabledSetting(new ComponentName(context, NeNotificationService2.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        Toast.makeText(MainActivity.this, "监听服务启动中...", Toast.LENGTH_SHORT).show();
    }
    public boolean isNotificationListenersEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (int i = 0; i < names.length; i++) {
                final ComponentName cn = ComponentName.unflattenFromString(names[i]);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    protected boolean gotoNotificationAccessSetting() {
        try {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;

        } catch (ActivityNotFoundException e) {//普通情况下找不到的时候需要再特殊处理找一次
            try {
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.Settings$NotificationAccessSettingsActivity");
                intent.setComponent(cn);
                intent.putExtra(":settings:show_fragment", "NotificationAccessSettings");
                startActivity(intent);
                return true;
            } catch (Exception e1) {
                LogStore.e(this, TAG, "Fallback notification access settings failed", e1);
            }
            Toast.makeText(this, "对不起，您的手机暂不支持", Toast.LENGTH_SHORT).show();
            LogStore.e(this, TAG, "Notification access settings are not supported", e);
            return false;
        }
    }

    private void requestPostNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    MainActivity.this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    3001
            );
        }
    }

    private void updateKeepAliveStatus() {
        if (txtKeepAliveStatus == null) return;
        boolean running = KeepAliveForegroundService.isMarkedRunning(this);
        boolean lastOk = KeepAliveForegroundService.getLastHeartbeatOk(this);
        long lastAt = KeepAliveForegroundService.getLastHeartbeatAt(this);
        String message = KeepAliveForegroundService.getLastHeartbeatMessage(this);
        boolean powerSaveMode = KeepAliveForegroundService.isPowerSaveMode(this);
        boolean ignoringBatteryOptimization = KeepAliveForegroundService.isIgnoringBatteryOptimizations(this);
        boolean exactAlarmAllowed = KeepAliveForegroundService.canScheduleExactAlarms(this);

        String heartbeatText;
        if (lastAt > 0L) {
            CharSequence time = android.text.format.DateFormat.format("HH:mm:ss", lastAt);
            heartbeatText = "，最近心跳 " + time + " " + (lastOk ? "成功" : "异常");
            if (!lastOk && !TextUtils.isEmpty(message)) {
                heartbeatText += "：" + message;
            }
        } else {
            heartbeatText = "，等待首次心跳";
        }
        if (powerSaveMode) {
            heartbeatText += "，系统省电已开";
        } else if (!ignoringBatteryOptimization) {
            heartbeatText += "，电池优化未放行";
        } else if (!exactAlarmAllowed) {
            heartbeatText += "，精准闹钟未开";
        }

        if (running && lastOk) {
            txtKeepAliveStatus.setText(" 保活服务：运行中" + heartbeatText);
            txtKeepAliveStatus.setTextColor(getResources().getColor(R.color.modernSuccess));
            txtKeepAliveStatus.setBackgroundResource(R.drawable.bg_status_chip_success);
        } else if (running) {
            txtKeepAliveStatus.setText(" 保活服务：运行中" + heartbeatText);
            txtKeepAliveStatus.setTextColor(getResources().getColor(R.color.modernWarning));
            txtKeepAliveStatus.setBackgroundResource(R.drawable.bg_status_chip_warning);
        } else {
            txtKeepAliveStatus.setText(" 保活服务：等待启动");
            txtKeepAliveStatus.setTextColor(getResources().getColor(R.color.modernWarning));
            txtKeepAliveStatus.setBackgroundResource(R.drawable.bg_status_chip_warning);
        }
    }

    private void showGuideDialog(String title, String message, final Runnable action) {
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.ModernDialog)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("知道了", null)
                .setPositiveButton(action == null ? "好的" : "去设置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (action != null) {
                            try {
                                action.run();
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "无法打开对应设置，请按提示路径手动进入", Toast.LENGTH_LONG).show();
                                openAppDetails();
                            }
                        }
                    }
                })
                .show();
        applyModernDialog(dialog);
    }

    private boolean tryStartActivity(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openAppDetails() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        tryStartActivity(intent);
    }

    private void applyModernDialog(AlertDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().getDecorView().setBackgroundResource(R.drawable.bg_dialog_panel);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }



    public static String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            String result = "";
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result += temp;
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "MD5 algorithm is not available", e);
        }
        return "";
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //扫描结果回调
        if (requestCode == Constant.REQ_QR_CODE && resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            String scanResult = bundle.getString(Constant.INTENT_EXTRA_KEY_QR_SCAN);

            ProtocolUtil.Config config = ProtocolUtil.parseConfig(scanResult);
            if (config == null){
                Toast.makeText(MainActivity.this, "二维码错误，请您扫描网站上显示的二维码!", Toast.LENGTH_SHORT).show();
                return;
            }

            String t = String.valueOf(new Date().getTime());
            String sign = md5(t+config.key);


            OkHttpClient okHttpClient = new OkHttpClient();
            String protocol = ProtocolUtil.getProtocol(config.host);
            String cleanHost = ProtocolUtil.cleanHost(config.host);
            Request request = new Request.Builder().url(protocol+cleanHost+"/appHeart?t="+t+"&sign="+sign).method("GET",null).build();
            Call call = okHttpClient.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {

                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    Log.d(TAG, "onResponse: "+response.body().string());
                    isOk = true;

                }
            });

            //将扫描出的信息显示出来
            txthost.setText(" 通知地址："+config.host);
            txtkey.setText(" 通讯密钥："+config.key);
            host = config.host;
            key = config.key;

            SecurePrefs.save(this, host, key);
            KeepAliveForegroundService.start(this, KeepAliveForegroundService.ACTION_HEALTH_CHECK);
            updateKeepAliveStatus();

        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case Constant.REQ_PERM_CAMERA:
                // 摄像头权限申请
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 获得授权
                    startQrCode(null);
                } else {
                    // 被禁止授权
                    Toast.makeText(MainActivity.this, "请至权限中心打开本应用的相机访问权限", Toast.LENGTH_LONG).show();
                }
                break;
            case Constant.REQ_PERM_EXTERNAL_STORAGE:
                // 文件读写权限申请
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 获得授权
                    startQrCode(null);
                } else {
                    // 被禁止授权
                    Toast.makeText(MainActivity.this, "请至权限中心打开本应用的文件读写权限", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }



}
