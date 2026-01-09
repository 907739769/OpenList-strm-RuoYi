package com.ruoyi.openliststrm.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.req.NotifyReq;
import com.ruoyi.openliststrm.service.ICopyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * @Author Jack
 * @Date 2024/6/23 20:34
 * @Version 1.0.0
 */
@RestController
@RequestMapping("api/v1")
@Slf4j
@Anonymous
public class NotifyController {

    @Autowired
    private ICopyService copyService;

    @Autowired
    private OpenListHelper openListHelper;

    @Autowired
    private OpenlistConfig config;

    @PostMapping("/notifyByDir")
    public void notifyByDir(@RequestBody @Valid NotifyReq req, HttpServletRequest request) {
        log.debug("req: {}" , req);
        if (StringUtils.isBlank(request.getHeader("X-API-KEY"))) {
            log.warn("没有设置参数openlist.api.apikey");
            return;
        }
        if (!request.getHeader("X-API-KEY").equals(config.getOpenListApiKey())) {
            log.warn("APIKEY校验不通过");
            return;
        }
        if (openListHelper.isVideo(req.getQbDlFilePath())) {
            copyService.syncOneFile(req.getSrcDir(), req.getSrcDst(), req.getQbDlFilePath().replaceFirst(req.getQbDlRootPath(), ""));
        } else {
            copyService.syncFiles(req.getSrcDir(), req.getSrcDst(), req.getQbDlFilePath().replaceFirst(req.getQbDlRootPath(), ""));
        }
    }


}
