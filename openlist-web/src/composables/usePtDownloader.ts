import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useTaskList } from './useTaskList'
import {
  getPtDownloaderListApi,
  addPtDownloaderApi,
  updatePtDownloaderApi,
  deletePtDownloaderApi,
  testPtDownloaderApi,
  validateSavePathApi
} from '@/api/openlist/ptDownloader'
import type { SearchParams } from '@/types'

interface PtDownloaderQuery extends SearchParams {
  name?: string
  enabled?: string
}

/**
 * PT 下载器配置 composable
 */
export function usePtDownloader() {
  const base = useTaskList<PtDownloaderQuery>({
    listApi: getPtDownloaderListApi,
    addApi: addPtDownloaderApi,
    updateApi: updatePtDownloaderApi,
    deleteApi: deletePtDownloaderApi,
    idField: 'id',
    initForm: () => ({
      id: undefined,
      name: undefined,
      type: 'QBITTORRENT',
      host: undefined,
      port: 8080,
      useHttps: '0',
      username: undefined,
      password: undefined,
      savePath: undefined,
      tag: 'osr-pt',
      enabled: '1'
    }),
    rules: {
      name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
      host: [{ required: true, message: '主机不能为空', trigger: 'blur' }],
      port: [
        { required: true, message: '端口不能为空', trigger: 'blur' },
        { type: 'number', min: 1, max: 65535, message: '端口须在 1-65535 之间', trigger: 'blur' }
      ],
      savePath: [{ required: true, message: '保存路径不能为空', trigger: 'blur' }],
      tag: [{ required: true, message: '标签不能为空', trigger: 'blur' }]
    },
    defaultQuery: {
      name: undefined,
      enabled: undefined
    }
  })

  const testLoading = ref(false)
  /** 保存路径校验警告文案，空串表示无警告 */
  const savePathWarning = ref('')

  const handleTest = async () => {
    if (!base.form.value.host || !base.form.value.port) {
      ElMessage.warning('请先填写主机与端口')
      return
    }
    testLoading.value = true
    try {
      await testPtDownloaderApi(base.form.value)
      ElMessage.success('连接成功')
    } catch (e) {
      // 失败提示已由 axios 拦截器统一弹出，见 usePtIndexer.ts 中的说明，这里不再重复弹窗
      console.error('[PT下载器] 测试连接失败:', e)
    } finally {
      testLoading.value = false
    }
  }

  /**
   * 保存路径失焦时校验。不阻断保存，仅提示——
   * 路径不在文件同步任务的监听目录下时，下载完成的文件不会被自动上传网盘。
   */
  const handleSavePathBlur = async () => {
    if (!base.form.value.savePath) {
      savePathWarning.value = ''
      return
    }
    try {
      savePathWarning.value = (await validateSavePathApi(base.form.value)) || ''
    } catch {
      savePathWarning.value = ''
    }
  }

  base.getList()

  return { ...base, testLoading, handleTest, savePathWarning, handleSavePathBlur }
}
