package com.ruoyi.openliststrm.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StrmServiceImpl implements IStrmService {

    /** BFS遍历收集的文件条目 */
    private record FileEntry(String path, String localPath, String name, long size) {}

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

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final Pattern ILLEGAL_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");

    /** STRM文件处理并发度控制，最多10个虚拟线程同时处理 */
    private static final Semaphore STRM_SEMAPHORE = new Semaphore(10);

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

    @Override
    public void strmDir(String path) {
        log.info("开始执行指定路径strm任务: {}", path);
        try {
            getData(path, getOutputDir());
        } catch (Exception e) {
            log.error("strm任务执行异常: {}", path, e);
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

        if (strmHelper.existsStrm(filePath, name)) {
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
        // 外部API调用在事务外执行
        Runnable externalAction = () -> strmList.forEach(strm -> {
            openListApi.fsRemove(strm.getStrmPath(), Collections.singletonList(strm.getStrmFileName()));
        });
        // 数据库操作在事务内执行
        Runnable dbAction = () -> {
            strmList.forEach(strm ->
                openlistCopyPlusService.remove(new LambdaQueryWrapper<OpenlistCopyPlus>()
                        .eq(OpenlistCopyPlus::getCopyDstFileName, strm.getStrmFileName())
                        .eq(OpenlistCopyPlus::getCopyDstPath, strm.getStrmPath())));
            openlistStrmPlusService.removeBatchByIds(idList);
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
    public void retryStrm(List<String> idList) {
        if (idList == null || idList.isEmpty()) return;
        List<OpenlistStrmPlus> strmList = openlistStrmPlusService.listByIds(idList);
        strmList.forEach(strm -> strm.setStrmStatus("0"));
        openlistStrmPlusService.updateBatchById(strmList);
        Runnable action = () -> {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = strmList.stream()
                    .map(strm -> CompletableFuture.runAsync(() -> {
                        try {
                            STRM_SEMAPHORE.acquire();
                            try {
                                strmOneFile(strm.getStrmPath() + "/" + strm.getStrmFileName());
                            } finally {
                                STRM_SEMAPHORE.release();
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

    public void getData(String rootPath, String localRootPath) {
        // 第一阶段：BFS遍历收集所有待处理文件
        List<FileEntry> fileEntries = new java.util.ArrayList<>();
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
                    fileEntries.add(new FileEntry(currentPath, currentLocalPath, rawName, size));
                }
            }
        }

        log.info("BFS遍历完成，共收集到 {} 个待处理文件", fileEntries.size());

        // 第二阶段：虚拟线程并行处理文件
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = fileEntries.stream()
                .map(entry -> CompletableFuture.runAsync(() -> {
                    try {
                        STRM_SEMAPHORE.acquire();
                        try {
                            processFileEntry(entry);
                        } finally {
                            STRM_SEMAPHORE.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, executor))
                .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        log.info("STRM文件并行处理完成，共处理 {} 个文件", fileEntries.size());
    }

    /**
     * 处理单个文件条目（从 getData 中提取出的文件处理逻辑）
     */
    private void processFileEntry(FileEntry entry) {
        String currentPath = entry.path();
        String currentLocalPath = entry.localPath();
        String rawName = entry.name();
        long size = entry.size();

        if (strmHelper.existsStrm(currentPath, rawName)) {
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

        if (openListHelper.isVideo(rawName)) {
            if (size < config.getMinFileSizeBytes()) {
                log.debug("Skipping small file {} ({} bytes)", rawName, size);
                return;
            }

            Path strmFile = Paths.get(currentLocalPath).resolve(fileName + ".strm");
            try {
                String encodePath = currentPath + "/" + rawName;
                if (shouldEncode()) {
                    encodePath = URLEncoder.encode(encodePath, StandardCharsets.UTF_8.name())
                            .replace("+", "%20")
                            .replace("%2F", "/");
                }
                String content = config.getOpenListUrl() + "/d" + encodePath;
                writeAtomically(strmFile, content);
                strmHelper.addStrm(currentPath, rawName, "1");
            } catch (Exception e) {
                log.error("写入 .strm 文件失败 {}", strmFile, e);
                strmHelper.addStrm(currentPath, rawName, "0");
            }
        }

        if (shouldDownloadSub() && openListHelper.isSrt(rawName)) {
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

    // SSRF防护：验证URL
    private static void validateUrl(String fileURL) {
        try {
            java.net.URI uri = new java.net.URI(fileURL);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("不允许的URL协议: " + scheme);
            }
            String host = uri.getHost();
            if (host != null) {
                java.net.InetAddress addr = java.net.InetAddress.getByName(host);
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                    throw new IllegalArgumentException("不允许访问内网地址: " + host);
                }
            }
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("无效的URL: " + fileURL);
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("无法解析主机: " + fileURL);
        }
    }

    public static void downloadFile(String fileURL, String saveDir) {
        if (StringUtils.isBlank(fileURL)) {
            return;
        }
        validateUrl(fileURL);
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
