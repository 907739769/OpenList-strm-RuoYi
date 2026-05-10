package com.ruoyi.common.config.thread;

import com.ruoyi.common.utils.ThreadTraceIdUtil;
import com.ruoyi.common.utils.Threads;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 线程池配置
 *
 * @author ruoyi
 **/
@Configuration
public class ThreadPoolConfig {

    @Bean(name = "threadPoolTaskExecutor")
    public TaskExecutor threadPoolTaskExecutor() {
        // Java 21+ 虚拟线程执行器 - 适合 I/O 密集型任务
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        return task -> {
            if (task == null) return;
            Runnable wrapped = Threads.wrap(task);
            virtualExecutor.execute(wrapped);
        };
    }

    /**
     * 执行周期性或定时任务
     * 注意：定时轮询任务不适合虚拟线程，保持传统线程池
     */
    @Bean(name = "scheduledExecutorService")
    protected ScheduledExecutorService scheduledExecutorService() {
        return new ScheduledThreadPoolExecutor(10,
                new BasicThreadFactory.Builder().namingPattern("schedule-pool-%d").daemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy()) {


            @Override
            public void execute(Runnable command) {
                super.execute(Threads.wrap(command));
            }

            @Override
            public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                return super.schedule(Threads.wrap(command), delay, unit);
            }

            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                          long initialDelay,
                                                          long period,
                                                          TimeUnit unit) {
                return super.scheduleAtFixedRate(Threads.wrap(command), initialDelay, period, unit);
            }

            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                             long initialDelay,
                                                             long delay,
                                                             TimeUnit unit) {
                return super.scheduleWithFixedDelay(Threads.wrap(command), initialDelay, delay, unit);
            }

            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                Threads.printException(r, t);
            }
        };
    }

}
