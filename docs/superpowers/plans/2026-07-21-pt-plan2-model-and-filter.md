# PT 订阅 计划2：订阅数据模型与过滤择优引擎 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 建立订阅的数据模型（4 张表），并实现过滤择优引擎——从一批候选种子中筛掉不合格的、再按可配置的维度顺序挑出最优的那一个。

**架构：** 新增 `pt/filter` 子包。过滤与排序分离：`TorrentFilterEngine.filter()` 做硬性淘汰，`TorrentFilterEngine.pickBest()` 按 `sort_priority` 配置的维度顺序把若干个 `Comparator<TorrentInfo>` 用 `thenComparing` 串起来取 Top 1。生效条件由「全局配置 + 订阅级 JSON 覆盖」合并成一个不可变的 `FilterCriteria`，引擎只认它，不直接读数据库——这样引擎是纯函数，可以密集单测。

**技术栈：** Java 25 (Spring Boot 4.0.6) / MyBatis-Plus / FastJSON2 / JUnit 5

**上游规格：** `docs/superpowers/specs/2026-07-21-pt-subscription-download-design.md`

**执行约定：** 直接在 `dev` 分支提交，不另开 feature 分支或 worktree。

**本计划不包含：** 订阅的增删改查服务与 REST 接口、TMDb 查总集数、任何前端、任何调度器。它们属于计划 3 与计划 4。本计划结束时，4 张表和过滤引擎就位且被测试覆盖，但还没有调用方——这是预期状态。

---

## 前置：计划 1 已就绪的成果

| 已有 | 位置 | 本计划怎么用 |
|---|---|---|
| `TorrentInfo` | `pt/model/TorrentInfo.java` | 过滤引擎的输入类型。字段：title / infoHash / downloadUrl / size(long) / seeders(int) / peers(int) / downloadVolumeFactor(double，默认 1.0) / pubDate / indexerId(Integer) / parsedTitle / parsedYear / parsedSeason(Integer) / parsedEpisode(Integer) / parsedResolution / parsedSource / parsedPubTime(Date)，方法 `isFree()` |
| 三张配置表与实体 | `pt_indexer` / `pt_downloader` / `pt_media_server` | 本计划的 `pt_download_record` 外键引用 indexer_id / downloader_id |
| 实体规范 | `mybatisplus/domain/PtDownloaderPlus.java` | 照搬：继承 `BaseEntity`、`@TableName` + `@TableField`、`@TableId(AUTO)`、业务 datetime 用 `java.util.Date` |
| 迁移规范 | `ruoyi-common/src/main/resources/sql/20260724-pt-base.sql` | 照搬幂等写法 |

**从计划 1 继承的两条硬约束：**

1. 所有 `mvn -pl ruoyi-openliststrm -am test -Dtest=X` 必须追加 `-Dsurefire.failIfNoSpecifiedTests=false`，否则多模块 reactor 下上游模块会因无匹配测试类而报假失败。多个测试类用**逗号**分隔（`-Dtest=A,B`），`+` 号语法在 Maven 3.6.3 下静默失效。
2. 业务 datetime 字段一律用 `java.util.Date`，不用 String（`BaseEntity` 的 createTime/updateTime 用 String 是框架自动填充的特例）。

---

## 文件结构

### 后端新增

| 文件 | 职责 |
|---|---|
| `ruoyi-common/src/main/resources/sql/20260725-pt-subscription.sql` | 4 张表 DDL + 过滤配置种子数据 |
| `pt/filter/FilterCriteria.java` | 生效过滤条件（全局配置 + 订阅覆盖合并后的不可变结果） |
| `pt/filter/SortDimension.java` | 排序维度枚举，每个枚举值自带一个 `Comparator<TorrentInfo>` |
| `pt/filter/FilterCriteriaFactory.java` | 把 `PtFilterConfigPlus` + 订阅的 `filterOverride` JSON 合并成 `FilterCriteria` |
| `pt/filter/TorrentFilterEngine.java` | `filter()` 硬性淘汰 + `pickBest()` 排序取优 |
| `mybatisplus/domain/PtFilterConfigPlus.java` 等 4 个 | 实体 |
| `mybatisplus/mapper/` 下 4 个 Mapper | 只继承 `BaseMapper` |
| `mybatisplus/service/` 下 4 个接口 + impl | Service |

### 后端修改

| 文件 | 修改内容 |
|---|---|
| `ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java` | 注册新迁移脚本 |

---

## 任务 1：数据库表与迁移脚本

**文件：**
- 创建：`ruoyi-common/src/main/resources/sql/20260725-pt-subscription.sql`
- 修改：`ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java`

> **背景（工程师必读）：** MyBatis-Plus 的 `SimpleDdl` 只有整个 SQL 文件**全部语句成功**才写入 `ddl_history` 标记已执行；任一语句报错则整个文件视为未完成、下次启动从头重跑。因此每条语句都必须幂等：`CREATE TABLE IF NOT EXISTS`、`INSERT IGNORE` + 显式主键值。
>
> **去重键的设计依据（实测结论，不要改回去）：** 真实环境（Prowlarr）返回的 Torznab 条目**不含 `infohash` 属性**，只有 `<guid>` / `<link>` / `<category>`。所以 `pt_download_record` 的唯一约束用 `(indexer_id, guid)` 而非 `torrent_hash`——后者会大面积为 NULL，而 MySQL 唯一索引允许多个 NULL，去重形同虚设。`<link>` 也不能用，Prowlarr 的下载链接内嵌 apikey，重新生成 key 后同一种子的 link 就变了。

- [ ] **步骤 1：编写迁移脚本**

创建 `ruoyi-common/src/main/resources/sql/20260725-pt-subscription.sql`：

```sql
-- ----------------------------
-- 20260725: PT 订阅业务表 —— 过滤配置 / 订阅 / 每集状态 / 下载记录
-- 每条语句均为幂等，原因见 20260720-rename-category-rule.sql 头部说明。
-- ----------------------------

CREATE TABLE IF NOT EXISTS `pt_filter_config` (
    `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键，本表恒定只有一行(id=1)',
    `min_seeders` int(10) NOT NULL DEFAULT 1 COMMENT '最低做种数，低于此值淘汰',
    `min_size` bigint(20) NOT NULL DEFAULT 0 COMMENT '最小体积(字节)，0表示不限',
    `max_size` bigint(20) NOT NULL DEFAULT 0 COMMENT '最大体积(字节)，0表示不限',
    `free_only` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '是否仅下载免费种 0-否 1-是',
    `include_keywords` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '逗号分隔，标题须命中其一，空表示不限',
    `exclude_keywords` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '逗号分隔，标题命中任一则淘汰',
    `resolution_priority` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '2160p,1080p,720p' COMMENT '分辨率优先级，逗号分隔，越靠前越优先',
    `sort_priority` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'RESOLUTION,FREE,SEEDERS,SIZE' COMMENT '排序维度顺序，逗号分隔',
    `preferred_size` bigint(20) NOT NULL DEFAULT 0 COMMENT '体积接近度的目标值(字节)，0表示该维度不参与比较',
    `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE
) COMMENT = 'PT 全局过滤与排序配置(单行)';

-- 种子数据：显式主键 + INSERT IGNORE 保证幂等，缺了这行引擎读不到配置
INSERT IGNORE INTO `pt_filter_config` (`id`, `min_seeders`, `min_size`, `max_size`, `free_only`, `include_keywords`, `exclude_keywords`, `resolution_priority`, `sort_priority`, `preferred_size`, `create_time`) VALUES
(1, 1, 0, 0, '0', NULL, '预告,花絮,samples', '2160p,1080p,720p', 'RESOLUTION,FREE,SEEDERS,SIZE', 0, '2026-07-25 00:00:00');

CREATE TABLE IF NOT EXISTS `pt_subscription` (
    `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `tmdb_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'TMDb ID',
    `media_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '媒体类型 TV/MOVIE',
    `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '作品标题',
    `year` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '年份',
    `season` int(10) NULL DEFAULT NULL COMMENT '季号，电影为NULL',
    `total_episodes` int(10) NOT NULL DEFAULT 1 COMMENT '总集数，电影恒为1',
    `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/COMPLETED/PAUSED',
    `filter_override` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订阅级过滤覆盖(JSON)，空表示全用全局配置',
    `downloader_id` int(10) NULL DEFAULT NULL COMMENT '指定下载器，空表示用唯一启用的那个',
    `poster_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'TMDb海报路径，列表展示用',
    `last_match_time` datetime(0) NULL DEFAULT NULL COMMENT '上次命中种子的时间，用于识别长期空转的订阅',
    `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_tmdb_season`(`tmdb_id`, `media_type`, `season`) USING BTREE,
    INDEX `idx_status`(`status`) USING BTREE
) COMMENT = 'PT 订阅';

CREATE TABLE IF NOT EXISTS `pt_subscription_episode` (
    `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `sub_id` int(10) UNSIGNED NOT NULL COMMENT '订阅ID',
    `episode` int(10) NOT NULL COMMENT '集号，电影恒为0',
    `state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'MISSING' COMMENT '状态 MISSING/IN_FLIGHT/IN_LIBRARY',
    `quality` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '已下载质量，为洗版预留',
    `download_id` int(10) UNSIGNED NULL DEFAULT NULL COMMENT '关联的下载记录ID',
    `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_sub_episode`(`sub_id`, `episode`) USING BTREE,
    INDEX `idx_sub_state`(`sub_id`, `state`) USING BTREE
) COMMENT = 'PT 订阅每集状态(缺集的唯一真相来源)';

CREATE TABLE IF NOT EXISTS `pt_download_record` (
    `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `sub_id` int(10) UNSIGNED NOT NULL COMMENT '订阅ID',
    `episode` int(10) NOT NULL COMMENT '集号，电影恒为0',
    `indexer_id` int(10) UNSIGNED NOT NULL COMMENT '来源索引器ID',
    `guid` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '索引器给出的条目唯一标识(RSS guid)',
    `guid_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'guid的SHA-256十六进制，用于唯一索引(guid本身过长无法直接建索引)',
    `torrent_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '种子hash，从下载器回填，仅供排查',
    `tracking_tag` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '推送时打的唯一标签 osr-pt-{id}，用于回映下载器中的种子',
    `title` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '原始种子标题',
    `size` bigint(20) NOT NULL DEFAULT 0 COMMENT '体积(字节)',
    `seeders` int(10) NOT NULL DEFAULT 0 COMMENT '做种数',
    `downloader_id` int(10) UNSIGNED NULL DEFAULT NULL COMMENT '推送到的下载器ID',
    `state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PUSHED' COMMENT '状态 PUSHED/DOWNLOADING/COMPLETED/FAILED',
    `fail_reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '失败原因',
    `pushed_time` datetime(0) NULL DEFAULT NULL COMMENT '推送时间',
    `completed_time` datetime(0) NULL DEFAULT NULL COMMENT '完成时间',
    `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_indexer_guid`(`indexer_id`, `guid_hash`) USING BTREE,
    INDEX `idx_sub_episode`(`sub_id`, `episode`) USING BTREE,
    INDEX `idx_state`(`state`) USING BTREE,
    INDEX `idx_tracking_tag`(`tracking_tag`) USING BTREE
) COMMENT = 'PT 下载记录';

