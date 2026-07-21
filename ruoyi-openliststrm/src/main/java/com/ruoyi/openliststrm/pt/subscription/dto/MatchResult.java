package com.ruoyi.openliststrm.pt.subscription.dto;

import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 一条种子匹配到的订阅与集号。
 *
 * @author Jack
 */
@Data
@AllArgsConstructor
public class MatchResult {

    /** 匹配到的订阅 */
    private PtSubscriptionPlus subscription;

    /**
     * 集号。电影恒为 0；剧集为具体集号；
     * <b>-1 表示季包</b>（种子含整季，推送时该订阅所有缺失集共同指向同一条下载记录）。
     */
    private int episode;
}
