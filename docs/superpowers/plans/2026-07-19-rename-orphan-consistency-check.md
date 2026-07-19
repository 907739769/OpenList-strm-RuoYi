# 重命名 STRM 一致性检查（孤儿清理）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 扫描 `rename_detail` 中已重命名的 `.strm` 文件，检测两类不一致状态——本地文件被人工删除（`local_missing`）、本地文件仍在但网盘源已被删除（`source_missing`）——生成待清理列表供人工确认，确认后清理残留的 `.strm`/NFO/图片文件及数据库记录。

**架构：** 新增 `rename_orphan` 表落待清理项；新增 `orphan` 功能域子包承载扫描/清理业务逻辑（`StrmSourcePathResolver` 解析 `.strm` 内容还原网盘源路径、`OrphanReconciler` 做纯函数式的"发现/更新/自动恢复/忽略跳过"决策、`RenameOrphanScanServiceImpl` 编排 I/O）；标准 MyBatis-Plus 三件套 + REST Controller + Quartz 定时任务对外暴露；前端新增独立页面 + composable + API 封装，复用现有 `useRecordList` 基础设施。

**技术栈：** Java 25 (Spring Boot 4.0.6, --enable-preview) + MyBatis-Plus + JUnit 5/Mockito（本次为 `ruoyi-openliststrm` 模块首次引入测试依赖）+ Vue 3 + TypeScript + Element Plus。

---

## 文件结构

**新增（后端）：**
- `ruoyi-common/src/main/resources/sql/20260719-rename-orphan.sql` — 建表 + sys_job + sys_menu
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/RenameOrphanPlus.java` — 实体
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/mapper/RenameOrphanPlusMapper.java` — Mapper
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/IRenameOrphanPlusService.java` — CRUD Service 接口
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/impl/RenameOrphanPlusServiceImpl.java` — CRUD Service 实现
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/StrmSourcePathResolver.java` — 从 `.strm` 文件内容还原网盘源路径的纯函数工具类
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/OrphanReconciler.java` — 「检测结果 vs 已有记录」的纯函数决策逻辑
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/IRenameOrphanScanService.java` — 扫描/清理/忽略业务接口
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/RenameOrphanScanServiceImpl.java` — 扫描/清理/忽略业务实现
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/RenameOrphanRestController.java` — REST API
- `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/orphan/StrmSourcePathResolverTest.java`
- `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/orphan/OrphanReconcilerTest.java`
- `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/orphan/RenameOrphanScanServiceImplTest.java`

**修改（后端）：**
- `ruoyi-openliststrm/pom.xml` — 加 `spring-boot-starter-test`（test scope）
- `ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java` — 注册新迁移脚本
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/task/OpenListStrmTask.java` — 新增 `checkRenameOrphan()` 方法

**新增（前端）：**
- `openlist-web/src/api/openlist/renameOrphan.ts`
- `openlist-web/src/composables/useRenameOrphanList.ts`
- `openlist-web/src/views/openlist/renameOrphan/index.vue`
- `openlist-web/src/views-mobile/renameOrphan/index.vue`

**修改（前端）：**
- `openlist-web/src/router/index.ts` — 注册新页面到 `componentMap` / `KEEP_ALIVE_COMPONENTS`

---

## 范围检查

本功能是单一子系统（重命名产物一致性检查），不需要再拆分子计划。

---

### 任务 0：为 ruoyi-openliststrm 模块引入测试依赖

**背景：** 该模块当前 `src/test` 目录为空，`pom.xml` 未声明任何测试依赖（`spring-boot-starter-test`/JUnit/Mockito 均无）。根 `pom.xml` 已通过 `spring-boot-dependencies` BOM（4.0.6）管理版本，子模块只需声明依赖无需写版本号。这是本模块的第一批单元测试，需要先把依赖装好。

**文件：**
- 修改：`ruoyi-openliststrm/pom.xml`

- [ ] **步骤 1：添加测试依赖**

在 `ruoyi-openliststrm/pom.xml` 的 `<dependencies>` 块末尾（`</dependencies>` 标签之前）加入：

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **步骤 2：验证依赖解析成功**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -pl . -am dependency:resolve -q`（在仓库根目录执行；`-am` 会先构建 `ruoyi-common`/`ruoyi-system`/`ruoyi-framework` 等被依赖模块）

预期：命令成功退出（exit code 0），无 `Could not resolve dependencies` 报错。

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-openliststrm/pom.xml
git commit -m "chore: 为ruoyi-openliststrm模块引入测试依赖"
```

---

### 任务 1：数据库迁移（建表 + 定时任务 + 菜单）

**文件：**
- 创建：`ruoyi-common/src/main/resources/sql/20260719-rename-orphan.sql`
- 修改：`ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java`

- [ ] **步骤 1：编写迁移 SQL**

```sql
-- ----------------------------
-- 20260719: 重命名 STRM 一致性检查（孤儿清理）功能
-- ----------------------------

CREATE TABLE `rename_orphan`  (
                                 `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                                 `detail_id` int(10) UNSIGNED NOT NULL COMMENT '关联rename_detail.id',
                                 `new_path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '重命名后目录',
                                 `new_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '重命名后文件名',
                                 `title` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '标题',
                                 `year` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '年份',
                                 `media_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '媒体类型',
                                 `reason` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '孤儿原因 local_missing-本地文件已删除 source_missing-网盘源已删除',
                                 `status` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态 0-待处理 1-已清理 2-已忽略',
                                 `found_time` datetime(0) NULL DEFAULT NULL COMMENT '发现时间',
                                 `clean_time` datetime(0) NULL DEFAULT NULL COMMENT '清理/忽略时间',
                                 `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
                                 `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
                                 PRIMARY KEY (`id`) USING BTREE,
                                 UNIQUE INDEX `uk_detail_id`(`detail_id`) USING BTREE,
                                 INDEX `idx_status`(`status`) USING BTREE
) COMMENT = '重命名孤儿记录（一致性检查待清理项）';

-- ----------------------------
-- 定时任务：每天一次扫描，避开已有 copy(3点)/rename(2点)/strm(5点) 任务
-- ----------------------------
INSERT IGNORE INTO `sys_job` VALUES (103, 'openliststrm-重命名一致性检查', 'DEFAULT', 'openListStrmTask.checkRenameOrphan()', '0 0 6 * * ?', '3', '1', '1', 'admin', '2026-07-19 00:00:00', '', NULL, '');

-- ----------------------------
-- 菜单：挂在 OpenListStrm(2006) 下，view + list/scan/clean/ignore 四个按钮权限
-- ----------------------------
INSERT INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2055, '重命名一致性检查', 2006, 7, '/openlist/renameOrphan', '', 'C', '0', '1', 'openliststrm:renameOrphan:view', '#', 'admin', '2026-07-19 00:00:00', '', NULL, '重命名STRM一致性检查菜单');
INSERT INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2056, '重命名一致性检查查询', 2055, 1, '#', '', 'F', '0', '1', 'openliststrm:renameOrphan:list', '#', 'admin', '2026-07-19 00:00:00', '', NULL, '');
INSERT INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2057, '重命名一致性检查扫描', 2055, 2, '#', '', 'F', '0', '1', 'openliststrm:renameOrphan:scan', '#', 'admin', '2026-07-19 00:00:00', '', NULL, '');
INSERT INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2058, '重命名一致性检查清理', 2055, 3, '#', '', 'F', '0', '1', 'openliststrm:renameOrphan:clean', '#', 'admin', '2026-07-19 00:00:00', '', NULL, '');
INSERT INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2059, '重命名一致性检查忽略', 2055, 4, '#', '', 'F', '0', '1', 'openliststrm:renameOrphan:ignore', '#', 'admin', '2026-07-19 00:00:00', '', NULL, '');
```

- [ ] **步骤 2：在 MysqlDdl 中注册新脚本**

`ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java` 的 `getSqlFiles()` 是显式列表，新文件不加进去不会被执行。在末尾追加一行：

```java
                "sql/20260718-add-openlist-configs.sql",
                "sql/20260719-rename-orphan.sql"
        );
```