-- ----------------------------
-- 菜单：挂在 OpenListStrm(2006) 下。图标类名必须存在于 useMenuIcon.ts 的映射表中，
-- 否则菜单图标不显示(历史bug，见 commit 0248e124)。本次复用已在表中的 'fa fa-bookmark-o'。
-- ----------------------------
INSERT IGNORE INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2064, 'PT订阅', 2006, 13, '/openlist/ptSubscription', '', 'C', '0', '1', 'openliststrm:ptSubscription:view', 'fa fa-bookmark-o', 'admin', '2026-07-25 00:00:00', '', NULL, 'PT 订阅管理'),
(2065, 'PT过滤规则', 2006, 14, '/openlist/ptFilterConfig', '', 'C', '0', '1', 'openliststrm:ptFilterConfig:view', 'fa fa-sliders', 'admin', '2026-07-25 00:00:00', '', NULL, 'PT 种子过滤与排序规则');
```

> **`guid_hash` 为什么存在：** MySQL 的 InnoDB 单个索引键最长 3072 字节（utf8mb4 下约 768 字符），而 `guid` 声明为 varchar(512) 已接近上限，且与 `indexer_id` 组成复合唯一索引会超限。所以存一列 `guid` 的 SHA-256 十六进制（固定 64 字符）专门用于唯一索引，`guid` 原文保留供排查。写入时两列必须同时赋值。

- [ ] **步骤 2：确认菜单图标类名已在前端映射表中**

运行：`grep -n "fa fa-bookmark-o\|fa fa-sliders" openlist-web/src/composables/useMenuIcon.ts`

预期：两个类名都能找到。若 `fa fa-sliders` 不在表中（计划 1 只补了 rss/download/server 三个），照计划 1 任务 1 的做法给 `useMenuIcon.ts` 补一条映射，并把改动一并提交。

- [ ] **步骤 3：注册迁移脚本**

修改 `ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java`，在 `getSqlFiles()` 返回列表**末尾**（`"sql/20260724-pt-base.sql"` 之后）追加一行：

```java
                "sql/20260724-pt-base.sql",
                "sql/20260725-pt-subscription.sql"
        );
```

- [ ] **步骤 4：编译验证**

运行：`mvn -pl ruoyi-common -am clean compile -DskipTests`

预期：BUILD SUCCESS

（注意用 `clean compile` 而非 `compile`：仅改资源文件时增量编译可能报 "Nothing to compile"，看不出真实结果。）

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-common/src/main/resources/sql/20260725-pt-subscription.sql ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java openlist-web/src/composables/useMenuIcon.ts
git commit -m "feat(pt): 新增订阅/每集状态/下载记录/过滤配置四张表及菜单"
```

---

## 任务 2：四个实体、Mapper、Service

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/PtFilterConfigPlus.java`
- 创建：`.../domain/PtSubscriptionPlus.java`
- 创建：`.../domain/PtSubscriptionEpisodePlus.java`
- 创建：`.../domain/PtDownloadRecordPlus.java`
- 创建：`.../mapper/` 下同名 4 个 Mapper
- 创建：`.../service/` 下 4 个接口 + `.../service/impl/` 下 4 个实现

> **约定：** 实体继承 `com.ruoyi.common.mybatisplus.BaseEntity`（已提供 createTime / updateTime，**不要重复声明**），`@TableName` + `@TableField` 显式映射，主键 `@TableId(value = "id", type = IdType.AUTO)`。Mapper 只继承 `BaseMapper`，**不写 XML Mapper**（项目 AGENTS.md 明令的反模式）。业务 datetime 字段用 `java.util.Date`。参照 `mybatisplus/domain/PtDownloaderPlus.java` 及其四件套。

- [ ] **步骤 1：编写 PtFilterConfigPlus 实体**

创建 `PtFilterConfigPlus.java`：

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
 * PT 全局过滤与排序配置（单行表，id 恒为 1）
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
@Getter
@Setter
@TableName("pt_filter_config")
public class PtFilterConfigPlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 本表恒定只有一行 */
    public static final int SINGLETON_ID = 1;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 最低做种数，低于此值淘汰 */
    @TableField("min_seeders")
    private Integer minSeeders;

    /** 最小体积(字节)，0 表示不限 */
    @TableField("min_size")
    private Long minSize;

    /** 最大体积(字节)，0 表示不限 */
    @TableField("max_size")
    private Long maxSize;

    /** 是否仅下载免费种 0-否 1-是 */
    @TableField("free_only")
    private String freeOnly;

    /** 逗号分隔，标题须命中其一，空表示不限 */
    @TableField("include_keywords")
    private String includeKeywords;

    /** 逗号分隔，标题命中任一则淘汰 */
    @TableField("exclude_keywords")
    private String excludeKeywords;

    /** 分辨率优先级，逗号分隔，越靠前越优先 */
    @TableField("resolution_priority")
    private String resolutionPriority;

    /** 排序维度顺序，逗号分隔，取值见 SortDimension 枚举 */
    @TableField("sort_priority")
    private String sortPriority;

    /** 体积接近度的目标值(字节)，0 表示该维度不参与比较 */
    @TableField("preferred_size")
    private Long preferredSize;
}
```

- [ ] **步骤 2：编写 PtSubscriptionPlus 实体**

创建 `PtSubscriptionPlus.java`：

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
 * PT 订阅
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
@Getter
@Setter
@TableName("pt_subscription")
public class PtSubscriptionPlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** TMDb ID */
    @TableField("tmdb_id")
    private String tmdbId;

    /** 媒体类型 TV/MOVIE */
    @TableField("media_type")
    private String mediaType;

    /** 作品标题 */
    @TableField("title")
    private String title;

    /** 年份 */
    @TableField("year")
    private String year;

    /** 季号，电影为 null */
    @TableField("season")
    private Integer season;

    /** 总集数，电影恒为 1 */
    @TableField("total_episodes")
    private Integer totalEpisodes;

    /** 状态 ACTIVE/COMPLETED/PAUSED */
    @TableField("status")
    private String status;

    /** 订阅级过滤覆盖(JSON)，空表示全用全局配置 */
    @TableField("filter_override")
    private String filterOverride;

    /** 指定下载器，空表示用唯一启用的那个 */
    @TableField("downloader_id")
    private Integer downloaderId;

    /** TMDb 海报路径，列表展示用 */
    @TableField("poster_path")
    private String posterPath;

    /** 上次命中种子的时间 */
    @TableField("last_match_time")
    private Date lastMatchTime;
}
```

- [ ] **步骤 3：编写 PtSubscriptionEpisodePlus 与 PtDownloadRecordPlus 实体**

创建 `PtSubscriptionEpisodePlus.java`：

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
 * PT 订阅每集状态。这张表是「缺集」的唯一真相来源：
 * Emby 查询结果与下载状态都往它上面收敛，前端进度展示直接查它。
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
@Getter
@Setter
@TableName("pt_subscription_episode")
public class PtSubscriptionEpisodePlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 订阅ID */
    @TableField("sub_id")
    private Integer subId;

    /** 集号，电影恒为 0 */
    @TableField("episode")
    private Integer episode;

    /** 状态 MISSING/IN_FLIGHT/IN_LIBRARY */
    @TableField("state")
    private String state;

    /** 已下载质量，为洗版预留 */
    @TableField("quality")
    private String quality;

    /** 关联的下载记录ID */
    @TableField("download_id")
    private Integer downloadId;
}
```

