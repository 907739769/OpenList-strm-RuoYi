package com.ruoyi.openliststrm.pt.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 搜索补集的结果，返回给前端展示成功/无结果提示。
 *
 * @author Jack
 */
@Data
@AllArgsConstructor
public class SupplementResult {

    /** 是否成功找到并推送了一个种子 */
    private boolean pushed;

    /** 本次搜索汇总到的候选种子总数（过滤前），供排查"搜到了但全被过滤掉"的情况 */
    private int candidateCount;
}
