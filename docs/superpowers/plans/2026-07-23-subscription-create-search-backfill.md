# 建订阅时补搜历史资源实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 新建订阅（存在缺失集）时，在事务提交后自动异步发起一次历史资源补搜——剧集先试整季包，季包搜不到再对仍缺失的集逐一兜底；电影只搜一次。不阻塞建订阅接口响应，不新增数据库字段，不新增前端开关。

**架构：** 复用已有原子能力 `SearchSupplementService.supplement(subId, episode, keyword)`。新增编排方法 `SearchSupplementService.supplementOnCreate(subId)` 决定"搜哪些目标、按什么顺序"（同步、可单测）；新建瘦调度类 `SubscriptionSearchOnCreateTrigger`（`@Component`，持有 `virtualScheduledExecutor`）只负责把它丢到虚拟线程异步执行；`PtSubscriptionRestController.subscribe()` 在订阅落库后，若状态为 `ACTIVE` 就调用调度类触发一次。

**技术栈：** Spring Boot（Java 25 preview）、MyBatis-Plus、JUnit 5 + Mockito。

**前置阅读**（实现前务必看一遍，计划中的设计决策均来自此文档）：
- `docs/superpowers/specs/2026-07-23-subscription-create-search-backfill-design.md`

---

## 文件清单

| 文件 | 改动 | 职责 |
|---|---|---|
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java` | 改动 | 新增 `episodeService` 依赖 + `supplementOnCreate(Integer subId)` 编排方法 |
| `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementServiceTest.java` | 改动 | 更新构造函数调用（4 处）+ 新增 `supplementOnCreate` 用例 |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionSearchOnCreateTrigger.java` | 新建 | 异步调度壳子，不含业务逻辑，风格同 `pt/task/AutoSearchTask` |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtSubscriptionRestController.java` | 改动 | `subscribe()` 拿到订阅返回值后，`ACTIVE` 时触发异步补搜 |

不改动：`SubscriptionService`、`SubscriptionEngine`、`AutoSearchService`、`SubscriptionMatcher`、任何前端文件、数据库脚本。

---

### 任务 1：SearchSupplementService 新增 supplementOnCreate 编排方法

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementServiceTest.java`

- [ ] **步骤 1：更新测试文件的构造函数调用，并写失败的测试**

先在 `SearchSupplementServiceTest.java` 顶部新增 mock 字段（放在 `subscriptionService` mock 之后）：

```java
    @Mock private IPtSubscriptionPlusService subscriptionService;
    @Mock private IPtSubscriptionEpisodePlusService episodeService;
```

新增 import：

```java
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
```

把 `setUp()` 里的构造调用：

```java
        service = new SearchSupplementService(indexerService, torznabClient, subscriptionEngine, subscriptionService, matcher, capabilityCache, 10);
```

改为（在 `subscriptionService` 后插入 `episodeService`）：

```java
        service = new SearchSupplementService(indexerService, torznabClient, subscriptionEngine, subscriptionService, episodeService, matcher, capabilityCache, 10);
```

另外，`searchAcrossIndexers_并发数不超过配置上限`、`searchAcrossIndexers_并发受限但最终仍处理完所有索引器`、`配置并发数小于1_至少允许1个` 这三个用例里各自都有一行：

```java
                indexerService, torznabClient, subscriptionEngine, subscriptionService, matcher, capabilityCache, limit);
```

或（第三个用例里 `limit` 换成字面量 `0`）：

```java
                indexerService, torznabClient, subscriptionEngine, subscriptionService, matcher, capabilityCache, 0);
```

这三行的公共子串 `subscriptionService, matcher, capabilityCache` 在全文件（含 `setUp()`）一共出现 4 次，把这 4 处全部替换成 `subscriptionService, episodeService, matcher, capabilityCache`（即在 `subscriptionService,` 和 `matcher,` 之间插入 `episodeService,`），`limit`/`0` 等其余参数原样保留不变。

再新增一个小 helper（放在 `movieSub` 之后）：

```java
    private PtSubscriptionEpisodePlus episode(int number, String state) {
        PtSubscriptionEpisodePlus ep = new PtSubscriptionEpisodePlus();
        ep.setEpisode(number);
        ep.setState(state);
        return ep;
    }
```

最后在文件末尾（`supplement_剧集订阅_单集ID搜索带season和ep` 用例之后、结尾 `}` 之前）新增一段新用例：

