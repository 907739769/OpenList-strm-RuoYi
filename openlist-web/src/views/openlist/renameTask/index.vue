<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="源目录" prop="sourceFolder">
          <el-input v-model="queryParams.sourceFolder" placeholder="请输入源目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="目标目录" prop="targetRoot">
          <el-input v-model="queryParams.targetRoot" placeholder="请输入目标目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="状态" clearable>
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
      <!-- Action Bar -->
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
          <el-button type="success" :disabled="single" @click="handleUpdate()">
            <el-icon><Edit /></el-icon> 修改
          </el-button>
          <el-button type="danger" :disabled="multiple" @click="handleDelete()">
            <el-icon><Delete /></el-icon> 删除
          </el-button>
          <el-button type="warning" :disabled="multiple" @click="handleExecute()">
            <el-icon><VideoPlay /></el-icon> 执行
          </el-button>
          <el-button type="info" @click="handleTest()">
            <el-icon><MagicStick /></el-icon> 测试
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <!-- Desktop Table -->
      <el-table v-if="appStore.device === 'desktop'" v-loading="loading" :data="taskList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="任务路径配置" min-width="300">
          <template #default="scope">
            <div class="path-box">
              <div class="path-row"><span class="path-label label-src">源</span> <span class="path-text">{{ scope.row.sourceFolder }}</span></div>
              <div class="path-row"><span class="path-label label-dst">目</span> <span class="path-text">{{ scope.row.targetRoot }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === '0' ? 'danger' : 'success'">
              {{ scope.row.status === '0' ? '停用' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" align="center" />
        <el-table-column label="操作" align="center" width="260" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row)">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
            <el-button link type="primary" @click="handleExecuteOne(scope.row)">
              <el-icon><VideoPlay /></el-icon> 执行
            </el-button>
            <el-button link type="primary" @click="handleTestOne(scope.row)">
              <el-icon><MagicStick /></el-icon> 测试
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Mobile Card List -->
      <div v-if="appStore.device === 'mobile'" v-loading="loading" class="mobile-card-list">
        <div v-for="item in taskList" :key="item.id" class="mobile-card">
          <div class="mobile-card-header">
            <span class="mobile-card-title"><i class="fa fa-exchange"></i> {{ item.sourceFolder }}</span>
            <el-tag size="small" :type="item.status === '0' ? 'danger' : 'success'">
              {{ item.status === '0' ? '停用' : '启用' }}
            </el-tag>
          </div>
          <div class="mobile-card-body">
            <div class="mobile-card-row">
              <span class="mobile-card-label">目标</span>
              <span class="mobile-card-value mobile-card-value-clip">{{ item.targetRoot }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">创建时间</span>
              <span class="mobile-card-value mobile-card-value-light">{{ item.createTime }}</span>
            </div>
          </div>
          <div class="mobile-card-actions">
            <el-button link type="primary" size="small" @click="handleUpdate(item)">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(item)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
            <el-button link type="primary" size="small" @click="handleExecuteOne(item)">
              <el-icon><VideoPlay /></el-icon> 执行
            </el-button>
            <el-button link type="primary" size="small" @click="handleTestOne(item)">
              <el-icon><MagicStick /></el-icon> 测试
            </el-button>
          </div>
        </div>
        <el-empty v-if="!taskList.length" description="暂无数据" />
      </div>

      <!-- Pagination -->
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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="源目录" prop="sourceFolder">
          <DirectoryTreeSelect v-model="form.sourceFolder" type="local" placeholder="请选择源目录" />
        </el-form-item>
        <el-form-item label="目标目录" prop="targetRoot">
          <DirectoryTreeSelect v-model="form.targetRoot" type="local" placeholder="请选择目标目录" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">停用</el-radio>
            <el-radio value="1">启用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确 定</el-button>
      </template>
    </el-dialog>

    <!-- Test Dialog -->
    <el-dialog v-model="testOpen" :title="testTitle" width="700px" append-to-body class="modern-dialog">
      <el-form label-width="100px">
        <el-form-item label="原文件名">
          <el-input v-model="testForm.filename" type="textarea" :rows="3" placeholder="例如: The.Movie.2024.1080p.mkv" />
        </el-form-item>
        <el-form-item label="重命名模板">
          <el-input v-model="testForm.template" type="textarea" :rows="4" placeholder="留空则使用默认配置" />
          <div style="color:#999;font-size:12px"><el-icon><InfoFilled /></el-icon> 留空则使用默认配置</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="testLoading" @click="doTest">
            <el-icon><MagicStick /></el-icon> 开始分析
          </el-button>
        </el-form-item>
      </el-form>

      <div v-if="testResult" style="margin-top:16px">
        <el-alert title="重命名结果预览" type="success" :closable="false" style="margin-bottom:12px">
          <div style="font-family:Consolas,monospace;word-break:break-all;white-space:pre-wrap">{{ testResult.renamed }}</div>
        </el-alert>
        <el-alert title="识别参数详情" type="info" :closable="false">
          <pre style="max-height:300px;overflow:auto;font-size:12px;background:#f5f5f5;padding:10px;border-radius:4px">{{ JSON.stringify(testResult.info, null, 2) }}</pre>
        </el-alert>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, VideoPlay, MagicStick, Filter, InfoFilled } from '@element-plus/icons-vue'
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect/index.vue'
import { getRenameTaskListApi, addRenameTaskApi, updateRenameTaskApi, deleteRenameTaskApi, executeRenameTaskApi, testParseRenameApi } from '@/api/openlist/renameTask'
import { useAppStore } from '@/stores/app'
import type { SearchParams, PageResult } from '@/types'

const appStore = useAppStore()
const showSearch = ref(appStore.device === 'desktop')

const taskList = ref<any[]>([])
const loading = ref(true)
const total = ref(0)
const single = ref(true)
const multiple = ref(true)
const selectedIds = ref<number[]>([])

const queryParams = reactive<SearchParams & { sourceFolder?: string; targetRoot?: string; status?: string }>({
  pageNum: 1,
  pageSize: 10
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getRenameTaskListApi(queryParams) as PageResult
    taskList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { (queryRef.value as any).resetFields(); handleQuery() }
const handleSelectionChange = (selection: any[]) => { single.value = selection.length !== 1; multiple.value = !selection.length; selectedIds.value = selection.map((item: any) => item.id) }

// Dialog state
const open = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)

const initForm = (): any => ({
  id: undefined,
  sourceFolder: undefined,
  targetRoot: undefined,
  status: '1',
  remark: undefined
})

const form = ref<any>(initForm())
const formRef = ref<any>()

const rules = reactive({
  sourceFolder: [{ required: true, message: '源目录不能为空', trigger: 'blur' }],
  targetRoot: [{ required: true, message: '目标目录不能为空', trigger: 'blur' }]
})

const handleAdd = () => {
  dialogTitle.value = '新增重命名任务'
  form.value = initForm()
  open.value = true
}

const handleUpdate = (row?: any) => {
  const id = row?.id || selectedIds.value[0]
  if (!id) {
    ElMessage.warning('请选择数据项')
    return
  }
  dialogTitle.value = '修改重命名任务'
  getRenameTaskListApi({ ...queryParams, pageNum: 1, pageSize: 100 }).then((res: PageResult) => {
    const task = res.records.find((t: any) => t.id === id)
    if (task) {
      form.value = { ...task }
      open.value = true
    } else {
      ElMessage.error('任务不存在')
    }
  })
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (form.value.id) {
      await updateRenameTaskApi(form.value)
      ElMessage.success('修改成功')
    } else {
      await addRenameTaskApi(form.value)
      ElMessage.success('新增成功')
    }
    open.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row?: any) => {
  const ids = row?.id ? [row.id] : selectedIds.value
  try {
    await ElMessageBox.confirm(`是否确认删除重命名任务编号为"${ids}"的数据项？`, '警告', { type: 'warning' })
    await deleteRenameTaskApi(ids[0])
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleExecute = async () => {
  try {
    await ElMessageBox.confirm(`是否确认执行选中的重命名任务？`, '警告', { type: 'warning' })
    await executeRenameTaskApi(selectedIds.value)
    ElMessage.success('执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleExecuteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认执行重命名任务"${row.sourceFolder}"？`, '警告', { type: 'warning' })
    await executeRenameTaskApi([row.id])
    ElMessage.success('执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const queryRef = ref<any>()
getList()

// Test dialog state
const testOpen = ref(false)
const testTitle = ref('文件名重命名测试')
const testLoading = ref(false)
const testResult = ref<any>(null)
const testForm = reactive({ filename: '', template: '' })

const handleTest = () => {
  testTitle.value = '文件名重命名测试'
  testForm.filename = ''
  testForm.template = ''
  testResult.value = null
  testOpen.value = true
}

const handleTestOne = (row: any) => {
  testTitle.value = `文件名重命名测试 - ${row.sourceFolder}`
  testForm.filename = ''
  testForm.template = ''
  testResult.value = null
  testOpen.value = true
}

const doTest = async () => {
  if (!testForm.filename.trim()) {
    ElMessage.warning('请输入文件名')
    return
  }
  testLoading.value = true
  try {
    const res = await testParseRenameApi(testForm.filename, testForm.template || undefined) as any
    if (res.code === 200) {
      testResult.value = res.data
      ElMessage.success('分析成功')
    } else {
      ElMessage.error(res.message || '分析失败')
    }
  } catch (e) {
    ElMessage.error('请求失败')
  } finally {
    testLoading.value = false
  }
}
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ============================================
   Search Card
   ============================================ */
.search-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);

  :deep(.el-card__body) {
    padding: 16px 20px;
  }
}

/* ============================================
   Table Card
   ============================================ */
.table-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);
  flex: 1;

  :deep(.el-card__body) {
    padding: 20px;
  }
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .action-left {
    display: flex;
    gap: 8px;
  }
}

/* ============================================
   Pagination
   ============================================ */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ============================================
   Mobile Responsive
   ============================================ */
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

  :deep(.el-table) {
    font-size: 13px;

    .el-table__cell {
      padding: 8px 0;
    }
  }

  .action-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
}

/* ============================================
   Mobile Card List
   ============================================ */
.mobile-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0;
}

.mobile-card {
  background: white;
  border-radius: var(--osr-radius-md);
  box-shadow: var(--osr-shadow-sm);
  border: 1px solid var(--osr-border-light);
  overflow: hidden;

  .mobile-card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 14px 8px;
    border-bottom: 1px solid var(--osr-border-light);

    .mobile-card-title {
      font-size: 14px;
      font-weight: 600;
      color: var(--osr-text-primary);
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      margin-right: 8px;
      i { color: var(--osr-primary); margin-right: 4px; }
    }
  }

  .mobile-card-body {
    padding: 10px 14px;

    .mobile-card-row {
      display: flex;
      padding: 4px 0;
      font-size: 13px;

      .mobile-card-label {
        width: 72px;
        color: var(--osr-text-secondary);
        flex-shrink: 0;
      }

      .mobile-card-value {
        flex: 1;
        color: var(--osr-text-primary);
        word-break: break-all;

        &.mobile-card-value-clip {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          max-width: 200px;
        }

        &.mobile-card-value-light {
          color: var(--osr-text-secondary);
        }
      }
    }
  }

  .mobile-card-actions {
    display: flex;
    justify-content: flex-end;
    gap: 4px;
    padding: 8px 14px 12px;
    border-top: 1px solid var(--osr-border-light);
  }
}
</style>
