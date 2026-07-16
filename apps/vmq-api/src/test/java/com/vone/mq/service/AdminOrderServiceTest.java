package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.entity.PayOrder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import java.util.Map;
import java.util.Optional;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminOrderServiceTest {
    private static final String ORDER_NOT_FOUND_MESSAGE = "\u8ba2\u5355\u4e0d\u5b58\u5728";

    private AdminOrderService service;
    private PayOrderDao payOrderDao;
    private CallbackTaskService callbackTaskService;
    private PaymentOrderStateService paymentOrderStateService;
    private SettingAccessService settingAccessService;

    @Before
    public void setUp() {
        service = new AdminOrderService();
        payOrderDao = mock(PayOrderDao.class);
        callbackTaskService = mock(CallbackTaskService.class);
        paymentOrderStateService = mock(PaymentOrderStateService.class);
        settingAccessService = mock(SettingAccessService.class);
        ReflectionTestUtils.setField(service, "payOrderDao", payOrderDao);
        ReflectionTestUtils.setField(service, "callbackTaskService", callbackTaskService);
        ReflectionTestUtils.setField(service, "paymentOrderStateService", paymentOrderStateService);
        ReflectionTestUtils.setField(service, "settingAccessService", settingAccessService);
    }

    @Test
    public void resendCallbackMarksPaidWhenCallbackSucceeds() {
        PayOrder payOrder = order(PaymentState.CALLBACK_FAILED);
        when(payOrderDao.findById(99L)).thenReturn(Optional.of(payOrder));
        when(settingAccessService.communicationKey()).thenReturn("secret");
        when(settingAccessService.defaultNotifyUrl()).thenReturn(Optional.empty());
        when(callbackTaskService.sendNowAndRecord(payOrder, Optional.empty(), "secret"))
                .thenReturn(CallbackResult.success("success"));

        CommonRes result = service.resendCallback(99);

        assertEquals(1, result.getCode());
        verify(paymentOrderStateService).markPaidByManualCallback(payOrder);
    }

    @Test
    public void resendCallbackReturnsResponseWhenCallbackFails() {
        PayOrder payOrder = order(PaymentState.CALLBACK_FAILED);
        when(payOrderDao.findById(99L)).thenReturn(Optional.of(payOrder));
        when(settingAccessService.communicationKey()).thenReturn("secret");
        when(settingAccessService.defaultNotifyUrl()).thenReturn(Optional.empty());
        when(callbackTaskService.sendNowAndRecord(payOrder, Optional.empty(), "secret"))
                .thenReturn(CallbackResult.failure("failed-response", "\u901a\u77e5\u5f02\u6b65\u5730\u5740\u5931\u8d25"));

        CommonRes result = service.resendCallback(99);

        assertEquals(-2, result.getCode());
        assertEquals("failed-response", result.getData());
    }

    @Test
    public void resendCallbackReturnsBusinessErrorWhenMissing() {
        when(payOrderDao.findById(99L)).thenReturn(Optional.empty());

        CommonRes result = service.resendCallback(99);

        assertEquals(-1, result.getCode());
        assertEquals(ORDER_NOT_FOUND_MESSAGE, result.getMsg());
    }

    @Test
    public void resendCallbackReturnsBusinessErrorWhenIdIsNull() {
        CommonRes result = service.resendCallback(null);

        assertEquals(-1, result.getCode());
        assertEquals(ORDER_NOT_FOUND_MESSAGE, result.getMsg());
    }

    @Test
    public void getMainCombinesPaidAndCallbackFailedAmounts() {
        when(payOrderDao.getTodayCount(anyLong(), anyLong())).thenReturn(10);
        when(payOrderDao.getTodayCount(anyLong(), anyLong(), eq(PaymentState.PAID))).thenReturn(6);
        when(payOrderDao.getTodayCount(anyLong(), anyLong(), eq(PaymentState.CALLBACK_FAILED))).thenReturn(2);
        when(payOrderDao.getTodayCount(anyLong(), anyLong(), eq(PaymentState.CLOSED))).thenReturn(1);
        when(payOrderDao.getTodayCountMoney(anyLong(), anyLong(), eq(PaymentState.PAID))).thenReturn(new BigDecimal("100.10"));
        when(payOrderDao.getTodayCountMoney(anyLong(), anyLong(), eq(PaymentState.CALLBACK_FAILED))).thenReturn(new BigDecimal("20.20"));
        when(payOrderDao.getCount(PaymentState.PAID)).thenReturn(8);
        when(payOrderDao.getCount(PaymentState.CALLBACK_FAILED)).thenReturn(2);
        when(payOrderDao.getCountMoney(PaymentState.PAID)).thenReturn(new BigDecimal("300.30"));
        when(payOrderDao.getCountMoney(PaymentState.CALLBACK_FAILED)).thenReturn(new BigDecimal("40.40"));

        CommonRes result = service.getMain();

        assertEquals(1, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("10", data.get("todayOrder"));
        assertEquals("8", data.get("todaySuccessOrder"));
        assertEquals("1", data.get("todayCloseOrder"));
        assertEquals("120.3", data.get("todayMoney"));
        assertEquals("10", data.get("countOrder"));
        assertEquals("340.7", data.get("countMoney"));
    }

    @Test
    public void getOrdersDefaultsInvalidPagingParameters() {
        when(payOrderDao.findAll(isA(Specification.class), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<PayOrder>(Collections.emptyList()));

        service.getOrders(0, null, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(payOrderDao).findAll(isA(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals("id: DESC", pageable.getSort().toString());
    }

    @Test
    public void getOrdersKeepsValidPagingParameters() {
        when(payOrderDao.findAll(isA(Specification.class), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<PayOrder>(Collections.emptyList()));

        service.getOrders(3, 20, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(payOrderDao).findAll(isA(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(2, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals("id: DESC", pageable.getSort().toString());
    }

    @Test
    public void deleteOrderDelegatesStateAwareDelete() {
        PayOrder payOrder = order(PaymentState.WAITING);
        when(payOrderDao.findById(99L)).thenReturn(Optional.of(payOrder));

        CommonRes result = service.deleteOrder(99L);

        assertEquals(1, result.getCode());
        verify(paymentOrderStateService).deleteOrder(payOrder);
    }

    @Test
    public void deleteOrderReturnsBusinessErrorWhenMissing() {
        when(payOrderDao.findById(99L)).thenReturn(Optional.empty());

        CommonRes result = service.deleteOrder(99L);

        assertEquals(-1, result.getCode());
        assertEquals(ORDER_NOT_FOUND_MESSAGE, result.getMsg());
    }

    @Test
    public void deleteOrderReturnsBusinessErrorWhenIdIsNull() {
        CommonRes result = service.deleteOrder(null);

        assertEquals(-1, result.getCode());
        assertEquals(ORDER_NOT_FOUND_MESSAGE, result.getMsg());
    }

    @Test
    public void deleteOrdersBefore7DaysDoesNotDeleteWaitingOrdersInBulk() {
        CommonRes result = service.deleteOrdersBefore7Days();

        assertEquals(1, result.getCode());
        verify(payOrderDao).deleteByAfterCreateDateAndStateNot(anyLong(), eq(PaymentState.WAITING));
    }

    private PayOrder order(int state) {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(99L);
        payOrder.setState(state);
        return payOrder;
    }
}
