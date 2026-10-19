import { ref } from 'vue'
import { defineStore } from 'pinia'
import { useStorage } from '@vueuse/core'
import { ApiBusinessError, publicHttp, readData, type ApiEnvelope } from '@/api/http'
import type { LoginTokens } from '@/api/auth'

/** Access Token 只驻留内存；一次性 Refresh Token 按当前后端 JSON 契约存储。 */
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref('')
  const accessExpiresAt = ref('')
  const userId = ref('')
  const refreshToken = useStorage('codekin.auth.refreshToken', '')
  let refreshInFlight: Promise<boolean> | null = null

  function accept(tokens: LoginTokens) {
    userId.value = tokens.userId
    accessToken.value = tokens.accessToken
    accessExpiresAt.value = tokens.expiresAt
    refreshToken.value = tokens.refreshToken
  }

  function clear() {
    accessToken.value = ''
    accessExpiresAt.value = ''
    refreshToken.value = ''
    userId.value = ''
  }

  /** 提前五分钟刷新；并发请求共用同一个 Promise，避免旧 Refresh Token 重放。 */
  async function ensureAccessToken(minRemainingMs = 5 * 60_000): Promise<boolean> {
    const remaining = Date.parse(accessExpiresAt.value) - Date.now()
    if (accessToken.value && remaining > minRemainingMs) return true
    if (!refreshToken.value) return Boolean(accessToken.value && remaining > 0)

    if (!refreshInFlight) {
      const currentRefreshToken = refreshToken.value
      refreshInFlight = (async () => {
        try {
          const response = await publicHttp.post<ApiEnvelope<LoginTokens>>('/api/auth/refresh', {
            refreshToken: currentRefreshToken,
          })
          const tokens = readData(response)
          // 退出或重新登录期间到达的旧刷新结果不能恢复已撤销的会话。
          if (refreshToken.value !== currentRefreshToken) return Boolean(accessToken.value)
          accept(tokens)
          return true
        } catch (error) {
          if (error instanceof ApiBusinessError && refreshToken.value === currentRefreshToken) clear()
          // 网络暂时失败时，可继续使用尚未过期的 Access Token。
          return Boolean(accessToken.value && Date.parse(accessExpiresAt.value) > Date.now())
        }
      })().finally(() => { refreshInFlight = null })
    }
    return refreshInFlight
  }

  return { accessToken, accessExpiresAt, refreshToken, userId, accept, clear, ensureAccessToken }
})
