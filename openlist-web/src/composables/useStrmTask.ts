import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useTaskList } from './useTaskList'
import {
  getStrmTaskListApi,
  addStrmTaskApi,
  updateStrmTaskApi,
  deleteStrmTaskApi,
  executeStrmTaskApi
} from '@/api/openlist/strmTask'
import type { SearchParams } from '@/types'

interface StrmTaskQuery extends SearchParams {
  strmTaskPath?: string
  strmTaskStatus?: string
}

/**
 * STRM 任务共享 composable
 * PC 端和移动端共享列表、CRUD、搜索逻辑
 */
export function useStrmTask() {
  const base = useTaskList<StrmTaskQuery>({
    listApi: getStrmTaskListApi,
    addApi: addStrmTaskApi,
    updateApi: updateStrmTaskApi,
    deleteApi: deleteStrmTaskApi,
    executeApi: executeStrmTaskApi,
    idField: 'strmTaskId',
    initForm: () => ({
      strmTaskId: undefined,
      strmTaskPath: undefined,
      strmTaskStatus: '1'
    }),
    rules: {
      strmTaskPath: [{ required: true, message: 'STRM目录不能为空', trigger: 'blur' }]
    },
    defaultQuery: {
      strmTaskStatus: undefined
    }
  })

  // 移动端 - 全选卡片逻辑
  const toggleSelect = (id: number) => {
    const idx = base.selectedIds.value.indexOf(id)
    if (idx > -1) {
      base.selectedIds.value.splice(idx, 1)
    } else {
      base.selectedIds.value.push(id)
    }
  }

  const handleCardClick = (event: Event, id: number) => {
    const target = event.target as HTMLElement
    if (target.closest('.card-checkbox')) return
    toggleSelect(id)
  }

  const clearSelection = () => {
    base.selectedIds.value = []
  }

  // 移动端 - 分页辅助
  const totalPages = computed(() => Math.ceil(base.total.value / base.queryParams.pageSize) || 1)

  const prevPage = () => {
    if (base.queryParams.pageNum > 1) {
      base.queryParams.pageNum--
      base.getList()
    }
  }

  const nextPage = () => {
    if (base.queryParams.pageNum < totalPages.value) {
      base.queryParams.pageNum++
      base.getList()
    }
  }

  const handleSizeChange = () => {
    base.queryParams.pageNum = 1
    base.getList()
  }

  // 移动端 - 全文对话框
  const fullTextVisible = ref(false)
  const fullTextTitle = ref('')
  const fullTextContent = ref('')

  const showFullText = (content: string, title: string) => {
    fullTextTitle.value = title
    fullTextContent.value = content
    fullTextVisible.value = true
  }

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text)
      ElMessage.success('已复制到剪贴板')
    } catch {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
      ElMessage.success('已复制到剪贴板')
    }
  }

  // 移动端 - 搜索面板折叠
  const searchCollapsed = ref(true)

  // 批量执行
  const handleBatchExecute = async () => {
    const { ElMessageBox } = await import('element-plus')
    try {
      await ElMessageBox.confirm(
        `是否确认批量执行选中的 ${base.selectedIds.value.length} 个STRM任务？`,
        '提示', { type: 'warning' }
      )
      await executeStrmTaskApi(base.selectedIds.value)
      ElMessage.success('批量执行成功')
      base.getList()
    } catch (e) { if (e !== 'cancel') console.error(e) }
  }

  // 初始化加载
  base.getList()

  return {
    ...base,
    // 移动端卡片选择
    toggleSelect, handleCardClick, clearSelection,
    // 移动端分页
    totalPages, prevPage, nextPage, handleSizeChange,
    // 全文对话框
    fullTextVisible, fullTextTitle, fullTextContent,
    showFullText, copyToClipboard,
    // 搜索面板
    searchCollapsed,
    // 批量执行
    handleBatchExecute
  }
}
