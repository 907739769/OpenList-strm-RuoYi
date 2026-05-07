package com.ruoyi.openliststrm.controller.api;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.PageResult;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.openliststrm.enums.CopyStatusEnum;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyPlusService;
import com.ruoyi.openliststrm.service.ICopyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件同步复制记录 REST API控制器
 *
 * @author Jack
 * @date 2025-07-21
 */
@RestController
@RequestMapping("/api/openliststrm/copy-records")
@Anonymous
@CrossOrigin
public class OpenlistCopyRestController extends BaseController
{
    @Autowired
    private IOpenlistCopyPlusService openlistCopyPlusService;

    @Autowired
    private ICopyService copyService;

    /**
     * 查询文件同步复制记录列表（分页）- 支持 /copy-records 和 /copy-records/list
     */
    @GetMapping({ "", "/list" })
    public Result<PageResult<OpenlistCopyPlus>> list(OpenlistCopyPlus openlistCopy)
    {
        return Result.success(selectPage(openlistCopyPlusService.getBaseMapper(), buildQueryWrapper(openlistCopy)));
    }

    /**
     * 根据ID获取文件同步复制记录
     */
    @GetMapping("/{id}")
    public Result<OpenlistCopyPlus> getById(@PathVariable("id") Integer id)
    {
        OpenlistCopyPlus record = openlistCopyPlusService.getById(id);
        if (record == null)
        {
            return Result.error("记录不存在");
        }
        return Result.success(record);
    }

    /**
     * 新增文件同步复制记录
     */
    @PostMapping
    public Result<Void> add(@RequestBody OpenlistCopyPlus openlistCopy)
    {
        boolean result = openlistCopyPlusService.save(openlistCopy);
        if (result)
        {
            return Result.success();
        }
        return Result.error("新增失败");
    }

    /**
     * 修改文件同步复制记录
     */
    @PutMapping
    public Result<Void> edit(@RequestBody OpenlistCopyPlus openlistCopy)
    {
        if (openlistCopy.getCopyId() == null)
        {
            return Result.error("记录ID不能为空");
        }
        OpenlistCopyPlus existing = openlistCopyPlusService.getById(openlistCopy.getCopyId());
        if (existing == null)
        {
            return Result.error("记录不存在");
        }
        boolean result = openlistCopyPlusService.updateById(openlistCopy);
        if (result)
        {
            return Result.success();
        }
        return Result.error("修改失败");
    }

    /**
     * 删除文件同步复制记录
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id)
    {
        OpenlistCopyPlus existing = openlistCopyPlusService.getById(id);
        if (existing == null)
        {
            return Result.error("记录不存在");
        }
        boolean result = openlistCopyPlusService.removeById(id);
        if (result)
        {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 批量删除文件同步复制记录
     */
    @DeleteMapping
    public Result<Void> batchDelete(@RequestParam("ids") String ids)
    {
        if (ids == null || ids.trim().isEmpty())
        {
            return Result.error("请选择要删除的记录");
        }
        List<String> idList = Arrays.stream(Convert.toStrArray(ids)).collect(Collectors.toList());
        boolean result = openlistCopyPlusService.removeByIds(idList);
        if (result)
        {
            return Result.success();
        }
        return Result.error("批量删除失败");
    }

    /**
     * 重试失败的复制记录
     */
    @PostMapping("/retry/{id}")
    public Result<Void> retry(@PathVariable("id") Integer id)
    {
        OpenlistCopyPlus record = openlistCopyPlusService.getById(id);
        if (record == null)
        {
            return Result.error("记录不存在");
        }
        logger.info("重试的复制记录：{}", id);
        List<String> idList = Collections.singletonList(String.valueOf(id));
        copyService.retryCopy(idList);
        return Result.success();
    }

    /**
     * 批量重试失败的复制记录
     */
    @PostMapping("/retry")
    public Result<Void> batchRetry(@RequestParam("ids") String ids)
    {
        if (ids == null || ids.trim().isEmpty())
        {
            return Result.error("请选择要重试的记录");
        }
        List<String> idList = Arrays.stream(Convert.toStrArray(ids)).collect(Collectors.toList());
        copyService.retryCopy(idList);
        return Result.success();
    }

    /**
     * 批量删除网盘文件（从网盘删除实际文件）
     */
    @PostMapping("/batchRemoveNetDisk")
    public Result<Void> batchRemoveNetDisk(@RequestParam("ids") String ids)
    {
        if (ids == null || ids.trim().isEmpty())
        {
            return Result.error("请选择要删除的记录");
        }
        List<String> idList = Arrays.stream(Convert.toStrArray(ids)).collect(Collectors.toList());
        copyService.batchRemoveNetDisk(idList);
        return Result.success();
    }

    /**
     * 构建查询条件
     */
    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OpenlistCopyPlus> buildQueryWrapper(OpenlistCopyPlus openlistCopy)
    {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OpenlistCopyPlus> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (openlistCopy != null)
        {
            if (openlistCopy.getCopySrcPath() != null && !openlistCopy.getCopySrcPath().isEmpty())
            {
                wrapper.like(OpenlistCopyPlus::getCopySrcPath, openlistCopy.getCopySrcPath());
            }
            if (openlistCopy.getCopyDstPath() != null && !openlistCopy.getCopyDstPath().isEmpty())
            {
                wrapper.like(OpenlistCopyPlus::getCopyDstPath, openlistCopy.getCopyDstPath());
            }
            if (openlistCopy.getCopySrcFileName() != null && !openlistCopy.getCopySrcFileName().isEmpty())
            {
                wrapper.like(OpenlistCopyPlus::getCopySrcFileName, openlistCopy.getCopySrcFileName());
            }
            if (openlistCopy.getCopyDstFileName() != null && !openlistCopy.getCopyDstFileName().isEmpty())
            {
                wrapper.like(OpenlistCopyPlus::getCopyDstFileName, openlistCopy.getCopyDstFileName());
            }
            if (openlistCopy.getCopyTaskId() != null && !openlistCopy.getCopyTaskId().isEmpty())
            {
                wrapper.eq(OpenlistCopyPlus::getCopyTaskId, openlistCopy.getCopyTaskId());
            }
            if (openlistCopy.getCopyStatus() != null && !openlistCopy.getCopyStatus().isEmpty())
            {
                wrapper.eq(OpenlistCopyPlus::getCopyStatus, openlistCopy.getCopyStatus());
            }
        }
        wrapper.last("ORDER BY create_time DESC");
        return wrapper;
    }
}
