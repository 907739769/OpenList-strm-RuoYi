package com.ruoyi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
    public static void main(String[] args) {
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

}