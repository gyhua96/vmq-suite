package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.PageRes;
import com.vone.mq.entity.PayOrder;
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
import java.text.NumberFormat;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminOrderService {
    static final String ORDER_NOT_FOUND_MESSAGE = "\u8ba2\u5355\u4e0d\u5b58\u5728";

    @Autowired
    private PayOrderDao payOrderDao;
    @Autowired
    private CallbackTaskService callbackTaskService;
    @Autowired
    private PaymentOrderStateService paymentOrderStateService;
    @Autowired
    private SettingAccessService settingAccessService;

    public PageRes getOrders(Integer page, Integer limit, Integer type, Integer state) {
        Pageable pageable = AdminPageSupport.byIdDesc(page, limit);
        Specification<PayOrder> specification = new Specification<PayOrder>() {
            @Override
            public Predicate toPredicate(Root<PayOrder> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder cb) {
                List<Predicate> list = new ArrayList<Predicate>();
                if (type != null) {
                    list.add(cb.equal(root.get("type").as(int.class), type));
                }
                if (state != null) {
                    list.add(cb.equal(root.get("state").as(int.class), state));
                }
                return cb.and(list.toArray(new Predicate[list.size()]));
            }
        };
        Page<PayOrder> payOrders = payOrderDao.findAll(specification, pageable);
        return PageRes.success(payOrders.getTotalElements(), payOrders.getContent());
    }

    public CommonRes resendCallback(Integer id) {
        PayOrder payOrder = id == null ? null : payOrderDao.findById(id.longValue()).orElse(null);
        if (payOrder == null) {
            return ResUtil.error(ORDER_NOT_FOUND_MESSAGE);
        }
        String key = settingAccessService.communicationKey();
        CallbackResult callbackResult = callbackTaskService.sendNowAndRecord(payOrder, settingAccessService.defaultNotifyUrl(), key);
        if (callbackResult.isSuccess()) {
            paymentOrderStateService.markPaidByManualCallback(payOrder);
            return ResUtil.success();
        }
        return ResUtil.error(-2, callbackResult.getResponse());
    }

    public CommonRes getMain() {
        Calendar currentDate = new GregorianCalendar();
        currentDate.set(Calendar.HOUR_OF_DAY, 0);
        currentDate.set(Calendar.MINUTE, 0);
        currentDate.set(Calendar.SECOND, 0);
        Date tmp = currentDate.getTime();
        long startDate = tmp.getTime();

        currentDate = new GregorianCalendar();
        currentDate.set(Calendar.HOUR_OF_DAY, 23);
        currentDate.set(Calendar.MINUTE, 59);
        currentDate.set(Calendar.SECOND, 59);
        tmp = currentDate.getTime();
        long endDate = tmp.getTime();

        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(2);

        int todayOrder = payOrderDao.getTodayCount(startDate, endDate);
        int todaySuccessOrder = payOrderDao.getTodayCount(startDate, endDate, PaymentState.PAID)
                + payOrderDao.getTodayCount(startDate, endDate, PaymentState.CALLBACK_FAILED);
        int todayCloseOrder = payOrderDao.getTodayCount(startDate, endDate, PaymentState.CLOSED);
        BigDecimal todayMoney = sumTodayMoney(startDate, endDate, PaymentState.PAID)
                .add(sumTodayMoney(startDate, endDate, PaymentState.CALLBACK_FAILED));

        int countOrder = payOrderDao.getCount(PaymentState.PAID)
                + payOrderDao.getCount(PaymentState.CALLBACK_FAILED);
        BigDecimal countMoney = sumCountMoney(PaymentState.PAID)
                .add(sumCountMoney(PaymentState.CALLBACK_FAILED));

        Map<String, String> map = new HashMap<>();
        map.put("todayOrder", String.valueOf(todayOrder));
        map.put("todaySuccessOrder", String.valueOf(todaySuccessOrder));
        map.put("todayCloseOrder", String.valueOf(todayCloseOrder));
        map.put("todayMoney", nf.format(todayMoney));
        map.put("countOrder", String.valueOf(countOrder));
        map.put("countMoney", nf.format(countMoney));
        return ResUtil.success(map);
    }

    public CommonRes deleteOrder(Long id) {
        PayOrder payOrder = id == null ? null : payOrderDao.findById(id).orElse(null);
        if (payOrder == null) {
            return ResUtil.error(ORDER_NOT_FOUND_MESSAGE);
        }
        paymentOrderStateService.deleteOrder(payOrder);
        return ResUtil.success();
    }

    public CommonRes deleteClosedOrders() {
        payOrderDao.deleteByState(PaymentState.CLOSED);
        return ResUtil.success();
    }

    public CommonRes deleteOrdersBefore7Days() {
        payOrderDao.deleteByAfterCreateDateAndStateNot(new Date().getTime() - 7 * 86400 * 1000, PaymentState.WAITING);
        return ResUtil.success();
    }

    private BigDecimal sumTodayMoney(long startDate, long endDate, int state) {
        try {
            BigDecimal value = payOrderDao.getTodayCountMoney(startDate, endDate, state);
            return value == null ? BigDecimal.ZERO : value;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal sumCountMoney(int state) {
        try {
            BigDecimal value = payOrderDao.getCountMoney(state);
            return value == null ? BigDecimal.ZERO : value;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
