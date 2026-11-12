import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { ApiResult } from './types'

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器: 注入 Token
service.interceptors.request.use(config => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 响应拦截器: 统一错误处理
service.interceptors.response.use(
  response => {
    // 对于 blob 或 arraybuffer 类型的响应，直接返回原始响应
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response
    }
    const res = response.data as ApiResult
    const silent = !!(response as any).config?.params?._silent ||
                   !!(response as any).config?.silent ||
                   !!(response as any).config?.headers?.['x-silent']
    if (res.code === 200) return res.data
    if (!silent) ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  error => {
    const cfg = (error?.config || {}) as any
    const silent = !!cfg.params?._silent || !!cfg.silent || !!cfg.headers?.['x-silent']

    // 401 处理: token 缺失/过期时统一提示并跳转到登录页
    // 若调用方显式传入 _forceRedirect=false 或 x-force-redirect=false 则不自动跳转
    if (error.response?.status === 401) {
      const forceRedirect = !(
        cfg.params?._forceRedirect === false ||
        cfg.headers?.['x-force-redirect'] === false
      )
      if (forceRedirect) {
        const userStore = useUserStore()
        userStore.logout()
        ElMessage.warning('登录已失效，请重新登录')
        window.location.href = '/login'
      }
      error.code = 401
    }

    if (!silent) ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

// 命名导出: 提供 get/post/put/delete 方法, 兼容 API 模块的 `import { request }` 用法
// - get(url, params): 第二个参数作为 query params
// - post(url, data, config?): 第二个参数作为 request body, 第三个参数为 axios config (可含 params)
// - put(url, data, config?):  第二个参数作为 request body, 第三个参数为 axios config (可含 params)
// - delete(url):     无 body
// 注意: 响应拦截器已将 AxiosResponse 解包为 data, 故泛型 T 即业务返回类型。
export const request = {
  get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    return service.get(url, { params }) as unknown as Promise<T>
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config) as unknown as Promise<T>
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config) as unknown as Promise<T>
  },
  delete<T>(url: string): Promise<T> {
    return service.delete(url) as unknown as Promise<T>
  }
}

export default service
// 兼容 `import { service }` 的命名导出 (用于 blob/自定义 config 请求)
export { service }
