package com.vone.mq.integration;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.service.OrderCreationResult;
import com.vone.mq.service.PaymentOrderCreationService;
import com.vone.mq.service.PriceLockService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class PaymentOrderCreationIntegrationTest {

    static {
        System.setProperty("vmq.admin.password", "test-only-password");
    }

    @Autowired
    private PaymentOrderCreationService creationService;
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
    public void createAfterPriceLockedPersistsWaitingOrderAndKeepsPriceLock() {
        int payType = 2;
        double reallyPrice = 49.96;
        String lockKey = priceLockService.lockKey(payType, reallyPrice);
        priceLockService.tryLock(payType, reallyPrice);

        OrderCreationResult result = creationService.createAfterPriceLocked(
                "PAY-001", "ORDER-001", "user-1", payType,
                49.95, reallyPrice, "https://merchant.example/cb",
                "https://merchant.example/return", 1, "qr-content", 1782873030123L);

        assertTrue(result.isSuccess());
        assertNotNull(result.getPayOrder().getId());
        assertTrue(tmpPriceDao.findById(lockKey).isPresent());

        PayOrder saved = payOrderDao.findByPayId("PAY-001");
        assertNotNull(saved);
        assertEquals("ORDER-001", saved.getOrderId());
        assertEquals(PaymentState.WAITING, saved.getState());
        assertEquals(BigDecimal.valueOf(reallyPrice), saved.getReallyPrice());
    }

    @Test
    public void duplicatePayIdFailsAndReleasesJustLockedPrice() {
        PayOrder existing = new PayOrder();
        existing.setPayId("PAY-DUP");
        existing.setOrderId("ORDER-OLD");
        existing.setCreateDate(1782873030000L);
        existing.setPayDate(0);
        existing.setCloseDate(0);
        existing.setParam("old");
        existing.setType(2);
        existing.setPrice(10.00);
        existing.setReallyPrice(10.01);
        existing.setNotifyUrl("https://merchant.example/cb");
        existing.setReturnUrl("https://merchant.example/return");
        existing.setState(PaymentState.WAITING);
        existing.setIsAuto(1);
        existing.setPayUrl("old-qr");
        payOrderDao.save(existing);

        int payType = 2;
        double newlyLockedPrice = 10.02;
        String newLockKey = priceLockService.lockKey(payType, newlyLockedPrice);
        priceLockService.tryLock(payType, newlyLockedPrice);
        assertTrue(tmpPriceDao.findById(newLockKey).isPresent());

        OrderCreationResult result = creationService.createAfterPriceLocked(
                "PAY-DUP", "ORDER-NEW", "new", payType,
                10.00, newlyLockedPrice, "https://merchant.example/new-cb",
                "https://merchant.example/new-return", 1, "new-qr", 1782873031000L);

        assertFalse(result.isSuccess());
        assertFalse(tmpPriceDao.findById(newLockKey).isPresent());
        assertEquals(1, payOrderDao.findAll().size());
        assertEquals("ORDER-OLD", payOrderDao.findByPayId("PAY-DUP").getOrderId());
    }
}
