<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="任务名称" prop="jobName">
          <el-input v-model="queryParams.jobName" placeholder="请输入任务名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="任务组名" prop="jobGroup">
          <el-select v-model="queryParams.jobGroup" placeholder="任务组名" clearable>
            <el-option label="默认" value="DEFAULT" />
            <el-option label="系统" value="SYSTEM" />
          </el-select>
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
        <div class="action-left">
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
          <el-button type="success" :disabled="single" @click="handleUpdate()">
            <el-icon><Edit /></el-icon> 修改
          </el-button>
          <el-button type="danger" :disabled="multiple" @click="handleDelete()">
            <el-icon><Delete /></el-icon> 删除
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <!-- Desktop Table -->
      <el-table v-if="appStore.device === 'desktop'" v-loading="loading" :data="jobList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="任务编号" prop="jobId" width="80" align="center" />
        <el-table-column label="任务名称" prop="jobName" min-width="140" show-overflow-tooltip />
        <el-table-column label="任务组名" prop="jobGroup" width="100" align="center" />
        <el-table-column label="调用目标字符串" prop="invokeTarget" min-width="200" show-overflow-tooltip />
        <el-table-column label="cron执行表达式" prop="cronExpression" width="140" align="center" />
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
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row)">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
            <el-button link type="primary" @click="handleRun(scope.row)">
              <el-icon><VideoPlay /></el-icon> 执行
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
              <span class="mobile-card-label">编号</span>
              <span class="mobile-card-value">{{ item.jobId }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">组名</span>
              <span class="mobile-card-value">{{ item.jobGroup }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">Cron</span>
              <span class="mobile-card-value mobile-card-value-clip">{{ item.cronExpression }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">调用目标</span>
              <span class="mobile-card-value mobile-card-value-clip">{{ item.invokeTarget }}</span>
            </div>
          </div>
          <div class="mobile-card-actions">
            <el-button link type="primary" size="small" @click="handleUpdate(item)">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(item)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
            <el-button link type="primary" size="small" @click="handleRun(item)">
              <el-icon><VideoPlay /></el-icon> 执行
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
        <el-form-item label="任务组名" prop="jobGroup">
          <el-select v-model="form.jobGroup" placeholder="请选择">
            <el-option label="默认" value="DEFAULT" />
            <el-option label="系统" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="调用目标字符串" prop="invokeTarget">
          <el-input v-model="form.invokeTarget" placeholder="如: com.ruoyi.task.RyTask.ryParams('ry')" />
        </el-form-item>
        <el-form-item label="cron执行表达式" prop="cronExpression">
          <el-input v-model="form.cronExpression" placeholder="请输入cron表达式">
            <template #append>
              <el-button @click="showCronDialog = true">表达式说明</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="任务负责人" prop="subPost">
          <el-input v-model="form.subPost" placeholder="请输入任务负责人" />
        </el-form-item>
        <el-form-item label="同步执行" prop="concurrent">
          <el-radio-group v-model="form.concurrent">
            <el-radio value="0">允许</el-radio>
            <el-radio value="1">禁止</el-radio>
          </el-radio-group>
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
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确 定</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, VideoPlay, Filter } from '@element-plus/icons-vue'
import { getJobListApi, addJobApi, updateJobApi, deleteJobApi, changeJobStatusApi, runJobApi } from '@/api/monitor/job'
import { useAppStore } from '@/stores/app'
import type { SearchParams, PageResult } from '@/types'

const appStore = useAppStore()
const showSearch = ref(window.innerWidth >= 768)

const jobList = ref<any[]>([])
const loading = ref(true)
const total = ref(0)
const single = ref(true)
const multiple = ref(true)
const selectedIds = ref<number[]>([])

const queryParams = reactive<SearchParams>({
  pageNum: 1,
  pageSize: 10,
  jobName: undefined,
  jobGroup: undefined,
  status: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getJobListApi(queryParams) as PageResult
    jobList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { (queryRef.value as any).resetFields(); handleQuery() }
const handleSelectionChange = (selection: any[]) => { single.value = selection.length !== 1; multiple.value = !selection.length; selectedIds.value = selection.map((item: any) => item.jobId) }

// Dialog state
const open = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const showCronDialog = ref(false)

const initForm = (): any => ({
  jobId: undefined,
  jobName: undefined,
  jobGroup: 'DEFAULT',
  invokeTarget: undefined,
  cronExpression: undefined,
  subPost: undefined,
  concurrent: '0',
  status: '0',
  remark: undefined
})

const form = ref<any>(initForm())
const formRef = ref<any>()

const rules = reactive({
  jobName: [{ required: true, message: '任务名称不能为空', trigger: 'blur' }],
  jobGroup: [{ required: true, message: '任务组名不能为空', trigger: 'blur' }],
  invokeTarget: [{ required: true, message: '调用目标字符串不能为空', trigger: 'blur' }],
  cronExpression: [{ required: true, message: 'Cron表达式不能为空', trigger: 'blur' }]
})

const handleAdd = () => {
  dialogTitle.value = '新增定时任务'
  form.value = initForm()
  open.value = true
}

const handleUpdate = (row?: any) => {
  const jobId = row?.jobId || selectedIds.value[0]
  if (!jobId) {
    ElMessage.warning('请选择数据项')
    return
  }
  dialogTitle.value = '修改定时任务'
  getJobListApi({ ...queryParams, pageNum: 1, pageSize: 100 }).then((res: PageResult) => {
    const job = res.records.find((t: any) => t.jobId === jobId)
    if (job) {
      form.value = { ...job }
      open.value = true
    } else {
      ElMessage.error('任务不存在')
    }
  })
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (form.value.jobId) {
      await updateJobApi(form.value)
      ElMessage.success('修改成功')
    } else {
      await addJobApi(form.value)
      ElMessage.success('新增成功')
    }
    open.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row?: any) => {
  const ids = row?.jobId ? [row.jobId] : selectedIds.value
  try {
    await ElMessageBox.confirm(`是否确认删除定时任务编号为"${ids}"的数据项？`, '警告', { type: 'warning' })
    await deleteJobApi(ids[0])
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

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
