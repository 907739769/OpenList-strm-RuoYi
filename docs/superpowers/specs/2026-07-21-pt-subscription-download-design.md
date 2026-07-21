# PT 站点订阅自动下载 — 设计规格

日期：2026-07-21
状态：已确认，待编写实现计划

## 1. 背景与目标

现有系统已具备「网盘文件 → 重命名 → 刮削 → STRM 生成」的完整链路，媒体入库依赖用户手动往网盘投放资源。本功能补齐链路**前端**：从 PT 站点自动发现资源并下载，使系统形成「订阅 → 下载 → 上传网盘 → 整理 → Emby 播放」的闭环。

对标产品为 MoviePilot，但本设计明确不复刻其全部能力，只取与本项目「云端媒体库 + STRM」定位契合的部分。

### 1.1 第一版范围

**做：** 剧集与电影订阅、Torznab 索引器 RSS 轮询、种子过滤与择优、qBittorrent 推送、下载状态追踪、Emby/Jellyfin 入库对账、集数追踪与订阅自动完成。

**不做（数据模型已预留扩展点）：** 洗版、站内搜索补集、站点原生 RSS、Transmission、下载完成后的种子清理、站点签到与流量统计、优先级规则组、订阅自动过期。

### 1.2 成功标准

1. 用户订阅一部连载剧后，新集发布可在一个轮询周期内被自动下载并最终出现在 Emby 中，全程无人工介入。
2. 订阅页面能准确显示「已入库 N/M 集，缺第 X、Y 集」，且该数字与 Emby 实际内容一致（含用户此前手动放入的存量剧集）。
3. 同一集不会被重复下载，无论是因为 RSS 重复推送、多个种子命中，还是链路处理延迟。
4. 任一外部依赖（Torznab / qBittorrent / Emby）不可用时，系统不产生错误数据，恢复后自动继续。

## 2. 架构

### 2.1 与现有链路的交接

现有 `monitor/WatchServiceMonitor` + `MediaUploadProcessor` 已实现「监听本地目录 → 文件稳定后上传网盘 → 触发 rename/scrape/strm」，且 `MediaUploadProcessor` 已内置对 `.!qB` 临时文件的忽略逻辑。

**本功能的职责边界终止于「把种子推送给 qBittorrent」**，其后的处理完全由现有链路承接：

```
订阅引擎 → 推种子给 qBittorrent（保存路径落在已被 FileMonitor 监听的目录）
                              ↓
                  qB 下载完成，.!qB 后缀消失，文件稳定
                              ↓
        【现有】MediaUploadProcessor → copyService.syncOneFile → 网盘
                              ↓
        【现有】rename → scrape → STRM 生成
                              ↓
                       Emby/Jellyfin 扫库识别
                              ↓
            【本功能】LibrarySyncTask 轮询 Emby，确认入库，集状态推进
```

**因此本功能不修改 `monitor/`、`upload/`、`rename/`、`scrape/` 任何代码。**

两项已确认的约束：

- `copyService.syncOneFile` 是复制而非移动，种子文件保留在本地继续做种。第一版不做种子清理与磁盘回收，由用户在 qBittorrent 侧配置做种比例限制自行管理。
- **配置前置依赖**：下载器的 `save_path` 必须位于某个已启用的文件同步任务（`OpenlistCopyTaskPlus`）所监听的目录之下。前端在保存下载器配置时校验此项并给出明确提示。

### 2.2 模块划分

新增 `com.ruoyi.openliststrm.pt` 包，内部按职责分 5 个子包，每个子包对外只暴露一个接口：

```
pt/
├── indexer/      TorznabClient          → List<TorrentInfo>    拉取并解析 Torznab 响应
├── filter/       TorrentFilterEngine    → TorrentInfo | null   过滤 + 排序择优
├── downloader/   IDownloaderClient      → 推种 / 查进度 / 打标签（QbittorrentClient 实现）
├── media/        IMediaServerClient     → 查询媒体库已有内容（EmbyClient，兼容 Jellyfin）
└── subscription/ SubscriptionEngine     → 编排上述四者
```

