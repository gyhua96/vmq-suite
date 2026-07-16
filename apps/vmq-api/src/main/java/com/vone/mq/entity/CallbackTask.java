package com.vone.mq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class CallbackTask {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 128)
    private String payId;

    @Column(nullable = false, length = 1024)
    private String notifyUrl;

    @Column(nullable = false, length = 2048)
    private String query;

    @Column(nullable = false)
    private int state;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private long nextRetryTime;

    @Column(nullable = false)
    private long createTime;

    @Column(nullable = false)
    private long updateTime;

    @Column(nullable = false)
    private long claimUntil;

    @Column(length = 1024)
    private String lastResponse;

    @Column(length = 255)
    private String lastError;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getPayId() {
        return payId;
    }

    public void setPayId(String payId) {
        this.payId = payId;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getNextRetryTime() {
        return nextRetryTime;
    }

    public void setNextRetryTime(long nextRetryTime) {
        this.nextRetryTime = nextRetryTime;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public long getClaimUntil() {
        return claimUntil;
    }

    public void setClaimUntil(long claimUntil) {
        this.claimUntil = claimUntil;
    }

    public String getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(String lastResponse) {
        this.lastResponse = lastResponse;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
