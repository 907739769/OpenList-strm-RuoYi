package com.ruoyi.openliststrm.pt.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.helper.TgHelper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloadRecordPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 下载追踪的编排逻辑：把下载器里的种子回映到下载记录并推进状态。
 * 抽成独立 Service 是为了脱离定时器单测。
 *
 * @author Jack
 */
@Slf4j
@Service
public class DownloadTrackService {

    private static final String STATE_PUSHED = "PUSHED";
    private static final String STATE_DOWNLOADING = "DOWNLOADING";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_FAILED = "FAILED";
    private static final String EP_MISSING = "MISSING";
    private static final String EP_IN_FLIGHT = "IN_FLIGHT";

    /** 推送后找不到对应种子的宽限期：超过它才判失败（qB 解析磁力元数据需要时间） */
    private static final long GRACE_MILLIS = 10 * 60 * 1000L;

    private final IPtDownloadRecordPlusService recordService;
    private final IPtSubscriptionEpisodePlusService episodeService;

    public DownloadTrackService(IPtDownloadRecordPlusService recordService,
                                IPtSubscriptionEpisodePlusService episodeService) {
        this.recordService = recordService;
        this.episodeService = episodeService;
    }

    /**
     * 追踪一个下载器：拉回来的种子已按公共标签过滤过，这里只做状态推进。
     */
    public void track(PtDownloaderPlus downloader, List<DownloaderTorrent> torrents) {
        List<PtDownloadRecordPlus> active = recordService.list(
                new QueryWrapper<PtDownloadRecordPlus>()
                        .eq("downloader_id", downloader.getId())
                        .in("state", STATE_PUSHED, STATE_DOWNLOADING));
        if (active.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PtDownloadRecordPlus record : active) {
            DownloaderTorrent matched = findByTag(torrents, record.getTrackingTag());
            if (matched == null) {
                handleMissing(record, now);
            } else if (matched.isCompleted()) {
                complete(record);
            } else if (!STATE_DOWNLOADING.equals(record.getState())) {
                record.setState(STATE_DOWNLOADING);
                recordService.updateById(record);
            }
        }
    }

    private DownloaderTorrent findByTag(List<DownloaderTorrent> torrents, String trackingTag) {
        if (StringUtils.isBlank(trackingTag)) {
            return null;
        }
        for (DownloaderTorrent torrent : torrents) {
            if (StringUtils.isBlank(torrent.getTags())) {
                continue;
            }
            for (String tag : torrent.getTags().split(",")) {
                if (trackingTag.equals(tag.trim())) {
                    return torrent;
                }
            }
        }
        return null;
    }

    /**
     * 发通知但绝不让通知失败影响主流程。TgHelper 未配置时本就静默返回；
     * 单测环境下 SpringUtils.getBean 会抛异常，这里一并兜住。
     */
    private void notifySafely(String msg) {
        try {
            TgHelper.sendMsg(msg);
        } catch (Exception e) {
            log.debug("发送通知失败（不影响主流程）：{}", e.getMessage());
        }
    }

    private void complete(PtDownloadRecordPlus record) {
        if (STATE_COMPLETED.equals(record.getState())) {
            return;
        }
        record.setState(STATE_COMPLETED);
        record.setCompletedTime(new Date());
        recordService.updateById(record);
        notifySafely("下载完成：" + record.getTitle());
        log.info("下载记录[{}] 已完成：{}", record.getId(), record.getTitle());
        // 注意：集状态不动，仍是 IN_FLIGHT，等 LibrarySyncTask 通过 Emby 确认后转 IN_LIBRARY
    }

    private void handleMissing(PtDownloadRecordPlus record, long now) {
        long age = record.getPushedTime() == null ? Long.MAX_VALUE : now - record.getPushedTime().getTime();
        if (age < GRACE_MILLIS) {
            // 刚推送不久，qB 可能还在解析元数据，本轮先不判失败
            return;
        }
        record.setState(STATE_FAILED);
        record.setFailReason("下载器中已找不到该种子（可能被删除或元数据解析失败）");
        recordService.updateById(record);

        // 该记录关联的所有集回退 MISSING 并清 download_id（普通集1条、季包多条，统一处理）
        List<PtSubscriptionEpisodePlus> episodes = episodeService.list(
                new QueryWrapper<PtSubscriptionEpisodePlus>()
                        .eq("download_id", record.getId())
                        .eq("state", EP_IN_FLIGHT));
        for (PtSubscriptionEpisodePlus episode : episodes) {
            PtSubscriptionEpisodePlus set = new PtSubscriptionEpisodePlus();
            set.setState(EP_MISSING);
            set.setDownloadId(null);
            episodeService.update(set, new UpdateWrapper<PtSubscriptionEpisodePlus>()
                    .eq("id", episode.getId())
                    .eq("state", EP_IN_FLIGHT));
        }
        notifySafely("下载失败：" + record.getTitle() + "，已释放待下轮重新匹配");
        log.warn("下载记录[{}] 失败，{} 个集回退缺失：{}", record.getId(), episodes.size(), record.getTitle());
    }
}
