package com.ruoyi.web.controller.monitor;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实时日志监控 WebSocket
 * 优化：增加移动端适配，智能隐藏元数据
 */
@ServerEndpoint("/websocket/log/{logType}")
@Component
public class LogWebSocket {

    private static final Logger log = LoggerFactory.getLogger(LogWebSocket.class);
    private static final String LOG_BASE_PATH = "/data/logs";

    // 正则表达式：匹配若依默认日志格式
    // 格式: [日期 时间][TraceId][级别][Logger] 消息
    // Group 1: 时间 [2025-01-01 10:00:00.123]
    // Group 2: 中间元数据 [trace][INFO ][logger]
    // Group 3: 剩余消息内容
    private static final Pattern LOG_PATTERN = Pattern.compile("^(\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\])(\\[.*?\\]\\[.*?\\]\\[.*?\\])(.*)");

    private ExecutorService executorService;
    private Tailer tailer;

    @OnOpen
    public void onOpen(Session session, @PathParam("logType") String logType) {
        try {
            String fileName = "sys-info.log";
            if ("debug".equalsIgnoreCase(logType)) fileName = "sys-debug.log";
            else if ("error".equalsIgnoreCase(logType)) fileName = "sys-error.log";

            File file = new File(LOG_BASE_PATH, fileName);
            if (!file.exists()) {
                session.getBasicRemote().sendText("<div class='log-item log-error'>文件不存在: " + file.getAbsolutePath() + "</div>");
                return;
            }

            // 发送历史日志
            sendHistoryLogs(session, file, 200);

            // 监听新日志
            executorService = Executors.newSingleThreadExecutor();
            executorService.submit(() -> {
                tailer = new Tailer(file, new TailerListenerAdapter() {
                    @Override
                    public void handle(String line) {
                        try {
                            if (session.isOpen()) {
                                session.getBasicRemote().sendText(formatLogLine(line));
                            }
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }, 500, true);
                tailer.run();
            });

        } catch (Exception e) {
            log.error("WebSocket启动失败", e);
        }
    }

    private void sendHistoryLogs(Session session, File file, int linesToRead) {
        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(file, StandardCharsets.UTF_8)) {
            List<String> messages = new ArrayList<>();
            String line;
            int counter = 0;
            while ((line = reader.readLine()) != null && counter < linesToRead) {
                messages.add(formatLogLine(line));
                counter++;
            }
            Collections.reverse(messages);
            messages.add("<div class='log-item' style='color: #888; border-bottom: 1px dashed #555; margin: 10px 0;'>--- 历史日志结束 (History End) ---</div>");
            for (String msg : messages) {
                session.getBasicRemote().sendText(msg);
            }
        } catch (IOException e) {
            log.error("读取历史日志失败", e);
        }
    }

    @OnClose
    public void onClose() { stopTailer(); }

    @OnError
    public void onError(Session session, Throwable error) { stopTailer(); }

    private void stopTailer() {
        if (tailer != null) tailer.stop();
        if (executorService != null) executorService.shutdownNow();
    }

    /**
     * 核心格式化方法：
     * 1. HTML转义
     * 2. 颜色高亮
     * 3. 结构化标记 (用于移动端隐藏)
     */
    private String formatLogLine(String line) {
        if (line == null) return "";

        // 1. 转义 HTML (防止日志里的 <script> 被执行)
        String content = line.replace("<", "&lt;").replace(">", "&gt;");

        // 2. 识别日志级别颜色
        String cssClass = "log-info";
        if (content.contains("ERROR")) cssClass = "log-error";
        else if (content.contains("WARN")) cssClass = "log-warn";
        else if (content.contains("DEBUG")) cssClass = "log-debug";

        // 3. 移动端适配处理：正则提取 [Trace][Level][Logger] 并包裹 hidden-xs 类
        Matcher matcher = LOG_PATTERN.matcher(content);
        if (matcher.find()) {
            // Group 1: 时间
            // Group 2: 元数据 -> 添加 class='hidden-xs' (Bootstrap类，手机端隐藏)
            // Group 3: 消息
            content = matcher.replaceFirst("$1<span class='hidden-xs'>$2</span>$3");
        }

        return "<div class='log-item " + cssClass + "'>" + content + "</div>";
    }
}