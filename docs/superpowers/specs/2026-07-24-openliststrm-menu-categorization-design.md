# OpenListStrm 菜单分类设计文档

## 背景与目标

`sys_menu` 里 `OpenListStrm`(menu_id=2006) 目录下平铺挂着 14 个二级菜单，横跨同步、STRM、重命名、PT 下载四个功能域，且 `order_num` 有重复值，随着功能持续增加会越来越难找。目标是把这 14 个菜单按功能域分组到 4 个新的二级目录下，`menu_id` 保持不变（不破坏现有角色的菜单授权），仅调整 `parent_id`/`order_num`。

## 现状

`OpenListStrm`(2006) 当前直属二级菜单（`menu_type='C'`）：

| menu_id | menu_name | 现 order_num |
|---|---|---|
| 2025 | 同步任务配置 | 1 |
| 2013 | 同步任务记录 | 2 |
| 2037 | strm任务配置 | 3 |
| 2019 | STRM生成记录 | 4 |
| 2049 | 重命名任务配置 | 5 |
| 2043 | 重命名明细 | 6 |
| 2055 | 重命名一致性检查 | 7 |
| 2060 | 重命名规则设置 | 8 |
| 2061 | PT索引器 | 10 |
| 2062 | PT下载器 | 11 |
| 2063 | 媒体服务器 | 12 |
| 2064 | PT订阅 | 13 |
| 2065 | PT过滤规则 | 14 |
| 2066 | PT下载记录 | 15 |

前端侧边栏（`DesktopLayout.vue` / `MobileLayout.vue`）目前是写死的两级渲染：`el-sub-menu` 内直接 `v-for` 平铺 `el-menu-item`，不支持递归。菜单树变成三级（目录 → 子目录 → 页面）后，两个布局文件都需要跟着改。

## 范围

**包含：**
- 新增 4 个 `menu_type='M'` 的子目录菜单，挂在 2006 下
- 把现有 14 个二级菜单通过 `UPDATE parent_id`/`UPDATE order_num` 挪到对应子目录下
- 前端抽取递归侧边栏菜单组件，替换 `DesktopLayout.vue`/`MobileLayout.vue` 里写死的两级渲染

**不包含：**
- 不改动任何 `menu_id`、`perms`（按钮权限字符串）——现有角色授权数据不受影响
- 不改动叶子页面本身的路由/组件/业务逻辑
- 不新建三级以下的更深层级（比如 PT 组内部不再二次分组）

## 数据库设计

新迁移文件 `ruoyi-common/src/main/resources/sql/20260736-menu-categories.sql`（文件名沿用仓库里 `sql/2026073X` 序列号约定，接在现有最后一个 PT 迁移 `20260735-pt-downloader-strm-task-link.sql` 之后），注册进 `MysqlDdl.java` 的 `getSqlFiles()` 列表末尾。

新增子目录菜单（`INSERT IGNORE` + 显式主键，幂等）：

| menu_id | menu_name | icon（须在 `useMenuIcon.ts` 的 `iconMap` 中） | order_num（挂在2006下） |
|---|---|---|---|
| 2067 | 同步管理 | `fa fa-copy` | 1 |
| 2068 | STRM管理 | `fa fa-video-play` | 2 |
| 2069 | 重命名管理 | `fa fa-edit` | 3 |
| 2070 | PT下载管理 | `fa fa-download` | 4 |

四个 icon 类名在当前（dev 分支）`useMenuIcon.ts` 的 `iconMap` 里均已存在，不会重演"图标不显示"的历史问题（见 commit `0248e124`）。

重新挂载现有二级菜单（`UPDATE sys_menu SET parent_id=?, order_num=? WHERE menu_id=?`）：

