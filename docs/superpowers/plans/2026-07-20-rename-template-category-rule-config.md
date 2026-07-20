# 重命名文件名模板 + 分类规则可视化配置 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让管理员在网页端可视化编辑重命名文件名模板（Pebble 语法）和分类目录规则，不再需要改代码重新部署。

**架构：** 文件名模板挪到 RuoYi 自带的 `sys_config` 参数表（新增一个 `IRenameTemplateConfigService` 封装读取默认值兜底、试渲染校验、保存）；分类规则挪到新表 `rename_category_rule`，匹配逻辑从 `MediaRenameProcessor` 里抽出三个纯函数类（`CategoryRuleConverter`/`CategoryClassifier`/`CategoryRuleValidator`）便于不依赖 Spring/DB 直接单测；`MediaRenameProcessor`/`RenameTaskRestController` 改为从这两个新服务读取配置而不是硬编码常量；前端新增一个"重命名规则设置"页面（两个 Tab）。

**技术栈：** Spring Boot 4 + MyBatis-Plus（后端）、JUnit5 + Mockito（单测）、Vue 3 + Element Plus + TypeScript（前端）。

---

## 参考文档

- 设计文档：[docs/superpowers/specs/2026-07-20-rename-template-category-rule-config-design.md](../specs/2026-07-20-rename-template-category-rule-config-design.md)
- 本计划中涉及的既有代码位置（写代码前建议先读一遍，避免凭空猜测签名）：
  - `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/monitor/processor/MediaRenameProcessor.java`
  - `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/CategoryRule.java`
  - `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/model/MediaInfo.java`
  - `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/render/PebbleRenderer.java`
  - `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/RenameTaskRestController.java`
  - `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/BaseCrudRestController.java`
  - `ruoyi-system/src/main/java/com/ruoyi/system/service/ISysConfigService.java` + `impl/SysConfigServiceImpl.java`

## 文件结构总览

**新建：**
- `ruoyi-common/src/main/resources/sql/20260720-rename-category-rule.sql` — 建表 + 12条种子规则 + sys_config种子 + sys_menu
- `ruoyi-openliststrm/.../mybatisplus/domain/RenameCategoryRulePlus.java` — 分类规则实体
- `ruoyi-openliststrm/.../mybatisplus/mapper/RenameCategoryRulePlusMapper.java`
- `ruoyi-openliststrm/.../mybatisplus/service/IRenameCategoryRulePlusService.java`
- `ruoyi-openliststrm/.../mybatisplus/service/impl/RenameCategoryRulePlusServiceImpl.java`
- `ruoyi-openliststrm/.../rename/rule/CategoryRuleValidator.java` — 纯校验（不依赖Spring/DB）
- `ruoyi-openliststrm/.../rename/rule/CategoryRuleConverter.java` — 纯转换（DB行 -> CategoryRule）
- `ruoyi-openliststrm/.../rename/rule/CategoryClassifier.java` — 从 MediaRenameProcessor 抽出的纯匹配逻辑
- `ruoyi-openliststrm/.../rename/config/IRenameTemplateConfigService.java`
- `ruoyi-openliststrm/.../rename/config/RenameTemplateConfigServiceImpl.java`
- `ruoyi-openliststrm/.../controller/api/RenameCategoryRuleRestController.java`
- `ruoyi-openliststrm/.../controller/api/RenameTemplateConfigRestController.java`
- 对应的3个测试类（`CategoryRuleValidatorTest`/`CategoryClassifierTest`/`RenameTemplateConfigServiceImplTest`）
- `openlist-web/src/api/openlist/renameConfig.ts`
- `openlist-web/src/composables/useRenameConfig.ts`
- `openlist-web/src/views/openlist/renameConfig/RuleTable.vue`
- `openlist-web/src/views/openlist/renameConfig/index.vue`

**修改：**
- `ruoyi-openliststrm/.../monitor/processor/MediaRenameProcessor.java` — 移除硬编码常量，改为从新服务读取
- `ruoyi-openliststrm/.../controller/api/RenameTaskRestController.java` — 移除重复常量，改为从新服务读取
- `openlist-web/src/router/index.ts` — 注册新页面路由

**不需要 Mapper XML：** 新表用 MyBatis-Plus 默认 CRUD（`BaseMapper`/`ServiceImpl`），自定义查询用 `lambdaQuery()`，无需手写 XML（与 `RenameOrphanPlusMapper` 等既有表一致）。

---

### 任务 1：建表迁移脚本（表 + 种子数据 + sys_config + 菜单）

**文件：**
- 创建：`ruoyi-common/src/main/resources/sql/20260720-rename-category-rule.sql`

- [ ] **步骤 1：编写迁移脚本**

```sql
-- ----------------------------
-- 20260720: 重命名文件名模板 + 分类规则可视化配置
-- 分类规则原来硬编码在 MediaRenameProcessor.defaultRules()，本次挪到数据库表，
-- 种子数据与原硬编码规则完全一致（电影3条 + 剧集9条，剧集含8条条件规则+1条兜底）。
-- 文件名模板原来硬编码为 DEFAULT_FILENAME_TEMPLATE 常量，本次挪到 sys_config，
-- 用 INSERT ... WHERE NOT EXISTS 保证幂等，与 20260718-add-openlist-configs.sql 手法一致。
-- ----------------------------

CREATE TABLE `rename_category_rule` (
    `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `media_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '媒体类型 movie/tv',
    `seq` int(10) NOT NULL DEFAULT 0 COMMENT '排序序号，越小优先级越高，同media_type下最大seq为兜底规则',
    `genre_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '逗号分隔的TMDB genre id，空表示不限',
    `original_languages` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '逗号分隔语言码，空表示不限',
    `origin_countries` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '逗号分隔国家码，空表示不限',
    `target_dir` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '命中后的目标目录名，同时兼作展示名',
    `is_fallback` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '是否兜底规则 0-否 1-是，每个media_type有且只有一条，且seq最大',
    `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_media_type_seq`(`media_type`, `seq`) USING BTREE
) COMMENT = '重命名分类规则配置';

-- ----------------------------
-- 种子数据：电影 3 条（对应原 defaultRules() 的 movie 列表）
-- ----------------------------
INSERT INTO `rename_category_rule` (`media_type`, `seq`, `genre_ids`, `original_languages`, `origin_countries`, `target_dir`, `is_fallback`, `create_time`) VALUES
('movie', 0, '16', NULL, NULL, '动画电影', '0', '2026-07-20 00:00:00'),
('movie', 1, NULL, 'zh,cn,bo,za', NULL, '华语电影', '0', '2026-07-20 00:00:00'),
('movie', 2, NULL, NULL, NULL, '外语电影', '1', '2026-07-20 00:00:00');

-- ----------------------------
-- 种子数据：电视剧 9 条（对应原 defaultRules() 的 tv 列表，8条条件规则 + 1条兜底）
-- ----------------------------
INSERT INTO `rename_category_rule` (`media_type`, `seq`, `genre_ids`, `original_languages`, `origin_countries`, `target_dir`, `is_fallback`, `create_time`) VALUES
('tv', 0, '16', NULL, 'CN,TW,HK', '国漫', '0', '2026-07-20 00:00:00'),
('tv', 1, '16', NULL, 'JP', '日番', '0', '2026-07-20 00:00:00'),
('tv', 2, '99', NULL, NULL, '纪录片', '0', '2026-07-20 00:00:00'),
('tv', 3, '10762', NULL, NULL, '儿童', '0', '2026-07-20 00:00:00'),
('tv', 4, '10764,10767', NULL, NULL, '综艺', '0', '2026-07-20 00:00:00'),
('tv', 5, NULL, NULL, 'CN,TW,HK', '国产剧', '0', '2026-07-20 00:00:00'),
('tv', 6, NULL, NULL, 'US,FR,GB,DE,ES,IT,NL,PT,RU,UK', '欧美剧', '0', '2026-07-20 00:00:00'),
('tv', 7, NULL, NULL, 'JP,KP,KR,TH,IN,SG', '日韩剧', '0', '2026-07-20 00:00:00'),
('tv', 8, NULL, NULL, NULL, '未分类', '1', '2026-07-20 00:00:00');

