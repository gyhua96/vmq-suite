package com.vone.mq.service;

import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.Setting;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CallbackServiceTest {
    private CallbackService callbackService;
    private FakeCallbackHttpClient httpClient;

    @Before
    public void setUp() {
        callbackService = new CallbackService();
        httpClient = new FakeCallbackHttpClient();
        ReflectionTestUtils.setField(callbackService, "callbackPayloadBuilder", new CallbackPayloadBuilder());
        ReflectionTestUtils.setField(callbackService, "callbackHttpClient", httpClient);
    }

    @Test
    public void sendsPayOrderNotifyUrlWhenResponseIsSuccess() {
        httpClient.response = "success";

        CallbackResult result = callbackService.sendNotify(order("https://example.com/callback"), Optional.empty(), "secret");

        assertTrue(result.isSuccess());
        assertEquals("https://example.com/callback", httpClient.url);
        assertTrue(httpClient.query.contains("payId=pay-1"));
        assertTrue(httpClient.query.contains("signType=HMAC_SHA256"));
    }

    @Test
    public void acceptsTrimmedCaseInsensitiveSuccessResponse() {
        httpClient.response = " SUCCESS\r\n";

        CallbackResult result = callbackService.sendNotify(order("https://example.com/callback"), Optional.empty(), "secret");

        assertTrue(result.isSuccess());
        assertEquals(" SUCCESS\r\n", result.getResponse());
    }

    @Test
    public void fallsBackToDefaultNotifyUrl() {
        httpClient.response = "success";
        Setting setting = new Setting();
        setting.setVkey("notifyUrl");
        setting.setVvalue("https://example.com/notify");

        CallbackResult result = callbackService.sendNotify(order(""), Optional.of(setting), "secret");

        assertTrue(result.isSuccess());
        assertEquals("https://example.com/notify", httpClient.url);
    }

    @Test
    public void rejectsMissingNotifyUrl() {
        CallbackResult result = callbackService.sendNotify(order(""), Optional.empty(), "secret");

        assertFalse(result.isSuccess());
        assertEquals("您还未配置异步通知地址，请现在系统配置中配置", result.getErrorMessage());
        assertEquals(null, httpClient.url);
    }

    @Test
    public void rejectsUnsafeNotifyUrl() {
        CallbackResult result = callbackService.sendNotify(order("http://127.0.0.1/callback"), Optional.empty(), "secret");

        assertFalse(result.isSuccess());
        assertEquals("异步通知地址不安全或不允许", result.getErrorMessage());
        assertEquals(null, httpClient.url);
    }

    @Test
    public void treatsNonSuccessResponseAsFailure() {
        httpClient.response = "failed";

        CallbackResult result = callbackService.sendNotify(order("https://example.com/callback"), Optional.empty(), "secret");

        assertFalse(result.isSuccess());
        assertEquals("failed", result.getResponse());
        assertEquals("通知异步地址失败", result.getErrorMessage());
    }

    private PayOrder order(String notifyUrl) {
        PayOrder payOrder = new PayOrder();
        payOrder.setPayId("pay-1");
        payOrder.setParam("param-1");
        payOrder.setType(2);
        payOrder.setPrice(49.95);
        payOrder.setReallyPrice(49.96);
        payOrder.setNotifyUrl(notifyUrl);
        return payOrder;
    }

    private static class FakeCallbackHttpClient implements CallbackHttpClient {
        private String url;
        private String query;
        private String response;

        @Override
        public String sendGet(String url, String query) {
            this.url = url;
            this.query = query;
            return response;
        }
    }
}
