package com.vone.mq.service;

import com.vone.mq.domain.PaymentState;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.CreateOrderRes;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class WebService {
    @Value("${vmq.app-heart.time-window-ms:60000}")
    private long appHeartTimeWindowMillis = 60 * 1000L;

    @Autowired
    private SignatureService signatureService;
    @Autowired
    private SettingAccessService settingAccessService;
    @Autowired
    private AppEventService appEventService;
    @Autowired
    private OrderApplicationService orderApplicationService;
    @Autowired
    private OrderQueryService orderQueryService;
    @Autowired
    private OrderAccessTokenService orderAccessTokenService;

    public CommonRes createOrder(String payId, String param, Integer type, String price, String notifyUrl, String returnUrl, String sign){
        return createOrder(payId, param, type, price, notifyUrl, returnUrl, sign, null, null, null);
    }

    public CommonRes createOrder(String payId, String param, Integer type, String price, String notifyUrl, String returnUrl,
                                 String sign, String signType, String timestamp, String nonce){
        String key = settingAccessService.communicationKey();
        Map<String, String> signParams = new LinkedHashMap<>();
        signParams.put("payId", payId);
        signParams.put("param", param == null ? "" : param);
        signParams.put("type", String.valueOf(type));
        signParams.put("price", price);
        signParams.put("timestamp", timestamp);
        signParams.put("nonce", nonce);
        signParams.put("signType", signType);
        if (!signatureService.verifyMerchant(signParams, key, sign, payId + param + type + price + key)){
            return ResUtil.error("签名校验不通过");
        }
        if (StringUtils.hasText(notifyUrl) && !UrlSecurityUtil.isSafePublicCallbackUrl(notifyUrl)) {
            return ResUtil.error("异步通知地址不安全或不允许");
        }
        if (StringUtils.hasText(returnUrl) && !UrlSecurityUtil.isSafePublicCallbackUrl(returnUrl)) {
            return ResUtil.error("同步跳转地址不安全或不允许");
        }

        OrderApplicationResult orderResult = orderApplicationService.createOrder(payId, param, type, price, notifyUrl, returnUrl);
        if (!orderResult.isSuccess()) {
            return ResUtil.error(orderResult.getErrorMessage());
        }
        PayOrder payOrder = orderResult.getPayOrder();
        CreateOrderRes createOrderRes = new CreateOrderRes(
                orderResult.getPayId(),
                orderResult.getOrderId(),
                orderResult.getType(),
                orderResult.getPrice(),
                orderResult.getReallyPrice(),
                orderResult.getPayUrl(),
                orderResult.getIsAuto(),
                PaymentState.WAITING,
                orderResult.getTimeOut(),
                payOrder.getCreateDate());
        long accessExpiresAt = payOrder.getCreateDate() + orderResult.getTimeOut() * 60L * 1000L;
        createOrderRes.setAccessToken(orderAccessTokenService.issue(orderResult.getOrderId(), accessExpiresAt, key),
                accessExpiresAt);

        return ResUtil.success(createOrderRes);
    }
    public CommonRes closeOrder(String orderId,String sign){
        return closeOrder(orderId, sign, null, null, null);
    }

    public CommonRes closeOrder(String orderId,String sign, String signType, String timestamp, String nonce){

        String key = settingAccessService.communicationKey();
        Map<String, String> signParams = new LinkedHashMap<>();
        signParams.put("orderId", orderId);
        signParams.put("timestamp", timestamp);
        signParams.put("nonce", nonce);
        signParams.put("signType", signType);
        if (!signatureService.verifyMerchant(signParams, key, sign, orderId + key)){
            return ResUtil.error("签名校验不通过");
        }

        return orderQueryService.closeWaitingOrder(orderId);
    }

    public CommonRes appHeart(String t,String sign){
        return appHeart(t, sign, null, null);
    }

    public CommonRes appHeart(String t,String sign, String signType, String nonce){
        String key = settingAccessService.communicationKey();
        if (!isAppTimestampValid(t)) {
            return ResUtil.error("客户端时间错误");
        }
        Map<String, String> signParams = new LinkedHashMap<>();
        signParams.put("t", t);
        signParams.put("nonce", nonce);
        signParams.put("signType", signType);
        if (!signatureService.verifyApp(signParams, key, sign, t + key)){
            return ResUtil.error("签名校验错误");
        }

        return appEventService.recordHeartbeat(t);
    }

    public CommonRes appPush(Integer type,String price,String t,String sign){
        return appPush(type, price, t, sign, null, null);
    }

    public CommonRes appPush(Integer type,String price,String t,String sign, String signType, String nonce){
        String key = settingAccessService.communicationKey();
        if (!isAppPushTimestampValid(t)) {
            return ResUtil.error("客户端时间错误");
        }
        Map<String, String> signParams = new LinkedHashMap<>();
        signParams.put("type", String.valueOf(type));
        signParams.put("price", price);
        signParams.put("t", t);
        signParams.put("nonce", nonce);
        signParams.put("signType", signType);
        if (!signatureService.verifyApp(signParams, key, sign, type + "" + price + t + key)){
            return ResUtil.error("签名校验错误");
        }

        return appEventService.handlePaymentPush(type, price, t, key);
    }

    private boolean isAppTimestampValid(String t) {
        try {
            long delta = Math.abs(Long.parseLong(t) - System.currentTimeMillis());
            return delta <= appHeartTimeWindowMillis;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAppPushTimestampValid(String t) {
        try {
            long delta = Math.abs(Long.parseLong(t) - System.currentTimeMillis());
            return delta <= 600 * 1000L; // 对订单推送放宽到10分钟，以防丢包严重时，多次重发在网络链路上积压超过50秒被服务器拒收
        } catch (Exception e) {
            return false;
        }
    }

    public CommonRes getOrder(String orderId){
        return getOrder(orderId, null, null, null, null, null);
    }

    public CommonRes getOrder(String orderId, String sign, String signType, String timestamp, String nonce) {
        return getOrder(orderId, sign, signType, timestamp, nonce, null);
    }

    public CommonRes getOrder(String orderId, String sign, String signType, String timestamp, String nonce,
                              String accessToken){
        String key = settingAccessService.communicationKey();
        if (sign == null && !orderAccessTokenService.verify(orderId, accessToken, key)) {
            return ResUtil.error("请提供有效的订单访问凭证");
        }
        if (sign != null && !signatureService.verifyOrderQuery(orderId, sign, signType, timestamp, nonce, key)) {
            return ResUtil.error("签名校验不通过");
        }
        return orderQueryService.getOrder(orderId);
    }



    public CommonRes checkOrder(String orderId){
        return checkOrder(orderId, null, null, null, null, null);
    }

    public CommonRes checkOrder(String orderId, String sign, String signType, String timestamp, String nonce) {
        return checkOrder(orderId, sign, signType, timestamp, nonce, null);
    }

    public CommonRes checkOrder(String orderId, String sign, String signType, String timestamp, String nonce,
                                String accessToken){
        String key = settingAccessService.communicationKey();
        if (sign == null && !orderAccessTokenService.verify(orderId, accessToken, key)) {
            return ResUtil.error("请提供有效的订单访问凭证");
        }
        if (sign != null && !signatureService.verifyOrderQuery(orderId, sign, signType, timestamp, nonce, key)) {
            return ResUtil.error("签名校验不通过");
        }
        return orderQueryService.checkOrder(orderId, key);
    }

    public CommonRes getState(String t,String sign){

        String key = settingAccessService.communicationKey();
        if (!signatureService.verifyLegacyStateQuery(t, sign, key)){
            return ResUtil.error("签名校验不通过");
        }

        return orderQueryService.getMonitorState();
    }

}
