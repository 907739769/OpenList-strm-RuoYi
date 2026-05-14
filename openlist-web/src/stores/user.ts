import { defineStore } from 'pinia'
import { ref } from 'vue'
import Cookies from 'js-cookie'
import type { UserInfo, LoginRequest, LoginResponse } from '@/types'
import { loginApi, logoutApi, getUserInfoApi, getRoutersApi } from '@/api/auth'

export interface MenuRoute {
  path: string
  name?: string
  component?: string
  hidden?: boolean
  redirect?: string
  meta?: {
    title?: string
    icon?: string
    alwaysShow?: boolean
  }
  children?: MenuRoute[]
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(Cookies.get('token') || '')
  const userInfo = ref<UserInfo | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const routes = ref<MenuRoute[]>([])

  const setToken = (newToken: string) => {
    token.value = newToken
    Cookies.set('token', newToken, { expires: 7 })
  }

  const clearToken = () => {
    token.value = ''
    userInfo.value = null
    roles.value = []
    permissions.value = []
    routes.value = []
    Cookies.remove('token')
  }

  const login = async (loginForm: LoginRequest) => {
    const data = await loginApi(loginForm) as LoginResponse
    setToken(data.token)
    userInfo.value = {
      userId: data.userId,
      loginName: data.loginName,
      userName: data.userName,
      roles: [],
      permissions: []
    }
    return data
  }

  const logout = async () => {
    try {
      await logoutApi()
    } finally {
      clearToken()
    }
  }

  const getUserInfo = async () => {
    const data = await getUserInfoApi()
    userInfo.value = {
      userId: (data.user as any).userId,
      loginName: (data.user as any).loginName,
      userName: (data.user as any).userName,
      roles: data.roles,
      permissions: data.permissions
    }
    roles.value = data.roles
    permissions.value = data.permissions
    return userInfo.value
  }

  const getRouters = async () => {
    const data = await getRoutersApi()
    routes.value = data as unknown as MenuRoute[]
    return routes.value
  }

  return { token, userInfo, roles, permissions, routes, setToken, clearToken, login, logout, getUserInfo, getRouters }
})
