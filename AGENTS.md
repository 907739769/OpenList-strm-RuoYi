# OpenListStrm 项目知识库

## OVERVIEW
OpenListStrm 是基于 RuoYi 4.8.1 二次开发的云盘管理系统，集成 OpenList API 与 strm 文件流媒体处理。后端 Spring Boot 4.0.6 + Java 25，前端 Vue 3 + Element Plus，Docker 部署。

## STRUCTURE
```
├── pom.xml                          # 父 POM，7 模块管理
├── ruoyi-admin/                     # 启动入口 + API 控制器
├── ruoyi-common/                    # 通用工具（Excel、字符串、加密等）
├── ruoyi-framework/                 # Spring Boot 配置（Shiro、Druid、MyBatis）
├── ruoyi-system/                    # 系统管理（用户/角色/菜单/字典）
├── ruoyi-quartz/                    # 定时任务
├── ruoyi-openliststrm/              # 核心业务（copy/strm/rename/telegram）
├── openlist-web/                    # Vue 3 前端（Vite + Pinia）
├── config/                          # 外部化配置（application.yml）
├── Dockerfile.backend / .frontend   # 双镜像构建
└── docker-compose.yml               # 三服务编排（MySQL + backend + frontend）
```

## WHERE TO LOOK
| 任务 | 位置 |
|------|------|
| 启动入口 | `ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java` |
| Shiro 安全配置 | `ruoyi-framework/src/main/java/com/ruoyi/framework/config/ShiroConfig.java` |
| Druid 数据源 | `ruoyi-framework/src/main/java/com/ruoyi/framework/config/DruidConfig.java` |
| MyBatis/MyBatis-Plus | `ruoyi-framework/src/main/java/com/ruoyi/framework/config/MyBatisConfig.java` + `MybatisPlusConfig.java` |
| 核心业务 API | `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/` |
| Helper 工具层 | `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/helper/` |
| 文件监控 | `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/monitor/` |
| 前端 API 调用 | `openlist-web/src/api/openlist/` |
| 前端视图 | `openlist-web/src/views/` + `openlist-web/src/views-mobile/` |
| 数据库脚本 | `ruoyi-common/src/main/resources/sql/` |
| Mapper XML | `ruoyi-system/src/main/resources/mapper/system/` + `ruoyi-openliststrm/src/main/resources/mapper/mybatisplus/` |

## CONVENTIONS
- **包命名**: `com.ruoyi.{module}.{layer}` — layer 为 controller/service/impl/mapper/domain
- **双 ORM**: MyBatis XML（ruoyi-system） + MyBatis-Plus（ruoyi-openliststrm），按模块隔离
- **API 风格**: RESTful 控制器统一放在 `controller/api/` 子包
- **配置管理**: 开发配置在 `config/application.yml`，Docker 环境变量注入
- **前端路由**: 桌面端 `views/` + 移动端 `views-mobile/` 双端适配
- **Telegram Bot**: 使用 telegrambots-abilities 框架，通过 TgHelper 集成

## ANTI-PATTERNS
- 不要混用 MyBatis XML 和 MyBatis-Plus 于同一模块
- Excel 导出统一用 `ExcelUtil`（`ruoyi-common/utils/poi/`），不要手写 CSV
- 密码加密使用项目自定义加密工具，不要引入新加密库
- Docker 部署时 DB_HOST 必须为 `mysql`（compose 服务名）

## COMMANDS
```bash
# 后端
mvn clean package -DskipTests          # 构建
java -jar ruoyi-admin/target/ruoyi-admin.jar  # 启动（默认端口 16895）

# 前端
cd openlist-web && npm run dev         # 开发
cd openlist-web && npm run build       # 生产构建
cd openlist-web && npm run test:e2e    # E2E 测试（Playwright）

# Docker
docker-compose up -d                   # 全栈启动
docker-compose down                    # 全栈停止

# CI/CD
# .github/workflows/docker-publish.yml  — Docker Hub 推送
```

## NOTES
- Java 25 需要 Shiro jakarta 版本（`classifier: jakarta`）
- Spring Boot 4.0.6 使用 Jakarta EE 9+ 命名空间（`javax` → `jakarta`）
- 数据库初始化由 `sql: init` 自动执行（schema.sql + data.sql）
- 文件路径配置 `ruoyi.profile` 在 Windows 用 `D:/`，Linux 用 `/data/`
- 大文件：`ExcelUtil.java` (1928行) 是最大工具类
