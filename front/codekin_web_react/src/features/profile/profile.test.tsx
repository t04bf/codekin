import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ProfileEditor, type EditSection } from './ProfileEditor'
import { profileFixture } from '@/test/fixtures'
import { userApi } from '@/api/private'
import { useAuthStore } from '@/features/auth/session'
function mount(section: EditSection) {
  const client = new QueryClient()
  client.setQueryData(['me', profileFixture.userId], profileFixture)
  const close = vi.fn()
  render(<QueryClientProvider client={client}><ProfileEditor me={profileFixture} section={section} onClose={close}/></QueryClientProvider>)
  return { client, close }
}
beforeEach(() => useAuthStore.setState({ userId: profileFixture.userId }))
afterEach(() => vi.restoreAllMocks())
describe('profile field migration', () => {
  it('preserves other fields when changing a name and clears optional blank values explicitly', async () => {
    const save = vi.spyOn(userApi, 'updateProfile').mockResolvedValue(null as never)
    const { close } = mount('name')
    const user = userEvent.setup()
    await user.clear(screen.getByLabelText('真实姓名'))
    await user.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(close).toHaveBeenCalledTimes(1))
    expect(save).toHaveBeenCalledWith({ nickname: '测试用户', bio: '简介', genderCode: 'FEMALE', birthDate: '2000-01-01', realName: null })
  })
  it('rejects blank nicknames without submitting', async () => {
    const save = vi.spyOn(userApi, 'updateProfile').mockResolvedValue(null as never)
    const first = mount('basic')
    const user = userEvent.setup()
    await user.clear(screen.getByLabelText('昵称'))
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('请输入昵称')).toBeVisible()
    expect(save).not.toHaveBeenCalled()
    first.client.clear()
  })
  it('rejects future dates without submitting', async () => {
    const save = vi.spyOn(userApi, 'updateProfile').mockResolvedValue(null as never)
    mount('birthday')
    fireEvent.change(screen.getByLabelText('生日'), { target: { value: '2999-01-01' } })
    await userEvent.setup().click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('不能晚于今天')
    expect(save).not.toHaveBeenCalled()
  })
  it('keeps the editor open on backend failure', async () => {
    vi.spyOn(userApi, 'updateProfile').mockRejectedValue(new Error('更新失败'))
    const { close } = mount('basic')
    await userEvent.setup().click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(screen.getByRole('button', { name: '保存' })).toBeEnabled())
    expect(close).not.toHaveBeenCalled()
  })
})
