package com.ruoyi.web.controller.api.system;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.ruoyi.system.service.ISysDictDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * 字典数据REST API控制器
 *
 * @author ruoyi
 */
@Tag(name = "字典数据管理API")
@RestController
@RequestMapping("/api/system/dict/data")
@Anonymous
@CrossOrigin(origins = "*")
public class SysDictDataApiController extends BaseController
{
    @Autowired
    private ISysDictDataService dictDataService;

    /**
     * 查询字典数据分页列表
     */
    @Operation(summary = "查询字典数据分页列表")
    @GetMapping("/list")
    public Result<PageResult<SysDictData>> list(SysDictData dictData)
    {
        startPage();
        List<SysDictData> list = dictDataService.selectDictDataList(dictData);
        PageInfo<SysDictData> pageInfo = new PageInfo<>(list);
        return Result.success(PageResult.of(list, pageInfo.getTotal(), pageInfo.getPageNum(), pageInfo.getPageSize()));
    }

    /**
     * 根据字典数据ID查询字典数据信息
     */
    @Operation(summary = "根据字典数据ID查询字典数据信息")
    @GetMapping("/{dictCode}")
    public Result<SysDictData> getInfo(@PathVariable("dictCode") Long dictCode)
    {
        SysDictData dictData = dictDataService.selectDictDataById(dictCode);
        return Result.success(dictData);
    }

    /**
     * 新增字典数据
     */
    @Operation(summary = "新增字典数据")
    @PostMapping
    public Result<Integer> add(@RequestBody SysDictData dict)
    {
        dict.setCreateBy(getLoginName());
        int rows = dictDataService.insertDictData(dict);
        return Result.success(rows);
    }

    /**
     * 修改字典数据
     */
    @Operation(summary = "修改字典数据")
    @PutMapping
    public Result<Integer> edit(@RequestBody SysDictData dict)
    {
        dict.setUpdateBy(getLoginName());
        int rows = dictDataService.updateDictData(dict);
        return Result.success(rows);
    }

    /**
     * 删除字典数据（单个）
     */
    @Operation(summary = "删除字典数据")
    @DeleteMapping("/{dictCode}")
    public Result<Integer> remove(@PathVariable("dictCode") Long dictCode)
    {
        dictDataService.deleteDictDataByIds(dictCode.toString());
        return Result.success(1);
    }

    /**
     * 删除字典数据（批量）
     */
    @Operation(summary = "删除字典数据（批量）")
    @DeleteMapping
    public Result<Integer> removeBatch(@RequestBody String ids)
    {
        dictDataService.deleteDictDataByIds(ids);
        return Result.success(1);
    }
}
