<template>
  <div class="mobile-page">
    <!-- 搜索 -->
    <MobileSearchPanel v-model:collapsed="searchCollapsed" :loading="loading" @search="handleQuery" @reset="resetQuery">
      <el-form ref="queryRef" :model="queryParams" label-width="72px">
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
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 100%">
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
      <el-button link type="primary" size="small" @click="handleBatchExecute">
        <el-icon><RefreshLeft /></el-icon> 执行
      </el-button>
      <el-button link type="danger" size="small" @click="handleBatchDelete">
        <el-icon><Delete /></el-icon> 删记录
      </el-button>
      <el-button link type="warning" size="small" @click="handleBatchScrape">
        <el-icon><Refresh /></el-icon> 刮削
      </el-button>
      <el-button link type="danger" size="small" @click="handleBatchDeleteScrape">
        <el-icon><Delete /></el-icon> 删刮削
      </el-button>
      <el-button link size="small" @click="clearSelection">
        取消
      </el-button>
    </div>

    <!-- Record List -->
    <div class="record-list" v-loading="loading">
      <div
        v-for="record in recordList"
        :key="record.id"
        class="record-card"
        :class="{ selected: selectedIds.includes(record.id) }"
        @click="handleCardClick($event, record.id)"
      >
        <div class="card-checkbox">
          <el-checkbox
            :model-value="selectedIds.includes(record.id)"
            size="large"
            @change="toggleSelect(record.id)"
          />
        </div>
        <div class="card-content">
          <!-- Rename comparison header -->
          <div class="rename-compare-header">
            <div class="rename-side rename-original-side">
              <span class="rename-label rename-label-original">原</span>
              <span class="rename-filename rename-filename-original" @click.stop="showFullText(record.originalName, '原文件名')" :title="record.originalName">
                {{ record.originalName }}
              </span>
            </div>
            <el-icon class="rename-arrow-icon" :size="16"><ArrowRight /></el-icon>
            <div class="rename-side rename-new-side">
              <span class="rename-label rename-label-new">新</span>
              <span class="rename-filename rename-filename-new" @click.stop="showFullText(record.newName, '新文件名')" :title="record.newName">
                {{ record.newName }}
              </span>
            </div>
          </div>
          <div class="mobile-status-row">
            <el-tag :type="record.status === '1' ? 'success' : 'danger'" size="small" effect="light" class="status-tag">
              {{ record.status === '1' ? '成功' : '失败' }}
            </el-tag>
            <el-tag v-if="record.scrapeStatus === '1'" type="success" size="small" class="scrape-tag">NFO</el-tag>
            <el-tag v-else-if="record.scrapeStatus === '2'" type="danger" size="small" class="scrape-tag">刮削失败</el-tag>
            <el-tag v-else-if="record.scrapeStatus === '0'" type="info" size="small" class="scrape-tag">未刮削</el-tag>
          </div>
          <!-- Path comparison -->
          <div class="rename-paths">
            <div class="rename-path-item rename-path-original" @click.stop="showFullText(record.originalPath, '原路径')">
              <el-icon class="path-icon"><Location /></el-icon>
              <span class="path-text">{{ record.originalPath }}</span>
            </div>
            <el-icon class="rename-path-arrow" :size="12"><ArrowRight /></el-icon>
            <div class="rename-path-item rename-path-new" @click.stop="showFullText(record.newPath, '新路径')">
              <el-icon class="path-icon"><Location /></el-icon>
              <span class="path-text">{{ record.newPath }}</span>
            </div>
          </div>
          <div class="card-time">
            <el-icon><Clock /></el-icon>
            {{ record.createTime }}
          </div>
        </div>
        <div class="card-actions" @click.stop>
          <el-button link type="warning" size="small" :icon="Refresh" @click="handleScrapeOne(record)">
            刮削
          </el-button>
          <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteScrapeOne(record)" v-if="record.scrapeStatus === '1'">
            删刮削
          </el-button>
          <el-button link type="primary" size="small" :icon="RefreshLeft" @click="handleRetryOne(record)">
            重试
          </el-button>
          <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteOne(record)">
            删记录
          </el-button>
        </div>
      </div>

      <el-empty v-if="!loading && recordList.length === 0" description="暂无重命名记录" />
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

    <!-- Edit & Rename Dialog -->
    <el-dialog v-model="retryDialogVisible" title="重试重命名" width="85%" @close="handleRetryClose">
      <el-form ref="retryFormRef" :model="retryForm" :rules="retryRules" label-width="60px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="retryForm.title" placeholder="留空则使用原值" maxlength="100" clearable />
        </el-form-item>
        <el-form-item label="年份" prop="year">
          <el-input v-model="retryForm.year" placeholder="留空则使用原值" maxlength="4" clearable />
        </el-form-item>
        <el-form-item label="季" prop="season" v-if="retryForm.mediaType === 'tv'">
          <el-input v-model="retryForm.season" placeholder="如 01" maxlength="4" clearable />
        </el-form-item>
        <el-form-item label="集" prop="episode" v-if="retryForm.mediaType === 'tv'">
          <el-input v-model="retryForm.episode" placeholder="如 05" maxlength="6" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="retryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRetrySubmit" :loading="retryLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  ArrowRight, Location, Clock,
  RefreshLeft, Refresh, Delete
} from '@element-plus/icons-vue'
import MobileSearchPanel from '@/components/mobile/MobileSearchPanel.vue'
import MobilePager from '@/components/mobile/MobilePager.vue'
import FullTextDialog from '@/components/mobile/FullTextDialog.vue'
import { useRenameDetailList } from '@/composables/useRenameDetailList'

