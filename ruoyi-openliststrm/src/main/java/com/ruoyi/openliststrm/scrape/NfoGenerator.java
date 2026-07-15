package com.ruoyi.openliststrm.scrape;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 生成 Emby/Jellyfin/Plex 兼容的 NFO 文件。
 * 利用 MediaInfo.metadata 中已有的 TMDb 详情 JsonNode。
 */
@Slf4j
@Component
public class NfoGenerator {

    private static final String TMDb_IMG_BASE = "https://image.tmdb.org/t/p/original";

    // ==================== Public API ====================

    /**
     * 生成电影 NFO: {@code <视频文件名>.nfo}（与 STRM 文件同名，不含视频后缀）
     */
    public void generateMovieNfo(MediaInfo info, Path destFile, Path outputDir, boolean forceOverwrite) throws IOException {
        String nfoContent = buildMovieNfo(info);
        String nfoName = stripExtension(destFile.getFileName().toString()) + ".nfo";
        Path nfoFile = destFile.resolveSibling(nfoName);
        writeNfo(nfoFile, nfoContent, forceOverwrite);
        log.info("生成电影 NFO: {}", nfoFile);
    }

    /**
     * 生成剧集 NFO:
     * - {@code tvshow.nfo} (剧集根目录，固定命名)
     * - {@code season.nfo} (季目录内，固定命名)
     * - {@code <episodedetails>.nfo} (与 STRM 文件同名)
     */
    public void generateTvNfo(MediaInfo info, Path destFile, Path outputDir, boolean forceOverwrite) throws IOException {
        // 剧集 NFO → 放在剧集根目录（{show_name} ({year})/），即 Season XX 的父目录
        String tvshowNfo = buildTvShowNfo(info);
        Path showRoot = destFile.getParent().getParent();
        Path tvshowNfoFile = showRoot.resolve("tvshow.nfo");
        writeNfo(tvshowNfoFile, tvshowNfo, false);
        log.info("生成剧集 NFO (系列): {}", tvshowNfoFile);

        // 季 NFO → 放在季目录内
        String seasonNfo = buildSeasonNfo(info);
        Path seasonNfoFile = destFile.getParent().resolve("season.nfo");
        writeNfo(seasonNfoFile, seasonNfo, forceOverwrite);
        log.info("生成剧集 NFO (季): {}", seasonNfoFile);

        // 单集 NFO → 与 STRM 文件同名
        String episodeNfo = buildEpisodeNfo(info);
        String episodeNfoName = stripExtension(destFile.getFileName().toString()) + ".nfo";
        Path episodeNfoFile = destFile.resolveSibling(episodeNfoName);
        writeNfo(episodeNfoFile, episodeNfo, false);
        log.info("生成剧集 NFO (单集): {}", episodeNfoFile);
    }

    // ==================== Movie NFO ====================

