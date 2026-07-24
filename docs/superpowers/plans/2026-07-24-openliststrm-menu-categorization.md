# OpenListStrm 菜单分类 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 把 `OpenListStrm`(menu_id=2006) 下平铺的 14 个二级菜单，按功能域（同步/STRM/重命名/PT下载）分组到 4 个新的子目录菜单下，并让前端侧边栏支持任意深度的菜单嵌套渲染。

**架构：** 新迁移脚本插入 4 个 `menu_type='M'` 的子目录菜单并 `UPDATE` 现有 14 个菜单的 `parent_id`/`order_num`（`menu_id` 不变，角色授权不受影响）；前端抽一个递归组件 `SidebarMenuItem.vue` 替换 `DesktopLayout.vue`/`MobileLayout.vue` 里写死的两级菜单渲染逻辑。

**技术栈：** Spring Boot + MyBatis-Plus `SimpleDdl`（SQL 文件迁移）、Vue 3 `<script setup>` + Element Plus `el-menu`、Vitest + `@vue/test-utils`。

**设计文档：** [docs/superpowers/specs/2026-07-24-openliststrm-menu-categorization-design.md](../specs/2026-07-24-openliststrm-menu-categorization-design.md) — 完整菜单分组表、menu_id 分配、`el-sub-menu` index 取值方案（用 `menu.name` 而不是 `menu.path`，避免多层目录反推路径相同导致的撞车）的推导过程都在这份文档里，本计划直接落地，不重复推导。

---

## 文件结构

- **创建** `ruoyi-common/src/main/resources/sql/20260736-menu-categories.sql` —— 新增 4 个目录菜单 + 重新挂载 14 个现有菜单
- **修改** `ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java` —— 注册新迁移文件
- **创建** `openlist-web/src/components/SidebarMenuItem.vue` —— 递归侧边栏菜单组件
- **创建** `openlist-web/src/components/__tests__/SidebarMenuItem.spec.ts` —— 递归渲染 + index 唯一性的单元测试
- **修改** `openlist-web/src/layouts/DesktopLayout.vue` —— 接入 `SidebarMenuItem`，删掉写死的两级渲染
- **修改** `openlist-web/src/layouts/MobileLayout.vue` —— 同上

---

### 任务 1：数据库迁移脚本

**文件：**
- 创建：`ruoyi-common/src/main/resources/sql/20260736-menu-categories.sql`
- 修改：`ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java:60`

- [ ] **步骤 1：创建迁移脚本**

