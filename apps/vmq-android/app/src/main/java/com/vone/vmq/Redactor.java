package com.vone.vmq;

public final class Redactor {
    private Redactor() {}

    public static String redact(String text) {
        if (text == null) return "";
        String out = text.replace('\n', ' ').replace('\r', ' ').trim();
        out = out.replaceAll("(?i)(sign=)[^&\\s]+", "$1***");
        out = out.replaceAll("(?i)(token=)[^&\\s]+", "$1***");
        out = out.replaceAll("(?i)(pass(word)?=)[^&\\s]+", "$1***");
        if (out.length() > 500) {
            out = out.substring(0, 500) + "...(truncated)";
        }
        return out;
    }
}
