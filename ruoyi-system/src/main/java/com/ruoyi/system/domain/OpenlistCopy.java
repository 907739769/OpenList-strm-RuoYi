package com.ruoyi.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * openlist的文件同步复制任务对象 openlist_copy
 * 
 * @author Jack
 * @date 2025-07-15
 */
public class OpenlistCopy extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Integer copyId;

    /** 源目录 */
    @Excel(name = "源目录")
    private String copySrcPath;

    /** 目标目录 */
    @Excel(name = "目标目录")
    private String copyDstPath;

    /** 源文件名称 */
    @Excel(name = "源文件名称")
    private String copySrcFileName;

    /** 目标文件名称 */
    @Excel(name = "目标文件名称")
    private String copyDstFileName;

    /** openlist的复制任务ID */
    @Excel(name = "openlist的复制任务ID")
    private String copyTaskId;

    /** 复制状态1-处理中2-失败3-成功4-未知 */
    @Excel(name = "复制状态1-处理中2-失败3-成功4-未知")
    private String copyStatus;

    public void setCopyId(Integer copyId) 
    {
        this.copyId = copyId;
    }

    public Integer getCopyId() 
    {
        return copyId;
    }

    public void setCopySrcPath(String copySrcPath) 
    {
        this.copySrcPath = copySrcPath;
    }

    public String getCopySrcPath() 
    {
        return copySrcPath;
    }

    public void setCopyDstPath(String copyDstPath) 
    {
        this.copyDstPath = copyDstPath;
    }

    public String getCopyDstPath() 
    {
        return copyDstPath;
    }

    public void setCopySrcFileName(String copySrcFileName) 
    {
        this.copySrcFileName = copySrcFileName;
    }

    public String getCopySrcFileName() 
    {
        return copySrcFileName;
    }

    public void setCopyDstFileName(String copyDstFileName) 
    {
        this.copyDstFileName = copyDstFileName;
    }

    public String getCopyDstFileName() 
    {
        return copyDstFileName;
    }

    public void setCopyTaskId(String copyTaskId) 
    {
        this.copyTaskId = copyTaskId;
    }

    public String getCopyTaskId() 
    {
        return copyTaskId;
    }

    public void setCopyStatus(String copyStatus) 
    {
        this.copyStatus = copyStatus;
    }

    public String getCopyStatus() 
    {
        return copyStatus;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("copyId", getCopyId())
            .append("copySrcPath", getCopySrcPath())
            .append("copyDstPath", getCopyDstPath())
            .append("copySrcFileName", getCopySrcFileName())
            .append("copyDstFileName", getCopyDstFileName())
            .append("copyTaskId", getCopyTaskId())
            .append("copyStatus", getCopyStatus())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
