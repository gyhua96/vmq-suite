package com.vone.mq.service;

import com.vone.mq.dao.SettingDao;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.entity.Setting;
import com.vone.mq.utils.PasswordUtil;
import com.vone.mq.utils.ResUtil;
import com.vone.mq.utils.UrlSecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminSettingService {
    static final String MASKED_PASSWORD = "********";
    static final String MASKED_SECRET = "********";

    @Autowired
    private SettingDao settingDao;
    @Autowired
    private SettingAccessService settingAccessService;

    @Transactional
    public CommonRes saveSetting(String user, String pass, String notifyUrl, String returnUrl, String key,
                                 String wxpay, String zfbpay, String close, String payQf, String callbackAsync) {
        CommonRes validation = validate(user, notifyUrl, returnUrl, close, payQf, callbackAsync);
        if (validation != null) {
            return validation;
        }

        saveSettingValue(SettingAccessService.KEY_ADMIN_USER, user);
        if (shouldUpdatePassword(pass)) {
            saveSettingValue(SettingAccessService.KEY_ADMIN_PASS, PasswordUtil.hash(pass));
        }
        saveSettingValue(SettingAccessService.KEY_NOTIFY_URL, normalize(notifyUrl));
        saveSettingValue(SettingAccessService.KEY_RETURN_URL, normalize(returnUrl));
        if (StringUtils.hasText(key) && !key.contains("****")) {
            saveSettingValue(SettingAccessService.KEY_COMMUNICATION_KEY, key.trim());
        }
        saveSettingValue(SettingAccessService.KEY_WX_PAY, wxpay);
        saveSettingValue(SettingAccessService.KEY_ZFB_PAY, zfbpay);
        saveSettingValue(SettingAccessService.KEY_PAY_QF, payQf.trim());
        saveSettingValue(SettingAccessService.KEY_CLOSE_MINUTES, close.trim());
        saveSettingValue(SettingAccessService.KEY_CALLBACK_ASYNC, normalizeCallbackAsync(callbackAsync));
        return ResUtil.success();
    }

    public CommonRes getSettings() {
        List<Setting> settings = settingDao.findAll();
        Map<String, String> map = new HashMap<>();
        for (Setting s : settings) {
            if (SettingAccessService.KEY_ADMIN_PASS.equals(s.getVkey())) {
                map.put(s.getVkey(), MASKED_PASSWORD);
            } else if (SettingAccessService.KEY_COMMUNICATION_KEY.equals(s.getVkey())) {
                map.put(s.getVkey(), MASKED_SECRET);
            } else {
                map.put(s.getVkey(), s.getVvalue());
            }
        }
        return ResUtil.success(map);
    }

    private CommonRes validate(String user, String notifyUrl, String returnUrl, String close, String payQf, String callbackAsync) {
        if (!StringUtils.hasText(user)) {
            return ResUtil.error("后台账号不能为空");
        }
        if (!isBlankOrSafeUrl(notifyUrl)) {
            return ResUtil.error("异步通知地址不安全");
        }
        if (!isBlankOrSafeUrl(returnUrl)) {
            return ResUtil.error("同步跳转地址不安全");
        }
        if (!isPositiveInteger(close)) {
            return ResUtil.error("订单有效期必须为正整数");
        }
        String normalizedPayQf = normalize(payQf);
        if (!"1".equals(normalizedPayQf) && !"2".equals(normalizedPayQf)) {
            return ResUtil.error("金额区分方向错误=>1|递增 2|递减");
        }
        String normalizedCallbackAsync = normalizeCallbackAsync(callbackAsync);
        if (!"0".equals(normalizedCallbackAsync) && !"1".equals(normalizedCallbackAsync)) {
            return ResUtil.error("回调模式错误=>0|同步 1|异步");
        }
        return null;
    }

    private boolean shouldUpdatePassword(String pass) {
        return StringUtils.hasText(pass) && !pass.contains("****");
    }

    private boolean isBlankOrSafeUrl(String url) {
        return !StringUtils.hasText(url) || UrlSecurityUtil.isSafePublicCallbackUrl(url);
    }

    private boolean isPositiveInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            return Integer.parseInt(value.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeCallbackAsync(String callbackAsync) {
        return StringUtils.hasText(callbackAsync) ? callbackAsync.trim() : "0";
    }

    private void saveSettingValue(String key, String value) {
        settingAccessService.saveValue(key, value);
    }
}
