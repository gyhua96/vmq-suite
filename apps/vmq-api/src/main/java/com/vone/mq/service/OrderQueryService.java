package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.CreateOrderRes;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.utils.ResUtil;
import com.vone.mq.utils.UrlSecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OrderQueryService {
    @Autowired
    private PayOrderDao payOrderDao;
    @Autowired
    private PaymentOrderStateService paymentOrderStateService;
    @Autowired
    private CallbackPayloadBuilder callbackPayloadBuilder;
    @Autowired
    private SettingAccessService settingAccessService;

    public CommonRes closeWaitingOrder(String orderId) {
        PayOrder payOrder = payOrderDao.findByOrderId(orderId);
        if (payOrder == null) {
            return ResUtil.error("云端订单编号不存在");
        }
        if (payOrder.getState() != PaymentState.WAITING) {
            return ResUtil.error("订单状态不允许关闭");
        }
        boolean closed = paymentOrderStateService.closeWaitingOrder(payOrder, System.currentTimeMillis());
        if (!closed) {
            return ResUtil.error("订单状态不允许关闭");
        }
        return ResUtil.success();
    }

    public CommonRes getOrder(String orderId) {
        PayOrder payOrder = payOrderDao.findByOrderId(orderId);
        if (payOrder == null) {
            return ResUtil.error("云端订单编号不存在");
        }
        return ResUtil.success(toCreateOrderRes(payOrder));
    }

    public CommonRes checkOrder(String orderId, String key) {
        PayOrder payOrder = payOrderDao.findByOrderId(orderId);
        if (payOrder == null) {
            return ResUtil.error("云端订单编号不存在");
        }
        if (payOrder.getState() == PaymentState.WAITING) {
            return ResUtil.error("订单未支付");
        }
        if (payOrder.getState() == PaymentState.CLOSED) {
            return ResUtil.error("订单已过期");
        }
        String query = callbackPayloadBuilder.buildQuery(payOrder, key);
        String url = payOrder.getReturnUrl();
        if (url == null || url.equals("")) {
            url = settingAccessService.defaultReturnUrl();
        }
        if (!UrlSecurityUtil.isSafePublicCallbackUrl(url)) {
            return ResUtil.error("同步跳转地址不安全或不允许");
        }
        return ResUtil.success(url + "?" + query);
    }

    public CommonRes getMonitorState() {
        Map<String, String> map = new HashMap<>();
        map.put("state", settingAccessService.getValue(SettingAccessService.KEY_MONITOR_STATE, "-1"));
        map.put("lastheart", settingAccessService.getValue(SettingAccessService.KEY_LAST_HEART, "0"));
        map.put("lastpay", settingAccessService.getValue(SettingAccessService.KEY_LAST_PAY, "0"));
        return ResUtil.success(map);
    }

    private CreateOrderRes toCreateOrderRes(PayOrder payOrder) {
        return new CreateOrderRes(
                payOrder.getPayId(),
                payOrder.getOrderId(),
                payOrder.getType(),
                payOrder.getPrice(),
                payOrder.getReallyPrice(),
                payOrder.getPayUrl(),
                payOrder.getIsAuto(),
                payOrder.getState(),
                settingAccessService.closeMinutes(),
                payOrder.getCreateDate());
    }
}