创建 `PtDownloadRecordPlus.java`：

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
 * PT 下载记录
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
@Getter
@Setter
@TableName("pt_download_record")
public class PtDownloadRecordPlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 订阅ID */
    @TableField("sub_id")
    private Integer subId;

    /** 集号，电影恒为 0 */
    @TableField("episode")
    private Integer episode;

    /** 来源索引器ID */
    @TableField("indexer_id")
    private Integer indexerId;

    /** 索引器给出的条目唯一标识(RSS guid) */
    @TableField("guid")
    private String guid;

    /** guid 的 SHA-256 十六进制，用于唯一索引 */
    @TableField("guid_hash")
    private String guidHash;

    /** 种子hash，从下载器回填，仅供排查 */
    @TableField("torrent_hash")
    private String torrentHash;

    /** 推送时打的唯一标签 osr-pt-{id} */
    @TableField("tracking_tag")
    private String trackingTag;

    /** 原始种子标题 */
    @TableField("title")
    private String title;

    /** 体积(字节) */
    @TableField("size")
    private Long size;

    /** 做种数 */
    @TableField("seeders")
    private Integer seeders;

    /** 推送到的下载器ID */
    @TableField("downloader_id")
    private Integer downloaderId;

    /** 状态 PUSHED/DOWNLOADING/COMPLETED/FAILED */
    @TableField("state")
    private String state;

    /** 失败原因 */
    @TableField("fail_reason")
    private String failReason;

    /** 推送时间 */
    @TableField("pushed_time")
    private Date pushedTime;

    /** 完成时间 */
    @TableField("completed_time")
    private Date completedTime;
}
```

- [ ] **步骤 4：编写 4 个 Mapper**

四个 Mapper 内容同构，均只继承 `BaseMapper`。以 `PtSubscriptionPlusMapper.java` 为例：

```java
package com.ruoyi.openliststrm.mybatisplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;

/**
 * <p>
 * PT 订阅 Mapper 接口
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
public interface PtSubscriptionPlusMapper extends BaseMapper<PtSubscriptionPlus> {

}
```

另外三个与上面**逐字同构**，只有三处不同——文件名、`BaseMapper` 的泛型参数、类注释里的中文名。照下表创建，其余内容（包名、import、`@author Jack`、`@since 2026-07-25`、空的接口体）完全一致：

| 文件名 | `extends BaseMapper<?>` 的泛型 | 类注释中文名 |
|---|---|---|
| `PtFilterConfigPlusMapper.java` | `PtFilterConfigPlus` | PT 全局过滤与排序配置 |
| `PtSubscriptionEpisodePlusMapper.java` | `PtSubscriptionEpisodePlus` | PT 订阅每集状态 |
| `PtDownloadRecordPlusMapper.java` | `PtDownloadRecordPlus` | PT 下载记录 |

每个文件同时要把 import 的实体类改成对应的那个（`import com.ruoyi.openliststrm.mybatisplus.domain.PtXxxPlus;`）。

- [ ] **步骤 5：编写 4 个 Service 接口与实现**

`IPtFilterConfigPlusService.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;

/**
 * <p>
 * PT 全局过滤与排序配置 服务类
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
public interface IPtFilterConfigPlusService extends IService<PtFilterConfigPlus> {

    /**
     * 取全局配置（单行表，id=1）。迁移脚本已插入种子数据，正常不会为 null；
     * 若确实缺失则返回一份内置默认值，保证过滤引擎永远拿得到配置。
     */
    PtFilterConfigPlus getConfig();
}
```

`PtFilterConfigPlusServiceImpl.java`：

```java
package com.ruoyi.openliststrm.mybatisplus.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import com.ruoyi.openliststrm.mybatisplus.mapper.PtFilterConfigPlusMapper;
import com.ruoyi.openliststrm.mybatisplus.service.IPtFilterConfigPlusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * PT 全局过滤与排序配置 服务实现类
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
@Slf4j
@Service
public class PtFilterConfigPlusServiceImpl extends ServiceImpl<PtFilterConfigPlusMapper, PtFilterConfigPlus> implements IPtFilterConfigPlusService {

    @Override
    public PtFilterConfigPlus getConfig() {
        PtFilterConfigPlus config = getById(PtFilterConfigPlus.SINGLETON_ID);
        if (config != null) {
            return config;
        }
        // 迁移脚本的种子数据被误删时的兜底，取值与 20260725-pt-subscription.sql 中的种子一致
        log.warn("pt_filter_config 缺少 id=1 的配置行，使用内置默认值");
        PtFilterConfigPlus fallback = new PtFilterConfigPlus();
        fallback.setId(PtFilterConfigPlus.SINGLETON_ID);
        fallback.setMinSeeders(1);
        fallback.setMinSize(0L);
        fallback.setMaxSize(0L);
        fallback.setFreeOnly("0");
        fallback.setExcludeKeywords("预告,花絮,samples");
        fallback.setResolutionPriority("2160p,1080p,720p");
        fallback.setSortPriority("RESOLUTION,FREE,SEEDERS,SIZE");
        fallback.setPreferredSize(0L);
        return fallback;
    }
}
```

`IPtSubscriptionPlusService.java` 与实现：

```java
package com.ruoyi.openliststrm.mybatisplus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;

import java.util.List;

/**
 * <p>
 * PT 订阅 服务类
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
public interface IPtSubscriptionPlusService extends IService<PtSubscriptionPlus> {

    /**
     * 查询全部处于订阅中(ACTIVE)的订阅。RSS 轮询只匹配这些。
     */
    List<PtSubscriptionPlus> listActive();
}
```

```java
package com.ruoyi.openliststrm.mybatisplus.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionPlus;
import com.ruoyi.openliststrm.mybatisplus.mapper.PtSubscriptionPlusMapper;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionPlusService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * PT 订阅 服务实现类
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
@Service
public class PtSubscriptionPlusServiceImpl extends ServiceImpl<PtSubscriptionPlusMapper, PtSubscriptionPlus> implements IPtSubscriptionPlusService {

    @Override
    public List<PtSubscriptionPlus> listActive() {
        return lambdaQuery()
                .eq(PtSubscriptionPlus::getStatus, "ACTIVE")
                .orderByAsc(PtSubscriptionPlus::getId)
                .list();
    }
}
```

`IPtSubscriptionEpisodePlusService.java` 与实现：

```java
package com.ruoyi.openliststrm.mybatisplus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;

import java.util.List;

/**
 * <p>
 * PT 订阅每集状态 服务类
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
public interface IPtSubscriptionEpisodePlusService extends IService<PtSubscriptionEpisodePlus> {

    /**
     * 按订阅查全部集，按集号升序。
     */
    List<PtSubscriptionEpisodePlus> listBySubscription(Integer subId);
}
```

```java
package com.ruoyi.openliststrm.mybatisplus.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.openliststrm.mybatisplus.domain.PtSubscriptionEpisodePlus;
import com.ruoyi.openliststrm.mybatisplus.mapper.PtSubscriptionEpisodePlusMapper;
import com.ruoyi.openliststrm.mybatisplus.service.IPtSubscriptionEpisodePlusService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * PT 订阅每集状态 服务实现类
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
@Service
public class PtSubscriptionEpisodePlusServiceImpl extends ServiceImpl<PtSubscriptionEpisodePlusMapper, PtSubscriptionEpisodePlus> implements IPtSubscriptionEpisodePlusService {

    @Override
    public List<PtSubscriptionEpisodePlus> listBySubscription(Integer subId) {
        return lambdaQuery()
                .eq(PtSubscriptionEpisodePlus::getSubId, subId)
                .orderByAsc(PtSubscriptionEpisodePlus::getEpisode)
                .list();
    }
}
```

`IPtDownloadRecordPlusService.java` 与实现（本计划只需最基础的 CRUD，追踪相关的查询留到计划 4）：

```java
package com.ruoyi.openliststrm.mybatisplus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;

/**
 * <p>
 * PT 下载记录 服务类
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
public interface IPtDownloadRecordPlusService extends IService<PtDownloadRecordPlus> {

}
```

```java
package com.ruoyi.openliststrm.mybatisplus.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloadRecordPlus;
import com.ruoyi.openliststrm.mybatisplus.mapper.PtDownloadRecordPlusMapper;
import com.ruoyi.openliststrm.mybatisplus.service.IPtDownloadRecordPlusService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * PT 下载记录 服务实现类
 * </p>
 *
 * @author Jack
 * @since 2026-07-25
 */
@Service
public class PtDownloadRecordPlusServiceImpl extends ServiceImpl<PtDownloadRecordPlusMapper, PtDownloadRecordPlus> implements IPtDownloadRecordPlusService {

}
```

- [ ] **步骤 6：编译验证**

运行：`mvn -pl ruoyi-openliststrm -am clean compile -DskipTests`

预期：BUILD SUCCESS

- [ ] **步骤 7：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/
git commit -m "feat(pt): 新增订阅相关四张表的实体、Mapper 与 Service"
```

---
## 任务 3：FilterCriteria 生效过滤条件

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/FilterCriteria.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/FilterCriteriaTest.java`

> **设计说明：** 这是「全局配置 + 订阅级覆盖」合并后的最终生效条件，用 record 保证不可变。过滤引擎只认它，不直接读数据库——引擎因此是纯函数，可以密集单测而不需要任何 Spring 上下文或 mock。
>
> 数据库里这些字段大多是逗号分隔的字符串（`resolution_priority` = `"2160p,1080p,720p"`），到了这一层统一变成 `List<String>`，避免每个使用点重复 split。解析逻辑放在本类的静态工具方法里，任务 5 的工厂会复用。
>
> **`0 表示不限` 的约定：** `minSize` / `maxSize` / `preferredSize` 为 0 时表示该维度不生效。这与数据库默认值一致，好处是不用引入包装类型的 null 判断。

- [ ] **步骤 1：编写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/FilterCriteriaTest.java`：

