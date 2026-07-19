<template>
  <div class="mobile-page">
    <MobileSearchPanel v-model:collapsed="searchCollapsed" :loading="loading" @search="handleQuery" @reset="resetQuery">
      <el-form ref="queryRef" :model="queryParams" label-width="72px">
        <el-form-item label="影视名称" prop="title">
          <el-input v-model="queryParams.title" placeholder="请输入影视名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-select v-model="queryParams.reason" placeholder="全部原因" clearable style="width: 100%">
            <el-option label="本地文件丢失" value="local_missing" />
            <el-option label="网盘源丢失" value="source_missing" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 100%">
            <el-option label="待处理" value="0" />
            <el-option label="已清理" value="1" />
            <el-option label="已忽略" value="2" />
          </el-select>
        </el-form-item>
      </el-form>
    </MobileSearchPanel>

    <div class="scan-bar">
      <el-button type="primary" size="small" :loading="scanning" @click="handleScanNow">
        <el-icon><Refresh /></el-icon> 立即扫描
      </el-button>
    </div>

    <div class="batch-bar" v-if="selectedIds.length > 0">
      <span class="selected-count">已选 {{ selectedIds.length }} 项</span>
      <el-button link type="danger" size="small" @click="handleBatchClean">
        <el-icon><Delete /></el-icon> 清理
      </el-button>
      <el-button link type="warning" size="small" @click="handleBatchIgnore">
        <el-icon><Warning /></el-icon> 忽略
      </el-button>
      <el-button link size="small" @click="clearSelection">
        取消
      </el-button>
    </div>

    <div v-loading="loading" class="mobile-card-list">
      <div v-for="item in recordList" :key="item.id" class="mobile-card" @click="handleCardClick($event, item.id)">
        <div class="mobile-card-header">
          <el-checkbox class="card-checkbox" :model-value="selectedIds.includes(item.id)" @change="toggleSelect(item.id)" @click.stop />
          <span class="mobile-title">{{ item.title || '未知' }}<span v-if="item.year">（{{ item.year }}）</span></span>
          <el-tag v-if="item.reason === 'local_missing'" type="warning" size="small">本地丢失</el-tag>
          <el-tag v-else type="danger" size="small">网盘源丢失</el-tag>
        </div>
        <div class="mobile-card-body">
          <div class="mobile-card-row">
            <span class="mobile-card-label">路径</span>
            <span class="mobile-card-value mobile-card-value-path" :title="`${item.newPath}/${item.newName}`">{{ item.newPath }}/{{ item.newName }}</span>
          </div>
          <div class="mobile-card-row">
            <span class="mobile-card-label">发现时间</span>
            <span class="mobile-card-value mobile-card-value-light">{{ item.foundTime }}</span>
          </div>
        </div>
        <div class="mobile-card-actions" v-if="item.status === '0'">
          <el-button link type="danger" size="small" @click.stop="handleCleanOne(item)">
            <el-icon><Delete /></el-icon> 清理
          </el-button>
          <el-button link type="warning" size="small" @click.stop="handleIgnoreOne(item)">
            <el-icon><Warning /></el-icon> 忽略
          </el-button>
        </div>
      </div>
      <el-empty v-if="!recordList.length" description="暂无数据" />
    </div>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        layout="prev, pager, next"
        small
        @current-change="getList"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Refresh, Delete, Warning } from '@element-plus/icons-vue'
import MobileSearchPanel from '@/components/mobile/MobileSearchPanel.vue'
import { useRenameOrphanList } from '@/composables/useRenameOrphanList'

const searchCollapsed = ref(true)

const {
  recordList, loading, total, queryParams,
  getList, queryRef, handleQuery, resetQuery,
  selectedIds, toggleSelect, handleCardClick, clearSelection,
  handleCleanOne, handleBatchClean,
  scanning, handleScanNow,
  handleIgnoreOne, handleBatchIgnore
} = useRenameOrphanList()

getList()
</script>

<style scoped lang="scss">
.mobile-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
}

.scan-bar {
  display: flex;
  justify-content: flex-end;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--osr-bg-page);
  border-radius: 8px;

  .selected-count {
    font-size: 13px;
    color: var(--osr-text-secondary);
    margin-right: auto;
  }
}

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
    align-items: center;
    gap: 6px;
    padding: 10px 12px 8px;
    border-bottom: 1px solid var(--osr-border-light);
    background: var(--osr-bg-page);

    .mobile-title {
      flex: 1;
      min-width: 0;
      font-size: 13px;
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
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
      }

      .mobile-card-value {
        flex: 1;
        min-width: 0;
        font-size: 13px;
        word-break: break-all;

        &.mobile-card-value-path {
          color: var(--osr-text-placeholder);
          font-size: 12px;
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

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}
</style>
