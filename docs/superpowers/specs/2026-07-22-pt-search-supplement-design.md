# PT 订阅搜索补集设计

**日期**：2026-07-22
**作者**：Jack（与 Claude 协作）
**前置阅读**：`docs/superpowers/specs/2026-07-21-pt-subscription-download-design.md`（本设计是其 §8 扩展点清单里「站内搜索补集」的落地）

## 1. 背景与目标

### 1.1 问题

现有 PT 订阅功能（`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/`）完全依赖 RSS 被动轮询：`TorznabClient.fetch()` 只调用 `t=search` 不带关键词，本质是拉索引器的"最新发布列表"。这意味着：

- 用户订阅一部已经播出完毕的老剧时，前面缺的集如果索引器的 RSS 流不会重复推送（大多数 Torznab 索引器 RSS 只覆盖近期发布），这些缺集**理论上永远等不到匹配**，完全靠运气。
- 系统没有任何主动检索能力，`q=` 关键词参数从代码库诞生起就没被用过。

### 1.2 目标

在已订阅老剧/电影出现缺集时，允许用户（或系统按周期自动）用关键词主动搜索索引器，找到资源后走与 RSS 完全相同的过滤择优/下载/追踪/入库链路。

**范围限定**：只服务「补全已订阅作品的缺集」场景，不做"不建订阅直接搜一部片子下载"的独立入口——用户明确不需要后者。

### 1.3 成功标准

1. 用户在订阅详情页对缺集点击搜索，能在几秒内看到"已推送下载"或"未搜索到匹配资源"的明确反馈。
2. 搜到的资源经过与 RSS 匹配完全一致的过滤择优/去重/占位链路，不会绕过质量过滤规则，也不会与并发的 RSS 轮询产生重复下载。
3. 开启"自动补搜"的订阅无需人工介入，能在设定周期内自动尝试补齐缺集。
4. 索引器不可用/搜索无结果时不产生脏数据，不影响其他订阅或其他索引器。

## 2. 架构

### 2.1 与现有链路的关系

不新建"搜索"这一独立的数据模型或推送编排逻辑，而是把它做成**现有 RSS 推送引擎的另一个入口**：RSS 轮询是"批量种子 → 匹配所有活跃订阅 → 逐组过滤择优推送"，搜索补集是"已知目标订阅 + 关键词 → 单组过滤择优推送"，两者在"过滤择优 → 原子占位 → 落库 → 推送下载器"这一步完全复用同一段代码（`SubscriptionEngine.handleGroup`）。

搜索补集的职责边界与 RSS 推送一致：**终止于把种子推给下载器**。下载追踪（`DownloadTrackTask`）、库对账（`LibrarySyncTask`）、rename/scrape 链路完全不用改，新产生的 `pt_download_record` 与 RSS 产生的记录在结构上没有任何区别。

### 2.2 数据流

```
用户点击"搜索补齐"(订阅详情页顶部，整季/整部) 或 缺集行尾"搜该集"(单集)
        │
        ▼
前端用 订阅.title + season(+episode) 拼出默认关键词，弹框展示，可编辑
        │
        ▼
POST /api/openliststrm/pt-subscriptions/{id}/search  { episode, keyword }
        │
        ▼
SearchSupplementService.supplement(subId, episode, keyword)
        │
        ├─▶ SearchSupplementService.searchAcrossIndexers(keyword)
        │     并发向所有 enabled=1 的索引器调用 TorznabClient.search(indexer, keyword)（新方法，
        │     t=search&q=关键词，复用现有 buildUrl/execute/TorznabParser.parse）；
        │     单个索引器超时/异常只记 log，不影响其他索引器，也不产生告警（与 RSS 轮询的告警机制无关）
        │
        ├─▶ 对每条结果调用 SubscriptionEngine.fillParsed（原私有方法改包内可见）做本地标题解析，
        │     填充 parsedResolution/parsedSource 等字段——过滤引擎的分辨率白名单要靠这些字段生效
        │
        └─▶ SubscriptionEngine.pushBest(订阅, episode, 候选列表)（新增方法，内部 new MatchResult(sub, episode)
              后直接调用包内可见的 handleGroup，与 RSS 走同一段过滤择优/原子占位/落库/推送代码）
        │
        ▼
回写 pt_subscription.last_search_time，返回 { pushed, candidateCount }
        │
        ▼
前端据 pushed 提示"已找到并推送下载" / "未搜索到匹配资源"，刷新进度
```

