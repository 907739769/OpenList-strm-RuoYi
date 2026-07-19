package com.ruoyi.openliststrm.orphan;

import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameOrphanPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameOrphanPlusService;
import com.ruoyi.openliststrm.scrape.ScrapeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RenameOrphanScanServiceImplTest {

    @Mock
    private IRenameDetailPlusService renameDetailService;
    @Mock
    private IRenameOrphanPlusService renameOrphanService;
    @Mock
    private OpenlistApi openListApi;
    @Mock
    private OpenlistConfig config;
    @Mock
    private ScrapeService scrapeService;

    private RenameOrphanScanServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RenameOrphanScanServiceImpl();
        service.renameDetailService = renameDetailService;
        service.renameOrphanService = renameOrphanService;
        service.openListApi = openListApi;
        service.config = config;
        service.scrapeService = scrapeService;
    }

    @Test
    void clean_原因是网盘源丢失_删除本地文件并清理刮削产物和明细记录() {
        RenameOrphanPlus orphan = new RenameOrphanPlus();
        orphan.setId(1);
        orphan.setDetailId(42);
        orphan.setReason("source_missing");
        when(renameOrphanService.listByIds(List.of(1))).thenReturn(List.of(orphan));

        RenameDetailPlus detail = new RenameDetailPlus();
        detail.setId(42);
        detail.setNewPath("/nonexistent/dir/for/test");
        detail.setNewName("does-not-exist.strm");
        when(renameDetailService.getById(42)).thenReturn(detail);

        service.clean(List.of(1));

        verify(scrapeService).deleteScrapeFiles(detail);
        verify(renameDetailService).removeById(42);
        verify(renameOrphanService).updateBatchById(argThat(list -> {
            RenameOrphanPlus updated = list.iterator().next();
            return "1".equals(updated.getStatus()) && updated.getCleanTime() != null;
        }));
    }

    @Test
    void clean_原因是本地文件已丢失_不重复删除本地文件仅清理刮削产物和明细记录() {
        RenameOrphanPlus orphan = new RenameOrphanPlus();
        orphan.setId(2);
        orphan.setDetailId(43);
        orphan.setReason("local_missing");
        when(renameOrphanService.listByIds(List.of(2))).thenReturn(List.of(orphan));

        RenameDetailPlus detail = new RenameDetailPlus();
        detail.setId(43);
        detail.setNewPath("/data/media/whatever");
        detail.setNewName("whatever.strm");
        when(renameDetailService.getById(43)).thenReturn(detail);

        service.clean(List.of(2));

        verify(scrapeService).deleteScrapeFiles(detail);
        verify(renameDetailService).removeById(43);
    }

    @Test
    void ignore_批量标记为已忽略并写清理时间() {
        RenameOrphanPlus orphan = new RenameOrphanPlus();
        orphan.setId(5);
        orphan.setStatus("0");
        when(renameOrphanService.listByIds(List.of(5))).thenReturn(List.of(orphan));

        service.ignore(List.of(5));

        verify(renameOrphanService).updateBatchById(argThat(list -> {
            RenameOrphanPlus updated = list.iterator().next();
            return "2".equals(updated.getStatus()) && updated.getCleanTime() != null;
        }));
    }
}