```java
package com.ruoyi.openliststrm.pt.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterCriteriaTest {

    @Test
    void splitCsv_正常逗号分隔_逐项去空白() {
        assertEquals(List.of("2160p", "1080p", "720p"), FilterCriteria.splitCsv("2160p, 1080p ,720p"));
    }

    @Test
    void splitCsv_空值_返回空列表() {
        assertTrue(FilterCriteria.splitCsv(null).isEmpty());
        assertTrue(FilterCriteria.splitCsv("").isEmpty());
        assertTrue(FilterCriteria.splitCsv("   ").isEmpty());
    }

    @Test
    void splitCsv_含空项_空项被丢弃() {
        assertEquals(List.of("a", "b"), FilterCriteria.splitCsv("a,,b,"));
    }

    @Test
    void splitCsv_中文关键词_正常切分() {
        assertEquals(List.of("预告", "花絮"), FilterCriteria.splitCsv("预告,花絮"));
    }

    @Test
    void 列表字段被防御性拷贝_外部修改不影响已构造的条件() {
        List<String> mutable = new java.util.ArrayList<>(List.of("1080p"));
        FilterCriteria criteria = new FilterCriteria(
                1, 0L, 0L, false, List.of(), List.of(), mutable, List.of(SortDimension.SEEDERS), 0L);

        mutable.add("720p");

        assertEquals(List.of("1080p"), criteria.resolutionPriority());
    }

    @Test
    void 列表字段不可变_尝试修改抛异常() {
        FilterCriteria criteria = new FilterCriteria(
                1, 0L, 0L, false, List.of(), List.of(), List.of("1080p"), List.of(SortDimension.SEEDERS), 0L);

        assertThrows(UnsupportedOperationException.class, () -> criteria.resolutionPriority().add("720p"));
    }

    @Test
    void 排序维度为空_回退到内置默认顺序() {
        FilterCriteria criteria = new FilterCriteria(
                1, 0L, 0L, false, List.of(), List.of(), List.of(), List.of(), 0L);

        // 空的排序配置会让择优退化成"随便挑一个"，必须有兜底
        assertEquals(FilterCriteria.DEFAULT_SORT_PRIORITY, criteria.sortPriority());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=FilterCriteriaTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `FilterCriteria` 与 `SortDimension` 找不到符号

（`SortDimension` 在任务 4 创建。为让本任务可独立编译，本步骤先创建一个只含枚举常量的最小版本，任务 4 再给它加 comparator。见步骤 3。）

- [ ] **步骤 3：编写实现**

先创建最小版 `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/SortDimension.java`（任务 4 会补齐 comparator）：

```java
package com.ruoyi.openliststrm.pt.filter;

/**
 * 择优时的排序维度。取值写入 pt_filter_config.sort_priority，逗号分隔。
 *
 * @author Jack
 */
public enum SortDimension {

    /** 分辨率匹配度，按 resolutionPriority 的先后顺序 */
    RESOLUTION,

    /** 是否免费种，免费优先 */
    FREE,

    /** 做种数，多者优先 */
    SEEDERS,

    /** 体积接近偏好值的程度，越接近越优先 */
    SIZE
}
```

创建 `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/FilterCriteria.java`：

```java
package com.ruoyi.openliststrm.pt.filter;

