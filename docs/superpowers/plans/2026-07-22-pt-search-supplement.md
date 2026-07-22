# PT 订阅搜索补集 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让用户能对已订阅的老剧/电影缺集主动发起关键词搜索并自动补齐，同时支持按订阅开启定时自动补搜。

**架构：** 复用现有 `SubscriptionEngine` 的过滤择优/占位/推送核心逻辑（`handleGroup`），只是把"批量 RSS 匹配所有订阅"换成"已知目标订阅+关键词搜索结果"这一条新入口（`pushBest`）。新增 `TorznabClient.search()`、`SearchSupplementService`、`AutoSearchService`/`AutoSearchTask`，改动一个 REST 端点和前端订阅页面。下载追踪、库对账、rename/scrape 链路不用改。

**技术栈：** Java 25 (Spring Boot 4.0.6, MyBatis-Plus, OkHttp), Vue 3 + Element Plus + TypeScript, JUnit 5 + Mockito + MockWebServer

**前置阅读：** `docs/superpowers/specs/2026-07-22-pt-search-supplement-design.md`（本计划的设计依据，遇到本计划未覆盖的细节以设计文档为准）

---

## 文件结构

| 文件 | 类型 | 职责 |
|---|---|---|
| `ruoyi-common/src/main/resources/sql/20260722-pt-search-supplement.sql` | 新建 | 新增 `auto_search`/`last_search_time`/`auto_search_interval_hours` 三列 |
| `ruoyi-openliststrm/.../mybatisplus/domain/PtSubscriptionPlus.java` | 修改 | 加 `autoSearch`/`lastSearchTime` 字段 |
| `ruoyi-openliststrm/.../mybatisplus/domain/PtFilterConfigPlus.java` | 修改 | 加 `autoSearchIntervalHours` 字段 |
| `ruoyi-openliststrm/.../pt/indexer/TorznabClient.java` | 修改 | 加 `search(indexer, keyword)` 方法 |
| `ruoyi-openliststrm/.../pt/subscription/SubscriptionEngine.java` | 修改 | `handleGroup`/`fillParsed` 改包内可见；加 `pushBest()` |
| `ruoyi-openliststrm/.../pt/subscription/dto/SupplementResult.java` | 新建 | 搜索补集结果 DTO |
| `ruoyi-openliststrm/.../pt/subscription/dto/SearchRequest.java` | 新建 | 搜索补集请求体 DTO |
| `ruoyi-openliststrm/.../pt/subscription/SearchSupplementService.java` | 新建 | 并发搜索索引器 + 编排推送 |
| `ruoyi-openliststrm/.../controller/api/PtSubscriptionRestController.java` | 修改 | 加 `POST /{id}/search` 端点 |
| `ruoyi-openliststrm/.../pt/task/AutoSearchService.java` | 新建 | 自动补搜业务逻辑 |
| `ruoyi-openliststrm/.../pt/task/AutoSearchTask.java` | 新建 | 自动补搜调度器外壳 |
| `openlist-web/src/api/openlist/ptSubscription.ts` | 修改 | 加 `searchSupplementApi` |
| `openlist-web/src/composables/usePtSubscription.ts` | 修改 | 加搜索弹窗状态、自动补搜开关处理 |
| `openlist-web/src/views/openlist/ptSubscription/index.vue` | 修改 | 加列、按钮、搜索确认弹窗 |

对应测试：
- `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java`（修改，加 search 测试）
- `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionEngineTest.java`（修改，加 pushBest 测试）
- `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementServiceTest.java`（新建）
- `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/task/AutoSearchServiceTest.java`（新建）

---

### 任务 1：数据库迁移 + 领域对象字段

**文件：**
- 创建：`ruoyi-common/src/main/resources/sql/20260722-pt-search-supplement.sql`
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/PtSubscriptionPlus.java`
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/PtFilterConfigPlus.java`

- [ ] **步骤 1：写迁移脚本**

```sql
-- ----------------------------
-- 20260722: 搜索补集功能 —— 订阅加自动补搜开关与上次搜索时间，全局配置加自动补搜周期（幂等脚本）
-- 沿用 20260728-pt-subscription-original-title.sql 的 INFORMATION_SCHEMA 判断写法。
-- ----------------------------

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

- [ ] **步骤 2：给 `PtSubscriptionPlus` 加字段**

在 `private Date lastMatchTime;` 字段（文件末尾，`}` 之前）后加：

```java
    /** 是否开启自动定时补搜 0-否 1-是 */
    @TableField("auto_search")
    private String autoSearch;

    /** 上次发起搜索补集的时间，用于自动补搜到期判断与前端展示 */
    @TableField("last_search_time")
    private Date lastSearchTime;
```

- [ ] **步骤 3：给 `PtFilterConfigPlus` 加字段**

在 `private Long preferredSize;` 字段（文件末尾，`}` 之前）后加：

```java

    /** 自动补搜的全局周期(小时) */
    @TableField("auto_search_interval_hours")
    private Integer autoSearchIntervalHours;
```

- [ ] **步骤 4：跑一次现有测试确认没有破坏任何东西**

运行：
```bash
mvn -pl ruoyi-openliststrm -am test -Dtest=PtSubscriptionPlusTest,PtFilterConfigPlusTest -DfailIfNoTests=false
```
预期：这两个类目前没有专属测试文件，命令应正常跳过（`-DfailIfNoTests=false`）且不报编译错误。若报编译错误，说明字段名或类型有误，检查并修正。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-common/src/main/resources/sql/20260722-pt-search-supplement.sql ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/PtSubscriptionPlus.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/PtFilterConfigPlus.java
git commit -m "feat(pt): 搜索补集数据库字段——订阅自动补搜开关、上次搜索时间、全局补搜周期"
```

---

