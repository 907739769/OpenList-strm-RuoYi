package com.ruoyi.openliststrm.rename;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameTaskPlusService;
import com.ruoyi.openliststrm.openai.OpenAIClient;
import com.ruoyi.openliststrm.tmdb.TMDbClient;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manager that polls rename_task table and keeps FileMonitorService instances running for active tasks.
 *
 * Behavior changes:
 * - TMDb/OpenAI keys are read from `OpenlistConfig` every poll. If TMDb key is missing, tasks will not start.
 * - If TMDb key is removed at runtime, all monitors are stopped until a valid key appears.
 */
@Slf4j
@Component
public class RenameTaskManager {

    @Autowired
    private IRenameTaskPlusService renameTaskService;

    @Autowired
    private IRenameDetailPlusService renameDetailService;

    @Autowired
    private OpenlistConfig openlistConfig;

    @Autowired
    private OpenListHelper openListHelper;

    // active monitors keyed by task id; store service + config so we can detect runtime config changes
    private static class MonitorInfo {
        final FileMonitorService svc;
        final String source;
        final String target;
        MonitorInfo(FileMonitorService svc, String source, String target) {
            this.svc = svc; this.source = source; this.target = target;
        }
    }
    private final Map<Integer, MonitorInfo> monitors = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rename-task-manager");
        t.setDaemon(true);
        return t;
    });

    // current keys and clients (volatile for visibility)
    private volatile String currentTmdbKey = null;
    private volatile String currentOpenAiKey = null;
    private volatile String currentOpenAiEndpoint = null;
    private volatile String currentOpenAiModel = null;
    private volatile TMDbClient tmdbClient = null;
    private volatile OpenAIClient openAIClient = null;

    @PostConstruct
    public void init() {
        // schedule polling every 10 seconds
        scheduler.scheduleWithFixedDelay(this::pollTasks, 0, 10, TimeUnit.SECONDS);
        log.info("RenameTaskManager started polling tasks (dynamic TMDb/OpenAI config)");
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        monitors.values().forEach(mi -> {
            try { mi.svc.stop(); } catch (Exception ignored) {}
        });
        monitors.clear();
        // destroy clients
        tmdbClient = null;
        openAIClient = null;
        log.info("RenameTaskManager stopped");
    }

    private void pollTasks() {
        try {
            // Load keys from DB-config (OpenlistConfig) first, then env as fallback
            String tmdbKey = null;
            String openaiKey = null;
            String openaiEndpoint = null;
            try {
                if (openlistConfig != null) {
                    tmdbKey = openlistConfig.getTmdbApiKey();
                    openaiKey = openlistConfig.getOpenAiApiKey();
                    openaiEndpoint = openlistConfig.getOpenAiEndpoint();
                }
            } catch (Exception e) {
                log.warn("Failed to read keys from OpenlistConfig: {}", e.getMessage());
            }
            if (tmdbKey == null || tmdbKey.isEmpty()) tmdbKey = System.getenv().getOrDefault("TMDB_API_KEY", "");
            if (openaiKey == null || openaiKey.isEmpty()) openaiKey = System.getenv().getOrDefault("OPENAI_API_KEY", "");
            if (openaiEndpoint == null || openaiEndpoint.isEmpty()) openaiEndpoint = System.getenv().getOrDefault("OPENAI_ENDPOINT", "");

            // update TMDb client if key changed
            if (tmdbKey == null || tmdbKey.isEmpty()) {
                if (currentTmdbKey != null) {
                    // key removed: stop all monitors and drop client
                    log.warn("TMDb API key removed or empty. Stopping all monitors until key is configured.");
                    stopAllMonitors();
                    tmdbClient = null;
                    currentTmdbKey = null;
                } else {
                    // no key configured, nothing to do
                    log.debug("No TMDb key configured; skipping task start.");
                }
                return; // do not start tasks without TMDb key
            } else {
                if (!tmdbKey.equals(currentTmdbKey)) {
                    // key is new/changed
                    log.info("TMDb API key changed or set; initializing TMDb client");
                    try {
                        tmdbClient = new TMDbClient(tmdbKey);
                        currentTmdbKey = tmdbKey;
                    } catch (Exception e) {
                        log.warn("Failed to initialize TMDbClient with provided key: {}", e.getMessage());
                        // ensure no monitors run
                        stopAllMonitors();
                        tmdbClient = null;
                        currentTmdbKey = null;
                        return;
                    }
                }
            }

            // update OpenAI client if key changed (optional)
            if (openaiKey == null || openaiKey.isEmpty()) {
                // no key -> clear client
                openAIClient = null;
                currentOpenAiKey = null;
                currentOpenAiEndpoint = null;
                currentOpenAiModel = null;
            } else {
                boolean endpointChanged = (openaiEndpoint == null && currentOpenAiEndpoint != null)
                        || (openaiEndpoint != null && !openaiEndpoint.equals(currentOpenAiEndpoint));
                boolean keyChanged = !openaiKey.equals(currentOpenAiKey);
                // read model from config/env
                String openaiModel = null;
                try {
                    openaiModel = openlistConfig != null ? openlistConfig.getOpenAiModel() : null;
                } catch (Exception ignored) {}
                if (openaiModel == null || openaiModel.isEmpty()) openaiModel = System.getenv().getOrDefault("OPENAI_MODEL", "");
                boolean modelChanged = (openaiModel == null && currentOpenAiModel != null) || (openaiModel != null && !openaiModel.equals(currentOpenAiModel));
                if (keyChanged || endpointChanged || modelChanged || openAIClient == null) {
                    try {
                        // prefer passing null for model if empty so client uses its default
                        String modelToPass = (openaiModel == null || openaiModel.isEmpty()) ? null : openaiModel;
                        openAIClient = new OpenAIClient(openaiKey, openaiEndpoint, modelToPass);
                        currentOpenAiKey = openaiKey;
                        currentOpenAiEndpoint = openaiEndpoint;
                        currentOpenAiModel = modelToPass;
                        log.info("OpenAI client initialized from config (endpoint={}, model={})", openaiEndpoint, modelToPass);
                    } catch (Exception e) {
                        log.warn("Failed to initialize OpenAI client: {}", e.getMessage());
                        openAIClient = null;
                        currentOpenAiKey = null;
                        currentOpenAiEndpoint = null;
                        currentOpenAiModel = null;
                    }
                }
            }

            // At this point tmdbClient is available. Load active tasks and start/restart monitors as needed.
            QueryWrapper<RenameTaskPlus> qw = new QueryWrapper<>();
            qw.eq("status", "1");
            for (RenameTaskPlus t : renameTaskService.list(qw)) {
                if (t == null || t.getId() == null) continue;
                String src = t.getSourceFolder();
                String tgt = t.getTargetRoot();
                MonitorInfo mi = monitors.get(t.getId());
                if (mi == null) {
                    // not running yet -> start
                    startMonitorForTask(t);
                } else {
                    // already running: check if source/target changed -> restart monitor to pick up new paths
                    boolean srcChanged = (src == null && mi.source != null) || (src != null && !src.equals(mi.source));
                    boolean tgtChanged = (tgt == null && mi.target != null) || (tgt != null && !tgt.equals(mi.target));
                    if (srcChanged || tgtChanged) {
                        log.info("Task {} config changed (src/tgt). Restarting monitor. oldSrc={} oldTgt={} newSrc={} newTgt={}", t.getId(), mi.source, mi.target, src, tgt);
                        stopMonitor(t.getId());
                        startMonitorForTask(t);
                    }
                }
            }

            // stop monitors for tasks that are no longer active
            for (Integer id : monitors.keySet()) {
                boolean exists = renameTaskService.getById(id) != null && "1".equals(renameTaskService.getById(id).getStatus());
                if (!exists) {
                    stopMonitor(id);
                }
            }
        } catch (Exception e) {
            log.error("Error polling rename tasks", e);
        }
    }

    private void stopAllMonitors() {
        for (Integer id : monitors.keySet()) {
            stopMonitor(id);
        }
    }

    private void startMonitorForTask(RenameTaskPlus task) {
        try {
            if (tmdbClient == null) {
                log.warn("TMDb client not initialized, skipping start for task {}", task.getId());
                return;
            }
            String src = task.getSourceFolder();
            String tgt = task.getTargetRoot();
            if (src == null || tgt == null) {
                log.warn("Task {} missing source or target, skipping", task.getId());
                return;
            }
            long minSize = 10 * 1024 * 1024L; // default 10MB in bytes
            try {
                String cfg = openlistConfig != null ? openlistConfig.getOpenListMinFileSize() : null;
                if (cfg != null && !cfg.isEmpty()) minSize = Long.parseLong(cfg) * 1024L * 1024L; // cfg treated as MB
            } catch (Exception ignored) {}

            // Create FileMonitorService with listener that persists rename details
            FileMonitorService svc = new FileMonitorService(java.nio.file.Paths.get(src), java.nio.file.Paths.get(tgt), tmdbClient, openAIClient, minSize, null, createPersistingListener(task.getId()), openListHelper);

            svc.start();
            monitors.put(task.getId(), new MonitorInfo(svc, src, tgt));
            log.info("Started monitor for task {}: {} -> {}", task.getId(), src, tgt);
        } catch (Exception e) {
            log.error("Failed to start monitor for task {}", task != null ? task.getId() : null, e);
        }
    }

    private void stopMonitor(Integer id) {
        MonitorInfo mi = monitors.remove(id);
        if (mi != null) {
            try {
                mi.svc.stop();
            } catch (Exception e) {
                log.warn("Error stopping monitor for {}: {}", id, e.getMessage());
            }
            log.info("Stopped monitor for task {}", id);
        }
    }

    /**
     * 立即执行单个任务一次（用于页面手动触发）。
     * 返回 true 表示已成功触发处理（不代表全部文件处理成功）。
     */
    public boolean executeTaskNow(Integer taskId) {
        try {
            if (taskId == null) return false;
            RenameTaskPlus task = renameTaskService.getById(taskId);
            if (task == null) {
                log.warn("executeTaskNow: task {} not found", taskId);
                return false;
            }
            if (currentTmdbKey == null || tmdbClient == null) {
                log.warn("executeTaskNow: TMDb client not initialized - cannot execute task {}", taskId);
                return false;
            }
            String src = task.getSourceFolder();
            String tgt = task.getTargetRoot();
            if (src == null || tgt == null) {
                log.warn("executeTaskNow: task {} missing source/target", taskId);
                return false;
            }
            long minSize = 10 * 1024 * 1024L; // default 10MB in bytes
            try {
                String cfg = openlistConfig != null ? openlistConfig.getOpenListMinFileSize() : null;
                if (cfg != null && !cfg.isEmpty()) minSize = Long.parseLong(cfg) * 1024L * 1024L; // cfg treated as MB
            } catch (Exception ignored) {}

            FileMonitorService svc = new FileMonitorService(java.nio.file.Paths.get(src), java.nio.file.Paths.get(tgt), tmdbClient, openAIClient, minSize, null, createPersistingListener(taskId), openListHelper);

            // perform a one-shot scan
            svc.processOnce();
            return true;
        } catch (Exception e) {
            log.error("executeTaskNow error for {}: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 批量立即执行任务（以逗号分隔的 id 字符串或整数列表）。
     */
    public Map<Integer, String> executeTasksBatch(java.util.List<Integer> ids) {
        Map<Integer, String> res = new HashMap<>();
        if (ids == null || ids.isEmpty()) return res;
        for (Integer id : ids) {
            boolean ok = executeTaskNow(id);
            res.put(id, ok ? "ok" : "failed");
        }
        return res;
    }

    /**
     * Factory for a RenameEventListener that persists rename results into the DB.
     * taskId is used for logging; the persisted RenameDetail does not store the task id currently,
     * but the method centralizes the save logic so it can be reused across calls.
     */
    private RenameEventListener createPersistingListener(final Integer taskId) {
        return new RenameEventListener() {
            @Override
            public void onRename(java.nio.file.Path original, java.nio.file.Path dest, com.ruoyi.openliststrm.rename.model.MediaInfo info, String mediaType) {
                try {
                    RenameDetailPlus d = new RenameDetailPlus();
                    // store the folder containing the original file (fallback to full path if parent missing)
                    String originalDir = null;
                    if (original != null) {
                        java.nio.file.Path parent = original.toAbsolutePath().getParent();
                        originalDir = parent != null ? parent.toString() : original.toAbsolutePath().toString();
                    }
                    d.setOriginalPath(originalDir);
                    d.setOriginalName(original != null && original.getFileName() != null ? original.getFileName().toString() : null);
                    // store the destination folder (parent of dest); keep filename separately
                    String destDir = null;
                    if (dest != null) {
                        java.nio.file.Path parent = dest.toAbsolutePath().getParent();
                        destDir = parent != null ? parent.toString() : dest.toAbsolutePath().toString();
                    }
                    d.setNewPath(destDir);
                    d.setNewName(dest != null && dest.getFileName() != null ? dest.getFileName().toString() : null);
                    d.setMediaType(mediaType);
                    d.setTitle(info.getTitle());
                    d.setYear(info.getYear());
                    d.setSeason(info.getSeason());
                    d.setEpisode(info.getEpisode());
                    d.setTmdbId(info.getTmdbId());
                    d.setResolution(info.getResolution());
                    d.setVideoCodec(info.getVideoCodec());
                    d.setAudioCodec(info.getAudioCodec());
                    d.setSource(info.getSource());
                    d.setReleaseGroup(info.getReleaseGroup());
                    d.setStatus("1");
                    renameDetailService.save(d);
                    log.info("Saved rename detail for task {} : {} -> {}", taskId, original, dest);
                } catch (Exception e) {
                    log.warn("Failed to persist rename detail: {}", e.getMessage());
                }
            }

            @Override
            public void onRenameFailed(java.nio.file.Path original, com.ruoyi.openliststrm.rename.model.MediaInfo info, String mediaType, String reason) {
                try {
                    RenameDetailPlus d = new RenameDetailPlus();
                    // store folder containing the original file
                    String originalDir = null;
                    if (original != null) {
                        java.nio.file.Path parent = original.toAbsolutePath().getParent();
                        originalDir = parent != null ? parent.toString() : original.toAbsolutePath().toString();
                    }
                    d.setOriginalPath(originalDir);
                    d.setOriginalName(original != null && original.getFileName() != null ? original.getFileName().toString() : null);
                    d.setNewPath(null);
                    d.setNewName(null);
                    d.setMediaType(mediaType);
                    d.setTitle(info != null ? info.getTitle() : null);
                    d.setYear(info != null ? info.getYear() : null);
                    d.setSeason(info != null ? info.getSeason() : null);
                    d.setEpisode(info != null ? info.getEpisode() : null);
                    d.setTmdbId(info != null ? info.getTmdbId() : null);
                    d.setResolution(info != null ? info.getResolution() : null);
                    d.setVideoCodec(info != null ? info.getVideoCodec() : null);
                    d.setAudioCodec(info != null ? info.getAudioCodec() : null);
                    d.setSource(info != null ? info.getSource() : null);
                    d.setReleaseGroup(info != null ? info.getReleaseGroup() : null);
                    d.setStatus("0"); // mark as failed
                    renameDetailService.save(d);
                    log.info("Saved failed rename detail for task {} : {} reason={}", taskId, original, reason);
                } catch (Exception e) {
                    log.warn("Failed to persist failed rename detail: {}", e.getMessage());
                }
            }
        };
    }

}
