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
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysDictType;
import com.ruoyi.system.service.ISysDictDataService;
import com.ruoyi.system.service.ISysDictTypeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * 字典类型REST API控制器
 *
 * @author ruoyi
 */
@Tag(name = "字典类型管理API")
@RestController
@RequestMapping("/api/system/dict/type")
@Anonymous
@CrossOrigin(origins = "*")
public class SysDictTypeApiController extends BaseController
{
    @Autowired
    private ISysDictTypeService dictTypeService;

    @Autowired
    private ISysDictDataService dictDataService;

    /**
     * 查询字典类型分页列表
     */
    @Operation(summary = "查询字典类型分页列表")
    @GetMapping("/list")
    public Result<PageResult<SysDictType>> list(SysDictType dictType)
    {
        startPage();
        List<SysDictType> list = dictTypeService.selectDictTypeList(dictType);
        PageInfo<SysDictType> pageInfo = new PageInfo<>(list);
        return Result.success(PageResult.of(list, pageInfo.getTotal(), pageInfo.getPageNum(), pageInfo.getPageSize()));
    }

    /**
     * 根据字典类型ID查询字典类型信息
     */
    @Operation(summary = "根据字典类型ID查询字典类型信息")
    @GetMapping("/{dictId}")
    public Result<SysDictType> getInfo(@PathVariable("dictId") Long dictId)
    {
        SysDictType dictType = dictTypeService.selectDictTypeById(dictId);
        return Result.success(dictType);
    }

    /**
     * 新增字典类型
     */
    @Operation(summary = "新增字典类型")
    @PostMapping
    public Result<Integer> add(@Validated @RequestBody SysDictType dict)
    {
        if (!dictTypeService.checkDictTypeUnique(dict))
        {
            return Result.error("新增字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dict.setCreateBy(getLoginName());
        int rows = dictTypeService.insertDictType(dict);
        return Result.success(rows);
    }

    /**
     * 修改字典类型
     */
    @Operation(summary = "修改字典类型")
    @PutMapping
    public Result<Integer> edit(@Validated @RequestBody SysDictType dict)
    {
        if (!dictTypeService.checkDictTypeUnique(dict))
        {
            return Result.error("修改字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dict.setUpdateBy(getLoginName());
        int rows = dictTypeService.updateDictType(dict);
        return Result.success(rows);
    }

    /**
     * 删除字典类型（单个）
     */
    @Operation(summary = "删除字典类型")
    @DeleteMapping("/{dictId}")
    public Result<Integer> remove(@PathVariable("dictId") Long dictId)
    {
        dictTypeService.deleteDictTypeByIds(dictId.toString());
        return Result.success(1);
    }

    /**
     * 删除字典类型（批量）
     */
    @Operation(summary = "删除字典类型（批量）")
    @DeleteMapping
    public Result<Integer> removeBatch(@RequestBody String dictIds)
    {
        dictTypeService.deleteDictTypeByIds(dictIds);
        return Result.success(1);
    }

    /**
     * 查询字典类型选项集合
     */
    @Operation(summary = "查询字典类型选项集合")
    @GetMapping("/options")
    public Result<List<SysDictType>> options()
    {
        List<SysDictType> dictTypes = dictTypeService.selectDictTypeAll();
        return Result.success(dictTypes);
    }

    /**
     * 根据字典类型查询字典数据
     */
    @Operation(summary = "根据字典类型查询字典数据")
    @GetMapping("/{dictType}")
    public Result<List<SysDictData>> dictData(@PathVariable("dictType") String dictType)
    {
        SysDictData dictData = new SysDictData();
        dictData.setDictType(dictType);
        List<SysDictData> dictDatas = dictDataService.selectDictDataList(dictData);
        return Result.success(dictDatas);
    }

    /**
     * 刷新字典缓存
     */
    @Operation(summary = "刷新字典缓存")
    @PostMapping("/refreshCache")
    public Result<Void> refreshCache()
    {
        dictTypeService.resetDictCache();
        return Result.success();
    }

    /**
     * 校验字典类型是否唯一
     */
    @Operation(summary = "校验字典类型是否唯一")
    @PostMapping("/checkDictTypeUnique")
    public Result<Boolean> checkDictTypeUnique(SysDictType dictType)
    {
        boolean isUnique = dictTypeService.checkDictTypeUnique(dictType);
        return Result.success(isUnique);
    }
}
