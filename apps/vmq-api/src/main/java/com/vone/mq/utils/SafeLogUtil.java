package com.vone.mq.utils;

public final class SafeLogUtil {
    private SafeLogUtil() {}

    public static String redact(String text) {
        if (text == null) return null;
        String out = text;
        out = out.replaceAll("(?i)(sign=)[^&\\s]+", "$1***");
        out = out.replaceAll("(?i)(key=)[^&\\s]+", "$1***");
        out = out.replaceAll("(?i)(token=)[^&\\s]+", "$1***");
        out = out.replaceAll("(?i)(pass(word)?=)[^&\\s]+", "$1***");
        return out;
    }
}
