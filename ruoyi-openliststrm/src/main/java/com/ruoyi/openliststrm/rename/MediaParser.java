package com.ruoyi.openliststrm.rename;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.openai.OpenAIClient;
import com.ruoyi.openliststrm.rename.extractor.Extractor;
import com.ruoyi.openliststrm.rename.extractor.impl.*;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import com.ruoyi.openliststrm.rename.processor.TitleProcessor;
import com.ruoyi.openliststrm.rename.render.PebbleRenderer;
import com.ruoyi.openliststrm.tmdb.TMDbClient;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @Author Jack
 * @Date 2025/8/12 16:54
 * @Version 1.0.0
 */
@Slf4j
public class MediaParser {
    private final List<Extractor> extractors = new ArrayList<>();
    private final TitleProcessor titleProcessor = new TitleProcessor();
    private final TMDbClient tmdbClient; // may be null
    private final OpenAIClient openAIClient; // may be null
    private final PebbleRenderer renderer = new PebbleRenderer();

    public MediaParser(TMDbClient tmdbClient) {
        this(tmdbClient, null);
    }

    public MediaParser(TMDbClient tmdbClient, OpenAIClient openAIClient) {
        this.tmdbClient = tmdbClient;
        this.openAIClient = openAIClient;
        // 默认顺序（可在构造时注入）
        extractors.add(new ResolutionExtractor());
        extractors.add(new CodecExtractor());
        extractors.add(new SourceAndGroupExtractor());
        extractors.add(new YearSeasonEpisodeExtractor());
    }

    public String rename(String filename, String template) {
        MediaInfo info = parse(filename);
        return render(info, template);
    }

    public MediaInfo parse(String filename) {
        return parse(filename, null, null);
    }

    public MediaInfo parse(String filename,String title,String year) {
        log.info("开始重命名文件名: {}", filename);
        String extension = "";
        String baseName = filename;
        if (filename.lastIndexOf('.') != -1) {
            extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            baseName = filename.substring(0, filename.lastIndexOf('.')).trim();
        }
        String norm = normalize(baseName);
        MediaInfo info = new MediaInfo(filename);
        info.setExtension(extension);
        String remaining = norm;
        for (Extractor ex : extractors) {
            remaining = ex.extract(remaining, info);
        }
        titleProcessor.processTitle(remaining, info);
        if (StringUtils.isNotEmpty(title)) {
            info.setTitle(title);
        }
        if (StringUtils.isNotEmpty(year)) {
            info.setYear(year);
        }
        if (tmdbClient != null) {
            tmdbClient.enrich(info);
        }

        // Only call OpenAI when still missing key/accurate fields
        if (openAIClient != null && needsAI(info)) {
            try {
                log.info("使用AI识别: {}", filename);
                boolean updated = openAIClient.enrich(info, filename);
                // If updated, and tmdb client exists, we could re-run tmdb lookup with new titles
                if (updated && tmdbClient != null) {
                    tmdbClient.enrich(info);
                }
            } catch (Exception e) {
                log.info("使用AI识别失败: {}", filename);
                log.error("", e);
            }
        }

        log.debug("文件名称: {} 识别结果info: {}", filename, info);
        return info;
    }

    /**
     * 渲染输出，传入 Pebble 模板字符串
     */
    public String render(MediaInfo info, String template) {
        return renderer.render(info, template);
    }

    private String normalize(String s) {
        // replace _ and . with spaces, but keep 中文
        String t = s.replace('_', ' ').replaceAll("\\.(?=[A-Za-z0-9])", " ");
        return t.trim();
    }

    /**
     * Decide whether to call AI: if title/originalTitle missing, or for TV items season/episode missing.
     * If TMDb already provided tmdbId and title/year/season+episode are present, we don't call AI.
     */
    private boolean needsAI(MediaInfo info) {
        return StringUtils.isBlank(info.getTmdbId());
    }

}