**`TorrentInfo` 是贯穿全流程的统一模型**，字段包含：原始标题、torrent hash、下载链接、体积、做种数、`downloadVolumeFactor`（免费判定依据）、发布时间、来源 indexer，以及解析产出的媒体信息（剧名/年份/季/集/分辨率/媒介）。

上游 indexer 产出它，下游 filter 与 subscription 消费它。这是后续接入「站内搜索」与「站点原生 RSS」的插入点——新数据源只要能产出 `TorrentInfo`，下游无需改动。

**复用现有能力，不重复造轮子：**

- 标题解析复用 `rename/MediaParser`
- TMDb 查询复用 `tmdb/TMDbApiService` + `TmdbCacheService`
- 通知复用 `helper/TgHelper`
- HTTP 客户端复用 `config/HttpClientConfig`
- 凭据（下载器密码、各类 API Key）沿用现有约定**明文存储**：代码库中不存在加密工具类，现有的 `openlist.server.token`、`openlist.tg.token`、`openlist.tmdb.apikey` 均明文存于 `sys_config`。不为本功能单独引入加密体系（YAGNI）

### 2.3 调度器

三个独立调度器，均沿用 `UploadTaskManager` 的既有写法（`virtualScheduledExecutor` bean + `@EventListener(ApplicationReadyEvent.class)` 启动 + `@PreDestroy` 停止）：

| 调度器 | 周期 | 职责 |
|---|---|---|
| `RssPollTask` | 每 indexer 可配，默认 10 分钟 | 拉取 Torznab → 匹配订阅 → 过滤择优 → 推送 qB |
| `DownloadTrackTask` | 30 秒 | 轮询 qB 按 tag 过滤的种子列表，更新下载记录状态 |
| `LibrarySyncTask` | 10 分钟 | 查询 Emby，将「在途」推进为「已入库」，集齐则订阅置完成 |

下载完成感知采用**轮询**而非 qBittorrent 的「完成后执行外部命令」，因为双容器部署下 qB 容器无法回调后端。`IDownloaderClient` 保留一个事件触发入口，未来同机部署时可切换为回调，不影响主体逻辑。

## 3. 数据模型

7 张新表，全部遵循项目现有 MyBatis-Plus 规范：实体置于 `mybatisplus/domain`，命名 `PtXxxPlus`，使用 `@TableName` + `BaseMapper` + `IService`。

### `pt_indexer` — Torznab 索引器

`id` / `name` / `url` / `api_key` / `categories`（逗号分隔的 Torznab 分类）/ `poll_interval`（秒）/ `enabled` / `last_poll_time` / `last_status` / `fail_count`

### `pt_downloader` — 下载器配置

`id` / `name` / `type`（枚举，第一版仅 `QBITTORRENT`）/ `host` / `port` / `use_https` / `username` / `password`（明文，见 §2.2）/ `save_path` / `tag`（默认 `osr-pt`）/ `enabled`

### `pt_media_server` — Emby / Jellyfin

`id` / `type`（`EMBY` / `JELLYFIN`）/ `url` / `api_key` / `user_id` / `enabled`

### `pt_subscription` — 订阅主体

`id` / `tmdb_id` / `media_type`（`TV` / `MOVIE`）/ `title` / `year` / `season`（电影恒为 0 的哨兵值，见附录 F）/ `total_episodes`（电影恒为 1）/ `status`（`ACTIVE` / `COMPLETED` / `PAUSED`）/ `filter_override`（JSON，订阅级过滤覆盖）/ `downloader_id` / `last_match_time` / 标准审计字段

### `pt_subscription_episode` — 每集状态（核心表）

`id` / `sub_id` / `episode`（电影恒为 0）/ `state`（`MISSING` / `IN_FLIGHT` / `IN_LIBRARY`）/ `quality`（已下载质量，为洗版预留）/ `download_id`

唯一约束：`(sub_id, episode)`

**这张表是「缺集」的唯一真相来源。** Emby 查询结果与下载状态均向它收敛，前端进度展示直接查询它。后续的站内搜索补集、洗版功能亦读写此表，无需变更模型。

### `pt_download_record` — 下载记录

