package com.vone.mq.service;

import com.vone.mq.utils.SigningUtil;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SignatureServiceTest {
    private final SignatureService signatureService = new SignatureService();

    @Test
    public void verifiesLegacyMerchantSignature() {
        String key = "secret";
        String source = "ORDERp249.95" + key;
        assertTrue(signatureService.verifyMerchant(new LinkedHashMap<>(), key, SigningUtil.legacyMd5(source), source));
        assertFalse(signatureService.verifyMerchant(new LinkedHashMap<>(), key, "bad", source));
    }

    @Test
    public void verifiesHmacMerchantSignatureWithFreshTimestamp() {
        String key = "secret";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("payId", "ORDER");
        params.put("param", "p");
        params.put("type", "2");
        params.put("price", "49.95");
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("nonce", "n1");
        params.put("signType", "HMAC_SHA256");
        String sign = SigningUtil.hmacSha256Hex(key, SigningUtil.canonicalize(params));

        assertTrue(signatureService.verifyMerchant(params, key, sign, "legacy"));
        assertFalse(signatureService.verifyMerchant(params, key, "bad", "legacy"));
    }

    @Test
    public void rejectsHmacMerchantSignatureWithMissingTimestamp() {
        String key = "secret";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderId", "202607010001");
        params.put("signType", "HMAC_SHA256");
        String sign = SigningUtil.hmacSha256Hex(key, SigningUtil.canonicalize(params));

        assertFalse(signatureService.verifyMerchant(params, key, sign, "legacy"));
    }

    @Test
    public void verifiesLegacyAppSignature() {
        String key = "secret";
        String t = String.valueOf(System.currentTimeMillis());
        String source = "1" + "1.0" + t + key;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("type", "1");
        params.put("price", "1.0");
        params.put("t", t);

        assertTrue(signatureService.verifyApp(params, key, SigningUtil.legacyMd5(source), source));
    }
}
