import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useTaskList } from './useTaskList'
import {
  getPtIndexerListApi,
  addPtIndexerApi,
  updatePtIndexerApi,
  deletePtIndexerApi,
  testPtIndexerApi
} from '@/api/openlist/ptIndexer'
import type { SearchParams } from '@/types'

interface PtIndexerQuery extends SearchParams {
  name?: string
  enabled?: string
}

/**
 * PT Torznab 索引器配置 composable
 */
export function usePtIndexer() {
  const base = useTaskList<PtIndexerQuery>({
    listApi: getPtIndexerListApi,
    addApi: addPtIndexerApi,
    updateApi: updatePtIndexerApi,
    deleteApi: deletePtIndexerApi,
    idField: 'id',
    initForm: () => ({
      id: undefined,
      name: undefined,
      url: undefined,
      apiKey: undefined,
      categories: undefined,
      pollInterval: 600,
      enabled: '1'
    }),
    rules: {
      name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
      url: [
        { required: true, message: '接口地址不能为空', trigger: 'blur' },
        {
          pattern: /^https?:\/\//,
          message: '地址须以 http:// 或 https:// 开头',
          trigger: 'blur'
        }
      ],
      apiKey: [{ required: true, message: 'apikey 不能为空', trigger: 'blur' }],
      pollInterval: [
        { required: true, message: '轮询周期不能为空', trigger: 'blur' },
        { type: 'number', min: 60, message: '轮询周期不得小于 60 秒', trigger: 'blur' }
      ]
    },
    defaultQuery: {
      name: undefined,
      enabled: undefined
    }
  })

  const testLoading = ref(false)

  const handleTest = async () => {
    if (!base.form.value.url || !base.form.value.apiKey) {
      ElMessage.warning('请先填写接口地址与 apikey')
      return
    }
    testLoading.value = true
    try {
      await testPtIndexerApi(base.form.value)
      ElMessage.success('连接成功')
    } catch (e) {
      // 失败提示已由 axios 拦截器统一弹出（request.ts 的响应拦截器无论业务错误
      // 还是网络错误都会 ElMessage.error 具体原因后再 reject），这里不再重复弹窗，
      // 否则同一条错误信息会被展示两次
      console.error('[PT索引器] 测试连接失败:', e)
    } finally {
      testLoading.value = false
    }
  }

  base.getList()

  return { ...base, testLoading, handleTest }
}
