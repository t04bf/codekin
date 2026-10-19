import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from './session'
import { ApiBusinessError, publicHttp, readData } from '@/api/http'
import { privateHttp, userApi } from '@/api/private'
import type { LoginTokens } from '@/api/auth'
import type { AxiosResponse } from 'axios'

const tokens = (refreshToken = 'refresh-one'): LoginTokens => ({
  userId: 'u1', accessToken: 'access-one', refreshToken, tokenType: 'Bearer',
  expiresAt: new Date(Date.now() + 60_000).toISOString(), refreshExpiresAt: '', sessionExpiresAt: '',
})
const response = (data: unknown) => ({ data: { code: '0', message: '', data } }) as AxiosResponse
beforeEach(() => useAuthStore.getState().clear())
afterEach(() => vi.restoreAllMocks())

describe('account contract and session migration', () => {
  it('does not treat HTTP 200 business errors as success', () => {
    expect(() => readData({ data: { code: 'BAD', message: '错误', data: null } } as AxiosResponse)).toThrow('错误')
  })
  it('persists only the refresh token', () => {
    useAuthStore.getState().accept(tokens())
    expect(window.localStorage.getItem('codekin.auth.refreshToken')).toBe('refresh-one')
    expect(JSON.stringify(window.localStorage)).not.toContain('access-one')
    useAuthStore.getState().clear()
    expect(window.localStorage.getItem('codekin.auth.refreshToken')).toBeNull()
  })
  it('coalesces concurrent refreshes for a rotating one-time token', async () => {
    useAuthStore.getState().accept(tokens())
    const post = vi.spyOn(publicHttp, 'post').mockResolvedValue(response(tokens('refresh-two')))
    expect(await Promise.all([useAuthStore.getState().ensureAccessToken(), useAuthStore.getState().ensureAccessToken()])).toEqual([true, true])
    expect(post).toHaveBeenCalledTimes(1)
    expect(useAuthStore.getState().refreshToken).toBe('refresh-two')
  })
  it('does not resurrect a session after logout', async () => {
    let resolve!: (value: AxiosResponse) => void
    vi.spyOn(publicHttp, 'post').mockReturnValue(new Promise(r => { resolve = r }))
    useAuthStore.getState().accept(tokens())
    const pending = useAuthStore.getState().ensureAccessToken()
    useAuthStore.getState().clear()
    resolve(response(tokens('refresh-two')))
    expect(await pending).toBe(false)
    expect(useAuthStore.getState().accessToken).toBe('')
  })
  it('does not overwrite an account that logged in during a refresh', async () => {
    let resolve!: (value: AxiosResponse) => void
    vi.spyOn(publicHttp, 'post').mockReturnValue(new Promise(r => { resolve = r }))
    useAuthStore.getState().accept(tokens())
    const pending = useAuthStore.getState().ensureAccessToken()
    useAuthStore.getState().accept({ ...tokens('account-two'), userId: 'u2' })
    resolve(response(tokens('stale-result')))
    await pending
    expect(useAuthStore.getState().userId).toBe('u2')
    expect(useAuthStore.getState().refreshToken).toBe('account-two')
  })
  it('clears an invalid session but keeps a still-valid access token on network failure', async () => {
    useAuthStore.getState().accept(tokens())
    vi.spyOn(publicHttp, 'post').mockRejectedValueOnce(new Error('offline'))
      .mockRejectedValueOnce(new ApiBusinessError('EXPIRED', '会话失效'))
    expect(await useAuthStore.getState().ensureAccessToken()).toBe(true)
    expect(await useAuthStore.getState().ensureAccessToken()).toBe(false)
    expect(useAuthStore.getState().refreshToken).toBe('')
  })
  it('rejects a profile that omits required hasPassword instead of showing setup controls', async () => {
    useAuthStore.getState().accept({ ...tokens(), expiresAt: new Date(Date.now() + 3600_000).toISOString() })
    const { privateHttp } = await import('@/api/private')
    vi.spyOn(privateHttp, 'get').mockResolvedValue(response({ userId: 'u1' }))
    await expect(userApi.getMe()).rejects.toThrow('个人资料加载失败')
  })
  it('uses the refreshed access token on private requests', async () => {
    useAuthStore.getState().accept(tokens())
    vi.spyOn(publicHttp, 'post').mockResolvedValue(response({ ...tokens('refresh-two'), accessToken: 'access-two', expiresAt: new Date(Date.now() + 3600_000).toISOString() }))
    const request = vi.fn(async config => ({ data: { code: '0', data: {} }, status: 200, statusText: 'OK', headers: {}, config }))
    await privateHttp.get('/test-private', { adapter: request })
    expect(request.mock.calls[0][0].headers.Authorization).toBe('Bearer access-two')
  })
  it('discards cached private profiles when the account changes', async () => {
    const { queryClient } = await import('@/app/query')
    useAuthStore.getState().accept(tokens())
    queryClient.setQueryData(['me', 'u1'], { userId: 'u1', realName: 'private profile' })
    useAuthStore.getState().accept({ ...tokens('account-two'), userId: 'u2' })
    expect(queryClient.getQueryData(['me', 'u1'])).toBeUndefined()
    queryClient.setQueryData(['me', 'u2'], { userId: 'u2' })
    useAuthStore.getState().clear()
    expect(queryClient.getQueryData(['me', 'u2'])).toBeUndefined()
  })
  it('does not send an old account request using a newly logged-in account token', async () => {
    let resolve!: (value: AxiosResponse) => void
    const started = vi.fn()
    vi.spyOn(publicHttp, 'post').mockImplementation(() => { started(); return new Promise(r => { resolve = r }) })
    useAuthStore.getState().accept(tokens())
    const adapter = vi.fn()
    const pending = privateHttp.put('/test-profile', { nickname: 'old account draft' }, { adapter })
    const rejected = expect(pending).rejects.toThrow('账号已变更')
    await vi.waitFor(() => expect(started).toHaveBeenCalled())
    useAuthStore.getState().accept({ ...tokens('account-two'), userId: 'u2', expiresAt: new Date(Date.now() + 3600_000).toISOString() })
    resolve(response(tokens('late-account-one')))
    await rejected
    expect(adapter).not.toHaveBeenCalled()
  })
})
