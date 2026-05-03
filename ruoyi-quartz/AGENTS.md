# ruoyi-quartz — Scheduled Task Management

## OVERVIEW
RuoYi Quartz integration: job scheduling, cron expression management, job log tracking. 18 Java files.

## STRUCTURE
```
ruoyi-quartz/src/main/java/com/ruoyi/quartz/
├── config/ScheduleConfig.java       — Quartz scheduler bean configuration
├── controller/
│   ├── SysJobController.java        — Job CRUD + start/stop/pause/resume
│   └── SysJobLogController.java     — Job execution log queries
├── domain/                          — SysJob, SysJobLog entities
├── mapper/                          — *Mapper interfaces (traditional MyBatis XML)
├── service/
│   ├── ISysJobService.java          — Job management interface
│   ├── ISysJobLogService.java       — Job log query interface
│   └── impl/                        — Service implementations
└── util/
    ├── AbstractQuartzJob.java       — Base class for Quartz job implementations
    ├── CronUtils.java               — Cron expression parsing/validation
    ├── JobInvokeUtil.java           — Reflection-based job method invocation
    ├── QuartzDisallowConcurrentExecution — @DisallowConcurrentExecution decorator
    ├── QuartzJobExecution.java      — Quartz job execution handler
    └── ScheduleUtils.java           — Scheduler factory + job creation utilities
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| Job execution entry | `util/QuartzJobExecution.java` |
| Custom job base class | `util/AbstractQuartzJob.java` |
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
