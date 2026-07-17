<template>
  <div class="mobile-page">
    <!-- 搜索 -->
    <MobileSearchPanel v-model:collapsed="searchCollapsed" :loading="loading" @search="handleQuery" @reset="resetQuery">
      <el-form ref="queryRef" :model="queryParams" label-width="72px">
        <el-form-item label="源目录" prop="copyTaskSrc">
          <el-input v-model="queryParams.copyTaskSrc" placeholder="请输入源目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="目标目录" prop="copyTaskDst">
          <el-input v-model="queryParams.copyTaskDst" placeholder="请输入目标目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="监控目录" prop="monitorDir">
          <el-input v-model="queryParams.monitorDir" placeholder="请输入监控目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="copyTaskStatus">
          <el-select v-model="queryParams.copyTaskStatus" placeholder="全部状态" clearable style="width: 100%">
            <el-option label="启用" value="1" />
            <el-option label="停用" value="0" />
          </el-select>
        </el-form-item>
      </el-form>
    </MobileSearchPanel>

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
    <el-button class="fab-add" type="primary" size="large" round @click="handleAdd('新增文件同步任务')">
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
          <el-button link type="primary" size="small" :icon="Edit" @click="handleUpdate(task, '修改文件同步任务')">
            修改
          </el-button>
          <el-button link type="danger" size="small" :icon="Delete" @click="handleDelete(task)">
            删除
          </el-button>
          <el-button link type="primary" size="small" :icon="VideoPlay" @click="handleExecuteOne(task, `是否确认执行同步任务“${task.copyTaskSrc}”？`)">
            执行
          </el-button>
        </div>
      </div>

      <el-empty v-if="!loading && taskList.length === 0" description="暂无同步任务" />
    </div>

    <!-- 分页 -->
    <MobilePager
      v-model:page-size="queryParams.pageSize"
      :page-num="queryParams.pageNum"
      :total="total"
      :total-pages="totalPages"
      @prev="prevPage"
      @next="nextPage"
      @size-change="handleSizeChange"
    />

    <!-- 全文查看 -->
    <FullTextDialog ref="fullTextRef" />

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
      </el-form>
      <template #footer>
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  Location, Clock, VideoPlay, Filter, Plus, Edit, Delete
} from '@element-plus/icons-vue'
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect/index.vue'
import MobileSearchPanel from '@/components/mobile/MobileSearchPanel.vue'
import MobilePager from '@/components/mobile/MobilePager.vue'
import FullTextDialog from '@/components/mobile/FullTextDialog.vue'
import { useCopyTask } from '@/composables/useCopyTask'
import { useDebounce } from '@/composables/useDebounce'

const {
  taskList, loading, total, queryParams, queryRef,
  getList, handleQuery, resetQuery,
  selectedIds,
  open, dialogTitle, submitLoading, formRef, form, rules,
  handleAdd, handleUpdate, submitForm,
  handleDelete, handleExecuteOne,
  toggleSelect, handleCardClick, clearSelection,
  totalPages, prevPage, nextPage, handleSizeChange,
  searchCollapsed, handleBatchExecute
} = useCopyTask()

const fullTextRef = ref<InstanceType<typeof FullTextDialog>>()
const showFullText = (content: string, title: string) => fullTextRef.value?.show(content, title)

// 搜索输入防抖：输入停止 300ms 后自动触发搜索
const debouncedSearch = useDebounce(() => {
  queryParams.pageNum = 1
  getList()
}, 300)

watch(
  () => [queryParams.copyTaskSrc, queryParams.copyTaskDst, queryParams.monitorDir, queryParams.copyTaskStatus],
  () => debouncedSearch()
)
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
