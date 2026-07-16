package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.entity.PayOrder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PaymentOrderCreationServiceTest {
    private PaymentOrderCreationService creationService;
    private PayOrderDao payOrderDao;

    @Before
    public void setUp() {
        creationService = new PaymentOrderCreationService();
        payOrderDao = mock(PayOrderDao.class);
        ReflectionTestUtils.setField(creationService, "payOrderDao", payOrderDao);
    }

    @Test
    public void createsWaitingOrderAfterPriceLocked() {
        when(payOrderDao.save(any(PayOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderCreationResult result = create();

        assertTrue(result.isSuccess());
        PayOrder payOrder = result.getPayOrder();
        assertEquals("pay-1", payOrder.getPayId());
        assertEquals("order-1", payOrder.getOrderId());
        assertEquals("param", payOrder.getParam());
        assertEquals(2, payOrder.getType());
        assertEquals(new BigDecimal("49.95"), payOrder.getPrice());
        assertEquals(new BigDecimal("49.96"), payOrder.getReallyPrice());
        assertEquals("https://example.com/notify", payOrder.getNotifyUrl());
        assertEquals("https://example.com/return", payOrder.getReturnUrl());
        assertEquals(PaymentState.WAITING, payOrder.getState());
        assertEquals(1, payOrder.getIsAuto());
        assertEquals("pay-url", payOrder.getPayUrl());
        assertEquals(1234L, payOrder.getCreateDate());
    }

    @Test
    public void releasesLockedPriceWhenPayIdAlreadyExists() {
        PayOrder existingOrder = new PayOrder();
        when(payOrderDao.findByPayId("pay-1")).thenReturn(existingOrder);

        OrderCreationResult result = create();

        assertFalse(result.isSuccess());
        assertEquals("商户订单号已存在！", result.getErrorMessage());
        verify(payOrderDao, never()).save(any(PayOrder.class));
    }

    @Test
    public void releasesLockedPriceWhenSaveFails() {
        RuntimeException failure = new RuntimeException("db failed");
        when(payOrderDao.save(any(PayOrder.class))).thenThrow(failure);

        try {
            create();
        } catch (RuntimeException e) {
            assertSame(failure, e);
        }

    }

    @Test
    public void createsUnmatchedTransferWithUniqueRecognizableIds() {
        when(payOrderDao.save(any(PayOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayOrder payOrder = creationService.createUnmatchedTransfer(2, 88.66, 1782870000000L);

        assertEquals("无订单转账-2-1782870000000", payOrder.getPayId());
        assertEquals("无订单转账-2-1782870000000", payOrder.getOrderId());
        assertEquals("无订单转账", payOrder.getParam());
        assertEquals("无订单转账", payOrder.getPayUrl());
        assertEquals(1782870000000L, payOrder.getCreateDate());
        assertEquals(1782870000000L, payOrder.getPayDate());
        assertEquals(1782870000000L, payOrder.getCloseDate());
        assertEquals(PaymentState.PAID, payOrder.getState());
        assertEquals(new BigDecimal("88.66"), payOrder.getPrice());
        assertEquals(new BigDecimal("88.66"), payOrder.getReallyPrice());
    }

    private OrderCreationResult create() {
        return creationService.createAfterPriceLocked(
                "pay-1",
                "order-1",
                "param",
                2,
                49.95,
                49.96,
                "https://example.com/notify",
                "https://example.com/return",
                1,
                "pay-url",
                1234L);
    }
}
