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
        <el-icon><RefreshLeft /></el-icon> 批量执行
      </el-button>
      <el-button link type="danger" size="small" @click="handleBatchDelete">
        <el-icon><Delete /></el-icon> 批量删除
      </el-button>
      <el-button link type="warning" size="small" @click="handleBatchScrape">
        <el-icon><Refresh /></el-icon> 批量刮削
      </el-button>
      <el-button link type="danger" size="small" @click="handleBatchDeleteScrape">
        <el-icon><Delete /></el-icon> 删除刮削
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
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  ArrowRight, Location, Clock,
  RefreshLeft, Refresh, Delete
} from '@element-plus/icons-vue'
import MobileSearchPanel from '@/components/mobile/MobileSearchPanel.vue'
import MobilePager from '@/components/mobile/MobilePager.vue'
import FullTextDialog from '@/components/mobile/FullTextDialog.vue'
import {
  getRenameDetailListApi,
  executeRenameDetailApi,
  batchDeleteRenameDetailApi,
  scrapeRenameDetailApi,
  batchScrapeRenameDetailApi,
  deleteScrapeFilesApi,
  batchDeleteScrapeFilesApi
} from '@/api/openlist/renameDetail'
import type { SearchParams, PageResult } from '@/types'

const recordList = ref<any[]>([])
const loading = ref(true)
const total = ref(0)
const selectedIds = ref<number[]>([])
const dateRange = ref<string[] | null>(null)
const searchCollapsed = ref(true)
const queryRef = ref<any>()

// Edit & Rename dialog
const retryDialogVisible = ref(false)
const retryLoading = ref(false)
const retryFormRef = ref<FormInstance>()
const retryForm = reactive({ id: 0, title: '', year: '', season: '', episode: '', mediaType: '' })
const retryRules = reactive<FormRules>({
  title: [{ max: 100, message: '最多 100 个字符', trigger: 'blur' }],
  year: [{ pattern: /^\d{0,4}$/, message: '年份为 4 位数字', trigger: 'blur' }],
  season: [{ pattern: /^\d{1,2}$/, message: '季为 1-2 位数字', trigger: 'blur' }],
  episode: [{ pattern: /^\d{1,4}$/, message: '集为 1-4 位数字', trigger: 'blur' }]
})

const fullTextRef = ref<InstanceType<typeof FullTextDialog>>()
const showFullText = (content: string, title: string) => fullTextRef.value?.show(content, title)

const totalPages = computed(() => Math.ceil(total.value / queryParams.pageSize) || 1)

const queryParams = reactive<SearchParams & {
  originalName?: string
  newName?: string
  originalPath?: string
  newPath?: string
  title?: string
  status?: string
}>({
  pageNum: 1,
  pageSize: 10,
  status: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getRenameDetailListApi(queryParams) as PageResult
    recordList.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  if (dateRange.value != null && dateRange.value.length === 2) {
    queryParams.params = {
      beginTime: dateRange.value[0] + ' 00:00:00',
      endTime: dateRange.value[1] + ' 23:59:59'
    }
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

const handleRetryOne = (row: any) => {
  retryForm.id = row.id
  retryForm.title = row.title || ''
  retryForm.year = row.year || ''
  retryForm.season = row.season || ''
  retryForm.episode = row.episode || ''
  retryForm.mediaType = row.mediaType || ''
  retryDialogVisible.value = true
}

const handleRetryClose = () => {
  retryFormRef.value?.resetFields()
}

const handleRetrySubmit = async () => {
  await retryFormRef.value?.validate()
  retryLoading.value = true
  try {
    await executeRenameDetailApi([retryForm.id], retryForm.title || undefined, retryForm.year || undefined, retryForm.season || undefined, retryForm.episode || undefined)
    ElMessage.success('编辑并重命名成功')
    retryDialogVisible.value = false
    getList()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    retryLoading.value = false
  }
}

const handleBatchExecute = async () => {
  try {
    await ElMessageBox.confirm(`是否确认批量执行选中的 ${selectedIds.value.length} 条记录？`, '提示', { type: 'warning' })
    await executeRenameDetailApi(selectedIds.value)
    ElMessage.success('批量执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(`是否确认删除选中的 ${selectedIds.value.length} 条记录？`, '警告', { type: 'warning' })
    await batchDeleteRenameDetailApi(selectedIds.value)
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleDeleteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认删除重命名记录"${row.originalName}"？`, '警告', { type: 'warning' })
    await batchDeleteRenameDetailApi([row.id])
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleScrapeOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认对"${row.newName}"执行刮削？`, '提示', { type: 'info' })
    await scrapeRenameDetailApi(row.id)
    ElMessage.success('刮削已启动')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchScrape = async () => {
  try {
    await ElMessageBox.confirm(`是否确认批量刮削选中的 ${selectedIds.value.length} 条记录？`, '提示', { type: 'info' })
    await batchScrapeRenameDetailApi(selectedIds.value)
    ElMessage.success('批量刮削已启动')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleDeleteScrapeOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认删除"${row.newName}"的刮削文件（NFO + 图片）？`, '删除刮削文件', { type: 'warning' })
    await deleteScrapeFilesApi(row.id)
    ElMessage.success('刮削文件已删除')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchDeleteScrape = async () => {
  try {
    await ElMessageBox.confirm(`是否确认删除选中记录的刮削文件？`, '批量删除刮削', { type: 'warning' })
    await batchDeleteScrapeFilesApi(selectedIds.value)
    ElMessage.success('刮削文件已删除')
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
  overflow-x: auto;

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
