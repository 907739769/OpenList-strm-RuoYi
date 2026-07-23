# PT 索引器分类下拉 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 把 PT 索引器管理页面（PC + 移动端）的「分类」字段从手填逗号分隔 ID 的文本框，改成从索引器 Torznab `t=caps` 接口获取分类树后可勾选的下拉多选框。

**架构：** 后端新增 `TorznabCapsParser.parseCategories` 解析 caps 响应里此前被忽略的 `<categories>` 节点，`TorznabClient.getCategories` 发起请求并解析，`PtIndexerRestController` 新增 `POST /categories` 端点（仿照现有 `/test` 端点模式，接收表单当前值即可调用，无需先保存）。前端新增「获取分类」按钮触发请求，`el-select multiple filterable allow-create` 展示分组分类树，底层仍以逗号分隔字符串存储，通过一个计算属性做数组⇄字符串转换，后端/数据库存储格式不变。

**技术栈：** Java 25 + Spring Boot（后端），Vue 3 + Element Plus（前端），JUnit 5 + MockWebServer（后端测试）。

---

## 文件清单

| 文件 | 变更 | 职责 |
|---|---|---|
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/CategoryOption.java` | 新建 | 分类树节点 DTO |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabCapsParser.java` | 修改 | 新增 `parseCategories` 解析 `<categories>` 节点 |
| `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabCapsParserTest.java` | 修改 | `parseCategories` 单元测试 |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java` | 修改 | 新增 `getCategories` 请求 + 解析 |
| `ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java` | 修改 | `getCategories` 单元测试 |
| `ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtIndexerRestController.java` | 修改 | 新增 `POST /categories` 端点 |
| `openlist-web/src/api/openlist/ptIndexer.ts` | 修改 | 新增 `getPtIndexerCategoriesApi` |
| `openlist-web/src/composables/usePtIndexer.ts` | 修改 | 新增分类获取状态/方法 + 字符串⇄数组转换 |
| `openlist-web/src/views/openlist/ptIndexer/index.vue` | 修改 | PC 端分类字段改下拉 |
| `openlist-web/src/views-mobile/ptIndexer/index.vue` | 修改 | 移动端分类字段改下拉 |

---

### 任务 1：后端 —— 分类树 DTO 与 caps 分类解析

**文件：**
- 创建：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/CategoryOption.java`
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabCapsParser.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabCapsParserTest.java`

- [ ] **步骤 1：创建 `CategoryOption` DTO**

```java
package com.ruoyi.openliststrm.pt.indexer;

import java.util.List;

/**
 * 索引器 t=caps 探测出的分类树节点：父分类下可能带若干子分类（subcat）。
 *
 * @author Jack
 */
public record CategoryOption(Integer id, String name, List<CategoryOption> children) {
}
```

- [ ] **步骤 2：在 `TorznabCapsParserTest.java` 追加失败的测试**

在类内最后一个测试方法（`parse_非法XML_返回NONE而不抛异常`）之后、类结尾 `}` 之前插入：

```java
    @Test
    void parseCategories_正常响应_解析出父子分类树() {
        String xml = """
                <caps>
                  <categories>
                    <category id="2000" name="Movies">
                      <subcat id="2010" name="Movies/Foreign"/>
                      <subcat id="2020" name="Movies/Other"/>
                    </category>
                    <category id="5000" name="TV">
                      <subcat id="5040" name="TV/HD"/>
                    </category>
                  </categories>
                </caps>
                """;

        List<CategoryOption> categories = TorznabCapsParser.parseCategories(xml);

        assertEquals(2, categories.size());
        assertEquals(2000, categories.get(0).id());
        assertEquals("Movies", categories.get(0).name());
        assertEquals(2, categories.get(0).children().size());
        assertEquals(2010, categories.get(0).children().get(0).id());
        assertEquals("Movies/Foreign", categories.get(0).children().get(0).name());
        assertEquals(5000, categories.get(1).id());
        assertEquals(1, categories.get(1).children().size());
        assertEquals(5040, categories.get(1).children().get(0).id());
    }

    @Test
    void parseCategories_无subcat的分类_children为空列表() {
        String xml = """
                <caps>
                  <categories>
                    <category id="8000" name="Other"/>
                  </categories>
                </caps>
                """;

        List<CategoryOption> categories = TorznabCapsParser.parseCategories(xml);

        assertEquals(1, categories.size());
        assertTrue(categories.get(0).children().isEmpty());
    }

    @Test
    void parseCategories_无categories节点_返回空列表() {
        assertTrue(TorznabCapsParser.parseCategories("<caps></caps>").isEmpty());
    }

    @Test
    void parseCategories_空字符串或null_返回空列表() {
        assertTrue(TorznabCapsParser.parseCategories("").isEmpty());
        assertTrue(TorznabCapsParser.parseCategories(null).isEmpty());
    }

    @Test
    void parseCategories_非法XML_返回空列表而不抛异常() {
        assertTrue(TorznabCapsParser.parseCategories("<caps><categories>").isEmpty());
    }
