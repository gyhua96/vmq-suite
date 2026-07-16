package com.vone.mq.dao;

import com.vone.mq.entity.RequestNonce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RequestNonceDao extends JpaRepository<RequestNonce, Long> {
    boolean existsByScopeAndNonce(String scope, String nonce);

    @Modifying
    @Query("delete from RequestNonce n where n.expiresAt < ?1")
    int deleteExpired(long now);
}
