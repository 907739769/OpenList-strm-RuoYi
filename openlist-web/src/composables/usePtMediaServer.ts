import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useTaskList } from './useTaskList'
import {
  getPtMediaServerListApi,
  addPtMediaServerApi,
  updatePtMediaServerApi,
  deletePtMediaServerApi,
  testPtMediaServerApi
} from '@/api/openlist/ptMediaServer'
import type { SearchParams } from '@/types'

interface PtMediaServerQuery extends SearchParams {
  name?: string
  enabled?: string
}

/**
 * PT 媒体服务器（Emby/Jellyfin）配置 composable
 */
export function usePtMediaServer() {
  const base = useTaskList<PtMediaServerQuery>({
    listApi: getPtMediaServerListApi,
    addApi: addPtMediaServerApi,
    updateApi: updatePtMediaServerApi,
    deleteApi: deletePtMediaServerApi,
    idField: 'id',
    initForm: () => ({
      id: undefined,
      name: undefined,
      type: 'EMBY',
      url: undefined,
      apiKey: undefined,
      userId: undefined,
      enabled: '1'
    }),
    rules: {
      name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
      url: [
        { required: true, message: '服务器地址不能为空', trigger: 'blur' },
        {
          pattern: /^https?:\/\//,
          message: '地址须以 http:// 或 https:// 开头',
          trigger: 'blur'
        }
      ],
      apiKey: [{ required: true, message: 'API Key 不能为空', trigger: 'blur' }]
    },
    defaultQuery: {
      name: undefined,
      enabled: undefined
    }
  })

  const testLoading = ref(false)

  const handleTest = async () => {
    if (!base.form.value.url || !base.form.value.apiKey) {
      ElMessage.warning('请先填写服务器地址与 API Key')
      return
    }
    testLoading.value = true
    try {
      await testPtMediaServerApi(base.form.value)
      ElMessage.success('连接成功')
    } catch (e) {
      // 失败提示已由 axios 拦截器统一弹出，见 usePtIndexer.ts 中的说明，这里不再重复弹窗
      console.error('[PT媒体服务器] 测试连接失败:', e)
    } finally {
      testLoading.value = false
    }
  }

  base.getList()

  return { ...base, testLoading, handleTest }
}