`id` / `sub_id` / `episode` / `guid`（索引器给出的条目唯一标识）/ `torrent_hash`（可空，从下载器回填，仅供排查）/ `tracking_tag`（推送时打的唯一标签）/ `title`（原始种子标题）/ `size` / `seeders` / `indexer_id` / `downloader_id` / `state`（`PUSHED` / `DOWNLOADING` / `COMPLETED` / `FAILED`）/ `pushed_time` / `completed_time` / `fail_reason`

唯一约束：`(indexer_id, guid_hash)`——`guid` 是 URL 过长无法直接建索引，故另存一列它的 SHA-256 十六进制专供唯一索引，`guid` 原文保留供排查，两列必须同时赋值

**为何不用 `torrent_hash` 做唯一约束（实测结论，2026-07-21）**：真实环境（Prowlarr + Rousi.pro）返回的 Torznab 条目**不含 `infohash` 属性**，只有 `<guid>` / `<link>` / `<category>`。若以 `torrent_hash` 为唯一键，该列会大面积为 NULL，而 MySQL 唯一索引允许多个 NULL，去重形同虚设。`<link>` 也不能用——Prowlarr 的下载链接内嵌 apikey，重新生成 key 后同一种子的 link 就变了。`guid` 是 RSS 规范定义的条目唯一标识，索引器必定提供、不含凭据、不随凭据变化；加 `indexer_id` 组成复合键是因为 guid 的唯一性只在单个 feed 内有保证。

### `pt_filter_config` — 全局过滤规则（单行表）

仿现有 `RenameTemplateConfig` 的单行配置表模式。

**过滤项：** `min_seeders` / `min_size` / `max_size` / `free_only` / `include_keywords` / `exclude_keywords`

**排序项：** `resolution_priority`（有序列表，如 `2160p,1080p,720p`）/ `sort_priority`（有序维度列表，默认 `resolution,free,seeders,size`）/ `preferred_size`（体积接近度的目标值）

`sort_priority` 决定多候选择优时各维度的比较顺序。例如调整为 `free,seeders,resolution,size` 即表示「宁可要免费的 1080p，也不要收费的 4K」。

**实现方式**：`TorrentFilterEngine` 内部将每个排序维度实现为独立的 `Comparator<TorrentInfo>`，运行时按 `sort_priority` 配置顺序以 `thenComparing` 串联。新增排序维度只需新增一个 Comparator 与一个枚举值，排序逻辑本身不变。

订阅可通过 `pt_subscription.filter_override`（JSON）覆盖上述任意项，包括 `sort_priority`。典型场景为「这部剧要 4K，其余保持默认」。

### 3.1 数据模型取舍说明

- **被过滤掉的种子不落库**，仅记录日志（含过滤原因），避免表体积失控。
- **双重去重**：`(indexer_id, guid)` 唯一约束防同一种子重复推送；`(sub_id, episode)` 唯一约束防同一集下载多个不同种子。
- **下载记录与下载器中种子的映射靠唯一标签，而非 hash**：推送时给种子打两个标签——公共标签（`pt_downloader.tag`，用于批量拉取）和唯一标签 `osr-pt-{guid_hash 前16位}`（**插入前生成，不依赖自增 id**，见附录 C）。轮询时按公共标签拉全量，再用唯一标签精确回映到下载记录。这样绕开了「推送时拿不到 hash」的问题（qBittorrent 的 `/torrents/add` 不返回 hash），无需下载 .torrent 文件解析 bencode 计算 SHA1。
- **订阅级覆盖用 JSON 而非独立列**，避免为少数可选字段扩展十余列。
- **电影统一为「只有 1 集的剧」**（`episode = 0`，`total_episodes = 1`），使 RSS 匹配、过滤择优、下载追踪、入库确认四段核心逻辑完全复用，分支仅存在于标题解析与 Emby 查询两处。

## 4. 关键流程

### 4.1 新建订阅

1. 用户通过 TMDb 搜索选定影视作品（复用现有 `TMDbApiService`）
2. 剧集：拉取指定季的总集数；电影：`total_episodes` 置 1
3. 立即执行一次 Emby 查询，初始化 `pt_subscription_episode` 每行的 `state`
4. 落库，订阅状态置 `ACTIVE`

