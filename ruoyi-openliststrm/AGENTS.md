# OpenList-strm 核心业务模块知识库

## OVERVIEW
OpenList-strm-RuoYi 核心业务层，负责 STRM 生成、文件夹同步、Telegram Bot、文件重命名、任务调度、第三方回调等业务逻辑。16 个子包按功能域划分。

## STRUCTURE
```
com/ruoyi/openliststrm/
├── api/              # OpenList API 客户端 (网盘操作封装)
├── config/           # 业务配置类
├── controller/       # REST API 端点 (STRM/同步/任务配置/回调)
├── controller/api/   # 第三方开放 API (qb/callback 等)
├── enums/            # 业务枚举 (任务状态、类型等)
├── helper/           # 辅助工具 (文件操作、路径处理)
├── model/            # 数据传输对象
├── monitor/          # 任务监控与状态追踪
├── mybatisplus/      # ★ MP 风格数据层 (domain/mapper/service)
├── openai/           # AI 相关功能
├── rename/           # 影视文件重命名
├── req/              # 请求 DTO
├── service/          # 业务服务层
├── task/             # 定时任务 + 手动任务执行
├── tg/               # Telegram Bot (commands, handlers, utils)
├── tmdb/             # TMDB 电影/剧集信息查询
└── upload/           # 文件上传处理
```

## WHERE TO LOOK
| 任务 | 位置 | 备注 |
|------|------|------|
| STRM 生成逻辑 | `task/` + `helper/` | TaskExecutor, StrmGenerator |
| 文件夹同步 | `service/` + `upload/` | SyncService, 文件夹操作 |
| Telegram Bot | `tg/` | Bot commands, message handlers |
| TMDB 查询 | `tmdb/` | 电影/剧集元数据获取 |
| 文件重命名 | `rename/` | 影视文件智能重命名 |
| 任务配置 | `mybatisplus/domain/` + `controller/` | StrmTaskConfig, SyncTaskConfig |
| 任务记录 | `mybatisplus/domain/` | StrmTaskRecord, SyncTaskRecord |
| 第三方回调 | `controller/api/` | QB 下载完成通知等 |
| MP Mapper | `mybatisplus/mapper/` | BaseMapper 接口 |
| MP Service | `mybatisplus/service/` | IService 接口 + Impl |

## CONVENTIONS
- **按功能域分包**，非按层分包 (tg/, tmdb/, rename/ 各自独立)
- **数据层**: 使用 MyBatis-Plus (BaseMapper + IService +ServiceImpl)，XML Mapper 在 `resources/mapper/mybatisplus/`
- **Controller 只负责** 参数接收、调用 Service、返回响应，不写业务逻辑
- **枚举优先**: 任务状态、类型等使用 enum，不用魔法数字
- **FastJSON2**: 所有 JSON 序列化/反序列化统一使用 FastJSON2
- **异步任务**: 使用虚拟线程 (Java 25 preview) 处理并发 IO

## ANTI-PATTERNS
- 不要在 Controller 中写业务逻辑
- 不要混用 XML Mapper 和 MP BaseMapper (本模块只用 MP)
- 不要在 Service 中直接操作 HTTP 请求，封装到 api/ 或 helper/
- Telegram Bot handler 不要超过 50 行，复杂逻辑抽到独立方法