```sql
-- ----------------------------
-- 20260736: OpenListStrm 菜单分类 —— 按功能域(同步/STRM/重命名/PT下载)分组
-- 现状：OpenListStrm(2006) 下平铺挂着 14 个二级菜单，随着功能增多难以查找。
-- 本脚本新增 4 个 M 类型子目录，把这 14 个菜单通过 UPDATE parent_id/order_num
-- 挪到对应子目录下，menu_id 不变，不影响现有角色的菜单授权（sys_role_menu 按
-- menu_id 关联，与 parent_id 无关）。
--
-- 幂等性：新增用 INSERT IGNORE + 显式主键；UPDATE 语句本身天然幂等（重复执行
-- 结果不变），配合 SimpleDdl「整文件成功才记入 ddl_history」的机制不会有部分
-- 执行风险。
-- 图标类名均已在 openlist-web/src/composables/useMenuIcon.ts 的 iconMap 中，
-- 避免重蹈历史上"图标不显示"的坑（见 commit 0248e124）。
-- ----------------------------

-- 新增 4 个子目录菜单
INSERT IGNORE INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2067, '同步管理', 2006, 1, '#', '', 'M', '0', '1', NULL, 'fa fa-copy', 'admin', '2026-07-24 00:00:00', '', NULL, '同步任务相关菜单分组'),
(2068, 'STRM管理', 2006, 2, '#', '', 'M', '0', '1', NULL, 'fa fa-video-play', 'admin', '2026-07-24 00:00:00', '', NULL, 'STRM生成相关菜单分组'),
(2069, '重命名管理', 2006, 3, '#', '', 'M', '0', '1', NULL, 'fa fa-edit', 'admin', '2026-07-24 00:00:00', '', NULL, '重命名相关菜单分组'),
(2070, 'PT下载管理', 2006, 4, '#', '', 'M', '0', '1', NULL, 'fa fa-bars', 'admin', '2026-07-24 00:00:00', '', NULL, 'PT下载相关菜单分组');

-- 同步管理(2067)：同步任务配置、同步任务记录
UPDATE `sys_menu` SET `parent_id` = 2067, `order_num` = 1 WHERE `menu_id` = 2025;
UPDATE `sys_menu` SET `parent_id` = 2067, `order_num` = 2 WHERE `menu_id` = 2013;

-- STRM管理(2068)：strm任务配置、STRM生成记录
UPDATE `sys_menu` SET `parent_id` = 2068, `order_num` = 1 WHERE `menu_id` = 2037;
UPDATE `sys_menu` SET `parent_id` = 2068, `order_num` = 2 WHERE `menu_id` = 2019;

-- 重命名管理(2069)：重命名任务配置、重命名规则设置、重命名明细、重命名一致性检查
UPDATE `sys_menu` SET `parent_id` = 2069, `order_num` = 1 WHERE `menu_id` = 2049;
UPDATE `sys_menu` SET `parent_id` = 2069, `order_num` = 2 WHERE `menu_id` = 2060;
UPDATE `sys_menu` SET `parent_id` = 2069, `order_num` = 3 WHERE `menu_id` = 2043;
UPDATE `sys_menu` SET `parent_id` = 2069, `order_num` = 4 WHERE `menu_id` = 2055;

-- PT下载管理(2070)：PT索引器、PT下载器、媒体服务器、PT订阅、PT过滤规则、PT下载记录
UPDATE `sys_menu` SET `parent_id` = 2070, `order_num` = 1 WHERE `menu_id` = 2061;
UPDATE `sys_menu` SET `parent_id` = 2070, `order_num` = 2 WHERE `menu_id` = 2062;
UPDATE `sys_menu` SET `parent_id` = 2070, `order_num` = 3 WHERE `menu_id` = 2063;
UPDATE `sys_menu` SET `parent_id` = 2070, `order_num` = 4 WHERE `menu_id` = 2064;
UPDATE `sys_menu` SET `parent_id` = 2070, `order_num` = 5 WHERE `menu_id` = 2065;
UPDATE `sys_menu` SET `parent_id` = 2070, `order_num` = 6 WHERE `menu_id` = 2066;
```

- [ ] **步骤 2：注册进 `MysqlDdl.java`**

修改 `ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java`，在 `getSqlFiles()` 列表最后一行 `"sql/20260735-pt-downloader-strm-task-link.sql"` 后面追加一行：

```java
                "sql/20260735-pt-downloader-strm-task-link.sql",
                "sql/20260736-menu-categories.sql"
```

（原来最后一行末尾没有逗号，记得给它加上逗号，新行不加逗号。）

- [ ] **步骤 3：验证迁移在真实数据库上执行正确**

本项目的 SQL 迁移脚本一贯没有自动化测试（`MysqlDdl` 是启动时按顺序跑 SQL 文件，靠人工对着真实库验证，参考 `sql/20260719-rename-orphan.sql` 等历史迁移的做法），本次也遵循同样的验证方式：

```bash
mvn clean package -DskipTests
docker compose up -d --build --no-deps backend
```

等后端容器重启完成后，查询确认分组结果（密码从容器环境变量读取，不要手输明文）：

```bash
docker exec osr-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" osr -N -e "SELECT menu_id, menu_name, parent_id, order_num FROM sys_menu WHERE parent_id IN (2006,2067,2068,2069,2070) ORDER BY parent_id, order_num;"'
```

预期输出：`parent_id=2006` 只剩 4 条（2067/2068/2069/2070 这 4 个新目录），其余 14 条分别按 `parent_id` 落在 2067/2068/2069/2070 下，且各组内 `order_num` 从 1 开始连续无重复。

再验证幂等性——重复执行一次迁移（重启后端容器）不应报错，且上面这条查询结果不变：

```bash
docker compose up -d --build --no-deps backend
```

- [ ] **步骤 4：Commit**

```bash
git add ruoyi-common/src/main/resources/sql/20260736-menu-categories.sql ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java
git commit -m "feat: 新增菜单分类迁移脚本，OpenListStrm下14个菜单按功能域分组"
```

---

### 任务 2：前端递归菜单组件 `SidebarMenuItem.vue`（TDD）

**文件：**
- 创建：`openlist-web/src/components/__tests__/SidebarMenuItem.spec.ts`
- 创建：`openlist-web/src/components/SidebarMenuItem.vue`

- [ ] **步骤 1：编写失败的测试**

创建 `openlist-web/src/components/__tests__/SidebarMenuItem.spec.ts`：

