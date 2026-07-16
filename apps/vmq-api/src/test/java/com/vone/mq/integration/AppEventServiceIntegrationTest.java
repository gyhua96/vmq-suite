package com.vone.mq.integration;

import com.vone.mq.dao.CallbackTaskDao;
import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.PaymentEventDao;
import com.vone.mq.dao.SettingDao;
import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.domain.CallbackTaskState;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.entity.CallbackTask;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.PaymentEvent;
import com.vone.mq.entity.Setting;
import com.vone.mq.service.AppEventService;
import com.vone.mq.service.CallbackResult;
import com.vone.mq.service.CallbackService;
import com.vone.mq.service.PriceLockService;
import com.vone.mq.service.SettingAccessService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class AppEventServiceIntegrationTest {

    static {
        System.setProperty("vmq.admin.password", "test-only-password");
    }

    @Autowired
    private AppEventService appEventService;
    @Autowired
    private PayOrderDao payOrderDao;
    @Autowired
    private TmpPriceDao tmpPriceDao;
    @Autowired
    private PaymentEventDao paymentEventDao;
    @Autowired
    private CallbackTaskDao callbackTaskDao;
    @Autowired
    private SettingDao settingDao;
    @Autowired
    private PriceLockService priceLockService;
    @MockBean
    private CallbackService callbackService;

    @Before
    public void cleanDatabase() {
        callbackTaskDao.deleteAll();
        paymentEventDao.deleteAll();
        payOrderDao.deleteAll();
        tmpPriceDao.deleteAll();
        settingDao.deleteAll();
    }

    @Test
    public void paymentPushMatchesWaitingOrderRecordsEventReleasesLockAndRecordsSuccessfulCallback() {
        setting(SettingAccessService.KEY_CALLBACK_ASYNC, "0");
        setting(SettingAccessService.KEY_NOTIFY_URL, "https://merchant.example/global-cb");
        PayOrder order = saveOrder("PAY-APP-001", "ORDER-APP-001", PaymentState.WAITING, 49.95, 49.96);
        String lockKey = priceLockService.lockKey(order.getType(), "49.96");
        priceLockService.tryLock(order.getType(), 49.96);
        when(callbackService.sendNotify(any(PayOrder.class), any(), eq("secret")))
                .thenReturn(CallbackResult.success("success"));

        CommonRes result = appEventService.handlePaymentPush(2, "49.96", "1782876000000", "secret");

        assertEquals(1, result.getCode());
        assertEquals("1782876000000", settingDao.findById(SettingAccessService.KEY_LAST_PAY).get().getVvalue());
        assertFalse(tmpPriceDao.findById(lockKey).isPresent());

        PayOrder paid = payOrderDao.findByPayId("PAY-APP-001");
        assertEquals(PaymentState.PAID, paid.getState());
        assertEquals(1782876000000L, paid.getPayDate());
        assertEquals(1782876000000L, paid.getCloseDate());

        PaymentEvent event = paymentEventDao.findByEventKey("2-49.96-1782876000000");
        assertNotNull(event);
        assertEquals(new BigDecimal("49.96"), event.getPrice());

        CallbackTask task = callbackTaskDao.findTopByOrderIdOrderByIdDesc(paid.getId());
        assertNotNull(task);
        assertEquals(CallbackTaskState.SUCCESS, task.getState());
        assertEquals(0, task.getRetryCount());
        assertEquals("success", task.getLastResponse());
        assertTrue(task.getQuery().contains("payId=PAY-APP-001"));
        verify(callbackService).sendNotify(any(PayOrder.class), any(), eq("secret"));
    }

    @Test
    public void duplicatePaymentPushReturnsLegacyDuplicateMessageWithoutTouchingOrderAgain() {
        setting(SettingAccessService.KEY_CALLBACK_ASYNC, "0");
        PayOrder order = saveOrder("PAY-DUP-EVENT", "ORDER-DUP-EVENT", PaymentState.WAITING, 20.00, 20.01);
        priceLockService.tryLock(order.getType(), 20.01);
        when(callbackService.sendNotify(any(PayOrder.class), any(), eq("secret")))
                .thenReturn(CallbackResult.success("success"));

        CommonRes first = appEventService.handlePaymentPush(2, "20.01", "1782876001000", "secret");
        CommonRes duplicate = appEventService.handlePaymentPush(2, "20.01", "1782876001000", "secret");

        assertEquals(1, first.getCode());
        assertEquals(1, duplicate.getCode());
        assertEquals("成功", duplicate.getMsg());
        assertEquals(1, paymentEventDao.findAll().size());
        assertEquals(1, callbackTaskDao.findAll().size());
        assertEquals(1782876001000L, payOrderDao.findByPayId("PAY-DUP-EVENT").getPayDate());
    }

    @Test
    public void paymentPushCreatesUnmatchedTransferWhenNoWaitingOrderExists() {
        CommonRes result = appEventService.handlePaymentPush(1, "88.66", "1782876002000", "secret");

        assertEquals(1, result.getCode());
        assertNotNull(paymentEventDao.findByEventKey("1-88.66-1782876002000"));
        List<PayOrder> orders = payOrderDao.findAll();
        assertEquals(1, orders.size());
        PayOrder transfer = orders.get(0);
        assertEquals(PaymentState.PAID, transfer.getState());
        assertEquals(new BigDecimal("88.66"), transfer.getReallyPrice());
        assertTrue(transfer.getPayId().contains("1782876002000"));
        assertEquals(0, callbackTaskDao.count());
        verifyNoInteractions(callbackService);
    }

    @Test
    public void paymentPushRejectsInvalidAmountBeforeRecordingSideEffects() {
        CommonRes result = appEventService.handlePaymentPush(2, "1.001", "1782876002500", "secret");

        assertEquals(-1, result.getCode());
        assertEquals("订单金额最多支持2位小数", result.getMsg());
        assertFalse(settingDao.findById(SettingAccessService.KEY_LAST_PAY).isPresent());
        assertEquals(0, paymentEventDao.count());
        assertEquals(0, payOrderDao.count());
        assertEquals(0, callbackTaskDao.count());
        verifyNoInteractions(callbackService);
    }

    @Test
    public void asyncPaymentPushEnqueuesCallbackTaskWithoutSendingHttpNow() {
        setting(SettingAccessService.KEY_CALLBACK_ASYNC, "1");
        setting(SettingAccessService.KEY_NOTIFY_URL, "https://merchant.example/global-cb");
        PayOrder order = saveOrder("PAY-ASYNC-001", "ORDER-ASYNC-001", PaymentState.WAITING, 66.60, 66.61);
        priceLockService.tryLock(order.getType(), 66.61);

        CommonRes result = appEventService.handlePaymentPush(2, "66.61", "1782876003000", "secret");

        assertEquals(1, result.getCode());
        PayOrder paid = payOrderDao.findByPayId("PAY-ASYNC-001");
        assertEquals(PaymentState.PAID, paid.getState());
        CallbackTask task = callbackTaskDao.findTopByOrderIdOrderByIdDesc(paid.getId());
        assertNotNull(task);
        assertEquals(CallbackTaskState.RETRY_WAITING, task.getState());
        assertEquals(0, task.getRetryCount());
        assertEquals("https://merchant.example/order-cb", task.getNotifyUrl());
        assertTrue(task.getQuery().contains("payId=PAY-ASYNC-001"));
        verifyNoInteractions(callbackService);
    }

    private PayOrder saveOrder(String payId, String orderId, int state, double price, double reallyPrice) {
        PayOrder payOrder = new PayOrder();
        payOrder.setPayId(payId);
        payOrder.setOrderId(orderId);
        payOrder.setCreateDate(1782873030000L);
        payOrder.setPayDate(0);
        payOrder.setCloseDate(0);
        payOrder.setParam("param");
        payOrder.setType(2);
        payOrder.setPrice(price);
        payOrder.setReallyPrice(reallyPrice);
        payOrder.setNotifyUrl("https://merchant.example/order-cb");
        payOrder.setReturnUrl("https://merchant.example/return");
        payOrder.setState(state);
        payOrder.setIsAuto(1);
        payOrder.setPayUrl("qr-content");
        return payOrderDao.save(payOrder);
    }

    private void setting(String key, String value) {
        Setting setting = new Setting();
        setting.setVkey(key);
        setting.setVvalue(value);
        settingDao.save(setting);
    }
}
