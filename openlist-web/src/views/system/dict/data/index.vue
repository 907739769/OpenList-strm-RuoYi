<template>
  <div class="app-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
        <el-form-item label="字典名称" prop="dictType">
          <el-select v-model="queryParams.dictType" placeholder="请选择字典类型">
            <el-option
              v-for="item in typeOptions"
              :key="item.dictId"
              :label="item.dictName"
              :value="item.dictType"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标签名称" prop="dictLabel">
          <el-input v-model="queryParams.dictLabel" placeholder="请输入标签名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="数据状态" clearable>
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

      <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="字典编码" align="center" prop="dictCode" />
        <el-table-column label="标签名称" align="center" prop="dictLabel" />
        <el-table-column label="键值值" align="center" prop="dictValue" />
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
      <el-form ref="dataRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="字典类型">
          <el-input v-model="form.dictType" :disabled="true" />
        </el-form-item>
        <el-form-item label="标签名称" prop="dictLabel">
          <el-input v-model="form.dictLabel" placeholder="请输入标签名称" />
        </el-form-item>
        <el-form-item label="键值值" prop="dictValue">
          <el-input v-model="form.dictValue" placeholder="请输入键值值" />
        </el-form-item>
        <el-form-item label="显示排序" prop="dictSort">
          <el-input-number v-model="form.dictSort" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="回显样式" prop="listClass">
          <el-select v-model="form.listClass" placeholder="回显样式">
            <el-option label="默认" value="default" />
            <el-option label="主要" value="primary" />
            <el-option label="成功" value="success" />
            <el-option label="信息" value="info" />
            <el-option label="警告" value="warning" />
            <el-option label="危险" value="danger" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
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
import { ref, reactive, toRefs, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDictDataListApi, addDictDataApi, updateDictDataApi, deleteDictDataApi, getDictTypeListApi } from '@/api/system/dict'
import type { FormInstance } from 'element-plus'
import type { SearchParams } from '@/types'
import type { SysDictData } from '@/types/system'

const route = useRoute()

const dataList = ref<any[]>([])
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
  dictType: undefined,
  dictLabel: undefined,
  status: undefined
})



const typeOptions = ref<any[]>([])

const dataRef = ref<FormInstance>()

const rules = {
  dictLabel: [{ required: true, message: '标签名称不能为空', trigger: 'blur' }],
  dictValue: [{ required: true, message: '键值值不能为空', trigger: 'blur' }],
  dictSort: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }]
}

const data = reactive({ form: {} as any })
const { form } = toRefs(data)

const getList = async () => {
  loading.value = true
  try {
    const dictType = route.params.dictId as string
    const res = await getDictDataListApi(dictType) as SysDictData[]
    dataList.value = res
    total.value = res.length
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const loadDictTypeList = async () => {
  try {
    const res = await getDictTypeListApi({ pageNum: 1, pageSize: 1000 })
    typeOptions.value = (res as any).records || res
  } catch (error) {
    console.error(error)
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
  selectedIds.value = selection.map((item: any) => item.dictCode)
}

const handleAdd = () => {
  reset()
  open.value = true
  title.value = '新增字典数据'
}

const handleUpdate = (row?: any) => {
  reset()
  open.value = true
  title.value = row ? '修改字典数据' : '新增字典数据'
  form.value.dictCode = row?.dictCode
  form.value.dictLabel = row?.dictLabel
  form.value.dictValue = row?.dictValue
  form.value.dictSort = row?.dictSort
  form.value.listClass = row?.listClass
  form.value.status = row?.status
  form.value.dictType = row?.dictType
}

const handleDelete = async (row?: any) => {
  const dictCodes = row?.dictCode ? [row.dictCode] : selectedIds.value
  try {
    await ElMessageBox.confirm(`是否确认删除字典编码为"${dictCodes}"的数据项？`, '警告', { type: 'warning' })
    await deleteDictDataApi(dictCodes[0])
    ElMessage.success('删除成功')
    getList()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const reset = () => {
  form.value = { dictCode: undefined, dictLabel: undefined, dictValue: undefined, dictSort: 0, listClass: 'default', status: '0', dictType: undefined }
  ;(dataRef.value as FormInstance)?.resetFields()
}

const cancel = () => {
  open.value = false
  reset()
}

const submitForm = async () => {
  const formEl = dataRef.value
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (valid) {
      try {
        if (form.value.dictCode) {
          await updateDictDataApi(form.value)
        } else {
          await addDictDataApi(form.value)
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

watch(() => route.params.dictId, async (val) => {
  if (val) {
    const selectedType = typeOptions.value.find((item: any) => item.dictId === Number(val))
    if (selectedType) {
      queryParams.dictType = selectedType.dictType
      loadDictTypeList()
      getList()
    }
  }
}, { immediate: true })

loadDictTypeList()
getList()
</script>

<style scoped lang="scss">
.app-container { padding: 16px; }
.mb8 { margin-bottom: 8px; }
</style>
