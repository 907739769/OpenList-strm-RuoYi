# PT 订阅 计划1：外部客户端与配置基座 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 打通 Torznab 索引器、qBittorrent 下载器、Emby/Jellyfin 媒体服务器三个外部系统的连接层，并提供可视化配置页面，使用户能在页面上配置并测试三者连通性。

**架构：** 新增 `com.ruoyi.openliststrm.pt` 包。三个外部系统各自一个客户端类，均基于项目已有的共享 `OkHttpClient`；下载器与媒体服务器通过接口抽象（`IDownloaderClient` / `IMediaServerClient`）+ 工厂分发，为未来接入 Transmission / 其他媒体服务器预留。三张配置表沿用项目既有的 MyBatis-Plus `XxxPlus` 实体规范与 `BaseCrudRestController` CRUD 基类。`TorrentInfo` 作为贯穿后续计划的统一种子模型在本计划中定义。

**技术栈：** Java 25 (Spring Boot 4.0.6) / MyBatis-Plus / OkHttp 4.12 / FastJSON2 / JDK 内置 DOM 解析 / JUnit 5 + MockWebServer / Vue 3 + Element Plus / vitest（本计划引入）

**执行约定：** 直接在 `dev` 分支上提交，不另开 feature 分支或 worktree。

**上游规格：** `docs/superpowers/specs/2026-07-21-pt-subscription-download-design.md`

**本计划不包含：** 订阅实体、集数追踪、过滤引擎、任何调度器。它们属于计划 2 与计划 3。

---

## 规格偏差说明

规格 §3 中 `pt_downloader.password` 标注为「Cipher 加密」。经核查代码库不存在任何加密工具类，现有的 `openlist.server.token`、`openlist.tg.token`、`openlist.tmdb.apikey` 均以明文存储于 `sys_config`。本计划遵循既有约定采用明文存储，不新引入加密体系（YAGNI）。规格文件已同步修正。

---

## 文件结构

### 后端新增

| 文件 | 职责 |
|---|---|
| `ruoyi-common/src/main/resources/sql/20260724-pt-base.sql` | 三张配置表 DDL + 菜单种子数据 |
| `pt/model/TorrentInfo.java` | 统一种子模型，贯穿全流程 |
| `pt/indexer/TorznabParser.java` | 纯函数：Torznab XML → `List<TorrentInfo>`，无 IO |
| `pt/indexer/TorznabClient.java` | HTTP 拉取 + 委托 Parser |
| `pt/downloader/model/DownloaderTorrent.java` | 下载器中一个种子的状态快照 |
| `pt/downloader/IDownloaderClient.java` | 下载器抽象接口 |
| `pt/downloader/QbittorrentClient.java` | qBittorrent Web API v2 实现 |
| `pt/downloader/DownloaderClientFactory.java` | 按 `type` 分发到具体实现 |
| `pt/media/IMediaServerClient.java` | 媒体服务器抽象接口 |
| `pt/media/EmbyClient.java` | Emby / Jellyfin 实现（两者 API 同源） |
| `pt/media/MediaServerClientFactory.java` | 按 `type` 分发 |
| `mybatisplus/domain/PtIndexerPlus.java` 等 3 个 | 实体 |
| `mybatisplus/mapper/PtIndexerPlusMapper.java` 等 3 个 | Mapper |
| `mybatisplus/service/IPtIndexerPlusService.java` 等 3 个 + impl | Service |
| `controller/api/PtIndexerRestController.java` 等 3 个 | CRUD + 连通性测试端点 |

### 后端修改

| 文件 | 修改内容 |
|---|---|
| `ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java` | 注册新迁移脚本 |
| `ruoyi-openliststrm/pom.xml` | 新增 mockwebserver test 依赖 |

### 前端新增

| 文件 | 职责 |
|---|---|
| `openlist-web/src/api/openlist/ptIndexer.ts` 等 3 个 | API 封装 |
| `openlist-web/src/composables/usePtIndexer.ts` 等 3 个 | 列表 CRUD + 连通性测试逻辑 |
| `openlist-web/src/views/openlist/ptIndexer/index.vue` 等 3 个 | 配置页面（只渲染，逻辑在 composable） |

### 前端修改

| 文件 | 修改内容 |
|---|---|
| `openlist-web/package.json` + 新增 `vitest.config.ts` | 引入 vitest 单元测试运行器 |
| `openlist-web/src/composables/useMenuIcon.ts` | 补 3 个菜单图标类名映射，否则新菜单图标不显示 |
| `openlist-web/src/composables/useTaskList.ts` | `executeApi` 改为可选，支持无「执行」动作的配置类页面 |
| `openlist-web/src/router/index.ts` | 注册 3 个组件路径 |

---

## 任务 1：数据库表与迁移脚本

**文件：**
- 创建：`ruoyi-common/src/main/resources/sql/20260724-pt-base.sql`
- 修改：`ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java`

> **背景（工程师必读）：** MyBatis-Plus 的 `SimpleDdl` 只有在整个 SQL 文件**全部语句执行成功**后才写入 `ddl_history` 标记为已执行。任何一条语句报错，整个文件视为未完成，下次启动从头重跑。因此**每条语句都必须幂等**：`CREATE TABLE IF NOT EXISTS`、`INSERT IGNORE` + 显式主键值。

- [ ] **步骤 1：编写迁移脚本**

创建 `ruoyi-common/src/main/resources/sql/20260724-pt-base.sql`：

```sql
-- ----------------------------
-- 20260724: PT 订阅功能基座 —— 索引器 / 下载器 / 媒体服务器 三张配置表
-- 每条语句均为幂等（CREATE TABLE IF NOT EXISTS / INSERT IGNORE + 显式主键），
-- 原因见 20260720-rename-category-rule.sql 头部说明。
-- 凭据字段（api_key / password）为明文存储，与现有 sys_config 中
-- openlist.server.token、openlist.tg.token 的存储方式保持一致。
-- ----------------------------

CREATE TABLE IF NOT EXISTS `pt_indexer` (
    `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '索引器展示名',
    `url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Torznab 接口地址，形如 http://jackett:9117/api/v2.0/indexers/xxx/results/torznab/api',
    `api_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Torznab apikey',
    `categories` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '逗号分隔的 Torznab 分类，空表示不限',
    `poll_interval` int(10) NOT NULL DEFAULT 600 COMMENT 'RSS 轮询周期（秒）',
    `enabled` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '是否启用 0-否 1-是',
    `last_poll_time` datetime(0) NULL DEFAULT NULL COMMENT '上次轮询时间',
    `last_status` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上次轮询结果，OK 或错误信息',
    `fail_count` int(10) NOT NULL DEFAULT 0 COMMENT '连续失败次数，成功后归零',
    `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_enabled`(`enabled`) USING BTREE
) COMMENT = 'PT Torznab 索引器配置';

CREATE TABLE IF NOT EXISTS `pt_downloader` (
    `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '下载器展示名',
    `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'QBITTORRENT' COMMENT '下载器类型，当前仅 QBITTORRENT',
    `host` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主机名或IP，不含协议与端口',
    `port` int(10) NOT NULL DEFAULT 8080 COMMENT '端口',
    `use_https` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '是否使用 https 0-否 1-是',
    `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名',
    `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '密码，明文存储',
    `save_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '种子保存路径，必须位于某个已启用文件同步任务的监听目录之下',
    `tag` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'osr-pt' COMMENT '推送时打的标签，用于轮询过滤',
    `enabled` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '是否启用 0-否 1-是',
    `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE
) COMMENT = 'PT 下载器配置';

CREATE TABLE IF NOT EXISTS `pt_media_server` (
    `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '媒体服务器展示名',
    `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'EMBY' COMMENT '类型 EMBY / JELLYFIN',
    `url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务器地址，形如 http://emby:8096',
    `api_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API Key，明文存储',
    `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户ID，查询剧集时使用，可空',
    `enabled` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '是否启用 0-否 1-是',
    `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE
) COMMENT = 'PT 媒体服务器配置（Emby/Jellyfin）';

-- ----------------------------
-- 菜单：挂在 OpenListStrm(2006) 下，显式主键 + INSERT IGNORE 保证幂等
-- 图标类名必须存在于前端图标映射表中，否则不显示（见 commit 0248e124）
-- ----------------------------
INSERT IGNORE INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2061, 'PT索引器', 2006, 10, '/openlist/ptIndexer', '', 'C', '0', '1', 'openliststrm:ptIndexer:view', 'fa fa-rss', 'admin', '2026-07-24 00:00:00', '', NULL, 'Torznab 索引器配置'),
(2062, 'PT下载器', 2006, 11, '/openlist/ptDownloader', '', 'C', '0', '1', 'openliststrm:ptDownloader:view', 'fa fa-download', 'admin', '2026-07-24 00:00:00', '', NULL, 'qBittorrent 下载器配置'),
(2063, '媒体服务器', 2006, 12, '/openlist/ptMediaServer', '', 'C', '0', '1', 'openliststrm:ptMediaServer:view', 'fa fa-server', 'admin', '2026-07-24 00:00:00', '', NULL, 'Emby/Jellyfin 配置');
```

- [ ] **步骤 2：注册迁移脚本**

修改 `ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java`，在 `getSqlFiles()` 返回列表的**末尾**（`"sql/20260723-fix-menu-icons-mapped-values.sql"` 之后）追加一行：

```java
                "sql/20260723-fix-menu-icons-mapped-values.sql",
                "sql/20260724-pt-base.sql"
        );
```

- [ ] **步骤 3：把三个新图标类名加进前端图标映射表**

> **背景：** 后端菜单存的是 Font Awesome 类名，前端用的是 Element Plus 图标组件，两者靠 `openlist-web/src/composables/useMenuIcon.ts` 的 `iconMap` 映射。**类名不在映射表里，菜单图标就不显示**（历史 bug，见 commit `0248e124`）。已核实 `fa fa-rss` / `fa fa-download` / `fa fa-server` 三个类名当前都不在表中，必须补。

修改 `openlist-web/src/composables/useMenuIcon.ts`，两处：

其一，扩充导入（在末尾追加三个组件）：

```typescript
import {
  Setting, Document, Picture, Monitor, Tools, Calendar, Coin, Promotion,
  Watermelon, Menu as IconMenu, VideoPlay, RefreshRight, EditPen,
  FolderOpened, DocumentCopy, MagicStick, Connection, Download, Film
} from '@element-plus/icons-vue'
```

其二，在 `iconMap` 末尾追加三项（注意为原末项 `'fa fa-magic': MagicStick` 补上逗号）：

```typescript
  'fa fa-magic': MagicStick,
  'fa fa-rss': Connection,
  'fa fa-download': Download,
  'fa fa-server': Film
}
```

- [ ] **步骤 3b：验证图标组件名有效**

运行：`cd openlist-web && npm run build`

预期：vue-tsc 通过。若报 `Connection` / `Download` / `Film` 不存在，运行 `ls node_modules/@element-plus/icons-vue/dist/types/components/ | head -80` 查可用图标名并替换为语义相近的。

- [ ] **步骤 4：编译验证**

运行：`mvn -pl ruoyi-common -am compile -DskipTests`

预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-common/src/main/resources/sql/20260724-pt-base.sql ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java openlist-web/src/composables/useMenuIcon.ts
git commit -m "feat(pt): 新增索引器/下载器/媒体服务器三张配置表及菜单"
```

---

## 任务 2：TorrentInfo 统一种子模型

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/model/TorrentInfo.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/model/TorrentInfoTest.java`

> **设计说明：** 这是贯穿 indexer → filter → subscription 全流程的唯一种子模型。上半部分字段来自 Torznab 响应，下半部分 `parsedXxx` 字段由后续计划中的标题解析填充，本计划只定义不填充。`downloadVolumeFactor` 是 PT 站促销状态的标准表达：0 表示免费，0.5 表示 50% 下载量，1 表示正常计量。

- [ ] **步骤 1：编写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/model/TorrentInfoTest.java`：

```java
package com.ruoyi.openliststrm.pt.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorrentInfoTest {

    @Test
    void isFree_下载量系数为0_判定为免费() {
        TorrentInfo info = new TorrentInfo();
        info.setDownloadVolumeFactor(0.0);
        assertTrue(info.isFree());
    }

    @Test
    void isFree_下载量系数为1_判定为非免费() {
        TorrentInfo info = new TorrentInfo();
        info.setDownloadVolumeFactor(1.0);
        assertFalse(info.isFree());
    }

    @Test
    void isFree_下载量系数为半价_判定为非免费() {
        TorrentInfo info = new TorrentInfo();
        info.setDownloadVolumeFactor(0.5);
        assertFalse(info.isFree());
    }

    @Test
    void 默认下载量系数为1_即非免费() {
        assertFalse(new TorrentInfo().isFree());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorrentInfoTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `TorrentInfo` 找不到符号

- [ ] **步骤 3：编写实现**

创建 `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/model/TorrentInfo.java`：

```java
package com.ruoyi.openliststrm.pt.model;

import lombok.Data;

/**
 * 统一种子模型，贯穿 indexer → filter → subscription 全流程。
 * <p>
 * 上半部分字段来自索引器响应，parsedXxx 字段由标题解析阶段填充。
 * 未来接入站内搜索、站点原生 RSS 时，新数据源只需产出本模型，下游无需改动。
 * </p>
 *
 * @author Jack
 */
@Data
public class TorrentInfo {

    /** 种子原始标题，过滤与解析的输入 */
    private String title;

    /** 种子 info hash，部分索引器不提供，可为空 */
    private String infoHash;

    /** .torrent 下载链接或磁力链，推送下载器时使用 */
    private String downloadUrl;

    /** 体积（字节） */
    private long size;

    /** 做种数 */
    private int seeders;

    /** 下载数 */
    private int peers;

    /**
     * 下载量系数：0=免费，0.5=50%，1=正常计量。
     * 索引器未提供时默认按正常计量处理，避免把收费种误判为免费。
     */
    private double downloadVolumeFactor = 1.0;

