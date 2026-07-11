package com.ruoyi.openliststrm.scrape;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 从 TMDb 下载媒体图片（海报、背景图等）。
 * 使用项目已有的 OkHttp 客户端，避免引入新依赖。
 */
@Slf4j
@Component
public class MediaImageDownloader {

    private static final String TMDb_IMG_BASE = "https://image.tmdb.org/t/p/original";
    private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

    private final OkHttpClient client;
    private final OpenlistConfig config;

    public MediaImageDownloader(OpenlistConfig config) {
        this.config = config;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 下载电影图片: poster.jpg, backdrop.jpg, logo.png
     */
    public void downloadMovieImages(MediaInfo info, Path outputDir) {
        JsonNode details = getDetails(info);
        if (details == null) return;

        downloadImage(details, "poster", "poster.jpg", outputDir);
        downloadImage(details, "backdrop", "backdrop.jpg", outputDir);
        downloadLogo(details, outputDir);
    }

    /**
     * 下载剧集图片: poster.jpg, backdrop.jpg, logo.png
     */
    public void downloadTvImages(MediaInfo info, Path outputDir) {
        JsonNode details = getDetails(info);
        if (details == null) return;

        downloadImage(details, "poster", "poster.jpg", outputDir);
        downloadImage(details, "backdrop", "backdrop.jpg", outputDir);
        downloadLogo(details, outputDir);
    }

    private void downloadImage(JsonNode details, String type, String filename, Path outputDir) {
        try {
            String imgUrl = extractImageUrl(details, type);
            if (imgUrl == null) {
                log.debug("未找到 {} 图片", type);
                return;
            }
            Path target = outputDir.resolve(filename);
            downloadToFile(imgUrl, target);
            log.info("下载 {} 图片: {} -> {}", type, imgUrl, target);
        } catch (Exception e) {
            log.warn("下载 {} 图片失败: {}", type, e.getMessage());
        }
    }

    private void downloadLogo(JsonNode details, Path outputDir) {
        try {
            String imgUrl = extractLogoUrl(details);
            if (imgUrl == null) return;
            Path target = outputDir.resolve("logo.png");
            downloadToFile(imgUrl, target);
            log.info("下载 logo 图片: {} -> {}", imgUrl, target);
        } catch (Exception e) {
            log.warn("下载 logo 图片失败: {}", e.getMessage());
        }
    }

    private String extractImageUrl(JsonNode details, String type) {
        // 优先从 images 节点获取
        JsonNode images = details.path("images");
        if (images.has(type) && images.get(type).isArray() && images.get(type).size() > 0) {
            JsonNode file = images.get(type).get(0);
            String filePath = file.path("file_path").asText(null);
            if (filePath != null) {
                return TMDb_IMG_BASE + filePath;
            }
        }
        // 回退: 某些 TMDb 响应使用 backdrop_path / poster_path 顶级字段
        if ("backdrop".equals(type) && details.has("backdrop_path")) {
            return TMDb_IMG_BASE + details.get("backdrop_path").asText();
        }
        if ("poster".equals(type) && details.has("poster_path")) {
            return TMDb_IMG_BASE + details.get("poster_path").asText();
        }
        return null;
    }

    private String extractLogoUrl(JsonNode details) {
        JsonNode images = details.path("images");
        if (images.has("logos") && images.get("logos").isArray() && images.get("logos").size() > 0) {
            // 优先选择中文 logo
            String lang = config.getTmdbImageLanguage();
            for (JsonNode logo : images.get("logos")) {
                String filePath = logo.path("file_path").asText(null);
                String isoLang = logo.path("iso_639_1").asText("");
                if (filePath != null && lang.equals(isoLang)) {
                    return TMDb_IMG_BASE + filePath;
                }
            }
            // 回退到第一个
            JsonNode first = images.get("logos").get(0);
            String filePath = first.path("file_path").asText(null);
            if (filePath != null) {
                return TMDb_IMG_BASE + filePath;
            }
        }
        return null;
    }

    private void downloadToFile(String urlStr, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Request request = new Request.Builder().url(urlStr).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            try (InputStream in = response.body().byteStream();
                 OutputStream out = Files.newOutputStream(target)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    private JsonNode getDetails(MediaInfo info) {
        if (info.getMetadata() == null) return null;
        Object details = info.getMetadata().get("details");
        if (details instanceof JsonNode) {
            return (JsonNode) details;
        }
        return null;
    }
}
