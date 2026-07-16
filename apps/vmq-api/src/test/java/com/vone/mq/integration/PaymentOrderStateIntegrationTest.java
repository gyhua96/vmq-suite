package com.vone.mq.integration;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.service.PaymentOrderStateService;
import com.vone.mq.service.PriceLockService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class PaymentOrderStateIntegrationTest {

    static {
        System.setProperty("vmq.admin.password", "test-only-password");
    }

    @Autowired
    private PaymentOrderStateService stateService;
    @Autowired
    private PriceLockService priceLockService;
    @Autowired
    private PayOrderDao payOrderDao;
    @Autowired
    private TmpPriceDao tmpPriceDao;

    @Before
    public void cleanDatabase() {
        payOrderDao.deleteAll();
        tmpPriceDao.deleteAll();
    }

    @Test
    public void appPushPaymentMarksWaitingOrderPaidOnceAndReleasesPriceLock() {
        PayOrder order = saveOrder("PAY-STATE-001", "ORDER-STATE-001", PaymentState.WAITING, 49.95, 49.96);
        String lockKey = priceLockService.lockKey(order.getType(), "49.96");
        priceLockService.tryLock(order.getType(), 49.96);
        assertTrue(tmpPriceDao.findById(lockKey).isPresent());

        boolean firstResult = stateService.markPaidFromAppPush(order, "49.96", 1782874000000L);

        assertTrue(firstResult);
        assertFalse(tmpPriceDao.findById(lockKey).isPresent());
        PayOrder paid = payOrderDao.findByPayId("PAY-STATE-001");
        assertEquals(PaymentState.PAID, paid.getState());
        assertEquals(1782874000000L, paid.getPayDate());
        assertEquals(1782874000000L, paid.getCloseDate());

        priceLockService.tryLock(order.getType(), 49.96);
        boolean secondResult = stateService.markPaidFromAppPush(order, "49.96", 1782874001111L);

        assertFalse(secondResult);
        assertTrue(tmpPriceDao.findById(lockKey).isPresent());
        PayOrder stillPaid = payOrderDao.findByPayId("PAY-STATE-001");
        assertEquals(1782874000000L, stillPaid.getPayDate());
        assertEquals(PaymentState.PAID, stillPaid.getState());
    }

    @Test
    public void closeWaitingOrderMarksClosedAndReleasesPriceLock() {
        PayOrder order = saveOrder("PAY-CLOSE-001", "ORDER-CLOSE-001", PaymentState.WAITING, 12.34, 12.35);
        String lockKey = priceLockService.lockKey(order.getType(), order.getReallyPrice());
        priceLockService.tryLock(order.getType(), order.getReallyPrice());

        boolean closedResult = stateService.closeWaitingOrder(order, 1782875000000L);

        assertTrue(closedResult);
        assertFalse(tmpPriceDao.findById(lockKey).isPresent());
        PayOrder closed = payOrderDao.findByPayId("PAY-CLOSE-001");
        assertEquals(PaymentState.CLOSED, closed.getState());
        assertEquals(1782875000000L, closed.getCloseDate());
    }

    @Test
    public void closeWaitingOrderDoesNotOverwritePaidOrderOrReleaseCurrentLockWhenStateChangedInDatabase() {
        PayOrder staleWaiting = saveOrder("PAY-CLOSE-RACE", "ORDER-CLOSE-RACE", PaymentState.WAITING, 13.00, 13.01);
        PayOrder paid = payOrderDao.findByPayId("PAY-CLOSE-RACE");
        paid.setState(PaymentState.PAID);
        paid.setPayDate(1782874999000L);
        paid.setCloseDate(1782874999000L);
        payOrderDao.save(paid);
        String lockKey = priceLockService.lockKey(staleWaiting.getType(), staleWaiting.getReallyPrice());
        priceLockService.tryLock(staleWaiting.getType(), staleWaiting.getReallyPrice());

        boolean closedResult = stateService.closeWaitingOrder(staleWaiting, 1782875000000L);

        assertFalse(closedResult);
        assertTrue(tmpPriceDao.findById(lockKey).isPresent());
        PayOrder stillPaid = payOrderDao.findByPayId("PAY-CLOSE-RACE");
        assertEquals(PaymentState.PAID, stillPaid.getState());
        assertEquals(1782874999000L, stillPaid.getCloseDate());
    }

    @Test
    public void deleteWaitingOrderReleasesPriceLockButDeletePaidOrderDoesNotTouchCurrentLock() {
        PayOrder waiting = saveOrder("PAY-DELETE-WAITING", "ORDER-DELETE-WAITING", PaymentState.WAITING, 30.00, 30.01);
        String waitingLock = priceLockService.lockKey(waiting.getType(), waiting.getReallyPrice());
        priceLockService.tryLock(waiting.getType(), waiting.getReallyPrice());

        stateService.deleteOrder(waiting);

        assertFalse(tmpPriceDao.findById(waitingLock).isPresent());
        assertFalse(payOrderDao.findById(waiting.getId()).isPresent());

        PayOrder paid = saveOrder("PAY-DELETE-PAID", "ORDER-DELETE-PAID", PaymentState.PAID, 40.00, 40.01);
        String paidLock = priceLockService.lockKey(paid.getType(), paid.getReallyPrice());
        priceLockService.tryLock(paid.getType(), paid.getReallyPrice());

        stateService.deleteOrder(paid);

        assertTrue(tmpPriceDao.findById(paidLock).isPresent());
        assertFalse(payOrderDao.findById(paid.getId()).isPresent());
    }

    @Test
    public void manualCallbackUsesDatabaseCurrentStateWhenStaleWaitingObjectWasAlreadyPaid() {
        PayOrder staleWaiting = saveOrder("PAY-MANUAL-RACE", "ORDER-MANUAL-RACE", PaymentState.WAITING, 50.00, 50.01);
        PayOrder paid = payOrderDao.findByPayId("PAY-MANUAL-RACE");
        paid.setState(PaymentState.PAID);
        payOrderDao.save(paid);
        String lockKey = priceLockService.lockKey(staleWaiting.getType(), staleWaiting.getReallyPrice());
        priceLockService.tryLock(staleWaiting.getType(), staleWaiting.getReallyPrice());

        stateService.markPaidByManualCallback(staleWaiting);

        assertTrue(tmpPriceDao.findById(lockKey).isPresent());
        PayOrder stillPaid = payOrderDao.findByPayId("PAY-MANUAL-RACE");
        assertEquals(PaymentState.PAID, stillPaid.getState());
    }

    @Test
    public void manualCallbackReleasesLockWhenDatabaseCurrentStateIsWaiting() {
        PayOrder waiting = saveOrder("PAY-MANUAL-WAITING", "ORDER-MANUAL-WAITING", PaymentState.WAITING, 55.00, 55.01);
        String lockKey = priceLockService.lockKey(waiting.getType(), waiting.getReallyPrice());
        priceLockService.tryLock(waiting.getType(), waiting.getReallyPrice());

        stateService.markPaidByManualCallback(waiting);

        assertFalse(tmpPriceDao.findById(lockKey).isPresent());
        PayOrder paid = payOrderDao.findByPayId("PAY-MANUAL-WAITING");
        assertEquals(PaymentState.PAID, paid.getState());
    }

    @Test
    public void callbackFailureDoesNotOverwriteCurrentClosedOrder() {
        PayOrder stalePaid = saveOrder("PAY-CALLBACK-RACE", "ORDER-CALLBACK-RACE", PaymentState.PAID, 56.00, 56.01);
        PayOrder closed = payOrderDao.findByPayId("PAY-CALLBACK-RACE");
        closed.setState(PaymentState.CLOSED);
        payOrderDao.save(closed);

        boolean result = stateService.markCallbackFailed(stalePaid);

        assertFalse(result);
        PayOrder stillClosed = payOrderDao.findByPayId("PAY-CALLBACK-RACE");
        assertEquals(PaymentState.CLOSED, stillClosed.getState());
    }

    @Test
    public void callbackFailureCanMarkCurrentPaidOrderFailed() {
        PayOrder paid = saveOrder("PAY-CALLBACK-FAILED", "ORDER-CALLBACK-FAILED", PaymentState.PAID, 57.00, 57.01);

        boolean result = stateService.markCallbackFailed(paid);

        assertTrue(result);
        PayOrder failed = payOrderDao.findByPayId("PAY-CALLBACK-FAILED");
        assertEquals(PaymentState.CALLBACK_FAILED, failed.getState());
    }

    @Test
    public void deleteOrderUsesDatabaseCurrentStateWhenStaleWaitingObjectWasAlreadyPaid() {
        PayOrder staleWaiting = saveOrder("PAY-DELETE-RACE", "ORDER-DELETE-RACE", PaymentState.WAITING, 60.00, 60.01);
        PayOrder paid = payOrderDao.findByPayId("PAY-DELETE-RACE");
        paid.setState(PaymentState.PAID);
        payOrderDao.save(paid);
        String lockKey = priceLockService.lockKey(staleWaiting.getType(), staleWaiting.getReallyPrice());
        priceLockService.tryLock(staleWaiting.getType(), staleWaiting.getReallyPrice());

        stateService.deleteOrder(staleWaiting);

        assertTrue(tmpPriceDao.findById(lockKey).isPresent());
        assertFalse(payOrderDao.findById(staleWaiting.getId()).isPresent());
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
        payOrder.setNotifyUrl("https://merchant.example/cb");
        payOrder.setReturnUrl("https://merchant.example/return");
        payOrder.setState(state);
        payOrder.setIsAuto(1);
        payOrder.setPayUrl("qr-content");
        PayOrder saved = payOrderDao.save(payOrder);
        assertNotNull(saved.getId());
        return saved;
    }
}
