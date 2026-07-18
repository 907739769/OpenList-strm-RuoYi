package com.ruoyi.openliststrm.monitor.processor;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameTaskPlusService;
import com.ruoyi.openliststrm.rename.CategoryRule;
import com.ruoyi.openliststrm.rename.MediaParser;
import com.ruoyi.openliststrm.rename.RenameClientProvider;
import com.ruoyi.openliststrm.rename.RenameEventListener;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import com.ruoyi.openliststrm.scrape.ScrapeService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * 媒体文件重命名处理器
 *
 * @author: Jack
 * @creat: 2026/1/12 14:45
 */
@Slf4j
public class MediaRenameProcessor implements FileProcessor {

    private final Path targetRoot;
    private final RenameEventListener renameListener;
    private final OpenListHelper openListHelper;
    private final long minFileSizeBytes;

    private final RenameClientProvider clientProvider;
    private final IRenameTaskPlusService taskService;
    private ScrapeService scrapeService;

    private static final Map<String, List<CategoryRule>> rules = defaultRules();
    private static final String DEFAULT_FILENAME_TEMPLATE = "{{ title }} {% if year %} ({{ year }}) {% endif %}/{% if season %}Season {{ season }}/{% endif %}{{ title }} {% if year and not season %} ({{ year }}) {% endif %}{% if season %}S{{ season }}{% endif %}{% if episode %}E{{ episode }}{% endif %}{% if resolution %} - {{ resolution }}{% endif %}{% if source %}.{{ source }}{% endif %}{% if videoCodec %}.{{ videoCodec }}{% endif %}{% if audioCodec %}.{{ audioCodec }}{% endif %}{% if tags is not empty %}.{{ tags|join('.') }}{% endif %}{% if releaseGroup %}-{{ releaseGroup }}{% endif %}.{{ extension }}";

    private static final ConcurrentMap<String, Object> FILE_LOCKS = new ConcurrentHashMap<>();
    private final Set<Path> processing = ConcurrentHashMap.newKeySet();


    public MediaRenameProcessor(
            Path targetRoot,
            RenameClientProvider clientProvider,
            OpenListHelper openListHelper,
            OpenlistConfig config,
            RenameEventListener renameListener
    ) {
        this.targetRoot = targetRoot;
        this.renameListener = renameListener;
        this.openListHelper = openListHelper;
        this.minFileSizeBytes =
                Optional.ofNullable(config)
                        .map(OpenlistConfig::getOpenListMinFileSize)
                        .filter(StringUtils::isNotBlank)
                        .map(v -> Long.parseLong(v) * 1024 * 1024)
                        .orElse(10 * 1024 * 1024L);
        this.clientProvider = clientProvider;
        this.taskService = SpringUtils.getBean(IRenameTaskPlusService.class);
        this.scrapeService = SpringUtils.getBean(ScrapeService.class);
    }

    @Override
    public void process(Path file) {
        Path p = file.toAbsolutePath().normalize();
        String fileName = p.toString();
        // 1. 忽略常见的临时文件后缀 (根据你的下载软件调整，如 .!qB, .part, .downloading)
        if (fileName.endsWith(".!qB") || fileName.endsWith(".part") || fileName.endsWith(".tmp")) {
            return;
        }
        //判断文件是否还在写入中
        if (!isFileStable(p)) {
            log.debug("文件仍在写入，稍后再试：{}", p);
            return;
        }
        if (!processing.add(p)) {
            log.debug("Skip duplicate processing {}", p);
            return;
        }
        try {
            handleFile(p);
        } catch (Exception e) {
            log.error("process failed {}", p, e);
        } finally {
            processing.remove(p);
        }
    }

    public void handleOneFile(Path file, String title, String year, String season, String episode) {
        try {
            handleFileIfReady(file, title, year, season, episode);
        } catch (IOException e) {
            log.error("", e);
        }
    }

    /**
     * //     * 执行一次完整扫描并处理源目录下的所有文件（用于手动/批量触发任务）。
     * //     * 与长期监控不同：不会注册 WatchService，仅遍历现有文件并按常规流程处理。
     * //
     */
    public void processOnce(Path sourceDir) {
        try {
            if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
                log.warn("processOnce: sourceDir does not exist or not a directory: {}", sourceDir);
                return;
            }
            IRenameDetailPlusService renameDetailPlusService = SpringUtils.getBean(IRenameDetailPlusService.class);
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path p = file.toAbsolutePath().normalize();
                    //判断是否有处理成功的数据
                    long count = renameDetailPlusService.lambdaQuery().eq(RenameDetailPlus::getOriginalPath, p.getParent().toString())
                            .eq(RenameDetailPlus::getOriginalName, p.getFileName().toString())
                            .eq(RenameDetailPlus::getStatus, "1")
                            .count();
                    if (count > 0) {
                        return FileVisitResult.CONTINUE;
                    }
                    processing.add(p);
                    try {
                        handleFileIfReady(p);
                    } catch (Exception e) {
                        log.error("processOnce handleFileIfReady failed for {}", p, e);
                    } finally {
                        processing.remove(p);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("processOnce failed", e);
        }
    }

