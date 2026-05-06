# Core Business Module — AGENTS.md

**Scope:** `ruoyi-openliststrm/`

## OVERVIEW

核心业务模块：STRM 文件生成、文件夹同步、文件重命名、Telegram Bot 控制、TMDb 元数据、第三方回调自动化。84 个 Java 文件，18 个子包。

## STRUCTURE

```
ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/
├── api/            # OpenList API 客户端（云盘接口封装）
├── config/         # 模块配置
├── controller/     # REST 控制器
│   └── api/        # 业务 API 控制器
├── domain/         # 实体类（MyBatis XML 风格）
├── enums/          # 状态枚举
├── helper/         # 辅助类
├── mapper/         # MyBatis Mapper XML（传统风格）
├── model/          # 数据模型
├── monitor/        # 文件监控（WatchService）
├── mybatisplus/    # MyBatis-Plus 实体/Mapper/Service（STRM/Copy/Rename）
├── openai/         # OpenAI 集成（文件重命名元数据生成）
├── rename/         # 文件重命名引擎
├── req/            # 请求 DTO
├── service/        # STRM/Copy 业务服务
├── task/           # 定时任务
├── tg/             # Telegram Bot
├── tmdb/           # TMDb 元数据服务
└── upload/         # 上传任务管理
```

## WHERE TO LOOK

| 功能 | 位置 |
|------|------|
| STRM 定时任务 | `task/` |
| Telegram Bot | `tg/` |
| 文件重命名 | `rename/` + `openai/` + `tmdb/` |
| 文件夹同步 | `service/` + `api/` |
| 第三方回调 | `controller/api/` |
| MyBatis-Plus 实体 | `mybatisplus/domain/` |
| 传统 MyBatis Mapper | `mapper/` |

## CONVENTIONS

- **双 ORM 风格**：本模块同时使用 MyBatis XML（`mapper/` + `domain/`）和 MyBatis-Plus（`mybatisplus/`），新代码建议统一用 MyBatis-Plus
- **BaseEntity 两套**：`com.ruoyi.common.core.domain.BaseEntity`（手动字段）和 `com.ruoyi.common.mybatisplus.BaseEntity`（仅 createTime/updateTime）
- **REST API 路径**：`/api/openlist/` 前缀
- **定时任务**：使用 `@Scheduled` 或 RuoYi Quartz 调度
- **异常处理**：业务异常使用 `ServiceException`

## ANTI-PATTERNS

- **双 ORM 并存**：与 system 模块一样，本模块也有 MyBatis XML 和 MyBatis-Plus 混合
- **无测试**：本模块无 `src/test/java`
