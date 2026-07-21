package com.ruoyi.openliststrm.pt.filter;

import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterCriteriaFactoryTest {

    private PtFilterConfigPlus globalConfig() {
        PtFilterConfigPlus c = new PtFilterConfigPlus();
        c.setMinSeeders(3);
        c.setMinSize(1_000L);
        c.setMaxSize(90_000_000_000L);
        c.setFreeOnly("0");
        c.setIncludeKeywords(null);
        c.setExcludeKeywords("预告,花絮");
        c.setResolutionPriority("2160p,1080p,720p");
        c.setSortPriority("RESOLUTION,FREE,SEEDERS,SIZE");
        c.setPreferredSize(0L);
        return c;
    }

    @Test
    void 无覆盖_全部沿用全局配置() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), null);

        assertEquals(3, c.minSeeders());
        assertEquals(1_000L, c.minSize());
        assertEquals(90_000_000_000L, c.maxSize());
        assertFalse(c.freeOnly());
        assertEquals(List.of("预告", "花絮"), c.excludeKeywords());
        assertEquals(List.of("2160p", "1080p", "720p"), c.resolutionPriority());
        assertEquals(FilterCriteria.DEFAULT_SORT_PRIORITY, c.sortPriority());
    }

    @Test
    void 空字符串覆盖_等同于无覆盖() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "   ");

        assertEquals(3, c.minSeeders());
        assertEquals(List.of("2160p", "1080p", "720p"), c.resolutionPriority());
    }

    @Test
    void 部分覆盖_只有出现的键被替换_其余沿用全局() {
        // 典型场景：这部剧我要 4K，其余保持默认
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "{\"resolutionPriority\":\"2160p\"}");

        assertEquals(List.of("2160p"), c.resolutionPriority());
        assertEquals(3, c.minSeeders(), "未出现在覆盖中的键必须沿用全局值");
        assertEquals(List.of("预告", "花絮"), c.excludeKeywords());
    }

    @Test
    void 覆盖数值型字段() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(),
                "{\"minSeeders\":10,\"minSize\":2000,\"maxSize\":3000,\"preferredSize\":2500}");

        assertEquals(10, c.minSeeders());
        assertEquals(2_000L, c.minSize());
        assertEquals(3_000L, c.maxSize());
        assertEquals(2_500L, c.preferredSize());
    }

    @Test
    void 覆盖仅免费开关() {
        assertTrue(FilterCriteriaFactory.build(globalConfig(), "{\"freeOnly\":\"1\"}").freeOnly());
        assertFalse(FilterCriteriaFactory.build(globalConfig(), "{\"freeOnly\":\"0\"}").freeOnly());
    }

    @Test
    void 覆盖排序维度顺序() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "{\"sortPriority\":\"FREE,SEEDERS\"}");

        assertEquals(List.of(SortDimension.FREE, SortDimension.SEEDERS), c.sortPriority());
    }

    @Test
    void 覆盖关键词_可以把全局排除词清空() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "{\"excludeKeywords\":\"\"}");

        // 显式传空串意味着「这部剧不排除任何关键词」，不能被当成"没覆盖"
        assertTrue(c.excludeKeywords().isEmpty());
    }

    @Test
    void 覆盖JSON非法_记警告并整体退回全局配置() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "{这不是合法JSON");

        assertEquals(3, c.minSeeders());
        assertEquals(List.of("2160p", "1080p", "720p"), c.resolutionPriority());
    }

    @Test
    void 覆盖JSON是数组而非对象_退回全局配置() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "[1,2,3]");

        assertEquals(3, c.minSeeders());
    }

    @Test
    void 全局配置字段为null_使用安全默认值而非NPE() {
        PtFilterConfigPlus empty = new PtFilterConfigPlus();

        FilterCriteria c = FilterCriteriaFactory.build(empty, null);

        assertEquals(0, c.minSeeders());
        assertEquals(0L, c.minSize());
        assertEquals(0L, c.maxSize());
        assertFalse(c.freeOnly());
        assertTrue(c.includeKeywords().isEmpty());
        assertTrue(c.excludeKeywords().isEmpty());
        assertTrue(c.resolutionPriority().isEmpty());
        assertEquals(FilterCriteria.DEFAULT_SORT_PRIORITY, c.sortPriority());
        assertEquals(0L, c.preferredSize());
    }
}
