<template>
  <div class="page-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="文件名称" prop="strmFileName">
          <el-input v-model="queryParams.strmFileName" placeholder="请输入文件名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="目录路径" prop="strmPath">
          <el-input v-model="queryParams.strmPath" placeholder="请输入目录路径" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="strmStatus">
          <el-select v-model="queryParams.strmStatus" placeholder="状态" clearable>
            <el-option label="成功" value="1" />
            <el-option label="失败" value="0" />
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
          <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">批量删除记录</el-button>
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
        <el-table-column label="文件信息" min-width="300">
          <template #default="scope">
            <div class="file-info-box">
              <div class="file-name"><i class="fa fa-file-video-o"></i> {{ scope.row.strmFileName }}</div>
              <div class="file-path" :title="scope.row.strmPath">{{ scope.row.strmPath }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="strmStatus" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.strmStatus === '1' ? 'success' : 'danger'">
              {{ scope.row.strmStatus === '1' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" align="center" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200">
          <template #default="scope">
            <el-button link type="primary" icon="Refresh" @click="handleRetryOne(scope.row)">重试生成</el-button>
            <el-button link type="warning" icon="CloudDownload" @click="handleRemoveNetDiskOne(scope.row)">删除网盘源文件</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDeleteOne(scope.row)">仅删除记录</el-button>
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
import { getStrmRecordListApi, retryStrmRecordApi, batchDeleteStrmRecordApi, batchRetryStrmRecordApi, batchRemoveStrmNetDiskApi } from '@/api/openlist/strmRecord'
import type { SearchParams, PageResult } from '@/types'

const recordList = ref<any[]>([])
const loading = ref(true)
const total = ref(0)
const multiple = ref(true)
const selectedIds = ref<number[]>([])
const dateRange = ref<string[] | null>(null)

const queryParams = reactive<SearchParams & { strmFileName?: string; strmPath?: string; strmStatus?: string }>({
  pageNum: 1,
  pageSize: 10,
  strmStatus: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getStrmRecordListApi(queryParams) as PageResult
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
const handleSelectionChange = (selection: any[]) => { multiple.value = !selection.length; selectedIds.value = selection.map((item: any) => item.strmId) }

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(`是否确认删除STRM记录编号为"${selectedIds.value}"的数据项？`, '警告', { type: 'warning' })
    await batchDeleteStrmRecordApi(selectedIds.value)
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleRetryOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认重试STRM记录"${row.strmId}"？`, '警告', { type: 'warning' })
    await retryStrmRecordApi(row.strmId)
    ElMessage.success('重试成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchRetry = async () => {
  try {
    await ElMessageBox.confirm(`是否确认批量重试选中的STRM记录？`, '警告', { type: 'warning' })
    await batchRetryStrmRecordApi(selectedIds.value)
    ElMessage.success('批量重试成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchRemoveNetDisk = async () => {
  try {
    await ElMessageBox.confirm(`危险操作：确认要从网盘中彻底删除选中的 ${selectedIds.value.length} 个文件吗？`, '警告', { type: 'error' })
    await batchRemoveStrmNetDiskApi(selectedIds.value)
    ElMessage.success('删除网盘文件成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleRemoveNetDiskOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`危险操作：确认要从网盘中彻底删除该文件吗？`, '警告', { type: 'error' })
    await batchRemoveStrmNetDiskApi([row.strmId])
    ElMessage.success('删除网盘文件成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleDeleteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认删除STRM记录编号为"${row.strmId}"的数据项？`, '警告', { type: 'warning' })
    await batchDeleteStrmRecordApi([row.strmId])
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
