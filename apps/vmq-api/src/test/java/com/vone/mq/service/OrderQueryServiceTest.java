package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.CreateOrderRes;
import com.vone.mq.entity.PayOrder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OrderQueryServiceTest {
    private OrderQueryService service;
    private PayOrderDao payOrderDao;
    private PaymentOrderStateService paymentOrderStateService;
    private CallbackPayloadBuilder callbackPayloadBuilder;
    private SettingAccessService settingAccessService;

    @Before
    public void setUp() {
        service = new OrderQueryService();
        payOrderDao = mock(PayOrderDao.class);
        paymentOrderStateService = mock(PaymentOrderStateService.class);
        callbackPayloadBuilder = mock(CallbackPayloadBuilder.class);
        settingAccessService = mock(SettingAccessService.class);
        ReflectionTestUtils.setField(service, "payOrderDao", payOrderDao);
        ReflectionTestUtils.setField(service, "paymentOrderStateService", paymentOrderStateService);
        ReflectionTestUtils.setField(service, "callbackPayloadBuilder", callbackPayloadBuilder);
        ReflectionTestUtils.setField(service, "settingAccessService", settingAccessService);
    }

    @Test
    public void closeWaitingOrderDelegatesStateChange() {
        PayOrder payOrder = order(PaymentState.WAITING);
        when(payOrderDao.findByOrderId("order-1")).thenReturn(payOrder);
        when(paymentOrderStateService.closeWaitingOrder(org.mockito.ArgumentMatchers.eq(payOrder), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);

        CommonRes result = service.closeWaitingOrder("order-1");

        assertEquals(1, result.getCode());
        verify(paymentOrderStateService).closeWaitingOrder(org.mockito.ArgumentMatchers.eq(payOrder), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    public void closeWaitingOrderReturnsStateErrorWhenConcurrentStateChangeWins() {
        PayOrder payOrder = order(PaymentState.WAITING);
        when(payOrderDao.findByOrderId("order-1")).thenReturn(payOrder);
        when(paymentOrderStateService.closeWaitingOrder(org.mockito.ArgumentMatchers.eq(payOrder), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(false);

        CommonRes result = service.closeWaitingOrder("order-1");

        assertEquals(-1, result.getCode());
        assertEquals("订单状态不允许关闭", result.getMsg());
    }

    @Test
    public void getOrderReturnsLegacyCreateOrderResponse() {
        PayOrder payOrder = order(PaymentState.WAITING);
        when(payOrderDao.findByOrderId("order-1")).thenReturn(payOrder);
        when(settingAccessService.closeMinutes()).thenReturn(5);

        CommonRes result = service.getOrder("order-1");

        assertEquals(1, result.getCode());
        CreateOrderRes data = (CreateOrderRes) result.getData();
        assertEquals("pay-1", data.getPayId());
        assertEquals("order-1", data.getOrderId());
        assertEquals(2, data.getPayType());
        assertEquals(new BigDecimal("49.95"), data.getPrice());
        assertEquals(new BigDecimal("49.96"), data.getReallyPrice());
        assertEquals(PaymentState.WAITING, data.getState());
        assertEquals(5, data.getTimeOut());
    }

    @Test
    public void checkOrderReturnsWaitingAndClosedErrors() {
        when(payOrderDao.findByOrderId("waiting")).thenReturn(order(PaymentState.WAITING));
        when(payOrderDao.findByOrderId("closed")).thenReturn(order(PaymentState.CLOSED));

        assertEquals("订单未支付", service.checkOrder("waiting", "secret").getMsg());
        assertEquals("订单已过期", service.checkOrder("closed", "secret").getMsg());
    }

    @Test
    public void checkOrderBuildsReturnUrlWithCallbackQuery() {
        PayOrder payOrder = order(PaymentState.PAID);
        payOrder.setReturnUrl("");
        when(payOrderDao.findByOrderId("order-1")).thenReturn(payOrder);
        when(settingAccessService.defaultReturnUrl()).thenReturn("https://example.com/return");
        when(callbackPayloadBuilder.buildQuery(payOrder, "secret")).thenReturn("payId=pay-1");

        CommonRes result = service.checkOrder("order-1", "secret");

        assertEquals(1, result.getCode());
        assertEquals("https://example.com/return?payId=pay-1", result.getData());
    }

    @Test
    public void getMonitorStateUsesCompatibleDefaults() {
        when(settingAccessService.getValue(SettingAccessService.KEY_MONITOR_STATE, "-1")).thenReturn("1");
        when(settingAccessService.getValue(SettingAccessService.KEY_LAST_HEART, "0")).thenReturn("1782870000000");
        when(settingAccessService.getValue(SettingAccessService.KEY_LAST_PAY, "0")).thenReturn("1782870001000");

        CommonRes result = service.getMonitorState();

        assertEquals(1, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("1", data.get("state"));
        assertEquals("1782870000000", data.get("lastheart"));
        assertEquals("1782870001000", data.get("lastpay"));
    }

    private PayOrder order(int state) {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(99L);
        payOrder.setPayId("pay-1");
        payOrder.setOrderId("order-1");
        payOrder.setType(2);
        payOrder.setPrice(49.95);
        payOrder.setReallyPrice(49.96);
        payOrder.setPayUrl("pay-url");
        payOrder.setIsAuto(1);
        payOrder.setState(state);
        payOrder.setCreateDate(1234L);
        payOrder.setReturnUrl("https://merchant.example/return");
        return payOrder;
    }
}
