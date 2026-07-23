package com.ruoyi.openliststrm.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.PageResult;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloadRecordPlusService;
import com.ruoyi.openliststrm.pt.subscription.dto.SupplementResult;
import com.ruoyi.openliststrm.pt.task.DownloadRecordAdminService;
import com.ruoyi.openliststrm.pt.task.dto.DownloadRecordView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PT 下载记录 REST API 控制器：只读列表 + 失败重试，不提供增删改（记录由下载追踪流程自动生成）。
 *
 * @author Jack
 */
@RestController
@RequestMapping("/api/openliststrm/pt-download-records")
public class PtDownloadRecordRestController extends BaseController {

    @Autowired
    private IPtDownloadRecordPlusService recordService;

    @Autowired
    private DownloadRecordAdminService adminService;

    @GetMapping({"", "/list"})
    public Result<PageResult<DownloadRecordView>> list(@RequestParam(required = false) Integer subId,
                                                        @RequestParam(required = false) String state,
                                                        @RequestParam(required = false) String title) {
        LambdaQueryWrapper<PtDownloadRecordPlus> wrapper = new LambdaQueryWrapper<>();
        if (subId != null) {
            wrapper.eq(PtDownloadRecordPlus::getSubId, subId);
        }
        if (StringUtils.isNotBlank(state)) {
            wrapper.eq(PtDownloadRecordPlus::getState, state);
        }
        if (StringUtils.isNotBlank(title)) {
            wrapper.like(PtDownloadRecordPlus::getTitle, title);
        }
        wrapper.orderByDesc(PtDownloadRecordPlus::getId);
        PageResult<PtDownloadRecordPlus> page = selectPage(recordService.getBaseMapper(), wrapper);
        return Result.success(adminService.enrich(page));
    }

    /**
     * 立即重试一条失败的下载记录：按订阅标题+季/集号重新发起一次搜索补集。
     */
    @PostMapping("/{id}/retry")
    public Result<SupplementResult> retry(@PathVariable("id") Integer id) {
        try {
            return Result.success(adminService.retry(id));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
