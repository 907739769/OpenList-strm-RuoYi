package com.ruoyi.framework.manager;

import com.ruoyi.common.utils.spring.SpringUtils;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;

/**
 * 异步任务管理器
 * 
 * @author liuhulu
 */
public class AsyncManager
{
    private final int OPERATE_DELAY_TIME = 10;

    private TaskScheduler executor = SpringUtils.getBean("virtualScheduledExecutor");

    private AsyncManager(){}

    private static AsyncManager me = new AsyncManager();

    public static AsyncManager me()
    {
        return me;
    }

    public void execute(Runnable task)
    {
        executor.scheduleWithFixedDelay(task, Duration.ofMillis(OPERATE_DELAY_TIME));
    }

    public void shutdown()
    {
    }
}
