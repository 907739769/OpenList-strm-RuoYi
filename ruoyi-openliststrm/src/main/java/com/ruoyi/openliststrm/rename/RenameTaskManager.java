package com.ruoyi.openliststrm.rename;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ThreadTraceIdUtil;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.monitor.processor.MediaRenameProcessor;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameTaskPlusService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理重命名任务的类。
 * 轮询重命名任务表并保持FileMonitorService实例运行的类。
 */
@Slf4j
@Component
public class RenameTaskManager {


    @Autowired
    private OpenlistConfig config;
    @Autowired
    private OpenListHelper helper;
    @Autowired
    private RenameClientProvider clientProvider;
    @Autowired
    private RenameEventListenerFactory listenerFactory;
    @Autowired
    private IRenameTaskPlusService renameTaskService;
    @Autowired
    private IRenameDetailPlusService renameDetailService;

    private final RenameMonitorRegistry registry = new RenameMonitorRegistry();
    private final TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor");

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ThreadTraceIdUtil.initTraceId();
        scheduler.scheduleAtFixedRate(this::poll, Instant.now().plusSeconds(10), Duration.ofSeconds(10));
        log.info("RenameTaskManager started");
    }

    @PreDestroy
    public void stop() {
        registry.stopAll();
        log.info("RenameTaskManager stopped");
        MDC.clear();
    }

    public void executeRenameDetails(Integer id, String title, String year, String season, String episode) {
        RenameDetailPlus rd = renameDetailService.getById(id);
        if (rd == null) {
            log.warn("Rename task not found: {}", id);
            return;
        }

        String tgt = rd.getNewPath();
        Path path = Paths.get(tgt);
        Path target = path;
        for (int i = path.getNameCount() - 1; i >= 0; i--) {
            String name = path.getName(i).toString();
            if (name.equals("电影") || name.equals("电视剧")) {
                target = path.getRoot().resolve(path.subpath(0, i));
                break;
            }
        }
        tgt = target.toString();

        // 用户留空则传空字符串，由 MediaParser 从文件名重新识别；手动填写则覆盖
        MediaRenameProcessor processor = new MediaRenameProcessor(
                Paths.get(tgt),
                clientProvider,
                helper,
                config,
                listenerFactory.create(id)
        );
        processor.handleOneFile(Paths.get(rd.getOriginalPath()).resolve(rd.getOriginalName()),
                StringUtils.isNotBlank(title) ? title : null,
                StringUtils.isNotBlank(year) ? year : null,
                season, episode);
    }

    /**
     * 立即执行单个任务一次（用于页面手动触发）。
     * 返回 true 表示已成功触发处理（不代表全部文件处理成功）。
     */
    public void executeTaskNow(Integer taskId) {
        try {
            if (taskId == null) return;
            RenameTaskPlus task = renameTaskService.getById(taskId);
            if (task == null) {
                log.warn("executeTaskNow: task {} not found", taskId);
                return;
            }
            String src = task.getSourceFolder();
            String tgt = task.getTargetRoot();
            if (src == null || tgt == null) {
                log.warn("executeTaskNow: task {} missing source/target", taskId);
                return;
            }
            MediaRenameProcessor processor = new MediaRenameProcessor(
                    Paths.get(tgt),
                    clientProvider,
                    helper,
                    config,
                    listenerFactory.create(taskId)
            );
            processor.processOnce(Paths.get(src));
        } catch (Exception e) {
            log.error("executeTaskNow error for {}: {}", taskId, e.getMessage(), e);
        }
    }

    private void poll() {
        try {
            //更新配置
            clientProvider.refresh(config);
            if (!clientProvider.available()) {
                registry.stopAll();
                return;
            }
            //查询所有启用的重命名任务
            Map<Integer, RenameTaskPlus> activeTasks = loadActiveTasks();
            registry.reconcile(activeTasks, clientProvider, helper, config, listenerFactory);
        } catch (Exception e) {
            log.error("poll error", e);
        }
    }


    private Map<Integer, RenameTaskPlus> loadActiveTasks() {
        QueryWrapper<RenameTaskPlus> qw = new QueryWrapper<>();
        qw.eq("status", "1");
        List<RenameTaskPlus> list = renameTaskService.list(qw);
        return list.stream().collect(Collectors.toMap(RenameTaskPlus::getId, t -> t));
    }
}