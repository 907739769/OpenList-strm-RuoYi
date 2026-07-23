package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.openliststrm.helper.TgHelper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtIndexerPlusService;
import com.ruoyi.openliststrm.pt.indexer.TorznabClient;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RssPollServiceTest {

    @Mock private IPtIndexerPlusService indexerService;
    @Mock private TorznabClient torznabClient;
    @Mock private SubscriptionEngine subscriptionEngine;

    private RssPollService service() {
        return new RssPollService(indexerService, torznabClient, subscriptionEngine);
    }

    private PtIndexerPlus indexer(int id, Integer pollInterval, java.util.Date lastPoll, int failCount) {
        PtIndexerPlus i = new PtIndexerPlus();
        i.setId(id);
        i.setName("idx-" + id);
        i.setPollInterval(pollInterval);
        i.setLastPollTime(lastPoll);
        i.setFailCount(failCount);
        i.setEnabled("1");
        return i;
    }

    private TorrentInfo torrent(String title) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle(title);
        return t;
    }

    @Test
    void 从未轮询过的索引器_视为到期_会拉取() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1, 600, null, 0)));
        when(torznabClient.fetch(any())).thenReturn(List.of(torrent("t1")));

        service().poll();

        verify(torznabClient).fetch(any());
        verify(subscriptionEngine).process(anyList());
    }

    @Test
    void 未到轮询周期的索引器_跳过不拉取() throws Exception {
        // 刚轮询过（1 秒前），周期 600 秒
        when(indexerService.listEnabled()).thenReturn(
                List.of(indexer(1, 600, new java.util.Date(System.currentTimeMillis() - 1000), 0)));

        service().poll();

        verify(torznabClient, never()).fetch(any());
        verify(subscriptionEngine, never()).process(anyList());
    }

    @Test
    void 已过轮询周期_到期拉取() throws Exception {
        // 上次 700 秒前，周期 600 秒 → 到期
        when(indexerService.listEnabled()).thenReturn(
                List.of(indexer(1, 600, new java.util.Date(System.currentTimeMillis() - 700_000), 0)));
        when(torznabClient.fetch(any())).thenReturn(List.of(torrent("t1")));

        service().poll();

        verify(torznabClient).fetch(any());
    }

    @Test
    void 拉取成功_更新索引器状态为OK并清零失败计数() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1, 600, null, 2)));
        when(torznabClient.fetch(any())).thenReturn(List.of(torrent("t1")));

        service().poll();

        ArgumentCaptor<PtIndexerPlus> captor = ArgumentCaptor.forClass(PtIndexerPlus.class);
        verify(indexerService).updateById(captor.capture());
        assertEquals("OK", captor.getValue().getLastStatus());
        assertEquals(0, captor.getValue().getFailCount());
    }

    @Test
    void 拉取失败_累加失败计数并记录错误() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1, 600, null, 0)));
        when(torznabClient.fetch(any())).thenThrow(new IOException("connection refused"));

        service().poll();

        ArgumentCaptor<PtIndexerPlus> captor = ArgumentCaptor.forClass(PtIndexerPlus.class);
        verify(indexerService).updateById(captor.capture());
        assertEquals(1, captor.getValue().getFailCount());
        // 失败时不调用引擎
        verify(subscriptionEngine, never()).process(anyList());
    }

    @Test
    void 多个索引器的种子被汇总后一次性交给引擎() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(
                indexer(1, 600, null, 0), indexer(2, 600, null, 0)));
        when(torznabClient.fetch(any())).thenReturn(List.of(torrent("a")), List.of(torrent("b")));

        service().poll();

        ArgumentCaptor<List<TorrentInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(subscriptionEngine).process(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void 无到期索引器_不调用引擎() throws Exception {
        when(indexerService.listEnabled()).thenReturn(
                List.of(indexer(1, 600, new java.util.Date(), 0)));

        service().poll();

        verify(subscriptionEngine, never()).process(anyList());
    }

    @Test
    void 无启用索引器_不做任何事() {
        when(indexerService.listEnabled()).thenReturn(List.of());

        service().poll();

        verify(subscriptionEngine, never()).process(anyList());
    }

    @Test
    void 连续失败第3次_发一次告警() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1, 600, null, 2)));
        when(torznabClient.fetch(any())).thenThrow(new IOException("connection refused"));

        try (MockedStatic<TgHelper> tg = mockStatic(TgHelper.class)) {
            service().poll();

            tg.verify(() -> TgHelper.sendMsg(argThat(m -> m.contains("已连续失败 3 次"))));
        }
    }

    @Test
    void 失败但未达第3次_不发告警() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1, 600, null, 0)));
        when(torznabClient.fetch(any())).thenThrow(new IOException("connection refused"));

        try (MockedStatic<TgHelper> tg = mockStatic(TgHelper.class)) {
            service().poll();

            tg.verify(() -> TgHelper.sendMsg(anyString()), never());
        }
    }

    // ---------- 自动降级 ----------

    @Test
    void 连续失败达到第10次_自动停用并告警一次() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1, 600, null, 9)));
        when(torznabClient.fetch(any())).thenThrow(new IOException("connection refused"));

        try (MockedStatic<TgHelper> tg = mockStatic(TgHelper.class)) {
            service().poll();

            ArgumentCaptor<PtIndexerPlus> captor = ArgumentCaptor.forClass(PtIndexerPlus.class);
            verify(indexerService).updateById(captor.capture());
            assertEquals("0", captor.getValue().getEnabled());
            assertEquals(10, captor.getValue().getFailCount());
            tg.verify(() -> TgHelper.sendMsg(argThat(m -> m.contains("已自动停用"))));
        }
    }

    @Test
    void 连续失败未达第10次_仍启用不停用() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1, 600, null, 5)));
        when(torznabClient.fetch(any())).thenThrow(new IOException("connection refused"));

        service().poll();

        ArgumentCaptor<PtIndexerPlus> captor = ArgumentCaptor.forClass(PtIndexerPlus.class);
        verify(indexerService).updateById(captor.capture());
        assertEquals("1", captor.getValue().getEnabled());
    }

    @Test
    void 达到第3次告警阈值时不会同时触发停用() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1, 600, null, 2)));
        when(torznabClient.fetch(any())).thenThrow(new IOException("connection refused"));

        try (MockedStatic<TgHelper> tg = mockStatic(TgHelper.class)) {
            service().poll();

            ArgumentCaptor<PtIndexerPlus> captor = ArgumentCaptor.forClass(PtIndexerPlus.class);
            verify(indexerService).updateById(captor.capture());
            assertEquals("1", captor.getValue().getEnabled());
            tg.verify(() -> TgHelper.sendMsg(argThat(m -> m.contains("已连续失败 3 次"))));
        }
    }
}