（即把原来末尾的 `"sql/20260718-add-openlist-configs.sql"` 后面加逗号，新增一行 `"sql/20260719-rename-orphan.sql"`，`);` 移到新行末尾）

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-common/src/main/resources/sql/20260719-rename-orphan.sql ruoyi-common/src/main/java/com/ruoyi/common/mybatisplus/MysqlDdl.java
git commit -m "feat: 新增重命名一致性检查数据表/定时任务/菜单"
```

（本步骤不含可自动化验证的运行时行为，留到任务 11 启动后端时一并验证建表成功）

---

### 任务 2：MyBatis-Plus 实体/Mapper/Service 三件套

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/RenameOrphanPlus.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/mapper/RenameOrphanPlusMapper.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/IRenameOrphanPlusService.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/impl/RenameOrphanPlusServiceImpl.java`

这四个文件是标准 MyBatis-Plus 三件套（实体+Mapper+Service接口+Service实现），逻辑上不含可测试的自定义行为，跟随现有 `OpenlistCopyPlus`/`RenameDetailPlus` 的写法，不单独写单元测试（与代码库现状一致——这类纯声明式文件在本代码库中均无对应测试）。

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

import java.util.Date;

/**
 * <p>
 * 重命名孤儿记录（一致性检查待清理项）
 * </p>
 *
 * @author Jack
 * @since 2026-07-19
 */
@Getter
@Setter
@TableName("rename_orphan")
public class RenameOrphanPlus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 自增主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 关联 rename_detail.id
     */
    @TableField("detail_id")
    private Integer detailId;

    /**
     * 重命名后目录
     */
    @TableField("new_path")
    private String newPath;

    /**
     * 重命名后文件名
     */
    @TableField("new_name")
    private String newName;

    /**
     * 标题
     */
    @TableField("title")
    private String title;

    /**
     * 年份
     */
    @TableField("year")
    private String year;

    /**
     * 媒体类型
     */
    @TableField("media_type")
    private String mediaType;

    /**
     * 孤儿原因 local_missing-本地文件已删除 source_missing-网盘源已删除
     */
    @TableField("reason")
    private String reason;

    /**
     * 状态 0-待处理 1-已清理 2-已忽略
     */
    @TableField("status")
    private String status;

    /**
     * 发现时间
     */
    @TableField("found_time")
    private Date foundTime;

    /**
     * 清理/忽略时间
     */
    @TableField("clean_time")
    private Date cleanTime;
}
```

- [ ] **步骤 2：创建 Mapper**

```java
package com.ruoyi.openliststrm.mybatisplus.mapper;

import com.ruoyi.openliststrm.mybatisplus.domain.RenameOrphanPlus;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 重命名孤儿记录 Mapper 接口
 * </p>
 *
 * @author Jack
 * @since 2026-07-19
 */
public interface RenameOrphanPlusMapper extends BaseMapper<RenameOrphanPlus> {

}
```

- [ ] **步骤 3：创建 Service 接口**

```java
package com.ruoyi.openliststrm.mybatisplus.service;

import com.ruoyi.openliststrm.mybatisplus.domain.RenameOrphanPlus;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 重命名孤儿记录 服务类
 * </p>
 *
 * @author Jack
 * @since 2026-07-19
 */
public interface IRenameOrphanPlusService extends IService<RenameOrphanPlus> {

}
```

- [ ] **步骤 4：创建 Service 实现**

```java
package com.ruoyi.openliststrm.mybatisplus.service.impl;

import com.ruoyi.openliststrm.mybatisplus.domain.RenameOrphanPlus;
import com.ruoyi.openliststrm.mybatisplus.mapper.RenameOrphanPlusMapper;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameOrphanPlusService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 重命名孤儿记录 服务实现类
 * </p>
 *
 * @author Jack
 * @since 2026-07-19
 */
@Service
public class RenameOrphanPlusServiceImpl extends ServiceImpl<RenameOrphanPlusMapper, RenameOrphanPlus> implements IRenameOrphanPlusService {

}
```

- [ ] **步骤 5：编译验证**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am compile -q`

预期：BUILD SUCCESS，无编译错误。

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/domain/RenameOrphanPlus.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/mapper/RenameOrphanPlusMapper.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/IRenameOrphanPlusService.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/mybatisplus/service/impl/RenameOrphanPlusServiceImpl.java
git commit -m "feat: 新增RenameOrphanPlus实体/Mapper/Service三件套"
```

---

### 任务 3：`.strm` 内容解析器（TDD）

**背景：** `.strm` 文件内容格式为 `baseUrl + "/d" + encodePath`（见 `StrmServiceImpl.processFileEntry`），`encodePath` 在开启编码时是 `URLEncoder.encode(path, UTF_8).replace("+","%20").replace("%2F","/")` 的结果。本任务写这个变换的逆过程：从文件内容还原网盘源路径。

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/StrmSourcePathResolver.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/orphan/StrmSourcePathResolverTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.ruoyi.openliststrm.orphan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StrmSourcePathResolverTest {

    @Test
    void resolve_未编码内容_直接截取baseUrl和d前缀后的路径() {
        String content = "http://192.168.1.10:5244/d/movies/Inception (2010)/Inception.mkv";
        String result = StrmSourcePathResolver.resolve(content, "http://192.168.1.10:5244", false);
        assertEquals("/movies/Inception (2010)/Inception.mkv", result);
    }

    @Test
    void resolve_已编码内容_还原出原始网盘路径() {
        // 对应源路径 "/movies/盗梦空间 (2010)/盗梦空间 1.mkv"，空格编码为%20，中文按UTF-8百分号编码，"/"保持不变
        String content = "http://192.168.1.10:5244/d/movies/%E7%9B%97%E6%A2%A6%E7%A9%BA%E9%97%B4%20(2010)/%E7%9B%97%E6%A2%A6%E7%A9%BA%E9%97%B4%201.mkv";
        String result = StrmSourcePathResolver.resolve(content, "http://192.168.1.10:5244", true);
        assertEquals("/movies/盗梦空间 (2010)/盗梦空间 1.mkv", result);
    }

    @Test
    void resolve_baseUrl前缀不匹配_返回null() {
        // 用户中途换过OpenList域名，历史.strm文件里的baseUrl跟当前配置对不上
        String content = "http://old-domain.example.com:5244/d/movies/Inception.mkv";
        String result = StrmSourcePathResolver.resolve(content, "http://192.168.1.10:5244", false);
        assertNull(result);
    }

    @Test
    void resolve_内容为空_返回null() {
        assertNull(StrmSourcePathResolver.resolve(null, "http://192.168.1.10:5244", false));
        assertNull(StrmSourcePathResolver.resolve("", "http://192.168.1.10:5244", false));
    }

    @Test
    void resolve_baseUrl为空_返回null() {
        assertNull(StrmSourcePathResolver.resolve("http://192.168.1.10:5244/d/movies/a.mkv", null, false));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=StrmSourcePathResolverTest -q`

预期：FAIL，报错找不到类 `StrmSourcePathResolver`（编译失败）。

- [ ] **步骤 3：编写实现代码**

```java
package com.ruoyi.openliststrm.orphan;

import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 从 .strm 文件内容还原网盘源路径。
 * <p>
 * .strm 内容由 {@code StrmServiceImpl} 写入，格式固定为
 * {@code baseUrl + "/d" + encodePath}；encodePath 在开启编码时是
 * {@code URLEncoder.encode(path, UTF_8).replace("+","%20").replace("%2F","/")} 的结果——
 * 本类做这个变换的逆过程。
 */
public final class StrmSourcePathResolver {

    private StrmSourcePathResolver() {
    }

    /**
     * @param strmContent .strm 文件内容
     * @param baseUrl     当前配置的 OpenList 访问地址（config.getOpenListUrl()）
     * @param encoded     是否按编码规则解码（config.getOpenListStrmEncode()）
     * @return 还原出的网盘源路径；内容为空、baseUrl 为空、或前缀不匹配（比如历史文件用的是旧域名）时返回 null
     */
    public static String resolve(String strmContent, String baseUrl, boolean encoded) {
        if (StringUtils.isBlank(strmContent) || StringUtils.isBlank(baseUrl)) {
            return null;
        }
        String prefix = baseUrl + "/d";
        if (!strmContent.startsWith(prefix)) {
            return null;
        }
        String path = strmContent.substring(prefix.length());
        if (!encoded) {
            return path;
        }
        try {
            return URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=StrmSourcePathResolverTest -q`

