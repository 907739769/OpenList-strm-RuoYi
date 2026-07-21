# PT 订阅 计划4：解析、匹配与推送引擎 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 把「一批 RSS 种子 + 一组订阅」变成「该推送哪些种子给下载器」的完整决策与执行，并落好账。

**架构：** 新增三个组件。`MediaParser.parseLocal` 是纯本地标题解析入口（**不触发 TMDb / AI**，因为 RSS 一轮几十上百条，逐条查 TMDb 会打爆配额）；`SubscriptionMatcher` 把 `TorrentInfo` 匹配到 (订阅, 集)；`SubscriptionEngine` 编排全流程——分组、剔除已下过的 guid、过滤择优、CAS 原子占位、推送下载器、落下载记录。

**技术栈：** Java 25 (Spring Boot 4.0.6) / MyBatis-Plus / JUnit 5 + Mockito

**上游规格：** `docs/superpowers/specs/2026-07-21-pt-subscription-download-design.md`（**必读附录 A–H**）

**执行约定：** 直接在 `dev` 分支提交。

**本计划不包含：** 三个定时调度器与 Telegram 通知——它们属于计划 5。本计划结束时，引擎能被调用一次就完成一轮完整决策与推送，但**没有任何东西会定时调用它**。

---

## 前置：已就绪的成果

| 已有 | 位置 | 本计划怎么用 |
|---|---|---|
| `TorrentInfo` | `pt/model/` | 含 `guid` / `title` / `size` / `seeders` / `downloadVolumeFactor` / `indexerId`，以及待填充的 `parsedTitle` / `parsedYear` / `parsedSeason` / `parsedEpisode` / `parsedResolution` / `parsedSource` |
| `GuidHasher` | `pt/indexer/` | 静态方法把 guid 算成 64 字符小写十六进制 SHA-256 |
| `TorrentFilterEngine` | `pt/filter/` | `filter(List, FilterCriteria)` 与 `pickBest(List, FilterCriteria)`（无候选返回 null） |
| `FilterCriteriaFactory.build(global, overrideJson)` | `pt/filter/` | 静态方法，产出 `FilterCriteria` |
| `IPtFilterConfigPlusService.getConfig()` | `mybatisplus/service/` | 取全局配置，永不返回 null |
| `IDownloaderClient` + `DownloaderClientFactory` | `pt/downloader/` | `addTorrent(config, downloadUrl, savePath, tag)`，**抛 IOException** |
| `IPtSubscriptionPlusService.listActive()` | `mybatisplus/service/` | 取 ACTIVE 订阅 |
| `IPtSubscriptionEpisodePlusService.listBySubscription(subId)` | `mybatisplus/service/` | 取某订阅的全部集行 |
| `IPtDownloadRecordPlusService` | `mybatisplus/service/` | 基础 CRUD |
| `MediaParser` | `rename/` | 已有 `parse()`（**会调 TMDb 与 AI**）与私有的 `extractBase()` |
| `RenameClientProvider` | `rename/` | `tmdb()` / `openAI()` / `available()` |

**继承的硬约束：**

1. 测试命令必须带 `-Dsurefire.failIfNoSpecifiedTests=false`；多测试类用**逗号**分隔。
2. 业务 datetime 用 `java.util.Date`。
3. **判断电影只看 `media_type`，绝不能用 `season == 0`**（剧集特别篇也是第 0 季）。
4. 长网络调用不要放进 `@Transactional` 方法体。

**规格附录里直接决定本计划实现的四条：**

- **附录 A**：季包用 `pt_download_record.episode = -1` 表示，推送时把该订阅所有 `MISSING` 集共同指向这条记录。
- **附录 B**：择优**前**要剔除「本订阅本集已存在下载记录」的候选 guid；`FAILED` 的 guid 不再重试。
- **附录 C**：`tracking_tag` = `osr-pt-` + guid_hash 前 16 位，**插入前生成**，不依赖自增 id。
- **附录 G**：多索引器可能并发轮询，占位必须用 `UPDATE ... WHERE state='MISSING'` 的条件更新按影响行数判断，不能先查后写。

---

## 文件结构

| 文件 | 职责 |
|---|---|
| 修改 `rename/MediaParser.java` | 新增 `parseLocal(String)` 公开入口，只做本地抽取 |
| 新增 `pt/subscription/SubscriptionMatcher.java` | `TorrentInfo` → 匹配到哪个订阅的哪一集 |
| 新增 `pt/subscription/dto/MatchResult.java` | 匹配结果（订阅 + 集号，集号 -1 表示季包） |
| 新增 `pt/subscription/SubscriptionEngine.java` | 编排一轮的完整决策与推送 |
| 新增 `mybatisplus/mapper/PtSubscriptionEpisodePlusMapper.java` 加一个方法 | CAS 条件更新占位 |

---

## 任务 1：MediaParser 的纯本地解析入口

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/MediaParser.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/MediaParserLocalTest.java`

> **为什么需要这个入口：** 现有的 `MediaParser.parse()` 在抽取完本地信息后会调 `tmdbClient.enrich(info)`，识别不出时还会调 OpenAI。这对「重命名一个文件」是合理的，但 RSS 轮询**一轮就有几十上百条种子**，逐条查 TMDb 会打爆配额、把一次轮询拖成几分钟，还会为大量根本不匹配任何订阅的种子白白付费。
>
> 所以本任务开一个只做本地抽取的入口：正则跑一遍分辨率、编码、来源、年份季集、标题，**一次网络调用都不发**。
>
> 私有方法 `extractBase(String)` 已经正好是这件事，本任务只是把它暴露出来并给个语义明确的名字。**不要改动 `extractBase` 的实现，也不要动现有的 `parse()` / `parseWithKnownTmdbId()`** —— 它们服务于重命名链路，改坏了会波及既有功能。

- [ ] **步骤 1：编写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/MediaParserLocalTest.java`：

