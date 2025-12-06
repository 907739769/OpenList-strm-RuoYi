package com.ruoyi.openliststrm.service;

import java.util.List;
import com.ruoyi.openliststrm.domain.RenameDetail;

/**
 * 重命名明细Service接口
 * 
 * @author Jack
 * @date 2025-09-30
 */
public interface IRenameDetailService 
{
    /**
     * 查询重命名明细
     * 
     * @param id 重命名明细主键
     * @return 重命名明细
     */
    public RenameDetail selectRenameDetailById(Integer id);

    /**
     * 查询重命名明细列表
     * 
     * @param renameDetail 重命名明细
     * @return 重命名明细集合
     */
    public List<RenameDetail> selectRenameDetailList(RenameDetail renameDetail);

    /**
     * 新增重命名明细
     * 
     * @param renameDetail 重命名明细
     * @return 结果
     */
    public int insertRenameDetail(RenameDetail renameDetail);

    /**
     * 修改重命名明细
     * 
     * @param renameDetail 重命名明细
     * @return 结果
     */
    public int updateRenameDetail(RenameDetail renameDetail);

    /**
     * 批量删除重命名明细
     * 
     * @param ids 需要删除的重命名明细主键集合
     * @return 结果
     */
    public int deleteRenameDetailByIds(String ids);

    /**
     * 删除重命名明细信息
     * 
     * @param id 重命名明细主键
     * @return 结果
     */
    public int deleteRenameDetailById(Integer id);
}