预期：PASS，5 个测试全部通过。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/StrmSourcePathResolver.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/orphan/StrmSourcePathResolverTest.java
git commit -m "feat: 新增.strm内容解析网盘源路径的工具类"
```

---

### 任务 4：孤儿判定的纯函数决策逻辑（TDD）

**背景：** 扫描每条 `rename_detail` 记录时，需要把"本次检测到的原因（reason，null 表示没问题）"与"该记录在 `rename_orphan` 表里已有的记录（可能没有）"做比对，决定是插入新记录、更新已有记录、删除已恢复正常的记录，还是跳过（已被人工忽略且问题仍然存在时不重复提醒）。这段决策逻辑不涉及任何 I/O，适合抽成纯函数单独测试。

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/OrphanReconciler.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/orphan/OrphanReconcilerTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.ruoyi.openliststrm.orphan;

import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameOrphanPlus;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class OrphanReconcilerTest {

    private RenameDetailPlus detail() {
        RenameDetailPlus d = new RenameDetailPlus();
        d.setId(42);
        d.setNewPath("/data/media/Movies/Inception (2010)");
        d.setNewName("Inception (2010).strm");
        d.setTitle("盗梦空间");
        d.setYear("2010");
        d.setMediaType("movie");
        return d;
    }

    @Test
    void reconcile_无问题且没有已有记录_跳过() {
        OrphanReconciler.Decision decision = OrphanReconciler.reconcile(detail(), null, null, new Date());
        assertEquals(OrphanReconciler.Action.SKIP, decision.action());
    }

    @Test
    void reconcile_无问题但存在待处理的已有记录_删除已恢复正常的记录() {
        RenameOrphanPlus existing = new RenameOrphanPlus();
        existing.setId(1);
        existing.setStatus("0");
        OrphanReconciler.Decision decision = OrphanReconciler.reconcile(detail(), existing, null, new Date());
        assertEquals(OrphanReconciler.Action.DELETE, decision.action());
        assertSame(existing, decision.toPersist());
    }

    @Test
    void reconcile_有问题且没有已有记录_插入新记录() {
        Date now = new Date();
        OrphanReconciler.Decision decision = OrphanReconciler.reconcile(detail(), null, "source_missing", now);
        assertEquals(OrphanReconciler.Action.INSERT, decision.action());
        RenameOrphanPlus persist = decision.toPersist();
        assertEquals(42, persist.getDetailId());
        assertEquals("/data/media/Movies/Inception (2010)", persist.getNewPath());
        assertEquals("Inception (2010).strm", persist.getNewName());
        assertEquals("盗梦空间", persist.getTitle());
        assertEquals("source_missing", persist.getReason());
        assertEquals("0", persist.getStatus());
        assertEquals(now, persist.getFoundTime());
    }

    @Test
    void reconcile_有问题且已有待处理记录_更新原因和发现时间() {
        RenameOrphanPlus existing = new RenameOrphanPlus();
        existing.setId(7);
        existing.setStatus("0");
        existing.setReason("local_missing");
        Date now = new Date();
        OrphanReconciler.Decision decision = OrphanReconciler.reconcile(detail(), existing, "source_missing", now);
        assertEquals(OrphanReconciler.Action.UPDATE, decision.action());
        assertSame(existing, decision.toPersist());
        assertEquals("source_missing", existing.getReason());
        assertEquals(now, existing.getFoundTime());
    }

    @Test
    void reconcile_有问题但已被人工忽略_跳过不重复提醒() {
        RenameOrphanPlus existing = new RenameOrphanPlus();
        existing.setId(9);
        existing.setStatus("2");
        OrphanReconciler.Decision decision = OrphanReconciler.reconcile(detail(), existing, "source_missing", new Date());
        assertEquals(OrphanReconciler.Action.SKIP, decision.action());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=OrphanReconcilerTest -q`

预期：FAIL，编译失败，找不到 `OrphanReconciler` 类。

- [ ] **步骤 3：编写实现代码**

```java
package com.ruoyi.openliststrm.orphan;

import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameOrphanPlus;

import java.util.Date;

/**
 * 孤儿判定的决策逻辑：把"本次检测结果"与"rename_orphan 表里已有的记录"做比对，
 * 决定插入 / 更新 / 删除（已恢复正常）/ 跳过（已忽略且问题仍在，不重复提醒）。
 * 不做任何 I/O，方便单测覆盖所有分支。
 */
public final class OrphanReconciler {

    private OrphanReconciler() {
    }

    public enum Action {
        INSERT, UPDATE, DELETE, SKIP
    }

    public record Decision(Action action, RenameOrphanPlus toPersist) {
    }

    private static final Decision SKIP_DECISION = new Decision(Action.SKIP, null);

    /**
     * @param detail  本次扫描到的重命名明细
     * @param existing 该 detail 在 rename_orphan 表中已有的记录，没有则为 null
     * @param reason   本次检测到的孤儿原因（local_missing / source_missing），没问题则为 null
     * @param now      发现/恢复时间
     */
    public static Decision reconcile(RenameDetailPlus detail, RenameOrphanPlus existing, String reason, Date now) {
        if (reason == null) {
            return existing != null ? new Decision(Action.DELETE, existing) : SKIP_DECISION;
        }
        if (existing != null && "2".equals(existing.getStatus())) {
            return SKIP_DECISION;
        }
        RenameOrphanPlus target = existing != null ? existing : new RenameOrphanPlus();
        target.setDetailId(detail.getId());
        target.setNewPath(detail.getNewPath());
        target.setNewName(detail.getNewName());
        target.setTitle(detail.getTitle());
        target.setYear(detail.getYear());
        target.setMediaType(detail.getMediaType());
        target.setReason(reason);
        target.setStatus("0");
        target.setFoundTime(now);
        return new Decision(existing != null ? Action.UPDATE : Action.INSERT, target);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=OrphanReconcilerTest -q`

预期：PASS，5 个测试全部通过。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/OrphanReconciler.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/orphan/OrphanReconcilerTest.java
git commit -m "feat: 新增孤儿判定决策逻辑OrphanReconciler"
```

---

### 任务 5：扫描服务接口 + clean/ignore 实现（TDD）

**背景：** 先实现 `clean()`/`ignore()`（比 `scan()` 简单，且 `scan()` 依赖它们共用的 `IRenameOrphanScanService` 接口），验证依赖组合方式没问题，再在任务 6 实现复杂的 `scan()`。

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/IRenameOrphanScanService.java`
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/RenameOrphanScanServiceImpl.java`（本任务只写 `clean`/`ignore`，`scan` 留空方法体，任务 6 补齐）
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/orphan/RenameOrphanScanServiceImplTest.java`

- [ ] **步骤 1：编写失败的测试（clean/ignore 部分）**

