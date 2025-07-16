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
import com.ruoyi.openliststrm.domain.OpenlistStrm;
import com.ruoyi.openliststrm.service.IOpenlistStrmService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * strm生成Controller
 * 
 * @author Jack
 * @date 2025-07-16
 */
@Controller
@RequestMapping("/openliststrm/strm")
public class OpenlistStrmController extends BaseController
{
    private String prefix = "openliststrm/strm";

    @Autowired
    private IOpenlistStrmService openlistStrmService;

    @RequiresPermissions("openliststrm:strm:view")
    @GetMapping()
    public String strm()
    {
        return prefix + "/strm";
    }

    /**
     * 查询strm生成列表
     */
    @RequiresPermissions("openliststrm:strm:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(OpenlistStrm openlistStrm)
    {
        startPage();
        List<OpenlistStrm> list = openlistStrmService.selectOpenlistStrmList(openlistStrm);
        return getDataTable(list);
    }

    /**
     * 导出strm生成列表
     */
    @RequiresPermissions("openliststrm:strm:export")
    @Log(title = "strm生成", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(OpenlistStrm openlistStrm)
    {
        List<OpenlistStrm> list = openlistStrmService.selectOpenlistStrmList(openlistStrm);
        ExcelUtil<OpenlistStrm> util = new ExcelUtil<OpenlistStrm>(OpenlistStrm.class);
        return util.exportExcel(list, "strm生成数据");
    }

    /**
     * 新增strm生成
     */
    @RequiresPermissions("openliststrm:strm:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存strm生成
     */
    @RequiresPermissions("openliststrm:strm:add")
    @Log(title = "strm生成", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(OpenlistStrm openlistStrm)
    {
        return toAjax(openlistStrmService.insertOpenlistStrm(openlistStrm));
    }

    /**
     * 修改strm生成
     */
    @RequiresPermissions("openliststrm:strm:edit")
    @GetMapping("/edit/{strmId}")
    public String edit(@PathVariable("strmId") Integer strmId, ModelMap mmap)
    {
        OpenlistStrm openlistStrm = openlistStrmService.selectOpenlistStrmByStrmId(strmId);
        mmap.put("openlistStrm", openlistStrm);
        return prefix + "/edit";
    }

    /**
     * 修改保存strm生成
     */
    @RequiresPermissions("openliststrm:strm:edit")
    @Log(title = "strm生成", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(OpenlistStrm openlistStrm)
    {
        return toAjax(openlistStrmService.updateOpenlistStrm(openlistStrm));
    }

    /**
     * 删除strm生成
     */
    @RequiresPermissions("openliststrm:strm:remove")
    @Log(title = "strm生成", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(openlistStrmService.deleteOpenlistStrmByStrmIds(ids));
    }
}
