package com.vone.mq.service;

import com.vone.mq.dao.PayQrcodeDao;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.PageRes;
import com.vone.mq.entity.PayQrcode;
import com.vone.mq.utils.MoneyUtil;
import com.vone.mq.utils.ResUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminQrcodeService {
    static final String QRCODE_CONTENT_REQUIRED_MESSAGE = "\u8bf7\u4f20\u5165\u4e8c\u7ef4\u7801\u5185\u5bb9";
    static final String QRCODE_NOT_FOUND_MESSAGE = "\u4e8c\u7ef4\u7801\u4e0d\u5b58\u5728";
    static final String INVALID_PAY_TYPE_MESSAGE = "\u652f\u4ed8\u65b9\u5f0f\u9519\u8bef=>1|\u5fae\u4fe1 2|\u652f\u4ed8\u5b9d";

    @Autowired
    private PayQrcodeDao payQrcodeDao;

    public PageRes getPayQrcodes(Integer page, Integer limit, Integer type) {
        Pageable pageable = AdminPageSupport.byIdDesc(page, limit);
        Specification<PayQrcode> specification = new Specification<PayQrcode>() {
            @Override
            public Predicate toPredicate(Root<PayQrcode> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder cb) {
                List<Predicate> list = new ArrayList<Predicate>();
                if (type != null) {
                    list.add(cb.equal(root.get("type").as(int.class), type));
                }
                return cb.and(list.toArray(new Predicate[list.size()]));
            }
        };
        Page<PayQrcode> payQrcodes = payQrcodeDao.findAll(specification, pageable);
        return PageRes.success(payQrcodes.getTotalElements(), payQrcodes.getContent());
    }

    public CommonRes addPayQrcode(PayQrcode payQrcode) {
        CommonRes validation = validatePayQrcode(payQrcode);
        if (validation != null) {
            return validation;
        }
        payQrcodeDao.save(payQrcode);
        return ResUtil.success();
    }

    public CommonRes updatePayQrcode(PayQrcode payQrcode) {
        if (payQrcode == null || payQrcode.getId() == null || !payQrcodeDao.existsById(payQrcode.getId())) {
            return ResUtil.error(QRCODE_NOT_FOUND_MESSAGE);
        }
        CommonRes validation = validatePayQrcode(payQrcode);
        if (validation != null) {
            return validation;
        }
        payQrcodeDao.save(payQrcode);
        return ResUtil.success();
    }

    private CommonRes validatePayQrcode(PayQrcode payQrcode) {
        if (payQrcode == null || payQrcode.getPayUrl() == null) {
            return ResUtil.error(QRCODE_CONTENT_REQUIRED_MESSAGE);
        }
        try {
            MoneyUtil.requirePositive(payQrcode.getPrice());
        } catch (IllegalArgumentException e) {
            return ResUtil.error(e.getMessage());
        }
        if (payQrcode.getType() != 1 && payQrcode.getType() != 2) {
            return ResUtil.error(INVALID_PAY_TYPE_MESSAGE);
        }
        return null;
    }

    public CommonRes deletePayQrcode(Long id) {
        if (id == null || !payQrcodeDao.existsById(id)) {
            return ResUtil.error(QRCODE_NOT_FOUND_MESSAGE);
        }
        payQrcodeDao.deleteById(id);
        return ResUtil.success();
    }
}