    /** 发布时间原始字符串，保留索引器返回的格式 */
    private String pubDate;

    /** 来源索引器 ID */
    private Integer indexerId;

    // ---------- 以下字段由标题解析阶段填充（计划3） ----------

    /** 解析出的作品标题 */
    private String parsedTitle;

    /** 解析出的年份 */
    private String parsedYear;

    /** 解析出的季号，电影为 null */
    private Integer parsedSeason;

    /** 解析出的集号，电影为 null */
    private Integer parsedEpisode;

    /** 解析出的分辨率，如 1080p、2160p */
    private String parsedResolution;

    /**
     * 是否为免费种。用容差比较而非直接 == 0，避免浮点解析误差。
     */
    public boolean isFree() {
        return downloadVolumeFactor < 0.0001;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorrentInfoTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 4, Failures: 0, Errors: 0

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/model/TorrentInfo.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/model/TorrentInfoTest.java
git commit -m "feat(pt): 新增 TorrentInfo 统一种子模型"
```

---

## 任务 3：TorznabParser 响应解析

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabParser.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabParserTest.java`

> **背景（工程师必读）：** Torznab 是 RSS 2.0 的扩展，在 `<item>` 内用 `<torznab:attr name="x" value="y"/>` 承载结构化字段。不同索引器实现存在差异：
> - 命名空间前缀可能是 `torznab:` 也可能是 `newznab:`（Jackett 对某些站点返回后者）
> - 体积可能在 `<size>` 元素里，也可能只在 `<enclosure length="...">` 属性里
> - 下载链接可能在 `<link>` 里，也可能只在 `<enclosure url="...">` 里
> - `seeders` 等属性可能整个缺失
>
> 解析器必须容忍以上全部情况。这是本功能 bug 最高发的地方，测试必须覆盖。
>
> **安全要求：** 解析外部 XML 必须禁用 DTD，防止 XXE 攻击。

- [ ] **步骤 1：编写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabParserTest.java`：

```java
package com.ruoyi.openliststrm.pt.indexer;

import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorznabParserTest {

    private String wrap(String items) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:torznab="http://torznab.com/schemas/2015/feed">
                  <channel>
                    <title>Indexer</title>
                %s
                  </channel>
                </rss>
                """.formatted(items);
    }

    @Test
    void parse_标准条目_全部字段正确() {
        String xml = wrap("""
                    <item>
                      <title>Some.Show.S01E05.1080p.WEB-DL.H264-GROUP</title>
                      <guid>https://pt.example.com/details.php?id=1</guid>
                      <link>https://pt.example.com/download.php?id=1</link>
                      <pubDate>Mon, 20 Jul 2026 10:00:00 +0800</pubDate>
                      <size>2147483648</size>
                      <torznab:attr name="seeders" value="12"/>
                      <torznab:attr name="peers" value="15"/>
                      <torznab:attr name="infohash" value="ABCDEF0123456789"/>
                      <torznab:attr name="downloadvolumefactor" value="0"/>
                    </item>
                """);

        List<TorrentInfo> list = TorznabParser.parse(xml);

        assertEquals(1, list.size());
        TorrentInfo t = list.get(0);
        assertEquals("Some.Show.S01E05.1080p.WEB-DL.H264-GROUP", t.getTitle());
        assertEquals("https://pt.example.com/download.php?id=1", t.getDownloadUrl());
        assertEquals(2147483648L, t.getSize());
        assertEquals(12, t.getSeeders());
        assertEquals(15, t.getPeers());
        assertEquals("ABCDEF0123456789", t.getInfoHash());
        assertEquals("Mon, 20 Jul 2026 10:00:00 +0800", t.getPubDate());
        assertTrue(t.isFree());
    }

    @Test
    void parse_中文标题_不乱码() {
        String xml = wrap("""
                    <item>
                      <title>大明王朝1566.S01E12.2160p.WEB-DL</title>
                      <link>https://pt.example.com/download.php?id=2</link>
                      <size>5368709120</size>
                    </item>
                """);

        List<TorrentInfo> list = TorznabParser.parse(xml);

        assertEquals("大明王朝1566.S01E12.2160p.WEB-DL", list.get(0).getTitle());
    }

    @Test
    void parse_newznab命名空间_属性同样被识别() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:newznab="http://www.newznab.com/DTD/2010/feeds/attributes/">
                  <channel>
                    <item>
                      <title>Movie.2026.1080p.BluRay</title>
                      <link>https://pt.example.com/download.php?id=3</link>
                      <size>10737418240</size>
                      <newznab:attr name="seeders" value="7"/>
                      <newznab:attr name="downloadvolumefactor" value="0.5"/>
                    </item>
                  </channel>
                </rss>
                """;

        List<TorrentInfo> list = TorznabParser.parse(xml);

        assertEquals(7, list.get(0).getSeeders());
        assertEquals(0.5, list.get(0).getDownloadVolumeFactor(), 0.0001);
    }

    @Test
    void parse_体积仅在enclosure中_从enclosure取值() {
        String xml = wrap("""
                    <item>
                      <title>Movie.2026.1080p</title>
                      <link>https://pt.example.com/download.php?id=4</link>
                      <enclosure url="https://pt.example.com/torrent/4.torrent" length="3221225472" type="application/x-bittorrent"/>
                    </item>
                """);

        assertEquals(3221225472L, TorznabParser.parse(xml).get(0).getSize());
    }

    @Test
    void parse_无link仅有enclosure_下载地址取enclosure的url() {
        String xml = wrap("""
                    <item>
                      <title>Movie.2026.1080p</title>
                      <enclosure url="https://pt.example.com/torrent/5.torrent" length="100" type="application/x-bittorrent"/>
                    </item>
                """);

        assertEquals("https://pt.example.com/torrent/5.torrent", TorznabParser.parse(xml).get(0).getDownloadUrl());
    }

    @Test
    void parse_属性全部缺失_使用安全默认值() {
        String xml = wrap("""
                    <item>
                      <title>Movie.2026.1080p</title>
                      <link>https://pt.example.com/download.php?id=6</link>
                    </item>
                """);

        TorrentInfo t = TorznabParser.parse(xml).get(0);

        assertEquals(0, t.getSeeders());
        assertEquals(0L, t.getSize());
        assertNull(t.getInfoHash());
        // 未提供促销信息时必须按非免费处理，避免误判
        assertEquals(1.0, t.getDownloadVolumeFactor(), 0.0001);
    }

    @Test
    void parse_属性值非数字_回退到默认值而非抛异常() {
        String xml = wrap("""
                    <item>
                      <title>Movie.2026.1080p</title>
                      <link>https://pt.example.com/download.php?id=7</link>
                      <size>not-a-number</size>
                      <torznab:attr name="seeders" value="N/A"/>
                    </item>
                """);

        TorrentInfo t = TorznabParser.parse(xml).get(0);

        assertEquals(0L, t.getSize());
        assertEquals(0, t.getSeeders());
    }

    @Test
    void parse_缺少标题的条目_被丢弃() {
        String xml = wrap("""
                    <item>
                      <link>https://pt.example.com/download.php?id=8</link>
                    </item>
                """);

        assertTrue(TorznabParser.parse(xml).isEmpty());
    }

    @Test
    void parse_既无link也无enclosure的条目_被丢弃() {
        String xml = wrap("""
                    <item>
                      <title>Movie.2026.1080p</title>
                    </item>
                """);

        assertTrue(TorznabParser.parse(xml).isEmpty());
    }

    @Test
    void parse_空结果集_返回空列表() {
        assertTrue(TorznabParser.parse(wrap("")).isEmpty());
    }

    @Test
    void parse_空字符串_返回空列表() {
        assertTrue(TorznabParser.parse("").isEmpty());
        assertTrue(TorznabParser.parse(null).isEmpty());
    }

    @Test
    void parse_非法XML_抛IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> TorznabParser.parse("<rss><channel><item>"));
    }

    @Test
    void parse_含DTD声明_抛异常而非解析_防XXE() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <rss><channel><item><title>&xxe;</title></item></channel></rss>
                """;

        assertThrows(IllegalArgumentException.class, () -> TorznabParser.parse(xml));
    }

    @Test
    void parse_多条目_全部返回且顺序保持() {
        String xml = wrap("""
                    <item><title>A</title><link>http://x/1</link></item>
                    <item><title>B</title><link>http://x/2</link></item>
                    <item><title>C</title><link>http://x/3</link></item>
                """);

        List<TorrentInfo> list = TorznabParser.parse(xml);

        assertEquals(3, list.size());
        assertEquals("A", list.get(0).getTitle());
        assertEquals("C", list.get(2).getTitle());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorznabParserTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `TorznabParser` 找不到符号

- [ ] **步骤 3：编写实现**

创建 `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabParser.java`：

```java
package com.ruoyi.openliststrm.pt.indexer;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Torznab / Newznab 响应解析器。纯函数，无 IO，无 Spring 依赖。
 * <p>
 * 不同索引器实现差异较大，本解析器对以下情况全部容错：
 * 命名空间前缀 torznab 与 newznab 混用、体积仅存在于 enclosure、
 * 下载地址仅存在于 enclosure、结构化属性整体缺失、属性值非数字。
 * </p>
 *
 * @author Jack
 */
@Slf4j
public final class TorznabParser {

    private TorznabParser() {
    }

    /**
     * 解析 Torznab XML 响应。
     *
     * @param xml 响应体，允许为 null 或空
     * @return 解析出的种子列表，顺序与响应一致；无有效条目时返回空列表
     * @throws IllegalArgumentException XML 格式非法，或包含 DTD 声明
     */
    public static List<TorrentInfo> parse(String xml) {
        List<TorrentInfo> result = new ArrayList<>();
        if (StringUtils.isBlank(xml)) {
            return result;
        }
        Document doc = buildDocument(xml);
        NodeList items = doc.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            TorrentInfo info = parseItem((Element) items.item(i));
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

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

    private static TorrentInfo parseItem(Element item) {
        String title = childText(item, "title");
        if (StringUtils.isBlank(title)) {
            log.debug("Torznab条目缺少title，已丢弃");
            return null;
        }

        Element enclosure = firstChildElement(item, "enclosure");

        String downloadUrl = childText(item, "link");
        if (StringUtils.isBlank(downloadUrl) && enclosure != null) {
            downloadUrl = StringUtils.trimToNull(enclosure.getAttribute("url"));
        }
        if (StringUtils.isBlank(downloadUrl)) {
            log.debug("Torznab条目缺少下载地址，已丢弃：{}", title);
            return null;
        }

        TorrentInfo info = new TorrentInfo();
        info.setTitle(title);
        info.setDownloadUrl(downloadUrl);
        info.setPubDate(childText(item, "pubDate"));

        long size = parseLong(childText(item, "size"), 0L);
        if (size == 0L && enclosure != null) {
            size = parseLong(enclosure.getAttribute("length"), 0L);
        }
        if (size == 0L) {
            size = parseLong(attrValue(item, "size"), 0L);
        }
        info.setSize(size);

        info.setSeeders((int) parseLong(attrValue(item, "seeders"), 0L));
        info.setPeers((int) parseLong(attrValue(item, "peers"), 0L));
        info.setInfoHash(StringUtils.trimToNull(attrValue(item, "infohash")));
        // 未提供促销信息时按正常计量处理，绝不能默认成免费
        info.setDownloadVolumeFactor(parseDouble(attrValue(item, "downloadvolumefactor"), 1.0));

        return info;
    }

    /**
     * 读取 torznab:attr / newznab:attr / attr 中 name 匹配的 value，大小写不敏感。
     */
    private static String attrValue(Element item, String name) {
        NodeList children = item.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String tag = node.getNodeName();
            if (!"attr".equals(tag) && !tag.endsWith(":attr")) {
                continue;
            }
            Element el = (Element) node;
            if (name.equalsIgnoreCase(el.getAttribute("name"))) {
                return el.getAttribute("value");
            }
        }
        return null;
    }

    private static String childText(Element parent, String tag) {
        Element el = firstChildElement(parent, tag);
        return el == null ? null : StringUtils.trimToNull(el.getTextContent());
    }

    private static Element firstChildElement(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static long parseLong(String value, long fallback) {
        if (StringUtils.isBlank(value)) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        if (StringUtils.isBlank(value)) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
```

> **已核实：** `com.ruoyi.common.utils.StringUtils extends org.apache.commons.lang3.StringUtils`，`trimToNull` / `isBlank` / `defaultString` 均可直接使用，无需另行导入。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorznabParserTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 14, Failures: 0, Errors: 0

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabParser.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabParserTest.java
git commit -m "feat(pt): 新增 Torznab 响应解析器，兼容 newznab 命名空间与字段缺失"
```

---

## 任务 4：索引器实体、Mapper、Service

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/PtIndexerPlus.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/mapper/PtIndexerPlusMapper.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/IPtIndexerPlusService.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/impl/PtIndexerPlusServiceImpl.java`

> **约定：** 实体继承 `BaseEntity`（提供 `createTime` / `updateTime` 自动填充），字段用 `@TableField` 显式映射下划线列名，主键 `@TableId(type = IdType.AUTO)`。Mapper 只继承 `BaseMapper`，不写 XML（本模块用 MyBatis-Plus 风格，见 AGENTS.md 反模式条款）。

- [ ] **步骤 1：编写实体**

创建 `PtIndexerPlus.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatisplus.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * <p>
 * PT Torznab 索引器配置
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
@Getter
@Setter
@TableName("pt_indexer")
public class PtIndexerPlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 索引器展示名 */
    @TableField("name")
    private String name;

    /** Torznab 接口地址 */
    @TableField("url")
    private String url;

    /** Torznab apikey */
    @TableField("api_key")
    private String apiKey;

    /** 逗号分隔的 Torznab 分类，空表示不限 */
    @TableField("categories")
    private String categories;

    /** RSS 轮询周期（秒） */
    @TableField("poll_interval")
    private Integer pollInterval;

    /** 是否启用 0-否 1-是 */
    @TableField("enabled")
    private String enabled;

    /** 上次轮询时间 */
    @TableField("last_poll_time")
    private Date lastPollTime;

    /** 上次轮询结果，OK 或错误信息 */
    @TableField("last_status")
    private String lastStatus;

    /** 连续失败次数，成功后归零 */
    @TableField("fail_count")
    private Integer failCount;
}
```

- [ ] **步骤 2：编写 Mapper**

创建 `PtIndexerPlusMapper.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;

/**
 * <p>
 * PT Torznab 索引器配置 Mapper 接口
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
public interface PtIndexerPlusMapper extends BaseMapper<PtIndexerPlus> {

}
```

- [ ] **步骤 3：编写 Service 接口与实现**

创建 `IPtIndexerPlusService.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;

import java.util.List;

/**
 * <p>
 * PT Torznab 索引器配置 服务类
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
public interface IPtIndexerPlusService extends IService<PtIndexerPlus> {

    /**
     * 查询全部启用中的索引器
     */
    List<PtIndexerPlus> listEnabled();
}
```

创建 `PtIndexerPlusServiceImpl.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.mybatisplus.mapper.PtIndexerPlusMapper;
import com.ruoyi.openliststrm.mybatisplus.service.IPtIndexerPlusService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * PT Torznab 索引器配置 服务实现类
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
@Service
public class PtIndexerPlusServiceImpl extends ServiceImpl<PtIndexerPlusMapper, PtIndexerPlus> implements IPtIndexerPlusService {

    @Override
    public List<PtIndexerPlus> listEnabled() {
        return lambdaQuery()
                .eq(PtIndexerPlus::getEnabled, "1")
                .orderByAsc(PtIndexerPlus::getId)
                .list();
    }
}
```

- [ ] **步骤 4：编译验证**

运行：`mvn -pl ruoyi-openliststrm -am compile -DskipTests`

预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/
git commit -m "feat(pt): 新增索引器实体、Mapper 与 Service"
```

---

## 任务 5：TorznabClient HTTP 拉取

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java`
- 修改：`ruoyi-openliststrm/pom.xml`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java`

> **背景：** Torznab 的查询接口约定：
> - `t=caps` 返回能力描述，用于连通性测试
> - `t=search` 不带 `q` 参数时返回最新发布列表，即我们要的「RSS 流」
> - `apikey` 必填，`cat` 可选（逗号分隔分类 ID）
>
> 用 MockWebServer 打桩验证 URL 构造与响应处理，不依赖真实索引器。

- [ ] **步骤 1：新增测试依赖**

修改 `ruoyi-openliststrm/pom.xml`，在 `spring-boot-starter-test` 依赖之后追加：

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>mockwebserver</artifactId>
            <version>${okhttp.version}</version>
            <scope>test</scope>
        </dependency>
```

`${okhttp.version}` 已在根 pom 中定义为 `4.12.0`，无需另加。

- [ ] **步骤 2：编写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java`：

```java
package com.ruoyi.openliststrm.pt.indexer;

import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorznabClientTest {

    private MockWebServer server;
    private TorznabClient client;

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:torznab="http://torznab.com/schemas/2015/feed">
              <channel>
                <item>
                  <title>Some.Show.S01E05.1080p.WEB-DL</title>
                  <link>https://pt.example.com/download.php?id=1</link>
                  <size>2147483648</size>
                  <torznab:attr name="seeders" value="12"/>
                </item>
              </channel>
            </rss>
            """;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new TorznabClient(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private PtIndexerPlus indexer(String categories) {
        PtIndexerPlus i = new PtIndexerPlus();
        i.setId(7);
        i.setName("测试索引器");
        i.setUrl(server.url("/api/v2.0/indexers/test/results/torznab/api").toString());
        i.setApiKey("secret-key");
        i.setCategories(categories);
        return i;
    }

    @Test
    void fetch_正常响应_返回解析结果并带上索引器ID() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        List<TorrentInfo> list = client.fetch(indexer("5000,5030"));

        assertEquals(1, list.size());
        assertEquals("Some.Show.S01E05.1080p.WEB-DL", list.get(0).getTitle());
        assertEquals(7, list.get(0).getIndexerId());
    }

    @Test
    void fetch_请求参数正确() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.fetch(indexer("5000,5030"));

        RecordedRequest request = server.takeRequest();
        assertEquals("secret-key", request.getRequestUrl().queryParameter("apikey"));
        assertEquals("search", request.getRequestUrl().queryParameter("t"));
        assertEquals("5000,5030", request.getRequestUrl().queryParameter("cat"));
    }

    @Test
    void fetch_分类为空_不带cat参数() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.fetch(indexer(null));

        RecordedRequest request = server.takeRequest();
        assertEquals(null, request.getRequestUrl().queryParameter("cat"));
    }

    @Test
    void fetch_HTTP错误码_抛IOException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThrows(IOException.class, () -> client.fetch(indexer(null)));
    }

    @Test
    void fetch_响应非XML_抛IllegalArgumentException() {
        server.enqueue(new MockResponse().setBody("<html>not xml"));

        assertThrows(IllegalArgumentException.class, () -> client.fetch(indexer(null)));
    }

    @Test
    void testConnection_caps接口返回正常_判定连通() throws Exception {
        server.enqueue(new MockResponse().setBody("<caps><server title=\"Jackett\"/></caps>"));

        assertTrue(client.testConnection(indexer(null)));

        RecordedRequest request = server.takeRequest();
        assertEquals("caps", request.getRequestUrl().queryParameter("t"));
    }

    @Test
    void testConnection_返回401_判定不连通() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertFalse(client.testConnection(indexer(null)));
    }

    @Test
    void testConnection_地址不可达_判定不连通而非抛异常() throws IOException {
        server.shutdown();

        assertFalse(client.testConnection(indexer(null)));
    }
}
```

- [ ] **步骤 3：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorznabClientTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `TorznabClient` 找不到符号

- [ ] **步骤 4：编写实现**

创建 `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java`：

```java
package com.ruoyi.openliststrm.pt.indexer;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Torznab 索引器客户端。负责 HTTP 拉取，解析委托 {@link TorznabParser}。
 *
 * @author Jack
 */
