package com.ruoyi.openliststrm.tmdb;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TMDb 网络请求封装，含数据库缓存、指数退避重试、速率限制。
 */
@Slf4j
@Service
public class TMDbApiService {
    private static final String BASE = "https://api.tmdb.org/3";
    private static final String LANGUAGE = "zh-CN";

    // --- 速率限制 ---
    /** 最多4个并发请求 */
    private static final Semaphore RATE_LIMITER = new Semaphore(4);
    /** 请求最小间隔 250ms（≈4 req/s，符合 TMDb 40req/10s 限制） */
    private static final long MIN_INTERVAL_MS = 250;
    private static final AtomicLong LAST_REQUEST_TIME = new AtomicLong(0);

    // --- 重试配置 ---
    private static final int MAX_RETRIES = 3;

    private final OkHttpClient http;

    @Autowired
    private TmdbCacheService tmdbCacheService;

    public TMDbApiService(@Qualifier("sharedOkHttpClient") OkHttpClient sharedClient) {
        this.http = sharedClient.newBuilder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 搜索接口
     * 优化点: 去掉 apiKey 参与 key 生成；返回 String 防止引用污染
     */
    @Cacheable(value = "tmdbSearch", key = "#p1 + ':' + #p2 + ':' + (#p3==null?'':#p3)", unless = "#result == null")
    public String search(String apiKey, String type, String query, String year) {
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(BASE + "/search/" + type)).newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("query", query)
                .addQueryParameter("language", LANGUAGE);
        if (year != null && !year.isEmpty()) {
            String yearKey = "movie".equals(type) ? "primary_release_year" : "first_air_date_year";
            b.addQueryParameter(yearKey, year);
        }
        Request req = new Request.Builder().url(b.build()).get().build();
        return executeAndReturnString(req, "search");
    }

