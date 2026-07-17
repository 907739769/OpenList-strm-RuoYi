<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
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
          <el-select v-model="queryParams.copyTaskStatus" placeholder="状态" clearable :style="{ width: '120px' }">
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
          <el-button type="primary" @click="handleAdd('新增文件同步任务')">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
          <el-button type="success" :disabled="single" @click="handleUpdate(undefined, '修改文件同步任务')">
            <el-icon><Edit /></el-icon> 修改
          </el-button>
          <el-button type="danger" :disabled="multiple" @click="handleDelete(undefined, `是否确认删除文件同步任务编号为“${selectedIds}”的数据项？`)">
            <el-icon><Delete /></el-icon> 删除
          </el-button>
          <el-button type="warning" :disabled="multiple" @click="handleExecute('是否确认执行选中的文件同步任务？')">
            <el-icon><VideoPlay /></el-icon> 批量执行
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
        <el-table-column label="同步配置" min-width="300">
          <template #default="scope">
            <div class="path-box">
              <div class="path-row"><span class="path-label label-src">源</span> <span class="path-text">{{ scope.row.copyTaskSrc }}</span></div>
              <div class="path-row"><span class="path-label label-dst">目</span> <span class="path-text">{{ scope.row.copyTaskDst }}</span></div>
              <div class="path-row" v-if="scope.row.monitorDir"><span class="path-label label-mon">监</span> <span class="path-text">{{ scope.row.monitorDir }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="copyTaskStatus" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.copyTaskStatus === '1' ? 'success' : 'danger'">
              {{ scope.row.copyTaskStatus === '1' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" align="center" />
        <el-table-column label="操作" align="center" width="220" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row, '修改文件同步任务')">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
            <el-button link type="primary" @click="handleExecuteOne(scope.row, `是否确认执行文件同步任务“${scope.row.copyTaskSrc} → ${scope.row.copyTaskDst}”？`)">
              <el-icon><VideoPlay /></el-icon> 执行
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Mobile Card List -->
      <div v-if="appStore.device === 'mobile'" v-loading="loading" class="mobile-card-list">
        <div v-for="item in taskList" :key="item.copyTaskId" class="mobile-card">
          <div class="mobile-card-header">
            <span class="mobile-card-title">
              <span class="path-label label-src">源</span> {{ item.copyTaskSrc }}
            </span>
            <el-tag size="small" :type="item.copyTaskStatus === '1' ? 'success' : 'danger'">
              {{ item.copyTaskStatus === '1' ? '启用' : '停用' }}
            </el-tag>
          </div>
          <div class="mobile-card-body">
            <div class="mobile-card-row">
              <span class="mobile-card-label">目标</span>
              <span class="mobile-card-value mobile-card-value-path" :title="item.copyTaskDst">{{ item.copyTaskDst }}</span>
            </div>
            <div v-if="item.monitorDir" class="mobile-card-row">
              <span class="mobile-card-label">监控</span>
              <span class="mobile-card-value mobile-card-value-path" :title="item.monitorDir">{{ item.monitorDir }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">创建时间</span>
              <span class="mobile-card-value mobile-card-value-light">{{ item.createTime }}</span>
            </div>
          </div>
          <div class="mobile-card-actions">
            <el-button link type="primary" size="small" @click="handleUpdate(item, '修改文件同步任务')">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(item)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
            <el-button link type="primary" size="small" @click="handleExecuteOne(item, `是否确认执行文件同步任务“${item.copyTaskSrc} → ${item.copyTaskDst}”？`)">
              <el-icon><VideoPlay /></el-icon> 执行
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
import { useCopyTask } from '@/composables/useCopyTask'
import { useDebounce } from '@/composables/useDebounce'
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect/index.vue'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const showSearch = ref(window.innerWidth >= 768)

const {
  taskList, loading, total, queryParams, queryRef,
  getList, handleQuery, resetQuery,
  selectedIds, single, multiple, handleSelectionChange,
  open, dialogTitle, submitLoading, formRef, form, rules,
  handleAdd, handleUpdate, submitForm,
  handleDelete, handleExecuteOne, handleExecute
} = useCopyTask()

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
.page-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ============================================
   Search Card
   ============================================ */
.search-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);

  :deep(.el-card__body) {
    padding: 14px 16px;
  }
}

/* ============================================
   Table Card
   ============================================ */
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

/* ============================================
   Pagination
   ============================================ */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 12px;
}

/* ============================================
   Sync Config Column (Desktop Table)
   ============================================ */
.path-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.path-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.path-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 3px;
  line-height: 1.4;
}

.label-src {
  color: #409eff;
  background: rgba(64, 158, 255, 0.1);
}

.label-dst {
  color: #67c23a;
  background: rgba(103, 194, 58, 0.1);
}

.label-mon {
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.1);
}

.path-text {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--osr-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ============================================
   Mobile Responsive
   ============================================ */
@media (max-width: 768px) {
  .page-container {
    gap: 10px;
  }

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
    gap: 6px;
    margin-bottom: 10px;

    .action-left {
      gap: 4px;
    }
  }

  .table-card :deep(.el-card__body) {
    padding: 12px;
  }

  /* ============================================
     Mobile Card List
     ============================================ */
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
      justify-content: space-between;
      align-items: center;
      padding: 10px 12px 8px;
      border-bottom: 1px solid var(--osr-border-light);
      background: var(--osr-bg-page);

      .mobile-card-title {
        font-size: 14px;
        font-weight: 600;
        color: var(--osr-text-primary);
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        margin-right: 8px;
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
          line-height: 1.5;
          padding-top: 1px;
        }

        .mobile-card-value {
          flex: 1;
          min-width: 0;
          color: var(--osr-text-primary);
          font-size: 13px;
          line-height: 1.5;
          word-break: break-all;

          &.mobile-card-value-clip {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          &.mobile-card-value-path {
            color: var(--osr-text-placeholder);
            font-size: 12px;
            line-height: 1.6;
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
}
</style>
