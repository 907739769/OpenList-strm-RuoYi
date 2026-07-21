package com.ruoyi.openliststrm.controller.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtIndexerPlusService;
import com.ruoyi.openliststrm.pt.indexer.TorznabClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PT Torznab 索引器配置 REST API 控制器
 *
 * @author Jack
 * @date 2026-07-24
 */
@RestController
@RequestMapping("/api/openliststrm/pt-indexers")
public class PtIndexerRestController extends BaseCrudRestController<IPtIndexerPlusService, PtIndexerPlus> {

    @Autowired
    private TorznabClient torznabClient;

    @Override
    protected Wrapper<PtIndexerPlus> buildQueryWrapper(PtIndexerPlus entity) {
        LambdaQueryWrapper<PtIndexerPlus> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(entity.getName())) {
            wrapper.like(PtIndexerPlus::getName, entity.getName());
        }
        if (StringUtils.isNotBlank(entity.getEnabled())) {
            wrapper.eq(PtIndexerPlus::getEnabled, entity.getEnabled());
        }
        wrapper.orderByAsc(PtIndexerPlus::getId);
        return wrapper;
    }

    /**
     * 连通性测试。接收前端表单当前值，无需先保存即可测试。
     */
    @PostMapping("/test")
    public Result<Void> test(@RequestBody PtIndexerPlus entity) {
        if (StringUtils.isBlank(entity.getUrl()) || StringUtils.isBlank(entity.getApiKey())) {
            return Result.error("接口地址与 apikey 不能为空");
        }
        return torznabClient.testConnection(entity)
                ? Result.success()
                : Result.error("连接失败，请检查地址、apikey 与网络");
    }
}
