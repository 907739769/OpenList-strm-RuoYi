package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmTaskPlusService;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionService;
import com.ruoyi.openliststrm.service.IStrmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 下载完成后的联动同步：触发一次该下载器关联 STRM 任务路径的增量生成，成功后立即对账
 * 该订阅一次，不必等 {@link LibrarySyncTask} 的下一轮批量周期（默认 10 分钟）才发现已入库。
 * <p>
 * 下载器需在管理页关联一个 STRM 任务（{@code pt_downloader.strm_task_id}）才会触发——
 * 项目里没有可靠的方式从下载器保存路径自动反推 OpenList 网盘路径，只能由用户手动关联一次。
 * 触发的是该 STRM 任务配置的整个路径而非单个文件，与
 * {@code OpenlistStrmTaskRestController} 的"立即执行"复用同一入口；
 * {@link IStrmService#strmDir} 内部按已生成记录去重，重复触发成本很低。
 * </p>
 * <p>
 * 这条联动是锦上添花，不是下载记录状态本身的一部分：两步各自 try/catch，任一步失败都不
 * 影响另一步，也不影响已经写成功的 COMPLETED 记录，{@link LibrarySyncTask} 仍会兜底追上。
 * </p>
 *
 * @author Jack
 */
@Slf4j
@Service
public class DownloadCompletionSyncService {

    private final IOpenlistStrmTaskPlusService strmTaskService;
    private final IStrmService strmService;
    private final SubscriptionService subscriptionService;

    public DownloadCompletionSyncService(IOpenlistStrmTaskPlusService strmTaskService,
                                         IStrmService strmService,
                                         SubscriptionService subscriptionService) {
        this.strmTaskService = strmTaskService;
        this.strmService = strmService;
        this.subscriptionService = subscriptionService;
    }

    public void sync(PtDownloadRecordPlus record, PtDownloaderPlus downloader) {
        if (downloader == null || downloader.getStrmTaskId() == null) {
            return;
        }
        boolean strmOk = triggerStrm(record, downloader.getStrmTaskId());
        refreshSubscription(record, strmOk);
    }

    private boolean triggerStrm(PtDownloadRecordPlus record, Integer strmTaskId) {
        try {
            OpenlistStrmTaskPlus task = strmTaskService.getById(strmTaskId);
            if (task == null || !"1".equals(task.getStrmTaskStatus())) {
                return false;
            }
            strmService.strmDir(task.getStrmTaskPath());
            log.info("下载记录[{}] 完成后已触发 STRM 任务[{}]增量生成", record.getId(), strmTaskId);
            return true;
        } catch (Exception e) {
            log.warn("下载记录[{}] 完成后触发 STRM 生成失败：{}", record.getId(), e.getMessage());
            return false;
        }
    }

    private void refreshSubscription(PtDownloadRecordPlus record, boolean strmOk) {
        if (!strmOk) {
            // STRM 都没生成成功，此刻对账 Emby 大概率还查不到，交给 LibrarySyncTask 下一轮兜底
            return;
        }
        try {
            subscriptionService.refresh(record.getSubId());
        } catch (Exception e) {
            log.warn("下载记录[{}] 完成后提前对账订阅[{}]失败：{}", record.getId(), record.getSubId(), e.getMessage());
        }
    }
}
