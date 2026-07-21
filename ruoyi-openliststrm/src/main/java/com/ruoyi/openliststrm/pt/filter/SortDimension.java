package com.ruoyi.openliststrm.pt.filter;

/**
 * 择优时的排序维度。取值写入 pt_filter_config.sort_priority，逗号分隔。
 *
 * @author Jack
 */
public enum SortDimension {

    /** 分辨率匹配度，按 resolutionPriority 的先后顺序 */
    RESOLUTION,

    /** 是否免费种，免费优先 */
    FREE,

    /** 做种数，多者优先 */
    SEEDERS,

    /** 体积接近偏好值的程度，越接近越优先 */
    SIZE
}
