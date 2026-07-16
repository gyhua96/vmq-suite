package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.PaymentEvent;
import com.vone.mq.utils.MoneyUtil;
import com.vone.mq.utils.ResUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AppEventService {
    @Autowired
    private PayOrderDao payOrderDao;
    @Autowired
    private SettingAccessService settingAccessService;
    @Autowired
    private PaymentEventService paymentEventService;
    @Autowired
    private PaymentOrderCreationService paymentOrderCreationService;
    @Autowired
    private PaymentOrderStateService paymentOrderStateService;
    @Autowired
    private CallbackTaskService callbackTaskService;

    public CommonRes recordHeartbeat(String t) {
        settingAccessService.saveValue(
                SettingAccessService.KEY_LAST_HEART,
                String.valueOf(System.currentTimeMillis())
        );
        settingAccessService.saveValue(SettingAccessService.KEY_MONITOR_STATE, "1");
        return ResUtil.success();
    }

    public CommonRes handlePaymentPush(Integer type, String price, String t, String key) {
        Long eventTime = parseEventTime(t);
        if (eventTime == null) {
            return ResUtil.error("客户端时间错误");
        }
        BigDecimal pushedPrice;
        try {
            pushedPrice = MoneyUtil.parsePositive(price);
        } catch (IllegalArgumentException e) {
            return ResUtil.error(e.getMessage());
        }
        settingAccessService.saveValue(SettingAccessService.KEY_LAST_PAY, t);
        PaymentEvent event = paymentEventService.accept(type, pushedPrice, eventTime);
        // Keep the legacy service seam usable for integrations compiled against the
        // pre-persistent-event API. The real implementation always returns an event.
        if (event == null) {
            boolean created = paymentEventService.recordIfNew(type, pushedPrice, eventTime);
            if (!created) {
                return ResUtil.error("閲嶅鎺ㄩ€?");
            }
            event = new PaymentEvent();
            event.setType(type);
            event.setPrice(pushedPrice);
            event.setEventTime(eventTime);
            event.setState(PaymentEvent.RECEIVED);
        }
        if (event.getState() == PaymentEvent.PROCESSED) {
            return ResUtil.success();
        }

        PayOrder payOrder = findRecoverableOrder(event, type, pushedPrice, eventTime);
        if (payOrder == null) {
            PayOrder unmatched = paymentOrderCreationService.createUnmatchedTransfer(type, pushedPrice, eventTime);
            if (unmatched == null) {
                unmatched = paymentOrderCreationService.createUnmatchedTransfer(type, Double.parseDouble(price), eventTime);
            }
            paymentEventService.markProcessed(event, unmatched.getId());
            return ResUtil.success();
        }

        if (payOrder.getState() == PaymentState.WAITING) {
            boolean markedPaid = paymentOrderStateService.markPaidFromAppPush(payOrder, price, eventTime);
            if (!markedPaid) {
                payOrder = payOrderDao.findByPayDate(eventTime);
                if (payOrder == null) {
                    return ResUtil.error("订单已被处理");
                }
            }
        }

        if (settingAccessService.callbackAsyncEnabled()) {
            callbackTaskService.enqueue(payOrder, settingAccessService.defaultNotifyUrl(), key);
            paymentEventService.markProcessed(event, payOrder.getId());
            return ResUtil.success();
        }

        CallbackResult callbackResult = callbackTaskService.sendNowAndRecord(payOrder, settingAccessService.defaultNotifyUrl(), key);
        paymentEventService.markProcessed(event, payOrder.getId());
        if (callbackResult.isSuccess()) {
            return ResUtil.success();
        }
        paymentOrderStateService.markCallbackFailed(payOrder);
        return ResUtil.error(callbackResult.getErrorMessage());
    }

    private PayOrder findRecoverableOrder(PaymentEvent event, int type, BigDecimal price, long eventTime) {
        if (event.getMatchedOrderId() != null) {
            PayOrder matched = payOrderDao.findById(event.getMatchedOrderId()).orElse(null);
            if (matched != null) {
                return matched;
            }
        }
        PayOrder waiting = payOrderDao.findByReallyPriceAndStateAndType(price, PaymentState.WAITING, type);
        if (waiting != null) {
            return waiting;
        }
        return payOrderDao.findByPayDate(eventTime);
    }

    private Long parseEventTime(String t) {
        if (t == null) {
            return null;
        }
        try {
            return Long.valueOf(t.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
