package com.ruoyi.openliststrm.rename.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author Jack
 * @Date 2025/8/12 16:37
 * @Version 1.0.0
 */
@Data
public class MediaInfo {
    private String originalName;
    private String title; // 最终确定的标题（优先中文TMDb，否则原始处理结果）
    private String originalTitle; // 从文件名抽取的原始标题，优先中文
    private String englishTitle; // 从文件名抽取的英文标题
    private String year;
    private String season; // S
    private String episode; // E or episode number
    private String tmdbId;

    private String resolution; // e.g., 2160p, 1080p, 1080i
    private String videoCodec; // H264, H265, x264, HEVC
    private String audioCodec; // AAC, DDP5.1, DTS
    private String source; // WEB-DL, BluRay, HDTV, NF, AMZN, DSNP, etc
    private List<String> tags = new ArrayList<>(); // 特效、标签、HDR,10bit,60fps etc
    private String releaseGroup; // 发布组 HHWEB, MWeb
    private String extension;//文件后缀

    public MediaInfo(String originalName) {
        this.originalName = originalName;
    }

}
