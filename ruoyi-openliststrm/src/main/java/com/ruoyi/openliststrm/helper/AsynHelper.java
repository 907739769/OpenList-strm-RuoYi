package com.ruoyi.openliststrm.helper;

import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyPlusService;
import com.ruoyi.openliststrm.service.IStrmService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 异步线程服务
 *
 * @Author Jack
 * @Version 1.1.0
 */
@Service
@Slf4j
public class AsynHelper {

    @Autowired
    private OpenlistApi openlistApi;

    @Autowired
    private IStrmService strmService;

    @Autowired
    private CopyHelper copyHelper;

    @Autowired
    private IOpenlistCopyPlusService openlistCopyPlusService;

    @Autowired
    private TgHelper tgHelper;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * 判断openlist的复制任务是否完成 完成就执行strm任务 (批量)
     * 改为异步调度模式
     */
    public void isCopyDone(String dstDir, String strmDir) {
        // 首次延迟30秒后开始检查
        scheduler.schedule(() -> {
            try {
                // 获取当前正在进行的任务列表
                List<OpenlistCopyPlus> copyList = openlistCopyPlusService.lambdaQuery()
                        .eq(OpenlistCopyPlus::getCopyStatus, "1")
                        .list();

                if (copyList == null || copyList.isEmpty()) {
                    // 如果没有进行中的任务，直接尝试执行收尾逻辑（保持原有业务逻辑）
                    finishStrmDir(dstDir, strmDir);
                    return;
                }

                // 开始递归检查
                processCopyListRecursive(copyList, dstDir, strmDir);
            } catch (Exception e) {
                log.error("Error in isCopyDone initialization", e);
            }
        }, 30, TimeUnit.SECONDS);
    }

    /**
     * 递归检查批量任务状态
     */
    private void processCopyListRecursive(List<OpenlistCopyPlus> copyList, String dstDir, String strmDir) {
        Iterator<OpenlistCopyPlus> iterator = copyList.iterator();
        boolean hasError = false;

        while (iterator.hasNext()) {
            OpenlistCopyPlus copy = iterator.next();
            String taskId = copy.getCopyTaskId();

            if (StringUtils.isBlank(taskId)) {
                iterator.remove();
                continue;
            }

            try {
                JSONObject jsonResponse = openlistApi.copyInfo(taskId);
                if (jsonResponse == null) {
                    // API 请求失败或无响应，视为异常结束
                    updateCopyStatus(copy, "4");
                    iterator.remove(); // 从监控列表中移除
                    continue;
                }

                // 检查任务状态
                Integer code = jsonResponse.getInteger("code");
                Integer state = -1;
                if (jsonResponse.getJSONObject("data") != null) {
                    state = jsonResponse.getJSONObject("data").getInteger("state");
                }

                // 状态判断逻辑
                if (200 == code && state != 2) {
                    // 状态1是运行中，状态8是等待重试，状态7是失败
                    if (state == 7) {
                        // 失败不重试了
                        updateCopyStatus(copy, "2");
                        tgHelper.sendMsg("*复制任务失败*\n" +
                                "源目录：" + StringUtils.escapeMarkdownV2(copy.getCopySrcPath()) + "\n" +
                                "源文件名：" + StringUtils.escapeMarkdownV2(copy.getCopySrcFileName()));
                        iterator.remove(); // 移除失败任务
                        hasError = true;
                    }
                    // 其他状态（如1运行中）则保留在列表中继续监控
                } else if (404 == code || state == 2) {
                    // 404: 任务丢失/过期? state=2: 完成
                    if (404 == code) {
                        updateCopyStatus(copy, "4");
                    }
                    if (state == 2) {
                        updateCopyStatus(copy, "3");
                    }
                    iterator.remove(); // 移除已完成任务
                }
            } catch (Exception e) {
                log.error("Error checking task status for id: {}", taskId, e);
            }
        }

        // 检查列表是否为空
        if (copyList.isEmpty()) {
            // 所有任务都已移出列表（完成或失败），执行最终的 strm 生成
            finishStrmDir(dstDir, strmDir);
        } else {
            // 列表不为空，说明还有任务在运行，延迟30秒后再次调用自己
            scheduler.schedule(() -> processCopyListRecursive(copyList, dstDir, strmDir), 30, TimeUnit.SECONDS);
        }
    }

    /**
     * 单个文件复制监控 (优化版)
     */
    public void isCopyDoneOneFile(String path, OpenlistCopyPlus copy) {
        if (StringUtils.isBlank(copy.getCopyTaskId())) {
            strmService.strmOneFile(path);// 生成 STRM 文件
            return;
        }

        // 延迟30秒后开始第一次检查
        scheduler.schedule(() -> checkOneFileRecursive(path, copy), 30, TimeUnit.SECONDS);
    }

    /**
     * 递归检查单文件状态
     */
    private void checkOneFileRecursive(String path, OpenlistCopyPlus copy) {
        try {
            JSONObject jsonResponse = openlistApi.copyInfo(copy.getCopyTaskId());

            if (jsonResponse == null) {
                updateCopyStatus(copy, "4");
                return; // 结束监控
            }

            Integer code = jsonResponse.getInteger("code");
            Integer state = -1;
            if (jsonResponse.getJSONObject("data") != null) {
                state = jsonResponse.getJSONObject("data").getInteger("state");
            }

            // 判定任务是否完成
            if (404 == code || state == 2) {
                if (404 == code) {
                    updateCopyStatus(copy, "4");
                }
                if (state == 2) {
                    updateCopyStatus(copy, "3");
                    // 成功后生成 strm
                    strmService.strmOneFile(path);
                }
                return; // 任务完成，退出递归
            } else if (state == 7) {
                // 失败状态
                updateCopyStatus(copy, "2");
                tgHelper.sendMsg("*复制任务失败*\n" +
                        "源目录：" + StringUtils.escapeMarkdownV2(copy.getCopySrcPath()) + "\n" +
                        "源文件名：" + StringUtils.escapeMarkdownV2(copy.getCopySrcFileName()));
                return; // 任务失败，退出递归
            }

            // 任务仍在运行中，继续调度下一次检查
            scheduler.schedule(() -> checkOneFileRecursive(path, copy), 30, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Error in checkOneFileRecursive for path: {}", path, e);
        }
    }

    // 辅助方法：更新数据库状态
    private void updateCopyStatus(OpenlistCopyPlus copy, String status) {
        copy.setCopyStatus(status);
        copyHelper.addCopy(copy);
    }

    // 辅助方法：处理目录 strm 生成逻辑 (保持原有逻辑不变)
    private void finishStrmDir(String dstDir, String strmDir) {
        try {
            String newStrmDir = strmDir;
            if (strmDir.startsWith("/")) {
                newStrmDir = strmDir.replaceFirst("/", "");
            }
            String newDstDir = dstDir;
            if (dstDir.endsWith("/")) {
                newDstDir = dstDir.substring(0, dstDir.lastIndexOf("/"));
            }
            strmService.strmDir(newDstDir + "/" + newStrmDir); // 生成 STRM 文件
        } catch (Exception e) {
            log.error("Error generating strm for dir: {}", dstDir, e);
        }
    }

    // 容器销毁时关闭线程池，防止内存泄漏
    @PreDestroy
    public void destroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}