因步骤 3，订阅创建完成后前端即可显示准确的「已入库 5/12」，无需等待首次轮询。

### 4.2 RSS 轮询（`RssPollTask`）

```
1. GET {url}?apikey={key}&t=search&cat={categories}  → 解析 Torznab XML
2. 逐条构造 TorrentInfo，用 MediaParser 解析标题
     剧集 → 剧名 / 季 / 集 / 分辨率
     电影 → 片名 / 年份 / 分辨率
3. 匹配 ACTIVE 订阅：
     优先按 TMDb id 匹配
     退化匹配：剧集用「剧名 + 季」，电影用「片名 + 年份」（年份必须一致，防同名串台）
4. 将本轮命中的候选按 (订阅, 集) 分组，逐组查 pt_subscription_episode：
     state = MISSING          → 该组继续
     state = IN_FLIGHT / IN_LIBRARY → 整组丢弃
5. 每组内的多个候选交由 TorrentFilterEngine：
     过滤：做种数 < min_seeders、体积越界、free_only 不满足、命中 exclude_keywords、未命中 include_keywords
     排序：按 sort_priority 配置的维度顺序比较，默认为
           分辨率匹配度 → 是否免费 → 做种数 → 体积接近偏好值
     取 Top 1，无候选则本轮跳过
6. 先写 pt_download_record（state = PUSHED），再推送 qBittorrent
     save_path = downloader.save_path，tag = downloader.tag
7. 该集 state → IN_FLIGHT，订阅 last_match_time 更新
8. 通过 TgHelper 发送通知
```

**先写库再推送**：若推送失败则删除该记录并回滚集状态。宁可漏下一轮（10 分钟后重来），不可重复下载。

### 4.3 下载追踪（`DownloadTrackTask`）

轮询 qBittorrent `torrents/info?tag={tag}`：

- 种子状态为完成 → 下载记录置 `COMPLETED`，此后本功能不再干预（由现有 `FileMonitor` 接管）
- 种子在 qB 中消失（用户手动删除）→ 记录置 `FAILED`，对应集 state 回退 `MISSING`，下一轮 RSS 可重新捡起
- 其余状态 → 记录置 `DOWNLOADING`

### 4.4 入库确认（`LibrarySyncTask`）

1. 对每个 `ACTIVE` 订阅，查询 Emby/Jellyfin：
   - 剧集：按 TMDb id 定位剧集，列出指定季已有的集号集合
   - 电影：按 TMDb id 判断该电影是否存在
2. 与 `pt_subscription_episode` 对账：Emby 中存在的集一律置 `IN_LIBRARY`

   此步骤同时使**用户此前手动放入网盘的存量剧集**被自动识别，无需下载。
3. 全部集为 `IN_LIBRARY` → 订阅 `status = COMPLETED`，不再参与 RSS 匹配

## 5. 错误处理

| 场景 | 处理 |
|---|---|
| Torznab 超时 / 返回错误 | 本轮跳过，记录 `last_status` 与 `fail_count`；连续失败 3 次发送 TG 告警。不做即时重试 |
| qBittorrent 连接失败 | 本轮所有推送跳过，集状态保持 `MISSING`，下一轮自然重试 |
| Emby / Jellyfin 连接失败 | 跳过本轮对账，不影响下载流程；前端集数显示沿用上次结果 |
| 标题无法解析出集数 / 年份 | 丢弃该候选，日志记录原始标题（此类日志是后续调优过滤规则的主要素材） |
| 推种成功但写库失败 | 先写库后推送，推送失败即删除记录并回滚集状态 |
| 磁盘满 / qB 返回错误 | 下载记录置 `FAILED`，集 state 回退 `MISSING`，发送 TG 告警 |

**统一原则：所有失败均为「本轮跳过 + 下轮重来」。** 不引入补偿事务、重试队列或死信机制——轮询架构本身即是重试机制。

## 6. 前端

新增 4 个页面（PC 端 `views/`，移动端 `views-mobile/` 按现有规范同步）：

