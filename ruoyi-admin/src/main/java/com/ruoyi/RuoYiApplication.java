package com.ruoyi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 启动程序
 * 
 * @author ruoyi
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableCaching
public class RuoYiApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(RuoYiApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OSR启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                " _______  _______  _______ \n" +
                "(  ___  )(  ____ \\(  ____ )\n" +
                "| (   ) || (    \\/| (    )|\n" +
                "| |   | || (_____ | (____)|\n" +
                "| |   | |(_____  )|     __)\n" +
                "| |   | |      ) || (\\ (   \n" +
                "| (___) |/\\____) || ) \\ \\__\n" +
                "(_______)\\_______)|/   \\__/\n");
    }
}