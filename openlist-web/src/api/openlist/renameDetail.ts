import request from '@/api/request'
import type { PageResult, SearchParams } from '@/types'

export function getRenameDetailListApi(params: SearchParams) {
  return request.get<any, PageResult<any>>('/openliststrm/rename-details', { params })
}

export function executeRenameDetailApi(detailIds: number[]) {
  return request.post('/openliststrm/rename-details/execute', detailIds)
}
