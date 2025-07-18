package com.ruoyi.openliststrm.service;

import java.util.List;
import com.ruoyi.openliststrm.domain.OpenlistStrmTask;

/**
 * strm任务配置Service接口
 * 
 * @author Jack
 * @date 2025-07-18
 */
public interface IOpenlistStrmTaskService 
{
    /**
     * 查询strm任务配置
     * 
     * @param strmTaskId strm任务配置主键
     * @return strm任务配置
     */
    public OpenlistStrmTask selectOpenlistStrmTaskByStrmTaskId(Long strmTaskId);

    /**
     * 查询strm任务配置列表
     * 
     * @param openlistStrmTask strm任务配置
     * @return strm任务配置集合
     */
    public List<OpenlistStrmTask> selectOpenlistStrmTaskList(OpenlistStrmTask openlistStrmTask);

    /**
     * 新增strm任务配置
     * 
     * @param openlistStrmTask strm任务配置
     * @return 结果
     */
    public int insertOpenlistStrmTask(OpenlistStrmTask openlistStrmTask);

    /**
     * 修改strm任务配置
     * 
     * @param openlistStrmTask strm任务配置
     * @return 结果
     */
    public int updateOpenlistStrmTask(OpenlistStrmTask openlistStrmTask);

    /**
     * 批量删除strm任务配置
     * 
     * @param strmTaskIds 需要删除的strm任务配置主键集合
     * @return 结果
     */
    public int deleteOpenlistStrmTaskByStrmTaskIds(String strmTaskIds);

    /**
     * 删除strm任务配置信息
     * 
     * @param strmTaskId strm任务配置主键
     * @return 结果
     */
    public int deleteOpenlistStrmTaskByStrmTaskId(Long strmTaskId);
}
