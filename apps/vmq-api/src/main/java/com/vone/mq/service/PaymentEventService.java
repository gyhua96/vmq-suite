package com.vone.mq.service;

import com.vone.mq.dao.PaymentEventDao;
import com.vone.mq.entity.PaymentEvent;
import com.vone.mq.utils.MoneyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PaymentEventService {
    @Autowired
    private PaymentEventDao paymentEventDao;

    @Transactional
    public boolean recordIfNew(int type, BigDecimal price, long eventTime) {
        String eventKey = buildEventKey(type, price, eventTime);
        if (paymentEventDao.findByEventKey(eventKey) != null) {
            return false;
        }
        PaymentEvent event = new PaymentEvent();
        event.setEventKey(eventKey);
        event.setType(type);
        event.setPrice(MoneyUtil.requirePositive(price));
        event.setEventTime(eventTime);
        event.setReceivedAt(System.currentTimeMillis());
        try {
            paymentEventDao.save(event);
            return true;
        } catch (DataIntegrityViolationException e) {
            // A concurrent insert winning the unique event-key constraint is a duplicate.
            return false;
        }
    }

    @Transactional
    public PaymentEvent accept(int type, BigDecimal price, long eventTime) {
        String eventKey = buildEventKey(type, price, eventTime);
        PaymentEvent existing = paymentEventDao.findByEventKey(eventKey);
        if (existing != null) {
            return existing;
        }

        PaymentEvent event = new PaymentEvent();
        event.setEventKey(eventKey);
        event.setType(type);
        event.setPrice(MoneyUtil.requirePositive(price));
        event.setEventTime(eventTime);
        event.setReceivedAt(System.currentTimeMillis());
        try {
            return paymentEventDao.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            return paymentEventDao.findByEventKey(eventKey);
        }
    }

    @Transactional
    public void markProcessed(PaymentEvent event, Long orderId) {
        event.setMatchedOrderId(orderId);
        event.setState(PaymentEvent.PROCESSED);
        paymentEventDao.save(event);
    }

    /** @deprecated use BigDecimal to preserve event amount precision. */
    @Deprecated
    public boolean recordIfNew(int type, String price, long eventTime) {
        return recordIfNew(type, MoneyUtil.parsePositive(price), eventTime);
    }

    String buildEventKey(int type, BigDecimal price, long eventTime) {
        return type + "-" + MoneyUtil.normalize(price) + "-" + eventTime;
    }

    /** @deprecated use the BigDecimal overload. */
    @Deprecated
    String buildEventKey(int type, String price, long eventTime) {
        return buildEventKey(type, MoneyUtil.parsePositive(price), eventTime);
    }
}