    private String buildMovieNfo(MediaInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>\n");
        sb.append("<movie>\n");

        JsonNode details = getDetails(info);
        JsonNode externalIds = getExternalIds(info);

        // 标题优先取 TMDb 接口返回值
        String movieTitle = details != null && details.hasNonNull("title") ? details.get("title").asText() : info.getTitle();
        String movieOriginalTitle = details != null && details.hasNonNull("original_title") ? details.get("original_title").asText() : info.getOriginalTitle();
        appendTag(sb, "title", movieTitle);
        appendTag(sb, "originaltitle", movieOriginalTitle);
        appendTag(sb, "sorttitle", movieTitle);
        appendTag(sb, "tmdbid", info.getTmdbId());

        // IMDb ID: 从 details 或 external_ids 获取
        String imdbId = null;
        if (details != null && details.hasNonNull("imdb_id")) {
            imdbId = details.get("imdb_id").asText();
        } else if (externalIds != null && externalIds.hasNonNull("imdb_id")) {
            imdbId = externalIds.get("imdb_id").asText();
        }
        if (StringUtils.isNotBlank(imdbId)) {
            appendTag(sb, "imdbid", imdbId);
        }

        // uniqueid 节点（IMDb 优先为 default）
        if (StringUtils.isNotBlank(imdbId)) {
            appendUniqueid(sb, imdbId, "imdb", "true");
        }
        appendUniqueid(sb, info.getTmdbId(), "tmdb", imdbId == null ? "true" : "false");

        appendTag(sb, "year", info.getYear());

        if (details != null && details.has("release_date")) {
            String rd = details.get("release_date").asText();
            appendTag(sb, "premiere", rd);
            appendTag(sb, "releasedate", rd);
        }
        if (details != null && details.has("runtime")) {
            appendTag(sb, "runtime", String.valueOf(details.get("runtime").asInt()));
        }

        // 分级信息 (MPAA 等)
        if (info.getMetadata() != null && info.getMetadata().get("release_dates") != null) {
            String certification = extractCertification(info);
            if (StringUtils.isNotBlank(certification)) {
                appendTag(sb, "mpaa", certification);
            }
        }

        // 多源评分
        appendRatings(sb, details, imdbId);

        if (details != null && details.has("metacritic")) {
            appendTag(sb, "metacritic", String.valueOf(details.get("metacritic").asInt()));
        }

        appendTag(sb, "tagline", details != null && details.has("tagline") ? details.get("tagline").asText() : null);
        appendCDataTag(sb, "outline", details != null && details.has("overview") ? truncate(details.get("overview").asText(), 200) : null);
        appendCDataTag(sb, "plot", details != null && details.has("overview") ? details.get("overview").asText() : null);

        if (details != null && details.has("videos")) {
            String trailer = extractTrailerUrl(details);
            if (trailer != null) appendTag(sb, "trailer", trailer);
        }

        // 合集信息 (belongs_to_collection)
        if (details != null && details.has("belongs_to_collection") && !details.get("belongs_to_collection").isNull()) {
            JsonNode collection = details.get("belongs_to_collection");
            String collectionName = collection.path("name").asText(null);
            if (StringUtils.isNotBlank(collectionName)) {
                sb.append("  <set>\n");
                appendTag(sb, "name", collectionName, "    ");
                appendTag(sb, "overview", "", "    ");
                sb.append("  </set>\n");
            }
        }

        appendGenres(sb, details, "genres");
        appendCountries(sb, details, "production_countries", "name");
        appendStudios(sb, details, "production_companies", "name");
        appendDirectors(sb, details);
        appendWriters(sb, details);
        appendActorsStructured(sb, details);

        // 标签 (keywords)
        appendKeywords(sb, details);

        // 图片引用 (thumb)
        appendThumbs(sb, info, details);

        sb.append("</movie>\n");
        return sb.toString();
    }

    // ==================== TV Show NFO (新规范) ====================

