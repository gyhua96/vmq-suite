package com.vone.mq.integration;

import com.vone.mq.dao.CallbackTaskDao;
import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.PaymentEventDao;
import com.vone.mq.dao.SettingDao;
import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.service.AdminOrderService;
import com.vone.mq.service.PriceLockService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class AdminOrderServiceIntegrationTest {

    static {
        System.setProperty("vmq.admin.password", "test-only-password");
    }

    @Autowired
    private AdminOrderService adminOrderService;
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

    @Before
    public void cleanDatabase() {
        callbackTaskDao.deleteAll();
        paymentEventDao.deleteAll();
        payOrderDao.deleteAll();
        tmpPriceDao.deleteAll();
        settingDao.deleteAll();
    }

    @Test
    public void deleteOrdersBefore7DaysDoesNotDeleteWaitingOrdersOrTheirLocks() {
        long now = System.currentTimeMillis();
        PayOrder oldWaiting = saveOrder("PAY-OLD-WAITING", "ORDER-OLD-WAITING", PaymentState.WAITING,
                10.00, 10.01, now - 8 * 86400 * 1000L);
        PayOrder oldClosed = saveOrder("PAY-OLD-CLOSED", "ORDER-OLD-CLOSED", PaymentState.CLOSED,
                11.00, 11.01, now - 8 * 86400 * 1000L);
        PayOrder recentPaid = saveOrder("PAY-RECENT-PAID", "ORDER-RECENT-PAID", PaymentState.PAID,
                12.00, 12.01, now);
        String waitingLock = priceLockService.lockKey(oldWaiting.getType(), oldWaiting.getReallyPrice());
        priceLockService.tryLock(oldWaiting.getType(), oldWaiting.getReallyPrice());

        CommonRes result = adminOrderService.deleteOrdersBefore7Days();

        assertEquals(1, result.getCode());
        assertNotNull(payOrderDao.findByPayId(oldWaiting.getPayId()));
        assertTrue(tmpPriceDao.findById(waitingLock).isPresent());
        assertFalse(payOrderDao.findById(oldClosed.getId()).isPresent());
        assertNotNull(payOrderDao.findByPayId(recentPaid.getPayId()));
    }

    @Test
    public void getMainCountsCallbackFailedOrdersAsSuccessfulIncome() {
        long now = System.currentTimeMillis();
        saveOrder("PAY-PAID", "ORDER-PAID", PaymentState.PAID, 10.10, 10.10, now);
        saveOrder("PAY-CALLBACK-FAILED", "ORDER-CALLBACK-FAILED", PaymentState.CALLBACK_FAILED,
                20.20, 20.20, now);
        saveOrder("PAY-CLOSED", "ORDER-CLOSED", PaymentState.CLOSED, 30.30, 30.30, now);

        CommonRes result = adminOrderService.getMain();

        assertEquals(1, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("3", data.get("todayOrder"));
        assertEquals("2", data.get("todaySuccessOrder"));
        assertEquals("1", data.get("todayCloseOrder"));
        assertEquals("2", data.get("countOrder"));
        assertEquals("30.3", data.get("todayMoney"));
        assertEquals("30.3", data.get("countMoney"));
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
        payOrder.setNotifyUrl("https://merchant.example/cb");
        payOrder.setReturnUrl("https://merchant.example/return");
        payOrder.setState(state);
        payOrder.setIsAuto(1);
        payOrder.setPayUrl("qr-content");
        return payOrderDao.save(payOrder);
    }
}
