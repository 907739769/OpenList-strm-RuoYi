package com.ruoyi.openliststrm.controller.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.PageResult;
import com.ruoyi.common.core.domain.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 通用 CRUD Controller 基类
 * <p>
 * 提供 list / getById / add / edit / delete 五个标准 CRUD 端点。
 * 子类只需实现 {@link #buildQueryWrapper(Object)} 并提供自己的 @RequestMapping。
 * 若子类需要自定义验证逻辑，可覆写对应方法。
 * </p>
 *
 * @param <S> IService 子类
 * @param <T> 实体类型
 * @author Jack
 */
public abstract class BaseCrudRestController<S extends IService<T>, T> extends BaseController
{
    @Autowired
    protected S service;

    /**
     * 子类实现查询条件构建（返回 QueryWrapper 或 LambdaQueryWrapper 均可）
     */
    protected abstract Wrapper<T> buildQueryWrapper(T entity);

    /**
     * 分页查询列表 - 支持 /xxx 和 /xxx/list
     */
    @GetMapping({"", "/list"})
    public Result<PageResult<T>> list(T entity)
    {
        return Result.success(selectPage(service.getBaseMapper(), buildQueryWrapper(entity)));
    }

    /**
     * 根据 ID 获取详情
     */
    @GetMapping("/{id}")
    public Result<T> getById(@PathVariable("id") Integer id)
    {
        T record = service.getById(id);
        if (record == null)
        {
            return Result.error("记录不存在");
        }
        return Result.success(record);
    }

    /**
     * 新增
     */
    @PostMapping
    public Result<Void> add(@RequestBody T entity)
    {
        boolean result = service.save(entity);
        return result ? Result.success() : Result.error("新增失败");
    }

    /**
     * 修改
     */
    @PutMapping
    public Result<Void> edit(@RequestBody T entity)
    {
        boolean result = service.updateById(entity);
        return result ? Result.success() : Result.error("修改失败");
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id)
    {
        boolean result = service.removeById(id);
        return result ? Result.success() : Result.error("删除失败");
    }
}
