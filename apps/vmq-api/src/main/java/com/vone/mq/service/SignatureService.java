package com.vone.mq.service;

import com.vone.mq.utils.SigningUtil;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SignatureService {
    private static final String HMAC_SHA256 = "HMAC_SHA256";
    private static final long MERCHANT_WINDOW_MILLIS = 5 * 60 * 1000L;
    private static final long APP_WINDOW_MILLIS = 60 * 1000L;

    @Autowired(required = false)
    private RequestNonceService requestNonceService;

    public boolean verifyMerchant(Map<String, String> params, String key, String providedSign, String legacySource) {
        if (isHmac(params)) {
            if (!freshTimestamp(params.get("timestamp"), MERCHANT_WINDOW_MILLIS)) return false;
            String expected = SigningUtil.hmacSha256Hex(key, SigningUtil.canonicalize(params));
            if (!SigningUtil.constantTimeEquals(providedSign, expected)) return false;
            return consumeNonce("merchant", params.get("nonce"), MERCHANT_WINDOW_MILLIS);
        }
        return SigningUtil.constantTimeEquals(providedSign, SigningUtil.legacyMd5(legacySource));
    }

    public boolean verifyApp(Map<String, String> params, String key, String providedSign, String legacySource) {
        if (isHmac(params)) {
            if (!freshTimestamp(params.get("t"), APP_WINDOW_MILLIS)) return false;
            String expected = SigningUtil.hmacSha256Hex(key, SigningUtil.canonicalize(params));
            if (!SigningUtil.constantTimeEquals(providedSign, expected)) return false;
            return consumeNonce("app", params.get("nonce"), APP_WINDOW_MILLIS);
        }
        return SigningUtil.constantTimeEquals(providedSign, SigningUtil.legacyMd5(legacySource));
    }

    public boolean verifyOrderQuery(String orderId, String sign, String signType, String timestamp, String nonce, String key) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderId", orderId);
        if (signType != null) params.put("signType", signType);
        if (timestamp != null) params.put("timestamp", timestamp);
        if (nonce != null) params.put("nonce", nonce);
        return verifyMerchant(params, key, sign, orderId + key);
    }

    public boolean verifyLegacyStateQuery(String t, String sign, String key) {
        return SigningUtil.constantTimeEquals(sign, SigningUtil.legacyMd5(t + key));
    }

    private boolean isHmac(Map<String, String> params) {
        return params != null && HMAC_SHA256.equalsIgnoreCase(params.get("signType"));
    }

    private boolean consumeNonce(String scope, String nonce, long windowMillis) {
        if (!StringUtils.hasText(nonce)) return false;
        if (requestNonceService == null) return true;
        return requestNonceService.consume(scope, nonce, System.currentTimeMillis() + windowMillis);
    }

    private boolean freshTimestamp(String value, long windowMillis) {
        try {
            long delta = Math.abs(Long.parseLong(value) - System.currentTimeMillis());
            return delta <= windowMillis;
        } catch (Exception e) {
            return false;
        }
    }
}
