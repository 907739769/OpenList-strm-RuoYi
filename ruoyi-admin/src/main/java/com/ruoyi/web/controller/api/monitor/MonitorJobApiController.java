package com.ruoyi.web.controller.api.monitor;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageInfo;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.PageResult;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.exception.job.TaskException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.quartz.domain.SysJob;
import com.ruoyi.quartz.service.ISysJobService;
import com.ruoyi.quartz.util.CronUtils;
import com.ruoyi.quartz.util.ScheduleUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * 定时任务监控REST API控制器
 *
 * @author ruoyi
 */
@Tag(name = "定时任务监控API")
@RestController
@RequestMapping("/api/monitor/job")
@Anonymous
@CrossOrigin(origins = "*")
public class MonitorJobApiController extends BaseController
{
    @Autowired
    private ISysJobService jobService;

    /**
     * 查询定时任务分页列表
     */
    @Operation(summary = "查询定时任务分页列表")
    @GetMapping("/list")
    public Result<PageResult<SysJob>> list(SysJob job)
    {
        startPage();
        List<SysJob> list = jobService.selectJobList(job);
        PageInfo<SysJob> pageInfo = new PageInfo<>(list);
        return Result.success(PageResult.of(list, pageInfo.getTotal(), pageInfo.getPageNum(), pageInfo.getPageSize()));
    }

    /**
     * 根据任务ID查询定时任务信息
     */
    @Operation(summary = "根据任务ID查询定时任务信息")
    @GetMapping("/{jobId}")
    public Result<SysJob> getInfo(@PathVariable("jobId") Long jobId)
    {
        SysJob job = jobService.selectJobById(jobId);
        return Result.success(job);
    }

    /**
     * 新增定时任务
     */
    @Operation(summary = "新增定时任务")
    @PostMapping
    public Result<Integer> add(@Validated @RequestBody SysJob job) throws SchedulerException, TaskException
    {
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            return Result.error("新增任务'" + job.getJobName() + "'失败，Cron表达式不正确");
        }
        else if (StringUtils.containsIgnoreCase(job.getInvokeTarget(), Constants.LOOKUP_RMI))
        {
            return Result.error("新增任务'" + job.getJobName() + "'失败，目标字符串不允许'rmi'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.LOOKUP_LDAP, Constants.LOOKUP_LDAPS }))
        {
            return Result.error("新增任务'" + job.getJobName() + "'失败，目标字符串不允许'ldap(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.HTTP, Constants.HTTPS }))
        {
            return Result.error("新增任务'" + job.getJobName() + "'失败，目标字符串不允许'http(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), Constants.JOB_ERROR_STR))
        {
            return Result.error("新增任务'" + job.getJobName() + "'失败，目标字符串存在违规");
        }
        else if (!ScheduleUtils.whiteList(job.getInvokeTarget()))
        {
            return Result.error("新增任务'" + job.getJobName() + "'失败，目标字符串不在白名单内");
        }
        job.setCreateBy(getLoginName());
        int rows = jobService.insertJob(job);
        return Result.success(rows);
    }

    /**
     * 修改定时任务
     */
    @Operation(summary = "修改定时任务")
    @PutMapping
    public Result<Integer> edit(@Validated @RequestBody SysJob job) throws SchedulerException, TaskException
    {
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            return Result.error("修改任务'" + job.getJobName() + "'失败，Cron表达式不正确");
        }
        else if (StringUtils.containsIgnoreCase(job.getInvokeTarget(), Constants.LOOKUP_RMI))
        {
            return Result.error("修改任务'" + job.getJobName() + "'失败，目标字符串不允许'rmi'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.LOOKUP_LDAP, Constants.LOOKUP_LDAPS }))
        {
            return Result.error("修改任务'" + job.getJobName() + "'失败，目标字符串不允许'ldap'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.HTTP, Constants.HTTPS }))
        {
            return Result.error("修改任务'" + job.getJobName() + "'失败，目标字符串不允许'http(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), Constants.JOB_ERROR_STR))
        {
            return Result.error("修改任务'" + job.getJobName() + "'失败，目标字符串存在违规");
        }
        else if (!ScheduleUtils.whiteList(job.getInvokeTarget()))
        {
            return Result.error("修改任务'" + job.getJobName() + "'失败，目标字符串不在白名单内");
        }
        int rows = jobService.updateJob(job);
        return Result.success(rows);
    }

    /**
     * 删除定时任务（单个）
     */
    @Operation(summary = "删除定时任务")
    @DeleteMapping("/{jobId}")
    public Result<Integer> remove(@PathVariable("jobId") Long jobId) throws SchedulerException
    {
        jobService.deleteJobByIds(jobId.toString());
        return Result.success(1);
    }

    /**
     * 删除定时任务（批量）
     */
    @Operation(summary = "删除定时任务（批量）")
    @DeleteMapping
    public Result<Integer> removeBatch(@RequestBody String jobIds) throws SchedulerException
    {
        jobService.deleteJobByIds(jobIds);
        return Result.success(1);
    }

    /**
     * 修改定时任务状态
     */
    @Operation(summary = "修改定时任务状态")
    @PutMapping("/changeStatus/{jobId}")
    public Result<Integer> changeStatus(@PathVariable("jobId") Long jobId, @RequestBody SysJob job) throws SchedulerException
    {
        SysJob newJob = jobService.selectJobById(jobId);
        newJob.setStatus(job.getStatus());
        int rows = jobService.changeStatus(newJob);
        return Result.success(rows);
    }

    /**
     * 立即执行一次定时任务
     */
    @Operation(summary = "立即执行一次定时任务")
    @PostMapping("/run/{jobId}")
    public Result<Boolean> run(@PathVariable("jobId") Long jobId) throws SchedulerException
    {
        SysJob job = jobService.selectJobById(jobId);
        boolean result = jobService.run(job);
        return result ? Result.success(true) : Result.error("任务不存在或已过期！");
    }
}
