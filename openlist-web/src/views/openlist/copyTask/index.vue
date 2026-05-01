<template>
  <div class="app-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="源目录" prop="copyTaskSrc">
          <el-input v-model="queryParams.copyTaskSrc" placeholder="请输入源目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="目标目录" prop="copyTaskDst">
          <el-input v-model="queryParams.copyTaskDst" placeholder="请输入目标目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="监控目录" prop="monitorDir">
          <el-input v-model="queryParams.monitorDir" placeholder="请输入监控目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="copyTaskStatus">
          <el-select v-model="queryParams.copyTaskStatus" placeholder="状态" clearable>
            <el-option label="启用" value="1" />
            <el-option label="停用" value="0" />
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
        <el-col :span="1.5">
          <el-button type="warning" plain icon="VideoPlay" :disabled="multiple" @click="handleExecute()">批量执行</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="同步配置" min-width="300">
          <template #default="scope">
            <div class="path-box">
              <div class="path-row"><span class="path-label label-src">源</span> <span class="path-text">{{ scope.row.copyTaskSrc }}</span></div>
              <div class="path-row"><span class="path-label label-dst">目</span> <span class="path-text">{{ scope.row.copyTaskDst }}</span></div>
              <div class="path-row" v-if="scope.row.monitorDir"><span class="path-label label-mon">监</span> <span class="path-text">{{ scope.row.monitorDir }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="copyTaskStatus" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.copyTaskStatus === '1' ? 'success' : 'danger'">
              {{ scope.row.copyTaskStatus === '1' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" align="center" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
            <el-button link type="primary" icon="VideoPlay" @click="handleExecuteOne(scope.row)">执行</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- Add/Edit Dialog -->
    <el-dialog :title="dialogTitle" v-model="open" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="源目录" prop="copyTaskSrc">
          <DirectoryTreeSelect v-model="form.copyTaskSrc" type="openlist" placeholder="请选择源目录" />
        </el-form-item>
        <el-form-item label="目标目录" prop="copyTaskDst">
          <DirectoryTreeSelect v-model="form.copyTaskDst" type="openlist" placeholder="请选择目标目录" />
        </el-form-item>
        <el-form-item label="监控目录" prop="monitorDir">
          <DirectoryTreeSelect v-model="form.monitorDir" type="local" placeholder="请选择监控目录（可选）" />
        </el-form-item>
        <el-form-item label="状态" prop="copyTaskStatus">
          <el-radio-group v-model="form.copyTaskStatus">
            <el-radio value="1">启用</el-radio>
            <el-radio value="0">停用</el-radio>
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
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'CopyTask' })
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect/index.vue'
import { getCopyTaskListApi, addCopyTaskApi, updateCopyTaskApi, deleteCopyTaskApi, executeCopyTaskApi } from '@/api/openlist/copyTask'
import type { SearchParams, PageResult } from '@/types'

const taskList = ref<any[]>([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const single = ref(true)
const multiple = ref(true)
const selectedIds = ref<number[]>([])

const queryParams = reactive<SearchParams & { copyTaskSrc?: string; copyTaskDst?: string; monitorDir?: string; copyTaskStatus?: string }>({
  pageNum: 1,
  pageSize: 10,
  copyTaskSrc: undefined,
  copyTaskDst: undefined,
  monitorDir: undefined,
  copyTaskStatus: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getCopyTaskListApi(queryParams) as PageResult
    taskList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { (queryRef.value as any).resetFields(); handleQuery() }
const handleSelectionChange = (selection: any[]) => { single.value = selection.length !== 1; multiple.value = !selection.length; selectedIds.value = selection.map((item: any) => item.copyTaskId) }

// Dialog state
const open = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)

const initForm = (): any => ({
  copyTaskId: undefined,
  copyTaskSrc: undefined,
  copyTaskDst: undefined,
  monitorDir: undefined,
  copyTaskStatus: '1',
  remark: undefined
})

const form = ref<any>(initForm())
const formRef = ref<any>()

const rules = reactive({
  copyTaskSrc: [{ required: true, message: '源目录不能为空', trigger: 'blur' }],
  copyTaskDst: [{ required: true, message: '目标目录不能为空', trigger: 'blur' }]
})

const handleAdd = () => {
  dialogTitle.value = '新增文件同步任务'
  form.value = initForm()
  open.value = true
}

const handleUpdate = (row?: any) => {
  const id = row?.copyTaskId || selectedIds.value[0]
  if (!id) {
    ElMessage.warning('请选择数据项')
    return
  }
  dialogTitle.value = '修改文件同步任务'
  getCopyTaskListApi({ ...queryParams, pageNum: 1, pageSize: 100 }).then((res: PageResult) => {
    const task = res.records.find((t: any) => t.copyTaskId === id)
    if (task) {
      form.value = { ...task }
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
    if (form.value.copyTaskId) {
      await updateCopyTaskApi(form.value)
      ElMessage.success('修改成功')
    } else {
      await addCopyTaskApi(form.value)
      ElMessage.success('新增成功')
    }
    open.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row?: any) => {
  const ids = row?.copyTaskId ? [row.copyTaskId] : selectedIds.value
  try {
    await ElMessageBox.confirm(`是否确认删除文件同步任务编号为"${ids}"的数据项？`, '警告', { type: 'warning' })
    await deleteCopyTaskApi(ids[0])
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleExecute = async () => {
  try {
    await ElMessageBox.confirm(`是否确认执行选中的文件同步任务？`, '警告', { type: 'warning' })
    await executeCopyTaskApi(selectedIds.value)
    ElMessage.success('执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleExecuteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认执行文件同步任务"${row.copyTaskSrc} → ${row.copyTaskDst}"？`, '警告', { type: 'warning' })
    await executeCopyTaskApi([row.copyTaskId])
    ElMessage.success('执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const queryRef = ref<any>()
getList()
</script>

<style scoped lang="scss">
.app-container { padding: 16px; }
.mb8 { margin-bottom: 8px; }

.path-box { display: flex; flex-direction: column; width: 100%; }
.path-row { margin-bottom: 4px; width: 100%; display: flex; align-items: baseline; }
.path-label { display: inline-block; padding: 1px 4px; font-size: 10px; font-weight: bold; color: #fff; border-radius: 3px; margin-right: 5px; flex-shrink: 0; width: 24px; text-align: center; }
.path-text { color: #555; font-size: 11px; font-family: Consolas, monospace; }
.path-label.label-src { background-color: #f8ac59; }
.path-label.label-dst { background-color: #1ab394; }
.path-label.label-mon { background-color: #23c6c8; }
</style>
