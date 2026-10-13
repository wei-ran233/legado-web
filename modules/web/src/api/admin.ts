import ajax from './axios'

/**
 * 管理员 API
 */

export interface AdminSourceRow {
  bookSourceUrl: string
  bookSourceName: string
  bookSourceGroup: string | null
  enabled: boolean
  customOrder: number
  weight: number
  respondTime: number
  bookSourceType: number
  availabilityScore?: number
  successCount?: number
  failCount?: number
  hasLatestChapter?: boolean
  isTextClean?: boolean
  canFetchArticle?: boolean
  lastCheckedAt?: number
}

export interface AdminUserRow {
  id: number
  username: string
  nickname?: string
  role: string
  totpEnabled: boolean
  disabled: boolean
  createdAt: number
}

export const listAdminSources = async (enabledOnly = false): Promise<AdminSourceRow[]> => {
  const { data } = await ajax.get('/admin/sources', { params: { enabled: enabledOnly } })
  return data.data ?? []
}

export const importSources = async (text: string) => {
  const { data } = await ajax.post('/admin/sources/import', text, {
    headers: { 'Content-Type': 'application/json' },
  })
  return data
}

export const setSourcesEnabled = async (urls: string[], enabled: boolean) => {
  const url = enabled ? '/admin/sources/enable' : '/admin/sources/disable'
  const { data } = await ajax.post(url, { urls })
  return data
}

export const deleteSources = async (urls: string[]) => {
  const { data } = await ajax.post('/admin/sources/delete', { urls })
  return data
}

export const updateSourceType = async (urls: string[], type: number) => {
  const { data } = await ajax.post('/admin/sources/type', { urls, type })
  return data
}

export const listUsers = async (): Promise<AdminUserRow[]> => {
  const { data } = await ajax.get('/admin/users')
  return data.data ?? []
}

export const createUser = async (username: string, password: string, role: string) => {
  const { data } = await ajax.post('/admin/users', { username, password, role })
  return data
}

export const userAction = async (id: number, action: string, extra: Record<string, unknown> = {}) => {
  const { data } = await ajax.patch(`/admin/users/${id}`, { action, ...extra })
  return data
}

export const deleteUser = async (id: number) => {
  const { data } = await ajax.delete(`/admin/users/${id}`)
  return data
}

export const getStats = async () => {
  const { data } = await ajax.get('/admin/stats')
  return data.data as { users: number; sources: number; sourcesEnabled: number; books: number }
}
