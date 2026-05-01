package com.ruoyi.web.controller.api.system;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageInfo;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.PageResult;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.system.domain.SysConfig;
import com.ruoyi.system.service.ISysConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * 参数配置REST API控制器
 *
 * @author ruoyi
 */
@Tag(name = "参数配置管理API")
@RestController
@RequestMapping("/api/system/config")
@Anonymous
@CrossOrigin(origins = "*")
public class SysConfigApiController extends BaseController
{
    @Autowired
    private ISysConfigService configService;

    /**
     * 查询参数配置分页列表
     */
    @Operation(summary = "查询参数配置分页列表")
    @GetMapping("/list")
    public Result<PageResult<SysConfig>> list(SysConfig config)
    {
        startPage();
        List<SysConfig> list = configService.selectConfigList(config);
        PageInfo<SysConfig> pageInfo = new PageInfo<>(list);
        return Result.success(PageResult.of(list, pageInfo.getTotal(), pageInfo.getPageNum(), pageInfo.getPageSize()));
    }

    /**
     * 根据参数配置ID查询参数配置信息
     */
    @Operation(summary = "根据参数配置ID查询参数配置信息")
    @GetMapping("/{configId}")
    public Result<SysConfig> getInfo(@PathVariable("configId") Long configId)
    {
        SysConfig config = configService.selectConfigById(configId);
        return Result.success(config);
    }

    /**
     * 新增参数配置
     */
    @Operation(summary = "新增参数配置")
    @PostMapping
    public Result<Integer> add(@Validated @RequestBody SysConfig config)
    {
        if (!configService.checkConfigKeyUnique(config))
        {
            return Result.error("新增参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setCreateBy(getLoginName());
        int rows = configService.insertConfig(config);
        return Result.success(rows);
    }

    /**
     * 修改参数配置
     */
    @Operation(summary = "修改参数配置")
    @PutMapping
    public Result<Integer> edit(@Validated @RequestBody SysConfig config)
    {
        if (!configService.checkConfigKeyUnique(config))
        {
            return Result.error("修改参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setUpdateBy(getLoginName());
        int rows = configService.updateConfig(config);
        return Result.success(rows);
    }

    /**
     * 删除参数配置（单个）
     */
    @Operation(summary = "删除参数配置")
    @DeleteMapping("/{configId}")
    public Result<Integer> remove(@PathVariable("configId") Long configId)
    {
        configService.deleteConfigByIds(configId.toString());
        return Result.success(1);
    }

    /**
     * 删除参数配置（批量）
     */
    @Operation(summary = "删除参数配置（批量）")
    @DeleteMapping
    public Result<Integer> removeBatch(@RequestBody String configIds)
    {
        configService.deleteConfigByIds(configIds);
        return Result.success(1);
    }

    /**
     * 刷新参数缓存
     */
    @Operation(summary = "刷新参数缓存")
    @PostMapping("/refreshCache")
    public Result<Void> refreshCache()
    {
        configService.resetConfigCache();
        return Result.success();
    }

    /**
     * 校验参数键名是否唯一
     */
    @Operation(summary = "校验参数键名是否唯一")
    @GetMapping("/checkConfigKeyUnique")
    public Result<Boolean> checkConfigKeyUnique(SysConfig config)
    {
        boolean isUnique = configService.checkConfigKeyUnique(config);
        return Result.success(isUnique);
    }
}
