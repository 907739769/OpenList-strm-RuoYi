package com.ruoyi.openliststrm.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.helper.StrmHelper;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmPlusService;
import com.ruoyi.openliststrm.service.IStrmService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TimerTask;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StrmServiceImpl implements IStrmService {

    @Autowired
    private OpenlistConfig config;

    @Autowired
    private StrmHelper strmHelper;

    @Autowired
    private OpenListHelper openListHelper;

    @Autowired
    private OpenlistApi openListApi;

    @Autowired
    private IOpenlistStrmPlusService openlistStrmPlusService;

    @Autowired
    private IOpenlistCopyPlusService openlistCopyPlusService;

    private static final Pattern ILLEGAL_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");

    private String getOutputDir() {
        String dir = config.getOpenListStrmOutputDir();
        return StringUtils.isNotBlank(dir) ? dir : "/data/strm";
    }

    private boolean shouldEncode() {
        return "1".equals(config.getOpenListStrmEncode());
    }

    private boolean shouldDownloadSub() {
        return "1".equals(config.getOpenListStrmDownloadSub());
    }

    private long getMinFileSizeBytes() {
        try {
            return Long.parseLong(config.getOpenListMinFileSize()) * 1024 * 1024;
        } catch (Exception e) {
            return 1 * 1024 * 1024;
        }
    }

    @Override
    public void strmDir(String path) {
        log.info("开始执行指定路径strm任务: {}", path);
        try {
            getData(path, getOutputDir());
        } catch (Exception e) {
            log.error("", e);
        } finally {
            log.info("strm任务执行完成: {}", path);
        }
    }

    @Override
    public void strmOneFile(String path) {
        log.info("开始执行指定文件strm任务: {}", path);
        String filePath = "";
        String name = path;
        if (path.contains("/")) {
            filePath = path.substring(0, path.lastIndexOf("/"));
            name = path.substring(path.lastIndexOf("/") + 1);
        }

        if (strmHelper.exitStrm(filePath, name)) {
            log.debug("文件已处理过，跳过处理{}", path);
            return;
        }
        String fileName = path.substring(path.lastIndexOf("/") + 1, path.lastIndexOf(".")).replaceAll("[\\\\/:*?\"<>|]", "");
        File file = new File(getOutputDir() + File.separator + filePath.replace("/", File.separator));
        if (!file.exists()) {
            file.mkdirs();
        }
        String relative = filePath.startsWith("/")
                ? filePath.substring(1)
                : filePath;
        Path strmFile = Paths.get(getOutputDir())
                .resolve(relative.replace("/", File.separator))
                .resolve((fileName.length() > 255 ? fileName.substring(0, 250) : fileName) + ".strm");
        try {
            String encodePath = path;
            if (shouldEncode()) {
                encodePath = URLEncoder.encode(path, StandardCharsets.UTF_8.name())
                        .replace("+", "%20")
                        .replace("%2F", "/");
            }
            String content = config.getOpenListUrl() + "/d" + encodePath;
            writeAtomically(strmFile, content);
            strmHelper.addStrm(filePath, name, "1");
        } catch (Exception e) {
            log.error("生成 .strm 文件失败 {}", strmFile, e);
            strmHelper.addStrm(filePath, name, "0");
        }
        log.info("执行指定文件strm任务完成: {}", path);
    }

    @Override
    public void batchRemoveNetDisk(List<String> idList) {
        if (idList == null || idList.isEmpty()) return;
        List<OpenlistStrmPlus> strmList = openlistStrmPlusService.listByIds(idList);
        Runnable action = () -> strmList.forEach(strm -> {
            openListApi.fsRemove(strm.getStrmPath(), Collections.singletonList(strm.getStrmFileName()));
            openlistCopyPlusService.remove(new LambdaQueryWrapper<OpenlistCopyPlus>()
                    .eq(OpenlistCopyPlus::getCopyDstFileName, strm.getStrmFileName())
                    .eq(OpenlistCopyPlus::getCopyDstPath, strm.getStrmPath()));
        });
        if (idList.size() > 20) {
            AsyncManager.me().execute(new TimerTask() {
                @Override
                public void run() {
                    action.run();
                    openlistStrmPlusService.removeBatchByIds(idList);
                }
            });
        } else {
            action.run();
            openlistStrmPlusService.removeBatchByIds(idList);
        }
    }

    @Override
    public void retryStrm(List<String> idList) {
        if (idList == null || idList.isEmpty()) return;
        List<OpenlistStrmPlus> strmList = openlistStrmPlusService.listByIds(idList);
        strmList.forEach(strm -> strm.setStrmStatus("0"));
        openlistStrmPlusService.updateBatchById(strmList);
        Runnable action = () -> strmList.forEach(strm ->
                strmOneFile(strm.getStrmPath() + "/" + strm.getStrmFileName()));
        if (idList.size() > 20) {
            AsyncManager.me().execute(new TimerTask() {
                @Override
                public void run() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    public void getData(String rootPath, String localRootPath) {
        Queue<String> dirsQueue = new LinkedList<>();
        dirsQueue.add(rootPath);

        while (!dirsQueue.isEmpty()) {
            String currentPath = dirsQueue.poll();
            currentPath = StringUtils.removeEnd(currentPath, "/");
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
                    String finalCurrentPath = currentPath;
                    AsyncManager.me().execute(new TimerTask() {
                        @Override
                        public void run() {
                            if (strmHelper.exitStrm(finalCurrentPath, rawName)) {
                                if (log.isDebugEnabled()) {
                                    log.debug("文件已处理过，跳过处理 {} / {}", finalCurrentPath, rawName);
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

                            if (openListHelper.isVideo(rawName)) {
                                if (size < getMinFileSizeBytes()) {
                                    log.debug("Skipping small file {} ({} bytes)", rawName, size);
                                    return;
                                }

                                Path strmFile = Paths.get(currentLocalPath).resolve(fileName + ".strm");
                                try {
                                    String encodePath = finalCurrentPath + "/" + rawName;
                                    if (shouldEncode()) {
                                        encodePath = URLEncoder.encode(encodePath, StandardCharsets.UTF_8.name())
                                                .replace("+", "%20")
                                                .replace("%2F", "/");
                                    }
                                    String content = config.getOpenListUrl() + "/d" + encodePath;
                                    writeAtomically(strmFile, content);
                                    strmHelper.addStrm(finalCurrentPath, rawName, "1");
                                } catch (Exception e) {
                                    log.error("写入 .strm 文件失败 {}", strmFile, e);
                                    strmHelper.addStrm(finalCurrentPath, rawName, "0");
                                }
                            }

                            if (shouldDownloadSub() && openListHelper.isSrt(rawName)) {
                                try {
                                    JSONObject fileJson = openListApi.getFile(finalCurrentPath + "/" + rawName);
                                    if (fileJson != null && fileJson.getJSONObject("data") != null) {
                                        String url = fileJson.getJSONObject("data").getString("raw_url");
                                        File outFile = new File(currentLocalPath + File.separator + fileName + rawName.substring(rawName.lastIndexOf(".")));
                                        downloadFile(url, outFile.getAbsolutePath());
                                        strmHelper.addStrm(finalCurrentPath, rawName, "1");
                                    }
                                } catch (Exception e) {
                                    log.error("下载字幕失败 {} / {}", finalCurrentPath, rawName, e);
                                    strmHelper.addStrm(finalCurrentPath, rawName, "0");
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
        URL url;
        try {
            url = new URL(fileURL);
        } catch (Exception e) {
            log.error("文件{}下载失败: 无效URL", fileURL);
            throw new RuntimeException("Invalid URL: " + fileURL, e);
        }
        URLConnection connection;
        try {
            connection = url.openConnection();
        } catch (IOException e) {
            log.error("文件{}下载失败: 无法建立连接", fileURL);
            throw new RuntimeException("Cannot open connection: " + fileURL, e);
        }
        try (InputStream inputStream = new BufferedInputStream(connection.getInputStream());
             FileOutputStream outputStream = new FileOutputStream(saveDir)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException ex) {
            log.error("文件{}下载失败: IO错误", fileURL);
            throw new RuntimeException("Download failed: " + fileURL, ex);
        }
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tmpFile = target.resolveSibling(target.getFileName().toString() + ".tmp");
        boolean moved = false;
        try {
            Files.write(tmpFile, content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmpFile, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            moved = true;
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(tmpFile);
            }
        }
    }

}
