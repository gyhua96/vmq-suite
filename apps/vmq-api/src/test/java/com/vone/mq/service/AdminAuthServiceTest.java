package com.vone.mq.service;

import com.vone.mq.dao.SettingDao;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.entity.Setting;
import com.vone.mq.utils.PasswordUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminAuthServiceTest {
    private AdminAuthService adminAuthService;
    private SettingDao settingDao;

    @Before
    public void setUp() {
        adminAuthService = new AdminAuthService();
        settingDao = mock(SettingDao.class);
        SettingAccessService settingAccessService = new SettingAccessService();
        ReflectionTestUtils.setField(settingAccessService, "settingDao", settingDao);
        ReflectionTestUtils.setField(adminAuthService, "settingDao", settingDao);
        ReflectionTestUtils.setField(adminAuthService, "settingAccessService", settingAccessService);
    }

    @Test
    public void loginReturnsBusinessErrorWhenAdminSettingsAreMissing() {
        when(settingDao.findById(SettingAccessService.KEY_ADMIN_USER)).thenReturn(Optional.empty());
        when(settingDao.findById(SettingAccessService.KEY_ADMIN_PASS)).thenReturn(Optional.empty());

        CommonRes result = adminAuthService.login("admin", "password");

        assertEquals(-1, result.getCode());
        assertEquals("系统配置缺失，请先初始化后台账号密码", result.getMsg());
    }

    @Test
    public void loginRejectsWrongUser() {
        givenAdminSettings("admin", PasswordUtil.hash("strong-pass"));

        CommonRes result = adminAuthService.login("other", "strong-pass");

        assertEquals(-1, result.getCode());
        assertEquals("账号或密码不正确", result.getMsg());
        verify(settingDao, never()).save(org.mockito.ArgumentMatchers.any(Setting.class));
    }

    @Test
    public void loginRejectsDefaultPlainPassword() {
        givenAdminSettings("admin", "admin");

        CommonRes result = adminAuthService.login("admin", "admin");

        assertEquals(-1, result.getCode());
        assertEquals("检测到不安全的默认后台密码，请先通过 VMQ_ADMIN_PASSWORD 或数据库更新为强密码", result.getMsg());
        verify(settingDao, never()).save(org.mockito.ArgumentMatchers.any(Setting.class));
    }

    @Test
    public void loginRejectsWrongPassword() {
        givenAdminSettings("admin", PasswordUtil.hash("strong-pass"));

        CommonRes result = adminAuthService.login("admin", "wrong-pass");

        assertEquals(-1, result.getCode());
        assertEquals("账号或密码不正确", result.getMsg());
        verify(settingDao, never()).save(org.mockito.ArgumentMatchers.any(Setting.class));
    }

    @Test
    public void loginMigratesLegacyPlainPasswordAfterSuccess() {
        givenAdminSettings("admin", "legacy-pass");

        CommonRes result = adminAuthService.login("admin", "legacy-pass");

        assertEquals(1, result.getCode());
        verify(settingDao).save(argThat(setting ->
                "pass".equals(setting.getVkey()) && PasswordUtil.matches("legacy-pass", setting.getVvalue())));
    }

    @Test
    public void loginDoesNotRewriteAlreadyHashedPassword() {
        givenAdminSettings("admin", PasswordUtil.hash("strong-pass"));

        CommonRes result = adminAuthService.login("admin", "strong-pass");

        assertEquals(1, result.getCode());
        verify(settingDao, never()).save(org.mockito.ArgumentMatchers.any(Setting.class));
    }

    private void givenAdminSettings(String user, String pass) {
        when(settingDao.findById(SettingAccessService.KEY_ADMIN_USER)).thenReturn(Optional.of(setting("user", user)));
        when(settingDao.findById(SettingAccessService.KEY_ADMIN_PASS)).thenReturn(Optional.of(setting("pass", pass)));
    }

    private Setting setting(String key, String value) {
        Setting setting = new Setting();
        setting.setVkey(key);
        setting.setVvalue(value);
        return setting;
    }
}
