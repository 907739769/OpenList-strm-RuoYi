package com.ruoyi.openliststrm.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.openliststrm.mapper.OpenlistCopyTaskMapper;
import com.ruoyi.openliststrm.domain.OpenlistCopyTask;
import com.ruoyi.openliststrm.service.IOpenlistCopyTaskService;
import com.ruoyi.common.core.text.Convert;

/**
 * 文件同步任务Service业务层处理
 * 
 * @author Jack
 * @date 2025-07-17
 */
@Service
public class OpenlistCopyTaskServiceImpl implements IOpenlistCopyTaskService 
{
    @Autowired
    private OpenlistCopyTaskMapper openlistCopyTaskMapper;

    /**
     * 查询文件同步任务
     * 
     * @param copyTaskId 文件同步任务主键
     * @return 文件同步任务
     */
    @Override
    public OpenlistCopyTask selectOpenlistCopyTaskByCopyTaskId(Integer copyTaskId)
    {
        return openlistCopyTaskMapper.selectOpenlistCopyTaskByCopyTaskId(copyTaskId);
    }

    /**
     * 查询文件同步任务列表
     * 
     * @param openlistCopyTask 文件同步任务
     * @return 文件同步任务
     */
    @Override
    public List<OpenlistCopyTask> selectOpenlistCopyTaskList(OpenlistCopyTask openlistCopyTask)
    {
        return openlistCopyTaskMapper.selectOpenlistCopyTaskList(openlistCopyTask);
    }

    /**
     * 新增文件同步任务
     * 
     * @param openlistCopyTask 文件同步任务
     * @return 结果
     */
    @Override
    public int insertOpenlistCopyTask(OpenlistCopyTask openlistCopyTask)
    {
        openlistCopyTask.setCreateTime(DateUtils.getNowDate());
        return openlistCopyTaskMapper.insertOpenlistCopyTask(openlistCopyTask);
    }

    /**
     * 修改文件同步任务
     * 
     * @param openlistCopyTask 文件同步任务
     * @return 结果
     */
    @Override
    public int updateOpenlistCopyTask(OpenlistCopyTask openlistCopyTask)
    {
        openlistCopyTask.setUpdateTime(DateUtils.getNowDate());
        return openlistCopyTaskMapper.updateOpenlistCopyTask(openlistCopyTask);
    }

    /**
     * 批量删除文件同步任务
     * 
     * @param copyTaskIds 需要删除的文件同步任务主键
     * @return 结果
     */
    @Override
    public int deleteOpenlistCopyTaskByCopyTaskIds(String copyTaskIds)
    {
        return openlistCopyTaskMapper.deleteOpenlistCopyTaskByCopyTaskIds(Convert.toStrArray(copyTaskIds));
    }

    /**
     * 删除文件同步任务信息
     * 
     * @param copyTaskId 文件同步任务主键
     * @return 结果
     */
    @Override
    public int deleteOpenlistCopyTaskByCopyTaskId(Integer copyTaskId)
    {
        return openlistCopyTaskMapper.deleteOpenlistCopyTaskByCopyTaskId(copyTaskId);
    }
}
