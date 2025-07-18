package com.ruoyi.openliststrm.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * strm任务配置对象 openlist_strm_task
 * 
 * @author Jack
 * @date 2025-07-18
 */
public class OpenlistStrmTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long strmTaskId;

    /** strm目录 */
    @Excel(name = "strm目录")
    private String strmTaskPath;

    /** 状态0-停用1-启用 */
    @Excel(name = "状态0-停用1-启用")
    private String strmTaskStatus;

    public void setStrmTaskId(Long strmTaskId) 
    {
        this.strmTaskId = strmTaskId;
    }

    public Long getStrmTaskId() 
    {
        return strmTaskId;
    }

    public void setStrmTaskPath(String strmTaskPath) 
    {
        this.strmTaskPath = strmTaskPath;
    }

    public String getStrmTaskPath() 
    {
        return strmTaskPath;
    }

    public void setStrmTaskStatus(String strmTaskStatus) 
    {
        this.strmTaskStatus = strmTaskStatus;
    }

    public String getStrmTaskStatus() 
    {
        return strmTaskStatus;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("strmTaskId", getStrmTaskId())
            .append("strmTaskPath", getStrmTaskPath())
            .append("strmTaskStatus", getStrmTaskStatus())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
