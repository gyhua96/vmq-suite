package com.vone.mq.domain;

public final class PaymentState {
    public static final int CLOSED = -1;
    public static final int WAITING = 0;
    public static final int PAID = 1;
    public static final int CALLBACK_FAILED = 2;

    private PaymentState() {
    }
}
