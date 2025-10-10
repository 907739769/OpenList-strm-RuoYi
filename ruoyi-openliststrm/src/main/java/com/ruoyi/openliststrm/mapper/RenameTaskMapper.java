package com.ruoyi.openliststrm.mapper;

import java.util.List;
import com.ruoyi.openliststrm.domain.RenameTask;

/**
 * 重命名任务配置Mapper接口
 * 
 * @author Jack
 * @date 2025-09-30
 */
public interface RenameTaskMapper 
{
    /**
     * 查询重命名任务配置
     * 
     * @param id 重命名任务配置主键
     * @return 重命名任务配置
     */
    public RenameTask selectRenameTaskById(Integer id);

    /**
     * 查询重命名任务配置列表
     * 
     * @param renameTask 重命名任务配置
     * @return 重命名任务配置集合
     */
    public List<RenameTask> selectRenameTaskList(RenameTask renameTask);

    /**
     * 新增重命名任务配置
     * 
     * @param renameTask 重命名任务配置
     * @return 结果
     */
    public int insertRenameTask(RenameTask renameTask);

    /**
     * 修改重命名任务配置
     * 
     * @param renameTask 重命名任务配置
     * @return 结果
     */
    public int updateRenameTask(RenameTask renameTask);

    /**
     * 删除重命名任务配置
     * 
     * @param id 重命名任务配置主键
     * @return 结果
     */
    public int deleteRenameTaskById(Integer id);

    /**
     * 批量删除重命名任务配置
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteRenameTaskByIds(String[] ids);
}
