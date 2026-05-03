<template>
  <div class="page-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="源目录" prop="copySrcPath">
          <el-input v-model="queryParams.copySrcPath" placeholder="请输入源目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="目标目录" prop="copyDstPath">
          <el-input v-model="queryParams.copyDstPath" placeholder="请输入目标目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="源文件名" prop="copySrcFileName">
          <el-input v-model="queryParams.copySrcFileName" placeholder="请输入源文件名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="目标名" prop="copyDstFileName">
          <el-input v-model="queryParams.copyDstFileName" placeholder="请输入目标名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="copyStatus">
          <el-select v-model="queryParams.copyStatus" placeholder="状态" clearable>
            <el-option label="处理中" value="1" />
            <el-option label="失败" value="2" />
            <el-option label="成功" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="-"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">批量删除</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="CloudDownload" :disabled="multiple" @click="handleBatchRemoveNetDisk()">批量删除网盘文件</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Refresh" :disabled="multiple" @click="handleBatchRetry()">批量重试</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="recordList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="复制详情" min-width="300">
          <template #default="scope">
            <div class="file-change-box">
              <div class="file-row">
                <span class="file-label label-src">源</span>
                <span class="file-name">{{ scope.row.copySrcFileName }}</span>
                <span class="file-path">{{ scope.row.copySrcPath }}</span>
              </div>
              <div class="file-row">
                <span class="file-label label-dst">目</span>
                <span class="file-name">{{ scope.row.copyDstFileName }}</span>
                <span class="file-path">{{ scope.row.copyDstPath }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="copyStatus" width="80" align="center">
          <template #default="scope">
            <el-tag :type="getCopyStatusType(scope.row.copyStatus)">
              {{ getCopyStatusText(scope.row.copyStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" align="center" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200">
          <template #default="scope">
            <el-button link type="primary" icon="Refresh" @click="handleRetryOne(scope.row)">重试</el-button>
            <el-button link type="warning" icon="CloudDownload" @click="handleRemoveNetDiskOne(scope.row)">删除网盘文件</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDeleteOne(scope.row)">删除记录</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCopyRecordListApi, retryCopyRecordApi, batchDeleteCopyRecordApi, batchRetryCopyRecordApi, batchRemoveCopyNetDiskApi } from '@/api/openlist/copyRecord'
import type { SearchParams, PageResult } from '@/types'

const recordList = ref<any[]>([])
const loading = ref(true)
const total = ref(0)
const multiple = ref(true)
const selectedIds = ref<number[]>([])
const dateRange = ref<string[] | null>(null)

const queryParams = reactive<SearchParams & { copySrcPath?: string; copyDstPath?: string; copySrcFileName?: string; copyDstFileName?: string; copyStatus?: string }>({
  pageNum: 1,
  pageSize: 10,
  copyStatus: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getCopyRecordListApi(queryParams) as PageResult
    recordList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  if (dateRange.value != null && dateRange.value.length === 2) {
    queryParams.params = { beginTime: dateRange.value[0] + ' 00:00:00', endTime: dateRange.value[1] + ' 23:59:59' }
  } else {
    delete queryParams.params
  }
  getList()
}
const resetQuery = () => {
  dateRange.value = null
  if (queryRef.value) (queryRef.value as any).resetFields()
  handleQuery()
}
const handleSelectionChange = (selection: any[]) => { multiple.value = !selection.length; selectedIds.value = selection.map((item: any) => item.copyId) }

const getCopyStatusText = (status: string) => {
  const map: Record<string, string> = { '1': '处理中', '2': '失败', '3': '成功', '4': '未知' }
  return map[status] || '未知'
}

const getCopyStatusType = (status: string) => {
  const map: Record<string, 'warning' | 'danger' | 'success' | 'info'> = { '1': 'warning', '2': 'danger', '3': 'success' }
  return map[status] || 'info'
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(`是否确认删除复制记录编号为"${selectedIds.value}"的数据项？`, '警告', { type: 'warning' })
    await batchDeleteCopyRecordApi(selectedIds.value)
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleRetryOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认重试复制记录"${row.copyId}"？`, '警告', { type: 'warning' })
    await retryCopyRecordApi(row.copyId)
    ElMessage.success('重试成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchRetry = async () => {
  try {
    await ElMessageBox.confirm(`是否确认批量重试选中的复制记录？`, '警告', { type: 'warning' })
    await batchRetryCopyRecordApi(selectedIds.value)
    ElMessage.success('批量重试成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchRemoveNetDisk = async () => {
  try {
    await ElMessageBox.confirm(`危险操作：确认要从网盘中彻底删除选中的 ${selectedIds.value.length} 个目标文件吗？`, '警告', { type: 'error' })
    await batchRemoveCopyNetDiskApi(selectedIds.value)
    ElMessage.success('删除网盘文件成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleRemoveNetDiskOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`危险操作：确认要从网盘中彻底删除该目标文件吗？`, '警告', { type: 'error' })
    await batchRemoveCopyNetDiskApi([row.copyId])
    ElMessage.success('删除网盘文件成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleDeleteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认删除复制记录编号为"${row.copyId}"的数据项？`, '警告', { type: 'warning' })
    await batchDeleteCopyRecordApi([row.copyId])
    ElMessage.success('删除成功')
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