@Slf4j
@Component
public class TorznabClient {

    private final OkHttpClient httpClient;

    public TorznabClient(OkHttpClient sharedOkHttpClient) {
        this.httpClient = sharedOkHttpClient;
    }

    /**
     * 拉取索引器的最新发布列表（t=search 不带 q，即 RSS 流）。
     *
     * @throws IOException              网络异常或 HTTP 非 2xx
     * @throws IllegalArgumentException 响应体不是合法 Torznab XML
     */
    public List<TorrentInfo> fetch(PtIndexerPlus indexer) throws IOException {
        HttpUrl url = buildUrl(indexer, "search");
        String body = execute(url);
        List<TorrentInfo> list = TorznabParser.parse(body);
        for (TorrentInfo info : list) {
            info.setIndexerId(indexer.getId());
        }
        log.debug("索引器[{}]返回{}条种子", indexer.getName(), list.size());
        return list;
    }

    /**
     * 连通性测试：调用 t=caps 能力接口。任何异常均视为不连通，不向上抛。
     */
    public boolean testConnection(PtIndexerPlus indexer) {
        try {
            execute(buildUrl(indexer, "caps"));
            return true;
        } catch (Exception e) {
            log.warn("索引器[{}]连通性测试失败：{}", indexer.getName(), e.getMessage());
            return false;
        }
    }

    private HttpUrl buildUrl(PtIndexerPlus indexer, String type) {
        HttpUrl base = HttpUrl.parse(indexer.getUrl());
        if (base == null) {
            throw new IllegalArgumentException("索引器地址非法：" + indexer.getUrl());
        }
        HttpUrl.Builder builder = base.newBuilder()
                .addQueryParameter("apikey", indexer.getApiKey())
                .addQueryParameter("t", type);
        if ("search".equals(type) && StringUtils.isNotBlank(indexer.getCategories())) {
            builder.addQueryParameter("cat", indexer.getCategories());
        }
        return builder.build();
    }

    private String execute(HttpUrl url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("索引器返回HTTP " + response.code());
            }
            ResponseBody body = response.body();
            return body == null ? "" : body.string();
        }
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorznabClientTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 8, Failures: 0, Errors: 0

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/pom.xml ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java
git commit -m "feat(pt): 新增 Torznab 索引器客户端与连通性测试"
```

---

## 任务 6：下载器抽象接口与模型

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/downloader/model/DownloaderTorrent.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/downloader/IDownloaderClient.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/downloader/model/DownloaderTorrentTest.java`

> **⚠ 执行顺序：** 本任务的 `IDownloaderClient` 引用 `PtDownloaderPlus` 实体，该实体在任务 7 步骤 1 创建。**请先完成任务 7 的步骤 1，再执行本任务**，否则编译不过。
>
> **设计说明：** 接口按「推种 / 查列表 / 测连通」三个动作定义，不暴露任何 qBittorrent 特有概念（如 SID、hash 大小写），使 Transmission 未来能以纯新增方式接入。完成判定统一用 `progress >= 1.0`，而非枚举 qB 的状态字符串——后者在不同 qB 版本间有差异，且 Transmission 的状态取值完全不同。

- [ ] **步骤 1：编写失败的测试**

创建 `DownloaderTorrentTest.java`：

```java
package com.ruoyi.openliststrm.pt.downloader.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloaderTorrentTest {

    private DownloaderTorrent torrent(double progress) {
        DownloaderTorrent t = new DownloaderTorrent();
        t.setHash("abc");
        t.setName("Some.Show.S01E05");
        t.setProgress(progress);
        return t;
    }

    @Test
    void isCompleted_进度为1_判定完成() {
        assertTrue(torrent(1.0).isCompleted());
    }

    @Test
    void isCompleted_进度为0999_判定未完成() {
        assertFalse(torrent(0.999).isCompleted());
    }

    @Test
    void isCompleted_进度为0_判定未完成() {
        assertFalse(torrent(0.0).isCompleted());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=DownloaderTorrentTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `DownloaderTorrent` 找不到符号

- [ ] **步骤 3：编写模型与接口**

创建 `DownloaderTorrent.java`：

```java
package com.ruoyi.openliststrm.pt.downloader.model;

import lombok.Data;

/**
 * 下载器中一个种子的状态快照，屏蔽各下载器的字段差异。
 *
 * @author Jack
 */
@Data
public class DownloaderTorrent {

    /** 种子 hash，统一为小写 */
    private String hash;

    /** 种子名称 */
    private String name;

    /** 下载进度，0.0 ~ 1.0 */
    private double progress;

    /** 下载器原始状态字符串，仅用于日志排查，不参与判定 */
    private String rawState;

    /** 保存路径 */
    private String savePath;

    /**
     * 是否已下载完成。统一按进度判定，不依赖各下载器的状态枚举——
     * qBittorrent 的完成态有 uploading/stalledUP/pausedUP 等多种，
     * 且不同版本取值有差异，Transmission 的取值又完全不同。
     */
    public boolean isCompleted() {
        return progress >= 1.0;
    }
}
```

创建 `IDownloaderClient.java`：

```java
package com.ruoyi.openliststrm.pt.downloader;

import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent;

import java.io.IOException;
import java.util.List;

/**
 * 下载器抽象接口。新增下载器类型时实现本接口并注册为 Spring Bean，
 * {@link DownloaderClientFactory} 会自动按 {@link #type()} 分发，
 * 调用方（订阅引擎）无需改动。
 *
 * @author Jack
 */
public interface IDownloaderClient {

    /**
     * 支持的下载器类型，与 pt_downloader.type 取值一致，如 QBITTORRENT。
     */
    String type();

    /**
     * 连通性测试。任何异常均视为不连通，不向上抛。
     */
    boolean testConnection(PtDownloaderPlus config);

    /**
     * 添加种子。
     *
     * @param downloadUrl .torrent 链接或磁力链
     * @param savePath    保存路径
     * @param tag         标签，后续按此标签过滤查询
     * @throws IOException 网络异常或下载器拒绝
     */
    void addTorrent(PtDownloaderPlus config, String downloadUrl, String savePath, String tag) throws IOException;

