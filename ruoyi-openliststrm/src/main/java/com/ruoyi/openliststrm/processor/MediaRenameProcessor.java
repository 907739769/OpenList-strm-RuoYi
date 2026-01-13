package com.ruoyi.openliststrm.processor;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.rename.CategoryRule;
import com.ruoyi.openliststrm.rename.MediaParser;
import com.ruoyi.openliststrm.rename.RenameClientProvider;
import com.ruoyi.openliststrm.rename.RenameEventListener;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
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
    }

    @Override
    public void process(Path file) {
        Path p = file.toAbsolutePath().normalize();
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
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path p = file.toAbsolutePath().normalize();
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
        Path p = file.toAbsolutePath().normalize();
        if (!Files.exists(p) || Files.isDirectory(p)) return; // nothing to do => treat as finished

        long size = Files.size(p);

        String filename = p.getFileName().toString();

        if (openListHelper != null) {
            boolean isStrm = openListHelper.isStrm(filename);
            boolean isVideo = openListHelper.isVideo(filename);
            if (!isStrm && !isVideo) {
                log.debug("Skipping non-video file {}", p);
                return;
            }
            // if not a .strm file, enforce minimum size; .strm files are processed regardless of size
            if (!isStrm && size < minFileSizeBytes) {
                log.debug("Skipping small file {} ({} bytes)", p, size);
                return;
            }
        } else {
            // legacy behavior when helper not available: enforce size threshold
            if (size < minFileSizeBytes) {
                log.debug("跳过小文件 {} ({} bytes)", p, size);
                return;
            }
        }
        //判断文件是否还在写入中
        if (!isFileStable(p)) {
            log.debug("文件仍在写入，稍后再试：{}", p);
            return;
        }

        MediaParser parser = new MediaParser(clientProvider.tmdb(), clientProvider.openAI());
        MediaInfo info = parser.parse(filename, title, year);
        if (StringUtils.isNotEmpty(season)) {
            info.setSeason(season);
        }
        if (StringUtils.isNotEmpty(episode)) {
            info.setEpisode(episode);
        }

        String mediaType = (info.getSeason() != null || info.getEpisode() != null) ? "tv" : "movie";
        if ((info.getTmdbId() == null || info.getTmdbId().trim().isEmpty())) {
            log.info("未找到 tmdbId，跳过文件处理：{} ; parsed info title={}", p, info.getTitle());
            if (renameListener != null) {
                try {
                    renameListener.onRenameFailed(p, targetRoot, info, mediaType, "tmdbId not found");
                } catch (Exception e) {
                    log.warn("renameListener.onRenameFailed failed: {}", e.getMessage());
                }
            }
            return;
        }

        String category = classify(info, mediaType);
        if (category == null) category = "未分类";

        // 第一层目录使用中文：电影 / 电视剧
        String topLevel = "movie".equals(mediaType) ? "电影" : "电视剧";
        Path destDir = targetRoot.resolve(topLevel).resolve(category);
        Files.createDirectories(destDir);

        String rendered = parser.render(info, DEFAULT_FILENAME_TEMPLATE);
        String newFilename = rendered == null || rendered.trim().isEmpty() ? filename : rendered.trim();
        // Normalize separators to forward slash for template-produced paths.
        newFilename = newFilename.replace('\\', '/');

        Path finalDestDir = destDir;
        String fileNameOnly = newFilename;
        if (newFilename.contains("/")) {
            String[] parts = newFilename.split("/");
            List<String> cleanParts = new ArrayList<>();
            for (String part : parts) {
                if (part == null) continue;
                part = part.trim();
                if (part.isEmpty()) continue; // skip empty segments
                // prevent directory traversal or relative segments
                if (".".equals(part) || "..".equals(part)) continue;
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

        Path destFile = finalDestDir.resolve(fileNameOnly);
        // 获取当前文件的锁对象
        String lockKey = destFile.toAbsolutePath().normalize().toString();
        Object lock = FILE_LOCKS.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            Path tmpFile = finalDestDir.resolve(fileNameOnly + ".tmp");
            try {
                if (openListHelper.isStrm(filename)) {
                    Files.copy(p, destFile, REPLACE_EXISTING);
                } else {
                    Files.copy(p, tmpFile, REPLACE_EXISTING);
                    Files.move(tmpFile, destFile, StandardCopyOption.ATOMIC_MOVE);
                }
                log.info("已复制并重命名 {} -> {}", p, destFile);
            } finally {
                Files.deleteIfExists(tmpFile);
            }
        }
        FILE_LOCKS.remove(lockKey, lock);

        // notify listener if present
        if (renameListener != null) {
            try {
                renameListener.onRename(p, destFile, info, mediaType);
            } catch (Exception e) {
                log.warn("renameListener failed: {}", e.getMessage());
            }
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

    private boolean isFileStable(Path p) {
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

}
