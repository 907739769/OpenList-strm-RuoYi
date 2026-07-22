# PT 订阅搜索补集：IMDb/TMDB ID 优先搜索设计

**日期**：2026-07-22
**作者**：Jack（与 Claude 协作）
**前置阅读**：
- `docs/superpowers/specs/2026-07-22-pt-search-supplement-design.md`（搜索补集主体设计：过滤择优/推送链路）
- `docs/superpowers/specs/2026-07-22-pt-search-supplement-bilingual-keyword-design.md`（中英文标题兜底设计）

**与前一份文档的关系**：本设计在"中文标题 → 英文/原语言标题"两级回退之前，再加一级"ID 精确搜索"，三级合并成完整的回退链。前一份文档里"中文优先、无结果补英文"的行为**保持不变**，只是整体顺序上挪到第 2/3 级；`filterByTarget`/`filterMovieCandidates` 等过滤标准同样保持不变。

## 1. 背景与目标

### 1.1 问题

即使有了中英文双语标题兜底，本质上仍然是"拼字符串关键词交给索引器做全文检索"——索引器的全文检索是模糊匹配，标题本身的翻译差异、命名习惯差异（有无年份/标点/繁简体）都可能导致搜不到或搜到无关内容，过滤层只能在搜到的结果里去伪存真，没法把"搜不到"变成"搜到"。

订阅本身在建订阅时就记录了 `tmdbId`（TMDb 官方 ID），如果索引器支持按外部 ID（IMDb ID / TMDB ID）检索，这是比标题字符串精确得多的匹配方式——命中的候选就是索引器自己数据库里关联了该 ID 的资源，不存在"标题像但其实是另一部作品"的问题。

### 1.2 目标

在中英文标题搜索之前新增一级 ID 精确搜索：优先用 IMDb ID（国内 PT 站——多为 NexusPHP 内核——挂载外部 ID 的主流做法），TMDB ID 作为补充；仅对通过 `t=caps` 探测确认支持该参数的索引器发起 ID 搜索，不支持的索引器自动跳过，不受影响。

### 1.3 成功标准

1. 索引器支持 IMDb/TMDB ID 搜索时，命中的候选无需依赖标题字符串匹配质量，直接进入过滤择优阶段。
2. 索引器不支持 ID 搜索（多数国内 PT 站的常见情况）时，行为与本设计上线前完全一致——直接走中英文标题搜索，不发多余请求、不报错。
3. 老订阅（本设计上线前建的，`imdbId` 为 null）自动降级为标题搜索，不需要人工补录数据。
4. 过滤标准不因"候选是 ID 命中还是标题命中"而改变——ID 命中的候选依然要过标题/年份/季集号校验，防止索引器对 ID 参数实现有 bug（比如把 `imdbid` 当普通关键词分词）导致误推。

## 2. 架构

### 2.1 三级回退顺序

```
supplement(subId, episode, keyword)
  │
  ├─ 第1级：ID 搜索
  │    对每个 enabled 索引器：
  │      capability = capabilityCache.get(indexer)   ← 探测/读缓存，见 §2.2
  │      电影订阅：
  │        capability.movieImdbSupported() && sub.imdbId 非空 → t=movie&imdbid=...
  │        否则 capability.movieTmdbSupported() && sub.tmdbId 非空 → t=movie&tmdbid=...
  │        否则跳过该索引器（不发请求）
  │      剧集订阅：同理，t=tvsearch&imdbid=...&season=..(&ep=..)，或 tmdbid 版本
  │    合并所有索引器结果 → fillParsed → filterByTarget → 非空则 pushBest，结束
  │
  ├─ 第2级：中文标题搜索（keyword）→ 过滤 → 非空则推送，结束        （与前一份文档一致，未改动）
  │
  └─ 第3级：英文/原语言标题搜索（originalTitle）→ 过滤 → 推送        （与前一份文档一致，未改动）
```

同一优先级内部**只选一个 ID**，不会对同一个索引器同时带 `imdbid` 和 `tmdbid` 两个参数——避免索引器把"两个 ID 都要匹配"当成 AND 条件，反而因为其中一个库对不上而返回空。`imdbId` 优先，只有索引器不支持 `imdbid` 或订阅没有 `imdbId`（老订阅/TMDb 没返回）时才试 `tmdbid`。