```java
package com.ruoyi.openliststrm.orphan;

import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameOrphanPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameOrphanPlusService;
import com.ruoyi.openliststrm.scrape.ScrapeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RenameOrphanScanServiceImplTest {

    @Mock
    private IRenameDetailPlusService renameDetailService;
    @Mock
    private IRenameOrphanPlusService renameOrphanService;
    @Mock
    private OpenlistApi openListApi;
    @Mock
    private OpenlistConfig config;
    @Mock
    private ScrapeService scrapeService;

    private RenameOrphanScanServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RenameOrphanScanServiceImpl();
        service.renameDetailService = renameDetailService;
        service.renameOrphanService = renameOrphanService;
        service.openListApi = openListApi;
        service.config = config;
        service.scrapeService = scrapeService;
    }

    @Test
    void clean_原因是网盘源丢失_删除本地文件并清理刮削产物和明细记录() {
        RenameOrphanPlus orphan = new RenameOrphanPlus();
        orphan.setId(1);
        orphan.setDetailId(42);
        orphan.setReason("source_missing");
        when(renameOrphanService.listByIds(List.of(1))).thenReturn(List.of(orphan));

        RenameDetailPlus detail = new RenameDetailPlus();
        detail.setId(42);
        detail.setNewPath("/nonexistent/dir/for/test");
        detail.setNewName("does-not-exist.strm");
        when(renameDetailService.getById(42)).thenReturn(detail);

        service.clean(List.of(1));

        verify(scrapeService).deleteScrapeFiles(detail);
        verify(renameDetailService).removeById(42);
        verify(renameOrphanService).updateBatchById(argThat(list -> {
            RenameOrphanPlus updated = list.iterator().next();
            return "1".equals(updated.getStatus()) && updated.getCleanTime() != null;
        }));
    }

    @Test
    void clean_原因是本地文件已丢失_不重复删除本地文件仅清理刮削产物和明细记录() {
        RenameOrphanPlus orphan = new RenameOrphanPlus();
        orphan.setId(2);
        orphan.setDetailId(43);
        orphan.setReason("local_missing");
        when(renameOrphanService.listByIds(List.of(2))).thenReturn(List.of(orphan));

        RenameDetailPlus detail = new RenameDetailPlus();
        detail.setId(43);
        detail.setNewPath("/data/media/whatever");
        detail.setNewName("whatever.strm");
        when(renameDetailService.getById(43)).thenReturn(detail);

        service.clean(List.of(2));

        verify(scrapeService).deleteScrapeFiles(detail);
        verify(renameDetailService).removeById(43);
    }

    @Test
    void ignore_批量标记为已忽略并写清理时间() {
        RenameOrphanPlus orphan = new RenameOrphanPlus();
        orphan.setId(5);
        orphan.setStatus("0");
        when(renameOrphanService.listByIds(List.of(5))).thenReturn(List.of(orphan));

        service.ignore(List.of(5));

        verify(renameOrphanService).updateBatchById(argThat(list -> {
            RenameOrphanPlus updated = list.iterator().next();
            return "2".equals(updated.getStatus()) && updated.getCleanTime() != null;
        }));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=RenameOrphanScanServiceImplTest -q`

预期：FAIL，编译失败（找不到 `IRenameOrphanScanService`/`RenameOrphanScanServiceImpl`）。

- [ ] **步骤 3：编写业务接口**

```java
package com.ruoyi.openliststrm.orphan;

import java.util.List;

/**
 * 重命名 STRM 一致性检查：扫描孤儿记录、清理确认后的孤儿、忽略误报。
 */
public interface IRenameOrphanScanService {

    /**
     * 全量扫描：遍历所有已重命名成功的 .strm 记录，检测本地文件/网盘源文件是否仍然存在，
     * 把检测结果落库到 rename_orphan（新增/更新/自动移除已恢复正常的记录）。
     *
     * @return 本次扫描汇总
     */
    ScanSummary scan();

    /**
     * 批量确认清理：删除残留的本地文件（仅 source_missing 需要）+ NFO/图片 + rename_detail 记录，
     * 并把对应 rename_orphan 记录标记为已清理。
     */
    void clean(List<Integer> orphanIds);

    /**
     * 批量忽略：仅标记 rename_orphan 记录为已忽略，不做任何文件操作。
     */
    void ignore(List<Integer> orphanIds);

    /**
     * 一次扫描的汇总结果。
     */
    record ScanSummary(int localMissing, int sourceMissing, int resolved, int unparsable) {
    }
}
```

- [ ] **步骤 4：编写 Service 实现（clean/ignore，scan 先占位）**

```java
package com.ruoyi.openliststrm.orphan;

import com.ruoyi.openliststrm.api.OpenlistApi;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameOrphanPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameOrphanPlusService;
import com.ruoyi.openliststrm.scrape.ScrapeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class RenameOrphanScanServiceImpl implements IRenameOrphanScanService {

    @Autowired
    IRenameDetailPlusService renameDetailService;

    @Autowired
    IRenameOrphanPlusService renameOrphanService;

    @Autowired
    OpenlistApi openListApi;

    @Autowired
    OpenlistConfig config;

    @Autowired
    ScrapeService scrapeService;

    @Override
    public ScanSummary scan() {
        // 任务6实现
        throw new UnsupportedOperationException("待任务6实现");
    }

    @Override
    public void clean(List<Integer> orphanIds) {
        if (orphanIds == null || orphanIds.isEmpty()) {
            return;
        }
        List<RenameOrphanPlus> orphans = renameOrphanService.listByIds(orphanIds);
        Date now = new Date();
        for (RenameOrphanPlus orphan : orphans) {
            RenameDetailPlus detail = renameDetailService.getById(orphan.getDetailId());
            if (detail != null) {
                if ("source_missing".equals(orphan.getReason())) {
                    deleteLocalFile(detail);
                }
                scrapeService.deleteScrapeFiles(detail);
                renameDetailService.removeById(detail.getId());
            }
            orphan.setStatus("1");
            orphan.setCleanTime(now);
        }
        renameOrphanService.updateBatchById(orphans);
    }

    @Override
    public void ignore(List<Integer> orphanIds) {
        if (orphanIds == null || orphanIds.isEmpty()) {
            return;
        }
        List<RenameOrphanPlus> orphans = renameOrphanService.listByIds(orphanIds);
        Date now = new Date();
        orphans.forEach(o -> {
            o.setStatus("2");
            o.setCleanTime(now);
        });
        renameOrphanService.updateBatchById(orphans);
    }

    private void deleteLocalFile(RenameDetailPlus detail) {
        try {
            Path file = Paths.get(detail.getNewPath(), detail.getNewName());
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("删除本地strm文件失败: {}/{}", detail.getNewPath(), detail.getNewName(), e);
        }
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=RenameOrphanScanServiceImplTest -q`

