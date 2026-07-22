package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.common.utils.ThreadTraceIdUtil;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 每 10 分钟遍历订阅中的订阅，与 Emby 对账（补齐总集数、推进已入库、重算状态）。
 *
 * @author Jack
 */
@Slf4j
@Component
public class LibrarySyncTask {

    @Autowired
    private IPtSubscriptionPlusService subscriptionService;
    @Autowired
    private SubscriptionService subscriptionBiz;

    private final TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor");

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ThreadTraceIdUtil.initTraceId();
        scheduler.scheduleAtFixedRate(this::poll, Instant.now().plusSeconds(120), Duration.ofMinutes(10));
        log.info("LibrarySyncTask started");
    }

    @PreDestroy
    public void stop() {
        log.info("LibrarySyncTask stopped");
        MDC.clear();
    }

    private void poll() {
        try {
            List<PtSubscriptionPlus> active = subscriptionService.listActive();
            for (PtSubscriptionPlus sub : active) {
                try {
                    subscriptionBiz.refresh(sub.getId());
                } catch (Exception e) {
                    log.warn("对账订阅[{}]失败：{}", sub.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("LibrarySyncTask poll error", e);
        }
    }
}
