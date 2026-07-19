<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="影视名称" prop="title">
          <el-input v-model="queryParams.title" placeholder="请输入影视名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-select v-model="queryParams.reason" placeholder="全部原因" clearable :style="{ width: '160px' }">
            <el-option label="本地文件丢失" value="local_missing" />
            <el-option label="网盘源丢失" value="source_missing" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable :style="{ width: '120px' }">
            <el-option label="待处理" value="0" />
            <el-option label="已清理" value="1" />
            <el-option label="已忽略" value="2" />
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
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" :loading="scanning" @click="handleScanNow">
            <el-icon><Refresh /></el-icon> 立即扫描
          </el-button>
          <el-button type="danger" :disabled="multiple" @click="handleBatchClean()">
            <el-icon><Delete /></el-icon> 批量清理
          </el-button>
          <el-button type="warning" :disabled="multiple" @click="handleBatchIgnore()">
            <el-icon><Warning /></el-icon> 批量忽略
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="recordList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="标题" min-width="200">
          <template #default="scope">
            <span>{{ scope.row.title || '未知' }}</span>
            <span v-if="scope.row.year" class="orphan-year">（{{ scope.row.year }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="原因" width="130" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.reason === 'local_missing'" type="warning" size="small">本地文件丢失</el-tag>
            <el-tag v-else-if="scope.row.reason === 'source_missing'" type="danger" size="small">网盘源丢失</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重命名后路径" min-width="320">
          <template #default="scope">
            <span class="orphan-path" :title="`${scope.row.newPath}/${scope.row.newName}`">{{ scope.row.newPath }}/{{ scope.row.newName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.status === '0'" type="info" size="small">待处理</el-tag>
            <el-tag v-else-if="scope.row.status === '1'" type="success" size="small">已清理</el-tag>
            <el-tag v-else type="info" size="small">已忽略</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发现时间" prop="foundTime" width="170" align="center" />
        <el-table-column label="操作" align="center" width="180" fixed="right">
          <template #default="scope">
            <el-button link type="danger" @click="handleCleanOne(scope.row)" v-if="scope.row.status === '0'">
              <el-icon><Delete /></el-icon> 清理
            </el-button>
            <el-button link type="warning" @click="handleIgnoreOne(scope.row)" v-if="scope.row.status === '0'">
              <el-icon><Warning /></el-icon> 忽略
            </el-button>
          </template>
        </el-table-column>
      </el-table>

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
import { ref } from 'vue'
import { Search, Refresh, Delete, Filter, Warning } from '@element-plus/icons-vue'
import { useRenameOrphanList } from '@/composables/useRenameOrphanList'

const showSearch = ref(window.innerWidth >= 768)

const {
  recordList, loading, total, queryParams,
  getList, queryRef, handleQuery, resetQuery,
  multiple, handleSelectionChange,
  handleCleanOne, handleBatchClean,
  scanning, handleScanNow,
  handleIgnoreOne, handleBatchIgnore
} = useRenameOrphanList()

getList()
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);

  :deep(.el-card__body) {
    padding: 14px 16px;
  }
}

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

.orphan-year {
  color: var(--osr-text-secondary);
  font-size: 12px;
}

.orphan-path {
  color: var(--osr-text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 12px;
}

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
}
</style>
