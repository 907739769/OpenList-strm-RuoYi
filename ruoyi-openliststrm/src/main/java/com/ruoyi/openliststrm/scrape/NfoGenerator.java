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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成 Emby/Jellyfin/Plex 兼容的 NFO 文件。
 * 利用 MediaInfo.metadata 中已有的 TMDb 详情 JsonNode。
 */
@Slf4j
@Component
public class NfoGenerator {

    private static final String TMDb_IMG_BASE = "https://image.tmdb.org/t/p/original";

    /**
     * 生成电影 NFO: {@code <视频文件名>.nfo}
     */
    public void generateMovieNfo(MediaInfo info, Path destFile, Path outputDir) throws IOException {
        String nfoContent = buildMovieNfo(info);
        Path nfoFile = destFile.resolveSibling(destFile.getFileName().toString() + ".nfo");
        writeNfo(nfoFile, nfoContent);
        log.info("生成电影 NFO: {}", nfoFile);
    }

    /**
     * 生成剧集 NFO:
     * - {@code <系列名>-tvshow.nfo} (系列级)
     * - {@code <视频文件名>.nfo} (单集级: season + episode)
     */
    public void generateTvNfo(MediaInfo info, Path destFile, Path outputDir) throws IOException {
        // 系列 NFO
        String tvshowNfo = buildTvShowNfo(info);
        String seriesName = sanitizeForNfo(info.getTitle());
        Path tvshowNfoFile = outputDir.resolve(seriesName + "-tvshow.nfo");
        writeNfo(tvshowNfoFile, tvshowNfo);
        log.info("生成剧集 NFO (系列): {}", tvshowNfoFile);

        // 单集 NFO
        String episodeNfo = buildEpisodeNfo(info);
        Path episodeNfoFile = destFile.resolveSibling(destFile.getFileName().toString() + ".nfo");
        writeNfo(episodeNfoFile, episodeNfo);
        log.info("生成剧集 NFO (单集): {}", episodeNfoFile);
    }

    // ==================== Movie NFO ====================

    private String buildMovieNfo(MediaInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<movie>\n");

        // title
        appendTag(sb, "title", info.getTitle());
        // originaltitle
        appendTag(sb, "originaltitle", info.getOriginalTitle());
        // sorttitle
        appendTag(sb, "sorttitle", info.getTitle());
        // tmdbid
        appendTag(sb, "tmdbid", info.getTmdbId());
        // imdbid (如果 TMDb 详情中有)
        JsonNode details = getDetails(info);
        if (details != null && details.has("imdb_id")) {
            appendTag(sb, "imdbid", details.get("imdb_id").asText());
        }
        // year
        appendTag(sb, "year", info.getYear());
        // premiere (release date)
        if (details != null && details.has("release_date")) {
            appendTag(sb, "premiere", details.get("release_date").asText());
        }
        // releasedate
        if (details != null && details.has("release_date")) {
            appendTag(sb, "releasedate", details.get("release_date").asText());
        }
        // runtime
        if (details != null && details.has("runtime")) {
            appendTag(sb, "runtime", String.valueOf(details.get("runtime").asInt()));
        }
        // rating
        if (details != null && details.has("vote_average")) {
            double rating = details.get("vote_average").asDouble(0);
            appendTag(sb, "rating", String.format("%.1f", rating));
        }
        // votes
        if (details != null && details.has("vote_count")) {
            appendTag(sb, "votes", String.valueOf(details.get("vote_count").asInt()));
        }
        // metacritic
        if (details != null && details.has("metacritic")) {
            appendTag(sb, "metacritic", String.valueOf(details.get("metacritic").asInt()));
        }
        // tagline
        appendTag(sb, "tagline", details != null && details.has("tagline") ? details.get("tagline").asText() : null);
        // outline (short synopsis)
        appendTag(sb, "outline", details != null && details.has("overview") ? truncate(details.get("overview").asText(), 200) : null);
        // plot (full synopsis)
        appendTag(sb, "plot", details != null && details.has("overview") ? details.get("overview").asText() : null);
        // trailer
        if (details != null && details.has("videos")) {
            String trailer = extractTrailerUrl(details);
            if (trailer != null) {
                appendTag(sb, "trailer", trailer);
            }
        }

        // genres
        if (details != null && details.has("genres")) {
            JsonNode genres = details.get("genres");
            for (JsonNode g : genres) {
                appendTag(sb, "genre", g.get("name").asText());
            }
        }

        // country
        if (details != null && details.has("production_countries")) {
            JsonNode countries = details.get("production_countries");
            for (JsonNode c : countries) {
                appendTag(sb, "country", c.get("iso_3166_1").asText());
            }
        }

        // studio
        if (details != null && details.has("production_companies")) {
            JsonNode companies = details.get("production_companies");
            for (JsonNode c : companies) {
                appendTag(sb, "studio", c.get("name").asText());
            }
        }

        // director
        if (details != null && details.has("credits")) {
            JsonNode credits = details.get("credits");
            if (credits.has("crew")) {
                JsonNode crew = credits.get("crew");
                for (JsonNode member : crew) {
                    if ("Director".equals(member.path("job").asText("")) || "Executive Producer".equals(member.path("department").asText(""))) {
                        appendTag(sb, "director", member.get("name").asText());
                    }
                }
            }
        }

        // actor
        if (details != null && details.has("credits")) {
            JsonNode credits = details.get("credits");
            if (credits.has("cast")) {
                JsonNode cast = credits.get("cast");
                int count = 0;
                for (JsonNode member : cast) {
                    if (count >= 20) break; // NFO 通常限制 20 个演员
                    appendTag(sb, "actor", member.get("name").asText());
                    count++;
                }
            }
        }

        // trailer (YouTube ID)
        if (details != null && details.has("videos")) {
            JsonNode videos = details.get("videos");
            if (videos.has("results")) {
                for (JsonNode v : videos.get("results")) {
                    if ("YouTube".equals(v.path("site").asText("")) && "Trailer".equals(v.path("type").asText(""))) {
                        appendTag(sb, "trailer", "plugin://plugin.video.youtube/play/?video_id=" + v.get("key").asText());
                        break;
                    }
                }
            }
        }

        // uniqueid (tmdb)
        appendTag(sb, "uniqueid", info.getTmdbId(), "tmdb", "false");

        sb.append("</movie>\n");
        return sb.toString();
    }