    /**
     * 剧集级 NFO — 放在剧集根目录（{show_name} ({year})/）
     * 根节点: <tvshow>
     */
    private String buildTvShowNfo(MediaInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>\n");
        sb.append("<tvshow>\n");

        JsonNode details = getDetails(info);
        JsonNode externalIds = getExternalIds(info);

        // 标题优先取 TMDb 接口返回值
        String tvTitle = details != null && details.hasNonNull("name") ? details.get("name").asText() : info.getTitle();
        String tvOriginalTitle = details != null && details.hasNonNull("original_name") ? details.get("original_name").asText() : tvTitle;
        appendTag(sb, "title", tvTitle);
        appendTag(sb, "originaltitle", tvOriginalTitle);
        appendTag(sb, "showtitle", tvTitle);

        // TMDB ID + IMDb ID
        appendTag(sb, "tmdbid", info.getTmdbId());
        String imdbId = null;
        if (externalIds != null && externalIds.hasNonNull("imdb_id")) {
            imdbId = externalIds.get("imdb_id").asText();
        }
        if (StringUtils.isNotBlank(imdbId)) {
            appendTag(sb, "imdbid", imdbId);
        }

        // uniqueid 节点
        if (StringUtils.isNotBlank(imdbId)) {
            appendUniqueid(sb, imdbId, "imdb", "true");
        }
        appendUniqueid(sb, info.getTmdbId(), "tmdb", imdbId == null ? "true" : "false");

        // TVDb ID
        if (externalIds != null && externalIds.hasNonNull("tvdb_id")) {
            String tvdbId = externalIds.get("tvdb_id").asText();
            if (StringUtils.isNotBlank(tvdbId)) {
                appendUniqueid(sb, tvdbId, "tvdb", "false");
            }
        }

        // 年份
        String firstAirDate = null;
        if (details != null && details.has("first_air_date")) {
            firstAirDate = details.get("first_air_date").asText();
            if (firstAirDate != null && firstAirDate.length() >= 4) {
                appendTag(sb, "year", firstAirDate.substring(0, 4));
            }
        }

        // 首播日期
        appendTag(sb, "premiered", firstAirDate);

        // 剧集状态
        if (details != null && details.has("status")) {
            String status = details.get("status").asText();
            if (StringUtils.isNotBlank(status)) {
                appendTag(sb, "status", status);
            }
        }

        // 多源评分（统一使用 <ratings> 结构体）
        appendRatings(sb, details, imdbId);

        // 内容分级 (TV-14, TV-MA 等)
        String tvCertification = extractTvCertification(info);
        if (StringUtils.isNotBlank(tvCertification)) {
            appendTag(sb, "mpaa", tvCertification);
        }

        // 简介
        String overview = details != null && details.has("overview") ? details.get("overview").asText() : null;
        appendCDataTag(sb, "plot", overview);
        appendCDataTag(sb, "outline", truncate(overview, 200));

        // tagline
        appendTag(sb, "tagline", details != null && details.has("tagline") ? details.get("tagline").asText() : null);

        // 季数、集数
        if (details != null) {
            if (details.has("number_of_seasons")) {
                appendTag(sb, "season", String.valueOf(details.get("number_of_seasons").asInt()));
            }
            if (details.has("number_of_episodes")) {
                appendTag(sb, "episode", String.valueOf(details.get("number_of_episodes").asInt()));
            }
        }

        // 类型、国家、播出网络
        appendGenres(sb, details, "genres");
        if (details != null && details.has("origin_country")) {
            JsonNode oc = details.get("origin_country");
            for (JsonNode c : oc) {
                appendTag(sb, "country", c.asText());
            }
        }
        // studio 使用播出网络（networks）而非制作公司
        if (details != null && details.has("networks") && details.get("networks").isArray() && !details.get("networks").isEmpty()) {
            appendStudios(sb, details, "networks", "name");
        } else {
            appendStudios(sb, details, "production_companies", "name");
        }
        appendActorsStructured(sb, details);

        // 标签 (keywords)
        appendKeywords(sb, details);

        // 图片引用 (thumb)
        appendThumbs(sb, info, details);

        sb.append("</tvshow>\n");
        return sb.toString();
    }

    // ==================== Season NFO (新规范) ====================

    /**
     * 季级 NFO — 放在 Season XX 目录内
     * 根节点: <season>
     */
    private String buildSeasonNfo(MediaInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>\n");
        sb.append("<season>\n");

        int seasonNum = parseSeasonNumber(info.getSeason());

        // 季标题：优先使用 TMDb 返回的季名称
        Object sdObj = info.getMetadata() != null ? info.getMetadata().get("season_details") : null;
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> seasonMeta = (sdObj instanceof java.util.Map)
                ? (java.util.Map<String, Object>) sdObj : null;

        String seasonName = null;
        if (seasonMeta != null && seasonMeta.get("name") instanceof String n && StringUtils.isNotBlank(n)) {
            seasonName = n;
        }
        appendTag(sb, "title", StringUtils.isNotBlank(seasonName) ? seasonName : "第 " + seasonNum + " 季");
        appendTag(sb, "seasonnumber", String.valueOf(seasonNum));

        // tvshowid 关联
        if (StringUtils.isNotBlank(info.getTmdbId())) {
            appendTag(sb, "tvshowid", info.getTmdbId());
        }

        // 季级别的 uniqueid：使用季的 TMDb ID
        String seasonTmdbId = null;
        if (seasonMeta != null && seasonMeta.get("id") instanceof String sid) {
            seasonTmdbId = sid;
        }
        if (StringUtils.isNotBlank(seasonTmdbId)) {
            appendUniqueid(sb, seasonTmdbId, "tmdb", "true");
        }

        // 年份：从季的首播日期提取
        String seasonAirDate = null;
        if (seasonMeta != null && seasonMeta.get("air_date") instanceof String ad) {
            seasonAirDate = ad;
        }
        if (StringUtils.isNotBlank(seasonAirDate) && seasonAirDate.length() >= 4) {
            appendTag(sb, "year", seasonAirDate.substring(0, 4));
        }

        // 首播日期：使用季的真实首播日期
        if (StringUtils.isNotBlank(seasonAirDate)) {
            appendTag(sb, "premiered", seasonAirDate);
        }

        // 剧情：使用季的独立概述
        String seasonPlot = null;
        if (seasonMeta != null && seasonMeta.get("overview") instanceof String so) {
            seasonPlot = so;
        }
        if (StringUtils.isBlank(seasonPlot)) {
            // 回退：使用剧集整体概述
            JsonNode details = getDetails(info);
            if (details != null && details.has("overview")) {
                seasonPlot = details.get("overview").asText();
            }
        }
        appendCDataTag(sb, "plot", StringUtils.isNotBlank(seasonPlot) ? truncate(seasonPlot, 500) : null);

        // 季级别图片引用
        JsonNode seasonImages = getSeasonImages(info);
        if (seasonImages != null) {
            JsonNode posters = seasonImages.path("posters");
            if (posters.isArray() && posters.size() > 0) {
                String posterPath = posters.get(0).path("file_path").asText(null);
                if (StringUtils.isNotBlank(posterPath)) {
                    sb.append("  <thumb aspect=\"poster\">").append(TMDb_IMG_BASE).append(escapeXml(posterPath)).append("</thumb>\n");
                }
            }
        }

        sb.append("</season>\n");
        return sb.toString();
    }