const searchCollapsed = ref(true)

const fullTextRef = ref<InstanceType<typeof FullTextDialog>>()
const showFullText = (content: string, title: string) => fullTextRef.value?.show(content, title)

const {
  recordList, loading, total, queryParams, totalPages,
  getList, prevPage, nextPage, handleSizeChange,
  queryRef, dateRange, handleQuery, resetQuery,
  selectedIds, toggleSelect, handleCardClick, clearSelection,
  handleDeleteOne, handleBatchDelete,
  retryDialogVisible, retryLoading, retryFormRef, retryForm, retryRules,
  handleRetryOne, handleRetryClose, handleRetrySubmit,
  handleBatchExecute, handleScrapeOne, handleBatchScrape,
  handleDeleteScrapeOne, handleBatchDeleteScrape
} = useRenameDetailList()

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
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  /* Rename comparison header */
  .rename-compare-header {
    display: flex;
    align-items: center;
    gap: 4px;

    .rename-side {
      display: flex;
      align-items: center;
      gap: 3px;
      min-width: 0;
      flex: 1;

      .rename-label {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 18px;
        height: 18px;
        border-radius: 4px;
        font-size: 10px;
        font-weight: 700;
        flex-shrink: 0;

        &.rename-label-original {
          background: #fef2f2;
          color: #ef4444;
          border: 1px solid #fecaca;
        }

        &.rename-label-new {
          background: #f0fdf4;
          color: #22c55e;
          border: 1px solid #bbf7d0;
        }
      }

      .rename-filename {
        font-size: 14px;
        font-weight: 500;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        line-height: 1.4;
        cursor: pointer;
        word-break: break-all;

        &.rename-filename-original {
          color: #dc2626;
          text-decoration: line-through;
          text-decoration-color: #dc2626;
          flex: 1;
          min-width: 0;
        }

        &.rename-filename-new {
          color: #16a34a;
          font-weight: 600;
          flex: 1;
          min-width: 0;
        }
      }
    }

    .rename-arrow-icon {
      flex-shrink: 0;
      color: var(--osr-text-disabled);
    }
  }

  .status-tag {
    align-self: flex-start;
  }

  .mobile-status-row {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-wrap: wrap;
  }

  .scrape-tag {
    font-size: 11px;
  }

  /* Path comparison */
  .rename-paths {
    display: flex;
    align-items: center;
    gap: 3px;
    font-size: 11px;

    .rename-path-item {
      display: flex;
      align-items: flex-start;
      gap: 3px;
      flex: 1;
      min-width: 0;
      cursor: pointer;

      .path-icon {
        flex-shrink: 0;
        margin-top: 1px;
        color: var(--osr-text-disabled);
        font-size: 12px;
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
        line-height: 1.4;
      }

      &:hover .path-text {
        color: var(--osr-primary);
      }

      &.rename-path-original .path-text {
        color: var(--osr-text-secondary);
      }

      &.rename-path-new .path-text {
        color: var(--osr-success);
      }
    }

    .rename-path-arrow {
      flex-shrink: 0;
      color: var(--osr-text-disabled);
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