### 任务 2：TorznabClient 新增关键词搜索

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java`

- [ ] **步骤 1：写失败的测试**

在 `TorznabClientTest.java` 的 `testConnection_地址不可达_判定不连通而非抛异常` 测试方法后面加：

```java

    @Test
    void search_请求参数正确带上关键词与分类() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.search(indexer("5000,5030"), "Some Show S02");

        RecordedRequest request = server.takeRequest();
        assertEquals("search", request.getRequestUrl().queryParameter("t"));
        assertEquals("Some Show S02", request.getRequestUrl().queryParameter("q"));
        assertEquals("5000,5030", request.getRequestUrl().queryParameter("cat"));
        assertEquals("secret-key", request.getRequestUrl().queryParameter("apikey"));
    }

    @Test
    void search_正常响应_返回解析结果并带上索引器ID() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        List<TorrentInfo> list = client.search(indexer(null), "Some Show");

        assertEquals(1, list.size());
        assertEquals("Some.Show.S01E05.1080p.WEB-DL", list.get(0).getTitle());
        assertEquals(7, list.get(0).getIndexerId());
    }

    @Test
    void search_HTTP错误码_抛IOException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThrows(IOException.class, () -> client.search(indexer(null), "kw"));
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorznabClientTest`
预期：FAIL，编译错误 "cannot find symbol: method search"（`TorznabClient` 还没有 `search` 方法）

- [ ] **步骤 3：实现 `search` 方法**

在 `TorznabClient.java` 的 `fetch` 方法后面（`testConnection` 方法前面）加：

```java

    /**
     * 按关键词搜索索引器（t=search 且带 q 参数），用于订阅缺集的主动补搜。
     *
     * @throws IOException              网络异常或 HTTP 非 2xx
     * @throws IllegalArgumentException 响应体不是合法 Torznab XML
     */
    public List<TorrentInfo> search(PtIndexerPlus indexer, String keyword) throws IOException {
        HttpUrl url = buildUrl(indexer, "search").newBuilder()
                .addQueryParameter("q", keyword)
                .build();
        String body = execute(url);
        List<TorrentInfo> list = TorznabParser.parse(body);
        for (TorrentInfo info : list) {
            info.setIndexerId(indexer.getId());
        }
        log.debug("索引器[{}]关键词搜索[{}]返回{}条种子", indexer.getName(), keyword, list.size());
        return list;
    }
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorznabClientTest`
预期：PASS，全部测试（含新增 3 个）通过

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java
git commit -m "feat(pt): TorznabClient 支持关键词搜索(t=search&q=)"
```

---

### 任务 3：SubscriptionEngine 新增 pushBest 复用入口

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionEngine.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionEngineTest.java`

- [ ] **步骤 1：写失败的测试**

在 `SubscriptionEngineTest.java` 的 `空种子列表_返回0` 测试方法后面（`}` 收尾类之前）加：

```java

    // ---------- pushBest（搜索补集复用） ----------

    @Test
    void pushBest_指定单集_只占位该集() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, "Some Show", 1, 3);
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "MISSING"), episode(102, 2, "MISSING"), episode(103, 3, "MISSING")));

        boolean pushed = engine.pushBest(sub, 2, List.of(torrent("Some.Show.S01E02.1080p", "g1", 10, "1080p")));

        assertTrue(pushed);
        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).save(captor.capture());
        assertEquals(2, captor.getValue().getEpisode());
    }

    @Test
    void pushBest_季包目标_占位全部缺失集() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, "Some Show", 1, 4);
        when(episodeService.listBySubscription(10)).thenReturn(List.of(
                episode(101, 1, "IN_LIBRARY"), episode(102, 2, "MISSING"),
                episode(103, 3, "MISSING"), episode(104, 4, "IN_FLIGHT")));

        boolean pushed = engine.pushBest(sub, SubscriptionMatcher.SEASON_PACK,
                List.of(torrent("Some.Show.S01.1080p.COMPLETE", "g-pack", 10, "1080p")));

        assertTrue(pushed);
        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).save(captor.capture());
        assertEquals(-1, captor.getValue().getEpisode());
        // 只占位第 2、3 集（IN_LIBRARY 和 IN_FLIGHT 的不动）
        verify(episodeService, times(2)).update(any(), any(Wrapper.class));
    }

    @Test
    void pushBest_电影目标episode恒为0() throws Exception {
        PtSubscriptionPlus movie = new PtSubscriptionPlus();
        movie.setId(20);
        movie.setMediaType("MOVIE");
        movie.setTitle("Some Movie");
        movie.setSeason(0);
        movie.setTotalEpisodes(1);
        movie.setStatus("ACTIVE");
        when(episodeService.listBySubscription(20)).thenReturn(List.of(episode(201, 0, "MISSING")));

        boolean pushed = engine.pushBest(movie, 0, List.of(torrent("Some.Movie.2020.1080p", "g1", 10, "1080p")));

        assertTrue(pushed);
        verify(downloaderClient).addTorrent(any(), anyString(), anyString(), anyString());
    }

    @Test
    void pushBest_候选为空_返回false() {
        PtSubscriptionPlus sub = tvSub(10, "Some Show", 1, 1);
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(101, 1, "MISSING")));

        assertFalse(engine.pushBest(sub, 1, List.of()));
    }
