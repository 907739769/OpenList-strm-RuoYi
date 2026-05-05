<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="文件名称" prop="strmFileName">
          <el-input v-model="queryParams.strmFileName" placeholder="请输入文件名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="目录路径" prop="strmPath">
          <el-input v-model="queryParams.strmPath" placeholder="请输入目录路径" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="strmStatus">
          <el-select v-model="queryParams.strmStatus" placeholder="状态" clearable>
            <el-option label="成功" value="1" />
            <el-option label="失败" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="-"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD"
          />
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
          <el-button type="danger" :disabled="multiple" @click="handleDelete()">
            <el-icon><Delete /></el-icon> 批量删除记录
          </el-button>
          <el-button type="danger" :disabled="multiple" @click="handleBatchRemoveNetDisk()">
            <el-icon><Download /></el-icon> 批量删除网盘文件
          </el-button>
          <el-button type="primary" :disabled="multiple" @click="handleBatchRetry()">
            <el-icon><Refresh /></el-icon> 批量重试
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <!-- Desktop Table -->
      <el-table v-if="appStore.device === 'desktop'" v-loading="loading" :data="recordList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="文件信息" min-width="300">
          <template #default="scope">
            <div class="file-info-box">
              <div class="file-name"><i class="fa fa-file-video-o"></i> {{ scope.row.strmFileName }}</div>
              <div class="file-path" :title="scope.row.strmPath">{{ scope.row.strmPath }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="strmStatus" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.strmStatus === '1' ? 'success' : 'danger'">
              {{ scope.row.strmStatus === '1' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" align="center" />
        <el-table-column label="操作" align="center" width="260" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleRetryOne(scope.row)">
              <el-icon><Refresh /></el-icon> 重试生成
            </el-button>
            <el-button link type="warning" @click="handleRemoveNetDiskOne(scope.row)">
              <el-icon><Download /></el-icon> 删除网盘源文件
            </el-button>
            <el-button link type="danger" @click="handleDeleteOne(scope.row)">
              <el-icon><Delete /></el-icon> 仅删除记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Mobile Card List -->
      <div v-if="appStore.device === 'mobile'" v-loading="loading" class="mobile-card-list">
        <div v-for="item in recordList" :key="item.strmId" class="mobile-card">
          <div class="mobile-card-header">
            <span class="mobile-card-title"><i class="fa fa-file-video-o"></i> {{ item.strmFileName }}</span>
            <el-tag size="small" :type="item.strmStatus === '1' ? 'success' : 'danger'">
              {{ item.strmStatus === '1' ? '成功' : '失败' }}
            </el-tag>
          </div>
          <div class="mobile-card-body">
            <div class="mobile-card-row">
              <span class="mobile-card-label">目录路径</span>
              <span class="mobile-card-value mobile-card-value-path" :title="item.strmPath">{{ item.strmPath }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">创建时间</span>
              <span class="mobile-card-value mobile-card-value-light">{{ item.createTime }}</span>
            </div>
          </div>
          <div class="mobile-card-actions">
            <el-button link type="primary" size="small" @click="handleRetryOne(item)">
              <el-icon><Refresh /></el-icon> 重试
            </el-button>
            <el-button link type="warning" size="small" @click="handleRemoveNetDiskOne(item)">
              <el-icon><Download /></el-icon> 删网盘
            </el-button>
            <el-button link type="danger" size="small" @click="handleDeleteOne(item)">
              <el-icon><Delete /></el-icon> 删记录
            </el-button>
          </div>
        </div>
        <el-empty v-if="!recordList.length" description="暂无数据" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, Download, Filter } from '@element-plus/icons-vue'
import { getStrmRecordListApi, retryStrmRecordApi, batchDeleteStrmRecordApi, batchRetryStrmRecordApi, batchRemoveStrmNetDiskApi } from '@/api/openlist/strmRecord'
import { useAppStore } from '@/stores/app'
import type { SearchParams, PageResult } from '@/types'

const appStore = useAppStore()
const showSearch = ref(window.innerWidth >= 768)

const recordList = ref<any[]>([])
const loading = ref(true)
const total = ref(0)
const multiple = ref(true)
const selectedIds = ref<number[]>([])
const dateRange = ref<string[] | null>(null)

const queryParams = reactive<SearchParams & { strmFileName?: string; strmPath?: string; strmStatus?: string }>({
  pageNum: 1,
  pageSize: 10,
  strmStatus: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getStrmRecordListApi(queryParams) as PageResult
    recordList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  if (dateRange.value != null && dateRange.value.length === 2) {
    queryParams.params = { beginTime: dateRange.value[0] + ' 00:00:00', endTime: dateRange.value[1] + ' 23:59:59' }
  } else {
    delete queryParams.params
  }
  getList()
}
const resetQuery = () => {
  dateRange.value = null
  if (queryRef.value) (queryRef.value as any).resetFields()
  handleQuery()
}
const handleSelectionChange = (selection: any[]) => { multiple.value = !selection.length; selectedIds.value = selection.map((item: any) => item.strmId) }

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(`是否确认删除STRM记录编号为"${selectedIds.value}"的数据项？`, '警告', { type: 'warning' })
    await batchDeleteStrmRecordApi(selectedIds.value)
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleRetryOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认重试STRM记录"${row.strmId}"？`, '警告', { type: 'warning' })
    await retryStrmRecordApi(row.strmId)
    ElMessage.success('重试成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchRetry = async () => {
  try {
    await ElMessageBox.confirm(`是否确认批量重试选中的STRM记录？`, '警告', { type: 'warning' })
    await batchRetryStrmRecordApi(selectedIds.value)
    ElMessage.success('批量重试成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchRemoveNetDisk = async () => {
  try {
    await ElMessageBox.confirm(`危险操作：确认要从网盘中彻底删除选中的 ${selectedIds.value.length} 个文件吗？`, '警告', { type: 'error' })
    await batchRemoveStrmNetDiskApi(selectedIds.value)
    ElMessage.success('删除网盘文件成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleRemoveNetDiskOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`危险操作：确认要从网盘中彻底删除该文件吗？`, '警告', { type: 'error' })
    await batchRemoveStrmNetDiskApi([row.strmId])
    ElMessage.success('删除网盘文件成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleDeleteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认删除STRM记录编号为"${row.strmId}"的数据项？`, '警告', { type: 'warning' })
    await batchDeleteStrmRecordApi([row.strmId])
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const queryRef = ref<any>()
getList()
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
        i { color: var(--osr-primary); margin-right: 4px; }
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
