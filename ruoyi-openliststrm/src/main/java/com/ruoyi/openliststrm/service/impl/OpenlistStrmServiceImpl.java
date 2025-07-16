package com.ruoyi.openliststrm.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.openliststrm.mapper.OpenlistStrmMapper;
import com.ruoyi.openliststrm.domain.OpenlistStrm;
import com.ruoyi.openliststrm.service.IOpenlistStrmService;
import com.ruoyi.common.core.text.Convert;

/**
 * strm生成Service业务层处理
 * 
 * @author Jack
 * @date 2025-07-16
 */
@Service
public class OpenlistStrmServiceImpl implements IOpenlistStrmService 
{
    @Autowired
    private OpenlistStrmMapper openlistStrmMapper;

    /**
     * 查询strm生成
     * 
     * @param strmId strm生成主键
     * @return strm生成
     */
    @Override
    public OpenlistStrm selectOpenlistStrmByStrmId(Integer strmId)
    {
        return openlistStrmMapper.selectOpenlistStrmByStrmId(strmId);
    }

    /**
     * 查询strm生成列表
     * 
     * @param openlistStrm strm生成
     * @return strm生成
     */
    @Override
    public List<OpenlistStrm> selectOpenlistStrmList(OpenlistStrm openlistStrm)
    {
        return openlistStrmMapper.selectOpenlistStrmList(openlistStrm);
    }

    /**
     * 新增strm生成
     * 
     * @param openlistStrm strm生成
     * @return 结果
     */
    @Override
    public int insertOpenlistStrm(OpenlistStrm openlistStrm)
    {
        openlistStrm.setCreateTime(DateUtils.getNowDate());
        return openlistStrmMapper.insertOpenlistStrm(openlistStrm);
    }

    /**
     * 修改strm生成
     * 
     * @param openlistStrm strm生成
     * @return 结果
     */
    @Override
    public int updateOpenlistStrm(OpenlistStrm openlistStrm)
    {
        openlistStrm.setUpdateTime(DateUtils.getNowDate());
        return openlistStrmMapper.updateOpenlistStrm(openlistStrm);
    }

    /**
     * 批量删除strm生成
     * 
     * @param strmIds 需要删除的strm生成主键
     * @return 结果
     */
    @Override
    public int deleteOpenlistStrmByStrmIds(String strmIds)
    {
        return openlistStrmMapper.deleteOpenlistStrmByStrmIds(Convert.toStrArray(strmIds));
    }

    /**
     * 删除strm生成信息
     * 
     * @param strmId strm生成主键
     * @return 结果
     */
    @Override
    public int deleteOpenlistStrmByStrmId(Integer strmId)
    {
        return openlistStrmMapper.deleteOpenlistStrmByStrmId(strmId);
    }
}
