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
     * 下载电影图片: poster.jpg, fanart.jpg, clearlogo.png, banner.jpg, clearart.png, landscape.jpg, thumb.jpg
     * 合并 details 和 images 数据到一个节点中，方便统一处理
     */
    public void downloadMovieImages(MediaInfo info, Path outputDir, boolean forceOverwrite) {
        JsonNode details = getDetails(info);
        if (details == null) return;

        // 合并 images 数据到 details 中
        details = mergeImages(details, getImagesFromMetadata(info));

        downloadImage(details, "poster", "poster.jpg", outputDir, forceOverwrite);
        downloadImage(details, "backdrop", "fanart.jpg", outputDir, forceOverwrite);
        downloadLogo(details, outputDir, forceOverwrite);
        downloadAdditionalImages(details, outputDir, forceOverwrite);
    }

    /**
     * 下载剧集图片: poster.jpg, fanart.jpg, clearlogo.png, banner.jpg, clearart.png, landscape.jpg, thumb.jpg
     * 合并 details 和 images 数据到一个节点中，方便统一处理
     */
    public void downloadTvImages(MediaInfo info, Path outputDir, boolean forceOverwrite) {
        JsonNode details = getDetails(info);
        if (details == null) return;

        // 合并 images 数据到 details 中
        details = mergeImages(details, getImagesFromMetadata(info));

        downloadImage(details, "poster", "poster.jpg", outputDir, forceOverwrite);
        downloadImage(details, "backdrop", "fanart.jpg", outputDir, forceOverwrite);
        downloadLogo(details, outputDir, forceOverwrite);
        downloadAdditionalImages(details, outputDir, forceOverwrite);
    }

    /**
     * 下载季级别图片: season-poster.jpg 到季目录
     */
    public void downloadSeasonPoster(MediaInfo info, Path seasonDir, boolean forceOverwrite) {
        if (info.getMetadata() == null) return;
        Object siObj = info.getMetadata().get("season_images");
        if (!(siObj instanceof JsonNode)) return;
        JsonNode seasonImages = (JsonNode) siObj;
        JsonNode posters = seasonImages.path("posters");
        if (!posters.isArray() || posters.size() == 0) return;

        String url = selectImageFromList(posters);
        if (url == null) return;

        Path target = seasonDir.resolve("season-poster.jpg");
        if (Files.exists(target) && !forceOverwrite) {
            log.debug("季海报已存在，跳过: {}", target);
            return;
        }
        try {
            downloadToFile(url, target);
            log.info("下载季海报图片: {} -> {}", url, target);
        } catch (Exception e) {
            log.warn("下载季海报图片失败: {}", e.getMessage());
        }
    }

    private JsonNode mergeImages(JsonNode details, JsonNode imagesNode) {
        if (imagesNode == null) return details;
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode merged = mapper.valueToTree(details);
            if (imagesNode.has("posters")) merged.set("posters", imagesNode.get("posters"));
            if (imagesNode.has("backdrops")) merged.set("backdrops", imagesNode.get("backdrops"));
            if (imagesNode.has("logos")) merged.set("logos", imagesNode.get("logos"));
            return merged;
        } catch (Exception e) {
            log.warn("合并 images 数据失败: {}", e.getMessage());
            return details;
        }
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
            String imgUrl = extractLogoUrl(details, 0);
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

    /**
     * 下载额外图片类型: banner, clearart, landscape, thumb
     */
    private void downloadAdditionalImages(JsonNode details, Path outputDir, boolean forceOverwrite) {
        // banner.jpg: 从 backdrops 中选择第二张（与 fanart 不同）
        downloadImageByIndex(details, "backdrops", "banner.jpg", 1, forceOverwrite, outputDir);

        // clearart.png: 从 logos 中选择第二张（与 clearlogo 不同）
        downloadImageByIndex(details, "logos", "clearart.png", 1, forceOverwrite, outputDir);

        // landscape.jpg: 从 backdrops 中选择第三张（或第二张如只有两张）
        downloadImageByIndex(details, "backdrops", "landscape.jpg", 2, forceOverwrite, outputDir);

        // thumb.jpg: 从 posters 中选择第二张（与 poster 不同）
        downloadImageByIndex(details, "posters", "thumb.jpg", 1, forceOverwrite, outputDir);
    }

    /**
     * 从图片列表中按索引下载图片，如果索引超出范围则回退到第一张
     */
    private void downloadImageByIndex(JsonNode details, String imagesKey, String filename,
                                      int preferredIndex, boolean forceOverwrite, Path outputDir) {
        try {
            JsonNode imagesArray = details.path(imagesKey);
            if (!imagesArray.isArray() || imagesArray.size() == 0) return;

            // 如果有足够的图片，使用指定索引；否则使用第一张
            int idx = Math.min(preferredIndex, imagesArray.size() - 1);
            String url = selectImageFromListAt(imagesArray, idx);
            if (url == null) return;

            Path target = outputDir.resolve(filename);
            if (Files.exists(target) && !forceOverwrite) {
                log.debug("{} 已存在，跳过: {}", filename, target);
                return;
            }
            downloadToFile(url, target);
            log.info("下载 {} 图片: {} -> {}", filename, url, target);
        } catch (Exception e) {
            log.warn("下载 {} 图片失败: {}", filename, e.getMessage());
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

    private String extractLogoUrl(JsonNode details, int preferredIndex) {
        // logos 数据已在 downloadMovieImages/downloadTvImages 中合并到 details 顶层
        if (details.has("logos") && details.get("logos").isArray() && details.get("logos").size() > 0) {
            return selectImageFromListAt(details.get("logos"), preferredIndex);
        }
        return null;
    }

    /**
     * 从图片列表中选择最佳图片（按语言偏好），返回完整 URL
     */
    private String selectImageFromList(JsonNode imageArray) {
        return selectImageFromListAt(imageArray, 0);
    }

    /**
     * 从图片列表中选择指定索引的图片（按语言偏好），返回完整 URL
     * 如果 preferredIndex 超出范围，则回退到第一张
     */
    private String selectImageFromListAt(JsonNode imageArray, int preferredIndex) {
        if (!imageArray.isArray() || imageArray.size() == 0) return null;

        String preferredLang = config.getTmdbImageLanguage();
        String[] langPriority = {preferredLang, "en", ""};

        // 首先尝试在 preferredIndex 附近按语言偏好选择
        int maxIdx = Math.min(preferredIndex, imageArray.size() - 1);

        // 从 preferredIndex 开始向前查找匹配语言的图片
        for (int i = maxIdx; i < imageArray.size(); i++) {
            JsonNode file = imageArray.get(i);
            String filePath = file.path("file_path").asText(null);
            if (filePath == null) continue;
            String isoLang = file.path("iso_639_1").asText("");
            if (preferredLang.equals(isoLang)) {
                return TMDb_IMG_BASE + filePath;
            }
        }
        // 再从头查找 en 语言
        for (int i = maxIdx; i < imageArray.size(); i++) {
            JsonNode file = imageArray.get(i);
            String filePath = file.path("file_path").asText(null);
            if (filePath == null) continue;
            String isoLang = file.path("iso_639_1").asText("");
            if ("en".equals(isoLang)) {
                return TMDb_IMG_BASE + filePath;
            }
        }
        // 回退: 使用 preferredIndex 处的图片
        JsonNode selected = imageArray.get(maxIdx);
        String filePath = selected.path("file_path").asText(null);
        if (filePath != null) {
            return TMDb_IMG_BASE + filePath;
        }
        // 最终回退: 使用第一张
        filePath = imageArray.get(0).path("file_path").asText(null);
        return filePath != null ? TMDb_IMG_BASE + filePath : null;
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
