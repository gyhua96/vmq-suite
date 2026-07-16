package com.vone.mq.service;

import com.vone.mq.dao.SettingDao;
import com.vone.mq.entity.Setting;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SettingAccessServiceTest {
    private SettingAccessService service;
    private SettingDao settingDao;

    @Before
    public void setUp() {
        service = new SettingAccessService();
        settingDao = mock(SettingDao.class);
        ReflectionTestUtils.setField(service, "settingDao", settingDao);
    }

    @Test
    public void typedDefaultsAvoidMissingSettingFailures() {
        when(settingDao.findById(SettingAccessService.KEY_CLOSE_MINUTES)).thenReturn(Optional.empty());
        when(settingDao.findById(SettingAccessService.KEY_PAY_QF)).thenReturn(Optional.of(setting("payQf", "bad")));
        when(settingDao.findById(SettingAccessService.KEY_CALLBACK_ASYNC)).thenReturn(Optional.empty());
        when(settingDao.findById(SettingAccessService.KEY_WX_PAY)).thenReturn(Optional.empty());

        assertEquals(5, service.closeMinutes());
        assertEquals(1, service.payQf());
        assertFalse(service.callbackAsyncEnabled());
        assertEquals("", service.payUrlForType(1));
    }

    @Test
    public void returnsConfiguredValuesWhenPresent() {
        when(settingDao.findById(SettingAccessService.KEY_CLOSE_MINUTES)).thenReturn(Optional.of(setting("close", "15")));
        when(settingDao.findById(SettingAccessService.KEY_PAY_QF)).thenReturn(Optional.of(setting("payQf", "2")));
        when(settingDao.findById(SettingAccessService.KEY_CALLBACK_ASYNC)).thenReturn(Optional.of(setting("callbackAsync", "1")));
        when(settingDao.findById(SettingAccessService.KEY_ZFB_PAY)).thenReturn(Optional.of(setting("zfbpay", "zfb-code")));

        assertEquals(15, service.closeMinutes());
        assertEquals(2, service.payQf());
        assertTrue(service.callbackAsyncEnabled());
        assertEquals("zfb-code", service.payUrlForType(2));
    }

    @Test
    public void getOrCreateValuePersistsMissingDefault() {
        when(settingDao.findById(SettingAccessService.KEY_LAST_HEART)).thenReturn(Optional.empty());

        String value = service.getOrCreateValue(SettingAccessService.KEY_LAST_HEART, "0");

        assertEquals("0", value);
        verify(settingDao).save(argThat(setting ->
                SettingAccessService.KEY_LAST_HEART.equals(setting.getVkey()) && "0".equals(setting.getVvalue())));
    }

    @Test
    public void ensureValuePersistsMissingDefaultAndReturnsTrue() {
        when(settingDao.findById(SettingAccessService.KEY_NOTIFY_URL)).thenReturn(Optional.empty());

        boolean created = service.ensureValue(SettingAccessService.KEY_NOTIFY_URL, "");

        assertTrue(created);
        verify(settingDao).save(argThat(setting ->
                SettingAccessService.KEY_NOTIFY_URL.equals(setting.getVkey()) && "".equals(setting.getVvalue())));
    }

    @Test
    public void ensureValueDoesNotOverwriteExistingSetting() {
        when(settingDao.findById(SettingAccessService.KEY_COMMUNICATION_KEY))
                .thenReturn(Optional.of(setting(SettingAccessService.KEY_COMMUNICATION_KEY, "existing-key")));

        boolean created = service.ensureValue(SettingAccessService.KEY_COMMUNICATION_KEY, "new-key");

        assertFalse(created);
        verify(settingDao, never()).save(org.mockito.ArgumentMatchers.any(Setting.class));
    }

    @Test(expected = IllegalStateException.class)
    public void requireValueFailsFastForMissingRequiredSetting() {
        when(settingDao.findById(SettingAccessService.KEY_COMMUNICATION_KEY)).thenReturn(Optional.empty());

        service.communicationKey();
    }

    private Setting setting(String key, String value) {
        Setting setting = new Setting();
        setting.setVkey(key);
        setting.setVvalue(value);
        return setting;
    }
}
