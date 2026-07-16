package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.Setting;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AppEventServiceTest {
    private AppEventService service;
    private PayOrderDao payOrderDao;
    private SettingAccessService settingAccessService;
    private PaymentEventService paymentEventService;
    private PaymentOrderCreationService paymentOrderCreationService;
    private PaymentOrderStateService paymentOrderStateService;
    private CallbackTaskService callbackTaskService;

    @Before
    public void setUp() {
        service = new AppEventService();
        payOrderDao = mock(PayOrderDao.class);
        settingAccessService = mock(SettingAccessService.class);
        paymentEventService = mock(PaymentEventService.class);
        paymentOrderCreationService = mock(PaymentOrderCreationService.class);
        paymentOrderStateService = mock(PaymentOrderStateService.class);
        callbackTaskService = mock(CallbackTaskService.class);
        ReflectionTestUtils.setField(service, "payOrderDao", payOrderDao);
        ReflectionTestUtils.setField(service, "settingAccessService", settingAccessService);
        ReflectionTestUtils.setField(service, "paymentEventService", paymentEventService);
        ReflectionTestUtils.setField(service, "paymentOrderCreationService", paymentOrderCreationService);
        ReflectionTestUtils.setField(service, "paymentOrderStateService", paymentOrderStateService);
        ReflectionTestUtils.setField(service, "callbackTaskService", callbackTaskService);
    }

    @Test
    public void recordHeartbeatUpdatesMonitorSettings() {
        long before = System.currentTimeMillis();
        CommonRes result = service.recordHeartbeat("1782870000000");
        long after = System.currentTimeMillis();

        assertEquals(1, result.getCode());
        verify(settingAccessService).saveValue(eq(SettingAccessService.KEY_LAST_HEART), argThat(value -> {
            long recordedAt = Long.parseLong(value);
            return recordedAt >= before && recordedAt <= after;
        }));
        verify(settingAccessService).saveValue(SettingAccessService.KEY_MONITOR_STATE, "1");
    }

    @Test
    public void paymentPushRejectsInvalidTimestampWithoutSideEffects() {
        CommonRes result = service.handlePaymentPush(2, "49.95", "not-a-time", "secret");

        assertEquals(-1, result.getCode());
        assertEquals("客户端时间错误", result.getMsg());
        verify(settingAccessService, never()).saveValue(SettingAccessService.KEY_LAST_PAY, "not-a-time");
        verify(paymentEventService, never()).recordIfNew(2, "49.95", 0L);
        verify(payOrderDao, never()).findByReallyPriceAndStateAndType(new BigDecimal("49.95"), PaymentState.WAITING, 2);
    }

    @Test
    public void paymentPushRejectsInvalidAmountWithoutSideEffects() {
        CommonRes result = service.handlePaymentPush(2, "abc", "1782870000000", "secret");

        assertEquals(-1, result.getCode());
        assertEquals("请传入订单金额", result.getMsg());
        verify(settingAccessService, never()).saveValue(SettingAccessService.KEY_LAST_PAY, "1782870000000");
        verify(paymentEventService, never()).recordIfNew(2, "abc", 1782870000000L);
        verify(payOrderDao, never()).findByReallyPriceAndStateAndType(
                any(BigDecimal.class),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    public void paymentPushReturnsDuplicateWhenEventAlreadyExists() {
        when(paymentEventService.recordIfNew(2, new BigDecimal("49.95"), 1782870000000L)).thenReturn(false);

        CommonRes result = service.handlePaymentPush(2, "49.95", "1782870000000", "secret");

        assertEquals(-1, result.getCode());
        verify(settingAccessService).saveValue(SettingAccessService.KEY_LAST_PAY, "1782870000000");
        verify(payOrderDao, never()).findByReallyPriceAndStateAndType(new BigDecimal("49.95"), PaymentState.WAITING, 2);
    }

    @Test
    public void paymentPushCreatesUnmatchedTransferWhenNoWaitingOrder() {
        when(paymentEventService.recordIfNew(2, new BigDecimal("88.66"), 1782870000000L)).thenReturn(true);
        when(paymentOrderCreationService.createUnmatchedTransfer(2, new BigDecimal("88.66"), 1782870000000L))
                .thenReturn(order());

        CommonRes result = service.handlePaymentPush(2, "88.66", "1782870000000", "secret");

        assertEquals(1, result.getCode());
        verify(paymentOrderCreationService).createUnmatchedTransfer(2, new BigDecimal("88.66"), 1782870000000L);
    }

    @Test
    public void paymentPushEnqueuesCallbackWhenAsyncEnabled() {
        PayOrder payOrder = order();
        Optional<Setting> defaultNotifyUrl = Optional.of(setting("notifyUrl", "https://example.com/notify"));
        when(paymentEventService.recordIfNew(2, new BigDecimal("49.95"), 1782870000000L)).thenReturn(true);
        when(payOrderDao.findByReallyPriceAndStateAndType(new BigDecimal("49.95"), PaymentState.WAITING, 2)).thenReturn(payOrder);
        when(paymentOrderStateService.markPaidFromAppPush(payOrder, "49.95", 1782870000000L)).thenReturn(true);
        when(settingAccessService.callbackAsyncEnabled()).thenReturn(true);
        when(settingAccessService.defaultNotifyUrl()).thenReturn(defaultNotifyUrl);

        CommonRes result = service.handlePaymentPush(2, "49.95", "1782870000000", "secret");

        assertEquals(1, result.getCode());
        verify(callbackTaskService).enqueue(payOrder, defaultNotifyUrl, "secret");
        verify(callbackTaskService, never()).sendNowAndRecord(payOrder, defaultNotifyUrl, "secret");
    }

    @Test
    public void paymentPushMarksCallbackFailedWhenSyncCallbackFails() {
        PayOrder payOrder = order();
        Optional<Setting> defaultNotifyUrl = Optional.empty();
        when(paymentEventService.recordIfNew(2, new BigDecimal("49.95"), 1782870000000L)).thenReturn(true);
        when(payOrderDao.findByReallyPriceAndStateAndType(new BigDecimal("49.95"), PaymentState.WAITING, 2)).thenReturn(payOrder);
        when(paymentOrderStateService.markPaidFromAppPush(payOrder, "49.95", 1782870000000L)).thenReturn(true);
        when(settingAccessService.defaultNotifyUrl()).thenReturn(defaultNotifyUrl);
        when(callbackTaskService.sendNowAndRecord(payOrder, defaultNotifyUrl, "secret"))
                .thenReturn(CallbackResult.failure("failed", "通知异步地址失败"));

        CommonRes result = service.handlePaymentPush(2, "49.95", "1782870000000", "secret");

        assertEquals(-1, result.getCode());
        assertEquals("通知异步地址失败", result.getMsg());
        verify(paymentOrderStateService).markCallbackFailed(payOrder);
    }

    private PayOrder order() {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(99L);
        payOrder.setType(2);
        payOrder.setReallyPrice(49.95);
        payOrder.setState(PaymentState.WAITING);
        return payOrder;
    }

    private Setting setting(String key, String value) {
        Setting setting = new Setting();
        setting.setVkey(key);
        setting.setVvalue(value);
        return setting;
    }
}
