package com.ruoyi.openliststrm.service;

import java.util.List;
import com.ruoyi.openliststrm.domain.OpenlistStrm;

/**
 * strm生成Service接口
 * 
 * @author Jack
 * @date 2025-07-16
 */
public interface IOpenlistStrmService 
{
    /**
     * 查询strm生成
     * 
     * @param strmId strm生成主键
     * @return strm生成
     */
    public OpenlistStrm selectOpenlistStrmByStrmId(Integer strmId);

    /**
     * 查询strm生成列表
     * 
     * @param openlistStrm strm生成
     * @return strm生成集合
     */
    public List<OpenlistStrm> selectOpenlistStrmList(OpenlistStrm openlistStrm);

    /**
     * 新增strm生成
     * 
     * @param openlistStrm strm生成
     * @return 结果
     */
    public int insertOpenlistStrm(OpenlistStrm openlistStrm);

    /**
     * 修改strm生成
     * 
     * @param openlistStrm strm生成
     * @return 结果
     */
    public int updateOpenlistStrm(OpenlistStrm openlistStrm);

    /**
     * 批量删除strm生成
     * 
     * @param strmIds 需要删除的strm生成主键集合
     * @return 结果
     */
    public int deleteOpenlistStrmByStrmIds(String strmIds);

    /**
     * 删除strm生成信息
     * 
     * @param strmId strm生成主键
     * @return 结果
     */
    public int deleteOpenlistStrmByStrmId(Integer strmId);
}