```java

    // ---------- supplementOnCreate ----------

    @Test
    void supplementOnCreate_订阅不存在_不发起搜索() {
        when(subscriptionService.getById(99)).thenReturn(null);

        service.supplementOnCreate(99);

        verify(subscriptionEngine, never()).pushBest(any(), anyInt(), anyList());
    }

    @Test
    void supplementOnCreate_订阅非ACTIVE_不发起搜索() {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        sub.setStatus("COMPLETED");
        when(subscriptionService.getById(10)).thenReturn(sub);

        service.supplementOnCreate(10);

        verify(subscriptionEngine, never()).pushBest(any(), anyInt(), anyList());
        verify(episodeService, never()).listBySubscription(10);
    }

    @Test
    void supplementOnCreate_无缺失集_不发起搜索() {
        PtSubscriptionPlus sub = tvSub(10, 1, 2);
        when(subscriptionService.getById(10)).thenReturn(sub);
        when(episodeService.listBySubscription(10)).thenReturn(
                List.of(episode(1, "IN_LIBRARY"), episode(2, "IN_LIBRARY")));

        service.supplementOnCreate(10);

        verify(subscriptionEngine, never()).pushBest(any(), anyInt(), anyList());
    }

    @Test
    void supplementOnCreate_电影订阅_只搜一次() throws Exception {
        PtSubscriptionPlus movie = movieSub(20, "手机", "2003");
        when(subscriptionService.getById(20)).thenReturn(movie);
        when(episodeService.listBySubscription(20)).thenReturn(List.of(episode(0, "MISSING")));
        when(indexerService.listEnabled()).thenReturn(List.of());

        service.supplementOnCreate(20);

        verify(subscriptionEngine, times(1)).pushBest(eq(movie), eq(0), anyList());
    }

    @Test
    void supplementOnCreate_剧集季包命中_不逐集兜底() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 2);
        when(subscriptionService.getById(10)).thenReturn(sub);
        // 第一次调用（进方法时判断是否有缺失集）返回全缺，第二次调用（季包搜完后重新查）
        // 模拟季包命中把所有缺集占位为 IN_FLIGHT 的结果
        when(episodeService.listBySubscription(10)).thenReturn(
                List.of(episode(1, "MISSING"), episode(2, "MISSING")),
                List.of(episode(1, "IN_FLIGHT"), episode(2, "IN_FLIGHT")));
        when(indexerService.listEnabled()).thenReturn(List.of());

        service.supplementOnCreate(10);

        verify(subscriptionEngine, times(1)).pushBest(eq(sub), eq(SubscriptionMatcher.SEASON_PACK), anyList());
        verify(subscriptionEngine, never()).pushBest(eq(sub), eq(1), anyList());
        verify(subscriptionEngine, never()).pushBest(eq(sub), eq(2), anyList());
    }

    @Test
    void supplementOnCreate_季包未命中_逐集兜底剩余缺失集() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        List<PtSubscriptionEpisodePlus> episodes = List.of(
                episode(1, "MISSING"), episode(2, "MISSING"), episode(3, "IN_LIBRARY"));
        when(episodeService.listBySubscription(10)).thenReturn(episodes, episodes);
        when(indexerService.listEnabled()).thenReturn(List.of());

        service.supplementOnCreate(10);

        verify(subscriptionEngine, times(1)).pushBest(eq(sub), eq(SubscriptionMatcher.SEASON_PACK), anyList());
        verify(subscriptionEngine, times(1)).pushBest(eq(sub), eq(1), anyList());
        verify(subscriptionEngine, times(1)).pushBest(eq(sub), eq(2), anyList());
        verify(subscriptionEngine, never()).pushBest(eq(sub), eq(3), anyList());
    }

    @Test
    void supplementOnCreate_逐集兜底关键词按season和episode两位数格式拼() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 2, 5);
        when(subscriptionService.getById(10)).thenReturn(sub);
        List<PtSubscriptionEpisodePlus> episodes = List.of(episode(5, "MISSING"));
        when(episodeService.listBySubscription(10)).thenReturn(episodes, episodes);
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
        when(torznabClient.search(any(), anyString())).thenReturn(List.of());

        service.supplementOnCreate(10);

        verify(torznabClient).search(any(), eq("Some Show S02"));
        verify(torznabClient).search(any(), eq("Some Show S02E05"));
    }

    @Test
    void supplementOnCreate_季包补搜异常_仍继续逐集兜底() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 2);
        when(subscriptionService.getById(10)).thenReturn(sub);
        List<PtSubscriptionEpisodePlus> episodes = List.of(
                episode(1, "MISSING"), episode(2, "MISSING"));
        when(episodeService.listBySubscription(10)).thenReturn(episodes, episodes);
        when(indexerService.listEnabled()).thenReturn(List.of());
        when(subscriptionEngine.pushBest(eq(sub), eq(SubscriptionMatcher.SEASON_PACK), anyList()))
                .thenThrow(new RuntimeException("boom"));

        service.supplementOnCreate(10);

        verify(subscriptionEngine, times(1)).pushBest(eq(sub), eq(1), anyList());
        verify(subscriptionEngine, times(1)).pushBest(eq(sub), eq(2), anyList());
    }

    @Test
    void supplementOnCreate_某集补搜异常_不影响其余集继续搜索() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 3);
        when(subscriptionService.getById(10)).thenReturn(sub);
        List<PtSubscriptionEpisodePlus> episodes = List.of(
                episode(1, "MISSING"), episode(2, "MISSING"), episode(3, "IN_LIBRARY"));
        when(episodeService.listBySubscription(10)).thenReturn(episodes, episodes);
        when(indexerService.listEnabled()).thenReturn(List.of());
        when(subscriptionEngine.pushBest(eq(sub), eq(1), anyList())).thenThrow(new RuntimeException("boom"));

        service.supplementOnCreate(10);

        verify(subscriptionEngine, times(1)).pushBest(eq(sub), eq(2), anyList());
    }
```

