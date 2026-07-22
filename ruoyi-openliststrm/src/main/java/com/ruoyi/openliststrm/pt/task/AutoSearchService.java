package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtFilterConfigPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.subscription.SearchSupplementService;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionMatcher;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自动补搜业务逻辑：对开启了 auto_search 且到期的订阅，按季/整部粒度发起一次搜索。
 * 只做整季/整部粒度（不逐集搜索），避免缺集很多的老剧每轮对索引器打太多请求。
 *
 * @author Jack
 */
@Slf4j
@Service
public class AutoSearchService {

    private static final String STATE_MISSING = "MISSING";
    private static final String AUTO_SEARCH_ON = "1";
    private static final int DEFAULT_INTERVAL_HOURS = 24;

    private final IPtSubscriptionPlusService subscriptionService;
    private final IPtSubscriptionEpisodePlusService episodeService;
    private final IPtFilterConfigPlusService filterConfigService;
    private final SearchSupplementService searchSupplementService;

    public AutoSearchService(IPtSubscriptionPlusService subscriptionService,
                             IPtSubscriptionEpisodePlusService episodeService,
                             IPtFilterConfigPlusService filterConfigService,
                             SearchSupplementService searchSupplementService) {
        this.subscriptionService = subscriptionService;
        this.episodeService = episodeService;
        this.filterConfigService = filterConfigService;
        this.searchSupplementService = searchSupplementService;
    }

    /**
     * 扫一轮：对每个开启自动补搜、到期、且仍有缺集的订阅发起一次搜索。单个订阅异常不影响其他订阅。
     */
    public void run() {
        List<PtSubscriptionPlus> active = subscriptionService.listActive();
        if (active.isEmpty()) {
            return;
        }
        int intervalHours = resolveIntervalHours();
        long now = System.currentTimeMillis();

        for (PtSubscriptionPlus sub : active) {
            if (!AUTO_SEARCH_ON.equals(sub.getAutoSearch())) {
                continue;
            }
            if (!isDue(sub, intervalHours, now)) {
                continue;
            }
            try {
                trySearch(sub);
            } catch (Exception e) {
                log.warn("订阅[{}]自动补搜失败：{}", sub.getId(), e.getMessage());
            }
        }
    }

    private void trySearch(PtSubscriptionPlus sub) {
        List<PtSubscriptionEpisodePlus> episodes = episodeService.listBySubscription(sub.getId());
        boolean hasMissing = episodes.stream().anyMatch(ep -> STATE_MISSING.equals(ep.getState()));
        if (!hasMissing) {
            return;
        }
        boolean movie = SubscriptionService.TYPE_MOVIE.equalsIgnoreCase(sub.getMediaType());
        int episode = movie ? 0 : SubscriptionMatcher.SEASON_PACK;
        String keyword = movie ? sub.getTitle() : sub.getTitle() + " S" + pad(sub.getSeason());
        searchSupplementService.supplement(sub.getId(), episode, keyword);
    }

    private boolean isDue(PtSubscriptionPlus sub, int intervalHours, long now) {
        if (sub.getLastSearchTime() == null) {
            return true;
        }
        return now - sub.getLastSearchTime().getTime() >= intervalHours * 3600_000L;
    }

    private int resolveIntervalHours() {
        PtFilterConfigPlus config = filterConfigService.getConfig();
        Integer hours = config.getAutoSearchIntervalHours();
        return hours == null ? DEFAULT_INTERVAL_HOURS : hours;
    }

    private String pad(Integer season) {
        int s = season == null ? 0 : season;
        return s < 10 ? "0" + s : String.valueOf(s);
    }
}
