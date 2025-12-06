package com.ruoyi.openliststrm.rename;

import com.ruoyi.openliststrm.openai.OpenAIClient;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import com.ruoyi.openliststrm.tmdb.TMDbClient;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 监控目录，识别并将文件复制到目标目录，按电影/电视剧和分类规则组织子目录。
 *
 * 说明：
 * - 递归注册子目录以支持监控子文件夹。
 * - 使用简单的去重机制：在短时间窗口内（默认5s）不会重复处理同一文件，也避免并发多次处理。
 */
@Slf4j
public class FileMonitorService {
    private final Path sourceDir;
    private final Path targetRoot;
    private final TMDbClient tmdbClient; // may be null
    private final OpenAIClient openAIClient; // may be null
    private final long minFileSizeBytes; // skip small files
    private final Map<String, List<CategoryRule>> rules; // keys: "movie" or "tv"

    // filename template used by MediaParser.rename; can be overridden
    private final String filenameTemplate;

    // optional OpenList helper - if provided, use it to decide whether a file is a video and whether it's a .strm
    private final OpenListHelper openListHelper;

    // WatchService held so we can close it on stop()
    private WatchService watchService;

    // optional callback to persist or react to rename events
    private final RenameEventListener renameListener;

    // dedupe / in-flight tracking
    private final Set<Path> processing = ConcurrentHashMap.newKeySet();
    private final Map<Path, Instant> recentProcessed = new ConcurrentHashMap<>();
    private final long dedupeWindowMillis = 5_000L; // don't re-process same file within 5 seconds

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // centralized default template used when none provided
    private static final String DEFAULT_FILENAME_TEMPLATE = "{{ title }} {% if year and not season %} ({{ year }}) {% endif %}{% if season %}S{{ season }}{% endif %}{% if episode %}E{{ episode }}{% endif %}{% if resolution %} - {{ resolution }}{% endif %}{% if source %}.{{ source }}{% endif %}{% if videoCodec %}.{{ videoCodec }}{% endif %}{% if audioCodec %}.{{ audioCodec }}{% endif %}{% if tags is not empty %}.{{ tags|join('.') }}{% endif %}{% if releaseGroup %}-{{ releaseGroup }}{% endif %}.{{ extension }}";

    // existing constructor kept (no listener) - delegates to extended constructor with null helper
    public FileMonitorService(Path sourceDir, Path targetRoot, TMDbClient tmdbClient, OpenAIClient openAIClient, long minFileSizeBytes) {
        this(sourceDir, targetRoot, tmdbClient, openAIClient, minFileSizeBytes, DEFAULT_FILENAME_TEMPLATE, null, null);
    }

    // existing constructor kept but delegates to new constructor with listener and no helper
    public FileMonitorService(Path sourceDir, Path targetRoot, TMDbClient tmdbClient, OpenAIClient openAIClient, long minFileSizeBytes, String filenameTemplate) {
        this(sourceDir, targetRoot, tmdbClient, openAIClient, minFileSizeBytes, filenameTemplate, null, null);
    }

    // existing constructor kept but delegates to new constructor with no helper
    public FileMonitorService(Path sourceDir, Path targetRoot, TMDbClient tmdbClient, OpenAIClient openAIClient, long minFileSizeBytes, String filenameTemplate, RenameEventListener renameListener) {
        this(sourceDir, targetRoot, tmdbClient, openAIClient, minFileSizeBytes, filenameTemplate, renameListener, null);
    }

    // new primary constructor with optional OpenListHelper injection (can be null for non-Spring use)
    public FileMonitorService(Path sourceDir, Path targetRoot, TMDbClient tmdbClient, OpenAIClient openAIClient, long minFileSizeBytes, String filenameTemplate, RenameEventListener renameListener, OpenListHelper openListHelper) {
        this.sourceDir = sourceDir;
        this.targetRoot = targetRoot;
        this.tmdbClient = tmdbClient;
        this.openAIClient = openAIClient;
        this.minFileSizeBytes = minFileSizeBytes;
        this.rules = defaultRules();
        // if caller didn't provide a template (null or empty), use centralized default
        this.filenameTemplate = (filenameTemplate != null && !filenameTemplate.trim().isEmpty()) ? filenameTemplate : DEFAULT_FILENAME_TEMPLATE;
        this.renameListener = renameListener;
        this.openListHelper = openListHelper;
    }

    public void start() throws IOException {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("sourceDir must exist and be a directory: " + sourceDir);
        }
        log.info("Start monitoring folder (recursive): {} -> {}", sourceDir, targetRoot);
        this.watchService = FileSystems.getDefault().newWatchService();

