package com.ruoyi.openliststrm.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.domain.OpenlistCopy;
import com.ruoyi.openliststrm.helper.AsynHelper;
import com.ruoyi.openliststrm.helper.CopyHelper;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 复制openlist文件
 *
 * @Author Jack
 * @Date 2024/6/22 17:53
 * @Version 1.0.0
 */
@Service
@Slf4j
public class CopyServiceImpl {

    @Autowired
    private OpenListHelper openListHelper;

    @Autowired
    private OpenlistApi openlistApi;

    @Autowired
    private AsynHelper asynHelper;

    @Autowired
    private CopyHelper copyHelper;

    @Value("${minFileSize:10}")
    private String minFileSize;

    @Value("${strmAfterSync:1}")
    private String strmAfterSync;

    private final Set<String> cache = ConcurrentHashMap.newKeySet();

    public void syncFiles(String srcDir, String dstDir, String relativePath, String strmDir, Set<OpenlistCopy> taskIdList) {
        if (StringUtils.isAnyBlank(srcDir, dstDir)) {
            return;
        }
        AtomicBoolean flag = new AtomicBoolean(false);
        //查出所有源目录
        JSONObject object = openlistApi.getOpenlist(srcDir + relativePath);
        if (object.getJSONObject("data") == null) {
            return;
        }
        JSONArray jsonArray = object.getJSONObject("data").getJSONArray("content");
        if (jsonArray == null) {
            return;
        }

        jsonArray.forEach(content -> {
            JSONObject contentJson = (JSONObject) content;
            String name = contentJson.getString("name");

            //不是视频文件就不用继续往下走上传了
            if (!contentJson.getBoolean("is_dir") && !openListHelper.isVideo(name)) {
                return;
            }

            JSONObject jsonObject = openlistApi.getFile(dstDir + "/" + relativePath + "/" + name);
            //是目录
            if (contentJson.getBoolean("is_dir")) {
                //判断目标目录是否存在这个文件夹
                //200就是存在 存在就继续往下级目录找
                if (200 == jsonObject.getInteger("code")) {
                    syncFiles(srcDir, dstDir, relativePath + "/" + name, taskIdList);
                } else {
                    openlistApi.mkdir(dstDir + "/" + relativePath + "/" + name);
                    syncFiles(srcDir, dstDir, relativePath + "/" + name, taskIdList);
                }
            } else {
                OpenlistCopy copy = new OpenlistCopy();
                copy.setCopySrcPath(srcDir + relativePath);
                copy.setCopyDstPath(dstDir + relativePath);
                copy.setCopySrcFileName(name);
                copy.setCopyDstFileName(name);
                if (copyHelper.exitCopy(copy)) {
                    log.info("文件已处理过，跳过处理" + dstDir + "/" + relativePath + "/" + name);
                    return;
                }
                //是视频文件才复制 并且不存在
                if (!(200 == jsonObject.getInteger("code")) && openListHelper.isVideo(name)) {
                    if (contentJson.getLong("size") > Long.parseLong(minFileSize) * 1024 * 1024) {
                        JSONObject jsonResponse = openlistApi.copyOpenlist(srcDir + "/" + relativePath, dstDir + "/" + relativePath, Collections.singletonList(name));
                        if (jsonResponse != null && 200 == jsonResponse.getInteger("code")) {
                            flag.set(true);
                            //获取上传文件的任务id
                            JSONArray tasks = jsonResponse.getJSONObject("data").getJSONArray("tasks");
                            copy.setCopyTaskId(tasks.getJSONObject(0).getString("id"));
                            copy.setCopyStatus("1");
                            copyHelper.addCopy(copy);
                            taskIdList.add(copy);
                        }
                    }
                }
            }
        });

        if (flag.get() && "1".equals(strmAfterSync)) {
            asynHelper.isCopyDone(dstDir, strmDir, taskIdList);
        }


    }

    public void syncOneFile(String srcDir, String dstDir, String relativePath) {
        OpenlistCopy copy = new OpenlistCopy();
        copy.setCopySrcPath(srcDir + relativePath);
        copy.setCopyDstPath(dstDir + relativePath);
        copy.setCopySrcFileName(relativePath.substring(relativePath.lastIndexOf("/")));
        copy.setCopyDstFileName(relativePath.substring(relativePath.lastIndexOf("/")));
        if (copyHelper.exitCopy(copy)) {
            log.info("文件已处理过，跳过处理" + dstDir + "/" + relativePath);
            return;
        }
        AtomicBoolean flag = new AtomicBoolean(false);
        JSONObject jsonObject = openlistApi.getFile(dstDir + "/" + relativePath);
        if (!(200 == jsonObject.getInteger("code")) && openListHelper.isVideo(relativePath)) {
            JSONObject srcJson = openlistApi.getFile(srcDir + "/" + relativePath);
            if (srcJson.getJSONObject("data").getLong("size") > Long.parseLong(minFileSize) * 1024 * 1024) {
                openlistApi.mkdir(dstDir + "/" + relativePath.substring(0, relativePath.lastIndexOf("/")));
                JSONObject jsonResponse = openlistApi.copyOpenlist(srcDir + "/" + relativePath.substring(0, relativePath.lastIndexOf("/")), dstDir + "/" + relativePath.substring(0, relativePath.lastIndexOf("/")), Collections.singletonList(relativePath.substring(relativePath.lastIndexOf("/"))));
                if (jsonResponse != null && 200 == jsonResponse.getInteger("code")) {
                    flag.set(true);
                    //获取上传文件的任务id
                    JSONArray tasks = jsonResponse.getJSONObject("data").getJSONArray("tasks");
                    copy.setCopyTaskId(tasks.getJSONObject(0).getString("id"));
                    copy.setCopyStatus("1");
                    copyHelper.addCopy(copy);
                }
            }
        }

        if (flag.get() && "1".equals(strmAfterSync)) {
            asynHelper.isCopyDoneOneFile(dstDir + relativePath, copy);
        }

    }

    public void syncFiles(String srcDir, String dstDir, String relativePath, Set<OpenlistCopy> taskIdList) {
        syncFiles(srcDir, dstDir, relativePath, "", taskIdList);
    }


}
