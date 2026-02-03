package com.ruoyi.web.controller.monitor;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ruoyi.common.core.controller.BaseController;
import org.apache.shiro.authz.annotation.RequiresPermissions;

@Controller
@RequestMapping("/monitor/log")
public class SysLogViewController extends BaseController {

    private String prefix = "monitor/log";

    @RequiresPermissions("monitor:log:view") // 记得在菜单管理里添加这个权限标识
    @GetMapping()
    public String log() {
        return prefix + "/log";
    }
}