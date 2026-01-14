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
                "sql/init.sql",
                "sql/20250724.sql",
                "sql/20251010.sql",
                "sql/20260107.sql",
                "sql/20260114.sql"
        );
    }
}