```java
package com.ruoyi.openliststrm.rename;

import com.ruoyi.openliststrm.rename.model.MediaInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * parseLocal 的行为：只做本地正则抽取，不发任何网络请求。
 * 构造时传 null 的 tmdbClient 与 openAIClient——若实现里意外调了它们，测试会以 NPE 失败。
 */
class MediaParserLocalTest {

    private final MediaParser parser = new MediaParser(null, null);

    @Test
    void parseLocal_标准剧集命名_抽出季集与分辨率() {
        MediaInfo info = parser.parseLocal("Some.Show.S01E05.1080p.WEB-DL.H264-GROUP.mkv");

        assertEquals("1", info.getSeason());
        assertEquals("5", info.getEpisode());
        assertEquals("1080p", info.getResolution());
    }

    @Test
    void parseLocal_不发网络请求_传null客户端也不抛异常() {
        // 若实现里调了 tmdbClient.enrich 或 openAIClient.enrich，这里会 NPE
        assertNotNull(parser.parseLocal("Some.Show.S02E10.2160p.BluRay.mkv"));
    }

    @Test
    void parseLocal_不做TMDb富化_tmdbId为空() {
        MediaInfo info = parser.parseLocal("Some.Show.S01E01.1080p.mkv");

        assertNull(info.getTmdbId());
    }

    @Test
    void parseLocal_电影命名_抽出年份且无季集() {
        MediaInfo info = parser.parseLocal("Fight.Club.1999.1080p.BluRay.x264.mkv");

        assertEquals("1999", info.getYear());
        assertNull(info.getSeason());
        assertNull(info.getEpisode());
    }

    @Test
    void parseLocal_中文剧名_能抽出季集() {
        MediaInfo info = parser.parseLocal("大明王朝1566.S01E12.2160p.WEB-DL.mkv");

        assertEquals("1", info.getSeason());
        assertEquals("12", info.getEpisode());
    }

    @Test
    void parseLocal_无扩展名的种子标题_同样能解析() {
        // RSS 里的 title 是种子名，通常没有文件扩展名
        MediaInfo info = parser.parseLocal("Some.Show.S03E07.1080p.WEB-DL");

        assertEquals("3", info.getSeason());
        assertEquals("7", info.getEpisode());
    }

    @Test
    void parseLocal_季包命名_有季无集() {
        MediaInfo info = parser.parseLocal("Some.Show.S01.1080p.WEB-DL.COMPLETE");

        assertEquals("1", info.getSeason());
        assertNull(info.getEpisode());
    }

    @Test
    void parseLocal_保留原始名称() {
        String raw = "Some.Show.S01E05.1080p.mkv";

        assertEquals(raw, parser.parseLocal(raw).getOriginalName());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=MediaParserLocalTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `parseLocal` 方法找不到

- [ ] **步骤 3：编写实现**

在 `MediaParser.java` 的 `parseWithKnownTmdbId` 方法**之后**、私有的 `extractBase` 方法**之前**插入：

```java
    /**
     * 只做本地正则抽取，<b>不发任何网络请求</b>（不查 TMDb、不调 AI）。
     * <p>
     * 供 PT 订阅的 RSS 轮询使用：一轮轮询有几十上百条种子标题，逐条查 TMDb 会打爆配额、
     * 把一次轮询拖成几分钟，而且其中大部分种子根本不匹配任何订阅，那些调用是纯浪费。
     * 匹配阶段只需要标题/年份/季/集/分辨率这些能从标题正则抽出来的信息。
     * </p>
     *
     * @param name 种子标题或文件名，允许没有扩展名
     */
    public MediaInfo parseLocal(String name) {
        return extractBase(name);
    }
```

**不要改动 `extractBase` 本身，也不要动 `parse()` 与 `parseWithKnownTmdbId()`。**

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=MediaParserLocalTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 8, Failures: 0, Errors: 0

若某个用例失败，说明现有正则对该命名形态的抽取结果与预期不符。**这时不要改 `extractBase`**（会波及重命名链路），而是把实际抽取结果记录到报告里，并把该测试的断言调整为实际行为 + 在测试方法上加注释说明「现有解析器对此形态的行为是 X」。这属于「记录现状」而非「放宽断言」，但必须在报告里显式说明改了哪几条、原因是什么。

- [ ] **步骤 5：跑一次重命名相关的既有测试确认没波及**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=RenameTaskManagerTest,CategoryClassifierTest,CategoryRuleConverterTest,CategoryRuleValidatorTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：全绿

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/MediaParser.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/MediaParserLocalTest.java
git commit -m "feat(pt): MediaParser 新增纯本地解析入口，供 RSS 轮询避免逐条查 TMDb"
```

---