import com.ruoyi.common.utils.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 生效的过滤与排序条件——全局配置与订阅级覆盖合并后的最终结果。
 * <p>
 * 不可变。过滤引擎只认本类型，不直接读数据库，因此引擎是纯函数、可密集单测。
 * </p>
 *
 * @param minSeeders        最低做种数，低于此值淘汰
 * @param minSize           最小体积(字节)，0 表示不限
 * @param maxSize           最大体积(字节)，0 表示不限
 * @param freeOnly          是否仅要免费种
 * @param includeKeywords   标题须命中其一，空列表表示不限
 * @param excludeKeywords   标题命中任一则淘汰
 * @param resolutionPriority 分辨率优先级，越靠前越优先
 * @param sortPriority      排序维度顺序；传空列表时回退到 {@link #DEFAULT_SORT_PRIORITY}
 * @param preferredSize     体积接近度的目标值(字节)，0 表示该维度不参与比较
 * @author Jack
 */
public record FilterCriteria(
        int minSeeders,
        long minSize,
        long maxSize,
        boolean freeOnly,
        List<String> includeKeywords,
        List<String> excludeKeywords,
        List<String> resolutionPriority,
        List<SortDimension> sortPriority,
        long preferredSize) {

    /** 未配置排序维度时的兜底顺序，与建表脚本的默认值一致 */
    public static final List<SortDimension> DEFAULT_SORT_PRIORITY =
            List.of(SortDimension.RESOLUTION, SortDimension.FREE, SortDimension.SEEDERS, SortDimension.SIZE);

    public FilterCriteria {
        // 防御性拷贝 + 不可变化：调用方之后修改传入的列表不应影响已构造的条件
        includeKeywords = List.copyOf(includeKeywords);
        excludeKeywords = List.copyOf(excludeKeywords);
        resolutionPriority = List.copyOf(resolutionPriority);
        // 空的排序配置会让择优退化成"随便挑一个"，必须有兜底
        sortPriority = sortPriority.isEmpty() ? DEFAULT_SORT_PRIORITY : List.copyOf(sortPriority);
    }

    /**
     * 把逗号分隔的配置串切成列表，逐项去空白、丢弃空项。
     * 输入为 null 或空白时返回空列表。
     */
    public static List<String> splitCsv(String csv) {
        if (StringUtils.isBlank(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=FilterCriteriaTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 7, Failures: 0, Errors: 0

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/ ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/
git commit -m "feat(pt): 新增 FilterCriteria 生效过滤条件与 SortDimension 枚举"
```

---

## 任务 4：SortDimension 的比较器

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/SortDimension.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/SortDimensionTest.java`

> **设计要害（决定「下得准不准」）：** 每个维度自带一个 `Comparator<TorrentInfo>`，语义统一为**「更优的排在前面」**，即 `compare(a, b) < 0` 表示 a 比 b 更值得下载。任务 6 会按 `sortPriority` 的顺序用 `thenComparing` 把它们串起来，所以每个比较器只管自己这一个维度，不许夹带其他判断。
>
> 各维度的「更优」定义：
> - **RESOLUTION**：在 `resolutionPriority` 列表中的下标越小越优；不在列表中（含解析不出分辨率）的一律排到最后。**大小写不敏感**——索引器给的标题里 `1080P` 和 `1080p` 都有。
> - **FREE**：免费种优先。
> - **SEEDERS**：做种数多者优先（降序）。
> - **SIZE**：与 `preferredSize` 的差的绝对值越小越优。`preferredSize` 为 0 时该维度**完全不参与比较**（返回恒 0 的比较器），而不是退化成「体积越小越好」——后者会让用户在没配置偏好体积时莫名其妙地总下到最小的那个。
>
> 新增维度时只需加一个枚举值 + 一个 comparator，串联逻辑不变。

- [ ] **步骤 1：编写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/SortDimensionTest.java`：

```java
package com.ruoyi.openliststrm.pt.filter;

import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SortDimensionTest {

    private FilterCriteria criteria(List<String> resolutions, long preferredSize) {
        return new FilterCriteria(0, 0L, 0L, false, List.of(), List.of(),
                resolutions, List.of(SortDimension.SEEDERS), preferredSize);
    }

    private TorrentInfo torrent(String resolution, boolean free, int seeders, long size) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle("t-" + resolution + "-" + seeders + "-" + size);
        t.setParsedResolution(resolution);
        t.setDownloadVolumeFactor(free ? 0.0 : 1.0);
        t.setSeeders(seeders);
        t.setSize(size);
        return t;
    }

    // ---------- RESOLUTION ----------

    @Test
    void resolution_按优先级列表排序_靠前的更优() {
        Comparator<TorrentInfo> c = SortDimension.RESOLUTION.comparator(
                criteria(List.of("2160p", "1080p", "720p"), 0L));

        assertTrue(c.compare(torrent("2160p", false, 0, 0), torrent("1080p", false, 0, 0)) < 0);
        assertTrue(c.compare(torrent("720p", false, 0, 0), torrent("1080p", false, 0, 0)) > 0);
        assertEquals(0, c.compare(torrent("1080p", false, 0, 0), torrent("1080p", false, 0, 0)));
    }

    @Test
    void resolution_大小写不敏感() {
        Comparator<TorrentInfo> c = SortDimension.RESOLUTION.comparator(
                criteria(List.of("2160p", "1080p"), 0L));

        // 索引器给的标题里 1080P 与 1080p 都出现过
        assertEquals(0, c.compare(torrent("1080P", false, 0, 0), torrent("1080p", false, 0, 0)));
        assertTrue(c.compare(torrent("2160P", false, 0, 0), torrent("1080p", false, 0, 0)) < 0);
    }

    @Test
    void resolution_不在优先级列表中的排到最后() {
        Comparator<TorrentInfo> c = SortDimension.RESOLUTION.comparator(
                criteria(List.of("2160p", "1080p"), 0L));

        assertTrue(c.compare(torrent("480p", false, 0, 0), torrent("1080p", false, 0, 0)) > 0);
        assertTrue(c.compare(torrent(null, false, 0, 0), torrent("1080p", false, 0, 0)) > 0);
        // 两个都不在列表中时视为同级
        assertEquals(0, c.compare(torrent("480p", false, 0, 0), torrent(null, false, 0, 0)));
    }

    @Test
    void resolution_优先级列表为空_全部同级() {
        Comparator<TorrentInfo> c = SortDimension.RESOLUTION.comparator(criteria(List.of(), 0L));

        assertEquals(0, c.compare(torrent("2160p", false, 0, 0), torrent("480p", false, 0, 0)));
    }

    // ---------- FREE ----------

    @Test
    void free_免费种更优() {
        Comparator<TorrentInfo> c = SortDimension.FREE.comparator(criteria(List.of(), 0L));

        assertTrue(c.compare(torrent("1080p", true, 0, 0), torrent("1080p", false, 0, 0)) < 0);
        assertTrue(c.compare(torrent("1080p", false, 0, 0), torrent("1080p", true, 0, 0)) > 0);
        assertEquals(0, c.compare(torrent("1080p", true, 0, 0), torrent("1080p", true, 0, 0)));
    }

    // ---------- SEEDERS ----------

    @Test
    void seeders_做种多者更优() {
        Comparator<TorrentInfo> c = SortDimension.SEEDERS.comparator(criteria(List.of(), 0L));

        assertTrue(c.compare(torrent("1080p", false, 50, 0), torrent("1080p", false, 3, 0)) < 0);
        assertTrue(c.compare(torrent("1080p", false, 0, 0), torrent("1080p", false, 1, 0)) > 0);
        assertEquals(0, c.compare(torrent("1080p", false, 7, 0), torrent("1080p", false, 7, 0)));
    }

    // ---------- SIZE ----------

    @Test
    void size_越接近偏好体积越优() {
        long preferred = 5_000_000_000L;
        Comparator<TorrentInfo> c = SortDimension.SIZE.comparator(criteria(List.of(), preferred));

        TorrentInfo close = torrent("1080p", false, 0, 5_100_000_000L);
        TorrentInfo far = torrent("1080p", false, 0, 60_000_000_000L);

        assertTrue(c.compare(close, far) < 0);
    }

    @Test
    void size_偏好体积两侧等距_视为同级() {
        long preferred = 5_000_000_000L;
        Comparator<TorrentInfo> c = SortDimension.SIZE.comparator(criteria(List.of(), preferred));

        assertEquals(0, c.compare(
                torrent("1080p", false, 0, 4_000_000_000L),
                torrent("1080p", false, 0, 6_000_000_000L)));
    }

    @Test
    void size_未配置偏好体积_该维度完全不参与比较() {
        Comparator<TorrentInfo> c = SortDimension.SIZE.comparator(criteria(List.of(), 0L));

        // 不能退化成"越小越好"，否则用户没配偏好时会莫名其妙总下到最小的那个
        assertEquals(0, c.compare(
                torrent("1080p", false, 0, 1_000L),
                torrent("1080p", false, 0, 90_000_000_000L)));
    }

    // ---------- 枚举解析 ----------

    @Test
    void parse_识别有效维度名_大小写不敏感() {
        assertEquals(List.of(SortDimension.FREE, SortDimension.SEEDERS),
                SortDimension.parseCsv("free, SEEDERS"));
    }

    @Test
    void parse_忽略无法识别的维度名_不抛异常() {
        // 配置是用户手输的，写错一个词不该让整个轮询挂掉
        assertEquals(List.of(SortDimension.SEEDERS), SortDimension.parseCsv("SEEDERS,不存在的维度"));
    }

    @Test
    void parse_空值_返回空列表() {
        assertTrue(SortDimension.parseCsv(null).isEmpty());
        assertTrue(SortDimension.parseCsv("").isEmpty());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SortDimensionTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `comparator` 与 `parseCsv` 方法找不到

- [ ] **步骤 3：编写实现**

用下面内容**整体替换** `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/SortDimension.java`：

```java
package com.ruoyi.openliststrm.pt.filter;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 择优时的排序维度。取值写入 pt_filter_config.sort_priority，逗号分隔。
 * <p>
 * 每个维度自带一个比较器，语义统一为「更优的排在前面」：
 * {@code compare(a, b) < 0} 表示 a 比 b 更值得下载。
 * 择优时按配置的维度顺序用 thenComparing 串联，因此每个比较器只管自己这一维，
 * 不得夹带其他判断。新增维度只需加一个枚举值与一个比较器，串联逻辑不变。
 * </p>
 *
 * @author Jack
 */
@Slf4j
public enum SortDimension {

    /** 分辨率匹配度，按 resolutionPriority 的先后顺序，不在列表中的排最后 */
    RESOLUTION {
        @Override
        public Comparator<TorrentInfo> comparator(FilterCriteria criteria) {
            List<String> priority = criteria.resolutionPriority();
            if (priority.isEmpty()) {
                return NO_PREFERENCE;
            }
            return Comparator.comparingInt(t -> rankOf(t.getParsedResolution(), priority));
        }
    },

    /** 是否免费种，免费优先 */
    FREE {
        @Override
        public Comparator<TorrentInfo> comparator(FilterCriteria criteria) {
            return Comparator.comparingInt(t -> t.isFree() ? 0 : 1);
        }
    },

    /** 做种数，多者优先 */
    SEEDERS {
        @Override
        public Comparator<TorrentInfo> comparator(FilterCriteria criteria) {
            return Comparator.comparingInt(TorrentInfo::getSeeders).reversed();
        }
    },

    /** 体积接近偏好值的程度，越接近越优先；未配置偏好值时不参与比较 */
    SIZE {
        @Override
        public Comparator<TorrentInfo> comparator(FilterCriteria criteria) {
            long preferred = criteria.preferredSize();
            if (preferred <= 0) {
                // 不能退化成「越小越好」：用户没配偏好体积时那样会总是下到最小的那个
                return NO_PREFERENCE;
            }
            return Comparator.comparingLong(t -> Math.abs(t.getSize() - preferred));
        }
    };

    /** 恒判同级的比较器，用于「该维度未配置」的情形 */
    private static final Comparator<TorrentInfo> NO_PREFERENCE = (a, b) -> 0;

    /**
     * 该维度的比较器，更优的排在前面。
     */
    public abstract Comparator<TorrentInfo> comparator(FilterCriteria criteria);

    /**
     * 解析逗号分隔的维度名，大小写不敏感。
     * <p>
     * 无法识别的名字只记日志跳过，不抛异常——这份配置是用户手输的，
     * 写错一个词不该让整轮 RSS 轮询挂掉。
     * </p>
     */
    public static List<SortDimension> parseCsv(String csv) {
        List<SortDimension> result = new ArrayList<>();
        for (String name : FilterCriteria.splitCsv(csv)) {
            try {
                result.add(SortDimension.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                log.warn("排序维度配置中存在无法识别的取值，已忽略：{}", name);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 分辨率在优先级列表中的名次，越小越优；不在列表中返回列表长度（排最后）。
     * 大小写不敏感——索引器标题里 1080P 与 1080p 都出现过。
     */
    private static int rankOf(String resolution, List<String> priority) {
        if (StringUtils.isBlank(resolution)) {
            return priority.size();
        }
        for (int i = 0; i < priority.size(); i++) {
            if (priority.get(i).equalsIgnoreCase(resolution.trim())) {
                return i;
            }
        }
        return priority.size();
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=SortDimensionTest,FilterCriteriaTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：两个测试类全绿，SortDimensionTest 12 个用例、FilterCriteriaTest 7 个用例

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/SortDimension.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/SortDimensionTest.java
git commit -m "feat(pt): SortDimension 各维度比较器与配置解析"
```

---
## 任务 5：FilterCriteriaFactory 条件合并

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/FilterCriteriaFactory.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/FilterCriteriaFactoryTest.java`

> **设计说明：** 把「全局配置」和「订阅级覆盖」合并成一份 `FilterCriteria`。覆盖用 JSON 存在 `pt_subscription.filter_override`，**只有出现在 JSON 里的键才覆盖**，没出现的沿用全局值。典型场景是「这部剧我要 4K，其余都保持默认」，那么 JSON 就是 `{"resolutionPriority":"2160p"}`。
>
> JSON 的键名与 `PtFilterConfigPlus` 的字段名一致，值的形态也与数据库一致（逗号分隔串、`"0"`/`"1"`、数字），这样前端表单可以直接复用同一套控件。
>
> **容错要求：** `filter_override` 是用户通过表单写入的，格式可能损坏。JSON 解析失败时**记警告并整体退回全局配置**，不能抛异常——一条坏配置不该让整轮 RSS 轮询挂掉。

- [ ] **步骤 1：编写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/FilterCriteriaFactoryTest.java`：

```java
package com.ruoyi.openliststrm.pt.filter;

import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterCriteriaFactoryTest {

    private PtFilterConfigPlus globalConfig() {
        PtFilterConfigPlus c = new PtFilterConfigPlus();
        c.setMinSeeders(3);
        c.setMinSize(1_000L);
        c.setMaxSize(90_000_000_000L);
        c.setFreeOnly("0");
        c.setIncludeKeywords(null);
        c.setExcludeKeywords("预告,花絮");
        c.setResolutionPriority("2160p,1080p,720p");
        c.setSortPriority("RESOLUTION,FREE,SEEDERS,SIZE");
        c.setPreferredSize(0L);
        return c;
    }

    @Test
    void 无覆盖_全部沿用全局配置() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), null);

        assertEquals(3, c.minSeeders());
        assertEquals(1_000L, c.minSize());
        assertEquals(90_000_000_000L, c.maxSize());
        assertFalse(c.freeOnly());
        assertEquals(List.of("预告", "花絮"), c.excludeKeywords());
        assertEquals(List.of("2160p", "1080p", "720p"), c.resolutionPriority());
        assertEquals(FilterCriteria.DEFAULT_SORT_PRIORITY, c.sortPriority());
    }

    @Test
    void 空字符串覆盖_等同于无覆盖() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "   ");

        assertEquals(3, c.minSeeders());
        assertEquals(List.of("2160p", "1080p", "720p"), c.resolutionPriority());
    }

    @Test
    void 部分覆盖_只有出现的键被替换_其余沿用全局() {
        // 典型场景：这部剧我要 4K，其余保持默认
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "{\"resolutionPriority\":\"2160p\"}");

        assertEquals(List.of("2160p"), c.resolutionPriority());
        assertEquals(3, c.minSeeders(), "未出现在覆盖中的键必须沿用全局值");
        assertEquals(List.of("预告", "花絮"), c.excludeKeywords());
    }

    @Test
    void 覆盖数值型字段() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(),
                "{\"minSeeders\":10,\"minSize\":2000,\"maxSize\":3000,\"preferredSize\":2500}");

        assertEquals(10, c.minSeeders());
        assertEquals(2_000L, c.minSize());
        assertEquals(3_000L, c.maxSize());
        assertEquals(2_500L, c.preferredSize());
    }

    @Test
    void 覆盖仅免费开关() {
        assertTrue(FilterCriteriaFactory.build(globalConfig(), "{\"freeOnly\":\"1\"}").freeOnly());
        assertFalse(FilterCriteriaFactory.build(globalConfig(), "{\"freeOnly\":\"0\"}").freeOnly());
    }

    @Test
    void 覆盖排序维度顺序() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "{\"sortPriority\":\"FREE,SEEDERS\"}");

        assertEquals(List.of(SortDimension.FREE, SortDimension.SEEDERS), c.sortPriority());
    }

    @Test
    void 覆盖关键词_可以把全局排除词清空() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "{\"excludeKeywords\":\"\"}");

        // 显式传空串意味着「这部剧不排除任何关键词」，不能被当成"没覆盖"
        assertTrue(c.excludeKeywords().isEmpty());
    }

    @Test
    void 覆盖JSON非法_记警告并整体退回全局配置() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "{这不是合法JSON");

        assertEquals(3, c.minSeeders());
        assertEquals(List.of("2160p", "1080p", "720p"), c.resolutionPriority());
    }

    @Test
    void 覆盖JSON是数组而非对象_退回全局配置() {
        FilterCriteria c = FilterCriteriaFactory.build(globalConfig(), "[1,2,3]");

        assertEquals(3, c.minSeeders());
    }

    @Test
    void 全局配置字段为null_使用安全默认值而非NPE() {
        PtFilterConfigPlus empty = new PtFilterConfigPlus();

        FilterCriteria c = FilterCriteriaFactory.build(empty, null);

        assertEquals(0, c.minSeeders());
        assertEquals(0L, c.minSize());
        assertEquals(0L, c.maxSize());
        assertFalse(c.freeOnly());
        assertTrue(c.includeKeywords().isEmpty());
        assertTrue(c.excludeKeywords().isEmpty());
        assertTrue(c.resolutionPriority().isEmpty());
        assertEquals(FilterCriteria.DEFAULT_SORT_PRIORITY, c.sortPriority());
        assertEquals(0L, c.preferredSize());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=FilterCriteriaFactoryTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `FilterCriteriaFactory` 找不到符号

- [ ] **步骤 3：编写实现**

创建 `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/FilterCriteriaFactory.java`：

```java
package com.ruoyi.openliststrm.pt.filter;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtFilterConfigPlus;
import lombok.extern.slf4j.Slf4j;

/**
 * 把全局过滤配置与订阅级覆盖合并成一份生效的 {@link FilterCriteria}。
 * <p>
 * 覆盖以 JSON 存在 pt_subscription.filter_override，键名与 {@link PtFilterConfigPlus}
 * 的字段名一致，值的形态也与数据库一致（逗号分隔串、"0"/"1"、数字）。
 * <b>只有出现在 JSON 里的键才覆盖</b>，没出现的沿用全局值。
 * </p>
 *
 * @author Jack
 */
@Slf4j
public final class FilterCriteriaFactory {

    private FilterCriteriaFactory() {
    }

    /**
     * @param global   全局配置，字段允许为 null（使用安全默认值）
     * @param override 订阅级覆盖 JSON，允许为 null / 空白 / 格式损坏
     */
    public static FilterCriteria build(PtFilterConfigPlus global, String override) {
        JSONObject patch = parseOverride(override);

        return new FilterCriteria(
                intOf(patch, "minSeeders", global.getMinSeeders()),
                longOf(patch, "minSize", global.getMinSize()),
                longOf(patch, "maxSize", global.getMaxSize()),
                "1".equals(strOf(patch, "freeOnly", global.getFreeOnly())),
                FilterCriteria.splitCsv(strOf(patch, "includeKeywords", global.getIncludeKeywords())),
                FilterCriteria.splitCsv(strOf(patch, "excludeKeywords", global.getExcludeKeywords())),
                FilterCriteria.splitCsv(strOf(patch, "resolutionPriority", global.getResolutionPriority())),
                SortDimension.parseCsv(strOf(patch, "sortPriority", global.getSortPriority())),
                longOf(patch, "preferredSize", global.getPreferredSize()));
    }

    /**
     * 解析覆盖 JSON。为 null/空白、格式非法、或不是 JSON 对象时一律返回空 patch，
     * 使全部字段退回全局配置——这份配置是用户手填的，一条坏数据不该让整轮轮询挂掉。
     */
    private static JSONObject parseOverride(String override) {
        if (StringUtils.isBlank(override)) {
            return new JSONObject();
        }
        try {
            JSONObject parsed = JSONObject.parseObject(override);
            return parsed == null ? new JSONObject() : parsed;
        } catch (Exception e) {
            log.warn("订阅级过滤覆盖不是合法的 JSON 对象，已整体退回全局配置：{}", e.getMessage());
            return new JSONObject();
        }
    }

    private static String strOf(JSONObject patch, String key, String fallback) {
        // 注意用 containsKey 而非判空：显式传 "" 意味着「这部剧不设该项」，是有效覆盖
        return patch.containsKey(key) ? patch.getString(key) : fallback;
    }

    private static int intOf(JSONObject patch, String key, Integer fallback) {
        if (patch.containsKey(key)) {
            Integer value = patch.getInteger(key);
            if (value != null) {
                return value;
            }
        }
        return fallback == null ? 0 : fallback;
    }

    private static long longOf(JSONObject patch, String key, Long fallback) {
        if (patch.containsKey(key)) {
            Long value = patch.getLong(key);
            if (value != null) {
                return value;
            }
        }
        return fallback == null ? 0L : fallback;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=FilterCriteriaFactoryTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 10, Failures: 0, Errors: 0

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/FilterCriteriaFactory.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/FilterCriteriaFactoryTest.java
git commit -m "feat(pt): 新增过滤条件合并工厂，支持订阅级覆盖全局配置"
```

---

## 任务 6：TorrentFilterEngine 硬性过滤

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/TorrentFilterEngine.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/TorrentFilterEngineFilterTest.java`

> **规则（全部为「不满足即淘汰」）：**
> 1. 做种数 < `minSeeders`
> 2. `minSize > 0` 且体积 < `minSize`
> 3. `maxSize > 0` 且体积 > `maxSize`
> 4. `freeOnly` 为真且该种非免费
> 5. 标题命中任一 `excludeKeywords`
> 6. `includeKeywords` 非空且标题一个都没命中
>
> 关键词匹配**大小写不敏感**且是子串包含（不是正则）——用户填的是「预告」「samples」这类词，不该要求他们懂正则。
>
> **被淘汰的种子不落库，只记 debug 日志并带上原因。** 这些日志是后续调优过滤规则的主要素材，所以原因必须具体（哪条规则、阈值多少、实际值多少），不能只写「不符合条件」。

- [ ] **步骤 1：编写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/TorrentFilterEngineFilterTest.java`：

```java
package com.ruoyi.openliststrm.pt.filter;

import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorrentFilterEngineFilterTest {

    private final TorrentFilterEngine engine = new TorrentFilterEngine();

    private FilterCriteria criteria(int minSeeders, long minSize, long maxSize, boolean freeOnly,
                                    List<String> include, List<String> exclude) {
        return new FilterCriteria(minSeeders, minSize, maxSize, freeOnly, include, exclude,
                List.of("1080p"), List.of(SortDimension.SEEDERS), 0L);
    }

    private TorrentInfo torrent(String title, int seeders, long size, boolean free) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle(title);
        t.setSeeders(seeders);
        t.setSize(size);
        t.setDownloadVolumeFactor(free ? 0.0 : 1.0);
        return t;
    }

    private TorrentInfo ok() {
        return torrent("Some.Show.S01E05.1080p.WEB-DL", 10, 5_000_000_000L, false);
    }

    @Test
    void 全部条件满足_保留() {
        List<TorrentInfo> result = engine.filter(List.of(ok()),
                criteria(1, 0L, 0L, false, List.of(), List.of()));

        assertEquals(1, result.size());
    }

    @Test
    void 做种数低于下限_淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("t", 2, 5_000_000_000L, false)),
                criteria(3, 0L, 0L, false, List.of(), List.of()));

        assertTrue(result.isEmpty());
    }

    @Test
    void 做种数等于下限_保留() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("t", 3, 5_000_000_000L, false)),
                criteria(3, 0L, 0L, false, List.of(), List.of()));

        assertEquals(1, result.size());
    }

    @Test
    void 体积小于下限_淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("t", 10, 500L, false)),
                criteria(0, 1_000L, 0L, false, List.of(), List.of()));

        assertTrue(result.isEmpty());
    }

    @Test
    void 体积大于上限_淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("t", 10, 90_000_000_000L, false)),
                criteria(0, 0L, 50_000_000_000L, false, List.of(), List.of()));

        assertTrue(result.isEmpty());
    }

    @Test
    void 体积上下限为0_表示不限() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("t", 10, 1L, false), torrent("t2", 10, 999_999_999_999L, false)),
                criteria(0, 0L, 0L, false, List.of(), List.of()));

        assertEquals(2, result.size());
    }

    @Test
    void 仅要免费_非免费种被淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("free", 10, 100L, true), torrent("paid", 10, 100L, false)),
                criteria(0, 0L, 0L, true, List.of(), List.of()));

        assertEquals(1, result.size());
        assertEquals("free", result.get(0).getTitle());
    }

    @Test
    void 命中排除词_淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("Some.Show.预告片.1080p", 10, 100L, false)),
                criteria(0, 0L, 0L, false, List.of(), List.of("预告", "花絮")));

        assertTrue(result.isEmpty());
    }

    @Test
    void 排除词大小写不敏感() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("Some.Show.SAMPLES.1080p", 10, 100L, false)),
                criteria(0, 0L, 0L, false, List.of(), List.of("samples")));

        assertTrue(result.isEmpty());
    }

    @Test
    void 包含词非空_一个都没命中则淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("Some.Show.1080p.WEB-DL", 10, 100L, false)),
                criteria(0, 0L, 0L, false, List.of("中字", "国语"), List.of()));

        assertTrue(result.isEmpty());
    }

    @Test
    void 包含词命中其一即保留() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("Some.Show.1080p.中字", 10, 100L, false)),
                criteria(0, 0L, 0L, false, List.of("中字", "国语"), List.of()));

        assertEquals(1, result.size());
    }

    @Test
    void 排除优先于包含_同时命中两者时淘汰() {
        List<TorrentInfo> result = engine.filter(
                List.of(torrent("Some.Show.中字.预告", 10, 100L, false)),
                criteria(0, 0L, 0L, false, List.of("中字"), List.of("预告")));

        assertTrue(result.isEmpty());
    }

    @Test
    void 多个候选_只保留合格的() {
        List<TorrentInfo> candidates = List.of(
                torrent("good.1080p", 10, 5_000_000_000L, false),
                torrent("低做种.1080p", 1, 5_000_000_000L, false),
                torrent("预告.1080p", 10, 5_000_000_000L, false),
                torrent("good2.1080p", 20, 5_000_000_000L, false));

        List<TorrentInfo> result = engine.filter(candidates,
                criteria(5, 0L, 0L, false, List.of(), List.of("预告")));

        assertEquals(List.of("good.1080p", "good2.1080p"),
                result.stream().map(TorrentInfo::getTitle).toList());
    }

    @Test
    void 输入为空列表_返回空列表() {
        assertTrue(engine.filter(List.of(), criteria(0, 0L, 0L, false, List.of(), List.of())).isEmpty());
    }

    @Test
    void 标题为null的候选_被淘汰而非抛异常() {
        TorrentInfo t = torrent(null, 10, 100L, false);

        List<TorrentInfo> result = engine.filter(List.of(t),
                criteria(0, 0L, 0L, false, List.of(), List.of("预告")));

        assertTrue(result.isEmpty());
    }

    @Test
    void 结果列表不含原列表引用_不会被调用方修改() {
        List<TorrentInfo> result = engine.filter(List.of(ok()),
                criteria(0, 0L, 0L, false, List.of(), List.of()));

        // 返回新列表而非原列表的视图
        result.add(ok());
        assertEquals(2, result.size());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorrentFilterEngineFilterTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `TorrentFilterEngine` 找不到符号

- [ ] **步骤 3：编写实现**

创建 `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/TorrentFilterEngine.java`：

```java
package com.ruoyi.openliststrm.pt.filter;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 种子过滤与择优引擎。纯逻辑，不读数据库、不发网络请求——生效条件由调用方
 * 通过 {@link FilterCriteria} 传入（见 {@link FilterCriteriaFactory}）。
 *
 * @author Jack
 */
@Slf4j
@Component
public class TorrentFilterEngine {

    /**
     * 硬性过滤：淘汰不满足条件的候选，保留原顺序。
     * <p>
     * 被淘汰的种子不落库，只记 debug 日志并带上具体原因（哪条规则、阈值、实际值）——
     * 这些日志是后续调优过滤规则的主要素材。
     * </p>
     *
     * @return 新的可变列表，调用方修改它不会影响入参
     */
    public List<TorrentInfo> filter(List<TorrentInfo> candidates, FilterCriteria criteria) {
        List<TorrentInfo> survivors = new ArrayList<>();
        for (TorrentInfo torrent : candidates) {
            String reason = rejectReason(torrent, criteria);
            if (reason == null) {
                survivors.add(torrent);
            } else {
                log.debug("种子被过滤：{} —— {}", torrent.getTitle(), reason);
            }
        }
        return survivors;
    }

    /**
     * 返回淘汰原因；返回 null 表示通过。
     */
    private String rejectReason(TorrentInfo torrent, FilterCriteria criteria) {
        if (torrent.getSeeders() < criteria.minSeeders()) {
            return "做种数 " + torrent.getSeeders() + " 低于下限 " + criteria.minSeeders();
        }
        if (criteria.minSize() > 0 && torrent.getSize() < criteria.minSize()) {
            return "体积 " + torrent.getSize() + " 小于下限 " + criteria.minSize();
        }
        if (criteria.maxSize() > 0 && torrent.getSize() > criteria.maxSize()) {
            return "体积 " + torrent.getSize() + " 超过上限 " + criteria.maxSize();
        }
        if (criteria.freeOnly() && !torrent.isFree()) {
            return "非免费种(下载量系数 " + torrent.getDownloadVolumeFactor() + ")，而配置为仅要免费";
        }

        String title = torrent.getTitle();
        // 标题缺失的条目无法做关键词判定，一律淘汰而非放行
        if (StringUtils.isBlank(title)) {
            return "标题为空，无法判定";
        }
        String lower = title.toLowerCase(Locale.ROOT);

        for (String keyword : criteria.excludeKeywords()) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return "命中排除词「" + keyword + "」";
            }
        }
        if (!criteria.includeKeywords().isEmpty() && !containsAny(lower, criteria.includeKeywords())) {
            return "未命中任何包含词 " + criteria.includeKeywords();
        }
        return null;
    }

    private boolean containsAny(String lowerTitle, List<String> keywords) {
        for (String keyword : keywords) {
            if (lowerTitle.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorrentFilterEngineFilterTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：Tests run: 16, Failures: 0, Errors: 0

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/TorrentFilterEngine.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/TorrentFilterEngineFilterTest.java
git commit -m "feat(pt): TorrentFilterEngine 硬性过滤规则"
```

---

## 任务 7：TorrentFilterEngine 择优

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/TorrentFilterEngine.java`（新增 `pickBest` 方法，不动 `filter`）
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/TorrentFilterEnginePickBestTest.java`

> **这是「下得准不准」的最终决定点。** 按 `criteria.sortPriority()` 的维度顺序，把各维度的比较器用 `thenComparing` 串起来，取排在最前的那个。
>
> **必须验证的核心行为：同一批候选，在不同的 `sortPriority` 配置下会选出不同的赢家。** 这是「排序权重可调」这个需求的唯一实证，如果测不出差异，说明串联逻辑没生效。

- [ ] **步骤 1：编写失败的测试**

创建 `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/TorrentFilterEnginePickBestTest.java`：

```java
package com.ruoyi.openliststrm.pt.filter;

import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TorrentFilterEnginePickBestTest {

    private final TorrentFilterEngine engine = new TorrentFilterEngine();

    private FilterCriteria criteria(List<SortDimension> sortPriority, long preferredSize) {
        return new FilterCriteria(0, 0L, 0L, false, List.of(), List.of(),
                List.of("2160p", "1080p", "720p"), sortPriority, preferredSize);
    }

    private TorrentInfo torrent(String title, String resolution, boolean free, int seeders, long size) {
        TorrentInfo t = new TorrentInfo();
        t.setTitle(title);
        t.setParsedResolution(resolution);
        t.setDownloadVolumeFactor(free ? 0.0 : 1.0);
        t.setSeeders(seeders);
        t.setSize(size);
        return t;
    }

    /** 三个候选，各维度上互有胜负，用于验证维度顺序真的决定结果 */
    private List<TorrentInfo> mixedCandidates() {
        return List.of(
                // 分辨率最高，但收费、做种少
                torrent("4K收费", "2160p", false, 3, 60_000_000_000L),
                // 分辨率中等，免费，做种中等
                torrent("1080免费", "1080p", true, 20, 5_000_000_000L),
                // 分辨率最低，收费，做种最多
                torrent("720多做种", "720p", false, 200, 2_000_000_000L));
    }

    @Test
    void 分辨率优先_选出4K() {
        TorrentInfo best = engine.pickBest(mixedCandidates(),
                criteria(List.of(SortDimension.RESOLUTION, SortDimension.FREE, SortDimension.SEEDERS), 0L));

        assertEquals("4K收费", best.getTitle());
    }

    @Test
    void 免费优先_选出1080免费() {
        // 宁可要免费的 1080p，也不要收费的 4K
        TorrentInfo best = engine.pickBest(mixedCandidates(),
                criteria(List.of(SortDimension.FREE, SortDimension.RESOLUTION, SortDimension.SEEDERS), 0L));

        assertEquals("1080免费", best.getTitle());
    }

    @Test
    void 做种数优先_选出720多做种() {
        TorrentInfo best = engine.pickBest(mixedCandidates(),
                criteria(List.of(SortDimension.SEEDERS, SortDimension.RESOLUTION), 0L));

        assertEquals("720多做种", best.getTitle());
    }

    @Test
    void 同一批候选_三种排序配置选出三个不同赢家() {
        // 这条是「排序权重可调」需求的核心实证
        List<TorrentInfo> candidates = mixedCandidates();

        String byResolution = engine.pickBest(candidates,
                criteria(List.of(SortDimension.RESOLUTION), 0L)).getTitle();
        String byFree = engine.pickBest(candidates,
                criteria(List.of(SortDimension.FREE, SortDimension.RESOLUTION), 0L)).getTitle();
        String bySeeders = engine.pickBest(candidates,
                criteria(List.of(SortDimension.SEEDERS), 0L)).getTitle();

        assertEquals("4K收费", byResolution);
        assertEquals("1080免费", byFree);
        assertEquals("720多做种", bySeeders);
    }

    @Test
    void 首维度同级时_由次维度决胜() {
        List<TorrentInfo> candidates = List.of(
                torrent("1080少做种", "1080p", false, 5, 100L),
                torrent("1080多做种", "1080p", false, 50, 100L));

        TorrentInfo best = engine.pickBest(candidates,
                criteria(List.of(SortDimension.RESOLUTION, SortDimension.SEEDERS), 0L));

        assertEquals("1080多做种", best.getTitle());
    }

    @Test
    void 体积接近度参与决胜() {
        List<TorrentInfo> candidates = List.of(
                torrent("超大", "1080p", false, 10, 60_000_000_000L),
                torrent("适中", "1080p", false, 10, 5_200_000_000L),
                torrent("过小", "1080p", false, 10, 100_000_000L));

        TorrentInfo best = engine.pickBest(candidates,
                criteria(List.of(SortDimension.SIZE), 5_000_000_000L));

        assertEquals("适中", best.getTitle());
    }

    @Test
    void 全部维度同级_返回第一个保持稳定() {
        List<TorrentInfo> candidates = List.of(
                torrent("先来的", "1080p", false, 10, 100L),
                torrent("后到的", "1080p", false, 10, 100L));

        TorrentInfo best = engine.pickBest(candidates,
                criteria(List.of(SortDimension.RESOLUTION, SortDimension.SEEDERS), 0L));

        assertEquals("先来的", best.getTitle());
    }

    @Test
    void 单个候选_直接返回它() {
        TorrentInfo only = torrent("唯一", "480p", false, 0, 1L);

        assertEquals("唯一", engine.pickBest(List.of(only), criteria(List.of(SortDimension.RESOLUTION), 0L)).getTitle());
    }

    @Test
    void 空候选_返回null() {
        assertNull(engine.pickBest(List.of(), criteria(List.of(SortDimension.RESOLUTION), 0L)));
    }

    @Test
    void 不修改入参列表的顺序() {
        List<TorrentInfo> candidates = new ArrayList<>(mixedCandidates());
        List<String> before = candidates.stream().map(TorrentInfo::getTitle).toList();

        engine.pickBest(candidates, criteria(List.of(SortDimension.SEEDERS), 0L));

        assertEquals(before, candidates.stream().map(TorrentInfo::getTitle).toList());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorrentFilterEnginePickBestTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：编译失败，报 `pickBest` 方法找不到

- [ ] **步骤 3：编写实现**

在 `TorrentFilterEngine.java` 的 `filter` 方法**之后**、`rejectReason` 方法**之前**插入：

```java
    /**
     * 从候选中挑出最优的一个。
     * <p>
     * 按 {@link FilterCriteria#sortPriority()} 的维度顺序，把各维度的比较器用
     * thenComparing 串联后取排在最前的那个。维度顺序由配置决定，因此同一批候选
     * 在不同配置下会选出不同的赢家——这正是「排序权重可调」的实现方式。
     * </p>
     * <p>
     * 全部维度都判同级时返回列表中的第一个（比较过程不改变入参列表的顺序）。
     * </p>
     *
     * @return 最优候选；候选为空时返回 null
     */
    public TorrentInfo pickBest(List<TorrentInfo> candidates, FilterCriteria criteria) {
        if (candidates.isEmpty()) {
            return null;
        }
        Comparator<TorrentInfo> comparator = null;
        for (SortDimension dimension : criteria.sortPriority()) {
            Comparator<TorrentInfo> next = dimension.comparator(criteria);
            comparator = (comparator == null) ? next : comparator.thenComparing(next);
        }
        if (comparator == null) {
            // FilterCriteria 保证 sortPriority 非空，这里只是防御
            return candidates.get(0);
        }

        TorrentInfo best = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            // 严格小于才替换，保证同级时保留先出现的那个
            if (comparator.compare(candidates.get(i), best) < 0) {
                best = candidates.get(i);
            }
        }
        log.debug("择优结果：{}（候选 {} 个，维度顺序 {}）",
                best.getTitle(), candidates.size(), criteria.sortPriority());
        return best;
    }
