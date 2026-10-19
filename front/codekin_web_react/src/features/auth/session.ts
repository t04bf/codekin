import { create } from 'zustand'
import axios from 'axios'
import { ApiBusinessError, publicHttp, readData, type ApiEnvelope } from '@/api/http'
import type { LoginTokens } from '@/api/auth'

const refreshKey = 'codekin.auth.refreshToken'
let refreshing: { token: string; promise: Promise<boolean> } | undefined

interface Session {
  userId: string
  accessToken: string
  accessExpiresAt: string
  refreshToken: string
  accept: (tokens: LoginTokens) => void
  clear: () => void
  ensureAccessToken: (minRemainingMs?: number) => Promise<boolean>
}

export const useAuthStore = create<Session>((set, get) => ({
  userId: '', accessToken: '', accessExpiresAt: '',
  refreshToken: window.localStorage.getItem(refreshKey) || '',
  accept(tokens) {
    // Preserve the existing plain-string refresh-token contract. Access tokens stay in memory.
    window.localStorage.setItem(refreshKey, tokens.refreshToken)
    set({ userId: tokens.userId, accessToken: tokens.accessToken,
      accessExpiresAt: tokens.expiresAt, refreshToken: tokens.refreshToken })
  },
  clear() {
    window.localStorage.removeItem(refreshKey)
    set({ userId: '', accessToken: '', accessExpiresAt: '', refreshToken: '' })
  },
  async ensureAccessToken(minRemainingMs = 5 * 60_000) {
    const session = get()
    const remaining = Date.parse(session.accessExpiresAt) - Date.now()
    if (session.accessToken && remaining > minRemainingMs) return true
    if (!session.refreshToken) return Boolean(session.accessToken && remaining > 0)
    if (refreshing?.token === session.refreshToken) return refreshing.promise

    const token = session.refreshToken
    const promise = (async () => {
      try {
        const response = await publicHttp.post<ApiEnvelope<LoginTokens>>('/api/auth/refresh', { refreshToken: token })
        const tokens = readData(response)
        // A late response must never restore a logged-out or replaced account.
        if (get().refreshToken !== token) return Boolean(get().accessToken)
        get().accept(tokens)
        return true
      } catch (error) {
        if (get().refreshToken === token && (error instanceof ApiBusinessError
          || axios.isAxiosError(error) && [401, 403].includes(error.response?.status || 0))) get().clear()
        return Boolean(get().accessToken && Date.parse(get().accessExpiresAt) > Date.now())
      } finally {
        if (refreshing?.token === token) refreshing = undefined
      }
    })()
    refreshing = { token, promise }
    return promise
  },
}))
