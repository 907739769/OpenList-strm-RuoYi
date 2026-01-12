package com.ruoyi.openliststrm.monitor;

import com.ruoyi.openliststrm.processor.FileProcessor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author: Jack
 * @creat: 2026/1/12 14:46
 */
@Slf4j
public class FileMonitorService {

    private final FileMonitor monitor;
    private final FileProcessor processor;
    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(10);

    public FileMonitorService(FileMonitor monitor, FileProcessor processor) {
        this.monitor = monitor;
        this.processor = processor;
    }

    public void start() {
        monitor.setListener(event -> {
            Path p = event.getPath();
            executor.schedule(() -> processor.process(p), 2, TimeUnit.SECONDS);
        });
        monitor.start();
    }

    public void stop() {
        monitor.stop();
        executor.shutdown();
    }
}

