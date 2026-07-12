package com.ruoyi.openliststrm.tmdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import com.ruoyi.common.utils.spring.SpringUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TMDb client helper
 */
@Slf4j
public class TMDbClient {
    private final String apiKey;
    private final ObjectMapper mapper;

    public TMDbClient(String apiKey) {
        this.apiKey = apiKey;
        this.mapper = new ObjectMapper();
    }

    public void enrich(MediaInfo info) {
        if (StringUtils.isEmpty(apiKey)) return;

        try {
            String tmdbTitle;
            String type = maybeTV(info) ? "tv" : "movie";
            // get Spring bean that performs TMDb HTTP calls (and provides caching)
            TMDbApiService api = SpringUtils.getBean(TMDbApiService.class);
            tmdbTitle = search(type, info, api);
            if (StringUtils.isNotEmpty(tmdbTitle)) {
                info.setTitle(tmdbTitle);
            }

            // TV: 获取当前季的集详情
            if (maybeTV(info) && info.getSeason() != null && info.getTmdbId() != null) {
                try {
                    enrichEpisodeDetails(info, api);
                } catch (Exception e) {
                    log.warn("enrichEpisodeDetails failed for tvId={}, season={}: {}",
                            info.getTmdbId(), info.getSeason(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("TMDb enrich error", e);
        }
    }

    /**
     * 从 TMDB 获取指定季的集列表，并回填当前集的详情（集标题、播出日期、剧情、tmdbId）
     */
    private void enrichEpisodeDetails(MediaInfo info, TMDbApiService api) throws IOException {
        int tvId = Integer.parseInt(info.getTmdbId());
        int seasonNum = Integer.parseInt(info.getSeason().replaceAll("\\D", ""));
        String epJson = api.getSeasonEpisodes(apiKey, tvId, seasonNum);
        if (epJson == null) return;

        JsonNode seasonRoot = mapper.readTree(epJson);
        JsonNode episodes = seasonRoot.path("episodes");
        if (!episodes.isArray() || episodes.isEmpty()) return;

        int currentEpNum = 0;
        if (StringUtils.isNotEmpty(info.getEpisode())) {
            try {
                currentEpNum = Integer.parseInt(info.getEpisode().replaceAll("\\D", ""));
            } catch (NumberFormatException ignored) {
            }
        }

        for (JsonNode ep : episodes) {
            int epNum = ep.path("episode_number").asInt(0);
            if (epNum != currentEpNum) continue;

            // 集标题（优先中文）
            String epName = ep.path("name").asText(null);
            if (StringUtils.isNotEmpty(epName) && isChinese(epName)) {
                // 尝试从 alternative_titles 获取中文
                info.setEpisodeName(epName);
            } else {
                info.setEpisodeName(epName);
            }

            // 单集 tmdbId (tv episode id)
            String epTmdbId = ep.path("id").asText(null);
            if (StringUtils.isNotEmpty(epTmdbId)) {
                info.setEpisodeTmdbId(epTmdbId);
            }

            // 播出日期
            String airDate = ep.path("air_date").asText(null);
            if (StringUtils.isNotEmpty(airDate)) {
                info.setEpisodeAiredDate(airDate);
            }

            // 剧情简介
            String overview = ep.path("overview").asText(null);
            if (StringUtils.isNotEmpty(overview)) {
                info.setEpisodePlot(overview);
            }

            break; // 找到当前集后退出
        }
    }

    private boolean maybeTV(MediaInfo info) {
        return info.getSeason() != null ||
                (info.getOriginalTitle() != null && info.getOriginalTitle().matches("(?i).*S\\d{1,2}.*"));
    }

    /**
     * 通用搜索（重构版）
     */
    private String search(String type, MediaInfo info, TMDbApiService api) throws IOException {
        if (StringUtils.isBlank(type) || info == null) return null;

        List<String> candidates = new ArrayList<>();
        candidates.add(info.getTitle());
        candidates.add(info.getOriginalTitle());
        candidates.add(info.getEnglishTitle());
        String title;
        // 逐一尝试（去重 + 过滤空串）
        for (String q : candidates.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList())) {
            log.debug("尝试根据标题查询TMDB：{}", q);
            JsonNode root = mapper.readTree(api.search(apiKey, type, q, info.getYear()));
            title = doSearchOnce(type, info, root, api);
            if (title != null) return title;
        }

        for (String q : candidates.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList())) {
            log.debug("尝试只根据标题查询TMDB，不限定年份：{}", q);
            JsonNode root = mapper.readTree(api.search(apiKey, type, q, null));
            title = doSearchOnce(type, info, root, api);
            if (title != null) return title;
        }

        return null;
    }

    /**
     * 处理 search 返回的 JsonNode（来自 TMDbApiService），解析首个结果
     */
    private String doSearchOnce(String type, MediaInfo info, com.fasterxml.jackson.databind.JsonNode root, TMDbApiService api) throws IOException {
        if (root == null) return null;
        JsonNode results = root.path("results");
        log.debug("doSearchOnce: {} results: {}", info.getOriginalName(), results);
        if (!results.isArray() || results.isEmpty()) return null;

        JsonNode first = null;
        if (StringUtils.isNotEmpty(info.getOriginalTitle()) && results.size() > 1) {
            for (int i = 0; i < results.size(); i++) {
                JsonNode node = results.get(i);
                if (info.getOriginalTitle().equals(getOfficialChineseTitle(node, type))) {
                    first = node;
                    break;
                }
            }

        }
        if (first == null) {
            first = results.get(0);
        }

        info.setYear(getYearSafe(first, type));
        info.setTmdbId(first.path("id").asText());

        int id = first.path("id").asInt(-1);
        String best = (id > 0) ? getBestTitle(type, first, id, api) : null;

        // fetch details to populate genres, original language and origin countries
        if (id > 0) {
            try {
                fetchDetails(type, id, info, api);
            } catch (Exception e) {
                log.warn("fetchDetails failed: {}", e.getMessage());
            }
        }

        return best;
    }

    private void fetchDetails(String type, int id, MediaInfo info, TMDbApiService api) throws IOException {
        JsonNode d = mapper.readTree(api.getDetails(apiKey, type, id));
        if (d == null) return;
        info.getMetadata().put("details", d);

        // genres -> ids
        JsonNode genres = d.path("genres");
        if (genres.isArray()) {
            info.getGenreIds().clear();
            for (JsonNode g : genres) {
                if (g.has("id")) info.getGenreIds().add(String.valueOf(g.get("id").asInt()));
            }
        }

        // original language
        if (d.hasNonNull("original_language")) {
            info.setOriginalLanguage(d.get("original_language").asText());
        }

        // origin countries: tv uses origin_country (array of codes); movie uses production_countries
        if (type.equals("tv")) {
            JsonNode oc = d.path("origin_country");
            if (oc.isArray()) {
                info.getOriginCountries().clear();
                for (JsonNode c : oc) info.getOriginCountries().add(c.asText());
            }

            // TV: 额外获取 images 数据 (posters, backdrops, logos)
            try {
                String imagesJson = api.getTvImages(apiKey, id);
                if (imagesJson != null) {
                    JsonNode images = mapper.readTree(imagesJson);
                    info.getMetadata().put("images", images);
                    log.debug("获取剧集 images 成功: tvId={}, posters={}, backdrops={}, logos={}",
                            id,
                            images.path("posters").size(),
                            images.path("backdrops").size(),
                            images.path("logos").size());
                }
            } catch (Exception e) {
                log.warn("获取剧集 images 失败: tvId={}, error={}", id, e.getMessage());
            }
        } else {
            JsonNode pcs = d.path("production_countries");
            if (pcs.isArray()) {
                info.getOriginCountries().clear();
                for (JsonNode pc : pcs) {
                    if (pc.has("iso_3166_1")) info.getOriginCountries().add(pc.get("iso_3166_1").asText());
                }
            }
        }
    }

    private String getBestTitle(String type, JsonNode result, int id, TMDbApiService api) throws IOException {
        String title = getOfficialChineseTitle(result, type);
        if (StringUtils.isNotEmpty(title)) return title;

        title = fetchChineseAlias(type, id, api);
        if (StringUtils.isNotEmpty(title)) return title;

        return fallbackTitle(result, type);
    }

    private String getOfficialChineseTitle(JsonNode result, String type) {
        String name = type.equals("movie") ? result.path("title").asText() : result.path("name").asText();
        if (isChinese(name)) {
            return name;
        }
        return null;
    }

    private String fetchChineseAlias(String type, int id, TMDbApiService api) throws IOException {
        JsonNode root = mapper.readTree(api.getAlternativeTitles(apiKey, type, id));
        if (root == null) return null;
        log.debug("fetchChineseAlias: {}", root);

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
        return null;
    }

    private String fallbackTitle(JsonNode result, String type) {
        return type.equals("movie") ? result.path("title").asText()
                : result.path("name").asText();
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
