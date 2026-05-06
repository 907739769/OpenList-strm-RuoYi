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
          <el-form-item label="STRM目录" prop="strmTaskPath">
            <el-input
              v-model="queryParams.strmTaskPath"
              placeholder="请输入STRM目录"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="状态" prop="strmTaskStatus">
            <el-select v-model="queryParams.strmTaskStatus" placeholder="全部状态" clearable style="width: 100%">
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

    <!-- Task List -->
    <div class="task-list" v-loading="loading">
      <div
        v-for="task in taskList"
        :key="task.strmTaskId"
        class="task-card"
        :class="{ selected: selectedIds.includes(task.strmTaskId) }"
        @click="handleCardClick($event, task.strmTaskId)"
      >
        <div class="card-checkbox">
          <el-checkbox
            :model-value="selectedIds.includes(task.strmTaskId)"
            size="large"
            @change="toggleSelect(task.strmTaskId)"
          />
        </div>
        <div class="card-content">
          <div class="card-top">
            <div class="task-name-row">
              <el-icon class="task-icon" :size="18"><VideoCamera /></el-icon>
              <span class="task-name" :title="task.strmTaskPath">{{ task.strmTaskPath }}</span>
            </div>
            <el-tag :type="task.strmTaskStatus === '1' ? 'success' : 'danger'" size="small" effect="light">
              {{ task.strmTaskStatus === '1' ? '启用' : '停用' }}
            </el-tag>
          </div>
          <div class="card-time">
            <el-icon><Clock /></el-icon>
            {{ task.createTime }}
          </div>
        </div>
        <div class="card-actions" @click.stop>
          <el-button link type="primary" size="small" icon="VideoPlay" @click="handleExecuteOne(task)">
            执行
          </el-button>
        </div>
      </div>

      <el-empty v-if="!loading && taskList.length === 0" description="暂无STRM任务" />
    </div>

    <!-- Pagination -->
    <div class="pagination-bar" v-if="total > 0">
      <div class="page-info">
        共 {{ total }} 条
      </div>
      <div class="page-controls-row">
        <div class="page-controls">
          <el-button
            :icon="ArrowLeft"
            circle
            size="small"
            :disabled="queryParams.pageNum <= 1"
            @click="prevPage"
          />
          <span class="page-num">{{ queryParams.pageNum }}</span>
          <el-button
            :icon="ArrowRight"
            circle
            size="small"
            :disabled="queryParams.pageNum >= totalPages"
            @click="nextPage"
          />
        </div>
        <el-select
          v-model="queryParams.pageSize"
          :width="80"
          size="small"
          @change="handleSizeChange"
        >
          <el-option :label="10" :value="10" />
          <el-option :label="20" :value="20" />
          <el-option :label="50" :value="50" />
        </el-select>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, ArrowDown, ArrowLeft, ArrowRight,
  VideoCamera, Clock, VideoPlay
} from '@element-plus/icons-vue'
import {
  getStrmTaskListApi,
  executeStrmTaskApi
} from '@/api/openlist/strmTask'
import type { SearchParams, PageResult } from '@/types'

const taskList = ref<any[]>([])
const loading = ref(true)
const total = ref(0)
const selectedIds = ref<number[]>([])
const searchCollapsed = ref(true)
const queryRef = ref<any>()

const totalPages = computed(() => Math.ceil(total.value / queryParams.pageSize) || 1)

const queryParams = reactive<SearchParams & {
  strmTaskPath?: string
  strmTaskStatus?: string
}>({
  pageNum: 1,
  pageSize: 10,
  strmTaskStatus: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getStrmTaskListApi(queryParams) as PageResult
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
    await ElMessageBox.confirm(`是否确认执行STRM任务"${row.strmTaskPath}"？`, '提示', { type: 'warning' })
    await executeStrmTaskApi([row.strmTaskId])
    ElMessage.success('执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchExecute = async () => {
  try {
    await ElMessageBox.confirm(`是否确认批量执行选中的 ${selectedIds.value.length} 个STRM任务？`, '提示', { type: 'warning' })
    await executeStrmTaskApi(selectedIds.value)
    ElMessage.success('批量执行成功')
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
  padding-bottom: 8px;

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
      white-space: nowrap;
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
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  padding: 10px 4px;

  .page-info {
    font-size: 13px;
    font-weight: 500;
    color: var(--osr-text-secondary);
    text-align: center;
  }

  .page-controls-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;

    .page-controls {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;

      .page-num {
        font-size: 15px;
        font-weight: 600;
        color: var(--osr-primary);
        min-width: 28px;
        text-align: center;
      }
    }

    :deep(.el-select) {
      flex: 0 0 auto;

      .el-input__wrapper {
        border-radius: var(--osr-radius-sm);
      }
    }
  }

  @media (min-width: 576px) {
    flex-direction: row;
    justify-content: space-between;

    .page-info {
      text-align: left;
      font-size: 12px;
      font-weight: 400;
    }

    .page-controls-row {
      .page-controls {
        flex: 0 1 auto;
      }

      :deep(.el-select) {
        margin-left: 8px;
      }
    }
  }
}
</style>