`episode` 语义与现有 `SubscriptionMatcher` 常量完全一致：`-1`（`SEASON_PACK`）表示整季/整部搜索，命中后一次性占位该订阅所有 `MISSING` 集；具体数字表示单集补搜；电影订阅恒为 `0`。

### 2.3 关键设计取舍

- **不做标题匹配校验**：`SubscriptionMatcher` 的标题归一化匹配是为了"一批种子里挑出属于哪个订阅"，但搜索场景里目标订阅已经由用户显式指定，跳过标题匹配直接复用 `handleGroup` 的过滤择优逻辑。代价是如果关键词起得过于宽泛，理论上可能匹配到不相关资源；这个风险通过"关键词默认由标题+季集号拼出、用户可编辑核实后再搜"来缓释，而非在代码里加额外校验。
- **自动补搜只做整季/整部粒度**：不会对每个缺集单独发请求，避免一部缺 20 集的老剧每天对索引器打 20 次请求。手动单集搜索是给用户的补充手段，自动补搜不使用这个粒度。
- **自动补搜频率是全局固定值**，不支持每订阅单独配置周期——YAGNI，等真有需求再加。
- **无结果不做特殊反馈机制**：不建搜索历史表，不做重试退避。手动搜索前端直接提示，自动补搜完全静默，靠 `last_search_time` 到期后自然重试。

## 3. 数据模型改动

新增迁移脚本 `ruoyi-common/src/main/resources/sql/20260722-pt-search-supplement.sql`，沿用 `20260728-pt-subscription-original-title.sql` 已建立的幂等写法（`INFORMATION_SCHEMA.COLUMNS` 查询列是否存在 + 动态 SQL，而非 `ADD COLUMN IF NOT EXISTS`——后者在项目实际使用的 MySQL 版本上行为不稳定，代码库里从未用过）：

