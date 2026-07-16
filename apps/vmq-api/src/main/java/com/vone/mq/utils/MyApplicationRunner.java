package com.vone.mq.utils;

import com.vone.mq.service.SettingAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MyApplicationRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyApplicationRunner.class);

    @Autowired
    private SettingAccessService settingAccessService;

    @Override
    public void run(ApplicationArguments var1) {
        LOGGER.info("Starting system setting initialization");

        // 逐项补齐基础设置。
        // PostgreSQL / Docker 首次启动、历史数据迁移或中途初始化失败时，setting 表可能不是空表，
        // 但部分 key 仍缺失；如果只根据 count()==0 初始化，定时任务会因 Optional.get() 空值而报错。
        String adminUser = getenvOrDefault("VMQ_ADMIN_USER", "admin");
        String adminPassword = secretFromEnvOrProperty("VMQ_ADMIN_PASSWORD", "vmq.admin.password");
        if (adminPassword == null || adminPassword.trim().isEmpty()) {
            throw new IllegalStateException("VMQ_ADMIN_PASSWORD 未配置，拒绝使用默认后台密码启动");
        }
        ensureSetting(SettingAccessService.KEY_ADMIN_USER, adminUser);
        ensureSetting(SettingAccessService.KEY_ADMIN_PASS, PasswordUtil.hashIfNecessary(adminPassword));

        ensureSetting(SettingAccessService.KEY_NOTIFY_URL, "");
        ensureSetting(SettingAccessService.KEY_RETURN_URL, "");
        ensureSetting(SettingAccessService.KEY_COMMUNICATION_KEY,
                java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", ""));
        ensureSetting(SettingAccessService.KEY_LAST_HEART, "0");
        ensureSetting(SettingAccessService.KEY_LAST_PAY, "0");
        ensureSetting(SettingAccessService.KEY_MONITOR_STATE, "-1");
        ensureSetting(SettingAccessService.KEY_CLOSE_MINUTES, "5");
        ensureSetting(SettingAccessService.KEY_PAY_QF, "1");
        ensureSetting(SettingAccessService.KEY_WX_PAY, "");
        ensureSetting(SettingAccessService.KEY_ZFB_PAY, "");
        ensureSetting(SettingAccessService.KEY_CALLBACK_ASYNC, "0");

        LOGGER.info("System setting initialization completed");

    }

    private void ensureSetting(String key, String defaultValue) {
        if (settingAccessService.ensureValue(key, defaultValue)) {
            LOGGER.info("Initializing missing setting key: {}", key);
        }
    }

    private String getenvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private String secretFromEnvOrProperty(String envKey, String propertyKey) {
        String value = System.getenv(envKey);
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(propertyKey);
        }
        return value;
    }

}
