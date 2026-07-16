package com.vone.mq.integration;

import com.vone.mq.dao.CallbackTaskDao;
import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.SettingDao;
import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.domain.CallbackTaskState;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.entity.CallbackTask;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.Setting;
import com.vone.mq.service.CallbackHttpClient;
import com.vone.mq.service.PriceLockService;
import com.vone.mq.service.QuartzService;
import com.vone.mq.service.SettingAccessService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = "vmq.monitor.offline-timeout-ms=60000")
@ActiveProfiles("test")
public class QuartzServiceIntegrationTest {

    static {
        System.setProperty("vmq.admin.password", "test-only-password");
    }

    @Autowired
    private QuartzService quartzService;
    @Autowired
    private PayOrderDao payOrderDao;
    @Autowired
    private TmpPriceDao tmpPriceDao;
    @Autowired
    private SettingDao settingDao;
    @Autowired
    private CallbackTaskDao callbackTaskDao;
    @Autowired
    private PriceLockService priceLockService;
    @MockBean
    private CallbackHttpClient callbackHttpClient;

    @Before
    public void cleanDatabase() {
        callbackTaskDao.deleteAll();
        payOrderDao.deleteAll();
        tmpPriceDao.deleteAll();
        settingDao.deleteAll();
    }

    @Test
    public void timerClosesExpiredWaitingOrdersUpdatesMonitorStateAndRetriesCallbackTasks() {
        long now = System.currentTimeMillis();
        setting(SettingAccessService.KEY_CLOSE_MINUTES, "1");
        setting(SettingAccessService.KEY_LAST_HEART, String.valueOf(now - 6 * 60 * 1000L));
        setting(SettingAccessService.KEY_MONITOR_STATE, "1");

        PayOrder expired = saveOrder("PAY-EXPIRED", "ORDER-EXPIRED", PaymentState.WAITING, 10.00, 10.01,
                now - 2 * 60 * 1000L);
        PayOrder active = saveOrder("PAY-ACTIVE", "ORDER-ACTIVE", PaymentState.WAITING, 11.00, 11.01, now);
        PayOrder paidExpired = saveOrder("PAY-PAID-EXPIRED", "ORDER-PAID-EXPIRED", PaymentState.PAID,
                12.00, 12.01, now - 2 * 60 * 1000L);
        String expiredLock = priceLockService.lockKey(expired.getType(), expired.getReallyPrice());
        String activeLock = priceLockService.lockKey(active.getType(), active.getReallyPrice());
        String paidExpiredLock = priceLockService.lockKey(paidExpired.getType(), paidExpired.getReallyPrice());
        priceLockService.tryLock(expired.getType(), expired.getReallyPrice());
        priceLockService.tryLock(active.getType(), active.getReallyPrice());
        priceLockService.tryLock(paidExpired.getType(), paidExpired.getReallyPrice());

        CallbackTask callbackTask = retryTask(200L, "PAY-CALLBACK", 2, now - 1000L);
        callbackTaskDao.save(callbackTask);
        when(callbackHttpClient.sendGet(eq("https://example.com/cb"), eq("payId=PAY-CALLBACK")))
                .thenReturn("success");

        quartzService.timerToZZP();

        PayOrder closed = payOrderDao.findByPayId("PAY-EXPIRED");
        assertEquals(PaymentState.CLOSED, closed.getState());
        assertTrue(closed.getCloseDate() >= now);
        assertFalse(tmpPriceDao.findById(expiredLock).isPresent());

        PayOrder stillWaiting = payOrderDao.findByPayId("PAY-ACTIVE");
        assertEquals(PaymentState.WAITING, stillWaiting.getState());
        assertTrue(tmpPriceDao.findById(activeLock).isPresent());

        PayOrder stillPaid = payOrderDao.findByPayId("PAY-PAID-EXPIRED");
        assertEquals(PaymentState.PAID, stillPaid.getState());
        assertTrue(tmpPriceDao.findById(paidExpiredLock).isPresent());

        assertEquals("0", settingDao.findById(SettingAccessService.KEY_MONITOR_STATE).get().getVvalue());

        CallbackTask retried = callbackTaskDao.findTopByOrderIdOrderByIdDesc(200L);
        assertEquals(CallbackTaskState.SUCCESS, retried.getState());
        assertEquals(2, retried.getRetryCount());
        assertEquals(0, retried.getNextRetryTime());
        assertEquals("success", retried.getLastResponse());
    }

    @Test
    public void timerUsesDefaultsWhenCloseMinutesAndLastHeartAreInvalid() {
        long now = System.currentTimeMillis();
        setting(SettingAccessService.KEY_CLOSE_MINUTES, "invalid");
        setting(SettingAccessService.KEY_LAST_HEART, "not-a-number");
        setting(SettingAccessService.KEY_MONITOR_STATE, "1");

        PayOrder expired = saveOrder("PAY-INVALID-CONFIG", "ORDER-INVALID-CONFIG", PaymentState.WAITING,
                12.00, 12.01, now - 6 * 60 * 1000L);
        String expiredLock = priceLockService.lockKey(expired.getType(), expired.getReallyPrice());
        priceLockService.tryLock(expired.getType(), expired.getReallyPrice());

        CallbackTask callbackTask = retryTask(201L, "PAY-INVALID-CALLBACK", 1, now - 1000L);
        callbackTaskDao.save(callbackTask);
        when(callbackHttpClient.sendGet(eq("https://example.com/cb"), eq("payId=PAY-INVALID-CALLBACK")))
                .thenReturn("success");

        quartzService.timerToZZP();

        PayOrder closed = payOrderDao.findByPayId("PAY-INVALID-CONFIG");
        assertEquals(PaymentState.CLOSED, closed.getState());
        assertFalse(tmpPriceDao.findById(expiredLock).isPresent());
        assertEquals("0", settingDao.findById(SettingAccessService.KEY_MONITOR_STATE).get().getVvalue());

        CallbackTask retried = callbackTaskDao.findTopByOrderIdOrderByIdDesc(201L);
        assertEquals(CallbackTaskState.SUCCESS, retried.getState());
        assertEquals("success", retried.getLastResponse());
    }

    private PayOrder saveOrder(String payId, String orderId, int state, double price, double reallyPrice, long createDate) {
        PayOrder payOrder = new PayOrder();
        payOrder.setPayId(payId);
        payOrder.setOrderId(orderId);
        payOrder.setCreateDate(createDate);
        payOrder.setPayDate(0);
        payOrder.setCloseDate(0);
        payOrder.setParam("param");
        payOrder.setType(2);
        payOrder.setPrice(price);
        payOrder.setReallyPrice(reallyPrice);
        payOrder.setNotifyUrl("https://example.com/cb");
        payOrder.setReturnUrl("https://example.com/return");
        payOrder.setState(state);
        payOrder.setIsAuto(1);
        payOrder.setPayUrl("qr-content");
        return payOrderDao.save(payOrder);
    }

    private CallbackTask retryTask(Long orderId, String payId, int retryCount, long nextRetryTime) {
        CallbackTask task = new CallbackTask();
        task.setOrderId(orderId);
        task.setPayId(payId);
        task.setNotifyUrl("https://example.com/cb");
        task.setQuery("payId=" + payId);
        task.setState(CallbackTaskState.RETRY_WAITING);
        task.setRetryCount(retryCount);
        task.setNextRetryTime(nextRetryTime);
        task.setCreateTime(1L);
        task.setUpdateTime(1L);
        return task;
    }

    private void setting(String key, String value) {
        Setting setting = new Setting();
        setting.setVkey(key);
        setting.setVvalue(value);
        settingDao.save(setting);
    }
}
