<template>
  <div class="mobile-page">
    <!-- 搜索 -->
    <MobileSearchPanel v-model:collapsed="searchCollapsed" :loading="loading" @search="handleQuery" @reset="resetQuery">
      <el-form ref="queryRef" :model="queryParams" label-width="72px">
        <el-form-item label="文件名称" prop="strmFileName">
          <el-input v-model="queryParams.strmFileName" placeholder="请输入文件名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="目录路径" prop="strmPath">
          <el-input v-model="queryParams.strmPath" placeholder="请输入目录路径" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="strmStatus">
          <el-select v-model="queryParams.strmStatus" placeholder="全部状态" clearable style="width: 100%">
            <el-option label="成功" value="1" />
            <el-option label="失败" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
    </MobileSearchPanel>

    <!-- Batch Actions -->
    <div class="batch-bar" v-if="selectedIds.length > 0">
      <span class="selected-count">已选 {{ selectedIds.length }} 项</span>
      <el-button link type="primary" size="small" @click="handleBatchRetry">
        <el-icon><RefreshLeft /></el-icon> 重试
      </el-button>
      <el-button link type="danger" size="small" @click="handleBatchRemoveNetDisk">
        <el-icon><Download /></el-icon> 删网盘
      </el-button>
      <el-button link type="danger" size="small" @click="handleBatchDelete">
        <el-icon><Delete /></el-icon> 删记录
      </el-button>
      <el-button link size="small" @click="clearSelection">
        取消
      </el-button>
    </div>

    <!-- Record List -->
    <div class="record-list" v-loading="loading">
      <div
        v-for="record in recordList"
        :key="record.strmId"
        class="record-card"
        :class="{ selected: selectedIds.includes(record.strmId) }"
        @click="handleCardClick($event, record.strmId)"
      >
        <div class="card-checkbox">
          <el-checkbox
            :model-value="selectedIds.includes(record.strmId)"
            size="large"
            @change="toggleSelect(record.strmId)"
          />
        </div>
        <div class="card-content">
          <div class="card-top">
            <div class="file-name-row">
              <el-icon class="file-icon" :size="18"><VideoCamera /></el-icon>
              <span class="file-name" @click.stop="showFullText(record.strmFileName, '文件名')">{{ record.strmFileName }}</span>
            </div>
            <el-tag :type="record.strmStatus === '1' ? 'success' : 'danger'" size="small" effect="light">
              {{ record.strmStatus === '1' ? '成功' : '失败' }}
            </el-tag>
          </div>
          <div class="file-path" @click.stop="showFullText(record.strmPath, '路径')">
            <el-icon class="path-icon"><Location /></el-icon>
            <span class="path-text">{{ record.strmPath }}</span>
          </div>
          <div class="card-time">
            <el-icon><Clock /></el-icon>
            {{ record.createTime }}
          </div>
        </div>
        <div class="card-actions" @click.stop>
          <el-button link type="primary" size="small" :icon="Refresh" @click="handleRetryOne(record)">
            重试
          </el-button>
          <el-button link type="warning" size="small" :icon="Download" @click="handleRemoveNetDiskOne(record)">
            删网盘
          </el-button>
          <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteOne(record)">
            删记录
          </el-button>
        </div>
      </div>

      <el-empty v-if="!loading && recordList.length === 0" description="暂无STRM记录" />
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
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  VideoCamera, Location, Clock,
  RefreshLeft, Refresh, Delete, Download
} from '@element-plus/icons-vue'
import MobileSearchPanel from '@/components/mobile/MobileSearchPanel.vue'
import MobilePager from '@/components/mobile/MobilePager.vue'
import FullTextDialog from '@/components/mobile/FullTextDialog.vue'
import { useStrmRecord } from '@/composables/useStrmRecord'

const searchCollapsed = ref(true)

const {
  recordList, loading, total, queryParams, totalPages,
  getList, prevPage, nextPage, handleSizeChange,
  queryRef, dateRange, handleQuery, resetQuery,
  selectedIds, toggleSelect, handleCardClick, clearSelection,
  handleRetryOne, handleBatchRetry, handleDeleteOne, handleBatchDelete,
  handleRemoveNetDiskOne, handleBatchRemoveNetDisk
} = useStrmRecord()

const fullTextRef = ref<InstanceType<typeof FullTextDialog>>()
const showFullText = (content: string, title: string) => fullTextRef.value?.show(content, title)

getList()
</script>

<style scoped lang="scss">
.mobile-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: calc(100vh - 120px);
  padding-bottom: 8px;

  .record-list {
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
  flex-wrap: wrap;

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
    margin-left: 0;
  }
}

/* ============================================
   Record List
   ============================================ */
.record-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 200px;
}

.record-card {
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

  .file-name-row {
    display: flex;
    align-items: center;
    gap: 5px;
    min-width: 0;
    flex: 1;

    .file-icon {
      color: var(--osr-primary);
      flex-shrink: 0;
    }

    .file-name {
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

  .file-path {
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

</style>
