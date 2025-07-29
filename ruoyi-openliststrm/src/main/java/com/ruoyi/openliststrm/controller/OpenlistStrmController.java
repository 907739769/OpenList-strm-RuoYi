package com.ruoyi.openliststrm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.domain.OpenlistStrm;
import com.ruoyi.openliststrm.enums.StrmStatusEnum;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmPlusService;
import com.ruoyi.openliststrm.service.IOpenlistStrmService;
import com.ruoyi.openliststrm.service.IStrmService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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

    @Autowired
    private OpenlistApi openlistApi;

    @Autowired
    private IOpenlistStrmPlusService openlistStrmPlusService;

    @Autowired
    private IOpenlistCopyPlusService openlistCopyPlusService;

    @Autowired
    private IStrmService strmService;

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

    @RequiresPermissions(value = {"openliststrm:strm:remove"})
    @Log(title = "strm生成", businessType = BusinessType.DELETE)
    @PostMapping("/batchRemoveNetDisk")
    @ResponseBody
    public AjaxResult batchRemoveNetDisk(String ids) {
        logger.info("删除网盘数据：{}", ids);
        List<String> idList = Arrays.stream(Convert.toStrArray(ids)).collect(Collectors.toList());
        List<OpenlistStrmPlus> openlistStrmPlusList = openlistStrmPlusService.listByIds(idList);
        //删除网盘数据
        openlistStrmPlusList.forEach(openlistStrmPlus -> {
            openlistApi.fsRemove(openlistStrmPlus.getStrmPath(), Collections.singletonList(openlistStrmPlus.getStrmFileName()));
            openlistCopyPlusService.remove(new LambdaQueryWrapper<OpenlistCopyPlus>()
                    .eq(OpenlistCopyPlus::getCopyDstFileName, openlistStrmPlus.getStrmFileName())
                    .eq(OpenlistCopyPlus::getCopyDstPath, openlistStrmPlus.getStrmPath())
            );
        });
        //删除表数据
        openlistStrmPlusService.removeBatchByIds(idList);
        return success();
    }

    /**
     * 重试strm任务
     */
    @RequiresPermissions("openliststrm:strm:edit")
    @Log(title = "strm生成", businessType = BusinessType.UPDATE)
    @PostMapping("/retry")
    @ResponseBody
    public AjaxResult retry(String ids) {
        logger.info("重试的任务：{}", ids);
        List<String> idList = Arrays.stream(Convert.toStrArray(ids)).collect(Collectors.toList());
        List<OpenlistStrmPlus> openlistStrmPlusList = openlistStrmPlusService.listByIds(idList);
        //更新为失败状态
        openlistStrmPlusList.forEach(openlistStrmPlus -> openlistStrmPlus.setStrmStatus("0"));
        openlistStrmPlusService.updateBatchById(openlistStrmPlusList);
        //重新strm文件
        openlistStrmPlusList.forEach(openlistStrmPlus -> {
            strmService.strmOneFile(openlistStrmPlus.getStrmPath() + "/" + openlistStrmPlus.getStrmFileName());
        });
        return success();
    }


    /**
     * 统计信息
     */
    @RequiresPermissions("openliststrm:strm:list")
    @PostMapping("/stats")
    @ResponseBody
    public AjaxResult stats() {
        LocalDate today = LocalDate.now();
        // 这里替换为实际业务数据，可以从service层获取
        QueryWrapper<OpenlistStrmPlus> wrapper = new QueryWrapper<>();
        wrapper.select("strm_status as status, count(*) as count")
                .between("create_time", today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .groupBy("strm_status");

        List<Map<String, Object>> maps = openlistStrmPlusService.listMaps(wrapper);
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
