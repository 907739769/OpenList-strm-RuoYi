package com.ruoyi.openliststrm.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 文件同步任务对象 openlist_copy_task
 * 
 * @author Jack
 * @date 2025-07-17
 */
public class OpenlistCopyTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Integer copyTaskId;

    /** 源目录 */
    @Excel(name = "源目录")
    private String copyTaskSrc;

    /** 目标目录 */
    @Excel(name = "目标目录")
    private String copyTaskDst;

    /** 状态0-停用1-启用 */
    @Excel(name = "状态0-停用1-启用")
    private String copyTaskStatus;

    public void setCopyTaskId(Integer copyTaskId) 
    {
        this.copyTaskId = copyTaskId;
    }

    public Integer getCopyTaskId() 
    {
        return copyTaskId;
    }

    public void setCopyTaskSrc(String copyTaskSrc) 
    {
        this.copyTaskSrc = copyTaskSrc;
    }

    public String getCopyTaskSrc() 
    {
        return copyTaskSrc;
    }

    public void setCopyTaskDst(String copyTaskDst) 
    {
        this.copyTaskDst = copyTaskDst;
    }

    public String getCopyTaskDst() 
    {
        return copyTaskDst;
    }

    public void setCopyTaskStatus(String copyTaskStatus) 
    {
        this.copyTaskStatus = copyTaskStatus;
    }

    public String getCopyTaskStatus() 
    {
        return copyTaskStatus;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("copyTaskId", getCopyTaskId())
            .append("copyTaskSrc", getCopyTaskSrc())
            .append("copyTaskDst", getCopyTaskDst())
            .append("copyTaskStatus", getCopyTaskStatus())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
