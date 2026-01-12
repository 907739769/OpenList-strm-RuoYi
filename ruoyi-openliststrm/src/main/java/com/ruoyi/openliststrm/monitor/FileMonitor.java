package com.ruoyi.openliststrm.monitor;

import java.util.function.Consumer;

/**
 * @author: Jack
 * @creat: 2026/1/12 14:40
 */
public interface FileMonitor {

    void start();

    void stop();

    void setListener(Consumer<FileEvent> listener);
}
