package com.ruoyi.openliststrm.orphan;

import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameOrphanPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameOrphanPlusService;
import com.ruoyi.openliststrm.scrape.ScrapeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class RenameOrphanScanServiceImpl implements IRenameOrphanScanService {

    @Autowired
    IRenameDetailPlusService renameDetailService;

    @Autowired
    IRenameOrphanPlusService renameOrphanService;

    @Autowired
    OpenlistApi openListApi;

    @Autowired
    OpenlistConfig config;

    @Autowired
    ScrapeService scrapeService;

    @Override
    public ScanSummary scan() {
        // 任务6实现
        throw new UnsupportedOperationException("待任务6实现");
    }

    @Override
    public void clean(List<Integer> orphanIds) {
        if (orphanIds == null || orphanIds.isEmpty()) {
            return;
        }
        List<RenameOrphanPlus> orphans = renameOrphanService.listByIds(orphanIds);
        Date now = new Date();
        for (RenameOrphanPlus orphan : orphans) {
            RenameDetailPlus detail = renameDetailService.getById(orphan.getDetailId());
            if (detail != null) {
                if ("source_missing".equals(orphan.getReason())) {
                    deleteLocalFile(detail);
                }
                scrapeService.deleteScrapeFiles(detail);
                renameDetailService.removeById(detail.getId());
            }
            orphan.setStatus("1");
            orphan.setCleanTime(now);
        }
        renameOrphanService.updateBatchById(orphans);
    }

    @Override
    public void ignore(List<Integer> orphanIds) {
        if (orphanIds == null || orphanIds.isEmpty()) {
            return;
        }
        List<RenameOrphanPlus> orphans = renameOrphanService.listByIds(orphanIds);
        Date now = new Date();
        orphans.forEach(o -> {
            o.setStatus("2");
            o.setCleanTime(now);
        });
        renameOrphanService.updateBatchById(orphans);
    }

    private void deleteLocalFile(RenameDetailPlus detail) {
        try {
            Path file = Paths.get(detail.getNewPath(), detail.getNewName());
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("删除本地strm文件失败: {}/{}", detail.getNewPath(), detail.getNewName(), e);
        }
    }
}