    // ==================== Episode NFO (新规范) ====================

    /**
     * 集级 NFO — 与 .strm 文件同名
     * 根节点: <episodedetails>
     */
    private String buildEpisodeNfo(MediaInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>\n");
        sb.append("<episodedetails>\n");

        JsonNode details = getDetails(info);

        // 剧集标题优先取 TMDb 接口返回值
        String showTitle = details != null && details.hasNonNull("name") ? details.get("name").asText() : info.getTitle();

        // 集标题
        String epTitle;
        if (StringUtils.isNotBlank(info.getEpisodeName())) {
            epTitle = info.getEpisodeName();
        } else {
            int epNum = parseEpisodeNumber(info.getEpisode());
            epTitle = "第 " + epNum + " 集";
        }
        appendTag(sb, "title", epTitle);

        // showtitle
        appendTag(sb, "showtitle", showTitle);

        // 季/集号（数字）
        int seasonNum = parseSeasonNumber(info.getSeason());
        int epNum = parseEpisodeNumber(info.getEpisode());
        appendTag(sb, "season", String.valueOf(seasonNum));
        appendTag(sb, "episode", String.valueOf(epNum));

        // 单集 tmdbId（仅当有单集独立 ID 时才写入，不回退到剧集 ID）
        if (StringUtils.isNotBlank(info.getEpisodeTmdbId())) {
            appendTag(sb, "tmdbid", info.getEpisodeTmdbId());
            appendUniqueid(sb, info.getEpisodeTmdbId(), "tmdb", "true");
        }

        // 播出日期
        if (StringUtils.isNotBlank(info.getEpisodeAiredDate())) {
            appendTag(sb, "aired", info.getEpisodeAiredDate());
        }

        // 单集评分
        if (StringUtils.isNotBlank(info.getEpisodeRating())) {
            appendTag(sb, "rating", info.getEpisodeRating());
        }

        // 导演
        if (StringUtils.isNotBlank(info.getEpisodeDirector())) {
            appendTag(sb, "director", info.getEpisodeDirector());
        }

        // 编剧
        if (StringUtils.isNotBlank(info.getEpisodeWriter())) {
            appendTag(sb, "writer", info.getEpisodeWriter());
        }

        // 客串演员
        if (StringUtils.isNotBlank(info.getEpisodeGuestStars())) {
            appendTag(sb, "credits", info.getEpisodeGuestStars());
        }

        // 剧情
        String plot = StringUtils.isNotBlank(info.getEpisodePlot())
                ? info.getEpisodePlot()
                : (details != null && details.has("overview") ? details.get("overview").asText() : null);
        appendCDataTag(sb, "plot", plot);

        // 单集剧照 (thumb)
        if (StringUtils.isNotBlank(info.getEpisodeStillPath())) {
            sb.append("  <thumb>").append(TMDb_IMG_BASE).append(escapeXml(info.getEpisodeStillPath())).append("</thumb>\n");
        }

        // fileinfo — 视频编码信息
        boolean hasVideo = StringUtils.isNotBlank(info.getVideoCodec());
        boolean hasAudio = StringUtils.isNotBlank(info.getAudioCodec());
        boolean hasResolution = StringUtils.isNotBlank(info.getResolution());
        if (hasVideo || hasAudio || hasResolution) {
            sb.append("  <fileinfo>\n");
            sb.append("    <streamdetails>\n");
            if (hasVideo) {
                sb.append("      <video>\n");
                sb.append("        <codec>").append(escapeXml(info.getVideoCodec())).append("</codec>\n");
                if (hasResolution) {
                    sb.append("        <width>").append(escapeXml(parseResolutionWidth(info.getResolution()))).append("</width>\n");
                    sb.append("        <height>").append(escapeXml(parseResolutionHeight(info.getResolution()))).append("</height>\n");
                }
                sb.append("      </video>\n");
            }
            if (hasAudio) {
                sb.append("      <audio>\n");
                sb.append("        <codec>").append(escapeXml(info.getAudioCodec())).append("</codec>\n");
                sb.append("      </audio>\n");
            }
            sb.append("    </streamdetails>\n");
            sb.append("  </fileinfo>\n");
        }

        sb.append("</episodedetails>\n");
        return sb.toString();
    }

