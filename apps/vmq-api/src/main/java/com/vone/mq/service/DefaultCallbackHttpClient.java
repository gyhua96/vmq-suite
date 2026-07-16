package com.vone.mq.service;

import com.vone.mq.utils.SafeLogUtil;
import com.vone.mq.utils.UrlSecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Set;

@Service
public class DefaultCallbackHttpClient implements CallbackHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCallbackHttpClient.class);
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_RESPONSE_CHARS = 4096;

    @Override
    public String sendGet(String url, String query) {
        String fullUrl = buildGetUrl(url, query);
        HttpURLConnection conn = null;
        try {
            URI target = URI.create(fullUrl);
            Set<String> resolvedAddresses = UrlSecurityUtil.resolvePublic(target.getHost());
            conn = (HttpURLConnection) new URL(fullUrl).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("accept", "text/plain,application/json,*/*");
            conn.setRequestProperty("user-agent", "VMQ-Suite/secure-callback");
            conn.connect();
            if (!UrlSecurityUtil.samePublicResolution(target.getHost(), resolvedAddresses)) {
                throw new SecurityException("callback DNS resolution changed during connection setup");
            }
            int status = conn.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    status >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
            try {
                return readLimited(in);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to send callback GET request: {}", SafeLogUtil.redact(e.toString()));
            return "服务器无响应";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String buildGetUrl(String url, String query) {
        String safeQuery = query == null ? "" : query;
        if (safeQuery.length() == 0) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + safeQuery;
    }

    private String readLimited(BufferedReader in) throws Exception {
        if (in == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1 && result.length() < MAX_RESPONSE_CHARS) {
            result.append((char) ch);
        }
        return result.toString();
    }
}
