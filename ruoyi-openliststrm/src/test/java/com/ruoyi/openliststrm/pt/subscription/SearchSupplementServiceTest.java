package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtIndexerPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.indexer.TorznabClient;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.pt.subscription.dto.SupplementResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchSupplementServiceTest {

    @Mock private IPtIndexerPlusService indexerService;
    @Mock private TorznabClient torznabClient;
    @Mock private SubscriptionEngine subscriptionEngine;
    @Mock private IPtSubscriptionPlusService subscriptionService;

    private SearchSupplementService service;

    @BeforeEach
    void setUp() {
        service = new SearchSupplementService(indexerService, torznabClient, subscriptionEngine, subscriptionService);
    }

    private PtIndexerPlus indexer(int id) {
        PtIndexerPlus i = new PtIndexerPlus();
        i.setId(id);
        i.setName("idx-" + id);
        i.setEnabled("1");
        return i;
    }

    private PtSubscriptionPlus tvSub(int id, int season, int total) {
        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setId(id);
        sub.setMediaType("TV");
        sub.setTitle("Some Show");
        sub.setSeason(season);
        sub.setTotalEpisodes(total);
        sub.setStatus("ACTIVE");
        return sub;
    }

    private TorrentInfo torrent(String title) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle(title);
        return t;
    }

    // ---------- searchAcrossIndexers ----------

    @Test
    void 并发搜索所有启用索引器_合并结果() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1), indexer(2)));
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(torrent("a")), List.of(torrent("b")));

        List<TorrentInfo> results = service.searchAcrossIndexers("kw");

        assertEquals(2, results.size());
    }

    @Test
    void 单个索引器搜索失败_不影响其他索引器结果() throws Exception {
        PtIndexerPlus idx1 = indexer(1);
        PtIndexerPlus idx2 = indexer(2);
        when(indexerService.listEnabled()).thenReturn(List.of(idx1, idx2));
        // 注意：PtIndexerPlus 未覆写 equals()/hashCode()，继承自 BaseEntity 的 Lombok @Data
        // 生成版本只比较 createTime/updateTime/params——新建实体这些字段均为 null，
        // 导致不同 id 的两个实例被判为"相等"。用 same() 按引用而非 equals 区分两个桩，
        // 否则第二个 when() 的取参调用会命中第一个桩（idx1 的 thenThrow），在此处就直接抛出。
        when(torznabClient.search(same(idx1), eq("kw"))).thenThrow(new IOException("timeout"));
        when(torznabClient.search(same(idx2), eq("kw"))).thenReturn(List.of(torrent("b")));

        List<TorrentInfo> results = service.searchAcrossIndexers("kw");

        assertEquals(1, results.size());
        assertEquals("b", results.get(0).getTitle());
    }

    @Test
    void 无启用索引器_返回空列表() {
        when(indexerService.listEnabled()).thenReturn(List.of());

        assertTrue(service.searchAcrossIndexers("kw").isEmpty());
    }

    // ---------- supplement ----------

    @Test
    void supplement_成功推送_返回pushed为true并回写搜索时间() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
        TorrentInfo t = torrent("Some.Show.S01E02.1080p");
        t.setParsedSeason(1);
        t.setParsedEpisode(2);
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(t));
        when(subscriptionEngine.pushBest(eq(sub), eq(2), anyList())).thenReturn(true);

        SupplementResult result = service.supplement(10, 2, "Some Show S01E02");

        assertTrue(result.isPushed());
        assertEquals(1, result.getCandidateCount());
        ArgumentCaptor<PtSubscriptionPlus> captor = ArgumentCaptor.forClass(PtSubscriptionPlus.class);
        verify(subscriptionService).updateById(captor.capture());
        Assertions.assertNotNull(captor.getValue().getLastSearchTime());
    }

    @Test
    void supplement_搜索结果先做本地解析再交给引擎() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
        TorrentInfo raw = torrent("Some.Show.S01E02.1080p");
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(raw));

        service.supplement(10, 2, "Some Show S01E02");

        verify(subscriptionEngine).fillParsed(raw);
    }

    @Test
    void supplement_订阅不存在_抛异常() {
        when(subscriptionService.getById(99)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.supplement(99, 1, "kw"));
    }

    @Test
    void supplement_订阅已暂停_抛异常() {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        sub.setStatus("PAUSED");
        when(subscriptionService.getById(10)).thenReturn(sub);

        assertThrows(IllegalArgumentException.class, () -> service.supplement(10, 1, "kw"));
    }

    @Test
    void supplement_电影订阅传非0集号_抛异常() {
        PtSubscriptionPlus movie = new PtSubscriptionPlus();
        movie.setId(20);
        movie.setMediaType("MOVIE");
        movie.setStatus("ACTIVE");
        when(subscriptionService.getById(20)).thenReturn(movie);

        assertThrows(IllegalArgumentException.class, () -> service.supplement(20, 1, "kw"));
    }

    @Test
    void supplement_剧集集号超出总集数_抛异常() {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);

        assertThrows(IllegalArgumentException.class, () -> service.supplement(10, 4, "kw"));
    }

    @Test
    void supplement_季包哨兵值_剧集订阅允许通过() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(indexerService.listEnabled()).thenReturn(List.of());

        SupplementResult result = service.supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");

        assertFalse(result.isPushed());
    }

    // ---------- 季/集号一致性校验（filterByTarget） ----------

    @Test
    void supplement_季包目标_季号不匹配的候选被过滤_不会传给引擎() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
        TorrentInfo mismatch = torrent("Some.Show.S02.1080p");
        mismatch.setParsedSeason(2);
        mismatch.setParsedEpisode(null);
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(mismatch));

        SupplementResult result = service.supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");

        assertEquals(1, result.getCandidateCount());
        ArgumentCaptor<List<TorrentInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(subscriptionEngine).pushBest(eq(sub), eq(SubscriptionMatcher.SEASON_PACK), captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }

    @Test
    void supplement_季包目标_parsedEpisode不为null的单集候选被过滤() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
        TorrentInfo singleEpisode = torrent("Some.Show.S01E05.1080p");
        singleEpisode.setParsedSeason(1);
        singleEpisode.setParsedEpisode(5);
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(singleEpisode));

        SupplementResult result = service.supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");

        assertEquals(1, result.getCandidateCount());
        ArgumentCaptor<List<TorrentInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(subscriptionEngine).pushBest(eq(sub), eq(SubscriptionMatcher.SEASON_PACK), captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }

    @Test
    void supplement_单集目标_集号不匹配的候选被过滤() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
        TorrentInfo wrongEpisode = torrent("Some.Show.S01E05.1080p");
        wrongEpisode.setParsedSeason(1);
        wrongEpisode.setParsedEpisode(5);
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(wrongEpisode));

        SupplementResult result = service.supplement(10, 3, "Some Show S01E03");

        assertEquals(1, result.getCandidateCount());
        ArgumentCaptor<List<TorrentInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(subscriptionEngine).pushBest(eq(sub), eq(3), captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }

    @Test
    void supplement_parsedSeason为null的候选被过滤() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
        TorrentInfo unparsed = torrent("Some.Show.Unknown");
        unparsed.setParsedSeason(null);
        unparsed.setParsedEpisode(null);
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(unparsed));

        service.supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");

        ArgumentCaptor<List<TorrentInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(subscriptionEngine).pushBest(eq(sub), eq(SubscriptionMatcher.SEASON_PACK), captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }

    @Test
    void supplement_季号集号都匹配的候选能正常传给引擎() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
        TorrentInfo mismatch = torrent("Some.Show.S02E03.1080p");
        mismatch.setParsedSeason(2);
        mismatch.setParsedEpisode(3);
        TorrentInfo match = torrent("Some.Show.S01E03.1080p");
        match.setParsedSeason(1);
        match.setParsedEpisode(3);
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(mismatch, match));
        when(subscriptionEngine.pushBest(eq(sub), eq(3), anyList())).thenReturn(true);

        SupplementResult result = service.supplement(10, 3, "Some Show S01E03");

        assertTrue(result.isPushed());
        assertEquals(2, result.getCandidateCount());
        ArgumentCaptor<List<TorrentInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(subscriptionEngine).pushBest(eq(sub), eq(3), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertTrue(captor.getValue().contains(match));
        assertFalse(captor.getValue().contains(mismatch));
    }

    @Test
    void supplement_电影订阅不做季集校验_候选原样传给引擎() throws Exception {
        PtSubscriptionPlus movie = new PtSubscriptionPlus();
        movie.setId(20);
        movie.setMediaType("MOVIE");
        movie.setStatus("ACTIVE");
        when(subscriptionService.getById(20)).thenReturn(movie);
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
        TorrentInfo t = torrent("Some.Movie.2024.1080p");
        // 电影没有季集概念，本地解析不会给它设置 parsedSeason/parsedEpisode
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(t));
        when(subscriptionEngine.pushBest(eq(movie), eq(0), anyList())).thenReturn(true);

        SupplementResult result = service.supplement(20, 0, "Some Movie 2024");

        assertTrue(result.isPushed());
        assertEquals(1, result.getCandidateCount());
        ArgumentCaptor<List<TorrentInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(subscriptionEngine).pushBest(eq(movie), eq(0), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertTrue(captor.getValue().contains(t));
    }

    // ---------- validateEpisode 的 totalEpisodes null 安全 ----------

    @Test
    void supplement_剧集totalEpisodes为null_抛IllegalArgumentException而非NPE() {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        sub.setTotalEpisodes(null);
        when(subscriptionService.getById(10)).thenReturn(sub);

        assertThrows(IllegalArgumentException.class, () -> service.supplement(10, 2, "kw"));
    }
}
