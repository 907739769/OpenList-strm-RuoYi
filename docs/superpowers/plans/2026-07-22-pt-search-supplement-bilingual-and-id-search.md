# PT 订阅搜索补集：三级回退（ID → 中文标题 → 英文标题）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让 `SearchSupplementService.supplement()` 的候选搜索从"只用中文标题查一次"升级为三级回退：先用 IMDb/TMDB ID 精确搜索（索引器支持时），再用中文标题搜索，最后用英文/原语言标题搜索——任一级过滤后有匹配就停止并推送，过滤标准全程不变。

**架构：** 三级回退顺序：ID 搜索（`t=movie`/`t=tvsearch` 带 `imdbid`/`tmdbid`，仅对 `t=caps` 探测确认支持的索引器发起）→ 中文标题搜索（现有 `keyword` 参数）→ 英文/原语言标题搜索（`sub.getOriginalTitle()`）。索引器 ID 搜索能力探测结果按索引器 ID 存进程内 `ConcurrentHashMap` 缓存。`imdbId` 在建订阅时从 TMDb 获取并落库（电影从详情接口直接取，剧集多查一次 `external_ids`）。过滤标准（`filterByTarget`/`filterMovieCandidates`）完全不变，三级候选统一走同一套过滤。

**技术栈：** Spring Boot（Java 25 preview）、MyBatis-Plus、OkHttp、`javax.xml`（DOM 解析）、JUnit 5 + Mockito、MockWebServer。

**前置阅读**（实现前务必看一遍，计划中的设计决策均来自这两份文档）：
- `docs/superpowers/specs/2026-07-22-pt-search-supplement-bilingual-keyword-design.md`
- `docs/superpowers/specs/2026-07-22-pt-search-supplement-id-based-search-design.md`

---

## 文件清单

| 文件 | 改动 | 职责 |
|---|---|---|
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java` | 改动 | 三级回退编排（本计划的核心） |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionMatcher.java` | 无需再改 | `normalizeAll` 已包内可见，直接复用 |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/SafeXmlDocuments.java` | 新建 | 共享的 XXE 防护 DOM 构建工具 |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabParser.java` | 改动 | `buildDocument` 改调用 `SafeXmlDocuments` |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/IndexerCapability.java` | 新建 | record，索引器 ID 搜索能力 |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabCapsParser.java` | 新建 | 解析 `t=caps` 响应 |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/IndexerCapabilityCache.java` | 新建 | 进程内缓存 + 探测 |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java` | 改动 | 新增 `getCaps`、`searchByExternalId`，`buildUrl` 的 `cat` 参数条件放宽 |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/PtSubscriptionPlus.java` | 改动 | 新增 `imdbId` 字段 |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/dto/TmdbSearchItem.java` | 改动 | 新增 `imdbId` 字段 |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/TmdbSearchService.java` | 改动 | `getDetail()` 填充 `imdbId` |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionService.java` | 改动 | `subscribe()` 落库 `imdbId` |
| `ruoyi-common/src/main/resources/sql/20260729-pt-subscription-imdb-id.sql` | 新建 | 迁移脚本 |
| 对应 `src/test/java` 下的测试文件 | 新建/改动 | 见各任务 |

---

### 任务 1：SearchSupplementService 加英文/原语言标题兜底

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementServiceTest.java`

- [x] **步骤 1：写失败的测试——中文搜到候选时不触发英文补搜**

在 `SearchSupplementServiceTest.java` 里 `supplement_电影订阅_标题年份都匹配的候选能正常传给引擎` 之后加：

```java
@Test
void supplement_中文搜到候选_不触发英文补搜() throws Exception {
    PtSubscriptionPlus movie = movieSub(20, "手机", "2003");
    movie.setOriginalTitle("手机");
    when(subscriptionService.getById(20)).thenReturn(movie);
    when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
    TorrentInfo t = torrent("手机.2003.1080p");
    t.setParsedTitle("手机");
    t.setParsedYear("2003");
    when(torznabClient.search(any(), anyString())).thenReturn(List.of(t));
    when(subscriptionEngine.pushBest(eq(movie), eq(0), anyList())).thenReturn(true);

    service.supplement(20, 0, "手机");

    verify(torznabClient, times(1)).search(any(), anyString());
}

@Test
void supplement_中文搜不到_originalTitle非空且不同_触发英文补搜() throws Exception {
    PtSubscriptionPlus movie = movieSub(20, "沙丘", "2021");
    movie.setOriginalTitle("Dune");
    when(subscriptionService.getById(20)).thenReturn(movie);
    when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
    TorrentInfo enTorrent = torrent("Dune.2021.2160p.WEB-DL");
    enTorrent.setParsedTitle("Dune");
    enTorrent.setParsedYear("2021");
    when(torznabClient.search(any(), eq("沙丘"))).thenReturn(List.of());
    when(torznabClient.search(any(), eq("Dune"))).thenReturn(List.of(enTorrent));
    when(subscriptionEngine.pushBest(eq(movie), eq(0), anyList())).thenReturn(true);

    SupplementResult result = service.supplement(20, 0, "沙丘");

    assertTrue(result.isPushed());
    ArgumentCaptor<List<TorrentInfo>> captor = ArgumentCaptor.forClass(List.class);
    verify(subscriptionEngine).pushBest(eq(movie), eq(0), captor.capture());
    assertEquals(1, captor.getValue().size());
    assertTrue(captor.getValue().contains(enTorrent));
}

@Test
void supplement_originalTitle为空_不触发英文补搜() throws Exception {
    PtSubscriptionPlus movie = movieSub(20, "手机", "2003");
    when(subscriptionService.getById(20)).thenReturn(movie);
    when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
    when(torznabClient.search(any(), eq("手机"))).thenReturn(List.of());

    service.supplement(20, 0, "手机");

    verify(torznabClient, times(1)).search(any(), anyString());
}

@Test
void supplement_originalTitle归一化后与title相同_不触发英文补搜() throws Exception {
    PtSubscriptionPlus movie = movieSub(20, "手机", "2003");
    movie.setOriginalTitle("手机");
    when(subscriptionService.getById(20)).thenReturn(movie);
    when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
    when(torznabClient.search(any(), eq("手机"))).thenReturn(List.of());

    service.supplement(20, 0, "手机");

    verify(torznabClient, times(1)).search(any(), anyString());
}

@Test
void supplement_剧集英文补搜关键词按原有格式拼season和episode() throws Exception {
    PtSubscriptionPlus sub = tvSub(10, 1, 12);
    sub.setOriginalTitle("Breaking Bad");
    when(subscriptionService.getById(10)).thenReturn(sub);
    when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
    when(torznabClient.search(any(), eq("Some Show S01E03"))).thenReturn(List.of());
    TorrentInfo enTorrent = torrent("Breaking.Bad.S01E03.1080p");
    enTorrent.setParsedSeason(1);
    enTorrent.setParsedEpisode(3);
    when(torznabClient.search(any(), eq("Breaking Bad S01E03"))).thenReturn(List.of(enTorrent));
    when(subscriptionEngine.pushBest(eq(sub), eq(3), anyList())).thenReturn(true);

    service.supplement(10, 3, "Some Show S01E03");

    verify(torznabClient).search(any(), eq("Breaking Bad S01E03"));
}