    // ==================== Helpers ====================

    private JsonNode getDetails(MediaInfo info) {
        if (info.getMetadata() == null) return null;
        Object details = info.getMetadata().get("details");
        if (details instanceof JsonNode) {
            return (JsonNode) details;
        }
        return null;
    }

    private JsonNode getExternalIds(MediaInfo info) {
        if (info.getMetadata() == null) return null;
        Object extIds = info.getMetadata().get("external_ids");
        if (extIds instanceof JsonNode) {
            return (JsonNode) extIds;
        }
        return null;
    }

    private JsonNode getSeasonImages(MediaInfo info) {
        if (info.getMetadata() == null) return null;
        Object si = info.getMetadata().get("season_images");
        if (si instanceof JsonNode) {
            return (JsonNode) si;
        }
        return null;
    }

    /**
     * 写入 CDATA 包裹的标签，适用于 plot/outline 等可能含特殊字符的长文本。
     * CDATA 内部无需转义 XML 实体，但需要处理内容中出现的 "]]>" 序列。
     */
    private void appendCDataTag(StringBuilder sb, String name, String value) {
        if (StringUtils.isNotBlank(value)) {
            // CDATA 中不能出现 "]]>" ，需拆分为 "]]" + ">" 
            String safe = value.replace("]]>", "]]><![CDATA[>");
            sb.append("  <").append(name).append("><![CDATA[").append(safe).append("]]></").append(name).append(">\n");
        }
    }

    private void appendTag(StringBuilder sb, String name, String value) {
        if (StringUtils.isNotBlank(value)) {
            sb.append("  <").append(name).append(">").append(escapeXml(value)).append("</").append(name).append(">\n");
        }
    }

    private void appendTag(StringBuilder sb, String name, String value, String indent) {
        if (StringUtils.isNotBlank(value)) {
            sb.append(indent).append("<").append(name).append(">").append(escapeXml(value)).append("</").append(name).append(">\n");
        }
    }

    private void appendTag(StringBuilder sb, String name, String value, String type, String defaultFlag) {
        if (StringUtils.isNotBlank(value)) {
            sb.append("  <").append(name).append(" type=\"").append(type).append("\" default=\"").append(defaultFlag).append("\">")
                    .append(escapeXml(value)).append("</").append(name).append(">\n");
        }
    }

    private void appendGenres(StringBuilder sb, JsonNode details, String key) {
        if (details != null && details.has(key)) {
            for (JsonNode g : details.get(key)) {
                appendTag(sb, "genre", g.get("name").asText());
            }
        }
    }

    private void appendCountries(StringBuilder sb, JsonNode details, String key, String nameField) {
        if (details != null && details.has(key)) {
            JsonNode countries = details.get(key);
            for (JsonNode c : countries) {
                appendTag(sb, "country", c.get(nameField).asText());
            }
        }
    }

    private void appendStudios(StringBuilder sb, JsonNode details, String key, String nameField) {
        if (details != null && details.has(key)) {
            JsonNode companies = details.get(key);
            for (JsonNode c : companies) {
                appendTag(sb, "studio", c.get(nameField).asText());
            }
        }
    }