预期：PASS，3 个测试全部通过（`scan()` 未被这批测试调用，占位实现不影响）。

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/IRenameOrphanScanService.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/RenameOrphanScanServiceImpl.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/orphan/RenameOrphanScanServiceImplTest.java
git commit -m "feat: 实现孤儿记录批量清理/忽略"
```

---

### 任务 6：扫描逻辑 scan()（TDD）

**背景：** 补齐 `scan()`：第一阶段本地存在性检查，第二阶段按目录批量核对网盘源存在性，两阶段都调用 `OrphanReconciler` 做决策并落库。测试用 Mockito 模拟 `openListApi.getOpenlist`，用 `@TempDir` 造真实临时文件验证 `Files.exists` 分支（比全部 mock 文件系统更贴近真实行为，且 JUnit 5 原生支持）。

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/RenameOrphanScanServiceImpl.java`
- 修改：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/orphan/RenameOrphanScanServiceImplTest.java`

- [ ] **步骤 1：追加失败的测试**

在 `RenameOrphanScanServiceImplTest.java` 中，`import` 区新增（`java.util.List` 任务 5 已经导入过，不要重复添加）：

```java
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
```

（与已有 import 合并，去重）在类体内追加：

```java
    @TempDir
    Path tempDir;

    @Test
    void scan_本地文件不存在_判定为local_missing并插入孤儿记录() throws IOException {
        RenameDetailPlus detail = new RenameDetailPlus();
        detail.setId(1);
        detail.setStatus("1");
        detail.setNewPath(tempDir.resolve("missing-dir").toString());
        detail.setNewName("ghost.strm");
        detail.setTitle("Ghost");
        when(renameDetailService.list(any())).thenReturn(List.of(detail));
        when(renameOrphanService.list()).thenReturn(List.of());

        service.scan();

        verify(renameOrphanService).save(argThat(o -> "local_missing".equals(o.getReason()) && o.getDetailId() == 1));
    }

    @Test
    void scan_本地文件存在但网盘源已删除_判定为source_missing() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("movies"));
        Path strmFile = dir.resolve("a.strm");
        when(config.getOpenListUrl()).thenReturn("http://alist.local");
        when(config.getOpenListStrmEncode()).thenReturn("0");
        Files.writeString(strmFile, "http://alist.local/d/movies/a.mkv", StandardCharsets.UTF_8);

        RenameDetailPlus detail = new RenameDetailPlus();
        detail.setId(2);
        detail.setStatus("1");
        detail.setNewPath(dir.toString());
        detail.setNewName("a.strm");
        when(renameDetailService.list(any())).thenReturn(List.of(detail));
        when(renameOrphanService.list()).thenReturn(List.of());
        when(config.getTraversalConcurrency()).thenReturn(4);

        JSONObject dirListing = new JSONObject();
        dirListing.put("code", 200);
        JSONObject data = new JSONObject();
        data.put("content", new com.alibaba.fastjson2.JSONArray());
        dirListing.put("data", data);
        when(openListApi.getOpenlist(eq("/movies"), eq(false))).thenReturn(dirListing);

        service.scan();

        verify(renameOrphanService).save(argThat(o -> "source_missing".equals(o.getReason()) && o.getDetailId() == 2));
    }

    @Test
    void scan_本地文件和网盘源都存在_不产生孤儿记录() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("movies2"));
        Path strmFile = dir.resolve("b.strm");
        when(config.getOpenListUrl()).thenReturn("http://alist.local");
        when(config.getOpenListStrmEncode()).thenReturn("0");
        Files.writeString(strmFile, "http://alist.local/d/movies2/b.mkv", StandardCharsets.UTF_8);

        RenameDetailPlus detail = new RenameDetailPlus();
        detail.setId(3);
        detail.setStatus("1");
        detail.setNewPath(dir.toString());
        detail.setNewName("b.strm");
        when(renameDetailService.list(any())).thenReturn(List.of(detail));
        when(renameOrphanService.list()).thenReturn(List.of());
        when(config.getTraversalConcurrency()).thenReturn(4);

        JSONObject dirListing = new JSONObject();
        dirListing.put("code", 200);
        JSONObject data = new JSONObject();
        com.alibaba.fastjson2.JSONArray content = new com.alibaba.fastjson2.JSONArray();
        JSONObject file = new JSONObject();
        file.put("name", "b.mkv");
        file.put("is_dir", false);
        content.add(file);
        data.put("content", content);
        dirListing.put("data", data);
        when(openListApi.getOpenlist(eq("/movies2"), eq(false))).thenReturn(dirListing);

        service.scan();

        verify(renameOrphanService, never()).save(any());
        verify(renameOrphanService, never()).updateBatchById(any());
    }
```

`renameDetailService.list(any())` 里的 `any()` 匹配 `scan()` 内部构造的 `LambdaQueryWrapper<RenameDetailPlus>`——用 `IService.list(Wrapper<T>)` 这个稳定公开方法来 stub，不依赖 MyBatis-Plus 内部链式构造器的具体类型，与 `OpenListStrmTask.java` 里 `copyTaskPlusService.list(wrapper)` 的现有写法保持一致。

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=RenameOrphanScanServiceImplTest -q`

预期：FAIL——新增的三个 `scan_*` 测试因为 `scan()` 抛 `UnsupportedOperationException` 而失败；已有的 `clean_*`/`ignore_*` 测试仍然通过。

- [ ] **步骤 3：实现 scan()**

替换 `RenameOrphanScanServiceImpl.java` 中 `scan()` 方法体（其余方法不变），并在文件顶部补充下列尚未导入的 import（`RenameDetailPlus`/`Date`/`List` 任务 5 已经导入过，不要重复添加）：

```java
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
```

```java
    private record ScanCandidate(RenameDetailPlus detail, String sourcePath) {
    }

    @Override
    public ScanSummary scan() {
        LambdaQueryWrapper<RenameDetailPlus> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RenameDetailPlus::getStatus, "1").likeLeft(RenameDetailPlus::getNewName, ".strm");
        List<RenameDetailPlus> candidates = renameDetailService.list(wrapper);

        Map<Integer, RenameOrphanPlus> existingByDetailId = renameOrphanService.list().stream()
                .collect(Collectors.toMap(RenameOrphanPlus::getDetailId, o -> o, (a, b) -> a));

        String baseUrl = config.getOpenListUrl();
        boolean encoded = "1".equals(config.getOpenListStrmEncode());
        Date now = new Date();

        int localMissing = 0;
        int unparsable = 0;
        List<ScanCandidate> stage2 = new ArrayList<>();

        for (RenameDetailPlus detail : candidates) {
            Path file = Paths.get(detail.getNewPath(), detail.getNewName());
            if (!Files.exists(file)) {
                applyDecision(OrphanReconciler.reconcile(detail, existingByDetailId.get(detail.getId()), "local_missing", now));
                localMissing++;
                continue;
            }
            String content;
            try {
                content = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("读取strm文件内容失败，跳过网盘源检测: {}", file, e);
                continue;
            }
            String sourcePath = StrmSourcePathResolver.resolve(content, baseUrl, encoded);
            if (sourcePath == null) {
                unparsable++;
                continue;
            }
            stage2.add(new ScanCandidate(detail, sourcePath));
        }

        Map<String, List<ScanCandidate>> byDir = stage2.stream()
                .collect(Collectors.groupingBy(c -> parentDir(c.sourcePath())));

        int sourceMissing = 0;
        int resolved = 0;
        if (!byDir.isEmpty()) {
            Semaphore semaphore = new Semaphore(config.getTraversalConcurrency());
            List<int[]> counts;
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<int[]>> futures = byDir.entrySet().stream()
                        .map(entry -> CompletableFuture.supplyAsync(() -> {
                            try {
                                semaphore.acquire();
                                try {
                                    return checkDirGroup(entry.getKey(), entry.getValue(), existingByDetailId, now);
                                } finally {
                                    semaphore.release();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return new int[]{0, 0};
                            }
                        }, executor))
                        .toList();
                counts = futures.stream().map(CompletableFuture::join).toList();
            }
            for (int[] c : counts) {
                sourceMissing += c[0];
                resolved += c[1];
            }
        }

        ScanSummary summary = new ScanSummary(localMissing, sourceMissing, resolved, unparsable);
        log.info("重命名一致性检查扫描完成: 本地丢失={}, 网盘源丢失={}, 已恢复正常={}, 无法解析跳过={}",
                summary.localMissing(), summary.sourceMissing(), summary.resolved(), summary.unparsable());
        return summary;
    }

    /**
     * 核对单个网盘目录下一组候选文件是否仍然存在，返回 {source_missing数量, 已恢复正常数量}。
     */
    private int[] checkDirGroup(String dir, List<ScanCandidate> group, Map<Integer, RenameOrphanPlus> existingByDetailId, Date now) {
        JSONObject resp = openListApi.getOpenlist(dir, false);
        Set<String> existingNames;
        boolean dirGone = resp == null || !Integer.valueOf(200).equals(resp.getInteger("code")) || resp.getJSONObject("data") == null;
        if (dirGone) {
            existingNames = Set.of();
        } else {
            JSONArray content = resp.getJSONObject("data").getJSONArray("content");
            existingNames = content == null ? Set.of() : content.stream()
                    .map(o -> ((JSONObject) o).getString("name"))
                    .collect(Collectors.toCollection(HashSet::new));
        }

        int sourceMissing = 0;
        int resolved = 0;
        for (ScanCandidate candidate : group) {
            String fileName = fileNameOf(candidate.sourcePath());
            RenameOrphanPlus existing = existingByDetailId.get(candidate.detail().getId());
            if (existingNames.contains(fileName)) {
                OrphanReconciler.Decision decision = OrphanReconciler.reconcile(candidate.detail(), existing, null, now);
                if (decision.action() == OrphanReconciler.Action.DELETE) {
                    resolved++;
                }
                applyDecision(decision);
            } else {
                OrphanReconciler.Decision decision = OrphanReconciler.reconcile(candidate.detail(), existing, "source_missing", now);
                if (decision.action() != OrphanReconciler.Action.SKIP) {
                    sourceMissing++;
                }
                applyDecision(decision);
            }
        }
        return new int[]{sourceMissing, resolved};
    }

    private void applyDecision(OrphanReconciler.Decision decision) {
        switch (decision.action()) {
            case INSERT -> renameOrphanService.save(decision.toPersist());
            case UPDATE -> renameOrphanService.updateById(decision.toPersist());
            case DELETE -> renameOrphanService.removeById(decision.toPersist().getId());
            case SKIP -> {
                // 无需处理
            }
        }
    }

    private static String parentDir(String sourcePath) {
        int idx = sourcePath.lastIndexOf('/');
        return idx > 0 ? sourcePath.substring(0, idx) : "/";
    }

    private static String fileNameOf(String sourcePath) {
        int idx = sourcePath.lastIndexOf('/');
        return idx >= 0 ? sourcePath.substring(idx + 1) : sourcePath;
    }
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=RenameOrphanScanServiceImplTest -q`

