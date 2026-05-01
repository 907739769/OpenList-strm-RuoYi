import request from '@/api/request'

export function getDashboardStatsApi() {
  return request.get('/openliststrm/dashboard/stats')
}

export function getCopyStatsApi(range: string) {
  return request.post('/openliststrm/dashboard/copy/stats', null, { params: { range } })
}

export function getStrmStatsApi(range: string) {
  return request.post('/openliststrm/dashboard/strm/stats', null, { params: { range } })
}

export function getRenameStatsApi(range: string) {
  return request.post('/openliststrm/dashboard/renameDetail/stats', null, { params: { range } })
}
