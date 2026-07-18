package com.ruoyi.openliststrm.scrape;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按目标文件路径加锁，避免批量刮削时多个任务并发写同一 NFO/图片文件（如 tvshow.nfo、season-poster.jpg）导致内容交错。
 * 仅包内可见，供 {@link NfoGenerator} 与 {@link MediaImageDownloader} 复用。
 */
final class ScrapeFileLock {

    private static final ConcurrentMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private ScrapeFileLock() {
    }

    @FunctionalInterface
    interface IoAction {
        void run() throws IOException;
    }

    static void withLock(Path target, IoAction action) throws IOException {
        String key = target.toAbsolutePath().normalize().toString();
        Object lock = LOCKS.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (lock) {
                action.run();
            }
        } finally {
            LOCKS.remove(key, lock);
        }
    }
}