@Test
void supplement_剧集季包英文补搜关键词不带E后缀() throws Exception {
    PtSubscriptionPlus sub = tvSub(10, 1, 12);
    sub.setOriginalTitle("Breaking Bad");
    when(subscriptionService.getById(10)).thenReturn(sub);
    when(indexerService.listEnabled()).thenReturn(List.of(indexer(1)));
    when(torznabClient.search(any(), eq("Some Show S01"))).thenReturn(List.of());
    when(torznabClient.search(any(), eq("Breaking Bad S01"))).thenReturn(List.of());

    service.supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");

    verify(torznabClient).search(any(), eq("Breaking Bad S01"));
}
```

同时在文件顶部 import 区补：`import static org.mockito.Mockito.times;`（其余 import 已存在）。

- [ ] **步骤 2：运行测试确认失败**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=SearchSupplementServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：新增的几个用例 FAIL（`Dune`/`Breaking Bad` 相关断言不成立，因为生产代码还没有英文兜底逻辑；`times(1)` 的用例目前也会通过，这是正常的——步骤 4 实现后要保证它们依然通过，不要退化成总是搜两次）。

- [ ] **步骤 3：实现英文兜底逻辑**

打开 `SearchSupplementService.java`，把 `supplement` 方法替换为：

```java
public SupplementResult supplement(Integer subId, int episode, String keyword) {
    PtSubscriptionPlus sub = requireSearchable(subId);
    validateEpisode(sub, episode);

    int totalCandidates = 0;
    List<TorrentInfo> candidates = searchAcrossIndexers(keyword);
    for (TorrentInfo torrent : candidates) {
        subscriptionEngine.fillParsed(torrent);
    }
    totalCandidates += candidates.size();
    List<TorrentInfo> matched = filterByTarget(sub, episode, candidates);

    if (matched.isEmpty()) {
        String altKeyword = buildAltKeyword(sub, episode, keyword);
        if (altKeyword != null) {
            List<TorrentInfo> altCandidates = searchAcrossIndexers(altKeyword);
            for (TorrentInfo torrent : altCandidates) {
                subscriptionEngine.fillParsed(torrent);
            }
            totalCandidates += altCandidates.size();
            matched = filterByTarget(sub, episode, altCandidates);
        }
    }

    boolean pushed = subscriptionEngine.pushBest(sub, episode, matched);

    sub.setLastSearchTime(new Date());
    subscriptionService.updateById(sub);

    log.info("订阅[{}] {} 关键词[{}]搜索补集：候选{}个，{}",
            sub.getId(), sub.getTitle(), keyword, totalCandidates, pushed ? "已推送" : "未推送");
    return new SupplementResult(pushed, totalCandidates);
}

/**
 * 中文关键词搜不到匹配时的英文/原语言标题兜底：originalTitle 为空、或归一化后与 title 相同
 * （中文原生内容，TMDb 原语言标题本来就是中文）时返回 null，跳过补搜。
 * 季/集号后缀按 supplement() 已有的 episode/sub.getSeason() 重新拼，不依赖对入参 keyword
 * 字符串做解析——用户手动改过关键词时也能正确拼出英文版。
 */
private String buildAltKeyword(PtSubscriptionPlus sub, int episode, String keyword) {
    String originalTitle = sub.getOriginalTitle();
    if (StringUtils.isBlank(originalTitle)) {
        return null;
    }
    if (matcher.normalizeAll(originalTitle).equals(matcher.normalizeAll(sub.getTitle()))) {
        return null;
    }
    if (SubscriptionService.TYPE_MOVIE.equalsIgnoreCase(sub.getMediaType())) {
        return originalTitle;
    }
    if (episode == SubscriptionMatcher.SEASON_PACK) {
        return originalTitle + " S" + pad(sub.getSeason());
    }
    return originalTitle + " S" + pad(sub.getSeason()) + "E" + pad(episode);
}

private String pad(Integer number) {
    int n = number == null ? 0 : number;
    return n < 10 ? "0" + n : String.valueOf(n);
}
```

在文件顶部 import 区补 `import java.util.Set;`（如果 `matcher.normalizeAll` 返回类型需要显式引用；实际这里只调方法不声明变量类型，通常不需要额外 import，跳过即可）。

- [ ] **步骤 4：运行测试确认通过**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=SearchSupplementServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：全部 PASS（含之前已有的全部用例——`filterByTarget`/`filterMovieCandidates` 未改动，只是外层多了一层"为空才补搜"的编排）。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementServiceTest.java
git commit -m "feat(pt): 搜索补集中文搜不到时自动补一次英文/原语言标题"
```

---

### 任务 2：提取 SafeXmlDocuments 共享 XXE 防护工具

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/SafeXmlDocuments.java`
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabParser.java:64-80`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabParserTest.java`（不新增用例，只确认现有用例仍通过——这是纯重构）

- [ ] **步骤 1：新建 SafeXmlDocuments**

```java
package com.ruoyi.openliststrm.pt.indexer;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * 索引器响应（Torznab RSS / t=caps）的 XXE 防护 DOM 构建工具。
 * 从 {@link TorznabParser} 抽出，供 {@link TorznabParser} 与 {@link TorznabCapsParser} 共用——
 * 这段是安全相关的固定写法，两处各写一份容易日后改一处漏改一处。
 *
 * @author Jack
 */
public final class SafeXmlDocuments {

    private SafeXmlDocuments() {
    }

    /**
     * @throws IllegalArgumentException xml 非法、含 DTD 声明、或解析失败
     */
    public static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 禁用 DTD，防止 XXE。索引器是外部输入，必须防护。
            // 禁用 DTD 声明即可阻断实体展开，无需再设置 ACCESS_EXTERNAL_* 属性
            // （部分 JAXB 实现不支持这两个属性，设置时会抛异常导致每次解析都失败）
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            // 关闭命名空间感知，使 getElementsByTagName("torznab:attr") 能按字面量匹配
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("XML响应解析失败：" + e.getMessage(), e);
        }
    }
}
```

- [ ] **步骤 2：TorznabParser 改为委托**

在 `TorznabParser.java` 里，把：

```java
    private static Document buildDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 禁用 DTD，防止 XXE。索引器是外部输入，必须防护。
            // 禁用 DTD 声明即可阻断实体展开，无需再设置 ACCESS_EXTERNAL_* 属性
            // （部分 JAXB 实现不支持这两个属性，设置时会抛异常导致每次解析都失败）
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            // 关闭命名空间感知，使 getElementsByTagName("torznab:attr") 能按字面量匹配
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Torznab响应解析失败：" + e.getMessage(), e);
        }
    }
```

替换为：

```java
    private static Document buildDocument(String xml) {
        return SafeXmlDocuments.parse(xml);
    }
```

并删除文件顶部不再需要的 import：`javax.xml.parsers.DocumentBuilder`、`javax.xml.parsers.DocumentBuilderFactory`、`java.io.ByteArrayInputStream`、`java.nio.charset.StandardCharsets`（`Document`/`DocumentBuilder` 若仍被其他地方用到则保留，实际检查后 `TorznabParser` 其余代码只用到 `Document`/`Element`/`Node`/`NodeList`，这四个 import 可以整段删除）。

- [ ] **步骤 3：运行现有测试确认不回归**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=TorznabParserTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：全部 PASS（含 `parse_非法XML_抛IllegalArgumentException` 与 `parse_含DTD声明_抛异常而非解析_防XXE`——这两个用例只断言异常类型，不校验异常消息文本，改消息文案不影响它们）。

- [ ] **步骤 4：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/SafeXmlDocuments.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabParser.java
git commit -m "refactor(pt): 提取共享的XXE防护DOM构建工具，供caps解析器复用"
```

---

### 任务 3：IndexerCapability + TorznabCapsParser

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/IndexerCapability.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabCapsParser.java`
- 测试：创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabCapsParserTest.java`

- [ ] **步骤 1：写失败的测试**

