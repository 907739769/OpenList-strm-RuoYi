# 重命名文件名模板 + 分类规则可视化配置 设计文档

## 背景与目标

现有智能重命名功能（`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/monitor/processor/MediaRenameProcessor.java`）里，最终生成目标路径依赖两样东西：

1. **文件名模板**：`DEFAULT_FILENAME_TEMPLATE` 常量，Pebble 语法字符串，写死在 Java 代码里；`RenameTaskRestController.java` 里为了支撑"模板测试预览"接口又硬拷贝了一份完全相同的字符串常量，两处并非同一来源。
2. **分类规则**：`defaultRules()` 方法，`Map<String, List<CategoryRule>>` 静态常量，按 `genre/originalLanguage/originCountries` 顺序匹配决定"国漫/日番/华语电影/欧美剧"等目录归类，同样写死在代码里，无法在不改代码、不重新打包部署的情况下调整。

数据库里 `rename_task`、`rename_detail` 两张表完全没有为模板/规则预留字段，前端也只有一个不落库的一次性"测试预览"入口（`renameTask/index.vue` 的测试弹窗）能临时编辑模板字符串。

本次目标：让管理员能在网页端可视化地修改文件名命名模板和分类目录规则，不用改代码重新部署。

## 范围

**包含：**
- 文件名模板：全局唯一一份，网页端编辑（保留 Pebble 文本语法），带变量参考和实时预览
- 分类规则：全局唯一一份，网页端可视化编辑器（增/删/改/拖拽排序），电影和剧集分开维护
- 保存时强校验（模板试渲染、规则兜底完整性检查），只影响保存后新处理的文件

**不包含（本次不做）：**
- 按重命名任务（`rename_task`）分别配置不同模板/规则（多任务差异化配置），本次只做全局唯一一套
- 对已有 `rename_detail` 历史记录做批量重新应用（重新计算路径并移动文件）
- Token 式模板拼接器（如 Sonarr 风格），本次继续用 Pebble 文本语法
- 模板/规则的多版本历史、审计日志、并发锁保护

## 现状代码基础

- `MediaRenameProcessor`（第50行）：`DEFAULT_FILENAME_TEMPLATE` 是 `private static final String` 常量，语法示例：`{{ title }} {% if year %}({{ year }}){% endif %}/...`，变量全部来自 `MediaInfo` 通过 `mapper.convertValue(info, Map.class)` 整体注入模板上下文（`PebbleRenderer.java` 第29行），故 `MediaInfo` 任意字段理论上都可在模板中引用。
- `defaultRules()`（第444-467行）：电影 3 条规则 + 电视剧 8 条规则，条件字段只有三类（`genreIds`/`originalLanguage`/`originCountries`），`classify()`（第436-442行）按列表顺序遍历、短路命中，最后一条为无条件兜底。
- `PebbleRenderer.render()`（第26-36行）：渲染异常直接 `throw new RuntimeException(e)`，被上游 `handleFile` 的 `catch (Exception e)` 吞掉只打日志，不触发失败通知——这是当前"模板出错=静默失败"的问题所在，本次要在保存阶段拦截。
- `RenameTaskRestController.java`（第29行、第145-177行）：`/test/{id}` 和 `/test/parse` 两个预览接口已经打通"自定义模板字符串→渲染预览"链路，可以复用其渲染调用方式。
- 数据库层：`rename_task`、`rename_detail` 均无 template/rule 相关字段，无预留设计，需要全新建模。

## 数据模型

**文件名模板**：不新建表，复用 RuoYi 自带的 `sys_config` 参数表，新增一条配置：
- `config_key = 'rename.filename.template'`，`config_value` 为 Pebble 模板字符串
- 找不到该配置时，代码里 fallback 到内置默认模板（即现有 `DEFAULT_FILENAME_TEMPLATE` 的内容），保证升级平滑、老环境不用手动初始化数据

**分类规则**：新建 `rename_category_rule` 表：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | 主键 |
| media_type | varchar(10) | `movie` / `tv` |
| seq | int | 排序序号，越小优先级越高 |
| rule_name | varchar(64) | 展示名，如"国漫"，同时作为审计/日志里的规则标识 |
| genre_ids | varchar(255) | 逗号分隔的 TMDB genre id，空表示不限 |
| original_languages | varchar(255) | 逗号分隔语言码，空表示不限 |
| origin_countries | varchar(255) | 逗号分隔国家码，空表示不限 |
| target_dir | varchar(128) | 命中后的目标目录名 |
| is_fallback | tinyint(1) | 是否为兜底规则（每个 media_type 有且只有一条，seq 必须最大） |
| create_time / update_time | datetime | 常规审计字段 |

初始化数据：随本次建表的 SQL 迁移脚本（`ruoyi-common/src/main/resources/sql/` 下新增一个 `2026xxxx-rename-category-rule.sql`，由 `MysqlDdl` 自动执行）一并 `INSERT` 现有 `defaultRules()` 的 11 条规则（电影3+剧集8）作为种子数据，保证老环境升级后行为不变，不需要额外的应用启动检查逻辑。

## 后端改造

