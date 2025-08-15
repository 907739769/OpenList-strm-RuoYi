package com.ruoyi.openliststrm.rename;

import com.ruoyi.openliststrm.rename.extractor.Extractor;
import com.ruoyi.openliststrm.rename.extractor.impl.*;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import com.ruoyi.openliststrm.rename.processor.TitleProcessor;
import com.ruoyi.openliststrm.rename.render.PebbleRenderer;
import com.ruoyi.openliststrm.tmdb.TMDbClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @Author Jack
 * @Date 2025/8/12 16:54
 * @Version 1.0.0
 */
public class MediaParser {
    private final List<Extractor> extractors = new ArrayList<>();
    private final TitleProcessor titleProcessor = new TitleProcessor();
    private final TMDbClient tmdbClient; // may be null
    private final PebbleRenderer renderer = new PebbleRenderer();

    public MediaParser(TMDbClient tmdbClient) {
        this.tmdbClient = tmdbClient;
        // 默认顺序（可在构造时注入）
        extractors.add(new ResolutionExtractor());
        extractors.add(new CodecExtractor());
        extractors.add(new SourceAndGroupExtractor());
        extractors.add(new YearSeasonEpisodeExtractor());
    }

    public MediaInfo parse(String filename) {
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
        if (tmdbClient != null) {
            tmdbClient.enrich(info);
        }
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

    public static void main(String[] args) {
        // 如果没有 TMDb Key，可传 null
        String tmdbKey = "xxxx"; // 或直接放在配置文件
        TMDbClient tmdb = (tmdbKey == null || tmdbKey.isEmpty()) ? null : new TMDbClient(tmdbKey);

        MediaParser parser = new MediaParser(tmdb);

        List<String> samples = Arrays.asList(
//                "芭芭拉.Barbara.2012.FRA.BDRip.1080p.x265.10bit.DDP5.1-DGB.mkv",
                "[Spy x Family Code White][Tokuten BD][SP02][First Day Theater Greeting][BDRIP][1080P][H264_DTS-HDMA].mkv"
//                "Lie.to.Me.S01E12.Blinded.1080p.DSNP.WEB-DL.DDP5.1.H264-HHWEB.mkv",
//                "凡人修仙传.A.Record.Of.Mortals.Journey.To.Immortality.S01E155.2020.2160p.WEB-DL.H264.AAC-ADWeb.mp4",
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

        String template = "{{ m.title }} {% if m.year %} ({{m.year }}) {% endif %}{% if m.season %}S{{ m.season }}{% endif %}{% if m.episode %}E{{ m.episode }}{% endif %}{% if m.resolution %} - {{ m.resolution }}{% endif %}{% if m.source %}.{{ m.source }}{% endif %}{% if m.videoCodec %}.{{ m.videoCodec }}{% endif %}{% if m.audioCodec %}.{{ m.audioCodec }}{% endif %}{% if m.tags is not empty %}.{{ m.tags|join('.') }}{% endif %}{% if m.releaseGroup %}-{{ m.releaseGroup }}{% endif %}.{{ m.extension }}";

        for (String s : samples) {
            MediaInfo info = parser.parse(s);
            String out = parser.render(info, template);
            System.out.println("IN:  " + s);
            System.out.println("OUT: " + out);
            System.out.println("---- raw fields: " + info.getOriginalTitle() + " | year=" + info.getYear() + " | tags=" + info.getTags());
            System.out.println(info);
            System.out.println();
        }
    }

}