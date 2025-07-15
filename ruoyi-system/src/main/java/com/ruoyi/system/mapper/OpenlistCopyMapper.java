package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.OpenlistCopy;

/**
 * openlist的文件同步复制任务Mapper接口
 * 
 * @author Jack
 * @date 2025-07-15
 */
public interface OpenlistCopyMapper 
{
    /**
     * 查询openlist的文件同步复制任务
     * 
     * @param copyId openlist的文件同步复制任务主键
     * @return openlist的文件同步复制任务
     */
    public OpenlistCopy selectOpenlistCopyByCopyId(Integer copyId);

    /**
     * 查询openlist的文件同步复制任务列表
     * 
     * @param openlistCopy openlist的文件同步复制任务
     * @return openlist的文件同步复制任务集合
     */
    public List<OpenlistCopy> selectOpenlistCopyList(OpenlistCopy openlistCopy);

    /**
     * 新增openlist的文件同步复制任务
     * 
     * @param openlistCopy openlist的文件同步复制任务
     * @return 结果
     */
    public int insertOpenlistCopy(OpenlistCopy openlistCopy);

    /**
     * 修改openlist的文件同步复制任务
     * 
     * @param openlistCopy openlist的文件同步复制任务
     * @return 结果
     */
    public int updateOpenlistCopy(OpenlistCopy openlistCopy);

    /**
     * 删除openlist的文件同步复制任务
     * 
     * @param copyId openlist的文件同步复制任务主键
     * @return 结果
     */
    public int deleteOpenlistCopyByCopyId(Integer copyId);

    /**
     * 批量删除openlist的文件同步复制任务
     * 
     * @param copyIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteOpenlistCopyByCopyIds(String[] copyIds);
}
