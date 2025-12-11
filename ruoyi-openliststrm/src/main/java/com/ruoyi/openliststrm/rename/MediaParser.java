package com.ruoyi.openliststrm.rename;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.openai.OpenAIClient;
import com.ruoyi.openliststrm.rename.extractor.Extractor;
import com.ruoyi.openliststrm.rename.extractor.impl.*;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import com.ruoyi.openliststrm.rename.processor.TitleProcessor;
import com.ruoyi.openliststrm.rename.render.PebbleRenderer;
import com.ruoyi.openliststrm.tmdb.TMDbClient;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @Author Jack
 * @Date 2025/8/12 16:54
 * @Version 1.0.0
 */
@Slf4j
public class MediaParser {
    private final List<Extractor> extractors = new ArrayList<>();
    private final TitleProcessor titleProcessor = new TitleProcessor();
    private final TMDbClient tmdbClient; // may be null
    private final OpenAIClient openAIClient; // may be null
    private final PebbleRenderer renderer = new PebbleRenderer();

    public MediaParser(TMDbClient tmdbClient) {
        this(tmdbClient, null);
    }

    public MediaParser(TMDbClient tmdbClient, OpenAIClient openAIClient) {
        this.tmdbClient = tmdbClient;
        this.openAIClient = openAIClient;
        // 默认顺序（可在构造时注入）
        extractors.add(new ResolutionExtractor());
        extractors.add(new CodecExtractor());
        extractors.add(new SourceAndGroupExtractor());
        extractors.add(new YearSeasonEpisodeExtractor());
    }

    public String rename(String filename, String template) {
        MediaInfo info = parse(filename);
        return render(info, template);
    }

    public MediaInfo parse(String filename) {
        return parse(filename, null, null);
    }

    public MediaInfo parse(String filename,String title,String year) {
        log.info("开始重命名文件名: {}", filename);
        String extension = "";
        String baseName = filename;
        if (filename.lastIndexOf('.') != -1) {
            extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            baseName = filename.substring(0, filename.lastIndexOf('.')).trim();
        }
        String norm = normalize(baseName);
        MediaInfo info = new MediaInfo(filename);
        info.setExtension(extension);
        String remaining = norm;
        for (Extractor ex : extractors) {
            remaining = ex.extract(remaining, info);
        }
        titleProcessor.processTitle(remaining, info);
        if (StringUtils.isNotEmpty(title)) {
            info.setTitle(title);
        }
        if (StringUtils.isNotEmpty(year)) {
            info.setYear(year);
        }
        if (tmdbClient != null) {
            tmdbClient.enrich(info);
        }

        // Only call OpenAI when still missing key/accurate fields
        if (openAIClient != null && needsAI(info)) {
            try {
                log.info("使用AI识别: {}", filename);
                boolean updated = openAIClient.enrich(info, filename);
                // If updated, and tmdb client exists, we could re-run tmdb lookup with new titles
                if (updated && tmdbClient != null) {
                    tmdbClient.enrich(info);
                }
            } catch (Exception e) {
                log.info("使用AI识别失败: {}", filename);
                log.error("", e);
            }
        }

        log.debug("文件名称: {} 识别结果info: {}", filename, info);
        return info;
    }

    /**
     * 渲染输出，传入 Pebble 模板字符串
     */
    public String render(MediaInfo info, String template) {
        return renderer.render(info, template);
    }

    private String normalize(String s) {
        // replace _ and . with spaces, but keep 中文
        String t = s.replace('_', ' ').replaceAll("\\.(?=[A-Za-z0-9])", " ");
        return t.trim();
    }

    /**
     * Decide whether to call AI: if title/originalTitle missing, or for TV items season/episode missing.
     * If TMDb already provided tmdbId and title/year/season+episode are present, we don't call AI.
     */
    private boolean needsAI(MediaInfo info) {
        return StringUtils.isBlank(info.getTmdbId());
    }