## 任务 2：订阅匹配器

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/dto/MatchResult.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionMatcher.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionMatcherTest.java`

> **职责：** 给一条已经本地解析过的 `TorrentInfo` 和一组活跃订阅，判断它对应哪个订阅的哪一集；匹配不上返回 null。
>
> **匹配规则（按优先级）：**
>
> - **剧集**：解析出的标题与订阅标题匹配（**大小写不敏感 + 去掉分隔符后比较**，因为种子标题是 `Some.Show` 而订阅标题是 `Some Show`），且解析出的季号等于订阅季号。
>   - 解析出集号 → 匹配到该集
>   - **解析出季号但没有集号 → 季包**，返回集号 `-1`（规格附录 A）
> - **电影**：标题匹配 **且年份必须一致**。年份不一致一律不匹配——同名电影翻拍太常见，宁可漏也不能串台。电影的集号恒为 `0`。
>
> **标题比较必须宽松到能容忍分隔符差异，但不能宽松到子串包含**。`Some.Show` 与 `Some Show` 要能匹配；但 `The Office` 不该匹配到 `The Office US`——否则一个订阅会吃掉另一个的种子。做法：两边都归一化（转小写、把 `.`/`_`/`-`/空格 压成单空格、去首尾空白）后做**全等**比较。

- [ ] **步骤 1：编写失败的测试**

创建 `SubscriptionMatcherTest.java`：

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.pt.subscription.dto.MatchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubscriptionMatcherTest {

    private final SubscriptionMatcher matcher = new SubscriptionMatcher();

    private PtSubscriptionPlus tvSub(int id, String title, int season) {
        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setId(id);
        sub.setMediaType("TV");
        sub.setTitle(title);
        sub.setSeason(season);
        return sub;
    }

    private PtSubscriptionPlus movieSub(int id, String title, String year) {
        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setId(id);
        sub.setMediaType("MOVIE");
        sub.setTitle(title);
        sub.setYear(year);
        sub.setSeason(0);
        return sub;
    }

    private TorrentInfo torrent(String parsedTitle, String year, Integer season, Integer episode) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle("raw-title");
        t.setParsedTitle(parsedTitle);
        t.setParsedYear(year);
        t.setParsedSeason(season);
        t.setParsedEpisode(episode);
        return t;
    }

    // ---------- 剧集 ----------

    @Test
    void 剧集_标题与季号都匹配_返回对应集() {
        MatchResult result = matcher.match(torrent("Some Show", null, 1, 5),
                List.of(tvSub(10, "Some Show", 1)));

        assertNotNull(result);
        assertEquals(10, result.getSubscription().getId());
        assertEquals(5, result.getEpisode());
    }

    @Test
    void 剧集_种子标题用点分隔_订阅标题用空格_仍能匹配() {
        MatchResult result = matcher.match(torrent("Some.Show", null, 1, 5),
                List.of(tvSub(10, "Some Show", 1)));

        assertNotNull(result);
        assertEquals(10, result.getSubscription().getId());
    }

    @Test
    void 剧集_大小写不同_仍能匹配() {
        MatchResult result = matcher.match(torrent("SOME show", null, 1, 5),
                List.of(tvSub(10, "Some Show", 1)));

        assertNotNull(result);
    }

    @Test
    void 剧集_标题是订阅标题的前缀_不匹配() {
        // The Office 不该吃掉 The Office US 的种子，反之亦然
        assertNull(matcher.match(torrent("The Office", null, 1, 5),
                List.of(tvSub(10, "The Office US", 1))));
        assertNull(matcher.match(torrent("The Office US", null, 1, 5),
                List.of(tvSub(10, "The Office", 1))));
    }

    @Test
    void 剧集_季号不同_不匹配() {
        assertNull(matcher.match(torrent("Some Show", null, 2, 5),
                List.of(tvSub(10, "Some Show", 1))));
    }

    @Test
    void 剧集_有季号无集号_识别为季包返回负一() {
        MatchResult result = matcher.match(torrent("Some Show", null, 1, null),
                List.of(tvSub(10, "Some Show", 1)));

        assertNotNull(result);
        assertEquals(-1, result.getEpisode());
    }

    @Test
    void 剧集_没有季号_不匹配() {
        assertNull(matcher.match(torrent("Some Show", null, null, 5),
                List.of(tvSub(10, "Some Show", 1))));
    }

    @Test
    void 剧集_中文标题_能匹配() {
        MatchResult result = matcher.match(torrent("大明王朝1566", null, 1, 12),
                List.of(tvSub(10, "大明王朝1566", 1)));

        assertNotNull(result);
        assertEquals(12, result.getEpisode());
    }

    // ---------- 电影 ----------

    @Test
    void 电影_标题与年份都匹配_集号为0() {
        MatchResult result = matcher.match(torrent("Fight Club", "1999", null, null),
                List.of(movieSub(20, "Fight Club", "1999")));

        assertNotNull(result);
        assertEquals(20, result.getSubscription().getId());
        assertEquals(0, result.getEpisode());
    }

    @Test
    void 电影_年份不同_不匹配() {
        // 同名翻拍太常见，年份不符宁可漏也不能串台
        assertNull(matcher.match(torrent("Fight Club", "2020", null, null),
                List.of(movieSub(20, "Fight Club", "1999"))));
    }

    @Test
    void 电影_种子无年份_不匹配() {
        assertNull(matcher.match(torrent("Fight Club", null, null, null),
                List.of(movieSub(20, "Fight Club", "1999"))));
    }

    @Test
    void 电影_订阅无年份_不匹配() {
        assertNull(matcher.match(torrent("Fight Club", "1999", null, null),
                List.of(movieSub(20, "Fight Club", null))));
    }

    @Test
    void 电影_解析出季集信息_不匹配电影订阅() {
        // 带季集的一定是剧集，不该匹配到电影订阅
        assertNull(matcher.match(torrent("Fight Club", "1999", 1, 5),
                List.of(movieSub(20, "Fight Club", "1999"))));
    }

    // ---------- 通用 ----------

    @Test
    void 解析标题为空_不匹配() {
        assertNull(matcher.match(torrent(null, "1999", 1, 5), List.of(tvSub(10, "Some Show", 1))));
        assertNull(matcher.match(torrent("  ", "1999", 1, 5), List.of(tvSub(10, "Some Show", 1))));
    }

    @Test
    void 订阅列表为空_不匹配() {
        assertNull(matcher.match(torrent("Some Show", null, 1, 5), List.of()));
    }

    @Test
    void 多个订阅_只匹配到对的那个() {
        List<PtSubscriptionPlus> subs = List.of(
                tvSub(10, "Other Show", 1),
                tvSub(11, "Some Show", 2),
                tvSub(12, "Some Show", 1));

        MatchResult result = matcher.match(torrent("Some Show", null, 1, 5), subs);

        assertNotNull(result);
        assertEquals(12, result.getSubscription().getId());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SubscriptionMatcherTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `SubscriptionMatcher` / `MatchResult` 找不到符号

- [ ] **步骤 3：编写 DTO**

创建 `pt/subscription/dto/MatchResult.java`：

```java
package com.ruoyi.openliststrm.pt.subscription.dto;

import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 一条种子匹配到的订阅与集号。
 *
 * @author Jack
 */
@Data
@AllArgsConstructor
public class MatchResult {

    /** 匹配到的订阅 */
    private PtSubscriptionPlus subscription;

