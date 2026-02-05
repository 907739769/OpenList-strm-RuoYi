package com.ruoyi.openliststrm.upload;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.common.utils.ThreadTraceIdUtil;
import com.ruoyi.common.utils.Threads;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyTaskPlusService;
import com.ruoyi.openliststrm.service.ICopyService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author: Jack
 * @creat: 2026/1/14 14:10
 */
@Slf4j
@Component
public class UploadTaskManager {

    @Autowired
    private IOpenlistCopyTaskPlusService copyTaskPlusService;
    @Autowired
    private ICopyService copyService;

    private final UploadMonitorRegistry registry = new UploadMonitorRegistry();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "upload-task-manager")
    );

    @PostConstruct
    public void start() {
        //轮询调用
        ThreadTraceIdUtil.initTraceId();
        scheduler.scheduleWithFixedDelay(Threads.wrap(this::poll), 0, 10, TimeUnit.SECONDS);
        log.info("UploadTaskManager started");
    }

    @PreDestroy
    public void stop() {
        Threads.shutdownAndAwaitTermination(scheduler);
        registry.stopAll();
        log.info("UploadTaskManager stopped");
        MDC.clear();
    }

    private void poll() {
        try {
            //查询所有启用的文件同步任务
            Map<Integer, OpenlistCopyTaskPlus> activeTasks = loadActiveTasks();
            registry.reconcile(activeTasks, copyService);
        } catch (Exception e) {
            log.error("poll error", e);
        }
    }


    private Map<Integer, OpenlistCopyTaskPlus> loadActiveTasks() {
        QueryWrapper<OpenlistCopyTaskPlus> qw = new QueryWrapper<>();
        qw.eq("copy_task_status", "1");
        qw.isNotNull("monitor_dir");
        qw.ne("monitor_dir", "");
        List<OpenlistCopyTaskPlus> list = copyTaskPlusService.list(qw);
        return list.stream().collect(Collectors.toMap(OpenlistCopyTaskPlus::getCopyTaskId, t -> t));
    }
}
