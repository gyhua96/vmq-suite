package com.vone.mq.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {
    private static final int SCALE = 2;

    private MoneyUtil() {
    }

    public static BigDecimal parsePositive(String value) {
        return requirePositiveAmount(parse(value));
    }

    public static BigDecimal requirePositive(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("请传入订单金额");
        }
        return requirePositiveAmount(value);
    }

    /** @deprecated use {@link #parsePositive(String)} to keep the amount exact. */
    @Deprecated
    public static double parsePositiveDouble(String value) {
        return parsePositive(value).doubleValue();
    }

    public static String normalize(String value) {
        return normalize(parse(value));
    }

    public static String normalize(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    public static BigDecimal addCent(BigDecimal amount) {
        return amount.add(new BigDecimal("0.01")).setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** @deprecated use {@link #addCent(BigDecimal)}. */
    @Deprecated
    public static double addCent(double amount) {
        return addCent(BigDecimal.valueOf(amount)).doubleValue();
    }

    public static BigDecimal subtractCent(BigDecimal amount) {
        return amount.subtract(new BigDecimal("0.01")).setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** @deprecated use {@link #subtractCent(BigDecimal)}. */
    @Deprecated
    public static double subtractCent(double amount) {
        return subtractCent(BigDecimal.valueOf(amount)).doubleValue();
    }

    private static BigDecimal parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("请传入订单金额");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("请传入订单金额");
        }
        if (amount.scale() > SCALE) {
            throw new IllegalArgumentException("订单金额最多支持2位小数");
        }
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount.scale() > SCALE) {
            throw new IllegalArgumentException("订单金额最多支持2位小数");
        }
        BigDecimal normalized = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("订单金额必须大于0");
        }
        return normalized;
    }
}