    /**
     * 查询指定标签下的全部种子。
     *
     * @throws IOException 网络异常
     */
    List<DownloaderTorrent> listByTag(PtDownloaderPlus config, String tag) throws IOException;
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=DownloaderTorrentTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 3, Failures: 0, Errors: 0

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/downloader/ ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/downloader/
git commit -m "feat(pt): 新增下载器抽象接口与种子状态模型"
```

---

## 任务 7：下载器实体、Mapper、Service

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/PtDownloaderPlus.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/mapper/PtDownloaderPlusMapper.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/IPtDownloaderPlusService.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/impl/PtDownloaderPlusServiceImpl.java`

- [ ] **步骤 1：编写实体**

创建 `PtDownloaderPlus.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatisplus.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * PT 下载器配置
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
@Getter
@Setter
@TableName("pt_downloader")
public class PtDownloaderPlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 下载器展示名 */
    @TableField("name")
    private String name;

    /** 下载器类型，当前仅 QBITTORRENT */
    @TableField("type")
    private String type;

    /** 主机名或IP，不含协议与端口 */
    @TableField("host")
    private String host;

    /** 端口 */
    @TableField("port")
    private Integer port;

    /** 是否使用 https 0-否 1-是 */
    @TableField("use_https")
    private String useHttps;

    /** 用户名 */
    @TableField("username")
    private String username;

    /** 密码，明文存储 */
    @TableField("password")
    private String password;

    /** 种子保存路径 */
    @TableField("save_path")
    private String savePath;

    /** 推送时打的标签 */
    @TableField("tag")
    private String tag;

    /** 是否启用 0-否 1-是 */
    @TableField("enabled")
    private String enabled;

    /**
     * 拼装下载器 Web UI 基地址，如 http://192.168.1.10:8080。
     * 末尾不带斜杠。
     */
    public String baseUrl() {
        String scheme = "1".equals(useHttps) ? "https" : "http";
        return scheme + "://" + host + ":" + port;
    }
}
```

- [ ] **步骤 2：编写 Mapper 与 Service**

创建 `PtDownloaderPlusMapper.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;

/**
 * <p>
 * PT 下载器配置 Mapper 接口
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
public interface PtDownloaderPlusMapper extends BaseMapper<PtDownloaderPlus> {

}
```

创建 `IPtDownloaderPlusService.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;

/**
 * <p>
 * PT 下载器配置 服务类
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
public interface IPtDownloaderPlusService extends IService<PtDownloaderPlus> {

    /**
     * 校验保存路径是否位于某个已启用文件同步任务的监听目录之下。
     * 不满足时下载完成的文件不会被现有 FileMonitor 链路接管。
     *
     * @return 校验失败的提示信息；通过时返回 null
     */
    String validateSavePath(String savePath);
}
```

创建 `PtDownloaderPlusServiceImpl.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.mapper.PtDownloaderPlusMapper;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyTaskPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloaderPlusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * <p>
 * PT 下载器配置 服务实现类
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
@Service
public class PtDownloaderPlusServiceImpl extends ServiceImpl<PtDownloaderPlusMapper, PtDownloaderPlus> implements IPtDownloaderPlusService {

    @Autowired
    private IOpenlistCopyTaskPlusService copyTaskService;

    @Override
    public String validateSavePath(String savePath) {
        if (StringUtils.isBlank(savePath)) {
            return "保存路径不能为空";
        }
        Path target = Paths.get(savePath).toAbsolutePath().normalize();
        List<OpenlistCopyTaskPlus> tasks = copyTaskService.list();
        for (OpenlistCopyTaskPlus task : tasks) {
            // 只认启用中的同步任务：停用的任务不会启动 FileMonitor，落在它目录下的文件同样不会被上传
            if (!"1".equals(task.getCopyTaskStatus())) {
                continue;
            }
            String monitorDir = task.getMonitorDir();
            if (StringUtils.isBlank(monitorDir)) {
                continue;
            }
            Path dir = Paths.get(monitorDir).toAbsolutePath().normalize();
            if (target.startsWith(dir)) {
                return null;
            }
        }
        return "保存路径不在任何已启用文件同步任务的监听目录之下，下载完成后不会被自动上传";
    }
}
```

> **字段来源已核实：** `OpenlistCopyTaskPlus` 的本地监听目录字段为 `monitorDir`（列 `monitor_dir`），启用状态字段为 `copyTaskStatus`（`"1"` 为启用）。无需再自行探查。

- [ ] **步骤 3：编译验证**

运行：`mvn -pl ruoyi-openliststrm -am compile -DskipTests`

预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/
git commit -m "feat(pt): 新增下载器实体、Service 及保存路径校验"
```

---

## 任务 8：QbittorrentClient 实现

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/downloader/QbittorrentClient.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/downloader/DownloaderClientFactory.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/downloader/QbittorrentClientTest.java`

> **背景（工程师必读）：** qBittorrent Web API v2 的关键约定：
> - 登录：`POST /api/v2/auth/login`，表单字段 `username` / `password`。成功时响应体为 `Ok.`，并通过 `Set-Cookie` 返回 `SID`。**响应码 200 但响应体为 `Fails.` 表示登录失败**，必须检查响应体而非只看状态码。
> - 后续请求需带 `Cookie: SID=xxx`。
> - 添加种子：`POST /api/v2/torrents/add`，表单字段 `urls`（换行分隔）、`savepath`、`tags`。成功响应体为 `Ok.`。
> - 查询：`GET /api/v2/torrents/info?tag=xxx`，返回 JSON 数组，元素含 `hash` / `name` / `progress` / `state` / `save_path`。
> - SID 会过期，过期后返回 **403**。因此需要「缓存 SID → 403 时重新登录并重试一次」的逻辑。
>
> SID 按下载器 ID 缓存在内存 Map 中。进程重启后缓存丢失，首次调用重新登录即可，无需持久化。

- [ ] **步骤 1：编写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/downloader/QbittorrentClientTest.java`：

```java
package com.ruoyi.openliststrm.pt.downloader;

import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QbittorrentClientTest {

    private MockWebServer server;
    private QbittorrentClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new QbittorrentClient(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private PtDownloaderPlus config(int id) {
        PtDownloaderPlus c = new PtDownloaderPlus();
        c.setId(id);
        c.setName("qb");
        c.setType("QBITTORRENT");
        c.setHost(server.getHostName());
        c.setPort(server.getPort());
        c.setUseHttps("0");
        c.setUsername("admin");
        c.setPassword("adminadmin");
        c.setSavePath("/data/downloads");
        c.setTag("osr-pt");
        return c;
    }

    private MockResponse loginOk() {
        return new MockResponse().setBody("Ok.").addHeader("Set-Cookie", "SID=test-sid; path=/");
    }

    @Test
    void type_返回QBITTORRENT() {
        assertEquals("QBITTORRENT", client.type());
    }

    @Test
    void addTorrent_登录后提交种子_参数正确() throws Exception {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("Ok."));

        client.addTorrent(config(1), "https://pt.example.com/t.torrent", "/data/downloads", "osr-pt");

        RecordedRequest login = server.takeRequest();
        assertEquals("/api/v2/auth/login", login.getPath());
        assertTrue(login.getBody().readUtf8().contains("username=admin"));

        RecordedRequest add = server.takeRequest();
        assertEquals("/api/v2/torrents/add", add.getPath());
        assertEquals("SID=test-sid", add.getHeader("Cookie"));
        String body = add.getBody().readUtf8();
        assertTrue(body.contains("savepath=%2Fdata%2Fdownloads"));
        assertTrue(body.contains("tags=osr-pt"));
    }

    @Test
    void addTorrent_登录返回Fails_抛IOException() {
        server.enqueue(new MockResponse().setBody("Fails."));

        assertThrows(IOException.class,
                () -> client.addTorrent(config(2), "https://pt.example.com/t.torrent", "/data/downloads", "osr-pt"));
    }

    @Test
    void addTorrent_添加接口返回非Ok_抛IOException() {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("Fails."));

        assertThrows(IOException.class,
                () -> client.addTorrent(config(3), "https://pt.example.com/t.torrent", "/data/downloads", "osr-pt"));
    }

    @Test
    void listByTag_解析JSON为种子快照() throws Exception {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("""
                [
                  {"hash":"AABBCC","name":"Show.S01E01","progress":1.0,"state":"uploading","save_path":"/data/downloads"},
                  {"hash":"DDEEFF","name":"Show.S01E02","progress":0.35,"state":"downloading","save_path":"/data/downloads"}
                ]
                """));

        List<DownloaderTorrent> list = client.listByTag(config(4), "osr-pt");

