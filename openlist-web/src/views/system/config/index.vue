<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="queryParams.configName" placeholder="请输入参数名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input v-model="queryParams.configKey" placeholder="请输入参数键名" clearable @keyup.enter="handleQuery" />
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
          <el-button type="warning" @click="handleExport">
            <el-icon><Download /></el-icon> 导出
          </el-button>
          <el-button type="danger" @click="handleRefreshCache">
            <el-icon><Refresh /></el-icon> 刷新缓存
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <!-- Desktop Table -->
      <el-table v-if="appStore.device === 'desktop'" v-loading="loading" :data="configList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="参数编号" align="center" prop="configId" width="100" />
        <el-table-column label="参数名称" align="center" prop="configName" min-width="140" :show-overflow-tooltip="true" />
        <el-table-column label="参数键名" align="center" prop="configKey" min-width="160" :show-overflow-tooltip="true" />
        <el-table-column label="参数键值" align="center" prop="configValue" min-width="160" :show-overflow-tooltip="true" />
        <el-table-column label="备注" align="center" prop="remark" min-width="140" :show-overflow-tooltip="true" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="170" />
        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row)">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Mobile Card List -->
      <div v-if="appStore.device === 'mobile'" v-loading="loading" class="mobile-card-list">
        <div v-for="item in configList" :key="item.configId" class="mobile-card">
          <div class="mobile-card-header">
            <span class="mobile-card-title">{{ item.configName }}</span>
            <el-tag size="small" :type="item.configId ? 'success' : 'info'">{{ item.configId }}</el-tag>
          </div>
          <div class="mobile-card-body">
            <div class="mobile-card-row">
              <span class="mobile-card-label">参数键名</span>
              <span class="mobile-card-value">{{ item.configKey }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">参数键值</span>
              <span class="mobile-card-value mobile-card-value-clip">{{ item.configValue }}</span>
            </div>
            <div v-if="item.remark" class="mobile-card-row">
              <span class="mobile-card-label">备注</span>
              <span class="mobile-card-value">{{ item.remark }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">创建时间</span>
              <span class="mobile-card-value mobile-card-value-light">{{ item.createTime }}</span>
            </div>
          </div>
          <div class="mobile-card-actions">
            <el-button link type="primary" size="small" @click="handleUpdate(item)">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(item)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </div>
        </div>
        <el-empty v-if="!configList.length" description="暂无数据" />
      </div>

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

    <!-- Dialog -->
    <el-dialog v-model="open" :title="title" width="520px" append-to-body class="modern-dialog">
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
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入内容" />
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
import { Search, Refresh, Plus, Edit, Delete, Download, Filter } from '@element-plus/icons-vue'
import { getConfigListApi, addConfigApi, updateConfigApi, deleteConfigApi, refreshCacheApi } from '@/api/system/config'
import { useAppStore } from '@/stores/app'
import type { FormInstance } from 'element-plus'
import type { SearchParams, PageResult } from '@/types'

const appStore = useAppStore()
const showSearch = ref(window.innerWidth >= 768)

const configList = ref<any[]>([])
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
.page-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ============================================
   Search Card
   ============================================ */
.search-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);

  :deep(.el-card__body) {
    padding: 14px 16px;
  }
}

/* ============================================
   Table Card
   ============================================ */
.table-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);

  :deep(.el-card__body) {
    padding: 16px;
    display: flex;
    flex-direction: column;
  }
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  .action-left {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }
}

/* ============================================
   Pagination
   ============================================ */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 12px;
}

/* ============================================
   Mobile Responsive
   ============================================ */
@media (max-width: 768px) {
  .page-container {
    gap: 10px;
  }

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
    gap: 6px;
    margin-bottom: 10px;

    .action-left {
      gap: 4px;
    }
  }

  .table-card :deep(.el-card__body) {
    padding: 12px;
  }

  /* ============================================
     Mobile Card List
     ============================================ */
  .mobile-card-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .mobile-card {
    background: white;
    border-radius: 8px;
    border: 1px solid var(--osr-border-light);
    overflow: hidden;

    .mobile-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 12px 8px;
      border-bottom: 1px solid var(--osr-border-light);
      background: var(--osr-bg-page);

      .mobile-card-title {
        font-size: 14px;
        font-weight: 600;
        color: var(--osr-text-primary);
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        margin-right: 8px;
        i { color: var(--osr-primary); margin-right: 4px; }
      }
    }

    .mobile-card-body {
      padding: 0;

      .mobile-card-row {
        display: flex;
        align-items: flex-start;
        padding: 8px 12px;
        font-size: 13px;
        border-bottom: 1px solid var(--osr-border-light);

        &:last-child {
          border-bottom: none;
        }

        .mobile-card-label {
          width: 64px;
          color: var(--osr-text-secondary);
          flex-shrink: 0;
          font-size: 12px;
          line-height: 1.5;
          padding-top: 1px;
        }

        .mobile-card-value {
          flex: 1;
          min-width: 0;
          color: var(--osr-text-primary);
          font-size: 13px;
          line-height: 1.5;
          word-break: break-all;

          &.mobile-card-value-clip {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          &.mobile-card-value-light {
            color: var(--osr-text-secondary);
            font-size: 12px;
          }
        }
      }
    }

    .mobile-card-actions {
      display: flex;
      justify-content: flex-end;
      gap: 2px;
      padding: 8px 12px 10px;
      border-top: 1px solid var(--osr-border-light);
    }
  }
}
</style>
