# 重命名 STRM 一致性检查（孤儿清理）设计文档

## 背景与目标

当前重命名流程（`rename_detail` 表）会把本地新出现的媒体文件（含 STRM 生成产出的 `.strm` 文件）整理、重命名到媒体库目录，并异步生成 NFO/图片（`ScrapeService`）。但缺少反向的一致性检查：

- 网盘源文件被删除/改名后，本地已重命名的 `.strm` 文件仍然指向一个失效链接，用户点开会播放失败，且残留的 NFO/图片和数据库记录也变成脏数据。
- 本地重命名产物被人工手动删除后，`rename_detail` 记录、以及对应的 NFO/图片仍然留在系统里，长期积累成垃圾数据。

本功能只针对 **`.strm` 类型的重命名产物**（因为 `.strm` 文件内容本身编码了网盘源路径，可以直接反查；Copy 同步下来的实体文件不依赖网盘存活，不在本次范围内）。目标是：定期/手动扫描出上述两类不一致状态，生成待清理列表供人工确认，确认后批量清理残留文件与数据库记录。

## 范围

**包含：**
- 遍历 `rename_detail` 中 `status=1`（重命名成功）且 `new_name` 以 `.strm` 结尾的记录
- 检测两种孤儿状态：
  1. `local_missing`：本地 `.strm` 文件已被人工删除，但 `rename_detail` 记录及关联 NFO/图片还在
  2. `source_missing`：本地 `.strm` 文件仍存在，但其内容指向的网盘源文件已经不存在
- 待清理项落库、前端展示、批量清理/忽略
- 定时任务 + 前端手动触发两种扫描入口

**不包含（后续可扩展，本次不做）：**
- Copy 同步产生的实体文件的一致性检查
- STRM 任务表（`openlist_strm`）本身的孤儿检查
- 自动清理（默认且唯一策略是"仅报告，人工确认后再删"）

## 数据模型

新增表 `rename_orphan`：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint, PK, 自增 | 主键 |
| detail_id | int | 关联 `rename_detail.id` |
| new_path | varchar | 冗余保存重命名后目录，记录被删后仍可追溯 |
| new_name | varchar | 冗余保存重命名后文件名 |
| title | varchar | 冗余展示字段 |
| year | varchar | 冗余展示字段 |
| media_type | varchar | movie / tv，冗余展示字段 |
| reason | varchar | `local_missing` / `source_missing` |
| status | varchar | 0-待处理 1-已清理 2-已忽略 |
| found_time | datetime | 本次（或最近一次）发现时间 |
| clean_time | datetime | 清理/忽略处理时间 |

按 `detail_id` 做 upsert 语义：
- 新扫描到孤儿 → 不存在则插入（`status=0`），存在且仍是待处理状态则更新 `found_time`
- 若某条待处理记录本轮扫描时文件/网盘源已经恢复正常（比如用户手动放回文件），直接从表中删除该待处理项，不需要人工忽略

## 扫描流程

`RenameOrphanScanServiceImpl.scan()`：

1. 分页查询 `rename_detail`，过滤 `status=1` 且 `new_name` 以 `.strm` 结尾
2. **第一阶段——本地存在性**：对每条记录 `Files.exists(Paths.get(newPath, newName))`
   - 不存在 → 直接判定为 `local_missing`，写入待清理表，**不进入第二阶段**
3. **第二阶段——网盘源存在性**（仅处理本地文件仍存在的记录）：
   1. 读取 `.strm` 文件内容，以当前 `config.getOpenListUrl() + "/d"` 作为前缀定位，截取后续部分；若开启了路径编码（`config.getOpenListStrmEncode()`），做对应的 URL 解码，还原出网盘源文件的完整路径
   2. 若内容中找不到匹配的 baseUrl 前缀（比如历史文件是用旧的 OpenList 域名生成的，配置已变更），该记录标记为"无法解析"并跳过，计入扫描汇总日志，不参与孤儿判定（避免误判）
   3. 将成功解析出源路径的记录按父目录分组
   4. 每个目录调用一次 `openListApi.getOpenlist(dir, refresh=false)`（复用 STRM 遍历时的信号量并发控制 `config.getTraversalConcurrency()`，走 AList 自身缓存不强制刷新，减轻对 OpenList 的压力），拿到该目录当前文件名集合
      - 若接口返回非 200（整个目录已被删除）→ 该目录下所有记录都判定为 `source_missing`
   5. 内存比对：解析出的文件名不在返回的集合中 → `source_missing`，写入待清理表
