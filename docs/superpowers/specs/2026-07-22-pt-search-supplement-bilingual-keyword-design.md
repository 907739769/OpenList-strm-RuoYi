# PT 订阅搜索补集：中英文双语关键词兜底设计

**日期**：2026-07-22
**作者**：Jack（与 Claude 协作）
**前置阅读**：`docs/superpowers/specs/2026-07-22-pt-search-supplement-design.md`（本设计改动的是该设计里 `SearchSupplementService.supplement()` 的关键词来源，不改变过滤/推送链路）

## 1. 背景与目标

### 1.1 问题

`SearchSupplementService.supplement(subId, episode, keyword)` 的关键词目前只来自订阅的中文 `title`：

- 自动补搜：`AutoSearchService.trySearch()` 拼 `sub.getTitle()`（+季号）
- 手动搜索弹窗：前端默认关键词同样拼 `row.title`（+季/集号），用户可编辑

但 PT/BT 站的种子标题大多数是英文（原语言）标题，尤其是欧美剧/电影。`PtSubscriptionPlus.originalTitle`（TMDb 原语言标题，创建订阅时已经写入库）目前完全没被用来搜索，导致这类订阅经常搜不到任何候选，只能等 RSS 轮询"守株待兔"。

### 1.2 目标

在不改变现有过滤/推送标准（含刚修复的电影标题/年份校验）的前提下，让搜索补集在中文关键词搜不到东西时，自动追加一次用 `originalTitle` 拼出的等价关键词重新搜索，提高欧美内容的命中率。

### 1.3 成功标准

1. 订阅了 `originalTitle` 与 `title`不同的作品（典型如欧美剧/电影）时，中文标题搜不到资源的情况下，无需用户手动改关键词，系统会自动再试一次英文/原语言标题。
2. 中文标题本身就能搜到匹配资源时，不发起多余的英文补搜请求（省索引器请求配额）。
3. `originalTitle` 为空、或归一化后与 `title` 相同（中文原生内容）时，行为与改动前完全一致，不产生任何多余请求。
4. 自动补搜与手动搜索弹窗共用同一套兜底逻辑，不需要分别改前端和定时任务。

## 2. 架构

### 2.1 触发时机：中文优先、无结果才补英文（已与用户确认的方案）

不是"总是双语查两次"，而是**中文关键词过滤后一个匹配都没有时才补一次英文关键词**——理由：
- 双语总是查两次会让索引器请求量翻倍，对有速率限制的私有站不友好；
- 只在中文命中零结果时才补搜，绝大多数场景（中文原生内容、中文标题本身就命中）不受影响。

### 2.2 数据流（在 `supplement()` 内部新增一段，其余流程不变）

```
supplement(subId, episode, keyword)
  │
  ├─ requireSearchable / validateEpisode（不变）
  │
  ├─ candidates = searchAcrossIndexers(keyword)          ← 中文关键词（或用户手动填的关键词），不变
  ├─ fillParsed（不变）
  ├─ matched = filterByTarget(sub, episode, candidates)   ← 不变，含电影标题/年份校验
  │
  ├─ 若 matched 非空 → 直接走 pushBest（跟改动前完全一样，不触发下面的英文补搜）
  │
  └─ 若 matched 为空：
        altKeyword = buildAltKeyword(sub, episode, keyword)
        │   - sub.getOriginalTitle() 为空 → altKeyword = null，直接跳过
        │   - normalize(originalTitle) == normalize(title) → altKeyword = null，跳过
        │     （复用 SubscriptionMatcher.normalize，同一套归一化规则，避免"标题只是大小写/标点不同"
        │      被误判为"值得再查一次"）
        │   - 否则按与原 keyword 相同的拼接格式，把标题换成 originalTitle：
        │       电影 → originalTitle
        │       季包(episode==SEASON_PACK) → originalTitle + " S" + pad(season)
        │       单集 → originalTitle + " S" + pad(season) + "E" + pad(episode)
        │     （季/集号格式从 supplement() 已有的 episode/sub.getSeason() 参数重新拼，
        │      不依赖对入参 keyword 字符串做解析——用户手动改过关键词时也能正确拼出英文版）
        │
        若 altKeyword != null：
          altCandidates = searchAcrossIndexers(altKeyword)
          fillParsed（同上）
          matched = filterByTarget(sub, episode, altCandidates)
          candidates = candidates ++ altCandidates          （仅用于返回的 candidateCount，便于前端/日志判断"到底搜到多少东西"）
  │
  └─ pushBest(sub, episode, matched)（不变）
```

