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
import com.ruoyi.openliststrm.domain.RenameTask;
import com.ruoyi.openliststrm.service.IRenameTaskService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 重命名任务配置Controller
 * 
 * @author Jack
 * @date 2025-09-30
 */
@Controller
@RequestMapping("/openliststrm/renameTask")
public class RenameTaskController extends BaseController
{
    private String prefix = "openliststrm/renameTask";

    @Autowired
    private IRenameTaskService renameTaskService;

    @RequiresPermissions("openliststrm:renameTask:view")
    @GetMapping()
    public String renameTask()
    {
        return prefix + "/renameTask";
    }

    /**
     * 查询重命名任务配置列表
     */
    @RequiresPermissions("openliststrm:renameTask:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(RenameTask renameTask)
    {
        startPage();
        List<RenameTask> list = renameTaskService.selectRenameTaskList(renameTask);
        return getDataTable(list);
    }

    /**
     * 导出重命名任务配置列表
     */
    @RequiresPermissions("openliststrm:renameTask:export")
    @Log(title = "重命名任务配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(RenameTask renameTask)
    {
        List<RenameTask> list = renameTaskService.selectRenameTaskList(renameTask);
        ExcelUtil<RenameTask> util = new ExcelUtil<RenameTask>(RenameTask.class);
        return util.exportExcel(list, "重命名任务配置数据");
    }

    /**
     * 新增重命名任务配置
     */
    @RequiresPermissions("openliststrm:renameTask:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存重命名任务配置
     */
    @RequiresPermissions("openliststrm:renameTask:add")
    @Log(title = "重命名任务配置", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(RenameTask renameTask)
    {
        return toAjax(renameTaskService.insertRenameTask(renameTask));
    }

    /**
     * 修改重命名任务配置
     */
    @RequiresPermissions("openliststrm:renameTask:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Integer id, ModelMap mmap)
    {
        RenameTask renameTask = renameTaskService.selectRenameTaskById(id);
        mmap.put("renameTask", renameTask);
        return prefix + "/edit";
    }

    /**
     * 修改保存重命名任务配置
     */
    @RequiresPermissions("openliststrm:renameTask:edit")
    @Log(title = "重命名任务配置", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(RenameTask renameTask)
    {
        return toAjax(renameTaskService.updateRenameTask(renameTask));
    }

    /**
     * 删除重命名任务配置
     */
    @RequiresPermissions("openliststrm:renameTask:remove")
    @Log(title = "重命名任务配置", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(renameTaskService.deleteRenameTaskByIds(ids));
    }
}
