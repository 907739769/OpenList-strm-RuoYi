<template>
  <div class="page-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="strm目录" prop="strmTaskPath">
          <el-input v-model="queryParams.strmTaskPath" placeholder="请输入strm目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="strmTaskStatus">
          <el-select v-model="queryParams.strmTaskStatus" placeholder="状态" clearable>
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
          <el-button type="warning" plain icon="VideoPlay" :disabled="multiple" @click="handleExecute()">执行</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="STRM 目录路径" min-width="300">
          <template #default="scope">
            <div class="path-text"><i class="fa fa-folder-open-o"></i> {{ scope.row.strmTaskPath }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="strmTaskStatus" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.strmTaskStatus === '0' ? 'danger' : 'success'">
              {{ scope.row.strmTaskStatus === '0' ? '停用' : '启用' }}
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
    <el-dialog :title="dialogTitle" v-model="open" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="STRM目录" prop="strmTaskPath">
          <DirectoryTreeSelect v-model="form.strmTaskPath" type="openlist" placeholder="请选择STRM目录" />
        </el-form-item>
        <el-form-item label="状态" prop="strmTaskStatus">
          <el-radio-group v-model="form.strmTaskStatus">
            <el-radio value="0">停用</el-radio>
            <el-radio value="1">启用</el-radio>
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
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect/index.vue'
import { getStrmTaskListApi, addStrmTaskApi, updateStrmTaskApi, deleteStrmTaskApi, executeStrmTaskApi } from '@/api/openlist/strmTask'
import type { SearchParams, PageResult } from '@/types'

const taskList = ref<any[]>([])
const loading = ref(true)
const total = ref(0)
const single = ref(true)
const multiple = ref(true)
const selectedIds = ref<number[]>([])

const queryParams = reactive<SearchParams & { strmTaskPath?: string; strmTaskStatus?: string }>({
  pageNum: 1,
  pageSize: 10
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getStrmTaskListApi(queryParams) as PageResult
    taskList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { (queryRef.value as any).resetFields(); handleQuery() }
const handleSelectionChange = (selection: any[]) => { single.value = selection.length !== 1; multiple.value = !selection.length; selectedIds.value = selection.map((item: any) => item.strmTaskId) }

// Dialog state
const open = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)

const initForm = (): any => ({
  strmTaskId: undefined,
  strmTaskPath: undefined,
  strmTaskStatus: '1',
  remark: undefined
})

const form = ref<any>(initForm())
const formRef = ref<any>()

const rules = reactive({
  strmTaskPath: [{ required: true, message: 'STRM目录不能为空', trigger: 'blur' }]
})

const handleAdd = () => {
  dialogTitle.value = '新增STRM任务'
  form.value = initForm()
  open.value = true
}

const handleUpdate = (row?: any) => {
  const id = row?.strmTaskId || selectedIds.value[0]
  if (!id) {
    ElMessage.warning('请选择数据项')
    return
  }
  dialogTitle.value = '修改STRM任务'
  // Fetch task details
  getStrmTaskListApi({ ...queryParams, pageNum: 1, pageSize: 100 }).then((res: PageResult) => {
    const task = res.records.find((t: any) => t.strmTaskId === id)
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
    if (form.value.strmTaskId) {
      await updateStrmTaskApi(form.value)
      ElMessage.success('修改成功')
    } else {
      await addStrmTaskApi(form.value)
      ElMessage.success('新增成功')
    }
    open.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row?: any) => {
  const ids = row?.strmTaskId ? [row.strmTaskId] : selectedIds.value
  try {
    await ElMessageBox.confirm(`是否确认删除STRM任务编号为"${ids}"的数据项？`, '警告', { type: 'warning' })
    await deleteStrmTaskApi(ids[0])
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleExecute = async () => {
  try {
    await ElMessageBox.confirm(`是否确认执行选中的STRM任务？`, '警告', { type: 'warning' })
    await executeStrmTaskApi(selectedIds.value)
    ElMessage.success('执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleExecuteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认执行STRM任务"${row.strmTaskPath}"？`, '警告', { type: 'warning' })
    await executeStrmTaskApi([row.strmTaskId])
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

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .page-container :deep(.el-form) {
    .el-form-item { margin-right: 0; }
    .el-input, .el-select { width: 100% !important; }
  }
}
</style>
