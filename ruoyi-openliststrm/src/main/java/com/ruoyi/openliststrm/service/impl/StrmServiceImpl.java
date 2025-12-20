package com.ruoyi.openliststrm.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.helper.StrmHelper;
import com.ruoyi.openliststrm.service.IStrmService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.Queue;
import java.util.TimerTask;
import java.util.regex.Pattern;

/**
 * @Author Jack
 * @Date 2024/6/10 20:13
 * @Version 1.0.0
 */
@Service
@Slf4j
public class StrmServiceImpl implements IStrmService {

    @Autowired
    private OpenlistConfig config;

    @Autowired
    private StrmHelper strmHelper;

    @Autowired
    private OpenListHelper openListHelper;

    private final String outputDir = "/data/strm";

    private final String encode = "0";

    private final String isDownSub = "0";

    @Autowired
    private OpenlistApi openListApi;

    private static final Pattern ILLEGAL_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");

    /**
     * strm处理一个目录
     *
     * @param path
     */
    public void strmDir(String path) {
        log.info("开始执行指定路径strm任务:{}", path);
        try {
            getData(path, outputDir);
        } catch (Exception e) {
            log.error("", e);
        } finally {
            log.info("strm任务执行完成{}:", path);
        }
    }

    /**
     * strm处理一个文件
     *
     * @param path
     */
    public void strmOneFile(String path) {
        String filePath = "";
        String name = path;
        if (path.contains("/")) {
            filePath = path.substring(0, path.lastIndexOf("/"));
            name = path.substring(path.lastIndexOf("/") + 1);
        }

        //判断是否处理过
        if (strmHelper.exitStrm(filePath, name)) {
            log.info("文件已处理过，跳过处理{}", path);
            return;
        }
        String fileName = path.substring(path.lastIndexOf("/") + 1, path.lastIndexOf(".")).replaceAll("[\\\\/:*?\"<>|]", "");
        File file = new File(outputDir + File.separator + filePath.replace("/", File.separator));
        if (!file.exists()) {
            file.mkdirs();
        }
        String finalPath=filePath;
        try (FileWriter writer = new FileWriter(outputDir + File.separator + finalPath.replace("/", File.separator) + File.separator + (fileName.length() > 255 ? fileName.substring(0, 250) : fileName) + ".strm")) {
            String encodePath = path;
            if ("1".equals(encode)) {
                encodePath = URLEncoder.encode(path, "UTF-8").replace("+", "%20").replace("%2F", "/");
            }
            writer.write(config.getOpenListUrl() + "/d" + encodePath);
            strmHelper.addStrm(filePath, name, "1");
        } catch (Exception e) {
            log.error("", e);
            strmHelper.addStrm(filePath, name, "0");
        }
    }

    public void getData(String rootPath, String localRootPath) {
        // 队列存目录信息
        Queue<String> dirsQueue = new LinkedList<>();
        dirsQueue.add(rootPath);

        while (!dirsQueue.isEmpty()) {
            String currentPath = dirsQueue.poll();
            String currentLocalPath = localRootPath + File.separator + currentPath.replace("/", File.separator);
            File currentDir = new File(currentLocalPath);
            if (!currentDir.exists()) {
                currentDir.mkdirs();
            }

            JSONObject jsonObject = openListApi.getOpenlist(currentPath);
            if (jsonObject == null || jsonObject.getInteger("code") != 200) {
                continue;
            }

            JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("content");
            if (jsonArray == null) {
                continue;
            }

            for (Object obj : jsonArray) {
                JSONObject object = (JSONObject) obj;
                String rawName = object.getString("name");
                boolean isDir = object.getBoolean("is_dir");
                long size = object.getLongValue("size");

                if (isDir) {
                    dirsQueue.add(currentPath + "/" + rawName);
                } else {
                    //异步处理 提升效率
                    AsyncManager.me().execute(new TimerTask() {
                        @Override
                        public void run() {
                            // 判断是否处理过
                            if (strmHelper.exitStrm(currentPath, rawName)) {
                                if (log.isDebugEnabled()) {
                                    log.debug("文件已处理过，跳过处理 {} / {}", currentPath, rawName);
                                }
                                return;
                            }

                            if (!openListHelper.isVideo(rawName) && !openListHelper.isSrt(rawName)) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Skipping no media file {}", rawName);
                                }
                                return;
                            }

                            int dot = rawName.lastIndexOf('.');
                            String baseName = (dot > 0) ? rawName.substring(0, dot) : rawName;
                            String safeName = ILLEGAL_PATTERN.matcher(baseName).replaceAll("");
                            String fileName = safeName.length() > 255 ? safeName.substring(0, 250) : safeName;

                            // 视频文件处理
                            if (openListHelper.isVideo(rawName)) {
                                if (size < Long.parseLong(config.getOpenListMinFileSize()) * 1024 * 1024) {
                                    log.debug("Skipping small file {} ({} bytes)", rawName, size);
                                    return;
                                }

                                File outFile = new File(currentLocalPath + File.separator + fileName + ".strm");
                                try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
                                    String encodePath = currentPath + "/" + rawName;
                                    if ("1".equals(encode)) {
                                        encodePath = URLEncoder.encode(encodePath, "UTF-8").replace("+", "%20").replace("%2F", "/");
                                    }
                                    writer.write(config.getOpenListUrl() + "/d" + encodePath);
                                    strmHelper.addStrm(currentPath, rawName, "1");
                                } catch (IOException e) {
                                    log.error("写入 .strm 文件失败 {}", outFile.getAbsolutePath(), e);
                                    strmHelper.addStrm(currentPath, rawName, "0");
                                }
                            }

                            // 字幕文件处理（异步限流）
                            if ("1".equals(isDownSub) && openListHelper.isSrt(rawName)) {
                                try {
                                    JSONObject fileJson = openListApi.getFile(currentPath + "/" + rawName);
                                    if (fileJson != null && fileJson.getJSONObject("data") != null) {
                                        String url = fileJson.getJSONObject("data").getString("raw_url");
                                        File outFile = new File(currentLocalPath + File.separator + fileName + rawName.substring(rawName.lastIndexOf(".")));
                                        downloadFile(url, outFile.getAbsolutePath());
                                        strmHelper.addStrm(currentPath, rawName, "1");
                                    }
                                } catch (Exception e) {
                                    log.error("下载字幕失败 {} / {}", currentPath, rawName, e);
                                    strmHelper.addStrm(currentPath, rawName, "0");
                                }
                            }
                        }
                    });
                }
            }

        }

    }

    public static void downloadFile(String fileURL, String saveDir) {
        if (StringUtils.isBlank(fileURL)) {
            return;
        }
        // 创建URL对象
        URL url = null;
        try {
            url = new URL(fileURL);
        } catch (Exception e) {
            log.error("文件{}下载失败1", fileURL);
            throw new RuntimeException(e);
        }
        // 打开连接
        URLConnection connection = null;
        try {
            connection = url.openConnection();
        } catch (IOException e) {
            log.error("文件{}下载失败2", fileURL);
            throw new RuntimeException(e);
        }
        try (InputStream inputStream = new BufferedInputStream(connection.getInputStream()); FileOutputStream outputStream = new FileOutputStream(saveDir)) {

            byte[] buffer = new byte[1024];
            int bytesRead = -1;

            // 读取文件并写入到本地
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

        } catch (IOException ex) {
            log.error("文件{}下载失败3", fileURL);
            log.error("", ex);
            throw new RuntimeException(ex);
        }
    }

}
