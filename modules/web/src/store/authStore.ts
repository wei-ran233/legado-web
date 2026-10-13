import { defineStore } from 'pinia'
import { getMe, logout as apiLogout, type MeInfo } from '@/api/auth'
import { tokenStore } from '@/api/axios'

export const useAuthStore = defineStore('auth', {
  state: () => {
    return {
      me: null as MeInfo | null,
      loading: false,
    }
  },
  getters: {
    isLogin: () => tokenStore.isLogin,
    isAdmin: state => state.me?.role === 'ADMIN',
    username: state => state.me?.username ?? '',
    nickname: state => state.me?.nickname ?? '',
    participateRanking: state => state.me?.participate_ranking !== false,
    displayName: state => {
      const nick = state.me?.nickname?.trim()
      return nick ? nick : (state.me?.username ?? '')
    },
  },
  actions: {
    setNickname(nick: string) {
      if (this.me) {
        this.me.nickname = nick
      }
    },
    setParticipateRanking(v: boolean) {
      if (this.me) {
        this.me.participate_ranking = v
      }
    },
    setUsername(newUsername: string) {
      if (this.me) {
        this.me.username = newUsername
      }
    },
    async fetchMe() {
      if (!tokenStore.isLogin) return null
      this.loading = true
      try {
        this.me = await getMe()
        return this.me
      } catch {
        this.me = null
        return null
      } finally {
        this.loading = false
      }
    },
    async logout() {
      await apiLogout()
      this.me = null
      location.hash = '#/login'
    },
  },
})