```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SidebarMenuItem from '../SidebarMenuItem.vue'
import type { MenuRoute } from '@/stores/user'

// el-sub-menu/el-menu-item/el-icon 在测试环境里没有全局注册 Element Plus，
// 用最小 stub 顶替，只保留渲染 index 属性和具名插槽，方便断言层级结构。
const stubs = {
  'el-sub-menu': {
    props: ['index'],
    template: '<div class="stub-sub-menu" :data-index="index"><div class="stub-title"><slot name="title" /></div><slot /></div>'
  },
  'el-menu-item': {
    props: ['index'],
    template: '<div class="stub-menu-item" :data-index="index"><slot name="title" /></div>'
  },
  'el-icon': { template: '<i><slot /></i>' }
}

function leaf(path: string, title: string): MenuRoute {
  return { path, name: title, meta: { title } }
}

describe('SidebarMenuItem', () => {
  it('叶子菜单渲染成 el-menu-item，index 用自身 path', () => {
    const menu = leaf('/openlist/renameConfig', '重命名规则设置')
    const wrapper = mount(SidebarMenuItem, { props: { menu }, global: { stubs } })

    const item = wrapper.find('.stub-menu-item')
    expect(item.exists()).toBe(true)
    expect(item.attributes('data-index')).toBe('/openlist/renameConfig')
    expect(item.text()).toContain('重命名规则设置')
    expect(wrapper.find('.stub-sub-menu').exists()).toBe(false)
  })

  it('目录菜单渲染成 el-sub-menu，index 用 menu.name 而不是 path', () => {
    const menu: MenuRoute = {
      path: '/openliststrm',
      name: '同步管理',
      meta: { title: '同步管理' },
      children: [
        leaf('/openliststrm/task', '同步任务配置'),
        leaf('/openliststrm/copy', '同步任务记录')
      ]
    }
    const wrapper = mount(SidebarMenuItem, { props: { menu }, global: { stubs } })

    const subMenu = wrapper.find('.stub-sub-menu')
    expect(subMenu.exists()).toBe(true)
    expect(subMenu.attributes('data-index')).toBe('同步管理')
    expect(subMenu.text()).toContain('同步管理')

    const items = wrapper.findAll('.stub-menu-item')
    expect(items).toHaveLength(2)
    expect(items[0].attributes('data-index')).toBe('/openliststrm/task')
    expect(items[1].attributes('data-index')).toBe('/openliststrm/copy')
  })

  it('三级嵌套（目录>子目录>叶子）逐级递归渲染，父子目录即使 path 相同，index(name) 也不会撞车', () => {
    const menu: MenuRoute = {
      path: '/openliststrm',
      name: 'OpenListStrm',
      meta: { title: 'OpenListStrm' },
      children: [
        {
          // 刻意让子目录的 path 和父目录一样，模拟后端 derivePath 反推路径撞车的真实场景，
          // 验证用 name 当 index 不受这个影响
          path: '/openliststrm',
          name: '同步管理',
          meta: { title: '同步管理' },
          children: [leaf('/openliststrm/task', '同步任务配置')]
        }
      ]
    }
    const wrapper = mount(SidebarMenuItem, { props: { menu }, global: { stubs } })

    const subMenus = wrapper.findAll('.stub-sub-menu')
    expect(subMenus).toHaveLength(2)
    expect(subMenus[0].attributes('data-index')).toBe('OpenListStrm')
    expect(subMenus[1].attributes('data-index')).toBe('同步管理')
    expect(subMenus[0].attributes('data-index')).not.toBe(subMenus[1].attributes('data-index'))

    const item = wrapper.find('.stub-menu-item')
    expect(item.attributes('data-index')).toBe('/openliststrm/task')
  })
})
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd openlist-web && npx vitest run src/components/__tests__/SidebarMenuItem.spec.ts`
预期：FAIL，报错找不到 `../SidebarMenuItem.vue` 模块（组件还不存在）。

- [ ] **步骤 3：编写组件实现**

创建 `openlist-web/src/components/SidebarMenuItem.vue`：

```vue
<template>
  <el-sub-menu v-if="menu.children?.length" :index="menu.name || menu.path">
    <template #title>
      <el-icon v-if="getIconComponent(menu.meta?.icon)"><component :is="getIconComponent(menu.meta?.icon)" /></el-icon>
      <span>{{ menu.meta?.title }}</span>
    </template>
    <SidebarMenuItem v-for="child in menu.children" :key="child.path" :menu="child" />
  </el-sub-menu>
  <el-menu-item v-else :index="menu.path">
    <el-icon v-if="getIconComponent(menu.meta?.icon)"><component :is="getIconComponent(menu.meta?.icon)" /></el-icon>
    <template #title>{{ menu.meta?.title }}</template>
  </el-menu-item>
</template>

<script setup lang="ts">
import type { MenuRoute } from '@/stores/user'
import { getIconComponent } from '@/composables/useMenuIcon'

defineProps<{ menu: MenuRoute }>()
</script>
```

