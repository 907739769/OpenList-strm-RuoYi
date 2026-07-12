package com.ruoyi.openliststrm.tmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper mapper;

    public TMDbApiService() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS))
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * 搜索接口
     * 优化点: 去掉 apiKey 参与 key 生成；返回 String 防止引用污染
     */
    @Cacheable(value = "tmdbSearch", key = "#p2 + ':' + #p3 + ':' + (#p4==null?'':#p4)", unless = "#result == null")
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
    @Cacheable(value = "tmdbDetails", key = "#p2 + ':' + #p3", unless = "#result == null")
    public String getDetails(String apiKey, String type, int id) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/" + type + "/" + id))
                .newBuilder().addQueryParameter("api_key", apiKey).addQueryParameter("language", LANGUAGE).build();
        Request req = new Request.Builder().url(url).get().build();
        return executeAndReturnString(req, "getDetails");
    }

    /**
     * 获取备用标题
     */
    @Cacheable(value = "tmdbAlts", key = "#p2 + ':' + #p3", unless = "#result == null")
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
    @Cacheable(value = "tmdbSeason", key = "#p2 + ':' + #p3 + ':' + #p4", unless = "#result == null")
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
     */
    @Cacheable(value = "tmdbTvImages", key = "#p2 + ':' + #p3", unless = "#result == null")
    public String getTvImages(String apiKey, int tvId) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/tv/" + tvId + "/images"))
                .newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("language", LANGUAGE)
                .addQueryParameter("include_image_language", LANGUAGE + ",en,null")
                .build();
        Request req = new Request.Builder().url(url).get().build();
        return executeAndReturnString(req, "getTvImages");
    }

    // --- 抽取公共的 HTTP 执行逻辑 ---
    private String executeAndReturnString(Request req, String methodName) {
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            return resp.body().string(); // 直接返回纯文本JSON，缓存它！
        } catch (IOException e) {
            log.warn("TMDb {} failed: {}", methodName, e.getMessage());
            return null;
        }
    }
}