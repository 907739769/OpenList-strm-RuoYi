package com.ruoyi.openliststrm.monitor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.io.monitor.FileEntry;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于 Apache Commons IO 的轮询文件监控
 *
 * @author Jack
 */
@Slf4j
public class FileAlterationMonitorMonitor implements FileMonitor {

    /**
     * 默认轮询间隔：3 秒（可根据 IO 压力调整）
     */
    private static final long DEFAULT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(3);

    private final Path root;
    private final long intervalMs;

    private Consumer<FileEvent> listener;
    private FileAlterationMonitor monitor;

    public FileAlterationMonitorMonitor(Path root) {
        this(root, DEFAULT_INTERVAL_MS);
    }

    public FileAlterationMonitorMonitor(Path root, long intervalMs) {
        this.root = root.toAbsolutePath().normalize();
        this.intervalMs = intervalMs;
    }

    @Override
    public void setListener(Consumer<FileEvent> listener) {
        this.listener = listener;
    }

    @Override
    public void start() {
        try {
            FileAlterationObserver observer = FileAlterationObserver.builder()
                    .setRootEntry(new FileEntry(root.toFile()))
                    .get();

            observer.addListener(new InternalListener());

            monitor = new FileAlterationMonitor(intervalMs, observer);
            monitor.start();

            log.info("FileAlterationMonitor started, root={}, interval={}ms",
                    root, intervalMs);

        } catch (Exception e) {
            log.error("start FileAlterationMonitor error", e);
        }
    }

    @Override
    public void stop() {
        try {
            if (monitor != null) {
                monitor.stop();
            }
        } catch (Exception e) {
            log.warn("stop FileAlterationMonitor error", e);
        }
    }

    /**
     * 内部监听器
     */
    private class InternalListener extends FileAlterationListenerAdaptor {

        @Override
        public void onFileCreate(File file) {
            fire(file.toPath(), FileEvent.Type.CREATE);
        }

        @Override
        public void onFileChange(File file) {
            fire(file.toPath(), FileEvent.Type.MODIFY);
        }

        @Override
        public void onFileDelete(File file) {
        }

        @Override
        public void onDirectoryCreate(File directory) {
        }

        @Override
        public void onDirectoryChange(File directory) {
            // 一般不需要处理
        }

        @Override
        public void onDirectoryDelete(File directory) {
        }

        private void fire(Path path, FileEvent.Type type) {
            if (listener == null) {
                return;
            }
            try {
                listener.accept(new FileEvent(
                        path.toAbsolutePath().normalize(),
                        type
                ));
            } catch (Exception e) {
                log.error("listener error, path={}, type={}", path, type, e);
            }
        }
    }
}