        // register all existing subdirectories recursively
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                log.debug("Registered watch on dir: {}", dir);
                return FileVisitResult.CONTINUE;
            }
        });

        // poll thread
        executor.scheduleWithFixedDelay(() -> {
            try {
                WatchKey key;
                while ((key = this.watchService.poll()) != null) {
                    Path watchedDir = (Path) key.watchable();
                    for (WatchEvent<?> ev : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = ev.kind();
                        Path rel = (Path) ev.context();
                        Path full = watchedDir.resolve(rel).toAbsolutePath().normalize();
                        log.debug("FS event {} -> {}", kind.name(), full);

                        // if a new directory is created, register it (and its subtree)
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            try {
                                if (Files.isDirectory(full)) {
                                    // register this new directory and its subdirs
                                    Files.walkFileTree(full, new SimpleFileVisitor<Path>() {
                                        @Override
                                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                            dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                                            log.debug("Registered new subdir: {}", dir);
                                            return FileVisitResult.CONTINUE;
                                        }
                                    });
                                    continue; // don't schedule directory for file processing
                                }
                            } catch (IOException e) {
                                log.warn("Failed to register new directory {}: {}", full, e.getMessage());
                            }
                        }

                        // schedule processing after short delay so file writes finish
                        if (shouldSchedule(full)) {
                            processing.add(full); // mark as in-flight to avoid duplicate scheduling
                            executor.schedule(() -> {
                                try {
                                    handleFileIfReady(full);
                                } catch (Exception e) {
                                    log.error("处理文件失败: {}", full, e);
                                } finally {
                                    // record processing time and clear in-flight mark
                                    recentProcessed.put(full, Instant.now());
                                    processing.remove(full);
                                }
                            }, 2, TimeUnit.SECONDS);
                        } else {
                            log.debug("Skipping duplicate scheduling for {}", full);
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                log.error("watcher loop error", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
        if (this.watchService != null) {
            try {
                this.watchService.close();
            } catch (IOException e) {
                log.warn("Failed to close watchService: {}", e.getMessage());
            }
            this.watchService = null;
        }
    }

    /**
     * 执行一次完整扫描并处理源目录下的所有文件（用于手动/批量触发任务）。
     * 与长期监控不同：不会注册 WatchService，仅遍历现有文件并按常规流程处理。
     */
    public void processOnce() {
        try {
            if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
                log.warn("processOnce: sourceDir does not exist or not a directory: {}", sourceDir);
                return;
            }
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path p = file.toAbsolutePath().normalize();
                    if (shouldSchedule(p)) {
                        processing.add(p);
                        try {
                            handleFileIfReady(p);
                        } catch (Exception e) {
                            log.error("processOnce handleFileIfReady failed for {}", p, e);
                        } finally {
                            recentProcessed.put(p, Instant.now());
                            processing.remove(p);
                        }
                    } else {
                        log.debug("processOnce skipping already-processed or in-flight file: {}", p);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("processOnce failed", e);
        }
    }

    private boolean shouldSchedule(Path full) {
        if (full == null) return false;
        Path p = full.toAbsolutePath().normalize();
        // skip directories
        try {
            if (Files.exists(p) && Files.isDirectory(p)) return false;
        } catch (Exception ignored) {}
        Instant last = recentProcessed.get(p);
        if (last != null && Instant.now().minusMillis(dedupeWindowMillis).isBefore(last)) return false;
        if (processing.contains(p)) return false;
        return true;
    }

    private void handleFileIfReady(Path file) throws IOException {
        Path p = file.toAbsolutePath().normalize();
        if (!Files.exists(p) || Files.isDirectory(p)) return;

        long size = Files.size(p);

        String filename = p.getFileName().toString();

        // If OpenListHelper is provided, use it to decide whether this file should be processed.
        if (openListHelper != null) {
            boolean isStrm = openListHelper.isStrm(filename);
            boolean isVideo = openListHelper.isVideo(filename);
            if (!isStrm && !isVideo) {
                log.info("Skipping non-video file {}", p);
                return;
            }
            // if not a .strm file, enforce minimum size; .strm files are processed regardless of size
            if (!isStrm && size < minFileSizeBytes) {
                log.info("Skipping small file {} ({} bytes)", p, size);
                return;
            }
        } else {
            // legacy behavior when helper not available: enforce size threshold
            if (size < minFileSizeBytes) {
                log.info("跳过小文件 {} ({} bytes)", p, size);
                return;
            }
        }

        // only process files that haven't been modified for a short window
        BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
        Instant modified = attr.lastModifiedTime().toInstant();
        if (Instant.now().minusSeconds(3).isBefore(modified)) {
            // still being written
            log.debug("文件仍在写入，稍后再试：{}", p);
            // reschedule (respect dedupe rules)
            executor.schedule(() -> {
                try {
                    if (shouldSchedule(p)) {
                        processing.add(p);
                        handleFileIfReady(p);
                    }
                } catch (Exception e) {
                    log.error("重试处理失败", e);
                } finally {
                    recentProcessed.put(p, Instant.now());
                    processing.remove(p);
                }
            }, 2, TimeUnit.SECONDS);
            return;
        }

        MediaParser parser = new MediaParser(tmdbClient, openAIClient);
        MediaInfo info = parser.parse(filename);

        // If no tmdbId was found, consider this a failure: do not copy and notify listener
        String mediaType = (info.getSeason() != null || info.getEpisode() != null) ? "tv" : "movie";
        if ((info.getTmdbId() == null || info.getTmdbId().trim().isEmpty())) {
            log.info("未找到 tmdbId，跳过文件处理：{} ; parsed info title={}", p, info.getTitle());
            if (renameListener != null) {
                try {
                    renameListener.onRenameFailed(p, info, mediaType, "tmdbId not found");
                } catch (Exception e) {
                    log.warn("renameListener.onRenameFailed failed: {}", e.getMessage());
                }
            }
            return;
        }

        // decide category
        String category = classify(info, mediaType);
        if (category == null) category = "未分类";

        // build destination dir: targetRoot/{mediaType}/{category}/{title (year)}
        String safeTitle = sanitizeForPath(info.getTitle() != null ? info.getTitle() : stripExtension(filename));
        String yearPart = info.getYear() != null && !info.getYear().isEmpty() ? " (" + info.getYear() + ")" : "";
        String destDirName = safeTitle + yearPart;
        // 第一层目录使用中文：电影 / 电视剧
        String topLevel = "movie".equals(mediaType) ? "电影" : "电视剧";
        Path destDir = targetRoot.resolve(topLevel).resolve(category).resolve(destDirName);
        Files.createDirectories(destDir);

        // produce new filename via MediaParser.rename
        String rendered = parser.rename(filename, filenameTemplate);
        String newFilename = rendered == null || rendered.trim().isEmpty() ? filename : rendered.trim();
        // If template produced path segments, keep only last segment as filename
        newFilename = newFilename.replace('\\', '/');
        if (newFilename.contains("/")) newFilename = newFilename.substring(newFilename.lastIndexOf('/') + 1);
        newFilename = sanitizeForPath(newFilename);

        Path destFile = destDir.resolve(newFilename);
        // copy and overwrite if exists
        Files.copy(p, destFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        log.info("已复制并重命名 {} -> {}", p, destFile);

        // notify listener if present
        if (renameListener != null) {
            try {
                renameListener.onRename(p, destFile, info, mediaType);
            } catch (Exception e) {
                log.warn("renameListener failed: {}", e.getMessage());
            }
        }
    }

    private String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx == -1 ? name : name.substring(0, idx);
    }

    private String sanitizeForPath(String s) {
        // remove characters invalid in Windows paths
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String classify(MediaInfo info, String mediaType) {
        List<CategoryRule> list = rules.getOrDefault(mediaType, Collections.emptyList());
        for (CategoryRule r : list) {
            if (r.matches(info)) return r.getName();
        }
        return null;
    }

    private Map<String, List<CategoryRule>> defaultRules() {
        Map<String, List<CategoryRule>> m = new HashMap<>();
        // movie rules in order
        List<CategoryRule> movie = new ArrayList<>();
        movie.add(new CategoryRule("动画电影").withGenreIds("16"));
        movie.add(new CategoryRule("华语电影").withOriginalLanguage("zh", "cn", "bo", "za"));
        movie.add(new CategoryRule("外语电影")); // fallback if not matched above
        m.put("movie", movie);

        // tv rules
        List<CategoryRule> tv = new ArrayList<>();
        tv.add(new CategoryRule("国漫").withGenreIds("16").withOriginCountry("CN","TW","HK"));
        tv.add(new CategoryRule("日番").withGenreIds("16").withOriginCountry("JP"));
        tv.add(new CategoryRule("纪录片").withGenreIds("99"));
        tv.add(new CategoryRule("儿童").withGenreIds("10762"));
        tv.add(new CategoryRule("综艺").withGenreIds("10764","10767"));
        tv.add(new CategoryRule("国产剧").withOriginCountry("CN","TW","HK"));
        tv.add(new CategoryRule("欧美剧").withOriginCountry("US","FR","GB","DE","ES","IT","NL","PT","RU","UK"));
        tv.add(new CategoryRule("日韩剧").withOriginCountry("JP","KP","KR","TH","IN","SG"));
        tv.add(new CategoryRule("未分类"));
        m.put("tv", tv);

        return m;
    }

}
