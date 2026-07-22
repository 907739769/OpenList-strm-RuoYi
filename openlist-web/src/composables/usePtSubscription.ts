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
  resumeSubscriptionApi,
  searchSupplementApi
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

  const currentSubscription = ref<any>(null)

  const showProgress = async (row: any) => {
    currentSubscription.value = row
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

  // ---------- 搜索补集 ----------

  const searchDialogOpen = ref(false)
  const searchDialogLoading = ref(false)
  const searchDialogKeyword = ref('')
  const searchDialogTarget = ref<{ subId: number; episode: number } | null>(null)

  const pad2 = (n: number) => (n < 10 ? '0' + n : String(n))

  /** 打开整季/整部搜索确认框（订阅详情顶部按钮、列表操作列按钮共用） */
  const openSeasonSearch = (row: any) => {
    const isMovie = row.mediaType === 'MOVIE'
    searchDialogTarget.value = { subId: row.id, episode: isMovie ? 0 : -1 }
    searchDialogKeyword.value = isMovie ? row.title : `${row.title} S${pad2(row.season)}`
    searchDialogOpen.value = true
  }

  /** 打开单集搜索确认框，episode 为具体集号（剧集缺集列表专用） */
  const openEpisodeSearch = (row: any, episode: number) => {
    searchDialogTarget.value = { subId: row.id, episode }
    searchDialogKeyword.value = `${row.title} S${pad2(row.season)}E${pad2(episode)}`
    searchDialogOpen.value = true
  }

  const confirmSearch = async () => {
    if (!searchDialogTarget.value) return
    if (!searchDialogKeyword.value?.trim()) {
      ElMessage.warning('请输入搜索关键词')
      return
    }
    searchDialogLoading.value = true
    try {
      const result = await searchSupplementApi(searchDialogTarget.value.subId, {
        episode: searchDialogTarget.value.episode,
        keyword: searchDialogKeyword.value.trim()
      })
      ElMessage[result.pushed ? 'success' : 'info'](result.pushed ? '已找到并推送下载' : '未搜索到匹配资源')
      searchDialogOpen.value = false
      base.getList()
      if (currentSubscription.value && currentSubscription.value.id === searchDialogTarget.value.subId) {
        progress.value = await getSubscriptionProgressApi(searchDialogTarget.value.subId)
      }
    } catch (e) {
      console.error(e)
    } finally {
      searchDialogLoading.value = false
    }
  }

  const toggleAutoSearch = async (row: any) => {
    try {
      await updatePtSubscriptionApi({ id: row.id, autoSearch: row.autoSearch })
      ElMessage.success(row.autoSearch === '1' ? '已开启自动补搜' : '已关闭自动补搜')
    } catch (e) {
      // 请求失败时把开关状态还原（v-model 已经乐观更新过了）
      row.autoSearch = row.autoSearch === '1' ? '0' : '1'
      console.error(e)
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
    progressOpen, progressLoading, progress, currentSubscription, showProgress,
    // 搜索补集
    searchDialogOpen, searchDialogLoading, searchDialogKeyword,
    openSeasonSearch, openEpisodeSearch, confirmSearch, toggleAutoSearch,
    // 行操作
    handleRefresh, handlePause, handleResume, handleRemove
  }
}
