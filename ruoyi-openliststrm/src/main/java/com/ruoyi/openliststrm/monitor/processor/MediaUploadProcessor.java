package com.ruoyi.openliststrm.monitor.processor;

import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.openliststrm.service.ICopyService;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: Jack
 * @creat: 2026/1/13 21:13
 */
@Slf4j
public class MediaUploadProcessor implements FileProcessor {

    private final String copyTaskSrc;
    private final String copyTaskDst;
    private final String monitorDir;
    private final ICopyService copyService;

    private final Set<Path> processing = ConcurrentHashMap.newKeySet();

    public MediaUploadProcessor(String copyTaskSrc, String copyTaskDst, String monitorDir, ICopyService copyService) {
        this.copyTaskSrc = copyTaskSrc;
        this.copyTaskDst = copyTaskDst;
        this.monitorDir = monitorDir;
        this.copyService = copyService;
    }

    @Override
    public void process(Path file) {
        Path p = file.toAbsolutePath().normalize();
        //判断文件是否还在写入中
        if (!FileUtils.isFileStable(p)) {
            log.debug("文件仍在写入，稍后再试：{}", p);
            return;
        }
        if (!processing.add(p)) {
            log.debug("Skip duplicate processing {}", p);
            return;
        }
        try {
            copyService.syncOneFile(copyTaskSrc, copyTaskDst, file.toAbsolutePath().toString().replace(monitorDir, ""));
        } catch (Exception e) {
            log.error("process failed {}", p, e);
        } finally {
            processing.remove(p);
        }
    }


}