- [ ] **步骤 2：运行测试确认失败**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=SearchSupplementServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：编译失败（构造函数参数数量不对——生产代码尚未加 `episodeService` 参数；`supplementOnCreate` 方法不存在）。

- [ ] **步骤 3：实现 supplementOnCreate**

打开 `SearchSupplementService.java`，在 import 区新增：

```java
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
```

把字段声明：

```java
    private final IPtIndexerPlusService indexerService;
    private final TorznabClient torznabClient;
    private final SubscriptionEngine subscriptionEngine;
    private final IPtSubscriptionPlusService subscriptionService;
    private final SubscriptionMatcher matcher;
    private final IndexerCapabilityCache capabilityCache;
```

改为：

```java
    private final IPtIndexerPlusService indexerService;
    private final TorznabClient torznabClient;
    private final SubscriptionEngine subscriptionEngine;
    private final IPtSubscriptionPlusService subscriptionService;
    private final IPtSubscriptionEpisodePlusService episodeService;
    private final SubscriptionMatcher matcher;
    private final IndexerCapabilityCache capabilityCache;
```

把构造函数：

```java
    public SearchSupplementService(IPtIndexerPlusService indexerService,
                                   TorznabClient torznabClient,
                                   SubscriptionEngine subscriptionEngine,
                                   IPtSubscriptionPlusService subscriptionService,
                                   SubscriptionMatcher matcher,
                                   IndexerCapabilityCache capabilityCache,
                                   @Value("${pt.search.max-concurrency:3}") int maxConcurrency) {
        this.indexerService = indexerService;
        this.torznabClient = torznabClient;
        this.subscriptionEngine = subscriptionEngine;
        this.subscriptionService = subscriptionService;
        this.matcher = matcher;
        this.capabilityCache = capabilityCache;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }
```

改为：

```java
    public SearchSupplementService(IPtIndexerPlusService indexerService,
                                   TorznabClient torznabClient,
                                   SubscriptionEngine subscriptionEngine,
                                   IPtSubscriptionPlusService subscriptionService,
                                   IPtSubscriptionEpisodePlusService episodeService,
                                   SubscriptionMatcher matcher,
                                   IndexerCapabilityCache capabilityCache,
                                   @Value("${pt.search.max-concurrency:3}") int maxConcurrency) {
        this.indexerService = indexerService;
        this.torznabClient = torznabClient;
        this.subscriptionEngine = subscriptionEngine;
        this.subscriptionService = subscriptionService;
        this.episodeService = episodeService;
        this.matcher = matcher;
        this.capabilityCache = capabilityCache;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }
```

