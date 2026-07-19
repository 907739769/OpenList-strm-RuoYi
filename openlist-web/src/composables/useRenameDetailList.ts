import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useRecordList } from './useRecordList'
import type { SearchParams } from '@/types'
import {
  getRenameDetailListApi,
  executeRenameDetailApi,
  batchDeleteRenameDetailApi,
  scrapeRenameDetailApi,
  batchScrapeRenameDetailApi,
  deleteScrapeFilesApi,
  batchDeleteScrapeFilesApi
} from '@/api/openlist/renameDetail'

export type RenameDetailQuery = SearchParams & {
  originalName?: string
  newName?: string
  originalPath?: string
  newPath?: string
  title?: string
  status?: string
}

/**
 * 重命名明细页（PC + 移动端）共用逻辑。
 * 列表/分页/搜索/选择/删除是标准记录页逻辑，复用 useRecordList；
 * 重试改名（弹窗可编辑 title/year/season/episode 后重新执行）与刮削相关操作是本页特有能力，
 * useRecordList 未提供，在此扩展叠加。
 */
export function useRenameDetailList() {
  const {
    recordList, loading, total, queryParams, totalPages,
    getList, silentRefresh, prevPage, nextPage, handleSizeChange,
    queryRef, dateRange, handleQuery, resetQuery,
    selectedIds, multiple, toggleSelect, handleCardClick, clearSelection, handleSelectionChange,
    handleDeleteOne, handleBatchDelete
  } = useRecordList<RenameDetailQuery>({
    listApi: getRenameDetailListApi,
    batchDeleteApi: batchDeleteRenameDetailApi,
    idField: 'id',
    labelField: 'newName',
    recordLabel: '重命名记录',
    defaultQuery: { status: undefined }
  })

  // --- 重试改名弹窗 ---
  const retryDialogVisible = ref(false)
  const retryLoading = ref(false)
  const retryFormRef = ref<FormInstance>()
  const retryForm = reactive({ id: 0, title: '', year: '', season: '', episode: '', mediaType: '' })
  const retryRules = reactive<FormRules>({
    title: [{ max: 100, message: '最多 100 个字符', trigger: 'blur' }],
    year: [{ pattern: /^\d{0,4}$/, message: '年份为 4 位数字', trigger: 'blur' }],
    season: [{ pattern: /^\d{1,2}$/, message: '季为 1-2 位数字', trigger: 'blur' }],
    episode: [{ pattern: /^\d{1,4}$/, message: '集为 1-4 位数字', trigger: 'blur' }]
  })

  const handleRetryOne = (row: any) => {
    retryForm.id = row.id
    retryForm.title = row.title || ''
    retryForm.year = row.year || ''
    retryForm.season = row.season || ''
    retryForm.episode = row.episode || ''
    retryForm.mediaType = row.mediaType || ''
    retryDialogVisible.value = true
  }

  const handleRetryClose = () => {
    retryFormRef.value?.resetFields()
  }

  const handleRetrySubmit = async () => {
    await retryFormRef.value?.validate()
    retryLoading.value = true
    try {
      await executeRenameDetailApi([retryForm.id], retryForm.title || undefined, retryForm.year || undefined, retryForm.season || undefined, retryForm.episode || undefined)
      ElMessage.success('编辑并重命名成功')
      retryDialogVisible.value = false
      getList()
    } catch (error: any) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      retryLoading.value = false
    }
  }

  // --- 批量执行 / 刮削 ---
  const handleBatchExecute = async () => {
    try {
      await ElMessageBox.confirm(`是否确认批量执行选中的 ${selectedIds.value.length} 条记录？`, '提示', { type: 'warning' })
      await executeRenameDetailApi(selectedIds.value)
      ElMessage.success('批量执行成功')
      getList()
    } catch (e) { if (e !== 'cancel') console.error(e) }
  }

  const handleScrapeOne = async (row: any) => {
    try {
      await ElMessageBox.confirm(`是否确认对"${row.newName}"执行刮削？`, '提示', { type: 'info' })
      await scrapeRenameDetailApi(row.id)
      ElMessage.success('刮削已启动')
      getList()
    } catch (e) { if (e !== 'cancel') console.error(e) }
  }

  const handleBatchScrape = async () => {
    try {
      await ElMessageBox.confirm(`是否确认批量刮削选中的 ${selectedIds.value.length} 条记录？`, '提示', { type: 'info' })
      await batchScrapeRenameDetailApi(selectedIds.value)
      ElMessage.success('批量刮削已启动')
      getList()
    } catch (e) { if (e !== 'cancel') console.error(e) }
  }

  const handleDeleteScrapeOne = async (row: any) => {
    try {
      await ElMessageBox.confirm(`是否确认删除"${row.newName}"的刮削文件（NFO + 图片）？`, '删除刮削文件', { type: 'warning' })
      await deleteScrapeFilesApi(row.id)
      ElMessage.success('刮削文件已删除')
      getList()
    } catch (e) { if (e !== 'cancel') console.error(e) }
  }

  const handleBatchDeleteScrape = async () => {
    try {
      await ElMessageBox.confirm(`是否确认删除选中记录的刮削文件？`, '批量删除刮削', { type: 'warning' })
      await batchDeleteScrapeFilesApi(selectedIds.value)
      ElMessage.success('刮削文件已删除')
      getList()
    } catch (e) { if (e !== 'cancel') console.error(e) }
  }

  return {
    recordList, loading, total, queryParams, totalPages,
    getList, silentRefresh, prevPage, nextPage, handleSizeChange,
    queryRef, dateRange, handleQuery, resetQuery,
    selectedIds, multiple, toggleSelect, handleCardClick, clearSelection, handleSelectionChange,
    handleDeleteOne, handleBatchDelete,
    retryDialogVisible, retryLoading, retryFormRef, retryForm, retryRules,
    handleRetryOne, handleRetryClose, handleRetrySubmit,
    handleBatchExecute, handleScrapeOne, handleBatchScrape,
    handleDeleteScrapeOne, handleBatchDeleteScrape
  }
}