预期：PASS，6 个测试全部通过。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/orphan/RenameOrphanScanServiceImpl.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/orphan/RenameOrphanScanServiceImplTest.java
git commit -m "feat: 实现重命名一致性检查扫描逻辑scan()"
```

---

### 任务 7：REST Controller

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/RenameOrphanRestController.java`

- [ ] **步骤 1：编写 Controller**

```java
package com.ruoyi.openliststrm.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameOrphanPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameOrphanPlusService;
import com.ruoyi.openliststrm.orphan.IRenameOrphanScanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 重命名一致性检查（孤儿清理）REST API控制器
 *
 * @author Jack
 * @date 2026-07-19
 */
@RestController
@RequestMapping("/api/openliststrm/rename-orphans")
public class RenameOrphanRestController extends BaseCrudRestController<IRenameOrphanPlusService, RenameOrphanPlus>
{
    @Autowired
    private IRenameOrphanScanService scanService;

    /**
     * 手动触发全量扫描（异步执行，立即返回）
     */
    @PostMapping("/scan")
    public Result<Void> scan()
    {
        AsyncManager.me().execute(scanService::scan);
        return Result.success();
    }

    /**
     * 批量确认清理
     */
    @PostMapping("/clean")
    public Result<Void> clean(@RequestParam("ids") String ids)
    {
        if (StringUtils.isEmpty(ids))
        {
            return Result.error("请选择要清理的记录");
        }
        scanService.clean(parseIds(ids));
        return Result.success();
    }

    /**
     * 批量忽略
     */
    @PostMapping("/ignore")
    public Result<Void> ignore(@RequestParam("ids") String ids)
    {
        if (StringUtils.isEmpty(ids))
        {
            return Result.error("请选择要忽略的记录");
        }
        scanService.ignore(parseIds(ids));
        return Result.success();
    }

    private List<Integer> parseIds(String ids)
    {
        return Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).collect(Collectors.toList());
    }

    @Override
    protected QueryWrapper<RenameOrphanPlus> buildQueryWrapper(RenameOrphanPlus entity)
    {
        QueryWrapper<RenameOrphanPlus> wrapper = new QueryWrapper<>();
        if (entity != null)
        {
            if (StringUtils.isNotEmpty(entity.getStatus()))
            {
                wrapper.eq("status", entity.getStatus());
            }
            if (StringUtils.isNotEmpty(entity.getReason()))
            {
                wrapper.eq("reason", entity.getReason());
            }
            if (StringUtils.isNotEmpty(entity.getTitle()))
            {
                wrapper.like("title", entity.getTitle());
            }
        }
        wrapper.orderByDesc("found_time");
        return wrapper;
    }
}
```

- [ ] **步骤 2：编译验证**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am compile -q`

预期：BUILD SUCCESS。

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/RenameOrphanRestController.java
git commit -m "feat: 新增重命名一致性检查REST API"
```

---

### 任务 8：定时任务钩子

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/task/OpenListStrmTask.java`

- [ ] **步骤 1：加依赖注入和方法**

在 `OpenListStrmTask` 类中，`renameTaskPlusService` 字段声明之后加：

```java
    @Autowired
    private com.ruoyi.openliststrm.orphan.IRenameOrphanScanService renameOrphanScanService;
```

在类末尾 `rename()` 方法之后、类结束 `}` 之前加：

```java
    public void checkRenameOrphan() {
        renameOrphanScanService.scan();
    }
```

（`sys_job` 记录已在任务 1 的 SQL 迁移中注册为 `openListStrmTask.checkRenameOrphan()`，此处只需提供对应方法）

- [ ] **步骤 2：编译验证**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am compile -q`

预期：BUILD SUCCESS。

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/task/OpenListStrmTask.java
git commit -m "feat: 接入重命名一致性检查定时任务"
```

---

### 任务 9：前端 API 封装层

**文件：**
- 创建：`openlist-web/src/api/openlist/renameOrphan.ts`

- [ ] **步骤 1：编写 API 封装**

```typescript
import request from '@/api/request'
import type { PageResult, SearchParams } from '@/types'

export function getRenameOrphanListApi(params: SearchParams) {
  return request.get<any, PageResult<any>>('/openliststrm/rename-orphans', { params })
}

export function scanRenameOrphanApi() {
  return request.post('/openliststrm/rename-orphans/scan')
}

export function batchCleanRenameOrphanApi(orphanIds: number[]) {
  return request.post('/openliststrm/rename-orphans/clean', null, { params: { ids: orphanIds.join(',') } })
}

export function batchIgnoreRenameOrphanApi(orphanIds: number[]) {
  return request.post('/openliststrm/rename-orphans/ignore', null, { params: { ids: orphanIds.join(',') } })
}
```

- [ ] **步骤 2：Commit**

```bash
git add openlist-web/src/api/openlist/renameOrphan.ts
git commit -m "feat: 新增重命名一致性检查前端API封装"
```

---

### 任务 10：前端 composable

**背景：** 复用 `useRecordList` 提供列表/分页/搜索/选择能力；把"清理"接到 `useRecordList` 的 `batchDeleteApi` 插槽（清理本质上就是删除残留文件+记录，语义相符，复用其 `handleDeleteOne`/`handleBatchDelete`），"忽略"作为本页特有能力单独扩展（做法与 `useRenameDetailList` 扩展刮削相关操作一致）。

**文件：**
- 创建：`openlist-web/src/composables/useRenameOrphanList.ts`

- [ ] **步骤 1：编写 composable**

```typescript
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRecordList } from './useRecordList'
import type { SearchParams } from '@/types'
import {
  getRenameOrphanListApi,
  scanRenameOrphanApi,
  batchCleanRenameOrphanApi,
  batchIgnoreRenameOrphanApi
} from '@/api/openlist/renameOrphan'

export type RenameOrphanQuery = SearchParams & {
  status?: string
  reason?: string
  title?: string
}

/**
 * 重命名一致性检查页（PC + 移动端）共用逻辑。
 * 列表/分页/搜索/选择/清理是标准记录页逻辑，复用 useRecordList（清理接到其 batchDeleteApi 插槽——
 * 清理本质上就是删除残留文件+记录，语义相符）；忽略、立即扫描是本页特有能力，在此扩展叠加。
 */
