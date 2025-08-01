package com.ruoyi.openliststrm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.domain.OpenlistCopy;
import com.ruoyi.openliststrm.enums.CopyStatusEnum;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmPlusService;
import com.ruoyi.openliststrm.service.ICopyService;
import com.ruoyi.openliststrm.service.IOpenlistCopyService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * openlist的文件同步复制任务Controller
 *
 * @author Jack
 * @date 2025-07-16
 */
@Controller
@RequestMapping("/openliststrm/copy")
public class OpenlistCopyController extends BaseController {
    private String prefix = "openliststrm/copy";

    @Autowired
    private IOpenlistCopyService openlistCopyService;

    @Autowired
    private OpenlistApi openlistApi;

    @Autowired
    private IOpenlistCopyPlusService openlistCopyPlusService;

    @Autowired
    private IOpenlistStrmPlusService openlistStrmPlusService;

    @Autowired
    private ICopyService copyService;

    @RequiresPermissions("openliststrm:copy:view")
    @GetMapping()
    public String copy() {
        return prefix + "/copy";
    }

    /**
     * 查询openlist的文件同步复制任务列表
     */
    @RequiresPermissions("openliststrm:copy:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(OpenlistCopy openlistCopy) {
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
    public AjaxResult export(OpenlistCopy openlistCopy) {
        List<OpenlistCopy> list = openlistCopyService.selectOpenlistCopyList(openlistCopy);
        ExcelUtil<OpenlistCopy> util = new ExcelUtil<OpenlistCopy>(OpenlistCopy.class);
        return util.exportExcel(list, "openlist的文件同步复制任务数据");
    }

    /**
     * 新增openlist的文件同步复制任务
     */
    @RequiresPermissions("openliststrm:copy:add")
    @GetMapping("/add")
    public String add() {
        return prefix + "/add";
    }

    /**
     * 新增保存openlist的文件同步复制任务
     */
    @RequiresPermissions("openliststrm:copy:add")
    @Log(title = "openlist的文件同步复制任务", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(OpenlistCopy openlistCopy) {
        return toAjax(openlistCopyService.insertOpenlistCopy(openlistCopy));
    }

    /**
     * 修改openlist的文件同步复制任务
     */
    @RequiresPermissions("openliststrm:copy:edit")
    @GetMapping("/edit/{copyId}")
    public String edit(@PathVariable("copyId") Integer copyId, ModelMap mmap) {
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
    public AjaxResult editSave(OpenlistCopy openlistCopy) {
        return toAjax(openlistCopyService.updateOpenlistCopy(openlistCopy));
    }

    /**
     * 删除openlist的文件同步复制任务
     */
    @RequiresPermissions("openliststrm:copy:remove")
    @Log(title = "openlist的文件同步复制任务", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(openlistCopyService.deleteOpenlistCopyByCopyIds(ids));
    }

    @RequiresPermissions("openliststrm:copy:remove")
    @Log(title = "openlist的文件同步复制任务", businessType = BusinessType.DELETE)
    @PostMapping("/batchRemoveNetDisk")
    @ResponseBody
    public AjaxResult batchRemoveNetDisk(String ids) {
        logger.info("删除网盘数据：{}", ids);
        List<String> idList = Arrays.stream(Convert.toStrArray(ids)).collect(Collectors.toList());
        List<OpenlistCopyPlus> openlistCopyPlusList = openlistCopyPlusService.listByIds(idList);
        //删除网盘数据
        openlistCopyPlusList.forEach(openlistCopyPlus -> {
            openlistApi.fsRemove(openlistCopyPlus.getCopyDstPath(), Collections.singletonList(openlistCopyPlus.getCopyDstFileName()));
            openlistStrmPlusService.remove(new LambdaQueryWrapper<OpenlistStrmPlus>()
                    .eq(OpenlistStrmPlus::getStrmFileName, openlistCopyPlus.getCopyDstFileName())
                    .eq(OpenlistStrmPlus::getStrmPath, openlistCopyPlus.getCopyDstPath())
            );
        });
        //删除表数据
        openlistCopyPlusService.removeBatchByIds(idList);
        return success();
    }

    /**
     * 重试文件同步任务
     */
    @RequiresPermissions("openliststrm:copy:edit")
    @Log(title = "openlist的文件同步复制任务", businessType = BusinessType.UPDATE)
    @PostMapping("/retry")
    @ResponseBody
    public AjaxResult retry(String ids) {
        logger.info("重试的任务：{}", ids);
        List<String> idList = Arrays.stream(Convert.toStrArray(ids)).collect(Collectors.toList());
        List<OpenlistCopyPlus> openlistCopyPlusList = openlistCopyPlusService.listByIds(idList);
        //更新为失败状态
        openlistCopyPlusList.forEach(openlistCopyPlus -> openlistCopyPlus.setCopyStatus("2"));
        openlistCopyPlusService.updateBatchById(openlistCopyPlusList);
        //重新同步文件
        openlistCopyPlusList.forEach(openlistCopyPlus -> {
            copyService.syncOneFile(openlistCopyPlus.getCopySrcPath(), openlistCopyPlus.getCopyDstPath(), openlistCopyPlus.getCopySrcFileName());
        });
        return success();
    }

    /**
     * 统计信息
     */
    @RequiresPermissions("openliststrm:copy:list")
    @PostMapping("/stats")
    @ResponseBody
    public AjaxResult stats(String range) {
        LocalDate today = LocalDate.now();
        // 这里替换为实际业务数据，可以从service层获取
        QueryWrapper<OpenlistCopyPlus> wrapper = new QueryWrapper<>();
        wrapper.select("copy_status as status, count(*) as count")
                .between(StringUtils.isEmpty(range) || "today".equals(range), "create_time", today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .between("yesterday".equals(range), "create_time", today.minusDays(1).atStartOfDay(), today.atStartOfDay())
                .groupBy("copy_status");

        List<Map<String, Object>> maps = openlistCopyPlusService.listMaps(wrapper);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> map : maps) {
            String status = String.valueOf(map.get("status"));
            Long count = Long.parseLong(map.get("count").toString());

            String statusChinese = CopyStatusEnum.getDescByCode(status);
            result.put(statusChinese, count);
        }
        return success(result);
    }

}
