<template>
  <div class="mobile-page">
    <!-- Search Panel -->
    <div class="search-panel" :class="{ collapsed: searchCollapsed }">
      <div class="search-panel-header" @click="searchCollapsed = !searchCollapsed">
        <span class="search-panel-title">
          <el-icon><Search /></el-icon>
          筛选查询
        </span>
        <el-icon class="collapse-icon" :class="{ expanded: !searchCollapsed }">
          <ArrowDown />
        </el-icon>
      </div>
      <div class="search-panel-body">
        <el-form :model="queryParams" ref="queryRef" label-width="72px">
          <el-form-item label="源目录" prop="copyTaskSrc">
            <el-input
              v-model="queryParams.copyTaskSrc"
              placeholder="请输入源目录"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="目标目录" prop="copyTaskDst">
            <el-input
              v-model="queryParams.copyTaskDst"
              placeholder="请输入目标目录"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="监控目录" prop="monitorDir">
            <el-input
              v-model="queryParams.monitorDir"
              placeholder="请输入监控目录"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="状态" prop="copyTaskStatus">
            <el-select v-model="queryParams.copyTaskStatus" placeholder="全部状态" clearable style="width: 100%">
              <el-option label="启用" value="1" />
              <el-option label="停用" value="0" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="search-actions">
          <el-button type="primary" icon="Search" @click="handleQuery" :loading="loading">
            搜索
          </el-button>
          <el-button icon="Refresh" @click="resetQuery">
            重置
          </el-button>
        </div>
      </div>
    </div>

    <!-- Batch Actions -->
    <div class="batch-bar" v-if="selectedIds.length > 0">
      <span class="selected-count">已选 {{ selectedIds.length }} 项</span>
      <el-button link type="primary" size="small" @click="handleBatchExecute">
        <el-icon><VideoPlay /></el-icon> 批量执行
      </el-button>
      <el-button link size="small" @click="clearSelection">
        取消
      </el-button>
    </div>

    <!-- Add Button (FAB) -->
    <el-button class="fab-add" type="primary" size="large" round @click="handleAdd">
      <el-icon><Plus /></el-icon> 新增
    </el-button>

    <!-- Task List -->
    <div class="task-list" v-loading="loading">
      <div
        v-for="task in taskList"
        :key="task.copyTaskId"
        class="task-card"
        :class="{ selected: selectedIds.includes(task.copyTaskId) }"
        @click="handleCardClick($event, task.copyTaskId)"
      >
        <div class="card-checkbox">
          <el-checkbox
            :model-value="selectedIds.includes(task.copyTaskId)"
            size="large"
            @change="toggleSelect(task.copyTaskId)"
          />
        </div>
        <div class="card-content">
          <div class="card-top">
            <div class="task-name-row">
              <el-icon class="task-icon" :size="18"><Location /></el-icon>
              <span class="task-name" @click.stop="showFullText(task.copyTaskSrc, '源目录')">{{ task.copyTaskSrc }}</span>
            </div>
            <el-tag :type="task.copyTaskStatus === '1' ? 'success' : 'danger'" size="small" effect="light">
              {{ task.copyTaskStatus === '1' ? '启用' : '停用' }}
            </el-tag>
          </div>
          <div class="task-path" @click.stop="showFullText(task.copyTaskDst, '目标目录')">
            <el-icon class="path-icon"><Location /></el-icon>
            <span class="path-text">{{ task.copyTaskDst }}</span>
          </div>
          <div class="task-path monitor-path" v-if="task.monitorDir" @click.stop="showFullText(task.monitorDir, '监控目录')">
            <el-icon class="path-icon"><Filter /></el-icon>
            <span class="path-text">{{ task.monitorDir }}</span>
          </div>
          <div class="card-time">
            <el-icon><Clock /></el-icon>
            {{ task.createTime }}
          </div>
        </div>
        <div class="card-actions" @click.stop>
          <el-button link type="primary" size="small" :icon="Edit" @click="handleUpdate(task)">
            修改
          </el-button>
          <el-button link type="danger" size="small" :icon="Delete" @click="handleDelete(task)">
            删除
          </el-button>
          <el-button link type="primary" size="small" :icon="VideoPlay" @click="handleExecuteOne(task)">
            执行
          </el-button>
        </div>
      </div>

      <el-empty v-if="!loading && taskList.length === 0" description="暂无同步任务" />
    </div>

    <!-- Pagination -->
    <div class="pagination-bar" v-if="total > 0">
      <div class="pagination-info">
        <span class="total-text">共 {{ total }} 条</span>
      </div>
      <div class="pagination-controls">
        <el-button
          :icon="ArrowLeft"
          text
          size="small"
          :disabled="queryParams.pageNum <= 1"
          @click="prevPage"
          class="page-btn"
        />
        <div class="page-num-box">
          <span class="current-page">{{ queryParams.pageNum }}</span>
          <span class="page-divider">/</span>
          <span class="total-pages">{{ totalPages }}</span>
        </div>
        <el-button
          :icon="ArrowRight"
          text
          size="small"
          :disabled="queryParams.pageNum >= totalPages"
          @click="nextPage"
          class="page-btn"
        />
        <el-select
          v-model="queryParams.pageSize"
          size="small"
          @change="handleSizeChange"
          class="page-size-select"
        >
          <el-option :label="10" :value="10" />
          <el-option :label="20" :value="20" />
          <el-option :label="50" :value="50" />
        </el-select>
        <span class="page-size-label">条/页</span>
      </div>
    </div>

    <!-- Full Text Dialog -->
    <el-dialog
      v-model="fullTextVisible"
      :title="fullTextTitle"
      width="85%"
      :close-on-click-modal="true"
      class="full-text-dialog"
    >
      <div class="full-text-content">{{ fullTextContent }}</div>
      <template #footer>
        <el-button size="small" @click="copyToClipboard(fullTextContent)">
          <el-icon><CopyDocument /></el-icon> 复制
        </el-button>
        <el-button size="small" type="primary" @click="fullTextVisible = false">
          关闭
        </el-button>
      </template>
    </el-dialog>

    <!-- Add/Edit Dialog -->
    <el-dialog v-model="open" :title="dialogTitle" width="90%" append-to-body class="modern-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="源目录" prop="copyTaskSrc">
          <DirectoryTreeSelect v-model="form.copyTaskSrc" type="openlist" placeholder="请选择源目录" />
        </el-form-item>
        <el-form-item label="目标目录" prop="copyTaskDst">
          <DirectoryTreeSelect v-model="form.copyTaskDst" type="openlist" placeholder="请选择目标目录" />
        </el-form-item>
        <el-form-item label="监控目录" prop="monitorDir">
          <DirectoryTreeSelect v-model="form.monitorDir" type="local" placeholder="请选择监控目录（可选）" />
        </el-form-item>
        <el-form-item label="状态" prop="copyTaskStatus">
          <el-radio-group v-model="form.copyTaskStatus">
            <el-radio value="1">启用</el-radio>
            <el-radio value="0">停用</el-radio>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, ArrowDown, ArrowLeft, ArrowRight,
  Location, Clock, VideoPlay, Filter, CopyDocument, Plus, Edit, Delete
} from '@element-plus/icons-vue'
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect/index.vue'
import {
  getCopyTaskListApi,
  addCopyTaskApi,
  updateCopyTaskApi,
  deleteCopyTaskApi,
  executeCopyTaskApi
} from '@/api/openlist/copyTask'
import type { SearchParams, PageResult } from '@/types'

