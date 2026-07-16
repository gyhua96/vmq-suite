package com.vone.mq.utils;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class SecurityUtilTest {

    @Test
    public void bcryptHashMatchesPlaintext() {
        String hash = PasswordUtil.hash("strong-password");
        assertTrue(PasswordUtil.isHash(hash));
        assertTrue(PasswordUtil.matches("strong-password", hash));
        assertFalse(PasswordUtil.matches("wrong", hash));
    }

    @Test
    public void detectsLegacyDefaultPassword() {
        assertTrue(PasswordUtil.isDefaultPassword("admin"));
        assertFalse(PasswordUtil.isDefaultPassword(PasswordUtil.hash("admin")));
        assertFalse(PasswordUtil.isDefaultPassword("not-admin"));
    }

    @Test
    public void legacyPlaintextPasswordStillMatchesForMigration() {
        assertTrue(PasswordUtil.matches("legacy-strong-password", "legacy-strong-password"));
        assertFalse(PasswordUtil.matches("wrong", "legacy-strong-password"));
        assertFalse(PasswordUtil.isHash("legacy-strong-password"));
    }

    @Test
    public void hmacCanonicalSignatureIsDeterministicAndRejectsWrongValue() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("price", "12.30");
        params.put("type", "1");
        params.put("payId", "A001");
        params.put("sign", "ignored");
        String canonical = SigningUtil.canonicalize(params);
        assertEquals("payId=A001&price=12.30&type=1", canonical);
        String sig = SigningUtil.hmacSha256Hex("secret", canonical);
        assertTrue(SigningUtil.constantTimeEquals(sig, SigningUtil.hmacSha256Hex("secret", canonical)));
        assertFalse(SigningUtil.constantTimeEquals(sig, SigningUtil.hmacSha256Hex("other", canonical)));
    }

    @Test
    public void callbackParamsAreUrlEncoded() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("payId", "A&1");
        params.put("param", "x=y z");
        String query = CallbackParamUtil.toQuery(params);
        assertEquals("payId=A%261&param=x%3Dy+z", query);
    }

    @Test
    public void urlValidatorBlocksPrivateAndAllowsPublicHttps() {
        assertTrue(UrlSecurityUtil.isSafePublicCallbackUrl("https://example.com/callback"));
        assertFalse(UrlSecurityUtil.isSafePublicCallbackUrl("http://127.0.0.1:8080/callback"));
        assertFalse(UrlSecurityUtil.isSafePublicCallbackUrl("http://localhost:8080/callback"));
        assertFalse(UrlSecurityUtil.isSafePublicCallbackUrl("file:///etc/passwd"));
        assertFalse(UrlSecurityUtil.isSafePublicCallbackUrl("http://10.0.0.1/callback"));
    }
}
