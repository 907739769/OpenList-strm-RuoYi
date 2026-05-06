# ruoyi-quartz — Scheduled Task Management

## OVERVIEW
RuoYi Quartz integration: job scheduling, cron expression management, job log tracking. 18 Java files. Traditional MyBatis XML mappers.

## STRUCTURE
```
ruoyi-quartz/src/main/java/com/ruoyi/quartz/
├── config/ScheduleConfig.java       — Quartz scheduler bean configuration (thread pool, etc.)
├── controller/
│   ├── SysJobController.java        — Job CRUD + start/stop/pause/resume
│   └── SysJobLogController.java     — Job execution log queries
├── domain/
│   ├── SysJob.java                  — Scheduled task entity (beanName, methodName, params, cronExpression, status)
│   └── SysJobLog.java               — Job execution log entity (invokeTarget, invokeTarget, status, etc.)
├── mapper/                          — 2 mapper interfaces (traditional MyBatis XML)
│   ├── SysJobMapper.java
│   └── SysJobLogMapper.java
├── service/
│   ├── ISysJobService.java          — Job management interface
│   ├── ISysJobLogService.java       — Job log query interface
│   └── impl/
│       ├── SysJobServiceImpl.java
│       └── SysJobLogServiceImpl.java
├── task/RyTask.java                 — Example Quartz task implementations
└── util/
    ├── AbstractQuartzJob.java       — Base class for Quartz job implementations
    ├── CronUtils.java               — Cron expression parsing/validation (timezone handling)
    ├── JobInvokeUtil.java           — Reflection-based job method invocation (bean + method, class + method)
    ├── QuartzDisallowConcurrentExecution — @DisallowConcurrentExecution decorator
    ├── QuartzJobExecution.java      — Quartz job execution handler (invokes AbstractQuartzJob)
    └── ScheduleUtils.java           — Scheduler factory + job creation/deletion utilities
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| Job execution entry | `util/QuartzJobExecution.java` |
| Custom job base class | `util/AbstractQuartzJob.java` |
| Example tasks | `task/RyTask.java` |
| Scheduler config | `config/ScheduleConfig.java` |
| Job CRUD controller | `controller/SysJobController.java` |
| Job log queries | `controller/SysJobLogController.java` |
| Cron utilities | `util/CronUtils.java` |
| MyBatis XML mappers | `src/main/resources/mapper/quartz/` |
| Thymeleaf templates | `src/main/resources/templates/monitor/job/` |

## CONVENTIONS
- All mappers use traditional MyBatis XML (not MyBatis-Plus)
- Custom jobs extend `AbstractQuartzJob` and implement `runTask(SysJob)`
- Job invocation uses reflection via `JobInvokeUtil` — supports bean name + method, class name + method
- Cron expressions validated via `CronUtils` before scheduling
- `@RequiresPermissions("monitor:job:action")` on all job controller methods
- Job status: `0=normal`, `1=pause` — controlled via `SysJob.getStatus()`
