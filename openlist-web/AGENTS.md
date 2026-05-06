# Frontend (openlist-web) — AGENTS.md

**Scope:** `openlist-web/`

## OVERVIEW

Vue 3 + TypeScript + Vite 5 + Element Plus + Pinia 管理后台，支持桌面端和移动端双布局。

## STRUCTURE

```
openlist-web/
├── src/
│   ├── api/              # API 层（auth/monitor/openlist/system/request）
│   ├── assets/           # 静态资源
│   ├── components/       # 公共组件（ChangePasswordDialog/DirectoryTreeSelect/OpenListTree）
│   ├── composables/      # 组合式函数（useBreakpoint.vue）
│   ├── layouts/          # 布局（DesktopLayout.vue/MobileLayout.vue）
│   ├── router/           # 路由（index.ts — 动态路由 + componentMap）
│   ├── stores/           # Pinia 状态（app.ts/user.ts/permission.ts）
│   ├── styles/           # 全局 SCSS
│   ├── types/            # TypeScript 类型定义
│   ├── utils/            # 工具（pwa.ts）
│   ├── views/            # 桌面端页面（auth/dashboard/monitor/openlist/system/error）
│   ├── views-mobile/     # 移动端页面
│   ├── App.vue
│   └── main.ts
├── e2e/                  # Playwright E2E 测试
├── vite.config.ts        # Vite 配置
└── package.json
```

## WHERE TO LOOK

| 功能 | 位置 |
|------|------|
| 入口 | `src/main.ts` |
| 路由/动态路由 | `src/router/index.ts` |
| API 请求封装 | `src/api/request.ts` |
| 认证 API | `src/api/auth.ts` |
| 业务 API | `src/api/openlist/` |
| 状态管理 | `src/stores/` |
| 桌面布局 | `src/layouts/DesktopLayout.vue` |
| 移动端布局 | `src/layouts/MobileLayout.vue` |
| 桌面端页面 | `src/views/` |
| 移动端页面 | `src/views-mobile/` |

## CONVENTIONS

- **组件**：PascalCase，`<script setup lang="ts">`
- **API 函数**：`{name}Api(data)` 命名，返回解包后的 data
- **Store**：Pinia composition API 风格
- **路由**：动态路由，`componentMap` 硬编码映射后端返回的 component 路径
- **路径别名**：`@/` → `src/`
- **自动导入**：`unplugin-auto-import` + `unplugin-vue-components`
- **移动端适配**：`device === 'mobile'` 时 `openlist/` → `views-mobile/`
- **Token**：js-cookie，请求头 `Authorization: Bearer {token}`
- **SCSS**：CSS 变量系统（`--osr-*` 前缀）

## ANTI-PATTERNS

- **componentMap 硬编码**：新增页面需同步修改 `router/index.ts`
- **permission.ts 未启用**：`hasPermission()` 始终返回 true

## COMMANDS

```bash
npm run dev           # 开发模式（:3000）
npm run build         # 生产构建
npm run lint          # ESLint 修复
npm run test:e2e      # Playwright E2E 测试
```
