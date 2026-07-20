# Telegram Bot 指令扩展实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 给 Telegram Bot 新增 3 个管理员指令：`/retry`（批量重试 STRM/同步/重命名三类失败记录）、`/rename`（执行全部启用的重命名任务）、`/checkorphan`（触发重命名一致性检查扫描并回复汇总）。

**架构：** 在 `IStrmService`/`ICopyService`/`RenameTaskManager` 三处分别新增一个"查询失败记录并批量重试"的薄封装方法（复用各自已有的 `retryStrm`/`retryCopy`/`executeRenameDetails`），`StrmBot` 新增三个 `Ability` 调用这些方法并格式化回复。

**技术栈：** Java 25 (Spring Boot 4.0.6, --enable-preview) + MyBatis-Plus + JUnit 5/Mockito（复用 `ruoyi-openliststrm` 模块已有的测试基础设施）+ telegrambots-abilities。

---

## 文件结构

**修改：**
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/IStrmService.java` — 新增 `retryAllFailed()` 方法声明 + `RetryOutcome` 记录类型
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/impl/StrmServiceImpl.java` — 实现 `retryAllFailed()`
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/ICopyService.java` — 新增 `retryAllFailed()` 方法声明 + `RetryOutcome` 记录类型
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/impl/CopyServiceImpl.java` — 实现 `retryAllFailed()`
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/RenameTaskManager.java` — 新增 `retryAllFailed()` 方法 + 内部 `RetryOutcome` 记录类型
- `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/tg/StrmBot.java` — `registerCommands()` 追加三条命令，新增 `retry()`/`rename()`/`checkOrphan()` 三个 `Ability`

**新增测试：**
- `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/service/impl/StrmServiceImplTest.java`
- `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/service/impl/CopyServiceImplTest.java`
- `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/RenameTaskManagerTest.java`

不涉及数据库迁移、不涉及前端改动。

---

## 范围检查

本功能是单一子系统（Telegram Bot 指令 + 三处薄封装方法），不需要再拆分子计划。

---

### 任务 1：`IStrmService.retryAllFailed()`（TDD）

**背景：** 查询 `openlist_strm` 表中 `strm_status='0'`（失败）的记录，按创建时间倒序最多取 200 条，调用已有的 `retryStrm(List<String>)`。为了让 Bot 能在超过上限时提示"还有 N 条未处理"，返回值不是单纯的 `int`，而是 `RetryOutcome(int retried, int remaining)` —— `retried` 是本次提交重试的数量，`remaining` 是未处理的剩余数量（`总失败数 - retried`）。

`IOpenlistStrmPlusService` 继承自 MyBatis-Plus `IService`，`list(Wrapper)` 和 `list(IPage)` 是两个重载方法——用 Mockito 的 `any()` 直接 stub 会因为参数类型不明确编译报错，必须用 `any(Wrapper.class)`（这是"重命名一致性检查"功能开发时已经踩过的坑，测试代码里已经直接用了正确写法）。

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/IStrmService.java`
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/impl/StrmServiceImpl.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/service/impl/StrmServiceImplTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.ruoyi.openliststrm.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmPlusService;
import com.ruoyi.openliststrm.service.IStrmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StrmServiceImplTest {

    @Mock
    private IOpenlistStrmPlusService openlistStrmPlusService;

    @InjectMocks
    private StrmServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void retryAllFailed_没有失败记录_返回0且不触发重试() {
        when(openlistStrmPlusService.count(any(Wrapper.class))).thenReturn(0L);

        IStrmService.RetryOutcome outcome = service.retryAllFailed();

        assertEquals(0, outcome.retried());
        assertEquals(0, outcome.remaining());
        verify(openlistStrmPlusService, never()).list(any(Wrapper.class));
        verify(openlistStrmPlusService, never()).listByIds(any());
    }

    @Test
    void retryAllFailed_失败记录未超上限_全部提交重试且remaining为0() {
        when(openlistStrmPlusService.count(any(Wrapper.class))).thenReturn(2L);
        OpenlistStrmPlus a = new OpenlistStrmPlus();
        a.setStrmId(5);
        OpenlistStrmPlus b = new OpenlistStrmPlus();
        b.setStrmId(3);
        when(openlistStrmPlusService.list(any(Wrapper.class))).thenReturn(List.of(a, b));
        // retryStrm 内部会再查一次 listByIds 取完整记录；返回空列表即可让内部的异步重试分支安全跑完，
        // 不需要真的执行网络请求，本测试只关心 retryAllFailed 自己的查询与转发逻辑
        when(openlistStrmPlusService.listByIds(any())).thenReturn(List.of());

        IStrmService.RetryOutcome outcome = service.retryAllFailed();

        assertEquals(2, outcome.retried());
        assertEquals(0, outcome.remaining());
        verify(openlistStrmPlusService).listByIds(eq(List.of("5", "3")));
    }

    @Test
    void retryAllFailed_失败记录超过200条上限_只取最新200条且remaining正确() {
        when(openlistStrmPlusService.count(any(Wrapper.class))).thenReturn(250L);
        OpenlistStrmPlus a = new OpenlistStrmPlus();
        a.setStrmId(9);
        when(openlistStrmPlusService.list(any(Wrapper.class))).thenReturn(List.of(a));
        when(openlistStrmPlusService.listByIds(any())).thenReturn(List.of());

        IStrmService.RetryOutcome outcome = service.retryAllFailed();

        assertEquals(1, outcome.retried());
        assertEquals(249, outcome.remaining());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=StrmServiceImplTest -q`

预期：FAIL，编译失败——`IStrmService` 没有 `retryAllFailed()` 方法，也没有 `RetryOutcome` 类型。

- [ ] **步骤 3：在 `IStrmService` 接口里加方法声明和记录类型**

在 `IStrmService.java` 的 `retryStrm(List<String> idList);` 之后加：

```java
    /**
     * 批量重试所有失败的 STRM 记录（最多重试最新 200 条）
     */
    RetryOutcome retryAllFailed();

    /**
     * @param retried   本次提交重试的记录数
     * @param remaining 超出 200 条上限、未处理的剩余失败记录数
     */
    record RetryOutcome(int retried, int remaining) {}
```

- [ ] **步骤 4：在 `StrmServiceImpl` 里实现该方法**

在 `StrmServiceImpl.java` 的 `retryStrm` 方法（第226-255行）之后加：

```java
    @Override
    public RetryOutcome retryAllFailed() {
        LambdaQueryWrapper<OpenlistStrmPlus> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(OpenlistStrmPlus::getStrmStatus, "0");
        long total = openlistStrmPlusService.count(countWrapper);
        if (total == 0) {
            return new RetryOutcome(0, 0);
        }

        LambdaQueryWrapper<OpenlistStrmPlus> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpenlistStrmPlus::getStrmStatus, "0")
                .select(OpenlistStrmPlus::getStrmId)
                .orderByDesc(OpenlistStrmPlus::getCreateTime)
                .last("LIMIT 200");
        List<OpenlistStrmPlus> failed = openlistStrmPlusService.list(wrapper);
        List<String> idList = failed.stream().map(s -> String.valueOf(s.getStrmId())).toList();
        retryStrm(idList);
        return new RetryOutcome(idList.size(), (int) total - idList.size());
    }
```

`LambdaQueryWrapper`、`OpenlistStrmPlus`、`List` 已经在该文件顶部导入过，不需要新增 import。

- [ ] **步骤 5：运行测试验证通过**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=StrmServiceImplTest -q`

预期：PASS，3 个测试全部通过。

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/IStrmService.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/impl/StrmServiceImpl.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/service/impl/StrmServiceImplTest.java
git commit -m "feat: IStrmService新增批量重试全部失败STRM记录的能力"
```

---

### 任务 2：`ICopyService.retryAllFailed()`（TDD）

**背景：** 与任务 1 结构完全一致，只是换成 `openlist_copy` 表，失败状态是 `copy_status='2'`，主键字段是 `copyId`，复用已有的 `retryCopy(List<String>)`。

**注意：** 步骤4的查询代码里**不要**加 `.select(OpenlistCopyPlus::getCopyId)` 列投影——`LambdaQueryWrapper.select(SFunction...)` 会在调用的当下就立即把方法引用解析成数据库列名，这个解析依赖的"lambda 缓存"只有在完整 Spring/MyBatis 容器启动后才会建立，纯单元测试（没有 Spring 容器）里调用会直接抛 `MybatisPlusException: can not find lambda cache for this entity`（任务1实现时已经踩过这个坑并验证了去掉 `.select()` 后测试全部通过）。`.eq()`/`.orderByDesc()` 这类条件方法不会立即解析列名，可以正常使用，只有 `.select(SFunction...)` 需要避免。直接用 `list(wrapper)` 拿完整实体对象、在 Java 里取 `getCopyId()` 拼 ID 列表即可，下面步骤4的代码已经是修正后的版本。

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/ICopyService.java`
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/impl/CopyServiceImpl.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/service/impl/CopyServiceImplTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.ruoyi.openliststrm.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyPlusService;
import com.ruoyi.openliststrm.service.ICopyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CopyServiceImplTest {

    @Mock
    private IOpenlistCopyPlusService openlistCopyPlusService;

    @InjectMocks
    private CopyServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void retryAllFailed_没有失败记录_返回0且不触发重试() {
        when(openlistCopyPlusService.count(any(Wrapper.class))).thenReturn(0L);

        ICopyService.RetryOutcome outcome = service.retryAllFailed();

        assertEquals(0, outcome.retried());
        assertEquals(0, outcome.remaining());
        verify(openlistCopyPlusService, never()).list(any(Wrapper.class));
        verify(openlistCopyPlusService, never()).listByIds(any());
    }

    @Test
    void retryAllFailed_失败记录未超上限_全部提交重试且remaining为0() {
        when(openlistCopyPlusService.count(any(Wrapper.class))).thenReturn(2L);
        OpenlistCopyPlus a = new OpenlistCopyPlus();
        a.setCopyId(7);
        OpenlistCopyPlus b = new OpenlistCopyPlus();
        b.setCopyId(4);
        when(openlistCopyPlusService.list(any(Wrapper.class))).thenReturn(List.of(a, b));
        when(openlistCopyPlusService.listByIds(any())).thenReturn(List.of());

        ICopyService.RetryOutcome outcome = service.retryAllFailed();

        assertEquals(2, outcome.retried());
        assertEquals(0, outcome.remaining());
        verify(openlistCopyPlusService).listByIds(eq(List.of("7", "4")));
    }

    @Test
    void retryAllFailed_失败记录超过200条上限_只取最新200条且remaining正确() {
        when(openlistCopyPlusService.count(any(Wrapper.class))).thenReturn(300L);
        OpenlistCopyPlus a = new OpenlistCopyPlus();
        a.setCopyId(1);
        when(openlistCopyPlusService.list(any(Wrapper.class))).thenReturn(List.of(a));
        when(openlistCopyPlusService.listByIds(any())).thenReturn(List.of());

        ICopyService.RetryOutcome outcome = service.retryAllFailed();

        assertEquals(1, outcome.retried());
        assertEquals(299, outcome.remaining());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=CopyServiceImplTest -q`

预期：FAIL，编译失败——`ICopyService` 没有 `retryAllFailed()` 方法，也没有 `RetryOutcome` 类型。

- [ ] **步骤 3：在 `ICopyService` 接口里加方法声明和记录类型**

在 `ICopyService.java` 的 `retryCopy(List<String> idList);` 之后加：

```java
    /**
     * 批量重试所有失败的复制记录（最多重试最新 200 条）
     */
    RetryOutcome retryAllFailed();

    /**
     * @param retried   本次提交重试的记录数
     * @param remaining 超出 200 条上限、未处理的剩余失败记录数
     */
    record RetryOutcome(int retried, int remaining) {}
```

- [ ] **步骤 4：在 `CopyServiceImpl` 里实现该方法**

在 `CopyServiceImpl.java` 的 `retryCopy` 方法（第411-444行）之后加：

```java
    @Override
    public RetryOutcome retryAllFailed() {
        LambdaQueryWrapper<OpenlistCopyPlus> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(OpenlistCopyPlus::getCopyStatus, "2");
        long total = openlistCopyPlusService.count(countWrapper);
        if (total == 0) {
            return new RetryOutcome(0, 0);
        }

        LambdaQueryWrapper<OpenlistCopyPlus> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpenlistCopyPlus::getCopyStatus, "2")
                .orderByDesc(OpenlistCopyPlus::getCreateTime)
                .last("LIMIT 200");
        List<OpenlistCopyPlus> failed = openlistCopyPlusService.list(wrapper);
        List<String> idList = failed.stream().map(c -> String.valueOf(c.getCopyId())).toList();
        retryCopy(idList);
        return new RetryOutcome(idList.size(), (int) total - idList.size());
    }
```

`LambdaQueryWrapper`、`OpenlistCopyPlus`、`List` 已经在该文件顶部导入过，不需要新增 import。

- [ ] **步骤 5：运行测试验证通过**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=CopyServiceImplTest -q`

预期：PASS，3 个测试全部通过。

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/ICopyService.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/service/impl/CopyServiceImpl.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/service/impl/CopyServiceImplTest.java
git commit -m "feat: ICopyService新增批量重试全部失败同步记录的能力"
```

---

### 任务 3：`RenameTaskManager.retryAllFailed()`（TDD）

**背景：** 查询 `rename_detail` 表中 `status='0'`（失败）的记录，最多取最新 200 条，对每条调用已有的 `executeRenameDetails(id, null, null, null, null)`（不传标题/年份/季/集覆盖值，保持原有解析结果重新执行）。

与前两个任务不同的是：`executeRenameDetails` 本身**没有**内部 try/catch 保护调用方的循环（它只在内部对 `IOException` 做了兜底，其他运行时异常会往外抛），所以 `retryAllFailed()` 必须自己在循环里对每条记录单独 try/catch，避免一条记录抛异常导致整批中断。

**测试上的额外坑：** `RenameTaskManager` 有一个字段初始化器 `private final TaskScheduler scheduler = SpringUtils.getBean("virtualScheduledExecutor");`，这行代码在**任何**方式构造 `RenameTaskManager` 实例时都会执行（包括测试里用 `new RenameTaskManager()` 或 Mockito 的 `@InjectMocks`）。测试环境没有启动 Spring 容器，`SpringUtils` 内部的静态 `beanFactory` 是 `null`，直接构造会抛 `NullPointerException`。解决办法：用 Mockito 的 `mockStatic(SpringUtils.class)` 在构造对象**期间**临时拦截 `SpringUtils.getBean("virtualScheduledExecutor")` 调用，构造完成后再用反射把 mock 的 `renameDetailService` 注入进去（因为 `mockStatic` 的拦截范围只在 try-with-resources 块内有效，不能像 `@InjectMocks` 那样直接用注解自动完成，需要手动分两步）。

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/RenameTaskManager.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/RenameTaskManagerTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.ruoyi.openliststrm.rename;

import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameDetailPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameDetailPlusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.scheduling.TaskScheduler;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RenameTaskManagerTest {

    @Mock
    private IRenameDetailPlusService renameDetailService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * RenameTaskManager 的字段初始化器会调用 SpringUtils.getBean("virtualScheduledExecutor")，
     * 必须在 mockStatic 作用域内完成构造，构造完再用反射注入 mock 的 renameDetailService。
     */
    private RenameTaskManager newService() throws Exception {
        RenameTaskManager service;
        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean("virtualScheduledExecutor")).thenReturn(mock(TaskScheduler.class));
            service = new RenameTaskManager();
        }
        Field field = RenameTaskManager.class.getDeclaredField("renameDetailService");
        field.setAccessible(true);
        field.set(service, renameDetailService);
        return service;
    }

    @Test
    void retryAllFailed_没有失败记录_返回0() throws Exception {
        RenameTaskManager service = newService();
        when(renameDetailService.count(any())).thenReturn(0L);

        RenameTaskManager.RetryOutcome outcome = service.retryAllFailed();

        assertEquals(0, outcome.retried());
        assertEquals(0, outcome.remaining());
        verify(renameDetailService, never()).list(any());
        verify(renameDetailService, never()).getById(any());
    }

    @Test
    void retryAllFailed_有失败记录_对每条调用getById触发重新执行() throws Exception {
        RenameTaskManager service = newService();
        when(renameDetailService.count(any())).thenReturn(2L);
        RenameDetailPlus a = new RenameDetailPlus();
        a.setId(5);
        RenameDetailPlus b = new RenameDetailPlus();
        b.setId(3);
        when(renameDetailService.list(any())).thenReturn(List.of(a, b));
        // getById 返回 null 时 executeRenameDetails 会记日志后直接返回，不会碰文件系统，
        // 本测试只关心 retryAllFailed 是否正确对每条失败记录发起了重新执行
        when(renameDetailService.getById(any())).thenReturn(null);

        RenameTaskManager.RetryOutcome outcome = service.retryAllFailed();

        assertEquals(2, outcome.retried());
        assertEquals(0, outcome.remaining());
        verify(renameDetailService).getById(5);
        verify(renameDetailService).getById(3);
    }

    @Test
    void retryAllFailed_单条记录抛异常不影响其余记录处理() throws Exception {
        RenameTaskManager service = newService();
        when(renameDetailService.count(any())).thenReturn(2L);
        RenameDetailPlus a = new RenameDetailPlus();
        a.setId(5);
        RenameDetailPlus b = new RenameDetailPlus();
        b.setId(3);
        when(renameDetailService.list(any())).thenReturn(List.of(a, b));
        when(renameDetailService.getById(5)).thenThrow(new RuntimeException("模拟异常"));
        when(renameDetailService.getById(3)).thenReturn(null);

        RenameTaskManager.RetryOutcome outcome = service.retryAllFailed();

        assertEquals(2, outcome.retried());
        verify(renameDetailService).getById(5);
        verify(renameDetailService).getById(3);
    }

    @Test
    void retryAllFailed_失败记录超过200条上限_remaining正确() throws Exception {
        RenameTaskManager service = newService();
        when(renameDetailService.count(any())).thenReturn(210L);
        RenameDetailPlus a = new RenameDetailPlus();
        a.setId(1);
        when(renameDetailService.list(any())).thenReturn(List.of(a));
        when(renameDetailService.getById(any())).thenReturn(null);

        RenameTaskManager.RetryOutcome outcome = service.retryAllFailed();

        assertEquals(1, outcome.retried());
        assertEquals(209, outcome.remaining());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=RenameTaskManagerTest -q`

预期：FAIL，编译失败——`RenameTaskManager` 没有 `retryAllFailed()` 方法，也没有 `RetryOutcome` 类型。

- [ ] **步骤 3：实现该方法**

`RenameTaskManager.java` 顶部已经导入了 `com.baomidou.mybatisplus.core.conditions.query.QueryWrapper`（第3行），本任务复用它，不需要新增任何 import。

在 `executeRenameDetails` 方法（第70-101行）之后加：

```java
    /**
     * 批量重试所有失败的重命名记录（最多重试最新 200 条）。
     * executeRenameDetails 本身只在内部处理了 IOException，其他运行时异常会往外抛，
     * 这里必须对每条记录单独 try/catch，避免一条异常中断整批。
     */
    public RetryOutcome retryAllFailed() {
        QueryWrapper<RenameDetailPlus> countWrapper = new QueryWrapper<>();
        countWrapper.eq("status", "0");
        long total = renameDetailService.count(countWrapper);
        if (total == 0) {
            return new RetryOutcome(0, 0);
        }

        QueryWrapper<RenameDetailPlus> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "0")
                .select("id")
                .orderByDesc("create_time")
                .last("LIMIT 200");
        List<RenameDetailPlus> failed = renameDetailService.list(wrapper);
        for (RenameDetailPlus detail : failed) {
            try {
                executeRenameDetails(detail.getId(), null, null, null, null);
            } catch (Exception e) {
                log.warn("retryAllFailed: 重试重命名明细失败 id={}", detail.getId(), e);
            }
        }
        return new RetryOutcome(failed.size(), (int) total - failed.size());
    }

    /**
     * @param retried   本次提交重试的记录数
     * @param remaining 超出 200 条上限、未处理的剩余失败记录数
     */
    public record RetryOutcome(int retried, int remaining) {}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -Dtest=RenameTaskManagerTest -q`

预期：PASS，4 个测试全部通过。

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/rename/RenameTaskManager.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/rename/RenameTaskManagerTest.java
git commit -m "feat: RenameTaskManager新增批量重试全部失败重命名记录的能力"
```

---

### 任务 4：Bot 层新增三个指令

**背景：** 在 `StrmBot.java` 里注册 `/retry`、`/rename`、`/checkorphan` 三个指令，写法对齐现有 `strm()`/`sync()`。

**重要：** 现有代码里 `strmDir()`/`syncDir()` 两个 Ability 用 `SpringUtils.getBean("strmService")`/`SpringUtils.getBean("copyService")`（按字符串 Bean 名查找）来拿 Service 实例，但 `StrmServiceImpl`/`CopyServiceImpl` 只标了普通 `@Service` 没有指定名字，Spring 默认生成的 Bean 名其实是 `"strmServiceImpl"`/`"copyServiceImpl"`，这两处字符串查找**很可能是现有代码里的 bug**（不在本次改动范围内，已经单独记录，不要在这个任务里顺手"修"它）。本任务新增的三个指令**不要复制这个按字符串查找的写法**，一律用类型安全的 `SpringUtils.getBean(Class)` 重载（`ruoyi-common/src/main/java/com/ruoyi/common/utils/spring/SpringUtils.java` 第60行，按类型查找，不依赖猜 Bean 名字符串）。

`/rename` 用到的 `OpenListStrmTask` 例外——它本身标了 `@Component("openListStrmTask")` 显式指定了 Bean 名（`task/OpenListStrmTask.java` 第26行），现有 `strm()`/`sync()` 已经在用 `SpringUtils.getBean("openListStrmTask")` 这个写法且是正确的，新指令继续沿用即可。

本任务不写单元测试——`Ability` 的构建/触发依赖 Telegram 运行时上下文，与代码库里现有的 `strm()`/`sync()`/`strmDir()`/`syncDir()` 等指令一致，历史上都是手动验证，不做单元测试。

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/tg/StrmBot.java`

- [ ] **步骤 1：在 `registerCommands()` 里追加三条命令**

把：

```java
        commands.add(new BotCommand("syncdir", "同步openlist指定目录"));
```

改成：

```java
        commands.add(new BotCommand("syncdir", "同步openlist指定目录"));
        commands.add(new BotCommand("retry", "重试所有失败任务"));
        commands.add(new BotCommand("rename", "执行重命名任务"));
        commands.add(new BotCommand("checkorphan", "执行重命名一致性检查"));
```

- [ ] **步骤 2：加 import**

在文件顶部 import 区加：

```java
import com.ruoyi.openliststrm.orphan.IRenameOrphanScanService;
import com.ruoyi.openliststrm.rename.RenameTaskManager;
```

（`com.ruoyi.openliststrm.service.ICopyService` 和 `com.ruoyi.openliststrm.service.IStrmService` 已经在文件顶部第5-6行导入过，不要重复添加，否则会有重复 import 编译错误）

- [ ] **步骤 3：新增 `retry()` Ability**

在 `syncDir()` 方法（第139-181行）之后、类结束 `}` 之前加：

```java
    public Ability retry() {
        return Ability.builder()
                .name("retry")
                .info("重试所有失败任务")
                .privacy(CREATOR)
                .locality(USER)
                .input(0)
                .action(ctx -> {
                    try {
                        ThreadTraceIdUtil.initTraceId();
                        silent.send("==开始重试所有失败任务==", ctx.chatId());
                        IStrmService strmService = SpringUtils.getBean(IStrmService.class);
                        ICopyService copyService = SpringUtils.getBean(ICopyService.class);
                        RenameTaskManager renameTaskManager = SpringUtils.getBean(RenameTaskManager.class);

                        IStrmService.RetryOutcome strmOutcome = strmService.retryAllFailed();
                        ICopyService.RetryOutcome copyOutcome = copyService.retryAllFailed();
                        RenameTaskManager.RetryOutcome renameOutcome = renameTaskManager.retryAllFailed();

                        int totalRetried = strmOutcome.retried() + copyOutcome.retried() + renameOutcome.retried();
                        if (totalRetried == 0) {
                            silent.send("没有需要重试的失败记录", ctx.chatId());
                        } else {
                            StringBuilder sb = new StringBuilder("==已提交重试请求==\n");
                            sb.append("STRM生成：").append(strmOutcome.retried()).append(" 条");
                            appendRemainingHint(sb, strmOutcome.remaining());
                            sb.append("\n同步：").append(copyOutcome.retried()).append(" 条");
                            appendRemainingHint(sb, copyOutcome.remaining());
                            sb.append("\n重命名：").append(renameOutcome.retried()).append(" 条");
                            appendRemainingHint(sb, renameOutcome.remaining());
                            silent.send(sb.toString(), ctx.chatId());
                        }
                    } finally {
                        MDC.clear();
                    }
                })
                .build();
    }

    private void appendRemainingHint(StringBuilder sb, int remaining) {
        if (remaining > 0) {
            sb.append("（还有 ").append(remaining).append(" 条未提交，可到网页端处理）");
        }
    }
```

- [ ] **步骤 4：新增 `rename()` Ability**

紧接着 `retry()` 之后加：

```java
    public Ability rename() {
        return Ability.builder()
                .name("rename")
                .info("执行重命名任务")
                .privacy(CREATOR)
                .locality(USER)
                .input(0)
                .action(ctx -> {
                    try {
                        ThreadTraceIdUtil.initTraceId();
                        silent.send("==开始执行重命名任务==", ctx.chatId());
                        OpenListStrmTask openListStrmTask = SpringUtils.getBean("openListStrmTask");
                        openListStrmTask.rename();
                        silent.send("==执行重命名任务完成==", ctx.chatId());
                    } finally {
                        MDC.clear();
                    }
                })
                .build();
    }
```

- [ ] **步骤 5：新增 `checkOrphan()` Ability**

紧接着 `rename()` 之后加：

```java
    public Ability checkOrphan() {
        return Ability.builder()
                .name("checkorphan")
                .info("执行重命名一致性检查")
                .privacy(CREATOR)
                .locality(USER)
                .input(0)
                .action(ctx -> {
                    try {
                        ThreadTraceIdUtil.initTraceId();
                        silent.send("==开始执行一致性检查==", ctx.chatId());
                        IRenameOrphanScanService scanService = SpringUtils.getBean(IRenameOrphanScanService.class);
                        IRenameOrphanScanService.ScanSummary summary = scanService.scan();
                        String text = "==一致性检查完成==\n" +
                                "本地文件丢失：" + summary.localMissing() + " 条\n" +
                                "网盘源丢失：" + summary.sourceMissing() + " 条\n" +
                                "已恢复正常：" + summary.resolved() + " 条\n" +
                                "无法解析跳过：" + summary.unparsable() + " 条" +
                                (summary.localMissing() + summary.sourceMissing() > 0
                                        ? "\n（如有待处理项，请到网页端\"重命名一致性检查\"页面确认清理）"
                                        : "");
                        silent.send(text, ctx.chatId());
                    } finally {
                        MDC.clear();
                    }
                })
                .build();
    }
```

- [ ] **步骤 6：编译验证**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am compile -q`

预期：BUILD SUCCESS，无编译错误。

- [ ] **步骤 7：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/tg/StrmBot.java
git commit -m "feat: Telegram Bot新增/retry /rename /checkorphan三个指令"
```

---

### 任务 5：全量验证

- [ ] **步骤 1：后端全量测试**

运行：`mvn -f "ruoyi-openliststrm/pom.xml" -am test -q`

预期：BUILD SUCCESS，所有测试通过（含本计划新增的 10 个测试：任务1的3个 + 任务2的3个 + 任务3的4个）。

- [ ] **步骤 2：后端全量打包**

运行：`mvn clean package -DskipTests`（仓库根目录）

预期：BUILD SUCCESS。

- [ ] **步骤 3：手动功能验证（需要真实 Telegram Bot 环境）**

1. 启动后端，确认 Telegram Bot 正常注册（日志无 `注册tg命令失败`/`初始化TelegramBotsApi失败` 报错）
2. 在 Telegram 客户端里给 Bot 发送 `/retry`：
   - 先造几条失败记录（比如手动改一条 `openlist_strm.strm_status` 为 `'0'`），确认收到"已提交重试请求"消息且数字正确
   - 清空失败记录后再发一次，确认收到"没有需要重试的失败记录"
3. 发送 `/rename`，确认收到"开始执行重命名任务"和"执行重命名任务完成"两条消息，且已启用的重命名任务确实被触发
4. 发送 `/checkorphan`，确认收到扫描汇总消息，数字与网页端"重命名一致性检查"页面看到的一致
5. 确认非 `CREATOR`（非管理员）身份发送这三个指令不会被响应（`privacy(CREATOR)` 生效）

（该步骤依赖真实 Telegram Bot Token 和网络环境，无法在本计划中自动化，执行时由人工在实际部署环境中完成）

---

## 执行说明

计划文件已保存到 `docs/superpowers/plans/2026-07-20-tg-bot-command-expansion.md`。两种执行方式：

1. **子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代
2. **内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点
