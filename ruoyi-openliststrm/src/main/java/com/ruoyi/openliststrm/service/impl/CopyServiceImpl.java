package com.ruoyi.openliststrm.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.AsynHelper;
import com.ruoyi.openliststrm.helper.CopyHelper;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmPlusService;
import com.ruoyi.openliststrm.service.ICopyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 复制 openlist 文件（队列版，非递归，防内存泄漏）
 */
@Service
@Slf4j
public class CopyServiceImpl implements ICopyService {

    /** 复制文件处理并发度控制，最多10个虚拟线程同时处理 */
    private static final Semaphore COPY_SEMAPHORE = new Semaphore(10);

    @Autowired
    private OpenListHelper openListHelper;

    @Autowired
    private OpenlistApi openlistApi;

    @Autowired
    private AsynHelper asynHelper;

    @Autowired
    private CopyHelper copyHelper;

    @Autowired
    private OpenlistConfig config;

    @Autowired
    private IOpenlistCopyPlusService openlistCopyPlusService;

    @Autowired
    private IOpenlistStrmPlusService openlistStrmPlusService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 队列方式同步目录（完全替代递归）
     *
     * @param incrementalAfter 增量基准时间，为 null 时全量同步
     */
    private void syncFilesByQueue(String srcDir, String dstDir, String startRelativePath, Date incrementalAfter) {
        log.info("开始同步目录: {} {} {}", srcDir, dstDir, startRelativePath);
        if (StringUtils.isAnyBlank(srcDir, dstDir)) {
            return;
        }

        if (StringUtils.isNotBlank(startRelativePath) && startRelativePath.startsWith("/")) {
            startRelativePath = startRelativePath.substring(1);
        }

        final String rootSrcDir = StringUtils.removeEnd(srcDir, "/");
        final String rootDstDir = StringUtils.removeEnd(dstDir, "/");
        final Semaphore dirSemaphore = new Semaphore(config.getTraversalConcurrency());

        // 逐层并行 BFS：同一层的目录并发列举（每目录含 fs/list + 目标列举 + 一次 DB 查询），
        // 层与层之间用 join 做屏障，保证父目录已 mkdir 后子目录才被列举。
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<String> currentLevel = new java.util.ArrayList<>();
            currentLevel.add(startRelativePath == null ? "" : startRelativePath);

            while (!currentLevel.isEmpty()) {
                List<CompletableFuture<List<String>>> futures = currentLevel.stream()
                        .map(rel -> CompletableFuture.supplyAsync(
                                () -> syncOneDir(rootSrcDir, rootDstDir, rel, incrementalAfter, dirSemaphore), executor))
                        .toList();

                List<String> nextLevel = new java.util.ArrayList<>();
                for (CompletableFuture<List<String>> f : futures) {
                    nextLevel.addAll(f.join());
                }
                currentLevel = nextLevel;
            }
        }
    }

    /**
     * 同步单个目录：列举源目录、创建缺失的目标子目录、对满足条件的视频文件提交异步复制任务，
     * 返回需要继续遍历的子目录相对路径列表。通过信号量限制并发列举数，避免压垮 AList。
     */
    private List<String> syncOneDir(String srcDir, String dstDir, String relativePath,
                                    Date incrementalAfter, Semaphore dirSemaphore) {
        try {
            dirSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
        try {
            String srcPath = srcDir + (StringUtils.isBlank(relativePath) ? "" : "/" + relativePath);
            JSONObject listResp = openlistApi.getOpenlist(srcPath);

            if (listResp == null || listResp.getJSONObject("data") == null) {
                return Collections.emptyList();
            }

            JSONArray contents = listResp.getJSONObject("data").getJSONArray("content");
            if (contents == null || contents.isEmpty()) {
                return Collections.emptyList();
            }

            // 一次性获取目标目录下已有的名称集合，避免对每个子项都单独调用 fs/get
            String dstPath = dstDir + (StringUtils.isBlank(relativePath) ? "" : "/" + relativePath);
            Set<String> dstExistingNames = listDstNames(dstPath);

            // 一次性批量查询该目录下已处理过的文件，避免逐文件查询数据库
            Set<String> processedFileNames = openlistCopyPlusService.lambdaQuery()
                    .eq(OpenlistCopyPlus::getCopySrcPath, srcPath)
                    .in(OpenlistCopyPlus::getCopyStatus, "1", "3")
                    .list()
                    .stream()
                    .map(OpenlistCopyPlus::getCopySrcFileName)
                    .collect(Collectors.toSet());

            List<String> childDirs = new java.util.ArrayList<>();
            for (Object obj : contents) {
                JSONObject content = (JSONObject) obj;
                String name = content.getString("name");
                boolean isDir = content.getBooleanValue("is_dir");

                // 非目录 & 非视频文件，直接跳过
                if (!isDir && !openListHelper.isVideo(name)) {
                    continue;
                }

                // 增量同步：跳过修改时间早于基准的文件
                if (!isDir && incrementalAfter != null) {
                    Date modified = parseModified(content.getString("modified"));
                    if (modified != null && !modified.after(incrementalAfter)) {
                        continue;
                    }
                }

                String childRelativePath =
                        StringUtils.isBlank(relativePath) ? name : relativePath + "/" + name;
                boolean existsInDst = dstExistingNames.contains(name);

                if (isDir) {
                    // 目录不存在则创建
                    if (!existsInDst) {
                        openlistApi.mkdir(dstPath + "/" + name);
                    }
                    childDirs.add(childRelativePath);
                } else {
                    if (processedFileNames.contains(name)) {
                        log.debug("文件已处理过，跳过处理 {}/{}", dstPath, name);
                        continue;
                    }
                    submitCopyTask(
                            srcDir,
                            dstDir,
                            relativePath,
                            name,
                            content.getLongValue("size"),
                            existsInDst
                    );
                }
            }
            return childDirs;
        } finally {
            dirSemaphore.release();
        }
    }

    /**
     * 一次性列出目标目录下的所有名称，用于批量存在性判断
     */
    private Set<String> listDstNames(String dstPath) {
        JSONObject resp = openlistApi.getOpenlist(dstPath);
        if (resp == null || resp.getJSONObject("data") == null) {
            return Collections.emptySet();
        }
        JSONArray contents = resp.getJSONObject("data").getJSONArray("content");
        if (contents == null) {
            return Collections.emptySet();
        }
        Set<String> names = new HashSet<>();
        for (Object obj : contents) {
            names.add(((JSONObject) obj).getString("name"));
        }
        return names;
    }

    /**
     * 提交异步复制任务（避免捕获大对象）
     */
    private void submitCopyTask(
            String srcDir,
            String dstDir,
            String relativePath,
            String fileName,
            long fileSize,
            boolean dstExists
    ) {
        final String copySrcPath =
                srcDir + (StringUtils.isBlank(relativePath) ? "" : "/" + relativePath);
        final String copyDstPath =
                dstDir + (StringUtils.isBlank(relativePath) ? "" : "/" + relativePath);

        AsyncManager.me().execute(() -> {
                OpenlistCopyPlus copy = new OpenlistCopyPlus();
                copy.setCopySrcPath(copySrcPath);
                copy.setCopyDstPath(copyDstPath);
                copy.setCopySrcFileName(fileName);
                copy.setCopyDstFileName(fileName);

                // 目标不存在 & 视频文件 & 体积满足
                if (!dstExists
                        && openListHelper.isVideo(fileName)
                        && fileSize >= config.getMinFileSizeBytes()) {

                    JSONObject resp = openlistApi.copyOpenlist(
                            copySrcPath,
                            copyDstPath,
                            Collections.singletonList(fileName)
                    );

                    if (resp != null && Integer.valueOf(200).equals(resp.getInteger("code"))) {
                        JSONArray tasks = resp.getJSONObject("data").getJSONArray("tasks");
                        copy.setCopyTaskId(tasks.getJSONObject(0).getString("id"));
                        copy.setCopyStatus("1");
                        copyHelper.addCopy(copy);
                    }
                } else if (dstExists) {
                    copy.setCopyStatus("3");
                    copyHelper.addCopy(copy);
                }
            });
    }

    /**
     * 单文件同步（保持原实现）
     */
    @Override
    public void syncOneFile(String srcDir, String dstDir, String relativePath) {
        log.info("开始同步文件: {}", relativePath);
        if (!openListHelper.isVideo(relativePath)) {
            return;
        }

        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        srcDir = StringUtils.removeEnd(srcDir, "/");
        dstDir = StringUtils.removeEnd(dstDir, "/");

        String copySrcPath = srcDir;
        String copyDstPath = dstDir;
        String fileName = relativePath;

        if (relativePath.contains("/")) {
            copySrcPath = srcDir + "/" + relativePath.substring(0, relativePath.lastIndexOf("/"));
            copyDstPath = dstDir + "/" + relativePath.substring(0, relativePath.lastIndexOf("/"));
            fileName = relativePath.substring(relativePath.lastIndexOf("/") + 1);
        }

        OpenlistCopyPlus copy = new OpenlistCopyPlus();
        copy.setCopySrcPath(copySrcPath);
        copy.setCopyDstPath(copyDstPath);
        copy.setCopySrcFileName(fileName);
        copy.setCopyDstFileName(fileName);

        if (copyHelper.existsCopy(copy)) {
            log.debug("文件已处理过，跳过处理 {}/{}", dstDir, relativePath);
            return;
        }

        AtomicBoolean flag = new AtomicBoolean(false);
        JSONObject dstExistResp = openlistApi.getFile(dstDir + "/" + relativePath);

        if (dstExistResp == null || !Integer.valueOf(200).equals(dstExistResp.getInteger("code"))) {
            JSONObject srcResp = openlistApi.getFile(srcDir + "/" + relativePath);
            if(null==srcResp||null==srcResp.getJSONObject("data")){
                log.warn("本地文件不存在{}/{}", srcDir, relativePath);
                return;
            }
            if (srcResp.getJSONObject("data").getLong("size") >= config.getMinFileSizeBytes()) {

                openlistApi.mkdir(copyDstPath);
                JSONObject resp = openlistApi.copyOpenlist(
                        copySrcPath,
                        copyDstPath,
                        Collections.singletonList(fileName)
                );

                if (resp != null && Integer.valueOf(200).equals(resp.getInteger("code"))) {
                    flag.set(true);
                    JSONArray tasks = resp.getJSONObject("data").getJSONArray("tasks");
                    copy.setCopyTaskId(tasks.getJSONObject(0).getString("id"));
                    copy.setCopyStatus("1");
                    copyHelper.addCopy(copy);
                }
            }
        } else {
            flag.set(true);
            copy.setCopyStatus("3");
            copyHelper.addCopy(copy);
        }

        if (flag.get()) {
            asynHelper.isCopyDoneOneFile(dstDir + "/" + relativePath, copy);
        }
    }

    @Override
    public void syncFiles(String srcDir, String dstDir, String relativePath) {
        syncFilesByQueue(srcDir, dstDir, relativePath, null);
        asynHelper.isCopyDone(dstDir, relativePath);
    }

    @Override
    public void syncFiles(String srcDir, String dstDir) {
        syncFiles(srcDir, dstDir, "");
    }

    @Override
    public void syncFilesIncremental(String srcDir, String dstDir, Date lastSyncTime) {
        log.info("开始增量同步目录: {} -> {}, 基准时间: {}", srcDir, dstDir, lastSyncTime);
        syncFilesByQueue(srcDir, dstDir, "", lastSyncTime);
        asynHelper.isCopyDone(dstDir, "");
    }

    @Override
    public void batchRemoveNetDisk(List<String> idList) {
        if (idList == null || idList.isEmpty()) return;
        List<OpenlistCopyPlus> copyList = openlistCopyPlusService.listByIds(idList);
        Runnable action = () -> {
            // 外部API调用在事务外执行，单条隔离失败，只清理网盘删除成功的记录，避免网盘/DB状态不一致
            List<OpenlistCopyPlus> succeeded = new java.util.ArrayList<>();
            for (OpenlistCopyPlus copy : copyList) {
                try {
                    JSONObject resp = openlistApi.fsRemove(copy.getCopyDstPath(), Collections.singletonList(copy.getCopyDstFileName()));
                    if (resp != null && Integer.valueOf(200).equals(resp.getInteger("code"))) {
                        succeeded.add(copy);
                    } else {
                        log.warn("网盘文件删除失败，跳过对应记录清理：{}/{}", copy.getCopyDstPath(), copy.getCopyDstFileName());
                    }
                } catch (Exception e) {
                    log.error("网盘文件删除异常，跳过对应记录清理：{}/{}", copy.getCopyDstPath(), copy.getCopyDstFileName(), e);
                }
            }
            if (succeeded.isEmpty()) {
                return;
            }
            List<Integer> succeededIds = succeeded.stream().map(OpenlistCopyPlus::getCopyId).toList();
            transactionTemplate.executeWithoutResult(status -> {
                // 合并为一条 OR 条件批量删除，避免逐条 DELETE
                LambdaQueryWrapper<OpenlistStrmPlus> strmWrapper = new LambdaQueryWrapper<>();
                for (OpenlistCopyPlus copy : succeeded) {
                    strmWrapper.or(w -> w.eq(OpenlistStrmPlus::getStrmFileName, copy.getCopyDstFileName())
                            .eq(OpenlistStrmPlus::getStrmPath, copy.getCopyDstPath()));
                }
                openlistStrmPlusService.remove(strmWrapper);
                openlistCopyPlusService.removeBatchByIds(succeededIds);
            });
        };
        if (idList.size() > 20) {
            AsyncManager.me().execute(action);
        } else {
            action.run();
        }
    }

    @Override
    public void retryCopy(List<String> idList) {
        if (idList == null || idList.isEmpty()) return;
        List<OpenlistCopyPlus> copyList = openlistCopyPlusService.listByIds(idList);
        copyList.forEach(copy -> {
            copy.setCopyStatus("2");
            copy.setCopyTaskId("");
        });
        openlistCopyPlusService.updateBatchById(copyList);
        Runnable action = () -> {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = copyList.stream()
                    .map(copy -> CompletableFuture.runAsync(() -> {
                        try {
                            COPY_SEMAPHORE.acquire();
                            try {
                                syncOneFile(copy.getCopySrcPath(), copy.getCopyDstPath(), copy.getCopySrcFileName());
                            } finally {
                                COPY_SEMAPHORE.release();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }, executor))
                    .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        };
        if (idList.size() > 20) {
            AsyncManager.me().execute(action);
        } else {
            action.run();
        }
    }

    /**
     * 解析 AList 返回的 modified 字段。
     * <p>
     * AList 通常返回带时区偏移的 ISO-8601（如 {@code 2024-01-01T12:00:00+08:00} 或以 Z 结尾），
     * 必须按偏移换算成绝对时刻再与基准时间比较；旧实现直接删掉偏移后缀会导致跨时区偏移，
     * 使增量同步漏拷或重复拷。这里优先按带偏移的 ISO-8601 解析，无偏移时才回退到服务器本地时区。
     */
    private Date parseModified(String modifiedStr) {
        if (StringUtils.isBlank(modifiedStr)) {
            return null;
        }
        String s = modifiedStr.trim();
        // 1) 带时区偏移或 Z 的 ISO-8601 —— 直接得到绝对时刻
        try {
            return Date.from(OffsetDateTime.parse(s).toInstant());
        } catch (DateTimeParseException ignore) {
            // 继续尝试其他格式
        }
        // 2) 无时区信息的 ISO-8601（含 T），按服务器本地时区解释
        try {
            return Date.from(LocalDateTime.parse(s.replace(' ', 'T'))
                    .atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException ignore) {
            // 继续尝试兜底格式
        }
        // 3) 兜底：yyyy-MM-dd HH:mm:ss（本地时区）
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s.replace('T', ' '));
        } catch (ParseException e) {
            log.debug("无法解析 modified 字段: {}", modifiedStr);
            return null;
        }
    }
}