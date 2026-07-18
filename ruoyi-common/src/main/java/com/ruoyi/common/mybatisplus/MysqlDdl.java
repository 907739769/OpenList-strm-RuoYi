package com.ruoyi.common.mybatisplus;

import com.baomidou.mybatisplus.extension.ddl.SimpleDdl;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * @Author Jack
 * @Date 2025/7/19 9:57
 * @Version 1.0.0
 */
@Component("mysqlDdl")
public class MysqlDdl extends SimpleDdl {

    /**
     * 执行 SQL 脚本方式
     */
    @Override
    public List<String> getSqlFiles() {
        return Arrays.asList(
                "sql/schema.sql",
                "sql/data.sql",
                "sql/init.sql",
                "sql/20250724.sql",
                "sql/20251010.sql",
                "sql/20260107.sql",
                "sql/20260114.sql",
                "sql/20260203.sql",
                "sql/20260207.sql",
                "sql/20260428-menu-icons.sql",
                "sql/20260510-cleanup-unused-tables.sql",
                "sql/20260511-cleanup-unused-dicts.sql",
                "sql/20260514-fix-password-column-length.sql",
                "sql/20260626-expand-release-group.sql",
                "sql/20260626-delindex.sql",
                "sql/20260711-add-scrape-fields.sql",
                "sql/20260716-add-indexes.sql",
                "sql/20260716-tmdb-cache-incremental-sync.sql",
                "sql/20260718-add-scrape-force-overwrite.sql",
                "sql/20260718-add-openlist-configs.sql"
        );
    }
}
