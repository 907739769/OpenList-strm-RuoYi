package com.ruoyi.openliststrm.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * strm生成对象 openlist_strm
 * 
 * @author Jack
 * @date 2025-07-16
 */
public class OpenlistStrm extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Integer strmId;

    /** strm目录 */
    @Excel(name = "strm目录")
    private String strmPath;

    /** strm文件名称 */
    @Excel(name = "strm文件名称")
    private String strmFileName;

    /** 状态0-失败1-成功 */
    @Excel(name = "状态0-失败1-成功")
    private String strmStatus;

    public void setStrmId(Integer strmId) 
    {
        this.strmId = strmId;
    }

    public Integer getStrmId() 
    {
        return strmId;
    }

    public void setStrmPath(String strmPath) 
    {
        this.strmPath = strmPath;
    }

    public String getStrmPath() 
    {
        return strmPath;
    }

    public void setStrmFileName(String strmFileName) 
    {
        this.strmFileName = strmFileName;
    }

    public String getStrmFileName() 
    {
        return strmFileName;
    }

    public void setStrmStatus(String strmStatus) 
    {
        this.strmStatus = strmStatus;
    }

    public String getStrmStatus() 
    {
        return strmStatus;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("strmId", getStrmId())
            .append("strmPath", getStrmPath())
            .append("strmFileName", getStrmFileName())
            .append("strmStatus", getStrmStatus())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
