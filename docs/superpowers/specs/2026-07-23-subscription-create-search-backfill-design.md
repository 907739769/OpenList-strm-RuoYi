# 建订阅时补搜历史资源设计

**日期**：2026-07-23
**作者**：Jack（与 Claude 协作）
**前置阅读**：
- `docs/superpowers/specs/2026-07-21-pt-subscription-download-design.md`（订阅建立/RSS 推送链路）
- `docs/superpowers/specs/2026-07-22-pt-search-supplement-design.md`（搜索补集能力的原始设计，本设计直接复用其产物）

## 1. 背景与目标

### 1.1 问题

[`SubscriptionService.subscribe()`](../../../ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionService.java) 建订阅时，唯一"补充历史资源"的动作是查询媒体服务器（Emby）判断哪些集已入库，**完全不会调用 PT 索引器搜索**。新建订阅后，缺失的集只能：

- 等 `RssPollTask` 下一次轮询恰好命中新发布的种子（大多数索引器 RSS 只覆盖近期发布，老剧缺集理论上等不到）；
- 等 `AutoSearchService` 到期（默认 24 小时后）自动补搜一次；
- 用户手动打开"搜索补集"弹窗填关键词触发。

也就是说，用户订阅一部已经存在大量历史资源的老剧时，体验是"订阅完之后什么都不会发生，得手动搜或者等一天"。

### 1.2 目标

建订阅这一步，在事务提交、订阅状态确定为 `ACTIVE`（存在缺失集）之后，**自动发起一次历史资源补搜**，尽量把订阅时刻已经存在于索引器上的历史资源直接下载下来，减少用户手动介入。

**范围限定**：只做"建订阅时机的一次性补搜"，不改变 RSS 轮询、不改变 `AutoSearchService` 的周期性补搜逻辑，不新增数据库字段，不新增前端开关。

### 1.3 成功标准

1. 新建一个存在历史资源的订阅后，无需用户任何额外操作，缺失集会在建订阅后的几秒到几十秒内自动出现下载进度（`IN_FLIGHT`）。
2. 建订阅接口本身的响应时间不受补搜耗时影响（补搜异步执行）。
3. 补搜过程中任意一集/一次索引器请求失败，不影响其他集的补搜，也不影响建订阅接口本身已经返回成功的结果。
4. 全部已入库（`status=COMPLETED`）的订阅不触发补搜。

## 2. 架构

### 2.1 与现有链路的关系

不新建搜索编排逻辑，直接复用 [`SearchSupplementService.supplement(subId, episode, keyword)`](../../../ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java)——这个方法已经封装了"三级回退搜索（ID 精确 → 中文标题 → 英文/原语言标题） → 过滤择优 → 占位 → 推送下载器 → 回写 `lastSearchTime`"的完整链路，`AutoSearchService` 已经在用它做周期性补搜。

新增内容只有两处：

1. `SearchSupplementService` 新增一个**编排方法** `supplementOnCreate(Integer subId)`：对一个刚建好的订阅，决定"搜哪些目标、按什么顺序搜"，内部循环调用 `supplement()`。这个方法保持同步、无外部调度依赖，可以像现有 `supplement()`/`searchAcrossIndexers()` 一样直接单元测试。
2. 新建一个**瘦调度类** `SubscriptionSearchOnCreateTrigger`（`pt/subscription/` 包），只负责"把 `supplementOnCreate` 丢到虚拟线程异步执行"，不含任何业务判断逻辑。

拆成两个类的原因：`SearchSupplementService` 现在是纯构造器注入、完全用 mock 单测的类（见 `SearchSupplementServiceTest`）；如果把 `TaskScheduler scheduler = SpringUtils.getBean(...)` 这种字段初始化直接加进这个类，现有测试里 `new SearchSupplementService(mock1, mock2, ...)` 的构造方式会在没有 Spring 容器时因 `SpringUtils.getBean` 取不到 bean 而失败。把"调度"单独放一个瘦类，正是本项目 `pt/task/AutoSearchTask`、`pt/task/RssPollTask` 等类的既有写法——业务逻辑在可单测的 `*Service` 里，调度壳子不单测。

### 2.2 数据流

