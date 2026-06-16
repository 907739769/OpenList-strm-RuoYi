import request from '@/api/request'
import type { PageResult, SearchParams } from '@/types'

export function getRenameDetailListApi(params: SearchParams) {
  return request.get<any, PageResult<any>>('/openliststrm/rename-details', { params })
}

export function batchDeleteRenameDetailApi(recordIds: number[]) {
  return request.post('/openliststrm/rename-details/batchDelete', null, { params: { ids: recordIds.join(',') } })
}

export function executeRenameDetailApi(detailIds: number[], title?: string, year?: string) {
  const params: Record<string, any> = { ids: detailIds.join(',') }
  if (title) params.title = title
  if (year) params.year = year
  return request.post('/openliststrm/rename-details/execute', null, { params })
}