- **订阅管理**：列表 + 新建（TMDb 搜索选片）+ 进度展示（已入库 N/M，缺集列表）+ 暂停/恢复/删除
- **索引器配置**：Torznab URL / API Key / 分类 / 轮询周期 + 连通性测试按钮
- **下载器配置**：qB 连接信息 + 保存路径（含「路径是否被文件同步任务监听」的校验提示）+ 连通性测试
- **媒体服务器配置**：Emby / Jellyfin 连接信息 + 连通性测试

全局过滤规则并入现有设置页，不单开页面。API 层遵循 `openlist-web/src/api/` 现有封装方式，REST 端点继承 `BaseCrudRestController`。

## 7. 测试策略

**单元测试（重点，无外部依赖）**

- `TorznabParser`：使用固定 XML 样本，覆盖中英文标题、字段缺失、命名空间异常、空结果
- `TorrentFilterEngine`：构造候选列表，验证过滤判定与排序结果的确定性；需覆盖多种 `sort_priority` 配置下同一候选集产出不同 Top 1 的场景
- `MediaParser` 在 PT 场景下的解析：使用真实 PT 种子标题样本集

以上两类是 bug 高发区，必须覆盖。

**集成测试**

`QbittorrentClient` 与 `EmbyClient` 使用 MockWebServer 打桩，验证登录握手、分页、错误码处理。

**不测**

三个调度器的定时触发逻辑。编排逻辑抽为可直接调用的方法，测方法而不测定时器。

## 8. 扩展点清单

| 未来功能 | 已预留的接入方式 |
|---|---|
| 站内搜索补集 | 产出 `TorrentInfo` 的新数据源，读 `pt_subscription_episode` 的 `MISSING` 列表 |
| 站点原生 RSS | 同上，`TorznabClient` 之外新增解析器 |
| Transmission | 实现 `IDownloaderClient`，`pt_downloader.type` 增加枚举值 |
| 洗版 | 读 `pt_subscription_episode.quality` |
| 新增过滤维度（发布组黑名单、编码偏好等） | `pt_filter_config` 加列，`TorrentFilterEngine` 加一个判定 |
| 新增排序维度 | 加一个 `Comparator<TorrentInfo>` + 一个枚举值，`sort_priority` 可引用 |
| 优先级规则组 | 在 `pt_filter_config` 之上叠加一层，不改现有结构 |
| qB 完成回调 | `IDownloaderClient` 的事件触发入口 |

---

## 附录：第二阶段完成后的设计决定（2026-07-21）

整体审查暴露出若干跨阶段缺口，以下决定已落地到代码与数据库，**第三、四阶段必须遵守**。

### A. 季包/多集种子：支持，用 `episode = -1` 表示

PT 上连载剧的存量集几乎全是季包（`S01`、`S01E01-E12`）。若按「标题解析不出集数就丢弃」处理，用户订阅一部已播 8 集的剧时前 8 集永远补不上，这是产品级缺口。

**做法**：`pt_download_record.episode = -1` 表示该记录对应一个整季包。推送时把该订阅当前所有 `MISSING` 的集置为 `IN_FLIGHT` 并把 `download_id` 都指向这条记录；Emby 确认后各自转 `IN_LIBRARY`。

**为什么不需要额外的关联表**：`pt_subscription_episode.download_id` 没有唯一约束，多行指向同一条下载记录本就允许。而下游链路（FileMonitor → 上传 → 重命名 → STRM → Emby）本来就是**逐文件**处理的，季包解压后每个文件各自走完整链路，Emby 最终看到的就是 N 集。所以季包只是「记账」问题，不是流程问题。

### B. 失败重试：择优前剔除已有记录的 guid，失败即放弃该种子

原先的矛盾：§4.3 承诺下载失败后集回退 `MISSING`、「下一轮 RSS 可重新捡起」，但下一轮若再次选中同一个 guid 会撞 `(indexer_id, guid_hash)` 唯一约束。

**做法**：§4.2 的择优**之前**增加一步——剔除「本订阅本集已存在下载记录」的候选 guid。`FAILED` 的 guid 视为该集的永久黑名单，不再重试；该集靠 feed 里出现的**其他**种子恢复。