| 新 parent_id (子目录) | menu_id | menu_name | 组内新 order_num |
|---|---|---|---|
| 2067 同步管理 | 2025 | 同步任务配置 | 1 |
| 2067 同步管理 | 2013 | 同步任务记录 | 2 |
| 2068 STRM管理 | 2037 | strm任务配置 | 1 |
| 2068 STRM管理 | 2019 | STRM生成记录 | 2 |
| 2069 重命名管理 | 2049 | 重命名任务配置 | 1 |
| 2069 重命名管理 | 2060 | 重命名规则设置 | 2 |
| 2069 重命名管理 | 2043 | 重命名明细 | 3 |
| 2069 重命名管理 | 2055 | 重命名一致性检查 | 4 |
| 2070 PT下载管理 | 2061 | PT索引器 | 1 |
| 2070 PT下载管理 | 2062 | PT下载器 | 2 |
| 2070 PT下载管理 | 2063 | 媒体服务器 | 3 |
| 2070 PT下载管理 | 2064 | PT订阅 | 4 |
| 2070 PT下载管理 | 2065 | PT过滤规则 | 5 |
| 2070 PT下载管理 | 2066 | PT下载记录 | 6 |

`UPDATE` 语句天然幂等（重复执行结果不变），配合 `SimpleDdl` 的"整文件成功才记入 ddl_history"机制不会有部分执行风险。

菜单的 `visible`/`perms` 字段不变，`PT订阅`(2064)/`PT过滤规则`(2065) 保留各自当前的 `visible` 值。

## 前端设计

新增 `openlist-web/src/components/SidebarMenuItem.vue`：递归组件，props 接收单个 `MenuRoute`：
- 有 `children`（长度>0）→ 渲染 `el-sub-menu`（`index` 用自身 `path`，`title` 插槽放 icon + `meta.title`），内部对每个 child 递归渲染 `<SidebarMenuItem>`
- 无 `children` → 渲染 `el-menu-item`（`index` 直接用 `menu.path`，因为现有叶子页面 `url` 都是以 `/` 开头的绝对路径，无需拼接父路径）

`DesktopLayout.vue` 和 `MobileLayout.vue` 里原来那段 `v-for="menu in sidebarMenus"` 展开的 `el-sub-menu`/`el-menu-item` 逻辑，替换成：
```vue
<SidebarMenuItem v-for="menu in sidebarMenus" :key="menu.path" :menu="menu" />
```
两个文件各自保留自己的外层结构（logo、顶栏、样式类名等不动），只替换菜单遍历这一段，保证桌面端/移动端各自现有的视觉风格不受影响。

组件天然支持任意深度嵌套，以后再加子目录、甚至子目录下再分组都不用改前端代码。

`extractLeafRoutes`/`convertMenuToRoute`（`router/index.ts`）已经是递归实现，不用改动——已验证它们按 `component === 'Layout'` 逐层展开子节点，不关心树的实际深度。

## 边界情况

- **权限过滤**：后端下发菜单树时如果某个子目录下的菜单因角色权限被过滤到只剩 0 个，子目录本身是否还需要显示——沿用现有行为（后端 `SysMenuServiceImpl` 现有的菜单树过滤逻辑不改，只是这次多加了一层目录节点，行为跟现有 `OpenListStrm` 顶层目录完全一致）。
- **图标缺失兜底**：`getIconComponent` 返回 `undefined` 时上层 `v-if` 已经处理为不渲染 icon，本次新增的 4 个 icon 类名已确认存在于映射表，不需要额外兜底逻辑。
- **折叠侧边栏**（`:collapse="!appStore.sidebarOpened"`）：Element Plus 的 `el-sub-menu` 原生支持折叠态下的 popper 弹出子菜单，递归结构不影响这一行为，验证时需要专门看一眼折叠态下三级菜单的展开效果。

## 测试计划

- 迁移脚本：本地对着测试库跑一次 `MysqlDdl`（或直接执行 SQL 文件两遍），确认 `sys_menu` 分组后的 `parent_id`/`order_num` 符合预期，且二次执行不报错。
- 前端：`npm run dev` 起服务，登录后检查：
  - 桌面端 + 移动端侧边栏都出现三级缩进结构，4 个新分组图标正常显示
  - 展开/收起子目录正常，点击叶子菜单能正确跳转到原页面（路由、面包屑、`KEEP_ALIVE_COMPONENTS` 缓存不受影响）
  - 侧边栏折叠态下子目录 popper 展开正常
- 回归：抽查 2-3 个原本挂在 2006 下的按钮级权限（`F` 类型菜单，比如 `openliststrm:copy:add`）没有被误挪动 `parent_id`（这些按钮权限挂在对应 `C` 类型菜单下，不直接挂 2006，不在本次 `UPDATE` 范围内，仅需确认脚本没有误伤）。
