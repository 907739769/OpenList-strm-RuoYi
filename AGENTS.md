# OpenList-strm-RuoYi 项目知识库

## OVERVIEW
基于 RuoYi 4.8.1 二次开发的影视 STRM 管理系统。Java 25 (Spring Boot 4.0.6) + Vue 3 + Element Plus，Docker 双容器部署。核心功能：STRM 文件生成、文件夹同步、Telegram Bot 控制、第三方回调自动化。

## STRUCTURE
```
├── ruoyi-admin/          # 启动模块 (Spring Boot main)
├── ruoyi-common/         # 通用工具 (annotation, utils, exception, mybatisplus)
├── ruoyi-framework/      # 框架配置 (security, shiro, config, websocket)
├── ruoyi-system/         # RuoYi 标准模块 (user/role/menu/dict domain)
├── ruoyi-quartz/         # 定时任务 (RuoYi job scheduler)
├── ruoyi-openliststrm/   # ★ 核心业务 (16个子包: strm/sync/tg/rename/monitor...)
├── openlist-web/         # Vue 3 前端 (Vite + Pinia + Element Plus + PWA)
├── Dockerfile.backend    # Java 25 JRE + --enable-preview
├── Dockerfile.frontend   # Node 20 build → Nginx Alpine
├── docker-compose.yml    # MySQL 8.0 + backend + frontend
└── nginx.conf            # SPA + API proxy + WebSocket proxy
```

## WHERE TO LOOK
| 任务 | 位置 | 备注 |
|------|------|------|
| STRM 生成 | `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/` | task/, helper/, tmdb/, rename/ |
| 文件夹同步 | `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/` | api/, upload/, service/ |
| Telegram Bot | `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/tg/` | bot commands & handlers |
| 定时任务 | `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/task/` + `ruoyi-quartz/` | 自定义 task + RuoYi job |
| 安全/认证 | `ruoyi-framework/src/main/java/com/ruoyi/framework/security/` + `shiro/` | Shiro + JWT |
| 第三方回调 | `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/` | 开放 API 端点 |
| 前端页面 | `openlist-web/src/views/` + `views-mobile/` | PC + 移动端 |
| 前端 API 层 | `openlist-web/src/api/` | axios 封装 + 模块 API |
| 前端路由 | `openlist-web/src/router/index.ts` | 动态路由 |
| 前端状态 | `openlist-web/src/stores/` | Pinia (app, user, permission) |
| DB 脚本 | `ruoyi-common/src/main/resources/sql/` | 初始化 + 升级脚本 |
| MyBatis Mapper | `ruoyi-system/src/main/resources/mapper/system/` + `ruoyi-openliststrm/src/main/resources/mapper/mybatisplus/` | XML 映射 |

## CONVENTIONS
- **包命名**: `com.ruoyi.{module}.{layer}` — controller/service/mapper/domain 分层
- **OpenList-strm 模块**: 按功能域分包 (tg/, tmdb/, rename/, helper/, monitor/, mybatisplus/)
- **MyBatis-Plus**: `ruoyi-openliststrm` 使用 MP 风格 (BaseMapper + IService)，`ruoyi-system` 使用传统 XML Mapper
- **Shiro + JWT**: 无状态认证，Shiro 管理权限，JWT 传递 token
- **Java 25 Preview**: 编译/运行带 `--enable-preview` (虚拟线程/结构化并发)
- **FastJSON2**: 统一使用 FastJSON2 做 JSON 序列化
- **密码加密**: 使用 Cipher 加密存储敏感配置 (DB_PASSWORD 等)
- **前端**: unplugin-auto-import + unplugin-vue-components 自动导入，`@` 指向 `src/`

## ANTI-PATTERNS
- 不要在 `ruoyi-system` 中新增业务模块 (那是 RuoYi 标准系统管理)
- 业务逻辑全部放在 `ruoyi-openliststrm` 中
- 不要在 Controller 中写业务逻辑，Service 层处理
- MyBatis-Plus 模块使用 `@TableName` + `BaseMapper`，不要混用 XML Mapper
- Java 25 preview 特性仅用于业务代码，框架配置不依赖 preview API

## COMMANDS
```bash
# 后端构建
mvn clean package -DskipTests

# 前端构建
cd openlist-web && npm run build

# Docker 部署
docker compose up -d --build

# 前端开发
cd openlist-web && npm run dev

# 前端 E2E 测试
cd openlist-web && npm run test:e2e
```

## NOTES
- 打包镜像前需先 `mvn package` 生成 ruoyi-admin.jar
- 容器内 `/data` 目录挂载宿主机，存放 upload/logs/strm 文件
- MySQL 默认数据库名 `osr`，连接信息通过 `.env` 注入
- 数据库初始化由 `com.ruoyi.common.mybatisplus.MysqlDdl` 自动执行（ruoyi-common/src/main/resources/sql/）
- 后端端口 6895，前端 Nginx 端口 80
- API 路径统一 `/api/` 前缀，Nginx 代理到后端
- WebSocket 路径 `/websocket/`，超时 86400s (长连接)
