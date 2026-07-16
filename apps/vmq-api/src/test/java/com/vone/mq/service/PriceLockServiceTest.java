package com.vone.mq.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PriceLockServiceTest {
    private final PriceLockService priceLockService = new PriceLockService();

    @Test
    public void normalizesEquivalentAmountKeys() {
        assertEquals("2-49.9", priceLockService.lockKey(2, 49.90));
        assertEquals("2-49.9", priceLockService.lockKey(2, "49.90"));
        assertEquals("1-1", priceLockService.lockKey(1, "1.00"));
        assertEquals("1-1.01", priceLockService.lockKey(1, 1.01));
    }

    @Test
    public void rejectsInvalidAmountPrecision() {
        try {
            priceLockService.lockKey(2, "49.999");
            fail("Expected invalid precision");
        } catch (IllegalArgumentException e) {
            assertEquals("订单金额最多支持2位小数", e.getMessage());
        }
    }
}
