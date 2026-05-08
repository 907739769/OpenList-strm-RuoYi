package com.ruoyi.common.utils;

import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.common.utils.sql.SqlUtil;
import com.ruoyi.common.utils.StringUtils;

/**
 * 分页工具类（已迁移至 MyBatis-Plus 分页）
 * 
 * @deprecated 请使用 MyBatis-Plus 的 Page 对象进行分页查询
 */
@Deprecated
public class PageUtils
{
    /**
     * 设置请求分页数据（已废弃 - 请使用 MyBatis-Plus Page 对象）
     * 
     * @deprecated 此方法已不再生效，Controller 中应直接创建 Page<T> 对象
     */
    @Deprecated
    public static void startPage()
    {
    }

    /**
     * 设置请求排序数据（已废弃）
     * 
     * @deprecated 此方法已不再生效
     */
    @Deprecated
    public static void startOrderBy()
    {
    }

    /**
     * 清理分页的线程变量（已废弃）
     * 
     * @deprecated 此方法已不再需要
     */
    @Deprecated
    public static void clearPage()
    {
    }

    /**
     * 从请求中获取分页参数
     */
    public static PageDomain getPageDomain()
    {
        return TableSupport.buildPageRequest();
    }
}
