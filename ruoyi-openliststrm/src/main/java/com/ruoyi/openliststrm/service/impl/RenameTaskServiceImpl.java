package com.ruoyi.openliststrm.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.openliststrm.mapper.RenameTaskMapper;
import com.ruoyi.openliststrm.domain.RenameTask;
import com.ruoyi.openliststrm.service.IRenameTaskService;
import com.ruoyi.common.core.text.Convert;

/**
 * 重命名任务配置Service业务层处理
 * 
 * @author Jack
 * @date 2025-09-30
 */
@Service
public class RenameTaskServiceImpl implements IRenameTaskService 
{
    @Autowired
    private RenameTaskMapper renameTaskMapper;

    /**
     * 查询重命名任务配置
     * 
     * @param id 重命名任务配置主键
     * @return 重命名任务配置
     */
    @Override
    public RenameTask selectRenameTaskById(Integer id)
    {
        return renameTaskMapper.selectRenameTaskById(id);
    }

    /**
     * 查询重命名任务配置列表
     * 
     * @param renameTask 重命名任务配置
     * @return 重命名任务配置
     */
    @Override
    public List<RenameTask> selectRenameTaskList(RenameTask renameTask)
    {
        return renameTaskMapper.selectRenameTaskList(renameTask);
    }

    /**
     * 新增重命名任务配置
     * 
     * @param renameTask 重命名任务配置
     * @return 结果
     */
    @Override
    public int insertRenameTask(RenameTask renameTask)
    {
        renameTask.setCreateTime(DateUtils.getNowDate());
        return renameTaskMapper.insertRenameTask(renameTask);
    }

    /**
     * 修改重命名任务配置
     * 
     * @param renameTask 重命名任务配置
     * @return 结果
     */
    @Override
    public int updateRenameTask(RenameTask renameTask)
    {
        renameTask.setUpdateTime(DateUtils.getNowDate());
        return renameTaskMapper.updateRenameTask(renameTask);
    }

    /**
     * 批量删除重命名任务配置
     * 
     * @param ids 需要删除的重命名任务配置主键
     * @return 结果
     */
    @Override
    public int deleteRenameTaskByIds(String ids)
    {
        return renameTaskMapper.deleteRenameTaskByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除重命名任务配置信息
     * 
     * @param id 重命名任务配置主键
     * @return 结果
     */
    @Override
    public int deleteRenameTaskById(Integer id)
    {
        return renameTaskMapper.deleteRenameTaskById(id);
    }
}