- `MediaRenameProcessor`：`DEFAULT_FILENAME_TEMPLATE` 改为运行时从 `ISysConfigService.selectConfigByKey("rename.filename.template")` 读取（该服务本身自带缓存，改动后调用 `resetConfigCache` 即可生效，无需额外加缓存层）；找不到配置 fallback 内置默认值。
- `defaultRules()` 改为 `ICategoryRuleService.listEnabledRules(mediaType)`：从 `rename_category_rule` 按 `seq` 升序查询并转换为运行时 `CategoryRule` 对象；`classify()` 的匹配逻辑（顺序遍历+短路+末位兜底）不变。
- `RenameTaskRestController` 里重复的 `DEFAULT_FILENAME_TEMPLATE` 常量删除，`/test/parse` 等预览接口改为调用同一个模板读取入口，保证预览和实际渲染永远一致。
- 新增 `RenameTemplateConfigController`：
  - `GET /api/openliststrm/rename-config/template`：返回当前模板字符串
  - `PUT /api/openliststrm/rename-config/template`：保存前用内置示例 `MediaInfo`（覆盖 season/episode/resolution/videoCodec 等常见字段的典型取值）调用 `PebbleRenderer.render()` 试渲染一次，渲染异常则返回 400 + 具体报错信息（Pebble 异常信息透传），渲染成功才写入 `sys_config` 并刷新缓存
- 新增 `CategoryRuleController`：规则的增删改查 + 排序（`PUT /reorder` 传入完整 id 顺序数组）：
  - 保存单条规则时校验 `target_dir` 非空
  - 任何写操作后校验每个 `media_type` 恰好存在一条 `is_fallback=1` 且其 `seq` 是该 `media_type` 下最大值，不满足则拒绝保存并提示"必须保留且只能保留一条兜底规则，且需排在最后"

## 前端设计

新增页面（挂载在系统管理菜单下，命名如"重命名规则设置"，仅管理员可见，复用现有菜单权限模型）：

**Tab 1：文件名模板**
- Pebble 文本框（沿用现有测试弹窗的输入体验），失焦或点击"预览"按钮后调用后端渲染接口，用固定的示例 `MediaInfo` 展示渲染结果
- 右侧变量参考卡片：列出 `title/year/season/episode/resolution/source/videoCodec/audioCodec/tags/releaseGroup/extension`，点击后插入到文本框光标位置
- 保存按钮调用 `PUT /template`，后端校验失败时在页面顶部展示具体错误文案

**Tab 2：分类规则**
- 电影/剧集分两个独立列表（对应 `media_type`）
- 每行：条件区（genre/语言/国家三个下拉多选，可为空表示不限）+ 目标目录名输入框 + 上下移动按钮（或拖拽手柄）+ 删除按钮
- 每个列表末尾固定一行"兜底规则"：条件区禁用置灰，只能编辑目标目录名，没有删除按钮，且不可通过排序移出最后一位
- 顶部"+ 新增规则"按钮，新增规则默认插入在兜底行之前
- 保存调用后端接口，校验失败（如目录名为空）在对应行内联提示

## 生效范围与错误处理

- 保存时强校验，渲染失败/兜底缺失直接拦截保存，不允许写入无效配置
- 保存成功后仅影响之后新处理的文件，不追溯、不批量重新应用到已有 `rename_detail` 记录
- 运行时 `PebbleRenderer.render()` 的异常处理保持现状不变（因为保存阶段已校验，运行时理论上不会再触发模板语法错误），只是日志里补充更明确的上下文（当前使用的模板内容摘要），便于后续排查
- 全局配置的并发写保护不做特殊处理（沿用 `sys_config` 现状的"后写覆盖"），与项目当前规模匹配

## 边界情况

- 表 `rename_category_rule` 为空（比如误删全部规则）：`ICategoryRuleService.listEnabledRules()` 查询为空时，`classify()` 无法匹配到任何规则，需要在代码层再加一道兜底——查询结果为空时使用一个硬编码的"未分类"目录名兜底，保证不会因为配置误删而导致重命名任务整体失败
- 老环境升级：建表迁移脚本自带 11 条种子数据 `INSERT`，随正常的迁移流程执行一次即可，保证升级后行为不变
- 模板变量拼写错误（引用了 `MediaInfo` 不存在的字段）：Pebble 对未定义变量默认渲染为空字符串而非报错，不在保存校验的拦截范围内，属于"用户自己造成的命名不完整"，不额外处理

## 测试计划

- 单元测试：
  - `ICategoryRuleService` 的增删改查、排序、"必须保留一条末位兜底规则"校验
  - `MediaRenameProcessor.classify()` 改造后使用种子数据的分类结果与原 `defaultRules()` 硬编码结果完全一致（回归保证）
  - 模板保存接口对合法模板/语法错误模板的响应行为
- 手动验证（浏览器）：
  - 规则编辑器的增删改拖拽排序、兜底行不可删除/不可移出末位
  - 模板变量插入、实时预览、保存报错展示
- 回归验证：改造完成后完整跑一遍现有重命名任务流程（源目录 → 解析 → 分类 → 渲染路径 → 落盘 → 刮削），确认默认配置下行为与改造前一致
