# ruoyi-quartz 模块知识库

## OVERVIEW
定时任务模块，基于 Quartz 实现任务调度，支持 Cron 表达式配置、任务执行日志记录、任务状态监控。

## STRUCTURE
```
src/main/java/com/ruoyi/quartz/
├── config/ScheduleConfig.java       # Quartz 配置类
├── domain/
│   ├── SysJob.java                  # 定时任务实体
│   └── SysJobLog.java               # 任务执行日志实体
├── mapper/
│   ├── SysJobMapper.java            # 任务 Mapper
│   └── SysJobLogMapper.java         # 日志 Mapper
├── service/
│   ├── ISysJobService.java          # 任务服务接口
│   ├── ISysJobLogService.java       # 日志服务接口
│   └── impl/                        # 服务实现
└── util/
    ├── AbstractQuartzJob.java       # 任务抽象基类
    ├── CronUtils.java               # Cron 表达式工具
    ├── JobInvokeUtil.java           # 方法调用工具
    ├── QuartzDisallowConcurrentExecution.java  # 禁止并发执行注解
    ├── QuartzJobExecution.java      # 任务执行入口
    └── ScheduleUtils.java           # 调度工具类
src/main/resources/mapper/quartz/
├── SysJobMapper.xml                 # 任务 SQL 映射
└── SysJobLogMapper.xml              # 日志 SQL 映射
```

## WHERE TO LOOK
| 任务 | 位置 |
|------|------|
| Quartz 配置 | `config/ScheduleConfig.java` |
| 任务抽象基类 | `util/AbstractQuartzJob.java` |
| 任务执行入口 | `util/QuartzJobExecution.java` |
| Cron 工具 | `util/CronUtils.java` |
| 调度工具 | `util/ScheduleUtils.java` |
| 任务实体 | `domain/SysJob.java` |
| 任务 Service | `service/ISysJobService.java` |
| 日志 Service | `service/ISysJobLogService.java` |

## CONVENTIONS
- 自定义任务需继承 `AbstractQuartzJob`，实现 `runMethod()` 方法
- Cron 表达式通过前端页面配置，存储在 `SysJob.cronExpression` 字段
- 任务调用通过 `JobInvokeUtil` 反射调用 Spring Bean 方法（格式：`beanNamemethodName`）
- 使用 `@QuartzDisallowConcurrentExecution` 注解禁止任务并发执行
- Mapper XML 放在 `mapper/quartz/` 目录，使用 MyBatis XML（非 MyBatis-Plus）

## ANTI-PATTERNS
- 不要直接操作 Quartz Scheduler，通过 `ScheduleUtils` 统一管理
- 不要在 Cron 表达式中使用非法值（秒 0-59，分 0-59，时 0-23 等）
- 任务执行异常会被捕获并记录到 `SysJobLog`，不要在任务中吞掉异常
- 不要在此模块使用 MyBatis-Plus
