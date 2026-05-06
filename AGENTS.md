# OpenList-strm-RuoYi — Project Knowledge Base

**Version:** 1.0.0 | **Stack:** Spring Boot 3.2.5 + Java 21 + Vue 3 + TypeScript

## OVERVIEW

基于 RuoYi 4.8.1 升级的云盘 STRM 管理系统。核心功能：STRM 文件生成、文件夹同步、文件重命名、Telegram Bot 控制、第三方回调自动化。前后端分离架构，Docker Compose 三服务部署。

## STRUCTURE

```
├── openlist-web/          # 前端：Vue 3 + TS + Vite + Element Plus + Pinia
├── pom.xml                # Maven 父 POM（6 个 backend 模块）
├── config/                # 外部化配置（application.yml + druid）— gitignore 排除
├── ruoyi-admin/           # 启动模块：RuoYiApplication + Controller
├── ruoyi-common/          # 通用工具：annotation/core/utils/exception/enums
├── ruoyi-framework/       # 框架核心：Shiro/Security/拦截器/配置/AOP
├── ruoyi-system/          # 系统管理：用户/角色/菜单/字典 CRUD（MyBatis XML）
├── ruoyi-quartz/          # 定时任务：Job 调度与日志
├── ruoyi-openliststrm/    # 核心业务：STRM/同步/重命名/TG Bot/TMDb
├── docker-compose.yml     # MySQL 8.0 + Backend + Frontend/Nginx
├── Dockerfile.backend     # JDK 21 JRE Alpine → 多 jar 打包
├── Dockerfile.frontend    # Node 20 构建 → Nginx Alpine
├── nginx.conf             # API/WebSocket 反向代理 + 安全头
└── .github/workflows/     # GitHub Actions：beta(master) + release(tag)
```

## WHERE TO LOOK

| Task | Location |
|------|----------|
| 应用入口 | `ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java` |
| 前端入口 | `openlist-web/src/main.ts` |
| 前端路由/动态路由 | `openlist-web/src/router/index.ts` |
| 前端状态管理 | `openlist-web/src/stores/{app,user,permission}.ts` |
| 后端配置 | `config/application.yml` + `config/application-druid.yml` |
| Shiro 安全 | `ruoyi-framework/src/main/java/com/ruoyi/framework/shiro/` |
| Security 配置 | `ruoyi-framework/src/main/java/com/ruoyi/framework/security/` |
| 通用工具/注解 | `ruoyi-common/src/main/java/com/ruoyi/common/` |
| STRM 业务逻辑 | `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/` |
| Telegram Bot | `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/tg/` |
| 定时任务 | `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/task/` |
| MyBatis Mapper XML | `ruoyi-system/src/main/resources/mapper/system/` + `ruoyi-openliststrm/src/main/resources/mapper/` |
| SQL 初始化 | `ruoyi-common/src/main/resources/sql/` |
| 前端 API 层 | `openlist-web/src/api/` |
| 前端视图 | `openlist-web/src/views/`（桌面）+ `openlist-web/src/views-mobile/`（移动端） |
| Docker 构建 | `.github/workflows/docker-publish.yml` (beta) + `docker-publish-tag.yml` (release) |

## CONVENTIONS

- **包命名**：全部 Java 代码使用 `com.ruoyi` 前缀，按模块划分子包
- **前后端端口**：后端 16895（dev）/ 6895（Docker），前端 3000（dev）/ 80（Docker）
- **认证机制**：Shiro（Jakarta 版）+ JWT 双机制，Token 存 js-cookie，请求头 `Authorization: Bearer {token}`
- **数据库**：MySQL 8.0，UTF8MB4，MyBatis XML（system 模块）+ MyBatis-Plus（openliststrm 模块）混合
- **缓存**：Ehcache，启动时 SQL 自动初始化（`schema.sql` + `data.sql`）
- **前端路由**：动态路由，从后端 `/auth/routers` 获取菜单树，前端 `componentMap` 映射组件
- **移动端适配**：通过 `useAppStore().device` 切换 `DesktopLayout`/`MobileLayout`，路由路径自动替换 `openlist/` → `views-mobile/`
- **Docker 部署**：`config/` 目录在宿主机，通过 volume 挂载；DB 密码等通过环境变量注入
- **CI/CD**：push master → beta 镜像；push v* tag → latest + 版本镜像；双架构 `linux/amd64, linux/arm64`

## ANTI-PATTERNS

- **无测试**：全项目零测试覆盖（无 `src/test/java`，无 JUnit/Mockito 依赖），`ruoyi-common/pom.xml` 中 `spring-test` 为 compile scope（非常规）
- **IDE 文件混入**：`.classpath`/`.project`/`.settings/` 存在于多个模块根目录（应被 gitignore）
- **Shiro + Security 共存**：`ruoyi-framework` 中同时存在 `ShiroConfig` 和 `SecurityConfig`，职责边界不清
- **MyBatis + MyBatis-Plus 混合**：system 模块用 MyBatis XML，openliststrm 模块用 MyBatis-Plus，同一项目两种 DAO 风格
- **config/ 目录被 gitignore**：根目录 `config/` 通过 `/config/` 排除，部署时需手动放置
- **前端路由硬编码**：`componentMap` 在 `router/index.ts` 中硬编码所有页面路径，新增页面需同步修改此处
- **YUI Compressor 压缩静态资源**：`ruoyi-admin/pom.xml` 中配置 yuicompressor 压缩 `src/main/resources/static/` 下的 JS/CSS

## COMMANDS

```bash
# 后端
mvn -B package -DskipTests          # 构建全部模块
mvn -pl ruoyi-admin -am package     # 仅构建 admin（含依赖）

# 前端
cd openlist-web && npm run dev      # 开发模式（:3000）
cd openlist-web && npm run build    # 生产构建
cd openlist-web && npm run preview  # 预览构建结果

# Docker
docker-compose up -d                # 启动全部服务
docker-compose down                 # 停止全部服务

# GitHub Actions（push 触发）
# push master → beta 镜像
# push v* tag → latest + 版本镜像
```

## NOTES

- 启动类 `RuoYiApplication` 排除了 `DataSourceAutoConfiguration`，数据源由 Shiro/RuoYi 自定义配置
- `@ComponentScan` 显式扫描 `com.ruoyi`, `com.ruoyi.web`, `com.ruoyi.framework`
- 后端 `TemplatePreheater` 组件在启动后预热页面（3 次 GET `/` + `/login`）
- 前端 `normalizeComponentPath()` 处理 legacy 菜单路径兼容（`openliststrm/` → `openlist/`）
- 前端 `VITE_APP_BASE_API` 环境变量控制 API 基础路径，默认 `/api`
- Nginx 配置中所有静态资源 `Cache-Control: no-cache`，强制每次加载最新版本
