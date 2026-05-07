package com.ruoyi.openliststrm.controller.api;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.PageResult;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameTaskPlusService;
import com.ruoyi.openliststrm.rename.*;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import com.ruoyi.openliststrm.tmdb.TMDbClient;
import com.ruoyi.openliststrm.openai.OpenAIClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.TimerTask;
import java.util.stream.Collectors;

/**
 * 重命名任务配置 REST API控制器
 *
 * @author Jack
 * @date 2025-10-10
 */
@RestController
@RequestMapping("/api/openliststrm/rename-tasks")
@Anonymous
@CrossOrigin
public class RenameTaskRestController extends BaseController
{
    private static final String DEFAULT_FILENAME_TEMPLATE = "{{ title }} {% if year %} ({{ year }}) {% endif %}/{% if season %}Season {{ season }}/{% endif %}{{ title }} {% if year and not season %} ({{ year }}) {% endif %}{% if season %}S{{ season }}{% endif %}{% if episode %}E{{ episode }}{% endif %}{% if resolution %} - {{ resolution }}{% endif %}{% if source %}.{{ source }}{% endif %}{% if videoCodec %}.{{ videoCodec }}{% endif %}{% if audioCodec %}.{{ audioCodec }}{% endif %}{% if tags is not empty %}.{{ tags|join('.') }}{% endif %}{% if releaseGroup %}-{{ releaseGroup }}{% endif %}.{{ extension }}";

    @Autowired
    private IRenameTaskPlusService renameTaskPlusService;

    @Autowired
    private RenameTaskManager renameTaskManager;

    @Autowired
    private RenameClientProvider renameClientProvider;

    @Autowired
    private OpenlistConfig config;

    /**
     * 查询重命名任务配置列表（分页）- 支持 /rename-tasks 和 /rename-tasks/list
     */
    @GetMapping({ "", "/list" })
    public Result<PageResult<RenameTaskPlus>> list(RenameTaskPlus renameTask)
    {
        return Result.success(selectPage(renameTaskPlusService.getBaseMapper(), buildQueryWrapper(renameTask)));
    }