### 2.2 能力探测：解析 `t=caps`，进程内缓存

新增 `IndexerCapability`（`pt/indexer/` 包内的简单 record）：

```java
public record IndexerCapability(boolean movieImdbSupported, boolean movieTmdbSupported,
                                 boolean tvImdbSupported, boolean tvTmdbSupported) {
    public static final IndexerCapability NONE = new IndexerCapability(false, false, false, false);
}
```

`TorznabClient` 新增 `getCaps(indexer)`：调用现有 `buildUrl(indexer, "caps")` + `execute()`，响应体交给新的 `TorznabCapsParser.parse(xml)` 解析 `<searching><movie-search supportedParams="q,imdbid,tmdbid"/><tv-search supportedParams="..."/></searching>`（`supportedParams` 逗号分隔，大小写不敏感匹配 `imdbid`/`tmdbid` 两个 token），返回 `IndexerCapability`；解析失败/请求异常均返回 `IndexerCapability.NONE`（与现有 `testConnection` "任何异常视为不连通"的容错哲学一致）。

`TorznabParser` 里做 XXE 防护的 `buildDocument` 逻辑抽成共享的包内工具类（暂定 `pt/indexer/SafeXmlDocuments.java`），供 `TorznabParser` 和新增的 `TorznabCapsParser` 共用——这段是安全相关的固定写法（禁 DTD、禁外部实体），两处各写一份容易日后改一处漏改一处，属于"抽公共方法"里少数值得做的例外。

新增 `IndexerCapabilityCache`（`@Component`，`ConcurrentHashMap<Integer, IndexerCapability>`）：`get(indexer)` 用 `computeIfAbsent` 探测并缓存，**进程生命周期内每个索引器只探测一次**（不落库、不加字段/迁移、不设 TTL 定期刷新——索引器的能力配置不会频繁变，YAGNI；如果索引器后来升级支持了 ID 搜索，重启应用即可重新探测）。

### 2.3 Torznab 请求构造

`TorznabClient` 新增：

```java
public List<TorrentInfo> searchByExternalId(PtIndexerPlus indexer, boolean movie,
                                             String idParamName, String idValue,
                                             Integer season, Integer episode) throws IOException
```

- 电影：`t=movie&{idParamName}={idValue}`
- 剧集：`t=tvsearch&{idParamName}={idValue}&season={season}`，若 `episode` 非季包哨兵值再加 `&ep={episode}`
- 复用现有 `buildUrl`（沿用 `cat` 分类参数逻辑）与 `execute`/`TorznabParser.parse`（响应体结构与 `t=search` 一致，仍是标准 Torznab RSS）

`idParamName` 由调用方传 `"imdbid"` 或 `"tmdbid"` 字面量，避免在 `TorznabClient` 里重复"先试 IMDb 再试 TMDB"的选择逻辑——那部分决策留在 `SearchSupplementService` 里做，`TorznabClient` 只管拼 URL。

### 2.4 过滤标准不变

ID 命中的候选和标题命中的候选走同一个 `filterByTarget`/`filterMovieCandidates`（[bilingual-keyword-design 已确认的标准](2026-07-22-pt-search-supplement-bilingual-keyword-design.md)：标题归一化 + 年份 + 电影排除季集信息）。不新增"ID 命中就信任、跳过过滤"的分支。

## 3. 数据模型改动

新增迁移脚本（沿用 `INFORMATION_SCHEMA.COLUMNS` 幂等写法），`pt_subscription` 加 `imdb_id varchar(20) NULL DEFAULT NULL COMMENT 'IMDb ID(如 tt0125664)，建订阅时从 TMDb 详情/external_ids 获取，用于索引器 ID 精确搜索'`。

`PtSubscriptionPlus` 加 `imdbId` 字段（`@TableField("imdb_id")`）。

`TmdbSearchItem` DTO 加 `imdbId` 字段。

### 3.1 填充时机：`TmdbSearchService.getDetail()`