        assertEquals(2, list.size());
        assertEquals("aabbcc", list.get(0).getHash());
        assertEquals("Show.S01E01", list.get(0).getName());
        assertTrue(list.get(0).isCompleted());
        assertEquals("uploading", list.get(0).getRawState());
        assertFalse(list.get(1).isCompleted());
    }

    @Test
    void listByTag_请求带上tag参数() throws Exception {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("[]"));

        client.listByTag(config(5), "osr-pt");

        server.takeRequest();
        RecordedRequest info = server.takeRequest();
        assertEquals("osr-pt", info.getRequestUrl().queryParameter("tag"));
    }

    @Test
    void listByTag_SID过期返回403_自动重新登录并重试成功() throws Exception {
        PtDownloaderPlus config = config(6);

        // 第一轮：登录 + 查询成功，SID 被缓存
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("[]"));
        client.listByTag(config, "osr-pt");
        server.takeRequest();
        server.takeRequest();

        // 第二轮：缓存的 SID 已过期 → 403 → 重新登录 → 重试成功
        server.enqueue(new MockResponse().setResponseCode(403));
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("""
                [{"hash":"AABBCC","name":"Show.S01E01","progress":1.0,"state":"uploading","save_path":"/data"}]
                """));

        List<DownloaderTorrent> list = client.listByTag(config, "osr-pt");

        assertEquals(1, list.size());
        assertEquals("/api/v2/torrents/info", server.takeRequest().getPath().split("\\?")[0]);
        assertEquals("/api/v2/auth/login", server.takeRequest().getPath());
        assertEquals("/api/v2/torrents/info", server.takeRequest().getPath().split("\\?")[0]);
    }

    @Test
    void listByTag_连续两次403_抛IOException不无限重试() {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setResponseCode(403));
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setResponseCode(403));

        assertThrows(IOException.class, () -> client.listByTag(config(7), "osr-pt"));
    }

    @Test
    void testConnection_版本接口正常_判定连通() throws Exception {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("v4.6.2"));

        assertTrue(client.testConnection(config(8)));
    }

    @Test
    void testConnection_登录失败_判定不连通而非抛异常() {
        server.enqueue(new MockResponse().setBody("Fails."));

        assertFalse(client.testConnection(config(9)));
    }

    @Test
    void testConnection_地址不可达_判定不连通() throws IOException {
        PtDownloaderPlus config = config(10);
        server.shutdown();

        assertFalse(client.testConnection(config));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=QbittorrentClientTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `QbittorrentClient` 找不到符号

- [ ] **步骤 3：编写实现**

创建 `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/downloader/QbittorrentClient.java`：

```java
package com.ruoyi.openliststrm.pt.downloader;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * qBittorrent Web API v2 客户端。
 * <p>
 * SID 按下载器 ID 缓存在内存中，遇到 403（会话过期）时自动重新登录并重试一次。
 * 进程重启后缓存丢失，首次调用重新登录即可，无需持久化。
 * </p>
 *
 * @author Jack
 */
@Slf4j
@Component
public class QbittorrentClient implements IDownloaderClient {

    private static final String TYPE = "QBITTORRENT";
    private static final String OK = "Ok.";

    private final OkHttpClient httpClient;

    /** downloaderId -> SID */
    private final Map<Integer, String> sidCache = new ConcurrentHashMap<>();

    public QbittorrentClient(OkHttpClient sharedOkHttpClient) {
        this.httpClient = sharedOkHttpClient;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean testConnection(PtDownloaderPlus config) {
        try {
            String version = get(config, "/api/v2/app/version", Map.of());
            log.info("下载器[{}]连通，版本：{}", config.getName(), version);
            return true;
        } catch (Exception e) {
            log.warn("下载器[{}]连通性测试失败：{}", config.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public void addTorrent(PtDownloaderPlus config, String downloadUrl, String savePath, String tag) throws IOException {
        FormBody body = new FormBody.Builder()
                .add("urls", downloadUrl)
                .add("savepath", savePath)
                .add("tags", tag)
                .build();
        String response = post(config, "/api/v2/torrents/add", body);
        if (!OK.equalsIgnoreCase(response.trim())) {
            throw new IOException("qBittorrent 拒绝添加种子，响应：" + response);
        }
        log.info("已推送种子到下载器[{}]：{}", config.getName(), downloadUrl);
    }

    @Override
    public List<DownloaderTorrent> listByTag(PtDownloaderPlus config, String tag) throws IOException {
        String json = get(config, "/api/v2/torrents/info", Map.of("tag", tag));
        List<DownloaderTorrent> result = new ArrayList<>();
        if (StringUtils.isBlank(json)) {
            return result;
        }
        JSONArray array = JSONArray.parse(json);
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            DownloaderTorrent torrent = new DownloaderTorrent();
            String hash = item.getString("hash");
            torrent.setHash(hash == null ? null : hash.toLowerCase());
            torrent.setName(item.getString("name"));
            torrent.setProgress(item.getDoubleValue("progress"));
            torrent.setRawState(item.getString("state"));
            torrent.setSavePath(item.getString("save_path"));
            result.add(torrent);
        }
        return result;
    }

    // ---------- 内部：带会话管理的请求执行 ----------

    private String get(PtDownloaderPlus config, String path, Map<String, String> query) throws IOException {
        return executeWithSession(config, sid -> {
            HttpUrl.Builder builder = HttpUrl.parse(config.baseUrl() + path).newBuilder();
            query.forEach(builder::addQueryParameter);
            return new Request.Builder()
                    .url(builder.build())
                    .header("Cookie", "SID=" + sid)
                    .get()
                    .build();
        });
    }

    private String post(PtDownloaderPlus config, String path, RequestBody body) throws IOException {
        return executeWithSession(config, sid -> new Request.Builder()
                .url(config.baseUrl() + path)
                .header("Cookie", "SID=" + sid)
                .post(body)
                .build());
    }

    /**
     * 使用缓存 SID 执行请求；遇 403 视为会话过期，重新登录后重试一次。
     */
    private String executeWithSession(PtDownloaderPlus config, RequestFactory factory) throws IOException {
        String sid = sidCache.get(config.getId());
        if (sid == null) {
            sid = login(config);
        }

        try (Response response = httpClient.newCall(factory.build(sid)).execute()) {
            if (response.code() != 403) {
                return readSuccessful(response);
            }
        }

        // 403：会话过期，重新登录后重试一次
        sidCache.remove(config.getId());
        String freshSid = login(config);
        try (Response retry = httpClient.newCall(factory.build(freshSid)).execute()) {
            return readSuccessful(retry);
        }
    }

    private String readSuccessful(Response response) throws IOException {
        if (!response.isSuccessful()) {
            throw new IOException("qBittorrent 返回 HTTP " + response.code());
        }
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    /**
     * 登录并缓存 SID。
     *
     * @throws IOException 凭据错误（响应体非 Ok.）或未返回 SID Cookie
     */
    private String login(PtDownloaderPlus config) throws IOException {
        FormBody body = new FormBody.Builder()
                .add("username", StringUtils.defaultString(config.getUsername(), ""))
                .add("password", StringUtils.defaultString(config.getPassword(), ""))
                .build();
        Request request = new Request.Builder()
                .url(config.baseUrl() + "/api/v2/auth/login")
                .header("Referer", config.baseUrl())
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String text = readSuccessful(response);
            // 注意：登录失败时 qBittorrent 同样返回 200，响应体为 Fails.
            if (!OK.equalsIgnoreCase(text.trim())) {
                throw new IOException("qBittorrent 登录失败，请检查用户名密码");
            }
            String sid = extractSid(response);
            if (sid == null) {
                throw new IOException("qBittorrent 登录成功但未返回 SID");
            }
            sidCache.put(config.getId(), sid);
            return sid;
        }
    }

    private String extractSid(Response response) {
        for (String cookie : response.headers("Set-Cookie")) {
            if (cookie.startsWith("SID=")) {
                int end = cookie.indexOf(';');
                return end > 0 ? cookie.substring(4, end) : cookie.substring(4);
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface RequestFactory {
        Request build(String sid);
    }
}
```

> **已核实：** `StringUtils.defaultString` 可用（继承自 commons-lang3）。

创建 `DownloaderClientFactory.java`：

```java
package com.ruoyi.openliststrm.pt.downloader;

import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按 pt_downloader.type 分发到具体的下载器实现。
 * 新增下载器类型只需实现 IDownloaderClient 并注册为 Bean，本类无需改动。
 *
 * @author Jack
 */
@Component
public class DownloaderClientFactory {

    private final Map<String, IDownloaderClient> clients;

    public DownloaderClientFactory(List<IDownloaderClient> clientList) {
        this.clients = clientList.stream()
                .collect(Collectors.toMap(IDownloaderClient::type, Function.identity()));
    }

    /**
     * @throws IllegalArgumentException 配置的类型没有对应实现
     */
    public IDownloaderClient get(PtDownloaderPlus config) {
        IDownloaderClient client = clients.get(config.getType());
        if (client == null) {
            throw new IllegalArgumentException("不支持的下载器类型：" + config.getType());
        }
        return client;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=QbittorrentClientTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 11, Failures: 0, Errors: 0

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/downloader/ ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/downloader/
git commit -m "feat(pt): 新增 qBittorrent 客户端与下载器工厂"
```

---

## 任务 9：媒体服务器实体与 EmbyClient

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/PtMediaServerPlus.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/mapper/PtMediaServerPlusMapper.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/IPtMediaServerPlusService.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/impl/PtMediaServerPlusServiceImpl.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/media/IMediaServerClient.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/media/EmbyClient.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/media/MediaServerClientFactory.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/media/EmbyClientTest.java`

> **背景（工程师必读）：** Jellyfin 由 Emby fork 而来，两者以下接口完全兼容，因此一个客户端同时支持两种类型：
> - 鉴权：请求头 `X-Emby-Token: {apiKey}`
> - 系统信息：`GET /System/Info`（用于连通性测试）
> - 按 TMDb ID 找剧集：`GET /Items?IncludeItemTypes=Series&Recursive=true&AnyProviderIdEquals=tmdb.{id}` → `Items[].Id`
> - 列某季的集：`GET /Shows/{seriesId}/Episodes?season={n}` → `Items[].IndexNumber`
> - 按 TMDb ID 找电影：`GET /Items?IncludeItemTypes=Movie&Recursive=true&AnyProviderIdEquals=tmdb.{id}`
>
> `userId` 可选：配置了就作为 `userId` 查询参数带上（按用户可见范围过滤），没配就不带（按服务器全库）。

- [ ] **步骤 1：编写实体、Mapper、Service**

创建 `PtMediaServerPlus.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatisplus.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * PT 媒体服务器配置（Emby/Jellyfin）
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
@Getter
@Setter
@TableName("pt_media_server")
public class PtMediaServerPlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 媒体服务器展示名 */
    @TableField("name")
    private String name;

    /** 类型 EMBY / JELLYFIN */
    @TableField("type")
    private String type;

    /** 服务器地址，形如 http://emby:8096 */
    @TableField("url")
    private String url;

    /** API Key，明文存储 */
    @TableField("api_key")
    private String apiKey;

    /** 用户ID，可空 */
    @TableField("user_id")
    private String userId;

    /** 是否启用 0-否 1-是 */
    @TableField("enabled")
    private String enabled;

    /**
     * 归一化后的基地址，去掉末尾斜杠，避免拼接出双斜杠。
     */
    public String baseUrl() {
        String value = url == null ? "" : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
```

创建 `PtMediaServerPlusMapper.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtMediaServerPlus;

/**
 * <p>
 * PT 媒体服务器配置 Mapper 接口
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
public interface PtMediaServerPlusMapper extends BaseMapper<PtMediaServerPlus> {

}
```

创建 `IPtMediaServerPlusService.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.openliststrm.mybatisplus.domain.PtMediaServerPlus;

/**
 * <p>
 * PT 媒体服务器配置 服务类
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
public interface IPtMediaServerPlusService extends IService<PtMediaServerPlus> {

    /**
     * 取当前启用的媒体服务器。多条启用时取 ID 最小的一条，无启用时返回 null。
     */
    PtMediaServerPlus getActive();
}
```

创建 `PtMediaServerPlusServiceImpl.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.openliststrm.mybatisplus.domain.PtMediaServerPlus;
import com.ruoyi.openliststrm.mybatisplus.mapper.PtMediaServerPlusMapper;
import com.ruoyi.openliststrm.mybatisplus.service.IPtMediaServerPlusService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * PT 媒体服务器配置 服务实现类
 * </p>
 *
 * @author Jack
 * @since 2026-07-24
 */
@Service
public class PtMediaServerPlusServiceImpl extends ServiceImpl<PtMediaServerPlusMapper, PtMediaServerPlus> implements IPtMediaServerPlusService {

    @Override
    public PtMediaServerPlus getActive() {
        return lambdaQuery()
                .eq(PtMediaServerPlus::getEnabled, "1")
                .orderByAsc(PtMediaServerPlus::getId)
                .last("limit 1")
                .one();
    }
}
```

- [ ] **步骤 2：编写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/media/EmbyClientTest.java`：

```java
package com.ruoyi.openliststrm.pt.media;

import com.ruoyi.openliststrm.mybatisplus.domain.PtMediaServerPlus;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbyClientTest {

    private MockWebServer server;
    private EmbyClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new EmbyClient(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private PtMediaServerPlus config(String userId) {
        PtMediaServerPlus c = new PtMediaServerPlus();
        c.setId(1);
        c.setName("emby");
        c.setType("EMBY");
        c.setUrl(server.url("/").toString());
        c.setApiKey("emby-key");
        c.setUserId(userId);
        return c;
    }

    @Test
    void type_返回EMBY() {
        assertEquals("EMBY", client.type());
    }

    @Test
    void listEpisodes_返回该季已有集号集合() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"Items":[{"Id":"series-42","Name":"某剧"}]}
                """));
        server.enqueue(new MockResponse().setBody("""
                {"Items":[
                  {"Id":"ep1","IndexNumber":1},
                  {"Id":"ep2","IndexNumber":2},
                  {"Id":"ep5","IndexNumber":5}
                ]}
                """));

        Set<Integer> episodes = client.listEpisodes(config(null), "12345", 1);

        assertEquals(Set.of(1, 2, 5), episodes);
    }

    @Test
    void listEpisodes_请求参数正确() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[{\"Id\":\"series-42\"}]}"));
        server.enqueue(new MockResponse().setBody("{\"Items\":[]}"));

        client.listEpisodes(config("user-9"), "12345", 3);

        RecordedRequest lookup = server.takeRequest();
        assertEquals("emby-key", lookup.getHeader("X-Emby-Token"));
        assertEquals("Series", lookup.getRequestUrl().queryParameter("IncludeItemTypes"));
        assertEquals("tmdb.12345", lookup.getRequestUrl().queryParameter("AnyProviderIdEquals"));

        RecordedRequest episodes = server.takeRequest();
        assertTrue(episodes.getPath().startsWith("/Shows/series-42/Episodes"));
        assertEquals("3", episodes.getRequestUrl().queryParameter("season"));
        assertEquals("user-9", episodes.getRequestUrl().queryParameter("userId"));
    }

    @Test
    void listEpisodes_未配置userId_不带该参数() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[{\"Id\":\"series-42\"}]}"));
        server.enqueue(new MockResponse().setBody("{\"Items\":[]}"));

        client.listEpisodes(config(null), "12345", 1);

        server.takeRequest();
        RecordedRequest episodes = server.takeRequest();
        assertEquals(null, episodes.getRequestUrl().queryParameter("userId"));
    }

    @Test
    void listEpisodes_剧集不在库中_返回空集合且不发第二次请求() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[]}"));

        assertTrue(client.listEpisodes(config(null), "99999", 1).isEmpty());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void listEpisodes_集号字段缺失的条目_被忽略() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[{\"Id\":\"series-42\"}]}"));
        server.enqueue(new MockResponse().setBody("""
                {"Items":[{"Id":"ep1","IndexNumber":1},{"Id":"special"}]}
                """));

        assertEquals(Set.of(1), client.listEpisodes(config(null), "12345", 1));
    }

    @Test
    void listEpisodes_HTTP错误_抛IOException() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertThrows(IOException.class, () -> client.listEpisodes(config(null), "12345", 1));
    }

    @Test
    void hasMovie_命中返回true() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[{\"Id\":\"movie-7\"}]}"));

        assertTrue(client.hasMovie(config(null), "550"));

        RecordedRequest request = server.takeRequest();
        assertEquals("Movie", request.getRequestUrl().queryParameter("IncludeItemTypes"));
        assertEquals("tmdb.550", request.getRequestUrl().queryParameter("AnyProviderIdEquals"));
    }

    @Test
    void hasMovie_未命中返回false() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[]}"));

        assertFalse(client.hasMovie(config(null), "550"));
    }

    @Test
    void testConnection_系统信息接口正常_判定连通() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"ServerName\":\"emby\",\"Version\":\"4.8.0\"}"));

        assertTrue(client.testConnection(config(null)));
        assertEquals("/System/Info", server.takeRequest().getPath());
    }

    @Test
    void testConnection_鉴权失败_判定不连通而非抛异常() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertFalse(client.testConnection(config(null)));
    }
}
```

- [ ] **步骤 3：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=EmbyClientTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `EmbyClient` 找不到符号

- [ ] **步骤 4：编写实现**

创建 `IMediaServerClient.java`：

```java
package com.ruoyi.openliststrm.pt.media;

import com.ruoyi.openliststrm.mybatisplus.domain.PtMediaServerPlus;

import java.io.IOException;
import java.util.Set;

/**
 * 媒体服务器抽象接口。用于查询「某作品已入库了哪些内容」，
 * 是订阅集数追踪的权威数据来源。
 *
 * @author Jack
 */
public interface IMediaServerClient {

    /**
     * 支持的类型，与 pt_media_server.type 取值一致，如 EMBY / JELLYFIN。
     */
    String type();

    /**
     * 连通性测试。任何异常均视为不连通，不向上抛。
     */
    boolean testConnection(PtMediaServerPlus config);

    /**
     * 查询某剧某季在库中已有的集号集合。
     *
     * @param tmdbId TMDb 剧集 ID
     * @param season 季号
     * @return 已有集号集合；该剧不在库中时返回空集合
     * @throws IOException 网络异常或服务器返回非 2xx
     */
    Set<Integer> listEpisodes(PtMediaServerPlus config, String tmdbId, int season) throws IOException;

    /**
     * 查询某电影是否已在库中。
     *
     * @param tmdbId TMDb 电影 ID
     * @throws IOException 网络异常或服务器返回非 2xx
     */
    boolean hasMovie(PtMediaServerPlus config, String tmdbId) throws IOException;
}
```

创建 `EmbyClient.java`：

```java
package com.ruoyi.openliststrm.pt.media;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtMediaServerPlus;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Emby / Jellyfin 客户端。两者 API 同源，以下用到的接口完全兼容，故共用一个实现。
 *
 * @author Jack
 */
@Slf4j
@Component
public class EmbyClient implements IMediaServerClient {

    private static final String TYPE = "EMBY";

    private final OkHttpClient httpClient;

    public EmbyClient(OkHttpClient sharedOkHttpClient) {
        this.httpClient = sharedOkHttpClient;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean testConnection(PtMediaServerPlus config) {
        try {
            String body = get(config, "/System/Info", Map.of());
            JSONObject info = JSONObject.parse(body);
            log.info("媒体服务器[{}]连通，版本：{}", config.getName(), info.getString("Version"));
            return true;
        } catch (Exception e) {
            log.warn("媒体服务器[{}]连通性测试失败：{}", config.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public Set<Integer> listEpisodes(PtMediaServerPlus config, String tmdbId, int season) throws IOException {
        Set<Integer> result = new HashSet<>();

        String seriesId = findItemId(config, "Series", tmdbId);
        if (seriesId == null) {
            log.debug("媒体服务器中未找到 tmdbId={} 的剧集", tmdbId);
            return result;
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("season", String.valueOf(season));
        if (StringUtils.isNotBlank(config.getUserId())) {
            query.put("userId", config.getUserId());
        }

        String body = get(config, "/Shows/" + seriesId + "/Episodes", query);
        JSONArray items = JSONObject.parse(body).getJSONArray("Items");
        if (items == null) {
            return result;
        }
        for (int i = 0; i < items.size(); i++) {
            Integer index = items.getJSONObject(i).getInteger("IndexNumber");
            // 特别篇等条目没有 IndexNumber，直接忽略
            if (index != null) {
                result.add(index);
            }
        }
        return result;
    }

    @Override
    public boolean hasMovie(PtMediaServerPlus config, String tmdbId) throws IOException {
        return findItemId(config, "Movie", tmdbId) != null;
    }

    /**
     * 按 TMDb ID 查找条目，返回其在媒体服务器中的 Id；未找到返回 null。
     */
    private String findItemId(PtMediaServerPlus config, String itemType, String tmdbId) throws IOException {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("IncludeItemTypes", itemType);
        query.put("Recursive", "true");
        query.put("AnyProviderIdEquals", "tmdb." + tmdbId);
        if (StringUtils.isNotBlank(config.getUserId())) {
            query.put("userId", config.getUserId());
        }

        String body = get(config, "/Items", query);
        JSONArray items = JSONObject.parse(body).getJSONArray("Items");
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.getJSONObject(0).getString("Id");
    }

    private String get(PtMediaServerPlus config, String path, Map<String, String> query) throws IOException {
        HttpUrl.Builder builder = HttpUrl.parse(config.baseUrl() + path).newBuilder();
        query.forEach(builder::addQueryParameter);

        Request request = new Request.Builder()
                .url(builder.build())
                .header("X-Emby-Token", config.getApiKey())
                .header("Accept", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("媒体服务器返回 HTTP " + response.code());
            }
            ResponseBody body = response.body();
            return body == null ? "{}" : body.string();
        }
    }
}
```

创建 `MediaServerClientFactory.java`：

```java
package com.ruoyi.openliststrm.pt.media;

import com.ruoyi.openliststrm.mybatisplus.domain.PtMediaServerPlus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按 pt_media_server.type 分发到具体的媒体服务器实现。
 * <p>
 * Jellyfin 与 Emby 的相关接口完全兼容，故 JELLYFIN 复用 EmbyClient。
 * 未来接入 Plex 等异构服务器时，实现 IMediaServerClient 并注册为 Bean 即可。
 * </p>
 *
 * @author Jack
 */
@Component
public class MediaServerClientFactory {

    private final Map<String, IMediaServerClient> clients;
    private final EmbyClient embyClient;

    public MediaServerClientFactory(List<IMediaServerClient> clientList, EmbyClient embyClient) {
        this.clients = clientList.stream()
                .collect(Collectors.toMap(IMediaServerClient::type, Function.identity()));
        this.embyClient = embyClient;
    }

    /**
     * @throws IllegalArgumentException 配置的类型没有对应实现
     */
    public IMediaServerClient get(PtMediaServerPlus config) {
        if ("JELLYFIN".equals(config.getType())) {
            return embyClient;
        }
        IMediaServerClient client = clients.get(config.getType());
        if (client == null) {
            throw new IllegalArgumentException("不支持的媒体服务器类型：" + config.getType());
        }
        return client;
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=EmbyClientTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 11, Failures: 0, Errors: 0

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/ ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/media/ ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/media/
git commit -m "feat(pt): 新增媒体服务器实体与 Emby/Jellyfin 客户端"
```

---

## 任务 10：三个 REST Controller

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtIndexerRestController.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtDownloaderRestController.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtMediaServerRestController.java`

> **约定：** 继承 `BaseCrudRestController<S, T>` 复用 list/getById/add/edit/delete 五个端点，子类只实现 `buildQueryWrapper` 并追加各自的 `/test` 连通性测试端点。路径前缀沿用 `/api/openliststrm/`。

- [ ] **步骤 1：编写索引器 Controller**

创建 `PtIndexerRestController.java`：

```java
package com.ruoyi.openliststrm.controller.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtIndexerPlusService;
import com.ruoyi.openliststrm.pt.indexer.TorznabClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PT Torznab 索引器配置 REST API 控制器
 *
 * @author Jack
 * @date 2026-07-24
 */
@RestController
@RequestMapping("/api/openliststrm/pt-indexers")
public class PtIndexerRestController extends BaseCrudRestController<IPtIndexerPlusService, PtIndexerPlus> {

    @Autowired
    private TorznabClient torznabClient;

    @Override
    protected Wrapper<PtIndexerPlus> buildQueryWrapper(PtIndexerPlus entity) {
        LambdaQueryWrapper<PtIndexerPlus> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(entity.getName())) {
            wrapper.like(PtIndexerPlus::getName, entity.getName());
        }
        if (StringUtils.isNotBlank(entity.getEnabled())) {
            wrapper.eq(PtIndexerPlus::getEnabled, entity.getEnabled());
        }
        wrapper.orderByAsc(PtIndexerPlus::getId);
        return wrapper;
    }

    /**
     * 连通性测试。接收前端表单当前值，无需先保存即可测试。
     */
    @PostMapping("/test")
    public Result<Void> test(@RequestBody PtIndexerPlus entity) {
        if (StringUtils.isBlank(entity.getUrl()) || StringUtils.isBlank(entity.getApiKey())) {
            return Result.error("接口地址与 apikey 不能为空");
        }
        return torznabClient.testConnection(entity)
                ? Result.success()
                : Result.error("连接失败，请检查地址、apikey 与网络");
    }
}
```

- [ ] **步骤 2：编写下载器 Controller**

创建 `PtDownloaderRestController.java`：

```java
package com.ruoyi.openliststrm.controller.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloaderPlusService;
import com.ruoyi.openliststrm.pt.downloader.DownloaderClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PT 下载器配置 REST API 控制器
 *
 * @author Jack
 * @date 2026-07-24
 */