```sql
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pt_subscription' AND COLUMN_NAME = 'auto_search');
SET @sql := IF(@exist = 0, 'ALTER TABLE `pt_subscription` ADD COLUMN `auto_search` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''0'' COMMENT ''是否开启自动定时补搜 0-否 1-是'' AFTER `last_match_time`', 'SELECT ''Column auto_search already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pt_subscription' AND COLUMN_NAME = 'last_search_time');
SET @sql := IF(@exist = 0, 'ALTER TABLE `pt_subscription` ADD COLUMN `last_search_time` datetime(0) NULL DEFAULT NULL COMMENT ''上次发起搜索补集的时间，用于自动补搜到期判断与前端展示'' AFTER `auto_search`', 'SELECT ''Column last_search_time already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pt_filter_config' AND COLUMN_NAME = 'auto_search_interval_hours');
SET @sql := IF(@exist = 0, 'ALTER TABLE `pt_filter_config` ADD COLUMN `auto_search_interval_hours` int(10) NOT NULL DEFAULT 24 COMMENT ''自动补搜的全局周期(小时)'' AFTER `preferred_size`', 'SELECT ''Column auto_search_interval_hours already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

`PtSubscriptionPlus` 加两个字段：`autoSearch`（`@TableField("auto_search")`）、`lastSearchTime`（`@TableField("last_search_time")`）。
`PtFilterConfigPlus` 加一个字段：`autoSearchIntervalHours`。

不新建任何表——搜索产生下载后的记录与 RSS 完全共用 `pt_download_record`，无结果的搜索不落任何数据。

## 4. 后端组件改动清单

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `pt/indexer/TorznabClient.java` | 改动 | 新增 `public List<TorrentInfo> search(PtIndexerPlus indexer, String keyword)`，复用 `buildUrl`（多加 `q` 查询参数）与 `execute`/`TorznabParser.parse` |
| `pt/subscription/SubscriptionEngine.java` | 改动 | `handleGroup` 可见性由 `private` 改为包内可见；`fillParsed` 同样改包内可见；新增 `public boolean pushBest(PtSubscriptionPlus sub, int episode, List<TorrentInfo> candidates)`，内部构造 `MatchResult` 后委托 `handleGroup` |
| `pt/subscription/SearchSupplementService.java` | 新建 | `searchAcrossIndexers(keyword)` 并发查询所有启用索引器（`Executors.newVirtualThreadPerTaskExecutor()`，写法同 `AsynHelper.fetchCopyInfoParallel`），单索引器异常吞掉记 log；`supplement(subId, episode, keyword)` 编排"搜索 → fillParsed → pushBest → 回写 last_search_time"，返回 `SupplementResult(pushed, candidateCount)` |
| `pt/subscription/dto/SupplementResult.java` | 新建 | 简单 DTO：`boolean pushed`、`int candidateCount` |
| `pt/task/AutoSearchService.java` | 新建 | 扫 `auto_search='1' AND status='ACTIVE'` 的订阅，逐个判断 `last_search_time` 是否到期（对齐 `pt_filter_config.auto_search_interval_hours`）；订阅当前没有任何 `MISSING` 集的直接跳过，不发请求；到期且有缺集的才用订阅信息拼默认关键词、`episode` 固定传季包/电影哨兵值，调用 `SearchSupplementService.supplement`；异常只记 log，不通知 |
| `pt/task/AutoSearchTask.java` | 新建 | 调度器外壳，风格同 `LibrarySyncTask`：`@EventListener(ApplicationReadyEvent.class)` 启动，`virtualScheduledExecutor` 心跳 30 分钟 |
| `controller/api/PtSubscriptionRestController.java` | 改动 | 新增 `POST /{id}/search`，body `{episode, keyword}` → `Result<SupplementResult>` |

## 5. API

```
POST /api/openliststrm/pt-subscriptions/{id}/search
Body: { "episode": number, "keyword": string }
Resp: { "code": 200, "data": { "pushed": boolean, "candidateCount": number } }
```

- `episode` 合法性由 `SearchSupplementService` 校验：电影订阅只接受 `0`；剧集订阅接受 `-1` 或 `1..totalEpisodes` 范围内的数字；非法值返回 `Result.error`。
- 订阅不存在、订阅已暂停（`status=PAUSED`）时返回 `Result.error`，与 `refresh`/`pause` 等现有接口的错误处理风格一致。
- `auto_search` 开关本身不新增专属接口，走已有的通用 `PUT /api/openliststrm/pt-subscriptions` 编辑接口更新 `autoSearch` 字段。

## 6. 前端改动

`openlist-web/src/views/openlist/ptSubscription/index.vue` + `composables/usePtSubscription.ts` + `api/openlist/ptSubscription.ts`：

1. 列表新增两列：「自动补搜」（`el-switch`，切换调用通用编辑接口）、「上次搜索」（展示 `lastSearchTime`，`-` 表示从未搜索，样式同现有「上次命中」列）。
2. 进度弹窗（`showProgress` 已有弹窗）顶部加「搜索补齐」按钮：弹出关键词确认框（预填默认关键词，可编辑输入框），确认后调用搜索接口，按钮期间 `loading` 防重复点击。剧集传 `episode=-1`，电影传 `episode=0`。
3. 进度弹窗里 `missingEpisodes` 列表每一项旁加一个小「搜」按钮（仅剧集类型显示），点击同样弹关键词确认框，预填单集关键词，`episode` 传具体集号。
4. 默认关键词生成规则（前端本地拼接，不额外调后端）：
   - 电影：`{title}`
   - 剧集整季：`{title} S{season:02d}`（如 `S02` 而非 `S2`）
   - 剧集单集：`{title} S{season:02d}E{episode:02d}`
5. 搜索请求返回后：`pushed=true` 提示"已找到并推送下载"，`pushed=false` 提示"未搜索到匹配资源"；无论结果都重新拉一次进度接口刷新页面。

移动端 `views-mobile/` 目前没有 PT 订阅页面（与现有 PT 功能一致，不在本次范围内新增）。

## 7. 测试计划

对齐现有 `pt` 包的测试覆盖风格（`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/**`）：

- `TorznabClient.search`：URL 正确带上 `q` 参数、复用现有 XML 解析（可仿 `TorznabClient` 现有测试补一个用例）
- `SubscriptionEngine.pushBest`：季包目标一次占位所有 MISSING 集、单集目标只占位对应集、无可用下载器/推送失败时正确回滚（复用 `handleGroup` 现有测试思路）
- `SearchSupplementService`：多索引器并发搜索时单个失败不影响其他、搜索结果为空时返回 `pushed=false` 且不落任何记录、episode 非法值校验
- `AutoSearchService`：到期判断（对齐 `RssPollService.isDue` 的测试写法）、只处理 `auto_search=1 且 ACTIVE` 的订阅、无 MISSING 集的订阅跳过

## 8. 不做的事情（本次范围之外）

- 不做"不建订阅、直接关键词搜一部片子下载"的独立入口
- 不做搜索结果人工选种 UI（列表展示候选让用户挑）
- 不做搜索历史/日志表
- 不做每订阅可单独配置的自动补搜周期
- 不做失败重试退避机制
- 不做搜索结果的标题相关性二次校验
