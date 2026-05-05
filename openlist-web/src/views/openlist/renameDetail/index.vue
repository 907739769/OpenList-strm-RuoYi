<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="原文件名" prop="originalName">
          <el-input v-model="queryParams.originalName" placeholder="请输入原文件名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="新文件名" prop="newName">
          <el-input v-model="queryParams.newName" placeholder="请输入新文件名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="原目录" prop="originalPath">
          <el-input v-model="queryParams.originalPath" placeholder="请输入原目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="新目录" prop="newPath">
          <el-input v-model="queryParams.newPath" placeholder="请输入新目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="影视名称" prop="title">
          <el-input v-model="queryParams.title" placeholder="请输入影视名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="状态" clearable>
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
            <el-icon><Delete /></el-icon> 批量删除
          </el-button>
          <el-button type="info" :disabled="multiple" @click="handleBatchExecute()">
            <el-icon><Refresh /></el-icon> 批量执行
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <!-- Desktop Table -->
      <el-table v-if="appStore.device === 'desktop'" v-loading="loading" :data="detailList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="文件信息" min-width="300">
          <template #default="scope">
            <div class="file-change-box">
              <div class="file-row">
                <span class="file-label label-src">原</span>
                <span class="file-name">{{ scope.row.originalFileName }}</span>
                <span class="file-path">{{ scope.row.originalFilePath }}</span>
              </div>
              <div class="file-row">
                <span class="file-label label-dst">新</span>
                <span class="file-name">{{ scope.row.newFileName }}</span>
                <span class="file-path">{{ scope.row.newFilePath }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === '0' ? 'danger' : 'success'">
              {{ scope.row.status === '0' ? '失败' : '成功' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" align="center" />
        <el-table-column label="操作" align="center" width="260" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleRetryOne(scope.row)">
              <el-icon><Refresh /></el-icon> 重试
            </el-button>
            <el-button link type="warning" @click="handleRemoveNetDiskOne(scope.row)">
              <el-icon><Download /></el-icon> 删除网盘文件
            </el-button>
            <el-button link type="danger" @click="handleDeleteOne(scope.row)">
              <el-icon><Delete /></el-icon> 删除记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Mobile Card List -->
      <div v-if="appStore.device === 'mobile'" v-loading="loading" class="mobile-card-list">
        <div v-for="item in detailList" :key="item.id" class="mobile-card">
          <div class="mobile-card-header">
            <span class="mobile-card-title">{{ item.originalFileName }}</span>
            <el-tag size="small" :type="item.status === '0' ? 'danger' : 'success'">
              {{ item.status === '0' ? '失败' : '成功' }}
            </el-tag>
          </div>
          <div class="mobile-card-body">
            <div class="mobile-card-row">
              <span class="mobile-card-label">原路径</span>
              <span class="mobile-card-value mobile-card-value-clip">{{ item.originalFilePath }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">新文件名</span>
              <span class="mobile-card-value mobile-card-value-clip">{{ item.newFileName }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">新路径</span>
              <span class="mobile-card-value mobile-card-value-clip">{{ item.newFilePath }}</span>
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
        <el-empty v-if="!detailList.length" description="暂无数据" />
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
import { Search, Refresh, Delete, Filter } from '@element-plus/icons-vue'
import { getRenameDetailListApi, executeRenameDetailApi } from '@/api/openlist/renameDetail'
import { useAppStore } from '@/stores/app'
import type { SearchParams, PageResult } from '@/types'

const appStore = useAppStore()
const showSearch = ref(appStore.device === 'desktop')

const detailList = ref<any[]>([])
const loading = ref(true)
const total = ref(0)
const multiple = ref(true)
const selectedIds = ref<number[]>([])
const dateRange = ref<string[] | null>(null)

const queryParams = reactive<SearchParams & { originalName?: string; newName?: string; originalPath?: string; newPath?: string; title?: string; status?: string }>({
  pageNum: 1,
  pageSize: 10,
  status: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getRenameDetailListApi(queryParams) as PageResult
    detailList.value = res.records
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
const handleSelectionChange = (selection: any[]) => { multiple.value = !selection.length; selectedIds.value = selection.map((item: any) => item.id) }

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(`是否确认删除重命名详情编号为"${selectedIds.value}"的数据项？`, '警告', { type: 'warning' })
    await executeRenameDetailApi(selectedIds.value.map(id => id)) // reuse delete via batch
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleDeleteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认删除重命名详情编号为"${row.id}"的数据项？`, '警告', { type: 'warning' })
    await executeRenameDetailApi([row.id])
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchExecute = async () => {
  try {
    await ElMessageBox.confirm(`是否确认批量执行选中的重命名详情？`, '警告', { type: 'warning' })
    await executeRenameDetailApi(selectedIds.value)
    ElMessage.success('批量执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleEdit = (_row: any) => {
  ElMessage.info('编辑功能待实现')
}

const handleExecuteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认执行重命名详情"${row.id}"？`, '警告', { type: 'warning' })
    await executeRenameDetailApi([row.id])
    ElMessage.success('执行成功')
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
