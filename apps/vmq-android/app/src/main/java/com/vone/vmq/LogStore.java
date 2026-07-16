package com.vone.vmq;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 应用内运行日志存储。
 *
 * Android 普通应用无法读取全系统 logcat；这里记录本 App 自己的关键运行日志，
 * 存放在 App 私有目录中，仅用于用户排查监听/心跳/推送/权限问题。
 */
public class LogStore {
    private static final String TAG = "VmqLogStore";
    private static final String LOG_FILE_NAME = "vmq_runtime.log";
    private static final long MAX_LOG_BYTES = 256 * 1024; // 256KB，避免无限增长
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA);

    public static void d(Context context, String tag, String message) {
        append(context, "D", tag, message, null);
    }

    public static void i(Context context, String tag, String message) {
        append(context, "I", tag, message, null);
    }

    public static void w(Context context, String tag, String message) {
        append(context, "W", tag, message, null);
    }

    public static void e(Context context, String tag, String message) {
        append(context, "E", tag, message, null);
    }

    public static void e(Context context, String tag, String message, Throwable throwable) {
        append(context, "E", tag, message, throwable);
    }

    public static synchronized void append(Context context, String level, String tag, String message, Throwable throwable) {
        if (context == null) return;
        try {
            File file = getLogFile(context);
            rotateIfNeeded(file);

            FileWriter writer = new FileWriter(file, true);
            writer.write(FORMAT.format(new Date()));
            writer.write(" ");
            writer.write(level);
            writer.write("/");
            writer.write(safe(tag));
            writer.write("  ");
            writer.write(safe(message));
            if (throwable != null) {
                writer.write("  | ");
                writer.write(throwable.getClass().getSimpleName());
                writer.write(": ");
                writer.write(safe(throwable.getMessage()));
            }
            writer.write("\n");
            writer.close();
        } catch (Exception e) {
            Log.w(TAG, "写入应用日志失败: " + e.getMessage());
        }
    }

    public static synchronized String readLogs(Context context) {
        if (context == null) return "";
        File file = getLogFile(context);
        if (!file.exists()) {
            return "暂无运行日志";
        }
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
        } catch (IOException e) {
            return "读取日志失败：" + e.getMessage();
        }
        String result = sb.toString().trim();
        return TextUtils.isEmpty(result) ? "暂无运行日志" : result;
    }

    public static synchronized void clearLogs(Context context) {
        if (context == null) return;
        File file = getLogFile(context);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        i(context, "LogStore", "运行日志已清空");
    }

    private static File getLogFile(Context context) {
        return new File(context.getFilesDir(), LOG_FILE_NAME);
    }

    private static void rotateIfNeeded(File file) {
        if (!file.exists() || file.length() <= MAX_LOG_BYTES) {
            return;
        }
        File old = new File(file.getParentFile(), LOG_FILE_NAME + ".old");
        if (old.exists()) {
            //noinspection ResultOfMethodCallIgnored
            old.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        file.renameTo(old);
    }

    private static String safe(String text) {
        return Redactor.redact(text);
    }
}