```

在文件顶部的 import 区新增 `import java.util.List;`（其余 `import static org.junit.jupiter.api.Assertions.*` 已覆盖 `assertTrue`/`assertEquals`，无需改动）。

- [ ] **步骤 3：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorznabCapsParserTest -q`
预期：FAIL，报错 `cannot find symbol: method parseCategories`

- [ ] **步骤 4：在 `TorznabCapsParser.java` 实现 `parseCategories`**

在现有 `parse` 方法之后（第 43 行 `}` 之后）、`supportsParam` 方法之前插入：

```java

    /**
     * @param xml t=caps 的响应体，允许为 null/空/非法 XML
     * @return 解析出的分类树（父分类 + 子分类 subcat）；响应为空、解析失败、或没有 categories 节点时返回空列表
     */
    public static List<CategoryOption> parseCategories(String xml) {
        if (StringUtils.isBlank(xml)) {
            return List.of();
        }
        try {
            Document doc = SafeXmlDocuments.parse(xml);
            Element categoriesEl = firstChildElement(doc.getDocumentElement(), "categories");
            if (categoriesEl == null) {
                return List.of();
            }
            List<CategoryOption> result = new ArrayList<>();
            NodeList children = categoriesEl.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && "category".equals(node.getNodeName())) {
                    result.add(parseCategory((Element) node));
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static CategoryOption parseCategory(Element categoryEl) {
        Integer id = Integer.valueOf(categoryEl.getAttribute("id"));
        String name = categoryEl.getAttribute("name");
        List<CategoryOption> children = new ArrayList<>();
        NodeList subNodes = categoryEl.getChildNodes();
        for (int i = 0; i < subNodes.getLength(); i++) {
            Node node = subNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "subcat".equals(node.getNodeName())) {
                Element subEl = (Element) node;
                children.add(new CategoryOption(
                        Integer.valueOf(subEl.getAttribute("id")), subEl.getAttribute("name"), List.of()));
            }
        }
        return new CategoryOption(id, name, children);
    }
```

在文件顶部新增 import：

```java
import java.util.ArrayList;
import java.util.List;
```

- [ ] **步骤 5：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorznabCapsParserTest -q`
预期：PASS，全部测试通过（含原有测试）

- [ ] **步骤 6：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/CategoryOption.java ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabCapsParser.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabCapsParserTest.java
git commit -m "feat(pt): TorznabCapsParser新增caps分类树解析"
```

---