```

并在文件顶部的 import 区补上：

```java
import java.util.Comparator;
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorrentFilterEnginePickBestTest,TorrentFilterEngineFilterTest -Dsurefire.failIfNoSpecifiedTests=false`

预期：两个测试类全绿（pickBest 10 个用例、filter 16 个用例）

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/filter/TorrentFilterEngine.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/filter/TorrentFilterEnginePickBestTest.java
git commit -m "feat(pt): TorrentFilterEngine 按可配置维度顺序择优"
```

---

## 任务 8：端到端验收

**文件：** 无代码变更

> 验证计划 2 的成功标准：4 张表就位，过滤引擎能在真实配置下从一批候选里选出正确的那个。

- [ ] **步骤 1：全量测试**

运行：`mvn -pl ruoyi-openliststrm -am test`

预期：BUILD SUCCESS。计划 1 结束时是 133 个测试，本计划新增 55 个（FilterCriteria 7、SortDimension 12、FilterCriteriaFactory 10、filter 16、pickBest 10），预期总数 188，无失败。

- [ ] **步骤 2：全量构建**

运行：`mvn clean package -DskipTests`

预期：BUILD SUCCESS，生成 `ruoyi-admin/target/ruoyi-admin.jar`

- [ ] **步骤 3：部署后端**

