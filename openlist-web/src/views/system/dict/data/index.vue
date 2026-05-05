<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
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
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
          <el-button type="danger" :disabled="multiple" @click="handleDelete">
            <el-icon><Delete /></el-icon> 删除
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="字典编码" prop="dictCode" width="100" align="center" />
        <el-table-column label="字典标签" prop="dictLabel" min-width="120" />
        <el-table-column label="字典键值" prop="dictValue" width="120" align="center" />
        <el-table-column label="字典排序" prop="dictSort" width="90" align="center" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'" effect="light">
              {{ scope.row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
        <el-table-column label="创建时间" prop="createTime" width="170" align="center" />
        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row)">
              <el-icon><EditPen /></el-icon> 编辑
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
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

    <!-- Dialog -->
    <el-dialog v-model="open" :title="title" width="520px" append-to-body class="modern-dialog">
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
          <el-input-number v-model="form.dictSort" controls-position="right" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="回显样式" prop="listClass">
          <el-select v-model="form.listClass" placeholder="回显样式" style="width: 100%">
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
import { Search, Refresh, Plus, Delete, Filter, EditPen } from '@element-plus/icons-vue'
import { getDictDataListApi, addDictDataApi, updateDictDataApi, deleteDictDataApi, getDictTypeListApi } from '@/api/system/dict'
import { useAppStore } from '@/stores/app'
import type { FormInstance } from 'element-plus'
import type { SearchParams } from '@/types'

const appStore = useAppStore()
const showSearch = ref(appStore.device === 'desktop')

const route = useRoute()

const dataList = ref<any[]>([])
const loading = ref(true)
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
  loading.value = true
  try {
    const dictType = route.query.dictType as string
    if (dictType) {
      queryParams.dictType = dictType
    } else {
      queryParams.dictType = undefined
    }
    const res = await getDictDataListApi(queryParams) as any
    if (res && typeof res === 'object') {
      dataList.value = res.records || res.list || []
      total.value = res.total || res.totalCount || 0
    } else {
      dataList.value = []
      total.value = 0
    }
  } catch (e: any) {
    console.error('[dict/data] error:', e)
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
  } catch (e: any) {
    console.error('[dict/data] load dict types error:', e)
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
  queryParams.pageNum = 1
  if (val) {
    queryParams.dictType = val as string
  } else {
    queryParams.dictType = undefined
  }
  await loadDictTypeList()
  await getList()
}, { immediate: true })

loadDictTypeList()
getList()
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);

  :deep(.el-card__body) {
    padding: 16px 20px;
  }
}

.table-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);
  flex: 1;

  :deep(.el-card__body) {
    padding: 20px;
  }
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .action-left {
    display: flex;
    gap: 8px;
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
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
  }

  .action-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>