    private void handleFile(Path file) throws Exception {
        handleFileIfReady(file);
    }

    private void handleFileIfReady(Path file) throws IOException {
        handleFileIfReady(file, null, null, null, null);
    }

    private void handleFileIfReady(Path file, String title, String year, String season, String episode) throws IOException {
        // Step 1: 验证文件是否就绪
        Path p = file.toAbsolutePath().normalize();
        if (!validateFile(p)) return;

        // Step 2: 解析媒体信息
        MediaParser parser = new MediaParser(clientProvider.tmdb(), clientProvider.openAI());
        MediaInfo info = parseMediaInfo(parser, p, title, year, season, episode);
        if (info == null) return;

        String mediaType = (info.getSeason() != null || info.getEpisode() != null) ? "tv" : "movie";
        if (info.getTmdbId() == null || info.getTmdbId().trim().isEmpty()) {
            log.info("未找到 tmdbId，跳过文件处理：{} ; parsed info title={}", p, info.getTitle());
            notifyFailed(p, info, mediaType, "tmdbId not found");
            return;
        }

        // Step 3: 构建目标路径
        Path[] destResult = buildDestPath(p, info, mediaType, parser);
        Path finalDestDir = destResult[0];
        Path destFile = destResult[1];

        // Step 4: 复制文件
        if (!copyFileToDest(p, destFile, finalDestDir, file.getFileName().toString())) return;

        // Step 5: 通知和刮削
        notifyAndScrape(p, destFile, info, mediaType, finalDestDir);
    }

    /** Step 1: 验证文件存在、类型和大小 */
    private boolean validateFile(Path p) throws IOException {
        if (!Files.exists(p) || Files.isDirectory(p)) return false;
        long size = Files.size(p);
        String filename = p.getFileName().toString();

        if (openListHelper != null) {
            boolean isStrm = openListHelper.isStrm(filename);
            boolean isVideo = openListHelper.isVideo(filename);
            if (!isStrm && !isVideo) {
                log.debug("Skipping non-video file {}", p);
                return false;
            }
            if (!isStrm && size < minFileSizeBytes) {
                log.debug("Skipping small file {} ({} bytes)", p, size);
                return false;
            }
        } else if (size < minFileSizeBytes) {
            log.debug("跳过小文件 {} ({} bytes)", p, size);
            return false;
        }

        if (!isFileStable(p)) {
            log.debug("文件仍在写入，稍后再试：{}", p);
            return false;
        }
        return true;
    }

    /** Step 2: 解析媒体信息 */
    private MediaInfo parseMediaInfo(MediaParser parser, Path p, String title, String year, String season, String episode) {
        MediaInfo info = parser.parse(p.getFileName().toString(), title, year);
        if (StringUtils.isNotEmpty(season)) info.setSeason(season);
        if (StringUtils.isNotEmpty(episode)) info.setEpisode(episode);
        return info;
    }

    /** Step 3: 构建目标路径，返回 [finalDestDir, destFile] */
    private Path[] buildDestPath(Path p, MediaInfo info, String mediaType, MediaParser parser) throws IOException {
        String category = classify(info, mediaType);
        if (category == null) category = "未分类";

        String topLevel = "movie".equals(mediaType) ? "电影" : "电视剧";
        Path destDir = targetRoot.resolve(topLevel).resolve(category);
        Files.createDirectories(destDir);

        String rendered = parser.render(info, DEFAULT_FILENAME_TEMPLATE);
        String filename = p.getFileName().toString();
        String newFilename = (rendered == null || rendered.trim().isEmpty()) ? filename : rendered.trim();
        newFilename = newFilename.replace('\\', '/');

        Path finalDestDir = destDir;
        String fileNameOnly = newFilename;
        if (newFilename.contains("/")) {
            String[] parts = newFilename.split("/");
            List<String> cleanParts = new ArrayList<>();
            for (String part : parts) {
                if (part == null) continue;
                part = part.trim();
                if (part.isEmpty() || ".".equals(part) || "..".equals(part)) continue;
                cleanParts.add(sanitizeForPath(part));
            }
            if (cleanParts.isEmpty()) {
                fileNameOnly = sanitizeForPath(filename);
            } else {
                for (int i = 0; i < cleanParts.size() - 1; i++) {
                    finalDestDir = finalDestDir.resolve(cleanParts.get(i));
                }
                fileNameOnly = cleanParts.get(cleanParts.size() - 1);
            }
        } else {
            fileNameOnly = sanitizeForPath(newFilename);
        }

        Files.createDirectories(finalDestDir);
        return new Path[]{finalDestDir, finalDestDir.resolve(fileNameOnly)};
    }

