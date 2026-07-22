# PT 订阅 计划5：调度与通知 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让整套 PT 订阅自动运转起来——定时拉 RSS 推送、定时追踪下载状态、定时与 Emby 对账，并在关键节点发 Telegram 通知。

**架构：** 三个调度器，均沿用 `upload/UploadTaskManager` 的既有写法。每个调度器的**编排逻辑抽成可直接调用的方法**（可单测），定时器只是薄包装（不测定时器本身）。回映下载器种子到下载记录靠 `tracking_tag`，所以先给 `DownloaderTorrent` 补 tags 解析。

**技术栈：** Java 25 (Spring Boot 4.0.6) / MyBatis-Plus / JUnit 5 + Mockito

**上游规格：** `docs/superpowers/specs/2026-07-21-pt-subscription-download-design.md`（**必读附录 A–H**）

**执行约定：** 直接在 `dev` 分支提交。

> ⚠️ **本计划的三个调度器都是启动时装配 + 执行的 Spring bean**（`@Component` + `@EventListener(ApplicationReadyEvent.class)`）。单元测试用 `new Xxx(...)` 绕过 Spring 装配，**测试全绿不代表能启动**。见 AGENTS.md 新增的「bean 装配启动验证约定」。**任务 6 的 docker 部署验证不可跳过**——上一阶段就是因为跳过它，`SubscriptionEngine` 注入非 bean 的 `MediaParser` 导致启动崩溃却没在测试里暴露。

**本计划完成后**，整个 PT 订阅功能端到端可用：订阅 → 自动下载 → 上传网盘（现有链路）→ Emby 对账 → 集数推进。

---

## 前置：已就绪的成果

| 已有 | 位置 | 本计划怎么用 |
|---|---|---|
| `TorznabClient.fetch(indexer)` | `pt/indexer/` | 返回 `List<TorrentInfo>`，抛 IOException |
| `SubscriptionEngine.process(torrents)` | `pt/subscription/` | 返回推送数；一次完整决策与推送 |
| `SubscriptionService.refresh(subId)` | `pt/subscription/` | 对账刷新一个订阅 |
| `IDownloaderClient.listByTag(config, tag)` | `pt/downloader/` | 返回 `List<DownloaderTorrent>`，抛 IOException |
| `DownloaderTorrent` | `pt/downloader/model/` | 有 `getHash/getName/getProgress/isCompleted/getSavePath/getRawState`，**没有 tags**（本计划补） |
| `DownloaderClientFactory.get(config)` | `pt/downloader/` | 按 type 取客户端 |
| `TgHelper.sendMsg(String)` | `helper/` | **token/userid 未配置时静默返回，不抛异常**，可放心调 |
| `IPtIndexerPlusService` / `IPtDownloaderPlusService` / `IPtSubscriptionPlusService` / `IPtDownloadRecordPlusService` / `IPtSubscriptionEpisodePlusService` | `mybatisplus/service/` | 各表 CRUD |
| `UploadTaskManager` | `upload/` | 调度器写法的参照样板 |

**调度器写法（逐字照搬 `UploadTaskManager`）：**

```java
private final TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor");

@EventListener(ApplicationReadyEvent.class)
public void start() {
    ThreadTraceIdUtil.initTraceId();
    scheduler.scheduleAtFixedRate(this::poll, Instant.now().plusSeconds(启动延迟), Duration.ofSeconds(周期));
    log.info("XxxTask started");
}

@PreDestroy
public void stop() {
    log.info("XxxTask stopped");
    MDC.clear();
}

private void poll() {
    try {
        // 调用可测的编排方法
    } catch (Exception e) {
        log.error("poll error", e);
    }
}
```

**继承的硬约束：**
1. 测试命令带 `-Dsurefire.failIfNoSpecifiedTests=false`；多测试类用逗号分隔。
2. `Date.now()` 等在业务代码里正常用（不是工作流脚本环境）。
3. 判断电影只看 `media_type`。
4. 状态字符串常量与前面计划一致：集状态 `MISSING`/`IN_FLIGHT`/`IN_LIBRARY`，记录状态 `PUSHED`/`DOWNLOADING`/`COMPLETED`/`FAILED`，订阅状态 `ACTIVE`/`COMPLETED`/`PAUSED`。

**规格附录里直接决定本计划实现的：**
- **附录 B**：下载 `FAILED` 后集回退 `MISSING`，该 guid 不再重试（靠别的种子恢复该集）。
- **附录 C**：`IN_FLIGHT` 超过 N 小时且 `pushed_time` 过期的记录，兜底回退 `MISSING`（防失联种子永久占位）。
- **附录 A**：季包记录 `episode = -1`，它 `FAILED` 时它指向的所有集都回退 `MISSING`。
- **附录 G**：多索引器轮询已在 `SubscriptionEngine` 内用 CAS 占位处理，本计划的 `RssPollTask` 只要串行遍历索引器即可（不要为每个索引器开线程）。

---

## 文件结构

