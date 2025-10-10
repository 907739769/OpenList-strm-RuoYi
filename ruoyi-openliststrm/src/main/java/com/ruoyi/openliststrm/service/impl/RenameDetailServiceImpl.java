package com.ruoyi.openliststrm.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.openliststrm.mapper.RenameDetailMapper;
import com.ruoyi.openliststrm.domain.RenameDetail;
import com.ruoyi.openliststrm.service.IRenameDetailService;
import com.ruoyi.common.core.text.Convert;

/**
 * 重命名明细Service业务层处理
 * 
 * @author Jack
 * @date 2025-09-30
 */
@Service
public class RenameDetailServiceImpl implements IRenameDetailService 
{
    @Autowired
    private RenameDetailMapper renameDetailMapper;

    /**
     * 查询重命名明细
     * 
     * @param id 重命名明细主键
     * @return 重命名明细
     */
    @Override
    public RenameDetail selectRenameDetailById(Integer id)
    {
        return renameDetailMapper.selectRenameDetailById(id);
    }

    /**
     * 查询重命名明细列表
     * 
     * @param renameDetail 重命名明细
     * @return 重命名明细
     */
    @Override
    public List<RenameDetail> selectRenameDetailList(RenameDetail renameDetail)
    {
        return renameDetailMapper.selectRenameDetailList(renameDetail);
    }

    /**
     * 新增重命名明细
     * 
     * @param renameDetail 重命名明细
     * @return 结果
     */
    @Override
    public int insertRenameDetail(RenameDetail renameDetail)
    {
        renameDetail.setCreateTime(DateUtils.getNowDate());
        return renameDetailMapper.insertRenameDetail(renameDetail);
    }

    /**
     * 修改重命名明细
     * 
     * @param renameDetail 重命名明细
     * @return 结果
     */
    @Override
    public int updateRenameDetail(RenameDetail renameDetail)
    {
        renameDetail.setUpdateTime(DateUtils.getNowDate());
        return renameDetailMapper.updateRenameDetail(renameDetail);
    }

    /**
     * 批量删除重命名明细
     * 
     * @param ids 需要删除的重命名明细主键
     * @return 结果
     */
    @Override
    public int deleteRenameDetailByIds(String ids)
    {
        return renameDetailMapper.deleteRenameDetailByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除重命名明细信息
     * 
     * @param id 重命名明细主键
     * @return 结果
     */
    @Override
    public int deleteRenameDetailById(Integer id)
    {
        return renameDetailMapper.deleteRenameDetailById(id);
    }
}
