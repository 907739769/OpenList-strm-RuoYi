package com.ruoyi.openliststrm.pt.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorrentFilterEngineFilterTest {

    private final TorrentFilterEngine engine = new TorrentFilterEngine();

    private FilterCriteria criteria(int minSeeders, long minSize, long maxSize, boolean freeOnly,
                                    List<String> include, List<String> exclude) {
        return new FilterCriteria(minSeeders, minSize, maxSize, freeOnly, include, exclude,
                List.of("1080p"), List.of(SortDimension.SEEDERS), 0L);
    }

    private TorrentInfo torrent(String title, int seeders, long size, boolean free) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle(title);
        t.setSeeders(seeders);
        t.setSize(size);
        t.setDownloadVolumeFactor(free ? 0.0 : 1.0);
        return t;
    }

    private TorrentInfo ok() {
        return torrent("Some.Show.S01E05.1080p.WEB-DL", 10, 5_000_000_000L, false);
    }

    @Test
    void 全部条件满足_保留() {
        List<TorrentInfo> result = engine.filter(List.of(ok()),
                criteria(1, 0L, 0L, false, List.of(), List.of()));

        assertEquals(1, result.size());
    }

    @Test
    void 做种数低于下限_淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("t", 2, 5_000_000_000L, false)),
                criteria(3, 0L, 0L, false, List.of(), List.of()));

        assertTrue(result.isEmpty());
    }

    @Test
    void 做种数等于下限_保留() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("t", 3, 5_000_000_000L, false)),
                criteria(3, 0L, 0L, false, List.of(), List.of()));

        assertEquals(1, result.size());
    }

    @Test
    void 体积小于下限_淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("t", 10, 500L, false)),
                criteria(0, 1_000L, 0L, false, List.of(), List.of()));

        assertTrue(result.isEmpty());
    }

    @Test
    void 体积大于上限_淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("t", 10, 90_000_000_000L, false)),
                criteria(0, 0L, 50_000_000_000L, false, List.of(), List.of()));

        assertTrue(result.isEmpty());
    }

    @Test
    void 体积上下限为0_表示不限() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("t", 10, 1L, false), torrent("t2", 10, 999_999_999_999L, false)),
                criteria(0, 0L, 0L, false, List.of(), List.of()));

        assertEquals(2, result.size());
    }

    @Test
    void 仅要免费_非免费种被淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("free", 10, 100L, true), torrent("paid", 10, 100L, false)),
                criteria(0, 0L, 0L, true, List.of(), List.of()));

        assertEquals(1, result.size());
        assertEquals("free", result.get(0).getTitle());
    }

    @Test
    void 命中排除词_淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("Some.Show.预告片.1080p", 10, 100L, false)),
                criteria(0, 0L, 0L, false, List.of(), List.of("预告", "花絮")));

        assertTrue(result.isEmpty());
    }

    @Test
    void 排除词大小写不敏感() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("Some.Show.SAMPLES.1080p", 10, 100L, false)),
                criteria(0, 0L, 0L, false, List.of(), List.of("samples")));

        assertTrue(result.isEmpty());
    }

    @Test
    void 包含词非空_一个都没命中则淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("Some.Show.1080p.WEB-DL", 10, 100L, false)),
                criteria(0, 0L, 0L, false, List.of("中字", "国语"), List.of()));

        assertTrue(result.isEmpty());
    }

    @Test
    void 包含词命中其一即保留() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("Some.Show.1080p.中字", 10, 100L, false)),
                criteria(0, 0L, 0L, false, List.of("中字", "国语"), List.of()));

        assertEquals(1, result.size());
    }

    @Test
    void 排除优先于包含_同时命中两者时淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("Some.Show.中字.预告", 10, 100L, false)),
                criteria(0, 0L, 0L, false, List.of("中字"), List.of("预告")));

        assertTrue(result.isEmpty());
    }

    @Test
    void 多个候选_只保留合格的() {
        List<TorrentInfo> candidates = List.of(
                torrent("good.1080p", 10, 5_000_000_000L, false),
                torrent("低做种.1080p", 1, 5_000_000_000L, false),
                torrent("预告.1080p", 10, 5_000_000_000L, false),
                torrent("good2.1080p", 20, 5_000_000_000L, false));

        List<TorrentInfo> result = engine.filter(candidates,
                criteria(5, 0L, 0L, false, List.of(), List.of("预告")));

        assertEquals(List.of("good.1080p", "good2.1080p"),
                result.stream().map(TorrentInfo::getTitle).toList());
    }

    @Test
    void 输入为空列表_返回空列表() {
        assertTrue(engine.filter(List.of(), criteria(0, 0L, 0L, false, List.of(), List.of())).isEmpty());
    }

    @Test
    void 标题为null的候选_被淘汰而非抛异常() {
        TorrentInfo t = torrent(null, 10, 100L, false);

        List<TorrentInfo> result = engine.filter(List.of(t),
                criteria(0, 0L, 0L, false, List.of(), List.of("预告")));

        assertTrue(result.isEmpty());
    }

    @Test
    void 结果列表不含原列表引用_不会被调用方修改() {
        List<TorrentInfo> result = engine.filter(List.of(ok()),
                criteria(0, 0L, 0L, false, List.of(), List.of()));

        // 返回新列表而非原列表的视图
        result.add(ok());
        assertEquals(2, result.size());
    }

    @Test
    void 淘汰记录写debug日志且原因具体到规则阈值与实际值() {
        // 规格要求：被淘汰的种子记 debug 日志，且原因必须具体（哪条规则、阈值、实际值），
        // 不能只写一句"被过滤"。用 ListAppender 把日志内容锁住，而不是靠人工阅读代码保证。
        Logger logger = (Logger) LoggerFactory.getLogger(TorrentFilterEngine.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level originalLevel = logger.getLevel();
        // debug 日志默认级别通常不输出，不显式调低级别就看不到这条日志
        logger.setLevel(Level.DEBUG);
        try {
            engine.filter(List.of(torrent("做种不足的种子", 2, 5_000_000_000L, false)),
                    criteria(10, 0L, 0L, false, List.of(), List.of()));

            String logged = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.DEBUG)
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);

            assertTrue(logged.contains("做种数"), "应指明是哪条规则淘汰的，实际内容：" + logged);
            assertTrue(logged.contains("10"), "应带上配置的阈值，实际内容：" + logged);
            assertTrue(logged.contains("2"), "应带上种子的实际值，实际内容：" + logged);
        } finally {
            logger.setLevel(originalLevel);
            logger.detachAppender(appender);
        }
    }
}
