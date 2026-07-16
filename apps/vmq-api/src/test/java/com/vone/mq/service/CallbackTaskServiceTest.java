package com.vone.mq.service;

import com.vone.mq.dao.CallbackTaskDao;
import com.vone.mq.domain.CallbackTaskState;
import com.vone.mq.entity.CallbackTask;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.entity.Setting;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CallbackTaskServiceTest {
    private CallbackTaskService taskService;
    private CallbackTaskDao callbackTaskDao;
    private CallbackService callbackService;
    private FakeCallbackHttpClient callbackHttpClient;
    private PaymentOrderStateService paymentOrderStateService;

    @Before
    public void setUp() {
        taskService = new CallbackTaskService();
        callbackTaskDao = mock(CallbackTaskDao.class);
        callbackService = mock(CallbackService.class);
        callbackHttpClient = new FakeCallbackHttpClient();
        paymentOrderStateService = mock(PaymentOrderStateService.class);
        ReflectionTestUtils.setField(taskService, "callbackTaskDao", callbackTaskDao);
        ReflectionTestUtils.setField(taskService, "callbackService", callbackService);
        ReflectionTestUtils.setField(taskService, "callbackPayloadBuilder", new CallbackPayloadBuilder());
        ReflectionTestUtils.setField(taskService, "callbackHttpClient", callbackHttpClient);
        ReflectionTestUtils.setField(taskService, "paymentOrderStateService", paymentOrderStateService);
    }

    @Test
    public void sendNowAndRecordSavesSuccessTask() {
        PayOrder payOrder = order("https://example.com/notify");
        when(callbackService.sendNotify(eq(payOrder), eq(Optional.empty()), eq("secret")))
                .thenReturn(CallbackResult.success("success"));
        when(callbackTaskDao.save(any(CallbackTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CallbackResult result = taskService.sendNowAndRecord(payOrder, Optional.empty(), "secret");

        assertTrue(result.isSuccess());
        verify(callbackTaskDao).save(org.mockito.ArgumentMatchers.argThat(task ->
                task.getOrderId().equals(99L)
                        && "pay-1".equals(task.getPayId())
                        && "https://example.com/notify".equals(task.getNotifyUrl())
                        && task.getQuery().contains("payId=pay-1")
                        && task.getState() == CallbackTaskState.SUCCESS
                        && task.getRetryCount() == 0
                        && task.getNextRetryTime() == 0));
    }

    @Test
    public void sendNowAndRecordSavesRetryWaitingTaskOnFailure() {
        PayOrder payOrder = order("");
        Setting setting = new Setting();
        setting.setVkey("notifyUrl");
        setting.setVvalue("https://merchant.example/notify");
        when(callbackService.sendNotify(eq(payOrder), any(), eq("secret")))
                .thenReturn(CallbackResult.failure("failed-response", "通知异步地址失败"));

        CallbackResult result = taskService.sendNowAndRecord(payOrder, Optional.of(setting), "secret");

        assertEquals("通知异步地址失败", result.getErrorMessage());
        verify(callbackTaskDao).save(org.mockito.ArgumentMatchers.argThat(task ->
                "https://merchant.example/notify".equals(task.getNotifyUrl())
                        && task.getState() == CallbackTaskState.RETRY_WAITING
                        && task.getRetryCount() == 1
                        && task.getNextRetryTime() > System.currentTimeMillis()
                        && "failed-response".equals(task.getLastResponse())
                        && "通知异步地址失败".equals(task.getLastError())));
    }

    @Test
    public void sendNowAndRecordReusesExistingLatestTask() {
        PayOrder payOrder = order("https://example.com/new");
        CallbackTask existing = new CallbackTask();
        existing.setRetryCount(2);
        existing.setNotifyUrl("https://example.com/old");
        existing.setQuery("old-query");
        when(callbackTaskDao.findTopByOrderIdOrderByIdDesc(99L)).thenReturn(existing);
        when(callbackService.sendNotify(eq(payOrder), eq(Optional.empty()), eq("secret")))
                .thenReturn(CallbackResult.failure(null, "通知异步地址失败"));

        taskService.sendNowAndRecord(payOrder, Optional.empty(), "secret");

        verify(callbackTaskDao).save(org.mockito.ArgumentMatchers.argThat(task ->
                task == existing
                        && "https://example.com/new".equals(task.getNotifyUrl())
                        && task.getQuery().contains("payId=pay-1")
                        && task.getRetryCount() == 3));
    }

    @Test
    public void enqueueCreatesRetryWaitingTaskWithoutSendingHttp() {
        PayOrder payOrder = order("https://example.com/notify");

        taskService.enqueue(payOrder, Optional.empty(), "secret");

        verify(callbackService, never()).sendNotify(any(PayOrder.class), any(), any(String.class));
        assertEquals(null, callbackHttpClient.url);
        verify(callbackTaskDao).save(org.mockito.ArgumentMatchers.argThat(task ->
                task.getOrderId().equals(99L)
                        && "pay-1".equals(task.getPayId())
                        && "https://example.com/notify".equals(task.getNotifyUrl())
                        && task.getQuery().contains("payId=pay-1")
                        && task.getState() == CallbackTaskState.RETRY_WAITING
                        && task.getRetryCount() == 0
                        && task.getNextRetryTime() > 0
                        && task.getLastResponse() == null
                        && task.getLastError() == null));
    }

    @Test
    public void enqueueReusesExistingTaskWithoutIncreasingRetryCount() {
        PayOrder payOrder = order("https://example.com/new");
        CallbackTask existing = new CallbackTask();
        existing.setRetryCount(4);
        when(callbackTaskDao.findTopByOrderIdOrderByIdDesc(99L)).thenReturn(existing);

        taskService.enqueue(payOrder, Optional.empty(), "secret");

        verify(callbackTaskDao).save(org.mockito.ArgumentMatchers.argThat(task ->
                task == existing
                        && task.getState() == CallbackTaskState.RETRY_WAITING
                        && task.getRetryCount() == 4
                        && "https://example.com/new".equals(task.getNotifyUrl())));
    }

    @Test
    public void retryDueTasksMarksSuccessAndReturnsSuccessCount() {
        CallbackTask task = retryTask(2);
        callbackHttpClient.response = " success\n";
        when(callbackTaskDao.findTop20ByStateAndNextRetryTimeLessThanEqualOrderByNextRetryTimeAsc(
                CallbackTaskState.RETRY_WAITING, 1000L)).thenReturn(Collections.singletonList(task));

        int count = taskService.retryDueTasks(1000L);

        assertEquals(1, count);
        assertEquals("https://example.com/notify", callbackHttpClient.url);
        assertEquals("payId=pay-1", callbackHttpClient.query);
        verify(callbackTaskDao).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved == task
                        && saved.getState() == CallbackTaskState.SUCCESS
                        && saved.getRetryCount() == 2
                        && saved.getNextRetryTime() == 0
                        && " success\n".equals(saved.getLastResponse())));
        verify(paymentOrderStateService).markCallbackSucceeded(org.mockito.ArgumentMatchers.argThat(order ->
                order.getId().equals(99L)));
        verify(paymentOrderStateService, never()).markCallbackFailed(any(PayOrder.class));
    }

    @Test
    public void retryDueTasksKeepsWaitingWhenResponseIsNotSuccess() {
        CallbackTask task = retryTask(2);
        callbackHttpClient.response = "failed";
        when(callbackTaskDao.findTop20ByStateAndNextRetryTimeLessThanEqualOrderByNextRetryTimeAsc(
                CallbackTaskState.RETRY_WAITING, 1000L)).thenReturn(Collections.singletonList(task));

        int count = taskService.retryDueTasks(1000L);

        assertEquals(0, count);
        verify(callbackTaskDao).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved == task
                        && saved.getState() == CallbackTaskState.RETRY_WAITING
                        && saved.getRetryCount() == 3
                        && saved.getNextRetryTime() == 61000L
                        && "failed".equals(saved.getLastResponse())
                        && "通知异步地址失败".equals(saved.getLastError())));
        verify(paymentOrderStateService, never()).markCallbackSucceeded(any(PayOrder.class));
        verify(paymentOrderStateService, never()).markCallbackFailed(any(PayOrder.class));
    }

    @Test
    public void retryDueTasksMarksFinalFailedWhenMaxRetryReached() {
        CallbackTask task = retryTask(9);
        callbackHttpClient.response = "failed";
        when(callbackTaskDao.findTop20ByStateAndNextRetryTimeLessThanEqualOrderByNextRetryTimeAsc(
                CallbackTaskState.RETRY_WAITING, 1000L)).thenReturn(Collections.singletonList(task));

        int count = taskService.retryDueTasks(1000L);

        assertEquals(0, count);
        verify(callbackTaskDao).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved == task
                        && saved.getState() == CallbackTaskState.FINAL_FAILED
                        && saved.getRetryCount() == 10
                        && saved.getNextRetryTime() == 0
                        && "failed".equals(saved.getLastResponse())
                        && "通知异步地址失败".equals(saved.getLastError())));
        verify(paymentOrderStateService).markCallbackFailed(org.mockito.ArgumentMatchers.argThat(order ->
                order.getId().equals(99L)));
        verify(paymentOrderStateService, never()).markCallbackSucceeded(any(PayOrder.class));
    }

    @Test
    public void retryDueTasksRejectsUnsafeNotifyUrlWithoutSendingHttp() {
        CallbackTask task = retryTask(2);
        task.setNotifyUrl("http://127.0.0.1/callback");
        when(callbackTaskDao.findTop20ByStateAndNextRetryTimeLessThanEqualOrderByNextRetryTimeAsc(
                CallbackTaskState.RETRY_WAITING, 1000L)).thenReturn(Collections.singletonList(task));

        int count = taskService.retryDueTasks(1000L);

        assertEquals(0, count);
        assertEquals(0, callbackHttpClient.callCount);
        verify(callbackTaskDao).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved == task
                        && saved.getState() == CallbackTaskState.RETRY_WAITING
                        && saved.getRetryCount() == 3
                        && saved.getNextRetryTime() == 61000L
                        && saved.getLastResponse() == null
                        && "异步通知地址不安全或不允许".equals(saved.getLastError())));
    }

    private PayOrder order(String notifyUrl) {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(99L);
        payOrder.setPayId("pay-1");
        payOrder.setParam("param");
        payOrder.setType(2);
        payOrder.setPrice(49.95);
        payOrder.setReallyPrice(49.96);
        payOrder.setNotifyUrl(notifyUrl);
        return payOrder;
    }

    private CallbackTask retryTask(int retryCount) {
        CallbackTask task = new CallbackTask();
        task.setOrderId(99L);
        task.setPayId("pay-1");
        task.setNotifyUrl("https://example.com/notify");
        task.setQuery("payId=pay-1");
        task.setState(CallbackTaskState.RETRY_WAITING);
        task.setRetryCount(retryCount);
        task.setNextRetryTime(1000L);
        task.setCreateTime(1L);
        task.setUpdateTime(1L);
        return task;
    }

    private static class FakeCallbackHttpClient implements CallbackHttpClient {
        private String url;
        private String query;
        private String response;
        private int callCount;

        @Override
        public String sendGet(String url, String query) {
            this.callCount++;
            this.url = url;
            this.query = query;
            return response;
        }
    }
}