    /**
     * 集号。电影恒为 0；剧集为具体集号；
     * <b>-1 表示季包</b>（种子含整季，推送时该订阅所有缺失集共同指向同一条下载记录）。
     */
    private int episode;
}
```

- [ ] **步骤 4：编写实现**

创建 `pt/subscription/SubscriptionMatcher.java`：

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.pt.subscription.dto.MatchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 把一条已本地解析的种子匹配到某个订阅的某一集。
 *
 * @author Jack
 */
@Slf4j
@Component
public class SubscriptionMatcher {

    /** 季包的集号哨兵值：种子含整季 */
    public static final int SEASON_PACK = -1;

    /** 电影的集号哨兵值 */
    public static final int MOVIE_EPISODE = 0;

    private static final String TYPE_MOVIE = "MOVIE";

    /**
     * @return 匹配结果；匹配不上返回 null
     */
    public MatchResult match(TorrentInfo torrent, List<PtSubscriptionPlus> subscriptions) {
        String torrentTitle = normalize(torrent.getParsedTitle());
        if (torrentTitle == null) {
            return null;
        }
        for (PtSubscriptionPlus sub : subscriptions) {
            if (!torrentTitle.equals(normalize(sub.getTitle()))) {
                continue;
            }
            MatchResult result = matchEpisode(torrent, sub);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private MatchResult matchEpisode(TorrentInfo torrent, PtSubscriptionPlus sub) {
        // 判断电影只看 media_type：剧集的特别篇在 TMDb 里也是第 0 季，用 season==0 判断会串台
        if (TYPE_MOVIE.equalsIgnoreCase(sub.getMediaType())) {
            // 带季集信息的一定是剧集，不该匹配电影订阅
            if (torrent.getParsedSeason() != null || torrent.getParsedEpisode() != null) {
                return null;
            }
            // 同名翻拍常见，年份不符宁可漏也不能串台
            if (StringUtils.isBlank(torrent.getParsedYear()) || StringUtils.isBlank(sub.getYear())
                    || !torrent.getParsedYear().equals(sub.getYear())) {
                return null;
            }
            return new MatchResult(sub, MOVIE_EPISODE);
        }

        if (torrent.getParsedSeason() == null || sub.getSeason() == null
                || !torrent.getParsedSeason().equals(sub.getSeason())) {
            return null;
        }
        // 有季无集 = 季包
        int episode = torrent.getParsedEpisode() == null ? SEASON_PACK : torrent.getParsedEpisode();
        return new MatchResult(sub, episode);
    }

    /**
     * 标题归一化：转小写、把点/下划线/连字符/连续空白压成单空格、去首尾空白。
     * <p>
     * 归一化后做<b>全等</b>比较而非子串包含——否则「The Office」会吃掉「The Office US」的种子。
     * </p>
     */
    private String normalize(String title) {
        if (StringUtils.isBlank(title)) {
            return null;
        }
        String normalized = title.toLowerCase(Locale.ROOT)
                .replaceAll("[._\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SubscriptionMatcherTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 16, Failures: 0, Errors: 0

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/ ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionMatcherTest.java
git commit -m "feat(pt): 新增订阅匹配器，支持季包识别与电影年份严格匹配"
```

