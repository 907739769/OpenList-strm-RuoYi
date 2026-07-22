package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtFilterConfigPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.subscription.SearchSupplementService;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoSearchServiceTest {

    @Mock private IPtSubscriptionPlusService subscriptionService;
    @Mock private IPtSubscriptionEpisodePlusService episodeService;
    @Mock private IPtFilterConfigPlusService filterConfigService;
    @Mock private SearchSupplementService searchSupplementService;

    private AutoSearchService service() {
        return new AutoSearchService(subscriptionService, episodeService, filterConfigService, searchSupplementService);
    }

    private PtSubscriptionPlus sub(int id, String mediaType, int season, String autoSearch, Date lastSearchTime) {
        PtSubscriptionPlus s = new PtSubscriptionPlus();
        s.setId(id);
        s.setMediaType(mediaType);
        s.setTitle(mediaType.equals("MOVIE") ? "Some Movie" : "Some Show");
        s.setSeason(season);
        s.setTotalEpisodes(10);
        s.setStatus("ACTIVE");
        s.setAutoSearch(autoSearch);
        s.setLastSearchTime(lastSearchTime);
        return s;
    }

    private PtFilterConfigPlus config(Integer intervalHours) {
        PtFilterConfigPlus c = new PtFilterConfigPlus();
        c.setAutoSearchIntervalHours(intervalHours);
        return c;
    }

    private PtSubscriptionEpisodePlus episode(int number, String state) {
        PtSubscriptionEpisodePlus ep = new PtSubscriptionEpisodePlus();
        ep.setEpisode(number);
        ep.setState(state);
        return ep;
    }

    @Test
    void 未开启自动补搜的订阅_跳过() {
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "0", null)));
        when(filterConfigService.getConfig()).thenReturn(config(24));

        service().run();

        verify(searchSupplementService, never()).supplement(any(), anyInt(), anyString());
    }

    @Test
    void 从未搜索过_视为到期_发起搜索() {
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "1", null)));
        when(filterConfigService.getConfig()).thenReturn(config(24));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(1, "MISSING")));

        service().run();

        verify(searchSupplementService).supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");
    }

    @Test
    void 未到周期_跳过() {
        Date recent = new Date(System.currentTimeMillis() - 3600_000L);
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "1", recent)));
        when(filterConfigService.getConfig()).thenReturn(config(24));

        service().run();

        verify(searchSupplementService, never()).supplement(any(), anyInt(), anyString());
    }

    @Test
    void 已过周期_发起搜索() {
        Date old = new Date(System.currentTimeMillis() - 25L * 3600_000L);
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "1", old)));
        when(filterConfigService.getConfig()).thenReturn(config(24));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(1, "MISSING")));

        service().run();

        verify(searchSupplementService).supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");
    }

    @Test
    void 没有任何缺失集_跳过不发请求() {
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "1", null)));
        when(filterConfigService.getConfig()).thenReturn(config(24));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(1, "IN_LIBRARY")));

        service().run();

        verify(searchSupplementService, never()).supplement(any(), anyInt(), anyString());
    }

    @Test
    void 电影订阅_目标集号恒为0() {
        when(subscriptionService.listActive()).thenReturn(List.of(sub(20, "MOVIE", 0, "1", null)));
        when(filterConfigService.getConfig()).thenReturn(config(24));
        when(episodeService.listBySubscription(20)).thenReturn(List.of(episode(0, "MISSING")));

        service().run();

        verify(searchSupplementService).supplement(20, 0, "Some Movie");
    }

    @Test
    void 全局周期未配置_默认24小时() {
        Date old = new Date(System.currentTimeMillis() - 25L * 3600_000L);
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "1", old)));
        when(filterConfigService.getConfig()).thenReturn(config(null));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(1, "MISSING")));

        service().run();

        verify(searchSupplementService).supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");
    }

    @Test
    void 单个订阅搜索抛异常_不影响其他订阅() {
        when(subscriptionService.listActive()).thenReturn(List.of(
                sub(10, "TV", 1, "1", null), sub(11, "TV", 1, "1", null)));
        when(filterConfigService.getConfig()).thenReturn(config(24));
        when(episodeService.listBySubscription(any())).thenReturn(List.of(episode(1, "MISSING")));
        when(searchSupplementService.supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01"))
                .thenThrow(new RuntimeException("boom"));

        service().run();

        verify(searchSupplementService).supplement(11, SubscriptionMatcher.SEASON_PACK, "Some Show S01");
    }
}
