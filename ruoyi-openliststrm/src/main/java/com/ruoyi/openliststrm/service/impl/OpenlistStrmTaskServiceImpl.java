package com.ruoyi.openliststrm.service.impl;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.openliststrm.domain.OpenlistStrmTask;
import com.ruoyi.openliststrm.mapper.OpenlistStrmTaskMapper;
import com.ruoyi.openliststrm.service.IOpenlistStrmTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * strm任务配置Service业务层处理
 * 
 * @author Jack
 * @date 2025-07-18
 */
@Service
public class OpenlistStrmTaskServiceImpl implements IOpenlistStrmTaskService 
{
    @Autowired
    private OpenlistStrmTaskMapper openlistStrmTaskMapper;

    /**
     * 查询strm任务配置
     * 
     * @param strmTaskId strm任务配置主键
     * @return strm任务配置
     */
    @Override
    public OpenlistStrmTask selectOpenlistStrmTaskByStrmTaskId(Long strmTaskId)
    {
        return openlistStrmTaskMapper.selectOpenlistStrmTaskByStrmTaskId(strmTaskId);
    }

    /**
     * 查询strm任务配置列表
     * 
     * @param openlistStrmTask strm任务配置
     * @return strm任务配置
     */
    @Override
    public List<OpenlistStrmTask> selectOpenlistStrmTaskList(OpenlistStrmTask openlistStrmTask)
    {
        return openlistStrmTaskMapper.selectOpenlistStrmTaskList(openlistStrmTask);
    }

    /**
     * 新增strm任务配置
     * 
     * @param openlistStrmTask strm任务配置
     * @return 结果
     */
    @Override
    public int insertOpenlistStrmTask(OpenlistStrmTask openlistStrmTask)
    {
        openlistStrmTask.setCreateTime(DateUtils.getNowDate());
        return openlistStrmTaskMapper.insertOpenlistStrmTask(openlistStrmTask);
    }

    /**
     * 修改strm任务配置
     * 
     * @param openlistStrmTask strm任务配置
     * @return 结果
     */
    @Override
    public int updateOpenlistStrmTask(OpenlistStrmTask openlistStrmTask)
    {
        openlistStrmTask.setUpdateTime(DateUtils.getNowDate());
        return openlistStrmTaskMapper.updateOpenlistStrmTask(openlistStrmTask);
    }

    /**
     * 批量删除strm任务配置
     * 
     * @param strmTaskIds 需要删除的strm任务配置主键
     * @return 结果
     */
    @Override
    public int deleteOpenlistStrmTaskByStrmTaskIds(String strmTaskIds)
    {
        return openlistStrmTaskMapper.deleteOpenlistStrmTaskByStrmTaskIds(Convert.toStrArray(strmTaskIds));
    }

    /**
     * 删除strm任务配置信息
     * 
     * @param strmTaskId strm任务配置主键
     * @return 结果
     */
    @Override
    public int deleteOpenlistStrmTaskByStrmTaskId(Long strmTaskId)
    {
        return openlistStrmTaskMapper.deleteOpenlistStrmTaskByStrmTaskId(strmTaskId);
    }
}
