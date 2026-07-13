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
import java.util.List;

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
    public void generateMovieNfo(MediaInfo info, Path destFile, Path outputDir) throws IOException {
        String nfoContent = buildMovieNfo(info);
        String nfoName = stripExtension(destFile.getFileName().toString()) + ".nfo";
        Path nfoFile = destFile.resolveSibling(nfoName);
        writeNfo(nfoFile, nfoContent, false);
        log.info("生成电影 NFO: {}", nfoFile);
    }

    /**
     * 生成剧集 NFO:
     * - {@code tvshow.nfo} (剧集根目录，固定命名)
     * - {@code season.nfo} (季目录内，固定命名)
     * - {@code <episodedetails>.nfo} (与 STRM 文件同名)
     */
    public void generateTvNfo(MediaInfo info, Path destFile, Path outputDir) throws IOException {
        // 剧集 NFO → 放在剧集根目录（{show_name} ({year})/），即 Season XX 的父目录
        String tvshowNfo = buildTvShowNfo(info);
        Path showRoot = destFile.getParent().getParent();
        Path tvshowNfoFile = showRoot.resolve("tvshow.nfo");
        writeNfo(tvshowNfoFile, tvshowNfo, false);
        log.info("生成剧集 NFO (系列): {}", tvshowNfoFile);

        // 季 NFO → 放在季目录内
        String seasonNfo = buildSeasonNfo(info);
        Path seasonNfoFile = destFile.getParent().resolve("season.nfo");
        writeNfo(seasonNfoFile, seasonNfo, false);
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

        appendTag(sb, "title", info.getTitle());
        appendTag(sb, "originaltitle", info.getOriginalTitle());
        appendTag(sb, "sorttitle", info.getTitle());
        appendTag(sb, "tmdbid", info.getTmdbId());

        JsonNode details = getDetails(info);
        if (details != null && details.has("imdb_id")) {
            appendTag(sb, "imdbid", details.get("imdb_id").asText());
        }
        appendTag(sb, "year", info.getYear());

        if (details != null && details.has("release_date")) {
            String rd = details.get("release_date").asText();
            appendTag(sb, "premiere", rd);
            appendTag(sb, "releasedate", rd);
        }
        if (details != null && details.has("runtime")) {
            appendTag(sb, "runtime", String.valueOf(details.get("runtime").asInt()));
        }
        if (details != null && details.has("vote_average")) {
            appendTag(sb, "rating", String.format("%.1f", details.get("vote_average").asDouble(0)));
        }
        if (details != null && details.has("vote_count")) {
            appendTag(sb, "votes", String.valueOf(details.get("vote_count").asInt()));
        }
        if (details != null && details.has("metacritic")) {
            appendTag(sb, "metacritic", String.valueOf(details.get("metacritic").asInt()));
        }
        appendTag(sb, "tagline", details != null && details.has("tagline") ? details.get("tagline").asText() : null);
        appendTag(sb, "outline", details != null && details.has("overview") ? truncate(details.get("overview").asText(), 200) : null);
        appendTag(sb, "plot", details != null && details.has("overview") ? details.get("overview").asText() : null);

        if (details != null && details.has("videos")) {
            String trailer = extractTrailerUrl(details);
            if (trailer != null) appendTag(sb, "trailer", trailer);
        }

        appendGenres(sb, details, "genres");
        appendCountries(sb, details, "production_countries", "iso_3166_1");
        appendStudios(sb, details, "production_companies", "name");
        appendDirectors(sb, details);
        appendActors(sb, details);
        appendUniqueid(sb, info.getTmdbId(), "tmdb", "false");

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

        // 标题
        appendTag(sb, "title", info.getTitle());
        appendTag(sb, "originaltitle", info.getOriginalTitle());
        appendTag(sb, "showtitle", info.getTitle());

        // TMDB ID
        appendTag(sb, "tmdbid", info.getTmdbId());
        appendUniqueid(sb, info.getTmdbId(), "tmdb", "true");

        // 年份
        JsonNode details = getDetails(info);
        if (details != null && details.has("first_air_date")) {
            String date = details.get("first_air_date").asText();
            if (date != null && date.length() >= 4) {
                appendTag(sb, "year", date.substring(0, 4));
            }
        }

        // 剧情
        appendTag(sb, "plot", details != null && details.has("overview") ? details.get("overview").asText() : null);

        // 类型、国家、制作公司、演员（同电影）
        appendGenres(sb, details, "genres");
        if (details != null && details.has("origin_country")) {
            JsonNode oc = details.get("origin_country");
            for (JsonNode c : oc) {
                appendTag(sb, "country", c.asText());
            }
        }
        appendStudios(sb, details, "production_companies", "name");
        appendActors(sb, details);

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

        // 季标题: 第 X 季
        int seasonNum = parseSeasonNumber(info.getSeason());
        appendTag(sb, "title", "第 " + seasonNum + " 季");
        appendTag(sb, "seasonnumber", String.valueOf(seasonNum));

        // 剧情（来自剧集总览）
        JsonNode details = getDetails(info);
        appendTag(sb, "plot", details != null && details.has("overview") ? truncate(details.get("overview").asText(), 500) : null);

        // uniqueid（使用剧集 tmdbId）
        appendUniqueid(sb, info.getTmdbId(), "tmdb", "true");

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

        // 集标题
        String epTitle = StringUtils.isNotBlank(info.getEpisodeName())
                ? info.getEpisodeName()
                : (info.getTitle() + " S" + padSeason(info.getSeason()) + "E" + info.getEpisode());
        appendTag(sb, "title", epTitle);

        // showtitle
        appendTag(sb, "showtitle", info.getTitle());

        // 季/集号（数字）
        int seasonNum = parseSeasonNumber(info.getSeason());
        int epNum = parseEpisodeNumber(info.getEpisode());
        appendTag(sb, "season", String.valueOf(seasonNum));
        appendTag(sb, "episode", String.valueOf(epNum));

        // 单集 tmdbId
        if (StringUtils.isNotBlank(info.getEpisodeTmdbId())) {
            appendTag(sb, "tmdbid", info.getEpisodeTmdbId());
            appendUniqueid(sb, info.getEpisodeTmdbId(), "tmdb", "true");
        } else if (StringUtils.isNotBlank(info.getTmdbId())) {
            appendTag(sb, "tmdbid", info.getTmdbId());
            appendUniqueid(sb, info.getTmdbId(), "tmdb", "true");
        }

        // 播出日期
        if (StringUtils.isNotBlank(info.getEpisodeAiredDate())) {
            appendTag(sb, "aired", info.getEpisodeAiredDate());
        }

        // 剧情
        String plot = StringUtils.isNotBlank(info.getEpisodePlot())
                ? info.getEpisodePlot()
                : (details != null && details.has("overview") ? details.get("overview").asText() : null);
        appendTag(sb, "plot", plot);

        // fileinfo — 视频编码信息
        if (StringUtils.isNotBlank(info.getVideoCodec())) {
            sb.append("  <fileinfo>\n");
            sb.append("    <streamdetails>\n");
            sb.append("      <video>\n");
            sb.append("        <codec>").append(escapeXml(info.getVideoCodec())).append("</codec>\n");
            sb.append("      </video>\n");
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

    private void appendTag(StringBuilder sb, String name, String value) {
        if (StringUtils.isNotBlank(value)) {
            sb.append("  <").append(name).append(">").append(escapeXml(value)).append("</").append(name).append(">\n");
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
