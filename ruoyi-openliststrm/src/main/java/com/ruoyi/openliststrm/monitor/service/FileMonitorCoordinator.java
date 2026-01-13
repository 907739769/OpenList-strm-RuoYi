package com.ruoyi.openliststrm.monitor.service;

import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.monitor.FileMonitor;
import com.ruoyi.openliststrm.monitor.processor.FileProcessor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.TimerTask;

/**
 * 文件监控协调者
 * 文件监控的运行与事件编排器
 *
 * @author: Jack
 * @creat: 2026/1/12 14:46
 */
@Slf4j
public class FileMonitorCoordinator {

    private final FileMonitor monitor;
    private final FileProcessor processor;

    public FileMonitorCoordinator(FileMonitor monitor, FileProcessor processor) {
        this.monitor = monitor;
        this.processor = processor;
    }

    public void start() {
        monitor.setListener(event -> {
            Path p = event.getPath();
            AsyncManager.me().execute(new TimerTask() {
                @Override
                public void run() {
                    processor.process(p);
                }
            });
        });
        monitor.start();
    }

    public void stop() {
        monitor.stop();
    }
}

