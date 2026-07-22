package com.ruoyi.openliststrm.pt.task;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloadRecordPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DownloadTrackServiceTest {

    @Mock private IPtDownloadRecordPlusService recordService;
    @Mock private IPtSubscriptionEpisodePlusService episodeService;

    private DownloadTrackService service() {
        return new DownloadTrackService(recordService, episodeService);
    }

    private PtDownloaderPlus downloader() {
        PtDownloaderPlus d = new PtDownloaderPlus();
        d.setId(1);
        d.setTag("osr-pt");
        return d;
    }

    private PtDownloadRecordPlus record(int id, int episode, String tag, String state, long pushedAgoMs) {
        PtDownloadRecordPlus r = new PtDownloadRecordPlus();
        r.setId(id);
        r.setSubId(10);
        r.setEpisode(episode);
        r.setTrackingTag(tag);
        r.setState(state);
        r.setTitle("Some.Show.S01E0" + episode);
        r.setPushedTime(new Date(System.currentTimeMillis() - pushedAgoMs));
        return r;
    }

    private DownloaderTorrent torrent(String tags, double progress) {
        DownloaderTorrent t = new DownloaderTorrent();
        t.setHash("h");
        t.setName("n");
        t.setProgress(progress);
        t.setTags(tags);
        return t;
    }

    @Test
    void 完成的种子_记录置完成_集状态不动() {
        PtDownloadRecordPlus r = record(100, 2, "osr-pt-aaa", "DOWNLOADING", 60_000);
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(r));

        service().track(downloader(), List.of(torrent("osr-pt,osr-pt-aaa", 1.0)));

        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).updateById(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getState());
        // 集状态不该被改（等 Emby 对账）
        verify(episodeService, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void 未完成的种子_记录置下载中() {
        PtDownloadRecordPlus r = record(100, 2, "osr-pt-aaa", "PUSHED", 60_000);
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(r));

        service().track(downloader(), List.of(torrent("osr-pt,osr-pt-aaa", 0.35)));

        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).updateById(captor.capture());
        assertEquals("DOWNLOADING", captor.getValue().getState());
    }

    @Test
    void 找不到种子且推送已超宽限期_记录置失败且集回退缺失() {
        // 宽限期 10 分钟，这条推送了 20 分钟还找不到
        PtDownloadRecordPlus r = record(100, 2, "osr-pt-aaa", "DOWNLOADING", 20 * 60_000);
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(r));
        when(episodeService.list(any(Wrapper.class))).thenReturn(List.of(episodeRow(500)));

        service().track(downloader(), List.of(torrent("osr-pt,osr-pt-other", 0.5)));

        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).updateById(captor.capture());
        assertEquals("FAILED", captor.getValue().getState());
        // 关联集回退 MISSING
        verify(episodeService).update(any(), any(Wrapper.class));
    }

    @Test
    void 找不到种子但推送未超宽限期_本轮跳过() {
        // 刚推送 1 分钟，qB 可能还在解析元数据
        PtDownloadRecordPlus r = record(100, 2, "osr-pt-aaa", "PUSHED", 60_000);
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(r));

        service().track(downloader(), List.of(torrent("osr-pt,osr-pt-other", 0.5)));

        verify(recordService, never()).updateById(any());
        verify(episodeService, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void 季包失败_关联的多个集全部回退() {
        PtDownloadRecordPlus r = record(100, -1, "osr-pt-pack", "DOWNLOADING", 20 * 60_000);
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(r));
        when(episodeService.list(any(Wrapper.class))).thenReturn(List.of(episodeRow(501), episodeRow(502)));

        service().track(downloader(), List.of(torrent("osr-pt", 0.5)));

        // 两个集都回退
        verify(episodeService, org.mockito.Mockito.times(2)).update(any(), any(Wrapper.class));
    }

    @Test
    void 已完成的记录不重复处理() {
        // list 只查 PUSHED/DOWNLOADING，COMPLETED 的不在结果里——用空列表模拟
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of());

        service().track(downloader(), List.of(torrent("osr-pt,osr-pt-done", 1.0)));

        verify(recordService, never()).updateById(any());
    }

    @Test
    void 无在途记录_不做任何事() {
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of());

        service().track(downloader(), List.of());

        verify(recordService, never()).updateById(any());
    }

    private PtSubscriptionEpisodePlus episodeRow(int id) {
        PtSubscriptionEpisodePlus ep = new PtSubscriptionEpisodePlus();
        ep.setId(id);
        ep.setState("IN_FLIGHT");
        return ep;
    }
}
