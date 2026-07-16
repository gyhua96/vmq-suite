package com.vone.mq.service;

import com.vone.mq.dao.SettingDao;
import com.vone.mq.entity.Setting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class SettingAccessService {
    public static final String KEY_ADMIN_USER = "user";
    public static final String KEY_ADMIN_PASS = "pass";
    public static final String KEY_NOTIFY_URL = "notifyUrl";
    public static final String KEY_RETURN_URL = "returnUrl";
    public static final String KEY_COMMUNICATION_KEY = "key";
    public static final String KEY_LAST_HEART = "lastheart";
    public static final String KEY_LAST_PAY = "lastpay";
    public static final String KEY_MONITOR_STATE = "jkstate";
    public static final String KEY_CLOSE_MINUTES = "close";
    public static final String KEY_PAY_QF = "payQf";
    public static final String KEY_WX_PAY = "wxpay";
    public static final String KEY_ZFB_PAY = "zfbpay";
    public static final String KEY_CALLBACK_ASYNC = "callbackAsync";

    @Autowired
    private SettingDao settingDao;

    public Optional<Setting> find(String key) {
        return settingDao.findById(key);
    }

    public Optional<String> findValue(String key) {
        return find(key).map(Setting::getVvalue);
    }

    public String getValue(String key, String defaultValue) {
        String value = findValue(key).orElse(null);
        return value == null ? defaultValue : value;
    }

    public String requireValue(String key) {
        String value = findValue(key).orElse(null);
        if (value == null) {
            throw new IllegalStateException("缺少系统配置: " + key);
        }
        return value;
    }

    public String communicationKey() {
        return requireValue(KEY_COMMUNICATION_KEY);
    }

    public int closeMinutes() {
        return positiveInt(KEY_CLOSE_MINUTES, 5);
    }

    public int payQf() {
        int value = positiveInt(KEY_PAY_QF, 1);
        return value == 2 ? 2 : 1;
    }

    public boolean callbackAsyncEnabled() {
        return "1".equals(getValue(KEY_CALLBACK_ASYNC, "0"));
    }

    public Optional<Setting> defaultNotifyUrl() {
        return find(KEY_NOTIFY_URL);
    }

    public String defaultReturnUrl() {
        return getValue(KEY_RETURN_URL, "");
    }

    public String payUrlForType(int type) {
        if (type == 1) {
            return getValue(KEY_WX_PAY, "");
        }
        if (type == 2) {
            return getValue(KEY_ZFB_PAY, "");
        }
        return "";
    }

    @Transactional
    public void saveValue(String key, String value) {
        Setting setting = new Setting();
        setting.setVkey(key);
        setting.setVvalue(value == null ? "" : value);
        settingDao.save(setting);
    }

    @Transactional
    public String getOrCreateValue(String key, String defaultValue) {
        Optional<Setting> setting = settingDao.findById(key);
        if (setting.isPresent()) {
            return setting.get().getVvalue();
        }
        saveValue(key, defaultValue);
        return defaultValue;
    }

    @Transactional
    public boolean ensureValue(String key, String defaultValue) {
        Optional<Setting> setting = settingDao.findById(key);
        if (setting.isPresent()) {
            return false;
        }
        saveValue(key, defaultValue);
        return true;
    }

    private int positiveInt(String key, int defaultValue) {
        String value = getValue(key, String.valueOf(defaultValue));
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
