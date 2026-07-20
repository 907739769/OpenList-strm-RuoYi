# Telegram Bot 指令扩展设计文档

## 背景与目标

现有 Telegram Bot（`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/tg/`）基于 `AbilityBot` 框架，只有 4 个管理员指令：`/strm`（执行全部 STRM 任务）、`/strmdir <路径>`（生成指定路径 STRM）、`/sync`（执行全部同步任务）、`/syncdir <源路径#目标路径>`（同步指定目录）。全部指令都要求 `privacy(CREATOR)`，仅管理员可用。

另有一个独立的通知通道 `TgHelper`/`TgSendMsg`，目前只用于失败告警推送（复制任务失败、复制任务监控超时、文件监控丢失事件），不接受指令。

本次扩展目标：管理员在手机上不打开网页也能处理最常见的运维动作——重试失败任务、执行重命名任务、触发一致性检查扫描。

## 范围

**包含：**
- `/retry`：一次性重试 STRM 生成失败 + 同步失败 + 重命名失败三类记录
- `/rename`：执行全部启用的重命名任务
- `/checkorphan`：触发重命名一致性检查扫描（[2026-07-19-rename-orphan-consistency-check-design.md](2026-07-19-rename-orphan-consistency-check-design.md) 里做的功能），回复扫描汇总

**不包含（本次不做）：**
- 状态/任务列表只读查询指令
- 每日汇总定时推送
- 非管理员权限模型（只读用户等）
- 在 TG 里直接批量清理/忽略一致性检查的待处理项（保持"网页端人工二次确认"的安全策略）

## 现状代码基础

- `StrmBot extends AbilityBot`：每个指令是一个 `Ability`，`registerCommands()` 里注册 Bot 命令菜单，`creatorId()` 决定谁是 `CREATOR`
- 现有指令模式（以 `strm()` 为例）：`Ability.builder().name(...).info(...).privacy(CREATOR).locality(USER).input(0).action(ctx -> { ThreadTraceIdUtil.initTraceId(); silent.send("==开始...=="); ...; silent.send("==...完成=="); } finally { MDC.clear(); }).build()`
- 需要参数的指令（`strmdir`/`syncdir`）用 `ctx.firstArg()` 取内联参数，取不到就 `silent.forceReply(提示文案, chatId)`，并额外注册一个 `.reply(...)` 处理用户对该提示的回复
- Bot 本身不是 Spring Bean（`TgBotRegister` 手动 `new StrmBot(...)`），指令内部通过 `SpringUtils.getBean(...)` 拿业务 Service

## 数据基础（各表"失败"状态取值）

| 表 | 状态字段 | 失败取值 |
|---|---|---|
| `openlist_strm` | `strm_status` | `"0"` |
| `openlist_copy` | `copy_status` | `"2"` |
| `rename_detail` | `status` | `"0"` |

## 新增能力

### 1. `/retry` —— 批量重试三类失败记录

新增三个"查询失败记录并全部重试"的业务方法（现有 `retryStrm`/`retryCopy`/`executeRenameDetails` 都需要外部先传具体 ID 列表，没有"查全部失败再重试"的封装）：

- `IStrmService.retryAllFailed()`：`int` 返回值。查询 `openlist_strm` 中 `strm_status='0'` 的记录，按创建时间倒序，最多取 200 条，取 ID 列表调用现有 `retryStrm(idList)`，返回实际重试的记录数
- `ICopyService.retryAllFailed()`：同上逻辑，查询 `openlist_copy` 中 `copy_status='2'`，调用现有 `retryCopy(idList)`
- `RenameTaskManager.retryAllFailed()`：查询 `rename_detail` 中 `status='0'`，最多 200 条，逐条调用现有 `executeRenameDetails(id, null, null, null, null)`（不传标题/年份/季/集覆盖值，即保持原有解析结果重新执行），返回处理的记录数

三个方法都只是"查询 + 调度现有重试方法"的薄封装，不改动底层重试/执行逻辑本身。单条记录重试失败不应中断整批——这个保证已经由被复用的 `retryStrm`/`retryCopy`/`executeRenameDetails` 自身提供（它们内部已经是"单条隔离失败"的实现），封装方法不需要额外包一层 try/catch。

超过 200 条上限时，只处理最新的 200 条，不隐瞒截断：Bot 回复文案里注明"还有 N 条未重试，可到网页端处理"。

Bot 侧 `retry()` Ability 依次调用三个方法，拼接汇总回复，例如：
```
==重试完成==
STRM生成：重试 12 条
同步：重试 3 条（还有 5 条未重试，可到网页端处理）
重命名：重试 0 条
```
三类都是 0 条时回复"没有需要重试的失败记录"。

### 2. `/rename` —— 执行全部启用的重命名任务

直接复用已存在的 `OpenListStrmTask.rename()`（`task/OpenListStrmTask.java`，供 Quartz 定时任务复用的同一个方法），写法与 `/strm`/`/sync` 完全一致。

### 3. `/checkorphan` —— 触发一致性检查扫描

直接复用 `IRenameOrphanScanService.scan()`（重命名一致性检查功能已实现的扫描入口），拿到返回的 `ScanSummary(localMissing, sourceMissing, resolved, unparsable)`，格式化成消息回复，例如：
```
==一致性检查完成==
本地文件丢失：2 条
网盘源丢失：1 条
已恢复正常：0 条
无法解析跳过：0 条
（如有待处理项，请到网页端"重命名一致性检查"页面确认清理）
```

## Bot 层改动

`StrmBot.java`：
- `registerCommands()` 追加三条 `BotCommand`："retry"/"重试所有失败任务"、"rename"/"执行重命名任务"、"checkorphan"/"执行重命名一致性检查"
- 新增 `retry()`、`rename()`、`checkOrphan()` 三个 `Ability`：均为 `privacy(CREATOR)`、`locality(USER)`、`input(0)`，不需要参数、不需要 `forceReply` 交互；内部结构与现有 `strm()`/`sync()` 一致（`ThreadTraceIdUtil.initTraceId()` → `silent.send` 开始提示 → 调用业务方法 → `silent.send` 完成提示（含汇总数字）→ `finally { MDC.clear(); }`）

## 边界情况

- 三类记录都没有失败时：`/retry` 回复"没有需要重试的失败记录"，不发起任何重试调用
- 超过 200 条上限：只处理最新 200 条，回复中注明剩余数量
- `/checkorphan` 扫描本身耗时可能较长（涉及网盘 API 调用）：与现有 `/strm`/`/sync` 一致，同步执行、执行期间 Bot 不响应新指令是可接受的（沿用现有指令的同步执行模型，不引入新的异步/进度反馈机制）
- 三个新指令均不涉及文件删除等破坏性操作，`/retry` 是"重新尝试"而非删除，`/checkorphan` 只报告不清理，风险可控

## 测试计划

- 单元测试：`IStrmService.retryAllFailed()`、`ICopyService.retryAllFailed()`、`RenameTaskManager.retryAllFailed()` 三个新方法，覆盖：无失败记录返回0、有失败记录正确调用底层重试方法、超过200条只取最新200条
- Bot 层（`StrmBot`/`Ability` 构建）不写单元测试，与现有 `strm()`/`sync()` 等指令一致（本代码库对 Bot 指令层没有单元测试传统，AbilityBot 的 `Ability` 构建/触发依赖 Telegram 运行时上下文，难以低成本单测，历史上都是手动验证）
- 手动验证：真实 Telegram 环境里分别触发 `/retry`（造几条失败记录）、`/rename`、`/checkorphan`，确认回复文案和实际执行效果符合预期
