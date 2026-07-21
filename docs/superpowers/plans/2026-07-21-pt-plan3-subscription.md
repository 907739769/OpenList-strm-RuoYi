# PT 订阅 计划3：订阅管理 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让用户能在页面上搜 TMDb 建订阅、看到准确的「已入库 5/12，缺 3、7」、配置过滤规则。

**架构：** 新增 `pt/subscription` 子包。`TmdbSearchService` 把 `TMDbApiService` 的原始 JSON 转成结构化 DTO；`SubscriptionService` 负责建订阅（查 TMDb 总集数 → 生成每集行 → 查 Emby 初始化状态）与对账刷新。REST 层薄转发。前端两个页面沿用 `useTaskList` 体系。

**技术栈：** Java 25 (Spring Boot 4.0.6) / MyBatis-Plus / FastJSON2 / JUnit 5 + Mockito / Vue 3 + Element Plus + vitest

**上游规格：** `docs/superpowers/specs/2026-07-21-pt-subscription-download-design.md`（**必读附录 A–H**，那里记着第二阶段拍板的设计决定）

**执行约定：** 直接在 `dev` 分支提交。

**本计划不包含：** RSS 轮询、下载追踪、Emby 定时对账三个调度器，以及 Telegram 通知——它们属于计划 4。本计划结束时，订阅能建、进度能看、规则能配，但**系统仍不会自动下载任何东西**。

---

## 前置：已就绪的成果

| 已有 | 位置 | 本计划怎么用 |
|---|---|---|
| `TMDbApiService` | `tmdb/TMDbApiService.java` | `search(apiKey, type, query, year)` 与 `getDetails(apiKey, type, id)` 均返回**原始 JSON 字符串** |
| TMDb API Key | `config/OpenlistConfig.getTmdbApiKey()` | 存于 `sys_config` 的 `openlist.tmdb.apikey` |
| `IMediaServerClient` + `MediaServerClientFactory` | `pt/media/` | `listEpisodes(config, tmdbId, season)` 返回 `Set<Integer>`；`hasMovie(config, tmdbId)` 返回 boolean。**两者都可能抛 `IOException`** |
| `IPtMediaServerPlusService.getActive()` | `mybatisplus/service/` | 取当前启用的媒体服务器，**无启用时返回 null** |
| 4 张业务表与实体 | 计划 2 | `PtSubscriptionPlus` / `PtSubscriptionEpisodePlus` / `PtFilterConfigPlus` / `PtDownloadRecordPlus` |
| `BaseCrudRestController` | `controller/api/` | 提供 list/getById/add/edit/delete 五个端点 |
| `useTaskList` composable | `openlist-web/src/composables/` | `executeApi` 已改为可选 |

**从前两个计划继承的硬约束：**

1. 所有 `mvn -pl ruoyi-openliststrm -am test -Dtest=X` 必须追加 `-Dsurefire.failIfNoSpecifiedTests=false`；多个测试类用**逗号**分隔（`+` 号在 Maven 3.6.3 下静默失效）。
2. 业务 datetime 用 `java.util.Date`；实体继承 `BaseEntity` 且不重复声明 createTime/updateTime；Mapper 只继承 `BaseMapper`，不写 XML。
3. **严禁用 `season == 0` 判断是否为电影**，`media_type` 是唯一依据（规格附录 F）。电影的 season 是 0 哨兵值，而剧集特别篇在 TMDb 里也是第 0 季。
4. 前端 API 用 `request.get`/`post`/`put`/`delete` 方法形式 + `getXxxListApi` 命名；业务逻辑放 composable 不放组件；错误提示由 axios 拦截器统一弹出，**composable 的 catch 里不要再弹一次**。
5. 菜单图标类名必须在 `openlist-web/src/composables/useMenuIcon.ts` 的 iconMap 中。

---

## 文件结构

### 后端新增

| 文件 | 职责 |
|---|---|
| `ruoyi-common/src/main/resources/sql/20260727-pt-subscription-menu.sql` | 把计划 2 隐藏的两个菜单翻成显示 |
| `pt/subscription/dto/TmdbSearchItem.java` | TMDb 搜索结果条目（前端选片用） |
| `pt/subscription/dto/SubscribeRequest.java` | 建订阅入参 |
| `pt/subscription/dto/SubscriptionProgress.java` | 订阅进度（已入库/在途/缺集列表） |
| `pt/subscription/TmdbSearchService.java` | 原始 JSON → DTO，含总集数查询 |
| `pt/subscription/SubscriptionService.java` | 建订阅 / 对账刷新 / 暂停恢复 / 查进度 |
| `controller/api/PtSubscriptionRestController.java` | 订阅 REST |
| `controller/api/PtFilterConfigRestController.java` | 过滤配置 REST |

### 后端修改

| 文件 | 修改内容 |
|---|---|
| `ruoyi-common/.../MysqlDdl.java` | 注册新迁移 |

### 前端新增

`api/openlist/ptSubscription.ts`、`ptFilterConfig.ts`；`composables/usePtSubscription.ts`、`usePtFilterConfig.ts`；`views/openlist/ptSubscription/index.vue`、`ptFilterConfig/index.vue`

### 前端修改

`router/index.ts` 注册两个组件路径

---

## 任务 1：TMDb 搜索服务

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/dto/TmdbSearchItem.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/TmdbSearchService.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/TmdbSearchServiceTest.java`

> **背景：** `TMDbApiService.search(apiKey, type, query, year)` 返回 TMDb 的**原始 JSON 字符串**，形如：
>
> ```json
> {"page":1,"results":[
>   {"id":1396,"name":"绝命毒师","original_name":"Breaking Bad","first_air_date":"2008-01-20","poster_path":"/abc.jpg","overview":"..."},
>   ...
> ],"total_results":2}
> ```
>
> 电影用 `title` / `original_title` / `release_date`，剧集用 `name` / `original_name` / `first_air_date`——**字段名不同，这是最容易写错的地方**。
>
> 剧集总集数要另外调 `getDetails(apiKey, "tv", id)`，其响应里有 `seasons` 数组：
>
> ```json
> {"id":1396,"name":"绝命毒师","seasons":[
>   {"season_number":0,"episode_count":8,"name":"特别篇"},
>   {"season_number":1,"episode_count":7},
>   {"season_number":2,"episode_count":13}
> ]}
> ```
>
> 注意 `season_number = 0` 是**特别篇**，不是电影。

- [ ] **步骤 1：编写失败的测试**

创建 `TmdbSearchServiceTest.java`：

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.pt.subscription.dto.TmdbSearchItem;
import com.ruoyi.openliststrm.tmdb.TMDbApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TmdbSearchServiceTest {

    @Mock
    private TMDbApiService tmDbApiService;

    @Mock
    private OpenlistConfig openlistConfig;

    @InjectMocks
    private TmdbSearchService service;

    @BeforeEach
    void setUp() {
        when(openlistConfig.getTmdbApiKey()).thenReturn("test-key");
    }

    @Test
    void search_剧集_取name与first_air_date() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("""
                        {"results":[
                          {"id":1396,"name":"绝命毒师","original_name":"Breaking Bad",
                           "first_air_date":"2008-01-20","poster_path":"/abc.jpg","overview":"简介"}
                        ]}
                        """);

        List<TmdbSearchItem> items = service.search("TV", "绝命毒师");

        assertEquals(1, items.size());
        TmdbSearchItem item = items.get(0);
        assertEquals("1396", item.getTmdbId());
        assertEquals("绝命毒师", item.getTitle());
        assertEquals("Breaking Bad", item.getOriginalTitle());
        assertEquals("2008", item.getYear());
        assertEquals("/abc.jpg", item.getPosterPath());
        assertEquals("TV", item.getMediaType());
    }

    @Test
    void search_电影_取title与release_date() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("""
                        {"results":[
                          {"id":550,"title":"搏击俱乐部","original_title":"Fight Club",
                           "release_date":"1999-10-15","poster_path":"/x.jpg"}
                        ]}
                        """);

        List<TmdbSearchItem> items = service.search("MOVIE", "搏击俱乐部");

        assertEquals("550", items.get(0).getTmdbId());
        assertEquals("搏击俱乐部", items.get(0).getTitle());
        assertEquals("Fight Club", items.get(0).getOriginalTitle());
        assertEquals("1999", items.get(0).getYear());
        assertEquals("MOVIE", items.get(0).getMediaType());
    }

    @Test
    void search_日期缺失或格式异常_年份为null而非抛异常() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("""
                        {"results":[
                          {"id":1,"name":"无日期","first_air_date":""},
                          {"id":2,"name":"缺字段"},
                          {"id":3,"name":"怪日期","first_air_date":"待定"}
                        ]}
                        """);

        List<TmdbSearchItem> items = service.search("TV", "x");

        assertEquals(3, items.size());
        assertNull(items.get(0).getYear());
        assertNull(items.get(1).getYear());
        assertNull(items.get(2).getYear());
    }

    @Test
    void search_空结果_返回空列表() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("{\"results\":[]}");

        assertTrue(service.search("TV", "不存在的剧").isEmpty());
    }

    @Test
    void search_响应无results字段_返回空列表而非NPE() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("{\"status_message\":\"Invalid API key\"}");

        assertTrue(service.search("TV", "x").isEmpty());
    }

    @Test
    void search_响应非法JSON_返回空列表而非抛异常() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("<html>error</html>");

        assertTrue(service.search("TV", "x").isEmpty());
    }

    @Test
    void search_关键词为空_直接返回空列表且不调TMDb() {
        assertTrue(service.search("TV", "  ").isEmpty());
    }

    @Test
    void getSeasonEpisodeCount_取指定季的集数() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("""
                        {"id":1396,"seasons":[
                          {"season_number":0,"episode_count":8},
                          {"season_number":1,"episode_count":7},
                          {"season_number":2,"episode_count":13}
                        ]}
                        """);

        assertEquals(7, service.getSeasonEpisodeCount("1396", 1));
        assertEquals(13, service.getSeasonEpisodeCount("1396", 2));
    }

    @Test
    void getSeasonEpisodeCount_季号0是特别篇_不是电影_同样能查到() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("""
                        {"seasons":[{"season_number":0,"episode_count":8},{"season_number":1,"episode_count":7}]}
                        """);

        assertEquals(8, service.getSeasonEpisodeCount("1396", 0));
    }

    @Test
    void getSeasonEpisodeCount_季不存在_抛IllegalArgumentException() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("{\"seasons\":[{\"season_number\":1,\"episode_count\":7}]}");

        assertThrows(IllegalArgumentException.class, () -> service.getSeasonEpisodeCount("1396", 99));
    }

    @Test
    void getSeasonEpisodeCount_响应无seasons_抛IllegalArgumentException() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("{\"id\":1396}");

        assertThrows(IllegalArgumentException.class, () -> service.getSeasonEpisodeCount("1396", 1));
    }

    @Test
    void getDetail_取标题年份海报() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("""
                        {"id":1396,"name":"绝命毒师","original_name":"Breaking Bad",
                         "first_air_date":"2008-01-20","poster_path":"/abc.jpg"}
                        """);

        TmdbSearchItem detail = service.getDetail("TV", "1396");

        assertEquals("绝命毒师", detail.getTitle());
        assertEquals("2008", detail.getYear());
        assertEquals("/abc.jpg", detail.getPosterPath());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TmdbSearchServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `TmdbSearchService` / `TmdbSearchItem` 找不到符号

> 若报 Mockito 相关依赖缺失，检查 `ruoyi-openliststrm/pom.xml` 是否已有 `spring-boot-starter-test`（它传递引入 mockito-core 与 mockito-junit-jupiter）。项目中 `PtDownloaderPlusServiceImplTest` 已用过 `@Mock`/`@InjectMocks`，说明依赖齐备。

- [ ] **步骤 3：编写 DTO**

创建 `pt/subscription/dto/TmdbSearchItem.java`：

```java
package com.ruoyi.openliststrm.pt.subscription.dto;

