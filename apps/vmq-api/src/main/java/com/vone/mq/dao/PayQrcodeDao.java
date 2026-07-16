package com.vone.mq.dao;

import com.vone.mq.entity.PayQrcode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.math.BigDecimal;

public interface PayQrcodeDao  extends JpaRepository<PayQrcode,Long>, JpaSpecificationExecutor {

    PayQrcode findByPriceAndType(BigDecimal price, int type);
}
