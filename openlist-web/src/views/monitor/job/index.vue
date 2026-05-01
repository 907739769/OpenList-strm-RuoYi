<template>
  <div class="app-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="68px">
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
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">修改</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="jobList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="任务编号" prop="jobId" width="80" />
        <el-table-column label="任务名称" prop="jobName" />
        <el-table-column label="任务组名" prop="jobGroup" />
        <el-table-column label="调用目标字符串" prop="invokeTarget" :show-overflow-tooltip="true" />
        <el-table-column label="cron执行表达式" prop="cronExpression" width="120" />
        <el-table-column label="状态" align="center">
          <template #default="scope">
            <el-switch
              v-model="scope.row.status"
              :active-value="'0'"
              :inactive-value="'1'"
              @change="handleSwitchChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
            <el-button link type="primary" icon="VideoPlay" @click="handleRun(scope.row)">执行</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- Add/Edit Dialog -->
    <el-dialog :title="dialogTitle" v-model="open" width="650px" append-to-body>
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
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确 定</el-button>
      </template>
    </el-dialog>

    <!-- Cron Expression Dialog -->
    <el-dialog title="Cron表达式说明" v-model="showCronDialog" width="500px" append-to-body>
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
import { getJobListApi, addJobApi, updateJobApi, deleteJobApi, changeJobStatusApi, runJobApi } from '@/api/monitor/job'
import type { SearchParams, PageResult } from '@/types'

const jobList = ref<any[]>([])
const loading = ref(true)
const showSearch = ref(true)
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
.app-container { padding: 16px; }
.mb8 { margin-bottom: 8px; }
.cron-desc { line-height: 2; color: #606266; }
</style>
