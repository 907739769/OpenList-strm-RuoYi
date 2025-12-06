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
import com.ruoyi.openliststrm.domain.RenameDetail;
import com.ruoyi.openliststrm.service.IRenameDetailService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 重命名明细Controller
 * 
 * @author Jack
 * @date 2025-09-30
 */
@Controller
@RequestMapping("/openliststrm/renameDetail")
public class RenameDetailController extends BaseController
{
    private String prefix = "openliststrm/renameDetail";

    @Autowired
    private IRenameDetailService renameDetailService;

    @RequiresPermissions("openliststrm:renameDetail:view")
    @GetMapping()
    public String renameDetail()
    {
        return prefix + "/renameDetail";
    }

    /**
     * 查询重命名明细列表
     */
    @RequiresPermissions("openliststrm:renameDetail:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(RenameDetail renameDetail)
    {
        startPage();
        List<RenameDetail> list = renameDetailService.selectRenameDetailList(renameDetail);
        return getDataTable(list);
    }

    /**
     * 导出重命名明细列表
     */
    @RequiresPermissions("openliststrm:renameDetail:export")
    @Log(title = "重命名明细", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(RenameDetail renameDetail)
    {
        List<RenameDetail> list = renameDetailService.selectRenameDetailList(renameDetail);
        ExcelUtil<RenameDetail> util = new ExcelUtil<RenameDetail>(RenameDetail.class);
        return util.exportExcel(list, "重命名明细数据");
    }

    /**
     * 新增重命名明细
     */
    @RequiresPermissions("openliststrm:renameDetail:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存重命名明细
     */
    @RequiresPermissions("openliststrm:renameDetail:add")
    @Log(title = "重命名明细", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(RenameDetail renameDetail)
    {
        return toAjax(renameDetailService.insertRenameDetail(renameDetail));
    }

    /**
     * 修改重命名明细
     */
    @RequiresPermissions("openliststrm:renameDetail:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Integer id, ModelMap mmap)
    {
        RenameDetail renameDetail = renameDetailService.selectRenameDetailById(id);
        mmap.put("renameDetail", renameDetail);
        return prefix + "/edit";
    }

    /**
     * 修改保存重命名明细
     */
    @RequiresPermissions("openliststrm:renameDetail:edit")
    @Log(title = "重命名明细", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(RenameDetail renameDetail)
    {
        return toAjax(renameDetailService.updateRenameDetail(renameDetail));
    }

    /**
     * 删除重命名明细
     */
    @RequiresPermissions("openliststrm:renameDetail:remove")
    @Log(title = "重命名明细", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(renameDetailService.deleteRenameDetailByIds(ids));
    }
}
