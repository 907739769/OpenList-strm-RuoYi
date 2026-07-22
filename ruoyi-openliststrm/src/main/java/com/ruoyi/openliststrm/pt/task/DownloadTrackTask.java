package com.ruoyi.openliststrm.pt.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.common.utils.ThreadTraceIdUtil;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloaderPlusService;
import com.ruoyi.openliststrm.pt.downloader.DownloaderClientFactory;
import com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent;
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
 * 每 30 秒轮询下载器，把种子状态回映到下载记录。
 *
 * @author Jack
 */
@Slf4j
@Component
public class DownloadTrackTask {

    @Autowired
    private IPtDownloaderPlusService downloaderService;
    @Autowired
    private DownloaderClientFactory downloaderClientFactory;
    @Autowired
    private DownloadTrackService trackService;

    private final TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor");

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ThreadTraceIdUtil.initTraceId();
        scheduler.scheduleAtFixedRate(this::poll, Instant.now().plusSeconds(30), Duration.ofSeconds(30));
        log.info("DownloadTrackTask started");
    }

    @PreDestroy
    public void stop() {
        log.info("DownloadTrackTask stopped");
        MDC.clear();
    }

    private void poll() {
        try {
            List<PtDownloaderPlus> downloaders = downloaderService.list(
                    new QueryWrapper<PtDownloaderPlus>().eq("enabled", "1"));
            for (PtDownloaderPlus downloader : downloaders) {
                try {
                    List<DownloaderTorrent> torrents = downloaderClientFactory.get(downloader)
                            .listByTag(downloader, downloader.getTag());
                    trackService.track(downloader, torrents);
                } catch (Exception e) {
                    log.warn("追踪下载器[{}]失败：{}", downloader.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("DownloadTrackTask poll error", e);
        }
    }
}
