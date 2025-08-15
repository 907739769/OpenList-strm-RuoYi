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
import java.util.Objects;

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
     * 通用搜索
     */
    private String search(String type, MediaInfo info) throws IOException {
        HttpUrl.Builder b = HttpUrl.parse(BASE + "/search/" + type).newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("query", guessQuery(info))
                .addQueryParameter("language", LANGUAGE);

        if (StringUtils.isNotEmpty(info.getYear())) {
            String yearKey = type.equals("movie") ? "year" : "first_air_date_year";
            b.addQueryParameter(yearKey, info.getYear());
        }

        Request req = new Request.Builder().url(b.build()).get().build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) return null;

            JsonNode root = mapper.readTree(resp.body().byteStream());
            System.out.println("search" + root);
            JsonNode results = root.get("results");
            if (results != null && !results.isEmpty()) {
                JsonNode r = results.get(0);

                // 写回年份
                if (StringUtils.isEmpty(info.getYear())) {
                    info.setYear(getYearSafe(r, type));
                }

                int id = r.get("id").asInt();
                return getBestTitle(type, r, id);
            }
        }
        return null;
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
