package com.vone.mq.service;

import com.vone.mq.dao.SettingDao;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.entity.Setting;
import com.vone.mq.utils.PasswordUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminSettingServiceTest {
    private AdminSettingService service;
    private SettingDao settingDao;
    private SettingAccessService settingAccessService;

    @Before
    public void setUp() {
        service = new AdminSettingService();
        settingDao = mock(SettingDao.class);
        settingAccessService = mock(SettingAccessService.class);
        ReflectionTestUtils.setField(service, "settingDao", settingDao);
        ReflectionTestUtils.setField(service, "settingAccessService", settingAccessService);
    }

    @Test
    public void saveSettingSavesValidatedValuesAndKeepsBlankKey() {
        CommonRes result = service.saveSetting(
                "admin",
                "new-password",
                " https://example.com/notify ",
                "https://example.com/return",
                " ",
                "wx-code",
                "zfb-code",
                " 10 ",
                " 2 ",
                " 1 "
        );

        assertEquals(1, result.getCode());
        verifySaved("user", "admin");
        verifySaved("notifyUrl", "https://example.com/notify");
        verifySaved("returnUrl", "https://example.com/return");
        verifySaved("wxpay", "wx-code");
        verifySaved("zfbpay", "zfb-code");
        verifySaved("close", "10");
        verifySaved("payQf", "2");
        verifySaved("callbackAsync", "1");
        verify(settingAccessService, never()).saveValue(eq("key"), any());
    }

    @Test
    public void saveSettingDoesNotUpdateMaskedPassword() {
        CommonRes result = service.saveSetting(
                "admin",
                "********",
                "",
                "",
                "secret-key",
                "",
                "",
                "5",
                "1",
                null
        );

        assertEquals(1, result.getCode());
        verify(settingAccessService, never()).saveValue(eq("pass"), any());
        verifySaved("key", "secret-key");
        verifySaved("callbackAsync", "0");
    }

    @Test
    public void saveSettingHashesNewPassword() {
        CommonRes result = service.saveSetting(
                "admin",
                "new-password",
                "",
                "",
                "",
                "",
                "",
                "5",
                "1",
                "0"
        );

        assertEquals(1, result.getCode());
        verify(settingAccessService).saveValue(eq("pass"), argThat(value -> PasswordUtil.matches("new-password", value)));
    }

    @Test
    public void saveSettingRejectsUnsafeNotifyUrlBeforeSaving() {
        CommonRes result = service.saveSetting(
                "admin",
                "",
                "http://127.0.0.1:8080/notify",
                "",
                "",
                "",
                "",
                "5",
                "1",
                "0"
        );

        assertEquals(-1, result.getCode());
        assertEquals("异步通知地址不安全", result.getMsg());
        verify(settingAccessService, never()).saveValue(any(), any());
    }

    @Test
    public void saveSettingRejectsUnsafeReturnUrlBeforeSaving() {
        CommonRes result = service.saveSetting(
                "admin",
                "",
                "",
                "file:///etc/passwd",
                "",
                "",
                "",
                "5",
                "1",
                "0"
        );

        assertEquals(-1, result.getCode());
        assertEquals("同步跳转地址不安全", result.getMsg());
        verify(settingAccessService, never()).saveValue(any(), any());
    }

    @Test
    public void saveSettingRejectsInvalidCloseBeforeSaving() {
        CommonRes result = service.saveSetting(
                "admin",
                "",
                "",
                "",
                "",
                "",
                "",
                "0",
                "1",
                "0"
        );

        assertEquals(-1, result.getCode());
        assertEquals("订单有效期必须为正整数", result.getMsg());
        verify(settingAccessService, never()).saveValue(any(), any());
    }

    @Test
    public void saveSettingRejectsInvalidPayQfBeforeSaving() {
        CommonRes result = service.saveSetting(
                "admin",
                "",
                "",
                "",
                "",
                "",
                "",
                "5",
                "3",
                "0"
        );

        assertEquals(-1, result.getCode());
        assertEquals("金额区分方向错误=>1|递增 2|递减", result.getMsg());
        verify(settingAccessService, never()).saveValue(any(), any());
    }

    @Test
    public void saveSettingRejectsInvalidCallbackAsyncBeforeSaving() {
        CommonRes result = service.saveSetting(
                "admin",
                "",
                "",
                "",
                "",
                "",
                "",
                "5",
                "1",
                "2"
        );

        assertEquals(-1, result.getCode());
        assertEquals("回调模式错误=>0|同步 1|异步", result.getMsg());
        verify(settingAccessService, never()).saveValue(any(), any());
    }

    @Test
    public void getSettingsMasksPassword() {
        when(settingDao.findAll()).thenReturn(Arrays.asList(
                setting("user", "admin"),
                setting("pass", "$2a$secret")
        ));

        CommonRes result = service.getSettings();

        assertEquals(1, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("admin", data.get("user"));
        assertEquals(AdminSettingService.MASKED_PASSWORD, data.get("pass"));
    }

    private Setting setting(String key, String value) {
        Setting setting = new Setting();
        setting.setVkey(key);
        setting.setVvalue(value);
        return setting;
    }

    private void verifySaved(String key, String value) {
        verify(settingAccessService).saveValue(key, value);
    }
}
