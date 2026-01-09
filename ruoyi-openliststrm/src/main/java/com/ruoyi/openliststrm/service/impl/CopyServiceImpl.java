package com.ruoyi.openliststrm.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.AsynHelper;
import com.ruoyi.openliststrm.helper.CopyHelper;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.service.ICopyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 复制 openlist 文件（队列版，非递归，防内存泄漏）
 */
@Service
@Slf4j
public class CopyServiceImpl implements ICopyService {

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
     */
    private void syncFilesByQueue(String srcDir, String dstDir, String startRelativePath) {
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

        AsyncManager.me().execute(new TimerTask() {
            @Override
            public void run() {
                OpenlistCopyPlus copy = new OpenlistCopyPlus();
                copy.setCopySrcPath(copySrcPath);
                copy.setCopyDstPath(copyDstPath);
                copy.setCopySrcFileName(fileName);
                copy.setCopyDstFileName(fileName);

                if (copyHelper.exitCopy(copy)) {
                    log.debug("文件已处理过，跳过处理 {}/{}", copyDstPath, fileName);
                    return;
                }

                // 目标不存在 & 视频文件 & 体积满足
                if ((dstExistResp == null || dstExistResp.getInteger("code") != 200)
                        && openListHelper.isVideo(fileName)
                        && fileSize >= Long.parseLong(config.getOpenListMinFileSize()) * 1024 * 1024) {

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
            }
        });
    }

    /**
     * 单文件同步（保持原实现）
     */
    @Override
    public void syncOneFile(String srcDir, String dstDir, String relativePath) {
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

        if (copyHelper.exitCopy(copy)) {
            log.debug("文件已处理过，跳过处理 {}/{}", dstDir, relativePath);
            return;
        }

        AtomicBoolean flag = new AtomicBoolean(false);
        JSONObject dstExistResp = openlistApi.getFile(dstDir + "/" + relativePath);

        if ((dstExistResp == null || dstExistResp.getInteger("code") != 200)) {
            JSONObject srcResp = openlistApi.getFile(srcDir + "/" + relativePath);
            if (srcResp.getJSONObject("data").getLong("size")
                    >= Long.parseLong(config.getOpenListMinFileSize()) * 1024 * 1024) {

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

        if (flag.get() && "1".equals(config.getOpenListCopyStrm())) {
            asynHelper.isCopyDoneOneFile(dstDir + "/" + relativePath, copy);
        }
    }

    @Override
    public void syncFiles(String srcDir, String dstDir, String relativePath) {
        syncFilesByQueue(srcDir, dstDir, relativePath);
        if ("1".equals(config.getOpenListCopyStrm())) {
            asynHelper.isCopyDone(dstDir, relativePath);
        }
    }

    @Override
    public void syncFiles(String srcDir, String dstDir) {
        syncFilesByQueue(srcDir, dstDir, "");
        if ("1".equals(config.getOpenListCopyStrm())) {
            asynHelper.isCopyDone(dstDir, "");
        }
    }
}