```java
package com.ruoyi.openliststrm.pt.indexer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorznabCapsParserTest {

    @Test
    void parse_movie与tv均支持imdbid和tmdbid() {
        String xml = """
                <caps>
                  <searching>
                    <search available="yes" supportedParams="q"/>
                    <tv-search available="yes" supportedParams="q,season,ep,imdbid,tmdbid"/>
                    <movie-search available="yes" supportedParams="q,imdbid,tmdbid"/>
                  </searching>
                </caps>
                """;

        IndexerCapability cap = TorznabCapsParser.parse(xml);

        assertTrue(cap.movieImdbSupported());
        assertTrue(cap.movieTmdbSupported());
        assertTrue(cap.tvImdbSupported());
        assertTrue(cap.tvTmdbSupported());
    }

    @Test
    void parse_只支持imdbid不支持tmdbid() {
        String xml = """
                <caps>
                  <searching>
                    <movie-search available="yes" supportedParams="q,imdbid"/>
                  </searching>
                </caps>
                """;

        IndexerCapability cap = TorznabCapsParser.parse(xml);

        assertTrue(cap.movieImdbSupported());
        assertFalse(cap.movieTmdbSupported());
    }

    @Test
    void parse_available为no时视为不支持() {
        String xml = """
                <caps>
                  <searching>
                    <movie-search available="no" supportedParams="q,imdbid,tmdbid"/>
                  </searching>
                </caps>
                """;

        IndexerCapability cap = TorznabCapsParser.parse(xml);

        assertFalse(cap.movieImdbSupported());
        assertFalse(cap.movieTmdbSupported());
    }

    @Test
    void parse_无searching节点_返回NONE() {
        assertEquals(IndexerCapability.NONE, TorznabCapsParser.parse("<caps></caps>"));
    }

    @Test
    void parse_无movieSearch或tvSearch节点_对应能力为false() {
        String xml = """
                <caps>
                  <searching>
                    <search available="yes" supportedParams="q"/>
                  </searching>
                </caps>
                """;

        IndexerCapability cap = TorznabCapsParser.parse(xml);

        assertFalse(cap.movieImdbSupported());
        assertFalse(cap.tvImdbSupported());
    }

    @Test
    void parse_空字符串或null_返回NONE() {
        assertEquals(IndexerCapability.NONE, TorznabCapsParser.parse(""));
        assertEquals(IndexerCapability.NONE, TorznabCapsParser.parse(null));
    }

    @Test
    void parse_非法XML_返回NONE而不抛异常() {
        assertEquals(IndexerCapability.NONE, TorznabCapsParser.parse("<caps><searching>"));
    }
}
```

补 import：`import static org.junit.jupiter.api.Assertions.assertEquals;`

- [ ] **步骤 2：运行测试确认失败**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=TorznabCapsParserTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：编译失败（`IndexerCapability`/`TorznabCapsParser` 尚不存在）。

- [ ] **步骤 3：新建 IndexerCapability**

```java
package com.ruoyi.openliststrm.pt.indexer;

/**
 * 索引器 t=caps 探测出的 ID 搜索能力：movie-search/tv-search 是否支持 imdbid/tmdbid 参数。
 *
 * @author Jack
 */
public record IndexerCapability(boolean movieImdbSupported, boolean movieTmdbSupported,
                                 boolean tvImdbSupported, boolean tvTmdbSupported) {

    /** 探测失败、响应非法、或索引器未声明任何 ID 搜索能力时的安全默认值 */
    public static final IndexerCapability NONE = new IndexerCapability(false, false, false, false);
}
```

- [ ] **步骤 4：新建 TorznabCapsParser**

```java
package com.ruoyi.openliststrm.pt.indexer;

import com.ruoyi.common.utils.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 解析 Torznab {@code t=caps} 响应，提取 movie-search/tv-search 是否支持 imdbid/tmdbid 参数。
 *
 * @author Jack
 */
public final class TorznabCapsParser {

    private TorznabCapsParser() {
    }

    /**
     * @param xml t=caps 的响应体，允许为 null/空/非法 XML
     * @return 解析出的能力；响应为空、解析失败、或没有 searching 节点时返回 {@link IndexerCapability#NONE}
     */
    public static IndexerCapability parse(String xml) {
        if (StringUtils.isBlank(xml)) {
            return IndexerCapability.NONE;
        }
        try {
            Document doc = SafeXmlDocuments.parse(xml);
            Element searching = firstChildElement(doc.getDocumentElement(), "searching");
            if (searching == null) {
                return IndexerCapability.NONE;
            }
            Element movieSearch = firstChildElement(searching, "movie-search");
            Element tvSearch = firstChildElement(searching, "tv-search");
            return new IndexerCapability(
                    supportsParam(movieSearch, "imdbid"),
                    supportsParam(movieSearch, "tmdbid"),
                    supportsParam(tvSearch, "imdbid"),
                    supportsParam(tvSearch, "tmdbid"));
        } catch (Exception e) {
            return IndexerCapability.NONE;
        }
    }

    private static boolean supportsParam(Element searchElement, String param) {
        if (searchElement == null) {
            return false;
        }
        if (!"yes".equalsIgnoreCase(searchElement.getAttribute("available"))) {
            return false;
        }
        String supportedParams = searchElement.getAttribute("supportedParams");
        if (StringUtils.isBlank(supportedParams)) {
            return false;
        }
        for (String token : supportedParams.split(",")) {
            if (param.equalsIgnoreCase(token.trim())) {
                return true;
            }
        }
        return false;
    }

    private static Element firstChildElement(Element parent, String tag) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }
}
```

- [ ] **步骤 5：运行测试确认通过**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=TorznabCapsParserTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：全部 PASS。

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/IndexerCapability.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabCapsParser.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabCapsParserTest.java
git commit -m "feat(pt): 新增 t=caps 响应解析，判断索引器是否支持imdbid/tmdbid搜索"
```

---

### 任务 4：TorznabClient.getCaps + buildUrl 的 cat 参数条件放宽

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java`

- [ ] **步骤 1：写失败的测试**

在 `TorznabClientTest.java` 末尾（`search_HTTP错误码_抛IOException` 之后）加：

```java
    @Test
    void getCaps_正常响应_解析出能力() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                <caps>
                  <searching>
                    <movie-search available="yes" supportedParams="q,imdbid,tmdbid"/>
                  </searching>
                </caps>
                """));

        IndexerCapability cap = client.getCaps(indexer(null));

        assertTrue(cap.movieImdbSupported());
        assertTrue(cap.movieTmdbSupported());
    }

    @Test
    void getCaps_请求异常_返回NONE而不抛异常() {
        server.shutdown();

        assertEquals(IndexerCapability.NONE, client.getCaps(indexer(null)));
    }

    @Test
    void getCaps_HTTP错误码_返回NONE而不抛异常() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertEquals(IndexerCapability.NONE, client.getCaps(indexer(null)));
    }
```

补 import：`import static org.junit.jupiter.api.Assertions.assertEquals;`（若已存在则不用重复加）。

- [ ] **步骤 2：运行测试确认失败**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=TorznabClientTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：编译失败（`getCaps` 方法不存在）。

- [ ] **步骤 3：实现 getCaps**

在 `TorznabClient.java` 的 `testConnection` 方法后面加：

```java
    /**
     * 探测索引器 ID 搜索能力（t=caps），用于判断是否可以发起 imdbid/tmdbid 精确搜索。
     * 任何异常（网络失败、响应非法）均返回 {@link IndexerCapability#NONE}，不向上抛——
     * 与 {@link #testConnection} 同样的容错哲学，能力探测失败不该阻断后续的标题搜索兜底。
     */
    public IndexerCapability getCaps(PtIndexerPlus indexer) {
        try {
            String body = execute(buildUrl(indexer, "caps"));
            return TorznabCapsParser.parse(body);
        } catch (Exception e) {
            log.warn("索引器[{}]能力探测失败：{}", indexer.getName(), e.getMessage());
            return IndexerCapability.NONE;
        }
    }
```

- [ ] **步骤 4：运行测试确认通过**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=TorznabClientTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：全部 PASS。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java
git commit -m "feat(pt): TorznabClient新增getCaps探测索引器ID搜索能力"
```

---

### 任务 5：IndexerCapabilityCache

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/IndexerCapabilityCache.java`
- 测试：创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/IndexerCapabilityCacheTest.java`

- [ ] **步骤 1：写失败的测试**

```java
package com.ruoyi.openliststrm.pt.indexer;

