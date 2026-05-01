<template>
  <div class="app-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="queryParams.configName" placeholder="请输入参数名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input v-model="queryParams.configKey" placeholder="请输入参数键名" clearable @keyup.enter="handleQuery" />
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
          <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Refresh" @click="handleRefreshCache">刷新缓存</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="configList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="参数编号" align="center" prop="configId" />
        <el-table-column label="参数名称" align="center" prop="configName" :show-overflow-tooltip="true" />
        <el-table-column label="参数键名" align="center" prop="configKey" :show-overflow-tooltip="true" />
        <el-table-column label="参数键值" align="center" prop="configValue" :show-overflow-tooltip="true" />
        <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="configRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="form.configName" placeholder="请输入参数名称" />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input v-model="form.configKey" placeholder="请输入参数键名" />
        </el-form-item>
        <el-form-item label="参数键值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="请输入参数键值" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, toRefs } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getConfigListApi, addConfigApi, updateConfigApi, deleteConfigApi, refreshCacheApi } from '@/api/system/config'
import type { FormInstance } from 'element-plus'
import type { SearchParams, PageResult } from '@/types'

const configList = ref<any[]>([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)
const single = ref(true)
const multiple = ref(true)
const selectedIds = ref<number[]>([])

const queryParams = reactive<SearchParams>({
  pageNum: 1,
  pageSize: 10,
  configName: undefined,
  configKey: undefined
})

const data = reactive({ form: {} as any })

// @ts-expect-error used in template
const { configName, configKey } = toRefs(queryParams)
const { form } = toRefs(data)

const configRef = ref<FormInstance>()

const rules = {
  configName: [{ required: true, message: '参数名称不能为空', trigger: 'blur' }],
  configKey: [{ required: true, message: '参数键名不能为空', trigger: 'blur' }],
  configValue: [{ required: true, message: '参数键值不能为空', trigger: 'blur' }]
}


const getList = async () => {
  loading.value = true
  try {
    const res = await getConfigListApi(queryParams) as PageResult
    configList.value = res.records
    total.value = res.total
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const resetQuery = () => {
  ;(queryRef.value as FormInstance).resetFields()
  handleQuery()
}

const handleSelectionChange = (selection: any[]) => {
  single.value = selection.length !== 1
  multiple.value = !selection.length
  selectedIds.value = selection.map((item: any) => item.configId)
}

const handleAdd = () => {
  reset()
  open.value = true
  title.value = '新增参数'
}

const handleUpdate = (row?: any) => {
  reset()
  open.value = true
  title.value = row ? '修改参数' : '新增参数'
  form.value.configId = row?.configId
  form.value.configName = row?.configName
  form.value.configKey = row?.configKey
  form.value.configValue = row?.configValue
  form.value.remark = row?.remark
}

const handleDelete = async (row?: any) => {
  const configIds = row?.configId ? [row.configId] : selectedIds.value
  try {
    await ElMessageBox.confirm(`是否确认删除参数编号为"${configIds}"的数据项？`, '警告', { type: 'warning' })
    await deleteConfigApi(configIds[0])
    ElMessage.success('删除成功')
    getList()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const reset = () => {
  form.value = { configId: undefined, configName: undefined, configKey: undefined, configValue: undefined, remark: undefined }
  ;(configRef.value as FormInstance)?.resetFields()
}

const cancel = () => {
  open.value = false
  reset()
}

const submitForm = async () => {
  const formEl = configRef.value
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (valid) {
      try {
        if (form.value.configId) {
          await updateConfigApi(form.value)
        } else {
          await addConfigApi(form.value)
        }
        ElMessage.success('操作成功')
        open.value = false
        getList()
      } catch (error) {
        console.error(error)
      }
    }
  })
}

const handleRefreshCache = async () => {
  try {
    await refreshCacheApi()
    ElMessage.success('刷新缓存成功')
  } catch (error) {
    console.error(error)
  }
}

const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

const queryRef = ref<FormInstance>()
getList()
</script>

<style scoped lang="scss">
.app-container { padding: 16px; }
.mb8 { margin-bottom: 8px; }
</style>
