import ajax from './axios'
import { tokenStore } from './axios'

/**
 * 服务器端认证与个人账户 API
 */

export interface LoginPayload {
  status: 'OK' | '2FA_REQUIRED'
  access_token?: string
  refresh_token?: string
  tmp_token?: string
  role?: string
  username?: string
}

export interface MeInfo {
  id: number
  username: string
  nickname?: string
  participate_ranking?: boolean
  role: 'ADMIN' | 'USER'
  totp_enabled: boolean
  recovery_codes_left: number
  must_change_password: boolean
}

export interface SessionInfo {
  id: string
  device: string
  ip: string
  createdAt: number
  lastUsedAt: number
  expiresAt: number
}

/** 用户名密码登录 */
export const login = async (username: string, password: string) => {
  const { data } = await ajax.post<{ isSuccess: boolean; errorMsg?: string; data: LoginPayload }>(
    '/auth/login',
    { username, password },
  )
  if (data.isSuccess && data.data.status === 'OK' && data.data.access_token) {
    tokenStore.set(data.data.access_token, data.data.refresh_token!)
  }
  return data
}

/** 2FA 动态码/恢复码验证 */
export const verify2fa = async (tmp_token: string, code: string) => {
  const { data } = await ajax.post<{ isSuccess: boolean; errorMsg?: string; data: LoginPayload }>(
    '/auth/2fa/verify',
    { tmp_token, code },
  )
  if (data.isSuccess && data.data.access_token) {
    tokenStore.set(data.data.access_token, data.data.refresh_token!)
  }
  return data
}

export const logout = async () => {
  try {
    await ajax.post('/auth/logout', { refresh_token: tokenStore.refresh })
  } finally {
    tokenStore.clear()
  }
}

export const getMe = async (): Promise<MeInfo> => {
  const { data } = await ajax.get('/me')
  return data.data
}

export const changePassword = async (old_password: string, new_password: string) => {
  const { data } = await ajax.patch<{ isSuccess: boolean; errorMsg?: string }>('/me/password', {
    old_password,
    new_password,
  })
  return data
}

/** 修改展示昵称或榜单参与设置 */
export const updateProfile = async (
  payload: string | { nickname?: string; participate_ranking?: boolean },
) => {
  const body = typeof payload === 'string' ? { nickname: payload } : payload
  const { data } = await ajax.patch<{
    isSuccess: boolean
    errorMsg?: string
    data?: { nickname: string; participate_ranking: boolean }
  }>('/me/profile', body)
  return data
}

/** 修改登录用户名（唯一账号） */
export const updateUsername = async (new_username: string, password: string) => {
  const { data } = await ajax.patch<{
    isSuccess: boolean
    errorMsg?: string
    data?: { username: string; access_token: string }
  }>('/me/username', {
    new_username,
    password,
  })
  if (data.isSuccess && data.data?.access_token) {
    // 保持现有的 refresh_token，更新 access_token 避免退出登录
    tokenStore.set(data.data.access_token, tokenStore.refresh || '')
  }
  return data
}

// ===== 2FA =====
export const setup2fa = async () => {
  const { data } = await ajax.post('/me/2fa/setup')
  return data.data as { secret: string; otpauth_uri: string }
}

export const enable2fa = async (secret: string, code: string) => {
  const { data } = await ajax.post('/me/2fa/enable', { secret, code })
  return data.data as { recovery_codes: string[] }
}

export const disable2fa = async (password: string) => {
  const { data } = await ajax.post<{ isSuccess: boolean; errorMsg?: string }>('/me/2fa/disable', {
    password,
  })
  return data
}

// ===== 会话 =====
export const listSessions = async (): Promise<SessionInfo[]> => {
  const { data } = await ajax.get('/me/sessions')
  return data.data
}

export const revokeSession = async (id: string) => {
  await ajax.delete(`/me/sessions/${id}`)
}