import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexerCapabilityCacheTest {

    @Mock
    private TorznabClient torznabClient;

    private final IndexerCapabilityCache cache = new IndexerCapabilityCache(torznabClient);

    private PtIndexerPlus indexer(int id) {
        PtIndexerPlus i = new PtIndexerPlus();
        i.setId(id);
        i.setName("idx-" + id);
        return i;
    }

    @Test
    void get_首次探测并缓存_第二次不再调用TorznabClient() {
        PtIndexerPlus indexer = indexer(1);
        IndexerCapability cap = new IndexerCapability(true, false, true, false);
        when(torznabClient.getCaps(indexer)).thenReturn(cap);

        IndexerCapability first = cache.get(indexer);
        IndexerCapability second = cache.get(indexer);

        assertSame(cap, first);
        assertSame(cap, second);
        verify(torznabClient, times(1)).getCaps(indexer);
    }

    @Test
    void get_不同索引器分别缓存() {
        PtIndexerPlus idx1 = indexer(1);
        PtIndexerPlus idx2 = indexer(2);
        when(torznabClient.getCaps(idx1)).thenReturn(new IndexerCapability(true, false, false, false));
        when(torznabClient.getCaps(idx2)).thenReturn(new IndexerCapability(false, true, false, false));

        assertEquals(true, cache.get(idx1).movieImdbSupported());
        assertEquals(true, cache.get(idx2).movieTmdbSupported());
        verify(torznabClient, times(1)).getCaps(idx1);
        verify(torznabClient, times(1)).getCaps(idx2);
    }

    @Test
    void get_TorznabClient返回null时视为NONE而非缓存null() {
        PtIndexerPlus indexer = indexer(3);
        when(torznabClient.getCaps(indexer)).thenReturn(null);

        IndexerCapability result = cache.get(indexer);

        assertEquals(IndexerCapability.NONE, result);
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=IndexerCapabilityCacheTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：编译失败（`IndexerCapabilityCache` 不存在）。

- [ ] **步骤 3：实现 IndexerCapabilityCache**

```java
package com.ruoyi.openliststrm.pt.indexer;

import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 索引器 ID 搜索能力的进程内缓存：每个索引器进程生命周期内只探测一次 t=caps。
 * 不落库、不设 TTL——索引器能力配置很少变化，重启应用即可重新探测（YAGNI）。
 *
 * @author Jack
 */
@Component
public class IndexerCapabilityCache {

    private final TorznabClient torznabClient;
    private final ConcurrentMap<Integer, IndexerCapability> cache = new ConcurrentHashMap<>();

    public IndexerCapabilityCache(TorznabClient torznabClient) {
        this.torznabClient = torznabClient;
    }

    public IndexerCapability get(PtIndexerPlus indexer) {
        return cache.computeIfAbsent(indexer.getId(), id -> {
            IndexerCapability capability = torznabClient.getCaps(indexer);
            return capability == null ? IndexerCapability.NONE : capability;
        });
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=IndexerCapabilityCacheTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：全部 PASS。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/IndexerCapabilityCache.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/IndexerCapabilityCacheTest.java
git commit -m "feat(pt): 新增IndexerCapabilityCache，进程内缓存索引器ID搜索能力"
```

---

### 任务 6：TorznabClient.searchByExternalId

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java`

- [ ] **步骤 1：写失败的测试**

在 `TorznabClientTest.java` 末尾加：

```java
    @Test
    void searchByExternalId_电影按imdbid拼URL_不带season和ep() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.searchByExternalId(indexer("5000"), true, "imdbid", "tt1160419", null, null);

        RecordedRequest request = server.takeRequest();
        assertEquals("movie", request.getRequestUrl().queryParameter("t"));
        assertEquals("tt1160419", request.getRequestUrl().queryParameter("imdbid"));
        assertEquals("5000", request.getRequestUrl().queryParameter("cat"));
        assertEquals(null, request.getRequestUrl().queryParameter("season"));
        assertEquals(null, request.getRequestUrl().queryParameter("ep"));
    }

    @Test
    void searchByExternalId_剧集季包按season搜索_不带ep() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.searchByExternalId(indexer(null), false, "tmdbid", "1396", 1, null);

        RecordedRequest request = server.takeRequest();
        assertEquals("tvsearch", request.getRequestUrl().queryParameter("t"));
        assertEquals("1396", request.getRequestUrl().queryParameter("tmdbid"));
        assertEquals("1", request.getRequestUrl().queryParameter("season"));
        assertEquals(null, request.getRequestUrl().queryParameter("ep"));
    }

    @Test
    void searchByExternalId_剧集单集带season和ep() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.searchByExternalId(indexer(null), false, "imdbid", "tt0903747", 1, 3);

        RecordedRequest request = server.takeRequest();
        assertEquals("1", request.getRequestUrl().queryParameter("season"));
        assertEquals("3", request.getRequestUrl().queryParameter("ep"));
    }

    @Test
    void searchByExternalId_正常响应_返回解析结果并带上索引器ID() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        List<TorrentInfo> list = client.searchByExternalId(indexer(null), true, "imdbid", "tt1160419", null, null);

        assertEquals(1, list.size());
        assertEquals(7, list.get(0).getIndexerId());
    }

    @Test
    void searchByExternalId_HTTP错误码_抛IOException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThrows(IOException.class, () -> client.searchByExternalId(indexer(null), true, "imdbid", "tt1", null, null));
    }
```

- [ ] **步骤 2：运行测试确认失败**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=TorznabClientTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：编译失败（`searchByExternalId` 不存在）。

- [ ] **步骤 3：实现 searchByExternalId，并放宽 buildUrl 的 cat 参数条件**

在 `TorznabClient.java` 里把：

```java
        if ("search".equals(type) && StringUtils.isNotBlank(indexer.getCategories())) {
            builder.addQueryParameter("cat", indexer.getCategories());
        }
```

改为：

```java
        if (!"caps".equals(type) && StringUtils.isNotBlank(indexer.getCategories())) {
            builder.addQueryParameter("cat", indexer.getCategories());
        }
```

（原逻辑只对 `t=search` 带分类参数；`t=movie`/`t=tvsearch` 同样受益于分类过滤，只有 `t=caps` 不需要。`fetch_分类为空_不带cat参数`/`fetch_请求参数正确` 等既有用例只覆盖 `t=search`，行为不受影响。）

然后在 `search` 方法后面加：

```java
    /**
     * 按外部 ID（IMDb/TMDB）精确搜索，用于订阅搜索补集的第一优先级。
     *
     * @param movie      true=电影(t=movie)，false=剧集(t=tvsearch)
     * @param idParamName "imdbid" 或 "tmdbid"
     * @param idValue    对应的 ID 值
     * @param season     剧集季号，电影传 null
     * @param episode    剧集集号，季包搜索或电影传 null
     * @throws IOException              网络异常或 HTTP 非 2xx
     * @throws IllegalArgumentException 响应体不是合法 Torznab XML
     */
    public List<TorrentInfo> searchByExternalId(PtIndexerPlus indexer, boolean movie,
                                                 String idParamName, String idValue,
                                                 Integer season, Integer episode) throws IOException {
        HttpUrl.Builder builder = buildUrl(indexer, movie ? "movie" : "tvsearch").newBuilder()
                .addQueryParameter(idParamName, idValue);
        if (!movie) {
            builder.addQueryParameter("season", String.valueOf(season));
            if (episode != null) {
                builder.addQueryParameter("ep", String.valueOf(episode));
            }
        }
        String body = execute(builder.build());
        List<TorrentInfo> list = TorznabParser.parse(body);
        for (TorrentInfo info : list) {
            info.setIndexerId(indexer.getId());
        }
        log.debug("索引器[{}]按{}={}搜索返回{}条种子", indexer.getName(), idParamName, idValue, list.size());
        return list;
    }