（`<script setup>` 编译后组件会以文件名 `SidebarMenuItem` 作为隐式组件名，模板里可以直接递归引用自己，不需要额外 `defineOptions({ name: ... })`——项目 Vue 版本 3.4.21 已支持这个特性。）

- [ ] **步骤 4：运行测试验证通过**

运行：`cd openlist-web && npx vitest run src/components/__tests__/SidebarMenuItem.spec.ts`
预期：PASS，3 个用例全部通过。

- [ ] **步骤 5：Commit**

```bash
git add openlist-web/src/components/SidebarMenuItem.vue openlist-web/src/components/__tests__/SidebarMenuItem.spec.ts
git commit -m "feat: 新增递归侧边栏菜单组件SidebarMenuItem，支持任意深度菜单嵌套"
```

---

### 任务 3：接入 `DesktopLayout.vue`

**文件：**
- 修改：`openlist-web/src/layouts/DesktopLayout.vue:20-39`（模板里写死的两级菜单渲染）
- 修改：`openlist-web/src/layouts/DesktopLayout.vue:102`（`getIconComponent` import，替换后不再使用，需要删除）

- [ ] **步骤 1：替换模板里写死的两级菜单渲染**

把这一段（第 20-39 行）：

```vue
          <template v-for="menu in sidebarMenus" :key="menu.path">
            <el-sub-menu v-if="menu.children?.length" :index="menu.children[0].path.startsWith('/') ? menu.children[0].path : menu.path + '/' + menu.children[0].path">
              <template #title>
                <el-icon v-if="getIconComponent(menu.meta?.icon)"><component :is="getIconComponent(menu.meta?.icon)" /></el-icon>
                <span>{{ menu.meta?.title }}</span>
              </template>
              <el-menu-item
                v-for="sub in menu.children"
                :key="sub.path"
                :index="sub.path.startsWith('/') ? sub.path : menu.path + '/' + sub.path"
              >
                <el-icon v-if="getIconComponent(sub.meta?.icon)"><component :is="getIconComponent(sub.meta?.icon)" /></el-icon>
                <template #title>{{ sub.meta?.title }}</template>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="menu.path">
              <el-icon v-if="getIconComponent(menu.meta?.icon)"><component :is="getIconComponent(menu.meta?.icon)" /></el-icon>
              <template #title>{{ menu.meta?.title }}</template>
            </el-menu-item>
          </template>
```

替换成：

```vue
          <SidebarMenuItem v-for="menu in sidebarMenus" :key="menu.path" :menu="menu" />
```

- [ ] **步骤 2：删除不再使用的 `getIconComponent` import**

`DesktopLayout.vue` 的 `<script setup>` 里，第 102 行：

```typescript
import { getIconComponent } from '@/composables/useMenuIcon'
```

替换后模板里已经没有任何地方调用 `getIconComponent`（图标渲染逻辑挪进了 `SidebarMenuItem.vue`），这一行整行删除。项目 `tsconfig.json` 开了 `noUnusedLocals: true`，不删会导致 `npm run build` 的 vue-tsc 检查报错。

`SidebarMenuItem` 组件本身不需要显式 import——`vite.config.ts` 里 `unplugin-vue-components` 的 `Components()` 插件默认扫描 `src/components` 目录自动注册；但为了和同文件里 `ChangePasswordDialog` 的写法保持一致（该文件对 `src/components` 下的组件采用显式 import），在 `<script setup>` 的 import 区加一行：

```typescript
import SidebarMenuItem from '@/components/SidebarMenuItem.vue'
```

- [ ] **步骤 3：类型检查验证**

运行：`cd openlist-web && npx vue-tsc --noEmit`
预期：无报错（尤其确认没有"unused import"和模板类型错误）。

- [ ] **步骤 4：Commit**

```bash
git add openlist-web/src/layouts/DesktopLayout.vue
git commit -m "refactor: DesktopLayout侧边栏改用递归组件SidebarMenuItem，支持三级菜单"
```

---

### 任务 4：接入 `MobileLayout.vue`

