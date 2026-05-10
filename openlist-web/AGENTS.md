# openlist-web 前端知识库

## OVERVIEW
Vue 3 + TypeScript + Element Plus 前端应用，使用 Vite 构建，Pinia 状态管理，支持桌面端和移动端双端适配。

## STRUCTURE
```
src/
├── api/openlist/       # API 调用层（copy/strm/rename/dashboard 等）
├── components/         # 公共组件（DirectoryTreeSelect, OpenListTree, ChangePasswordDialog）
├── composables/        # 组合式函数（useBreakpoint 等）
├── layouts/            # 布局组件（DesktopLayout, MobileLayout）
├── router/             # 路由配置
├── store/              # Pinia 状态管理
├── utils/              # 前端工具（request, auth 等）
├── views/              # 桌面端页面
│   ├── auth/           # 登录
│   ├── dashboard/      # 仪表盘
│   ├── monitor/        # 监控（定时任务、实时日志）
│   ├── openlist/       # 核心业务（copy/strm/rename 任务与记录）
│   └── system/         # 系统管理（配置、字典）
├── views-mobile/       # 移动端页面（copy/strm/rename 核心操作）
└── App.vue             # 根组件
```

## WHERE TO LOOK
| 任务 | 位置 |
|------|------|
| API 请求封装 | `src/utils/request.ts` |
| API 定义 | `src/api/openlist/` |
| 路由配置 | `src/router/` |
| Pinia Store | `src/store/` |
| 桌面端视图 | `src/views/` |
| 移动端视图 | `src/views-mobile/` |
| 公共组件 | `src/components/` |
| 布局切换 | `src/layouts/` |
| E2E 测试 | `e2e/` |
| Playwright 配置 | `playwright.config.ts` |

## CONVENTIONS
- API 层统一使用 axios 封装，通过 `request.ts` 统一处理 token 和错误
- 桌面端和移动端页面按功能目录隔离，不混用
- 组件使用 `<script setup lang="ts">` 语法
- 路由按模块懒加载

## ANTI-PATTERNS
- 不要在视图组件中直接调用 axios，必须通过 api 层
- 不要在组件中写全局状态，使用 Pinia store
- 移动端和桌面端不要共享视图文件（布局差异大）
