package com.ruoyi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

/**
 * 启动程序
 *
 * @author ruoyi
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableCaching
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

    /**
     * 监听 Spring Boot 启动完成事件
     */
    @Component
    public static class TemplatePreheater {

        @EventListener(ApplicationReadyEvent.class)
        public void onApplicationReady(ApplicationReadyEvent event) {
            ConfigurableApplicationContext context = event.getApplicationContext();
            String port = context.getEnvironment().getProperty("server.port", "8080");
            // 模拟调用登录页
            try {
                for (int i = 0; i < 3; i++) {
                    List<String> pages = Arrays.asList("/", "/login");
                    for (String page : pages) {
                        new RestTemplate().getForObject("http://localhost:" + port + page, String.class);
                    }
                }
            } catch (Exception ignored) {
            }
        }
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