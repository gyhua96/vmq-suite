package com.vone.mq.service;

import com.google.gson.Gson;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.CreateOrderRes;
import com.vone.mq.utils.MoneyUtil;
import com.vone.mq.utils.ResUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CreateOrderEndpointService {
    private static final Gson GSON = new Gson();

    @Autowired
    private WebService webService;

    public String handle(String payId, String param, Integer type, String price, String notifyUrl, String returnUrl,
                         String sign, String signType, String timestamp, String nonce, Integer isHtml) {
        CommonRes validationError = validate(payId, type, price, sign);
        if (validationError != null) {
            return json(validationError);
        }
        String safeParam = param == null ? "" : param;
        int renderMode = isHtml == null ? 0 : isHtml;
        CommonRes commonRes = webService.createOrder(payId, safeParam, type, price, notifyUrl, returnUrl,
                sign, signType, timestamp, nonce);
        return render(commonRes, renderMode);
    }

    private CommonRes validate(String payId, Integer type, String price, String sign) {
        if (payId == null || payId.equals("")) {
            return ResUtil.error("请传入商户订单号");
        }
        if (type == null) {
            return ResUtil.error("请传入支付方式=>1|微信 2|支付宝");
        }
        if (type != 1 && type != 2) {
            return ResUtil.error("支付方式错误=>1|微信 2|支付宝");
        }

        try {
            MoneyUtil.parsePositive(price);
        } catch (IllegalArgumentException e) {
            return ResUtil.error(e.getMessage());
        }

        if (sign == null || sign.equals("")) {
            return ResUtil.error("请传入签名");
        }
        return null;
    }

    private String render(CommonRes commonRes, int renderMode) {
        if (renderMode == 0) {
            return json(commonRes);
        }
        CreateOrderRes createOrderRes = (CreateOrderRes) commonRes.getData();
        if (createOrderRes == null) {
            return commonRes.getMsg();
        }
        String accessToken = createOrderRes.getAccessToken();
        String target = "/payPage/pay.html?orderId=" + createOrderRes.getOrderId();
        if (accessToken != null && !accessToken.isBlank()) {
            target += "&accessToken=" + accessToken;
        }
        return "<script>window.location.href = '" + target + "'</script>";
    }

    private String json(CommonRes commonRes) {
        return GSON.toJson(commonRes);
    }
}
