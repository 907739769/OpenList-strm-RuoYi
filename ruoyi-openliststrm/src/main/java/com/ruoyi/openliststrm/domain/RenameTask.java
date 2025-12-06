package com.ruoyi.openliststrm.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 重命名任务配置对象 rename_task
 * 
 * @author Jack
 * @date 2025-09-30
 */
public class RenameTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Integer id;

    /** 源目录 */
    @Excel(name = "源目录")
    private String sourceFolder;

    /** 目标目录 */
    @Excel(name = "目标目录")
    private String targetRoot;

    /** 状态 */
    @Excel(name = "状态")
    private String status;

    public void setId(Integer id) 
    {
        this.id = id;
    }

    public Integer getId() 
    {
        return id;
    }

    public void setSourceFolder(String sourceFolder) 
    {
        this.sourceFolder = sourceFolder;
    }

    public String getSourceFolder() 
    {
        return sourceFolder;
    }

    public void setTargetRoot(String targetRoot) 
    {
        this.targetRoot = targetRoot;
    }

    public String getTargetRoot() 
    {
        return targetRoot;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("sourceFolder", getSourceFolder())
            .append("targetRoot", getTargetRoot())
            .append("status", getStatus())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
