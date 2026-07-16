package com.vone.mq.service;

import com.vone.mq.entity.PayOrder;
import com.vone.mq.utils.CallbackParamUtil;
import com.vone.mq.utils.SigningUtil;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CallbackPayloadBuilder {
    public String buildQuery(PayOrder payOrder, String key) {
        Map<String, String> params = buildSignedParams(payOrder, key);
        return CallbackParamUtil.toQuery(params);
    }

    Map<String, String> buildSignedParams(PayOrder payOrder, String key) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("payId", payOrder.getPayId());
        params.put("param", payOrder.getParam());
        params.put("type", String.valueOf(payOrder.getType()));
        params.put("price", String.valueOf(payOrder.getPrice()));
        params.put("reallyPrice", String.valueOf(payOrder.getReallyPrice()));
        params.put("signType", "HMAC_SHA256");
        params.put("eventId", "payment-" + payOrder.getOrderId());
        params.put("timestamp", String.valueOf(payOrder.getPayDate()));
        params.put("nonce", UUID.randomUUID().toString().replace("-", ""));
        String canonical = SigningUtil.canonicalize(params);
        params.put("sign", SigningUtil.hmacSha256Hex(key, canonical));
        return params;
    }
}
