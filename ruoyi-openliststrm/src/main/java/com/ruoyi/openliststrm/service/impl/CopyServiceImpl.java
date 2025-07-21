package com.ruoyi.openliststrm.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.domain.OpenlistCopy;
import com.ruoyi.openliststrm.helper.AsynHelper;
import com.ruoyi.openliststrm.helper.CopyHelper;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.service.ICopyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

    private void syncFilesRecursion(String srcDir, String dstDir, String relativePath, Set<OpenlistCopy> copySet) {
        if (StringUtils.isAnyBlank(srcDir, dstDir)) {
            return;
        }
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.replaceFirst("/", "");
        }
        if (srcDir.endsWith("/")) {
            srcDir = srcDir.substring(0, srcDir.lastIndexOf("/"));
        }
        if (dstDir.endsWith("/")) {
            dstDir = dstDir.substring(0, dstDir.lastIndexOf("/"));
        }
        //查出所有源目录
        JSONObject object = openlistApi.getOpenlist(srcDir + "/" + relativePath);
        if (object.getJSONObject("data") == null) {
            return;
        }
        JSONArray jsonArray = object.getJSONObject("data").getJSONArray("content");
        if (jsonArray == null) {
            return;
        }

        for (Object content : jsonArray) {
            JSONObject contentJson = (JSONObject) content;
            String name = contentJson.getString("name");

            //不是视频文件就不用继续往下走上传了
            if (!contentJson.getBoolean("is_dir") && !openListHelper.isVideo(name)) {
                continue;
            }

            JSONObject jsonObject = openlistApi.getFile(dstDir + "/" + relativePath + (StringUtils.isBlank(relativePath) ? "" : "/") + name);
            //是目录
            if (contentJson.getBoolean("is_dir")) {
                //判断目标目录是否存在这个文件夹
                //200就是存在 存在就继续往下级目录找
                if (200 == jsonObject.getInteger("code")) {
                    syncFiles(srcDir, dstDir, relativePath + (StringUtils.isBlank(relativePath) ? "" : "/") + name, copySet);
                } else {
                    openlistApi.mkdir(dstDir + "/" + relativePath + (StringUtils.isBlank(relativePath) ? "" : "/") + name);
                    syncFiles(srcDir, dstDir, relativePath + (StringUtils.isBlank(relativePath) ? "" : "/") + name, copySet);
                }
            } else {
                OpenlistCopy copy = new OpenlistCopy();
                copy.setCopySrcPath(srcDir + "/" + relativePath);
                copy.setCopyDstPath(dstDir + "/" + relativePath);
                copy.setCopySrcFileName(name);
                copy.setCopyDstFileName(name);
                if (copyHelper.exitCopy(copy)) {
                    log.info("文件已处理过，跳过处理" + dstDir + "/" + relativePath + (StringUtils.isBlank(relativePath) ? "" : "/") + name);
                    continue;
                }
                //是视频文件才复制 并且不存在
                if (!(200 == jsonObject.getInteger("code")) && openListHelper.isVideo(name)) {
                    if (contentJson.getLong("size") >= Long.parseLong(config.getOpenListMinFileSize()) * 1024 * 1024) {
                        JSONObject jsonResponse = openlistApi.copyOpenlist(srcDir + "/" + relativePath, dstDir + "/" + relativePath, Collections.singletonList(name));
                        if (jsonResponse != null && 200 == jsonResponse.getInteger("code")) {
                            //获取上传文件的任务id
                            JSONArray tasks = jsonResponse.getJSONObject("data").getJSONArray("tasks");
                            copy.setCopyTaskId(tasks.getJSONObject(0).getString("id"));
                            copy.setCopyStatus("1");
                            copyHelper.addCopy(copy);
                            copySet.add(copy);
                        }
                    }
                } else if (200 == jsonObject.getInteger("code")) {
                    copy.setCopyStatus("3");
                    copyHelper.addCopy(copy);
                }
            }
        }

    }

    public void syncOneFile(String srcDir, String dstDir, String relativePath) {
        if (!openListHelper.isVideo(relativePath)) {
            return;
        }
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.replaceFirst("/", "");
        }
        if (srcDir.endsWith("/")) {
            srcDir = srcDir.substring(0, srcDir.lastIndexOf("/"));
        }
        if (dstDir.endsWith("/")) {
            dstDir = dstDir.substring(0, dstDir.lastIndexOf("/"));
        }
        //源目录
        String copySrcPath = srcDir + "/" + relativePath.substring(0, relativePath.lastIndexOf("/"));
        //目标目录
        String copyDstPath = dstDir + "/" + relativePath.substring(0, relativePath.lastIndexOf("/"));
        //文件名
        String fileName = relativePath.substring(relativePath.lastIndexOf("/") + 1);
        OpenlistCopy copy = new OpenlistCopy();
        copy.setCopySrcPath(copySrcPath);
        copy.setCopyDstPath(copyDstPath);
        copy.setCopySrcFileName(fileName);
        copy.setCopyDstFileName(fileName);
        if (copyHelper.exitCopy(copy)) {
            log.info("文件已处理过，跳过处理" + dstDir + "/" + relativePath);
            return;
        }
        AtomicBoolean flag = new AtomicBoolean(false);
        JSONObject jsonObject = openlistApi.getFile(dstDir + "/" + relativePath);
        if (!(200 == jsonObject.getInteger("code")) && openListHelper.isVideo(relativePath)) {
            JSONObject srcJson = openlistApi.getFile(srcDir + "/" + relativePath);
            if (srcJson.getJSONObject("data").getLong("size") > Long.parseLong(config.getOpenListMinFileSize()) * 1024 * 1024) {
                openlistApi.mkdir(copyDstPath);
                JSONObject jsonResponse = openlistApi.copyOpenlist(copySrcPath, copyDstPath, Collections.singletonList(fileName));
                if (jsonResponse != null && 200 == jsonResponse.getInteger("code")) {
                    flag.set(true);
                    //获取上传文件的任务id
                    JSONArray tasks = jsonResponse.getJSONObject("data").getJSONArray("tasks");
                    copy.setCopyTaskId(tasks.getJSONObject(0).getString("id"));
                    copy.setCopyStatus("1");
                    copyHelper.addCopy(copy);
                }
            }
        } else if (200 == jsonObject.getInteger("code")) {
            flag.set(true);
            copy.setCopyStatus("3");
            copyHelper.addCopy(copy);
        }

        if (flag.get() && "1".equals(config.getOpenListCopyStrm())) {
            asynHelper.isCopyDoneOneFile(dstDir + "/" + relativePath, copy);
        }

    }

    public void syncFiles(String srcDir, String dstDir, String relativePath, Set<OpenlistCopy> copySet) {
        syncFilesRecursion(srcDir, dstDir, relativePath, copySet);
    }

    public void syncFiles(String srcDir, String dstDir, String relativePath) {
        Set<OpenlistCopy> copySet = ConcurrentHashMap.newKeySet();
        syncFilesRecursion(srcDir, dstDir, relativePath, copySet);
        if ("1".equals(config.getOpenListCopyStrm())) {
            asynHelper.isCopyDone(dstDir, relativePath, copySet);
        }
    }

    public void syncFiles(String srcDir, String dstDir) {
        Set<OpenlistCopy> copySet = ConcurrentHashMap.newKeySet();
        syncFilesRecursion(srcDir, dstDir, "", copySet);
        if ("1".equals(config.getOpenListCopyStrm())) {
            asynHelper.isCopyDone(dstDir, "", copySet);
        }
    }


}
