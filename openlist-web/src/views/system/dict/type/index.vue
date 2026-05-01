<template>
  <div class="app-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="queryParams.dictName" placeholder="请输入字典名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="queryParams.dictType" placeholder="请输入字典类型" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="字典状态" clearable>
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
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

      <el-table v-loading="loading" :data="typeList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="字典编号" align="center" prop="dictId" />
        <el-table-column label="字典名称" align="center" prop="dictName" />
        <el-table-column label="字典类型" align="center" prop="dictType" />
        <el-table-column label="状态" align="center" prop="status">
          <template #default="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'">
              {{ scope.row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
            <el-button link type="primary" icon="List" @click="handleData(scope.row)">数据</el-button>
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
      <el-form ref="typeRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="form.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="form.dictType" placeholder="请输入字典类型" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDictTypeListApi, addDictTypeApi, updateDictTypeApi, deleteDictTypeApi } from '@/api/system/dict'
import type { FormInstance } from 'element-plus'
import type { SearchParams, PageResult } from '@/types'

const router = useRouter()

const typeList = ref<any[]>([])
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
  dictName: undefined,
  dictType: undefined,
  status: undefined
})

// @ts-expect-error used in template
const { dictName, dictType, status } = toRefs(queryParams)

const typeRef = ref<FormInstance>()

const rules = {
  dictName: [{ required: true, message: '字典名称不能为空', trigger: 'blur' }],
  dictType: [{ required: true, message: '字典类型不能为空', trigger: 'blur' }]
}

const data = reactive({ form: {} as any })
const { form } = toRefs(data)

const getList = async () => {
  loading.value = true
  try {
    const res = await getDictTypeListApi(queryParams) as PageResult
    typeList.value = res.records
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
  selectedIds.value = selection.map((item: any) => item.dictId)
}

const handleAdd = () => {
  reset()
  open.value = true
  title.value = '新增字典类型'
}

const handleUpdate = (row?: any) => {
  reset()
  open.value = true
  title.value = row ? '修改字典类型' : '新增字典类型'
  form.value.dictId = row?.dictId
  form.value.dictName = row?.dictName
  form.value.dictType = row?.dictType
  form.value.status = row?.status
  form.value.remark = row?.remark
}

const handleDelete = async (row?: any) => {
  const dictIds = row?.dictId ? [row.dictId] : selectedIds.value
  try {
    await ElMessageBox.confirm(`是否确认删除字典编号为"${dictIds}"的数据项？`, '警告', { type: 'warning' })
    await deleteDictTypeApi(dictIds[0])
    ElMessage.success('删除成功')
    getList()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleData = (row: any) => {
  router.push(`/system/dict/data/${row.dictId}`)
}

const reset = () => {
  form.value = { dictId: undefined, dictName: undefined, dictType: undefined, status: '0', remark: undefined }
  ;(typeRef.value as FormInstance)?.resetFields()
}

const cancel = () => {
  open.value = false
  reset()
}

const submitForm = async () => {
  const formEl = typeRef.value
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (valid) {
      try {
        if (form.value.dictId) {
          await updateDictTypeApi(form.value)
        } else {
          await addDictTypeApi(form.value)
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

const queryRef = ref<FormInstance>()
getList()
</script>

<style scoped lang="scss">
.app-container { padding: 16px; }
.mb8 { margin-bottom: 8px; }
</style>
