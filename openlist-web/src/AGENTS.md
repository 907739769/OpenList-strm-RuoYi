# OpenList-web 前端知识库

## OVERVIEW
Vue 3 + Vite + Element Plus + Pinia 前端应用，支持 PWA。包含 PC 端 (`views/`) 和移动端 (`views-mobile/`) 两套界面。

## STRUCTURE
```
src/
├── api/                    # API 请求层
│   ├── auth.ts             # 认证 API
│   ├── request.ts          # axios 封装 (拦截器、token 处理)
│   ├── monitor/            # 监控相关 API
│   ├── openlist/           # 业务 API (STRM/同步/任务)
│   └── system/             # 系统管理 API
├── components/             # 公共组件
├── composables/            # 组合式函数
├── layouts/                # 布局组件
├── router/                 # 路由配置 (动态路由)
│   └── index.ts
├── stores/                 # Pinia 状态管理
│   ├── app.ts              # 应用全局状态
│   ├── permission.ts       # 权限/菜单
│   └── user.ts             # 用户状态
├── styles/                 # 全局样式 (SCSS)
├── types/                  # TypeScript 类型定义
├── views/                  # PC 端页面
├── views-mobile/           # 移动端页面
├── App.vue
└── main.ts
```

## WHERE TO LOOK
| 任务 | 位置 | 备注 |
|------|------|------|
| 页面组件 | `views/` + `views-mobile/` | 按模块分目录 |
| API 调用 | `src/api/` | 统一 axios 封装 |
| 路由 | `src/router/index.ts` | 动态路由加载 |
| 状态管理 | `src/stores/` | Pinia store |
| 布局 | `src/layouts/` | 侧边栏、顶栏、内容区 |
| PWA 配置 | `vite.config.ts` | VitePWA 插件配置 |

## CONVENTIONS
- **自动导入**: unplugin-auto-import + unplugin-vue-components，`vue`/`vue-router`/`pinia`/`ElementPlus` 无需手动 import
- **`@` 别名**: 指向 `src/` 目录
- **API 层**: 返回标准 `{ code, msg, data }` 格式，axios 拦截器自动处理
- **路由**: 后端动态返回菜单，前端根据权限生成路由
- **移动端**: `views-mobile/` 独立于 `views/`，用于手机访问
- **TypeScript**: 严格模式，`vue-tsc` 类型检查

## ANTI-PATTERNS
- 不要在组件中直接调用 `axios`，统一用 `src/api/` 中的封装
- 不要绕过路由守卫，权限校验在 store 中统一处理
- 不要在组件中写大量业务逻辑，抽到 composables/
- 移动端页面不要使用 PC 端组件 (Element Plus PC 组件)
