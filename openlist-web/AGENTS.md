# openlist-web — Vue 3 Admin Frontend

## OVERVIEW
Vue 3 + Vite 5 + Element Plus admin frontend. TypeScript. Pinia state management. Responsive desktop/mobile layouts. 53+ source files.

## STRUCTURE
```
openlist-web/src/
├── api/                          — Axios API layer
│   ├── auth.ts                  — Login/logout/token
│   ├── request.ts               — Axios instance (interceptors, baseURL, token)
│   ├── monitor/                 — Job management APIs
│   ├── openlist/                — STRM, sync, rename, notify APIs
│   └── system/                  — User, role, dept, menu APIs
├── views/                        — Desktop page components (Vue SFC)
│   ├── auth/                    — Login page
│   ├── dashboard/               — Home dashboard
│   ├── error/                   — 403/404 pages
│   ├── monitor/                 — Job management views
│   ├── openlist/                — STRM, sync, rename, Telegram views
│   └── system/                  — User, role, dept, menu, dict views
├── views-mobile/                 — Mobile-optimized views (separate routing)
│   ├── dashboard/               — Mobile dashboard
│   ├── strmRecord/              — Mobile STRM record
│   ├── renameDetail/            — Mobile rename detail
│   └── ...                      — Other mobile-optimized pages
├── layouts/
│   ├── DesktopLayout.vue         — Sidebar + header + main (desktop)
│   └── MobileLayout.vue          — Collapsible nav (mobile)
├── components/                   — Reusable UI components
├── composables/                  — Vue composables (useXxx)
├── stores/
│   ├── app.ts                   — App state (sidebar, theme, device)
│   ├── permission.ts            — Route permission/menus
│   └── user.ts                  — User info, tokens, permissions
├── router/index.ts              — Vue Router (dynamic route injection)
├── styles/                      — Global SCSS variables, resets
├── utils/                       — Frontend utilities
├── types/                       — TypeScript type definitions
├── assets/                      — Static assets (icons, images)
├── App.vue                      — Root component
├── main.ts                      — App bootstrap (Pinia, Router, ElementPlus)
└── auto-imports.d.ts            — Unplugin auto-imports (Vue, ElementPlus)
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| API request instance | `api/request.ts` |
| OpenList business APIs | `api/openlist/` |
| STRM/sync/rename views | `views/openlist/` |
| User auth flow | `api/auth.ts`, `stores/user.ts` |
| Dynamic route injection | `router/index.ts`, `stores/permission.ts` |
| Desktop layout | `layouts/DesktopLayout.vue` |
| Mobile layout | `layouts/MobileLayout.vue` |
| ESLint config | `.eslintrc` (if exists) or `eslint` in package.json |

## CONVENTIONS
- **API layer**: Functions return `Promise<IResp>` — `request.get/post/put/delete` wrappers
- **State management**: Pinia stores (`defineStore` composition API style)
- **Routing**: Static routes + dynamic routes injected from backend menu data
- **Layouts**: `DesktopLayout` for desktop, `MobileLayout` for mobile (device detection via `stores/app.ts`)
- **Component style**: Vue 3 SFC with `<script setup lang="ts">`
- **Auto-imports**: `unplugin-auto-import` + `unplugin-vue-components` — no manual imports for Vue/ElementPlus
- **PWA**: `vite-plugin-pwa` for offline support
- **E2E tests**: Playwright (`playwright.config.ts`, `e2e/`)
- **Build**: `vue-tsc && vite build` (type-check + production build)
