package com.vone.mq.service;

import com.vone.mq.dao.PayQrcodeDao;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.PayQrcode;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OrderApplicationServiceTest {
    private OrderApplicationService service;
    private PayQrcodeDao payQrcodeDao;
    private PriceLockService priceLockService;
    private PaymentOrderCreationService paymentOrderCreationService;
    private SettingAccessService settingAccessService;

    @Before
    public void setUp() {
        service = new OrderApplicationService();
        payQrcodeDao = mock(PayQrcodeDao.class);
        priceLockService = mock(PriceLockService.class);
        paymentOrderCreationService = mock(PaymentOrderCreationService.class);
        settingAccessService = mock(SettingAccessService.class);
        ReflectionTestUtils.setField(service, "payQrcodeDao", payQrcodeDao);
        ReflectionTestUtils.setField(service, "priceLockService", priceLockService);
        ReflectionTestUtils.setField(service, "paymentOrderCreationService", paymentOrderCreationService);
        ReflectionTestUtils.setField(service, "settingAccessService", settingAccessService);
    }

    @Test
    public void createsOrderWithAllocatedAutoQrcode() {
        PayOrder savedOrder = order(1234L);
        when(settingAccessService.payQf()).thenReturn(1);
        when(settingAccessService.payUrlForType(2)).thenReturn("auto-pay-url");
        when(settingAccessService.closeMinutes()).thenReturn(5);
        when(priceLockService.tryLock(2, new BigDecimal("49.95"))).thenReturn(0);
        when(priceLockService.tryLock(2, new BigDecimal("49.96"))).thenReturn(1);
        when(paymentOrderCreationService.createAfterPriceLocked(
                org.mockito.ArgumentMatchers.eq("pay-1"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("param"),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("49.95")),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("49.96")),
                org.mockito.ArgumentMatchers.eq("https://example.com/notify"),
                org.mockito.ArgumentMatchers.eq("https://example.com/return"),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq("auto-pay-url"),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(OrderCreationResult.success(savedOrder));

        OrderApplicationResult result = service.createOrder(
                "pay-1", "param", 2, "49.95", "https://example.com/notify", "https://example.com/return");

        assertTrue(result.isSuccess());
        assertEquals("pay-1", result.getPayId());
        assertEquals(2, result.getType());
        assertEquals(new BigDecimal("49.95"), result.getPrice());
        assertEquals(new BigDecimal("49.96"), result.getReallyPrice());
        assertEquals("auto-pay-url", result.getPayUrl());
        assertEquals(1, result.getIsAuto());
        assertEquals(5, result.getTimeOut());
        assertEquals(1234L, result.getPayOrder().getCreateDate());
    }

    @Test
    public void usesFixedQrcodeWhenPriceAndTypeMatch() {
        PayQrcode fixed = new PayQrcode();
        fixed.setPayUrl("fixed-pay-url");
        when(settingAccessService.payQf()).thenReturn(1);
        when(settingAccessService.payUrlForType(2)).thenReturn("auto-pay-url");
        when(settingAccessService.closeMinutes()).thenReturn(5);
        when(priceLockService.tryLock(2, new BigDecimal("49.95"))).thenReturn(1);
        when(payQrcodeDao.findByPriceAndType(new BigDecimal("49.95"), 2)).thenReturn(fixed);
        when(paymentOrderCreationService.createAfterPriceLocked(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("49.95")),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("49.95")),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq("fixed-pay-url"),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(OrderCreationResult.success(order(1234L)));

        OrderApplicationResult result = service.createOrder("pay-1", "param", 2, "49.95", "", "");

        assertTrue(result.isSuccess());
        assertEquals("fixed-pay-url", result.getPayUrl());
        assertEquals(0, result.getIsAuto());
    }

    @Test
    public void failsWhenAllAmountsAreOccupiedByDescendingAllocation() {
        when(settingAccessService.payQf()).thenReturn(2);
        when(priceLockService.tryLock(anyInt(), anyDouble())).thenReturn(0);

        OrderApplicationResult result = service.createOrder("pay-1", "param", 2, "0.01", "", "");

        assertFalse(result.isSuccess());
        assertEquals("所有金额均被占用", result.getErrorMessage());
        verify(paymentOrderCreationService, never()).createAfterPriceLocked(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    public void failsWhenNoCommonQrcodeConfigured() {
        when(settingAccessService.payQf()).thenReturn(1);
        when(priceLockService.tryLock(2, new BigDecimal("49.95"))).thenReturn(1);
        when(settingAccessService.payUrlForType(2)).thenReturn("");

        OrderApplicationResult result = service.createOrder("pay-1", "param", 2, "49.95", "", "");

        assertFalse(result.isSuccess());
        assertEquals("请先在V免签后台配置默认收款码或上传对应金额的二维码", result.getErrorMessage());
        verify(paymentOrderCreationService, never()).createAfterPriceLocked(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    public void propagatesCreationFailureMessage() {
        when(settingAccessService.payQf()).thenReturn(1);
        when(settingAccessService.payUrlForType(2)).thenReturn("auto-pay-url");
        when(priceLockService.tryLock(2, new BigDecimal("49.95"))).thenReturn(1);
        when(paymentOrderCreationService.createAfterPriceLocked(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("49.95")),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("49.95")),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq("auto-pay-url"),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(OrderCreationResult.failure("商户订单号已存在！"));

        OrderApplicationResult result = service.createOrder("pay-1", "param", 2, "49.95", "", "");

        assertFalse(result.isSuccess());
        assertEquals("商户订单号已存在！", result.getErrorMessage());
    }

    private PayOrder order(long createDate) {
        PayOrder payOrder = new PayOrder();
        payOrder.setCreateDate(createDate);
        return payOrder;
    }
}