```

- [ ] **步骤 4：运行测试确认通过**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=TorznabClientTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：全部 PASS。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java
git commit -m "feat(pt): TorznabClient新增searchByExternalId，支持t=movie/t=tvsearch按ID搜索"
```

---

### 任务 7：迁移脚本 + PtSubscriptionPlus.imdbId

**文件：**
- 创建：`ruoyi-common/src/main/resources/sql/20260729-pt-subscription-imdb-id.sql`
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/PtSubscriptionPlus.java`

- [ ] **步骤 1：新建迁移脚本**

```sql
-- ----------------------------
-- 20260729: pt_subscription 增加 imdb_id 列，用于索引器 ID 精确搜索（幂等脚本）
-- 该表已在真实库存在且可能有数据，用 ALTER 而非重建。
-- ----------------------------

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pt_subscription' AND COLUMN_NAME = 'imdb_id');
SET @sql := IF(@exist = 0, 'ALTER TABLE `pt_subscription` ADD COLUMN `imdb_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''IMDb ID(如tt0125664)，建订阅时从TMDb获取，用于索引器ID精确搜索'' AFTER `tmdb_id`', 'SELECT ''Column imdb_id already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

- [ ] **步骤 2：PtSubscriptionPlus 加字段**

在 `PtSubscriptionPlus.java` 的 `tmdbId` 字段后面加：

```java
    /** IMDb ID（如 tt0125664），建订阅时从 TMDb 详情/external_ids 获取，用于索引器 ID 精确搜索 */
    @TableField("imdb_id")
    private String imdbId;
```

- [ ] **步骤 3：编译确认**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am compile
```
预期：无编译错误（`@Getter @Setter` 自动生成 `getImdbId`/`setImdbId`）。

- [ ] **步骤 4：Commit**

```bash
git add ruoyi-common/src/main/resources/sql/20260729-pt-subscription-imdb-id.sql ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/PtSubscriptionPlus.java
git commit -m "feat(pt): pt_subscription新增imdb_id列"
```

---

### 任务 8：TmdbSearchItem.imdbId + TmdbSearchService.getDetail() 填充

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/dto/TmdbSearchItem.java`
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/TmdbSearchService.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/TmdbSearchServiceTest.java`

- [ ] **步骤 1：写失败的测试**

在 `TmdbSearchServiceTest.java` 的 `getDetail_取标题年份海报` 之后加：

```java
    @Test
    void getDetail_电影从详情直接取imdb_id_不额外调external_ids() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("""
                        {"id":550,"title":"搏击俱乐部","original_title":"Fight Club",
                         "release_date":"1999-10-15","imdb_id":"tt0137523"}
                        """);

        TmdbSearchItem detail = service.getDetail("MOVIE", "550");

        assertEquals("tt0137523", detail.getImdbId());
        verify(tmDbApiService, never()).getExternalIds(anyString(), anyString(), anyInt());
    }

    @Test
    void getDetail_电影详情无imdb_id字段_imdbId为null() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("{\"id\":550,\"title\":\"片\",\"release_date\":\"1999-10-15\"}");

        assertNull(service.getDetail("MOVIE", "550").getImdbId());
    }

    @Test
    void getDetail_剧集查external_ids取imdb_id() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("""
                        {"id":1396,"name":"绝命毒师","original_name":"Breaking Bad",
                         "first_air_date":"2008-01-20"}
                        """);
        when(tmDbApiService.getExternalIds(anyString(), eq("tv"), anyInt()))
                .thenReturn("{\"imdb_id\":\"tt0903747\"}");

        TmdbSearchItem detail = service.getDetail("TV", "1396");

        assertEquals("tt0903747", detail.getImdbId());
    }

    @Test
    void getDetail_剧集external_ids无imdb_id_imdbId为null() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("{\"id\":1396,\"name\":\"剧\",\"first_air_date\":\"2008-01-20\"}");
        when(tmDbApiService.getExternalIds(anyString(), eq("tv"), anyInt()))
                .thenReturn("{\"tvdb_id\":123}");

        assertNull(service.getDetail("TV", "1396").getImdbId());
    }
```

补 import：`import static org.mockito.ArgumentMatchers.eq;`（其余已存在）。

- [ ] **步骤 2：运行测试确认失败**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=TmdbSearchServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：编译失败（`TmdbSearchItem.getImdbId()` 不存在）。

- [ ] **步骤 3：TmdbSearchItem 加字段**

在 `TmdbSearchItem.java` 的 `originalTitle` 字段后面加：

```java
    /** IMDb ID（如 tt0125664），电影从 TMDb 详情直接取，剧集需要额外查 external_ids */
    private String imdbId;
```

- [ ] **步骤 4：TmdbSearchService.getDetail() 补充解析**

把 `getDetail` 方法：

```java
    public TmdbSearchItem getDetail(String mediaType, String tmdbId) {
        JSONObject detail = readObject(tmDbApiService.getDetails(
                openlistConfig.getTmdbApiKey(), tmdbType(mediaType), Integer.parseInt(tmdbId)));
        if (detail == null) {
            throw new IllegalArgumentException("TMDb 未返回 " + tmdbId + " 的详情");
        }
        return toItem(detail, mediaType);
    }
```

改为：

```java
    public TmdbSearchItem getDetail(String mediaType, String tmdbId) {
        JSONObject detail = readObject(tmDbApiService.getDetails(
                openlistConfig.getTmdbApiKey(), tmdbType(mediaType), Integer.parseInt(tmdbId)));
        if (detail == null) {
            throw new IllegalArgumentException("TMDb 未返回 " + tmdbId + " 的详情");
        }
        TmdbSearchItem item = toItem(detail, mediaType);
        item.setImdbId(resolveImdbId(mediaType, tmdbId, detail));
        return item;
    }

    /**
     * 电影详情接口（/movie/{id}）本身带 imdb_id，直接取，不产生额外请求；
     * 剧集详情接口（/tv/{id}）没有该字段，需要多查一次 external_ids。
     * 两种情况都取不到时返回 null——imdbId 为空是允许的降级路径（走标题搜索）。
     */
    private String resolveImdbId(String mediaType, String tmdbId, JSONObject detail) {
        if (TYPE_MOVIE.equalsIgnoreCase(mediaType)) {
            return detail.getString("imdb_id");
        }
        JSONObject externalIds = readObject(
                tmDbApiService.getExternalIds(openlistConfig.getTmdbApiKey(), "tv", Integer.parseInt(tmdbId)));
        return externalIds == null ? null : externalIds.getString("imdb_id");
    }
```

- [ ] **步骤 5：运行测试确认通过**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=TmdbSearchServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：全部 PASS（含既有的 `getDetail_取标题年份海报`——它是 TV 类型且没有 mock `getExternalIds`，未打桩的 mock 方法调用默认返回 null，`readObject(null)` 走 `isBlank` 分支返回 null，不会抛异常，测试原有断言不受影响）。

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/dto/TmdbSearchItem.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/TmdbSearchService.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/TmdbSearchServiceTest.java
git commit -m "feat(pt): getDetail补充解析imdb_id，电影直取剧集查external_ids"
```

---

### 任务 9：SubscriptionService.subscribe() 落库 imdbId

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionService.java:84-96`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionServiceTest.java`

- [ ] **步骤 1：写失败的测试**

在 `SubscriptionServiceTest.java` 的 `subscribe_电影_不调用剧集的总集数接口` 之后加：

