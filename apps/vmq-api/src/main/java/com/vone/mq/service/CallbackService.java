package com.vone.mq.service;

import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.Setting;
import com.vone.mq.utils.UrlSecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CallbackService {
    @Autowired
    private CallbackPayloadBuilder callbackPayloadBuilder;
    @Autowired
    private CallbackHttpClient callbackHttpClient;

    public CallbackResult sendNotify(PayOrder payOrder, Optional<Setting> defaultNotifyUrl, String key) {
        String url = resolveNotifyUrl(payOrder, defaultNotifyUrl);
        if (url == null || url.equals("")) {
            return CallbackResult.failure(null, "您还未配置异步通知地址，请现在系统配置中配置");
        }
        if (!UrlSecurityUtil.isSafePublicCallbackUrl(url)) {
            return CallbackResult.failure(null, "异步通知地址不安全或不允许");
        }
        String query = callbackPayloadBuilder.buildQuery(payOrder, key);
        String response = callbackHttpClient.sendGet(url, query);
        if (CallbackResponseMatcher.isSuccess(response)) {
            return CallbackResult.success(response);
        }
        return CallbackResult.failure(response, "通知异步地址失败");
    }

    private String resolveNotifyUrl(PayOrder payOrder, Optional<Setting> defaultNotifyUrl) {
        if (payOrder.getNotifyUrl() != null && !payOrder.getNotifyUrl().equals("")) {
            return payOrder.getNotifyUrl();
        }
        return defaultNotifyUrl.map(Setting::getVvalue).orElse("");
    }
}
