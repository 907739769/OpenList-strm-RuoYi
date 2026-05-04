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

      <!-- Table -->
      <el-table v-loading="loading" :data="detailList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="媒体信息" width="120">
          <template #default="scope">
            <div>
              <div class="media-info-title">{{ scope.row.title || '未识别' }}{{ scope.row.year ? ' (' + scope.row.year + ')' : '' }}</div>
              <div class="media-info-meta">
                {{ scope.row.mediaType || '' }}{{ scope.row.season ? ' · S' + scope.row.season : '' }}{{ scope.row.episode ? ' E' + scope.row.episode : '' }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="文件变更对比" min-width="300">
          <template #default="scope">
            <div class="file-change-box">
              <div class="file-row">
                <span class="file-label label-old">原</span>
                <span class="file-name">{{ scope.row.originalName }}</span>
              </div>
              <div class="file-path">{{ scope.row.originalPath }}</div>
              <div class="file-row">
                <span class="file-label label-new">新</span>
                <span class="file-name">{{ scope.row.newName || '...' }}</span>
              </div>
              <div class="file-path" v-if="scope.row.newPath">{{ scope.row.newPath }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="参数" width="200">
          <template #default="scope">
            <div class="tech-badges">
              <el-tag v-if="scope.row.resolution" size="small" type="info">{{ scope.row.resolution }}</el-tag>
              <el-tag v-if="scope.row.videoCodec" size="small" type="primary">{{ scope.row.videoCodec }}</el-tag>
              <el-tag v-if="scope.row.source" size="small" type="warning">{{ scope.row.source }}</el-tag>
              <el-tag v-if="scope.row.releaseGroup" size="small">{{ scope.row.releaseGroup }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === '1' ? 'success' : 'danger'">
              {{ scope.row.status === '1' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" align="center" />
        <el-table-column label="操作" align="center" width="220" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="handleDeleteOne(scope.row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
            <el-button link type="primary" @click="handleExecuteOne(scope.row)">
              <el-icon><VideoPlay /></el-icon> 执行
            </el-button>
          </template>
        </el-table-column>
      </el-table>

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
import { Search, Refresh, Delete, VideoPlay, Filter } from '@element-plus/icons-vue'
import { getRenameDetailListApi, executeRenameDetailApi } from '@/api/openlist/renameDetail'
import type { SearchParams, PageResult } from '@/types'

const detailList = ref<any[]>([])
const loading = ref(true)
const showSearch = ref(true)
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
</style>
