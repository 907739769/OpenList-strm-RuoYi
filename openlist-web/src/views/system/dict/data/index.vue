<template>
  <div class="app-container">
    <el-card v-if="error">
      <el-alert :title="'加载失败: ' + error" type="error" :closable="false" show-icon />
      <el-button type="primary" @click="getList" style="margin-top: 12px;">重试</el-button>
    </el-card>
    <el-card v-else>
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
        <el-form-item label="字典类型" prop="dictType">
          <el-select v-model="queryParams.dictType" placeholder="请选择字典类型" clearable @change="handleQuery">
            <el-option
              v-for="item in typeOptions"
              :key="item.dictId"
              :label="item.dictName + ' (' + item.dictType + ')'"
              :value="item.dictType"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标签名称" prop="dictLabel">
          <el-input v-model="queryParams.dictLabel" placeholder="请输入标签名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="数据状态" clearable @change="handleQuery">
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
        <el-col :span="1.5" style="margin-left: auto;">
          <el-button icon="Search" @click="showSearch = !showSearch">{{ showSearch ? '隐藏搜索' : '显示搜索' }}</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="字典编码" align="center" prop="dictCode" />
        <el-table-column label="字典标签" align="center" prop="dictLabel" />
        <el-table-column label="字典键值" align="center" prop="dictValue" />
        <el-table-column label="字典排序" align="center" prop="dictSort" />
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

      <el-pagination
        v-show="total > 0"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="getList"
        @size-change="getList"
      />
    </el-card>

    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="dataRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="字典类型">
          <el-input v-model="form.dictType" :disabled="true" placeholder="未选择字典类型" />
        </el-form-item>
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="form.dictLabel" placeholder="请输入字典标签" />
        </el-form-item>
        <el-form-item label="字典键值" prop="dictValue">
          <el-input v-model="form.dictValue" placeholder="请输入字典键值" />
        </el-form-item>
        <el-form-item label="字典排序" prop="dictSort">
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
import { ref, reactive, toRefs, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDictDataListApi, addDictDataApi, updateDictDataApi, deleteDictDataApi, getDictTypeListApi } from '@/api/system/dict'
import type { FormInstance } from 'element-plus'
import type { SearchParams } from '@/types'

const route = useRoute()

const error = ref<string>('')
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
  dictLabel: [{ required: true, message: '字典标签不能为空', trigger: 'blur' }],
  dictValue: [{ required: true, message: '字典键值不能为空', trigger: 'blur' }],
  dictSort: [{ required: true, message: '字典排序不能为空', trigger: 'blur' }]
}

const data = reactive({ form: {} as any })
const { form } = toRefs(data)

const queryRef = ref<FormInstance>()

const getList = async () => {
  error.value = ''
  loading.value = true
  try {
    const dictType = route.query.dictType as string
    if (dictType) {
      queryParams.dictType = dictType
    } else {
      queryParams.dictType = undefined
    }
    console.log('[dict/data] calling API with params:', { ...queryParams })
    const res = await getDictDataListApi(queryParams) as any
    console.log('[dict/data] API response:', res)
    if (res && typeof res === 'object') {
      dataList.value = res.records || res.list || []
      total.value = res.total || res.totalCount || 0
      console.log('[dict/data] loaded', total.value, 'items')
    } else {
      dataList.value = []
      total.value = 0
    }
  } catch (e: any) {
    console.error('[dict/data] error:', e)
    error.value = e?.message || '加载失败'
    dataList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const loadDictTypeList = async () => {
  try {
    const res = await getDictTypeListApi({ pageNum: 1, pageSize: 1000 }) as any
    typeOptions.value = (res && res.records) ? res.records : (Array.isArray(res) ? res : [])
    console.log('[dict/data] loaded', typeOptions.value.length, 'dict types')
  } catch (e: any) {
    console.error('[dict/data] load dict types error:', e)
    error.value = '加载字典类型失败: ' + (e?.message || '')
    typeOptions.value = []
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const resetQuery = async () => {
  await nextTick()
  queryRef.value?.resetFields()
  queryParams.pageNum = 1
  getList()
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
  const dictType = route.query.dictType as string
  if (dictType) {
    form.value.dictType = dictType
  }
}

const handleUpdate = (row?: any) => {
  reset()
  open.value = true
  title.value = row ? '修改字典数据' : '新增字典数据'
  if (row) {
    form.value.dictCode = row.dictCode
    form.value.dictLabel = row.dictLabel
    form.value.dictValue = row.dictValue
    form.value.dictSort = row.dictSort
    form.value.listClass = row.listClass
    form.value.status = row.status
    form.value.dictType = row.dictType
  }
}

const handleDelete = async (row?: any) => {
  const dictCodes = row?.dictCode ? [row.dictCode] : selectedIds.value
  if (!dictCodes.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`是否确认删除字典编码为"${dictCodes.join(', ')}"的数据项？`, '警告', { type: 'warning' })
    await deleteDictDataApi(dictCodes[0])
    ElMessage.success('删除成功')
    getList()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const reset = () => {
  form.value = { dictCode: undefined, dictLabel: undefined, dictValue: undefined, dictSort: 0, listClass: 'default', status: '0', dictType: undefined }
  dataRef.value?.resetFields()
}

const cancel = () => {
  open.value = false
  reset()
}

const submitForm = async () => {
  const formEl = dataRef.value
  if (!formEl) return
  await formEl.validate(async (valid: boolean) => {
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

watch(() => route.query.dictType, async (val) => {
  console.log('[dict/data] dictType changed to:', val)
  queryParams.pageNum = 1
  if (val) {
    queryParams.dictType = val as string
  } else {
    queryParams.dictType = undefined
  }
  await loadDictTypeList()
  await getList()
}, { immediate: true })

console.log('[dict/data] component mounted, route:', route.path, route.query)
loadDictTypeList()
getList()
</script>

<style scoped lang="scss">
.app-container { padding: 16px; }
.mb8 { margin-bottom: 8px; }
</style>
