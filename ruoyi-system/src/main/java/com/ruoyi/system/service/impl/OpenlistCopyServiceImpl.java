package com.ruoyi.system.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.mapper.OpenlistCopyMapper;
import com.ruoyi.system.domain.OpenlistCopy;
import com.ruoyi.system.service.IOpenlistCopyService;
import com.ruoyi.common.core.text.Convert;

/**
 * openlist的文件同步复制任务Service业务层处理
 * 
 * @author Jack
 * @date 2025-07-15
 */
@Service
public class OpenlistCopyServiceImpl implements IOpenlistCopyService 
{
    @Autowired
    private OpenlistCopyMapper openlistCopyMapper;

    /**
     * 查询openlist的文件同步复制任务
     * 
     * @param copyId openlist的文件同步复制任务主键
     * @return openlist的文件同步复制任务
     */
    @Override
    public OpenlistCopy selectOpenlistCopyByCopyId(Integer copyId)
    {
        return openlistCopyMapper.selectOpenlistCopyByCopyId(copyId);
    }

    /**
     * 查询openlist的文件同步复制任务列表
     * 
     * @param openlistCopy openlist的文件同步复制任务
     * @return openlist的文件同步复制任务
     */
    @Override
    public List<OpenlistCopy> selectOpenlistCopyList(OpenlistCopy openlistCopy)
    {
        return openlistCopyMapper.selectOpenlistCopyList(openlistCopy);
    }

    /**
     * 新增openlist的文件同步复制任务
     * 
     * @param openlistCopy openlist的文件同步复制任务
     * @return 结果
     */
    @Override
    public int insertOpenlistCopy(OpenlistCopy openlistCopy)
    {
        openlistCopy.setCreateTime(DateUtils.getNowDate());
        return openlistCopyMapper.insertOpenlistCopy(openlistCopy);
    }

    /**
     * 修改openlist的文件同步复制任务
     * 
     * @param openlistCopy openlist的文件同步复制任务
     * @return 结果
     */
    @Override
    public int updateOpenlistCopy(OpenlistCopy openlistCopy)
    {
        openlistCopy.setUpdateTime(DateUtils.getNowDate());
        return openlistCopyMapper.updateOpenlistCopy(openlistCopy);
    }

    /**
     * 批量删除openlist的文件同步复制任务
     * 
     * @param copyIds 需要删除的openlist的文件同步复制任务主键
     * @return 结果
     */
    @Override
    public int deleteOpenlistCopyByCopyIds(String copyIds)
    {
        return openlistCopyMapper.deleteOpenlistCopyByCopyIds(Convert.toStrArray(copyIds));
    }

    /**
     * 删除openlist的文件同步复制任务信息
     * 
     * @param copyId openlist的文件同步复制任务主键
     * @return 结果
     */
    @Override
    public int deleteOpenlistCopyByCopyId(Integer copyId)
    {
        return openlistCopyMapper.deleteOpenlistCopyByCopyId(copyId);
    }
}
