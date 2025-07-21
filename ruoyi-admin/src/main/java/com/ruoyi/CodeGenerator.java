package com.ruoyi;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.ruoyi.common.mybatisplus.BaseEntity;

import java.util.Collections;

/**
 * @Author Jack
 * @Date 2025/7/5 16:47
 * @Version 1.0.0
 */
public class CodeGenerator {

    public static void main(String[] args) {

        //需要生成代码的表名
        String tableName = "openlist_strm";
        String url = "jdbc:mysql://xxxxx/osr?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8";
        String password = "xxx";

        String projectPath = System.getProperty("user.dir");
        FastAutoGenerator.create(url, "root", password).globalConfig(builder -> {
                    builder.author("Jack") // 设置作者
                            .outputDir(projectPath + "/ruoyi-openliststrm/src/main/java"); // 指定输出目录
                }).packageConfig(builder -> builder.parent("com.ruoyi.openliststrm") // 设置父包名
                        .moduleName("mybatisplus") // 设置父包模块名
                        .entity("domain")
                        .pathInfo(Collections.singletonMap(OutputFile.xml, projectPath + "/ruoyi-openliststrm/src/main/resources/mapper/mybatisplus")) // 设置mapperXml生成路径
                ).strategyConfig(builder -> builder.addInclude(tableName) // 设置需要生成的表名
                        .entityBuilder()
                        .enableLombok()
                        .enableTableFieldAnnotation()
                        .addSuperEntityColumns("create_time", "update_time")
                        .superClass(BaseEntity.class)
                ).templateEngine(new FreemarkerTemplateEngine())// 使用Freemarker引擎模板，默认的是Velocity引擎模板
                .templateConfig(builder -> builder.controller(""))
                .execute();
    }

}
