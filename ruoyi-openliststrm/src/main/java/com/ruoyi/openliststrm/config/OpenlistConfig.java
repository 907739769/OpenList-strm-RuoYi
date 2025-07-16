package com.ruoyi.openliststrm.config;

import com.ruoyi.system.service.ISysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Author Jack
 * @Date 2025/7/16 19:32
 * @Version 1.0.0
 */
@Component
public class OpenlistConfig {

    @Autowired
    private ISysConfigService sysConfigService;

    public String getOpenListUrl(){
        return sysConfigService.selectConfigByKey("openlist.server.url");
    }

    public String getOpenListToken(){
        return sysConfigService.selectConfigByKey("openlist.server.token");
    }



}
