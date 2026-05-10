package com.ruoyi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

import java.net.InetAddress;

/**
 * 启动程序
 *
 * @author ruoyi
 */
@SpringBootApplication(excludeName = {
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
    "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
    "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration",
    "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration"
})
@EnableCaching
@ComponentScan(basePackages = {"com.ruoyi", "com.ruoyi.web", "com.ruoyi.framework"})
public class RuoYiApplication {
    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();
        ConfigurableApplicationContext context = SpringApplication.run(RuoYiApplication.class, args);
        long endTime = System.currentTimeMillis();
        long cost = (endTime - startTime) / 1000;

        Environment env = context.getEnvironment();

        String appName = env.getProperty("spring.application.name", "application");
        String port = env.getProperty("server.port", "8080");
        String protocol = env.getProperty("server.ssl.key-store") == null ? "http" : "https";

        String hostAddress = InetAddress.getLocalHost().getHostAddress();

        String localUrl = protocol + "://localhost:" + port;
        String remoteUrl = protocol + "://" + hostAddress + ":" + port;

        // 启动完成后展示面板
        System.out.println("\n" +
                "╔════════════════════════════════════════════════════╗\n" +
                "║   🚀 OSR 系统启动成功！                             ║\n" +
                "╠════════════════════════════════════════════════════╣\n" +
                "║   🌐 本地访问：" + padRight(localUrl, 34) + "  ║\n" +
                "║   🖥️  内网访问：" + padRight(remoteUrl, 34) + " ║\n" +
                "║   ⏱  启动耗时：" + padRight(cost + " 秒", 34) + " ║\n" +
                "╚════════════════════════════════════════════════════╝\n"
        );
        System.out.println("(♥◠‿◠)ﾉﾞ  OSR启动成功！欢迎使用   ლ(´ڡ`ლ)ﾞ  \n" +
                " _______  _______  _______ \n" +
                "(  ___  )(  ____ \\(  ____ )\n" +
                "| (   ) || (    \\/| (    )|\n" +
                "| |   | || (_____ | (____)|\n" +
                "| |   | |(_____  )|     __)\n" +
                "| |   | |      ) || (\\ (   \n" +
                "| (___) |/\\____) || ) \\ \\__\n" +
                "(_______)\\_______)|/   \\__/\n");
    }

    private static String padRight(String str, int length) {
        if (str == null) {
            str = "";
        }
        if (str.length() >= length) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < length) {
            sb.append(" ");
        }
        return sb.toString();
    }
}