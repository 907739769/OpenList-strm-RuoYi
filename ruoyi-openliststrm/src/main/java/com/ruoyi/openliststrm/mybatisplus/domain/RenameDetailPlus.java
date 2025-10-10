package com.ruoyi.openliststrm.mybatisplus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatisplus.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 重命名明细
 * </p>
 *
 * @author Jack
 * @since 2025-10-10
 */
@Getter
@Setter
@TableName("rename_detail")
public class RenameDetailPlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 自增主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 原文件路径
     */
    @TableField("originalPath")
    private String originalPath;

    /**
     * 原文件名称
     */
    @TableField("originalName")
    private String originalName;

    /**
     * 新文件路径
     */
    @TableField("newPath")
    private String newPath;

    /**
     * 新文件名称
     */
    @TableField("newName")
    private String newName;

    /**
     * 媒体类型
     */
    @TableField("media_type")
    private String mediaType;

    /**
     * 标题
     */
    @TableField("title")
    private String title;

    /**
     * 年份
     */
    @TableField("year")
    private String year;

    /**
     * 季
     */
    @TableField("season")
    private String season;

    /**
     * 集
     */
    @TableField("episode")
    private String episode;

    /**
     * tmdbId
     */
    @TableField("tmdbId")
    private String tmdbId;

    /**
     * 分辨率
     */
    @TableField("resolution")
    private String resolution;

    /**
     * 视频编码
     */
    @TableField("videoCodec")
    private String videoCodec;

    /**
     * 音频编码
     */
    @TableField("audioCodec")
    private String audioCodec;

    /**
     * 来源
     */
    @TableField("source")
    private String source;

    /**
     * 发布组
     */
    @TableField("releaseGroup")
    private String releaseGroup;

    /**
     * 状态
     */
    @TableField("status")
    private String status;
}
