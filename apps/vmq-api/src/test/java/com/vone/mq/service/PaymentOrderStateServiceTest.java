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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PaymentOrderStateServiceTest {
    private PaymentOrderStateService stateService;
    private PayOrderDao payOrderDao;
    private PriceLockService priceLockService;

    @Before
    public void setUp() {
        stateService = new PaymentOrderStateService();
        payOrderDao = mock(PayOrderDao.class);
        priceLockService = mock(PriceLockService.class);
        ReflectionTestUtils.setField(stateService, "payOrderDao", payOrderDao);
        ReflectionTestUtils.setField(stateService, "priceLockService", priceLockService);
    }

    @Test
    public void closeWaitingOrderReleasesLockAndMarksClosed() {
        PayOrder payOrder = order(PaymentState.WAITING);
        when(payOrderDao.closeIfWaiting(1234L, PaymentState.CLOSED, 99L, PaymentState.WAITING)).thenReturn(1);

        boolean result = stateService.closeWaitingOrder(payOrder, 1234L);

        assertTrue(result);
        verify(priceLockService).release(2, BigDecimal.valueOf(49.95));
        assertEquals(PaymentState.CLOSED, payOrder.getState());
        assertEquals(1234L, payOrder.getCloseDate());
        verify(payOrderDao).closeIfWaiting(1234L, PaymentState.CLOSED, 99L, PaymentState.WAITING);
    }

    @Test
    public void closeWaitingOrderDoesNotReleaseLockWhenStateWasAlreadyChanged() {
        PayOrder payOrder = order(PaymentState.WAITING);
        when(payOrderDao.closeIfWaiting(1234L, PaymentState.CLOSED, 99L, PaymentState.WAITING)).thenReturn(0);

        boolean result = stateService.closeWaitingOrder(payOrder, 1234L);

        assertFalse(result);
        verify(priceLockService, never()).release(2, 49.95);
        assertEquals(PaymentState.WAITING, payOrder.getState());
    }

    @Test
    public void markPaidFromAppPushReleasesPushedPriceAndSavesDates() {
        PayOrder payOrder = order(PaymentState.WAITING);
        when(payOrderDao.markPaidIfWaiting(PaymentState.PAID, 5678L, 99L, PaymentState.WAITING)).thenReturn(1);

        boolean result = stateService.markPaidFromAppPush(payOrder, "49.95", 5678L);

        assertTrue(result);
        verify(priceLockService).release(2, "49.95");
        assertEquals(PaymentState.PAID, payOrder.getState());
        assertEquals(5678L, payOrder.getPayDate());
        assertEquals(5678L, payOrder.getCloseDate());
        verify(payOrderDao).markPaidIfWaiting(PaymentState.PAID, 5678L, 99L, PaymentState.WAITING);
    }

    @Test
    public void markPaidFromAppPushDoesNotReleaseLockWhenStateWasAlreadyChanged() {
        PayOrder payOrder = order(PaymentState.WAITING);
        when(payOrderDao.markPaidIfWaiting(PaymentState.PAID, 5678L, 99L, PaymentState.WAITING)).thenReturn(0);

        boolean result = stateService.markPaidFromAppPush(payOrder, "49.95", 5678L);

        assertFalse(result);
        verify(priceLockService, never()).release(2, "49.95");
        assertEquals(PaymentState.WAITING, payOrder.getState());
    }

    @Test
    public void callbackFailureOnlyMarksDatabasePaidOrderFailed() {
        PayOrder payOrder = order(PaymentState.PAID);
        when(payOrderDao.setStateIfCurrent(PaymentState.CALLBACK_FAILED, 99L, PaymentState.PAID)).thenReturn(1);

        boolean result = stateService.markCallbackFailed(payOrder);

        assertTrue(result);
        assertEquals(PaymentState.CALLBACK_FAILED, payOrder.getState());
        verify(payOrderDao).setStateIfCurrent(PaymentState.CALLBACK_FAILED, 99L, PaymentState.PAID);
    }

    @Test
    public void callbackFailureDoesNotOverwriteCurrentNonPaidDatabaseState() {
        PayOrder stalePaid = order(PaymentState.PAID);
        when(payOrderDao.setStateIfCurrent(PaymentState.CALLBACK_FAILED, 99L, PaymentState.PAID)).thenReturn(0);

        boolean result = stateService.markCallbackFailed(stalePaid);

        assertFalse(result);
        assertEquals(PaymentState.PAID, stalePaid.getState());
        verify(payOrderDao).setStateIfCurrent(PaymentState.CALLBACK_FAILED, 99L, PaymentState.PAID);
    }

    @Test
    public void callbackSuccessOnlyMarksCallbackFailedOrderPaid() {
        PayOrder payOrder = order(PaymentState.CALLBACK_FAILED);
        when(payOrderDao.setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.CALLBACK_FAILED)).thenReturn(1);

        boolean result = stateService.markCallbackSucceeded(payOrder);

        assertTrue(result);
        assertEquals(PaymentState.PAID, payOrder.getState());
        verify(payOrderDao).setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.CALLBACK_FAILED);
    }

    @Test
    public void callbackSuccessDoesNotOverwriteCurrentNonFailedDatabaseState() {
        PayOrder staleFailed = order(PaymentState.CALLBACK_FAILED);
        when(payOrderDao.setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.CALLBACK_FAILED)).thenReturn(0);

        boolean result = stateService.markCallbackSucceeded(staleFailed);

        assertFalse(result);
        assertEquals(PaymentState.CALLBACK_FAILED, staleFailed.getState());
        verify(payOrderDao).setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.CALLBACK_FAILED);
    }

    @Test
    public void manualCallbackMarksCallbackFailedOrderPaidWithoutReleasingLock() {
        PayOrder payOrder = order(PaymentState.CALLBACK_FAILED);
        when(payOrderDao.setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.WAITING)).thenReturn(0);
        when(payOrderDao.setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.CALLBACK_FAILED)).thenReturn(1);

        stateService.markPaidByManualCallback(payOrder);

        verify(priceLockService, never()).release(2, 49.95);
        verify(payOrderDao).setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.WAITING);
        verify(payOrderDao).setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.CALLBACK_FAILED);
        assertEquals(PaymentState.PAID, payOrder.getState());
    }

    @Test
    public void manualCallbackReleasesLockOnlyWhenDatabaseOrderIsStillWaiting() {
        PayOrder payOrder = order(PaymentState.WAITING);
        when(payOrderDao.setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.WAITING)).thenReturn(1);

        stateService.markPaidByManualCallback(payOrder);

        verify(priceLockService).release(2, BigDecimal.valueOf(49.95));
        verify(payOrderDao).setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.WAITING);
        verify(payOrderDao, never()).setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.CALLBACK_FAILED);
        assertEquals(PaymentState.PAID, payOrder.getState());
    }

    @Test
    public void manualCallbackDoesNotReleaseLockWhenDatabaseStateAlreadyChanged() {
        PayOrder staleWaiting = order(PaymentState.WAITING);
        when(payOrderDao.setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.WAITING)).thenReturn(0);
        when(payOrderDao.setStateIfCurrent(PaymentState.PAID, 99L, PaymentState.CALLBACK_FAILED)).thenReturn(0);

        stateService.markPaidByManualCallback(staleWaiting);

        verify(priceLockService, never()).release(2, 49.95);
        assertEquals(PaymentState.WAITING, staleWaiting.getState());
    }

    @Test
    public void deleteOrderReleasesLockOnlyWhenDatabaseOrderIsStillWaiting() {
        PayOrder payOrder = order(PaymentState.WAITING);
        when(payOrderDao.deleteByIdIfCurrent(99L, PaymentState.WAITING)).thenReturn(1);

        stateService.deleteOrder(payOrder);

        verify(priceLockService).release(2, BigDecimal.valueOf(49.95));
        verify(payOrderDao).deleteByIdIfCurrent(99L, PaymentState.WAITING);
        verify(payOrderDao, never()).deleteByIdIfNotCurrent(99L, PaymentState.WAITING);
    }

    @Test
    public void deleteOrderDoesNotReleaseLockWhenDatabaseStateAlreadyChanged() {
        PayOrder staleWaiting = order(PaymentState.WAITING);
        when(payOrderDao.deleteByIdIfCurrent(99L, PaymentState.WAITING)).thenReturn(0);

        stateService.deleteOrder(staleWaiting);

        verify(priceLockService, never()).release(2, 49.95);
        verify(payOrderDao).deleteByIdIfCurrent(99L, PaymentState.WAITING);
        verify(payOrderDao).deleteByIdIfNotCurrent(99L, PaymentState.WAITING);
    }

    private PayOrder order(int state) {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(99L);
        payOrder.setType(2);
        payOrder.setReallyPrice(49.95);
        payOrder.setState(state);
        return payOrder;
    }
}