import lombok.Data;

/**
 * TMDb 搜索结果条目，供前端选片。
 *
 * @author Jack
 */
@Data
public class TmdbSearchItem {

    /** TMDb ID */
    private String tmdbId;

    /** 媒体类型 TV / MOVIE */
    private String mediaType;

    /** 中文标题（剧集取 name，电影取 title） */
    private String title;

    /** 原始标题（剧集取 original_name，电影取 original_title） */
    private String originalTitle;

    /** 首播/上映年份，解析不出时为 null */
    private String year;

    /** 海报路径（TMDb 的相对路径，如 /abc.jpg） */
    private String posterPath;

    /** 简介 */
    private String overview;
}
```

- [ ] **步骤 4：编写实现**

创建 `pt/subscription/TmdbSearchService.java`：

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.pt.subscription.dto.TmdbSearchItem;
import com.ruoyi.openliststrm.tmdb.TMDbApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 TMDb 返回的原始 JSON 转成结构化 DTO。
 * <p>
 * 剧集与电影的字段名不同：剧集用 name / original_name / first_air_date，
 * 电影用 title / original_title / release_date。本类屏蔽这个差异。
 * </p>
 *
 * @author Jack
 */
@Slf4j
@Service
public class TmdbSearchService {

    /** 媒体类型：剧集 */
    public static final String TYPE_TV = "TV";

    /** 媒体类型：电影 */
    public static final String TYPE_MOVIE = "MOVIE";

    @Autowired
    private TMDbApiService tmDbApiService;

    @Autowired
    private OpenlistConfig openlistConfig;

    /**
     * 按关键词搜索。
     *
     * @param mediaType TV / MOVIE
     * @return 搜索结果；关键词为空、响应异常或无结果时返回空列表（不抛异常，搜索失败不该让页面报错）
     */
    public List<TmdbSearchItem> search(String mediaType, String keyword) {
        List<TmdbSearchItem> result = new ArrayList<>();
        if (StringUtils.isBlank(keyword)) {
            return result;
        }
        String raw = tmDbApiService.search(openlistConfig.getTmdbApiKey(), tmdbType(mediaType), keyword.trim(), null);
        JSONArray results = readArray(raw, "results");
        if (results == null) {
            return result;
        }
        for (int i = 0; i < results.size(); i++) {
            result.add(toItem(results.getJSONObject(i), mediaType));
        }
        return result;
    }

    /**
     * 按 TMDb ID 取详情，用于建订阅时补全标题/年份/海报。
     *
     * @throws IllegalArgumentException 响应无法解析
     */
    public TmdbSearchItem getDetail(String mediaType, String tmdbId) {
        JSONObject detail = readObject(tmDbApiService.getDetails(
                openlistConfig.getTmdbApiKey(), tmdbType(mediaType), Integer.parseInt(tmdbId)));
        if (detail == null) {
            throw new IllegalArgumentException("TMDb 未返回 " + tmdbId + " 的详情");
        }
        return toItem(detail, mediaType);
    }

    /**
     * 取剧集指定季的总集数。
     * <p>
     * 注意季号 0 是**特别篇**（TMDb 约定），不是电影——电影不该走这个方法。
     * </p>
     *
     * @throws IllegalArgumentException 响应无 seasons，或该季不存在
     */
    public int getSeasonEpisodeCount(String tmdbId, int season) {
        String raw = tmDbApiService.getDetails(openlistConfig.getTmdbApiKey(), "tv", Integer.parseInt(tmdbId));
        JSONArray seasons = readArray(raw, "seasons");
        if (seasons == null) {
            throw new IllegalArgumentException("TMDb 未返回剧集 " + tmdbId + " 的季信息");
        }
        for (int i = 0; i < seasons.size(); i++) {
            JSONObject item = seasons.getJSONObject(i);
            Integer number = item.getInteger("season_number");
            if (number != null && number == season) {
                Integer count = item.getInteger("episode_count");
                if (count == null || count <= 0) {
                    throw new IllegalArgumentException("TMDb 中剧集 " + tmdbId + " 第 " + season + " 季的集数无效");
                }
                return count;
            }
        }
        throw new IllegalArgumentException("TMDb 中剧集 " + tmdbId + " 不存在第 " + season + " 季");
    }

    private TmdbSearchItem toItem(JSONObject json, String mediaType) {
        boolean tv = !TYPE_MOVIE.equalsIgnoreCase(mediaType);
        TmdbSearchItem item = new TmdbSearchItem();
        item.setTmdbId(json.getString("id"));
        item.setMediaType(tv ? TYPE_TV : TYPE_MOVIE);
        item.setTitle(json.getString(tv ? "name" : "title"));
        item.setOriginalTitle(json.getString(tv ? "original_name" : "original_title"));
        item.setYear(extractYear(json.getString(tv ? "first_air_date" : "release_date")));
        item.setPosterPath(json.getString("poster_path"));
        item.setOverview(json.getString("overview"));
        return item;
    }

    /**
     * 从 yyyy-MM-dd 取年份。TMDb 对未定档作品会给空串或非常规值，此时返回 null。
     */
    private String extractYear(String date) {
        if (StringUtils.isBlank(date) || date.length() < 4) {
            return null;
        }
        String year = date.substring(0, 4);
        return year.chars().allMatch(Character::isDigit) ? year : null;
    }

    private String tmdbType(String mediaType) {
        return TYPE_MOVIE.equalsIgnoreCase(mediaType) ? "movie" : "tv";
    }

    private JSONObject readObject(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return JSONObject.parseObject(raw);
        } catch (Exception e) {
            log.warn("TMDb 响应不是合法 JSON：{}", e.getMessage());
            return null;
        }
    }

    private JSONArray readArray(String raw, String key) {
        JSONObject json = readObject(raw);
        return json == null ? null : json.getJSONArray(key);
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TmdbSearchServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 12, Failures: 0, Errors: 0

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/ ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/
git commit -m "feat(pt): 新增 TMDb 搜索服务，屏蔽剧集与电影的字段名差异"
```