@RestController
@RequestMapping("/api/openliststrm/pt-downloaders")
public class PtDownloaderRestController extends BaseCrudRestController<IPtDownloaderPlusService, PtDownloaderPlus> {

    @Autowired
    private DownloaderClientFactory downloaderClientFactory;

    @Override
    protected Wrapper<PtDownloaderPlus> buildQueryWrapper(PtDownloaderPlus entity) {
        LambdaQueryWrapper<PtDownloaderPlus> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(entity.getName())) {
            wrapper.like(PtDownloaderPlus::getName, entity.getName());
        }
        if (StringUtils.isNotBlank(entity.getEnabled())) {
            wrapper.eq(PtDownloaderPlus::getEnabled, entity.getEnabled());
        }
        wrapper.orderByAsc(PtDownloaderPlus::getId);
        return wrapper;
    }

    /**
     * 连通性测试。
     */
    @PostMapping("/test")
    public Result<Void> test(@RequestBody PtDownloaderPlus entity) {
        if (StringUtils.isBlank(entity.getHost()) || entity.getPort() == null) {
            return Result.error("主机与端口不能为空");
        }
        try {
            return downloaderClientFactory.get(entity).testConnection(entity)
                    ? Result.success()
                    : Result.error("连接失败，请检查地址、端口与用户名密码");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 校验保存路径是否位于某个文件同步任务的监听目录之下。
     * 不满足不阻断保存，仅返回提示，由前端以警告形式展示。
     */
    @PostMapping("/validate-save-path")
    public Result<String> validateSavePath(@RequestBody PtDownloaderPlus entity) {
        String message = service.validateSavePath(entity.getSavePath());
        return message == null ? Result.success() : Result.success(message);
    }
}
```

- [ ] **步骤 3：编写媒体服务器 Controller**

创建 `PtMediaServerRestController.java`：

```java
package com.ruoyi.openliststrm.controller.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtMediaServerPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IPtMediaServerPlusService;
import com.ruoyi.openliststrm.pt.media.MediaServerClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PT 媒体服务器配置 REST API 控制器
 *
 * @author Jack
 * @date 2026-07-24
 */
@RestController
@RequestMapping("/api/openliststrm/pt-media-servers")
public class PtMediaServerRestController extends BaseCrudRestController<IPtMediaServerPlusService, PtMediaServerPlus> {

    @Autowired
    private MediaServerClientFactory mediaServerClientFactory;

    @Override
    protected Wrapper<PtMediaServerPlus> buildQueryWrapper(PtMediaServerPlus entity) {
        LambdaQueryWrapper<PtMediaServerPlus> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(entity.getName())) {
            wrapper.like(PtMediaServerPlus::getName, entity.getName());
        }
        if (StringUtils.isNotBlank(entity.getEnabled())) {
            wrapper.eq(PtMediaServerPlus::getEnabled, entity.getEnabled());
        }
        wrapper.orderByAsc(PtMediaServerPlus::getId);
        return wrapper;
    }

    /**
     * 连通性测试。
     */
    @PostMapping("/test")
    public Result<Void> test(@RequestBody PtMediaServerPlus entity) {
        if (StringUtils.isBlank(entity.getUrl()) || StringUtils.isBlank(entity.getApiKey())) {
            return Result.error("服务器地址与 API Key 不能为空");
        }
        try {
            return mediaServerClientFactory.get(entity).testConnection(entity)
                    ? Result.success()
                    : Result.error("连接失败，请检查地址、API Key 与网络");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
```

- [ ] **步骤 4：全量测试与编译验证**

运行：`mvn -pl ruoyi-openliststrm -am test`

预期：BUILD SUCCESS，全部既有测试与新增测试通过

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/
git commit -m "feat(pt): 新增索引器/下载器/媒体服务器 REST 接口与连通性测试端点"
```

---

## 任务 11：引入 vitest 并让 useTaskList 支持无「执行」动作的页面

**文件：**
- 修改：`openlist-web/package.json`
- 创建：`openlist-web/vitest.config.ts`
- 修改：`openlist-web/src/composables/useTaskList.ts`
- 测试：`openlist-web/src/composables/__tests__/useTaskList.spec.ts`

> **背景一（测试基建）：** 前端当前只有 Playwright e2e（`npm run test:e2e`），**没有单元测试运行器**。本任务引入 vitest，供本计划及后续计划 2、3 的前端逻辑使用。用独立的 `vitest.config.ts` 而非在 `vite.config.ts` 里加 `test` 块——后者会把 PWA 插件一并拉进测试环境，徒增噪音。
>
> **背景二（改动动机）：** `useTaskList` 是项目中列表页 CRUD 的通用 composable（`useCopyTask`、`useStrmTask` 等均基于它）。它当前把 `executeApi` 定义为**必填**，因为既有页面都是「任务」类页面，都能被执行。
>
> PT 的三个配置页是纯配置，没有「执行」这个动作。为它们传一个空函数只是掩盖问题。正确做法是把 `executeApi` 改为可选，缺失时执行相关方法给出明确提示而非静默失败。这是一处有针对性的小改进，不破坏任何既有调用方（它们都传了 `executeApi`）。

- [ ] **步骤 1：安装 vitest 依赖**

运行：

```bash
cd openlist-web && npm install -D vitest@^1.6.0 @vue/test-utils@^2.4.5 jsdom@^24.0.0
```

`vitest 1.x` 对应 Vite 5.x（项目当前 `vite ^5.2.8`），不要装 2.x 以免 peer 版本冲突。

- [ ] **步骤 2：新增 vitest 配置与脚本**

创建 `openlist-web/vitest.config.ts`：

```typescript
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 单元测试专用配置。
 * 不复用 vite.config.ts：那份配置带 PWA、自动导入等插件，
 * 在测试环境下只会制造噪音，单测需要的只有 vue 插件与 @ 别名。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    globals: false,
    // 只收 src 下的 .spec.ts，避免把 Playwright 的 e2e 用例也跑进来
    include: ['src/**/*.spec.ts']
  }
})
```

修改 `openlist-web/package.json` 的 `scripts`，在 `test:e2e` 之后追加一行（注意为 `test:e2e` 补逗号）：

```json
    "test:e2e": "playwright test",
    "test:unit": "vitest run"
```

- [ ] **步骤 3：编写失败的测试**

创建 `openlist-web/src/composables/__tests__/useTaskList.spec.ts`：

```typescript
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ElMessage } from 'element-plus'
import { useTaskList } from '../useTaskList'

const listApi = () => Promise.resolve({ records: [], total: 0 })

function build(overrides: Record<string, any> = {}) {
  return useTaskList({
    listApi,
    addApi: vi.fn(),
    updateApi: vi.fn(),
    deleteApi: vi.fn(),
    idField: 'id',
    initForm: () => ({ id: undefined }),
    rules: {},
    ...overrides
  })
}

describe('useTaskList 的 executeApi 可选性', () => {
  let warnSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    warnSpy = vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({}) as any)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('不传 executeApi 时可以正常构造', () => {
    expect(() => build()).not.toThrow()
  })

  it('不传 executeApi 时 handleExecute 给出提示而非抛异常', async () => {
    const base = build()
    await base.handleExecute('是否确认执行？')
    expect(warnSpy).toHaveBeenCalledWith('该列表不支持执行操作')
  })

  it('不传 executeApi 时 handleExecuteOne 给出提示而非抛异常', async () => {
    const base = build()
    await base.handleExecuteOne({ id: 1 }, '是否确认执行？')
    expect(warnSpy).toHaveBeenCalledWith('该列表不支持执行操作')
  })

  it('传了 executeApi 时不走提示分支', async () => {
    const executeApi = vi.fn().mockResolvedValue({})
    const base = build({ executeApi })
    // 确认弹窗会中断流程，此处只验证没有走到「不支持执行」的提示分支
    await base.handleExecute('是否确认执行？').catch(() => undefined)
    expect(warnSpy).not.toHaveBeenCalledWith('该列表不支持执行操作')
  })
})
```

- [ ] **步骤 4：运行测试验证失败**

运行：`cd openlist-web && npm run test:unit`

预期：4 个用例中至少 3 个失败。`executeApi` 当前是必填且未做缺失守卫，`handleExecute` 会在调用 `executeApi(...)` 时抛 `TypeError: executeApi is not a function`，`ElMessage.warning` 从未被调用。

- [ ] **步骤 5：修改 useTaskList**

修改 `openlist-web/src/composables/useTaskList.ts`，共三处。

其一，接口中的 `executeApi` 改为可选并补注释：

```typescript
  /** 批量删除 API。不传则退化为逐条调用 deleteApi */
  batchDeleteApi?: (ids: number[]) => Promise<any>
  /** 执行 API。配置类页面（如 PT 索引器/下载器/媒体服务器）没有执行动作，可不传 */
  executeApi?: (ids: number[]) => Promise<any>
```

其二，`handleExecuteOne` 增加缺失守卫：

```typescript
  const handleExecuteOne = async (row: any, confirmMsg: string) => {
    if (!executeApi) {
      ElMessage.warning('该列表不支持执行操作')
      return
    }
    try {
      await ElMessageBox.confirm(confirmMsg, '提示', { type: 'warning' })
      await executeApi([row[idField]])
      ElMessage.success('执行成功')
      getList()
    } catch (e) { if (e !== 'cancel') console.error(e) }
  }
```

其三，`handleExecute` 增加同样的守卫：

```typescript
  const handleExecute = async (confirmMsg: string) => {
    if (!executeApi) {
      ElMessage.warning('该列表不支持执行操作')
      return
    }
    try {
      await ElMessageBox.confirm(confirmMsg, '警告', { type: 'warning' })
      await executeApi(selectedIds.value)
      ElMessage.success('执行成功')
      getList()
    } catch (e) { if (e !== 'cancel') console.error(e) }
  }
```

- [ ] **步骤 6：运行测试验证通过**

运行：`cd openlist-web && npm run test:unit`

预期：Test Files 1 passed，Tests 4 passed

- [ ] **步骤 7：验证既有页面未受影响**

运行：`cd openlist-web && npm run lint && npm run build`

预期：lint 无错误，vue-tsc 通过。`useCopyTask`、`useStrmTask` 等既有 composable 都传了 `executeApi`，改为可选不影响它们。

- [ ] **步骤 8：Commit**

```bash
git add openlist-web/package.json openlist-web/package-lock.json openlist-web/vitest.config.ts openlist-web/src/composables/useTaskList.ts openlist-web/src/composables/__tests__/
git commit -m "refactor(web): 引入 vitest，useTaskList 的 executeApi 改为可选"
```

---

## 任务 12：前端 API 封装

**文件：**
- 创建：`openlist-web/src/api/openlist/ptIndexer.ts`
- 创建：`openlist-web/src/api/openlist/ptDownloader.ts`
- 创建：`openlist-web/src/api/openlist/ptMediaServer.ts`

> **约定（严格遵守，参照 `openlist-web/src/api/openlist/copyTask.ts`）：**
> - 使用 `request.get` / `request.post` / `request.put` / `request.delete` 方法形式，**不要**用 `request({ url, method })` 对象形式
> - 函数命名为 `getXxxListApi` / `addXxxApi` / `updateXxxApi` / `deleteXxxApi`，后缀 `Api` 不能省
> - 列表接口的返回类型标注为 `request.get<any, PageResult<any>>(...)`
> - axios 拦截器已剥离 `{ code, msg, data }` 外壳，函数直接返回 `data`
> - 路径不带 `/api` 前缀（由 request.ts 的 baseURL 统一加）

- [ ] **步骤 1：编写索引器 API**

创建 `openlist-web/src/api/openlist/ptIndexer.ts`：

```typescript
import request from '@/api/request'
import type { PageResult, SearchParams } from '@/types'

export function getPtIndexerListApi(params: SearchParams) {
  return request.get<any, PageResult<any>>('/openliststrm/pt-indexers', { params })
}

export function addPtIndexerApi(data: any) {
  return request.post('/openliststrm/pt-indexers', data)
}

export function updatePtIndexerApi(data: any) {
  return request.put('/openliststrm/pt-indexers', data)
}

export function deletePtIndexerApi(id: number) {
  return request.delete(`/openliststrm/pt-indexers/${id}`)
}

/** 连通性测试，传入表单当前值，无需先保存 */
export function testPtIndexerApi(data: any) {
  return request.post('/openliststrm/pt-indexers/test', data)
}
```

- [ ] **步骤 2：编写下载器 API**

创建 `openlist-web/src/api/openlist/ptDownloader.ts`：

```typescript
import request from '@/api/request'
import type { PageResult, SearchParams } from '@/types'

export function getPtDownloaderListApi(params: SearchParams) {
  return request.get<any, PageResult<any>>('/openliststrm/pt-downloaders', { params })
}

export function addPtDownloaderApi(data: any) {
  return request.post('/openliststrm/pt-downloaders', data)
}

export function updatePtDownloaderApi(data: any) {
  return request.put('/openliststrm/pt-downloaders', data)
}

export function deletePtDownloaderApi(id: number) {
  return request.delete(`/openliststrm/pt-downloaders/${id}`)
}

/** 连通性测试 */
export function testPtDownloaderApi(data: any) {
  return request.post('/openliststrm/pt-downloaders/test', data)
}

/** 校验保存路径。返回空串表示通过，非空为警告文案 */
export function validateSavePathApi(data: any) {
  return request.post<any, string>('/openliststrm/pt-downloaders/validate-save-path', data)
}
```

- [ ] **步骤 3：编写媒体服务器 API**

创建 `openlist-web/src/api/openlist/ptMediaServer.ts`：

```typescript
import request from '@/api/request'
import type { PageResult, SearchParams } from '@/types'

export function getPtMediaServerListApi(params: SearchParams) {
  return request.get<any, PageResult<any>>('/openliststrm/pt-media-servers', { params })
}

export function addPtMediaServerApi(data: any) {
  return request.post('/openliststrm/pt-media-servers', data)
}

export function updatePtMediaServerApi(data: any) {
  return request.put('/openliststrm/pt-media-servers', data)
}

export function deletePtMediaServerApi(id: number) {
  return request.delete(`/openliststrm/pt-media-servers/${id}`)
}

/** 连通性测试 */
export function testPtMediaServerApi(data: any) {
  return request.post('/openliststrm/pt-media-servers/test', data)
}
```

- [ ] **步骤 4：类型检查**

运行：`cd openlist-web && npm run build`

预期：vue-tsc 类型检查通过

- [ ] **步骤 5：Commit**

```bash
git add openlist-web/src/api/openlist/ptIndexer.ts openlist-web/src/api/openlist/ptDownloader.ts openlist-web/src/api/openlist/ptMediaServer.ts
git commit -m "feat(pt): 新增索引器/下载器/媒体服务器前端 API 封装"
```

---

## 任务 13：三个配置页的 composable

**文件：**
- 创建：`openlist-web/src/composables/usePtIndexer.ts`
- 创建：`openlist-web/src/composables/usePtDownloader.ts`
- 创建：`openlist-web/src/composables/usePtMediaServer.ts`

> **约定：** 参照 `openlist-web/src/composables/useCopyTask.ts`。业务逻辑放 composable，页面组件只负责渲染（见前端 AGENTS.md 反模式条款「不要在组件中写大量业务逻辑」）。
>
> 三个 composable 共同的额外职责：封装「测试连接」动作 —— 调用对应 test API，成功弹成功提示，失败弹错误提示，过程中维持 loading 状态。

- [ ] **步骤 1：编写索引器 composable**

创建 `openlist-web/src/composables/usePtIndexer.ts`：

```typescript
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useTaskList } from './useTaskList'
import {
  getPtIndexerListApi,
  addPtIndexerApi,
  updatePtIndexerApi,
  deletePtIndexerApi,
  testPtIndexerApi
} from '@/api/openlist/ptIndexer'
import type { SearchParams } from '@/types'

interface PtIndexerQuery extends SearchParams {
  name?: string
  enabled?: string
}

/**
 * PT Torznab 索引器配置 composable
 */
export function usePtIndexer() {
  const base = useTaskList<PtIndexerQuery>({
    listApi: getPtIndexerListApi,
    addApi: addPtIndexerApi,
    updateApi: updatePtIndexerApi,
    deleteApi: deletePtIndexerApi,
    idField: 'id',
    initForm: () => ({
      id: undefined,
      name: undefined,
      url: undefined,
      apiKey: undefined,
      categories: undefined,
      pollInterval: 600,
      enabled: '1'
    }),
    rules: {
      name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
      url: [
        { required: true, message: '接口地址不能为空', trigger: 'blur' },
        {
          pattern: /^https?:\/\//,
          message: '地址须以 http:// 或 https:// 开头',
          trigger: 'blur'
        }
      ],
      apiKey: [{ required: true, message: 'apikey 不能为空', trigger: 'blur' }],
      pollInterval: [
        { required: true, message: '轮询周期不能为空', trigger: 'blur' },
        { type: 'number', min: 60, message: '轮询周期不得小于 60 秒', trigger: 'blur' }
      ]
    },
    defaultQuery: {
      name: undefined,
      enabled: undefined
    }
  })

  const testLoading = ref(false)

  const handleTest = async () => {
    if (!base.form.value.url || !base.form.value.apiKey) {
      ElMessage.warning('请先填写接口地址与 apikey')
      return
    }
    testLoading.value = true
    try {
      await testPtIndexerApi(base.form.value)
      ElMessage.success('连接成功')
    } catch (e: any) {
      ElMessage.error(e?.message || '连接失败')
    } finally {
      testLoading.value = false
    }
  }

  base.getList()

  return { ...base, testLoading, handleTest }
}
```

- [ ] **步骤 2：编写下载器 composable**

创建 `openlist-web/src/composables/usePtDownloader.ts`：

```typescript
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useTaskList } from './useTaskList'
import {
  getPtDownloaderListApi,
  addPtDownloaderApi,
  updatePtDownloaderApi,
  deletePtDownloaderApi,
  testPtDownloaderApi,
  validateSavePathApi
} from '@/api/openlist/ptDownloader'
import type { SearchParams } from '@/types'

interface PtDownloaderQuery extends SearchParams {
  name?: string
  enabled?: string
}

/**
 * PT 下载器配置 composable
 */
export function usePtDownloader() {
  const base = useTaskList<PtDownloaderQuery>({
    listApi: getPtDownloaderListApi,
    addApi: addPtDownloaderApi,
    updateApi: updatePtDownloaderApi,
    deleteApi: deletePtDownloaderApi,
    idField: 'id',
    initForm: () => ({
      id: undefined,
      name: undefined,
      type: 'QBITTORRENT',
      host: undefined,
      port: 8080,
      useHttps: '0',
      username: undefined,
      password: undefined,
      savePath: undefined,
      tag: 'osr-pt',
      enabled: '1'
    }),
    rules: {
      name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
      host: [{ required: true, message: '主机不能为空', trigger: 'blur' }],
      port: [
        { required: true, message: '端口不能为空', trigger: 'blur' },
        { type: 'number', min: 1, max: 65535, message: '端口须在 1-65535 之间', trigger: 'blur' }
      ],
      savePath: [{ required: true, message: '保存路径不能为空', trigger: 'blur' }],
      tag: [{ required: true, message: '标签不能为空', trigger: 'blur' }]
    },
    defaultQuery: {
      name: undefined,
      enabled: undefined
    }
  })

  const testLoading = ref(false)
  /** 保存路径校验警告文案，空串表示无警告 */
  const savePathWarning = ref('')

  const handleTest = async () => {
    if (!base.form.value.host || !base.form.value.port) {
      ElMessage.warning('请先填写主机与端口')
      return
    }
    testLoading.value = true
    try {
      await testPtDownloaderApi(base.form.value)
      ElMessage.success('连接成功')
    } catch (e: any) {
      ElMessage.error(e?.message || '连接失败')
    } finally {
      testLoading.value = false
    }
  }

  /**
   * 保存路径失焦时校验。不阻断保存，仅提示——
   * 路径不在文件同步任务的监听目录下时，下载完成的文件不会被自动上传网盘。
   */
  const handleSavePathBlur = async () => {
    if (!base.form.value.savePath) {
      savePathWarning.value = ''
      return
    }
    try {
      savePathWarning.value = (await validateSavePathApi(base.form.value)) || ''
    } catch {
      savePathWarning.value = ''
    }
  }

  base.getList()

  return { ...base, testLoading, handleTest, savePathWarning, handleSavePathBlur }
}
```

- [ ] **步骤 3：编写媒体服务器 composable**

创建 `openlist-web/src/composables/usePtMediaServer.ts`：

```typescript
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useTaskList } from './useTaskList'
import {
  getPtMediaServerListApi,
  addPtMediaServerApi,
  updatePtMediaServerApi,
  deletePtMediaServerApi,
  testPtMediaServerApi
} from '@/api/openlist/ptMediaServer'
import type { SearchParams } from '@/types'

interface PtMediaServerQuery extends SearchParams {
  name?: string
  enabled?: string
}

/**
 * PT 媒体服务器（Emby/Jellyfin）配置 composable
 */
export function usePtMediaServer() {
  const base = useTaskList<PtMediaServerQuery>({
    listApi: getPtMediaServerListApi,
    addApi: addPtMediaServerApi,
    updateApi: updatePtMediaServerApi,
    deleteApi: deletePtMediaServerApi,
    idField: 'id',
    initForm: () => ({
      id: undefined,
      name: undefined,
      type: 'EMBY',
      url: undefined,
      apiKey: undefined,
      userId: undefined,
      enabled: '1'
    }),
    rules: {
      name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
      url: [
        { required: true, message: '服务器地址不能为空', trigger: 'blur' },
        {
          pattern: /^https?:\/\//,
          message: '地址须以 http:// 或 https:// 开头',
          trigger: 'blur'
        }
      ],
      apiKey: [{ required: true, message: 'API Key 不能为空', trigger: 'blur' }]
    },
    defaultQuery: {
      name: undefined,
      enabled: undefined
    }
  })

  const testLoading = ref(false)

  const handleTest = async () => {
    if (!base.form.value.url || !base.form.value.apiKey) {
      ElMessage.warning('请先填写服务器地址与 API Key')
      return
    }
    testLoading.value = true
    try {
      await testPtMediaServerApi(base.form.value)
      ElMessage.success('连接成功')
    } catch (e: any) {
      ElMessage.error(e?.message || '连接失败')
    } finally {
      testLoading.value = false
    }
  }

  base.getList()

  return { ...base, testLoading, handleTest }
}
```

- [ ] **步骤 4：类型检查**

运行：`cd openlist-web && npm run build`

预期：vue-tsc 类型检查通过

- [ ] **步骤 5：Commit**

```bash
git add openlist-web/src/composables/usePtIndexer.ts openlist-web/src/composables/usePtDownloader.ts openlist-web/src/composables/usePtMediaServer.ts
git commit -m "feat(pt): 新增三个配置页的 composable"
```

---

## 任务 14：前端三个配置页面

**文件：**
- 创建：`openlist-web/src/views/openlist/ptIndexer/index.vue`
- 创建：`openlist-web/src/views/openlist/ptDownloader/index.vue`
- 创建：`openlist-web/src/views/openlist/ptMediaServer/index.vue`
- 修改：`openlist-web/src/router/index.ts`

> **约定：** 结构参照 `openlist-web/src/views/openlist/copyTask/index.vue`（搜索卡片 + 操作栏 + 表格 + 分页 + 弹窗），复用其 `page-container` / `search-card` / `table-card` / `action-bar` / `modern-table` / `modern-dialog` / `pagination-wrapper` 类名，样式由全局 SCSS 提供，页面内无需写 style。
>
> 与 copyTask 的差异：配置页**不做移动端卡片列表**（与 `renameConfig` 一致，配置类页面只有 PC 版），**不做批量执行按钮**。
>
> 自动导入已开启，Element Plus 组件与图标、Vue API 均无需手动 import。

- [ ] **步骤 1：编写媒体服务器配置页（结构最简，作为另外两页的样板）**

创建 `openlist-web/src/views/openlist/ptMediaServer/index.vue`：

```vue
<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-select v-model="queryParams.enabled" placeholder="状态" clearable :style="{ width: '120px' }">
            <el-option label="启用" value="1" />
            <el-option label="停用" value="0" />
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

    <!-- Table Card -->
    <el-card class="table-card">
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" @click="handleAdd('新增媒体服务器')">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
          <el-button type="success" :disabled="single" @click="handleUpdate(undefined, '修改媒体服务器')">
            <el-icon><Edit /></el-icon> 修改
          </el-button>
          <el-button type="danger" :disabled="multiple" @click="handleDelete(undefined, `是否确认删除编号为“${selectedIds}”的媒体服务器？`)">
            <el-icon><Delete /></el-icon> 批量删除
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="名称" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" prop="type" width="110" align="center">
          <template #default="scope">
            {{ scope.row.type === 'JELLYFIN' ? 'Jellyfin' : 'Emby' }}
          </template>
        </el-table-column>
        <el-table-column label="服务器地址" prop="url" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" prop="enabled" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.enabled === '1' ? 'success' : 'danger'">
              {{ scope.row.enabled === '1' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" align="center" />
        <el-table-column label="操作" align="center" width="160" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row, '修改媒体服务器')">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
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

    <!-- Add/Edit Dialog -->
    <el-dialog v-model="open" :title="dialogTitle" width="600px" append-to-body class="modern-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" :style="{ width: '100%' }">
            <el-option label="Emby" value="EMBY" />
            <el-option label="Jellyfin" value="JELLYFIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务器地址" prop="url">
          <el-input v-model="form.url" placeholder="如 http://192.168.1.10:8096" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="请输入 API Key" />
        </el-form-item>
        <el-form-item label="用户ID" prop="userId">
          <el-input v-model="form.userId" placeholder="留空则按服务器全库查询" />
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio value="1">启用</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :loading="testLoading" @click="handleTest">测试连接</el-button>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { usePtMediaServer } from '@/composables/usePtMediaServer'

const showSearch = ref(window.innerWidth >= 768)

const {
  taskList, loading, total, queryParams, getList, handleQuery, resetQuery, queryRef,
  selectedIds, single, multiple, handleSelectionChange,
  open, dialogTitle, submitLoading, formRef, form, rules,
  handleAdd, handleUpdate, submitForm, handleDelete,
  testLoading, handleTest
} = usePtMediaServer()
</script>
```

- [ ] **步骤 2：编写索引器配置页**

创建 `openlist-web/src/views/openlist/ptIndexer/index.vue`。**完整复制步骤 1 的文件**，然后做以下替换：

1. `<script setup>` 中改为 `import { usePtIndexer } from '@/composables/usePtIndexer'`，解构调用改为 `usePtIndexer()`（返回字段与媒体服务器版完全一致）
2. 三处弹窗标题文案：`新增媒体服务器` → `新增索引器`，`修改媒体服务器` → `修改索引器`，批量删除确认文案中的「媒体服务器」→「索引器」
3. 表格列替换为：

```vue
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="名称" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="接口地址" prop="url" min-width="280" show-overflow-tooltip />
        <el-table-column label="分类" prop="categories" width="140" align="center">
          <template #default="scope">
            {{ scope.row.categories || '不限' }}
          </template>
        </el-table-column>
        <el-table-column label="轮询周期" prop="pollInterval" width="100" align="center">
          <template #default="scope">
            {{ scope.row.pollInterval }} 秒
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="enabled" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.enabled === '1' ? 'success' : 'danger'">
              {{ scope.row.enabled === '1' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上次轮询" prop="lastPollTime" width="170" align="center">
          <template #default="scope">
            {{ scope.row.lastPollTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="上次结果" prop="lastStatus" min-width="160" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="!scope.row.lastStatus">-</span>
            <el-tag v-else-if="scope.row.lastStatus === 'OK'" type="success">正常</el-tag>
            <el-tag v-else type="danger">{{ scope.row.lastStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="160" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row, '修改索引器')">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
```

4. 弹窗表单项替换为：

```vue
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="接口地址" prop="url">
          <el-input v-model="form.url" placeholder="如 http://jackett:9117/api/v2.0/indexers/xxx/results/torznab/api" />
        </el-form-item>
        <el-form-item label="apikey" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="请输入 Torznab apikey" />
        </el-form-item>
        <el-form-item label="分类" prop="categories">
          <el-input v-model="form.categories" placeholder="逗号分隔的分类 ID，如 5000,5030；留空表示不限" />
        </el-form-item>
        <el-form-item label="轮询周期" prop="pollInterval">
          <el-input-number v-model="form.pollInterval" :min="60" :step="60" :style="{ width: '200px' }" />
          <span class="form-tip">秒</span>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio value="1">启用</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
```

- [ ] **步骤 3：编写下载器配置页**

创建 `openlist-web/src/views/openlist/ptDownloader/index.vue`。同样以步骤 1 的文件为骨架，做以下替换：

1. `<script setup>` 改为：

```typescript
import { ref } from 'vue'
import { usePtDownloader } from '@/composables/usePtDownloader'

const showSearch = ref(window.innerWidth >= 768)

const {
  taskList, loading, total, queryParams, getList, handleQuery, resetQuery, queryRef,
  selectedIds, single, multiple, handleSelectionChange,
  open, dialogTitle, submitLoading, formRef, form, rules,
  handleAdd, handleUpdate, submitForm, handleDelete,
  testLoading, handleTest, savePathWarning, handleSavePathBlur
} = usePtDownloader()
```

2. 标题文案改为「新增下载器」「修改下载器」，批量删除确认文案中改为「下载器」
3. 表格列替换为：

```vue
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="名称" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" prop="type" width="120" align="center">
          <template #default="scope">
            {{ scope.row.type === 'QBITTORRENT' ? 'qBittorrent' : scope.row.type }}
          </template>
        </el-table-column>
        <el-table-column label="地址" min-width="200" show-overflow-tooltip>
          <template #default="scope">
            {{ (scope.row.useHttps === '1' ? 'https://' : 'http://') + scope.row.host + ':' + scope.row.port }}
          </template>
        </el-table-column>
        <el-table-column label="保存路径" prop="savePath" min-width="220" show-overflow-tooltip />
        <el-table-column label="标签" prop="tag" width="110" align="center" />
        <el-table-column label="状态" prop="enabled" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.enabled === '1' ? 'success' : 'danger'">
              {{ scope.row.enabled === '1' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="160" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row, '修改下载器')">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
```

4. 弹窗表单项替换为（注意保存路径项带失焦校验与警告展示）：

```vue
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" :style="{ width: '100%' }">
            <el-option label="qBittorrent" value="QBITTORRENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机" prop="host">
          <el-input v-model="form.host" placeholder="主机名或 IP，不含协议与端口" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" :style="{ width: '200px' }" />
        </el-form-item>
        <el-form-item label="HTTPS" prop="useHttps">
          <el-radio-group v-model="form.useHttps">
            <el-radio value="0">关闭</el-radio>
            <el-radio value="1">开启</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="保存路径" prop="savePath">
          <el-input v-model="form.savePath" placeholder="种子保存路径" @blur="handleSavePathBlur" />
          <div v-if="savePathWarning" class="save-path-warning">{{ savePathWarning }}</div>
        </el-form-item>
        <el-form-item label="标签" prop="tag">
          <el-input v-model="form.tag" placeholder="推送种子时打的标签" />
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio value="1">启用</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
```

5. 本页需要一小段局部样式，追加到文件末尾：

```vue
<style scoped>
.save-path-warning {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-color-warning);
}
</style>
```

- [ ] **步骤 4：注册路由**

修改 `openlist-web/src/router/index.ts`，在组件映射表中 `'openlist/renameConfig/index'` 一行之后追加三行（注意为原本作为末项的 renameConfig 行补上逗号）：

```typescript
  'openlist/renameConfig/index': () => import('@/views/openlist/renameConfig/index.vue'),
  'openlist/ptIndexer/index': () => import('@/views/openlist/ptIndexer/index.vue'),
  'openlist/ptDownloader/index': () => import('@/views/openlist/ptDownloader/index.vue'),
  'openlist/ptMediaServer/index': () => import('@/views/openlist/ptMediaServer/index.vue')
```

三个页面不做移动端版本，与 `renameConfig` 的处理一致（配置类页面只有 PC 版）。

- [ ] **步骤 5：Lint 与构建验证**

运行：`cd openlist-web && npm run lint && npm run build`

预期：lint 无错误，vue-tsc 类型检查通过，构建成功

- [ ] **步骤 6：Commit**

```bash
git add openlist-web/src/views/openlist/ptIndexer openlist-web/src/views/openlist/ptDownloader openlist-web/src/views/openlist/ptMediaServer openlist-web/src/router/index.ts
git commit -m "feat(pt): 新增索引器/下载器/媒体服务器三个配置页面"
```

---

## 任务 15：端到端验收

**文件：** 无代码变更

> 本任务验证计划 1 的成功标准：用户能在页面上配置三个外部系统并测通连接。

- [ ] **步骤 1：全量构建**

运行：`mvn clean package -DskipTests`

预期：BUILD SUCCESS，生成 `ruoyi-admin/target/ruoyi-admin.jar`

- [ ] **步骤 2：全量测试**

运行：`mvn test`

预期：BUILD SUCCESS，无失败用例

- [ ] **步骤 3：部署验证**

运行：`docker compose up -d --build`

- [ ] **步骤 4：验证数据库迁移已执行**

运行：`docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" osr -e "show tables like 'pt_%'; select menu_id, menu_name, url from sys_menu where menu_id in (2061,2062,2063);"`

预期：输出 `pt_indexer`、`pt_downloader`、`pt_media_server` 三张表，以及三条菜单记录

- [ ] **步骤 5：验证菜单与页面**

在浏览器打开前端，登录后确认 OpenListStrm 菜单下出现「PT索引器」「PT下载器」「媒体服务器」三项，**且图标正常显示**（图标不显示说明类名不在前端映射表中，回到任务 1 步骤 3 修正）。

依次进入三个页面，各新增一条配置，点击「测试连接」。

预期：三个页面均能保存、编辑、删除；「测试连接」在配置正确时提示成功，在故意填错地址时提示失败且不抛未捕获异常。

- [ ] **步骤 6：验证保存路径校验**

在下载器页面，把保存路径填成一个不在任何文件同步任务监听目录下的路径（如 `/tmp/nowhere`），失焦。

预期：字段下方出现橙色警告文案，但仍可保存。

- [ ] **步骤 7：Commit（如有修正）**

若步骤 5、6 中发现问题并修正，提交修正：

```bash
git add -A
git commit -m "fix(pt): 修正配置页面验收中发现的问题"
```

---

## 后续计划

本计划完成后，进入：

- **计划 2：订阅模型与过滤引擎** — `pt_filter_config` / `pt_subscription` / `pt_subscription_episode` / `pt_download_record` 四张表，`TorrentFilterEngine`（Comparator 串联的可配置排序），订阅 CRUD + TMDb 搜索建订阅，前端订阅页
- **计划 3：编排与调度** — `MediaParser.parseLocal`（不触发 TMDb/AI 的纯本地解析）、`SubscriptionEngine`、`RssPollTask` / `DownloadTrackTask` / `LibrarySyncTask` 三个调度器、Telegram 通知

---

## 执行期变更记录

计划文本中的代码是起点，执行过程中经代码审查发现的问题及修正记录于此（避免重跑计划时重蹈覆辙）：

- **任务 3**：`parse()` 原用 `doc.getElementsByTagName("item")` 会跨全文档任意深度匹配，与类内只遍历直接子节点的做法矛盾。已改为只取 `<channel>` 的直接子元素，`<channel>` 缺失返回空列表。另补测试覆盖 size 三级回退的第三级（`torznab:attr name="size"`）。
- **任务 4**：`PtIndexerPlus.lastPollTime` 原写 `String`，与项目既有约定不符（`OpenlistCopyTaskPlus.lastSyncTime`、`RenameOrphanPlus.foundTime`、`TmdbCache.expireTime` 均用 `java.util.Date`）。已改为 `Date`，计划正文同步修正。
- **任务 7**：`PtDownloaderPlus.baseUrl()` 增加 host 清洗（去首尾空白、剥离大小写不敏感的 `http(s)://` 前缀、去末尾斜杠），清洗只在拼 URL 时发生、不回写字段。`validateSavePath` 增加 `InvalidPathException` 捕获：`savePath` 非法返回提示文案而非抛异常，单个任务 `monitorDir` 非法则跳过该任务继续遍历。均补了单元测试。
- **全局**：所有 `mvn -pl ruoyi-openliststrm -am test -Dtest=X` 命令必须追加 `-Dsurefire.failIfNoSpecifiedTests=false`，否则多模块 reactor 下上游模块会因无匹配测试类而报假失败。多个测试类用逗号分隔（`-Dtest=A,B`），`+` 号语法在 Maven 3.6.3 下静默失效。
