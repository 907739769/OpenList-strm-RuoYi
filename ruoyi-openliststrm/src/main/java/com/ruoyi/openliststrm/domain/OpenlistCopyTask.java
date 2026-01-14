package com.ruoyi.openliststrm.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件同步任务对象 openlist_copy_task
 * 
 * @author Jack
 * @date 2025-07-17
 */
@EqualsAndHashCode(callSuper = true)
@Data
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

    /** 监控目录 */
    @Excel(name = "监控目录")
    private String monitorDir;

    /** 状态0-停用1-启用 */
    @Excel(name = "状态0-停用1-启用")
    private String copyTaskStatus;

}
