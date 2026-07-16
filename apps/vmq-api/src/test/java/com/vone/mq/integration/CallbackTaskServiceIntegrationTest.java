package com.vone.mq.integration;

import com.vone.mq.dao.CallbackTaskDao;
import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.domain.CallbackTaskState;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.entity.CallbackTask;
import com.vone.mq.entity.PayOrder;
import com.vone.mq.service.CallbackHttpClient;
import com.vone.mq.service.CallbackTaskService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class CallbackTaskServiceIntegrationTest {

    static {
        System.setProperty("vmq.admin.password", "test-only-password");
    }

    @Autowired
    private CallbackTaskService callbackTaskService;
    @Autowired
    private CallbackTaskDao callbackTaskDao;
    @Autowired
    private PayOrderDao payOrderDao;
    @MockBean
    private CallbackHttpClient callbackHttpClient;

    @Before
    public void cleanDatabase() {
        callbackTaskDao.deleteAll();
        payOrderDao.deleteAll();
    }

    @Test
    public void enqueuePersistsRetryWaitingTaskAndReusesLatestTaskForSameOrder() {
        PayOrder order = order(99L, "PAY-CALLBACK-001", "https://merchant.example/cb");

        CallbackTask first = callbackTaskService.enqueue(order, Optional.empty(), "secret");
        CallbackTask second = callbackTaskService.enqueue(order, Optional.empty(), "secret-2");

        assertEquals(first.getId(), second.getId());
        assertEquals(1, callbackTaskDao.findAll().size());
        CallbackTask saved = callbackTaskDao.findTopByOrderIdOrderByIdDesc(99L);
        assertNotNull(saved);
        assertEquals(CallbackTaskState.RETRY_WAITING, saved.getState());
        assertEquals(0, saved.getRetryCount());
        assertEquals("PAY-CALLBACK-001", saved.getPayId());
        assertEquals("https://merchant.example/cb", saved.getNotifyUrl());
        assertTrue(saved.getQuery().contains("payId=PAY-CALLBACK-001"));
        assertTrue(saved.getQuery().contains("signType=HMAC_SHA256"));
    }

    @Test
    public void retryDueTasksMarksDueTaskSuccessAndLeavesFutureTaskWaiting() {
        CallbackTask due = retryTask(100L, "PAY-DUE", 1, 1000L);
        CallbackTask future = retryTask(101L, "PAY-FUTURE", 1, 5000L);
        callbackTaskDao.save(due);
        callbackTaskDao.save(future);
        when(callbackHttpClient.sendGet(eq("https://example.com/cb"), eq("payId=PAY-DUE")))
                .thenReturn(" success\n");

        int successCount = callbackTaskService.retryDueTasks(1000L);

        assertEquals(1, successCount);
        CallbackTask success = callbackTaskDao.findTopByOrderIdOrderByIdDesc(100L);
        assertEquals(CallbackTaskState.SUCCESS, success.getState());
        assertEquals(1, success.getRetryCount());
        assertEquals(0, success.getNextRetryTime());
        assertEquals(" success\n", success.getLastResponse());

        CallbackTask waiting = callbackTaskDao.findTopByOrderIdOrderByIdDesc(101L);
        assertEquals(CallbackTaskState.RETRY_WAITING, waiting.getState());
        assertEquals(1, waiting.getRetryCount());
        assertEquals(5000L, waiting.getNextRetryTime());
    }

    @Test
    public void retryDueTasksMarksFinalFailedAfterMaxRetryCount() {
        CallbackTask due = retryTask(102L, "PAY-FINAL", 9, 1000L);
        callbackTaskDao.save(due);
        when(callbackHttpClient.sendGet(eq("https://example.com/cb"), eq("payId=PAY-FINAL")))
                .thenReturn("failed");

        int successCount = callbackTaskService.retryDueTasks(1000L);

        assertEquals(0, successCount);
        CallbackTask failed = callbackTaskDao.findTopByOrderIdOrderByIdDesc(102L);
        assertEquals(CallbackTaskState.FINAL_FAILED, failed.getState());
        assertEquals(10, failed.getRetryCount());
        assertEquals(0, failed.getNextRetryTime());
        assertEquals("failed", failed.getLastResponse());
        assertEquals("通知异步地址失败", failed.getLastError());
    }

    @Test
    public void retryDueTasksMarksPaidOrderCallbackFailedWhenRetriesExhausted() {
        PayOrder order = saveOrder("PAY-FINAL-ORDER", PaymentState.PAID);
        CallbackTask due = retryTask(order.getId(), "PAY-FINAL-ORDER", 9, 1000L);
        callbackTaskDao.save(due);
        when(callbackHttpClient.sendGet(eq("https://example.com/cb"), eq("payId=PAY-FINAL-ORDER")))
                .thenReturn("failed");

        callbackTaskService.retryDueTasks(1000L);

        PayOrder failedOrder = payOrderDao.findByPayId("PAY-FINAL-ORDER");
        assertEquals(PaymentState.CALLBACK_FAILED, failedOrder.getState());
    }

    @Test
    public void retryDueTasksMarksCallbackFailedOrderPaidWhenRetrySucceeds() {
        PayOrder order = saveOrder("PAY-RECOVER-ORDER", PaymentState.CALLBACK_FAILED);
        CallbackTask due = retryTask(order.getId(), "PAY-RECOVER-ORDER", 1, 1000L);
        callbackTaskDao.save(due);
        when(callbackHttpClient.sendGet(eq("https://example.com/cb"), eq("payId=PAY-RECOVER-ORDER")))
                .thenReturn("success");

        callbackTaskService.retryDueTasks(1000L);

        PayOrder paidOrder = payOrderDao.findByPayId("PAY-RECOVER-ORDER");
        assertEquals(PaymentState.PAID, paidOrder.getState());
    }

    @Test
    public void retryDueTasksRejectsUnsafeNotifyUrlWithoutCallingHttpClient() {
        CallbackTask due = retryTask(103L, "PAY-UNSAFE", 2, 1000L);
        due.setNotifyUrl("http://127.0.0.1/cb");
        callbackTaskDao.save(due);

        int successCount = callbackTaskService.retryDueTasks(1000L);

        assertEquals(0, successCount);
        verify(callbackHttpClient, never()).sendGet(eq("http://127.0.0.1/cb"), eq("payId=PAY-UNSAFE"));
        CallbackTask failed = callbackTaskDao.findTopByOrderIdOrderByIdDesc(103L);
        assertEquals(CallbackTaskState.RETRY_WAITING, failed.getState());
        assertEquals(3, failed.getRetryCount());
        assertEquals(61000L, failed.getNextRetryTime());
        assertEquals("异步通知地址不安全或不允许", failed.getLastError());
    }

    private PayOrder order(Long id, String payId, String notifyUrl) {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(id);
        payOrder.setPayId(payId);
        payOrder.setParam("param");
        payOrder.setType(2);
        payOrder.setPrice(49.95);
        payOrder.setReallyPrice(49.96);
        payOrder.setNotifyUrl(notifyUrl);
        return payOrder;
    }

    private CallbackTask retryTask(Long orderId, String payId, int retryCount, long nextRetryTime) {
        CallbackTask task = new CallbackTask();
        task.setOrderId(orderId);
        task.setPayId(payId);
        task.setNotifyUrl("https://example.com/cb");
        task.setQuery("payId=" + payId);
        task.setState(CallbackTaskState.RETRY_WAITING);
        task.setRetryCount(retryCount);
        task.setNextRetryTime(nextRetryTime);
        task.setCreateTime(1L);
        task.setUpdateTime(1L);
        return task;
    }

    private PayOrder saveOrder(String payId, int state) {
        PayOrder payOrder = new PayOrder();
        payOrder.setPayId(payId);
        payOrder.setOrderId("ORDER-" + payId);
        payOrder.setCreateDate(1782873030000L);
        payOrder.setPayDate(1782873040000L);
        payOrder.setCloseDate(1782873040000L);
        payOrder.setParam("param");
        payOrder.setType(2);
        payOrder.setPrice(49.95);
        payOrder.setReallyPrice(49.96);
        payOrder.setNotifyUrl("https://merchant.example/cb");
        payOrder.setReturnUrl("https://merchant.example/return");
        payOrder.setState(state);
        payOrder.setIsAuto(1);
        payOrder.setPayUrl("qr-content");
        return payOrderDao.save(payOrder);
    }
}
