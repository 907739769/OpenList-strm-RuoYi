package com.ruoyi.web.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI springDocOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("OpenList-Strm-RuoYi API")
                .description("若依管理系统接口文档")
                .version("v4.8.1"));
    }
}
