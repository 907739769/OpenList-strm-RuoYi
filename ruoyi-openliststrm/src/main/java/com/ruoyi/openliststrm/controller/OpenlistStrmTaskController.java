package com.ruoyi.openliststrm.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.openliststrm.domain.OpenlistStrmTask;
import com.ruoyi.openliststrm.service.IOpenlistStrmTaskService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * strm任务配置Controller
 * 
 * @author Jack
 * @date 2025-07-18
 */
@Controller
@RequestMapping("/openliststrm/strm_task")
public class OpenlistStrmTaskController extends BaseController
{
    private String prefix = "openliststrm/strm_task";

    @Autowired
    private IOpenlistStrmTaskService openlistStrmTaskService;

    @RequiresPermissions("openliststrm:strm_task:view")
    @GetMapping()
    public String strm_task()
    {
        return prefix + "/strm_task";
    }

    /**
     * 查询strm任务配置列表
     */
    @RequiresPermissions("openliststrm:strm_task:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(OpenlistStrmTask openlistStrmTask)
    {
        startPage();
        List<OpenlistStrmTask> list = openlistStrmTaskService.selectOpenlistStrmTaskList(openlistStrmTask);
        return getDataTable(list);
    }

    /**
     * 导出strm任务配置列表
     */
    @RequiresPermissions("openliststrm:strm_task:export")
    @Log(title = "strm任务配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(OpenlistStrmTask openlistStrmTask)
    {
        List<OpenlistStrmTask> list = openlistStrmTaskService.selectOpenlistStrmTaskList(openlistStrmTask);
        ExcelUtil<OpenlistStrmTask> util = new ExcelUtil<OpenlistStrmTask>(OpenlistStrmTask.class);
        return util.exportExcel(list, "strm任务配置数据");
    }

    /**
     * 新增strm任务配置
     */
    @RequiresPermissions("openliststrm:strm_task:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存strm任务配置
     */
    @RequiresPermissions("openliststrm:strm_task:add")
    @Log(title = "strm任务配置", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(OpenlistStrmTask openlistStrmTask)
    {
        return toAjax(openlistStrmTaskService.insertOpenlistStrmTask(openlistStrmTask));
    }

    /**
     * 修改strm任务配置
     */
    @RequiresPermissions("openliststrm:strm_task:edit")
    @GetMapping("/edit/{strmTaskId}")
    public String edit(@PathVariable("strmTaskId") Long strmTaskId, ModelMap mmap)
    {
        OpenlistStrmTask openlistStrmTask = openlistStrmTaskService.selectOpenlistStrmTaskByStrmTaskId(strmTaskId);
        mmap.put("openlistStrmTask", openlistStrmTask);
        return prefix + "/edit";
    }

    /**
     * 修改保存strm任务配置
     */
    @RequiresPermissions("openliststrm:strm_task:edit")
    @Log(title = "strm任务配置", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(OpenlistStrmTask openlistStrmTask)
    {
        return toAjax(openlistStrmTaskService.updateOpenlistStrmTask(openlistStrmTask));
    }

    /**
     * 删除strm任务配置
     */
    @RequiresPermissions("openliststrm:strm_task:remove")
    @Log(title = "strm任务配置", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(openlistStrmTaskService.deleteOpenlistStrmTaskByStrmTaskIds(ids));
    }
}
