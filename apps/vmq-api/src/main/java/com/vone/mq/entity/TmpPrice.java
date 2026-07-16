package com.vone.mq.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class TmpPrice {
    @Id
    private String price;

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }
}