    public static void main(String[] args) {
        // If you have keys, set them as env vars OPENAI_API_KEY and TMDB_API_KEY, or edit below.
        String tmdbKey = System.getenv().getOrDefault("TMDB_API_KEY", "");
        String openaiKey = System.getenv().getOrDefault("OPENAI_API_KEY", "");
        String openaiEndpoint = System.getenv().getOrDefault("OPENAI_ENDPOINT", "");
        String openaiModel = System.getenv().getOrDefault("OPENAI_MODEL", "");
        TMDbClient tmdb = (tmdbKey == null || tmdbKey.isEmpty()) ? null : new TMDbClient(tmdbKey);
        OpenAIClient openai = null;
        if (openaiKey != null && !openaiKey.isEmpty()) {
            String modelToPass = (openaiModel == null || openaiModel.isEmpty()) ? null : openaiModel;
            openai = new OpenAIClient(openaiKey, openaiEndpoint, modelToPass);
        }

        MediaParser parser = new MediaParser(tmdb, openai);

        List<String> samples = Arrays.asList(
//                "芭芭拉.Barbara.2012.FRA.BDRip.1080p.x265.10bit.DDP5.1-DGB.mkv",
//                "The Legend of Lu Xiao Feng 2007 E06 WEB-DL 2160p H265 AAC-Dave.mp4"
//                "Hao.Tuan.Yuan.S01E31.1080p.TX.WEB-DL.AAC2.0.H.264-MWeb.strm"
//                "斗破苍穹年番.Fights.Break.Sphere.S05E165.2022.2160p.WEB-DL.H265.AAC-HHWEB.strm"
//                "The.The.Legendary.Swordsman.2001.E40.2160p.WEB-DL.H264.AAC-HDPTWeb.strm"
//                "Bakusou.Kyoudai.Let's.&.Go.MAX.EP01.1998.1080p.BluRay.x265.10bit.FLAC.2.0.3Audio-ADE.strm"
//                "命悬一生.The.Hunt.S01E10.2025.2160p.WEB-DL.H265.HDR.DDP5.1-HHWEB.strm"
//                "Dead Winter 2025 1080p WEB H264-SLOT.strm"
                "[向往的生活 第二季 先导片].Back.to.field.Pre.2018.S02E01.2160p.WEB-DL.H265.AAC-UBWEB.strm"
//                "Lie.to.Me.S01E12.Blinded.1080p.DSNP.WEB-DL.DDP5.1.H264-HHWEB.mkv",
//                "凡人修仙传.A.Record.Of.Mortals.Journey.To.Immortality.S01E155.2020.2160p.WEB-DL.H264.AAC-ADWeb.mp4"
//                "Bitch.x.Rich.Season.2.S02E08.2025.1080p.friDay.WEB-DL.H264.AAC-ADWeb.mkv",
//                "[拼出未来].Piece.by.Piece.2024.1080p.WEB-DL.H264.MPEG-OurTV.mp4",
//                "[海贼王剧场版].Onepiece.Film.Strong.World.2009.BluRay.720p.x264.AC3-CMCT.mkv",
//                "Severance.S02E10.2022.2160p.ATVP.WEB-DL.DDP5.1.Atmos.HDR.H.265-HHWEB.strm",
//                "One.Piece.S01E01.2023.2160p.NF.WEB-DL.DDP5.1.Atmos.H.265-HHWEB.mkv",
//                "One.Hundred.Thousand.Years.of.Qi.Refining.S01E260.2160p.TX.WEB-DL.AAC2.0.H.265-MWeb.strm",
//                "定风波.The.Wanted.Detective.S01E25.2025.2160p.V2.WEB-DL.H265.EDR.DDP5.1-HHWEB.strm",
//                "海贼王.One.Piece.S01E1139.1999.1080p.WEB-DL.H264.AAC-ADWeb.strm",
//                "对我说谎试试.Lie.To.Me.S01E02.2011.1080p.NF.WEB-DL.DDP2.0.x264-Ao.strm",
//                "[仙剑奇侠传三].Chinese.Paladin.3.2009.S03E22.2160p.V2.60fps.WEB-DL.HEVC.10bit.AAC-QHstudIo.strm"
        );

        String template = "{{ title }} {% if year %} ({{ year }}) {% endif %}/{% if season %}Season {{ season }}/{% endif %}{{ title }} {% if year and not season %} ({{ year }}) {% endif %}{% if season %}S{{ season }}{% endif %}{% if episode %}E{{ episode }}{% endif %}{% if resolution %} - {{ resolution }}{% endif %}{% if source %}.{{ source }}{% endif %}{% if videoCodec %}.{{ videoCodec }}{% endif %}{% if audioCodec %}.{{ audioCodec }}{% endif %}{% if tags is not empty %}.{{ tags|join('.') }}{% endif %}{% if releaseGroup %}-{{ releaseGroup }}{% endif %}.{{ extension }}";

        for (String s : samples) {
            MediaInfo info = parser.parse(s);
            String out = parser.render(info, template);
            System.out.println("IN:  " + s);
            System.out.println("OUT: " + out);
            System.out.println("---- raw fields: " + info.getTitle() + " | year=" + info.getYear() + " | tags=" + info.getTags());
            System.out.println(info);
            System.out.println();
        }
    }

}