```java
    @Test
    void subscribe_落库时带上TMDb返回的imdbId() throws Exception {
        TmdbSearchItem d = detail("搏击俱乐部", "1999");
        d.setImdbId("tt0137523");
        when(tmdbSearchService.getDetail(anyString(), anyString())).thenReturn(d);
        stubSaveAssignsId(70);
        when(mediaServerService.getActive()).thenReturn(null);

        service.subscribe(movieRequest());

        ArgumentCaptor<PtSubscriptionPlus> captor = ArgumentCaptor.forClass(PtSubscriptionPlus.class);
        verify(subscriptionService).save(captor.capture());
        assertEquals("tt0137523", captor.getValue().getImdbId());
    }
```

- [ ] **步骤 2：运行测试确认失败**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=SubscriptionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：FAIL（`captor.getValue().getImdbId()` 为 null，断言不成立）。

- [ ] **步骤 3：实现**

在 `SubscriptionService.java` 里，`sub.setOriginalTitle(detail.getOriginalTitle());` 后面加一行：

```java
        sub.setImdbId(detail.getImdbId());
```

- [ ] **步骤 4：运行测试确认通过**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=SubscriptionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：全部 PASS。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionService.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionServiceTest.java
git commit -m "feat(pt): 建订阅时落库TMDb返回的imdbId"
```

---

### 任务 10：SearchSupplementService 整合 ID 搜索第一级

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementServiceTest.java`

这是整合任务：最终顺序变成 ID 搜索 → 中文标题（任务1已实现）→ 英文标题（任务1已实现）。

- [ ] **步骤 1：写失败的测试**

在 `setUp()` 里补充 `IndexerCapabilityCache`（用真实实例包住已有的 `torznabClient` mock，理由同 `matcher` 用真实实例——能力判断逻辑本身就是要测试的行为）：

```java
    @Mock private IPtIndexerPlusService indexerService;
    @Mock private TorznabClient torznabClient;
    @Mock private SubscriptionEngine subscriptionEngine;
    @Mock private IPtSubscriptionPlusService subscriptionService;
    private final SubscriptionMatcher matcher = new SubscriptionMatcher();
    private IndexerCapabilityCache capabilityCache;

    private SearchSupplementService service;

    @BeforeEach
    void setUp() {
        capabilityCache = new IndexerCapabilityCache(torznabClient);
        service = new SearchSupplementService(indexerService, torznabClient, subscriptionEngine, subscriptionService, matcher, capabilityCache);
    }
```

在文件末尾加测试（新增 import：`import com.ruoyi.openliststrm.pt.indexer.IndexerCapability; import com.ruoyi.openliststrm.pt.indexer.IndexerCapabilityCache;`）：

```java
    // ---------- ID 搜索第一级 ----------

    @Test
    void supplement_电影订阅_索引器支持imdbid且订阅有imdbId_优先用imdbid搜索() throws Exception {
        PtSubscriptionPlus movie = movieSub(20, "沙丘", "2021");
        movie.setImdbId("tt1160419");
        movie.setTmdbId("438631");
        when(subscriptionService.getById(20)).thenReturn(movie);
        PtIndexerPlus idx = indexer(1);
        when(indexerService.listEnabled()).thenReturn(List.of(idx));
        when(torznabClient.getCaps(idx)).thenReturn(new IndexerCapability(true, true, false, false));
        TorrentInfo t = torrent("Dune.2021.2160p.WEB-DL");
        t.setParsedTitle("Dune");
        t.setParsedYear("2021");
        when(torznabClient.searchByExternalId(idx, true, "imdbid", "tt1160419", null, null))
                .thenReturn(List.of(t));
        when(subscriptionEngine.pushBest(eq(movie), eq(0), anyList())).thenReturn(true);

        SupplementResult result = service.supplement(20, 0, "沙丘");

        assertTrue(result.isPushed());
        verify(torznabClient, never()).search(any(), anyString());
        ArgumentCaptor<List<TorrentInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(subscriptionEngine).pushBest(eq(movie), eq(0), captor.capture());
        assertTrue(captor.getValue().contains(t));
    }

    @Test
    void supplement_电影订阅_索引器只支持tmdbid_退到tmdbid() throws Exception {
        PtSubscriptionPlus movie = movieSub(20, "沙丘", "2021");
        movie.setImdbId("tt1160419");
        movie.setTmdbId("438631");
        when(subscriptionService.getById(20)).thenReturn(movie);
        PtIndexerPlus idx = indexer(1);
        when(indexerService.listEnabled()).thenReturn(List.of(idx));
        when(torznabClient.getCaps(idx)).thenReturn(new IndexerCapability(false, true, false, false));
        TorrentInfo t = torrent("Dune.2021.2160p.WEB-DL");
        t.setParsedTitle("Dune");
        t.setParsedYear("2021");
        when(torznabClient.searchByExternalId(idx, true, "tmdbid", "438631", null, null))
                .thenReturn(List.of(t));
        when(subscriptionEngine.pushBest(eq(movie), eq(0), anyList())).thenReturn(true);

        service.supplement(20, 0, "沙丘");

        verify(torznabClient).searchByExternalId(idx, true, "tmdbid", "438631", null, null);
        verify(torznabClient, never()).searchByExternalId(eq(idx), eq(true), eq("imdbid"), any(), any(), any());
    }

    @Test
    void supplement_电影订阅_订阅无imdbId_退到tmdbid() throws Exception {
        PtSubscriptionPlus movie = movieSub(20, "沙丘", "2021");
        movie.setTmdbId("438631");
        when(subscriptionService.getById(20)).thenReturn(movie);
        PtIndexerPlus idx = indexer(1);
        when(indexerService.listEnabled()).thenReturn(List.of(idx));
        when(torznabClient.getCaps(idx)).thenReturn(new IndexerCapability(true, true, false, false));
        when(torznabClient.searchByExternalId(idx, true, "tmdbid", "438631", null, null))
                .thenReturn(List.of());
        when(torznabClient.search(any(), anyString())).thenReturn(List.of());

        service.supplement(20, 0, "沙丘");

        verify(torznabClient).searchByExternalId(idx, true, "tmdbid", "438631", null, null);
    }

    @Test
    void supplement_索引器不支持ID搜索_跳过ID搜索直接走标题() throws Exception {
        PtSubscriptionPlus movie = movieSub(20, "手机", "2003");
        movie.setImdbId("tt0125664");
        movie.setTmdbId("1");
        when(subscriptionService.getById(20)).thenReturn(movie);
        PtIndexerPlus idx = indexer(1);
        when(indexerService.listEnabled()).thenReturn(List.of(idx));
        when(torznabClient.getCaps(idx)).thenReturn(IndexerCapability.NONE);
        TorrentInfo t = torrent("手机.2003.1080p");
        t.setParsedTitle("手机");
        t.setParsedYear("2003");
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(t));
        when(subscriptionEngine.pushBest(eq(movie), eq(0), anyList())).thenReturn(true);

        service.supplement(20, 0, "手机");

        verify(torznabClient, never()).searchByExternalId(any(), anyBoolean(), anyString(), anyString(), any(), any());
    }

    @Test
    void supplement_ID搜到候选但过滤后为空_继续走标题搜索而非直接判定未命中() throws Exception {
        PtSubscriptionPlus movie = movieSub(20, "手机", "2003");
        movie.setImdbId("tt0125664");
        when(subscriptionService.getById(20)).thenReturn(movie);
        PtIndexerPlus idx = indexer(1);
        when(indexerService.listEnabled()).thenReturn(List.of(idx));
        when(torznabClient.getCaps(idx)).thenReturn(new IndexerCapability(true, false, false, false));
        // 索引器 ID 搜索实现有 bug，返回了不相关内容
        TorrentInfo wrong = torrent("Cellphone.2003.1080p");
        wrong.setParsedTitle("Cellphone");
        wrong.setParsedYear("2003");
        when(torznabClient.searchByExternalId(idx, true, "imdbid", "tt0125664", null, null))
                .thenReturn(List.of(wrong));
        TorrentInfo right = torrent("手机.2003.1080p");
        right.setParsedTitle("手机");
        right.setParsedYear("2003");
        when(torznabClient.search(any(), anyString())).thenReturn(List.of(right));
        when(subscriptionEngine.pushBest(eq(movie), eq(0), anyList())).thenReturn(true);

        SupplementResult result = service.supplement(20, 0, "手机");

        assertTrue(result.isPushed());
        ArgumentCaptor<List<TorrentInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(subscriptionEngine).pushBest(eq(movie), eq(0), captor.capture());
        assertTrue(captor.getValue().contains(right));
        assertFalse(captor.getValue().contains(wrong));
    }

    @Test
    void supplement_剧集订阅_季包ID搜索不带ep参数() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 12);
        sub.setImdbId("tt0903747");
        when(subscriptionService.getById(10)).thenReturn(sub);
        PtIndexerPlus idx = indexer(1);
        when(indexerService.listEnabled()).thenReturn(List.of(idx));
        when(torznabClient.getCaps(idx)).thenReturn(new IndexerCapability(false, false, true, false));
        when(torznabClient.searchByExternalId(idx, false, "imdbid", "tt0903747", 1, null))
                .thenReturn(List.of());

        service.supplement(10, SubscriptionMatcher.SEASON_PACK, "Some Show S01");

        verify(torznabClient).searchByExternalId(idx, false, "imdbid", "tt0903747", 1, null);
    }

    @Test
    void supplement_剧集订阅_单集ID搜索带season和ep() throws Exception {
        PtSubscriptionPlus sub = tvSub(10, 1, 12);
        sub.setImdbId("tt0903747");
        when(subscriptionService.getById(10)).thenReturn(sub);
        PtIndexerPlus idx = indexer(1);
        when(indexerService.listEnabled()).thenReturn(List.of(idx));
        when(torznabClient.getCaps(idx)).thenReturn(new IndexerCapability(false, false, true, false));
        when(torznabClient.searchByExternalId(idx, false, "imdbid", "tt0903747", 1, 3))
                .thenReturn(List.of());

        service.supplement(10, 3, "Some Show S01E03");

        verify(torznabClient).searchByExternalId(idx, false, "imdbid", "tt0903747", 1, 3);
    }
```

