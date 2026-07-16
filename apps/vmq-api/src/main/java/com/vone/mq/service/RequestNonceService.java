package com.vone.mq.service;

import com.vone.mq.dao.RequestNonceDao;
import com.vone.mq.entity.RequestNonce;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RequestNonceService {
    @Autowired
    private RequestNonceDao requestNonceDao;

    @Transactional
    public boolean consume(String scope, String nonce, long expiresAt) {
        if (!StringUtils.hasText(scope) || !StringUtils.hasText(nonce) || nonce.length() > 128) {
            return false;
        }
        if (requestNonceDao.existsByScopeAndNonce(scope, nonce)) {
            return false;
        }
        RequestNonce requestNonce = new RequestNonce();
        requestNonce.setScope(scope);
        requestNonce.setNonce(nonce);
        requestNonce.setExpiresAt(expiresAt);
        requestNonce.setCreatedAt(System.currentTimeMillis());
        try {
            requestNonceDao.saveAndFlush(requestNonce);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Transactional
    @Scheduled(fixedDelay = 300000)
    public void deleteExpired() {
        requestNonceDao.deleteExpired(System.currentTimeMillis());
    }
}