最后在 `supplement(...)` 方法结束之后（`searchAcrossIndexers` 方法之前）插入新方法：

```java

    /**
     * 建订阅后一次性补搜历史资源：电影只搜一次；剧集先试整季包，季包搜不到再对仍缺失的
     * 集逐一兜底。供 {@link SubscriptionSearchOnCreateTrigger} 异步调用——顶层不抛异常，
     * 季包搜索与每一集的搜索都各自 try/catch，任一目标失败不影响其余目标继续搜索。
     */
    public void supplementOnCreate(Integer subId) {
        PtSubscriptionPlus sub = subscriptionService.getById(subId);
        if (sub == null || !SubscriptionService.STATUS_ACTIVE.equals(sub.getStatus())) {
            return;
        }
        List<PtSubscriptionEpisodePlus> episodes = episodeService.listBySubscription(subId);
        boolean hasMissing = episodes.stream()
                .anyMatch(ep -> SubscriptionService.STATE_MISSING.equals(ep.getState()));
        if (!hasMissing) {
            return;
        }

        boolean movie = SubscriptionService.TYPE_MOVIE.equalsIgnoreCase(sub.getMediaType());
        if (movie) {
            supplement(subId, 0, sub.getTitle());
            return;
        }

        try {
            supplement(subId, SubscriptionMatcher.SEASON_PACK, sub.getTitle() + " S" + pad(sub.getSeason()));
        } catch (Exception e) {
            log.warn("订阅[{}] 建订阅补搜整季包失败：{}", subId, e.getMessage());
        }

        List<PtSubscriptionEpisodePlus> remaining = episodeService.listBySubscription(subId);
        for (PtSubscriptionEpisodePlus ep : remaining) {
            if (!SubscriptionService.STATE_MISSING.equals(ep.getState())) {
                continue;
            }
            try {
                String keyword = sub.getTitle() + " S" + pad(sub.getSeason()) + "E" + pad(ep.getEpisode());
                supplement(subId, ep.getEpisode(), keyword);
            } catch (Exception e) {
                log.warn("订阅[{}] 建订阅补搜第{}集失败：{}", subId, ep.getEpisode(), e.getMessage());
            }
        }
    }
```

- [ ] **步骤 4：运行测试确认通过**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=SearchSupplementServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：全部 PASS（含之前已有的全部用例，`supplement()` 本身逻辑未改动，只是新增了一个调用它的编排方法）。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementServiceTest.java
git commit -m "feat(pt): SearchSupplementService新增建订阅补搜编排方法"
```

---

### 任务 2：新建 SubscriptionSearchOnCreateTrigger 调度壳子

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionSearchOnCreateTrigger.java`

不写单测：与 `pt/task/AutoSearchTask.java`、`pt/task/RssPollTask.java` 等同类调度壳子一致——`SpringUtils.getBean("virtualScheduledExecutor")` 是字段初始化时的静态调用，脱离 Spring 容器无法实例化，本项目里这类"纯调度、不含业务判断"的类历来不单测，业务逻辑已经在任务 1 的 `supplementOnCreate` 里覆盖。