const taskList = ref<any[]>([])
const loading = ref(true)
const total = ref(0)
const selectedIds = ref<number[]>([])
const searchCollapsed = ref(true)
const queryRef = ref<any>()

// Full text dialog
const fullTextVisible = ref(false)
const fullTextTitle = ref('')
const fullTextContent = ref('')

const showFullText = (content: string, title: string) => {
  fullTextTitle.value = title
  fullTextContent.value = content
  fullTextVisible.value = true
}

const copyToClipboard = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('已复制到剪贴板')
  }
}

const totalPages = computed(() => Math.ceil(total.value / queryParams.pageSize) || 1)

const queryParams = reactive<SearchParams & {
  copyTaskSrc?: string
  copyTaskDst?: string
  monitorDir?: string
  copyTaskStatus?: string
}>({
  pageNum: 1,
  pageSize: 10,
  copyTaskStatus: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getCopyTaskListApi(queryParams) as PageResult
    taskList.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const resetQuery = () => {
  if (queryRef.value) (queryRef.value as any).resetFields()
  handleQuery()
}

const toggleSelect = (id: number) => {
  const idx = selectedIds.value.indexOf(id)
  if (idx > -1) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(id)
  }
}

const handleCardClick = (event: Event, id: number) => {
  const target = event.target as HTMLElement
  if (target.closest('.card-checkbox')) return
  toggleSelect(id)
}

const clearSelection = () => {
  selectedIds.value = []
}

const prevPage = () => {
  if (queryParams.pageNum > 1) {
    queryParams.pageNum--
    getList()
  }
}

const nextPage = () => {
  if (queryParams.pageNum < totalPages.value) {
    queryParams.pageNum++
    getList()
  }
}

const handleSizeChange = () => {
  queryParams.pageNum = 1
  getList()
}

// --- Actions ---

const handleExecuteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认执行同步任务"${row.copyTaskSrc}"？`, '提示', { type: 'warning' })
    await executeCopyTaskApi([row.copyTaskId])
    ElMessage.success('执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchExecute = async () => {
  try {
    await ElMessageBox.confirm(`是否确认批量执行选中的 ${selectedIds.value.length} 个同步任务？`, '提示', { type: 'warning' })
    await executeCopyTaskApi(selectedIds.value)
    ElMessage.success('批量执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

// --- Dialog State ---

const open = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<any>()

const initForm = (): any => ({
  copyTaskId: undefined,
  copyTaskSrc: undefined,
  copyTaskDst: undefined,
  monitorDir: undefined,
  copyTaskStatus: '1',
  remark: undefined
})

const form = ref<any>(initForm())

const rules = reactive({
  copyTaskSrc: [{ required: true, message: '源目录不能为空', trigger: 'blur' }],
  copyTaskDst: [{ required: true, message: '目标目录不能为空', trigger: 'blur' }]
})

const handleAdd = () => {
  dialogTitle.value = '新增文件同步任务'
  form.value = initForm()
  open.value = true
}

const handleUpdate = (row?: any) => {
  const id = row?.copyTaskId
  if (!id) {
    ElMessage.warning('请选择数据项')
    return
  }
  dialogTitle.value = '修改文件同步任务'
  getCopyTaskListApi({ ...queryParams, pageNum: 1, pageSize: 100 }).then((res: PageResult) => {
    const task = res.records.find((t: any) => t.copyTaskId === id)
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
    if (form.value.copyTaskId) {
      await updateCopyTaskApi(form.value)
      ElMessage.success('修改成功')
    } else {
      await addCopyTaskApi(form.value)
      ElMessage.success('新增成功')
    }
    open.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row?: any) => {
  const id = row?.copyTaskId
  if (!id) {
    ElMessage.warning('请选择数据项')
    return
  }
  try {
    await ElMessageBox.confirm(`是否确认删除文件同步任务"${row.copyTaskSrc} → ${row.copyTaskDst}"？`, '警告', { type: 'warning' })
    await deleteCopyTaskApi(id)
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

getList()
</script>

<style scoped lang="scss">
.mobile-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: calc(100vh - 120px);
  padding-bottom: 8px;

  .task-list {
    flex: 1;
  }

  @media (max-width: 768px) {
    .pagination-bar {
      margin-bottom: 8px;
    }
  }
}

/* ============================================
   Search Panel
   ============================================ */
.search-panel {
  background: var(--osr-surface);
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);
  overflow: hidden;
  transition: all var(--osr-transition-base);

  &.collapsed .search-panel-body {
    display: none;
  }

  .search-panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 14px;
    cursor: pointer;
    user-select: none;
    transition: background var(--osr-transition-fast);

    &:active {
      background: var(--osr-bg-page);
    }

    .search-panel-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 14px;
      font-weight: 600;
      color: var(--osr-text-primary);

      .el-icon {
        color: var(--osr-primary);
        font-size: 16px;
      }
    }

    .collapse-icon {
      font-size: 16px;
      color: var(--osr-text-secondary);
      transition: transform var(--osr-transition-base);

      &.expanded {
        transform: rotate(180deg);
      }
    }
  }

  .search-panel-body {
    padding: 0 14px 14px;

    :deep(.el-form) {
      .el-form-item {
        margin-bottom: 12px;
        margin-right: 0;

        .el-form-item__label {
          font-size: 13px;
          color: var(--osr-text-secondary);
          padding-bottom: 4px;
        }
      }

      .el-input__wrapper,
      .el-select__wrapper {
        border-radius: var(--osr-radius-sm);
        box-shadow: 0 0 0 1px var(--osr-border-base) inset;
      }
    }

    .search-actions {
      display: flex;
      gap: 8px;
      margin-top: 4px;

      .el-button {
        flex: 1;
        border-radius: var(--osr-radius-sm);
      }
    }
  }
}

/* ============================================
   Batch Action Bar
   ============================================ */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: var(--osr-primary-light-9);
  border: 1px solid var(--osr-primary-light-7);
  border-radius: var(--osr-radius-md);
  font-size: 13px;

  .selected-count {
    font-weight: 600;
    color: var(--osr-primary);
    margin-right: 4px;
    white-space: nowrap;
  }

  .el-button {
    font-size: 12px;
    padding: 0 4px;
    height: auto;
  }
}

/* ============================================
   Task List
   ============================================ */
.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 200px;
}

.task-card {
  display: flex;
  gap: 10px;
  background: var(--osr-surface);
  border-radius: var(--osr-radius-lg);
  padding: 12px;
  box-shadow: var(--osr-shadow-base);
  border: 2px solid transparent;
  transition: all var(--osr-transition-fast);

  &.selected {
    border-color: var(--osr-primary-light-5);
    background: var(--osr-primary-light-9);
  }

  &:active {
    transform: scale(0.99);
  }

  .card-checkbox {
    flex-shrink: 0;
    display: flex;
    align-items: flex-start;
    padding-top: 2px;
    padding-left: 2px;
  }

  .card-content {
    flex: 1;
    min-width: 0;
  }

  .card-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
    gap: 8px;
  }

  .task-name-row {
    display: flex;
    align-items: center;
    gap: 5px;
    min-width: 0;
    flex: 1;

    .task-icon {
      color: var(--osr-primary);
      flex-shrink: 0;
    }

    .task-name {
      font-size: 14px;
      font-weight: 500;
      color: var(--osr-text-primary);
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      line-height: 1.4;
      cursor: pointer;
      word-break: break-all;

      &:hover {
        color: var(--osr-primary);
      }
    }
  }

  .task-path {
    display: flex;
    align-items: flex-start;
    gap: 3px;
    font-size: 12px;
    color: var(--osr-text-secondary);
    margin-bottom: 6px;
    cursor: pointer;
    line-height: 1.5;

    .path-icon {
      flex-shrink: 0;
      margin-top: 2px;
      color: var(--osr-text-disabled);
    }

    .path-text {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      word-break: break-all;
    }

    &.monitor-path .path-text {
      color: var(--osr-warning);
    }

    &:hover {
      color: var(--osr-primary);
    }
  }

  .card-time {
    display: flex;
    align-items: center;
    gap: 3px;
    font-size: 11px;
    color: var(--osr-text-disabled);

    .el-icon {
      flex-shrink: 0;
    }
  }

  .card-actions {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex-shrink: 0;
    padding-left: 8px;
    border-left: 1px solid var(--osr-border-light);

    .el-button {
      font-size: 11px;
      padding: 2px 0;
      height: auto;
      white-space: nowrap;
    }
  }
}

/* ============================================
   Pagination Bar
   ============================================ */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: var(--osr-surface);
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);
  gap: 12px;

  .pagination-info {
    flex-shrink: 0;

    .total-text {
      font-size: 13px;
      font-weight: 600;
      color: var(--osr-text-secondary);
    }
  }

  .pagination-controls {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1;
    justify-content: flex-end;

    .page-btn {
      padding: 4px;
      min-width: unset;
      height: unset;

      :deep(.el-icon) {
        font-size: 18px;
        color: var(--osr-text-primary);
      }

      &:disabled {
        :deep(.el-icon) {
          color: var(--osr-text-disabled);
        }
      }
    }

    .page-num-box {
      display: flex;
      align-items: center;
      gap: 2px;
      padding: 4px 10px;
      background: var(--osr-bg-page);
      border-radius: var(--osr-radius-sm);
      border: 1px solid var(--osr-border-light);

      .current-page {
        font-size: 16px;
        font-weight: 700;
        color: var(--osr-primary);
        line-height: 1;
      }

      .page-divider {
        font-size: 12px;
        color: var(--osr-text-disabled);
        margin: 0 2px;
      }

      .total-pages {
        font-size: 13px;
        color: var(--osr-text-secondary);
        line-height: 1;
      }
    }

    .page-size-select {
      width: 64px;

      :deep(.el-input__wrapper) {
        padding: 0 8px;
        height: 28px;
        border-radius: var(--osr-radius-sm);
        box-shadow: 0 0 0 1px var(--osr-border-light) inset;
      }

      :deep(.el-input__inner) {
        font-size: 13px;
        text-align: center;
        color: var(--osr-text-primary);
      }
    }

    .page-size-label {
      font-size: 12px;
      color: var(--osr-text-secondary);
      flex-shrink: 0;
      white-space: nowrap;
    }
  }
}

/* ============================================
   Full Text Dialog
   ============================================ */
:deep(.full-text-dialog) {
  .el-dialog__body {
    padding: 16px;
  }
}

.full-text-content {
  word-break: break-all;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  color: var(--osr-text-primary);
  max-height: 400px;
  overflow-y: auto;
  background: var(--osr-bg-page);
  border-radius: var(--osr-radius-sm);
  padding: 12px;
}

/* ============================================
   FAB Add Button
   ============================================ */
.fab-add {
  position: fixed;
  right: 20px;
  bottom: calc(56px + 16px + env(safe-area-inset-bottom, 0px));
  z-index: 1000;
  padding: 12px 20px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all var(--osr-transition-fast);

  &:active {
    transform: scale(0.96);
  }

  @media (min-width: 768px) {
    right: 40px;
    bottom: calc(56px + 24px);
    padding: 14px 24px;
    font-size: 15px;
  }
}

/* ============================================
   Card Actions
   ============================================ */
.task-card {
  .card-actions {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
    padding-left: 8px;
    border-left: 1px solid var(--osr-border-light);

    .el-button {
      font-size: 11px;
      padding: 2px 4px;
      height: auto;
      white-space: nowrap;
    }
  }
}

/* ============================================
   Dialog
   ============================================ */
:deep(.modern-dialog) {
  .el-dialog__body {
    padding: 16px;
  }
}
</style>