**文件：**
- 修改：`openlist-web/src/layouts/MobileLayout.vue:29-48`（写死的两级菜单渲染）
- 修改：`openlist-web/src/layouts/MobileLayout.vue:125`（`getIconComponent` import，替换后不再使用，需要删除）

- [ ] **步骤 1：替换模板里写死的两级菜单渲染**

把这一段（第 29-48 行）：

```vue
            <template v-for="menu in sidebarMenus" :key="menu.path">
              <el-sub-menu v-if="menu.children?.length" :index="menu.path">
                <template #title>
                  <el-icon v-if="getIconComponent(menu.meta?.icon)"><component :is="getIconComponent(menu.meta?.icon)" /></el-icon>
                  <span>{{ menu.meta?.title }}</span>
                </template>
                <el-menu-item
                  v-for="sub in menu.children"
                  :key="sub.path"
                  :index="sub.path.startsWith('/') ? sub.path : menu.path + '/' + sub.path"
                >
                  <el-icon v-if="getIconComponent(sub.meta?.icon)"><component :is="getIconComponent(sub.meta?.icon)" /></el-icon>
                  <template #title>{{ sub.meta?.title }}</template>
                </el-menu-item>
              </el-sub-menu>
              <el-menu-item v-else :index="menu.path">
                <el-icon v-if="getIconComponent(menu.meta?.icon)"><component :is="getIconComponent(menu.meta?.icon)" /></el-icon>
                <template #title>{{ menu.meta?.title }}</template>
              </el-menu-item>
            </template>
```

替换成：

```vue
            <SidebarMenuItem v-for="menu in sidebarMenus" :key="menu.path" :menu="menu" />
```

- [ ] **步骤 2：删除不再使用的 `getIconComponent` import，加上 `SidebarMenuItem` import**

`MobileLayout.vue` 第 125 行删除：

```typescript
import { getIconComponent } from '@/composables/useMenuIcon'
```

在同一块 import 区加上（与 `DesktopLayout.vue` 保持一致的显式 import 风格）：

```typescript
import SidebarMenuItem from '@/components/SidebarMenuItem.vue'
```

- [ ] **步骤 3：类型检查验证**

运行：`cd openlist-web && npx vue-tsc --noEmit`
预期：无报错。

- [ ] **步骤 4：Commit**

```bash
git add openlist-web/src/layouts/MobileLayout.vue
git commit -m "refactor: MobileLayout侧边栏改用递归组件SidebarMenuItem，支持三级菜单"
```

---

### 任务 5：端到端验证

**文件：** 无新增/修改，仅验证。

- [ ] **步骤 1：跑一次完整单元测试**

运行：`cd openlist-web && npm run test:unit`
预期：全部通过，包括任务 2 新增的 3 个用例。

- [ ] **步骤 2：起本地 dev server 人工验证前端**

```bash
cd openlist-web && npm run dev
```

登录后检查：
- 桌面端侧边栏出现三级缩进结构：`OpenListStrm` → `同步管理`/`STRM管理`/`重命名管理`/`PT下载管理` → 各自的叶子页面，4 个新分组图标（复制/播放/编辑/下载图标）正常显示
- 移动端（抽屉菜单）同样呈现三级结构
- 点击任意叶子菜单能正确跳转到原页面，浏览器地址栏路径不变（比如"同步任务记录"还是跳到 `/openliststrm/copy`）
- 侧边栏折叠态（点击顶部汉堡按钮）下，子目录 popper 展开正常，多层级不错位
- 抽查 1-2 个原本在 `KEEP_ALIVE_COMPONENTS` 里的页面（比如同步任务记录），来回切换页面后筛选条件和分页状态还在，确认三级化没有影响 keep-alive

- [ ] **步骤 3：后端菜单数据回归检查**

```bash
docker exec osr-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" osr -N -e "SELECT menu_id, menu_name, parent_id, menu_type FROM sys_menu WHERE menu_type=\"F\" AND parent_id IN (2006,2013,2019,2025,2037,2043,2049,2055,2060,2061,2062,2063,2064,2065,2066);"'
```

预期：能查到原本挂在这 14 个二级菜单下的按钮级权限（`F` 类型，比如 `openliststrm:copy:add`），`parent_id` 都还指向对应的二级菜单本身（2013/2019/... 这些 `menu_id` 没有被本次迁移误改），确认脚本只动了二级菜单自己的 `parent_id`，没有误伤按钮权限。

- [ ] **步骤 4：确认无遗留问题后，本任务计划执行完成**

无需额外 commit（前面每个任务已各自提交）。
