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
                // 测试存储过程
                "sql/init.sql"
        );
    }
}