    private void appendDirectors(StringBuilder sb, JsonNode details) {
        if (details != null && details.has("credits")) {
            JsonNode credits = details.get("credits");
            if (credits.has("crew")) {
                for (JsonNode member : credits.get("crew")) {
                    String job = member.path("job").asText("");
                    if ("Director".equals(job)) {
                        appendTag(sb, "director", member.get("name").asText());
                    }
                }
            }
        }
    }

    private void appendActors(StringBuilder sb, JsonNode details) {
        if (details != null && details.has("credits")) {
            JsonNode credits = details.get("credits");
            if (credits.has("cast")) {
                int count = 0;
                for (JsonNode member : credits.get("cast")) {
                    if (count >= 20) break;
                    appendTag(sb, "actor", member.get("name").asText());
                    count++;
                }
            }
        }
    }

    /**
     * 结构化 actor 节点：包含 name、role、thumb
     */
    private void appendActorsStructured(StringBuilder sb, JsonNode details) {
        if (details == null || !details.has("credits")) return;
        JsonNode credits = details.get("credits");
        if (!credits.has("cast")) return;
        int count = 0;
        for (JsonNode member : credits.get("cast")) {
            if (count >= 20) break;
            String name = member.path("name").asText(null);
            String role = member.path("character").asText(null);
            String profilePath = member.path("profile_path").asText(null);
            if (StringUtils.isBlank(name)) continue;
            sb.append("  <actor>\n");
            appendTag(sb, "name", name, "    ");
            appendTag(sb, "role", role, "    ");
            if (StringUtils.isNotBlank(profilePath)) {
                appendTag(sb, "thumb", TMDb_IMG_BASE + profilePath, "    ");
            }
            sb.append("  </actor>\n");
            count++;
        }
    }

    private void appendWriters(StringBuilder sb, JsonNode details) {
        if (details == null || !details.has("credits")) return;
        JsonNode credits = details.get("credits");
        if (!credits.has("crew")) return;
        for (JsonNode member : credits.get("crew")) {
            String department = member.path("department").asText("");
            if ("Writing".equals(department)) {
                String name = member.path("name").asText(null);
                if (StringUtils.isNotBlank(name)) {
                    appendTag(sb, "writer", name);
                }
            }
        }
    }

    private void appendKeywords(StringBuilder sb, JsonNode details) {
        if (details == null) return;
        JsonNode keywords = null;
        if (details.has("keywords") && details.get("keywords").has("keywords")) {
            keywords = details.get("keywords").get("keywords");
        } else if (details.has("keywords") && details.get("keywords").isArray()) {
            keywords = details.get("keywords");
        }
        if (keywords != null && keywords.isArray()) {
            int count = 0;
            for (JsonNode kw : keywords) {
                if (count >= 10) break;
                String name = kw.path("name").asText(null);
                if (StringUtils.isNotBlank(name)) {
                    appendTag(sb, "tag", name);
                    count++;
                }
            }
        }
    }

    private void appendRatings(StringBuilder sb, JsonNode details, String imdbId) {
        if (details == null) return;
        boolean hasTmdbRating = details.has("vote_average") && details.get("vote_average").asDouble(0) > 0;
        boolean hasImdb = StringUtils.isNotBlank(imdbId);
        if (!hasTmdbRating && !hasImdb) return;

        sb.append("  <ratings>\n");
        if (hasTmdbRating) {
            sb.append("    <rating name=\"themoviedb\" max=\"10\" default=\"true\">\n");
            appendTag(sb, "value", String.format("%.1f", details.get("vote_average").asDouble(0)), "      ");
            if (details.has("vote_count")) {
                appendTag(sb, "votes", String.valueOf(details.get("vote_count").asInt()), "      ");
            }
            sb.append("    </rating>\n");
        }
        sb.append("  </ratings>\n");
    }

    private void appendThumbs(StringBuilder sb, MediaInfo info, JsonNode details) {
        if (details == null) return;
        // poster
        String posterPath = details.path("poster_path").asText(null);
        if (StringUtils.isNotBlank(posterPath)) {
            sb.append("  <thumb aspect=\"poster\" preview=\"").append(TMDb_IMG_BASE).append(escapeXml(posterPath)).append("\">")
                    .append(TMDb_IMG_BASE).append(escapeXml(posterPath)).append("</thumb>\n");
        }
        // fanart / backdrop
        String backdropPath = details.path("backdrop_path").asText(null);
        if (StringUtils.isNotBlank(backdropPath)) {
            sb.append("  <fanart>\n");
            sb.append("    <thumb preview=\"").append(TMDb_IMG_BASE).append(escapeXml(backdropPath)).append("\">")
                    .append(TMDb_IMG_BASE).append(escapeXml(backdropPath)).append("</thumb>\n");
            sb.append("  </fanart>\n");
        }
    }

