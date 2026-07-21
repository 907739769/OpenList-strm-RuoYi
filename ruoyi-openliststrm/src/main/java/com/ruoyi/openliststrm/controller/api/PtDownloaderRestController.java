package com.ruoyi.openliststrm.controller.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloaderPlusService;
import com.ruoyi.openliststrm.pt.downloader.DownloaderClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PT 下载器配置 REST API 控制器
 *
 * @author Jack
 * @date 2026-07-24
 */
@RestController
@RequestMapping("/api/openliststrm/pt-downloaders")
public class PtDownloaderRestController extends BaseCrudRestController<IPtDownloaderPlusService, PtDownloaderPlus> {

    @Autowired
    private DownloaderClientFactory downloaderClientFactory;

    @Override
    protected Wrapper<PtDownloaderPlus> buildQueryWrapper(PtDownloaderPlus entity) {
        LambdaQueryWrapper<PtDownloaderPlus> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(entity.getName())) {
            wrapper.like(PtDownloaderPlus::getName, entity.getName());
        }
        if (StringUtils.isNotBlank(entity.getEnabled())) {
            wrapper.eq(PtDownloaderPlus::getEnabled, entity.getEnabled());
        }
        wrapper.orderByAsc(PtDownloaderPlus::getId);
        return wrapper;
    }

    /**
     * 连通性测试。
     */
    @PostMapping("/test")
    public Result<Void> test(@RequestBody PtDownloaderPlus entity) {
        if (StringUtils.isBlank(entity.getHost()) || entity.getPort() == null) {
            return Result.error("主机与端口不能为空");
        }
        try {
            return downloaderClientFactory.get(entity).testConnection(entity)
                    ? Result.success()
                    : Result.error("连接失败，请检查地址、端口与用户名密码");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 校验保存路径是否位于某个文件同步任务的监听目录之下。
     * 不满足不阻断保存，仅返回提示，由前端以警告形式展示。
     */
    @PostMapping("/validate-save-path")
    public Result<String> validateSavePath(@RequestBody PtDownloaderPlus entity) {
        String message = service.validateSavePath(entity.getSavePath());
        return message == null ? Result.success() : Result.success(message);
    }
}