---
## 任务 2：订阅服务

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/dto/SubscribeRequest.java`
- 创建：`.../dto/SubscriptionProgress.java`
- 创建：`.../SubscriptionService.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/SubscriptionServiceTest.java`

> **这是本计划的核心。** 三件事：
>
> **建订阅**：查 TMDb 拿总集数与元信息 → 落库 → 生成每集行 → **立即查一次 Emby 初始化状态**。因为最后这步，订阅创建完就能显示准确的「已入库 5/12」，不用等轮询。
>
> **对账刷新**：重新查 TMDb 总集数（连载剧会增加）补齐集行 → 查 Emby → 把已有的集推进为 `IN_LIBRARY` → 重算订阅状态。计划 4 的 `LibrarySyncTask` 只要对每个订阅调一次本方法即可。
>
> **查进度**：产出「已入库 N/M、在途 K、缺第 X、Y 集」。
>
> **三条关键设计约定：**
>
> 1. **电影统一为「只有 1 集的剧」**：`media_type=MOVIE`、`season=0`（哨兵值）、`total_episodes=1`、唯一的集行 `episode=0`。这样四段核心逻辑完全复用。**判断是否电影只看 `media_type`，绝不能看 `season == 0`**——剧集的特别篇在 TMDb 里也是第 0 季（规格附录 F）。
> 2. **Emby 不可用不阻断建订阅**：没配媒体服务器、或查询抛 `IOException`，一律记 warn 后按「全部缺失」处理，订阅照常建成。媒体服务器是加分项不是前置依赖。
> 3. **对账只升级不降级**：`MISSING`/`IN_FLIGHT` → `IN_LIBRARY` 会做，反向**不做**。若用户从 Emby 删了某集，我们不把它退回 `MISSING`——否则一次误删会触发一轮重新下载。代价是进度显示会偏乐观，这是有意的取舍，写进方法注释。

- [ ] **步骤 1：编写失败的测试**

创建 `SubscriptionServiceTest.java`：

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.openliststrm.mybatisplus.domain.PtMediaServerPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtMediaServerPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.media.IMediaServerClient;
import com.ruoyi.openliststrm.pt.media.MediaServerClientFactory;
import com.ruoyi.openliststrm.pt.subscription.dto.SubscribeRequest;
import com.ruoyi.openliststrm.pt.subscription.dto.SubscriptionProgress;
import com.ruoyi.openliststrm.pt.subscription.dto.TmdbSearchItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceTest {

    @Mock
    private IPtSubscriptionPlusService subscriptionService;
    @Mock
    private IPtSubscriptionEpisodePlusService episodeService;
    @Mock
    private IPtMediaServerPlusService mediaServerService;
    @Mock
    private MediaServerClientFactory mediaServerClientFactory;
    @Mock
    private IMediaServerClient mediaServerClient;
    @Mock
    private TmdbSearchService tmdbSearchService;

    @InjectMocks
    private SubscriptionService service;

    private TmdbSearchItem detail(String title, String year) {
        TmdbSearchItem item = new TmdbSearchItem();
        item.setTitle(title);
        item.setYear(year);
        item.setPosterPath("/p.jpg");
        return item;
    }

    /** 让 save 给实体塞上 id，模拟自增主键回填 */
    private void stubSaveAssignsId(int id) {
        doAnswer(inv -> {
            ((PtSubscriptionPlus) inv.getArgument(0)).setId(id);
            return true;
        }).when(subscriptionService).save(any(PtSubscriptionPlus.class));
    }

    private void stubEmbyConfigured() {
        PtMediaServerPlus server = new PtMediaServerPlus();
        server.setId(1);
        server.setType("EMBY");
        when(mediaServerService.getActive()).thenReturn(server);
        when(mediaServerClientFactory.get(any())).thenReturn(mediaServerClient);
    }

    private SubscribeRequest tvRequest() {
        SubscribeRequest req = new SubscribeRequest();
        req.setTmdbId("1396");
        req.setMediaType("TV");
        req.setSeason(1);
        return req;
    }

    private SubscribeRequest movieRequest() {
        SubscribeRequest req = new SubscribeRequest();
        req.setTmdbId("550");
        req.setMediaType("MOVIE");
        return req;
    }

    // ---------- 建订阅：剧集 ----------

    @Test
    void subscribe_剧集_按总集数生成集行并用Emby初始化状态() throws Exception {
        when(tmdbSearchService.getDetail(anyString(), anyString())).thenReturn(detail("绝命毒师", "2008"));
        when(tmdbSearchService.getSeasonEpisodeCount(anyString(), anyInt())).thenReturn(7);
        stubSaveAssignsId(10);
        stubEmbyConfigured();
        when(mediaServerClient.listEpisodes(any(), anyString(), anyInt())).thenReturn(Set.of(1, 2, 5));

        service.subscribe(tvRequest());

        ArgumentCaptor<List<PtSubscriptionEpisodePlus>> captor = ArgumentCaptor.forClass(List.class);
        verify(episodeService).saveBatch(captor.capture());
        List<PtSubscriptionEpisodePlus> episodes = captor.getValue();

        assertEquals(7, episodes.size());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7),
                episodes.stream().map(PtSubscriptionEpisodePlus::getEpisode).toList());
        assertEquals(List.of("IN_LIBRARY", "IN_LIBRARY", "MISSING", "MISSING", "IN_LIBRARY", "MISSING", "MISSING"),
                episodes.stream().map(PtSubscriptionEpisodePlus::getState).toList());
        assertTrue(episodes.stream().allMatch(e -> e.getSubId() == 10));
    }

    @Test
    void subscribe_剧集_落库字段正确() throws Exception {
        when(tmdbSearchService.getDetail(anyString(), anyString())).thenReturn(detail("绝命毒师", "2008"));
        when(tmdbSearchService.getSeasonEpisodeCount(anyString(), anyInt())).thenReturn(7);
        stubSaveAssignsId(10);
        when(mediaServerService.getActive()).thenReturn(null);

        service.subscribe(tvRequest());

        ArgumentCaptor<PtSubscriptionPlus> captor = ArgumentCaptor.forClass(PtSubscriptionPlus.class);
        verify(subscriptionService).save(captor.capture());
        PtSubscriptionPlus sub = captor.getValue();

        assertEquals("1396", sub.getTmdbId());
        assertEquals("TV", sub.getMediaType());
        assertEquals("绝命毒师", sub.getTitle());
        assertEquals("2008", sub.getYear());
        assertEquals(1, sub.getSeason());
        assertEquals(7, sub.getTotalEpisodes());
        assertEquals("ACTIVE", sub.getStatus());
        assertEquals("/p.jpg", sub.getPosterPath());
    }

    @Test
    void subscribe_剧集_全部已入库_直接置为已完成() throws Exception {
        when(tmdbSearchService.getDetail(anyString(), anyString())).thenReturn(detail("剧", "2020"));
        when(tmdbSearchService.getSeasonEpisodeCount(anyString(), anyInt())).thenReturn(3);
        stubSaveAssignsId(11);
        stubEmbyConfigured();
        when(mediaServerClient.listEpisodes(any(), anyString(), anyInt())).thenReturn(Set.of(1, 2, 3));

        service.subscribe(tvRequest());

        ArgumentCaptor<PtSubscriptionPlus> captor = ArgumentCaptor.forClass(PtSubscriptionPlus.class);
        verify(subscriptionService).save(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getStatus());
    }

    // ---------- 建订阅：电影 ----------

    @Test
    void subscribe_电影_季号0总集数1唯一集行为0() throws Exception {
        when(tmdbSearchService.getDetail(anyString(), anyString())).thenReturn(detail("搏击俱乐部", "1999"));
        stubSaveAssignsId(20);
        stubEmbyConfigured();
        when(mediaServerClient.hasMovie(any(), anyString())).thenReturn(false);

        service.subscribe(movieRequest());

        ArgumentCaptor<PtSubscriptionPlus> subCaptor = ArgumentCaptor.forClass(PtSubscriptionPlus.class);
        verify(subscriptionService).save(subCaptor.capture());
        assertEquals("MOVIE", subCaptor.getValue().getMediaType());
        assertEquals(0, subCaptor.getValue().getSeason());
        assertEquals(1, subCaptor.getValue().getTotalEpisodes());

        ArgumentCaptor<List<PtSubscriptionEpisodePlus>> epCaptor = ArgumentCaptor.forClass(List.class);
        verify(episodeService).saveBatch(epCaptor.capture());
        assertEquals(1, epCaptor.getValue().size());
        assertEquals(0, epCaptor.getValue().get(0).getEpisode());
        assertEquals("MISSING", epCaptor.getValue().get(0).getState());
    }

    @Test
    void subscribe_电影_不调用剧集的总集数接口() throws Exception {
        when(tmdbSearchService.getDetail(anyString(), anyString())).thenReturn(detail("片", "1999"));
        stubSaveAssignsId(21);
        when(mediaServerService.getActive()).thenReturn(null);

        service.subscribe(movieRequest());

        verify(tmdbSearchService, never()).getSeasonEpisodeCount(anyString(), anyInt());
    }

    @Test
    void subscribe_电影已在库_置为已完成() throws Exception {
        when(tmdbSearchService.getDetail(anyString(), anyString())).thenReturn(detail("片", "1999"));
        stubSaveAssignsId(22);
        stubEmbyConfigured();
        when(mediaServerClient.hasMovie(any(), anyString())).thenReturn(true);

        service.subscribe(movieRequest());

        ArgumentCaptor<PtSubscriptionPlus> captor = ArgumentCaptor.forClass(PtSubscriptionPlus.class);
        verify(subscriptionService).save(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getStatus());
    }

    // ---------- Emby 不可用 ----------

    @Test
    void subscribe_未配置媒体服务器_全部按缺失处理且订阅照常建成() throws Exception {
        when(tmdbSearchService.getDetail(anyString(), anyString())).thenReturn(detail("剧", "2020"));
        when(tmdbSearchService.getSeasonEpisodeCount(anyString(), anyInt())).thenReturn(3);
        stubSaveAssignsId(30);
        when(mediaServerService.getActive()).thenReturn(null);

        service.subscribe(tvRequest());

        ArgumentCaptor<List<PtSubscriptionEpisodePlus>> captor = ArgumentCaptor.forClass(List.class);
        verify(episodeService).saveBatch(captor.capture());
        assertTrue(captor.getValue().stream().allMatch(e -> "MISSING".equals(e.getState())));
    }

    @Test
    void subscribe_Emby查询抛IO异常_全部按缺失处理而非让建订阅失败() throws Exception {
        when(tmdbSearchService.getDetail(anyString(), anyString())).thenReturn(detail("剧", "2020"));
        when(tmdbSearchService.getSeasonEpisodeCount(anyString(), anyInt())).thenReturn(3);
        stubSaveAssignsId(31);
        stubEmbyConfigured();
        when(mediaServerClient.listEpisodes(any(), anyString(), anyInt())).thenThrow(new IOException("connection refused"));

        service.subscribe(tvRequest());

        ArgumentCaptor<List<PtSubscriptionEpisodePlus>> captor = ArgumentCaptor.forClass(List.class);
        verify(episodeService).saveBatch(captor.capture());
        assertTrue(captor.getValue().stream().allMatch(e -> "MISSING".equals(e.getState())));
    }

    // ---------- 入参校验 ----------

    @Test
    void subscribe_剧集未指定季_抛IllegalArgumentException() {
        SubscribeRequest req = tvRequest();
        req.setSeason(null);

        assertThrows(IllegalArgumentException.class, () -> service.subscribe(req));
    }

    @Test
    void subscribe_tmdbId为空_抛IllegalArgumentException() {
        SubscribeRequest req = tvRequest();
        req.setTmdbId("  ");

        assertThrows(IllegalArgumentException.class, () -> service.subscribe(req));
    }

    // ---------- 对账刷新 ----------

    @Test
    void refresh_把Emby已有的集升级为已入库() throws Exception {
        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setId(40);
        sub.setTmdbId("1396");
        sub.setMediaType("TV");
        sub.setSeason(1);
        sub.setTotalEpisodes(3);
        sub.setStatus("ACTIVE");
        when(subscriptionService.getById(40)).thenReturn(sub);
        when(episodeService.listBySubscription(40)).thenReturn(List.of(
                episode(1, "MISSING"), episode(2, "IN_FLIGHT"), episode(3, "MISSING")));
        when(tmdbSearchService.getSeasonEpisodeCount(anyString(), anyInt())).thenReturn(3);
        stubEmbyConfigured();
        when(mediaServerClient.listEpisodes(any(), anyString(), anyInt())).thenReturn(Set.of(1, 2));

        service.refresh(40);

        ArgumentCaptor<List<PtSubscriptionEpisodePlus>> captor = ArgumentCaptor.forClass(List.class);
        verify(episodeService).updateBatchById(captor.capture());
        assertEquals(List.of(1, 2), captor.getValue().stream().map(PtSubscriptionEpisodePlus::getEpisode).toList());
        assertTrue(captor.getValue().stream().allMatch(e -> "IN_LIBRARY".equals(e.getState())));
    }

    @Test
    void refresh_不把已入库降级回缺失() throws Exception {
        PtSubscriptionPlus sub = activeTv(41, 2);
        when(subscriptionService.getById(41)).thenReturn(sub);
        when(episodeService.listBySubscription(41)).thenReturn(List.of(
                episode(1, "IN_LIBRARY"), episode(2, "IN_LIBRARY")));
        when(tmdbSearchService.getSeasonEpisodeCount(anyString(), anyInt())).thenReturn(2);
        stubEmbyConfigured();
        // 用户从 Emby 删了第 2 集
        when(mediaServerClient.listEpisodes(any(), anyString(), anyInt())).thenReturn(Set.of(1));

        service.refresh(41);

        // 只升级不降级：不应有任何更新
        verify(episodeService, never()).updateBatchById(any());
    }

    @Test
    void refresh_总集数增加_补齐新集行并把已完成的订阅改回订阅中() throws Exception {
        PtSubscriptionPlus sub = activeTv(42, 2);
        sub.setStatus("COMPLETED");
        when(subscriptionService.getById(42)).thenReturn(sub);
        when(episodeService.listBySubscription(42)).thenReturn(List.of(
                episode(1, "IN_LIBRARY"), episode(2, "IN_LIBRARY")));
        // TMDb 那边这一季从 2 集涨到 4 集
        when(tmdbSearchService.getSeasonEpisodeCount(anyString(), anyInt())).thenReturn(4);
        when(mediaServerService.getActive()).thenReturn(null);

        service.refresh(42);

        ArgumentCaptor<List<PtSubscriptionEpisodePlus>> captor = ArgumentCaptor.forClass(List.class);
        verify(episodeService).saveBatch(captor.capture());
        assertEquals(List.of(3, 4), captor.getValue().stream().map(PtSubscriptionEpisodePlus::getEpisode).toList());

        ArgumentCaptor<PtSubscriptionPlus> subCaptor = ArgumentCaptor.forClass(PtSubscriptionPlus.class);
        verify(subscriptionService).updateById(subCaptor.capture());
        assertEquals("ACTIVE", subCaptor.getValue().getStatus());
        assertEquals(4, subCaptor.getValue().getTotalEpisodes());
    }

    @Test
    void refresh_全部入库_订阅置为已完成() throws Exception {
        PtSubscriptionPlus sub = activeTv(43, 2);
        when(subscriptionService.getById(43)).thenReturn(sub);
        when(episodeService.listBySubscription(43)).thenReturn(List.of(
                episode(1, "IN_LIBRARY"), episode(2, "MISSING")));
        when(tmdbSearchService.getSeasonEpisodeCount(anyString(), anyInt())).thenReturn(2);
        stubEmbyConfigured();
        when(mediaServerClient.listEpisodes(any(), anyString(), anyInt())).thenReturn(Set.of(1, 2));

        service.refresh(43);

        ArgumentCaptor<PtSubscriptionPlus> captor = ArgumentCaptor.forClass(PtSubscriptionPlus.class);
        verify(subscriptionService).updateById(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getStatus());
    }

    @Test
    void refresh_订阅不存在_抛IllegalArgumentException() {
        when(subscriptionService.getById(999)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.refresh(999));
    }

    // ---------- 进度 ----------

    @Test
    void getProgress_统计已入库在途与缺集列表() {
        PtSubscriptionPlus sub = activeTv(50, 5);
        sub.setTitle("某剧");
        when(subscriptionService.getById(50)).thenReturn(sub);
        when(episodeService.listBySubscription(50)).thenReturn(List.of(
                episode(1, "IN_LIBRARY"), episode(2, "IN_LIBRARY"),
                episode(3, "MISSING"), episode(4, "IN_FLIGHT"), episode(5, "MISSING")));

        SubscriptionProgress progress = service.getProgress(50);

        assertEquals(5, progress.getTotalEpisodes());
        assertEquals(2, progress.getInLibraryCount());
        assertEquals(1, progress.getInFlightCount());
        assertEquals(List.of(3, 5), progress.getMissingEpisodes());
        assertEquals("某剧", progress.getTitle());
    }

    // ---------- 暂停恢复 ----------

    @Test
    void pause_把订阅置为暂停() {
        when(subscriptionService.getById(60)).thenReturn(activeTv(60, 1));

        service.pause(60);

        ArgumentCaptor<PtSubscriptionPlus> captor = ArgumentCaptor.forClass(PtSubscriptionPlus.class);
        verify(subscriptionService).updateById(captor.capture());
        assertEquals("PAUSED", captor.getValue().getStatus());
    }

    @Test
    void resume_把订阅置回订阅中() {
        PtSubscriptionPlus sub = activeTv(61, 1);
        sub.setStatus("PAUSED");
        when(subscriptionService.getById(61)).thenReturn(sub);

        service.resume(61);

        ArgumentCaptor<PtSubscriptionPlus> captor = ArgumentCaptor.forClass(PtSubscriptionPlus.class);
        verify(subscriptionService).updateById(captor.capture());
        assertEquals("ACTIVE", captor.getValue().getStatus());
    }

    // ---------- 辅助 ----------

    private PtSubscriptionPlus activeTv(int id, int total) {
        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setId(id);
        sub.setTmdbId("1396");
        sub.setMediaType("TV");
        sub.setSeason(1);
        sub.setTotalEpisodes(total);
        sub.setStatus("ACTIVE");
        return sub;
    }

    private PtSubscriptionEpisodePlus episode(int number, String state) {
        PtSubscriptionEpisodePlus ep = new PtSubscriptionEpisodePlus();
        ep.setId(number * 100);
        ep.setEpisode(number);
        ep.setState(state);
        return ep;
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SubscriptionServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `SubscriptionService` / `SubscribeRequest` / `SubscriptionProgress` 找不到符号

- [ ] **步骤 3：编写两个 DTO**

创建 `pt/subscription/dto/SubscribeRequest.java`：

```java
package com.ruoyi.openliststrm.pt.subscription.dto;

