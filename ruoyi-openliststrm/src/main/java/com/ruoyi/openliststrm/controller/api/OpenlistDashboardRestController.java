package com.ruoyi.openliststrm.controller.api;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.enums.CopyStatusEnum;
import com.ruoyi.openliststrm.enums.StrmStatusEnum;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

/**
 * 仪表盘统计 REST API控制器
 */
@RestController
@RequestMapping("/api/openliststrm/dashboard")
@Anonymous
@CrossOrigin
public class OpenlistDashboardRestController {

    private static final Logger log = LoggerFactory.getLogger(OpenlistDashboardRestController.class);

    @Autowired
    private IOpenlistCopyPlusService openlistCopyPlusService;

    @Autowired
    private IOpenlistStrmPlusService openlistStrmPlusService;

    @Autowired
    private IRenameDetailPlusService renameDetailPlusService;

    /**
     * 获取仪表盘统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            stats.put("strmRecordCount", openlistStrmPlusService.count());
            stats.put("copyRecordCount", openlistCopyPlusService.count());
            stats.put("renameDetailCount", renameDetailPlusService.count());
        } catch (Exception e) {
            log.error("获取仪表盘统计失败", e);
        }
        return Result.success(stats);
    }

    @PostMapping("/copy/stats")
    public Result<Map<String, Long>> copyStats(@RequestParam(value = "range", defaultValue = "today") String range) {
        Map<String, Long> result = getStatsByStatus("copy", range);
        return Result.success(result);
    }

    @PostMapping("/strm/stats")
    public Result<Map<String, Long>> strmStats(@RequestParam(value = "range", defaultValue = "today") String range) {
        Map<String, Long> result = getStatsByStatus("strm", range);
        return Result.success(result);
    }

    @PostMapping("/renameDetail/stats")
    public Result<Map<String, Long>> renameStats(@RequestParam(value = "range", defaultValue = "today") String range) {
        Map<String, Long> result = getStatsByStatus("rename", range);
        return Result.success(result);
    }

    private Map<String, Long> getStatsByStatus(String type, String range) {
        LocalDate today = LocalDate.now();
        Map<String, Long> result = new LinkedHashMap<>();

        if ("copy".equals(type)) {
            var wrapper = Wrappers.<OpenlistCopyPlus>query();
            wrapper.select("copy_status as status, count(*) as count")
                    .between(StringUtils.isEmpty(range) || "today".equals(range), "create_time", today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                    .between("yesterday".equals(range), "create_time", today.minusDays(1).atStartOfDay(), today.atStartOfDay())
                    .groupBy("copy_status");
            List<Map<String, Object>> maps = openlistCopyPlusService.listMaps(wrapper);
            for (Map<String, Object> map : maps) {
                String status = String.valueOf(map.get("status"));
                Long count = Long.parseLong(map.get("count").toString());
                result.put(CopyStatusEnum.getDescByCode(status), count);
            }
        } else if ("strm".equals(type)) {
            var wrapper = Wrappers.<OpenlistStrmPlus>query();
            wrapper.select("strm_status as status, count(*) as count")
                    .between(StringUtils.isEmpty(range) || "today".equals(range), "create_time", today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                    .between("yesterday".equals(range), "create_time", today.minusDays(1).atStartOfDay(), today.atStartOfDay())
                    .groupBy("strm_status");
            List<Map<String, Object>> maps = openlistStrmPlusService.listMaps(wrapper);
            for (Map<String, Object> map : maps) {
                String status = String.valueOf(map.get("status"));
                Long count = Long.parseLong(map.get("count").toString());
                result.put(StrmStatusEnum.getDescByCode(status), count);
            }
        } else if ("rename".equals(type)) {
            var wrapper = Wrappers.<RenameDetailPlus>query();
            wrapper.select("status as status, count(*) as count")
                    .between(StringUtils.isEmpty(range) || "today".equals(range), "create_time", today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                    .between("yesterday".equals(range), "create_time", today.minusDays(1).atStartOfDay(), today.atStartOfDay())
                    .groupBy("status");
            List<Map<String, Object>> maps = renameDetailPlusService.listMaps(wrapper);
            for (Map<String, Object> map : maps) {
                String status = String.valueOf(map.get("status"));
                Long count = Long.parseLong(map.get("count").toString());
                result.put(StrmStatusEnum.getDescByCode(status), count);
            }
        }
        return result;
    }
}
