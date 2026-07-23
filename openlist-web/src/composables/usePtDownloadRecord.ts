import { ref, reactive, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPtDownloadRecordListApi, retryPtDownloadRecordApi } from '@/api/openlist/ptDownloadRecord'
import type { PtDownloadRecordQuery } from '@/api/openlist/ptDownloadRecord'

/**
 * PT 下载记录 composable：只读列表 + 失败重试，没有增删改，
 * 因此不复用 useTaskList（那个是围绕 CRUD 设计的，硬凑只会留一堆空实现）。
 */
export function usePtDownloadRecord() {
  const route = useRoute()

  const taskList = ref<any[]>([])
  const loading = ref(true)
  const total = ref(0)

  // 支持从订阅页"下载记录"按钮带 subId 跳转过来，直接筛出该订阅的记录
  const initialSubId = route.query.subId ? Number(route.query.subId) : undefined

  const queryParams = reactive<PtDownloadRecordQuery>({
    pageNum: 1,
    pageSize: 10,
    subId: initialSubId,
    state: undefined,
    title: undefined
  })

  const queryRef = ref<any>()

  const getList = async () => {
    loading.value = true
    try {
      const res = await getPtDownloadRecordListApi(queryParams)
      taskList.value = res.records || []
      total.value = res.total || 0
    } finally {
      loading.value = false
    }
  }

  const handleQuery = () => {
    queryParams.pageNum = 1
    getList()
  }

  const resetQuery = () => {
    if (queryRef.value) (queryRef.value as any).resetFields()
    handleQuery()
  }

  // ---------- 重试 ----------
  const retryingIds = reactive(new Set<number>())

  const handleRetry = async (row: any) => {
    retryingIds.add(row.id)
    try {
      const result = await retryPtDownloadRecordApi(row.id)
      ElMessage[result.pushed ? 'success' : 'info'](
        result.pushed ? '已重新找到并推送下载' : '重试未搜索到匹配资源'
      )
      getList()
    } catch (e) {
      console.error(e)
    } finally {
      retryingIds.delete(row.id)
    }
  }

  // ---------- 移动端 - 分页辅助 ----------
  const totalPages = computed(() => Math.ceil(total.value / queryParams.pageSize!) || 1)

  const prevPage = () => {
    if (queryParams.pageNum! > 1) {
      queryParams.pageNum!--
      getList()
    }
  }

  const nextPage = () => {
    if (queryParams.pageNum! < totalPages.value) {
      queryParams.pageNum!++
      getList()
    }
  }

  const handleSizeChange = () => {
    queryParams.pageNum = 1
    getList()
  }

  const searchCollapsed = ref(true)

  getList()

  return {
    taskList, loading, total, queryParams, getList, handleQuery, resetQuery, queryRef,
    retryingIds, handleRetry,
    totalPages, prevPage, nextPage, handleSizeChange, searchCollapsed
  }
}
