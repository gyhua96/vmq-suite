package com.vone.mq.dao;

import com.vone.mq.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventDao extends JpaRepository<PaymentEvent, Long> {
    PaymentEvent findByEventKey(String eventKey);
}