-- ----------------------------
-- 文件名模板默认值种子（与原 DEFAULT_FILENAME_TEMPLATE 常量内容完全一致）
-- ----------------------------
INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `remark`)
SELECT '重命名文件名模板', 'rename.filename.template', '{{ title }} {% if year %} ({{ year }}) {% endif %}/{% if season %}Season {{ season }}/{% endif %}{{ title }} {% if year and not season %} ({{ year }}) {% endif %}{% if season %}S{{ season }}{% endif %}{% if episode %}E{{ episode }}{% endif %}{% if resolution %} - {{ resolution }}{% endif %}{% if source %}.{{ source }}{% endif %}{% if videoCodec %}.{{ videoCodec }}{% endif %}{% if audioCodec %}.{{ audioCodec }}{% endif %}{% if tags is not empty %}.{{ tags|join(\'.\') }}{% endif %}{% if releaseGroup %}-{{ releaseGroup }}{% endif %}.{{ extension }}', 'N', 'admin', '2026-07-20 00:00:00', '重命名文件名模板（Pebble语法），可在"重命名规则设置"页面修改'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'rename.filename.template');

-- ----------------------------
-- 菜单：挂在 OpenListStrm(2006) 下
-- ----------------------------
INSERT INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2060, '重命名规则设置', 2006, 8, '/openlist/renameConfig', '', 'C', '0', '1', 'openliststrm:renameConfig:view', '#', 'admin', '2026-07-20 00:00:00', '', NULL, '重命名文件名模板与分类规则可视化配置菜单');
```

- [ ] **步骤 2：确认命名与既有迁移脚本风格一致**

打开 `ruoyi-common/src/main/resources/sql/20260719-rename-orphan.sql` 对照检查：字符集/排序规则写法、`INSERT IGNORE`/`WHERE NOT EXISTS` 幂等手法、菜单字段顺序是否一致。

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-common/src/main/resources/sql/20260720-rename-category-rule.sql
git commit -m "feat: 新增重命名分类规则表与文件名模板参数种子数据"
```

---

### 任务 2：`RenameCategoryRulePlus` 实体

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/RenameCategoryRulePlus.java`

- [ ] **步骤 1：创建实体类**

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
 * 重命名分类规则配置
 * </p>
 *
 * @author Jack
 * @since 2026-07-20
 */
@Getter
@Setter
@TableName("rename_category_rule")
public class RenameCategoryRulePlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 自增主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 媒体类型 movie/tv
     */
    @TableField("media_type")
    private String mediaType;

    /**
     * 排序序号，越小优先级越高
     */
    @TableField("seq")
    private Integer seq;

    /**
     * 逗号分隔的TMDB genre id，空表示不限
     */
    @TableField("genre_ids")
    private String genreIds;

    /**
     * 逗号分隔语言码，空表示不限
     */
    @TableField("original_languages")
    private String originalLanguages;

    /**
     * 逗号分隔国家码，空表示不限
     */
    @TableField("origin_countries")
    private String originCountries;

    /**
     * 命中后的目标目录名，同时兼作展示名
     */
    @TableField("target_dir")
    private String targetDir;

    /**
     * 是否兜底规则 0-否 1-是
     */
    @TableField("is_fallback")
    private String isFallback;
}
```

- [ ] **步骤 2：Commit**

```bash
git add "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/RenameCategoryRulePlus.java"
git commit -m "feat: 新增重命名分类规则实体 RenameCategoryRulePlus"
```

---

### 任务 3：`CategoryRuleValidator`（纯校验类，TDD）

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/rule/CategoryRuleValidator.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/rule/CategoryRuleValidatorTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.ruoyi.openliststrm.rename.rule;