import lombok.Data;

/**
 * 建订阅入参。
 *
 * @author Jack
 */
@Data
public class SubscribeRequest {

    /** TMDb ID */
    private String tmdbId;

    /** 媒体类型 TV / MOVIE */
    private String mediaType;

    /** 季号；剧集必填，电影忽略（服务端会写成哨兵值 0） */
    private Integer season;

    /** 指定下载器，可空 */
    private Integer downloaderId;

    /** 订阅级过滤覆盖(JSON)，可空 */
    private String filterOverride;
}
```

创建 `pt/subscription/dto/SubscriptionProgress.java`：

```java
package com.ruoyi.openliststrm.pt.subscription.dto;

import lombok.Data;

import java.util.List;

/**
 * 订阅进度，供前端展示「已入库 5/12，缺 3、7」。
 *
 * @author Jack
 */
@Data
public class SubscriptionProgress {

    private Integer subId;

    private String title;

    private String status;

    /** 总集数；电影恒为 1 */
    private int totalEpisodes;

    /** 已入库集数 */
    private int inLibraryCount;

    /** 在途（已推送下载器但尚未入库）集数 */
    private int inFlightCount;

    /** 仍缺失的集号，升序 */
    private List<Integer> missingEpisodes;
}
```

- [ ] **步骤 4：编写实现**

创建 `pt/subscription/SubscriptionService.java`：

```java
package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtMediaServerPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtMediaServerPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.media.MediaServerClientFactory;
import com.ruoyi.openliststrm.pt.subscription.dto.SubscribeRequest;
import com.ruoyi.openliststrm.pt.subscription.dto.SubscriptionProgress;
import com.ruoyi.openliststrm.pt.subscription.dto.TmdbSearchItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 订阅的建立、对账与状态维护。
 *
 * @author Jack
 */
@Slf4j
@Service
public class SubscriptionService {

    /** 媒体类型：电影。判断是否电影只看它，绝不能用 season == 0（剧集特别篇也是第 0 季） */
    public static final String TYPE_MOVIE = "MOVIE";

    public static final String STATE_MISSING = "MISSING";
    public static final String STATE_IN_FLIGHT = "IN_FLIGHT";
    public static final String STATE_IN_LIBRARY = "IN_LIBRARY";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_PAUSED = "PAUSED";

    /** 电影的哨兵季号与集号 */
    private static final int MOVIE_SEASON = 0;
    private static final int MOVIE_EPISODE = 0;

    @Autowired
    private IPtSubscriptionPlusService subscriptionService;
    @Autowired
    private IPtSubscriptionEpisodePlusService episodeService;
    @Autowired
    private IPtMediaServerPlusService mediaServerService;
    @Autowired
    private MediaServerClientFactory mediaServerClientFactory;
    @Autowired
    private TmdbSearchService tmdbSearchService;

    /**
     * 建订阅：查 TMDb 拿元信息与总集数 → 落库 → 生成每集行 → 立即查一次 Emby 初始化状态。
     * <p>
     * 因为最后一步，订阅创建完就能显示准确的「已入库 5/12」，不必等首次轮询。
     * Emby 不可用（未配置或查询失败）不阻断建订阅，按全部缺失处理。
     * </p>
     *
     * @throws IllegalArgumentException 入参非法，或 TMDb 查不到该作品
     */
    @Transactional
    public PtSubscriptionPlus subscribe(SubscribeRequest request) {
        if (StringUtils.isBlank(request.getTmdbId())) {
            throw new IllegalArgumentException("tmdbId 不能为空");
        }
        boolean movie = TYPE_MOVIE.equalsIgnoreCase(request.getMediaType());
        if (!movie && request.getSeason() == null) {
            throw new IllegalArgumentException("订阅剧集必须指定季号");
        }

        TmdbSearchItem detail = tmdbSearchService.getDetail(request.getMediaType(), request.getTmdbId());
        int season = movie ? MOVIE_SEASON : request.getSeason();
        int totalEpisodes = movie ? 1 : tmdbSearchService.getSeasonEpisodeCount(request.getTmdbId(), season);

        Set<Integer> inLibrary = queryLibrary(request.getMediaType(), request.getTmdbId(), season);

        PtSubscriptionPlus sub = new PtSubscriptionPlus();
        sub.setTmdbId(request.getTmdbId());
        sub.setMediaType(movie ? TYPE_MOVIE : "TV");
        sub.setTitle(detail.getTitle());
        sub.setYear(detail.getYear());
        sub.setSeason(season);
        sub.setTotalEpisodes(totalEpisodes);
        sub.setPosterPath(detail.getPosterPath());
        sub.setDownloaderId(request.getDownloaderId());
        sub.setFilterOverride(request.getFilterOverride());
        sub.setStatus(coversAll(inLibrary, movie, totalEpisodes) ? STATUS_COMPLETED : STATUS_ACTIVE);
        subscriptionService.save(sub);

        List<PtSubscriptionEpisodePlus> episodes = new ArrayList<>();
        for (int number : episodeNumbers(movie, totalEpisodes)) {
            PtSubscriptionEpisodePlus ep = new PtSubscriptionEpisodePlus();
            ep.setSubId(sub.getId());
            ep.setEpisode(number);
            ep.setState(inLibrary.contains(number) ? STATE_IN_LIBRARY : STATE_MISSING);
            episodes.add(ep);
        }
        episodeService.saveBatch(episodes);

        log.info("已建立订阅[{}] {} 共{}集，其中已入库{}集", sub.getId(), sub.getTitle(), totalEpisodes, inLibrary.size());
        return sub;
    }