### 2.3 关键设计取舍

- **过滤标准不变**：`filterByTarget`/`filterMovieCandidates`/`TorrentFilterEngine` 完全不动，这次只解决"喂给过滤逻辑的候选从哪来"，不改变"候选是否真的匹配订阅"的判断标准。
- **英文关键词的季/集号后缀由 `supplement()` 自己拼，不解析原 `keyword`**：因为手动搜索弹窗允许用户任意编辑关键词文本（可能已经不是"标题+季集号"这种规整格式），无法可靠地从字符串里"抠出"标题部分再替换。`supplement()` 本身已经拿到了结构化的 `episode` 参数和 `sub.getSeason()`，直接按固定格式重新拼更可靠。
- **不做增量学习/记忆"这个订阅英文标题更好用"**：每次都是"先中文过滤失败 → 再试英文"，不新增字段记录"上次是靠哪个关键词命中的"。YAGNI，如果后续发现英文命中率明显更高、想跳过中文这一步再加。
- **`candidateCount` 语义**：两次搜索都发生时，返回值是两次候选数之和（不去重），用于前端"搜到 N 个候选"的展示；这只是给用户的参考数字，不影响推送决策（决策只看 `matched`/`pushBest` 结果）。

## 3. 数据模型改动

无。`originalTitle` 字段已存在（`PtSubscriptionPlus.originalTitle`），无需新增迁移脚本。

## 4. 后端组件改动清单

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `pt/subscription/SearchSupplementService.java` | 改动 | `supplement()` 内新增"中文过滤为空则补英文重试"分支；新增私有方法 `buildAltKeyword(sub, episode, keyword)` |
| `pt/subscription/SubscriptionMatcher.java` | 无需再改 | `normalizeAll`/`normalize` 已在上一次修复中改为包内可见，本次直接复用 |

不改 `AutoSearchService`、不改前端、不改 API 请求/响应结构——调用方无感知，`SupplementResult` 结构不变。

## 5. 测试计划

在 `SearchSupplementServiceTest` 补充用例（对齐现有 mock 风格，`matcher` 用真实实例）：

- 中文关键词过滤后有匹配 → 不触发英文补搜（验证 `torznabClient.search` 只被调用一次/每索引器一次，而非两次）
- 中文关键词过滤后无匹配、`originalTitle` 非空且与 `title` 不同 → 触发英文补搜，且英文候选经过滤后能被推送
- `originalTitle` 为空 → 不触发补搜
- `originalTitle` 归一化后与 `title` 相同 → 不触发补搜
- 电影订阅英文补搜关键词 = 纯 `originalTitle`（无季集后缀）；剧集季包/单集补搜关键词按 `originalTitle + S{season}(E{episode})` 拼接
- 中英文两次都无匹配 → 最终 `pushed=false`，`candidateCount` 为两次候选数之和

## 6. 不做的事情（本次范围之外）

- 不做"总是双语同时查"的模式（已与用户确认选择兜底方案）
- 不做前端默认关键词文案的调整（沿用中文标题预填，英文兜底完全在后端透明发生）
- 不记录"这个订阅上次是靠中文还是英文命中"之类的统计/学习机制
- 不改变候选过滤标准本身（标题归一化、年份、季集号校验）