    /**
     * 提取电视剧内容分级（TV-14, TV-MA 等）
     * 从 TMDb content_ratings 接口数据中获取
     */
    private String extractTvCertification(MediaInfo info) {
        try {
            if (info.getMetadata() == null) return null;
            Object crObj = info.getMetadata().get("content_ratings");
            if (!(crObj instanceof JsonNode)) return null;
            JsonNode cr = (JsonNode) crObj;
            JsonNode results = cr.path("results");
            if (!results.isArray()) return null;
            // 优先查找 CN 分级，然后 US
            String[] priorityCountries = {"CN", "US"};
            for (String country : priorityCountries) {
                for (JsonNode r : results) {
                    if (country.equals(r.path("iso_3166_1").asText(""))) {
                        String rating = r.path("rating").asText("");
                        if (StringUtils.isNotBlank(rating)) return rating;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private String extractCertification(MediaInfo info) {
        try {
            Object rdObj = info.getMetadata().get("release_dates");
            if (!(rdObj instanceof JsonNode)) return null;
            JsonNode rd = (JsonNode) rdObj;
            JsonNode results = rd.path("results");
            if (!results.isArray()) return null;
            // 优先查找 CN 分级，然后 US
            String[] priorityCountries = {"CN", "US"};
            for (String country : priorityCountries) {
                for (JsonNode r : results) {
                    if (country.equals(r.path("iso_3166_1").asText(""))) {
                        JsonNode dates = r.path("release_dates");
                        if (dates.isArray()) {
                            for (JsonNode d : dates) {
                                String cert = d.path("certification").asText("");
                                if (StringUtils.isNotBlank(cert)) return cert;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private void appendUniqueid(StringBuilder sb, String value, String type, String defaultFlag) {
        if (StringUtils.isNotBlank(value)) {
            appendTag(sb, "uniqueid", value, type, defaultFlag);
        }
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private String stripExtension(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private int parseSeasonNumber(String season) {
        if (StringUtils.isBlank(season)) return 0;
        try {
            return Integer.parseInt(season.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseEpisodeNumber(String episode) {
        if (StringUtils.isBlank(episode)) return 0;
        try {
            return Integer.parseInt(episode.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String padSeason(String season) {
        if (StringUtils.isBlank(season)) return "00";
        int num = Integer.parseInt(season.replaceAll("\\D", ""));
        return num < 10 ? "0" + num : String.valueOf(num);
    }

    /**
     * 从 resolution 字符串解析宽度（如 "1080p" -> "1920", "2160p" -> "3840", "720p" -> "1280"）
     */
    private String parseResolutionWidth(String resolution) {
        if (resolution == null) return "";
        String lower = resolution.toLowerCase();
        if (lower.contains("2160") || lower.contains("4k") || lower.contains("uhd")) return "3840";
        if (lower.contains("1080")) return "1920";
        if (lower.contains("720")) return "1280";
        if (lower.contains("480")) return "854";
        if (lower.contains("576")) return "1024";
        return "";
    }

    /**
     * 从 resolution 字符串解析高度（如 "1080p" -> "1080"）
     */
    private String parseResolutionHeight(String resolution) {
        if (resolution == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{3,4})").matcher(resolution);
        if (m.find()) return m.group(1);
        return "";
    }

    private String extractTrailerUrl(JsonNode details) {
        if (details == null || !details.has("videos")) return null;
        JsonNode videos = details.get("videos");
        if (!videos.has("results")) return null;
        for (JsonNode v : videos.get("results")) {
            if ("YouTube".equals(v.path("site").asText("")) && "Trailer".equals(v.path("type").asText(""))) {
                return "plugin://plugin.video.youtube/play/?video_id=" + v.get("key").asText();
            }
        }
        return null;
    }

    private void writeNfo(Path nfoFile, String content, boolean forceOverwrite) throws IOException {
        Path parent = nfoFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(nfoFile) && !forceOverwrite) {
            log.debug("NFO 文件已存在，跳过: {}", nfoFile);
            return;
        }
        Files.write(nfoFile, content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