### 任务 2：后端 —— `TorznabClient.getCategories`

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java`
- 测试：`ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java`

- [ ] **步骤 1：在 `TorznabClientTest.java` 追加失败的测试**

在 `getCaps_HTTP错误码_返回NONE而不抛异常`（第 188-192 行）之后、`searchByExternalId_电影按imdbid拼URL_不带season和ep` 之前插入：

```java
    @Test
    void getCategories_正常响应_解析出分类树() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                <caps>
                  <categories>
                    <category id="5000" name="TV">
                      <subcat id="5040" name="TV/HD"/>
                    </category>
                  </categories>
                </caps>
                """));

        List<CategoryOption> categories = client.getCategories(indexer(null));

        assertEquals(1, categories.size());
        assertEquals(5000, categories.get(0).id());
        assertEquals("TV", categories.get(0).name());
        assertEquals(1, categories.get(0).children().size());
        assertEquals(5040, categories.get(0).children().get(0).id());
    }

    @Test
    void getCategories_请求参数正确_不带cat参数() throws Exception {
        server.enqueue(new MockResponse().setBody("<caps><categories/></caps>"));

        client.getCategories(indexer("5000,5030"));

        RecordedRequest request = server.takeRequest();
        assertEquals("caps", request.getRequestUrl().queryParameter("t"));
        assertEquals(null, request.getRequestUrl().queryParameter("cat"));
    }

    @Test
    void getCategories_HTTP错误码_抛IOException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThrows(IOException.class, () -> client.getCategories(indexer(null)));
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorznabClientTest -q`
预期：FAIL，报错 `cannot find symbol: method getCategories`

- [ ] **步骤 3：在 `TorznabClient.java` 实现 `getCategories`**

在 `getCaps` 方法之后（第 94 行 `}` 之后）、`searchByExternalId` 方法之前插入：

```java

    /**
     * 获取索引器支持的分类树（t=caps 响应中的 categories 节点），供前端分类下拉使用。
     * 与 {@link #getCaps} 不同，本方法不吞异常——前端需要区分"未获取"与"获取失败"并提示具体原因。
     *
     * @throws IOException              网络异常或 HTTP 非 2xx
     * @throws IllegalArgumentException 索引器地址非法
     */
    public List<CategoryOption> getCategories(PtIndexerPlus indexer) throws IOException {
        String body = execute(buildUrl(indexer, "caps"));
        return TorznabCapsParser.parseCategories(body);
    }
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl ruoyi-openliststrm -am test -Dtest=TorznabClientTest -q`
预期：PASS，全部测试通过（含原有测试）

- [ ] **步骤 5：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/pt/indexer/TorznabClient.java ruoyi-openliststrm/src/test/java/com/ruoyi/openliststrm/pt/indexer/TorznabClientTest.java
git commit -m "feat(pt): TorznabClient新增getCategories获取分类树"
```

---

### 任务 3：后端 —— `/categories` REST 端点

**文件：**
- 修改：`ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtIndexerRestController.java`

此端点是对已有 `TorznabClient` bean 和已有 `PtIndexerRestController` 的新增方法，不引入新 bean/新构造依赖，故不需要按 AGENTS.md 中"新增 bean/调度器"的强制 docker 启动验证规则；用 `mvn compile` + 手工 curl 验证即可。

- [ ] **步骤 1：在 `PtIndexerRestController.java` 新增端点**

在现有 `test` 方法（第 45-57 行）之后、类结尾 `}` 之前插入：

```java

    /**
     * 获取索引器支持的分类树（t=caps），供前端分类下拉使用。接收表单当前值，无需先保存。
     */
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

在文件顶部新增 import：

```java
import com.ruoyi.openliststrm.pt.indexer.CategoryOption;

import java.util.List;
```

- [ ] **步骤 2：编译验证**

运行：`mvn -pl ruoyi-openliststrm -am compile -q`
预期：编译成功，无报错

- [ ] **步骤 3：Commit**

```bash
git add ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/controller/api/PtIndexerRestController.java
git commit -m "feat(pt): 新增PT索引器分类获取接口 POST /categories"
```

---

### 任务 4：前端 —— API 封装

**文件：**
- 修改：`openlist-web/src/api/openlist/ptIndexer.ts`

- [ ] **步骤 1：新增 `getPtIndexerCategoriesApi`**

在文件末尾（`testPtIndexerApi` 之后）追加：

```ts
/** 获取索引器支持的分类树，传入表单当前值，无需先保存 */
export function getPtIndexerCategoriesApi(data: any) {
  return request.post('/openliststrm/pt-indexers/categories', data)
}
```

- [ ] **步骤 2：Commit**

```bash
git add openlist-web/src/api/openlist/ptIndexer.ts
git commit -m "feat(pt): 前端新增获取索引器分类树API"
```

---

### 任务 5：前端 —— `usePtIndexer.ts` 组合式函数改造

**文件：**
- 修改：`openlist-web/src/composables/usePtIndexer.ts`

- [ ] **步骤 1：更新 import**

将文件顶部的：

```ts
import {
  getPtIndexerListApi,
  addPtIndexerApi,
  updatePtIndexerApi,
  deletePtIndexerApi,
  testPtIndexerApi
} from '@/api/openlist/ptIndexer'
```

替换为：

```ts
import {
  getPtIndexerListApi,
  addPtIndexerApi,
  updatePtIndexerApi,
  deletePtIndexerApi,
  testPtIndexerApi,
  getPtIndexerCategoriesApi
} from '@/api/openlist/ptIndexer'
```

- [ ] **步骤 2：新增分类类型与状态、`fetchCategories` 方法、`categoriesSelected` 计算属性**

在 `interface PtIndexerQuery` 定义之后（第 16 行之后）新增类型：

```ts

interface CategoryOption {
  id: number
  name: string
  children: CategoryOption[]
}
```

在 `handleTest` 方法定义之后（第 78 行 `}` 之后）、`// ---------- 卡片勾选` 注释之前插入：

```ts

  // ---------- 分类获取（caps 接口） ----------
  const categoriesLoading = ref(false)
  const categoryOptions = ref<CategoryOption[]>([])

  const fetchCategories = async () => {
    if (!base.form.value.url || !base.form.value.apiKey) {
      ElMessage.warning('请先填写接口地址与 apikey')
      return
    }
    categoriesLoading.value = true
    try {
      categoryOptions.value = await getPtIndexerCategoriesApi(base.form.value) as unknown as CategoryOption[]
      ElMessage.success('分类获取成功')
    } catch (e) {
      // 失败提示已由 axios 拦截器统一弹出，见 handleTest 同类注释
      console.error('[PT索引器] 获取分类失败:', e)
    } finally {
      categoriesLoading.value = false
    }
  }

  // 分类字段落库/提交仍是逗号分隔字符串，仅在下拉展示层转换为数组
  const categoriesSelected = computed<string[]>({
    get: () => (base.form.value.categories ? String(base.form.value.categories).split(',').filter(Boolean) : []),
    set: (val: string[]) => {
      base.form.value.categories = val.length ? val.join(',') : undefined
    }
  })
```

- [ ] **步骤 3：导出新增的状态与方法**

将文件末尾的 `return { ... }`（第 131-136 行）：

```ts
  return {
    ...base, testLoading, handleTest,
    toggleSelect, handleCardClick, clearSelection,
    totalPages, prevPage, nextPage, handleSizeChange,
    searchCollapsed
  }
```

替换为：

```ts
  return {
    ...base, testLoading, handleTest,
    categoriesLoading, categoryOptions, fetchCategories, categoriesSelected,
    toggleSelect, handleCardClick, clearSelection,
    totalPages, prevPage, nextPage, handleSizeChange,
    searchCollapsed
  }
```

- [ ] **步骤 4：Commit**

```bash
git add openlist-web/src/composables/usePtIndexer.ts
git commit -m "feat(pt): usePtIndexer新增分类获取与字符串数组双向转换"
```

---

### 任务 6：前端 —— PC 端页面改造

**文件：**
- 修改：`openlist-web/src/views/openlist/ptIndexer/index.vue`

- [ ] **步骤 1：替换分类表单项**

将第 131-133 行：

```html
        <el-form-item label="分类" prop="categories">
          <el-input v-model="form.categories" placeholder="逗号分隔的分类 ID，如 5000,5030；留空表示不限" />
        </el-form-item>
```

替换为：

```html
        <el-form-item label="分类" prop="categories">
          <div class="category-field">
            <el-select
              v-model="categoriesSelected"
              multiple
              filterable
              allow-create
              default-first-option
              collapse-tags
              collapse-tags-tooltip
              placeholder="点击右侧「获取分类」后选择，或直接输入分类 ID"
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
            <el-button :loading="categoriesLoading" @click="fetchCategories">获取分类</el-button>
          </div>
        </el-form-item>
```

- [ ] **步骤 2：解构新增的状态与方法**

将 `<script setup>` 中的解构（第 160-166 行）：

```ts
const {
  taskList, loading, total, queryParams, getList, handleQuery, resetQuery, queryRef,
  selectedIds, single, multiple, toggleSelect,
  open, dialogTitle, submitLoading, formRef, form, rules,
  handleAdd, handleUpdate, submitForm, handleDelete,
  testLoading, handleTest
} = usePtIndexer()
```

替换为：

```ts
const {
  taskList, loading, total, queryParams, getList, handleQuery, resetQuery, queryRef,
  selectedIds, single, multiple, toggleSelect,
  open, dialogTitle, submitLoading, formRef, form, rules,
  handleAdd, handleUpdate, submitForm, handleDelete,
  testLoading, handleTest,
  categoriesLoading, categoryOptions, fetchCategories, categoriesSelected
} = usePtIndexer()
```

- [ ] **步骤 3：新增 `.category-field` 样式**

在 `<style scoped lang="scss">` 内、`.form-tip` 相关规则不存在时可直接在 `.card-footer` 规则块（第 293-299 行）之后新增：

```scss

.category-field {
  display: flex;
  gap: 8px;
  align-items: flex-start;

  .el-select {
    flex: 1;
    min-width: 0;
  }
}
```

- [ ] **步骤 4：Commit**

```bash
git add openlist-web/src/views/openlist/ptIndexer/index.vue
git commit -m "feat(pt): PC端PT索引器分类字段改为下拉多选"
```

---

### 任务 7：前端 —— 移动端页面改造

**文件：**
- 修改：`openlist-web/src/views-mobile/ptIndexer/index.vue`

- [ ] **步骤 1：替换分类表单项**

将第 126-128 行：

```html
        <el-form-item label="分类" prop="categories">
          <el-input v-model="form.categories" placeholder="逗号分隔分类 ID，留空表示不限" />
        </el-form-item>
```

替换为：

```html
        <el-form-item label="分类" prop="categories">
          <div class="category-field">
            <el-select
              v-model="categoriesSelected"
              multiple
              filterable
              allow-create
              default-first-option
              collapse-tags
              collapse-tags-tooltip
              placeholder="点击右侧「获取分类」后选择，或直接输入分类 ID"
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
            <el-button :loading="categoriesLoading" @click="fetchCategories">获取分类</el-button>
          </div>
        </el-form-item>
```

- [ ] **步骤 2：解构新增的状态与方法**

将 `<script setup>` 中的解构（第 154-163 行）：

```ts
const {
  taskList, loading, total, queryParams, queryRef,
  handleQuery, resetQuery,
  selectedIds, toggleSelect, handleCardClick, clearSelection,
  open, dialogTitle, submitLoading, formRef, form, rules,
  handleAdd, handleUpdate, submitForm, handleDelete,
  testLoading, handleTest,
  totalPages, prevPage, nextPage, handleSizeChange,
  searchCollapsed
} = usePtIndexer()
```

替换为：

```ts
const {
  taskList, loading, total, queryParams, queryRef,
  handleQuery, resetQuery,
  selectedIds, toggleSelect, handleCardClick, clearSelection,
  open, dialogTitle, submitLoading, formRef, form, rules,
  handleAdd, handleUpdate, submitForm, handleDelete,
  testLoading, handleTest,
  categoriesLoading, categoryOptions, fetchCategories, categoriesSelected,
  totalPages, prevPage, nextPage, handleSizeChange,
  searchCollapsed
} = usePtIndexer()
```

- [ ] **步骤 3：新增 `.category-field` 样式**

在 `<style scoped lang="scss">` 内、`:deep(.modern-dialog)` 规则块（第 324-328 行）之前新增：

```scss
.category-field {
  display: flex;
  gap: 8px;
  align-items: flex-start;

  .el-select {
    flex: 1;
    min-width: 0;
  }
}

```

- [ ] **步骤 4：Commit**

```bash
git add openlist-web/src/views-mobile/ptIndexer/index.vue
git commit -m "feat(pt): 移动端PT索引器分类字段改为下拉多选"
```

---

### 任务 8：浏览器验证

**目的：** 验证下拉分组展示、多选、`allow-create` 手动输入、按钮 loading/disabled 状态在真实浏览器中符合预期。由于开发环境通常没有可直接连通的真实 PT 索引器 caps 接口，用浏览器控制台临时打桩 `getPtIndexerCategoriesApi` 的网络响应来验证 UI 交互，不依赖真实网络请求。

- [ ] **步骤 1：启动前端开发服务器**

用 `mcp__Claude_Browser__preview_start` 以 `openlist-web` 的 dev server 配置打开预览（`.claude/launch.json` 中应已有 `npm run dev` 的配置；若没有则先创建，端口 3000，`/api` 由 Vite 代理到后端）。

- [ ] **步骤 2：打开 PT 索引器管理页面并进入新增弹窗**

导航到 PT 索引器管理页面（`/openlist/ptIndexer` 或菜单中对应路径），点击「新增」打开弹窗。

- [ ] **步骤 3：用浏览器 devtools 拦截 `/openliststrm/pt-indexers/categories` 请求验证下拉交互**

用 `mcp__Claude_Browser__javascript_tool` 在页面 console 里临时打桩 `window.fetch`（或直接在 Network 面板确认请求路径/方法后，用测试用的假 url+apikey 触发一次真实请求观察失败提示），核对：
- url/apiKey 为空时「获取分类」按钮为 disabled
- 填写 url/apiKey 后点击「获取分类」按钮显示 loading，请求结束后 loading 消失
- 请求失败时出现 `ElMessage.error` 提示，下拉框本身仍可通过 `allow-create` 手动输入并生成新 tag
- 手动在下拉框输入任意分类 ID 字符串（如 `9999`）后回车，能生成对应 tag 并选中
- 若能拿到一次真实/打桩的分类树响应：确认 `el-option-group` 按父分类分组展示，子分类带缩进，选中值仍是扁平的 ID 字符串列表

- [ ] **步骤 4：确认表单提交后分类字段仍是逗号分隔字符串**

选中若干分类 tag 后点击「确定」提交，用 `mcp__Claude_Browser__read_network_requests` 查看 `POST /openliststrm/pt-indexers` 请求体，确认 `categories` 字段仍是形如 `"5000,5040"` 的逗号分隔字符串，与改造前格式一致。

- [ ] **步骤 5：截图存证**

用 `mcp__Claude_Browser__computer` 的 `screenshot` action 截取分类下拉展开状态，作为改造完成的可视化证据。

---

## 自检记录

- **规格覆盖度**：设计文档中的「后端改动」1-4 点对应任务 1-3；「前端改动」1-4 点对应任务 4-7；「测试计划」的后端部分对应任务 1-2 的 TDD 步骤，前端部分对应任务 8。「不做的事」（存储格式不变、不缓存、手动触发、保留手动输入）已在各任务实现细节中体现，无遗漏。
- **占位符扫描**：全部步骤含完整代码块与精确命令，无 TODO/待定/"类似任务 N"。
- **类型一致性**：`CategoryOption(id, name, children)` 字段名在任务 1（定义）、任务 2/3（Java 消费）、任务 5-7（前端 TS interface 与模板 `parent.id`/`parent.name`/`parent.children`/`child.id`/`child.name`）中保持一致；前端 `categoriesSelected`、`categoryOptions`、`categoriesLoading`、`fetchCategories` 命名在任务 5 定义、任务 6/7 解构与使用中保持一致。
