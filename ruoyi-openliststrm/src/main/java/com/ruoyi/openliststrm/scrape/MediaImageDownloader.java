package com.ruoyi.openliststrm.scrape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
     * 下载电影图片: poster.jpg, fanart.jpg, clearlogo.png
     * 合并 details 和 images 数据到一个节点中，方便统一处理
     */
    public void downloadMovieImages(MediaInfo info, Path outputDir, boolean forceOverwrite) {
        JsonNode details = getDetails(info);
        if (details == null) return;

        // 合并 images 数据到 details 中
        JsonNode imagesNode = getImagesFromMetadata(info);
        if (imagesNode != null) {
            // 使用 ObjectMapper 合并节点
            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode merged = mapper.valueToTree(details);
                if (imagesNode.has("posters")) merged.set("posters", imagesNode.get("posters"));
                if (imagesNode.has("backdrops")) merged.set("backdrops", imagesNode.get("backdrops"));
                if (imagesNode.has("logos")) merged.set("logos", imagesNode.get("logos"));
                details = merged;
            } catch (Exception e) {
                log.warn("合并 images 数据失败: {}", e.getMessage());
            }
        }

        downloadImage(details, "poster", "poster.jpg", outputDir, forceOverwrite);
        downloadImage(details, "backdrop", "fanart.jpg", outputDir, forceOverwrite);
        downloadLogo(details, outputDir, forceOverwrite);
    }

    /**
     * 下载剧集图片: poster.jpg, fanart.jpg, clearlogo.png
     * 合并 details 和 images 数据到一个节点中，方便统一处理
     */
    public void downloadTvImages(MediaInfo info, Path outputDir, boolean forceOverwrite) {
        JsonNode details = getDetails(info);
        if (details == null) return;

        // 合并 images 数据到 details 中
        JsonNode imagesNode = getImagesFromMetadata(info);
        if (imagesNode != null) {
            // 使用 ObjectMapper 合并节点
            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode merged = mapper.valueToTree(details);
                if (imagesNode.has("posters")) merged.set("posters", imagesNode.get("posters"));
                if (imagesNode.has("backdrops")) merged.set("backdrops", imagesNode.get("backdrops"));
                if (imagesNode.has("logos")) merged.set("logos", imagesNode.get("logos"));
                details = merged;
            } catch (Exception e) {
                log.warn("合并 images 数据失败: {}", e.getMessage());
            }
        }

        downloadImage(details, "poster", "poster.jpg", outputDir, forceOverwrite);
        downloadImage(details, "backdrop", "fanart.jpg", outputDir, forceOverwrite);
        downloadLogo(details, outputDir, forceOverwrite);
    }

    private void downloadImage(JsonNode details, String type, String filename, Path outputDir, boolean forceOverwrite) {
        try {
            String imgUrl = extractImageUrl(details, type);
            if (imgUrl == null) {
                log.debug("未找到 {} 图片", type);
                return;
            }
            Path target = outputDir.resolve(filename);
            if (Files.exists(target) && !forceOverwrite) {
                log.debug("图片已存在，跳过: {}", target);
                return;
            }
            downloadToFile(imgUrl, target);
            log.info("下载 {} 图片: {} -> {}", type, imgUrl, target);
        } catch (Exception e) {
            log.warn("下载 {} 图片失败: {}", type, e.getMessage());
        }
    }

    private void downloadLogo(JsonNode details, Path outputDir, boolean forceOverwrite) {
        try {
            String imgUrl = extractLogoUrl(details);
            if (imgUrl == null) return;
            Path target = outputDir.resolve("clearlogo.png");
            if (Files.exists(target) && !forceOverwrite) {
                log.debug("clearlogo 已存在，跳过: {}", target);
                return;
            }
            downloadToFile(imgUrl, target);
            log.info("下载 clearlogo 图片: {} -> {}", imgUrl, target);
        } catch (Exception e) {
            log.warn("下载 clearlogo 图片失败: {}", e.getMessage());
        }
    }

    private String extractImageUrl(JsonNode details, String type) {
        // images 数据已在 downloadMovieImages/downloadTvImages 中合并到 details 顶层
        // 映射: poster -> posters, backdrop -> backdrops
        String imagesKey = type.equals("poster") ? "posters" : 
                           type.equals("backdrop") ? "backdrops" : type;
        
        if (details.has(imagesKey) && details.get(imagesKey).isArray() && details.get(imagesKey).size() > 0) {
            // 按语言偏好选择: zh > en > null
            String preferredLang = config.getTmdbImageLanguage();
            String[] langPriority = {preferredLang, "en", ""};
            
            for (String lang : langPriority) {
                for (JsonNode file : details.get(imagesKey)) {
                    String filePath = file.path("file_path").asText(null);
                    String isoLang = file.path("iso_639_1").asText("");
                    if (filePath != null && lang.equals(isoLang)) {
                        return TMDb_IMG_BASE + filePath;
                    }
                }
            }
            // 回退到第一个
            JsonNode first = details.get(imagesKey).get(0);
            String filePath = first.path("file_path").asText(null);
            if (filePath != null) {
                return TMDb_IMG_BASE + filePath;
            }
        }
        
        // 回退: 从 details 顶级字段获取 (poster_path, backdrop_path)
        if ("backdrop".equals(type) && details.has("backdrop_path")) {
            return TMDb_IMG_BASE + details.get("backdrop_path").asText();
        }
        if ("poster".equals(type) && details.has("poster_path")) {
            return TMDb_IMG_BASE + details.get("poster_path").asText();
        }
        return null;
    }

    private String extractLogoUrl(JsonNode details) {
        // logos 数据已在 downloadMovieImages/downloadTvImages 中合并到 details 顶层
        if (details.has("logos") && details.get("logos").isArray() && details.get("logos").size() > 0) {
            // 按语言偏好选择: zh > en > null
            String preferredLang = config.getTmdbImageLanguage();
            String[] langPriority = {preferredLang, "en", ""};
            
            for (String lang : langPriority) {
                for (JsonNode logo : details.get("logos")) {
                    String filePath = logo.path("file_path").asText(null);
                    String isoLang = logo.path("iso_639_1").asText("");
                    if (filePath != null && lang.equals(isoLang)) {
                        return TMDb_IMG_BASE + filePath;
                    }
                }
            }
            // 回退到第一个
            JsonNode first = details.get("logos").get(0);
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

    private JsonNode getImagesFromMetadata(MediaInfo info) {
        if (info.getMetadata() == null) return null;
        Object images = info.getMetadata().get("images");
        if (images instanceof JsonNode) {
            return (JsonNode) images;
        }
        return null;
    }
}
