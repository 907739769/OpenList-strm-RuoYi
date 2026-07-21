package com.ruoyi.openliststrm.pt.filter;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import lombok.extern.slf4j.Slf4j;

/**
 * 把全局过滤配置与订阅级覆盖合并成一份生效的 {@link FilterCriteria}。
 * <p>
 * 覆盖以 JSON 存在 pt_subscription.filter_override，键名与 {@link PtFilterConfigPlus}
 * 的字段名一致，值的形态也与数据库一致（逗号分隔串、"0"/"1"、数字）。
 * <b>只有出现在 JSON 里的键才覆盖</b>，没出现的沿用全局值。
 * </p>
 *
 * @author Jack
 */
@Slf4j
public final class FilterCriteriaFactory {

    private FilterCriteriaFactory() {
    }

    /**
     * @param global   全局配置，字段允许为 null（使用安全默认值）
     * @param override 订阅级覆盖 JSON，允许为 null / 空白 / 格式损坏
     */
    public static FilterCriteria build(PtFilterConfigPlus global, String override) {
        JSONObject patch = parseOverride(override);

        return new FilterCriteria(
                intOf(patch, "minSeeders", global.getMinSeeders()),
                longOf(patch, "minSize", global.getMinSize()),
                longOf(patch, "maxSize", global.getMaxSize()),
                "1".equals(strOf(patch, "freeOnly", global.getFreeOnly())),
                FilterCriteria.splitCsv(strOf(patch, "includeKeywords", global.getIncludeKeywords())),
                FilterCriteria.splitCsv(strOf(patch, "excludeKeywords", global.getExcludeKeywords())),
                FilterCriteria.splitCsv(strOf(patch, "resolutionPriority", global.getResolutionPriority())),
                SortDimension.parseCsv(strOf(patch, "sortPriority", global.getSortPriority())),
                longOf(patch, "preferredSize", global.getPreferredSize()));
    }

    /**
     * 解析覆盖 JSON。为 null/空白、格式非法、或不是 JSON 对象时一律返回空 patch，
     * 使全部字段退回全局配置——这份配置是用户手填的，一条坏数据不该让整轮轮询挂掉。
     */
    private static JSONObject parseOverride(String override) {
        if (StringUtils.isBlank(override)) {
            return new JSONObject();
        }
        try {
            JSONObject parsed = JSONObject.parseObject(override);
            return parsed == null ? new JSONObject() : parsed;
        } catch (Exception e) {
            log.warn("订阅级过滤覆盖不是合法的 JSON 对象，已整体退回全局配置：{}", e.getMessage());
            return new JSONObject();
        }
    }

    private static String strOf(JSONObject patch, String key, String fallback) {
        // 注意用 containsKey 而非判空：显式传 "" 意味着「这部剧不设该项」，是有效覆盖
        return patch.containsKey(key) ? patch.getString(key) : fallback;
    }

    private static int intOf(JSONObject patch, String key, Integer fallback) {
        if (patch.containsKey(key)) {
            Integer value = patch.getInteger(key);
            if (value != null) {
                return value;
            }
        }
        return fallback == null ? 0 : fallback;
    }

    private static long longOf(JSONObject patch, String key, Long fallback) {
        if (patch.containsKey(key)) {
            Long value = patch.getLong(key);
            if (value != null) {
                return value;
            }
        }
        return fallback == null ? 0L : fallback;
    }
}
