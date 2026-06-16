package com.ruoyi.openliststrm.tmdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * TMDb 网络请求封装，每次真实请求 API。
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
     */
    public JsonNode search(String apiKey, String type, String query, String year) throws IOException {
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(BASE + "/search/" + type)).newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("query", query)
                .addQueryParameter("language", LANGUAGE);
        if (year != null && !year.isEmpty()) {
            String yearKey = "movie".equals(type) ? "year" : "first_air_date_year";
            b.addQueryParameter(yearKey, year);
        }
        Request req = new Request.Builder().url(b.build()).get().build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            JsonNode result = mapper.readTree(resp.body().byteStream());
            return result;
        } catch (IOException e) {
            log.warn("TMDb search failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取详情
     */
    public JsonNode getDetails(String apiKey, String type, int id) throws IOException {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/" + type + "/" + id))
                .newBuilder().addQueryParameter("api_key", apiKey).addQueryParameter("language", LANGUAGE).build();
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            JsonNode result = mapper.readTree(resp.body().byteStream());
            return result;
        } catch (IOException e) {
            log.warn("TMDb getDetails failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取备用标题（alternative titles / TV results）
     */
    public JsonNode getAlternativeTitles(String apiKey, String type, int id) throws IOException {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/" + type + "/" + id + "/alternative_titles"))
                .newBuilder().addQueryParameter("api_key", apiKey).build();
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            JsonNode result = mapper.readTree(resp.body().byteStream());
            return result;
        } catch (IOException e) {
            log.warn("TMDb getAlternativeTitles failed: {}", e.getMessage());
            return null;
        }
    }

   }

