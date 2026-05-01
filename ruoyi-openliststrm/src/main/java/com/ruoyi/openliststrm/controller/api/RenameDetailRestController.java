package com.ruoyi.openliststrm.controller.api;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.PageResult;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.enums.StrmStatusEnum;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.service.IRenameDetailService;
import com.ruoyi.openliststrm.rename.RenameTaskManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.TimerTask;
import java.util.stream.Collectors;

/**
 * 重命名明细 REST API控制器
 *
 * @author Jack
 * @date 2025-09-30
 */
@RestController
@RequestMapping("/api/openliststrm/rename-details")
@Anonymous
@CrossOrigin
public class RenameDetailRestController extends BaseController
{
    @Autowired
    private IRenameDetailService renameDetailService;

    @Autowired
    private RenameTaskManager renameTaskManager;

    @Autowired
    private IRenameDetailPlusService renameDetailPlusService;

    /**
     * 查询重命名明细列表（分页）- 支持 /rename-details 和 /rename-details/list
     */
    @GetMapping({ "", "/list" })
    public Result<PageResult<RenameDetailPlus>> list(RenameDetailPlus renameDetail)
    {
        startPage();
        List<RenameDetailPlus> list = renameDetailPlusService.list(buildQueryWrapper(renameDetail));
        long total = PageHelper.count(() -> renameDetailPlusService.list(buildQueryWrapper(renameDetail)));
        int page = getPageNum();
        int size = getPageSize();
        return Result.success(PageResult.of(list, total, page, size));
    }

    /**
     * 根据ID获取重命名明细
     */
    @GetMapping("/{id}")
    public Result<RenameDetailPlus> getById(@PathVariable("id") Integer id)
    {
        RenameDetailPlus detail = renameDetailPlusService.getById(id);
        if (detail == null)
        {
            return Result.error("重命名明细不存在");
        }
        return Result.success(detail);
    }

    /**
     * 新增重命名明细
     */
    @PostMapping
    public Result<Void> add(@RequestBody RenameDetailPlus renameDetail)
    {
        boolean result = renameDetailPlusService.save(renameDetail);
        if (result)
        {
            return Result.success();
        }
        return Result.error("新增失败");
    }

    /**
     * 修改重命名明细
     */
    @PutMapping
    public Result<Void> edit(@RequestBody RenameDetailPlus renameDetail)
    {
        if (renameDetail.getId() == null)
        {
            return Result.error("重命名明细ID不能为空");
        }
        RenameDetailPlus existing = renameDetailPlusService.getById(renameDetail.getId());
        if (existing == null)
        {
            return Result.error("重命名明细不存在");
        }
        boolean result = renameDetailPlusService.updateById(renameDetail);
        if (result)
        {
            return Result.success();
        }
        return Result.error("修改失败");
    }

    /**
     * 删除重命名明细
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id)
    {
        RenameDetailPlus existing = renameDetailPlusService.getById(id);
        if (existing == null)
        {
            return Result.error("重命名明细不存在");
        }
        boolean result = renameDetailPlusService.removeById(id);
        if (result)
        {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 批量删除重命名明细
     */
    @DeleteMapping
    public Result<Void> batchDelete(@RequestParam("ids") String ids)
    {
        if (ids == null || ids.trim().isEmpty())
        {
            return Result.error("请选择要删除的重命名明细");
        }
        List<String> idList = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        boolean result = renameDetailPlusService.removeByIds(idList);
        if (result)
        {
            return Result.success();
        }
        return Result.error("批量删除失败");
    }

    /**
     * 执行重命名明细
     */
    @PostMapping("/execute/{id}")
    public Result<Void> execute(@PathVariable("id") Integer id,
                                @RequestParam(value = "title", required = false) String title,
                                @RequestParam(value = "year", required = false) String year)
    {
        if (id == null)
        {
            return Result.error("id 为空");
        }
        logger.info("开始执行重命名明细，ID：{}", id);
        AsyncManager.me().execute(new TimerTask()
        {
            @Override
            public void run()
            {
                renameTaskManager.executeRenameDetails(id, title, year);
            }
        });
        return Result.success();
    }

    /**
     * 批量执行重命名明细
     */
    @PostMapping("/execute")
    public Result<Void> batchExecute(@RequestBody List<Integer> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return Result.error("请选择要执行的记录");
        }
        for (Integer id : ids)
        {
            logger.info("开始执行重命名明细，ID：{}", id);
            final int detailId = id;
            AsyncManager.me().execute(new TimerTask()
            {
                @Override
                public void run()
                {
                    renameTaskManager.executeRenameDetails(detailId, null, null);
                }
            });
        }
        return Result.success();
    }

    /**
     * 构建查询条件
     */
    private com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RenameDetailPlus> buildQueryWrapper(RenameDetailPlus renameDetail)
    {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RenameDetailPlus> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (renameDetail != null)
        {
            if (StringUtils.isNotEmpty(renameDetail.getOriginalPath()))
            {
                wrapper.like("original_path", renameDetail.getOriginalPath());
            }
            if (StringUtils.isNotEmpty(renameDetail.getOriginalName()))
            {
                wrapper.like("original_name", renameDetail.getOriginalName());
            }
            if (StringUtils.isNotEmpty(renameDetail.getNewPath()))
            {
                wrapper.like("new_path", renameDetail.getNewPath());
            }
            if (StringUtils.isNotEmpty(renameDetail.getNewName()))
            {
                wrapper.like("new_name", renameDetail.getNewName());
            }
            if (StringUtils.isNotEmpty(renameDetail.getMediaType()))
            {
                wrapper.eq("media_type", renameDetail.getMediaType());
            }
            if (StringUtils.isNotEmpty(renameDetail.getTitle()))
            {
                wrapper.like("title", renameDetail.getTitle());
            }
            if (StringUtils.isNotEmpty(renameDetail.getYear()))
            {
                wrapper.eq("year", renameDetail.getYear());
            }
            if (StringUtils.isNotEmpty(renameDetail.getStatus()))
            {
                wrapper.eq("status", renameDetail.getStatus());
            }
        }
        wrapper.orderByDesc("create_time");
        return wrapper;
    }

    /**
     * 获取当前页码
     */
    private int getPageNum()
    {
        String pageNumStr = com.ruoyi.common.utils.ServletUtils.getRequest().getParameter("pageNum");
        return pageNumStr != null ? Integer.parseInt(pageNumStr) : 1;
    }

    /**
     * 获取每页大小
     */
    private int getPageSize()
    {
        String pageSizeStr = com.ruoyi.common.utils.ServletUtils.getRequest().getParameter("pageSize");
        return pageSizeStr != null ? Integer.parseInt(pageSizeStr) : 10;
    }
}
