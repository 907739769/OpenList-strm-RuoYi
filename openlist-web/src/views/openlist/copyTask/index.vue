<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
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
          <el-button type="warning" :disabled="multiple" @click="handleExecute()">
            <el-icon><VideoPlay /></el-icon> 批量执行
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <!-- Table -->
      <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
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
        <el-table-column label="创建时间" prop="createTime" width="170" align="center" />
        <el-table-column label="操作" align="center" width="220" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row)">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
            <el-button link type="primary" @click="handleExecuteOne(scope.row)">
              <el-icon><VideoPlay /></el-icon> 执行
            </el-button>
          </template>
        </el-table-column>
      </el-table>

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
    <el-dialog v-model="open" :title="dialogTitle" width="600px" append-to-body class="modern-dialog">
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
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入内容" />
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
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, VideoPlay, Filter } from '@element-plus/icons-vue'
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect/index.vue'
import { getCopyTaskListApi, addCopyTaskApi, updateCopyTaskApi, deleteCopyTaskApi, executeCopyTaskApi } from '@/api/openlist/copyTask'
import { useAppStore } from '@/stores/app'
import type { SearchParams, PageResult } from '@/types'

const appStore = useAppStore()
const showSearch = computed(() => appStore.device === 'desktop')

const taskList = ref<any[]>([])
const loading = ref(true)
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
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ============================================
   Search Card
   ============================================ */
.search-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);

  :deep(.el-card__body) {
    padding: 16px 20px;
  }
}

/* ============================================
   Table Card
   ============================================ */
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

/* ============================================
   Pagination
   ============================================ */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ============================================
   Mobile Responsive
   ============================================ */
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

    .el-table__cell {
      padding: 8px 0;
    }
  }

  .action-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>
