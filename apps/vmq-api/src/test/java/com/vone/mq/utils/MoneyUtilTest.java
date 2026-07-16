package com.vone.mq.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MoneyUtilTest {
    @Test
    public void parsesPositiveMoneyWithTwoDecimals() {
        assertEquals(49.9, MoneyUtil.parsePositiveDouble("49.90"), 0.001);
        assertEquals("49.9", MoneyUtil.normalize("49.90"));
    }

    @Test
    public void rejectsZeroAndNegativeMoney() {
        assertInvalid("0", "订单金额必须大于0");
        assertInvalid("-1", "订单金额必须大于0");
    }

    @Test
    public void rejectsInvalidMoneyFormat() {
        assertInvalid(null, "请传入订单金额");
        assertInvalid("", "请传入订单金额");
        assertInvalid("abc", "请传入订单金额");
        assertInvalid("1.001", "订单金额最多支持2位小数");
    }

    @Test
    public void addsAndSubtractsCentPrecisely() {
        assertEquals(1.01, MoneyUtil.addCent(1.00), 0.001);
        assertEquals(0.99, MoneyUtil.subtractCent(1.00), 0.001);
        assertEquals(50.00, MoneyUtil.addCent(49.99), 0.001);
    }

    private void assertInvalid(String value, String message) {
        try {
            MoneyUtil.parsePositive(value);
        } catch (IllegalArgumentException e) {
            assertEquals(message, e.getMessage());
            return;
        }
        throw new AssertionError("Expected invalid money: " + value);
    }
}