    // ==================== TV Show NFO ====================

    private String buildTvShowNfo(MediaInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<tvshow>\n");

        // title
        appendTag(sb, "title", info.getTitle());
        // originaltitle
        appendTag(sb, "originaltitle", info.getOriginalTitle());
        // sorttitle
        appendTag(sb, "sorttitle", info.getTitle());
        // tmdbid
        appendTag(sb, "tmdbid", info.getTmdbId());
        // imdbid
        JsonNode details = getDetails(info);
        if (details != null && details.has("imdb_id")) {
            appendTag(sb, "imdbid", details.get("imdb_id").asText());
        }
        // premiered
        if (details != null && details.has("first_air_date")) {
            appendTag(sb, "premiered", details.get("first_air_date").asText());
        }
        // year
        if (details != null && details.has("first_air_date")) {
            String date = details.get("first_air_date").asText();
            if (date != null && date.length() >= 4) {
                appendTag(sb, "year", date.substring(0, 4));
            }
        }
        // status
        if (details != null && details.has("status")) {
            appendTag(sb, "status", details.get("status").asText());
        }
        // rating
        if (details != null && details.has("vote_average")) {
            appendTag(sb, "rating", String.format("%.1f", details.get("vote_average").asDouble(0)));
        }
        // episodes (number_of_episodes)
        if (details != null && details.has("number_of_episodes")) {
            appendTag(sb, "episodes", String.valueOf(details.get("number_of_episodes").asInt()));
        }
        // seasons (number_of_seasons)
        if (details != null && details.has("number_of_seasons")) {
            appendTag(sb, "seasons", String.valueOf(details.get("number_of_seasons").asInt()));
        }
        // outline
        appendTag(sb, "outline", details != null && details.has("overview") ? truncate(details.get("overview").asText(), 200) : null);
        // plot
        appendTag(sb, "plot", details != null && details.has("overview") ? details.get("overview").asText() : null);
        // tagline
        appendTag(sb, "tagline", details != null && details.has("tagline") ? details.get("tagline").asText() : null);

        // genres
        if (details != null && details.has("genres")) {
            for (JsonNode g : details.get("genres")) {
                appendTag(sb, "genre", g.get("name").asText());
            }
        }

        // country
        if (details != null && details.has("origin_country")) {
            for (JsonNode c : details.get("origin_country")) {
                appendTag(sb, "country", c.asText());
            }
        }

        // studio
        if (details != null && details.has("production_companies")) {
            for (JsonNode c : details.get("production_companies")) {
                appendTag(sb, "studio", c.get("name").asText());
            }
        }

        // actor (top cast)
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

        // uniqueid
        appendTag(sb, "uniqueid", info.getTmdbId(), "tmdb", "false");

        sb.append("</tvshow>\n");
        return sb.toString();
    }

    // ==================== Episode NFO ====================

    private String buildEpisodeNfo(MediaInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<episodedetails>\n");

        // title (使用文件名中的剧集标题，或默认)
        String episodeTitle = info.getTitle();
        if (StringUtils.isNotBlank(info.getEpisode())) {
            episodeTitle = info.getTitle() + " S" + padSeason(info.getSeason()) + "E" + info.getEpisode();
        }
        appendTag(sb, "title", episodeTitle);

        // season
        appendTag(sb, "season", info.getSeason());
        // episode
        appendTag(sb, "episode", info.getEpisode());

        // tmdbid
        appendTag(sb, "tmdbid", info.getTmdbId());

        // premiered
        if (info.getYear() != null) {
            appendTag(sb, "premiered", info.getYear() + "-01-01");
        }

        // rating
        JsonNode details = getDetails(info);
        if (details != null && details.has("vote_average")) {
            appendTag(sb, "rating", String.format("%.1f", details.get("vote_average").asDouble(0)));
        }

        // plot
        appendTag(sb, "plot", details != null && details.has("overview") ? details.get("overview").asText() : null);

        // uniqueid
        appendTag(sb, "uniqueid", info.getTmdbId(), "tmdb", "false");

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

    private String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String sanitizeForNfo(String s) {
        if (StringUtils.isBlank(s)) return "unknown";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
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

    private void writeNfo(Path nfoFile, String content) throws IOException {
        Path parent = nfoFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(nfoFile, content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
