package com.vone.vmq;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ProtocolUtil {
    private ProtocolUtil() {}

    public static final class Config {
        public final String host;
        public final String key;

        private Config(String host, String key) {
            this.host = host;
            this.key = key;
        }
    }

    /**
     * 所有生产请求均使用 HTTPS。历史配置中的 HTTP 地址会被拒绝，不再自动降级。
     */
    public static String getProtocol(String host) {
        if (host == null || host.trim().isEmpty()) {
            return "https://";
        }
        String h = host.trim().toLowerCase();
        return "https://";
    }

    public static boolean isSecureHost(String host) {
        if (host == null || host.trim().isEmpty()) return false;
        String h = host.trim().toLowerCase();
        return !h.startsWith("http://") && !h.startsWith("vmq://")
                && (!h.contains("://") || h.startsWith("https://"));
    }

    /**
     * 过滤 host 中已带有的 http:// 或 https:// 协议头，返回纯地址，避免拼装重复。
     */
    public static String cleanHost(String host) {
        if (host == null) return "";
        String h = host.trim();
        if (h.toLowerCase().startsWith("http://")) {
            return h.substring(7);
        }
        if (h.toLowerCase().startsWith("https://")) {
            return h.substring(8);
        }
        return h;
    }

    public static Config parseConfig(String content) {
        if (content == null) return null;
        String text = content.trim();
        if (text.isEmpty()) return null;
        Config vmqConfig = parseVmqConfig(text);
        if (vmqConfig != null) return vmqConfig;
        return parseLegacyConfig(text);
    }

    private static Config parseVmqConfig(String text) {
        String lower = text.toLowerCase();
        if (!lower.startsWith("vmq://config?")) return null;
        String query = text.substring(text.indexOf('?') + 1);
        String host = null;
        String key = null;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            String name = decode(pair.substring(0, index));
            String value = decode(pair.substring(index + 1));
            if ("url".equals(name)) {
                host = value;
            } else if ("key".equals(name)) {
                key = value;
            }
        }
        return createConfig(host, key);
    }

    private static Config parseLegacyConfig(String text) {
        int separator = text.lastIndexOf('/');
        if (separator <= 0 || separator >= text.length() - 1) return null;
        return createConfig(text.substring(0, separator), text.substring(separator + 1));
    }

    private static Config createConfig(String host, String key) {
        if (host == null || key == null) return null;
        String cleanHost = host.trim();
        String cleanKey = key.trim();
        if (cleanHost.isEmpty() || cleanKey.isEmpty() || !isSecureHost(cleanHost)) return null;
        return new Config(cleanHost, cleanKey);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    public static String appPushUrl(String host, int type, String price, String timestamp, String key) {
        String protocol = getProtocol(host);
        String cleanHost = cleanHost(host);
        String sign = md5(type + "" + price + timestamp + key);
        return protocol + cleanHost + "/appPush?t=" + timestamp
                + "&type=" + type + "&price=" + price + "&sign=" + sign;
    }

    public static String md5(String string) {
        if (string == null || string.isEmpty()) return "";
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) result.append("0");
                result.append(hex);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
