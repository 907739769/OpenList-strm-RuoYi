package com.ruoyi.openliststrm.rename;

import com.ruoyi.openliststrm.openai.OpenAIClient;
import com.ruoyi.openliststrm.tmdb.TMDbClient;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;

/**
 * 简单 runner，用于手动启动监控（非 Spring）。
 * Usage:
 * java MonitorRunner [sourceDir] [targetDir] [minFileSizeBytes] [filenameTemplate]
 * or set env: MONITOR_SOURCE, MONITOR_TARGET, TMDB_API_KEY, MIN_FILE_SIZE, FILENAME_TEMPLATE, OPENAI_API_KEY
 */
@Slf4j
public class MonitorRunner {
    public static void main(String[] args) throws Exception {
        String src = args.length > 0 ? args[0] : System.getenv().getOrDefault("MONITOR_SOURCE", "C:/tmp/watch/src");
        String tgt = args.length > 1 ? args[1] : System.getenv().getOrDefault("MONITOR_TARGET", "C:/tmp/watch/target");
        String tmdbKey = System.getenv().getOrDefault("TMDB_API_KEY", "");
        String openaiKey = System.getenv().getOrDefault("OPENAI_API_KEY", "");
        String openaiEndpoint = System.getenv().getOrDefault("OPENAI_ENDPOINT", "");
        String openaiModel = System.getenv().getOrDefault("OPENAI_MODEL", "");
        long minFileSize = 10 * 1024 * 1024; // default 10MB
        if (args.length > 2) {
            try { minFileSize = Long.parseLong(args[2]); } catch (Exception ignored) {}
        } else if (System.getenv().containsKey("MIN_FILE_SIZE")) {
            try { minFileSize = Long.parseLong(System.getenv().get("MIN_FILE_SIZE")); } catch (Exception ignored) {}
        }

        // filename template from args[3] or env FILENAME_TEMPLATE
        String defaultTemplate = "{{ title }} {% if year %} ({{ year }}) {% endif %}/{% if season %}Season {{ season }}/{% endif %}{{ title }} {% if year and not season %} ({{ year }}) {% endif %}{% if season %}S{{ season }}{% endif %}{% if episode %}E{{ episode }}{% endif %}{% if resolution %} - {{ resolution }}{% endif %}{% if source %}.{{ source }}{% endif %}{% if videoCodec %}.{{ videoCodec }}{% endif %}{% if audioCodec %}.{{ audioCodec }}{% endif %}{% if tags is not empty %}.{{ tags|join('.') }}{% endif %}{% if releaseGroup %}-{{ releaseGroup }}{% endif %}.{{ extension }}";
        String template = args.length > 3 ? args[3] : System.getenv().getOrDefault("FILENAME_TEMPLATE", defaultTemplate);

        TMDbClient tmdb = (tmdbKey == null || tmdbKey.isEmpty()) ? null : new TMDbClient(tmdbKey);
        OpenAIClient openai = null;
        if (openaiKey != null && !openaiKey.isEmpty()) {
            String modelToPass = (openaiModel == null || openaiModel.isEmpty()) ? null : openaiModel;
            openai = new OpenAIClient(openaiKey, openaiEndpoint, modelToPass);
        }

        FileMonitorService svc = new FileMonitorService(Paths.get(src), Paths.get(tgt), tmdb, openai, minFileSize, template);
        svc.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down monitor");
            svc.stop();
        }));

        log.info("Monitor started. source={} target={} minSize={} template={} openAI={} tmdb={}", src, tgt, minFileSize, template, openai != null, tmdb != null);
        // keep main thread alive
        Thread.currentThread().join();
    }
}
