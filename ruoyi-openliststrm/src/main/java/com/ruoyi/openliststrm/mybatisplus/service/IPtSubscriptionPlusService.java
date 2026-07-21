package com.ruoyi.openliststrm.mybatisplus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;

import java.util.List;

/**
 * <p>
 * PT 订阅 服务类
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
public interface IPtSubscriptionPlusService extends IService<PtSubscriptionPlus> {

    /**
     * 查询全部处于订阅中(ACTIVE)的订阅。RSS 轮询只匹配这些。
     */
    List<PtSubscriptionPlus> listActive();
}
