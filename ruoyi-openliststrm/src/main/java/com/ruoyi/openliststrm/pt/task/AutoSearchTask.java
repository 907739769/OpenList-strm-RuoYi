package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.common.utils.ThreadTraceIdUtil;
import com.ruoyi.common.utils.spring.SpringUtils;
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

/**
 * 自动补搜心跳：每 30 分钟检查一次哪些订阅开启了自动补搜且到期，到期的发起一次搜索
 * （具体周期由 pt_filter_config.auto_search_interval_hours 决定，默认 24 小时）。
 *
 * @author Jack
 */
@Slf4j
@Component
public class AutoSearchTask {

    @Autowired
    private AutoSearchService autoSearchService;

    private final TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor");

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ThreadTraceIdUtil.initTraceId();
        scheduler.scheduleAtFixedRate(this::poll, Instant.now().plusSeconds(180), Duration.ofMinutes(30));
        log.info("AutoSearchTask started");
    }

    @PreDestroy
    public void stop() {
        log.info("AutoSearchTask stopped");
        MDC.clear();
    }

    private void poll() {
        try {
            autoSearchService.run();
        } catch (Exception e) {
            log.error("AutoSearchTask poll error", e);
        }
    }
}
