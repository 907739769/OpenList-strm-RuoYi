package com.ruoyi.openliststrm.pt.filter;

import com.ruoyi.common.utils.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 生效的过滤与排序条件——全局配置与订阅级覆盖合并后的最终结果。
 * <p>
 * 不可变。过滤引擎只认本类型，不直接读数据库，因此引擎是纯函数、可密集单测。
 * </p>
 *
 * @param minSeeders        最低做种数，低于此值淘汰
 * @param minSize           最小体积(字节)，0 表示不限
 * @param maxSize           最大体积(字节)，0 表示不限
 * @param freeOnly          是否仅要免费种
 * @param includeKeywords   标题须命中其一，空列表表示不限
 * @param excludeKeywords   标题命中任一则淘汰
 * @param resolutionPriority 分辨率优先级，越靠前越优先
 * @param sortPriority      排序维度顺序；传空列表时回退到 {@link #DEFAULT_SORT_PRIORITY}
 * @param preferredSize     体积接近度的目标值(字节)，0 表示该维度不参与比较
 * @author Jack
 */
public record FilterCriteria(
        int minSeeders,
        long minSize,
        long maxSize,
        boolean freeOnly,
        List<String> includeKeywords,
        List<String> excludeKeywords,
        List<String> resolutionPriority,
        List<SortDimension> sortPriority,
        long preferredSize) {

    /** 未配置排序维度时的兜底顺序，与建表脚本的默认值一致 */
    public static final List<SortDimension> DEFAULT_SORT_PRIORITY =
            List.of(SortDimension.RESOLUTION, SortDimension.FREE, SortDimension.SEEDERS, SortDimension.SIZE);

    public FilterCriteria {
        // 防御性拷贝 + 不可变化：调用方之后修改传入的列表不应影响已构造的条件
        includeKeywords = List.copyOf(includeKeywords);
        excludeKeywords = List.copyOf(excludeKeywords);
        resolutionPriority = List.copyOf(resolutionPriority);
        // 空的排序配置会让择优退化成"随便挑一个"，必须有兜底
        sortPriority = sortPriority.isEmpty() ? DEFAULT_SORT_PRIORITY : List.copyOf(sortPriority);
    }

    /**
     * 把逗号分隔的配置串切成列表，逐项去空白、丢弃空项。
     * 输入为 null 或空白时返回空列表。
     */
    public static List<String> splitCsv(String csv) {
        if (StringUtils.isBlank(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
