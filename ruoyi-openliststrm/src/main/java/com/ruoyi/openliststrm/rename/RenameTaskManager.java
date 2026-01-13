package com.ruoyi.openliststrm.rename;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.Threads;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameTaskPlusService;
import com.ruoyi.openliststrm.processor.MediaRenameProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Manager that polls rename_task table and keeps FileMonitorService instances running for active tasks.
 * <p>
 * Behavior changes:
 * - TMDb/OpenAI keys are read from `OpenlistConfig` every poll. If TMDb key is missing, tasks will not start.
 * - If TMDb key is removed at runtime, all monitors are stopped until a valid key appears.
 */
@Slf4j
@Component
public class RenameTaskManager {


    private final IRenameTaskPlusService taskService;
    private final OpenlistConfig config;
    private final OpenListHelper helper;
    private final RenameClientProvider clientProvider;
    private final RenameEventListenerFactory listenerFactory;
    private final IRenameTaskPlusService renameTaskService;
    private final IRenameDetailPlusService renameDetailService;

    private final RenameMonitorRegistry registry = new RenameMonitorRegistry();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "rename-task-manager")
    );


    public RenameTaskManager(IRenameTaskPlusService taskService,
                             OpenlistConfig config,
                             OpenListHelper helper,
                             RenameClientProvider clientProvider,
                             RenameEventListenerFactory listenerFactory, IRenameTaskPlusService renameTaskService, IRenameDetailPlusService renameDetailService) {
        this.taskService = taskService;
        this.config = config;
        this.helper = helper;
        this.clientProvider = clientProvider;
        this.listenerFactory = listenerFactory;
        this.renameTaskService = renameTaskService;
        this.renameDetailService = renameDetailService;
    }

    @PostConstruct
    public void start() {
        //轮询调用
        scheduler.scheduleWithFixedDelay(Threads.wrap(this::poll), 0, 10, TimeUnit.SECONDS);
        log.info("RenameTaskManager started");
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdown();
        registry.stopAll();
        log.info("RenameTaskManager stopped");
    }

    public void executeRenameDetails(Integer id, String title, String year) {
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

        MediaRenameProcessor processor = new MediaRenameProcessor(
                Paths.get(tgt),
                clientProvider,
                helper,
                config,
                listenerFactory.create(id)
        );
        processor.handleOneFile(Paths.get(rd.getOriginalPath()).resolve(rd.getOriginalName()), StringUtils.isNotBlank(title) ? title : rd.getTitle(), StringUtils.isNotBlank(year) ? year : rd.getYear(), rd.getSeason(), rd.getEpisode());
    }

    public void executeRenameDetailsBatch(List<Integer> ids, String title, String year) {
        if (ids == null || ids.isEmpty()) return;
        for (Integer id : ids) {
            executeRenameDetails(id, title, year);
        }
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

    /**
     * 批量立即执行任务（以逗号分隔的 id 字符串或整数列表）。
     */
    public void executeTasksBatch(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Integer id : ids) {
            executeTaskNow(id);
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
        List<RenameTaskPlus> list = taskService.list(qw);
        return list.stream().collect(Collectors.toMap(RenameTaskPlus::getId, t -> t));
    }
}