    /**
     * 对账刷新：重新拉 TMDb 总集数补齐集行 → 查 Emby → 推进集状态 → 重算订阅状态。
     * <p>
     * <b>只升级不降级</b>：MISSING / IN_FLIGHT → IN_LIBRARY 会做，反向不做。
     * 若用户从 Emby 删了某集，不把它退回 MISSING——否则一次误删会触发一轮重新下载。
     * 代价是进度显示偏乐观，这是有意的取舍。
     * </p>
     *
     * @throws IllegalArgumentException 订阅不存在
     */
    @Transactional
    public void refresh(Integer subId) {
        PtSubscriptionPlus sub = requireSubscription(subId);
        boolean movie = TYPE_MOVIE.equalsIgnoreCase(sub.getMediaType());
        List<PtSubscriptionEpisodePlus> episodes = episodeService.listBySubscription(subId);

        int totalEpisodes = sub.getTotalEpisodes();
        if (!movie) {
            // 连载剧的总集数会增长，TMDb 对正在播出的季常先给一个偏小的值
            try {
                totalEpisodes = tmdbSearchService.getSeasonEpisodeCount(sub.getTmdbId(), sub.getSeason());
            } catch (Exception e) {
                log.warn("刷新订阅[{}]时取 TMDb 总集数失败，沿用原值 {}：{}", subId, totalEpisodes, e.getMessage());
            }
            appendNewEpisodes(sub, episodes, totalEpisodes);
        }

        Set<Integer> inLibrary = queryLibrary(sub.getMediaType(), sub.getTmdbId(), sub.getSeason());
        List<PtSubscriptionEpisodePlus> upgraded = new ArrayList<>();
        for (PtSubscriptionEpisodePlus ep : episodes) {
            if (inLibrary.contains(ep.getEpisode()) && !STATE_IN_LIBRARY.equals(ep.getState())) {
                ep.setState(STATE_IN_LIBRARY);
                upgraded.add(ep);
            }
        }
        if (!upgraded.isEmpty()) {
            episodeService.updateBatchById(upgraded);
        }

        boolean allInLibrary = episodes.stream().allMatch(e -> STATE_IN_LIBRARY.equals(e.getState()));
        String newStatus = allInLibrary ? STATUS_COMPLETED : STATUS_ACTIVE;
        boolean statusChanged = !newStatus.equals(sub.getStatus()) && !STATUS_PAUSED.equals(sub.getStatus());
        boolean totalChanged = totalEpisodes != sub.getTotalEpisodes();
        if (statusChanged || totalChanged) {
            sub.setStatus(STATUS_PAUSED.equals(sub.getStatus()) ? STATUS_PAUSED : newStatus);
            sub.setTotalEpisodes(totalEpisodes);
            subscriptionService.updateById(sub);
        }
    }

    /**
     * 查进度，供前端展示「已入库 N/M，缺第 X、Y 集」。
     *
     * @throws IllegalArgumentException 订阅不存在
     */
    public SubscriptionProgress getProgress(Integer subId) {
        PtSubscriptionPlus sub = requireSubscription(subId);
        List<PtSubscriptionEpisodePlus> episodes = episodeService.listBySubscription(subId);

        SubscriptionProgress progress = new SubscriptionProgress();
        progress.setSubId(sub.getId());
        progress.setTitle(sub.getTitle());
        progress.setStatus(sub.getStatus());
        progress.setTotalEpisodes(sub.getTotalEpisodes() == null ? episodes.size() : sub.getTotalEpisodes());
        progress.setInLibraryCount((int) episodes.stream().filter(e -> STATE_IN_LIBRARY.equals(e.getState())).count());
        progress.setInFlightCount((int) episodes.stream().filter(e -> STATE_IN_FLIGHT.equals(e.getState())).count());
        progress.setMissingEpisodes(episodes.stream()
                .filter(e -> STATE_MISSING.equals(e.getState()))
                .map(PtSubscriptionEpisodePlus::getEpisode)
                .sorted()
                .toList());
        return progress;
    }

    /** 暂停订阅，暂停期间不参与 RSS 匹配 */
    public void pause(Integer subId) {
        PtSubscriptionPlus sub = requireSubscription(subId);
        sub.setStatus(STATUS_PAUSED);
        subscriptionService.updateById(sub);
    }

    /** 恢复订阅 */
    public void resume(Integer subId) {
        PtSubscriptionPlus sub = requireSubscription(subId);
        sub.setStatus(STATUS_ACTIVE);
        subscriptionService.updateById(sub);
    }

    // ---------- 内部 ----------

    private PtSubscriptionPlus requireSubscription(Integer subId) {
        PtSubscriptionPlus sub = subscriptionService.getById(subId);
        if (sub == null) {
            throw new IllegalArgumentException("订阅不存在：" + subId);
        }
        return sub;
    }

    /**
     * 查媒体库中已有的集号。电影用集号 0 表示"已在库"。
     * <p>
     * 未配置媒体服务器或查询失败时返回空集合并记 warn——媒体服务器是加分项不是前置依赖。
     * </p>
     */
    private Set<Integer> queryLibrary(String mediaType, String tmdbId, int season) {
        PtMediaServerPlus server = mediaServerService.getActive();
        if (server == null) {
            log.warn("未配置启用中的媒体服务器，订阅 {} 的已入库集数按 0 处理", tmdbId);
            return Collections.emptySet();
        }
        try {
            if (TYPE_MOVIE.equalsIgnoreCase(mediaType)) {
                return mediaServerClientFactory.get(server).hasMovie(server, tmdbId)
                        ? Set.of(MOVIE_EPISODE) : Collections.emptySet();
            }
            return mediaServerClientFactory.get(server).listEpisodes(server, tmdbId, season);
        } catch (Exception e) {
            log.warn("查询媒体库失败，订阅 {} 的已入库集数按 0 处理：{}", tmdbId, e.getMessage());
            return Collections.emptySet();
        }
    }

    /** 总集数增长时补齐新集行，新集一律 MISSING */
    private void appendNewEpisodes(PtSubscriptionPlus sub, List<PtSubscriptionEpisodePlus> existing, int totalEpisodes) {
        int maxExisting = existing.stream().mapToInt(PtSubscriptionEpisodePlus::getEpisode).max().orElse(0);
        if (totalEpisodes <= maxExisting) {
            return;
        }
        List<PtSubscriptionEpisodePlus> added = new ArrayList<>();
        for (int number = maxExisting + 1; number <= totalEpisodes; number++) {
            PtSubscriptionEpisodePlus ep = new PtSubscriptionEpisodePlus();
            ep.setSubId(sub.getId());
            ep.setEpisode(number);
            ep.setState(STATE_MISSING);
            added.add(ep);
        }
        episodeService.saveBatch(added);
        existing.addAll(added);
        log.info("订阅[{}] 总集数由 {} 增至 {}，已补齐 {} 个新集", sub.getId(), maxExisting, totalEpisodes, added.size());
    }

    private List<Integer> episodeNumbers(boolean movie, int totalEpisodes) {
        if (movie) {
            return List.of(MOVIE_EPISODE);
        }
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= totalEpisodes; i++) {
            numbers.add(i);
        }
        return numbers;
    }

    private boolean coversAll(Set<Integer> inLibrary, boolean movie, int totalEpisodes) {
        return inLibrary.containsAll(episodeNumbers(movie, totalEpisodes));
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SubscriptionServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 18, Failures: 0, Errors: 0

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/subscription/ ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/subscription/
git commit -m "feat(pt): 新增订阅服务，含建订阅、Emby对账刷新与进度统计"
```

---
## 任务 3：菜单翻成显示

**文件：**
- 创建：`ruoyi-common/src/main/resources/sql/20260727-pt-subscription-menu.sql`
- 修改：`ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java`

> **背景：** 计划 2 插入菜单 2064（PT订阅）/ 2065（PT过滤规则）时把 `visible` 设成了 `'1'`（隐藏），因为当时页面还没建，放出来点进去是 404。本计划把页面建好，所以翻回显示。
>
> RuoYi 约定：`visible` 为 `'0'` 显示、`'1'` 隐藏。

- [ ] **步骤 1：编写迁移脚本**

创建 `ruoyi-common/src/main/resources/sql/20260727-pt-subscription-menu.sql`：

```sql
-- ----------------------------
-- 20260727: PT 订阅与过滤规则页面上线，把此前隐藏的两个菜单翻成显示
-- 计划2插入这两条菜单时页面尚未创建，visible 设为 '1'(隐藏) 以免用户点进 404。
-- UPDATE 天然幂等，重跑无副作用。
-- ----------------------------

UPDATE `sys_menu` SET `visible` = '0' WHERE `menu_id` IN (2064, 2065);
```

- [ ] **步骤 2：注册迁移脚本**

修改 `MysqlDdl.getSqlFiles()`，在列表**末尾**（`"sql/20260726-pt-filter-and-record-fix.sql"` 之后）追加：

```java
                "sql/20260726-pt-filter-and-record-fix.sql",
                "sql/20260727-pt-subscription-menu.sql"
        );
```

- [ ] **步骤 3：编译验证**

运行：`mvn -pl ruoyi-common -am clean compile -DskipTests`

预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add ruoyi-common/src/main/resources/sql/20260727-pt-subscription-menu.sql ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java
git commit -m "feat(pt): 订阅与过滤规则菜单翻为显示"
```

---

## 任务 4：订阅 REST 接口

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtSubscriptionRestController.java`

> **约定：** 继承 `BaseCrudRestController<S, T>` 复用 list/getById/add/edit/delete，子类实现 `buildQueryWrapper` 并追加业务端点。路径前缀 `/api/openliststrm/`。返回值用 `com.ruoyi.common.core.domain.Result`。**业务逻辑不写在 Controller 里**，全部转发给 `SubscriptionService` / `TmdbSearchService`。
>
> `SubscriptionService` 的方法在入参非法或订阅不存在时抛 `IllegalArgumentException`，Controller 必须 catch 转成 `Result.error(msg)`，不得冒泡成 500。
>
> **删除订阅时要连带删除它的集行**，否则 `pt_subscription_episode` 会残留孤儿数据。`BaseCrudRestController` 的 `delete` 只删主表，所以本类要覆写它。

- [ ] **步骤 1：编写 Controller**

创建 `PtSubscriptionRestController.java`：

```java
package com.ruoyi.openliststrm.controller.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import com.ruoyi.openliststrm.pt.subscription.SubscriptionService;
import com.ruoyi.openliststrm.pt.subscription.TmdbSearchService;
import com.ruoyi.openliststrm.pt.subscription.dto.SubscribeRequest;
import com.ruoyi.openliststrm.pt.subscription.dto.SubscriptionProgress;
import com.ruoyi.openliststrm.pt.subscription.dto.TmdbSearchItem;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PT 订阅 REST API 控制器
 *
 * @author Jack
 * @date 2026-07-27
 */
@RestController
@RequestMapping("/api/openliststrm/pt-subscriptions")
public class PtSubscriptionRestController extends BaseCrudRestController<IPtSubscriptionPlusService, PtSubscriptionPlus> {

    @Autowired
    private SubscriptionService subscriptionBiz;

    @Autowired
    private TmdbSearchService tmdbSearchService;

    @Autowired
    private IPtSubscriptionEpisodePlusService episodeService;

