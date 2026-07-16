package com.vone.mq.service;

import com.vone.mq.dao.CallbackTaskDao;
import com.vone.mq.domain.CallbackTaskState;
import com.vone.mq.entity.CallbackTask;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.Setting;
import com.vone.mq.utils.UrlSecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CallbackTaskService {
    private static final long RETRY_DELAY_MILLIS = 60 * 1000L;
    private static final long CLAIM_LEASE_MILLIS = 30 * 1000L;
    private static final int MAX_RETRY_COUNT = 10;

    @Autowired
    private CallbackTaskDao callbackTaskDao;
    @Autowired
    private CallbackService callbackService;
    @Autowired
    private CallbackPayloadBuilder callbackPayloadBuilder;
    @Autowired
    private CallbackHttpClient callbackHttpClient;
    @Autowired
    private PaymentOrderStateService paymentOrderStateService;

    @Transactional
    public CallbackResult sendNowAndRecord(PayOrder payOrder, Optional<Setting> defaultNotifyUrl, String key) {
        String notifyUrl = resolveNotifyUrl(payOrder, defaultNotifyUrl);
        String query = callbackPayloadBuilder.buildQuery(payOrder, key);
        CallbackTask task = findOrCreateTask(payOrder, notifyUrl, query, System.currentTimeMillis());
        CallbackResult result = callbackService.sendNotify(payOrder, defaultNotifyUrl, key);
        applyResult(task, result, System.currentTimeMillis());
        callbackTaskDao.save(task);
        return result;
    }

    @Transactional
    public CallbackTask enqueue(PayOrder payOrder, Optional<Setting> defaultNotifyUrl, String key) {
        long now = System.currentTimeMillis();
        String notifyUrl = resolveNotifyUrl(payOrder, defaultNotifyUrl);
        String query = callbackPayloadBuilder.buildQuery(payOrder, key);
        CallbackTask task = findOrCreateTask(payOrder, notifyUrl, query, now);
        task.setState(CallbackTaskState.RETRY_WAITING);
        task.setNextRetryTime(now);
        task.setUpdateTime(now);
        task.setLastResponse(null);
        task.setLastError(null);
        return callbackTaskDao.save(task);
    }

    public int retryDueTasks(long now) {
        int successCount = 0;
        java.util.List<CallbackTask> dueTasks = callbackTaskDao.findDueTasks(now);
        // The legacy repository method is retained as a compatibility fallback for
        // deployments upgrading without the V3 DAO query yet.
        if (dueTasks == null || dueTasks.isEmpty()) {
            dueTasks = callbackTaskDao.findTop20ByStateAndNextRetryTimeLessThanEqualOrderByNextRetryTimeAsc(
                    CallbackTaskState.RETRY_WAITING, now);
        }
        for (CallbackTask task : dueTasks) {
            if (task.getId() != null && callbackTaskDao.claim(task.getId(), now + CLAIM_LEASE_MILLIS, now) == 0) {
                continue;
            }
            // The conditional UPDATE already owns this row for the lease. Reusing the
            // loaded entity avoids a second read and keeps the worker safe under tests
            // and high-latency replicas.
            task.setState(CallbackTaskState.CLAIMED);
            task.setClaimUntil(now + CLAIM_LEASE_MILLIS);
            CallbackResult result = sendTask(task);
            applyResult(task, result, now);
            syncOrderCallbackState(task);
            callbackTaskDao.save(task);
            if (result.isSuccess()) {
                successCount++;
            }
        }
        return successCount;
    }

    private CallbackTask findOrCreateTask(PayOrder payOrder, String notifyUrl, String query, long now) {
        CallbackTask task = callbackTaskDao.findTopByOrderIdOrderByIdDesc(payOrder.getId());
        if (task != null) {
            task.setNotifyUrl(notifyUrl);
            task.setQuery(query);
            return task;
        }

        task = new CallbackTask();
        task.setOrderId(payOrder.getId());
        task.setPayId(payOrder.getPayId());
        task.setNotifyUrl(notifyUrl);
        task.setQuery(query);
        task.setState(CallbackTaskState.PENDING);
        task.setRetryCount(0);
        task.setNextRetryTime(now);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        return task;
    }

    private String resolveNotifyUrl(PayOrder payOrder, Optional<Setting> defaultNotifyUrl) {
        if (payOrder.getNotifyUrl() != null && !payOrder.getNotifyUrl().equals("")) {
            return payOrder.getNotifyUrl();
        }
        return defaultNotifyUrl.map(Setting::getVvalue).orElse("");
    }

    private void applyResult(CallbackTask task, CallbackResult result, long now) {
        task.setUpdateTime(now);
        task.setLastResponse(truncate(result.getResponse(), 1024));
        task.setLastError(truncate(result.getErrorMessage(), 255));
        if (result.isSuccess()) {
            task.setState(CallbackTaskState.SUCCESS);
            task.setNextRetryTime(0);
            return;
        }
        task.setRetryCount(task.getRetryCount() + 1);
        if (task.getRetryCount() >= MAX_RETRY_COUNT) {
            task.setState(CallbackTaskState.FINAL_FAILED);
            task.setNextRetryTime(0);
            return;
        }
        task.setState(CallbackTaskState.RETRY_WAITING);
        task.setNextRetryTime(now + RETRY_DELAY_MILLIS);
    }

    private CallbackResult sendTask(CallbackTask task) {
        if (!UrlSecurityUtil.isSafePublicCallbackUrl(task.getNotifyUrl())) {
            return CallbackResult.failure(null, "异步通知地址不安全或不允许");
        }
        String response = callbackHttpClient.sendGet(task.getNotifyUrl(), task.getQuery());
        if (CallbackResponseMatcher.isSuccess(response)) {
            return CallbackResult.success(response);
        }
        return CallbackResult.failure(response, "通知异步地址失败");
    }

    private void syncOrderCallbackState(CallbackTask task) {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(task.getOrderId());
        if (task.getState() == CallbackTaskState.SUCCESS) {
            paymentOrderStateService.markCallbackSucceeded(payOrder);
            return;
        }
        if (task.getState() == CallbackTaskState.FINAL_FAILED) {
            paymentOrderStateService.markCallbackFailed(payOrder);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
