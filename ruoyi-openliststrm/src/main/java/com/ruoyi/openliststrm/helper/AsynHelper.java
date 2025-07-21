package com.ruoyi.openliststrm.helper;

import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.utils.Threads;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.domain.OpenlistCopy;
import com.ruoyi.openliststrm.service.IStrmService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.TimerTask;

/**
 * 异步线程服务
 *
 * @Author Jack
 * @Date 2024/6/23 12:42
 * @Version 1.0.0
 */
@Service
public class AsynHelper {

    @Autowired
    private OpenlistApi openlistApi;

    @Autowired
    private IStrmService strmService;

    @Autowired
    private CopyHelper copyHelper;

    /**
     * 判断openlist的复制任务是否完成 完成就执行strm任务
     *
     * @return
     * @Async
     */
    public void isCopyDone(String dstDir, String strmDir, Set<OpenlistCopy> taskIdList) {
        AsyncManager.me().execute(new TimerTask() {
            @Override
            public void run() {
                Threads.sleep(30000);
                while (true) {
                    boolean allTasksCompleted = true;
                    for (OpenlistCopy copy : taskIdList) {
                        String taskId = copy.getCopyTaskId();
                        if (StringUtils.isBlank(taskId)) {
                            copy.setCopyStatus("4");
                            copyHelper.addCopy(copy);
                            continue;
                        }
                        JSONObject jsonResponse = openlistApi.copyInfo(taskId);
                        if (jsonResponse == null) {
                            copy.setCopyStatus("4");
                            copyHelper.addCopy(copy);
                            continue;
                        }

                        // 检查任务状态
                        Integer code = jsonResponse.getInteger("code");
                        Integer state = -1;
                        if (jsonResponse.getJSONObject("data") != null) {
                            state = jsonResponse.getJSONObject("data").getInteger("state");
                        }

                        //不是上传成功状态
                        if (200 == code && state != 2) {
                            //失败状态了  就重试 状态1是运行中  状态8是等待重试
                            if (state == 7) {
                                openlistApi.copyRetry(taskId);
                            }
                            allTasksCompleted = false;
                        } else if (404 == code || state == 2) {
                            if (404 == code) {
                                copy.setCopyStatus("4");
                                copyHelper.addCopy(copy);
                            }
                            if (state == 2) {
                                copy.setCopyStatus("3");
                                copyHelper.addCopy(copy);
                            }
                            taskIdList.remove(copy);
                        }
                    }
                    if (allTasksCompleted) {
                        String newStrmDir = strmDir;
                        if (strmDir.startsWith("/")) {
                            newStrmDir = strmDir.replaceFirst("/", "");
                        }
                        String newDstDir = dstDir;
                        if (dstDir.endsWith("/")) {
                            newDstDir = dstDir.substring(0, dstDir.lastIndexOf("/"));
                        }
                        strmService.strmDir(newDstDir + "/" + newStrmDir);// 生成 STRM 文件
                        break;// 任务完成，退出循环
                    } else {
                        Threads.sleep(30000);//继续检查
                    }
                }
            }
        });

    }

    public void isCopyDoneOneFile(String path, OpenlistCopy copy) {
        AsyncManager.me().execute(new TimerTask() {
            @Override
            public void run() {
                if (StringUtils.isBlank(copy.getCopyTaskId())) {
                    strmService.strmOneFile(path);// 生成 STRM 文件
                }
                Threads.sleep(30000);
                while (true) {
                    JSONObject jsonResponse = openlistApi.copyInfo(copy.getCopyTaskId());
                    if (jsonResponse == null) {
                        copy.setCopyStatus("4");
                        copyHelper.addCopy(copy);
                        break;
                    }
                    // 检查任务状态
                    Integer code = jsonResponse.getInteger("code");
                    Integer state = -1;
                    if (jsonResponse.getJSONObject("data") != null) {
                        state = jsonResponse.getJSONObject("data").getInteger("state");
                    }
                    //判定任务是否完成了 完成了就生成strm文件
                    if (404 == code || state == 2) {
                        if (404 == code) {
                            copy.setCopyStatus("4");
                            copyHelper.addCopy(copy);
                        }
                        if (state == 2) {
                            copy.setCopyStatus("3");
                            copyHelper.addCopy(copy);
                        }
                        strmService.strmOneFile(path);// 生成 STRM 文件
                        break;// 任务完成，退出循环
                    } else if (state == 7) {
                        //失败就重试
                        openlistApi.copyRetry(copy.getCopyTaskId());
                    }
                    Threads.sleep(30000);//继续检查
                }
            }
        });

    }

}
