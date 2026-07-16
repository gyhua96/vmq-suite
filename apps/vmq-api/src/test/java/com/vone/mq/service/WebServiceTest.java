package com.vone.mq.service;

import com.vone.mq.dto.CommonRes;
import com.vone.mq.utils.ResUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WebServiceTest {
    private static final String CLIENT_TIME_ERROR = "\u5ba2\u6237\u7aef\u65f6\u95f4\u9519\u8bef";

    private WebService service;
    private SignatureService signatureService;
    private SettingAccessService settingAccessService;
    private AppEventService appEventService;

    @Before
    public void setUp() {
        service = new WebService();
        signatureService = mock(SignatureService.class);
        settingAccessService = mock(SettingAccessService.class);
        appEventService = mock(AppEventService.class);
        ReflectionTestUtils.setField(service, "signatureService", signatureService);
        ReflectionTestUtils.setField(service, "settingAccessService", settingAccessService);
        ReflectionTestUtils.setField(service, "appEventService", appEventService);
    }

    @Test
    public void appHeartRejectsNonNumericTimestampBeforeSignatureVerification() {
        when(settingAccessService.communicationKey()).thenReturn("secret");

        CommonRes result = service.appHeart("not-a-time", "sign", null, null);

        assertEquals(-1, result.getCode());
        assertEquals(CLIENT_TIME_ERROR, result.getMsg());
        verify(signatureService, never()).verifyApp(any(Map.class), eq("secret"), eq("sign"), any(String.class));
        verify(appEventService, never()).recordHeartbeat(any(String.class));
    }

    @Test
    public void appPushRejectsNonNumericTimestampBeforeSignatureVerification() {
        when(settingAccessService.communicationKey()).thenReturn("secret");

        CommonRes result = service.appPush(2, "49.95", "not-a-time", "sign", null, null);

        assertEquals(-1, result.getCode());
        assertEquals(CLIENT_TIME_ERROR, result.getMsg());
        verify(signatureService, never()).verifyApp(any(Map.class), eq("secret"), eq("sign"), any(String.class));
        verify(appEventService, never()).handlePaymentPush(eq(2), eq("49.95"), any(String.class), eq("secret"));
    }

    @Test
    public void appHeartDelegatesWhenTimestampAndSignatureAreValid() {
        String t = String.valueOf(System.currentTimeMillis());
        when(settingAccessService.communicationKey()).thenReturn("secret");
        when(signatureService.verifyApp(any(Map.class), eq("secret"), eq("sign"), eq(t + "secret"))).thenReturn(true);
        when(appEventService.recordHeartbeat(t)).thenReturn(ResUtil.success());

        CommonRes result = service.appHeart(t, "sign", null, null);

        assertEquals(1, result.getCode());
        verify(appEventService).recordHeartbeat(t);
    }

    @Test
    public void appHeartAllowsDelayedTimestampWithinConfiguredWindow() {
        String t = String.valueOf(System.currentTimeMillis() - 45 * 1000L);
        when(settingAccessService.communicationKey()).thenReturn("secret");
        when(signatureService.verifyApp(any(Map.class), eq("secret"), eq("sign"), eq(t + "secret"))).thenReturn(true);
        when(appEventService.recordHeartbeat(t)).thenReturn(ResUtil.success());

        CommonRes result = service.appHeart(t, "sign", null, null);

        assertEquals(1, result.getCode());
        verify(appEventService).recordHeartbeat(t);
    }

    @Test
    public void appPushDelegatesWhenTimestampAndSignatureAreValid() {
        String t = String.valueOf(System.currentTimeMillis());
        when(settingAccessService.communicationKey()).thenReturn("secret");
        when(signatureService.verifyApp(any(Map.class), eq("secret"), eq("sign"), eq("249.95" + t + "secret")))
                .thenReturn(true);
        when(appEventService.handlePaymentPush(2, "49.95", t, "secret")).thenReturn(ResUtil.success());

        CommonRes result = service.appPush(2, "49.95", t, "sign", null, null);

        assertEquals(1, result.getCode());
        verify(appEventService).handlePaymentPush(2, "49.95", t, "secret");
    }
}
