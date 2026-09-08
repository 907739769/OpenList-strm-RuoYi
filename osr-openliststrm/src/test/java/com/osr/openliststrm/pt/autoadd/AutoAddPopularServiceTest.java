package com.osr.openliststrm.pt.autoadd;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.osr.openliststrm.mybatisplus.domain.PtAutoAddRulePlus;
import com.osr.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.osr.openliststrm.mybatisplus.service.IPtAutoAddLogPlusService;
import com.osr.openliststrm.mybatisplus.service.IPtAutoAddRulePlusService;
import com.osr.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.osr.openliststrm.pt.autoadd.dto.AutoAddRunResult;
import com.osr.openliststrm.pt.autoadd.source.PopularItem;
import com.osr.openliststrm.pt.autoadd.source.PopularSource;
import com.osr.openliststrm.pt.subscription.SubscriptionSearchOnCreateTrigger;
import com.osr.openliststrm.pt.subscription.SubscriptionService;
import com.osr.openliststrm.pt.subscription.TmdbSearchService;
import com.osr.openliststrm.pt.subscription.dto.SubscribeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住「热门自动订阅建完订阅要发起一次补搜」。
 * <p>
 * 这一条曾经漏了，而<b>漏掉它不会让任何东西变红、也不会在日志里留下痕迹</b>：规则照常跑完、
 * 执行日志里一排 ADDED，只是那些订阅的进度恒为 0。因为 {@code auto_search} 的库默认是 '0'，
 * 定期补搜的候选 SQL 要求那个开关开着，于是自动订进来的剧一条主动搜索路径都不走，
 * 只剩 RSS 轮询碰运气——而榜单里的热门剧多半已经播了几集，历史集的种子早出窗口了。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoAddPopularServiceTest {

    @Mock
    private PopularSource source;

    @Mock
    private IPtAutoAddRulePlusService ruleService;

    @Mock
    private IPtAutoAddLogPlusService logService;

    @Mock
    private IPtSubscriptionPlusService subscriptionPlusService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private TmdbSearchService tmdbSearchService;

    @Mock
    private PopularItemResolver resolver;

    @Mock
    private SubscriptionSearchOnCreateTrigger searchOnCreateTrigger;

    @InjectMocks
    private AutoAddPopularService service;

    @BeforeEach
    void setUp() {
        // sources 是 List<PopularSource>，@InjectMocks 填不进去
        ReflectionTestUtils.setField(service, "sources", List.of(source));
        when(source.supports(any())).thenReturn(true);
        when(source.fetch(any())).thenReturn(List.of(item()));
        // 没有同作品同季的存量订阅
        when(subscriptionPlusService.count(any(Wrapper.class))).thenReturn(0L);
    }

    private PtAutoAddRulePlus rule() {
        PtAutoAddRulePlus rule = new PtAutoAddRulePlus();
        rule.setId(1);
        rule.setName("TMDb 热门剧集");
        rule.setSource("TMDB_POPULAR");
        rule.setMediaType("TV");
        rule.setMaxAddPerRun(5);
        return rule;
    }

    /** 已带 tmdbId 与季号：不走补全，也不打 TMDb 查最新季 */
    private PopularItem item() {
        PopularItem item = new PopularItem();
        item.setTmdbId("1399");
        item.setMediaType("TV");
        item.setTitle("权力的游戏");
        item.setSeasonNumber(1);
        return item;
    }

    private PtSubscriptionPlus sub(String status) {
        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setId(42);
        sub.setTitle("权力的游戏");
        sub.setSeason(1);
        sub.setStatus(status);
        return sub;
    }

    @Test
    void 建订阅成功后应发起一次建订阅补搜() {
        when(subscriptionService.subscribe(any(SubscribeRequest.class)))
                .thenReturn(sub(SubscriptionService.STATUS_ACTIVE));

        AutoAddRunResult result = service.runRule(rule());

        assertEquals(1, result.getAddedCount());
        verify(searchOnCreateTrigger).triggerAsync(eq(42));
    }

    /**
     * 建订阅时已经对过一次账，全部集都在库的订阅直接是 COMPLETED——没有可补的东西，
     * 补搜只会白打一轮索引器请求。
     */
    @Test
    void 订阅建完就是已完成时不补搜() {
        when(subscriptionService.subscribe(any(SubscribeRequest.class)))
                .thenReturn(sub("COMPLETED"));

        service.runRule(rule());

        verify(searchOnCreateTrigger, never()).triggerAsync(any());
    }

    @Test
    void 建订阅失败时不补搜() {
        when(subscriptionService.subscribe(any(SubscribeRequest.class)))
                .thenThrow(new IllegalArgumentException("已存在该订阅"));

        AutoAddRunResult result = service.runRule(rule());

        assertEquals(0, result.getAddedCount());
        assertEquals(1, result.getFailedCount());
        verify(searchOnCreateTrigger, never()).triggerAsync(any());
    }

    /**
     * 触发补搜失败不能把这条翻成 FAILED：订阅此时已经建成功了，而 FAILED 那条日志
     * 是用来解释「这轮为什么没加」的。
     */
    @Test
    void 补搜触发失败不影响订阅已新增的结论() {
        when(subscriptionService.subscribe(any(SubscribeRequest.class)))
                .thenReturn(sub(SubscriptionService.STATUS_ACTIVE));
        doThrow(new IllegalStateException("调度器已关闭")).when(searchOnCreateTrigger).triggerAsync(any());

        AutoAddRunResult result = service.runRule(rule());

        assertEquals(1, result.getAddedCount());
        assertEquals(0, result.getFailedCount());
    }
}