```
POST /api/openliststrm/pt-subscriptions/subscribe
        │
        ▼
Controller.subscribe(request)
        │
        ├─▶ subscriptionBiz.subscribe(request)          // 不变，事务内落库，返回 PtSubscriptionPlus
        │
        ├─▶ 若 sub.status == ACTIVE：
        │       searchOnCreateTrigger.triggerAsync(sub.getId())
        │       （虚拟线程调度，立即执行；Controller 不等待其完成）
        │
        ▼
Controller 照常返回 Result.success()（响应时间不受补搜影响）


SubscriptionSearchOnCreateTrigger.triggerAsync(subId)   [异步线程内]
        │
        ├─▶ 顶层 try/catch：任何异常只记 warn 日志，不外抛（调度器线程池不该吃到未捕获异常）
        │
        ▼
searchSupplementService.supplementOnCreate(subId)        [同步，可单测]
        │
        ├─▶ 取订阅；不存在 / 非 ACTIVE / 无 MISSING 集 → 直接返回，不发任何请求
        │
        ├─▶ 电影订阅：
        │       supplement(subId, episode=0, sub.getTitle())
        │       结束
        │
        └─▶ 剧集订阅：
              1. supplement(subId, SEASON_PACK, title + " S" + pad(season))   // 先试整季包
              2. 重新查询该订阅当前的每集状态（季包命中会把命中范围内的 MISSING 集
                 原子占位为 IN_FLIGHT，见 SubscriptionEngine.resolveTargets）
              3. 对仍处于 MISSING 的每一集，逐个：
                     try { supplement(subId, episode, title+" S"+pad(season)+"E"+pad(episode)) }
                     catch (Exception e) { log.warn(...); 继续下一集 }
```

`SEASON_PACK`（`SubscriptionMatcher.SEASON_PACK = -1`）与电影的哨兵集号 `0` 直接复用现有常量，语义与 `AutoSearchService.trySearch()` 完全一致。

### 2.3 关键设计取舍

- **先整季包再逐集兜底，而非只搜整季或对每集都发请求**：新订阅可能一次缺很多集（全新剧集可能 20+ 集全缺）。只搜整季包在索引器没有季包资源时永远补不到历史单集；对每集都发请求在缺集很多时请求量太大。"先季包、季包搜不到的集再逐个补"在覆盖率和请求量之间是当前最合理的折中，且与 `SearchSupplementService.supplement()` 内部"任一级过滤后有匹配就停止"的回退思路保持风格一致。
- **无条件默认开启，不加开关**：`subscribe()` 落库时已经通过 `coversAll()` 判断了是否需要搜索（全部入库直接是 `COMPLETED`，不会触发）；对于确实存在缺集的场景，补搜历史资源符合用户建订阅的直接预期，没有必要让用户每次手动勾选。后续如果出现"不想自动补搜"的真实需求，再加开关（YAGNI）。
- **异步执行，不阻塞建订阅接口**：整季包 + 多集逐个补搜可能涉及数十次索引器网络请求（`SearchSupplementService` 内部按 `maxConcurrency`=3 限流排队），耗时可能到几十秒。建订阅是用户在表单里点一下就应该立刻有反馈的操作，不应该被搜索耗时拖慢。代价是用户看不到"这次订阅到底搜没搜到"的即时反馈，需要刷新列表/进度弹窗才能看到——这个代价可接受，因为进度弹窗、"上次搜索时间"、`pt_search_log` 排查日志等现有 UI 已经能覆盖"事后查看结果"的需要，不需要为这一次性动作新增专门的前端状态。
- **不改动 `SubscriptionService.subscribe()` 本身**：`subscribe()` 保持职责单一（建订阅+查 Emby 初始化状态），触发补搜的判断放在 Controller 和新的调度类里，避免这个已经有完整测试覆盖的核心方法承担新职责。
- **不改动 `AutoSearchService`**：建订阅补搜与周期性补搜是两个独立触发点，共用同一个 `supplement()` 原子能力即可，没有必要合并成一个方法（两者的目标粒度选择逻辑本来就不同：周期性补搜为了限流只做整季/整部粒度，建订阅补搜要覆盖到逐集）。

## 3. 数据模型改动

