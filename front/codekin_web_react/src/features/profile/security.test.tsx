import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { useState } from 'react'
import { ContactBindDialog } from './ContactBindDialog'
import { PasswordDialog } from './PasswordDialog'
import { profileFixture } from '@/test/fixtures'
import { userApi } from '@/api/private'
import { useAuthStore } from '@/features/auth/session'

beforeEach(() => useAuthStore.setState({ userId: profileFixture.userId, refreshToken: 'test-refresh', accessToken: 'test-access' }))
afterEach(() => vi.restoreAllMocks())
function mountContact() {
  function Harness() { const [open, setOpen] = useState(true); return <><button onClick={() => setOpen(true)}>重新打开</button><ContactBindDialog open={open} kind="email" me={profileFixture} onClose={() => setOpen(false)}/></> }
  render(<QueryClientProvider client={new QueryClient()}><Harness/></QueryClientProvider>)
}
function mountPassword(hasPassword = true) {
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter initialEntries={['/settings/profile']}><Routes><Route path="/settings/profile" element={<PasswordDialog me={{ ...profileFixture, hasPassword }} onClose={vi.fn()}/>}/><Route path="/login" element={<p>重新登录页</p>}/></Routes></MemoryRouter></QueryClientProvider>)
}
describe('account security migration', () => {
  it('keeps send cooldown after a dialog closes, resets drafts, and clears a code on recipient change', async () => {
    const send = vi.spyOn(userApi, 'sendBindEmailCode').mockResolvedValue(null as never)
    const user = userEvent.setup()
    mountContact()
    await user.type(screen.getByLabelText('新邮箱'), 'NEW@EXAMPLE.COM')
    await user.click(screen.getByRole('button', { name: '获取验证码' }))
    await waitFor(() => expect(send).toHaveBeenCalledWith('new@example.com'))
    await user.type(screen.getByLabelText('验证码'), '123456')
    await user.click(screen.getByRole('button', { name: '取消' }))
    await user.click(screen.getByRole('button', { name: '重新打开' }))
    expect(screen.getByLabelText('新邮箱')).toHaveValue('')
    await user.type(screen.getByLabelText('新邮箱'), 'new@example.com')
    expect(screen.getByRole('button', { name: /秒后重试/ })).toBeDisabled()
    await user.type(screen.getByLabelText('验证码'), '123456')
    await user.type(screen.getByLabelText('新邮箱'), '.test')
    expect(screen.getByLabelText('验证码')).toHaveValue('')
  })
  it('binds a normalized recipient only with a six-digit code', async () => {
    const bind = vi.spyOn(userApi, 'bindEmail').mockResolvedValue(null as never)
    const user = userEvent.setup()
    mountContact()
    await user.type(screen.getByLabelText('新邮箱'), 'NEW@EXAMPLE.COM')
    await user.type(screen.getByLabelText('验证码'), '123456')
    await user.click(screen.getByRole('button', { name: '确认换绑' }))
    await waitFor(() => expect(bind).toHaveBeenCalledWith('new@example.com', '123456'))
  })
  it('rejects a password whose UTF-8 bytes exceed 72 even when character length is valid', async () => {
    const save = vi.spyOn(userApi, 'setPassword').mockResolvedValue(null as never)
    const user = userEvent.setup()
    mountPassword(false)
    await user.type(screen.getByLabelText('新密码', { exact: true }), '中'.repeat(25))
    await user.type(screen.getByLabelText('确认新密码'), '中'.repeat(25))
    await user.click(screen.getByRole('button', { name: '保存并重新登录' }))
    expect(await screen.findByText('密码不能超过 72 个 UTF-8 字节')).toBeVisible()
    expect(save).not.toHaveBeenCalled()
  })
  it('requires an existing password and clears session after saving', async () => {
    const save = vi.spyOn(userApi, 'setPassword').mockResolvedValue(null as never)
    const user = userEvent.setup()
    mountPassword()
    await user.type(screen.getByLabelText('新密码', { exact: true }), 'new-password')
    await user.type(screen.getByLabelText('确认新密码'), 'new-password')
    await user.click(screen.getByRole('button', { name: '保存并重新登录' }))
    expect(await screen.findByText('请输入当前密码')).toBeVisible()
    expect(save).not.toHaveBeenCalled()
    await user.type(screen.getByLabelText('当前密码'), 'old-password')
    await user.click(screen.getByRole('button', { name: '保存并重新登录' }))
    expect(await screen.findByText('重新登录页')).toBeVisible()
    expect(save).toHaveBeenCalledWith('new-password', 'old-password')
    expect(useAuthStore.getState().refreshToken).toBe('')
  })
})
