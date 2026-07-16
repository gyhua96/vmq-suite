package com.vone.mq.service;

import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.utils.MoneyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

@Service
public class PriceLockService {
    @Autowired
    private TmpPriceDao tmpPriceDao;

    public int tryLock(int payType, BigDecimal amount) {
        try {
            return tmpPriceDao.checkPrice(lockKey(payType, amount));
        } catch (DataIntegrityViolationException e) {
            return 0;
        }
    }

    /** @deprecated use BigDecimal to avoid floating-point lock keys. */
    @Deprecated
    public int tryLock(int payType, double amount) {
        return tryLock(payType, BigDecimal.valueOf(amount));
    }

    public void release(int payType, String amount) {
        tmpPriceDao.delprice(lockKey(payType, amount));
    }

    public void release(int payType, BigDecimal amount) {
        tmpPriceDao.delprice(lockKey(payType, amount));
    }

    /** @deprecated use BigDecimal to avoid floating-point lock keys. */
    @Deprecated
    public void release(int payType, double amount) {
        release(payType, BigDecimal.valueOf(amount));
    }

    public String lockKey(int payType, BigDecimal amount) {
        return payType + "-" + MoneyUtil.normalize(amount);
    }

    /** @deprecated use BigDecimal to avoid floating-point lock keys. */
    @Deprecated
    public String lockKey(int payType, double amount) {
        return lockKey(payType, BigDecimal.valueOf(amount));
    }

    public String lockKey(int payType, String amount) {
        return payType + "-" + MoneyUtil.normalize(amount);
    }
}
