package com.vone.mq.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpRequest {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequest.class);
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_RESPONSE_CHARS = 4096;

    public static String sendGet(String url, String param) {
        String joiner = url.contains("?") ? "&" : "?";
        return request("GET", url + joiner + (param == null ? "" : param), null);
    }

    public static String sendPost(String url, String param) {
        return request("POST", url, param == null ? "" : param);
    }

    private static String request(String method, String url, String body) {
        HttpURLConnection conn = null;
        try {
            URL realUrl = new URL(url);
            conn = (HttpURLConnection) realUrl.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod(method);
            conn.setRequestProperty("accept", "text/plain,application/json,*/*");
            conn.setRequestProperty("user-agent", "VMQ-Suite/secure-callback");
            if ("POST".equals(method)) {
                conn.setDoOutput(true);
                conn.setRequestProperty("content-type", "application/x-www-form-urlencoded;charset=UTF-8");
                try (PrintWriter out = new PrintWriter(new OutputStreamWriter(conn.getOutputStream(), "UTF-8"))) {
                    out.print(body);
                }
            }
            int status = conn.getResponseCode();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    status >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"))) {
                return readLimited(in);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to send {} request: {}", method, SafeLogUtil.redact(e.toString()));
            return "服务器无响应";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readLimited(BufferedReader in) throws Exception {
        if (in == null) return "";
        StringBuilder result = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1 && result.length() < MAX_RESPONSE_CHARS) {
            result.append((char) ch);
        }
        return result.toString();
    }
}
