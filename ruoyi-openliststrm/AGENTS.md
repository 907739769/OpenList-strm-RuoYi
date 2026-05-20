# ruoyi-openliststrm 模块知识库

## OVERVIEW
核心业务模块，实现 OpenList 云盘集成、strm 文件流媒体生成、媒体自动重命名、Telegram Bot 通知、文件监控四大功能。

## STRUCTURE
```
src/main/java/com/ruoyi/openliststrm/
├── api/                # OpenList API 客户端封装
├── config/             # 模块配置（OpenList 连接参数）
├── controller/api/     # REST API 控制器（copy/strm/rename/dashboard）
├── enums/              # 业务枚举（CopyStatusEnum, StrmStatusEnum）
├── helper/             # 核心工具层（AsynHelper, CopyHelper, StrmHelper, TgHelper 等）
├── monitor/            # 文件监控系统（WatchService + processor 链）
├── mybatisplus/        # MyBatis-Plus 实体/Mapper/Service（copy/strm 表）
└── rename/             # 重命名规则引擎
```

## WHERE TO LOOK
| 任务 | 位置 |
|------|------|
| OpenList API 客户端 | `api/OpenlistApi.java` |
| Copy 业务 API | `controller/api/OpenlistCopyRestController.java` |
| Strm 业务 API | `controller/api/OpenlistStrmRestController.java` |
| 重命名任务 API | `controller/api/RenameTaskRestController.java` |
| CopyHelper 核心逻辑 | `helper/CopyHelper.java` |
| StrmHelper 核心逻辑 | `helper/StrmHelper.java` |
| Telegram Bot | `helper/TgHelper.java` |
| 文件监控协调器 | `monitor/service/FileMonitorCoordinator.java` |
| 媒体重命名处理器 | `monitor/processor/MediaRenameProcessor.java` |
| 媒体上传处理器 | `monitor/processor/MediaUploadProcessor.java` |
| MyBatis-Plus Mapper | `mybatisplus/mapper/` |
| 数据库映射 XML | `src/main/resources/mapper/mybatisplus/` |

## CONVENTIONS
- 控制器统一继承 REST 风格，路径以 `/api/openlist/` 前缀
- 业务逻辑集中在 `helper/` 层，控制器只做参数校验和响应封装
- MyBatis-Plus 实体使用 `Plus` 后缀（如 `OpenlistCopyPlus`）
- 文件监控采用 WatchService + 事件处理器链模式

## ANTI-PATTERNS
- 不要在控制器中写业务逻辑，必须下沉到 helper 层
- 不要在 monitor 处理器中直接操作数据库，通过 service 层
- Strm 生成路径需与 ruoyi.profile 配置保持一致