    /**
     * 根据ID获取重命名任务配置
     */
    @GetMapping("/{id}")
    public Result<RenameTaskPlus> getById(@PathVariable("id") Integer id)
    {
        RenameTaskPlus task = renameTaskPlusService.getById(id);
        if (task == null)
        {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }

    /**
     * 新增重命名任务配置
     */
    @PostMapping
    public Result<Void> add(@RequestBody RenameTaskPlus renameTask)
    {
        boolean result = renameTaskPlusService.save(renameTask);
        if (result)
        {
            return Result.success();
        }
        return Result.error("新增失败");
    }

    /**
     * 修改重命名任务配置
     */
    @PutMapping
    public Result<Void> edit(@RequestBody RenameTaskPlus renameTask)
    {
        if (renameTask.getId() == null)
        {
            return Result.error("任务ID不能为空");
        }
        RenameTaskPlus existing = renameTaskPlusService.getById(renameTask.getId());
        if (existing == null)
        {
            return Result.error("任务不存在");
        }
        boolean result = renameTaskPlusService.updateById(renameTask);
        if (result)
        {
            return Result.success();
        }
        return Result.error("修改失败");
    }

    /**
     * 删除重命名任务配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id)
    {
        RenameTaskPlus existing = renameTaskPlusService.getById(id);
        if (existing == null)
        {
            return Result.error("任务不存在");
        }
        boolean result = renameTaskPlusService.removeById(id);
        if (result)
        {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 批量删除重命名任务配置
     */
    @DeleteMapping
    public Result<Void> batchDelete(@RequestParam("ids") String ids)
    {
        if (ids == null || ids.trim().isEmpty())
        {
            return Result.error("请选择要删除的任务");
        }
        List<String> idList = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        boolean result = renameTaskPlusService.removeByIds(idList);
        if (result)
        {
            return Result.success();
        }
        return Result.error("批量删除失败");
    }

    /**
     * 立即执行重命名任务
     */
    @PostMapping("/execute/{id}")
    public Result<Void> execute(@PathVariable("id") Integer id)
    {
        RenameTaskPlus task = renameTaskPlusService.getById(id);
        if (task == null)
        {
            return Result.error("任务不存在");
        }
        logger.info("开始执行重命名任务，任务ID：{}", id);
        AsyncManager.me().execute(new TimerTask()
        {
            @Override
            public void run()
            {
                renameTaskManager.executeTaskNow(id);
            }
        });
        return Result.success();
    }

    /**
     * 批量执行重命名任务
     */
    @PostMapping("/execute")
    public Result<Void> batchExecute(@RequestBody List<Integer> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return Result.error("请选择要执行的任务");
        }
        for (Integer id : ids)
        {
            RenameTaskPlus task = renameTaskPlusService.getById(id);
            if (task != null)
            {
                logger.info("开始执行重命名任务，任务ID：{}", id);
                final int taskId = id;
                AsyncManager.me().execute(new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        renameTaskManager.executeTaskNow(taskId);
                    }
                });
            }
        }
        return Result.success();
    }

    /**
     * 测试重命名解析（预览）
     */
    @PostMapping("/test/{id}")
    public Result<Map<String, Object>> test(@PathVariable("id") Integer id,
                                            @RequestParam("filename") String filename,
                                            @RequestParam(value = "template", required = false) String template)
    {
        if (StringUtils.isEmpty(filename))
        {
            return Result.error("文件名不能为空");
        }

        RenameTaskPlus task = renameTaskPlusService.getById(id);
        if (task == null)
        {
            return Result.error("任务不存在");
        }

        renameClientProvider.refresh(config);
        TMDbClient tmdbClient = renameClientProvider.tmdb();
        OpenAIClient openAIClient = renameClientProvider.openAI();

        MediaParser parser = new MediaParser(tmdbClient, openAIClient);

        try
        {
            MediaInfo info = parser.parse(filename);

            String renderTemplate = StringUtils.isEmpty(template) ? DEFAULT_FILENAME_TEMPLATE : template;
            String renamed = parser.render(info, renderTemplate);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("info", info);
            result.put("renamed", renamed);
            result.put("template", renderTemplate);
            return Result.success(result);
        }
        catch (Exception e)
        {
            return Result.error("解析失败: " + e.getMessage());
        }
    }

    /**
     * 通用测试重命名解析（不依赖具体任务）
     */
    @PostMapping("/test/parse")
    public Result<Map<String, Object>> testParse(@RequestParam("filename") String filename,
                                                 @RequestParam(value = "template", required = false) String template)
    {
        if (StringUtils.isEmpty(filename))
        {
            return Result.error("文件名不能为空");
        }

        renameClientProvider.refresh(config);
        TMDbClient tmdbClient = renameClientProvider.tmdb();
        OpenAIClient openAIClient = renameClientProvider.openAI();

        MediaParser parser = new MediaParser(tmdbClient, openAIClient);

        try
        {
            MediaInfo info = parser.parse(filename);

            String renderTemplate = StringUtils.isEmpty(template) ? DEFAULT_FILENAME_TEMPLATE : template;
            String renamed = parser.render(info, renderTemplate);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("info", info);
            result.put("renamed", renamed);
            result.put("template", renderTemplate);
            return Result.success(result);
        }
        catch (Exception e)
        {
            return Result.error("解析失败: " + e.getMessage());
        }
    }

    /**
     * 构建查询条件
     */
    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RenameTaskPlus> buildQueryWrapper(RenameTaskPlus renameTask)
    {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RenameTaskPlus> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (renameTask != null)
        {
            if (renameTask.getSourceFolder() != null && !renameTask.getSourceFolder().isEmpty())
            {
                wrapper.like(RenameTaskPlus::getSourceFolder, renameTask.getSourceFolder());
            }
            if (renameTask.getTargetRoot() != null && !renameTask.getTargetRoot().isEmpty())
            {
                wrapper.like(RenameTaskPlus::getTargetRoot, renameTask.getTargetRoot());
            }
            if (renameTask.getStatus() != null && !renameTask.getStatus().isEmpty())
            {
                wrapper.eq(RenameTaskPlus::getStatus, renameTask.getStatus());
            }
        }
        wrapper.last("ORDER BY create_time DESC");
        return wrapper;
    }
}