---
## 任务 3：订阅推送引擎

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionEngine.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionEngineTest.java`

> **这是整个功能的编排中枢。** 输入一批 RSS 种子，输出「推了几个种子给下载器」，并把账全部落好。
>
> **完整流程：**
>
> ```
> 1. 取活跃订阅；无则直接返回
> 2. 取全局过滤配置
> 3. 逐条种子：本地解析标题（不查 TMDb）→ 匹配到 (订阅, 集)
> 4. 按 (订阅, 集) 分组
> 5. 每组：
>    a. 确定目标集
>       - 集号 >= 0：就这一集，且必须是 MISSING
>       - 集号 == -1（季包）：该订阅所有 MISSING 的集
>       - 无目标集 → 跳过
>    b. 剔除已存在下载记录的候选（按 indexer_id + guid_hash）
>    c. 合并「全局配置 + 订阅覆盖」得到生效条件
>    d. 过滤 → 择优；无幸存者 → 跳过
>    e. CAS 占位：逐集 UPDATE ... WHERE state='MISSING'，按影响行数判断
>       全部占位失败（被并发轮询抢走）→ 跳过
>    f. 写下载记录（PUSHED）
>    g. 推送下载器
>    h. 回写集行的 download_id；更新订阅 last_match_time
> ```
>
> **五个必须守住的点：**
>
> 1. **CAS 占位不能「先查后写」**（规格附录 G）。多索引器可能并发轮询，两个线程都读到某集 `MISSING` 就会各推一个种子。必须用 `update(实体, UpdateWrapper.eq("id", id).eq("state", "MISSING"))` 的条件更新，MyBatis-Plus 的 `IService.update` 返回 boolean 表示是否有行被影响——**这就是原子占位，不需要自定义 Mapper 方法**。
> 2. **`tracking_tag` 在插入前生成**（附录 C）：`"osr-pt-" + guidHash 前 16 位`，不依赖自增 id。这样一次 insert 就完成，不会出现「有记录但没 tag」的失联种子。
> 3. **网络调用不在事务里**。推送下载器是网络操作，本引擎不加 `@Transactional`；各步写库各自独立，失败时显式回滚（见第 4 点）。
> 4. **推送失败要回滚**：删掉刚写的下载记录、把占位的集改回 `MISSING`。这样下一轮可以重来。注意这与「下载失败即放弃该种子」不同——那是种子已经进了下载器之后的事，由计划 5 的追踪任务处理。
> 5. **季包**（附录 A）：一条记录 `episode = -1`，该订阅所有被成功占位的集的 `download_id` 都指向它。

- [ ] **步骤 1：编写失败的测试**

创建 `SubscriptionEngineTest.java`：

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloadRecordPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloaderPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtFilterConfigPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.downloader.DownloaderClientFactory;
import com.ruoyi.openliststrm.pt.downloader.IDownloaderClient;
import com.ruoyi.openliststrm.pt.filter.TorrentFilterEngine;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.rename.MediaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionEngineTest {

    @Mock private IPtSubscriptionPlusService subscriptionService;
    @Mock private IPtSubscriptionEpisodePlusService episodeService;
    @Mock private IPtDownloadRecordPlusService recordService;
    @Mock private IPtDownloaderPlusService downloaderService;
    @Mock private IPtFilterConfigPlusService filterConfigService;
    @Mock private DownloaderClientFactory downloaderClientFactory;
    @Mock private IDownloaderClient downloaderClient;

    private SubscriptionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SubscriptionEngine(
                subscriptionService, episodeService, recordService, downloaderService,
                filterConfigService, downloaderClientFactory,
                new TorrentFilterEngine(), new SubscriptionMatcher(), new MediaParser(null, null));

        PtFilterConfigPlus config = new PtFilterConfigPlus();
        config.setMinSeeders(0);
        config.setMinSize(0L);
        config.setMaxSize(0L);
        config.setFreeOnly("0");
        config.setResolutionPriority("2160p,1080p,720p");
        config.setSortPriority("RESOLUTION,SEEDERS");
        config.setPreferredSize(0L);
        when(filterConfigService.getConfig()).thenReturn(config);

        PtDownloaderPlus downloader = new PtDownloaderPlus();
        downloader.setId(1);
        downloader.setType("QBITTORRENT");
        downloader.setSavePath("/data/downloads");
        downloader.setTag("osr-pt");
        downloader.setEnabled("1");
        when(downloaderService.list(any(Wrapper.class))).thenReturn(List.of(downloader));
        when(downloaderClientFactory.get(any())).thenReturn(downloaderClient);

        when(recordService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(recordService.save(any())).thenReturn(true);
        // 默认占位成功
        when(episodeService.update(any(), any(Wrapper.class))).thenReturn(true);
    }

    private PtSubscriptionPlus tvSub(int id, String title, int season, int total) {
        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setId(id);
        sub.setMediaType("TV");
        sub.setTitle(title);
        sub.setSeason(season);
        sub.setTotalEpisodes(total);
        sub.setStatus("ACTIVE");
        return sub;
    }

    private PtSubscriptionEpisodePlus episode(int id, int number, String state) {
        PtSubscriptionEpisodePlus ep = new PtSubscriptionEpisodePlus();
        ep.setId(id);
        ep.setEpisode(number);
        ep.setState(state);
        return ep;
    }

    private TorrentInfo torrent(String title, String guid, int seeders, String resolution) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle(title);
        t.setGuid(guid);
        t.setSeeders(seeders);
        t.setSize(5_000_000_000L);
        t.setIndexerId(1);
        t.setDownloadUrl("http://indexer/download?id=" + guid);
        return t;
    }

    // ---------- 基本推送 ----------

    @Test
    void 命中缺失集_推送并落记录() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 3)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "MISSING"), episode(102, 2, "MISSING"), episode(103, 3, "MISSING")));

        int pushed = engine.process(List.of(torrent("Some.Show.S01E02.1080p.WEB-DL", "g1", 10, "1080p")));

        assertEquals(1, pushed);
        verify(downloaderClient).addTorrent(any(), anyString(), anyString(), anyString());

        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).save(captor.capture());
        PtDownloadRecordPlus record = captor.getValue();
        assertEquals(10, record.getSubId());
        assertEquals(2, record.getEpisode());
        assertEquals("g1", record.getGuid());
        assertEquals("PUSHED", record.getState());
        assertEquals(64, record.getGuidHash().length());
        assertTrue(record.getTrackingTag().startsWith("osr-pt-"));
        assertEquals("osr-pt-" + record.getGuidHash().substring(0, 16), record.getTrackingTag());
    }

    @Test
    void 推送时带上下载器的保存路径与标签() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));

        engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 10, "1080p")));

        ArgumentCaptor<String> savePath = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tag = ArgumentCaptor.forClass(String.class);
        verify(downloaderClient).addTorrent(any(), anyString(), savePath.capture(), tag.capture());
        assertEquals("/data/downloads", savePath.getValue());
        // 公共标签 + 唯一标签，用逗号分隔一次打上
        assertTrue(tag.getValue().contains("osr-pt"));
    }

    // ---------- 跳过 ----------

    @Test
    void 无活跃订阅_不做任何事() {
        when(subscriptionService.listActive()).thenReturn(List.of());

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 10, "1080p"))));
        verify(episodeService, never()).listBySubscription(any());
    }

    @Test
    void 匹配不到订阅_跳过() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Other Show", 1, 3)));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E02.1080p", "g1", 10, "1080p"))));
        verify(downloaderClient, never()).addTorrent(any(), anyString(), anyString(), anyString());
    }

    @Test
    void 目标集已在途_跳过不重复推送() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 2)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "MISSING"), episode(102, 2, "IN_FLIGHT")));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E02.1080p", "g1", 10, "1080p"))));
        verify(downloaderClient, never()).addTorrent(any(), anyString(), anyString(), anyString());
    }

    @Test
    void 目标集已入库_跳过() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 2)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "MISSING"), episode(102, 2, "IN_LIBRARY")));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E02.1080p", "g1", 10, "1080p"))));
    }

    @Test
    void 该guid已有下载记录_剔除不重复推送() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 2)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(102, 2, "MISSING")));
        PtDownloadRecordPlus existing = new PtDownloadRecordPlus();
        existing.setGuidHash(com.ruoyi.openliststrm.pt.indexer.GuidHasher.sha256Hex("g1"));
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(existing));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E02.1080p", "g1", 10, "1080p"))));
        verify(downloaderClient, never()).addTorrent(any(), anyString(), anyString(), anyString());
    }

    @Test
    void 全部候选被过滤规则淘汰_跳过() throws Exception {
        PtFilterConfigPlus strict = new PtFilterConfigPlus();
        strict.setMinSeeders(100);
        strict.setMinSize(0L);
        strict.setMaxSize(0L);
        strict.setFreeOnly("0");
        strict.setResolutionPriority("1080p");
        strict.setSortPriority("SEEDERS");
        strict.setPreferredSize(0L);
        when(filterConfigService.getConfig()).thenReturn(strict);
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 3, "1080p"))));
    }

    @Test
    void CAS占位失败_跳过不推送() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));
        // 模拟并发轮询已抢走该集
        when(episodeService.update(any(), any(Wrapper.class))).thenReturn(false);

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 10, "1080p"))));
        verify(downloaderClient, never()).addTorrent(any(), anyString(), anyString(), anyString());
        verify(recordService, never()).save(any());
    }

    // ---------- 择优 ----------

    @Test
    void 同一集多个候选_只推最优的一个() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));

        int pushed = engine.process(List.of(
                torrent("Some.Show.S01E01.720p.WEB-DL", "g-720", 10, "720p"),
                torrent("Some.Show.S01E01.2160p.WEB-DL", "g-4k", 10, "2160p"),
                torrent("Some.Show.S01E01.1080p.WEB-DL", "g-1080", 10, "1080p")));

        assertEquals(1, pushed);
        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).save(captor.capture());
        // 默认排序维度是 RESOLUTION 优先，优先级列表 2160p 在最前
        assertEquals("g-4k", captor.getValue().getGuid());
    }

    // ---------- 季包 ----------

    @Test
    void 季包_一条记录集号为负一_所有缺失集共同指向它() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 4)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "IN_LIBRARY"), episode(102, 2, "MISSING"),
                episode(103, 3, "MISSING"), episode(104, 4, "IN_FLIGHT")));

        int pushed = engine.process(List.of(torrent("Some.Show.S01.1080p.WEB-DL.COMPLETE", "g-pack", 10, "1080p")));

        assertEquals(1, pushed);
        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).save(captor.capture());
        assertEquals(-1, captor.getValue().getEpisode());
        // 只占位 MISSING 的第 2、3 集，已入库和在途的不动
        verify(episodeService, times(2)).update(any(), any(Wrapper.class));
    }

    @Test
    void 季包_无缺失集_跳过() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 2)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "IN_LIBRARY"), episode(102, 2, "IN_LIBRARY")));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01.1080p.COMPLETE", "g-pack", 10, "1080p"))));
    }

    // ---------- 推送失败回滚 ----------

    @Test
    void 推送失败_删记录并把集改回缺失() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));
        PtDownloadRecordPlus saved = new PtDownloadRecordPlus();
        when(recordService.save(any())).thenAnswer(inv -> {
            ((PtDownloadRecordPlus) inv.getArgument(0)).setId(999);
            return true;
        });
        org.mockito.Mockito.doThrow(new IOException("qb down"))
                .when(downloaderClient).addTorrent(any(), anyString(), anyString(), anyString());

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 10, "1080p"))));

        verify(recordService).removeById(999);
        // 回滚：把占位过的集改回 MISSING（第二次 update 调用）
        verify(episodeService, times(2)).update(any(), any(Wrapper.class));
    }

    // ---------- 下载器 ----------

    @Test
    void 无启用的下载器_不推送并返回0() throws Exception {
        when(downloaderService.list(any(Wrapper.class))).thenReturn(List.of());
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));

        assertEquals(0, engine.process(List.of(torrent("Some.Show.S01E01.1080p", "g1", 10, "1080p"))));
        verify(recordService, never()).save(any());
    }

    // ---------- 多订阅 ----------

    @Test
    void 多个订阅各命中一集_各推一个() throws Exception {
        when(subscriptionService.listActive()).thenReturn(List.of(
                tvSub(10, "Show A", 1, 1), tvSub(20, "Show B", 1, 1)));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));
        when(episodeService.listBySubscription(20)).thenReturn(List.of(episode(201, 1, "MISSING")));

        int pushed = engine.process(List.of(
                torrent("Show.A.S01E01.1080p", "gA", 10, "1080p"),
                torrent("Show.B.S01E01.1080p", "gB", 10, "1080p")));

        assertEquals(2, pushed);
        verify(downloaderClient, times(2)).addTorrent(any(), anyString(), anyString(), anyString());
    }

    @Test
    void 空种子列表_返回0() {
        when(subscriptionService.listActive()).thenReturn(List.of(tvSub(10, "Some Show", 1, 1)));

        assertEquals(0, engine.process(List.of()));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SubscriptionEngineTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `SubscriptionEngine` 找不到符号

- [ ] **步骤 3：编写实现**

创建 `pt/subscription/SubscriptionEngine.java`：

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloadRecordPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloaderPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtFilterConfigPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.downloader.DownloaderClientFactory;
import com.ruoyi.openliststrm.pt.filter.FilterCriteria;
import com.ruoyi.openliststrm.pt.filter.FilterCriteriaFactory;
import com.ruoyi.openliststrm.pt.filter.TorrentFilterEngine;
import com.ruoyi.openliststrm.pt.indexer.GuidHasher;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.pt.subscription.dto.MatchResult;
import com.ruoyi.openliststrm.rename.MediaParser;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订阅推送引擎：把一批 RSS 种子变成「推给下载器的决策」并落好账。
 * <p>
 * 不加 {@code @Transactional}——方法体内含推送下载器的网络调用，长事务是反模式。
 * 各步写库各自独立，推送失败时显式回滚（删记录 + 集状态改回 MISSING）。
 * </p>
 *
 * @author Jack
 */
@Slf4j
@Component
public class SubscriptionEngine {

    private static final String STATE_MISSING = "MISSING";
    private static final String STATE_IN_FLIGHT = "IN_FLIGHT";
    private static final String RECORD_PUSHED = "PUSHED";

    /** 唯一标签前缀，用于把下载记录回映到下载器里的种子 */
    private static final String TAG_PREFIX = "osr-pt-";

    private final IPtSubscriptionPlusService subscriptionService;
    private final IPtSubscriptionEpisodePlusService episodeService;
    private final IPtDownloadRecordPlusService recordService;
    private final IPtDownloaderPlusService downloaderService;
    private final IPtFilterConfigPlusService filterConfigService;
    private final DownloaderClientFactory downloaderClientFactory;
    private final TorrentFilterEngine filterEngine;
    private final SubscriptionMatcher matcher;
    private final MediaParser mediaParser;

    public SubscriptionEngine(IPtSubscriptionPlusService subscriptionService,
                              IPtSubscriptionEpisodePlusService episodeService,
                              IPtDownloadRecordPlusService recordService,
                              IPtDownloaderPlusService downloaderService,
                              IPtFilterConfigPlusService filterConfigService,
                              DownloaderClientFactory downloaderClientFactory,
                              TorrentFilterEngine filterEngine,
                              SubscriptionMatcher matcher,
                              MediaParser mediaParser) {
        this.subscriptionService = subscriptionService;
        this.episodeService = episodeService;
        this.recordService = recordService;
        this.downloaderService = downloaderService;
        this.filterConfigService = filterConfigService;
        this.downloaderClientFactory = downloaderClientFactory;
        this.filterEngine = filterEngine;
        this.matcher = matcher;
        this.mediaParser = mediaParser;
    }

    /**
     * 处理一批种子：匹配订阅 → 分组 → 过滤择优 → 占位 → 推送 → 落账。
     *
     * @return 成功推送给下载器的种子数
     */
    public int process(List<TorrentInfo> torrents) {
        List<PtSubscriptionPlus> subscriptions = subscriptionService.listActive();
        if (subscriptions.isEmpty() || torrents.isEmpty()) {
            return 0;
        }
        PtFilterConfigPlus globalConfig = filterConfigService.getConfig();

        // 按 (订阅id, 集号) 分组；集号 -1 表示季包
        Map<String, List<TorrentInfo>> groups = new LinkedHashMap<>();
        Map<String, MatchResult> groupMatch = new LinkedHashMap<>();
        for (TorrentInfo torrent : torrents) {
            fillParsed(torrent);
            MatchResult match = matcher.match(torrent, subscriptions);
            if (match == null) {
                log.debug("种子未匹配到任何订阅：{}", torrent.getTitle());
                continue;
            }
            String key = match.getSubscription().getId() + "#" + match.getEpisode();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(torrent);
            groupMatch.putIfAbsent(key, match);
        }

        int pushed = 0;
        Map<Integer, List<PtSubscriptionEpisodePlus>> episodeCache = new LinkedHashMap<>();
        for (Map.Entry<String, List<TorrentInfo>> entry : groups.entrySet()) {
            MatchResult match = groupMatch.get(entry.getKey());
            if (handleGroup(match, entry.getValue(), globalConfig, episodeCache)) {
                pushed++;
            }
        }
        return pushed;
    }

    /**
     * @return 是否成功推送了一个种子
     */
    private boolean handleGroup(MatchResult match, List<TorrentInfo> candidates,
                                PtFilterConfigPlus globalConfig,
                                Map<Integer, List<PtSubscriptionEpisodePlus>> episodeCache) {
        PtSubscriptionPlus sub = match.getSubscription();
        List<PtSubscriptionEpisodePlus> allEpisodes = episodeCache.computeIfAbsent(
                sub.getId(), episodeService::listBySubscription);

        List<PtSubscriptionEpisodePlus> targets = resolveTargets(match, allEpisodes);
        if (targets.isEmpty()) {
            log.debug("订阅[{}] 集{} 无可占位的缺失集，跳过", sub.getId(), match.getEpisode());
            return false;
        }

        List<TorrentInfo> fresh = excludeAlreadyRecorded(candidates);
        if (fresh.isEmpty()) {
            log.debug("订阅[{}] 集{} 的候选都已有下载记录，跳过", sub.getId(), match.getEpisode());
            return false;
        }

        FilterCriteria criteria = FilterCriteriaFactory.build(globalConfig, sub.getFilterOverride());
        TorrentInfo best = filterEngine.pickBest(filterEngine.filter(fresh, criteria), criteria);
        if (best == null) {
            return false;
        }

        PtDownloaderPlus downloader = resolveDownloader(sub);
        if (downloader == null) {
            log.warn("没有可用的下载器，订阅[{}] 本轮跳过", sub.getId());
            return false;
        }

        // 原子占位：条件更新按影响行数判断，防止并发轮询给同一集推两个种子
        List<PtSubscriptionEpisodePlus> claimed = new ArrayList<>();
        for (PtSubscriptionEpisodePlus target : targets) {
            if (claim(target)) {
                claimed.add(target);
            }
        }
        if (claimed.isEmpty()) {
            log.debug("订阅[{}] 集{} 已被并发轮询占位，跳过", sub.getId(), match.getEpisode());
            return false;
        }

        String guidHash = GuidHasher.sha256Hex(best.getGuid());
        PtDownloadRecordPlus record = buildRecord(sub, match.getEpisode(), best, guidHash, downloader);
        if (!recordService.save(record)) {
            releaseAll(claimed);
            return false;
        }

        try {
            String tags = downloader.getTag() + "," + record.getTrackingTag();
            downloaderClientFactory.get(downloader)
                    .addTorrent(downloader, best.getDownloadUrl(), downloader.getSavePath(), tags);
        } catch (Exception e) {
            log.error("推送种子到下载器失败，已回滚：{}", best.getTitle(), e);
            recordService.removeById(record.getId());
            releaseAll(claimed);
            return false;
        }

        for (PtSubscriptionEpisodePlus ep : claimed) {
            ep.setDownloadId(record.getId());
            ep.setState(STATE_IN_FLIGHT);
        }
        episodeService.updateBatchById(claimed);

        sub.setLastMatchTime(new Date());
        subscriptionService.updateById(sub);

        log.info("订阅[{}] {} 已推送种子：{}（占位 {} 集）",
                sub.getId(), sub.getTitle(), best.getTitle(), claimed.size());
        return true;
    }

    /** 用本地解析结果填充种子的 parsedXxx 字段，不发任何网络请求 */
    private void fillParsed(TorrentInfo torrent) {
        MediaInfo info = mediaParser.parseLocal(torrent.getTitle());
        torrent.setParsedTitle(info.getTitle());
        torrent.setParsedYear(info.getYear());
        torrent.setParsedSeason(toInt(info.getSeason()));
        torrent.setParsedEpisode(toInt(info.getEpisode()));
        torrent.setParsedResolution(info.getResolution());
        torrent.setParsedSource(info.getSource());
    }

    private Integer toInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 确定要占位的集：普通集就它自己，季包则是该订阅所有 MISSING 的集。
     */
    private List<PtSubscriptionEpisodePlus> resolveTargets(MatchResult match,
                                                           List<PtSubscriptionEpisodePlus> allEpisodes) {
        List<PtSubscriptionEpisodePlus> targets = new ArrayList<>();
        if (match.getEpisode() == SubscriptionMatcher.SEASON_PACK) {
            for (PtSubscriptionEpisodePlus ep : allEpisodes) {
                if (STATE_MISSING.equals(ep.getState())) {
                    targets.add(ep);
                }
            }
            return targets;
        }
        for (PtSubscriptionEpisodePlus ep : allEpisodes) {
            if (ep.getEpisode() == match.getEpisode() && STATE_MISSING.equals(ep.getState())) {
                targets.add(ep);
            }
        }
        return targets;
    }

    /**
     * 剔除已有下载记录的候选。按 (indexer_id, guid_hash) 判断——这正是表上的唯一约束，
     * 提前剔除既避免重复下载，也避免插入时撞约束。
     */
    private List<TorrentInfo> excludeAlreadyRecorded(List<TorrentInfo> candidates) {
        List<String> hashes = candidates.stream()
                .map(t -> GuidHasher.sha256Hex(t.getGuid()))
                .toList();
        List<PtDownloadRecordPlus> existing = recordService.list(
                new QueryWrapper<PtDownloadRecordPlus>().in("guid_hash", hashes));
        Set<String> taken = new HashSet<>();
        for (PtDownloadRecordPlus record : existing) {
            taken.add(record.getGuidHash());
        }
        List<TorrentInfo> fresh = new ArrayList<>();
        for (TorrentInfo torrent : candidates) {
            if (!taken.contains(GuidHasher.sha256Hex(torrent.getGuid()))) {
                fresh.add(torrent);
            }
        }
        return fresh;
    }

    /** 条件更新占位：只有仍是 MISSING 才能占位成功 */
    private boolean claim(PtSubscriptionEpisodePlus target) {
        PtSubscriptionEpisodePlus set = new PtSubscriptionEpisodePlus();
        set.setState(STATE_IN_FLIGHT);
        return episodeService.update(set, new UpdateWrapper<PtSubscriptionEpisodePlus>()
                .eq("id", target.getId())
                .eq("state", STATE_MISSING));
    }

    /** 回滚占位 */
    private void releaseAll(List<PtSubscriptionEpisodePlus> claimed) {
        for (PtSubscriptionEpisodePlus ep : claimed) {
            PtSubscriptionEpisodePlus set = new PtSubscriptionEpisodePlus();
            set.setState(STATE_MISSING);
            episodeService.update(set, new UpdateWrapper<PtSubscriptionEpisodePlus>()
                    .eq("id", ep.getId())
                    .eq("state", STATE_IN_FLIGHT));
        }
    }

    private PtDownloadRecordPlus buildRecord(PtSubscriptionPlus sub, int episode, TorrentInfo torrent,
                                             String guidHash, PtDownloaderPlus downloader) {
        PtDownloadRecordPlus record = new PtDownloadRecordPlus();
        record.setSubId(sub.getId());
        record.setEpisode(episode);
        record.setIndexerId(torrent.getIndexerId());
        record.setGuid(torrent.getGuid());
        record.setGuidHash(guidHash);
        // 插入前生成，不依赖自增 id：否则要「插入→回填 tag→推送」两次写库，
        // 中间崩溃会留下没有 tag、永远回映不到的失联种子
        record.setTrackingTag(TAG_PREFIX + guidHash.substring(0, 16));
        record.setTorrentHash(torrent.getInfoHash());
        record.setTitle(torrent.getTitle());
        record.setSize(torrent.getSize());
        record.setSeeders(torrent.getSeeders());
        record.setDownloaderId(downloader.getId());
        record.setState(RECORD_PUSHED);
        record.setPushedTime(new Date());
        return record;
    }

    /** 订阅指定了下载器就用它，否则用唯一启用的那个 */
    private PtDownloaderPlus resolveDownloader(PtSubscriptionPlus sub) {
        List<PtDownloaderPlus> enabled = downloaderService.list(
                new QueryWrapper<PtDownloaderPlus>().eq("enabled", "1"));
        if (enabled.isEmpty()) {
            return null;
        }
        if (sub.getDownloaderId() != null) {
            for (PtDownloaderPlus downloader : enabled) {
                if (sub.getDownloaderId().equals(downloader.getId())) {
                    return downloader;
                }
            }
            log.warn("订阅[{}] 指定的下载器 {} 不可用，改用第一个启用的", sub.getId(), sub.getDownloaderId());
        }
        return enabled.get(0);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SubscriptionEngineTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 15, Failures: 0, Errors: 0

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionEngine.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionEngineTest.java
git commit -m "feat(pt): 新增订阅推送引擎，含季包处理、CAS原子占位与推送失败回滚"
```