| 文件 | 职责 |
|---|---|
| 修改 `pt/downloader/model/DownloaderTorrent.java` | 加 `tags` 字段 |
| 修改 `pt/downloader/QbittorrentClient.java` | 解析 qB 返回的 tags |
| 新增 `pt/task/DownloadTrackService.java` | 下载追踪的可测编排逻辑 |
| 新增 `pt/task/DownloadTrackTask.java` | 30 秒调度器（薄包装） |
| 新增 `pt/task/RssPollService.java` | RSS 轮询的可测编排逻辑 |
| 新增 `pt/task/RssPollTask.java` | 轮询调度器（薄包装） |
| 新增 `pt/task/LibrarySyncTask.java` | 10 分钟对账调度器 |

> 放在新的 `pt/task` 子包，与既有 `task/`（`OpenListStrmTask`）区分开。

---

## 任务 1：DownloaderTorrent 补 tags 解析

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/downloader/model/DownloaderTorrent.java`
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/downloader/QbittorrentClient.java`
- 修改：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/downloader/QbittorrentClientTest.java`

> **为什么需要：** 下载追踪要把下载器里的种子回映到 `pt_download_record`。推送时给种子打了两个标签：公共标签（`pt_downloader.tag`，如 `osr-pt`）和唯一标签（`osr-pt-{guidHash前16位}`）。追踪时按公共标签拉全量，再用唯一标签精确定位是哪条下载记录。但 `DownloaderTorrent` 现在没有 tags 字段，qB 返回的 `tags`（逗号分隔字符串）被丢掉了。本任务补上。

- [ ] **步骤 1：给 DownloaderTorrent 加字段**

在 `DownloaderTorrent.java` 的 `savePath` 字段后加：

```java
    /** 种子的标签，逗号分隔（qB 的 tags 字段原样保留），用于回映到下载记录 */
    private String tags;
```

- [ ] **步骤 2：编写失败的测试**

在 `QbittorrentClientTest.java` 里加一个用例（放在 `listByTag_解析JSON为种子快照` 附近，仿其结构）：

```java
    @Test
    void listByTag_解析出tags字段() throws Exception {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("""
                [{"hash":"AABBCC","name":"Show.S01E01","progress":1.0,"state":"uploading",
                  "save_path":"/data","tags":"osr-pt,osr-pt-0123456789abcdef"}]
                """));

        List<DownloaderTorrent> list = client.listByTag(config(20), "osr-pt");

        assertEquals("osr-pt,osr-pt-0123456789abcdef", list.get(0).getTags());
    }
```

- [ ] **步骤 3：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=QbittorrentClientTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：新用例失败（`getTags()` 返回 null）

- [ ] **步骤 4：解析 tags**

在 `QbittorrentClient.listByTag` 组装 `DownloaderTorrent` 的循环里，`torrent.setSavePath(...)` 之后加一行：

```java
            torrent.setTags(item.getString("tags"));
```

- [ ] **步骤 5：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=QbittorrentClientTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：全绿（原有用例 + 新用例）

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/downloader/ ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/downloader/QbittorrentClientTest.java
git commit -m "feat(pt): DownloaderTorrent 补 tags 字段，供下载追踪回映记录"
```

