package com.vone.mq.service;

import com.vone.mq.entity.PayOrder;
import com.vone.mq.utils.SigningUtil;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CallbackPayloadBuilderTest {
    private final CallbackPayloadBuilder builder = new CallbackPayloadBuilder();

    @Test
    public void buildsSignedHmacCallbackParamsBeforeUrlEncoding() {
        PayOrder order = new PayOrder();
        order.setPayId("A&1");
        order.setParam("x=y z");
        order.setType(2);
        order.setPrice(49.95);
        order.setReallyPrice(49.96);

        Map<String, String> params = builder.buildSignedParams(order, "secret");
        assertEquals("HMAC_SHA256", params.get("signType"));
        String sign = params.remove("sign");
        assertEquals(SigningUtil.hmacSha256Hex("secret", SigningUtil.canonicalize(params)), sign);

        String query = builder.buildQuery(order, "secret");
        assertTrue(query.contains("payId=A%261"));
        assertTrue(query.contains("param=x%3Dy+z"));
        assertTrue(query.contains("signType=HMAC_SHA256"));
    }
}
