package com.vone.mq.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PaymentStateTest {
    @Test
    public void keepsLegacyWireValues() {
        assertEquals(-1, PaymentState.CLOSED);
        assertEquals(0, PaymentState.WAITING);
        assertEquals(1, PaymentState.PAID);
        assertEquals(2, PaymentState.CALLBACK_FAILED);
    }
}