export function useRenameOrphanList() {
  const {
    recordList, loading, total, queryParams, totalPages,
    getList, silentRefresh, prevPage, nextPage, handleSizeChange,
    queryRef, dateRange, handleQuery, resetQuery,
    selectedIds, multiple, toggleSelect, handleCardClick, clearSelection, handleSelectionChange,
    handleDeleteOne: handleCleanOne, handleBatchDelete: handleBatchClean
  } = useRecordList<RenameOrphanQuery>({
    listApi: getRenameOrphanListApi,
    batchDeleteApi: batchCleanRenameOrphanApi,
    idField: 'id',
    labelField: 'newName',
    recordLabel: '孤儿记录',
    defaultQuery: { status: '0' }
  })

  // --- 立即扫描 ---
  const scanning = ref(false)
  const handleScanNow = async () => {
    scanning.value = true
    try {
      await scanRenameOrphanApi()
      ElMessage.success('扫描已在后台启动，请稍后刷新查看结果')
    } catch (error: any) {
      ElMessage.error(error.message || '触发扫描失败')
    } finally {
      scanning.value = false
    }
  }

  // --- 忽略 ---
  const handleIgnoreOne = async (row: any) => {
    try {
      await ElMessageBox.confirm(`是否确认忽略"${row.newName}"？忽略后不会自动清理，也不会再次提醒。`, '提示', { type: 'warning' })
      await batchIgnoreRenameOrphanApi([row.id])
      ElMessage.success('已忽略')
      getList()
    } catch (e) { if (e !== 'cancel') console.error(e) }
  }

  const handleBatchIgnore = async () => {
    try {
      await ElMessageBox.confirm(`是否确认忽略选中的 ${selectedIds.value.length} 条记录？`, '提示', { type: 'warning' })
      await batchIgnoreRenameOrphanApi(selectedIds.value)
      ElMessage.success('已忽略')
      getList()
    } catch (e) { if (e !== 'cancel') console.error(e) }
  }

  return {
    recordList, loading, total, queryParams, totalPages,
    getList, silentRefresh, prevPage, nextPage, handleSizeChange,
    queryRef, dateRange, handleQuery, resetQuery,
    selectedIds, multiple, toggleSelect, handleCardClick, clearSelection, handleSelectionChange,
    handleCleanOne, handleBatchClean,
    scanning, handleScanNow,
    handleIgnoreOne, handleBatchIgnore
  }
}
```

需要在文件顶部补一个 `import { ref } from 'vue'`（`unplugin-auto-import` 通常能自动注入，但该 composable 文件不在 `views/`/`components/` 自动扫描范围内时需要显式导入，与 `useRenameDetailList.ts` 顶部显式 `import { reactive, ref } from 'vue'` 的写法保持一致）：

```typescript
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
```

- [ ] **步骤 2：Commit**

```bash
git add openlist-web/src/composables/useRenameOrphanList.ts
git commit -m "feat: 新增重命名一致性检查前端composable"
```

---

### 任务 11：前端桌面端页面

**文件：**
- 创建：`openlist-web/src/views/openlist/renameOrphan/index.vue`

- [ ] **步骤 1：编写页面**

```vue
<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="影视名称" prop="title">
          <el-input v-model="queryParams.title" placeholder="请输入影视名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-select v-model="queryParams.reason" placeholder="全部原因" clearable :style="{ width: '160px' }">
            <el-option label="本地文件丢失" value="local_missing" />
            <el-option label="网盘源丢失" value="source_missing" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable :style="{ width: '120px' }">
            <el-option label="待处理" value="0" />
            <el-option label="已清理" value="1" />
            <el-option label="已忽略" value="2" />
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
          <el-button type="primary" :loading="scanning" @click="handleScanNow">
            <el-icon><Refresh /></el-icon> 立即扫描
          </el-button>
          <el-button type="danger" :disabled="multiple" @click="handleBatchClean()">
            <el-icon><Delete /></el-icon> 批量清理
          </el-button>
          <el-button type="warning" :disabled="multiple" @click="handleBatchIgnore()">
            <el-icon><Warning /></el-icon> 批量忽略
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="recordList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="标题" min-width="200">
          <template #default="scope">
            <span>{{ scope.row.title || '未知' }}</span>
            <span v-if="scope.row.year" class="orphan-year">（{{ scope.row.year }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="原因" width="130" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.reason === 'local_missing'" type="warning" size="small">本地文件丢失</el-tag>
            <el-tag v-else-if="scope.row.reason === 'source_missing'" type="danger" size="small">网盘源丢失</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重命名后路径" min-width="320">
          <template #default="scope">
            <span class="orphan-path" :title="`${scope.row.newPath}/${scope.row.newName}`">{{ scope.row.newPath }}/{{ scope.row.newName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.status === '0'" type="info" size="small">待处理</el-tag>
            <el-tag v-else-if="scope.row.status === '1'" type="success" size="small">已清理</el-tag>
            <el-tag v-else type="default" size="small">已忽略</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发现时间" prop="foundTime" width="170" align="center" />
        <el-table-column label="操作" align="center" width="180" fixed="right">
          <template #default="scope">
            <el-button link type="danger" @click="handleCleanOne(scope.row)" v-if="scope.row.status === '0'">
              <el-icon><Delete /></el-icon> 清理
            </el-button>
            <el-button link type="warning" @click="handleIgnoreOne(scope.row)" v-if="scope.row.status === '0'">
              <el-icon><Warning /></el-icon> 忽略
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
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search, Refresh, Delete, Filter, Warning } from '@element-plus/icons-vue'
import { useRenameOrphanList } from '@/composables/useRenameOrphanList'

const showSearch = ref(window.innerWidth >= 768)

const {
  recordList, loading, total, queryParams,
  getList, queryRef, handleQuery, resetQuery,
  multiple, handleSelectionChange,
  handleCleanOne, handleBatchClean,
  scanning, handleScanNow,
  handleIgnoreOne, handleBatchIgnore
} = useRenameOrphanList()

getList()
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);

  :deep(.el-card__body) {
    padding: 14px 16px;
  }
}

.table-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);

  :deep(.el-card__body) {
    padding: 16px;
    display: flex;
    flex-direction: column;
  }
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  .action-left {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }
}

.orphan-year {
  color: var(--osr-text-secondary);
  font-size: 12px;
}

.orphan-path {
  color: var(--osr-text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 12px;
}

@media (max-width: 768px) {
  .search-card :deep(.el-form) {
    .el-form-item {
      margin-right: 0;
    }

    .el-input,
    .el-select {
      width: 100% !important;
    }
  }
}
</style>
```

- [ ] **步骤 2：Commit**

```bash
git add openlist-web/src/views/openlist/renameOrphan/index.vue
git commit -m "feat: 新增重命名一致性检查桌面端页面"
```

---

### 任务 12：前端移动端页面

**文件：**
- 创建：`openlist-web/src/views-mobile/renameOrphan/index.vue`

- [ ] **步骤 1：编写页面**

```vue
<template>
  <div class="mobile-page">
    <MobileSearchPanel v-model:collapsed="searchCollapsed" :loading="loading" @search="handleQuery" @reset="resetQuery">
      <el-form ref="queryRef" :model="queryParams" label-width="72px">
        <el-form-item label="影视名称" prop="title">
          <el-input v-model="queryParams.title" placeholder="请输入影视名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-select v-model="queryParams.reason" placeholder="全部原因" clearable style="width: 100%">
            <el-option label="本地文件丢失" value="local_missing" />
            <el-option label="网盘源丢失" value="source_missing" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 100%">
            <el-option label="待处理" value="0" />
            <el-option label="已清理" value="1" />
            <el-option label="已忽略" value="2" />
          </el-select>
        </el-form-item>
      </el-form>
    </MobileSearchPanel>

    <div class="scan-bar">
      <el-button type="primary" size="small" :loading="scanning" @click="handleScanNow">
        <el-icon><Refresh /></el-icon> 立即扫描
      </el-button>
    </div>

    <div class="batch-bar" v-if="selectedIds.length > 0">
      <span class="selected-count">已选 {{ selectedIds.length }} 项</span>
      <el-button link type="danger" size="small" @click="handleBatchClean">
        <el-icon><Delete /></el-icon> 清理
      </el-button>
      <el-button link type="warning" size="small" @click="handleBatchIgnore">
        <el-icon><Warning /></el-icon> 忽略
      </el-button>
      <el-button link size="small" @click="clearSelection">
        取消
      </el-button>
    </div>

    <div v-loading="loading" class="mobile-card-list">
      <div v-for="item in recordList" :key="item.id" class="mobile-card" @click="handleCardClick($event, item.id)">
        <div class="mobile-card-header">
          <el-checkbox class="card-checkbox" :model-value="selectedIds.includes(item.id)" @change="toggleSelect(item.id)" @click.stop />
          <span class="mobile-title">{{ item.title || '未知' }}<span v-if="item.year">（{{ item.year }}）</span></span>
          <el-tag v-if="item.reason === 'local_missing'" type="warning" size="small">本地丢失</el-tag>
          <el-tag v-else type="danger" size="small">网盘源丢失</el-tag>
        </div>
        <div class="mobile-card-body">
          <div class="mobile-card-row">
            <span class="mobile-card-label">路径</span>
            <span class="mobile-card-value mobile-card-value-path" :title="`${item.newPath}/${item.newName}`">{{ item.newPath }}/{{ item.newName }}</span>
          </div>
          <div class="mobile-card-row">
            <span class="mobile-card-label">发现时间</span>
            <span class="mobile-card-value mobile-card-value-light">{{ item.foundTime }}</span>
          </div>
        </div>
        <div class="mobile-card-actions" v-if="item.status === '0'">
          <el-button link type="danger" size="small" @click.stop="handleCleanOne(item)">
            <el-icon><Delete /></el-icon> 清理
          </el-button>
          <el-button link type="warning" size="small" @click.stop="handleIgnoreOne(item)">
            <el-icon><Warning /></el-icon> 忽略
          </el-button>
        </div>
      </div>
      <el-empty v-if="!recordList.length" description="暂无数据" />
    </div>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        layout="prev, pager, next"
        small
        @current-change="getList"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Refresh, Delete, Warning } from '@element-plus/icons-vue'
