package com.vone.mq.service;

import com.vone.mq.dao.PaymentEventDao;
import com.vone.mq.entity.PaymentEvent;
import org.junit.Before;
import org.junit.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PaymentEventServiceTest {
    private PaymentEventService service;
    private PaymentEventDao paymentEventDao;

    @Before
    public void setUp() {
        service = new PaymentEventService();
        paymentEventDao = mock(PaymentEventDao.class);
        ReflectionTestUtils.setField(service, "paymentEventDao", paymentEventDao);
    }

    @Test
    public void recordIfNewSavesNormalizedEventKey() {
        boolean result = service.recordIfNew(2, "49.90", 1782870000000L);

        assertTrue(result);
        verify(paymentEventDao).save(argThat(event ->
                "2-49.9-1782870000000".equals(event.getEventKey())
                        && event.getType() == 2
                        && new BigDecimal("49.90").compareTo(event.getPrice()) == 0
                        && event.getEventTime() == 1782870000000L
                        && event.getReceivedAt() > 0));
    }

    @Test
    public void recordIfNewReturnsFalseWhenEventAlreadyExists() {
        when(paymentEventDao.findByEventKey("2-49.9-1782870000000")).thenReturn(new PaymentEvent());

        boolean result = service.recordIfNew(2, "49.90", 1782870000000L);

        assertFalse(result);
        verify(paymentEventDao, never()).save(any(PaymentEvent.class));
    }

    @Test
    public void recordIfNewReturnsFalseWhenUniqueConstraintWinsRace() {
        when(paymentEventDao.save(any(PaymentEvent.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        boolean result = service.recordIfNew(2, "49.90", 1782870000000L);

        assertFalse(result);
    }

    @Test
    public void buildEventKeyNormalizesEquivalentMoneyText() {
        assertEquals("1-1-100", service.buildEventKey(1, "1.00", 100L));
    }
}
