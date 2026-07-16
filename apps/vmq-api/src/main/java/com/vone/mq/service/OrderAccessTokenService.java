package com.vone.mq.service;

import com.vone.mq.utils.SigningUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OrderAccessTokenService {
    public String issue(String orderId, long expiresAt, String key) {
        return token(orderId, expiresAt, key);
    }

    public boolean verify(String orderId, String accessToken, String key) {
        if (!StringUtils.hasText(orderId) || !StringUtils.hasText(accessToken)) {
            return false;
        }
        String[] parts = accessToken.split("\\.", 2);
        if (parts.length != 2) {
            return false;
        }
        try {
            long expiresAt = Long.parseLong(parts[0]);
            if (expiresAt < System.currentTimeMillis()) {
                return false;
            }
            return SigningUtil.constantTimeEquals(parts[1], token(orderId, expiresAt, key).split("\\.", 2)[1]);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String token(String orderId, long expiresAt, String key) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderId", orderId);
        params.put("expiresAt", String.valueOf(expiresAt));
        return expiresAt + "." + SigningUtil.hmacSha256Hex(key, SigningUtil.canonicalize(params));
    }
}