    /** Step 4: 复制文件到目标路径（含文件锁保护） */
    private boolean copyFileToDest(Path source, Path destFile, Path finalDestDir, String originalFilename) throws IOException {
        String lockKey = destFile.toAbsolutePath().normalize().toString();
        Object lock = FILE_LOCKS.computeIfAbsent(lockKey, k -> new Object());
        try {
            synchronized (lock) {
                Path tmpFile = finalDestDir.resolve(destFile.getFileName().toString() + ".tmp");
                try {
                    if (openListHelper.isStrm(originalFilename)) {
                        Files.copy(source, destFile, REPLACE_EXISTING);
                    } else {
                        Files.copy(source, tmpFile, REPLACE_EXISTING);
                        Files.move(tmpFile, destFile, StandardCopyOption.ATOMIC_MOVE);
                    }
                    log.info("已复制并重命名 {} -> {}", source, destFile);
                } finally {
                    Files.deleteIfExists(tmpFile);
                }
            }
        } finally {
            FILE_LOCKS.remove(lockKey, lock);
        }
        return true;
    }

    /** Step 5: 通知监听器并触发刮削 */
    private void notifyAndScrape(Path source, Path destFile, MediaInfo info, String mediaType, Path outputDir) {
        Integer detailId = null;
        if (renameListener != null) {
            try {
                detailId = renameListener.onRename(source, destFile, info, mediaType);
            } catch (Exception e) {
                log.warn("renameListener failed: {}", e.getMessage());
            }
        }
        try {
            scrapeAsyncFile(destFile, info, mediaType, outputDir, detailId);
        } catch (Exception e) {
            log.warn("启动刮削失败: {}", e.getMessage());
        }
    }

    private void notifyFailed(Path p, MediaInfo info, String mediaType, String reason) {
        if (renameListener != null) {
            try {
                renameListener.onRenameFailed(p, targetRoot, info, mediaType, reason);
            } catch (Exception e) {
                log.warn("renameListener.onRenameFailed failed: {}", e.getMessage());
            }
        }
    }

    /**
     * 异步触发刮削（NFO + 图片下载）。
     * 从任务配置中读取 scrape 开关。
     */
    private void scrapeAsyncFile(Path destFile, MediaInfo info, String mediaType, Path outputDir, Integer detailId) {
        try {
            // 直接使用 processor 的 targetRoot 字段查询对应的任务配置
            String targetRootPath = targetRoot.toString().replaceAll("/+$", "");
            RenameTaskPlus task = taskService.getOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RenameTaskPlus>()
                            .eq(RenameTaskPlus::getTargetRoot, targetRootPath)
                            .last("LIMIT 1")
            );
            if (task == null) {
                log.warn("未找到 targetRoot={} 对应的重命名任务，跳过刮削", targetRootPath);
                return;
            }

            // 检查刮削配置：只有启用了刮削才执行
            if (!"1".equals(task.getScrapeEnabled())) {
                log.debug("任务 {} 未启用刮削，跳过", task.getId());
                return;
            }

            scrapeService.scrapeAsync(
                    detailId, info, mediaType, destFile, outputDir,
                    task.getScrapeEnabled(), task.getScrapeNfo(), task.getScrapeImages(),
                    "1".equals(task.getScrapeForceOverwrite())
            );
        } catch (Exception e) {
            log.warn("查询任务配置失败: {}", e.getMessage());
        }
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

    private static Map<String, List<CategoryRule>> defaultRules() {
        Map<String, List<CategoryRule>> m = new HashMap<>();
        // movie rules in order
        List<CategoryRule> movie = new ArrayList<>();
        movie.add(new CategoryRule("动画电影").withGenreIds("16"));
        movie.add(new CategoryRule("华语电影").withOriginalLanguage("zh", "cn", "bo", "za"));
        movie.add(new CategoryRule("外语电影")); // fallback if not matched above
        m.put("movie", movie);

        // tv rules
        List<CategoryRule> tv = new ArrayList<>();
        tv.add(new CategoryRule("国漫").withGenreIds("16").withOriginCountry("CN", "TW", "HK"));
        tv.add(new CategoryRule("日番").withGenreIds("16").withOriginCountry("JP"));
        tv.add(new CategoryRule("纪录片").withGenreIds("99"));
        tv.add(new CategoryRule("儿童").withGenreIds("10762"));
        tv.add(new CategoryRule("综艺").withGenreIds("10764", "10767"));
        tv.add(new CategoryRule("国产剧").withOriginCountry("CN", "TW", "HK"));
        tv.add(new CategoryRule("欧美剧").withOriginCountry("US", "FR", "GB", "DE", "ES", "IT", "NL", "PT", "RU", "UK"));
        tv.add(new CategoryRule("日韩剧").withOriginCountry("JP", "KP", "KR", "TH", "IN", "SG"));
        tv.add(new CategoryRule("未分类"));
        m.put("tv", tv);

        return Collections.unmodifiableMap(m);
    }

    private static boolean isFileStable(Path p) {
        try {
            long s1 = Files.size(p);
            long t1 = Files.getLastModifiedTime(p).toMillis();
            TimeUnit.SECONDS.sleep(2);
            long s2 = Files.size(p);
            long t2 = Files.getLastModifiedTime(p).toMillis();
            return s1 == s2 && t1 == t2;
        } catch (Exception ignored) {
        }
        return false;
    }

}
