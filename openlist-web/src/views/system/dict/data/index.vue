<template>
  <div class="page-container">
    <!-- Header -->
    <div class="page-header">
      <div class="page-header-left">
        <div class="page-header-icon">
          <el-icon><List /></el-icon>
        </div>
        <div>
          <h2 class="page-title">字典数据</h2>
          <p class="page-desc">
            字典类型：<code class="dict-type-code">{{ currentDictType || '—' }}</code>
          </p>
        </div>
      </div>
    </div>

    <!-- Table Card -->
    <el-card class="table-card">
      <div class="action-bar">
        <div class="action-left">
          <el-button @click="handleBack">
            <el-icon><ArrowLeft /></el-icon> 返回
          </el-button>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
        </div>
      </div>

      <!-- Desktop Table -->
      <el-table v-if="appStore.device === 'desktop'" v-loading="loading" :data="dataList" class="modern-table">
        <el-table-column label="字典标签" prop="dictLabel" min-width="120" show-overflow-tooltip />
        <el-table-column label="字典键值" prop="dictValue" width="120" align="center" show-overflow-tooltip />
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

      <!-- Mobile Card List -->
      <div v-if="appStore.device === 'mobile'" v-loading="loading" class="mobile-card-list">
        <div v-for="item in dataList" :key="item.dictCode" class="mobile-card">
          <div class="mobile-card-header">
            <span class="mobile-card-title">{{ item.dictLabel }}</span>
            <el-tag size="small" :type="item.status === '0' ? 'success' : 'danger'">
              {{ item.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </div>
          <div class="mobile-card-body">
            <div class="mobile-card-row">
              <span class="mobile-card-label">字典键值</span>
              <span class="mobile-card-value">{{ item.dictValue }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">创建时间</span>
              <span class="mobile-card-value mobile-card-value-light">{{ item.createTime }}</span>
            </div>
          </div>
          <div class="mobile-card-actions">
            <el-button link type="primary" size="small" @click="handleUpdate(item)">
              <el-icon><EditPen /></el-icon> 编辑
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(item)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </div>
        </div>
        <el-empty v-if="!dataList.length" description="暂无数据" />
      </div>

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
    <el-dialog v-model="open" :title="title" :width="appStore.device === 'mobile' ? '90%' : '520px'" append-to-body class="modern-dialog">
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
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancel">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, EditPen, Delete, List } from '@element-plus/icons-vue'
import { getDictDataListApi, addDictDataApi, deleteDictDataApi, updateDictDataApi } from '@/api/system/dict'
import { useAppStore } from '@/stores/app'
import type { FormInstance } from 'element-plus'
import type { SearchParams } from '@/types'
import type { SysDictData } from '@/types/system'

const appStore = useAppStore()

const router = useRouter()
const route = useRoute()

const currentDictType = computed(() => (route.query.dictType as string) || '')

const handleBack = () => {
  router.push('/system/dict/type')
}

const dataList = ref<any[]>([])
const loading = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)

const queryParams = reactive<SearchParams>({
  pageNum: 1,
  pageSize: 10,
  dictType: undefined
})

const dataRef = ref<FormInstance>()

const rules = {
  dictLabel: [{ required: true, message: '字典标签不能为空', trigger: 'blur' }],
  dictValue: [{ required: true, message: '字典键值不能为空', trigger: 'blur' }]
}

const form = reactive<Partial<SysDictData>>({ dictCode: undefined, dictLabel: undefined, dictValue: undefined, dictType: undefined, dictSort: undefined, listClass: 'default', cssClass: '', isDefault: 'N', status: '0' })

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

const handleAdd = () => {
  reset()
  open.value = true
  title.value = '新增字典数据'
  const dictType = route.query.dictType as string
  if (dictType) {
    form.dictType = dictType
  }
}

const handleUpdate = (row?: any) => {
  open.value = true
  title.value = row ? '修改字典数据' : '新增字典数据'
  if (row) {
    form.dictCode = row.dictCode
    form.dictLabel = row.dictLabel
    form.dictValue = row.dictValue
    form.dictType = row.dictType
    form.dictSort = row.dictSort
    form.listClass = row.listClass || 'default'
    form.cssClass = row.cssClass || ''
    form.isDefault = row.isDefault || 'N'
    form.status = row.status
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认删除字典编码为"${row.dictCode}"的数据项？`, '警告', { type: 'warning' })
    await deleteDictDataApi(row.dictCode)
    ElMessage.success('删除成功')
    getList()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const reset = () => {
  form.dictCode = undefined
  form.dictLabel = undefined
  form.dictValue = undefined
  form.dictType = undefined
  form.dictSort = undefined
  form.listClass = 'default'
  form.cssClass = ''
  form.isDefault = 'N'
  form.status = '0'
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
        if (form.dictCode) {
          await updateDictDataApi(form as SysDictData)
        } else {
          await addDictDataApi(form as SysDictData)
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

getList()
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ============================================
   Page Header
   ============================================ */
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;

  .page-header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .page-header-icon {
      width: 48px;
      height: 48px;
      border-radius: 14px;
      background: linear-gradient(135deg, #0d9488, #14b8a6);
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      box-shadow: 0 4px 14px rgba(13, 148, 136, 0.35);
    }

    .page-title {
      margin: 0;
      font-size: 22px;
      font-weight: 700;
      color: var(--osr-text-primary);
      letter-spacing: 0.3px;
    }

    .page-desc {
      margin: 4px 0 0;
      font-size: 13px;
      color: var(--osr-text-secondary);

      .dict-type-code {
        font-family: 'SF Mono', 'Courier New', monospace;
        font-size: 12px;
        color: var(--osr-primary);
        background: var(--osr-primary-light-9);
        padding: 1px 8px;
        border-radius: 5px;
      }
    }
  }
}

/* ============================================
   Action Bar
   ============================================ */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  .action-left {
    display: flex;
    gap: 8px;
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
    gap: 12px;
  }

  .page-header {
    padding: 0 4px;

    .page-header-icon {
      width: 42px;
      height: 42px;
      font-size: 20px;
    }

    .page-title { font-size: 19px; }
    .page-desc { font-size: 12px; }
  }

  :deep(.el-table) {
    font-size: 13px;

    .el-table__cell {
      padding: 8px 0;
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
    background: var(--osr-surface);
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