---
## 任务 2：下载追踪服务

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/DownloadTrackService.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/task/DownloadTrackServiceTest.java`

> **这是本计划状态流转最绕的部分**，所以把编排逻辑抽成可直接调用的方法，密集单测。
>
> **输入**：一个下载器 + 它按公共标签拉回来的 `List<DownloaderTorrent>`。
> **要做**：把每条 `PUSHED`/`DOWNLOADING` 的下载记录，按 `tracking_tag` 回映到下载器里的种子，据此推进状态。
>
> **回映**：`DownloaderTorrent.tags`（逗号分隔）里包含某条记录的 `tracking_tag` 即匹配。
>
> **四条状态规则：**
>
> 1. **找到且已完成** → 记录置 `COMPLETED` + `completedTime`，发「下载完成」通知。**集状态不动**（仍是 `IN_FLIGHT`）——「下载完成」≠「已入库」，入库要 Emby 确认，由 `LibrarySyncTask` 推进。
> 2. **找到但未完成** → 记录置 `DOWNLOADING`。
> 3. **找不到对应种子，且推送已超过宽限期**（用户在 qB 删了种，或元数据始终没解析出来）→ 记录置 `FAILED`，**该记录关联的所有集回退 `MISSING` 并清 `download_id`**（附录 B：这个 guid 不再重试，靠别的种子补）。宽限期兼作附录 C 的「僵尸种子超时兜底」。
> 4. **找不到但推送还没超过宽限期** → 本轮跳过（刚推送的种子 qB 可能还在解析元数据，别急着判失败）。
>
> **集回退用 `download_id == record.id` 定位**（占位时 `SubscriptionEngine` 设了 download_id），这样普通集（1 条）和季包（多条 `episode=-1`）统一处理，不用区分。

- [ ] **步骤 1：编写失败的测试**

创建 `DownloadTrackServiceTest.java`：

```java
package com.ruoyi.openliststrm.pt.task;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloadRecordPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DownloadTrackServiceTest {

    @Mock private IPtDownloadRecordPlusService recordService;
    @Mock private IPtSubscriptionEpisodePlusService episodeService;

    private DownloadTrackService service() {
        return new DownloadTrackService(recordService, episodeService);
    }

    private PtDownloaderPlus downloader() {
        PtDownloaderPlus d = new PtDownloaderPlus();
        d.setId(1);
        d.setTag("osr-pt");
        return d;
    }

    private PtDownloadRecordPlus record(int id, int episode, String tag, String state, long pushedAgoMs) {
        PtDownloadRecordPlus r = new PtDownloadRecordPlus();
        r.setId(id);
        r.setSubId(10);
        r.setEpisode(episode);
        r.setTrackingTag(tag);
        r.setState(state);
        r.setTitle("Some.Show.S01E0" + episode);
        r.setPushedTime(new Date(System.currentTimeMillis() - pushedAgoMs));
        return r;
    }

    private DownloaderTorrent torrent(String tags, double progress) {
        DownloaderTorrent t = new DownloaderTorrent();
        t.setHash("h");
        t.setName("n");
        t.setProgress(progress);
        t.setTags(tags);
        return t;
    }

    @Test
    void 完成的种子_记录置完成_集状态不动() {
        PtDownloadRecordPlus r = record(100, 2, "osr-pt-aaa", "DOWNLOADING", 60_000);
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(r));

        service().track(downloader(), List.of(torrent("osr-pt,osr-pt-aaa", 1.0)));

        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).updateById(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getState());
        // 集状态不该被改（等 Emby 对账）
        verify(episodeService, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void 未完成的种子_记录置下载中() {
        PtDownloadRecordPlus r = record(100, 2, "osr-pt-aaa", "PUSHED", 60_000);
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(r));

        service().track(downloader(), List.of(torrent("osr-pt,osr-pt-aaa", 0.35)));

        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).updateById(captor.capture());
        assertEquals("DOWNLOADING", captor.getValue().getState());
    }

    @Test
    void 找不到种子且推送已超宽限期_记录置失败且集回退缺失() {
        // 宽限期 10 分钟，这条推送了 20 分钟还找不到
        PtDownloadRecordPlus r = record(100, 2, "osr-pt-aaa", "DOWNLOADING", 20 * 60_000);
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(r));
        when(episodeService.list(any(Wrapper.class))).thenReturn(List.of(episodeRow(500)));

        service().track(downloader(), List.of(torrent("osr-pt,osr-pt-other", 0.5)));

        ArgumentCaptor<PtDownloadRecordPlus> captor = ArgumentCaptor.forClass(PtDownloadRecordPlus.class);
        verify(recordService).updateById(captor.capture());
        assertEquals("FAILED", captor.getValue().getState());
        // 关联集回退 MISSING
        verify(episodeService).update(any(), any(Wrapper.class));
    }

    @Test
    void 找不到种子但推送未超宽限期_本轮跳过() {
        // 刚推送 1 分钟，qB 可能还在解析元数据
        PtDownloadRecordPlus r = record(100, 2, "osr-pt-aaa", "PUSHED", 60_000);
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(r));

        service().track(downloader(), List.of(torrent("osr-pt,osr-pt-other", 0.5)));

        verify(recordService, never()).updateById(any());
        verify(episodeService, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void 季包失败_关联的多个集全部回退() {
        PtDownloadRecordPlus r = record(100, -1, "osr-pt-pack", "DOWNLOADING", 20 * 60_000);
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of(r));
        when(episodeService.list(any(Wrapper.class))).thenReturn(List.of(episodeRow(501), episodeRow(502)));

        service().track(downloader(), List.of(torrent("osr-pt", 0.5)));

        // 两个集都回退
        verify(episodeService, org.mockito.Mockito.times(2)).update(any(), any(Wrapper.class));
    }

    @Test
    void 已完成的记录不重复处理() {
        // list 只查 PUSHED/DOWNLOADING，COMPLETED 的不在结果里——用空列表模拟
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of());

        service().track(downloader(), List.of(torrent("osr-pt,osr-pt-done", 1.0)));

        verify(recordService, never()).updateById(any());
    }

    @Test
    void 无在途记录_不做任何事() {
        when(recordService.list(any(Wrapper.class))).thenReturn(List.of());

        service().track(downloader(), List.of());

        verify(recordService, never()).updateById(any());
    }

    private PtSubscriptionEpisodePlus episodeRow(int id) {
        PtSubscriptionEpisodePlus ep = new PtSubscriptionEpisodePlus();
        ep.setId(id);
        ep.setState("IN_FLIGHT");
        return ep;
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=DownloadTrackServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `DownloadTrackService` 找不到

- [ ] **步骤 3：编写实现**

创建 `pt/task/DownloadTrackService.java`：

```java
package com.ruoyi.openliststrm.pt.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.helper.TgHelper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloadRecordPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 下载追踪的编排逻辑：把下载器里的种子回映到下载记录并推进状态。
 * 抽成独立 Service 是为了脱离定时器单测。
 *
 * @author Jack
 */
@Slf4j
@Service
public class DownloadTrackService {

    private static final String STATE_PUSHED = "PUSHED";
    private static final String STATE_DOWNLOADING = "DOWNLOADING";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_FAILED = "FAILED";
    private static final String EP_MISSING = "MISSING";
    private static final String EP_IN_FLIGHT = "IN_FLIGHT";

    /** 推送后找不到对应种子的宽限期：超过它才判失败（qB 解析磁力元数据需要时间） */
    private static final long GRACE_MILLIS = 10 * 60 * 1000L;

    private final IPtDownloadRecordPlusService recordService;
    private final IPtSubscriptionEpisodePlusService episodeService;

    public DownloadTrackService(IPtDownloadRecordPlusService recordService,
                                IPtSubscriptionEpisodePlusService episodeService) {
        this.recordService = recordService;
        this.episodeService = episodeService;
    }

    /**
     * 追踪一个下载器：拉回来的种子已按公共标签过滤过，这里只做状态推进。
     */
    public void track(PtDownloaderPlus downloader, List<DownloaderTorrentView> torrents) {
        List<PtDownloadRecordPlus> active = recordService.list(
                new QueryWrapper<PtDownloadRecordPlus>()
                        .eq("downloader_id", downloader.getId())
                        .in("state", STATE_PUSHED, STATE_DOWNLOADING));
        if (active.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PtDownloadRecordPlus record : active) {
            DownloaderTorrentView matched = findByTag(torrents, record.getTrackingTag());
            if (matched == null) {
                handleMissing(record, now);
            } else if (matched.isCompleted()) {
                complete(record);
            } else if (!STATE_DOWNLOADING.equals(record.getState())) {
                record.setState(STATE_DOWNLOADING);
                recordService.updateById(record);
            }
        }
    }

    private DownloaderTorrentView findByTag(List<DownloaderTorrentView> torrents, String trackingTag) {
        if (StringUtils.isBlank(trackingTag)) {
            return null;
        }
        for (DownloaderTorrentView torrent : torrents) {
            if (StringUtils.isBlank(torrent.getTags())) {
                continue;
            }
            for (String tag : torrent.getTags().split(",")) {
                if (trackingTag.equals(tag.trim())) {
                    return torrent;
                }
            }
        }
        return null;
    }

    /**
     * 发通知但绝不让通知失败影响主流程。TgHelper 未配置时本就静默返回；
     * 单测环境下 SpringUtils.getBean 会抛异常，这里一并兜住。
     */
    private void notifySafely(String msg) {
        try {
            TgHelper.sendMsg(msg);
        } catch (Exception e) {
            log.debug("发送通知失败（不影响主流程）：{}", e.getMessage());
        }
    }

    private void complete(PtDownloadRecordPlus record) {
        if (STATE_COMPLETED.equals(record.getState())) {
            return;
        }
        record.setState(STATE_COMPLETED);
        record.setCompletedTime(new Date());
        recordService.updateById(record);
        notifySafely("✅ 下载完成：" + record.getTitle());
        log.info("下载记录[{}] 已完成：{}", record.getId(), record.getTitle());
        // 注意：集状态不动，仍是 IN_FLIGHT，等 LibrarySyncTask 通过 Emby 确认后转 IN_LIBRARY
    }

    private void handleMissing(PtDownloadRecordPlus record, long now) {
        long age = record.getPushedTime() == null ? Long.MAX_VALUE : now - record.getPushedTime().getTime();
        if (age < GRACE_MILLIS) {
            // 刚推送不久，qB 可能还在解析元数据，本轮先不判失败
            return;
        }
        record.setState(STATE_FAILED);
        record.setFailReason("下载器中已找不到该种子（可能被删除或元数据解析失败）");
        recordService.updateById(record);

        // 该记录关联的所有集回退 MISSING 并清 download_id（普通集1条、季包多条，统一处理）
        List<PtSubscriptionEpisodePlus> episodes = episodeService.list(
                new QueryWrapper<PtSubscriptionEpisodePlus>()
                        .eq("download_id", record.getId())
                        .eq("state", EP_IN_FLIGHT));
        for (PtSubscriptionEpisodePlus episode : episodes) {
            PtSubscriptionEpisodePlus set = new PtSubscriptionEpisodePlus();
            set.setState(EP_MISSING);
            set.setDownloadId(null);
            episodeService.update(set, new UpdateWrapper<PtSubscriptionEpisodePlus>()
                    .eq("id", episode.getId())
                    .eq("state", EP_IN_FLIGHT));
        }
        notifySafely("❌ 下载失败：" + record.getTitle() + "，已释放待下轮重新匹配");
        log.warn("下载记录[{}] 失败，{} 个集回退缺失：{}", record.getId(), episodes.size(), record.getTitle());
    }
}
```

> **注意 `DownloaderTorrentView`**：测试里用的是 `com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent`，但实现里为了让 `track` 方法可测且不依赖真实客户端，把入参类型直接用 `DownloaderTorrent`。**把上面实现代码里所有 `DownloaderTorrentView` 替换为 `DownloaderTorrent`，并 import `com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent`**（测试步骤 1 的 import 也是这个类型）。`DownloaderTorrentView` 只是本文档里的占位名，不要真的创建这个类。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=DownloadTrackServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 7, Failures: 0, Errors: 0

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/DownloadTrackService.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/task/DownloadTrackServiceTest.java
git commit -m "feat(pt): 新增下载追踪服务，含完成/失败状态推进与季包集回退"
```

---

## 任务 3：下载追踪调度器

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/DownloadTrackTask.java`

> 薄包装：30 秒轮询所有启用的下载器，各自 `listByTag` 拉全量后交给 `DownloadTrackService.track`。无单元测试（定时器 + 网络，逻辑已在 service 测过）。

- [ ] **步骤 1：编写调度器**

创建 `pt/task/DownloadTrackTask.java`：

```java
package com.ruoyi.openliststrm.pt.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.common.utils.ThreadTraceIdUtil;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloaderPlusService;
import com.ruoyi.openliststrm.pt.downloader.DownloaderClientFactory;
import com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent;
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
import java.util.List;

/**
 * 每 30 秒轮询下载器，把种子状态回映到下载记录。
 *
 * @author Jack
 */
@Slf4j
@Component
public class DownloadTrackTask {

    @Autowired
    private IPtDownloaderPlusService downloaderService;
    @Autowired
    private DownloaderClientFactory downloaderClientFactory;
    @Autowired
    private DownloadTrackService trackService;

    private final TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor");

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ThreadTraceIdUtil.initTraceId();
        scheduler.scheduleAtFixedRate(this::poll, Instant.now().plusSeconds(30), Duration.ofSeconds(30));
        log.info("DownloadTrackTask started");
    }

    @PreDestroy
    public void stop() {
        log.info("DownloadTrackTask stopped");
        MDC.clear();
    }

    private void poll() {
        try {
            List<PtDownloaderPlus> downloaders = downloaderService.list(
                    new QueryWrapper<PtDownloaderPlus>().eq("enabled", "1"));
            for (PtDownloaderPlus downloader : downloaders) {
                try {
                    List<DownloaderTorrent> torrents = downloaderClientFactory.get(downloader)
                            .listByTag(downloader, downloader.getTag());
                    trackService.track(downloader, torrents);
                } catch (Exception e) {
                    log.warn("追踪下载器[{}]失败：{}", downloader.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("DownloadTrackTask poll error", e);
        }
    }
}
```

- [ ] **步骤 2：编译验证**

运行：`mvn -pl ruoyi-openliststrm -am test`

预期：BUILD SUCCESS，既有测试全绿

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/DownloadTrackTask.java
git commit -m "feat(pt): 新增下载追踪调度器（30秒轮询下载器）"
```

---
## 任务 4：RSS 轮询服务

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/RssPollService.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/task/RssPollServiceTest.java`

> **编排逻辑**：遍历启用的索引器，对每个「到期」的（`now - last_poll_time >= poll_interval`）拉一次 RSS，汇总所有种子交给 `SubscriptionEngine.process`。索引器连续失败 3 次发告警。
>
> **附录 G**：多索引器串行遍历即可，不要为每个索引器开线程——`SubscriptionEngine` 内部已用 CAS 占位防并发，但那是为了防「不同轮次/不同实例」的并发；同一轮内串行处理最简单也最安全。

- [ ] **步骤 1：编写失败的测试**

创建 `RssPollServiceTest.java`：

```java
package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtIndexerPlusService;
import com.ruoyi.openliststrm.pt.indexer.TorznabClient;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RssPollServiceTest {

    @Mock private IPtIndexerPlusService indexerService;
    @Mock private TorznabClient torznabClient;
    @Mock private SubscriptionEngine subscriptionEngine;

    private RssPollService service() {
        return new RssPollService(indexerService, torznabClient, subscriptionEngine);
    }

    private PtIndexerPlus indexer(int id, Integer pollInterval, java.util.Date lastPoll, int failCount) {
        PtIndexerPlus i = new PtIndexerPlus();
        i.setId(id);
        i.setName("idx-" + id);
        i.setPollInterval(pollInterval);
        i.setLastPollTime(lastPoll);
        i.setFailCount(failCount);
        i.setEnabled("1");
        return i;
    }

    private TorrentInfo torrent(String title) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle(title);
        return t;
    }

    @Test
    void 从未轮询过的索引器_视为到期_会拉取() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1, 600, null, 0)));
        when(torznabClient.fetch(any())).thenReturn(List.of(torrent("t1")));

        service().poll();

        verify(torznabClient).fetch(any());
        verify(subscriptionEngine).process(anyList());
    }

    @Test
    void 未到轮询周期的索引器_跳过不拉取() throws Exception {
        // 刚轮询过（1 秒前），周期 600 秒
        when(indexerService.listEnabled()).thenReturn(
                List.of(indexer(1, 600, new java.util.Date(System.currentTimeMillis() - 1000), 0)));

        service().poll();

        verify(torznabClient, never()).fetch(any());
        verify(subscriptionEngine, never()).process(anyList());
    }

    @Test
    void 已过轮询周期_到期拉取() throws Exception {
        // 上次 700 秒前，周期 600 秒 → 到期
        when(indexerService.listEnabled()).thenReturn(
                List.of(indexer(1, 600, new java.util.Date(System.currentTimeMillis() - 700_000), 0)));
        when(torznabClient.fetch(any())).thenReturn(List.of(torrent("t1")));

        service().poll();

        verify(torznabClient).fetch(any());
    }

    @Test
    void 拉取成功_更新索引器状态为OK并清零失败计数() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1, 600, null, 2)));
        when(torznabClient.fetch(any())).thenReturn(List.of(torrent("t1")));

        service().poll();

        ArgumentCaptor<PtIndexerPlus> captor = ArgumentCaptor.forClass(PtIndexerPlus.class);
        verify(indexerService).updateById(captor.capture());
        assertEquals("OK", captor.getValue().getLastStatus());
        assertEquals(0, captor.getValue().getFailCount());
    }

    @Test
    void 拉取失败_累加失败计数并记录错误() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(indexer(1, 600, null, 0)));
        when(torznabClient.fetch(any())).thenThrow(new IOException("connection refused"));

        service().poll();

        ArgumentCaptor<PtIndexerPlus> captor = ArgumentCaptor.forClass(PtIndexerPlus.class);
        verify(indexerService).updateById(captor.capture());
        assertEquals(1, captor.getValue().getFailCount());
        // 失败时不调用引擎
        verify(subscriptionEngine, never()).process(anyList());
    }

    @Test
    void 多个索引器的种子被汇总后一次性交给引擎() throws Exception {
        when(indexerService.listEnabled()).thenReturn(List.of(
                indexer(1, 600, null, 0), indexer(2, 600, null, 0)));
        when(torznabClient.fetch(any())).thenReturn(List.of(torrent("a")), List.of(torrent("b")));

        service().poll();

        ArgumentCaptor<List<TorrentInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(subscriptionEngine).process(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void 无到期索引器_不调用引擎() throws Exception {
        when(indexerService.listEnabled()).thenReturn(
                List.of(indexer(1, 600, new java.util.Date(), 0)));

        service().poll();

        verify(subscriptionEngine, never()).process(anyList());
    }

    @Test
    void 无启用索引器_不做任何事() {
        when(indexerService.listEnabled()).thenReturn(List.of());

        service().poll();

        verify(subscriptionEngine, never()).process(anyList());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=RssPollServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `RssPollService` 找不到

- [ ] **步骤 3：编写实现**

创建 `pt/task/RssPollService.java`：

```java
package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.openliststrm.helper.TgHelper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtIndexerPlusService;
import com.ruoyi.openliststrm.pt.indexer.TorznabClient;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * RSS 轮询编排：遍历到期的索引器拉取种子，汇总后交给推送引擎。
 *
 * @author Jack
 */
@Slf4j
@Service
public class RssPollService {

    /** 连续失败达到该次数时发告警，只在恰好达到时发一次，避免每轮刷屏 */
    private static final int ALERT_FAIL_THRESHOLD = 3;

    private final IPtIndexerPlusService indexerService;
    private final TorznabClient torznabClient;
    private final SubscriptionEngine subscriptionEngine;

    public RssPollService(IPtIndexerPlusService indexerService,
                          TorznabClient torznabClient,
                          SubscriptionEngine subscriptionEngine) {
        this.indexerService = indexerService;
        this.torznabClient = torznabClient;
        this.subscriptionEngine = subscriptionEngine;
    }

    /**
     * 轮询一轮：拉取所有到期索引器的种子，汇总交给引擎。
     */
    public void poll() {
        List<PtIndexerPlus> indexers = indexerService.listEnabled();
        List<TorrentInfo> allTorrents = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (PtIndexerPlus indexer : indexers) {
            if (!isDue(indexer, now)) {
                continue;
            }
            try {
                List<TorrentInfo> fetched = torznabClient.fetch(indexer);
                allTorrents.addAll(fetched);
                indexer.setLastPollTime(new Date());
                indexer.setLastStatus("OK");
                indexer.setFailCount(0);
                log.info("索引器[{}]拉取到 {} 条种子", indexer.getName(), fetched.size());
            } catch (Exception e) {
                int fails = (indexer.getFailCount() == null ? 0 : indexer.getFailCount()) + 1;
                indexer.setFailCount(fails);
                indexer.setLastStatus(truncate(e.getMessage()));
                log.warn("索引器[{}]拉取失败（第{}次）：{}", indexer.getName(), fails, e.getMessage());
                if (fails == ALERT_FAIL_THRESHOLD) {
                    notifySafely("⚠️ 索引器[" + indexer.getName() + "]已连续失败 " + fails + " 次：" + e.getMessage());
                }
            }
            indexerService.updateById(indexer);
        }

        if (!allTorrents.isEmpty()) {
            int pushed = subscriptionEngine.process(allTorrents);
            if (pushed > 0) {
                notifySafely("📥 本轮为订阅推送了 " + pushed + " 个种子");
            }
            log.info("本轮共拉取 {} 条种子，推送 {} 个", allTorrents.size(), pushed);
        }
    }

    private boolean isDue(PtIndexerPlus indexer, long now) {
        if (indexer.getLastPollTime() == null) {
            return true;
        }
        int interval = indexer.getPollInterval() == null ? 600 : indexer.getPollInterval();
        return now - indexer.getLastPollTime().getTime() >= interval * 1000L;
    }

    private String truncate(String msg) {
        if (msg == null) {
            return "未知错误";
        }
        return msg.length() > 480 ? msg.substring(0, 480) : msg;
    }

    private void notifySafely(String msg) {
        try {
            TgHelper.sendMsg(msg);
        } catch (Exception e) {
            log.debug("发送通知失败（不影响主流程）：{}", e.getMessage());
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=RssPollServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 8, Failures: 0, Errors: 0

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/RssPollService.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/task/RssPollServiceTest.java
git commit -m "feat(pt): 新增 RSS 轮询服务，含到期判断与索引器失败告警"
```

---

## 任务 5：RSS 轮询与库对账两个调度器

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/RssPollTask.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/LibrarySyncTask.java`

> 两个薄调度器。`RssPollTask` 每 60 秒调 `RssPollService.poll`（60 秒只是「检查哪些索引器到期」的心跳，真正的拉取周期由每个索引器的 `poll_interval` 决定）。`LibrarySyncTask` 每 10 分钟遍历 ACTIVE 订阅调 `SubscriptionService.refresh`。

- [ ] **步骤 1：编写 RssPollTask**

创建 `pt/task/RssPollTask.java`：

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
 * RSS 轮询心跳：每 60 秒检查一次哪些索引器到期，到期的才真正拉取
 * （拉取周期由各索引器的 poll_interval 决定）。
 *
 * @author Jack
 */
@Slf4j
@Component
public class RssPollTask {

    @Autowired
    private RssPollService rssPollService;

    private final TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor");

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ThreadTraceIdUtil.initTraceId();
        scheduler.scheduleAtFixedRate(this::poll, Instant.now().plusSeconds(60), Duration.ofSeconds(60));
        log.info("RssPollTask started");
    }

    @PreDestroy
    public void stop() {
        log.info("RssPollTask stopped");
        MDC.clear();
    }

    private void poll() {
        try {
            rssPollService.poll();
        } catch (Exception e) {
            log.error("RssPollTask poll error", e);
        }
    }
}
```

- [ ] **步骤 2：编写 LibrarySyncTask**

创建 `pt/task/LibrarySyncTask.java`：

```java
package com.ruoyi.openliststrm.pt.task;

import com.ruoyi.common.utils.ThreadTraceIdUtil;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionService;
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
import java.util.List;

/**
 * 每 10 分钟遍历订阅中的订阅，与 Emby 对账（补齐总集数、推进已入库、重算状态）。
 *
 * @author Jack
 */
@Slf4j
@Component
public class LibrarySyncTask {

    @Autowired
    private IPtSubscriptionPlusService subscriptionService;
    @Autowired
    private SubscriptionService subscriptionBiz;

    private final TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor");

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ThreadTraceIdUtil.initTraceId();
        scheduler.scheduleAtFixedRate(this::poll, Instant.now().plusSeconds(120), Duration.ofMinutes(10));
        log.info("LibrarySyncTask started");
    }

    @PreDestroy
    public void stop() {
        log.info("LibrarySyncTask stopped");
        MDC.clear();
    }

    private void poll() {
        try {
            List<PtSubscriptionPlus> active = subscriptionService.listActive();
            for (PtSubscriptionPlus sub : active) {
                try {
                    subscriptionBiz.refresh(sub.getId());
                } catch (Exception e) {
                    log.warn("对账订阅[{}]失败：{}", sub.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("LibrarySyncTask poll error", e);
        }
    }
}
```

- [ ] **步骤 3：全量测试**

运行：`mvn -pl ruoyi-openliststrm -am test`

预期：BUILD SUCCESS，既有测试全绿

- [ ] **步骤 4：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/RssPollTask.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/task/LibrarySyncTask.java
git commit -m "feat(pt): 新增 RSS 轮询与库对账两个调度器"
```

---

## 任务 6：端到端部署验证（不可跳过）

**文件：** 无代码变更

> ⚠️ **这个任务是本计划最重要的一步，绝不能跳过。** 三个调度器都是 `@Component` + `@EventListener(ApplicationReadyEvent.class)`，Spring 启动时装配 + 触发。单元测试用构造器 new 绕过了 Spring 装配——上一阶段就是因为跳过部署验证，`SubscriptionEngine` 注入非 bean 的 `MediaParser` 导致启动崩溃却全绿。**必须真实启动确认。**

- [ ] **步骤 1：全量测试与构建**

运行：

```bash
mvn -pl ruoyi-openliststrm -am test
mvn clean package -DskipTests
```

预期：均 BUILD SUCCESS。测试总数在计划 4 的 290 基础上增加约 16（DownloadTrackService 7 + RssPollService 8 + QbittorrentClient 新增 1）。

- [ ] **步骤 2：部署后端**

运行：`docker compose up -d --build --no-deps backend`

- [ ] **步骤 3：确认启动成功（关键）**

轮询直到接口响应，并确认容器**没有反复重启**：

```bash
for i in $(seq 1 12); do
  sleep 10
  ST=$(docker inspect -f '{{.State.Status}} restarts={{.RestartCount}}' osr-backend 2>/dev/null)
  CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:6895/api/openliststrm/pt-filter-config 2>/dev/null)
  echo "[$((i*10))s] $ST http=$CODE"
  if [ "$CODE" = "401" ] || [ "$CODE" = "200" ]; then echo ">>> 就绪"; break; fi
done
```

预期：`running restarts=0`，http=401。

**若容器反复重启**（restarts 递增或状态 restarting）：说明有 bean 装配失败。按 AGENTS.md 的方法取错误：

```bash
docker update --restart=no osr-backend && docker restart osr-backend && sleep 40
docker cp osr-backend:/data/logs/sys-error.log ./tmp-err.log
tail -40 ./tmp-err.log   # 找 APPLICATION FAILED TO START
```

修掉 bean 问题后重新部署，直到 restarts=0。

- [ ] **步骤 4：确认三个调度器都启动了**

```bash
docker cp osr-backend:/data/logs/sys-info.log ./tmp-info.log
grep -E "RssPollTask started|DownloadTrackTask started|LibrarySyncTask started" ./tmp-info.log
rm -f ./tmp-info.log ./tmp-err.log
```

预期：三行 `started` 日志都在。

- [ ] **步骤 5：功能冒烟（需已配置索引器/下载器/订阅，人工）**

前提：已配好 Prowlarr 索引器、qBittorrent 下载器、至少一个订阅（用一部 PT 站上有资源的剧），且下载器保存路径落在文件同步任务的监听目录下。

观察 1–2 个 RSS 轮询周期后：
1. `sys-info.log` 里有 `索引器[xx]拉取到 N 条种子`
2. 若命中订阅缺集，有 `订阅[xx] xxx 已推送种子`
3. qBittorrent 里出现被打了 `osr-pt` 和 `osr-pt-xxx` 标签的任务
4. `pt_download_record` 表出现 PUSHED 记录，对应 `pt_subscription_episode` 的集变 IN_FLIGHT
5. 下载完成后 30 秒内记录变 COMPLETED
6. 文件被现有 FileMonitor 链路上传网盘、Emby 扫到后，10 分钟内对账把集推进为 IN_LIBRARY

用 SQL 观察状态流转：

```bash
docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" osr -e "
select id, sub_id, episode, state, tracking_tag from pt_download_record order by id desc limit 10;
select sub_id, episode, state from pt_subscription_episode where state != 'MISSING' order by sub_id, episode limit 20;"
```

- [ ] **步骤 6：Commit（如有修正）**

```bash
git add -A
git commit -m "fix(pt): 修正调度器验收中发现的问题"
```

---

## 全功能完成

计划 1–5 全部完成后，PT 订阅功能端到端可用：

```
用户订阅一部剧
  → RssPollTask 定时拉 Prowlarr RSS
  → SubscriptionEngine 匹配缺集、过滤择优、推送 qBittorrent
  → DownloadTrackTask 追踪下载完成
  → 现有 FileMonitor 链路上传网盘 → 重命名 → 刮削 → STRM 生成
  → Emby 扫库
  → LibrarySyncTask 对账，集数推进为已入库
  → 集齐后订阅自动完成
```

**遗留的已知优化点（见规格附录，非阻塞）：**
- `SubscriptionService.subscribe` / `refresh` 的 `@Transactional` 方法体内含 TMDb / Emby 网络调用，订阅量大时应把网络调用挪出事务。
- Telegram 通知目前只在推送/完成/失败/索引器告警几个节点，订阅集齐完成的通知未做。
- 洗版、站内搜索补集、原生 RSS、Transmission 等规格 §1.1 明确列为「不做」的扩展点，数据模型已预留。