运行：`docker compose up -d --build --no-deps backend`

（只重建后端。MySQL 与前端不动——本计划没有前端改动。）

- [ ] **步骤 4：验证迁移已执行**

运行：

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" osr -e "show tables like 'pt\_%'; select * from pt_filter_config\G select menu_id, menu_name, url from sys_menu where menu_id in (2064,2065);"
```

预期：
- 表列表含 `pt_filter_config` / `pt_subscription` / `pt_subscription_episode` / `pt_download_record`，加上计划 1 的三张共 7 张
- `pt_filter_config` 有且仅有 id=1 一行，`sort_priority` 为 `RESOLUTION,FREE,SEEDERS,SIZE`
- 两条菜单记录存在

若菜单名显示为 `???`，那是终端代码页问题不是数据问题，用 `select menu_id, hex(menu_name) from sys_menu where menu_id in (2064,2065);` 复核 UTF-8 编码是否正确。

- [ ] **步骤 5：验证唯一约束真的生效**

运行：

```bash
docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" osr -e "
insert into pt_download_record (sub_id,episode,indexer_id,guid,guid_hash,title,size,seeders,state) values (1,1,1,'g1','hash-dup','t1',1,1,'PUSHED');
insert into pt_download_record (sub_id,episode,indexer_id,guid,guid_hash,title,size,seeders,state) values (1,2,1,'g2','hash-dup','t2',1,1,'PUSHED');
" 2>&1 | tail -2
```

预期：第二条插入报 `Duplicate entry` 错误——这证明 `(indexer_id, guid_hash)` 唯一约束生效。

清理测试数据：

```bash
docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" osr -e "delete from pt_download_record where guid_hash='hash-dup';"
```

- [ ] **步骤 6：Commit（如有修正）**

若步骤 4、5 中发现问题并修正，提交修正：

```bash
git add -A
git commit -m "fix(pt): 修正订阅表验收中发现的问题"
```

---

## 后续计划

- **计划 3：订阅管理** — 订阅创建服务（TMDb 查总集数、初始化每集状态、查 Emby 初始化已入库集）、订阅 CRUD 与 TMDb 搜索 REST 接口、订阅页面（进度展示「已入库 5/12，缺 3、7」）、过滤规则配置页
- **计划 4：编排与调度** — `MediaParser` 的纯本地解析入口（不触发 TMDb/AI）、`SubscriptionEngine`、`RssPollTask` / `DownloadTrackTask` / `LibrarySyncTask` 三个调度器、Telegram 通知

**计划 3 开工前必须先读**计划 1 文档末尾的「留给计划 2 的已知约束」一节（凭据脱敏与编辑功能的耦合、Emby 多条命中取并集、SID 缓存并发、多媒体服务器只有 id 最小的生效）。
