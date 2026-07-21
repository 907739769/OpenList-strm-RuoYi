package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.pt.subscription.dto.MatchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubscriptionMatcherTest {

    private final SubscriptionMatcher matcher = new SubscriptionMatcher();

    private PtSubscriptionPlus tvSub(int id, String title, int season) {
        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setId(id);
        sub.setMediaType("TV");
        sub.setTitle(title);
        sub.setSeason(season);
        return sub;
    }

    private PtSubscriptionPlus movieSub(int id, String title, String year) {
        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setId(id);
        sub.setMediaType("MOVIE");
        sub.setTitle(title);
        sub.setYear(year);
        sub.setSeason(0);
        return sub;
    }

    private TorrentInfo torrent(String parsedTitle, String year, Integer season, Integer episode) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle("raw-title");
        t.setParsedTitle(parsedTitle);
        t.setParsedYear(year);
        t.setParsedSeason(season);
        t.setParsedEpisode(episode);
        return t;
    }

    // ---------- 剧集 ----------

    @Test
    void 剧集_标题与季号都匹配_返回对应集() {
        MatchResult result = matcher.match(torrent("Some Show", null, 1, 5),
                List.of(tvSub(10, "Some Show", 1)));

        assertNotNull(result);
        assertEquals(10, result.getSubscription().getId());
        assertEquals(5, result.getEpisode());
    }

    @Test
    void 剧集_种子标题用点分隔_订阅标题用空格_仍能匹配() {
        MatchResult result = matcher.match(torrent("Some.Show", null, 1, 5),
                List.of(tvSub(10, "Some Show", 1)));

        assertNotNull(result);
        assertEquals(10, result.getSubscription().getId());
    }

    @Test
    void 剧集_大小写不同_仍能匹配() {
        MatchResult result = matcher.match(torrent("SOME show", null, 1, 5),
                List.of(tvSub(10, "Some Show", 1)));

        assertNotNull(result);
    }

    @Test
    void 剧集_标题是订阅标题的前缀_不匹配() {
        // The Office 不该吃掉 The Office US 的种子，反之亦然
        assertNull(matcher.match(torrent("The Office", null, 1, 5),
                List.of(tvSub(10, "The Office US", 1))));
        assertNull(matcher.match(torrent("The Office US", null, 1, 5),
                List.of(tvSub(10, "The Office", 1))));
    }

    @Test
    void 剧集_季号不同_不匹配() {
        assertNull(matcher.match(torrent("Some Show", null, 2, 5),
                List.of(tvSub(10, "Some Show", 1))));
    }

    @Test
    void 剧集_有季号无集号_识别为季包返回负一() {
        MatchResult result = matcher.match(torrent("Some Show", null, 1, null),
                List.of(tvSub(10, "Some Show", 1)));

        assertNotNull(result);
        assertEquals(-1, result.getEpisode());
    }

    @Test
    void 剧集_没有季号_不匹配() {
        assertNull(matcher.match(torrent("Some Show", null, null, 5),
                List.of(tvSub(10, "Some Show", 1))));
    }

    @Test
    void 剧集_中文标题_能匹配() {
        MatchResult result = matcher.match(torrent("大明王朝1566", null, 1, 12),
                List.of(tvSub(10, "大明王朝1566", 1)));

        assertNotNull(result);
        assertEquals(12, result.getEpisode());
    }

    // ---------- 电影 ----------

    @Test
    void 电影_标题与年份都匹配_集号为0() {
        MatchResult result = matcher.match(torrent("Fight Club", "1999", null, null),
                List.of(movieSub(20, "Fight Club", "1999")));

        assertNotNull(result);
        assertEquals(20, result.getSubscription().getId());
        assertEquals(0, result.getEpisode());
    }

    @Test
    void 电影_年份不同_不匹配() {
        // 同名翻拍太常见，年份不符宁可漏也不能串台
        assertNull(matcher.match(torrent("Fight Club", "2020", null, null),
                List.of(movieSub(20, "Fight Club", "1999"))));
    }

    @Test
    void 电影_种子无年份_不匹配() {
        assertNull(matcher.match(torrent("Fight Club", null, null, null),
                List.of(movieSub(20, "Fight Club", "1999"))));
    }

    @Test
    void 电影_订阅无年份_不匹配() {
        assertNull(matcher.match(torrent("Fight Club", "1999", null, null),
                List.of(movieSub(20, "Fight Club", null))));
    }

    @Test
    void 电影_解析出季集信息_不匹配电影订阅() {
        // 带季集的一定是剧集，不该匹配到电影订阅
        assertNull(matcher.match(torrent("Fight Club", "1999", 1, 5),
                List.of(movieSub(20, "Fight Club", "1999"))));
    }

    // ---------- 通用 ----------

    @Test
    void 解析标题为空_不匹配() {
        assertNull(matcher.match(torrent(null, "1999", 1, 5), List.of(tvSub(10, "Some Show", 1))));
        assertNull(matcher.match(torrent("  ", "1999", 1, 5), List.of(tvSub(10, "Some Show", 1))));
    }

    @Test
    void 订阅列表为空_不匹配() {
        assertNull(matcher.match(torrent("Some Show", null, 1, 5), List.of()));
    }

    @Test
    void 多个订阅_只匹配到对的那个() {
        List<PtSubscriptionPlus> subs = List.of(
                tvSub(10, "Other Show", 1),
                tvSub(11, "Some Show", 2),
                tvSub(12, "Some Show", 1));

        MatchResult result = matcher.match(torrent("Some Show", null, 1, 5), subs);

        assertNotNull(result);
        assertEquals(12, result.getSubscription().getId());
    }
}
