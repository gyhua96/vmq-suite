package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.entity.PayOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PaymentOrderCreationService {
    @Autowired
    private PayOrderDao payOrderDao;
    @Autowired
    private PriceLockService priceLockService;
    @Transactional
    public OrderCreationResult createAfterPriceLocked(String payId, String orderId, String param, int type,
                                                      BigDecimal price, BigDecimal reallyPrice, String notifyUrl,
                                                      String returnUrl, int isAuto, String payUrl, long createDate) {
        PayOrder existingOrder = payOrderDao.findByPayId(payId);
        if (existingOrder != null) {
            if (priceLockService != null) {
                priceLockService.release(type, reallyPrice);
            }
            return OrderCreationResult.failure("商户订单号已存在！");
        }

            PayOrder payOrder = new PayOrder();
            payOrder.setPayId(payId);
            payOrder.setOrderId(orderId);
            payOrder.setCreateDate(createDate);
            payOrder.setPayDate(0);
            payOrder.setCloseDate(0);
            payOrder.setParam(param);
            payOrder.setType(type);
            payOrder.setPrice(price);
            payOrder.setReallyPrice(reallyPrice);
            payOrder.setNotifyUrl(notifyUrl);
            payOrder.setReturnUrl(returnUrl);
            payOrder.setState(PaymentState.WAITING);
            payOrder.setIsAuto(isAuto);
            payOrder.setPayUrl(payUrl);
        try {
            return OrderCreationResult.success(payOrderDao.save(payOrder));
        } catch (DataIntegrityViolationException e) {
            if (priceLockService != null) {
                priceLockService.release(type, reallyPrice);
            }
            return OrderCreationResult.failure("order already exists");
        }
    }

    /** @deprecated use the BigDecimal overload. */
    @Deprecated
    public OrderCreationResult createAfterPriceLocked(String payId, String orderId, String param, int type,
                                                      double price, double reallyPrice, String notifyUrl,
                                                      String returnUrl, int isAuto, String payUrl, long createDate) {
        return createAfterPriceLocked(payId, orderId, param, type, BigDecimal.valueOf(price),
                BigDecimal.valueOf(reallyPrice), notifyUrl, returnUrl, isAuto, payUrl, createDate);
    }

    @Transactional
    public PayOrder createUnmatchedTransfer(int type, BigDecimal price, long eventTime) {
        String unmatchedId = "无订单转账-" + type + "-" + eventTime;
        PayOrder payOrder = new PayOrder();
        payOrder.setPayId(unmatchedId);
        payOrder.setOrderId(unmatchedId);
        payOrder.setCreateDate(eventTime);
        payOrder.setPayDate(eventTime);
        payOrder.setCloseDate(eventTime);
        payOrder.setParam("无订单转账");
        payOrder.setType(type);
        payOrder.setPrice(price);
        payOrder.setReallyPrice(price);
        payOrder.setState(PaymentState.PAID);
        payOrder.setPayUrl("无订单转账");
        return payOrderDao.save(payOrder);
    }

    /** @deprecated use the BigDecimal overload. */
    @Deprecated
    public PayOrder createUnmatchedTransfer(int type, double price, long eventTime) {
        return createUnmatchedTransfer(type, BigDecimal.valueOf(price), eventTime);
    }
}
