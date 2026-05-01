<template>
  <div class="app-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="源目录" prop="sourceFolder">
          <el-input v-model="queryParams.sourceFolder" placeholder="请输入源目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="目标目录" prop="targetRoot">
          <el-input v-model="queryParams.targetRoot" placeholder="请输入目标目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="状态" clearable>
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
        <el-col :span="1.5">
          <el-button type="info" plain icon="MagicStick" @click="handleTest()">测试</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="任务路径配置" min-width="300">
          <template #default="scope">
            <div class="path-box">
              <div class="path-row"><span class="path-label label-src">源</span> <span class="path-text">{{ scope.row.sourceFolder }}</span></div>
              <div class="path-row"><span class="path-label label-dst">目</span> <span class="path-text">{{ scope.row.targetRoot }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === '0' ? 'danger' : 'success'">
              {{ scope.row.status === '0' ? '停用' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" align="center" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
            <el-button link type="primary" icon="VideoPlay" @click="handleExecuteOne(scope.row)">执行</el-button>
            <el-button link type="primary" icon="MagicStick" @click="handleTestOne(scope.row)">测试</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- Add/Edit Dialog -->
    <el-dialog :title="dialogTitle" v-model="open" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="源目录" prop="sourceFolder">
          <DirectoryTreeSelect v-model="form.sourceFolder" type="local" placeholder="请选择源目录" />
        </el-form-item>
        <el-form-item label="目标目录" prop="targetRoot">
          <DirectoryTreeSelect v-model="form.targetRoot" type="local" placeholder="请选择目标目录" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
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

    <!-- Test Dialog -->
    <el-dialog v-model="testOpen" :title="testTitle" width="700px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="原文件名">
          <el-input v-model="testForm.filename" type="textarea" :rows="3" placeholder="例如: The.Movie.2024.1080p.mkv" />
        </el-form-item>
        <el-form-item label="重命名模板">
          <el-input v-model="testForm.template" type="textarea" :rows="4" placeholder="留空则使用默认配置" />
          <div style="color:#999;font-size:12px"><i class="el-icon-info"></i> 留空则使用默认配置</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="testLoading" @click="doTest"><i class="el-icon-flask"></i> 开始分析</el-button>
        </el-form-item>
      </el-form>

      <div v-if="testResult" style="margin-top:16px">
        <el-alert title="重命名结果预览" type="success" :closable="false" style="margin-bottom:12px">
          <div style="font-family:Consolas,monospace;word-break:break-all;white-space:pre-wrap">{{ testResult.renamed }}</div>
        </el-alert>
        <el-alert title="识别参数详情" type="info" :closable="false">
          <pre style="max-height:300px;overflow:auto;font-size:12px;background:#f5f5f5;padding:10px;border-radius:4px">{{ JSON.stringify(testResult.info, null, 2) }}</pre>
        </el-alert>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect/index.vue'
import { getRenameTaskListApi, addRenameTaskApi, updateRenameTaskApi, deleteRenameTaskApi, executeRenameTaskApi, testParseRenameApi } from '@/api/openlist/renameTask'
import type { SearchParams, PageResult } from '@/types'

const taskList = ref<any[]>([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const single = ref(true)
const multiple = ref(true)
const selectedIds = ref<number[]>([])

const queryParams = reactive<SearchParams & { sourceFolder?: string; targetRoot?: string; status?: string }>({
  pageNum: 1,
  pageSize: 10
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getRenameTaskListApi(queryParams) as PageResult
    taskList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { (queryRef.value as any).resetFields(); handleQuery() }
const handleSelectionChange = (selection: any[]) => { single.value = selection.length !== 1; multiple.value = !selection.length; selectedIds.value = selection.map((item: any) => item.id) }

// Dialog state
const open = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)

const initForm = (): any => ({
  id: undefined,
  sourceFolder: undefined,
  targetRoot: undefined,
  status: '1',
  remark: undefined
})

const form = ref<any>(initForm())
const formRef = ref<any>()

const rules = reactive({
  sourceFolder: [{ required: true, message: '源目录不能为空', trigger: 'blur' }],
  targetRoot: [{ required: true, message: '目标目录不能为空', trigger: 'blur' }]
})

const handleAdd = () => {
  dialogTitle.value = '新增重命名任务'
  form.value = initForm()
  open.value = true
}

const handleUpdate = (row?: any) => {
  const id = row?.id || selectedIds.value[0]
  if (!id) {
    ElMessage.warning('请选择数据项')
    return
  }
  dialogTitle.value = '修改重命名任务'
  getRenameTaskListApi({ ...queryParams, pageNum: 1, pageSize: 100 }).then((res: PageResult) => {
    const task = res.records.find((t: any) => t.id === id)
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
    if (form.value.id) {
      await updateRenameTaskApi(form.value)
      ElMessage.success('修改成功')
    } else {
      await addRenameTaskApi(form.value)
      ElMessage.success('新增成功')
    }
    open.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row?: any) => {
  const ids = row?.id ? [row.id] : selectedIds.value
  try {
    await ElMessageBox.confirm(`是否确认删除重命名任务编号为"${ids}"的数据项？`, '警告', { type: 'warning' })
    await deleteRenameTaskApi(ids[0])
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleExecute = async () => {
  try {
    await ElMessageBox.confirm(`是否确认执行选中的重命名任务？`, '警告', { type: 'warning' })
    await executeRenameTaskApi(selectedIds.value)
    ElMessage.success('执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleExecuteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认执行重命名任务"${row.sourceFolder}"？`, '警告', { type: 'warning' })
    await executeRenameTaskApi([row.id])
    ElMessage.success('执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const queryRef = ref<any>()
getList()

// Test dialog state
const testOpen = ref(false)
const testTitle = ref('文件名重命名测试')
const testLoading = ref(false)
const testResult = ref<any>(null)
const testForm = reactive({ filename: '', template: '' })

const handleTest = () => {
  testTitle.value = '文件名重命名测试'
  testForm.filename = ''
  testForm.template = ''
  testResult.value = null
  testOpen.value = true
}

const handleTestOne = (row: any) => {
  testTitle.value = `文件名重命名测试 - ${row.sourceFolder}`
  testForm.filename = ''
  testForm.template = ''
  testResult.value = null
  testOpen.value = true
}

const doTest = async () => {
  if (!testForm.filename.trim()) {
    ElMessage.warning('请输入文件名')
    return
  }
  testLoading.value = true
  try {
    const res = await testParseRenameApi(testForm.filename, testForm.template || undefined) as any
    if (res.code === 200) {
      testResult.value = res.data
      ElMessage.success('分析成功')
    } else {
      ElMessage.error(res.message || '分析失败')
    }
  } catch (e) {
    ElMessage.error('请求失败')
  } finally {
    testLoading.value = false
  }
}
</script>

<style scoped lang="scss">
.app-container { padding: 16px; }
.mb8 { margin-bottom: 8px; }
.path-box { display: flex; flex-direction: column; width: 100%; }
.path-row { margin-bottom: 6px; width: 100%; display: flex; align-items: baseline; }
.path-label { display: inline-block; padding: 1px 4px; font-size: 10px; font-weight: bold; color: #fff; border-radius: 3px; margin-right: 4px; flex-shrink: 0; width: 24px; text-align: center; }
.label-src { background-color: #f8ac59; }
.label-dst { background-color: #1ab394; }
.path-text { color: #555; font-size: 12px; font-family: Consolas, monospace; }
</style>
