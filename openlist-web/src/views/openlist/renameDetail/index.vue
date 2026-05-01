<template>
  <div class="app-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="原文件名" prop="originalName">
          <el-input v-model="queryParams.originalName" placeholder="请输入原文件名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="新文件名" prop="newName">
          <el-input v-model="queryParams.newName" placeholder="请输入新文件名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="原目录" prop="originalPath">
          <el-input v-model="queryParams.originalPath" placeholder="请输入原目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="新目录" prop="newPath">
          <el-input v-model="queryParams.newPath" placeholder="请输入新目录" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="影视名称" prop="title">
          <el-input v-model="queryParams.title" placeholder="请输入影视名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="状态" clearable>
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
          <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">批量删除</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="info" plain icon="Refresh" :disabled="multiple" @click="handleBatchExecute()">批量执行</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="detailList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="媒体信息" width="120">
          <template #default="scope">
            <div>
              <div class="media-info-title">{{ scope.row.title || '未识别' }}{{ scope.row.year ? ' (' + scope.row.year + ')' : '' }}</div>
              <div class="media-info-meta">
                {{ scope.row.mediaType || '' }}{{ scope.row.season ? ' · S' + scope.row.season : '' }}{{ scope.row.episode ? ' E' + scope.row.episode : '' }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="文件变更对比" min-width="300">
          <template #default="scope">
            <div class="file-change-box">
              <div class="file-row">
                <span class="file-label label-old">原</span>
                <span class="file-name">{{ scope.row.originalName }}</span>
              </div>
              <div class="file-path">{{ scope.row.originalPath }}</div>
              <div class="file-row">
                <span class="file-label label-new">新</span>
                <span class="file-name">{{ scope.row.newName || '...' }}</span>
              </div>
              <div class="file-path" v-if="scope.row.newPath">{{ scope.row.newPath }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="参数" width="200">
          <template #default="scope">
            <div class="tech-badges">
              <el-tag v-if="scope.row.resolution" size="small" type="info">{{ scope.row.resolution }}</el-tag>
              <el-tag v-if="scope.row.videoCodec" size="small" type="primary">{{ scope.row.videoCodec }}</el-tag>
              <el-tag v-if="scope.row.source" size="small" type="warning">{{ scope.row.source }}</el-tag>
              <el-tag v-if="scope.row.releaseGroup" size="small">{{ scope.row.releaseGroup }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === '1' ? 'success' : 'danger'">
              {{ scope.row.status === '1' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" align="center" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDeleteOne(scope.row)">删除</el-button>
            <el-button link type="primary" icon="VideoPlay" @click="handleExecuteOne(scope.row)">执行</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRenameDetailListApi, executeRenameDetailApi } from '@/api/openlist/renameDetail'
import type { SearchParams, PageResult } from '@/types'

const detailList = ref<any[]>([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const multiple = ref(true)
const selectedIds = ref<number[]>([])
const dateRange = ref<string[] | null>(null)

const queryParams = reactive<SearchParams & { originalName?: string; newName?: string; originalPath?: string; newPath?: string; title?: string; status?: string }>({
  pageNum: 1,
  pageSize: 10,
  status: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getRenameDetailListApi(queryParams) as PageResult
    detailList.value = res.records
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
const handleSelectionChange = (selection: any[]) => { multiple.value = !selection.length; selectedIds.value = selection.map((item: any) => item.id) }

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(`是否确认删除重命名详情编号为"${selectedIds.value}"的数据项？`, '警告', { type: 'warning' })
    await executeRenameDetailApi(selectedIds.value.map(id => id)) // reuse delete via batch
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleDeleteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认删除重命名详情编号为"${row.id}"的数据项？`, '警告', { type: 'warning' })
    await executeRenameDetailApi([row.id])
    ElMessage.success('删除成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleBatchExecute = async () => {
  try {
    await ElMessageBox.confirm(`是否确认批量执行选中的重命名详情？`, '警告', { type: 'warning' })
    await executeRenameDetailApi(selectedIds.value)
    ElMessage.success('批量执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleEdit = (_row: any) => {
  ElMessage.info('编辑功能待实现')
}

const handleExecuteOne = async (row: any) => {
  try {
    await ElMessageBox.confirm(`是否确认执行重命名详情"${row.id}"？`, '警告', { type: 'warning' })
    await executeRenameDetailApi([row.id])
    ElMessage.success('执行成功')
    getList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const queryRef = ref<any>()
getList()
</script>

<style scoped lang="scss">
.app-container { padding: 16px; }
.mb8 { margin-bottom: 8px; }
.file-change-box { display: flex; flex-direction: column; width: 100%; }
.file-row { margin-bottom: 4px; display: flex; align-items: baseline; }
.file-label { display: inline-block; padding: 1px 4px; font-size: 10px; font-weight: bold; color: #fff; border-radius: 3px; margin-right: 4px; flex-shrink: 0; width: 24px; text-align: center; }
.label-old { background-color: #f8ac59; }
.label-new { background-color: #1ab394; }
.file-name { font-size: 13px; color: #333; font-weight: 600; }
.file-path { color: #999; font-size: 11px; padding-left: 28px; font-family: Consolas, "Courier New", monospace; }
.media-info-title { font-size: 13px; color: #333; font-weight: 600; }
.media-info-meta { font-size: 11px; color: #999; margin-top: 2px; }
.tech-badges { display: flex; flex-wrap: wrap; gap: 4px; }
</style>
