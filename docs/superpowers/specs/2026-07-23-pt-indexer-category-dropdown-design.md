# PT 索引器分类下拉优化设计

## 背景

PT 索引器管理页面（PC + 移动端）的「分类」字段目前是纯文本输入框，用户需要自己手填逗号分隔的 Torznab 分类 ID（如 `5000,5030`），容易记错、拼错。索引器本身通过 Torznab `t=caps` 接口可以查询到自己支持的完整分类树（含父子层级），但后端目前只解析了 caps 响应里的 `searching` 节点（用于判断 imdbid/tmdbid 搜索能力），完全没解析 `categories` 节点。

目标：把分类字段改成从索引器的 caps 接口获取分类树，用户可以下拉勾选，而不是手填 ID。

## 范围

仅涉及 PT 索引器（`ruoyi-openliststrm/.../pt/indexer/`）分类字段的获取与展示方式，不改变分类的存储格式（仍是逗号分隔 ID 字符串），不改变 Torznab 搜索时 `cat` 参数的拼接逻辑。

## 后端改动

### 1. 分类解析：`TorznabCapsParser`

新增 `parseCategories(String xml)` 方法，解析 caps 响应中的：

```xml
<categories>
  <category id="5000" name="TV">
    <subcat id="5040" name="TV/HD"/>
    <subcat id="5030" name="TV/SD"/>
  </category>
  <category id="2000" name="Movies">
    <subcat id="2040" name="Movies/HD"/>
  </category>
</categories>
```

返回父子两层结构（Torznab 分类规范最多两层，不需要处理更深层级）。与现有 `parse()` 方法（解析 `searching` 节点）并列，不复用同一次 DOM 遍历也不冲突——两者都是从同一份 caps XML 里各取所需节点。

### 2. 新增 DTO：`CategoryOption`

同包（`com.ruoyi.openliststrm.pt.indexer`）下新增：

```java
public record CategoryOption(Integer id, String name, List<CategoryOption> children) {}
```

`children` 为空列表（非 null）表示无子分类。

### 3. 客户端方法：`TorznabClient.getCategories`

新增 `getCategories(PtIndexerPlus indexer)`：
- 复用现有 `buildUrl(indexer, "caps")` 构造请求 URL（与 `getCaps`/`testConnection` 共享同一条 URL 构造路径）
- url/apiKey 为空时抛 `IllegalArgumentException`（与 `testConnection` 现有校验行为一致）
- 请求成功后用 `TorznabCapsParser.parseCategories` 解析响应体，返回 `List<CategoryOption>`
- 请求异常（网络错误、HTTP 非 2xx、XML 解析失败）不吞掉，向上抛出，由 controller 层统一转成 `Result.error`

不做缓存：分类获取是编辑弹窗内的一次性手动操作（点按钮触发），没有跨请求复用的价值，且索引器分类可能变化，缓存反而可能导致展示过期数据。

### 4. 新增接口：`PtIndexerRestController`

仿照现有 `/test` 端点新增：

```java
@PostMapping("/categories")
public Result<List<CategoryOption>> categories(@RequestBody PtIndexerPlus entity) {
    if (StringUtils.isBlank(entity.getUrl()) || StringUtils.isBlank(entity.getApiKey())) {
        return Result.error("接口地址与 apikey 不能为空");
    }
    try {
        return Result.success(torznabClient.getCategories(entity));
    } catch (IllegalArgumentException e) {
        return Result.error(e.getMessage());
    } catch (Exception e) {
        return Result.error("获取分类失败：" + e.getMessage());
    }
}
```

- 路径：`POST /api/openliststrm/pt-indexers/categories`
- 请求体：整个 `PtIndexerPlus` 表单当前值（无需先保存到库，与 `/test` 一致）
- 返回：`Result<List<CategoryOption>>`

## 前端改动（PC 与移动端页面同步实现）

涉及文件：
- `openlist-web/src/api/openlist/ptIndexer.ts`
- `openlist-web/src/composables/usePtIndexer.ts`
- `openlist-web/src/views/openlist/ptIndexer/index.vue`
- `openlist-web/src/views-mobile/ptIndexer/index.vue`

### 1. API 封装

`ptIndexer.ts` 新增：

```ts
export function getPtIndexerCategoriesApi(data: any) {
  return request({ url: '/openliststrm/pt-indexers/categories', method: 'post', data })
}
```

### 2. 交互：「获取分类」按钮

url、apiKey 输入框旁新增按钮，点击后调用上述接口：
- url 或 apiKey 为空时按钮禁用
- 请求中按钮显示 loading
- 成功：把返回的分类树存入表单级状态 `categoryOptions`
- 失败：`ElMessage.error(msg)`，`categoryOptions` 保持原值不清空（避免用户已获取过一次后误触发失败请求把已有选项清掉）

放在 `usePtIndexer.ts` composable 里实现（`categoryOptions` ref + `fetchCategories()` 方法），PC/移动端两个页面共用同一份逻辑，与现有「测试连接」的实现方式保持一致的位置和风格。

### 3. 分类下拉

原 `el-input` 替换为：

```html
<el-form-item label="分类" prop="categories">
  <el-select
    v-model="categoriesSelected"
    multiple filterable allow-create default-first-option
    collapse-tags collapse-tags-tooltip
    placeholder="点击「获取分类」后选择，或直接输入分类 ID"
  >
    <el-option-group
      v-for="parent in categoryOptions"
      :key="parent.id"
      :label="`${parent.name} (${parent.id})`"
    >
      <el-option :label="`${parent.name} (${parent.id})`" :value="String(parent.id)" />
      <el-option
        v-for="child in parent.children"
        :key="child.id"
        :label="`　${child.name} (${child.id})`"
        :value="String(child.id)"
      />
    </el-option-group>
  </el-select>
</el-form-item>
```

- `allow-create`：保留手动输入任意 ID 的能力，兼容"还没获取分类""获取失败""分类树里没有的自定义 ID"等场景
- 底层存储不变：`form.categories` 仍是逗号分隔字符串，`categoriesSelected` 是 `usePtIndexer.ts` 里新增的一个计算属性（getter 按 `,` split 成数组，setter 按 `,` join 回字符串），只在展示层做转换，提交给后端的格式与现在完全一致，数据库/后端实体都不需要改动
- 已保存索引器的分类 ID 在未点「获取分类」前，下拉会以原始 ID 值展示（Element Plus 对无匹配 option 的已选值按原始值展示）；点击「获取分类」后自动替换成带名称的展示

## 不做的事

- 不改变 `categories` 字段的存储格式（仍是逗号分隔字符串）
- 不做后端缓存
- 不做分类获取的自动触发（保持手动按钮触发）
- 不限制用户只能选接口返回的分类（保留 `allow-create` 手动输入兜底）

## 测试计划

- 后端单元测试：`TorznabCapsParser.parseCategories`，用含多级 `subcat` 的真实 Torznab caps 响应样例验证解析结果（父分类、子分类、空 children）
- 后端测试：`/categories` 端点的参数校验（url/apiKey 为空）与异常降级（请求异常时返回 `Result.error`）
- 前端：`npm run dev` 起本地服务，浏览器验证下拉分组展示、多选、`allow-create` 手动输入、按钮 loading/disabled 状态；若无可用真实 PT 索引器测试地址，用临时 mock 数据验证 UI 交互，不依赖真实网络请求