无。不新增字段、不新增表、不新增迁移脚本。补搜产生的下载记录与 RSS/手动补搜完全共用 `pt_download_record`；`pt_subscription.last_search_time` 会被 `supplement()` 现有逻辑自然更新，语义不变（"上次发起搜索补集的时间"，覆盖了建订阅这次）。

## 4. 后端组件改动清单

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `pt/subscription/SearchSupplementService.java` | 改动 | 新增 `public void supplementOnCreate(Integer subId)`：判断订阅是否存在/ACTIVE/有缺集；电影单次调用 `supplement`；剧集先季包后逐集兜底，逐集用 try/catch 包裹单独失败 |
| `pt/subscription/SubscriptionSearchOnCreateTrigger.java` | 新建 | `@Component`，持有 `TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor")`（字段初始化，风格同 `AutoSearchTask`）；唯一方法 `public void triggerAsync(Integer subId)`，`scheduler.schedule(...)` 立即调度一次，内部 try/catch 兜底 `searchSupplementService.supplementOnCreate(subId)` 的异常 |
| `controller/api/PtSubscriptionRestController.java` | 改动 | `subscribe()` 方法：接收 `subscriptionBiz.subscribe(request)` 的返回值（当前丢弃），若 `status == SubscriptionService.STATUS_ACTIVE` 则调用 `searchOnCreateTrigger.triggerAsync(sub.getId())`；其余错误处理分支不变 |

不改动：`SubscriptionService`、`SubscriptionEngine`、`AutoSearchService`、`SubscriptionMatcher`、前端任何文件、数据库脚本。

## 5. API

无新增/变更接口。`POST /api/openliststrm/pt-subscriptions/subscribe` 的请求/响应结构不变，只是响应返回之前（严格说是返回之后，异步）会额外触发一次后台搜索。

## 6. 前端改动

无。不新增开关、不新增提示文案。用户能感知到效果的地方是：建订阅后刷新列表/打开进度弹窗，缺失集可能已经变成"在途"或"已入库"，与手动点"搜索补集"后的效果一致，复用现成 UI。

## 7. 测试计划

对齐现有 `pt/subscription` 包的测试覆盖风格（`SearchSupplementServiceTest` 用 mock 构造 `SearchSupplementService`，注意 AGENTS.md 提到的 `*Plus` 实体浅层 `equals` 陷阱，同一用例里区分不同 `PtSubscriptionEpisodePlus`/`PtSubscriptionPlus` 实例要用 `same()`/`eq()` 显式匹配）：

- `supplementOnCreate`：
  - 订阅不存在 / 非 ACTIVE / 无 MISSING 集 → 不调用 `supplement`，直接返回
  - 电影订阅 → `supplement` 恰好被调用 1 次，参数为 `(subId, 0, title)`
  - 剧集订阅，季包搜索命中（假设 `supplement` mock 返回 pushed=true 且占位了全部缺集）→ 只调用 1 次 `supplement`（季包），不逐集兜底
  - 剧集订阅，季包搜索未命中，仍有 N 个 MISSING 集 → 调用 `supplement` 共 N+1 次（季包 1 次 + 每集 1 次），逐集关键词格式为 `title S{season:02d}E{episode:02d}`
  - 逐集补搜时某一集抛异常 → 不影响其余集继续调用 `supplement`（用 mock 让某次调用抛异常，断言后续调用仍发生）
- `SubscriptionSearchOnCreateTrigger`：不单测（与 `AutoSearchTask`/`RssPollTask` 一致，纯调度壳子，`SpringUtils.getBean` 依赖 Spring 容器无法脱离容器单测）
- `PtSubscriptionRestController` 层（如现有测试基础设施支持 mock 依赖）：`subscribe()` 成功且返回 `ACTIVE` 时触发一次 `triggerAsync`；返回 `COMPLETED` 时不触发

## 8. 不做的事情（本次范围之外）

- 不做前端开关（"创建时是否补搜历史资源"）
- 不做补搜进度的前端实时反馈（如 WebSocket 推送"补搜中/已完成"）
- 不改变 `AutoSearchService` 的周期性补搜粒度策略
- 不做补搜失败的重试/退避机制（单次尝试，失败就等下一次周期性补搜或用户手动搜）
- 不新增数据库字段记录"是否已做过建订阅补搜"（`lastSearchTime` 已经隐含覆盖这个语义）