```

在文件顶部的 import 区加一行（与其他 `static org.junit.jupiter.api.Assertions.*` 放在一起）：

```java
import static org.junit.jupiter.api.Assertions.assertFalse;
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SubscriptionEngineTest`
预期：FAIL，编译错误 "cannot find symbol: method pushBest"

- [ ] **步骤 3：实现 `pushBest`，并把 `handleGroup`/`fillParsed` 改为包内可见**

在 `SubscriptionEngine.java` 中，把

```java
    private boolean handleGroup(MatchResult match, List<TorrentInfo> candidates,
```

改为（去掉 `private`）：

```java
    boolean handleGroup(MatchResult match, List<TorrentInfo> candidates,
```

把

```java
    /** 用本地解析结果填充种子的 parsedXxx 字段，不发任何网络请求 */
    private void fillParsed(TorrentInfo torrent) {
```

改为（去掉 `private`）：

```java
    /** 用本地解析结果填充种子的 parsedXxx 字段，不发任何网络请求 */
    void fillParsed(TorrentInfo torrent) {
```

在 `process` 方法后面（`handleGroup` 方法前面）加：

```java

    /**
     * 供搜索补集复用：已知目标订阅与集号（-1=季包，电影恒为0），跳过 RSS 的批量匹配阶段，
     * 直接对候选种子走过滤择优 → 原子占位 → 落库 → 推送，与 {@link #process} 共用同一段核心逻辑。
     *
     * @return 是否成功推送了一个种子
     */
    public boolean pushBest(PtSubscriptionPlus sub, int episode, List<TorrentInfo> candidates) {
        PtFilterConfigPlus globalConfig = filterConfigService.getConfig();
        MatchResult match = new MatchResult(sub, episode);
        Map<Integer, List<PtSubscriptionEpisodePlus>> episodeCache = new LinkedHashMap<>();
        return handleGroup(match, candidates, globalConfig, episodeCache);
    }
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SubscriptionEngineTest`
预期：PASS，全部测试（含新增 4 个）通过

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionEngine.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionEngineTest.java
git commit -m "feat(pt): SubscriptionEngine 新增 pushBest，供搜索补集复用过滤择优/推送链路"
```

---

### 任务 4：新增 DTO（SupplementResult / SearchRequest）

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/dto/SupplementResult.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/dto/SearchRequest.java`

- [ ] **步骤 1：创建 `SupplementResult`**

```java
package com.ruoyi.openliststrm.pt.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 搜索补集的结果，返回给前端展示成功/无结果提示。
 *
 * @author Jack
 */
@Data
@AllArgsConstructor
public class SupplementResult {

    /** 是否成功找到并推送了一个种子 */
    private boolean pushed;

    /** 本次搜索汇总到的候选种子总数（过滤前），供排查"搜到了但全被过滤掉"的情况 */
    private int candidateCount;
}
```

- [ ] **步骤 2：创建 `SearchRequest`**

```java
package com.ruoyi.openliststrm.pt.subscription.dto;

import lombok.Data;

/**
 * 搜索补集请求体。
 *
 * @author Jack
 */
@Data
public class SearchRequest {

    /** 目标集号：-1(SubscriptionMatcher.SEASON_PACK)=季包/整部，电影恒为0，剧集单集传具体集号 */
    private int episode;

    /** 搜索关键词，前端按标题/季集号预填，用户可编辑 */
    private String keyword;
}
```

- [ ] **步骤 3：编译确认无误**

运行：`mvn -pl ruoyi-openliststrm -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/dto/SupplementResult.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/dto/SearchRequest.java
git commit -m "feat(pt): 新增搜索补集的请求/响应 DTO"
```

---

### 任务 5：SearchSupplementService

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementServiceTest.java`

- [ ] **步骤 1：写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementServiceTest.java`：

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtIndexerPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.indexer.TorznabClient;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.pt.subscription.dto.SupplementResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchSupplementServiceTest {

    @Mock private IPtIndexerPlusService indexerService;
    @Mock private TorznabClient torznabClient;
    @Mock private SubscriptionEngine subscriptionEngine;
    @Mock private IPtSubscriptionPlusService subscriptionService;

    private SearchSupplementService service;

    @BeforeEach
    void setUp() {
        service = new SearchSupplementService(indexerService, torznabClient, subscriptionEngine, subscriptionService);
    }

    private PtIndexerPlus indexer(int id) {
        PtIndexerPlus i = new PtIndexerPlus();
        i.setId(id);
        i.setName("idx-" + id);
        i.setEnabled("1");
        return i;
    }

    private PtSubscriptionPlus tvSub(int id, int season, int total) {
        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setId(id);
        sub.setMediaType("TV");
        sub.setTitle("Some Show");
        sub.setSeason(season);
        sub.setTotalEpisodes(total);
        sub.setStatus("ACTIVE");
        return sub;
    }

    private TorrentInfo torrent(String title) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle(title);
        return t;
    }

    // ---------- searchAcrossIndexers ----------

    @Test
    void 并发搜索所有启用索引器_合并结果() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1), indexer(2)));
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(torrent("a")), List.of(torrent("b")));

        List<TorrentInfo> results = service.searchAcrossIndexers("kw");

        assertEquals(2, results.size());
    }

    @Test
    void 单个索引器搜索失败_不影响其他索引器结果() throws Exception {
        PtIndexerPlus idx1 = indexer(1);
        PtIndexerPlus idx2 = indexer(2);
        when(indexerService.listEnabled()).thenReturn(List.of(idx1, idx2));
        when(torznabClient.search(idx1, "kw")).thenThrow(new IOException("timeout"));
        when(torznabClient.search(idx2, "kw")).thenReturn(List.of(torrent("b")));

        List<TorrentInfo> results = service.searchAcrossIndexers("kw");

        assertEquals(1, results.size());
        assertEquals("b", results.get(0).getTitle());
    }

    @Test
    void 无启用索引器_返回空列表() {
        when(indexerService.listEnabled()).thenReturn(List.of());

        assertTrue(service.searchAcrossIndexers("kw").isEmpty());
    }

    // ---------- supplement ----------

    @Test
    void supplement_成功推送_返回pushed为true并回写搜索时间() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(torrent("Some.Show.S01E02.1080p")));
        when(subscriptionEngine.pushBest(eq(sub), eq(2), anyList())).thenReturn(true);

        SupplementResult result = service.supplement(10, 2, "Some Show S01E02");

        assertTrue(result.isPushed());
        assertEquals(1, result.getCandidateCount());
        ArgumentCaptor<PtSubscriptionPlus> captor = ArgumentCaptor.forClass(PtSubscriptionPlus.class);
        verify(subscriptionService).updateById(captor.capture());
        Assertions.assertNotNull(captor.getValue().getLastSearchTime());
    }

    @Test
    void supplement_搜索结果先做本地解析再交给引擎() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
        TorrentInfo raw = torrent("Some.Show.S01E02.1080p");
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(raw));

        service.supplement(10, 2, "Some Show S01E02");

        verify(subscriptionEngine).fillParsed(raw);
    }

    @Test
    void supplement_订阅不存在_抛异常() {
        when(subscriptionService.getById(99)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.supplement(99, 1, "kw"));
    }

    @Test
    void supplement_订阅已暂停_抛异常() {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        sub.setStatus("PAUSED");
        when(subscriptionService.getById(10)).thenReturn(sub);

        assertThrows(IllegalArgumentException.class, () -> service.supplement(10, 1, "kw"));
    }

    @Test
    void supplement_电影订阅传非0集号_抛异常() {
        PtSubscriptionPlus movie = new PtSubscriptionPlus();
        movie.setId(20);
        movie.setMediaType("MOVIE");
        movie.setStatus("ACTIVE");
        when(subscriptionService.getById(20)).thenReturn(movie);

        assertThrows(IllegalArgumentException.class, () -> service.supplement(20, 1, "kw"));
    }

    @Test
    void supplement_剧集集号超出总集数_抛异常() {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);

        assertThrows(IllegalArgumentException.class, () -> service.supplement(10, 4, "kw"));
    }

    @Test
    void supplement_季包哨兵值_剧集订阅允许通过() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(indexerService.listEnabled()).thenReturn(List.of());

        SupplementResult result = service.supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");

        assertFalse(result.isPushed());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SearchSupplementServiceTest`
预期：FAIL，编译错误 "cannot find symbol: class SearchSupplementService"

- [ ] **步骤 3：实现 `SearchSupplementService`**

创建 `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java`：

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtIndexerPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.indexer.TorznabClient;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.pt.subscription.dto.SupplementResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * 搜索补集编排：关键词并发查询所有索引器，交给 {@link SubscriptionEngine} 走与 RSS 相同的
 * 过滤择优/占位/推送链路。职责边界同样终止于"把种子推给下载器"。
 *
 * @author Jack
 */
@Slf4j
@Service
public class SearchSupplementService {

    private final IPtIndexerPlusService indexerService;
    private final TorznabClient torznabClient;
    private final SubscriptionEngine subscriptionEngine;
    private final IPtSubscriptionPlusService subscriptionService;

    public SearchSupplementService(IPtIndexerPlusService indexerService,
                                   TorznabClient torznabClient,
                                   SubscriptionEngine subscriptionEngine,
                                   IPtSubscriptionPlusService subscriptionService) {
        this.indexerService = indexerService;
        this.torznabClient = torznabClient;
        this.subscriptionEngine = subscriptionEngine;
        this.subscriptionService = subscriptionService;
    }

    /**
     * 对指定订阅的指定目标（集号，或季包/电影的哨兵值）发起一次搜索补集。
     *
     * @throws IllegalArgumentException 订阅不存在、订阅未在订阅中(ACTIVE)，或 episode 不合法
     */
    public SupplementResult supplement(Integer subId, int episode, String keyword) {
        PtSubscriptionPlus sub = requireSearchable(subId);
        validateEpisode(sub, episode);

        List<TorrentInfo> candidates = searchAcrossIndexers(keyword);
        for (TorrentInfo torrent : candidates) {
            subscriptionEngine.fillParsed(torrent);
        }
        boolean pushed = subscriptionEngine.pushBest(sub, episode, candidates);

        sub.setLastSearchTime(new Date());
        subscriptionService.updateById(sub);

        log.info("订阅[{}] {} 关键词[{}]搜索补集：候选{}个，{}",
                sub.getId(), sub.getTitle(), keyword, candidates.size(), pushed ? "已推送" : "未推送");
        return new SupplementResult(pushed, candidates.size());
    }

    /**
     * 并发向所有启用索引器发起关键词搜索，合并结果。单索引器超时/异常只记 log，不影响其他索引器。
     */
    public List<TorrentInfo> searchAcrossIndexers(String keyword) {
        List<PtIndexerPlus> indexers = indexerService.listEnabled();
        if (indexers.isEmpty()) {
            return List.of();
        }
        List<TorrentInfo> merged = new CopyOnWriteArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = indexers.stream()
                    .map(indexer -> CompletableFuture.runAsync(() -> {
                        try {
                            merged.addAll(torznabClient.search(indexer, keyword));
                        } catch (Exception e) {
                            log.warn("索引器[{}]关键词搜索失败：{}", indexer.getName(), e.getMessage());
                        }
                    }, executor))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        return new ArrayList<>(merged);
    }

    private PtSubscriptionPlus requireSearchable(Integer subId) {
        PtSubscriptionPlus sub = subscriptionService.getById(subId);
        if (sub == null) {
            throw new IllegalArgumentException("订阅不存在：" + subId);
        }
        if (!SubscriptionService.STATUS_ACTIVE.equals(sub.getStatus())) {
            throw new IllegalArgumentException("订阅未在订阅中(当前状态 " + sub.getStatus() + ")，无法搜索补集");
        }
        return sub;
    }

    private void validateEpisode(PtSubscriptionPlus sub, int episode) {
        if (SubscriptionService.TYPE_MOVIE.equalsIgnoreCase(sub.getMediaType())) {
            if (episode != 0) {
                throw new IllegalArgumentException("电影订阅只能传 episode=0");
            }
            return;
        }
        if (episode == SubscriptionMatcher.SEASON_PACK) {
            return;
        }
        if (episode < 1 || episode > sub.getTotalEpisodes()) {
            throw new IllegalArgumentException("集号超出范围：" + episode);
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SearchSupplementServiceTest`
预期：PASS，全部 9 个测试通过

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementServiceTest.java
git commit -m "feat(pt): 新增 SearchSupplementService，并发搜索索引器并推送最优结果"
```

---

### 任务 6：REST 端点

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtSubscriptionRestController.java`

- [ ] **步骤 1：加自动装配字段与端点**

在 `PtSubscriptionRestController.java` 中，把

```java
    @Autowired
    private IPtSubscriptionEpisodePlusService episodeService;
```

改为：

```java
    @Autowired
    private IPtSubscriptionEpisodePlusService episodeService;

    @Autowired
    private SearchSupplementService searchSupplementService;
```

在文件顶部 import 区加：

```java
import com.ruoyi.openliststrm.pt.subscription.SearchSupplementService;
import com.ruoyi.openliststrm.pt.subscription.dto.SearchRequest;
import com.ruoyi.openliststrm.pt.subscription.dto.SupplementResult;
```

在 `refresh` 方法后面（`pause` 方法前面）加：

```java

    /**
     * 搜索补集：关键词并发搜索所有索引器，命中后走与 RSS 相同的过滤择优/推送链路。
     */
    @PostMapping("/{id}/search")
    public Result<SupplementResult> search(@PathVariable("id") Integer id, @RequestBody SearchRequest request) {
        try {
            return Result.success(searchSupplementService.supplement(id, request.getEpisode(), request.getKeyword()));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
```

- [ ] **步骤 2：编译确认无误**

运行：`mvn -pl ruoyi-openliststrm -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtSubscriptionRestController.java
git commit -m "feat(pt): 新增 POST /pt-subscriptions/{id}/search 搜索补集端点"
```

---

### 任务 7：AutoSearchService

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/AutoSearchService.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/task/AutoSearchServiceTest.java`

- [ ] **步骤 1：写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/task/AutoSearchServiceTest.java`：

```java
package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtFilterConfigPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.subscription.SearchSupplementService;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoSearchServiceTest {

    @Mock private IPtSubscriptionPlusService subscriptionService;
    @Mock private IPtSubscriptionEpisodePlusService episodeService;
    @Mock private IPtFilterConfigPlusService filterConfigService;
    @Mock private SearchSupplementService searchSupplementService;

    private AutoSearchService service() {
        return new AutoSearchService(subscriptionService, episodeService, filterConfigService, searchSupplementService);
    }

    private PtSubscriptionPlus sub(int id, String mediaType, int season, String autoSearch, Date lastSearchTime) {
        PtSubscriptionPlus s = new PtSubscriptionPlus();
        s.setId(id);
        s.setMediaType(mediaType);
        s.setTitle(mediaType.equals("MOVIE") ? "Some Movie" : "Some Show");
        s.setSeason(season);
        s.setTotalEpisodes(10);
        s.setStatus("ACTIVE");
        s.setAutoSearch(autoSearch);
        s.setLastSearchTime(lastSearchTime);
        return s;
    }

    private PtFilterConfigPlus config(Integer intervalHours) {
        PtFilterConfigPlus c = new PtFilterConfigPlus();
        c.setAutoSearchIntervalHours(intervalHours);
        return c;
    }

    private PtSubscriptionEpisodePlus episode(int number, String state) {
        PtSubscriptionEpisodePlus ep = new PtSubscriptionEpisodePlus();
        ep.setEpisode(number);
        ep.setState(state);
        return ep;
    }

    @Test
    void 未开启自动补搜的订阅_跳过() {
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "0", null)));
        when(filterConfigService.getConfig()).thenReturn(config(24));

        service().run();

        verify(searchSupplementService, never()).supplement(any(), anyInt(), anyString());
    }

    @Test
    void 从未搜索过_视为到期_发起搜索() {
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "1", null)));
        when(filterConfigService.getConfig()).thenReturn(config(24));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(1, "MISSING")));

        service().run();

        verify(searchSupplementService).supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");
    }

    @Test
    void 未到周期_跳过() {
        Date recent = new Date(System.currentTimeMillis() - 3600_000L);
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "1", recent)));
        when(filterConfigService.getConfig()).thenReturn(config(24));

        service().run();

        verify(searchSupplementService, never()).supplement(any(), anyInt(), anyString());
    }

    @Test
    void 已过周期_发起搜索() {
        Date old = new Date(System.currentTimeMillis() - 25L * 3600_000L);
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "1", old)));
        when(filterConfigService.getConfig()).thenReturn(config(24));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(1, "MISSING")));

        service().run();

        verify(searchSupplementService).supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");
    }

    @Test
    void 没有任何缺失集_跳过不发请求() {
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "1", null)));
        when(filterConfigService.getConfig()).thenReturn(config(24));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(1, "IN_LIBRARY")));

        service().run();

        verify(searchSupplementService, never()).supplement(any(), anyInt(), anyString());
    }

    @Test
    void 电影订阅_目标集号恒为0() {
        when(subscriptionService.listActive()).thenReturn(List.of(sub(20, "MOVIE", 0, "1", null)));
        when(filterConfigService.getConfig()).thenReturn(config(24));
        when(episodeService.listBySubscription(20)).thenReturn(List.of(episode(0, "MISSING")));

        service().run();

        verify(searchSupplementService).supplement(20, 0, "Some Movie");
    }

    @Test
    void 全局周期未配置_默认24小时() {
        Date old = new Date(System.currentTimeMillis() - 25L * 3600_000L);
        when(subscriptionService.listActive()).thenReturn(List.of(sub(10, "TV", 1, "1", old)));
        when(filterConfigService.getConfig()).thenReturn(config(null));
        when(episodeService.listBySubscription(10)).thenReturn(List.of(episode(1, "MISSING")));

        service().run();

        verify(searchSupplementService).supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");
    }

    @Test
    void 单个订阅搜索抛异常_不影响其他订阅() {
        when(subscriptionService.listActive()).thenReturn(List.of(
                sub(10, "TV", 1, "1", null), sub(11, "TV", 1, "1", null)));
        when(filterConfigService.getConfig()).thenReturn(config(24));
        when(episodeService.listBySubscription(any())).thenReturn(List.of(episode(1, "MISSING")));
        when(searchSupplementService.supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01"))
                .thenThrow(new RuntimeException("boom"));

        service().run();

        verify(searchSupplementService).supplement(11, SubscriptionMatcher.SEASON_PACK, "Some Show S01");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=AutoSearchServiceTest`
预期：FAIL，编译错误 "cannot find symbol: class AutoSearchService"

- [ ] **步骤 3：实现 `AutoSearchService`**

创建 `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/AutoSearchService.java`：

```java
package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtFilterConfigPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.subscription.SearchSupplementService;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionMatcher;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自动补搜业务逻辑：对开启了 auto_search 且到期的订阅，按季/整部粒度发起一次搜索。
 * 只做整季/整部粒度（不逐集搜索），避免缺集很多的老剧每轮对索引器打太多请求。
 *
 * @author Jack
 */
@Slf4j
@Service
public class AutoSearchService {

    private static final String STATE_MISSING = "MISSING";
    private static final String AUTO_SEARCH_ON = "1";
    private static final int DEFAULT_INTERVAL_HOURS = 24;

    private final IPtSubscriptionPlusService subscriptionService;
    private final IPtSubscriptionEpisodePlusService episodeService;
    private final IPtFilterConfigPlusService filterConfigService;
    private final SearchSupplementService searchSupplementService;

    public AutoSearchService(IPtSubscriptionPlusService subscriptionService,
                             IPtSubscriptionEpisodePlusService episodeService,
                             IPtFilterConfigPlusService filterConfigService,
                             SearchSupplementService searchSupplementService) {
        this.subscriptionService = subscriptionService;
        this.episodeService = episodeService;
        this.filterConfigService = filterConfigService;
        this.searchSupplementService = searchSupplementService;
    }

    /**
     * 扫一轮：对每个开启自动补搜、到期、且仍有缺集的订阅发起一次搜索。单个订阅异常不影响其他订阅。
     */
    public void run() {
        List<PtSubscriptionPlus> active = subscriptionService.listActive();
        if (active.isEmpty()) {
            return;
        }
        int intervalHours = resolveIntervalHours();
        long now = System.currentTimeMillis();

        for (PtSubscriptionPlus sub : active) {
            if (!AUTO_SEARCH_ON.equals(sub.getAutoSearch())) {
                continue;
            }
            if (!isDue(sub, intervalHours, now)) {
                continue;
            }
            try {
                trySearch(sub);
            } catch (Exception e) {
                log.warn("订阅[{}]自动补搜失败：{}", sub.getId(), e.getMessage());
            }
        }
    }

    private void trySearch(PtSubscriptionPlus sub) {
        List<PtSubscriptionEpisodePlus> episodes = episodeService.listBySubscription(sub.getId());
        boolean hasMissing = episodes.stream().anyMatch(ep -> STATE_MISSING.equals(ep.getState()));
        if (!hasMissing) {
            return;
        }
        boolean movie = SubscriptionService.TYPE_MOVIE.equalsIgnoreCase(sub.getMediaType());
        int episode = movie ? 0 : SubscriptionMatcher.SEASON_PACK;
        String keyword = movie ? sub.getTitle() : sub.getTitle() + " S" + pad(sub.getSeason());
        searchSupplementService.supplement(sub.getId(), episode, keyword);
    }

    private boolean isDue(PtSubscriptionPlus sub, int intervalHours, long now) {
        if (sub.getLastSearchTime() == null) {
            return true;
        }
        return now - sub.getLastSearchTime().getTime() >= intervalHours * 3600_000L;
    }

    private int resolveIntervalHours() {
        PtFilterConfigPlus config = filterConfigService.getConfig();
        Integer hours = config.getAutoSearchIntervalHours();
        return hours == null ? DEFAULT_INTERVAL_HOURS : hours;
    }

    private String pad(Integer season) {
        int s = season == null ? 0 : season;
        return s < 10 ? "0" + s : String.valueOf(s);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=AutoSearchServiceTest`
预期：PASS，全部 8 个测试通过

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/AutoSearchService.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/task/AutoSearchServiceTest.java
git commit -m "feat(pt): 新增 AutoSearchService，到期且有缺集的自动补搜订阅按季/整部搜索"
```

---

### 任务 8：AutoSearchTask 调度器外壳

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/AutoSearchTask.java`

- [ ] **步骤 1：创建调度器**

风格与 `LibrarySyncTask` 完全一致（`RssPollTask`/`DownloadTrackTask`/`LibrarySyncTask` 均无专属单元测试，调度器外壳只是把 `virtualScheduledExecutor` 心跳接到业务 Service，靠启动验证确认装配正确）：

```java
package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.common.utils.ThreadTraceIdUtil;
import com.ruoyi.common.utils.spring.SpringUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 自动补搜心跳：每 30 分钟检查一次哪些订阅开启了自动补搜且到期，到期的发起一次搜索
 * （具体周期由 pt_filter_config.auto_search_interval_hours 决定，默认 24 小时）。
 *
 * @author Jack
 */
@Slf4j
@Component
public class AutoSearchTask {

    @Autowired
    private AutoSearchService autoSearchService;

    private final TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor");

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ThreadTraceIdUtil.initTraceId();
        scheduler.scheduleAtFixedRate(this::poll, Instant.now().plusSeconds(180), Duration.ofMinutes(30));
        log.info("AutoSearchTask started");
    }

    @PreDestroy
    public void stop() {
        log.info("AutoSearchTask stopped");
        MDC.clear();
    }

    private void poll() {
        try {
            autoSearchService.run();
        } catch (Exception e) {
            log.error("AutoSearchTask poll error", e);
        }
    }
}
```

- [ ] **步骤 2：编译确认无误**

运行：`mvn -pl ruoyi-openliststrm -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/AutoSearchTask.java
git commit -m "feat(pt): 新增 AutoSearchTask 调度器，30分钟心跳检查自动补搜到期订阅"
```

---

### 任务 9：后端启动验证

**背景（见项目 `AGENTS.md`）：** 新增 `@Component`/`@Service` bean 后必须做启动验证——单元测试常用构造器直接 new 目标类，能绕过 Spring 装配；只有真实启动才能暴露 bean 装配失败（比如构造器误注入了非 bean 的依赖）。本任务新增了 `SearchSupplementService`、`AutoSearchService`、`AutoSearchTask` 三个新 bean，且改动了 `PtSubscriptionRestController` 的自动装配，必须验证。

- [ ] **步骤 1：跑全部单元测试**

运行：`mvn -pl ruoyi-openliststrm -am test`
预期：BUILD SUCCESS，`pt` 包下全部测试通过（含本计划新增的约 21 个测试）

- [ ] **步骤 2：打包**

运行：`mvn clean package -DskipTests`
预期：BUILD SUCCESS，生成 `ruoyi-admin/target/ruoyi-admin.jar`

- [ ] **步骤 3：重启后端容器并确认无重启循环**

```bash
docker compose up -d --build --no-deps backend
```

等待约 30 秒后检查：

```bash
docker ps --filter name=osr-backend --format "{{.Names}}: {{.Status}}"
```

预期：状态里不含 "Restarting"，`STATUS` 显示 `Up`。若容器反复重启，按 `AGENTS.md` 里的排查方法：

```bash
docker update --restart=no osr-backend && docker restart osr-backend
docker cp osr-backend:/data/logs ./tmp-logs
```

然后读 `./tmp-logs/sys-error.log` 定位具体是哪个 bean 装配失败。

- [ ] **步骤 4：验证新端点能正常响应（哪怕业务上报错，也必须是 JSON 错误而不是 404/500）**

前提：容器内已有至少一条 `pt_subscription` 记录（本地开发环境可通过前端"新增订阅"或直接 INSERT 造一条测试数据，`id` 假设为 1）。

```bash
curl -s -X POST http://localhost:6895/api/openliststrm/pt-subscriptions/1/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <替换为登录后拿到的token>" \
  -d '{"episode": -1, "keyword": "测试关键词"}'
```

预期：返回标准 `{"code":...,"msg":...,"data":...}` 结构（无论 code 是 200 还是业务错误），而不是网关 404 或 Spring 启动失败的 500 堆栈。

- [ ] **步骤 5：提交验证记录（若前几步发现问题已在对应任务中修复并重新 commit，此步骤仅确认无遗留改动）**

```bash
git status
```

预期：working tree clean（若不是，说明验证过程中做了修复但忘记 commit，回头补上）

---

### 任务 10：前端 API 封装

**文件：**
- 修改：`openlist-web/src/api/openlist/ptSubscription.ts`

- [ ] **步骤 1：加 `searchSupplementApi`**

在文件末尾加：

```ts

/** 搜索补集：关键词搜索所有索引器并推送最优结果 */
export function searchSupplementApi(id: number, data: { episode: number; keyword: string }) {
  return request.post<any, { pushed: boolean; candidateCount: number }>(
    `/openliststrm/pt-subscriptions/${id}/search`,
    data
  )
}
```

- [ ] **步骤 2：类型检查**

运行：`cd openlist-web && npx vue-tsc --noEmit -p tsconfig.json`
预期：无新增类型错误（该命令可能因项目已有历史告警而非 0 输出，只需确认没有新增指向 `ptSubscription.ts` 的错误）

- [ ] **步骤 3：Commit**

```bash
git add openlist-web/src/api/openlist/ptSubscription.ts
git commit -m "feat(pt): 前端新增搜索补集 API 封装"
```

---

### 任务 11：前端 composable

**文件：**
- 修改：`openlist-web/src/composables/usePtSubscription.ts`

- [ ] **步骤 1：加 import**

把

```ts
import {
  getPtSubscriptionListApi,
  addPtSubscriptionApi,
  updatePtSubscriptionApi,
  deletePtSubscriptionApi,
  tmdbSearchApi,
  subscribeApi,
  getSubscriptionProgressApi,
  refreshSubscriptionApi,
  pauseSubscriptionApi,
  resumeSubscriptionApi
} from '@/api/openlist/ptSubscription'
```

改为：

```ts
import {
  getPtSubscriptionListApi,
  addPtSubscriptionApi,
  updatePtSubscriptionApi,
  deletePtSubscriptionApi,
  tmdbSearchApi,
  subscribeApi,
  getSubscriptionProgressApi,
  refreshSubscriptionApi,
  pauseSubscriptionApi,
  resumeSubscriptionApi,
  searchSupplementApi
} from '@/api/openlist/ptSubscription'
```

- [ ] **步骤 2：记录当前查看进度的订阅行，供搜索用**

把

```ts
  const showProgress = async (row: any) => {
    progressOpen.value = true
    progressLoading.value = true
    progress.value = null
    try {
      progress.value = await getSubscriptionProgressApi(row.id)
    } catch (e) {
      console.error(e)
    } finally {
      progressLoading.value = false
    }
  }
```

改为：

```ts
  const currentSubscription = ref<any>(null)

  const showProgress = async (row: any) => {
    currentSubscription.value = row
    progressOpen.value = true
    progressLoading.value = true
    progress.value = null
    try {
      progress.value = await getSubscriptionProgressApi(row.id)
    } catch (e) {
      console.error(e)
    } finally {
      progressLoading.value = false
    }
  }
```

- [ ] **步骤 3：加搜索弹窗状态与处理函数、自动补搜开关处理函数**

在 `// ---------- 行操作 ----------` 这一段之前加：

```ts
  // ---------- 搜索补集 ----------

  const searchDialogOpen = ref(false)
  const searchDialogLoading = ref(false)
  const searchDialogKeyword = ref('')
  const searchDialogTarget = ref<{ subId: number; episode: number } | null>(null)

  const pad2 = (n: number) => (n < 10 ? '0' + n : String(n))

  /** 打开整季/整部搜索确认框（订阅详情顶部按钮、列表操作列按钮共用） */
  const openSeasonSearch = (row: any) => {
    const isMovie = row.mediaType === 'MOVIE'
    searchDialogTarget.value = { subId: row.id, episode: isMovie ? 0 : -1 }
    searchDialogKeyword.value = isMovie ? row.title : `${row.title} S${pad2(row.season)}`
    searchDialogOpen.value = true
  }

  /** 打开单集搜索确认框，episode 为具体集号（剧集缺集列表专用） */
  const openEpisodeSearch = (row: any, episode: number) => {
    searchDialogTarget.value = { subId: row.id, episode }
    searchDialogKeyword.value = `${row.title} S${pad2(row.season)}E${pad2(episode)}`
    searchDialogOpen.value = true
  }

  const confirmSearch = async () => {
    if (!searchDialogTarget.value) return
    if (!searchDialogKeyword.value?.trim()) {
      ElMessage.warning('请输入搜索关键词')
      return
    }
    searchDialogLoading.value = true
    try {
      const result = await searchSupplementApi(searchDialogTarget.value.subId, {
        episode: searchDialogTarget.value.episode,
        keyword: searchDialogKeyword.value.trim()
      })
      ElMessage[result.pushed ? 'success' : 'info'](result.pushed ? '已找到并推送下载' : '未搜索到匹配资源')
      searchDialogOpen.value = false
      base.getList()
      if (currentSubscription.value && currentSubscription.value.id === searchDialogTarget.value.subId) {
        progress.value = await getSubscriptionProgressApi(searchDialogTarget.value.subId)
      }
    } catch (e) {
      console.error(e)
    } finally {
      searchDialogLoading.value = false
    }
  }

  const toggleAutoSearch = async (row: any) => {
    try {
      await updatePtSubscriptionApi({ id: row.id, autoSearch: row.autoSearch })
      ElMessage.success(row.autoSearch === '1' ? '已开启自动补搜' : '已关闭自动补搜')
    } catch (e) {
      // 请求失败时把开关状态还原（v-model 已经乐观更新过了）
      row.autoSearch = row.autoSearch === '1' ? '0' : '1'
      console.error(e)
    }
  }

```

- [ ] **步骤 4：在 return 里导出新增的状态与函数**

把

```ts
  return {
    ...base,
    // 建订阅向导
    subscribeOpen, searchLoading, subscribeLoading, searchResults, searchForm,
    picked, pickedSeason, openSubscribeDialog, doSearch, pick, confirmSubscribe,
    // 进度
    progressOpen, progressLoading, progress, showProgress,
    // 行操作
    handleRefresh, handlePause, handleResume, handleRemove
  }
```

改为：

```ts
  return {
    ...base,
    // 建订阅向导
    subscribeOpen, searchLoading, subscribeLoading, searchResults, searchForm,
    picked, pickedSeason, openSubscribeDialog, doSearch, pick, confirmSubscribe,
    // 进度
    progressOpen, progressLoading, progress, currentSubscription, showProgress,
    // 搜索补集
    searchDialogOpen, searchDialogLoading, searchDialogKeyword,
    openSeasonSearch, openEpisodeSearch, confirmSearch, toggleAutoSearch,
    // 行操作
    handleRefresh, handlePause, handleResume, handleRemove
  }
```

- [ ] **步骤 5：类型检查**

运行：`cd openlist-web && npx vue-tsc --noEmit -p tsconfig.json`
预期：无新增指向 `usePtSubscription.ts` 的类型错误

- [ ] **步骤 6：Commit**

```bash
git add openlist-web/src/composables/usePtSubscription.ts
git commit -m "feat(pt): composable 新增搜索补集弹窗状态与自动补搜开关处理"
```

---

### 任务 12：前端页面

**文件：**
- 修改：`openlist-web/src/views/openlist/ptSubscription/index.vue`

- [ ] **步骤 1：列表加"自动补搜"列与"上次搜索"列**

把

```html
        <el-table-column label="上次命中" prop="lastMatchTime" width="170" align="center">
          <template #default="scope">
            {{ scope.row.lastMatchTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="300" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="showProgress(scope.row)">进度</el-button>
            <el-button link type="primary" @click="handleRefresh(scope.row)">对账</el-button>
            <el-button v-if="scope.row.status !== 'PAUSED'" link type="warning" @click="handlePause(scope.row)">暂停</el-button>
            <el-button v-else link type="success" @click="handleResume(scope.row)">恢复</el-button>
            <el-button link type="danger" @click="handleRemove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
```

改为：

```html
        <el-table-column label="上次命中" prop="lastMatchTime" width="170" align="center">
          <template #default="scope">
            {{ scope.row.lastMatchTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="上次搜索" prop="lastSearchTime" width="170" align="center">
          <template #default="scope">
            {{ scope.row.lastSearchTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="自动补搜" width="100" align="center">
          <template #default="scope">
            <el-switch
              v-model="scope.row.autoSearch"
              active-value="1"
              inactive-value="0"
              @change="toggleAutoSearch(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="360" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="showProgress(scope.row)">进度</el-button>
            <el-button link type="primary" @click="openSeasonSearch(scope.row)">搜索补齐</el-button>
            <el-button link type="primary" @click="handleRefresh(scope.row)">对账</el-button>
            <el-button v-if="scope.row.status !== 'PAUSED'" link type="warning" @click="handlePause(scope.row)">暂停</el-button>
            <el-button v-else link type="success" @click="handleResume(scope.row)">恢复</el-button>
            <el-button link type="danger" @click="handleRemove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
```

- [ ] **步骤 2：进度弹窗里给缺集列表加单集搜索按钮，footer 加整季搜索按钮**

把

```html
    <!-- 进度 -->
    <el-dialog v-model="progressOpen" title="订阅进度" width="520px" append-to-body class="modern-dialog">
      <div v-loading="progressLoading">
        <template v-if="progress">
          <p class="progress-title">{{ progress.title }}</p>
          <el-progress
            :percentage="progress.totalEpisodes ? Math.round((progress.inLibraryCount / progress.totalEpisodes) * 100) : 0"
          />
          <p>已入库 <strong>{{ progress.inLibraryCount }}</strong> / {{ progress.totalEpisodes }} 集</p>
          <p v-if="progress.inFlightCount">在途 {{ progress.inFlightCount }} 集（已推送下载器，尚未入库）</p>
          <p v-if="progress.missingEpisodes && progress.missingEpisodes.length">
            仍缺第 {{ progress.missingEpisodes.join('、') }} 集
          </p>
          <p v-else class="all-done">全部集已入库</p>
        </template>
      </div>
      <template #footer>
        <el-button @click="progressOpen = false">关闭</el-button>
      </template>
    </el-dialog>
```

改为：

```html
    <!-- 进度 -->
    <el-dialog v-model="progressOpen" title="订阅进度" width="520px" append-to-body class="modern-dialog">
      <div v-loading="progressLoading">
        <template v-if="progress">
          <p class="progress-title">{{ progress.title }}</p>
          <el-progress
            :percentage="progress.totalEpisodes ? Math.round((progress.inLibraryCount / progress.totalEpisodes) * 100) : 0"
          />
          <p>已入库 <strong>{{ progress.inLibraryCount }}</strong> / {{ progress.totalEpisodes }} 集</p>
          <p v-if="progress.inFlightCount">在途 {{ progress.inFlightCount }} 集（已推送下载器，尚未入库）</p>
          <div v-if="progress.missingEpisodes && progress.missingEpisodes.length" class="missing-list">
            仍缺第
            <span v-for="ep in progress.missingEpisodes" :key="ep" class="missing-item">
              {{ ep }}
              <el-button
                v-if="currentSubscription && currentSubscription.mediaType !== 'MOVIE'"
                link
                type="primary"
                size="small"
                @click="openEpisodeSearch(currentSubscription, ep)"
              >搜</el-button>
            </span>
            集
          </div>
          <p v-else class="all-done">全部集已入库</p>
        </template>
      </div>
      <template #footer>
        <el-button v-if="currentSubscription" type="primary" @click="openSeasonSearch(currentSubscription)">
          搜索补齐
        </el-button>
        <el-button @click="progressOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 搜索补集确认 -->
    <el-dialog v-model="searchDialogOpen" title="搜索补集" width="480px" append-to-body class="modern-dialog">
      <el-form label-width="80px">
        <el-form-item label="关键词">
          <el-input v-model="searchDialogKeyword" placeholder="搜索关键词，可编辑后再搜" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="searchDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="searchDialogLoading" @click="confirmSearch">搜索</el-button>
      </template>
    </el-dialog>
```

- [ ] **步骤 3：`<script setup>` 里补充解构导出**

把

```ts
const {
  taskList, loading, total, queryParams, getList, handleQuery, resetQuery, queryRef,
  subscribeOpen, searchLoading, subscribeLoading, searchResults, searchForm,
  picked, pickedSeason, openSubscribeDialog, doSearch, pick, confirmSubscribe,
  progressOpen, progressLoading, progress, showProgress,
  handleRefresh, handlePause, handleResume, handleRemove
} = usePtSubscription()
```

改为：

```ts
const {
  taskList, loading, total, queryParams, getList, handleQuery, resetQuery, queryRef,
  subscribeOpen, searchLoading, subscribeLoading, searchResults, searchForm,
  picked, pickedSeason, openSubscribeDialog, doSearch, pick, confirmSubscribe,
  progressOpen, progressLoading, progress, currentSubscription, showProgress,
  searchDialogOpen, searchDialogLoading, searchDialogKeyword,
  openSeasonSearch, openEpisodeSearch, confirmSearch, toggleAutoSearch,
  handleRefresh, handlePause, handleResume, handleRemove
} = usePtSubscription()
```

- [ ] **步骤 4：加样式**

在 `<style scoped>` 块的 `.all-done` 规则后面加：

```css

.missing-list {
  margin: 8px 0;
  line-height: 1.8;
}

.missing-item {
  display: inline-flex;
  align-items: center;
  margin-right: 4px;
}
```

- [ ] **步骤 5：类型检查**

运行：`cd openlist-web && npx vue-tsc --noEmit -p tsconfig.json`
预期：无新增指向 `ptSubscription/index.vue` 的类型错误

- [ ] **步骤 6：Commit**

```bash
git add openlist-web/src/views/openlist/ptSubscription/index.vue
git commit -m "feat(pt): 订阅页面新增搜索补齐入口、自动补搜开关、上次搜索列"
```

---

### 任务 13：前端浏览器验证

**目的：** 类型检查只保证编译通过，不保证交互和请求参数正确；本任务在真实 dev server + 后端容器下手工走一遍。

- [ ] **步骤 1：启动前端 dev server**

使用 preview 工具启动 `openlist-web`（`npm run dev`，端口 3000，`/api` 已代理到 `localhost:6895`），确认后端容器已在任务 9 中启动且状态正常。

- [ ] **步骤 2：打开 PT 订阅页面，走一遍新增交互**

导航到 `http://localhost:3000/openlist/ptSubscription`（若菜单未放出，直接访问该路径；菜单可见性不属于本次改动范围）。

1. 确认列表新增了"上次搜索"列和"自动补搜"开关列，切换开关后 Network 面板能看到一次 `PUT /api/openliststrm/pt-subscriptions` 请求，且请求体只含 `id` 和 `autoSearch`
2. 点击某一行的"搜索补齐"，确认弹出关键词确认框，且预填的关键词符合"标题 + S季号"格式（剧集）或"标题"（电影）
3. 确认后点"搜索"，Network 面板确认请求打到 `POST /api/openliststrm/pt-subscriptions/{id}/search`，请求体含 `episode` 和 `keyword`
4. 打开某订阅的"进度"弹窗，若有缺集，确认每个缺集号后面有一个"搜"按钮，点击后弹出预填了具体集号的关键词确认框（`S季号E集号` 格式）

- [ ] **步骤 3：检查控制台与网络面板无异常**

用浏览器工具读取 console 消息与网络请求，确认没有 JS 报错、没有 4xx/5xx（业务报错如"未配置索引器"以外的错误）。

- [ ] **步骤 4：截图存档**

截一张订阅页面列表（含新列）和搜索确认弹窗的图，作为本次功能验证的凭证。

- [ ] **步骤 5：若发现问题，回到对应任务修复**

修复后重新走一遍受影响的步骤，并对修复本身做一次新的 commit（说明修的是什么问题）。

---

## 自检记录

- **规格覆盖度**：设计文档 §2-§8 的每一点都能对应到具体任务——架构复用（任务3/5）、数据模型（任务1）、后端组件清单（任务2-8）、API（任务6）、前端交互（任务10-12）、测试计划（任务2/3/5/7 均含测试）、"不做的事情"未在任何任务中实现。
- **占位符扫描**：全部步骤均含完整可运行代码，无 TODO/"后续实现"/"类似任务N"。
- **类型一致性**：`pushBest(PtSubscriptionPlus, int, List<TorrentInfo>)` 在任务3定义、任务5/7调用处签名一致；`SupplementResult(boolean pushed, int candidateCount)` 在任务4定义、任务5/10/11 使用处字段名一致；`SearchSupplementService.supplement(Integer, int, String)` 与 `searchAcrossIndexers(String)` 方法名在任务5定义后任务7/11/12 引用处保持一致；前端 `openSeasonSearch`/`openEpisodeSearch`/`confirmSearch`/`toggleAutoSearch` 在任务11定义、任务12模板引用处名称一致。