---

## 任务 4：全量验证

**文件：** 无代码变更

- [ ] **步骤 1：全量测试**

运行：`mvn -pl ruoyi-openliststrm -am test`

预期：BUILD SUCCESS。计划 3 结束时是 246 个测试，本计划新增 39 个（MediaParserLocal 8 + SubscriptionMatcher 16 + SubscriptionEngine 15），预期 285 左右，无失败。

- [ ] **步骤 2：全量构建**

运行：`mvn clean package -DskipTests`

预期：BUILD SUCCESS

- [ ] **步骤 3：确认既有重命名链路未受影响**

本计划唯一改动的既有文件是 `MediaParser`（只新增方法，未改既有逻辑）。确认相关测试全绿：

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=RenameTaskManagerTest,CategoryClassifierTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：全绿

- [ ] **步骤 4：Commit（如有修正）**

```bash
git add -A
git commit -m "fix(pt): 修正引擎验收中发现的问题"
```

---

## 后续计划

**计划 5：调度与通知** — `RssPollTask`（按每个索引器的 `poll_interval` 拉取，调用本计划的 `SubscriptionEngine.process`）、`DownloadTrackTask`（30 秒轮询下载器，按 `tracking_tag` 回映更新记录状态，完成的置 COMPLETED、消失的置 FAILED 并把集回退 MISSING）、`LibrarySyncTask`（10 分钟调用 `SubscriptionService.refresh` 逐个对账）、Telegram 通知。

三个调度器都沿用 `upload/UploadTaskManager` 的既有写法（`virtualScheduledExecutor` bean + `@EventListener(ApplicationReadyEvent.class)` 启动 + `@PreDestroy` 停止）。

**开工前注意：**

- `SubscriptionService.subscribe` / `refresh` 标了 `@Transactional` 且方法体内含 TMDb 与 Emby 的网络调用。`LibrarySyncTask` 循环调用 `refresh` 会放大这个问题（每个订阅一个事务，各自夹着网络往返）。订阅数量少时可接受，但如果订阅数上去了，应把网络调用挪出事务。
- Telegram 的 `openlist.tg.token` 与 `openlist.tg.userid` 在当前环境**都是空的**，通知会静默不发。做通知功能时要确认 `TgHelper.sendMsg` 在未配置时不抛异常、不刷错误日志。