    @Override
    protected Wrapper<PtSubscriptionPlus> buildQueryWrapper(PtSubscriptionPlus entity) {
        LambdaQueryWrapper<PtSubscriptionPlus> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(entity.getTitle())) {
            wrapper.like(PtSubscriptionPlus::getTitle, entity.getTitle());
        }
        if (StringUtils.isNotBlank(entity.getMediaType())) {
            wrapper.eq(PtSubscriptionPlus::getMediaType, entity.getMediaType());
        }
        if (StringUtils.isNotBlank(entity.getStatus())) {
            wrapper.eq(PtSubscriptionPlus::getStatus, entity.getStatus());
        }
        wrapper.orderByDesc(PtSubscriptionPlus::getId);
        return wrapper;
    }

    /**
     * TMDb 搜索，供建订阅时选片。
     */
    @GetMapping("/tmdb-search")
    public Result<List<TmdbSearchItem>> tmdbSearch(@RequestParam("mediaType") String mediaType,
                                                   @RequestParam("keyword") String keyword) {
        return Result.success(tmdbSearchService.search(mediaType, keyword));
    }

    /**
     * 查某剧在 TMDb 上的各季集数，供选季。
     */
    @GetMapping("/tmdb-seasons/{tmdbId}")
    public Result<Integer> seasonEpisodeCount(@PathVariable("tmdbId") String tmdbId,
                                              @RequestParam("season") Integer season) {
        try {
            return Result.success(tmdbSearchService.getSeasonEpisodeCount(tmdbId, season));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 建订阅。
     */
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

    /**
     * 查订阅进度（已入库/在途/缺集列表）。
     */
    @GetMapping("/{id}/progress")
    public Result<SubscriptionProgress> progress(@PathVariable("id") Integer id) {
        try {
            return Result.success(subscriptionBiz.getProgress(id));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查订阅的每集明细。
     */
    @GetMapping("/{id}/episodes")
    public Result<List<PtSubscriptionEpisodePlus>> episodes(@PathVariable("id") Integer id) {
        return Result.success(episodeService.listBySubscription(id));
    }

    /**
     * 立即与媒体库对账刷新。
     */
    @PostMapping("/{id}/refresh")
    public Result<Void> refresh(@PathVariable("id") Integer id) {
        try {
            subscriptionBiz.refresh(id);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 暂停订阅。
     */
    @PostMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable("id") Integer id) {
        try {
            subscriptionBiz.pause(id);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 恢复订阅。
     */
    @PostMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable("id") Integer id) {
        try {
            subscriptionBiz.resume(id);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除订阅，连带删除其每集状态行。
     * <p>
     * 覆写基类实现：基类只删主表，会在 pt_subscription_episode 留下孤儿数据。
     * </p>
     */
    @Override
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        episodeService.remove(new QueryWrapper<PtSubscriptionEpisodePlus>().eq("sub_id", id));
        boolean removed = service.removeById(id);
        return removed ? Result.success() : Result.error("删除失败");
    }
}
```

- [ ] **步骤 2：编译并跑全量测试**

运行：`mvn -pl ruoyi-openliststrm -am test`

预期：BUILD SUCCESS，既有测试全绿

> 本任务无单元测试：Controller 是薄转发层，逻辑都在已被 `SubscriptionServiceTest` / `TmdbSearchServiceTest` 覆盖的服务里。唯一有自身逻辑的是 `delete` 的连带删除，将在任务 10 的端到端验收中人工确认。

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtSubscriptionRestController.java
git commit -m "feat(pt): 新增订阅 REST 接口，含 TMDb 搜索、建订阅、进度与对账"
```

---

## 任务 5：过滤配置 REST 接口

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtFilterConfigRestController.java`

> `pt_filter_config` 是单行配置表（id 恒为 1），不适合走 CRUD 基类，所以本类**不继承** `BaseCrudRestController`，只提供「读」「存」两个端点，仿 `RenameTemplateConfigRestController` 的形态。

- [ ] **步骤 1：编写 Controller**

创建 `PtFilterConfigRestController.java`：

```java
package com.ruoyi.openliststrm.controller.api;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtFilterConfigPlusService;
import com.ruoyi.openliststrm.pt.filter.SortDimension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * PT 全局过滤与排序规则 REST API 控制器。
 * <p>
 * pt_filter_config 是单行配置表，不走 CRUD 基类，只提供读与存两个端点。
 * </p>
 *
 * @author Jack
 * @date 2026-07-27
 */
@RestController
@RequestMapping("/api/openliststrm/pt-filter-config")
public class PtFilterConfigRestController extends BaseController {

    @Autowired
    private IPtFilterConfigPlusService filterConfigService;

    /**
     * 读取全局过滤规则。种子数据被误删时服务层会返回内置默认值，不会为 null。
     */
    @GetMapping
    public Result<PtFilterConfigPlus> get() {
        return Result.success(filterConfigService.getConfig());
    }

    /**
     * 可选的排序维度清单，供前端渲染拖拽/多选控件。
     */
    @GetMapping("/sort-dimensions")
    public Result<List<String>> sortDimensions() {
        return Result.success(Arrays.stream(SortDimension.values()).map(Enum::name).toList());
    }

    /**
     * 保存全局过滤规则。强制写 id=1，避免前端漏传主键导致插出第二行。
     */
    @PutMapping
    public Result<Void> save(@RequestBody PtFilterConfigPlus config) {
        config.setId(PtFilterConfigPlus.SINGLETON_ID);
        boolean ok = filterConfigService.saveOrUpdate(config);
        return ok ? Result.success() : Result.error("保存失败");
    }
}
```

- [ ] **步骤 2：编译验证**

运行：`mvn -pl ruoyi-openliststrm -am test`

预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtFilterConfigRestController.java
git commit -m "feat(pt): 新增全局过滤规则读写接口"
```

---

## 任务 6：前端 API 封装

**文件：**
- 创建：`openlist-web/src/api/openlist/ptSubscription.ts`
- 创建：`openlist-web/src/api/openlist/ptFilterConfig.ts`

> **约定（严格遵守，参照 `openlist-web/src/api/openlist/ptIndexer.ts`）：** 用 `request.get` / `post` / `put` / `delete` **方法形式**；命名 `getXxxListApi` / `addXxxApi` / `updateXxxApi` / `deleteXxxApi`；列表接口标注 `request.get<any, PageResult<any>>(...)`；从 `@/types` 导入 `PageResult` / `SearchParams`；路径不带 `/api` 前缀。

- [ ] **步骤 1：编写订阅 API**

创建 `openlist-web/src/api/openlist/ptSubscription.ts`：

```typescript
import request from '@/api/request'
import type { PageResult, SearchParams } from '@/types'

export function getPtSubscriptionListApi(params: SearchParams) {
  return request.get<any, PageResult<any>>('/openliststrm/pt-subscriptions', { params })
}

export function addPtSubscriptionApi(data: any) {
  return request.post('/openliststrm/pt-subscriptions', data)
}

export function updatePtSubscriptionApi(data: any) {
  return request.put('/openliststrm/pt-subscriptions', data)
}

export function deletePtSubscriptionApi(id: number) {
  return request.delete(`/openliststrm/pt-subscriptions/${id}`)
}

/** TMDb 搜索，供建订阅时选片 */
export function tmdbSearchApi(mediaType: string, keyword: string) {
  return request.get<any, any[]>('/openliststrm/pt-subscriptions/tmdb-search', {
    params: { mediaType, keyword }
  })
}

/** 查某剧指定季在 TMDb 上的总集数 */
export function tmdbSeasonEpisodeCountApi(tmdbId: string, season: number) {
  return request.get<any, number>(`/openliststrm/pt-subscriptions/tmdb-seasons/${tmdbId}`, {
    params: { season }
  })
}

/** 建订阅 */
export function subscribeApi(data: any) {
  return request.post('/openliststrm/pt-subscriptions/subscribe', data)
}

/** 查订阅进度 */
export function getSubscriptionProgressApi(id: number) {
  return request.get<any, any>(`/openliststrm/pt-subscriptions/${id}/progress`)
}

/** 查订阅的每集明细 */
export function getSubscriptionEpisodesApi(id: number) {
  return request.get<any, any[]>(`/openliststrm/pt-subscriptions/${id}/episodes`)
}

/** 立即与媒体库对账刷新 */
export function refreshSubscriptionApi(id: number) {
  return request.post(`/openliststrm/pt-subscriptions/${id}/refresh`)
}

/** 暂停订阅 */
export function pauseSubscriptionApi(id: number) {
  return request.post(`/openliststrm/pt-subscriptions/${id}/pause`)
}

/** 恢复订阅 */
export function resumeSubscriptionApi(id: number) {
  return request.post(`/openliststrm/pt-subscriptions/${id}/resume`)
}
```

- [ ] **步骤 2：编写过滤配置 API**

创建 `openlist-web/src/api/openlist/ptFilterConfig.ts`：

```typescript
import request from '@/api/request'

/** PT 全局过滤与排序配置 */
export interface PtFilterConfig {
  id?: number
  minSeeders?: number
  minSize?: number
  maxSize?: number
  /** 是否仅下载免费种 0-否 1-是 */
  freeOnly?: string
  includeKeywords?: string
  excludeKeywords?: string
  /** 分辨率优先级，逗号分隔，只影响排序 */
  resolutionPriority?: string
  /** 分辨率白名单，逗号分隔，硬性过滤；空表示不限 */
  resolutionWhitelist?: string
  /** 排序维度顺序，逗号分隔 */
  sortPriority?: string
  preferredSize?: number
}

export function getPtFilterConfigApi() {
  return request.get<any, PtFilterConfig>('/openliststrm/pt-filter-config')
}

export function updatePtFilterConfigApi(data: PtFilterConfig) {
  return request.put('/openliststrm/pt-filter-config', data)
}

/** 可选的排序维度清单 */
export function getSortDimensionsApi() {
  return request.get<any, string[]>('/openliststrm/pt-filter-config/sort-dimensions')
}
```

- [ ] **步骤 3：类型检查**

运行：`cd openlist-web && npm run build`

预期：vue-tsc 通过

- [ ] **步骤 4：Commit**

```bash
git add openlist-web/src/api/openlist/ptSubscription.ts openlist-web/src/api/openlist/ptFilterConfig.ts
git commit -m "feat(pt): 新增订阅与过滤规则前端 API 封装"
```

---
## 任务 7：前端 composable

**文件：**
- 创建：`openlist-web/src/composables/usePtSubscription.ts`
- 创建：`openlist-web/src/composables/usePtFilterConfig.ts`

> **约定：** 参照 `openlist-web/src/composables/usePtIndexer.ts`。业务逻辑放 composable，页面只渲染（前端 AGENTS.md 反模式条款）。**错误提示由 axios 拦截器统一弹出，catch 里不要再弹一次**（会重复两条）。`useTaskList` 的 `executeApi` 已是可选，配置类列表不传。

- [ ] **步骤 1：编写订阅 composable**

创建 `openlist-web/src/composables/usePtSubscription.ts`：

```typescript
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTaskList } from './useTaskList'
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
import type { SearchParams } from '@/types'

interface PtSubscriptionQuery extends SearchParams {
  title?: string
  mediaType?: string
  status?: string
}

/**
 * PT 订阅 composable
 */
export function usePtSubscription() {
  const base = useTaskList<PtSubscriptionQuery>({
    listApi: getPtSubscriptionListApi,
    addApi: addPtSubscriptionApi,
    updateApi: updatePtSubscriptionApi,
    deleteApi: deletePtSubscriptionApi,
    idField: 'id',
    initForm: () => ({ id: undefined }),
    rules: {},
    defaultQuery: { title: undefined, mediaType: undefined, status: undefined }
  })

  // ---------- 建订阅向导 ----------

  const subscribeOpen = ref(false)
  const searchLoading = ref(false)
  const subscribeLoading = ref(false)
  const searchResults = ref<any[]>([])

  const searchForm = reactive({
    mediaType: 'TV',
    keyword: ''
  })

  /** 当前选中的候选作品 */
  const picked = ref<any>(null)
  /** 剧集才需要选季 */
  const pickedSeason = ref<number>(1)

  const openSubscribeDialog = () => {
    searchForm.mediaType = 'TV'
    searchForm.keyword = ''
    searchResults.value = []
    picked.value = null
    pickedSeason.value = 1
    subscribeOpen.value = true
  }

  const doSearch = async () => {
    if (!searchForm.keyword?.trim()) {
      ElMessage.warning('请输入片名')
      return
    }
    searchLoading.value = true
    try {
      searchResults.value = (await tmdbSearchApi(searchForm.mediaType, searchForm.keyword)) || []
      if (!searchResults.value.length) {
        ElMessage.info('没有搜到结果，换个关键词试试')
      }
    } catch (e) {
      // 拦截器已弹过错误提示，这里只记录
      console.error(e)
    } finally {
      searchLoading.value = false
    }
  }

  const pick = (item: any) => {
    picked.value = item
    pickedSeason.value = 1
  }

  const confirmSubscribe = async () => {
    if (!picked.value) {
      ElMessage.warning('请先选择一部作品')
      return
    }
    subscribeLoading.value = true
    try {
      await subscribeApi({
        tmdbId: picked.value.tmdbId,
        mediaType: searchForm.mediaType,
        season: searchForm.mediaType === 'MOVIE' ? undefined : pickedSeason.value
      })
      ElMessage.success('订阅成功')
      subscribeOpen.value = false
      base.getList()
    } catch (e) {
      console.error(e)
    } finally {
      subscribeLoading.value = false
    }
  }

  // ---------- 进度 ----------

  const progressOpen = ref(false)
  const progressLoading = ref(false)
  const progress = ref<any>(null)

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

  // ---------- 行操作 ----------

  const handleRefresh = async (row: any) => {
    try {
      await refreshSubscriptionApi(row.id)
      ElMessage.success('已与媒体库对账')
      base.getList()
    } catch (e) {
      console.error(e)
    }
  }

  const handlePause = async (row: any) => {
    try {
      await pauseSubscriptionApi(row.id)
      ElMessage.success('已暂停')
      base.getList()
    } catch (e) {
      console.error(e)
    }
  }

  const handleResume = async (row: any) => {
    try {
      await resumeSubscriptionApi(row.id)
      ElMessage.success('已恢复')
      base.getList()
    } catch (e) {
      console.error(e)
    }
  }

  const handleRemove = async (row: any) => {
    try {
      await ElMessageBox.confirm(
        `确认删除订阅「${row.title}」？其集数追踪记录会一并删除。`,
        '警告',
        { type: 'warning' }
      )
      await deletePtSubscriptionApi(row.id)
      ElMessage.success('删除成功')
      base.getList()
    } catch (e) {
      if (e !== 'cancel') console.error(e)
    }
  }

  base.getList()

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
}
```

- [ ] **步骤 2：编写过滤配置 composable**

创建 `openlist-web/src/composables/usePtFilterConfig.ts`：

```typescript
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getPtFilterConfigApi,
  updatePtFilterConfigApi,
  getSortDimensionsApi,
  type PtFilterConfig
} from '@/api/openlist/ptFilterConfig'

/** 各排序维度的中文说明，键必须与后端 SortDimension 枚举名一致 */
const DIMENSION_LABELS: Record<string, string> = {
  RESOLUTION: '分辨率优先级',
  FREE: '促销优先（计量系数越低越优）',
  SEEDERS: '做种数（多者优先）',
  SIZE: '体积接近偏好值'
}

/**
 * PT 全局过滤与排序规则 composable
 */
export function usePtFilterConfig() {
  const loading = ref(false)
  const saving = ref(false)
  const formRef = ref<any>()

  const form = reactive<PtFilterConfig>({
    minSeeders: 1,
    minSize: 0,
    maxSize: 0,
    freeOnly: '0',
    includeKeywords: '',
    excludeKeywords: '',
    resolutionPriority: '',
    resolutionWhitelist: '',
    sortPriority: '',
    preferredSize: 0
  })

  /** 排序维度的当前顺序，用有序数组承载，提交时拼成逗号分隔串 */
  const sortOrder = ref<string[]>([])
  const allDimensions = ref<string[]>([])

  const rules = {
    minSeeders: [{ required: true, message: '最低做种数不能为空', trigger: 'blur' }]
  }

  const labelOf = (dimension: string) => DIMENSION_LABELS[dimension] || dimension

  const load = async () => {
    loading.value = true
    try {
      const [config, dimensions] = await Promise.all([
        getPtFilterConfigApi(),
        getSortDimensionsApi()
      ])
      Object.assign(form, config)
      allDimensions.value = dimensions || []
      // 已配置的在前保持原顺序，未出现在配置里的补到末尾，避免新增维度后消失
      const configured = (config.sortPriority || '')
        .split(',')
        .map((s: string) => s.trim())
        .filter((s: string) => s && allDimensions.value.includes(s))
      const rest = allDimensions.value.filter((d) => !configured.includes(d))
      sortOrder.value = [...configured, ...rest]
    } catch (e) {
      console.error(e)
    } finally {
      loading.value = false
    }
  }

  /** 把某个维度上移一位 */
  const moveUp = (index: number) => {
    if (index <= 0) return
    const arr = sortOrder.value
    ;[arr[index - 1], arr[index]] = [arr[index], arr[index - 1]]
  }

  /** 把某个维度下移一位 */
  const moveDown = (index: number) => {
    const arr = sortOrder.value
    if (index >= arr.length - 1) return
    ;[arr[index], arr[index + 1]] = [arr[index + 1], arr[index]]
  }

  const save = async () => {
    if (formRef.value) {
      await formRef.value.validate()
    }
    saving.value = true
    try {
      await updatePtFilterConfigApi({ ...form, sortPriority: sortOrder.value.join(',') })
      ElMessage.success('保存成功')
      await load()
    } catch (e) {
      console.error(e)
    } finally {
      saving.value = false
    }
  }

  load()

  return { loading, saving, formRef, form, rules, sortOrder, allDimensions, labelOf, moveUp, moveDown, load, save }
}
```

- [ ] **步骤 3：类型检查与 lint**

运行：`cd openlist-web && npm run lint && npm run build`

预期：均通过

- [ ] **步骤 4：Commit**

```bash
git add openlist-web/src/composables/usePtSubscription.ts openlist-web/src/composables/usePtFilterConfig.ts
git commit -m "feat(pt): 新增订阅与过滤规则的 composable"
```

---

## 任务 8：订阅页面

**文件：**
- 创建：`openlist-web/src/views/openlist/ptSubscription/index.vue`
- 修改：`openlist-web/src/router/index.ts`

> **约定：** 以 `openlist-web/src/views/openlist/ptMediaServer/index.vue` 为骨架（同一批做的配置页），复用 `page-container` / `search-card` / `table-card` / `action-bar` / `modern-table` / `modern-dialog` / `pagination-wrapper` 类名，样式由全局 SCSS 提供。自动导入已开启，Element Plus 组件与 Vue API 无需手动 import，composable 要 import。不做移动端版本。

- [ ] **步骤 1：编写页面**

创建 `openlist-web/src/views/openlist/ptSubscription/index.vue`：

```vue
<template>
  <div class="page-container">
    <!-- 搜索 -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="queryParams.title" placeholder="请输入标题" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="类型" prop="mediaType">
          <el-select v-model="queryParams.mediaType" placeholder="类型" clearable :style="{ width: '120px' }">
            <el-option label="剧集" value="TV" />
            <el-option label="电影" value="MOVIE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="状态" clearable :style="{ width: '130px' }">
            <el-option label="订阅中" value="ACTIVE" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已暂停" value="PAUSED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon> 搜索
          </el-button>
          <el-button @click="resetQuery">
            <el-icon><Refresh /></el-icon> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="table-card">
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" @click="openSubscribeDialog">
            <el-icon><Plus /></el-icon> 新增订阅
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="taskList" class="modern-table">
        <el-table-column label="标题" min-width="220" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.title }}
            <span v-if="scope.row.year" class="sub-year">({{ scope.row.year }})</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="scope">
            {{ scope.row.mediaType === 'MOVIE' ? '电影' : '剧集' }}
          </template>
        </el-table-column>
        <el-table-column label="季" width="70" align="center">
          <template #default="scope">
            {{ scope.row.mediaType === 'MOVIE' ? '-' : 'S' + scope.row.season }}
          </template>
        </el-table-column>
        <el-table-column label="总集数" prop="totalEpisodes" width="90" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 'ACTIVE'" type="success">订阅中</el-tag>
            <el-tag v-else-if="scope.row.status === 'COMPLETED'" type="info">已完成</el-tag>
            <el-tag v-else type="warning">已暂停</el-tag>
          </template>
        </el-table-column>
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
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="getList"
          @size-change="getList"
        />
      </div>
    </el-card>

    <!-- 新增订阅：TMDb 选片 -->
    <el-dialog v-model="subscribeOpen" title="新增订阅" width="720px" append-to-body class="modern-dialog">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="类型">
          <el-select v-model="searchForm.mediaType" :style="{ width: '110px' }">
            <el-option label="剧集" value="TV" />
            <el-option label="电影" value="MOVIE" />
          </el-select>
        </el-form-item>
        <el-form-item label="片名">
          <el-input v-model="searchForm.keyword" placeholder="输入片名后回车" :style="{ width: '280px' }" @keyup.enter="doSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="searchLoading" @click="doSearch">搜索 TMDb</el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="searchLoading"
        :data="searchResults"
        height="300"
        highlight-current-row
        @current-change="pick"
      >
        <el-table-column label="标题" min-width="200" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.title }}
            <span v-if="scope.row.originalTitle && scope.row.originalTitle !== scope.row.title" class="sub-year">
              / {{ scope.row.originalTitle }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="年份" prop="year" width="80" align="center">
          <template #default="scope">{{ scope.row.year || '-' }}</template>
        </el-table-column>
        <el-table-column label="TMDb ID" prop="tmdbId" width="100" align="center" />
      </el-table>

      <div v-if="picked" class="picked-bar">
        已选：<strong>{{ picked.title }}</strong>
        <template v-if="searchForm.mediaType !== 'MOVIE'">
          &nbsp;第
          <el-input-number v-model="pickedSeason" :min="0" :max="99" size="small" :style="{ width: '110px' }" />
          季
          <span class="sub-year">（第 0 季是特别篇）</span>
        </template>
      </div>

      <template #footer>
        <el-button @click="subscribeOpen = false">取消</el-button>
        <el-button type="primary" :loading="subscribeLoading" :disabled="!picked" @click="confirmSubscribe">
          订阅
        </el-button>
      </template>
    </el-dialog>

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
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { usePtSubscription } from '@/composables/usePtSubscription'

const showSearch = ref(window.innerWidth >= 768)

const {
  taskList, loading, total, queryParams, getList, handleQuery, resetQuery, queryRef,
  subscribeOpen, searchLoading, subscribeLoading, searchResults, searchForm,
  picked, pickedSeason, openSubscribeDialog, doSearch, pick, confirmSubscribe,
  progressOpen, progressLoading, progress, showProgress,
  handleRefresh, handlePause, handleResume, handleRemove
} = usePtSubscription()
</script>

<style scoped>
.sub-year {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.picked-bar {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.progress-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}

.all-done {
  color: var(--el-color-success);
}
</style>
```

- [ ] **步骤 2：注册路由**

修改 `openlist-web/src/router/index.ts`，在组件映射表末尾追加（注意给原末项补逗号）：

```typescript
  'openlist/ptMediaServer/index': () => import('@/views/openlist/ptMediaServer/index.vue'),
  'openlist/ptSubscription/index': () => import('@/views/openlist/ptSubscription/index.vue'),
  'openlist/ptFilterConfig/index': () => import('@/views/openlist/ptFilterConfig/index.vue')
```

（`ptFilterConfig` 的页面在下一个任务创建；两条一起加，避免改两次同一个文件。**因此本任务的 build 会在下一个任务完成前报找不到模块——把 build 验证放到下一个任务末尾统一做。**）

- [ ] **步骤 3：lint**

运行：`cd openlist-web && npm run lint`

预期：无错误（lint 不解析动态 import 的目标文件，所以此时可以通过）

- [ ] **步骤 4：Commit**

```bash
git add openlist-web/src/views/openlist/ptSubscription openlist-web/src/router/index.ts
git commit -m "feat(pt): 新增订阅页面与路由注册"
```

---

## 任务 9：过滤规则配置页面

**文件：**
- 创建：`openlist-web/src/views/openlist/ptFilterConfig/index.vue`

> 这是单表单页（不是列表页），参照 `openlist-web/src/views/openlist/renameConfig/index.vue` 的形态。
>
> **一处必须讲清楚的 UI 文案**：`resolutionPriority` 与 `resolutionWhitelist` 名字像但作用完全不同——前者只影响**排序**（不在列表里的分辨率只是排最后，仍会被下载），后者是**硬性过滤**（不在白名单里的直接淘汰）。表单里必须写明，否则用户配了 priority 却以为能过滤掉 720p。

- [ ] **步骤 1：编写页面**

创建 `openlist-web/src/views/openlist/ptFilterConfig/index.vue`：

```vue
<template>
  <div class="page-container">
    <el-card v-loading="loading" class="table-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px" :style="{ maxWidth: '760px' }">
        <el-divider content-position="left">硬性过滤（不满足即淘汰）</el-divider>

        <el-form-item label="最低做种数" prop="minSeeders">
          <el-input-number v-model="form.minSeeders" :min="0" :style="{ width: '200px' }" />
          <span class="form-tip">做种数低于此值的种子直接淘汰</span>
        </el-form-item>

        <el-form-item label="体积下限">
          <el-input-number v-model="form.minSize" :min="0" :step="1073741824" :style="{ width: '240px' }" />
          <span class="form-tip">字节，0 表示不限</span>
        </el-form-item>

        <el-form-item label="体积上限">
          <el-input-number v-model="form.maxSize" :min="0" :step="1073741824" :style="{ width: '240px' }" />
          <span class="form-tip">字节，0 表示不限</span>
        </el-form-item>

        <el-form-item label="仅要免费种">
          <el-radio-group v-model="form.freeOnly">
            <el-radio value="0">否</el-radio>
            <el-radio value="1">是</el-radio>
          </el-radio-group>
          <span class="form-tip">开启后 50% 促销种也会被淘汰，只留完全免费的</span>
        </el-form-item>

        <el-form-item label="分辨率白名单">
          <el-input v-model="form.resolutionWhitelist" placeholder="如 2160p,1080p；留空表示不限" />
          <span class="form-tip">
            <strong>硬性过滤</strong>：不在白名单内的分辨率直接淘汰。解析不出分辨率的种子在白名单非空时也会被淘汰
          </span>
        </el-form-item>

        <el-form-item label="标题包含词">
          <el-input v-model="form.includeKeywords" placeholder="逗号分隔，命中其一即可；留空表示不限" />
        </el-form-item>

        <el-form-item label="标题排除词">
          <el-input v-model="form.excludeKeywords" placeholder="逗号分隔，命中任一即淘汰" />
        </el-form-item>

        <el-divider content-position="left">择优排序（从存活的候选里挑一个）</el-divider>

        <el-form-item label="分辨率优先级">
          <el-input v-model="form.resolutionPriority" placeholder="如 2160p,1080p,720p" />
          <span class="form-tip">
            <strong>只影响排序</strong>，不做过滤——不在此列表内的分辨率只是排在最后，仍可能被下载。要过滤请用上面的白名单
          </span>
        </el-form-item>

        <el-form-item label="偏好体积">
          <el-input-number v-model="form.preferredSize" :min="0" :step="1073741824" :style="{ width: '240px' }" />
          <span class="form-tip">字节，0 表示体积不参与择优比较</span>
        </el-form-item>

        <el-form-item label="维度优先顺序">
          <div class="dimension-list">
            <div v-for="(dimension, index) in sortOrder" :key="dimension" class="dimension-row">
              <span class="dimension-index">{{ index + 1 }}</span>
              <span class="dimension-label">{{ labelOf(dimension) }}</span>
              <el-button link :disabled="index === 0" @click="moveUp(index)">上移</el-button>
              <el-button link :disabled="index === sortOrder.length - 1" @click="moveDown(index)">下移</el-button>
            </div>
          </div>
          <span class="form-tip">
            排在前面的维度先比较。例如把「促销优先」放到「分辨率优先级」之前，就表示宁可要免费的 1080p，也不要收费的 4K
          </span>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
          <el-button @click="load">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { usePtFilterConfig } from '@/composables/usePtFilterConfig'

const { loading, saving, formRef, form, rules, sortOrder, labelOf, moveUp, moveDown, load, save } =
  usePtFilterConfig()
</script>

<style scoped>
.dimension-list {
  width: 100%;
}

.dimension-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.dimension-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--el-fill-color);
  font-size: 12px;
}

.dimension-label {
  min-width: 220px;
}
</style>
```

- [ ] **步骤 2：lint 与构建验证**

运行：`cd openlist-web && npm run lint && npm run build && npm run test:unit`

预期：lint 无错误；vue-tsc 通过（此时两个页面都已存在，任务 8 注册的路由能解析）；既有单测全绿

- [ ] **步骤 3：Commit**

```bash
git add openlist-web/src/views/openlist/ptFilterConfig
git commit -m "feat(pt): 新增过滤规则配置页面"
```

---

## 任务 10：端到端验收

**文件：** 无代码变更

- [ ] **步骤 1：全量测试与构建**

运行：

```bash
mvn -pl ruoyi-openliststrm -am test
mvn clean package -DskipTests
cd openlist-web && npm run build && npm run test:unit
```

预期：均 BUILD SUCCESS，后端测试总数在 214 基础上增加约 30（TmdbSearchService 12 + SubscriptionService 18）

- [ ] **步骤 2：部署**

运行：`docker compose up -d --build --no-deps backend && docker compose up -d --build --no-deps frontend`

（MySQL 不动。）

- [ ] **步骤 3：验证菜单已翻显示**

运行：

```bash
docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" osr -e "select menu_id, visible from sys_menu where menu_id in (2064,2065);"
```

预期：两条的 `visible` 均为 `0`

- [ ] **步骤 4：页面验收（需登录，人工）**

1. 菜单出现「PT订阅」「PT过滤规则」且**图标正常显示**
2. 进「PT过滤规则」：能读到当前配置；把「促销优先」上移到第一位并保存；刷新页面确认顺序被持久化
3. 进「PT订阅」→ 新增订阅 → 搜一部**已在 Emby 里有的剧** → 选季 → 订阅
4. 点「进度」，确认「已入库 N/M」与 Emby 里的实际集数**一致**（这是验证 Emby 对账真的通了）
5. 再订阅一部 Emby 里没有的剧，确认显示「已入库 0/M，仍缺第 1、2…集」
6. 点「对账」，确认无报错
7. 暂停 → 恢复 → 删除，确认操作生效

- [ ] **步骤 5：验证删除订阅连带删除集行**

删除一个订阅后运行：

```bash
docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" osr -e "
select s.id as sub_id, (select count(*) from pt_subscription_episode e where e.sub_id = s.id) as ep_rows from pt_subscription s;
select count(*) as orphan_rows from pt_subscription_episode where sub_id not in (select id from pt_subscription);"
```

预期：`orphan_rows` 为 0

- [ ] **步骤 6：Commit（如有修正）**

```bash
git add -A
git commit -m "fix(pt): 修正订阅页面验收中发现的问题"
```

---

## 后续计划

**计划 4：编排与调度** — `MediaParser` 的纯本地解析入口（不触发 TMDb/AI）、`SubscriptionEngine`（RSS 条目 → 匹配订阅 → 分组 → 过滤择优 → 推送）、`RssPollTask` / `DownloadTrackTask` / `LibrarySyncTask` 三个调度器、Telegram 通知。

**开工前必读**规格附录 A–H，其中这几条直接决定计划 4 的实现：

- 附录 A：季包用 `episode = -1`，推送时把该订阅所有 MISSING 集共同指向这条记录
- 附录 B：择优**前**要剔除本订阅本集已有下载记录的候选 guid；FAILED 的 guid 不再重试
- 附录 C：`tracking_tag` 在插入前生成（`osr-pt-` + guid_hash 前 16 位），不依赖自增 id
- 附录 F：判断电影只看 `media_type`，绝不能用 `season == 0`
- 附录 G：多索引器并发轮询要用 `UPDATE ... WHERE state='MISSING'` 的条件更新原子占位；`LibrarySyncTask` 直接调用本计划的 `SubscriptionService.refresh(subId)` 即可，总集数刷新与状态重算已在里面
