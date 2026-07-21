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
 * PT 下载器配置
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
@Getter
@Setter
@TableName("pt_downloader")
public class PtDownloaderPlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 下载器展示名 */
    @TableField("name")
    private String name;

    /** 下载器类型，当前仅 QBITTORRENT */
    @TableField("type")
    private String type;

    /** 主机名或IP，不含协议与端口 */
    @TableField("host")
    private String host;

    /** 端口 */
    @TableField("port")
    private Integer port;

    /** 是否使用 https 0-否 1-是 */
    @TableField("use_https")
    private String useHttps;

    /** 用户名 */
    @TableField("username")
    private String username;

    /** 密码，明文存储 */
    @TableField("password")
    private String password;

    /** 种子保存路径 */
    @TableField("save_path")
    private String savePath;

    /** 推送时打的标签 */
    @TableField("tag")
    private String tag;

    /** 是否启用 0-否 1-是 */
    @TableField("enabled")
    private String enabled;

    /**
     * 拼装下载器 Web UI 基地址，如 http://192.168.1.10:8080。
     * 末尾不带斜杠。
     */
    public String baseUrl() {
        String scheme = "1".equals(useHttps) ? "https" : "http";
        return scheme + "://" + host + ":" + port;
    }
}
