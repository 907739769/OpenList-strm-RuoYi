package com.ruoyi.openliststrm.tmdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @Author Jack
 * @Date 2025/8/12 16:53
 * @Version 1.1.0
 */
public class TMDbClient {
    private static final String BASE = "https://api.tmdb.org/3";
    private static final String LANGUAGE = "zh-CN";
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;

    public TMDbClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public void enrich(MediaInfo info) {
        if (StringUtils.isEmpty(apiKey)) return;
        String query = guessQuery(info);
        if (StringUtils.isEmpty(query)) return;

        try {
            String tmdbTitle = null;
            if (maybeTV(info)) {
                tmdbTitle = search("tv", info);
            }
            if (StringUtils.isEmpty(tmdbTitle)) {
                tmdbTitle = search("movie", info);
            }
            if (StringUtils.isNotEmpty(tmdbTitle)) {
                info.setTitle(tmdbTitle);
            }
        } catch (Exception e) {
            // ignore network errors, keep original title
        }
    }

    private String guessQuery(MediaInfo info) {
        if (info.getOriginalTitle() == null) return null;
        Object eng = info.getExtra().get("englishTitle");
        if (eng != null) return eng.toString();
        return info.getOriginalTitle();
    }

    private boolean maybeTV(MediaInfo info) {
        return info.getSeason() != null ||
                (info.getOriginalTitle() != null && info.getOriginalTitle().matches("(?i).*S\\d{1,2}.*"));
    }

    /**
     * 通用搜索（重构版）
     */
    private String search(String type, MediaInfo info) throws IOException {
        if (StringUtils.isBlank(type) || info == null) return null;

        // 构建候选查询：优先 guessQuery，必要时回退 originalTitle
        List<String> candidates = new ArrayList<>();
        candidates.add(guessQuery(info));
        Object englishTitle = (info.getExtra() != null) ? info.getExtra().get("englishTitle") : null;
        if (englishTitle != null && StringUtils.isNotBlank(info.getOriginalTitle())) {
            candidates.add(info.getOriginalTitle());
        }

        // 逐一尝试（去重 + 过滤空串）
        for (String q : candidates.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList())) {

            HttpUrl url = buildSearchUrl(type, q, info.getYear());
            String title = doSearchOnce(type, info, url);
            if (title != null) return title;
        }
        return null;
    }

    private HttpUrl buildSearchUrl(String type, String query, String year) {
        HttpUrl.Builder b = HttpUrl.parse(BASE + "/search/" + type).newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("query", query)
                .addQueryParameter("language", LANGUAGE);

        if (StringUtils.isNotBlank(year)) {
            String yearKey = "movie".equals(type) ? "year" : "first_air_date_year";
            b.addQueryParameter(yearKey, year);
        }
        return b.build();
    }

    /**
     * 执行一次请求并解析首个结果；若 info.year 为空则回填
     */
    private String doSearchOnce(String type, MediaInfo info, HttpUrl url) throws IOException {
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;

            JsonNode root = mapper.readTree(resp.body().byteStream());
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) return null;

            JsonNode first = results.get(0);

            // 回填年份
            if (StringUtils.isBlank(info.getYear())) {
                info.setYear(getYearSafe(first, type));
            }

            int id = first.path("id").asInt(-1);
            return (id > 0) ? getBestTitle(type, first, id) : null;
        }
    }

    private String getBestTitle(String type, JsonNode result, int id) throws IOException {
        String title = getOfficialChineseTitle(result, type);
        if (StringUtils.isNotEmpty(title)) return title;

        title = fetchChineseAlias(type, id);
        if (StringUtils.isNotEmpty(title)) return title;

        return fallbackTitle(result, type);
    }

    private String getOfficialChineseTitle(JsonNode result, String type) {
        String name = type.equals("movie") ? result.get("title").asText() : result.get("name").asText();
        if (isChinese(name)) {
            return name;
        }
        return null;
    }

    private String fetchChineseAlias(String type, int id) throws IOException {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE + "/" + type + "/" + id + "/alternative_titles"))
                .newBuilder().addQueryParameter("api_key", apiKey).build();
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) return null;

            JsonNode root = mapper.readTree(resp.body().byteStream());
            System.out.println("fetchChineseAlias" + root);

            JsonNode titles = type.equals("movie") ? root.get("titles") : root.get("results");
            if (titles != null) {
                for (JsonNode t : titles) {
                    if (t.has("iso_3166_1") && "CN".equals(t.get("iso_3166_1").asText())) {
                        String title = t.has("title") ? t.get("title").asText() :
                                t.has("name") ? t.get("name").asText() : null;
                        if (isChinese(title)) return title;
                    }
                }
            }
        }
        return null;
    }

    private String fallbackTitle(JsonNode result, String type) {
        return type.equals("movie") ? result.get("original_title").asText()
                : result.get("original_name").asText();
    }

    private String getYearSafe(JsonNode result, String type) {
        String dateField = type.equals("movie") ? "release_date" : "first_air_date";
        if (result.hasNonNull(dateField)) {
            String date = result.get(dateField).asText();
            if (date != null && date.length() >= 4) {
                return date.substring(0, 4);
            }
        }
        return "";
    }

    private boolean isChinese(String text) {
        return text != null && text.matches(".*[\\u4E00-\\u9FFF].*");
    }
}
