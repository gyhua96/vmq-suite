package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.entity.PayOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOrderStateService {
    @Autowired
    private PayOrderDao payOrderDao;
    @Autowired
    private PriceLockService priceLockService;

    @Transactional
    public boolean closeWaitingOrder(PayOrder payOrder, long closeDate) {
        int updated = payOrderDao.closeIfWaiting(closeDate, PaymentState.CLOSED, payOrder.getId(), PaymentState.WAITING);
        if (updated == 0) {
            return false;
        }
        priceLockService.release(payOrder.getType(), payOrder.getReallyPrice());
        payOrder.setCloseDate(closeDate);
        payOrder.setState(PaymentState.CLOSED);
        return true;
    }

    @Transactional
    public boolean markPaidFromAppPush(PayOrder payOrder, String pushedPrice, long payDate) {
        int updated = payOrderDao.markPaidIfWaiting(PaymentState.PAID, payDate, payOrder.getId(), PaymentState.WAITING);
        if (updated == 0) {
            return false;
        }
        priceLockService.release(payOrder.getType(), pushedPrice);
        payOrder.setState(PaymentState.PAID);
        payOrder.setPayDate(payDate);
        payOrder.setCloseDate(payDate);
        return true;
    }

    @Transactional
    public boolean markCallbackFailed(PayOrder payOrder) {
        int updated = payOrderDao.setStateIfCurrent(PaymentState.CALLBACK_FAILED, payOrder.getId(), PaymentState.PAID);
        if (updated == 0) {
            return false;
        }
        payOrder.setState(PaymentState.CALLBACK_FAILED);
        return true;
    }

    @Transactional
    public boolean markCallbackSucceeded(PayOrder payOrder) {
        int updated = payOrderDao.setStateIfCurrent(PaymentState.PAID, payOrder.getId(), PaymentState.CALLBACK_FAILED);
        if (updated == 0) {
            return false;
        }
        payOrder.setState(PaymentState.PAID);
        return true;
    }

    @Transactional
    public void markPaidByManualCallback(PayOrder payOrder) {
        int updatedWaiting = payOrderDao.setStateIfCurrent(PaymentState.PAID, payOrder.getId(), PaymentState.WAITING);
        if (updatedWaiting > 0) {
            priceLockService.release(payOrder.getType(), payOrder.getReallyPrice());
            payOrder.setState(PaymentState.PAID);
            return;
        }
        int updatedCallbackFailed = payOrderDao.setStateIfCurrent(PaymentState.PAID, payOrder.getId(), PaymentState.CALLBACK_FAILED);
        if (updatedCallbackFailed > 0) {
            payOrder.setState(PaymentState.PAID);
        }
    }

    @Transactional
    public void deleteOrder(PayOrder payOrder) {
        int deletedWaiting = payOrderDao.deleteByIdIfCurrent(payOrder.getId(), PaymentState.WAITING);
        if (deletedWaiting > 0) {
            priceLockService.release(payOrder.getType(), payOrder.getReallyPrice());
            return;
        }
        payOrderDao.deleteByIdIfNotCurrent(payOrder.getId(), PaymentState.WAITING);
    }
}
