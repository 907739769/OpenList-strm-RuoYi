package com.ruoyi.openliststrm.scrape;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ThreadTraceIdUtil;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * 刮削服务：重命名完成后异步生成 NFO 和下载图片。
 * 刮削失败不影响重命名结果。
 */
@Slf4j
@Service
public class ScrapeService {

    @Autowired
    private NfoGenerator nfoGenerator;

    @Autowired
    private MediaImageDownloader imageDownloader;

    @Autowired
    private OpenlistConfig config;

    @Autowired
    private IRenameDetailPlusService renameDetailService;

    /**
     * 异步执行刮削（NFO + 图片）。
     *
     * @param detailId    重命名明细 ID
     * @param info        媒体信息（已填充 TMDb 元数据）
     * @param mediaType   "movie" 或 "tv"
     * @param destFile    目标文件路径
     * @param outputDir   输出目录（系列级目录）
     * @param scrapeEnabled 是否启用刮削
     * @param scrapeNfo   是否生成 NFO
     * @param scrapeImages 是否下载图片
     */
    public void scrapeAsync(Integer detailId, MediaInfo info, String mediaType,
                            Path destFile, Path outputDir,
                            String scrapeEnabled, String scrapeNfo, String scrapeImages) {
        if (!"1".equals(scrapeEnabled)) {
            return;
        }

        AsyncManager.me().execute(() -> {
            ThreadTraceIdUtil.initTraceId();
            try {
                boolean anyScraped = false;

                if ("1".equals(scrapeNfo)) {
                    generateNfo(info, mediaType, destFile, outputDir);
                    anyScraped = true;
                }

                if ("1".equals(scrapeImages)) {
                    downloadImages(info, mediaType, outputDir);
                    anyScraped = true;
                }

                if (anyScraped && detailId != null) {
                    updateScrapeStatus(detailId, "1", null);
                }
            } catch (Exception e) {
                log.warn("刮削失败: {}", e.getMessage());
                if (detailId != null) {
                    String msg = e.getMessage();
                    if (msg != null && msg.length() > 500) {
                        msg = msg.substring(0, 500);
                    }
                    updateScrapeStatus(detailId, "2", msg);
                }
            }
        });
    }

    private void generateNfo(MediaInfo info, String mediaType, Path destFile, Path outputDir) throws Exception {
        if ("tv".equals(mediaType)) {
            nfoGenerator.generateTvNfo(info, destFile, outputDir);
        } else {
            nfoGenerator.generateMovieNfo(info, destFile, outputDir);
        }
    }

    private void downloadImages(MediaInfo info, String mediaType, Path outputDir) throws Exception {
        if ("tv".equals(mediaType)) {
            imageDownloader.downloadTvImages(info, outputDir);
        } else {
            imageDownloader.downloadMovieImages(info, outputDir);
        }
    }

    private void updateScrapeStatus(Integer detailId, String status, String msg) {
        try {
            UpdateWrapper<RenameDetailPlus> uw = new UpdateWrapper<>();
            uw.eq("id", detailId)
                    .set("scrape_status", status);
            if (StringUtils.isNotBlank(msg)) {
                uw.set("scrape_msg", msg);
            }
            renameDetailService.update(null, uw);
            log.debug("更新刮削状态: detailId={}, status={}", detailId, status);
        } catch (Exception e) {
            log.warn("更新刮削状态失败: {}", e.getMessage());
        }
    }
}
