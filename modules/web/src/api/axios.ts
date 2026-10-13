import axios from 'axios'

/** @type {string} localStorage保存自定义阅读http服务接口的键值 */
export const baseURL_localStorage_key = 'remoteUrl'
const SECOND = 1000

const ajax = axios.create({
  baseURL:
    import.meta.env.VITE_API ||
    localStorage.getItem(baseURL_localStorage_key) ||
    location.origin + '/api',
  timeout: 120 * SECOND,
})

// ===== 认证令牌管理 =====
const TOKEN_KEY = 'legado_access_token'
const REFRESH_KEY = 'legado_refresh_token'

export const tokenStore = {
  get access() {
    return localStorage.getItem(TOKEN_KEY) || ''
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY) || ''
  },
  set(access: string, refresh: string) {
    localStorage.setItem(TOKEN_KEY, access)
    localStorage.setItem(REFRESH_KEY, refresh)
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
  get isLogin() {
    return !!localStorage.getItem(REFRESH_KEY)
  },
}

// ===== 401 静默刷新（排队去重） =====
let refreshing: Promise<boolean> | null = null

async function doRefresh(): Promise<boolean> {
  const refresh = tokenStore.refresh
  if (!refresh) return false
  try {
    const { data } = await axios.post(
      `${ajax.defaults.baseURL}/auth/refresh`,
      { refresh_token: refresh },
      { timeout: 10 * SECOND },
    )
    if (data?.isSuccess) {
      tokenStore.set(data.data.access_token, data.data.refresh_token)
      return true
    }
  } catch {
    /* fallthrough */
  }
  tokenStore.clear()
  return false
}

export function refreshQueue(): Promise<boolean> {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

ajax.interceptors.request.use(config => {
  const token = tokenStore.access
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

ajax.interceptors.response.use(
  response => response,
  async error => {
    const { response, config } = error
    const url = config?.url || ''
    const isAuthEndpoint =
      url.includes('/auth/login') ||
      url.includes('/auth/2fa/verify') ||
      url.includes('/auth/refresh') ||
      url.includes('/auth/logout')

    if (response?.status === 401 && config && !config.__retried && !isAuthEndpoint) {
      config.__retried = true
      const ok = await refreshQueue()
      if (ok) {
        config.headers.Authorization = `Bearer ${tokenStore.access}`
        return ajax(config)
      }
      // 刷新失败，跳转登录
      if (!location.hash.startsWith('#/login')) {
        location.hash = '#/login'
      }
    }
    return Promise.reject(error)
  },
)

export default ajax
