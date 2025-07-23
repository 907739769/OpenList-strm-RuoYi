package com.ruoyi.common.config.thread;

import com.ruoyi.common.utils.ThreadTraceIdUtil;
import com.ruoyi.common.utils.Threads;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 线程池配置
 *
 * @author ruoyi
 **/
@Configuration
public class ThreadPoolConfig
{
    // 核心线程池大小
    private int corePoolSize = 50;

    // 最大可创建的线程数
    private int maxPoolSize = 200;

    // 队列最大长度
    private int queueCapacity = 1000;

    // 线程池维护线程所允许的空闲时间
    private int keepAliveSeconds = 300;

    @Bean(name = "threadPoolTaskExecutor")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(maxPoolSize);
        executor.setCorePoolSize(corePoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        // 线程池对拒绝任务(无线程可用)的处理策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    /**
     * 执行周期性或定时任务
     */
    @Bean(name = "scheduledExecutorService")
    protected ScheduledExecutorService scheduledExecutorService()
    {
        return new ScheduledThreadPoolExecutor(corePoolSize,
                new BasicThreadFactory.Builder().namingPattern("schedule-pool-%d").daemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy()) {

            // 统一包装Runnable任务
            private Runnable wrap(Runnable task) {
                Map<String, String> context = MDC.getCopyOfContextMap();
                return () -> {
                    try {
                        if (context != null) {
                            MDC.setContextMap(context);
                            String childTraceId = ThreadTraceIdUtil.createChildTraceId();
                            MDC.put(ThreadTraceIdUtil.TRACE_ID_KEY, childTraceId);
                        }
                        task.run();
                    } finally {
                        MDC.clear();
                    }
                };
            }

            @Override
            public void execute(Runnable command) {
                super.execute(wrap(command));
            }

            @Override
            public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                return super.schedule(wrap(command), delay, unit);
            }

            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                          long initialDelay,
                                                          long period,
                                                          TimeUnit unit) {
                return super.scheduleAtFixedRate(wrap(command), initialDelay, period, unit);
            }

            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                             long initialDelay,
                                                             long delay,
                                                             TimeUnit unit) {
                return super.scheduleWithFixedDelay(wrap(command), initialDelay, delay, unit);
            }

            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                Threads.printException(r, t);
            }
        };
    }
}
