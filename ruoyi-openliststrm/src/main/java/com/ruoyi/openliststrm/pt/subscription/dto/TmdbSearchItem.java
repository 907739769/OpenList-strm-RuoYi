package com.ruoyi.openliststrm.pt.subscription.dto;

import lombok.Data;

/**
 * TMDb 搜索结果条目，供前端选片。
 *
 * @author Jack
 */
@Data
public class TmdbSearchItem {

    /** TMDb ID */
    private String tmdbId;

    /** 媒体类型 TV / MOVIE */
    private String mediaType;

    /** 中文标题（剧集取 name，电影取 title） */
    private String title;

    /** 原始标题（剧集取 original_name，电影取 original_title） */
    private String originalTitle;

    /** 首播/上映年份，解析不出时为 null */
    private String year;

    /** 海报路径（TMDb 的相对路径，如 /abc.jpg） */
    private String posterPath;

    /** 简介 */
    private String overview;
}