    /**
     * 获取详情
     */
    @Cacheable(value = "tmdbDetails", key = "#p1 + ':' + #p2", unless = "#result == null")
    public String getDetails(String apiKey, String type, int id) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/" + type + "/" + id))
                .newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("language", LANGUAGE)
                .addQueryParameter("append_to_response", "credits,videos")
                .build();
        Request req = new Request.Builder().url(url).get().build();
        return executeAndReturnString(req, "getDetails");
    }

    /**
     * 获取备用标题
     */
    @Cacheable(value = "tmdbAlts", key = "#p1 + ':' + #p2", unless = "#result == null")
    public String getAlternativeTitles(String apiKey, String type, int id) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/" + type + "/" + id + "/alternative_titles"))
                .newBuilder().addQueryParameter("api_key", apiKey).build();
        Request req = new Request.Builder().url(url).get().build();
        return executeAndReturnString(req, "getAlternativeTitles");
    }

    /**
     * 获取季集列表（TV 专用）
     * 接口: GET /tv/{id}/season/{season_number}
     */
    @Cacheable(value = "tmdbSeason", key = "#p1 + ':' + #p2", unless = "#result == null")
    public String getSeasonEpisodes(String apiKey, int tvId, int seasonNumber) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/tv/" + tvId + "/season/" + seasonNumber))
                .newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("language", LANGUAGE)
                .build();
        Request req = new Request.Builder().url(url).get().build();
        return executeAndReturnString(req, "getSeasonEpisodes");
    }

    /**
     * 获取剧集图片（TV 专用）
     */
    @Cacheable(value = "tmdbTvImages", key = "#p1", unless = "#result == null")
    public String getTvImages(String apiKey, int tvId) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/tv/" + tvId + "/images"))
                .newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("include_image_language", "zh,en,null")
                .build();
        Request req = new Request.Builder().url(url).get().build();
        return executeAndReturnString(req, "getTvImages");
    }

    /**
     * 获取电影图片（Movie 专用）
     */
    @Cacheable(value = "tmdbMovieImages", key = "#p1", unless = "#result == null")
    public String getMovieImages(String apiKey, int movieId) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/movie/" + movieId + "/images"))
                .newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("include_image_language", "zh,en,null")
                .build();
        Request req = new Request.Builder().url(url).get().build();
        return executeAndReturnString(req, "getMovieImages");
    }

    /**
     * 获取外部 ID（IMDb、TVDb 等）
     */
    @Cacheable(value = "tmdbExternalIds", key = "#p1 + ':' + #p2", unless = "#result == null")
    public String getExternalIds(String apiKey, String type, int id) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/" + type + "/" + id + "/external_ids"))
                .newBuilder()
                .addQueryParameter("api_key", apiKey)
                .build();
        Request req = new Request.Builder().url(url).get().build();
        return executeAndReturnString(req, "getExternalIds");
    }

    /**
     * 获取电影上映/分级信息（Movie 专用）
     */
    @Cacheable(value = "tmdbReleaseDates", key = "#p1", unless = "#result == null")
    public String getMovieReleaseDates(String apiKey, int movieId) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/movie/" + movieId + "/release_dates"))
                .newBuilder()
                .addQueryParameter("api_key", apiKey)
                .build();
        Request req = new Request.Builder().url(url).get().build();
        return executeAndReturnString(req, "getMovieReleaseDates");
    }

    /**
     * 获取季级别图片（TV 专用）
     */
    @Cacheable(value = "tmdbSeasonImages", key = "#p1 + ':' + #p2", unless = "#result == null")
    public String getTvSeasonImages(String apiKey, int tvId, int seasonNumber) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/tv/" + tvId + "/season/" + seasonNumber + "/images"))
                .newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("include_image_language", "zh,en,null")
                .build();
        Request req = new Request.Builder().url(url).get().build();
        return executeAndReturnString(req, "getTvSeasonImages");
    }

    /**
     * 获取剧集内容分级（TV 专用）
     */
    @Cacheable(value = "tmdbContentRatings", key = "#p1", unless = "#result == null")
    public String getTvContentRatings(String apiKey, int tvId) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/tv/" + tvId + "/content_ratings"))
                .newBuilder()
                .addQueryParameter("api_key", apiKey)
                .build();
        Request req = new Request.Builder().url(url).get().build();
        return executeAndReturnString(req, "getTvContentRatings");
    }

    // ======================== 核心执行逻辑 ========================

    /**
     * 带数据库缓存 + 重试 + 速率限制的执行方法。
     * 缓存层：DB（持久化，跨重启）
     */
    private String executeAndReturnString(Request req, String methodName) {
        // 生成缓存 key（对 URL 去掉 api_key 参数后取 MD5）
        String cacheKey = buildCacheKey(req);
        String cacheType = methodName;

        // 1. 查数据库缓存
        try {
            String cached = tmdbCacheService.getCachedResponse(cacheKey, cacheType);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.debug("查询TMDb数据库缓存异常（忽略）: {}", e.getMessage());
        }

        // 2. 带重试的 HTTP 调用
        String result = executeWithRetry(req, methodName);

        // 3. 写入数据库缓存
        if (result != null) {
            try {
                tmdbCacheService.cacheResponse(cacheKey, cacheType, result, 0);
            } catch (Exception e) {
                log.debug("写入TMDb数据库缓存异常（忽略）: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 指数退避重试
     */
    private String executeWithRetry(Request req, String methodName) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return doHttpCall(req);
            } catch (IOException e) {
                if (attempt == MAX_RETRIES) {
                    log.error("TMDb API请求失败，已重试{}次: {} error={}", MAX_RETRIES, methodName, e.getMessage());
                    return null;
                }
                long delay = (long) Math.pow(2, attempt) * 1000L; // 1s, 2s, 4s
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                log.warn("TMDb API请求失败，第{}次重试（{}ms后）: {} error={}",
                        attempt + 1, delay, methodName, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 执行单次 HTTP 调用（含速率限制）
     */
    private String doHttpCall(Request req) throws IOException {
        // 速率限制：等待可用许可
        try {
            RATE_LIMITER.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("速率限制等待被中断", e);
        }
        try {
            // 保证最小间隔
            long now = System.currentTimeMillis();
            long lastTime = LAST_REQUEST_TIME.get();
            long elapsed = now - lastTime;
            if (elapsed < MIN_INTERVAL_MS) {
                try {
                    Thread.sleep(MIN_INTERVAL_MS - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("请求间隔等待被中断", e);
                }
            }
            LAST_REQUEST_TIME.set(System.currentTimeMillis());

            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    log.warn("TMDb请求失败，HTTP {}: {}", resp.code(), req.url());
                    throw new IOException("HTTP " + resp.code());
                }
                return resp.body().string();
            }
        } finally {
            RATE_LIMITER.release();
        }
    }

    /**
     * 生成缓存 key：对 URL 去掉 api_key 后取 MD5 摘要
     */
    private String buildCacheKey(Request req) {
        HttpUrl url = req.url();
        // 重建 URL 不含 api_key
        HttpUrl.Builder b = url.newBuilder().removeAllQueryParameters("api_key");
        String keyStr = b.build().toString();
        return md5(keyStr);
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 始终可用
            return String.valueOf(input.hashCode());
        }
    }
}
