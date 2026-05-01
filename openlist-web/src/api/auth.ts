import request from '@/api/request'
import type { LoginRequest, LoginResponse } from '@/types'

export function loginApi(data: LoginRequest) {
  return request.post<any, LoginResponse>('/auth/login', data)
}

export function logoutApi() {
  return request.post('/auth/logout')
}

export function getCaptchaApi(type?: string) {
  return request.get('/auth/captchaImage', { params: { type } })
}

export function getUserInfoApi() {
  return request.get<any, { user: Record<string, unknown>; roles: string[]; permissions: string[] }>('/auth/info')
}

export function getRoutersApi() {
  return request.get<any, Record<string, unknown>[]>('/auth/routers')
}

export function changePasswordApi(data: { oldPassword: string; newPassword: string }) {
  return request.post('/auth/changePassword', data)
}
