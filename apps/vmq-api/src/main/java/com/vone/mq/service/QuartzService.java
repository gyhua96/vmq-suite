package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.domain.PaymentState;
import com.vone.mq.entity.PayOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuartzService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzService.class);

    @Value("${vmq.monitor.offline-timeout-ms:60000}")
    private long monitorOfflineTimeoutMs;

    @Autowired
    private PayOrderDao payOrderDao;
    @Autowired
    private PaymentOrderStateService paymentOrderStateService;
    @Autowired
    private CallbackTaskService callbackTaskService;
    @Autowired
    private SettingAccessService settingAccessService;


    @Scheduled(fixedRate = 30000)
    public void timerToZZP(){
        long now = System.currentTimeMillis();
        closeExpiredOrders(now);
        updateMonitorOfflineState(now);
        retryCallbackTasks(now);
    }

    @Scheduled(fixedRateString = "${vmq.monitor.offline-check-rate-ms:10000}")
    public void monitorOfflineTimer() {
        updateMonitorOfflineState(System.currentTimeMillis());
    }

    private void closeExpiredOrders(long now) {
        try {
            int closeMinutes = settingAccessService.closeMinutes();
            long timeoutTime = now - closeMinutes * 60 * 1000L;
            List<PayOrder> payOrders = payOrderDao.findAllByCreateDateLessThanAndState(timeoutTime, PaymentState.WAITING);
            int row = 0;
            for (PayOrder payOrder: payOrders) {
                if (paymentOrderStateService.closeWaitingOrder(payOrder, now)) {
                    row++;
                }
            }
            LOGGER.info("Closed {} expired waiting orders", row);
        }catch (Exception e){
            LOGGER.error("Failed to close expired waiting orders", e);
        }
    }

    private void updateMonitorOfflineState(long now) {
        try {
            String lastheart = settingAccessService.getValue(SettingAccessService.KEY_LAST_HEART, "0");
            String state = settingAccessService.getOrCreateValue(SettingAccessService.KEY_MONITOR_STATE, "-1");
            if (state.equals("1") && now - parseLongOrDefault(lastheart, 0L) >= monitorOfflineTimeoutMs){
                settingAccessService.saveValue(SettingAccessService.KEY_MONITOR_STATE, "0");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to update monitor offline state", e);
        }
    }

    private void retryCallbackTasks(long now) {
        try {
            callbackTaskService.retryDueTasks(now);
        } catch (Exception e) {
            LOGGER.error("Failed to retry callback tasks", e);
        }
    }

    private long parseLongOrDefault(String value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
