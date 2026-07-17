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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * 目录遍历任务（极轻量）
     */
    private static class DirTask {
        String relativePath;

        DirTask(String relativePath) {
            this.relativePath = relativePath;
        }
    }

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

        srcDir = StringUtils.removeEnd(srcDir, "/");
        dstDir = StringUtils.removeEnd(dstDir, "/");

        Queue<DirTask> queue = new ArrayDeque<>();
        queue.offer(new DirTask(startRelativePath));

        while (!queue.isEmpty()) {
            DirTask task = queue.poll();
            String relativePath = task.relativePath;

            String srcPath = srcDir + (StringUtils.isBlank(relativePath) ? "" : "/" + relativePath);
            JSONObject listResp = openlistApi.getOpenlist(srcPath);

            if (listResp == null || listResp.getJSONObject("data") == null) {
                continue;
            }

            JSONArray contents = listResp.getJSONObject("data").getJSONArray("content");
            if (contents == null) {
                continue;
            }

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

                String dstCheckPath = dstDir + "/" + childRelativePath;
                JSONObject dstExistResp = openlistApi.getFile(dstCheckPath);

                if (isDir) {
                    // 目录不存在则创建
                    if (dstExistResp == null || dstExistResp.getInteger("code") != 200) {
                        openlistApi.mkdir(
                                dstDir + (StringUtils.isBlank(relativePath) ? "" : "/" + relativePath)
                                        + "/" + name
                        );
                    }
                    queue.offer(new DirTask(childRelativePath));
                } else {
                    submitCopyTask(
                            srcDir,
                            dstDir,
                            relativePath,
                            name,
                            content.getLongValue("size"),
                            dstExistResp
                    );
                }
            }
        }
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
            JSONObject dstExistResp
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

                if (copyHelper.existsCopy(copy)) {
                    log.debug("文件已处理过，跳过处理 {}/{}", copyDstPath, fileName);
                    return;
                }

                // 目标不存在 & 视频文件 & 体积满足
                if ((dstExistResp == null || dstExistResp.getInteger("code") != 200)
                        && openListHelper.isVideo(fileName)
                        && fileSize >= config.getMinFileSizeBytes()) {

                    JSONObject resp = openlistApi.copyOpenlist(
                            copySrcPath,
                            copyDstPath,
                            Collections.singletonList(fileName)
                    );

                    if (resp != null && resp.getInteger("code") == 200) {
                        JSONArray tasks = resp.getJSONObject("data").getJSONArray("tasks");
                        copy.setCopyTaskId(tasks.getJSONObject(0).getString("id"));
                        copy.setCopyStatus("1");
                        copyHelper.addCopy(copy);
                    }
                } else if (dstExistResp != null && dstExistResp.getInteger("code") == 200) {
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

        if ((dstExistResp == null || dstExistResp.getInteger("code") != 200)) {
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

                if (resp != null && resp.getInteger("code") == 200) {
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
        // 外部API调用在事务外执行
        Runnable externalAction = () -> copyList.forEach(copy -> {
            openlistApi.fsRemove(copy.getCopyDstPath(), Collections.singletonList(copy.getCopyDstFileName()));
        });
        // 数据库操作在事务内执行
        Runnable dbAction = () -> {
            copyList.forEach(copy ->
                openlistStrmPlusService.remove(new LambdaQueryWrapper<OpenlistStrmPlus>()
                        .eq(OpenlistStrmPlus::getStrmFileName, copy.getCopyDstFileName())
                        .eq(OpenlistStrmPlus::getStrmPath, copy.getCopyDstPath())));
            openlistCopyPlusService.removeBatchByIds(idList);
        };
        if (idList.size() > 20) {
            AsyncManager.me().execute(() -> {
                externalAction.run();
                transactionTemplate.executeWithoutResult(status -> dbAction.run());
            });
        } else {
            externalAction.run();
            transactionTemplate.executeWithoutResult(status -> dbAction.run());
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
     * 解析 AList 返回的 modified 字段（ISO-8601 格式或 yyyy-MM-dd HH:mm:ss）
     */
    private Date parseModified(String modifiedStr) {
        if (StringUtils.isBlank(modifiedStr)) {
            return null;
        }
        // AList 可能返回多种格式，优先尝试 ISO-8601（去掉 T 和时区后缀）
        String normalized = modifiedStr
                .replace("T", " ")
                .replaceAll("\\+\\d{2}:\\d{2}$", "")
                .replaceAll("Z$", "")
                .trim();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.parse(normalized);
        } catch (ParseException e) {
            log.debug("无法解析 modified 字段: {}", modifiedStr);
            return null;
        }
    }
}