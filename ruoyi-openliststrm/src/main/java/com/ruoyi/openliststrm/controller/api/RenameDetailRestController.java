package com.ruoyi.openliststrm.controller.api;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.PageResult;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.enums.StrmStatusEnum;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameTaskPlusService;
import com.ruoyi.openliststrm.rename.RenameTaskManager;
import com.ruoyi.openliststrm.scrape.ScrapeService;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import com.ruoyi.openliststrm.rename.MediaParser;
import com.ruoyi.openliststrm.rename.RenameClientProvider;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.rename.RenameEventListener;
import com.ruoyi.openliststrm.monitor.processor.MediaRenameProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
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
    private RenameTaskManager renameTaskManager;

    @Autowired
    private IRenameDetailPlusService renameDetailPlusService;

    @Autowired
    private IRenameTaskPlusService renameTaskPlusService;

    @Autowired
    private ScrapeService scrapeService;

    @Autowired
    private RenameClientProvider clientProvider;

    @Autowired
    private OpenListHelper openListHelper;

    @Autowired
    private OpenlistConfig config;

    /**
     * 查询重命名明细列表（分页）- 支持 /rename-details 和 /rename-details/list
     */
    @GetMapping({ "", "/list" })
    public Result<PageResult<RenameDetailPlus>> list(RenameDetailPlus renameDetail)
    {
        return Result.success(selectPage(renameDetailPlusService.getBaseMapper(), buildQueryWrapper(renameDetail)));
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
    @PostMapping("/batchDelete")
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
                                @RequestParam(value = "year", required = false) String year,
                                @RequestParam(value = "season", required = false) String season,
                                @RequestParam(value = "episode", required = false) String episode)
    {
        if (id == null)
        {
            return Result.error("id 为空");
        }
        logger.info("开始执行重命名明细，ID：{}，title={}，year={}，season={}，episode={}", id, title, year, season, episode);
        AsyncManager.me().execute(() -> renameTaskManager.executeRenameDetails(id, title, year, season, episode));
        return Result.success();
    }

    /**
     * 批量执行重命名明细
     */
    @PostMapping("/execute")
    public Result<Void> batchExecute(@RequestParam("ids") String ids,
                                     @RequestParam(value = "title", required = false) String title,
                                     @RequestParam(value = "year", required = false) String year,
                                     @RequestParam(value = "season", required = false) String season,
                                     @RequestParam(value = "episode", required = false) String episode)
    {
        if (ids == null || ids.trim().isEmpty())
        {
            return Result.error("请选择要执行的记录");
        }
        List<Integer> idList = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).collect(Collectors.toList());
        for (Integer id : idList)
        {
            logger.info("开始执行重命名明细，ID：{}，title={}，year={}，season={}，episode={}", id, title, year, season, episode);
            final int detailId = id;
            final String t = title;
            final String y = year;
            final String s = season;
            final String e = episode;
            AsyncManager.me().execute(() -> renameTaskManager.executeRenameDetails(detailId, t, y, s, e));
        }
        return Result.success();
    }

    /**
     * 重新刮削单条记录（默认全部刮削：NFO+图片）
     */
    @PostMapping("/scrape/{id}")
    public Result<Void> scrape(@PathVariable("id") Integer id)
    {
        if (id == null)
        {
            return Result.error("ID 为空");
        }
        RenameDetailPlus detail = renameDetailPlusService.getById(id);
        if (detail == null)
        {
            return Result.error("重命名明细不存在");
        }
        logger.info("开始重新刮削（全部），ID：{}", id);
        final int detailId = id;
        final String newName = detail.getNewName();
        final String mediaType = detail.getMediaType();
        
        AsyncManager.me().execute(() -> {
            try {
                // 解析媒体信息
                MediaParser parser = new MediaParser(clientProvider.tmdb(), clientProvider.openAI());
                MediaInfo info = parser.parse(newName);
                
                // 查找目标文件
                Path destFile = Paths.get(detail.getNewPath(), newName);
                Path outputDir = destFile.getParent();
                
                // 默认全部刮削：NFO + 图片，强制覆盖
                scrapeService.scrapeAsync(
                        detailId, info, mediaType, destFile, outputDir,
                        "1", "1", "1", true
                );
                
                logger.info("刮削任务已启动，ID：{}", id);
            } catch (Exception e) {
                logger.error("刮削失败，ID：{}", id, e);
            }
        });
        return Result.success();
    }

    /**
     * 批量重新刮削
     */
    @PostMapping("/scrape")
    public Result<Void> batchScrape(@RequestParam("ids") String ids)
    {
        if (ids == null || ids.trim().isEmpty())
        {
            return Result.error("请选择要刮削的记录");
        }
        List<Integer> idList = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).collect(Collectors.toList());
        for (Integer id : idList)
        {
            logger.info("开始批量刮削，ID：{}", id);
            final int detailId = id;
            AsyncManager.me().execute(() -> {
                try {
                    RenameDetailPlus detail = renameDetailPlusService.getById(detailId);
                    if (detail != null) {
                        scrape(detail.getId());
                    }
                } catch (Exception e) {
                    logger.error("批量刮削失败，ID：{}", detailId, e);
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
            if (renameDetail.getParams() != null)
            {
                String beginTime = (String) renameDetail.getParams().get("beginTime");
                String endTime = (String) renameDetail.getParams().get("endTime");
                if (StringUtils.isNotEmpty(beginTime))
                {
                    wrapper.ge("create_time", beginTime);
                }
                if (StringUtils.isNotEmpty(endTime))
                {
                    wrapper.le("create_time", endTime);
                }
            }
        }
        wrapper.orderByDesc("create_time");
        return wrapper;
    }
}
