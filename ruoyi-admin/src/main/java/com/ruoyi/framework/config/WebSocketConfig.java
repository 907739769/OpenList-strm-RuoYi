package com.ruoyi.framework.config;

import com.ruoyi.web.controller.monitor.LogWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfig {
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        log.info(">>> ServerEndpointExporter bean created");
        return new ServerEndpointExporter();
    }

    @Bean
    public LogWebSocket logWebSocket() {
        log.info(">>> LogWebSocket bean registered");
        return new LogWebSocket();
    }
}
