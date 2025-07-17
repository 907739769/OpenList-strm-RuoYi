package com.ruoyi.openliststrm.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.openliststrm.domain.OpenlistCopyTask;
import com.ruoyi.openliststrm.service.IOpenlistCopyTaskService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 文件同步任务Controller
 * 
 * @author Jack
 * @date 2025-07-17
 */
@Controller
@RequestMapping("/openliststrm/task")
public class OpenlistCopyTaskController extends BaseController
{
    private String prefix = "openliststrm/task";

    @Autowired
    private IOpenlistCopyTaskService openlistCopyTaskService;

    @RequiresPermissions("openliststrm:task:view")
    @GetMapping()
    public String task()
    {
        return prefix + "/task";
    }

    /**
     * 查询文件同步任务列表
     */
    @RequiresPermissions("openliststrm:task:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(OpenlistCopyTask openlistCopyTask)
    {
        startPage();
        List<OpenlistCopyTask> list = openlistCopyTaskService.selectOpenlistCopyTaskList(openlistCopyTask);
        return getDataTable(list);
    }

    /**
     * 导出文件同步任务列表
     */
    @RequiresPermissions("openliststrm:task:export")
    @Log(title = "文件同步任务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(OpenlistCopyTask openlistCopyTask)
    {
        List<OpenlistCopyTask> list = openlistCopyTaskService.selectOpenlistCopyTaskList(openlistCopyTask);
        ExcelUtil<OpenlistCopyTask> util = new ExcelUtil<OpenlistCopyTask>(OpenlistCopyTask.class);
        return util.exportExcel(list, "文件同步任务数据");
    }

    /**
     * 新增文件同步任务
     */
    @RequiresPermissions("openliststrm:task:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存文件同步任务
     */
    @RequiresPermissions("openliststrm:task:add")
    @Log(title = "文件同步任务", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(OpenlistCopyTask openlistCopyTask)
    {
        return toAjax(openlistCopyTaskService.insertOpenlistCopyTask(openlistCopyTask));
    }

    /**
     * 修改文件同步任务
     */
    @RequiresPermissions("openliststrm:task:edit")
    @GetMapping("/edit/{copyTaskId}")
    public String edit(@PathVariable("copyTaskId") Integer copyTaskId, ModelMap mmap)
    {
        OpenlistCopyTask openlistCopyTask = openlistCopyTaskService.selectOpenlistCopyTaskByCopyTaskId(copyTaskId);
        mmap.put("openlistCopyTask", openlistCopyTask);
        return prefix + "/edit";
    }

    /**
     * 修改保存文件同步任务
     */
    @RequiresPermissions("openliststrm:task:edit")
    @Log(title = "文件同步任务", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(OpenlistCopyTask openlistCopyTask)
    {
        return toAjax(openlistCopyTaskService.updateOpenlistCopyTask(openlistCopyTask));
    }

    /**
     * 删除文件同步任务
     */
    @RequiresPermissions("openliststrm:task:remove")
    @Log(title = "文件同步任务", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(openlistCopyTaskService.deleteOpenlistCopyTaskByCopyTaskIds(ids));
    }
}
