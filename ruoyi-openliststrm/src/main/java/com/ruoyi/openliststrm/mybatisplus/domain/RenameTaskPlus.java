package com.ruoyi.openliststrm.mybatisplus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatisplus.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 重命名任务配置
 * </p>
 *
 * @author Jack
 * @since 2025-10-10
 */
@Getter
@Setter
@TableName("rename_task")
public class RenameTaskPlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 自增主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 源目录
     */
    @TableField("source_folder")
    private String sourceFolder;

    /**
     * 目标目录
     */
    @TableField("target_root")
    private String targetRoot;

    /**
     * 状态
     */
    @TableField("status")
    private String status;
}
