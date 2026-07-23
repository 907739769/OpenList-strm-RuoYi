package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmTaskPlusService;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionService;
import com.ruoyi.openliststrm.service.IStrmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DownloadCompletionSyncServiceTest {

    @Mock private IOpenlistStrmTaskPlusService strmTaskService;
    @Mock private IStrmService strmService;
    @Mock private SubscriptionService subscriptionService;

    private DownloadCompletionSyncService service() {
        return new DownloadCompletionSyncService(strmTaskService, strmService, subscriptionService);
    }

    private PtDownloadRecordPlus record() {
        PtDownloadRecordPlus r = new PtDownloadRecordPlus();
        r.setId(100);
        r.setSubId(10);
        r.setTitle("Some.Show.S01E02");
        return r;
    }

    private PtDownloaderPlus downloader(Integer strmTaskId) {
        PtDownloaderPlus d = new PtDownloaderPlus();
        d.setId(1);
        d.setStrmTaskId(strmTaskId);
        return d;
    }

    private OpenlistStrmTaskPlus task(String status) {
        OpenlistStrmTaskPlus t = new OpenlistStrmTaskPlus();
        t.setStrmTaskId(5);
        t.setStrmTaskPath("/网盘/剧集");
        t.setStrmTaskStatus(status);
        return t;
    }

    @Test
    void 下载器为空_不做任何事() {
        service().sync(record(), null);

        verify(strmTaskService, never()).getById(any());
        verify(strmService, never()).strmDir(any());
        verify(subscriptionService, never()).refresh(any());
    }

    @Test
    void 下载器未关联STRM任务_不做任何事() {
        service().sync(record(), downloader(null));

        verify(strmTaskService, never()).getById(any());
        verify(strmService, never()).strmDir(any());
        verify(subscriptionService, never()).refresh(any());
    }

    @Test
    void 关联的STRM任务不存在_不触发生成也不对账() {
        when(strmTaskService.getById(5)).thenReturn(null);

        service().sync(record(), downloader(5));

        verify(strmService, never()).strmDir(any());
        verify(subscriptionService, never()).refresh(any());
    }

    @Test
    void 关联的STRM任务已停用_不触发生成也不对账() {
        when(strmTaskService.getById(5)).thenReturn(task("0"));

        service().sync(record(), downloader(5));

        verify(strmService, never()).strmDir(any());
        verify(subscriptionService, never()).refresh(any());
    }

    @Test
    void 任务存在且启用_触发STRM生成成功后立即对账订阅() {
        when(strmTaskService.getById(5)).thenReturn(task("1"));

        service().sync(record(), downloader(5));

        verify(strmService).strmDir(eq("/网盘/剧集"));
        verify(subscriptionService).refresh(eq(10));
    }

    @Test
    void STRM生成抛异常_不对账也不向外抛异常() {
        when(strmTaskService.getById(5)).thenReturn(task("1"));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(strmService).strmDir(any());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service().sync(record(), downloader(5)));

        verify(subscriptionService, never()).refresh(any());
    }

    @Test
    void 对账订阅抛异常_不向外抛异常() {
        when(strmTaskService.getById(5)).thenReturn(task("1"));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(subscriptionService).refresh(anyInt());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service().sync(record(), downloader(5)));
    }
}
