package com.vone.mq.service;

import com.vone.mq.entity.PayOrder;

public class OrderCreationResult {
    private final boolean success;
    private final PayOrder payOrder;
    private final String errorMessage;

    private OrderCreationResult(boolean success, PayOrder payOrder, String errorMessage) {
        this.success = success;
        this.payOrder = payOrder;
        this.errorMessage = errorMessage;
    }

    public static OrderCreationResult success(PayOrder payOrder) {
        return new OrderCreationResult(true, payOrder, null);
    }

    public static OrderCreationResult failure(String errorMessage) {
        return new OrderCreationResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public PayOrder getPayOrder() {
        return payOrder;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
