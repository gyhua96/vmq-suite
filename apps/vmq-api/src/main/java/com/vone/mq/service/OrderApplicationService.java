package com.vone.mq.service;

import com.vone.mq.dao.PayQrcodeDao;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.PayQrcode;
import com.vone.mq.utils.MoneyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderApplicationService {
    @Autowired
    private PayQrcodeDao payQrcodeDao;
    @Autowired
    private PriceLockService priceLockService;
    @Autowired
    private PaymentOrderCreationService paymentOrderCreationService;
    @Autowired
    private SettingAccessService settingAccessService;
    @Value("${vmq.price-allocation.max-attempts:100}")
    private int maxAllocationAttempts = 100;

    public OrderApplicationResult createOrder(String payId, String param, Integer type, String price,
                                              String notifyUrl, String returnUrl) {
        BigDecimal priceD = MoneyUtil.parsePositive(price);
        String orderId = generateOrderId();
        BigDecimal reallyPrice = allocateReallyPrice(type, priceD);
        if (reallyPrice == null) {
            return OrderApplicationResult.failure("所有金额均被占用");
        }

        // 先尝试从专属金额二维码表中查找是否存在该金额的码
        int isAuto = 1;
        String payUrl = "";
        boolean retainedLock = true;
        try {
            PayQrcode payQrcode = payQrcodeDao.findByPriceAndType(reallyPrice, type);
        if (payQrcode != null) {
            payUrl = payQrcode.getPayUrl();
            isAuto = 0;
        } else {
            // 如果专属金额码未匹配到，则降级为读取系统默认全局码 (自动微调金额模式)
            payUrl = settingAccessService.payUrlForType(type);
                if (payUrl == null || payUrl.isEmpty()) {
                return OrderApplicationResult.failure("请先在V免签后台配置默认收款码或上传对应金额的二维码");
            }
        }

            OrderCreationResult orderCreationResult;
            try {
                orderCreationResult = paymentOrderCreationService.createAfterPriceLocked(
                        payId, orderId, param, type, priceD, reallyPrice, notifyUrl, returnUrl, isAuto, payUrl,
                        System.currentTimeMillis());
            } catch (DataIntegrityViolationException e) {
                return OrderApplicationResult.failure("商户订单号或云端订单号已存在，请重试");
            }
        if (!orderCreationResult.isSuccess()) {
            return OrderApplicationResult.failure(orderCreationResult.getErrorMessage());
        }
            retainedLock = false;
        PayOrder payOrder = orderCreationResult.getPayOrder();
        return OrderApplicationResult.success(payOrder, payId, orderId, type, priceD, reallyPrice, payUrl, isAuto,
                settingAccessService.closeMinutes());
        } finally {
            if (retainedLock) {
                priceLockService.release(type, reallyPrice);
            }
        }
    }

    private BigDecimal allocateReallyPrice(int type, BigDecimal price) {
        int payQf = settingAccessService.payQf();
        BigDecimal reallyPrice = price;
        for (int attempt = 0; attempt < maxAllocationAttempts; attempt++) {
            int row = priceLockService.tryLock(type, reallyPrice);
            if (row != 0) {
                return reallyPrice;
            }
            if (payQf == 1) {
                reallyPrice = MoneyUtil.addCent(reallyPrice);
            } else {
                reallyPrice = MoneyUtil.subtractCent(reallyPrice);
            }
            if (reallyPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
        }
        return null;
    }

    private String generateOrderId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
