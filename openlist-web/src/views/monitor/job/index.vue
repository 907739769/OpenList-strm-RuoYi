<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="任务名称" prop="jobName">
          <el-input v-model="queryParams.jobName" placeholder="请输入任务名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="任务状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="任务状态" clearable>
            <el-option label="正常" value="0" />
            <el-option label="暂停" value="1" />
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
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <!-- Desktop Table -->
      <el-table v-if="appStore.device === 'desktop'" v-loading="loading" :data="jobList" class="modern-table">
        <el-table-column label="任务名称" prop="jobName" min-width="140" show-overflow-tooltip />
        <el-table-column label="cron执行表达式" prop="cronExpression" width="140" align="center" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-switch
              v-model="scope.row.status"
              :active-value="'0'"
              :inactive-value="'1'"
              @change="handleSwitchChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="240" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row, '修改定时任务')">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="primary" @click="handleRun(scope.row)">
              <el-icon><VideoPlay /></el-icon> 执行
            </el-button>
            <el-button link type="info" @click="handleViewLogs(scope.row)">
              <el-icon><List /></el-icon> 记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Mobile Card List -->
      <div v-if="appStore.device === 'mobile'" v-loading="loading" class="mobile-card-list">
        <div v-for="item in jobList" :key="item.jobId" class="mobile-card">
          <div class="mobile-card-header">
            <span class="mobile-card-title"><i class="fa fa-cog"></i> {{ item.jobName }}</span>
            <el-switch
              size="small"
              v-model="item.status"
              :active-value="'0'"
              :inactive-value="'1'"
              @change="handleSwitchChange(item)"
            />
          </div>
          <div class="mobile-card-body">
            <div class="mobile-card-row">
              <span class="mobile-card-label">Cron</span>
              <span class="mobile-card-value mobile-card-value-clip">{{ item.cronExpression }}</span>
            </div>
          </div>
          <div class="mobile-card-actions">
            <el-button link type="primary" size="small" @click="handleUpdate(item, '修改定时任务')">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="primary" size="small" @click="handleRun(item)">
              <el-icon><VideoPlay /></el-icon> 执行
            </el-button>
            <el-button link type="info" size="small" @click="handleViewLogs(item)">
              <el-icon><List /></el-icon> 记录
            </el-button>
          </div>
        </div>
        <el-empty v-if="!jobList.length" description="暂无数据" />
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
    <el-dialog v-model="open" :title="dialogTitle" width="650px" append-to-body class="modern-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="任务名称" prop="jobName">
          <el-input v-model="form.jobName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="cron执行表达式" prop="cronExpression">
          <el-input v-model="form.cronExpression" placeholder="请输入cron表达式">
            <template #append>
              <el-button @click="showCronDialog = true">表达式说明</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">暂停</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>

    <!-- Cron Expression Dialog -->
    <el-dialog title="Cron表达式说明" v-model="showCronDialog" width="500px" append-to-body class="modern-dialog">
      <div class="cron-desc">
        <p><strong>秒 分 时 日 月 周 年(可选)</strong></p>
        <p>例：0 0 12 * * ? 每天12点运行</p>
        <p>例：0 15 10 ? * * 每天10:15运行</p>
        <p>例：0 0/5 * * * ? 每5分钟运行</p>
        <p>例：0 15 10 ? * MON-FRI 周一到周五10:15运行</p>
      </div>
      <template #footer>
        <el-button @click="showCronDialog = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- Job Log Dialog -->
    <el-dialog
      v-model="logOpen"
      :title="`${logTitle} - 执行记录`"
      :width="appStore.device === 'mobile' ? '100%' : '900px'"
      :top="appStore.device === 'mobile' ? '0' : '5vh'"
      :fullscreen="appStore.device === 'mobile'"
      append-to-body
      class="modern-dialog log-dialog"
    >
      <!-- Mobile: Collapsible Search Panel -->
      <div v-if="appStore.device === 'mobile'" class="mobile-search-panel" :class="{ collapsed: logSearchCollapsed }">
        <div class="mobile-search-panel-header" @click="logSearchCollapsed = !logSearchCollapsed">
          <span class="mobile-search-panel-title">
            <el-icon><Search /></el-icon>
            筛选查询
          </span>
          <el-icon class="collapse-icon" :class="{ expanded: !logSearchCollapsed }">
            <ArrowDown />
          </el-icon>
        </div>
        <div class="mobile-search-panel-body">
          <el-form :model="logQueryParams" label-width="72px">
            <el-form-item label="执行状态">
              <el-select v-model="logQueryParams.status" placeholder="全部状态" clearable style="width: 100%">
                <el-option label="成功" value="0" />
                <el-option label="失败" value="1" />
              </el-select>
            </el-form-item>
          </el-form>
          <div class="search-actions">
            <el-button type="primary" @click="logQueryParams.pageNum = 1; getJobLogList()">
              <el-icon><Search /></el-icon> 搜索
            </el-button>
            <el-button @click="resetLogQuery">
              <el-icon><Refresh /></el-icon> 重置
            </el-button>
          </div>
        </div>
      </div>

      <!-- Desktop: Inline Search -->
      <el-form v-else :model="logQueryParams" :inline="true" label-width="70px" class="log-search-form">
        <el-form-item label="执行状态" prop="status">
          <el-select v-model="logQueryParams.status" placeholder="全部状态" clearable>
            <el-option label="成功" value="0" />
            <el-option label="失败" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="logQueryParams.pageNum = 1; getJobLogList()">
            <el-icon><Search /></el-icon> 搜索
          </el-button>
          <el-button @click="resetLogQuery">
            <el-icon><Refresh /></el-icon> 重置
          </el-button>
        </el-form-item>
      </el-form>

      <!-- Desktop Table -->
      <el-table v-if="appStore.device === 'desktop'" v-loading="logLoading" :data="logList" class="modern-table log-table">
        <el-table-column label="ID" prop="jobLogId" width="70" align="center" />
        <el-table-column label="任务名称" prop="jobName" width="140" show-overflow-tooltip />
        <el-table-column label="调用目标" prop="invokeTarget" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'" size="small">
              {{ scope.row.status === '0' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="日志信息" prop="jobMessage" min-width="200" show-overflow-tooltip />
        <el-table-column label="耗时" align="center" width="100">
          <template #default="scope">
            <span v-if="scope.row.startTime && scope.row.endTime">
              {{ formatDuration(scope.row.startTime, scope.row.endTime) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" prop="startTime" width="160" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="90" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleViewLogDetail(scope.row)">
              <el-icon><View /></el-icon> 详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Mobile Card List -->
      <div v-if="appStore.device === 'mobile'" v-loading="logLoading" class="log-card-list">
        <div
          v-for="item in logList"
          :key="item.jobLogId"
          class="log-card"
        >
          <div class="log-card-header">
            <div class="log-card-title-row">
              <span class="log-card-title">
                <el-icon><Clock /></el-icon>
                {{ item.jobName }}
              </span>
              <el-tag :type="item.status === '0' ? 'success' : 'danger'" size="small" effect="light">
                {{ item.status === '0' ? '成功' : '失败' }}
              </el-tag>
            </div>
          </div>
          <div class="log-card-body">
            <div class="log-card-row">
              <span class="log-card-label">调用目标</span>
              <span class="log-card-value log-card-value-clip">{{ item.invokeTarget }}</span>
            </div>
            <div class="log-card-row">
              <span class="log-card-label">日志信息</span>
              <span class="log-card-value log-card-value-clip" @click.stop="handleViewLogDetail(item)">{{ item.jobMessage || '-' }}</span>
            </div>
            <div class="log-card-row">
              <span class="log-card-label">开始时间</span>
              <span class="log-card-value log-card-value-light">{{ formatTime(item.startTime) }}</span>
            </div>
            <div class="log-card-row" v-if="item.startTime && item.endTime">
              <span class="log-card-label">耗时</span>
              <span class="log-card-value log-card-value-light">{{ formatDuration(item.startTime, item.endTime) }}</span>
            </div>
          </div>
          <div class="log-card-footer">
            <el-button link type="primary" size="small" @click="handleViewLogDetail(item)">
              <el-icon><View /></el-icon> 查看详情
            </el-button>
          </div>
        </div>
        <el-empty v-if="!logList.length" description="暂无执行记录" />
      </div>

      <!-- Pagination -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="logQueryParams.pageNum"
          v-model:page-size="logQueryParams.pageSize"
          :total="logTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="getJobLogList"
          @size-change="getJobLogList"
        />
      </div>

      <template #footer>
        <el-button @click="logOpen = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- Log Detail Dialog -->
    <el-dialog
      v-model="detailOpen"
      title="执行记录详情"
      :width="appStore.device === 'mobile' ? '100%' : '700px'"
      :top="appStore.device === 'mobile' ? '5vh' : '5vh'"
      append-to-body
      class="modern-dialog log-detail-dialog"
    >
      <el-descriptions
        :column="appStore.device === 'mobile' ? 1 : 2"
        border
        v-if="logDetail"
      >
        <el-descriptions-item label="日志ID">{{ logDetail.jobLogId }}</el-descriptions-item>
        <el-descriptions-item label="执行状态">
          <el-tag :type="logDetail.status === '0' ? 'success' : 'danger'">
            {{ logDetail.status === '0' ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="任务名称" :span="appStore.device === 'mobile' ? 1 : 2">{{ logDetail.jobName }}</el-descriptions-item>
        <el-descriptions-item label="任务组名" :span="appStore.device === 'mobile' ? 1 : 2">{{ logDetail.jobGroup }}</el-descriptions-item>
        <el-descriptions-item label="调用目标" :span="2">{{ logDetail.invokeTarget }}</el-descriptions-item>
        <el-descriptions-item label="日志信息" :span="2">{{ logDetail.jobMessage || '-' }}</el-descriptions-item>
        <el-descriptions-item label="异常信息" :span="2" v-if="logDetail.exceptionInfo">
          <pre class="exception-text">{{ logDetail.exceptionInfo }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间" :span="2">{{ formatTime(logDetail.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间" :span="2">{{ formatTime(logDetail.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="耗时" :span="2" v-if="logDetail.startTime && logDetail.endTime">
          {{ formatDuration(logDetail.startTime, logDetail.endTime) }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailOpen = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Edit, VideoPlay, Filter, List, View, ArrowDown, Clock } from '@element-plus/icons-vue'
import { getJobListApi, addJobApi, updateJobApi, deleteJobApi, changeJobStatusApi, runJobApi } from '@/api/monitor/job'
import { getJobLogListApi, getJobLogDetailApi } from '@/api/monitor/jobLog'
import { useAppStore } from '@/stores/app'
import { useTaskList } from '@/composables/useTaskList'
import type { SearchParams, PageResult } from '@/types'

const appStore = useAppStore()
const showSearch = ref(window.innerWidth >= 768)

const {
  taskList: jobList, loading, total, queryParams,
  getList, handleQuery, resetQuery, queryRef,
  open, dialogTitle, submitLoading, formRef, form, rules,
  handleUpdate, submitForm
} = useTaskList<SearchParams & { jobName?: string; jobGroup?: string; status?: string }>({
  listApi: getJobListApi,
  addApi: addJobApi,
  updateApi: updateJobApi,
  deleteApi: deleteJobApi,
  // 该页当前无批量执行 UI，仅支持单个 jobId 执行
  executeApi: (ids: number[]) => runJobApi(ids[0]),
  idField: 'jobId',
  initForm: () => ({
    jobId: undefined,
    jobName: undefined,
    jobGroup: 'DEFAULT',
    invokeTarget: undefined,
    cronExpression: undefined,
    subPost: undefined,
    concurrent: '0',
    status: '0',
    remark: undefined
  }),
  rules: {
    jobName: [{ required: true, message: '任务名称不能为空', trigger: 'blur' }],
    jobGroup: [{ required: true, message: '任务组名不能为空', trigger: 'blur' }],
    invokeTarget: [{ required: true, message: '调用目标字符串不能为空', trigger: 'blur' }],
    cronExpression: [{ required: true, message: 'Cron表达式不能为空', trigger: 'blur' }]
  },
  defaultQuery: { jobName: undefined, jobGroup: undefined, status: undefined }
})

const showCronDialog = ref(false)

const handleSwitchChange = async (row: any) => {
  const newStatus = row.status
  const text = newStatus === '0' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`是否确认${text}任务"${row.jobName}"？`, '警告', { type: 'info' })
    await changeJobStatusApi(row.jobId, newStatus)
    ElMessage.success(`${text}成功`)
  } catch (e) {
    if (e !== 'cancel') {
      // Revert status on API failure
      row.status = row.status === '0' ? '1' : '0'
      console.error(e)
    } else {
      // User cancelled - revert status
      row.status = row.status === '0' ? '1' : '0'
    }
  }
}

const handleRun = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认执行任务"${row.jobName}"？`, '警告', { type: 'warning' })
    await runJobApi(row.jobId)
    ElMessage.success('执行成功')
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

// ========== Job Log ==========
const logOpen = ref(false)
const logLoading = ref(false)
const logList = ref<any[]>([])
const logTotal = ref(0)
const logTitle = ref('')
const logSearchCollapsed = ref(true)
const logQueryParams = reactive<SearchParams>({
  pageNum: 1,
  pageSize: 10,
  jobName: undefined,
  status: undefined
})

const detailOpen = ref(false)
const logDetail = ref<any>(null)

const handleViewLogs = (row: any) => {
  if (!row?.jobId) {
    ElMessage.warning('请选择数据项')
    return
  }
  logTitle.value = row.jobName
  logQueryParams.pageNum = 1
  logQueryParams.jobName = row.jobName
  logOpen.value = true
  getJobLogList()
}

const getJobLogList = async () => {
  logLoading.value = true
  try {
    const res = await getJobLogListApi(logQueryParams) as PageResult
    logList.value = res.records
    logTotal.value = res.total
  } finally {
    logLoading.value = false
  }
}

const resetLogQuery = () => {
  logQueryParams.pageNum = 1
  logQueryParams.jobName = undefined
  logQueryParams.status = undefined
  getJobLogList()
}

const handleViewLogDetail = async (row: any) => {
  try {
    const res = await getJobLogDetailApi(row.jobLogId) as any
    logDetail.value = res
    detailOpen.value = true
  } catch (e) {
    console.error(e)
  }
}

const formatTime = (time: string | null): string => {
  if (!time) return '-'
  const date = new Date(time)
  if (isNaN(date.getTime())) return time
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const formatDuration = (start: string | null, end: string | null): string => {
  if (!start || !end) return '-'
  const s = new Date(start).getTime()
  const e = new Date(end).getTime()
  const diff = Math.abs(e - s)
  const ms = diff % 1000
  const sec = Math.floor(diff / 1000) % 60
  const min = Math.floor(diff / 60000)
  if (min > 0) return `${min}分${sec}秒`
  if (sec > 0) return `${sec}.${ms.toString().padStart(3, '0').slice(0, 1)}秒`
  return `${diff}毫秒`
}

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

/* ============================================
   Log Search Form
   ============================================ */
.log-search-form {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--osr-border-light);

  :deep(.el-form-item) {
    margin-bottom: 0;
    margin-right: 12px;
  }
}

/* ============================================
   Mobile Search Panel (for log dialog)
   ============================================ */
.mobile-search-panel {
  border: 1px solid var(--osr-border-light);
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: hidden;
  background: white;

  &.collapsed {
    .mobile-search-panel-body {
      display: none;
    }
  }
}

.mobile-search-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: var(--osr-bg-page);
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid transparent;

  .mobile-search-panel-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    font-weight: 600;
    color: var(--osr-text-primary);
  }

  .collapse-icon {
    transition: transform 0.2s;

    &.expanded {
      transform: rotate(180deg);
    }
  }
}

.mobile-search-panel-body {
  padding: 12px 14px;

  .search-actions {
    display: flex;
    gap: 8px;
    margin-top: 8px;
  }
}

/* ============================================
   Log Card List (mobile)
   ============================================ */
.log-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.log-card {
  background: white;
  border-radius: 10px;
  border: 1px solid var(--osr-border-light);
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

  .log-card-header {
    padding: 12px 14px 10px;
    border-bottom: 1px solid var(--osr-border-light);
    background: var(--osr-bg-page);
  }

  .log-card-title-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .log-card-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    font-weight: 600;
    color: var(--osr-text-primary);
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    .el-icon {
      color: var(--osr-primary);
      flex-shrink: 0;
    }
  }

  .log-card-body {
    padding: 0;
  }

  .log-card-row {
    display: flex;
    align-items: flex-start;
    padding: 9px 14px;
    font-size: 13px;
    border-bottom: 1px solid var(--osr-border-light);

    &:last-child {
      border-bottom: none;
    }

    .log-card-label {
      width: 72px;
      color: var(--osr-text-secondary);
      flex-shrink: 0;
      font-size: 12px;
      line-height: 1.5;
      padding-top: 1px;
    }

    .log-card-value {
      flex: 1;
      min-width: 0;
      color: var(--osr-text-primary);
      font-size: 13px;
      line-height: 1.5;
      word-break: break-all;
      cursor: default;

      &.log-card-value-clip {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      &.log-card-value-light {
        color: var(--osr-text-secondary);
        font-size: 12px;
      }
    }
  }

  .log-card-footer {
    display: flex;
    justify-content: flex-end;
    padding: 8px 14px 10px;
    border-top: 1px solid var(--osr-border-light);
  }
}

/* ============================================
   Log Dialog (mobile fullscreen)
   ============================================ */
.log-dialog {
  :deep(.el-dialog__body) {
    padding: 14px;
    max-height: calc(100vh - 120px);
    overflow-y: auto;
  }
}

/* ============================================
   Log Table
   ============================================ */
.log-table {
  :deep(.el-table__cell) {
    font-size: 13px;
  }
}

/* ============================================
   Exception Text
   ============================================ */
.exception-text {
  max-height: 300px;
  overflow-y: auto;
  margin: 0;
  padding: 12px;
  background: var(--osr-bg-content);
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--osr-text-danger);
}
</style>
