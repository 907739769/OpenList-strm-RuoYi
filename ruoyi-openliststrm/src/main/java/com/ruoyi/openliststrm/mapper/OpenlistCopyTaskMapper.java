package com.ruoyi.openliststrm.mapper;

import java.util.List;
import com.ruoyi.openliststrm.domain.OpenlistCopyTask;

/**
 * 文件同步任务Mapper接口
 * 
 * @author Jack
 * @date 2025-07-17
 */
public interface OpenlistCopyTaskMapper 
{
    /**
     * 查询文件同步任务
     * 
     * @param copyTaskId 文件同步任务主键
     * @return 文件同步任务
     */
    public OpenlistCopyTask selectOpenlistCopyTaskByCopyTaskId(Integer copyTaskId);

    /**
     * 查询文件同步任务列表
     * 
     * @param openlistCopyTask 文件同步任务
     * @return 文件同步任务集合
     */
    public List<OpenlistCopyTask> selectOpenlistCopyTaskList(OpenlistCopyTask openlistCopyTask);

    /**
     * 新增文件同步任务
     * 
     * @param openlistCopyTask 文件同步任务
     * @return 结果
     */
    public int insertOpenlistCopyTask(OpenlistCopyTask openlistCopyTask);

    /**
     * 修改文件同步任务
     * 
     * @param openlistCopyTask 文件同步任务
     * @return 结果
     */
    public int updateOpenlistCopyTask(OpenlistCopyTask openlistCopyTask);

    /**
     * 删除文件同步任务
     * 
     * @param copyTaskId 文件同步任务主键
     * @return 结果
     */
    public int deleteOpenlistCopyTaskByCopyTaskId(Integer copyTaskId);

    /**
     * 批量删除文件同步任务
     * 
     * @param copyTaskIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteOpenlistCopyTaskByCopyTaskIds(String[] copyTaskIds);
}
