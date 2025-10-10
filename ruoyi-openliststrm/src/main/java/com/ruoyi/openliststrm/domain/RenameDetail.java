package com.ruoyi.openliststrm.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 重命名明细对象 rename_detail
 * 
 * @author Jack
 * @date 2025-09-30
 */
public class RenameDetail extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Integer id;

    /** 原文件路径 */
    @Excel(name = "原文件路径")
    private String originalPath;

    /** 原文件名称 */
    @Excel(name = "原文件名称")
    private String originalName;

    /** 新文件路径 */
    @Excel(name = "新文件路径")
    private String newPath;

    /** 新文件名称 */
    @Excel(name = "新文件名称")
    private String newName;

    /** 媒体类型 */
    @Excel(name = "媒体类型")
    private String mediaType;

    /** 标题 */
    @Excel(name = "标题")
    private String title;

    /** 年份 */
    @Excel(name = "年份")
    private String year;

    /** 季 */
    @Excel(name = "季")
    private String season;

    /** 集 */
    @Excel(name = "集")
    private String episode;

    /** tmdbId */
    @Excel(name = "tmdbId")
    private String tmdbId;

    /** 分辨率 */
    @Excel(name = "分辨率")
    private String resolution;

    /** 视频编码 */
    @Excel(name = "视频编码")
    private String videoCodec;

    /** 音频编码 */
    @Excel(name = "音频编码")
    private String audioCodec;

    /** 来源 */
    @Excel(name = "来源")
    private String source;

    /** 发布组 */
    @Excel(name = "发布组")
    private String releaseGroup;

    /** 状态 */
    @Excel(name = "状态")
    private String status;

    public void setId(Integer id) 
    {
        this.id = id;
    }

    public Integer getId() 
    {
        return id;
    }

    public void setOriginalPath(String originalPath) 
    {
        this.originalPath = originalPath;
    }

    public String getOriginalPath() 
    {
        return originalPath;
    }

    public void setOriginalName(String originalName) 
    {
        this.originalName = originalName;
    }

    public String getOriginalName() 
    {
        return originalName;
    }

    public void setNewPath(String newPath) 
    {
        this.newPath = newPath;
    }

    public String getNewPath() 
    {
        return newPath;
    }

    public void setNewName(String newName) 
    {
        this.newName = newName;
    }

    public String getNewName() 
    {
        return newName;
    }

    public void setMediaType(String mediaType) 
    {
        this.mediaType = mediaType;
    }

    public String getMediaType() 
    {
        return mediaType;
    }

    public void setTitle(String title) 
    {
        this.title = title;
    }

    public String getTitle() 
    {
        return title;
    }

    public void setYear(String year) 
    {
        this.year = year;
    }

    public String getYear() 
    {
        return year;
    }

    public void setSeason(String season) 
    {
        this.season = season;
    }

    public String getSeason() 
    {
        return season;
    }

    public void setEpisode(String episode) 
    {
        this.episode = episode;
    }

    public String getEpisode() 
    {
        return episode;
    }

    public void setTmdbId(String tmdbId) 
    {
        this.tmdbId = tmdbId;
    }

    public String getTmdbId() 
    {
        return tmdbId;
    }

    public void setResolution(String resolution) 
    {
        this.resolution = resolution;
    }

    public String getResolution() 
    {
        return resolution;
    }

    public void setVideoCodec(String videoCodec) 
    {
        this.videoCodec = videoCodec;
    }

    public String getVideoCodec() 
    {
        return videoCodec;
    }

    public void setAudioCodec(String audioCodec) 
    {
        this.audioCodec = audioCodec;
    }

    public String getAudioCodec() 
    {
        return audioCodec;
    }

    public void setSource(String source) 
    {
        this.source = source;
    }

    public String getSource() 
    {
        return source;
    }

    public void setReleaseGroup(String releaseGroup) 
    {
        this.releaseGroup = releaseGroup;
    }

    public String getReleaseGroup() 
    {
        return releaseGroup;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("originalPath", getOriginalPath())
            .append("originalName", getOriginalName())
            .append("newPath", getNewPath())
            .append("newName", getNewName())
            .append("mediaType", getMediaType())
            .append("title", getTitle())
            .append("year", getYear())
            .append("season", getSeason())
            .append("episode", getEpisode())
            .append("tmdbId", getTmdbId())
            .append("resolution", getResolution())
            .append("videoCodec", getVideoCodec())
            .append("audioCodec", getAudioCodec())
            .append("source", getSource())
            .append("releaseGroup", getReleaseGroup())
            .append("status", getStatus())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
