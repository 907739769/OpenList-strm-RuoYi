package com.ruoyi.openliststrm.pt.subscription;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloadRecordPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloaderPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtFilterConfigPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.downloader.DownloaderClientFactory;
import com.ruoyi.openliststrm.pt.downloader.IDownloaderClient;
import com.ruoyi.openliststrm.pt.filter.TorrentFilterEngine;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.rename.MediaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionEngineTest {

    @Mock private IPtSubscriptionPlusService subscriptionService;
    @Mock private IPtSubscriptionEpisodePlusService episodeService;
    @Mock private IPtDownloadRecordPlusService recordService;
    @Mock private IPtDownloaderPlusService downloaderService;
    @Mock private IPtFilterConfigPlusService filterConfigService;
    @Mock private DownloaderClientFactory downloaderClientFactory;
    @Mock private IDownloaderClient downloaderClient;

    private SubscriptionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SubscriptionEngine(
                subscriptionService, episodeService, recordService, downloaderService,
                filterConfigService, downloaderClientFactory,
                new TorrentFilterEngine(), new SubscriptionMatcher());

        PtFilterConfigPlus config = new PtFilterConfigPlus();
        config.setMinSeeders(0);
        config.setMinSize(0L);
        config.setMaxSize(0L);
        config.setFreeOnly("0");
        config.setResolutionPriority("2160p,1080p,720p");
        config.setSortPriority("RESOLUTION,SEEDERS");
        config.setPreferredSize(0L);
        when(filterConfigService.getConfig()).thenReturn(config);

        PtDownloaderPlus downloader = new PtDownloaderPlus();
        downloader.setId(1);
        downloader.setType("QBITTORRENT");
        downloader.setSavePath("/data/downloads");
        downloader.setTag("osr-pt");
        downloader.setEnabled("1");
        when(downloaderService.list(any(Wrapper.class))).thenReturn(List.of(downloader));
        when(downloaderClientFactory.get(any())).thenReturn(downloaderClient);

        when(recordService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(recordService.save(any())).thenReturn(true);
        // 默认占位成功
        when(episodeService.update(any(), any(Wrapper.class))).thenReturn(true);
    }

    private PtSubscriptionPlus tvSub(int id, String title, int season, int total) {
        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setId(id);
        sub.setMediaType("TV");
        sub.setTitle(title);
        sub.setSeason(season);
        sub.setTotalEpisodes(total);
        sub.setStatus("ACTIVE");
        return sub;
    }

    private PtSubscriptionEpisodePlus episode(int id, int number, String state) {
        PtSubscriptionEpisodePlus ep = new PtSubscriptionEpisodePlus();
        ep.setId(id);
        ep.setEpisode(number);
        ep.setState(state);
        return ep;
    }

    private TorrentInfo torrent(String title, String guid, int seeders, String resolution) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle(title);
        t.setGuid(guid);
        t.setSeeders(seeders);
        t.setSize(5_000_000_000L);
        t.setIndexerId(1);
        t.setDownloadUrl("http://indexer/download?id=" + guid);
        return t;
    }

    // ---------- 基本推送 ----------

    @Test
    void 命中缺失集_推送并落记录() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 3)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "MISSING"), episode(102, 2, "MISSING"), episode(103, 3, "MISSING")));

        int pushed = engine.process(List.of(torrent("Some.Show.S01E02.1080p.WEB-DL", "g1", 10, "1080p")));

        assertEquals(1, pushed);
        verify(downloaderClient).addTorrent(any(), anyString(), anyString(), anyString());

        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).save(captor.capture());
        PtDownloadRecordPlus record = captor.getValue();
        assertEquals(10, record.getSubId());
        assertEquals(2, record.getEpisode());
        assertEquals("g1", record.getGuid());
        assertEquals("PUSHED", record.getState());
        assertEquals(64, record.getGuidHash().length());
        assertTrue(record.getTrackingTag().startsWith("osr-pt-"));
        assertEquals("osr-pt-" + record.getGuidHash().substring(0, 16), record.getTrackingTag());
    }

    @Test
    void 推送时带上下载器的保存路径与标签() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));

        engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 10, "1080p")));

        ArgumentCaptor<String> savePath = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tag = ArgumentCaptor.forClass(String.class);
        verify(downloaderClient).addTorrent(any(), anyString(), savePath.capture(), tag.capture());
        assertEquals("/data/downloads", savePath.getValue());
        // 公共标签 + 唯一标签，用逗号分隔一次打上
        assertTrue(tag.getValue().contains("osr-pt"));
    }

    // ---------- 跳过 ----------

    @Test
    void 无活跃订阅_不做任何事() {
        when(subscriptionService.listActive()).thenReturn(List.of());

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 10, "1080p"))));
        verify(episodeService, never()).listBySubscription(any());
    }

    @Test
    void 匹配不到订阅_跳过() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Other Show", 1, 3)));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E02.1080p", "g1", 10, "1080p"))));
        verify(downloaderClient, never()).addTorrent(any(), anyString(), anyString(), anyString());
    }

    @Test
    void 目标集已在途_跳过不重复推送() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 2)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "MISSING"), episode(102, 2, "IN_FLIGHT")));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E02.1080p", "g1", 10, "1080p"))));
        verify(downloaderClient, never()).addTorrent(any(), anyString(), anyString(), anyString());
    }

    @Test
    void 目标集已入库_跳过() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 2)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "MISSING"), episode(102, 2, "IN_LIBRARY")));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E02.1080p", "g1", 10, "1080p"))));
    }

    @Test
    void 该guid已有下载记录_剔除不重复推送() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 2)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(102, 2, "MISSING")));
        PtDownloadRecordPlus existing = new PtDownloadRecordPlus();
        existing.setGuidHash(com.ruoyi.openliststrm.pt.indexer.GuidHasher.hash("g1"));
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(existing));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E02.1080p", "g1", 10, "1080p"))));
        verify(downloaderClient, never()).addTorrent(any(), anyString(), anyString(), anyString());
    }

    @Test
    void 全部候选被过滤规则淘汰_跳过() throws Exception {
        PtFilterConfigPlus strict = new PtFilterConfigPlus();
        strict.setMinSeeders(100);
        strict.setMinSize(0L);
        strict.setMaxSize(0L);
        strict.setFreeOnly("0");
        strict.setResolutionPriority("1080p");
        strict.setSortPriority("SEEDERS");
        strict.setPreferredSize(0L);
        when(filterConfigService.getConfig()).thenReturn(strict);
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 3, "1080p"))));
    }

    @Test
    void CAS占位失败_跳过不推送() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));
        // 模拟并发轮询已抢走该集
        when(episodeService.update(any(), any(Wrapper.class))).thenReturn(false);

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 10, "1080p"))));
        verify(downloaderClient, never()).addTorrent(any(), anyString(), anyString(), anyString());
        verify(recordService, never()).save(any());
    }

    // ---------- 择优 ----------

    @Test
    void 同一集多个候选_只推最优的一个() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));

        int pushed = engine.process(List.of(
                torrent("Some.Show.S01E01.720p.WEB-DL", "g-720", 10, "720p"),
                torrent("Some.Show.S01E01.2160p.WEB-DL", "g-4k", 10, "2160p"),
                torrent("Some.Show.S01E01.1080p.WEB-DL", "g-1080", 10, "1080p")));

        assertEquals(1, pushed);
        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).save(captor.capture());
        // 默认排序维度是 RESOLUTION 优先，优先级列表 2160p 在最前
        assertEquals("g-4k", captor.getValue().getGuid());
    }

    // ---------- 季包 ----------

    @Test
    void 季包_一条记录集号为负一_所有缺失集共同指向它() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 4)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "IN_LIBRARY"), episode(102, 2, "MISSING"),
                episode(103, 3, "MISSING"), episode(104, 4, "IN_FLIGHT")));

        int pushed = engine.process(List.of(torrent("Some.Show.S01.1080p.WEB-DL.COMPLETE", "g-pack", 10, "1080p")));

        assertEquals(1, pushed);
        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).save(captor.capture());
        assertEquals(-1, captor.getValue().getEpisode());
        // 只占位 MISSING 的第 2、3 集，已入库和在途的不动
        verify(episodeService, times(2)).update(any(), any(Wrapper.class));
    }

    @Test
    void 季包_无缺失集_跳过() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 2)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "IN_LIBRARY"), episode(102, 2, "IN_LIBRARY")));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01.1080p.COMPLETE", "g-pack", 10, "1080p"))));
    }

    // ---------- 推送失败回滚 ----------

    @Test
    void 推送失败_删记录并把集改回缺失() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));
        PtDownloadRecordPlus saved = new PtDownloadRecordPlus();
        when(recordService.save(any())).thenAnswer(inv -> {
            ((PtDownloadRecordPlus) inv.getArgument(0)).setId(999);
            return true;
        });
        org.mockito.Mockito.doThrow(new IOException("qb down"))
                .when(downloaderClient).addTorrent(any(), anyString(), anyString(), anyString());

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 10, "1080p"))));

        verify(recordService).removeById(999);
        // 回滚：把占位过的集改回 MISSING（第二次 update 调用）
        verify(episodeService, times(2)).update(any(), any(Wrapper.class));
    }

    // ---------- 下载器 ----------

    @Test
    void 无启用的下载器_不推送并返回0() throws Exception {
        when(downloaderService.list(any(Wrapper.class))).thenReturn(List.of());
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 10, "1080p"))));
        verify(recordService, never()).save(any());
    }

    // ---------- 多订阅 ----------

    @Test
    void 多个订阅各命中一集_各推一个() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(
                tvSub(10, "Show A", 1, 1), tvSub(20, "Show B", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));
        when(episodeService.listBySubscription(20)).thenReturn(List.of(episode(201, 1, "MISSING")));

        int pushed = engine.process(List.of(
                torrent("Show.A.S01E01.1080p", "gA", 10, "1080p"),
                torrent("Show.B.S01E01.1080p", "gB", 10, "1080p")));

        assertEquals(2, pushed);
        verify(downloaderClient, times(2)).addTorrent(any(), anyString(), anyString(), anyString());
    }

    @Test
    void 空种子列表_返回0() {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));

        assertEquals(0, engine.process(List.of()));
    }
}
