package com.vone.mq.dao;

import com.vone.mq.entity.CallbackTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CallbackTaskDao extends JpaRepository<CallbackTask, Long> {
    CallbackTask findTopByOrderIdOrderByIdDesc(Long orderId);

    List<CallbackTask> findTop20ByStateAndNextRetryTimeLessThanEqualOrderByNextRetryTimeAsc(int state,
                                                                                              long nextRetryTime);

    @Query("select t from CallbackTask t where (t.state = 2 and t.nextRetryTime <= ?1) "
            + "or (t.state = 4 and t.claimUntil <= ?1) order by t.nextRetryTime asc")
    List<CallbackTask> findDueTasks(long now);

    @Modifying
    @Transactional
    @Query("update CallbackTask t set t.state = 4, t.claimUntil = ?2, t.updateTime = ?3 "
            + "where t.id = ?1 and ((t.state = 2 and t.nextRetryTime <= ?3) "
            + "or (t.state = 4 and t.claimUntil <= ?3))")
    int claim(Long id, long claimUntil, long now);
}