- **电影**：TMDb `/movie/{id}` 详情接口本身直接带 `imdb_id` 字段（刮削模块 `NfoXmlBuilder.extractImdbId` 已经这么取过），从已经在调的 `getDetails()` 响应里顺手取出，**不新增网络请求**。
- **剧集**：TMDb `/tv/{id}` 详情接口没有 `imdb_id`，需要多调一次已存在的 `TMDbApiService.getExternalIds(apiKey, "tv", id)`（刮削模块已用于生成 nfo，本次只是在建订阅这个时机也调一次）——每个订阅只在创建时调一次，不影响后续搜索性能。
- TMDb 没返回 `imdb_id`（少数冷门作品）时存 `null`，走"老订阅"的自然降级路径。

`SubscriptionService.subscribe()` 里 `sub.setImdbId(detail.getImdbId())` 一行接入。

## 4. 后端组件改动清单

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `pt/indexer/SafeXmlDocuments.java` | 新建 | 从 `TorznabParser.buildDocument` 抽出的共享 XXE 防护 DOM 构建工具 |
| `pt/indexer/TorznabParser.java` | 改动 | `buildDocument` 改为调用 `SafeXmlDocuments` |
| `pt/indexer/TorznabCapsParser.java` | 新建 | 解析 `t=caps` 响应为 `IndexerCapability` |
| `pt/indexer/IndexerCapability.java` | 新建 | record，4 个 boolean 字段 + `NONE` 常量 |
| `pt/indexer/IndexerCapabilityCache.java` | 新建 | `@Component`，`ConcurrentHashMap` 缓存 + 探测 |
| `pt/indexer/TorznabClient.java` | 改动 | 新增 `getCaps(indexer)`、`searchByExternalId(indexer, movie, idParamName, idValue, season, episode)` |
| `mybatisplus/domain/PtSubscriptionPlus.java` | 改动 | 新增 `imdbId` 字段 |
| `pt/subscription/dto/TmdbSearchItem.java` | 改动 | 新增 `imdbId` 字段 |
| `pt/subscription/TmdbSearchService.java` | 改动 | `getDetail()` 补充 `imdbId` 解析（电影直接取，剧集查 external_ids） |
| `pt/subscription/SubscriptionService.java` | 改动 | `subscribe()` 里 `sub.setImdbId(...)` |
| `pt/subscription/SearchSupplementService.java` | 改动 | `supplement()` 新增第1级 ID 搜索分支（在中文标题搜索之前） |
| `ruoyi-common/src/main/resources/sql/` | 新建迁移脚本 | `pt_subscription.imdb_id` 列 |

不改前端、不改 API 请求/响应结构（`imdbId` 是纯后端内部字段，不需要在订阅列表/详情页展示）。

## 5. 测试计划

- `TorznabCapsParser`：能正确解析 `movie-search`/`tv-search` 的 `supportedParams`（含大小写、含/不含 imdbid/tmdbid、`available="no"` 时视为不支持）、caps 为空/非法 XML 时返回 `NONE`
- `IndexerCapabilityCache`：同一索引器重复 `get()` 只探测一次（验证 `TorznabClient.getCaps` 调用次数）
- `TorznabClient.searchByExternalId`：电影/剧集分别拼出正确的 `t=movie`/`t=tvsearch` URL 及参数
- `SearchSupplementService`：
  - 索引器支持 imdbid 且订阅有 imdbId → 优先用 imdbid 搜索，不再退到 tmdbid/标题
  - 索引器只支持 tmdbid，或订阅 imdbId 为空 → 退到 tmdbid
  - 索引器两者都不支持 → 跳过第1级，直接走中文标题（验证不发 ID 相关请求）
  - 第1级搜到候选但过滤后为空（伪装成对的 ID 但标题/年份对不上）→ 继续走第2级，不提前判定"未命中"
- `TmdbSearchService.getDetail()`：电影从 details 直接取 imdb_id；剧集多调一次 external_ids 取 imdb_id；TMDb 未返回时为 null

## 6. 不做的事情（本次范围之外）

- 不做前端展示/编辑 `imdbId`（纯后端匹配用途）
- 不做 TVDB ID 支持（国内索引器更罕见，且订阅当前不采集 TVDB ID）
- 不做能力探测结果的持久化/定时刷新，只做进程内一次性缓存
- 不做"同时带 imdbid 和 tmdbid 两个参数"的请求方式
- 不改变候选过滤标准本身（沿用已确认的标题归一化 + 年份 + 季集号校验）
