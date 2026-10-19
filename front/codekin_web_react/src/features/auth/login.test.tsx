import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import LoginPage from './LoginPage'
import { RequireAuth } from './RequireAuth'
import { useAuthStore } from './session'
import { authApi } from '@/api/auth'
import { publicHttp } from '@/api/http'

vi.mock('./CaptchaDialog', () => ({ CaptchaDialog: ({ open, onOpenChange, onVerified }: { open: boolean; onOpenChange: (open: boolean) => void; onVerified: (id: string) => void }) => open ? <button onClick={() => { onOpenChange(false); onVerified('verified-captcha') }}>测试验证通过</button> : null }))
const tokens = { userId: 'test-user', accessToken: 'test-access', refreshToken: 'test-refresh', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600_000).toISOString(), refreshExpiresAt: '', sessionExpiresAt: '' }
function mount(path = '/login') {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/login" element={<LoginPage/>}/><Route element={<RequireAuth/>}><Route path="/profile" element={<p>受保护主页</p>}/><Route path="/settings/profile" element={<p>受保护设置</p>}/></Route>
  </Routes></MemoryRouter></QueryClientProvider>)
}
beforeEach(() => useAuthStore.getState().clear())
afterEach(() => vi.restoreAllMocks())

describe('login and route behavior', () => {
  it('validates empty fields without sending a request, then preserves password and redirects after login', async () => {
    const login = vi.spyOn(authApi, 'passwordLogin').mockResolvedValue(tokens)
    const user = userEvent.setup()
    mount('/login?redirect=%2Fsettings%2Fprofile')
    await user.click(screen.getByRole('button', { name: '登录并继续' }))
    expect(await screen.findByText('请输入密码')).toBeVisible()
    expect(login).not.toHaveBeenCalled()
    await user.type(screen.getByLabelText('邮箱或手机号'), 'dev@example.com')
    await user.type(screen.getByLabelText('密码'), ' secret password ')
    await user.click(screen.getByRole('button', { name: '登录并继续' }))
    expect(await screen.findByText('受保护设置')).toBeVisible()
    expect(login).toHaveBeenCalledWith('dev@example.com', ' secret password ')
  })
  it('sends a normalized email only after captcha succeeds and keeps recipient cooldown across login modes', async () => {
    const send = vi.spyOn(authApi, 'sendEmailCode').mockResolvedValue(null as never)
    const user = userEvent.setup()
    mount()
    await user.click(screen.getByRole('button', { name: '邮箱验证码登录' }))
    await user.type(screen.getByLabelText('邮箱地址'), 'DEV@EXAMPLE.COM')
    await user.click(screen.getByRole('button', { name: '获取验证码' }))
    expect(send).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: '测试验证通过' }))
    await waitFor(() => expect(send).toHaveBeenCalledWith('dev@example.com', 'verified-captcha'))
    expect(screen.getByRole('button', { name: /后重发/ })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: '手机验证码登录' }))
    await user.click(screen.getByRole('button', { name: '邮箱验证码登录' }))
    await user.type(screen.getByLabelText('邮箱地址'), 'dev@example.com')
    expect(screen.getByRole('button', { name: /后重发/ })).toBeDisabled()
  })
  it('validates mainland phone and six digit code before SMS login', async () => {
    const login = vi.spyOn(authApi, 'smsLogin').mockResolvedValue(tokens)
    const user = userEvent.setup()
    mount()
    await user.click(screen.getByRole('button', { name: '手机验证码登录' }))
    await user.type(screen.getByLabelText('手机号码'), '13800138000')
    await user.type(screen.getByLabelText('验证码'), '123')
    await user.click(screen.getByRole('button', { name: '登录并继续' }))
    expect(await screen.findByText('请输入六位数字验证码')).toBeVisible()
    expect(login).not.toHaveBeenCalled()
    await user.type(screen.getByLabelText('验证码'), '456')
    await user.click(screen.getByRole('button', { name: '登录并继续' }))
    expect(await screen.findByText('受保护主页')).toBeVisible()
    expect(login).toHaveBeenCalledWith('13800138000', '123456')
  })
  it('redirects anonymous access to login and restores a session before showing private content', async () => {
    const first = mount('/settings/profile')
    expect(await screen.findByRole('heading', { name: '欢迎回来' })).toBeVisible()
    first.unmount()
    useAuthStore.setState({ refreshToken: 'saved-refresh' })
    vi.spyOn(publicHttp, 'post').mockResolvedValue({ data: { code: '0', data: tokens } })
    mount('/settings/profile')
    expect(await screen.findByText('受保护设置')).toBeVisible()
  })
})
