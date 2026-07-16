package com.vone.mq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;

@Entity
public class PayQrcode {
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Long id;

    private String payUrl;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    private int type;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
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

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
