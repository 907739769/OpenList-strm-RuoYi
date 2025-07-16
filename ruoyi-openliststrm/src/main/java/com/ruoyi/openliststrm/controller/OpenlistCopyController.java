package com.ruoyi.openliststrm.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.openliststrm.domain.OpenlistCopy;
import com.ruoyi.openliststrm.service.IOpenlistCopyService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * openlist的文件同步复制任务Controller
 * 
 * @author Jack
 * @date 2025-07-16
 */
@Controller
@RequestMapping("/openliststrm/copy")
public class OpenlistCopyController extends BaseController
{
    private String prefix = "openliststrm/copy";

    @Autowired
    private IOpenlistCopyService openlistCopyService;

    @RequiresPermissions("openliststrm:copy:view")
    @GetMapping()
    public String copy()
    {
        return prefix + "/copy";
    }

    /**
     * 查询openlist的文件同步复制任务列表
     */
    @RequiresPermissions("openliststrm:copy:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(OpenlistCopy openlistCopy)
    {
        startPage();
        List<OpenlistCopy> list = openlistCopyService.selectOpenlistCopyList(openlistCopy);
        return getDataTable(list);
    }

    /**
     * 导出openlist的文件同步复制任务列表
     */
    @RequiresPermissions("openliststrm:copy:export")
    @Log(title = "openlist的文件同步复制任务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(OpenlistCopy openlistCopy)
    {
        List<OpenlistCopy> list = openlistCopyService.selectOpenlistCopyList(openlistCopy);
        ExcelUtil<OpenlistCopy> util = new ExcelUtil<OpenlistCopy>(OpenlistCopy.class);
        return util.exportExcel(list, "openlist的文件同步复制任务数据");
    }

    /**
     * 新增openlist的文件同步复制任务
     */
    @RequiresPermissions("openliststrm:copy:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存openlist的文件同步复制任务
     */
    @RequiresPermissions("openliststrm:copy:add")
    @Log(title = "openlist的文件同步复制任务", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(OpenlistCopy openlistCopy)
    {
        return toAjax(openlistCopyService.insertOpenlistCopy(openlistCopy));
    }

    /**
     * 修改openlist的文件同步复制任务
     */
    @RequiresPermissions("openliststrm:copy:edit")
    @GetMapping("/edit/{copyId}")
    public String edit(@PathVariable("copyId") Integer copyId, ModelMap mmap)
    {
        OpenlistCopy openlistCopy = openlistCopyService.selectOpenlistCopyByCopyId(copyId);
        mmap.put("openlistCopy", openlistCopy);
        return prefix + "/edit";
    }

    /**
     * 修改保存openlist的文件同步复制任务
     */
    @RequiresPermissions("openliststrm:copy:edit")
    @Log(title = "openlist的文件同步复制任务", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(OpenlistCopy openlistCopy)
    {
        return toAjax(openlistCopyService.updateOpenlistCopy(openlistCopy));
    }

    /**
     * 删除openlist的文件同步复制任务
     */
    @RequiresPermissions("openliststrm:copy:remove")
    @Log(title = "openlist的文件同步复制任务", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(openlistCopyService.deleteOpenlistCopyByCopyIds(ids));
    }
}