4. 扫描结束后记录本次汇总日志（新增待清理数、跳过的无法解析数、耗时）

## 清理动作

`RenameOrphanRestController` 提供批量 `clean`：

- 逐条按 `reason` 区分处理：
  - `source_missing`：先删除本地 `.strm` 文件本身（`Files.deleteIfExists`）
  - `local_missing`：文件本来就不存在，跳过删除文件这步
- 两种 reason 都调用现有 `ScrapeService.deleteScrapeFiles(detail)` 清理关联 NFO/图片（该方法已内置"同季/同剧是否还有其他记录"的判断，直接复用）
- 删除 `rename_detail` 原记录
- 更新 `rename_orphan.status=1`，写 `clean_time`

批量 `ignore`：仅将 `rename_orphan.status` 置为 2，写 `clean_time`，不做任何文件操作。

## API 设计

挂在 `ruoyi-openliststrm` 现有 controller 风格下，新增 `controller/api/RenameOrphanRestController`：

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/openliststrm/rename-orphan/scan` | 手动触发全量扫描（数量大时走 `AsyncManager` 异步执行，前端轮询最近扫描状态） |
| GET | `/api/openliststrm/rename-orphan/list` | 分页查询，支持按 `status`/`reason`/`title` 过滤 |
| POST | `/api/openliststrm/rename-orphan/clean` | 批量确认清理，body 传 id 列表 |
| POST | `/api/openliststrm/rename-orphan/ignore` | 批量忽略，body 传 id 列表 |

## 定时任务

`task/OpenListStrmTask` 新增方法 `checkRenameOrphan()`，内部调用扫描 service。`ruoyi-common/src/main/resources/sql/` 新增迁移脚本注册 `sys_job`：任务名 `openliststrm-重命名一致性检查`，默认 cron 每天一次（具体时间避开现有 copy(3点)/strm(5点) 任务，比如 `0 0 6 * * ?`），可在定时任务页面自由调整。

## 前端

新页面 `openlist-web/src/views/openlist/renameOrphan/index.vue`（及移动端 `views-mobile/renameOrphan`），布局参考现有 `renameDetail` 列表页：

- 顶部：最近一次扫描时间 + "立即扫描"按钮（触发后禁用按钮并轮询状态，扫描中给 loading 提示）
- 列表列：标题 / 年份 / 媒体类型 / 原因（标签区分：本地丢失=橙色，网盘源丢失=红色）/ 重命名后路径 / 发现时间 / 状态
- 批量操作：清理、忽略（危险操作二次确认弹窗）
- 菜单：`sys_menu` 新增一条，挂在现有重命名相关父菜单下，需要对应的按钮级权限（清理/忽略/扫描）

## 边界情况与风险

- **baseUrl 变更**：用户中途更换过 OpenList 域名配置，历史 `.strm` 内容里的 baseUrl 前缀会对不上——按上述"无法解析则跳过"处理，避免误判为 `source_missing`
- **扫描期间的正常变更**：目录结构在扫描过程中被正常的同步/重命名任务修改，可能导致个别记录短暂误报——由于清理策略是"仅报告、人工确认后再删"，不会自动产生破坏性后果，下一轮扫描会自动纠正（文件恢复正常则从待清理表移除）
- **网络盘符/权限异常**：`Files.exists` 或 `openListApi.getOpenlist` 单次调用异常不应中断整批扫描，需要 try/catch 隔离，记录日志后继续下一条/下一目录
- **并发**：扫描是纯读操作，不加锁；清理操作与其他重命名/刮削流程可能存在文件竞争（比如清理时另一个任务又重新生成了同名文件），概率低，此次不做额外加锁保护，后续如有实际问题再补

## 测试计划

- 单元测试：`.strm` 内容解析（含/不含 URL 编码、baseUrl 不匹配的降级路径）
- 单元测试：扫描两阶段逻辑（mock `Files.exists` 和 `openListApi.getOpenlist` 返回不同组合，验证 reason 判定正确）
- 单元测试：清理动作对 `local_missing` 和 `source_missing` 两种 reason 的不同处理路径
- 集成/手动验证：真实起一个测试重命名任务生成 `.strm`，手动删除网盘源文件后跑扫描，确认能扫出 `source_missing` 并正确清理
