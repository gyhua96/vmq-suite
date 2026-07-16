package com.vone.mq.service;

import com.vone.mq.entity.PayOrder;

import java.math.BigDecimal;

public class OrderApplicationResult {
    private final boolean success;
    private final PayOrder payOrder;
    private final String payId;
    private final String orderId;
    private final int type;
    private final BigDecimal price;
    private final BigDecimal reallyPrice;
    private final String payUrl;
    private final int isAuto;
    private final int timeOut;
    private final String errorMessage;

    private OrderApplicationResult(boolean success, PayOrder payOrder, String payId, String orderId, int type,
                                   BigDecimal price, BigDecimal reallyPrice, String payUrl, int isAuto,
                                   int timeOut, String errorMessage) {
        this.success = success;
        this.payOrder = payOrder;
        this.payId = payId;
        this.orderId = orderId;
        this.type = type;
        this.price = price;
        this.reallyPrice = reallyPrice;
        this.payUrl = payUrl;
        this.isAuto = isAuto;
        this.timeOut = timeOut;
        this.errorMessage = errorMessage;
    }

    public static OrderApplicationResult success(PayOrder payOrder, String payId, String orderId, int type,
                                                 BigDecimal price, BigDecimal reallyPrice, String payUrl,
                                                 int isAuto, int timeOut) {
        return new OrderApplicationResult(true, payOrder, payId, orderId, type, price, reallyPrice,
                payUrl, isAuto, timeOut, null);
    }

    public static OrderApplicationResult failure(String errorMessage) {
        return new OrderApplicationResult(false, null, null, null, 0, null, null, null, 0, 0, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public PayOrder getPayOrder() {
        return payOrder;
    }

    public String getPayId() {
        return payId;
    }

    public String getOrderId() {
        return orderId;
    }

    public int getType() {
        return type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getReallyPrice() {
        return reallyPrice;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public int getIsAuto() {
        return isAuto;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
