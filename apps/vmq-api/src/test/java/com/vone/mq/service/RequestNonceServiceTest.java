package com.vone.mq.service;

import com.vone.mq.dao.RequestNonceDao;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RequestNonceServiceTest {
    private RequestNonceService service;
    private RequestNonceDao dao;

    @Before
    public void setUp() {
        service = new RequestNonceService();
        dao = mock(RequestNonceDao.class);
        ReflectionTestUtils.setField(service, "requestNonceDao", dao);
    }

    @Test
    public void consumesNewNonce() {
        when(dao.existsByScopeAndNonce("merchant", "n-1")).thenReturn(false);
        assertTrue(service.consume("merchant", "n-1", 123L));
        verify(dao).saveAndFlush(any());
    }

    @Test
    public void rejectsExistingNonceBeforeInsert() {
        when(dao.existsByScopeAndNonce("merchant", "n-1")).thenReturn(true);
        assertFalse(service.consume("merchant", "n-1", 123L));
        verify(dao, never()).saveAndFlush(any());
    }

    @Test
    public void rejectsInvalidNonce() {
        assertFalse(service.consume("", "n-1", 123L));
        assertFalse(service.consume("merchant", "", 123L));
        verify(dao, never()).existsByScopeAndNonce(any(), any());
    }

    @Test
    public void deletesExpiredNonces() {
        service.deleteExpired();
        verify(dao).deleteExpired(any(Long.class));
    }
}
