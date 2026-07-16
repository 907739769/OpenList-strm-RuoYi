package com.ruoyi.openliststrm.tmdb;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * TMDb 网络请求封装，已配置防乱七八糟缓存策略。
 */
@Slf4j
@Service
public class TMDbApiService {
    private static final String BASE = "https://api.tmdb.org/3";
    private static final String LANGUAGE = "zh-CN";

    private final OkHttpClient http;

    public TMDbApiService() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS))
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
     * 接口: GET /tv/{id}/images
     * 返回: { posters: [...], backdrops: [...], logos: [...], stills: [...] }
     * include_image_language: zh,en,null - 返回中文、英文、无语言标签的图片
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
     * 接口: GET /movie/{id}/images
     * 返回: { posters: [...], backdrops: [...], logos: [...], stills: [...] }
     * include_image_language: zh,en,null - 返回中文、英文、无语言标签的图片
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
     * 接口: GET /{type}/{id}/external_ids
     * 返回: { imdb_id, tvdb_id, ... }
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
     * 接口: GET /movie/{id}/release_dates
     * 返回: { results: [{ iso_3166_1, release_dates: [{ certification, ... }] }] }
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
     * 接口: GET /tv/{id}/season/{season_number}/images
     * 返回: { posters: [...], ... }
     * include_image_language: zh,en,null
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
     * 接口: GET /tv/{id}/content_ratings
     * 返回: { results: [{ iso_3166_1, rating }] }
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

    // --- 抽取公共的 HTTP 执行逻辑 ---
    private String executeAndReturnString(Request req, String methodName) {
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                log.warn(methodName + " failed with status code " + resp.code());
                log.debug(methodName + " failed with resp " + resp);
                return null;
            }
            return resp.body().string(); // 直接返回纯文本JSON，缓存它！
        } catch (IOException e) {
            log.warn("TMDb {} failed: {}", methodName, e.getMessage());
            return null;
        }
    }
}