import MobileSearchPanel from '@/components/MobileSearchPanel.vue'
import { useRenameOrphanList } from '@/composables/useRenameOrphanList'

const searchCollapsed = ref(true)

const {
  recordList, loading, total, queryParams,
  getList, queryRef, handleQuery, resetQuery,
  selectedIds, toggleSelect, handleCardClick, clearSelection,
  handleCleanOne, handleBatchClean,
  scanning, handleScanNow,
  handleIgnoreOne, handleBatchIgnore
} = useRenameOrphanList()

getList()
</script>

<style scoped lang="scss">
.mobile-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
}

.scan-bar {
  display: flex;
  justify-content: flex-end;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--osr-bg-page);
  border-radius: 8px;

  .selected-count {
    font-size: 13px;
    color: var(--osr-text-secondary);
    margin-right: auto;
  }
}

.mobile-card-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mobile-card {
  background: white;
  border-radius: 8px;
  border: 1px solid var(--osr-border-light);
  overflow: hidden;

  .mobile-card-header {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 12px 8px;
    border-bottom: 1px solid var(--osr-border-light);
    background: var(--osr-bg-page);

    .mobile-title {
      flex: 1;
      min-width: 0;
      font-size: 13px;
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .mobile-card-body {
    padding: 0;

    .mobile-card-row {
      display: flex;
      align-items: flex-start;
      padding: 8px 12px;
      font-size: 13px;
      border-bottom: 1px solid var(--osr-border-light);

      &:last-child {
        border-bottom: none;
      }

      .mobile-card-label {
        width: 64px;
        color: var(--osr-text-secondary);
        flex-shrink: 0;
        font-size: 12px;
      }

      .mobile-card-value {
        flex: 1;
        min-width: 0;
        font-size: 13px;
        word-break: break-all;

        &.mobile-card-value-path {
          color: var(--osr-text-placeholder);
          font-size: 12px;
        }

        &.mobile-card-value-light {
          color: var(--osr-text-secondary);
          font-size: 12px;
        }
      }
    }
  }

  .mobile-card-actions {
    display: flex;
    justify-content: flex-end;
    gap: 2px;
    padding: 8px 12px 10px;
    border-top: 1px solid var(--osr-border-light);
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}
</style>
```

- [ ] **步骤 2：Commit**

```bash
git add openlist-web/src/views-mobile/renameOrphan/index.vue
git commit -m "feat: 新增重命名一致性检查移动端页面"
```

---

### 任务 13：路由注册

**背景：** 后端菜单 `url` 为 `/openlist/renameOrphan`，`SysMenu.getComponentPath()` 会把它转成 `openlist/renameOrphan/index`；这个值已经是前端 `router/index.ts` 里 `normalizeComponentPath` 认得的规范格式（以 `openlist/` 开头、以 `/index` 结尾会原样返回），所以不需要改 `aliasMap`/`directPathMap`，只需要把这个 key 加进 `componentMap` 并加入需要 keep-alive 的列表。

**文件：**
- 修改：`openlist-web/src/router/index.ts`

- [ ] **步骤 1：注册组件映射**

在 `componentMap` 里 `'openlist/renameDetail/index'` 条目之后加：

```typescript
  'openlist/renameDetail/index': createDeviceView(
    () => import('@/views/openlist/renameDetail/index.vue'),
    () => import('@/views-mobile/renameDetail/index.vue')
  ),
  'openlist/renameOrphan/index': createDeviceView(
    () => import('@/views/openlist/renameOrphan/index.vue'),
    () => import('@/views-mobile/renameOrphan/index.vue')
  )
```

（即把原来 `'openlist/renameDetail/index': createDeviceView(...)` 这一项末尾的逗号保留，新增一项，最后一项后不加逗号——注意这是 `componentMap` 对象字面量的最后一个条目，务必保持对象语法合法）

- [ ] **步骤 2：加入 keep-alive 列表**

在 `KEEP_ALIVE_COMPONENTS` 集合里 `'openlist/renameDetail/index'` 之后加一行：

```typescript
const KEEP_ALIVE_COMPONENTS = new Set([
  'openlist/strmTask/index',
  'openlist/strmRecord/index',
  'openlist/copyTask/index',
  'openlist/copyRecord/index',
  'openlist/renameTask/index',
  'openlist/renameDetail/index',
  'openlist/renameOrphan/index'
])
```

- [ ] **步骤 3：类型检查验证**

运行：`cd openlist-web && npm run build`

预期：`vue-tsc` 类型检查通过，Vite 构建成功，无报错。

- [ ] **步骤 4：Commit**

```bash
git add openlist-web/src/router/index.ts
git commit -m "feat: 注册重命名一致性检查页面路由"
```

---

### 任务 14：全量验证

**背景：** 前面每个任务都验证过局部编译/单测，本任务做一次端到端确认：全量单测、全量前端构建、后端整体编译（含 DDL 自动建表）。

- [ ] **步骤 1：后端全量测试**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -q`

预期：BUILD SUCCESS，所有测试通过（含任务 3/4/5/6 新增的 14 个测试）。

- [ ] **步骤 2：后端全量打包**

运行：`mvn clean package -DskipTests`（仓库根目录）

预期：BUILD SUCCESS，`ruoyi-admin/target/` 下生成可执行 jar。

- [ ] **步骤 3：前端构建**

运行：`cd openlist-web && npm run build`

预期：构建成功，`dist/` 目录生成。

- [ ] **步骤 4：前端 lint**

运行：`cd openlist-web && npm run lint`

预期：无报错（允许自动修复后无残留错误）。

- [ ] **步骤 5：手动功能验证（需要真实 OpenList 环境）**

1. 启动后端（`docker compose up -d --build --no-deps backend` 或本地运行 jar），确认日志里 DDL 执行到 `20260719-rename-orphan.sql` 无报错，数据库出现 `rename_orphan` 表
2. 用现有重命名任务生成至少一条 `.strm` 结尾的 `rename_detail` 记录（`status=1`）
3. 手动删除该记录对应的本地 `.strm` 文件，登录前端访问「重命名一致性检查」页面，点击「立即扫描」，刷新后确认出现一条 `reason=local_missing` 的待处理记录
4. 恢复出另一条正常记录，改为在 OpenList 网盘端删除其对应源文件，再次扫描，确认出现 `reason=source_missing` 的记录，且原 `local_missing` 的记录仍在（未被误清）
5. 分别测试「清理」（确认本地文件/NFO/图片/`rename_detail` 记录被删除）与「忽略」（确认状态变为已忽略且不再出现在待处理列表默认视图里）
6. 把某条已标记孤儿的记录对应文件手动放回原处，再次扫描，确认该条待处理记录被自动移除

（该步骤依赖真实 OpenList 服务与网络环境，无法在本计划中自动化，执行时由人工在实际部署环境中完成）

---

## 执行说明

计划文件已保存到 `docs/superpowers/plans/2026-07-19-rename-orphan-consistency-check.md`。两种执行方式：

1. **子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代
2. **内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点
