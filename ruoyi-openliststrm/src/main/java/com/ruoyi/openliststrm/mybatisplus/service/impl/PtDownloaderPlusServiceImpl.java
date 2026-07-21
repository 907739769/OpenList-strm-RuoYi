package com.ruoyi.openliststrm.mybatisplus.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.mapper.PtDownloaderPlusMapper;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyTaskPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloaderPlusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * <p>
 * PT 下载器配置 服务实现类
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
@Service
public class PtDownloaderPlusServiceImpl extends ServiceImpl<PtDownloaderPlusMapper, PtDownloaderPlus> implements IPtDownloaderPlusService {

    @Autowired
    private IOpenlistCopyTaskPlusService copyTaskService;

    @Override
    public String validateSavePath(String savePath) {
        if (StringUtils.isBlank(savePath)) {
            return "保存路径不能为空";
        }
        Path target = Paths.get(savePath).toAbsolutePath().normalize();
        List<OpenlistCopyTaskPlus> tasks = copyTaskService.list();
        for (OpenlistCopyTaskPlus task : tasks) {
            // 只认启用中的同步任务：停用的任务不会启动 FileMonitor，落在它目录下的文件同样不会被上传
            if (!"1".equals(task.getCopyTaskStatus())) {
                continue;
            }
            String monitorDir = task.getMonitorDir();
            if (StringUtils.isBlank(monitorDir)) {
                continue;
            }
            Path dir = Paths.get(monitorDir).toAbsolutePath().normalize();
            if (target.startsWith(dir)) {
                return null;
            }
        }
        return "保存路径不在任何已启用文件同步任务的监听目录之下，下载完成后不会被自动上传";
    }
}
