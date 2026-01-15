package com.ruoyi.openliststrm.controller;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.enums.CopyStatusEnum;
import com.ruoyi.openliststrm.enums.StrmStatusEnum;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.rename.RenameTaskManager;
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

    @Autowired
    private RenameTaskManager renameTaskManager;

    @Autowired
    private IRenameDetailPlusService renameDetailPlusService;

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

    /**
     * 页面手动触发单个
     */
    @PostMapping("/execute/{id}")
    @ResponseBody
    @RequiresPermissions("openliststrm:renameDetail:edit")
    @Log(title = "重命名明细", businessType = BusinessType.UPDATE)
    public AjaxResult executeNow(@PathVariable("id") Integer id, String title, String year) {
        if (id == null) return AjaxResult.error("id 为空");
        AsyncManager.me().execute(new TimerTask() {
            @Override
            public void run() {
                renameTaskManager.executeRenameDetails(id,title,year);
            }
        });
        return AjaxResult.success();
    }

    /**
     * 页面手动触发批量执行
     */
    @PostMapping("/executeBatch")
    @ResponseBody
    @RequiresPermissions("openliststrm:renameDetail:edit")
    @Log(title = "重命名明细", businessType = BusinessType.UPDATE)
    public AjaxResult executeBatch(String ids, String title, String year) {
        if (ids == null || ids.trim().isEmpty()) return AjaxResult.error("没有选择任务");
        AsyncManager.me().execute(new TimerTask() {
            @Override
            public void run() {
                List<String> idList = Arrays.stream(Convert.toStrArray(ids)).collect(Collectors.toList());
                // convert to Integer list
                List<Integer> intIds = idList.stream().map(s -> {
                    try { return Integer.valueOf(s); } catch (Exception e) { return null; }
                }).filter(i -> i != null).collect(Collectors.toList());
                renameTaskManager.executeRenameDetailsBatch(intIds, title, year);
            }
        });
        return AjaxResult.success();
    }

    /**
     * 统计信息
     */
    @RequiresPermissions("openliststrm:renameDetail:list")
    @PostMapping("/stats")
    @ResponseBody
    public AjaxResult stats(String range) {
        LocalDate today = LocalDate.now();
        // 这里替换为实际业务数据，可以从service层获取
        QueryWrapper<RenameDetailPlus> wrapper = new QueryWrapper<>();
        wrapper.select("status as status, count(*) as count")
                .between(StringUtils.isEmpty(range) || "today".equals(range), "create_time", today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .between("yesterday".equals(range), "create_time", today.minusDays(1).atStartOfDay(), today.atStartOfDay())
                .groupBy("status");

        List<Map<String, Object>> maps = renameDetailPlusService.listMaps(wrapper);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> map : maps) {
            String status = String.valueOf(map.get("status"));
            Long count = Long.parseLong(map.get("count").toString());

            String statusChinese = StrmStatusEnum.getDescByCode(status);
            result.put(statusChinese, count);
        }
        return success(result);
    }

}
