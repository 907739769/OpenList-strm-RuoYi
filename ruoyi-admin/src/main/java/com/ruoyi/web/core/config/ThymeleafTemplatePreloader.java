package com.ruoyi.web.core.config;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 启动时候预渲染缓存模板
 *
 * @Author Jack
 * @Date 2025/8/8 9:20
 * @Version 1.0.0
 */
@Component
public class ThymeleafTemplatePreloader {

    private final SpringTemplateEngine templateEngine;
    private final ServletContext servletContext;

    public ThymeleafTemplatePreloader(SpringTemplateEngine templateEngine, ServletContext servletContext) {
        this.templateEngine = templateEngine;
        this.servletContext = servletContext;
    }

    @PostConstruct
    public void preloadTemplates() {
        try {
            HttpServletRequest request = new MockHttpServletRequest();
            HttpServletResponse response = new MockHttpServletResponse();
            WebContext webContext = new WebContext(request, response, servletContext);
            templateEngine.process("login", webContext);
            templateEngine.process("main_v2", webContext);
        } catch (Exception ignored) {
        }

    }
}