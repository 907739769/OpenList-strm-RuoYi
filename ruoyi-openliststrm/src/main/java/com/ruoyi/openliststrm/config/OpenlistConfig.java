package com.ruoyi.openliststrm.config;

import com.ruoyi.system.service.ISysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * @Author Jack
 * @Date 2025/7/16 19:32
 * @Version 1.0.0
 */
@Component
public class OpenlistConfig {

    @Autowired
    private ISysConfigService sysConfigService;

    //	openlist-访问地址
    public String getOpenListUrl() {
        return sysConfigService.selectConfigByKey("openlist.server.url");
    }

    //openlist-api访问token
    public String getOpenListToken() {
        return sysConfigService.selectConfigByKey("openlist.server.token");
    }

    //复制的最小文件
    public String getOpenListMinFileSize() {
        return sysConfigService.selectConfigByKey("openlist.copy.minfilesize");
    }

    //复制完文件生成strm
    public String getOpenListCopyStrm() {
        return sysConfigService.selectConfigByKey("openlist.copy.strm");
    }

    //tg机器人token
    public String getOpenListTgToken() {
        return sysConfigService.selectConfigByKey("openlist.tg.token");
    }

    //tg用户id
    public String getOpenListTgUserId() {
        return sysConfigService.selectConfigByKey("openlist.tg.userid");
    }

    //Apikey
    public String getOpenListApiKey() {
        return sysConfigService.selectConfigByKey("openlist.api.apikey");
    }

    // TMDb API Key (stored in sys_config as 'openlist.tmdb.apikey')
    public String getTmdbApiKey() {
        return sysConfigService.selectConfigByKey("openlist.tmdb.apikey");
    }

    // OpenAI API Key (stored in sys_config as 'openlist.openai.apikey')
    public String getOpenAiApiKey() {
        return sysConfigService.selectConfigByKey("openlist.openai.apikey");
    }

    // OpenAI service endpoint / host. If configured, this can be a full URL (including scheme and path)
    // or just a host/domain like 'api.chatanywhere.tech'. If empty, clients should default to OpenAI's endpoint.
    public String getOpenAiEndpoint() {
        return sysConfigService.selectConfigByKey("openlist.openai.endpoint");
    }

    // OpenAI model name (stored in sys_config as 'openlist.openai.model'). If empty, clients should use a sensible default.
    public String getOpenAiModel() {
        return sysConfigService.selectConfigByKey("openlist.openai.model");
    }

    // STRM输出目录 (默认: /data/strm)
    public String getOpenListStrmOutputDir() {
        String value = sysConfigService.selectConfigByKey("openlist.strm.outputdir");
        return (value != null && !value.isBlank()) ? value : "/data/strm";
    }

    // STRM路径编码开关 (默认: 0-不编码)
    public String getOpenListStrmEncode() {
        String value = sysConfigService.selectConfigByKey("openlist.strm.encode");
        return (value != null && !value.isBlank()) ? value : "0";
    }

    // STRM下载字幕开关 (默认: 0-不下载)
    public String getOpenListStrmDownloadSub() {
        String value = sysConfigService.selectConfigByKey("openlist.strm.downloadsub");
        return (value != null && !value.isBlank()) ? value : "0";
    }

    // API refresh开关 (默认: 1-开启)
    public String getOpenListApiRefresh() {
        String value = sysConfigService.selectConfigByKey("openlist.api.refresh");
        return (value != null && !value.isBlank()) ? value : "1";
    }

    // TMDb图片语言偏好 (默认: zh)
    public String getTmdbImageLanguage() {
        String value = sysConfigService.selectConfigByKey("openlist.tmdb.image.language");
        return (value != null && !value.isBlank()) ? value : "zh";
    }

    // TMDb元数据（标题、简介等）请求语言 (默认: zh-CN)
    public String getTmdbMetadataLanguage() {
        String value = sysConfigService.selectConfigByKey("openlist.tmdb.metadata.language");
        return (value != null && !value.isBlank()) ? value : "zh-CN";
    }

    // TMDb下载图片的尺寸 (默认: original；可选 w780/w500/w342 等以节省带宽和存储，见 TMDb 官方图片尺寸文档)
    public String getTmdbImageSize() {
        String value = sysConfigService.selectConfigByKey("openlist.tmdb.image.size");
        return (value != null && !value.isBlank()) ? value : "original";
    }

    /**
     * 获取最小文件大小（字节）。
     * 配置项存储的是 MB 值，此方法将其转换为字节。
     * 默认值：1 MB
     */
    public long getMinFileSizeBytes() {
        try {
            return Long.parseLong(getOpenListMinFileSize()) * 1024 * 1024;
        } catch (Exception e) {
            return 1L * 1024 * 1024;
        }
    }

    /**
     * 本地目录浏览接口允许访问的根目录白名单（逗号分隔）。
     * 未配置时默认仅允许挂载的 /data 目录，避免管理端接口可以枚举整个宿主机文件系统。
     */
    public List<String> getAllowedLocalRoots() {
        String value = sysConfigService.selectConfigByKey("openlist.local.allowedroots");
        if (value == null || value.isBlank()) {
            return List.of("/data");
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 复制任务状态监控的最长持续时间（分钟）。超过该时长仍未结束的任务会被强制标记为异常，
     * 停止继续调度，避免下游长期卡在非终态时，调度任务无限期堆积。
     * 未配置或配置非法时默认 600 分钟。
     */
    public long getCopyMonitorMaxMinutes() {
        String value = sysConfigService.selectConfigByKey("openlist.copy.monitor.maxminutes");
        if (value == null || value.isBlank()) {
            return 600L;
        }
        try {
            long minutes = Long.parseLong(value.trim());
            return minutes > 0 ? minutes : 600L;
        } catch (NumberFormatException e) {
            return 600L;
        }
    }

}
