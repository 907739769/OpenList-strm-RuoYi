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
import java.util.TimerTask;

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

    /**
     * strm处理一个目录
     *
     * @param path
     */
    public void strmDir(String path) {
        log.info("开始执行指定路径strm任务{}", LocalDateTime.now());
        try {
            getData(path, outputDir + File.separator + path.replace("/", File.separator));
        } catch (Exception e) {
            log.error("", e);
        } finally {
            log.info("strm任务执行完成{}", LocalDateTime.now());
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

    public void getData(String path, String localPath) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.lastIndexOf("/"));
        }

        File outputDirFile = new File(localPath);
        outputDirFile.mkdirs();

        JSONObject jsonObject = openListApi.getOpenlist(path);
        if (jsonObject != null && 200 == jsonObject.getInteger("code")) {
            JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("content");
            if (jsonArray == null) {
                return;
            }

            for (Object obj : jsonArray) {
                JSONObject object = (JSONObject) obj;
                String name = object.getString("name");
                if (object.getBoolean("is_dir")) {
                    String newLocalPath = localPath + File.separator + (name.length() > 255 ? name.substring(0, 250) : name);
                    File file = new File(newLocalPath);
                    if (!file.exists()) {
                        file.mkdirs();
                    }
                    getData(path + "/" + name, newLocalPath);
                } else {
                    //判断是否处理过
                    if (strmHelper.exitStrm(path, name)) {
                        log.info("文件已处理过，跳过处理" + path + "/" + name);
                        continue;
                    }
                    //视频文件
                    if (openListHelper.isVideo(name)) {
                        String finalPath = path;
                        //异步处理 提升效率
                        AsyncManager.me().execute(new TimerTask() {
                            @Override
                            public void run() {
                                String fileName = name.substring(0, name.lastIndexOf(".")).replaceAll("[\\\\/:*?\"<>|]", "");
                                try (FileWriter writer = new FileWriter(localPath + File.separator + (fileName.length() > 255 ? fileName.substring(0, 250) : fileName) + ".strm")) {
                                    String encodePath = finalPath + "/" + name;
                                    if ("1".equals(encode)) {
                                        encodePath = URLEncoder.encode(finalPath + "/" + name, "UTF-8").replace("+", "%20").replace("%2F", "/");
                                    }
                                    writer.write(config.getOpenListUrl() + "/d" + encodePath);
                                    strmHelper.addStrm(finalPath, name, "1");
                                } catch (Exception e) {
                                    log.error("", e);
                                    strmHelper.addStrm(finalPath, name, "0");
                                }
                            }
                        });
                    }

                    //字幕文件
                    if ("1".equals(isDownSub) && openListHelper.isSrt(name)) {
                        String url = openListApi.getFile(path + "/" + name).getJSONObject("data").getString("raw_url");
                        String fileName = name.replaceAll("[\\\\/:*?\"<>|]", "");
                        try {
                            downloadFile(url, localPath + File.separator + (fileName.length() > 255 ? fileName.substring(0, 250) : fileName) + name.substring(name.lastIndexOf(".")));
                            strmHelper.addStrm(path, name, "1");
                        } catch (Exception e) {
                            log.error("", e);
                            strmHelper.addStrm(path, name, "0");
                        }

                    }
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
