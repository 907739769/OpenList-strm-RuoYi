import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTaskList } from './useTaskList'
import {
  getPtSubscriptionListApi,
  addPtSubscriptionApi,
  updatePtSubscriptionApi,
  deletePtSubscriptionApi,
  tmdbSearchApi,
  subscribeApi,
  getSubscriptionProgressApi,
  refreshSubscriptionApi,
  pauseSubscriptionApi,
  resumeSubscriptionApi
} from '@/api/openlist/ptSubscription'
import type { SearchParams } from '@/types'

interface PtSubscriptionQuery extends SearchParams {
  title?: string
  mediaType?: string
  status?: string
}

/**
 * PT 订阅 composable
 */
export function usePtSubscription() {
  const base = useTaskList<PtSubscriptionQuery>({
    listApi: getPtSubscriptionListApi,
    addApi: addPtSubscriptionApi,
    updateApi: updatePtSubscriptionApi,
    deleteApi: deletePtSubscriptionApi,
    idField: 'id',
    initForm: () => ({ id: undefined }),
    rules: {},
    defaultQuery: { title: undefined, mediaType: undefined, status: undefined }
  })

  // ---------- 建订阅向导 ----------

  const subscribeOpen = ref(false)
  const searchLoading = ref(false)
  const subscribeLoading = ref(false)
  const searchResults = ref<any[]>([])

  const searchForm = reactive({
    mediaType: 'TV',
    keyword: ''
  })

  /** 当前选中的候选作品 */
  const picked = ref<any>(null)
  /** 剧集才需要选季 */
  const pickedSeason = ref<number>(1)

  const openSubscribeDialog = () => {
    searchForm.mediaType = 'TV'
    searchForm.keyword = ''
    searchResults.value = []
    picked.value = null
    pickedSeason.value = 1
    subscribeOpen.value = true
  }

  const doSearch = async () => {
    if (!searchForm.keyword?.trim()) {
      ElMessage.warning('请输入片名')
      return
    }
    // 换关键词重搜时必须清掉上一次的选择，否则用户没重新点选就点「订阅」会提交旧作品
    picked.value = null
    searchLoading.value = true
    try {
      searchResults.value = (await tmdbSearchApi(searchForm.mediaType, searchForm.keyword)) || []
      if (!searchResults.value.length) {
        ElMessage.info('没有搜到结果，换个关键词试试')
      }
    } catch (e) {
      // 拦截器已弹过错误提示，这里只记录
      console.error(e)
    } finally {
      searchLoading.value = false
    }
  }

  const pick = (item: any) => {
    picked.value = item
    pickedSeason.value = 1
  }

  const confirmSubscribe = async () => {
    if (!picked.value) {
      ElMessage.warning('请先选择一部作品')
      return
    }
    subscribeLoading.value = true
    try {
      await subscribeApi({
        tmdbId: picked.value.tmdbId,
        mediaType: searchForm.mediaType,
        season: searchForm.mediaType === 'MOVIE' ? undefined : pickedSeason.value
      })
      ElMessage.success('订阅成功')
      subscribeOpen.value = false
      base.getList()
    } catch (e) {
      console.error(e)
    } finally {
      subscribeLoading.value = false
    }
  }

  // ---------- 进度 ----------

  const progressOpen = ref(false)
  const progressLoading = ref(false)
  const progress = ref<any>(null)

  const showProgress = async (row: any) => {
    progressOpen.value = true
    progressLoading.value = true
    progress.value = null
    try {
      progress.value = await getSubscriptionProgressApi(row.id)
    } catch (e) {
      console.error(e)
    } finally {
      progressLoading.value = false
    }
  }

  // ---------- 行操作 ----------

  const handleRefresh = async (row: any) => {
    try {
      await refreshSubscriptionApi(row.id)
      ElMessage.success('已与媒体库对账')
      base.getList()
    } catch (e) {
      console.error(e)
    }
  }

  const handlePause = async (row: any) => {
    try {
      await pauseSubscriptionApi(row.id)
      ElMessage.success('已暂停')
      base.getList()
    } catch (e) {
      console.error(e)
    }
  }

  const handleResume = async (row: any) => {
    try {
      await resumeSubscriptionApi(row.id)
      ElMessage.success('已恢复')
      base.getList()
    } catch (e) {
      console.error(e)
    }
  }

  const handleRemove = async (row: any) => {
    try {
      await ElMessageBox.confirm(
        `确认删除订阅「${row.title}」？其集数追踪记录会一并删除。`,
        '警告',
        { type: 'warning' }
      )
      await deletePtSubscriptionApi(row.id)
      ElMessage.success('删除成功')
      base.getList()
    } catch (e) {
      if (e !== 'cancel') console.error(e)
    }
  }

  base.getList()

  return {
    ...base,
    // 建订阅向导
    subscribeOpen, searchLoading, subscribeLoading, searchResults, searchForm,
    picked, pickedSeason, openSubscribeDialog, doSearch, pick, confirmSubscribe,
    // 进度
    progressOpen, progressLoading, progress, showProgress,
    // 行操作
    handleRefresh, handlePause, handleResume, handleRemove
  }
}
