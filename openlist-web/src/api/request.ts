import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result, LoginResponse } from '@/types'
import Cookies from 'js-cookie'
import { useUserStore } from '@/stores/user'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '/api',
  timeout: 15000
})

let isRefreshing = false
let retryQueue: Array<(token: string) => void> = []

const subscribeRequest = (callback: (token: string) => void) => {
  retryQueue.push(callback)
}

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = Cookies.get('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  async (response: AxiosResponse<Result>) => {
    const { code, message, data } = response.data

    if (code === 200) {
      return data as any
    } else {
      ElMessage.error(message || '请求失败')
      return Promise.reject(new Error(message || '请求失败'))
    }
  },
  async (error) => {
    if (error.response?.status === 401 || error.response?.status === 302) {
      const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

      if (originalRequest._retry) {
        return Promise.reject(error)
      }

      if (isRefreshing) {
        try {
          await new Promise<void>((resolve) => {
            subscribeRequest((token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve()
            })
          })
          return service(originalRequest)
        } catch (refreshError) {
          const userStore = useUserStore()
          userStore.clearToken()
          window.location.href = '/login'
          return Promise.reject(refreshError)
        }
      }

      isRefreshing = true
      originalRequest._retry = true

      try {
        const userStore = useUserStore()
        const refreshTokenValue = sessionStorage.getItem('refreshToken')

        if (!refreshTokenValue) {
          userStore.clearToken()
          window.location.href = '/login'
          return Promise.reject(error)
        }

        const { refreshApi } = await import('@/api/auth')
        const data = await refreshApi(refreshTokenValue) as LoginResponse

        const userStore2 = useUserStore()
        userStore2.setToken(data.token)
        userStore2.setRefreshToken(data.refreshToken)

        retryQueue.forEach(callback => callback(data.token))
        retryQueue = []

        originalRequest.headers.Authorization = `Bearer ${data.token}`
        return service(originalRequest)
      } catch (refreshError) {
        const userStore = useUserStore()
        userStore.clearToken()
        ElMessage.error('登录已过期，请重新登录')
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    } else {
      ElMessage.error(error.message || '网络错误')
      return Promise.reject(error)
    }
  }
)

export default service