补 import（文件顶部）：
```java
import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.pt.indexer.IndexerCapability;
import com.ruoyi.openliststrm.pt.indexer.IndexerCapabilityCache;
import static org.mockito.ArgumentMatchers.anyBoolean;
```
（`PtIndexerPlus` 可能已经 import 过，检查一下避免重复。）

- [ ] **步骤 2：运行测试确认失败**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=SearchSupplementServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：编译失败（构造函数参数数量不对、`searchByExternalId` 未被调用等）。

- [ ] **步骤 3：实现——SearchSupplementService 整合三级回退**

把整个 `SearchSupplementService.java` 替换为：

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtIndexerPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.indexer.IndexerCapability;
import com.ruoyi.openliststrm.pt.indexer.IndexerCapabilityCache;
import com.ruoyi.openliststrm.pt.indexer.TorznabClient;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import com.ruoyi.openliststrm.pt.subscription.dto.SupplementResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * 搜索补集编排：三级回退（ID 精确搜索 → 中文标题 → 英文/原语言标题）找候选，
 * 交给 {@link SubscriptionEngine} 走与 RSS 相同的过滤择优/占位/推送链路。
 * 职责边界同样终止于"把种子推给下载器"。
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
    private final SubscriptionMatcher matcher;
    private final IndexerCapabilityCache capabilityCache;

    public SearchSupplementService(IPtIndexerPlusService indexerService,
                                   TorznabClient torznabClient,
                                   SubscriptionEngine subscriptionEngine,
                                   IPtSubscriptionPlusService subscriptionService,
                                   SubscriptionMatcher matcher,
                                   IndexerCapabilityCache capabilityCache) {
        this.indexerService = indexerService;
        this.torznabClient = torznabClient;
        this.subscriptionEngine = subscriptionEngine;
        this.subscriptionService = subscriptionService;
        this.matcher = matcher;
        this.capabilityCache = capabilityCache;
    }

    /**
     * 对指定订阅的指定目标（集号，或季包/电影的哨兵值）发起一次搜索补集。
     * <p>
     * 三级回退：ID 精确搜索（索引器支持时）→ 中文标题 → 英文/原语言标题，任一级过滤后有
     * 匹配就停止，不再尝试后面的级别；过滤标准（{@link #filterByTarget}）全程不变。
     * </p>
     *
     * @throws IllegalArgumentException 订阅不存在、订阅未在订阅中(ACTIVE)，或 episode 不合法
     */
    public SupplementResult supplement(Integer subId, int episode, String keyword) {
        PtSubscriptionPlus sub = requireSearchable(subId);
        validateEpisode(sub, episode);

        int totalCandidates = 0;

        List<TorrentInfo> idCandidates = searchByExternalId(sub, episode);
        fillParsedAll(idCandidates);
        totalCandidates += idCandidates.size();
        List<TorrentInfo> matched = filterByTarget(sub, episode, idCandidates);

        if (matched.isEmpty()) {
            List<TorrentInfo> candidates = searchAcrossIndexers(keyword);
            fillParsedAll(candidates);
            totalCandidates += candidates.size();
            matched = filterByTarget(sub, episode, candidates);
        }

        if (matched.isEmpty()) {
            String altKeyword = buildAltKeyword(sub, episode, keyword);
            if (altKeyword != null) {
                List<TorrentInfo> altCandidates = searchAcrossIndexers(altKeyword);
                fillParsedAll(altCandidates);
                totalCandidates += altCandidates.size();
                matched = filterByTarget(sub, episode, altCandidates);
            }
        }

        boolean pushed = subscriptionEngine.pushBest(sub, episode, matched);

        sub.setLastSearchTime(new Date());
        subscriptionService.updateById(sub);

        log.info("订阅[{}] {} 关键词[{}]搜索补集：候选{}个，{}",
                sub.getId(), sub.getTitle(), keyword, totalCandidates, pushed ? "已推送" : "未推送");
        return new SupplementResult(pushed, totalCandidates);
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

    /**
     * 第一优先级：对每个启用索引器，若 {@code t=caps} 探测到支持则用 IMDb ID（优先）或
     * TMDB ID（订阅无 IMDb ID 或索引器不支持 imdbid 时）发起精确搜索；两者都不满足的索引器
     * 直接跳过，不发请求。
     */
    private List<TorrentInfo> searchByExternalId(PtSubscriptionPlus sub, int episode) {
        List<PtIndexerPlus> indexers = indexerService.listEnabled();
        if (indexers.isEmpty()) {
            return List.of();
        }
        boolean movie = SubscriptionService.TYPE_MOVIE.equalsIgnoreCase(sub.getMediaType());
        Integer season = movie ? null : sub.getSeason();
        Integer ep = (movie || episode == SubscriptionMatcher.SEASON_PACK) ? null : episode;

        List<TorrentInfo> merged = new CopyOnWriteArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = indexers.stream()
                    .map(indexer -> CompletableFuture.runAsync(() -> {
                        IdSearchParam param = resolveIdParam(sub, indexer, movie);
                        if (param == null) {
                            return;
                        }
                        try {
                            merged.addAll(torznabClient.searchByExternalId(
                                    indexer, movie, param.name(), param.value(), season, ep));
                        } catch (Exception e) {
                            log.warn("索引器[{}]按{}搜索失败：{}", indexer.getName(), param.name(), e.getMessage());
                        }
                    }, executor))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        return new ArrayList<>(merged);
    }

    private record IdSearchParam(String name, String value) {
    }

    private IdSearchParam resolveIdParam(PtSubscriptionPlus sub, PtIndexerPlus indexer, boolean movie) {
        IndexerCapability capability = capabilityCache.get(indexer);
        boolean imdbSupported = movie ? capability.movieImdbSupported() : capability.tvImdbSupported();
        boolean tmdbSupported = movie ? capability.movieTmdbSupported() : capability.tvTmdbSupported();
        if (imdbSupported && StringUtils.isNotBlank(sub.getImdbId())) {
            return new IdSearchParam("imdbid", sub.getImdbId());
        }
        if (tmdbSupported && StringUtils.isNotBlank(sub.getTmdbId())) {
            return new IdSearchParam("tmdbid", sub.getTmdbId());
        }
        return null;
    }

    private void fillParsedAll(List<TorrentInfo> candidates) {
        for (TorrentInfo torrent : candidates) {
            subscriptionEngine.fillParsed(torrent);
        }
    }

    /**
     * 中文关键词搜不到匹配时的英文/原语言标题兜底：originalTitle 为空、或归一化后与 title 相同
     * （中文原生内容，TMDb 原语言标题本来就是中文）时返回 null，跳过补搜。
     * 季/集号后缀按 supplement() 已有的 episode/sub.getSeason() 重新拼，不依赖对入参 keyword
     * 字符串做解析——用户手动改过关键词时也能正确拼出英文版。
     */
    private String buildAltKeyword(PtSubscriptionPlus sub, int episode, String keyword) {
        String originalTitle = sub.getOriginalTitle();
        if (StringUtils.isBlank(originalTitle)) {
            return null;
        }
        if (matcher.normalizeAll(originalTitle).equals(matcher.normalizeAll(sub.getTitle()))) {
            return null;
        }
        if (SubscriptionService.TYPE_MOVIE.equalsIgnoreCase(sub.getMediaType())) {
            return originalTitle;
        }
        if (episode == SubscriptionMatcher.SEASON_PACK) {
            return originalTitle + " S" + pad(sub.getSeason());
        }
        return originalTitle + " S" + pad(sub.getSeason()) + "E" + pad(episode);
    }

    private String pad(Integer number) {
        int n = number == null ? 0 : number;
        return n < 10 ? "0" + n : String.valueOf(n);
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
        Integer totalEpisodes = sub.getTotalEpisodes();
        if (episode < 1 || totalEpisodes == null || episode > totalEpisodes) {
            throw new IllegalArgumentException("集号超出范围：" + episode);
        }
    }

    /**
     * 数据一致性校验：搜索补集的候选来自模糊全文搜索或 ID 搜索，未经过 {@link SubscriptionMatcher} 确认，
     * 必须在交给 {@link SubscriptionEngine#pushBest} 之前自行校验候选是否真的匹配目标订阅，否则错配种子会被
     * handleGroup 无差别占位/推送（剧集会永久卡在 IN_FLIGHT，电影会直接下载错内容）。ID 命中的候选同样要
     * 过这层校验——防的是索引器对 ID 参数实现有 bug（比如把 imdbid 当普通关键词分词处理）。
     *
     * <p>电影订阅没有季/集号可比对，改为校验标题（复用 {@link SubscriptionMatcher} 同一套归一化
     * 全等规则）与年份，并排除带季/集信息的候选（说明是剧集/综艺）。</p>
     */
    private List<TorrentInfo> filterByTarget(PtSubscriptionPlus sub, int episode, List<TorrentInfo> candidates) {
        if (SubscriptionService.TYPE_MOVIE.equalsIgnoreCase(sub.getMediaType())) {
            return filterMovieCandidates(sub, candidates);
        }
        Integer subSeason = sub.getSeason();
        List<TorrentInfo> matched = new ArrayList<>();
        for (TorrentInfo candidate : candidates) {
            Integer parsedSeason = candidate.getParsedSeason();
            if (parsedSeason == null || !parsedSeason.equals(subSeason)) {
                continue;
            }
            Integer parsedEpisode = candidate.getParsedEpisode();
            if (episode == SubscriptionMatcher.SEASON_PACK) {
                if (parsedEpisode == null) {
                    matched.add(candidate);
                }
            } else if (parsedEpisode != null && parsedEpisode == episode) {
                matched.add(candidate);
            }
        }
        return matched;
    }

    /**
     * 电影候选校验标准与 {@link SubscriptionMatcher} 的电影分支保持一致：
     * 带季/集信息的一定是剧集/综艺，标题需归一化后与订阅有交集，年份必须完全一致
     * （同名翻拍常见，宁可漏也不能串台）。
     */
    private List<TorrentInfo> filterMovieCandidates(PtSubscriptionPlus sub, List<TorrentInfo> candidates) {
        Set<String> subTitles = matcher.normalizeAll(sub.getTitle(), sub.getOriginalTitle());
        List<TorrentInfo> matched = new ArrayList<>();
        for (TorrentInfo candidate : candidates) {
            if (candidate.getParsedSeason() != null || candidate.getParsedEpisode() != null) {
                continue;
            }
            Set<String> torrentTitles = matcher.normalizeAll(candidate.getParsedTitle(), candidate.getParsedTitleEn());
            if (Collections.disjoint(torrentTitles, subTitles)) {
                continue;
            }
            if (StringUtils.isBlank(candidate.getParsedYear()) || StringUtils.isBlank(sub.getYear())
                    || !candidate.getParsedYear().equals(sub.getYear())) {
                continue;
            }
            matched.add(candidate);
        }
        return matched;
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：
```bash
mvn -q -pl ruoyi-openliststrm -am test -Dtest=SearchSupplementServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：全部 PASS（含任务 1 已实现的英文兜底用例、之前所有电影/剧集过滤用例、以及本任务新增的 ID 搜索用例）。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementService.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SearchSupplementServiceTest.java
git commit -m "feat(pt): 搜索补集接入ID精确搜索第一优先级，三级回退完整闭环"
```

---

### 任务 11：全量验证

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

- [ ] **步骤 3（可选，需要用户确认后再做）：启动验证**

新增了 `IndexerCapabilityCache`（`@Component`）与 `SearchSupplementService` 构造函数参数变化，虽然都是已注册 bean 的依赖注入，理论上风险很低，但涉及 bean 装配变化，按项目规范（见 `AGENTS.md` NOTES）建议做一次真实启动验证：

```bash
docker compose up -d --build --no-deps backend
```
确认容器 `restarts=0`，且 `/api/openliststrm/pt-subscriptions` 等接口能正常响应。若跳过此步骤，需在交付时明确告知用户"仅单测验证，未做容器启动验证"。

- [ ] **步骤 4：更新 AGENTS.md（可选）**

若本次改动引入了值得记录的通用注意事项（例如"新增 XML 解析器必须复用 SafeXmlDocuments，不要重新写 XXE 防护逻辑"），可以补一条到 `AGENTS.md` 的 CONVENTIONS 部分。非强制，视改动是否有长期可复用的经验决定。

---

## 自检记录

- **规格覆盖度**：两份设计文档的每个小节都能对应到具体任务——中英文兜底(任务1)、SafeXmlDocuments(任务2)、caps解析(任务3)、getCaps(任务4)、能力缓存(任务5)、searchByExternalId(任务6)、imdbId数据模型(任务7-9)、三级回退整合(任务10)、验证(任务11)。
- **占位符扫描**：已通读，无 "TODO"/"待补充"/"类似任务N" 等模式，每个步骤的代码块均为完整可运行代码。
- **类型一致性**：`IndexerCapability` 的四个字段名（`movieImdbSupported`/`movieTmdbSupported`/`tvImdbSupported`/`tvTmdbSupported`）在 `TorznabCapsParser`、`IndexerCapabilityCache`、`SearchSupplementService.resolveIdParam` 中保持一致；`TorznabClient.searchByExternalId` 的参数顺序 `(indexer, movie, idParamName, idValue, season, episode)` 在任务6定义后，任务10的调用方完全对齐。
