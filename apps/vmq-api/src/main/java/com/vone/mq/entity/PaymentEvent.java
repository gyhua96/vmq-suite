package com.vone.mq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_event_event_key", columnNames = "event_key")
})
public class PaymentEvent {
    public static final int RECEIVED = 0;
    public static final int PROCESSED = 1;
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "event_key", nullable = false, length = 128)
    private String eventKey;

    @Column(nullable = false)
    private int type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private long eventTime;

    @Column(nullable = false)
    private long receivedAt;

    @Column(nullable = false)
    private int state = RECEIVED;

    private Long matchedOrderId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /** @deprecated use the BigDecimal setter. */
    @Deprecated
    public void setPrice(double price) {
        this.price = BigDecimal.valueOf(price);
    }

    public long getEventTime() {
        return eventTime;
    }

    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
    }

    public long getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public Long getMatchedOrderId() {
        return matchedOrderId;
    }

    public void setMatchedOrderId(Long matchedOrderId) {
        this.matchedOrderId = matchedOrderId;
    }
}
