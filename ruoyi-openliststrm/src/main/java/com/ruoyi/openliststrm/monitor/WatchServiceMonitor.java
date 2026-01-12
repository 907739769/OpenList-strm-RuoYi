package com.ruoyi.openliststrm.monitor;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * @author: Jack
 * @creat: 2026/1/12 14:40
 */
@Slf4j
public class WatchServiceMonitor implements FileMonitor {

    private final Path root;
    private WatchService watchService;
    private Consumer<FileEvent> listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public WatchServiceMonitor(Path root) {
        this.root = root;
    }

    @Override
    public void setListener(Consumer<FileEvent> listener) {
        this.listener = listener;
    }

    @Override
    public void start() {
        executor.submit(this::run);
    }

    private void run() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerAll(root);

            while (true) {
                WatchKey key = watchService.take();
                Path dir = (Path) key.watchable();

                for (WatchEvent<?> event : key.pollEvents()) {
                    Path p = (Path) event.context();
                    Path fullPath = dir.resolve(p).toAbsolutePath().normalize();
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == ENTRY_CREATE) {
                        handleCreate(fullPath);
                    } else if (kind == ENTRY_MODIFY) {
                        handleModify(fullPath);
                    } else if (kind == ENTRY_DELETE) {
                        handleDelete(fullPath);
                    }
                }
                key.reset();
            }
        } catch (Exception e) {
            log.error("watch loop error", e);
        }
    }

    @Override
    public void stop() {
        executor.shutdownNow();
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {
        }
    }

    private void handleCreate(Path path) {
        try {
            if (Files.isDirectory(path)) {
                // 新目录：必须递归注册
                registerAll(path);

                // 补扫目录内已有文件（极关键）
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (!Files.isDirectory(file)) {
                            listener.accept(new FileEvent(path, FileEvent.Type.CREATE));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                if (listener != null) {
                    listener.accept(new FileEvent(path, FileEvent.Type.CREATE));
                }
            }
        } catch (Exception e) {
            log.error("handleCreate error {}", path, e);
        }
    }

    private void handleModify(Path path) {
        if (!Files.isDirectory(path)) {
            listener.accept(new FileEvent(path, FileEvent.Type.MODIFY));
        }
    }

    private void handleDelete(Path path) {
    }


    /**
     * 递归注册
     */
    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path dir) throws IOException {
        Path p = dir.toAbsolutePath().normalize();
        p.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
        log.debug("Registered {}", p);
    }

}
