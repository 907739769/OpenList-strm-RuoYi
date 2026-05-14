package com.ruoyi.openliststrm.controller.api;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.req.NotifyReq;
import com.ruoyi.openliststrm.service.ICopyService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 通知回调 REST API控制器
 *
 * @author Jack
 */
@RestController
@RequestMapping("/api/openliststrm/notify")
@Anonymous
public class NotifyRestController
{
    private static final Logger log = LoggerFactory.getLogger(NotifyRestController.class);

    @Autowired
    private ICopyService copyService;

    @Autowired
    private OpenListHelper openListHelper;

    @Autowired
    private OpenlistConfig config;

    /**
     * 目录变更通知
     */
    @PostMapping("/notifyByDir")
    public Result<Void> notifyByDir(@RequestBody @Valid NotifyReq req, HttpServletRequest request)
    {
        log.debug("req: {}", req);
        if (StringUtils.isBlank(request.getHeader("X-API-KEY")))
        {
            log.warn("没有设置参数openlist.api.apikey");
            return Result.error("APIKEY校验不通过");
        }
        if (!request.getHeader("X-API-KEY").equals(config.getOpenListApiKey()))
        {
            log.warn("APIKEY校验不通过");
            return Result.error("APIKEY校验不通过");
        }
        if (openListHelper.isVideo(req.getQbDlFilePath()))
        {
            copyService.syncOneFile(req.getSrcDir(), req.getSrcDst(), req.getQbDlFilePath().replaceFirst(req.getQbDlRootPath(), ""));
        }
        else
        {
            copyService.syncFiles(req.getSrcDir(), req.getSrcDst(), req.getQbDlFilePath().replaceFirst(req.getQbDlRootPath(), ""));
        }
        return Result.success();
    }
}