- [ ] **步骤 1：创建文件**

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.common.utils.spring.SpringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 建订阅后一次性补搜历史资源的调度壳子：只负责把
 * {@link SearchSupplementService#supplementOnCreate(Integer)} 丢到虚拟线程异步执行，
 * 不含任何业务判断逻辑（风格同 {@code pt.task.AutoSearchTask}）。
 *
 * @author Jack
 */
@Slf4j
@Component
public class SubscriptionSearchOnCreateTrigger {

    @Autowired
    private SearchSupplementService searchSupplementService;

    private final TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor");

    /**
     * 异步发起一次建订阅补搜，立即返回，不等待搜索完成。
     */
    public void triggerAsync(Integer subId) {
        scheduler.schedule(() -> {
            try {
                searchSupplementService.supplementOnCreate(subId);
            } catch (Exception e) {
                log.warn("订阅[{}] 建订阅后补搜历史资源失败：{}", subId, e.getMessage());
            }
        }, Instant.now());
    }
}
```

- [ ] **步骤 2：编译确认**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am compile
```
预期：无编译错误。

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionSearchOnCreateTrigger.java
git commit -m "feat(pt): 新增建订阅补搜异步调度壳子SubscriptionSearchOnCreateTrigger"
```

---

### 任务 3：Controller 接入——建订阅成功后触发异步补搜

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtSubscriptionRestController.java:37-104`

- [ ] **步骤 1：修改 subscribe() 方法**

在类字段区（`searchSupplementService` 字段之后）新增：

```java
    @Autowired
    private SubscriptionSearchOnCreateTrigger searchOnCreateTrigger;
```

把 `subscribe()` 方法：

```java
    @PostMapping("/subscribe")
    public Result<Void> subscribe(@RequestBody SubscribeRequest request) {
        try {
            subscriptionBiz.subscribe(request);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            // 唯一约束冲突（同一作品同一季重复订阅）会在这里被兜住
            return Result.error("建立订阅失败，该作品的这一季可能已订阅过：" + e.getMessage());
        }
    }
```

改为：

```java
    @PostMapping("/subscribe")
    public Result<Void> subscribe(@RequestBody SubscribeRequest request) {
        try {
            PtSubscriptionPlus sub = subscriptionBiz.subscribe(request);
            if (SubscriptionService.STATUS_ACTIVE.equals(sub.getStatus())) {
                searchOnCreateTrigger.triggerAsync(sub.getId());
            }
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            // 唯一约束冲突（同一作品同一季重复订阅）会在这里被兜住
            return Result.error("建立订阅失败，该作品的这一季可能已订阅过：" + e.getMessage());
        }
    }
```

`PtSubscriptionPlus`、`SubscriptionService` 已在文件顶部 import（第 9、14 行），无需新增 import；只需新增 `SubscriptionSearchOnCreateTrigger` 的 import：

```java
import com.ruoyi.openliststrm.pt.subscription.SubscriptionSearchOnCreateTrigger;
```

- [ ] **步骤 2：编译确认**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am compile
```
预期：无编译错误。

说明：本项目 REST controller 目前没有对应的单元测试基础设施（`PtSubscriptionRestController` 等 controller 类没有测试文件），这一处改动只做编译检查，不新增 controller 测试，与现状保持一致。`subscribe()` 触发异步补搜的实际行为已经通过任务 1 的 `supplementOnCreate` 单测 + 任务 4 的启动验证间接覆盖。

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtSubscriptionRestController.java
git commit -m "feat(pt): 建订阅成功后自动触发一次历史资源补搜"
```

---

### 任务 4：全量验证

**背景**：任务 2 新增了一个 `@Component`（`SubscriptionSearchOnCreateTrigger`），按 `AGENTS.md` NOTES 的规则，新增 `@Component`/`@Service` bean 后必须做真实启动验证——单元测试用 mock 直接 `new` 目标类会绕过 Spring 装配，测试全绿不代表能启动；`SpringUtils.getBean("virtualScheduledExecutor")` 这类字段初始化在真实容器里执行的时机（bean 实例化阶段）和单测里完全不同，只有真实启动才能验证 `virtualScheduledExecutor` 这个 bean 此时确实已经可用。

- [ ] **步骤 1：全量单测**

```bash
mvn -pl ruoyi-openliststrm -am test
```
预期：`BUILD SUCCESS`，`Tests run` 汇总里 `Failures: 0, Errors: 0`。

- [ ] **步骤 2：完整打包（含 `--enable-preview` 编译检查）**

```bash
mvn clean package -DskipTests
```
预期：`BUILD SUCCESS`，生成 `ruoyi-admin/target/ruoyi-admin.jar`。

- [ ] **步骤 3：启动验证**

```bash
docker compose up -d --build --no-deps backend
```
确认容器 `restarts=0`（`docker ps` 看 `osr-backend` 状态，或 `docker compose ps`），且 `POST /api/openliststrm/pt-subscriptions/subscribe` 等接口能正常响应（可用现有前端页面新建一次订阅验证，或直接用 curl/Postman 打一次请求）。若容器反复重启，按 `AGENTS.md` NOTES 的排查方法：

```bash
docker update --restart=no osr-backend && docker restart osr-backend
docker cp osr-backend:/data/logs ./tmp
```

再看 `./tmp/sys-error.log`（异常写在这里，不在 docker stdout）。

若当前执行环境不支持 `docker compose`（例如纯代码沙箱、无 Docker），需在交付时明确告知用户"仅完成单测与打包验证，未做容器启动验证"，并请用户在自己的环境里补做这一步。
