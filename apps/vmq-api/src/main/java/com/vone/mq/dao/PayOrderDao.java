package com.vone.mq.dao;

import com.vone.mq.entity.PayOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;

public interface PayOrderDao  extends JpaRepository<PayOrder,Long>, JpaSpecificationExecutor {

    PayOrder findByPayId(String payId);
    PayOrder findByOrderId(String orderId);

    @Transactional
    @Modifying
    @Query(value = "update pay_order set state=?1 where id=?2", nativeQuery = true)
    int setState(int state,long id);

    @Transactional
    @Modifying
    @Query(value = "update pay_order set state=?1 where id=?2 and state=?3", nativeQuery = true)
    int setStateIfCurrent(int state,long id,int currentState);

    @Transactional
    @Modifying
    @Query(value = "delete from pay_order where id=?1 and state=?2", nativeQuery = true)
    int deleteByIdIfCurrent(long id,int currentState);

    @Transactional
    @Modifying
    @Query(value = "delete from pay_order where id=?1 and state<>?2", nativeQuery = true)
    int deleteByIdIfNotCurrent(long id,int currentState);

    @Transactional
    @Modifying
    @Query(value = "update pay_order set state=?1,pay_date=?2,close_date=?2 where id=?3 and state=?4", nativeQuery = true)
    int markPaidIfWaiting(int state,long payDate,long id,int currentState);

    @Transactional
    @Modifying
    @Query(value = "update pay_order set close_date=?1,state=?2 where id=?3 and state=?4", nativeQuery = true)
    int closeIfWaiting(long closeDate,int state,long id,int currentState);

    List<PayOrder> findAllByCreateDateLessThanAndState(long createDate,int state);

    PayOrder findByReallyPriceAndStateAndType(BigDecimal reallyPrice, int state, int type);

    PayOrder findByPayDate(Long payDate);

    @Query(value = "select count(*) from pay_order where create_date >= ?1 and create_date <= ?2", nativeQuery = true)
    int getTodayCount(long startDate,long endDate);

    @Query(value = "select count(*) from pay_order where create_date >= ?1 and create_date <= ?2 and state = ?3", nativeQuery = true)
    int getTodayCount(long startDate,long endDate,int state);

    @Query(value = "select sum(price) from pay_order where create_date >= ?1 and create_date <= ?2 and state = ?3", nativeQuery = true)
    BigDecimal getTodayCountMoney(long startDate, long endDate, int state);

    @Query(value = "select count(*) from pay_order", nativeQuery = true)
    int getCount();

    @Query(value = "select count(*) from pay_order where state = ?1", nativeQuery = true)
    int getCount(int state);

    @Query(value = "select sum(price) from pay_order where state = ?1", nativeQuery = true)
    BigDecimal getCountMoney(int state);


    @Transactional
    int deleteByState(int state);


    @Transactional
    @Modifying
    @Query(value = "delete from pay_order where create_date<?1 and state<>?2", nativeQuery = true)
    int deleteByAfterCreateDateAndStateNot(long date, int state);

}