代价是「一次失败等于放弃该种子」，换来的是不改表、逻辑简单、不会出现无限重试同一个坏种。

### C. `tracking_tag` 在插入前生成，不依赖自增 id

原设计 `osr-pt-{自增id}` 要求「先插入拿 id → 再更新 tag → 再推送」两次写库，中间崩溃会产生「有记录无 tag」的永久失联种子，对应集永久停在 `IN_FLIGHT` 且无从发现。

**做法**：tag 值在插入**前**生成（如 `osr-pt-` + `guid_hash` 前 16 位），一次 insert 完成，推送前 tag 已确定。`pt_download_record` 的 `tracking_tag` 索引已由普通索引升级为 **UNIQUE**（`uk_tracking_tag`），防止重复 tag 导致把一个种子的状态写到另一条记录上。

第四阶段仍需一条兜底清理：`IN_FLIGHT` 且 `pushed_time` 超过 N 小时的记录回退 `MISSING`。

### D. 分辨率白名单：`resolution_priority` 只排序，新增 `resolution_whitelist` 做硬过滤

`resolution_priority` 只影响排序权重，不在列表里的分辨率只是排最后、**不会被淘汰**。用户无法表达「只在 4K 和 1080p 之间选，720p 一律不要」。

**做法**：`pt_filter_config` 新增 `resolution_whitelist` 列（逗号分隔，空表示不限），作为 `TorrentFilterEngine` 的第 7 条硬性过滤规则，大小写不敏感。**解析不出分辨率时，若白名单非空一律淘汰**——无法判定的不放行。

### E. `FREE` 排序维度改为按 `downloadVolumeFactor` 连续比较

原先 `isFree() ? 0 : 1` 的二值判断使 PT 站常见的 50% 促销种（系数 0.5）与全价种（1.0）判同级。改为 `comparingDouble(downloadVolumeFactor)`，语义是「计量系数越小越优」。`freeOnly` 的**硬过滤**仍用 `isFree()`（「只要免费」就是只要 0，不含半价）。

### F. 严禁用 `season == 0` 判断是否为电影

`media_type` 是判断电影的**唯一**依据。电影的 `season` 用 0 做哨兵值（避免 MySQL 唯一索引对多个 NULL 失效），而剧集的特别篇在 TMDb 里也是第 0 季。二者在数据层不冲突（唯一索引含 `media_type`），但代码里任何一处写出 `if (season == 0) { 当成电影 }`，订阅特别篇就会立刻串台。

### G. 第四阶段必须处理的两件事（本阶段无法验证）

1. **多索引器并发轮询**：`pt_indexer` 每个索引器有独立的 `poll_interval`，轮询可能并发。两个线程同时读到某集 `MISSING` 会各推一个种子，破坏「同一集只下一个」。必须二选一：串行轮询所有索引器，或用 `UPDATE pt_subscription_episode SET state='IN_FLIGHT' WHERE id=? AND state='MISSING'` 的条件更新按影响行数原子占位。
2. **`total_episodes` 的刷新**：TMDb 对正在播出的季常给偏小的总集数。若不刷新，连载剧会提前 `COMPLETED` 后永久停更。`LibrarySyncTask` 需重新拉取总集数并补齐 `pt_subscription_episode` 行，同时明确 `COMPLETED` 订阅在总集数增加时是否自动回到 `ACTIVE`。

### H. 联调时优先验证的假设（MockWebServer 验不了）

- 真实索引器是否提供 `seeders` 与 `downloadvolumefactor` 属性。若不提供，默认配置 `min_seeders=1` 会把**全部候选整批淘汰**，且只落 debug 日志——现象是「订阅了但什么都不下」，排查成本极高。建议第四阶段在每轮轮询结束时打一条 INFO 汇总（本轮 N 条、带 seeders 的 M 条、带促销信息的 K 条、各规则各淘汰多少）。
- `EmbyClient.listEpisodes` 的 `season` 参数大小写。若被服务端忽略会返回全部季的集号，导致对账把别的季的集误判为已入库。
