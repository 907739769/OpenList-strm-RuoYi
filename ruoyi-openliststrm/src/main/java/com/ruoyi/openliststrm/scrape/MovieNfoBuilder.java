package com.ruoyi.openliststrm.scrape;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.rename.model.MediaInfo;

import static com.ruoyi.openliststrm.scrape.NfoXmlBuilder.*;

/**
 * 电影 NFO 构建策略。
 * 根节点: &lt;movie&gt;
 */
public class MovieNfoBuilder implements NfoTypeStrategy {

    @Override
    public String buildNfo(MediaInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append(xmlHeader());
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
        appendThumbs(sb, details);

        sb.append("</movie>\n");
        return sb.toString();
    }
}
