package com.ruoyi.openliststrm.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.domain.RenameTask;
import com.ruoyi.openliststrm.rename.RenameTaskManager;
import com.ruoyi.openliststrm.service.IRenameTaskService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.TimerTask;
import java.util.stream.Collectors;

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

    @Autowired
    private RenameTaskManager renameTaskManager;

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

    /**
     * 页面手动触发单个任务执行（立即扫描一次）。
     */
    @PostMapping("/execute/{id}")
    @ResponseBody
    @RequiresPermissions("openliststrm:renameTask:edit")
    @Log(title = "重命名任务配置", businessType = BusinessType.UPDATE)
    public AjaxResult executeNow(@PathVariable("id") Integer id) {
        if (id == null) return AjaxResult.error("id 为空");
        AsyncManager.me().execute(new TimerTask() {
            @Override
            public void run() {
                renameTaskManager.executeTaskNow(id);
            }
        });
        return AjaxResult.success();
    }

    /**
     * 页面手动触发批量任务执行（立即扫描一次）。
     */
    @PostMapping("/executeBatch")
    @ResponseBody
    @RequiresPermissions("openliststrm:renameTask:edit")
    @Log(title = "重命名任务配置", businessType = BusinessType.UPDATE)
    public AjaxResult executeBatch(String ids) {
        if (ids == null || ids.trim().isEmpty()) return AjaxResult.error("没有选择任务");
        AsyncManager.me().execute(new TimerTask() {
            @Override
            public void run() {
                List<String> idList = Arrays.stream(Convert.toStrArray(ids)).collect(Collectors.toList());
                // convert to Integer list
                List<Integer> intIds = idList.stream().map(s -> {
                    try { return Integer.valueOf(s); } catch (Exception e) { return null; }
                }).filter(i -> i != null).collect(Collectors.toList());
                renameTaskManager.executeTasksBatch(intIds);
            }
        });
        return AjaxResult.success();
    }
}