import com.ruoyi.openliststrm.mybatisplus.domain.RenameCategoryRulePlus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoryRuleValidatorTest {

    private RenameCategoryRulePlus rule(String targetDir, boolean fallback) {
        RenameCategoryRulePlus r = new RenameCategoryRulePlus();
        r.setTargetDir(targetDir);
        r.setIsFallback(fallback ? "1" : "0");
        return r;
    }

    @Test
    void validate_合法列表_不抛异常() {
        List<RenameCategoryRulePlus> rules = new ArrayList<>();
        rules.add(rule("国漫", false));
        rules.add(rule("未分类", true));
        assertDoesNotThrow(() -> CategoryRuleValidator.validate(rules));
    }

    @Test
    void validate_列表为空_抛异常() {
        assertThrows(IllegalArgumentException.class, () -> CategoryRuleValidator.validate(new ArrayList<>()));
    }

    @Test
    void validate_存在目标目录为空的规则_抛异常() {
        List<RenameCategoryRulePlus> rules = new ArrayList<>();
        rules.add(rule("", false));
        rules.add(rule("未分类", true));
        assertThrows(IllegalArgumentException.class, () -> CategoryRuleValidator.validate(rules));
    }

    @Test
    void validate_没有兜底规则_抛异常() {
        List<RenameCategoryRulePlus> rules = new ArrayList<>();
        rules.add(rule("国漫", false));
        rules.add(rule("日番", false));
        assertThrows(IllegalArgumentException.class, () -> CategoryRuleValidator.validate(rules));
    }

    @Test
    void validate_存在两条兜底规则_抛异常() {
        List<RenameCategoryRulePlus> rules = new ArrayList<>();
        rules.add(rule("国漫", true));
        rules.add(rule("未分类", true));
        assertThrows(IllegalArgumentException.class, () -> CategoryRuleValidator.validate(rules));
    }

    @Test
    void validate_兜底规则不在最后一位_抛异常() {
        List<RenameCategoryRulePlus> rules = new ArrayList<>();
        rules.add(rule("未分类", true));
        rules.add(rule("国漫", false));
        assertThrows(IllegalArgumentException.class, () -> CategoryRuleValidator.validate(rules));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test-compile -Dtest=CategoryRuleValidatorTest`
预期：编译失败，报错 "cannot find symbol: class CategoryRuleValidator"

- [ ] **步骤 3：编写最少实现代码**

```java
package com.ruoyi.openliststrm.rename.rule;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameCategoryRulePlus;

import java.util.List;

/**
 * 分类规则集合的纯校验逻辑：不依赖Spring/DB，方便单测。
 * 校验规则：每条 targetDir 非空；恰好一条 isFallback=1；该条必须排在列表最后一位。
 */
public final class CategoryRuleValidator {

    private CategoryRuleValidator() {
    }

    public static void validate(List<RenameCategoryRulePlus> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("规则列表不能为空");
        }
        for (int i = 0; i < rules.size(); i++) {
            RenameCategoryRulePlus rule = rules.get(i);
            if (StringUtils.isBlank(rule.getTargetDir())) {
                throw new IllegalArgumentException("第" + (i + 1) + "条规则的目标目录名不能为空");
            }
        }
        long fallbackCount = rules.stream().filter(r -> "1".equals(r.getIsFallback())).count();
        if (fallbackCount != 1) {
            throw new IllegalArgumentException("必须保留且只能保留一条兜底规则");
        }
        RenameCategoryRulePlus last = rules.get(rules.size() - 1);
        if (!"1".equals(last.getIsFallback())) {
            throw new IllegalArgumentException("兜底规则必须排在最后一位");
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=CategoryRuleValidatorTest`
预期：PASS，6 个测试全部通过

- [ ] **步骤 5：Commit**

```bash
git add "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/rule/CategoryRuleValidator.java" \
        "ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/rule/CategoryRuleValidatorTest.java"
git commit -m "feat: 新增分类规则集合校验器 CategoryRuleValidator"
```

---

### 任务 4：`CategoryRuleConverter`（DB行 -> CategoryRule 纯转换，TDD）

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/rule/CategoryRuleConverter.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/rule/CategoryRuleConverterTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.ruoyi.openliststrm.rename.rule;

import com.ruoyi.openliststrm.mybatisplus.domain.RenameCategoryRulePlus;
import com.ruoyi.openliststrm.rename.CategoryRule;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryRuleConverterTest {

    @Test
    void toCategoryRule_全部条件字段都为空_转换为无条件规则() {
        RenameCategoryRulePlus row = new RenameCategoryRulePlus();
        row.setTargetDir("未分类");
        CategoryRule rule = CategoryRuleConverter.toCategoryRule(row);
        assertEquals("未分类", rule.getName());
        MediaInfo info = new MediaInfo("x.mkv");
        assertTrue(rule.matches(info));
    }

    @Test
    void toCategoryRule_逗号分隔的genreIds_正确拆分并匹配() {
        RenameCategoryRulePlus row = new RenameCategoryRulePlus();
        row.setTargetDir("综艺");
        row.setGenreIds("10764,10767");
        CategoryRule rule = CategoryRuleConverter.toCategoryRule(row);

        MediaInfo matched = new MediaInfo("x.mkv");
        matched.setGenreIds(Arrays.asList("10767"));
        assertTrue(rule.matches(matched));

        MediaInfo notMatched = new MediaInfo("y.mkv");
        notMatched.setGenreIds(Arrays.asList("99"));
        assertFalse(rule.matches(notMatched));
    }

    @Test
    void toCategoryRule_逗号分隔的originCountries_正确拆分并匹配() {
        RenameCategoryRulePlus row = new RenameCategoryRulePlus();
        row.setTargetDir("国产剧");
        row.setOriginCountries("CN,TW,HK");
        CategoryRule rule = CategoryRuleConverter.toCategoryRule(row);

        MediaInfo matched = new MediaInfo("x.mkv");
        matched.setOriginCountries(Arrays.asList("TW"));
        assertTrue(rule.matches(matched));
    }

    @Test
    void toCategoryRules_按输入顺序转换整个列表() {
        RenameCategoryRulePlus row1 = new RenameCategoryRulePlus();
        row1.setTargetDir("A");
        RenameCategoryRulePlus row2 = new RenameCategoryRulePlus();
        row2.setTargetDir("B");
        List<CategoryRule> rules = CategoryRuleConverter.toCategoryRules(Arrays.asList(row1, row2));
        assertEquals(2, rules.size());
        assertEquals("A", rules.get(0).getName());
        assertEquals("B", rules.get(1).getName());
    }

    @Test
    void toCategoryRules_空列表_返回空列表() {
        assertTrue(CategoryRuleConverter.toCategoryRules(Collections.emptyList()).isEmpty());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test-compile -Dtest=CategoryRuleConverterTest`
预期：编译失败，报错 "cannot find symbol: class CategoryRuleConverter"

- [ ] **步骤 3：编写最少实现代码**

```java
package com.ruoyi.openliststrm.rename.rule;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameCategoryRulePlus;
import com.ruoyi.openliststrm.rename.CategoryRule;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据库行 (RenameCategoryRulePlus) 与运行时匹配对象 (CategoryRule) 之间的纯转换。
 */
public final class CategoryRuleConverter {

    private CategoryRuleConverter() {
    }

    public static CategoryRule toCategoryRule(RenameCategoryRulePlus row) {
        CategoryRule rule = new CategoryRule(row.getTargetDir());
        if (StringUtils.isNotBlank(row.getGenreIds())) {
            rule.withGenreIds(row.getGenreIds().split(","));
        }
        if (StringUtils.isNotBlank(row.getOriginalLanguages())) {
            rule.withOriginalLanguage(row.getOriginalLanguages().split(","));
        }
        if (StringUtils.isNotBlank(row.getOriginCountries())) {
            rule.withOriginCountry(row.getOriginCountries().split(","));
        }
        return rule;
    }

    public static List<CategoryRule> toCategoryRules(List<RenameCategoryRulePlus> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(CategoryRuleConverter::toCategoryRule).collect(Collectors.toList());
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=CategoryRuleConverterTest`
预期：PASS，5 个测试全部通过

- [ ] **步骤 5：Commit**

```bash
git add "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/rule/CategoryRuleConverter.java" \
        "ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/rule/CategoryRuleConverterTest.java"
git commit -m "feat: 新增分类规则转换器 CategoryRuleConverter"
```

---

### 任务 5：`CategoryClassifier`（从 MediaRenameProcessor 抽出的纯匹配逻辑，TDD 回归）

这一步是本次重构最关键的安全网：用与迁移脚本种子数据等价的规则集合，验证新的匹配逻辑与原 `MediaRenameProcessor.defaultRules()` 硬编码规则的分类结果完全一致。

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/rule/CategoryClassifier.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/rule/CategoryClassifierTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.ruoyi.openliststrm.rename.rule;

import com.ruoyi.openliststrm.rename.CategoryRule;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CategoryClassifierTest {

    // 与 20260720-rename-category-rule.sql 种子数据等价的规则集合，
    // 用于验证新匹配逻辑与原 MediaRenameProcessor.defaultRules() 的分类结果完全一致（回归保证）。
    private List<CategoryRule> movieRules() {
        return Arrays.asList(
                new CategoryRule("动画电影").withGenreIds("16"),
                new CategoryRule("华语电影").withOriginalLanguage("zh", "cn", "bo", "za"),
                new CategoryRule("外语电影")
        );
    }

    private List<CategoryRule> tvRules() {
        return Arrays.asList(
                new CategoryRule("国漫").withGenreIds("16").withOriginCountry("CN", "TW", "HK"),
                new CategoryRule("日番").withGenreIds("16").withOriginCountry("JP"),
                new CategoryRule("纪录片").withGenreIds("99"),
                new CategoryRule("儿童").withGenreIds("10762"),
                new CategoryRule("综艺").withGenreIds("10764", "10767"),
                new CategoryRule("国产剧").withOriginCountry("CN", "TW", "HK"),
                new CategoryRule("欧美剧").withOriginCountry("US", "FR", "GB", "DE", "ES", "IT", "NL", "PT", "RU", "UK"),
                new CategoryRule("日韩剧").withOriginCountry("JP", "KP", "KR", "TH", "IN", "SG"),
                new CategoryRule("未分类")
        );
    }

    @Test
    void classify_国漫_同时命中动画和CN应归为国漫而非动画电影() {
        MediaInfo info = new MediaInfo("x.mkv");
        info.setGenreIds(Arrays.asList("16"));
        info.setOriginCountries(Arrays.asList("CN"));
        assertEquals("国漫", CategoryClassifier.classify(tvRules(), info));
    }

    @Test
    void classify_日番_动画且日本() {
        MediaInfo info = new MediaInfo("x.mkv");
        info.setGenreIds(Arrays.asList("16"));
        info.setOriginCountries(Arrays.asList("JP"));
        assertEquals("日番", CategoryClassifier.classify(tvRules(), info));
    }

    @Test
    void classify_欧美剧_美国出品且非动画() {
        MediaInfo info = new MediaInfo("x.mkv");
        info.setOriginCountries(Arrays.asList("US"));
        assertEquals("欧美剧", CategoryClassifier.classify(tvRules(), info));
    }

    @Test
    void classify_未分类_什么条件都不满足时落到兜底() {
        MediaInfo info = new MediaInfo("x.mkv");
        info.setOriginCountries(Arrays.asList("BR"));
        assertEquals("未分类", CategoryClassifier.classify(tvRules(), info));
    }

    @Test
    void classify_动画电影_电影类型且genre为动画() {
        MediaInfo info = new MediaInfo("x.mkv");
        info.setGenreIds(Arrays.asList("16"));
        assertEquals("动画电影", CategoryClassifier.classify(movieRules(), info));
    }

    @Test
    void classify_华语电影_原始语言为zh() {
        MediaInfo info = new MediaInfo("x.mkv");
        info.setOriginalLanguage("zh");
        assertEquals("华语电影", CategoryClassifier.classify(movieRules(), info));
    }

    @Test
    void classify_外语电影_什么条件都不满足时落到兜底() {
        MediaInfo info = new MediaInfo("x.mkv");
        info.setOriginalLanguage("en");
        assertEquals("外语电影", CategoryClassifier.classify(movieRules(), info));
    }

    @Test
    void classify_规则列表为空_返回null由调用方自行兜底() {
        MediaInfo info = new MediaInfo("x.mkv");
        assertNull(CategoryClassifier.classify(List.of(), info));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test-compile -Dtest=CategoryClassifierTest`
预期：编译失败，报错 "cannot find symbol: class CategoryClassifier"

- [ ] **步骤 3：编写最少实现代码**

```java
package com.ruoyi.openliststrm.rename.rule;

import com.ruoyi.openliststrm.rename.CategoryRule;
import com.ruoyi.openliststrm.rename.model.MediaInfo;

import java.util.List;

/**
 * 从 MediaRenameProcessor 抽出的纯匹配逻辑：按顺序遍历规则列表，第一条 matches 命中的即为结果。
 * 不依赖Spring/DB，方便直接用规则数据回归验证。
 */
public final class CategoryClassifier {

    private CategoryClassifier() {
    }

    public static String classify(List<CategoryRule> rules, MediaInfo info) {
        if (rules == null) {
            return null;
        }
        for (CategoryRule rule : rules) {
            if (rule.matches(info)) {
                return rule.getName();
            }
        }
        return null;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=CategoryClassifierTest`
预期：PASS，8 个测试全部通过

- [ ] **步骤 5：Commit**

```bash
git add "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/rule/CategoryClassifier.java" \
        "ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/rule/CategoryClassifierTest.java"
git commit -m "feat: 抽出分类匹配纯逻辑 CategoryClassifier 并做回归测试"
```

---

### 任务 6：`RenameCategoryRulePlusMapper`

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/mapper/RenameCategoryRulePlusMapper.java`

- [ ] **步骤 1：创建 Mapper 接口**

```java
package com.ruoyi.openliststrm.mybatisplus.mapper;

import com.ruoyi.openliststrm.mybatisplus.domain.RenameCategoryRulePlus;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 重命名分类规则配置 Mapper 接口
 * </p>
 *
 * @author Jack
 * @since 2026-07-20
 */
public interface RenameCategoryRulePlusMapper extends BaseMapper<RenameCategoryRulePlus> {

}
```

- [ ] **步骤 2：Commit**

```bash
git add "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/mapper/RenameCategoryRulePlusMapper.java"
git commit -m "feat: 新增 RenameCategoryRulePlusMapper"
```

---

### 任务 7：`IRenameCategoryRulePlusService` + 实现

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/IRenameCategoryRulePlusService.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/impl/RenameCategoryRulePlusServiceImpl.java`

不写隔离单测：`replaceAll` 依赖真实数据库事务（`remove`+`saveBatch`），本仓库对这类直接包装 MyBatis-Plus CRUD 的 ServiceImpl 一贯不写隔离单测（对照 `RenameOrphanPlusServiceImpl` 同样没有测试文件），核心校验逻辑已在任务3的 `CategoryRuleValidatorTest` 里覆盖；整条链路在任务17做手动浏览器验证。

- [ ] **步骤 1：创建 Service 接口**

```java
package com.ruoyi.openliststrm.mybatisplus.service;

import com.ruoyi.openliststrm.mybatisplus.domain.RenameCategoryRulePlus;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 重命名分类规则配置 服务类
 * </p>
 *
 * @author Jack
 * @since 2026-07-20
 */
public interface IRenameCategoryRulePlusService extends IService<RenameCategoryRulePlus> {

    /**
     * 按 media_type 查询启用中的规则，按 seq 升序排列
     */
    List<RenameCategoryRulePlus> listEnabledRules(String mediaType);

    /**
     * 整体替换某个 media_type 下的全部规则：先校验（CategoryRuleValidator），
     * 校验通过后在同一事务内清空旧数据、按提交顺序重新写入（seq=数组下标）。
     * 校验失败抛 IllegalArgumentException，不写库。
     */
    void replaceAll(String mediaType, List<RenameCategoryRulePlus> rules);
}
```

- [ ] **步骤 2：创建 ServiceImpl**

```java
package com.ruoyi.openliststrm.mybatisplus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameCategoryRulePlus;
import com.ruoyi.openliststrm.mybatisplus.mapper.RenameCategoryRulePlusMapper;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameCategoryRulePlusService;
import com.ruoyi.openliststrm.rename.rule.CategoryRuleValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 重命名分类规则配置 服务实现类
 * </p>
 *
 * @author Jack
 * @since 2026-07-20
 */
@Service
public class RenameCategoryRulePlusServiceImpl extends ServiceImpl<RenameCategoryRulePlusMapper, RenameCategoryRulePlus> implements IRenameCategoryRulePlusService {

    @Override
    public List<RenameCategoryRulePlus> listEnabledRules(String mediaType) {
        return lambdaQuery()
                .eq(RenameCategoryRulePlus::getMediaType, mediaType)
                .orderByAsc(RenameCategoryRulePlus::getSeq)
                .list();
    }

    @Override
    @Transactional
    public void replaceAll(String mediaType, List<RenameCategoryRulePlus> rules) {
        CategoryRuleValidator.validate(rules);
        remove(new LambdaQueryWrapper<RenameCategoryRulePlus>().eq(RenameCategoryRulePlus::getMediaType, mediaType));
        for (int i = 0; i < rules.size(); i++) {
            RenameCategoryRulePlus rule = rules.get(i);
            rule.setId(null);
            rule.setMediaType(mediaType);
            rule.setSeq(i);
        }
        saveBatch(rules);
    }
}
```

- [ ] **步骤 3：编译确认无误**

运行：`mvn -pl ruoyi-openliststrm -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/IRenameCategoryRulePlusService.java" \
        "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/impl/RenameCategoryRulePlusServiceImpl.java"
git commit -m "feat: 新增 IRenameCategoryRulePlusService，支持按media_type整体替换规则"
```

---

### 任务 8：`RenameCategoryRuleRestController`

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/RenameCategoryRuleRestController.java`

- [ ] **步骤 1：创建 Controller**

```java
package com.ruoyi.openliststrm.controller.api;

import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameCategoryRulePlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameCategoryRulePlusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 重命名分类规则配置 REST API 控制器
 *
 * @author Jack
 * @date 2026-07-20
 */
@RestController
@RequestMapping("/api/openliststrm/rename-category-rules")
public class RenameCategoryRuleRestController {

    @Autowired
    private IRenameCategoryRulePlusService categoryRuleService;

    /**
     * 查询某个 media_type 下的当前规则列表（按 seq 升序）
     */
    @GetMapping
    public Result<List<RenameCategoryRulePlus>> list(@RequestParam("mediaType") String mediaType) {
        if (StringUtils.isEmpty(mediaType)) {
            return Result.error("mediaType不能为空");
        }
        return Result.success(categoryRuleService.listEnabledRules(mediaType));
    }

    /**
     * 整体替换某个 media_type 下的全部规则（前端一次性提交整份有序列表）
     */
    @PutMapping("/{mediaType}")
    public Result<Void> replaceAll(@PathVariable("mediaType") String mediaType, @RequestBody List<RenameCategoryRulePlus> rules) {
        try {
            categoryRuleService.replaceAll(mediaType, rules);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
```

- [ ] **步骤 2：编译确认无误**

运行：`mvn -pl ruoyi-openliststrm -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/RenameCategoryRuleRestController.java"
git commit -m "feat: 新增重命名分类规则配置 REST API"
```

---

### 任务 9：`IRenameTemplateConfigService` + 实现（TDD）

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/config/IRenameTemplateConfigService.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/config/RenameTemplateConfigServiceImpl.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/config/RenameTemplateConfigServiceImplTest.java`

- [ ] **步骤 1：先创建接口（测试需要引用其中的常量）**

```java
package com.ruoyi.openliststrm.rename.config;

/**
 * 重命名文件名模板配置：读取/校验/保存，统一了原来分散在
 * MediaRenameProcessor 和 RenameTaskRestController 里的两份重复常量。
 */
public interface IRenameTemplateConfigService {

    String CONFIG_KEY = "rename.filename.template";

    String DEFAULT_TEMPLATE = "{{ title }} {% if year %} ({{ year }}) {% endif %}/{% if season %}Season {{ season }}/{% endif %}{{ title }} {% if year and not season %} ({{ year }}) {% endif %}{% if season %}S{{ season }}{% endif %}{% if episode %}E{{ episode }}{% endif %}{% if resolution %} - {{ resolution }}{% endif %}{% if source %}.{{ source }}{% endif %}{% if videoCodec %}.{{ videoCodec }}{% endif %}{% if audioCodec %}.{{ audioCodec }}{% endif %}{% if tags is not empty %}.{{ tags|join('.') }}{% endif %}{% if releaseGroup %}-{{ releaseGroup }}{% endif %}.{{ extension }}";

    /**
     * 获取当前生效的模板：优先读 sys_config，取不到时 fallback 到 DEFAULT_TEMPLATE
     */
    String getTemplate();

    /**
     * 用内置示例 MediaInfo 试渲染模板，不落库。渲染失败抛 IllegalArgumentException。
     * 供前端"实时预览"高频调用，不走 TMDb，纯本地渲染。
     */
    String previewRender(String template);

    /**
     * 校验（复用 previewRender）+ 保存到 sys_config + 刷新缓存。
     * 校验失败抛 IllegalArgumentException，不写库。
     */
    void saveTemplate(String template);
}
```

- [ ] **步骤 2：编写失败的测试**

```java
package com.ruoyi.openliststrm.rename.config;

import com.ruoyi.system.domain.SysConfig;
import com.ruoyi.system.service.ISysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RenameTemplateConfigServiceImplTest {

    @Mock
    private ISysConfigService sysConfigService;

    private RenameTemplateConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RenameTemplateConfigServiceImpl();
        service.sysConfigService = sysConfigService;
    }

    @Test
    void getTemplate_数据库中有配置_返回配置值() {
        when(sysConfigService.selectConfigByKey(IRenameTemplateConfigService.CONFIG_KEY))
                .thenReturn("{{ title }}.{{ extension }}");
        assertEquals("{{ title }}.{{ extension }}", service.getTemplate());
    }

    @Test
    void getTemplate_数据库中没有配置_返回内置默认模板() {
        when(sysConfigService.selectConfigByKey(IRenameTemplateConfigService.CONFIG_KEY)).thenReturn("");
        assertEquals(IRenameTemplateConfigService.DEFAULT_TEMPLATE, service.getTemplate());
    }

    @Test
    void previewRender_合法模板_返回渲染结果() {
        String result = service.previewRender("{{ title }}.{{ extension }}");
        assertTrue(result.contains("."));
    }

    @Test
    void previewRender_模板语法错误_抛IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.previewRender("{{ title "));
    }

    @Test
    void saveTemplate_模板非法_不写入配置直接抛异常() {
        assertThrows(IllegalArgumentException.class, () -> service.saveTemplate("{{ title "));
        verify(sysConfigService, never()).insertConfig(any());
        verify(sysConfigService, never()).updateConfig(any());
    }

    @Test
    void saveTemplate_配置不存在_新增配置并刷新缓存() {
        when(sysConfigService.selectConfigList(any())).thenReturn(Collections.emptyList());
        service.saveTemplate("{{ title }}.{{ extension }}");
        verify(sysConfigService).insertConfig(any());
        verify(sysConfigService).resetConfigCache();
    }

    @Test
    void saveTemplate_配置已存在_更新配置并刷新缓存() {
        SysConfig existing = new SysConfig();
        existing.setConfigId(100L);
        existing.setConfigKey(IRenameTemplateConfigService.CONFIG_KEY);
        when(sysConfigService.selectConfigList(any())).thenReturn(List.of(existing));
        service.saveTemplate("{{ title }}.{{ extension }}");
        verify(sysConfigService).updateConfig(existing);
        verify(sysConfigService).resetConfigCache();
    }
}
```

- [ ] **步骤 3：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test-compile -Dtest=RenameTemplateConfigServiceImplTest`
预期：编译失败，报错 "cannot find symbol: class RenameTemplateConfigServiceImpl"

- [ ] **步骤 4：编写最少实现代码**

```java
package com.ruoyi.openliststrm.rename.config;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import com.ruoyi.openliststrm.rename.render.PebbleRenderer;
import com.ruoyi.system.domain.SysConfig;
import com.ruoyi.system.service.ISysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

@Service
public class RenameTemplateConfigServiceImpl implements IRenameTemplateConfigService {

    @Autowired
    ISysConfigService sysConfigService;

    private final PebbleRenderer renderer = new PebbleRenderer();

    @Override
    public String getTemplate() {
        String value = sysConfigService.selectConfigByKey(CONFIG_KEY);
        return StringUtils.isNotBlank(value) ? value : DEFAULT_TEMPLATE;
    }

    @Override
    public String previewRender(String template) {
        try {
            return renderer.render(buildSampleMediaInfo(), template);
        } catch (Exception e) {
            throw new IllegalArgumentException("模板渲染失败：" + e.getMessage());
        }
    }

    @Override
    public void saveTemplate(String template) {
        previewRender(template);
        Optional<SysConfig> existing = findExisting();
        if (existing.isPresent()) {
            SysConfig config = existing.get();
            config.setConfigValue(template);
            sysConfigService.updateConfig(config);
        } else {
            SysConfig config = new SysConfig();
            config.setConfigName("重命名文件名模板");
            config.setConfigKey(CONFIG_KEY);
            config.setConfigValue(template);
            config.setConfigType("N");
            sysConfigService.insertConfig(config);
        }
        sysConfigService.resetConfigCache();
    }

    private Optional<SysConfig> findExisting() {
        SysConfig query = new SysConfig();
        query.setConfigKey(CONFIG_KEY);
        return sysConfigService.selectConfigList(query).stream()
                .filter(c -> CONFIG_KEY.equals(c.getConfigKey()))
                .findFirst();
    }

    private MediaInfo buildSampleMediaInfo() {
        MediaInfo info = new MediaInfo("sample.mkv");
        info.setTitle("示例电影");
        info.setYear("2026");
        info.setSeason("1");
        info.setEpisode("3");
        info.setResolution("1080p");
        info.setSource("WEB-DL");
        info.setVideoCodec("H265");
        info.setAudioCodec("AAC");
        info.setTags(Arrays.asList("HDR"));
        info.setReleaseGroup("EXAMPLE");
        info.setExtension("mkv");
        return info;
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=RenameTemplateConfigServiceImplTest`
预期：PASS，7 个测试全部通过

- [ ] **步骤 6：Commit**

```bash
git add "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/config/IRenameTemplateConfigService.java" \
        "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/config/RenameTemplateConfigServiceImpl.java" \
        "ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/config/RenameTemplateConfigServiceImplTest.java"
git commit -m "feat: 新增重命名文件名模板配置服务 IRenameTemplateConfigService"
```

---

### 任务 10：`RenameTemplateConfigRestController`

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/RenameTemplateConfigRestController.java`

- [ ] **步骤 1：创建 Controller**

```java
package com.ruoyi.openliststrm.controller.api;

import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.rename.config.IRenameTemplateConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 重命名文件名模板配置 REST API 控制器
 *
 * @author Jack
 * @date 2026-07-20
 */
@RestController
@RequestMapping("/api/openliststrm/rename-config")
public class RenameTemplateConfigRestController {

    @Autowired
    private IRenameTemplateConfigService templateConfigService;

    /**
     * 获取当前生效的文件名模板
     */
    @GetMapping("/template")
    public Result<Map<String, String>> getTemplate() {
        Map<String, String> data = new HashMap<>();
        data.put("template", templateConfigService.getTemplate());
        return Result.success(data);
    }

    /**
     * 试渲染预览（不落库），供页面实时预览
     */
    @PostMapping("/template/preview")
    public Result<String> preview(@RequestBody Map<String, String> body) {
        String template = body.get("template");
        if (StringUtils.isEmpty(template)) {
            return Result.error("模板不能为空");
        }
        try {
            return Result.success(templateConfigService.previewRender(template));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 保存模板（校验通过才写库）
     */
    @PutMapping("/template")
    public Result<Void> updateTemplate(@RequestBody Map<String, String> body) {
        String template = body.get("template");
        if (StringUtils.isEmpty(template)) {
            return Result.error("模板不能为空");
        }
        try {
            templateConfigService.saveTemplate(template);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
```

- [ ] **步骤 2：编译确认无误**

运行：`mvn -pl ruoyi-openliststrm -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/RenameTemplateConfigRestController.java"
git commit -m "feat: 新增重命名文件名模板配置 REST API"
```

---

### 任务 11：改造 `MediaRenameProcessor`

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/monitor/processor/MediaRenameProcessor.java`

- [ ] **步骤 1：替换 import 块**

将文件顶部（第3-17行）的 import 列表替换为：

```java
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.helper.OpenListHelper;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameCategoryRulePlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameCategoryRulePlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameTaskPlusService;
import com.ruoyi.openliststrm.rename.MediaParser;
import com.ruoyi.openliststrm.rename.RenameClientProvider;
import com.ruoyi.openliststrm.rename.RenameEventListener;
import com.ruoyi.openliststrm.rename.config.IRenameTemplateConfigService;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import com.ruoyi.openliststrm.rename.rule.CategoryClassifier;
import com.ruoyi.openliststrm.rename.rule.CategoryRuleConverter;
import com.ruoyi.openliststrm.scrape.ScrapeService;
import lombok.extern.slf4j.Slf4j;
```

（去掉原来的 `import com.ruoyi.openliststrm.rename.CategoryRule;`，因为改造后本文件不再直接写出 `CategoryRule` 这个类型名）

- [ ] **步骤 2：移除静态 rules 字段和 DEFAULT_FILENAME_TEMPLATE 常量**

删除原第49-50行：

```java
    private static final Map<String, List<CategoryRule>> rules = defaultRules();
    private static final String DEFAULT_FILENAME_TEMPLATE = "{{ title }} {% if year %} ({{ year }}) {% endif %}/{% if season %}Season {{ season }}/{% endif %}{{ title }} {% if year and not season %} ({{ year }}) {% endif %}{% if season %}S{{ season }}{% endif %}{% if episode %}E{{ episode }}{% endif %}{% if resolution %} - {{ resolution }}{% endif %}{% if source %}.{{ source }}{% endif %}{% if videoCodec %}.{{ videoCodec }}{% endif %}{% if audioCodec %}.{{ audioCodec }}{% endif %}{% if tags is not empty %}.{{ tags|join('.') }}{% endif %}{% if releaseGroup %}-{{ releaseGroup }}{% endif %}.{{ extension }}";
```

- [ ] **步骤 3：新增两个实例字段并在构造函数里注入**

在 `private final IRenameTaskPlusService taskService;` 后面新增：

```java
    private final IRenameCategoryRulePlusService categoryRuleService;
    private final IRenameTemplateConfigService templateConfigService;
```

在构造函数里 `this.taskService = SpringUtils.getBean(IRenameTaskPlusService.class);` 之后新增：

```java
        this.categoryRuleService = SpringUtils.getBean(IRenameCategoryRulePlusService.class);
        this.templateConfigService = SpringUtils.getBean(IRenameTemplateConfigService.class);
```

- [ ] **步骤 4：`buildDestPath` 改为从模板配置服务取模板**

将：

```java
        String rendered = parser.render(info, DEFAULT_FILENAME_TEMPLATE);
```

改为：

```java
        String rendered = parser.render(info, templateConfigService.getTemplate());
```

- [ ] **步骤 5：`classify` 改为从分类规则服务取规则并委托给 CategoryClassifier**

将：

```java
    private String classify(MediaInfo info, String mediaType) {
        List<CategoryRule> list = rules.getOrDefault(mediaType, Collections.emptyList());
        for (CategoryRule r : list) {
            if (r.matches(info)) return r.getName();
        }
        return null;
    }

    private static Map<String, List<CategoryRule>> defaultRules() {
        Map<String, List<CategoryRule>> m = new HashMap<>();
        // movie rules in order
        List<CategoryRule> movie = new ArrayList<>();
        movie.add(new CategoryRule("动画电影").withGenreIds("16"));
        movie.add(new CategoryRule("华语电影").withOriginalLanguage("zh", "cn", "bo", "za"));
        movie.add(new CategoryRule("外语电影")); // fallback if not matched above
        m.put("movie", movie);

        // tv rules
        List<CategoryRule> tv = new ArrayList<>();
        tv.add(new CategoryRule("国漫").withGenreIds("16").withOriginCountry("CN", "TW", "HK"));
        tv.add(new CategoryRule("日番").withGenreIds("16").withOriginCountry("JP"));
        tv.add(new CategoryRule("纪录片").withGenreIds("99"));
        tv.add(new CategoryRule("儿童").withGenreIds("10762"));
        tv.add(new CategoryRule("综艺").withGenreIds("10764", "10767"));
        tv.add(new CategoryRule("国产剧").withOriginCountry("CN", "TW", "HK"));
        tv.add(new CategoryRule("欧美剧").withOriginCountry("US", "FR", "GB", "DE", "ES", "IT", "NL", "PT", "RU", "UK"));
        tv.add(new CategoryRule("日韩剧").withOriginCountry("JP", "KP", "KR", "TH", "IN", "SG"));
        tv.add(new CategoryRule("未分类"));
        m.put("tv", tv);

        return Collections.unmodifiableMap(m);
    }

}
```

改为：

```java
    private String classify(MediaInfo info, String mediaType) {
        List<RenameCategoryRulePlus> rows = categoryRuleService.listEnabledRules(mediaType);
        if (rows.isEmpty()) {
            log.warn("未配置 mediaType={} 的分类规则，使用兜底目录", mediaType);
            return "未分类";
        }
        String category = CategoryClassifier.classify(CategoryRuleConverter.toCategoryRules(rows), info);
        return category != null ? category : "未分类";
    }

}
```

- [ ] **步骤 6：编译确认无误**

运行：`mvn -pl ruoyi-openliststrm -am compile`
预期：BUILD SUCCESS（注意确认没有遗留对 `CategoryRule`/`defaultRules`/`DEFAULT_FILENAME_TEMPLATE` 的引用报错）

- [ ] **步骤 7：Commit**

```bash
git add "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/monitor/processor/MediaRenameProcessor.java"
git commit -m "refactor: MediaRenameProcessor改为从数据库读取分类规则与文件名模板"
```

---

### 任务 12：改造 `RenameTaskRestController`

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/RenameTaskRestController.java`

- [ ] **步骤 1：新增 import 并移除重复常量**

在 import 块里新增：

```java
import com.ruoyi.openliststrm.rename.config.IRenameTemplateConfigService;
```

删除原第29行：

```java
    private static final String DEFAULT_FILENAME_TEMPLATE = "{{ title }} {% if year %} ({{ year }}) {% endif %}/{% if season %}Season {{ season }}/{% endif %}{{ title }} {% if year and not season %} ({{ year }}) {% endif %}{% if season %}S{{ season }}{% endif %}{% if episode %}E{{ episode }}{% endif %}{% if resolution %} - {{ resolution }}{% endif %}{% if source %}.{{ source }}{% endif %}{% if videoCodec %}.{{ videoCodec }}{% endif %}{% if audioCodec %}.{{ audioCodec }}{% endif %}{% if tags is not empty %}.{{ tags|join('.') }}{% endif %}{% if releaseGroup %}-{{ releaseGroup }}{% endif %}.{{ extension }}";
```

- [ ] **步骤 2：新增字段注入**

在 `@Autowired private OpenlistConfig config;` 后面新增：

```java
    @Autowired
    private IRenameTemplateConfigService templateConfigService;
```

- [ ] **步骤 3：替换 `test` 方法里的模板取值**

将：

```java
            String renderTemplate = StringUtils.isEmpty(template) ? DEFAULT_FILENAME_TEMPLATE : template;
```

（`test` 方法内，第127行）改为：

```java
            String renderTemplate = StringUtils.isEmpty(template) ? templateConfigService.getTemplate() : template;
```

- [ ] **步骤 4：替换 `testParse` 方法里的模板取值**

将同样一行（`testParse` 方法内，第164行）也改为：

```java
            String renderTemplate = StringUtils.isEmpty(template) ? templateConfigService.getTemplate() : template;
```

- [ ] **步骤 5：编译确认无误**

运行：`mvn -pl ruoyi-openliststrm -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 6：Commit**

```bash
git add "ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/RenameTaskRestController.java"
git commit -m "refactor: RenameTaskRestController改用统一的模板配置服务，去除重复常量"
```

---

### 任务 13：全量运行后端测试

- [ ] **步骤 1：运行整个 ruoyi-openliststrm 模块的测试**

运行：`mvn -pl ruoyi-openliststrm -am test`
预期：BUILD SUCCESS，新增的 `CategoryRuleValidatorTest`/`CategoryRuleConverterTest`/`CategoryClassifierTest`/`RenameTemplateConfigServiceImplTest` 和既有的 `RenameOrphanScanServiceImplTest` 等全部通过，无回归失败

如有失败，定位失败用例，修复后重新运行本命令，不要跳过。

---

### 任务 14：前端 API 封装

**文件：**
- 创建：`openlist-web/src/api/openlist/renameConfig.ts`

- [ ] **步骤 1：创建 API 文件**

```typescript
import request from '@/api/request'

export interface CategoryRule {
  id?: number
  mediaType: string
  seq?: number
  genreIds?: string
  originalLanguages?: string
  originCountries?: string
  targetDir: string
  isFallback: string
}

export function getRenameTemplateApi() {
  return request.get<any, { template: string }>('/openliststrm/rename-config/template')
}

export function previewRenameTemplateApi(template: string) {
  return request.post<any, string>('/openliststrm/rename-config/template/preview', { template })
}

export function updateRenameTemplateApi(template: string) {
  return request.put('/openliststrm/rename-config/template', { template })
}

export function getCategoryRulesApi(mediaType: string) {
  return request.get<any, CategoryRule[]>('/openliststrm/rename-category-rules', { params: { mediaType } })
}

export function saveCategoryRulesApi(mediaType: string, rules: CategoryRule[]) {
  return request.put(`/openliststrm/rename-category-rules/${mediaType}`, rules)
}
```

- [ ] **步骤 2：Commit**

```bash
git add "openlist-web/src/api/openlist/renameConfig.ts"
git commit -m "feat: 新增重命名规则配置前端API封装"
```

---

### 任务 15：前端 composable

**文件：**
- 创建：`openlist-web/src/composables/useRenameConfig.ts`

- [ ] **步骤 1：创建 composable**

```typescript
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getRenameTemplateApi,
  previewRenameTemplateApi,
  updateRenameTemplateApi,
  getCategoryRulesApi,
  saveCategoryRulesApi,
  type CategoryRule
} from '@/api/openlist/renameConfig'

/** 模板里可用的变量参考，点击插入到文本框光标处 */
export const TEMPLATE_VARIABLES = [
  'title', 'year', 'season', 'episode', 'resolution',
  'source', 'videoCodec', 'audioCodec', 'tags', 'releaseGroup', 'extension'
]

export function useRenameConfig() {
  // ---- 文件名模板 ----
  const template = ref('')
  const templateLoading = ref(false)
  const templateSaving = ref(false)
  const previewResult = ref('')
  const previewError = ref('')

  const loadTemplate = async () => {
    templateLoading.value = true
    try {
      const data = await getRenameTemplateApi() as any
      template.value = data.template
      await doPreview()
    } catch (e) {
      console.error('[重命名规则设置] 加载模板失败:', e)
      ElMessage.error('加载模板失败')
    } finally {
      templateLoading.value = false
    }
  }

  let previewTimer: ReturnType<typeof setTimeout> | undefined
  const doPreview = async () => {
    if (previewTimer) clearTimeout(previewTimer)
    return new Promise<void>((resolve) => {
      previewTimer = setTimeout(async () => {
        try {
          previewResult.value = await previewRenameTemplateApi(template.value) as any
          previewError.value = ''
        } catch (e: any) {
          previewResult.value = ''
          previewError.value = e?.message || '预览失败'
        }
        resolve()
      }, 300)
    })
  }

  const saveTemplate = async () => {
    templateSaving.value = true
    try {
      await updateRenameTemplateApi(template.value)
      ElMessage.success('模板保存成功')
    } catch (e) {
      // 具体错误文案（如"模板渲染失败：..."）已经由 request.ts 的响应拦截器统一 toast 过了，这里不重复弹一次
      console.error('[重命名规则设置] 保存模板失败:', e)
    } finally {
      templateSaving.value = false
    }
  }

  // ---- 分类规则 ----
  const movieRules = ref<CategoryRule[]>([])
  const tvRules = ref<CategoryRule[]>([])
  const rulesLoading = ref(false)
  const rulesSaving = ref(false)

  const loadRules = async () => {
    rulesLoading.value = true
    try {
      movieRules.value = await getCategoryRulesApi('movie') as any
      tvRules.value = await getCategoryRulesApi('tv') as any
    } catch (e) {
      console.error('[重命名规则设置] 加载分类规则失败:', e)
      ElMessage.error('加载分类规则失败')
    } finally {
      rulesLoading.value = false
    }
  }

  const listRef = (mediaType: string) => (mediaType === 'movie' ? movieRules : tvRules)

  const addRule = (mediaType: string) => {
    const list = listRef(mediaType)
    const insertIndex = Math.max(list.value.length - 1, 0)
    list.value.splice(insertIndex, 0, {
      mediaType,
      targetDir: '',
      genreIds: '',
      originalLanguages: '',
      originCountries: '',
      isFallback: '0'
    })
  }

  const removeRule = (mediaType: string, index: number) => {
    const list = listRef(mediaType)
    if (list.value[index]?.isFallback === '1') return
    list.value.splice(index, 1)
  }

  const moveRule = (mediaType: string, index: number, direction: -1 | 1) => {
    const list = listRef(mediaType)
    const target = index + direction
    if (target < 0 || target >= list.value.length) return
    // 兜底行必须保持最后一位，禁止把它移走、也禁止把别的行移到它后面
    if (list.value[index].isFallback === '1' || list.value[target].isFallback === '1') return
    const arr = list.value
    ;[arr[index], arr[target]] = [arr[target], arr[index]]
  }

  const saveRules = async (mediaType: string) => {
    rulesSaving.value = true
    try {
      await saveCategoryRulesApi(mediaType, listRef(mediaType).value)
      ElMessage.success('分类规则保存成功')
      await loadRules()
    } catch (e) {
      // 具体错误文案（如"必须保留且只能保留一条兜底规则"）已经由 request.ts 的响应拦截器统一 toast 过了，这里不重复弹一次
      console.error('[重命名规则设置] 保存分类规则失败:', e)
    } finally {
      rulesSaving.value = false
    }
  }

  loadTemplate()
  loadRules()

  return {
    template, templateLoading, templateSaving, previewResult, previewError,
    doPreview, saveTemplate,
    movieRules, tvRules, rulesLoading, rulesSaving,
    addRule, removeRule, moveRule, saveRules
  }
}
```

- [ ] **步骤 2：Commit**

```bash
git add "openlist-web/src/composables/useRenameConfig.ts"
git commit -m "feat: 新增重命名规则配置 composable"
```

---

### 任务 16：前端页面 + 路由注册

**文件：**
- 创建：`openlist-web/src/views/openlist/renameConfig/RuleTable.vue` — 规则行编辑表格，电影/剧集两个列表复用同一个组件
- 创建：`openlist-web/src/views/openlist/renameConfig/index.vue` — 页面主体（两个 Tab）
- 修改：`openlist-web/src/router/index.ts`

说明：规则表格拆成独立的 `.vue` 组件文件，而不是在 `index.vue` 里用 `h()` 渲染函数内联定义——本项目所有组件都是 `<script setup>` + `<template>` 的声明式写法（例如 `renameTask/index.vue` 引入 `DirectoryTreeSelect` 的方式），用 `h('el-table', ...)` 这种裸字符串标签的渲染函数并不会被 Vue 解析成 Element Plus 的全局注册组件（那只在模板编译器生成的 `resolveComponent` 调用里才会生效），必然渲染不出预期效果，所以改为和现有代码一致的正规子组件写法。

- [ ] **步骤 1：创建规则表格子组件**

```vue
<template>
  <div>
    <el-table :data="rules" size="small" style="width:100%">
      <el-table-column label="目标目录名" min-width="140">
        <template #default="{ row }">
          <el-input
            v-model="row.targetDir"
            :disabled="row.isFallback === '1'"
            :placeholder="row.isFallback === '1' ? '兜底目录' : '目录名'"
          />
        </template>
      </el-table-column>
      <el-table-column label="Genre IDs（逗号分隔）" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.genreIds" :disabled="row.isFallback === '1'" placeholder="不限" />
        </template>
      </el-table-column>
      <el-table-column label="原始语言（逗号分隔）" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.originalLanguages" :disabled="row.isFallback === '1'" placeholder="不限" />
        </template>
      </el-table-column>
      <el-table-column label="国家/地区（逗号分隔）" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.originCountries" :disabled="row.isFallback === '1'" placeholder="不限" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row, $index }">
          <el-button link :disabled="row.isFallback === '1'" @click="$emit('move', mediaType, $index, -1)">上移</el-button>
          <el-button link :disabled="row.isFallback === '1'" @click="$emit('move', mediaType, $index, 1)">下移</el-button>
          <el-button link type="danger" :disabled="row.isFallback === '1'" @click="$emit('remove', mediaType, $index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top:8px">
      <el-button @click="$emit('add', mediaType)">+ 新增规则</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { CategoryRule } from '@/api/openlist/renameConfig'

defineProps<{
  rules: CategoryRule[]
  mediaType: string
}>()

defineEmits<{
  add: [mediaType: string]
  remove: [mediaType: string, index: number]
  move: [mediaType: string, index: number, direction: -1 | 1]
}>()
</script>
```

- [ ] **步骤 2：创建页面主体**

```vue
<template>
  <div class="page-container">
    <el-card class="table-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="文件名模板" name="template">
          <div v-loading="templateLoading" class="template-tab">
            <div class="template-editor">
              <el-input
                ref="templateInputRef"
                v-model="template"
                type="textarea"
                :rows="6"
                placeholder="Pebble 语法，例如 {{ title }} ({{ year }}).{{ extension }}"
                @input="doPreview"
              />
              <div class="template-actions">
                <el-button type="primary" :loading="templateSaving" @click="saveTemplate">保存模板</el-button>
              </div>
              <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" style="margin-top:12px" />
              <el-alert v-else :title="previewResult || '（预览为空）'" type="success" :closable="false" style="margin-top:12px">
                <div style="font-family:Consolas,monospace;word-break:break-all;white-space:pre-wrap">{{ previewResult }}</div>
              </el-alert>
            </div>
            <div class="template-variables">
              <div class="variables-title">可用变量（点击插入）</div>
              <el-tag
                v-for="v in TEMPLATE_VARIABLES"
                :key="v"
                class="variable-tag"
                @click="insertVariable(v)"
              >{{ v }}</el-tag>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="分类规则" name="rules">
          <div v-loading="rulesLoading">
            <el-divider content-position="left">电影</el-divider>
            <RuleTable :rules="movieRules" media-type="movie"
              @add="addRule" @remove="removeRule" @move="moveRule" />
            <div class="rules-actions">
              <el-button type="primary" :loading="rulesSaving" @click="saveRules('movie')">保存电影分类规则</el-button>
            </div>

            <el-divider content-position="left">剧集</el-divider>
            <RuleTable :rules="tvRules" media-type="tv"
              @add="addRule" @remove="removeRule" @move="moveRule" />
            <div class="rules-actions">
              <el-button type="primary" :loading="rulesSaving" @click="saveRules('tv')">保存剧集分类规则</el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import RuleTable from './RuleTable.vue'
import { useRenameConfig, TEMPLATE_VARIABLES } from '@/composables/useRenameConfig'

const activeTab = ref('template')
const templateInputRef = ref()

const {
  template, templateLoading, templateSaving, previewResult, previewError,
  doPreview, saveTemplate,
  movieRules, tvRules, rulesLoading, rulesSaving,
  addRule, removeRule, moveRule, saveRules
} = useRenameConfig()

/**
 * 插入到光标位置而不是简单追加到末尾：ElInput(textarea) 把底层 <textarea> DOM
 * 暴露在组件实例的 .textarea 上，取不到时（理论上不会发生）退化为追加到末尾。
 */
const insertVariable = (varName: string) => {
  const snippet = `{{ ${varName} }}`
  const textarea: HTMLTextAreaElement | undefined = templateInputRef.value?.textarea
  if (!textarea) {
    template.value += snippet
    doPreview()
    return
  }
  const start = textarea.selectionStart ?? template.value.length
  const end = textarea.selectionEnd ?? template.value.length
  template.value = template.value.slice(0, start) + snippet + template.value.slice(end)
  doPreview()
  nextTick(() => {
    const cursor = start + snippet.length
    textarea.focus()
    textarea.setSelectionRange(cursor, cursor)
  })
}
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);
}

.template-tab {
  display: flex;
  gap: 20px;

  .template-editor {
    flex: 1;
    min-width: 0;
  }

  .template-variables {
    width: 220px;
    flex-shrink: 0;

    .variables-title {
      font-size: 13px;
      color: var(--osr-text-secondary);
      margin-bottom: 8px;
    }

    .variable-tag {
      margin: 0 6px 6px 0;
      cursor: pointer;
    }
  }
}

.template-actions,
.rules-actions {
  margin-top: 12px;
}
</style>
```

- [ ] **步骤 3：在路由的 componentMap 里注册新页面**

在 `openlist-web/src/router/index.ts` 的 `componentMap` 对象里，`'openlist/renameOrphan/index': createDeviceView(...)` 这一项之后新增（不使用 `createDeviceView`，因为本页面只做 PC 端，与 `system/config/index` 一致）：

```typescript
  'openlist/renameConfig/index': () => import('@/views/openlist/renameConfig/index.vue')
```

- [ ] **步骤 4：Commit**

```bash
git add "openlist-web/src/views/openlist/renameConfig/RuleTable.vue" \
        "openlist-web/src/views/openlist/renameConfig/index.vue" \
        "openlist-web/src/router/index.ts"
git commit -m "feat: 新增重命名规则设置页面并注册路由"
```

---

### 任务 17：前端类型检查 + 手动浏览器验证 + 完整回归

- [ ] **步骤 1：前端类型检查**

运行：`cd openlist-web && npm run build`
预期：`vue-tsc` 类型检查通过，构建成功。如有类型错误，修复后重新运行。

- [ ] **步骤 2：启动前后端，手动验证模板 Tab**

启动后端（`mvn -pl ruoyi-admin -am spring-boot:run` 或按项目既有方式）和前端 `npm run dev`，登录后台，进入"OpenListStrm -> 重命名规则设置"页面：
- 确认默认加载出的模板与原 `DEFAULT_FILENAME_TEMPLATE` 一致，且下方预览区显示出对应的示例渲染结果
- 点击右侧变量标签，确认能精确插入到文本框光标位置（先点一下文本框中间某处定位光标，再点变量标签），且预览随之刷新（防抖300ms）
- 故意输入一个语法错误的模板（例如把 `{{` 换成 `{%` 但不闭合），确认预览区显示报错而不是崩溃
  - 已知的可接受权衡：预览接口失败时，全局 `request.ts` 响应拦截器也会弹一次通用错误 toast（它对所有接口的非 200 响应都会弹），编辑中途输入到一半的模板短暂无效时可能连带弹出——这不是本次改造引入的新问题（拦截器本身就是全量生效的既有行为），本计划不为此单独改造全局请求层，验证时确认这不影响预览区本身正确显示报错内容即可
- 修正模板并点击"保存模板"，确认提示保存成功；刷新页面确认新模板被正确加载出来

- [ ] **步骤 3：手动验证分类规则 Tab**

- 确认电影/剧集两个列表默认展示出种子数据的 3 条 / 9 条规则，且顺序、目录名与原硬编码规则一致
- 尝试删除兜底行（最后一行）：确认删除按钮被禁用，无法删除
- 尝试上移兜底行：确认上移按钮被禁用
- 新增一条规则，留空目标目录名直接保存：确认后端拒绝保存并展示报错信息（如"目标目录名不能为空"）
- 新增合法规则、调整顺序后保存：确认提示保存成功，刷新页面后顺序与内容都被正确持久化

- [ ] **步骤 4：完整回归——跑一遍现有重命名任务流程**

在"重命名任务配置"页面选一个已有源目录，触发一次执行（或往监控目录里放一个测试视频文件），确认：
- 文件按照当前配置的模板正确重命名
- 文件被放入按当前分类规则计算出的目录（比如放一个 genre=动画、country=CN 的测试文件，确认落在"国漫"目录而不是别的）
- 刮削流程未受影响，仍能正常触发

如与预期不符，回到对应任务的代码定位问题，修复后重新走一遍本任务的步骤2-4。

---

## 完成后

全部任务完成、测试通过、手动验证通过后，参照 `finishing-a-development-branch` 技能决定后续如何收尾（合并/PR/清理）。
