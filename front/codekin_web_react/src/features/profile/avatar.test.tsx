import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { toast } from 'sonner'
import { AvatarPicker } from './AvatarPicker'
import { profileFixture } from '@/test/fixtures'
import { userApi } from '@/api/private'
import { fileApi } from '@/api/file'
import { useAuthStore } from '@/features/auth/session'
import * as crop from './avatarCrop'
import { useEffect } from 'react'
import type { AvatarCropSelection } from './AvatarCropEditor'

// Image decoding/canvas is verified in the real browser; here isolate upload transitions.
vi.mock('./AvatarCropEditor', () => ({ AvatarCropEditor: ({ onSelection }: { onSelection: (value: AvatarCropSelection) => void }) => {
  useEffect(() => { onSelection({ image: new Image(), area: { x: 10, y: 20, width: 80, height: 80 } }) }, [onSelection])
  return <p>裁剪测试预览</p>
} }))

beforeEach(() => {
  useAuthStore.setState({ userId: profileFixture.userId })
  vi.spyOn(userApi, 'getDefaultAvatars').mockResolvedValue([{ name: '默认一', objectKey: 'default/one.png', url: 'https://example.com/signed-display' }])
  vi.spyOn(fileApi, 'uploadTypes').mockResolvedValue([{ code: 'AVATAR', extensions: ['png'], contentTypes: ['image/png'], maxBytes: 5, maxWidth: 100, maxHeight: 100, maxPixels: 10000 }])
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:local-preview')
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  vi.spyOn(crop, 'createAvatarFile').mockResolvedValue(new File(['cut'], 'avatar.png', { type: 'image/png' }))
})
afterEach(() => vi.restoreAllMocks())
async function mount() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AvatarPicker me={profileFixture}/></QueryClientProvider>)
  const user = userEvent.setup({ applyAccept: false })
  await user.click(screen.getByRole('button', { name: '更换头像' }))
  await screen.findByRole('button', { name: '默认一' })
  await waitFor(() => expect(screen.getByRole('button', { name: '上传头像' })).toBeEnabled())
  return user
}
describe('avatar migration', () => {
  it('saves a stable object key and reloads signed display URLs on reopen', async () => {
    const save = vi.spyOn(userApi, 'setDefaultAvatar').mockResolvedValue(null as never)
    const user = await mount()
    await user.click(screen.getByRole('button', { name: '默认一' }))
    await user.click(screen.getByRole('button', { name: '确认' }))
    await waitFor(() => expect(save).toHaveBeenCalledWith('default/one.png'))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: '更换头像' }))
    await waitFor(() => expect(userApi.getDefaultAvatars).toHaveBeenCalledTimes(2))
  })
  it('only previews a local file before confirmation and releases its object URL on cancel', async () => {
    const signature = vi.spyOn(fileApi, 'signature')
    const user = await mount()
    await user.upload(screen.getByLabelText('头像图片文件'), new File(['png'], 'photo.png', { type: 'image/png' }))
    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalledTimes(1))
    expect(signature).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: '取消' }))
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:local-preview'))
  })
  it('checks server file policy before selecting unsupported or oversized files', async () => {
    const error = vi.spyOn(toast, 'error')
    const user = await mount()
    await user.upload(screen.getByLabelText('头像图片文件'), new File(['bad'], 'photo.gif', { type: 'image/gif' }))
    expect(error).toHaveBeenCalledWith('请选择支持的图片格式')
    await user.upload(screen.getByLabelText('头像图片文件'), new File(['too-large'], 'photo.png', { type: 'image/png' }))
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '确认' })).toBeDisabled()
  })
  it('confirms upload only in signature → storage → completion → avatar order', async () => {
    const calls: string[] = []
    vi.spyOn(fileApi, 'signature').mockImplementation(async () => { calls.push('signature'); return { fileId: 'file-one', uploadUrl: 'https://storage.example.com/upload', method: 'POST', fields: {}, expiresAt: '', confirmationExpiresAt: '' } })
    vi.spyOn(fileApi, 'directUpload').mockImplementation(async (_signature, _file, progress) => { calls.push('storage'); progress(100) })
    vi.spyOn(fileApi, 'complete').mockImplementation(async () => { calls.push('complete'); return { fileId: 'file-one', size: 3, contentType: 'image/png', typeCode: 'AVATAR' } })
    vi.spyOn(userApi, 'setAvatar').mockImplementation(async () => { calls.push('avatar'); return null as never })
    const user = await mount()
    await user.upload(screen.getByLabelText('头像图片文件'), new File(['png'], 'photo.png', { type: 'image/png' }))
    await user.click(screen.getByRole('button', { name: '确认' }))
    await waitFor(() => expect(calls).toEqual(['signature', 'storage', 'complete', 'avatar']))
    expect(userApi.setAvatar).toHaveBeenCalledWith('file-one')
    const uploaded = vi.mocked(fileApi.signature).mock.calls[0][1]
    expect(uploaded.name).toBe('avatar.png')
    expect(vi.mocked(fileApi.directUpload).mock.calls[0][1]).toBe(uploaded)
    expect(crop.createAvatarFile).toHaveBeenCalledWith(expect.any(HTMLImageElement), { x: 10, y: 20, width: 80, height: 80 })
  })
  it('keeps selection available after a failed upload and does not complete or set the avatar', async () => {
    vi.spyOn(fileApi, 'signature').mockRejectedValue(new Error('上传暂不可用'))
    const complete = vi.spyOn(fileApi, 'complete')
    const setAvatar = vi.spyOn(userApi, 'setAvatar')
    const user = await mount()
    await user.upload(screen.getByLabelText('头像图片文件'), new File(['png'], 'photo.png', { type: 'image/png' }))
    await user.click(screen.getByRole('button', { name: '确认' }))
    await waitFor(() => expect(fileApi.signature).toHaveBeenCalled())
    await waitFor(() => expect(screen.getByRole('button', { name: '确认' })).toBeEnabled())
    expect(complete).not.toHaveBeenCalled()
    expect(setAvatar).not.toHaveBeenCalled()
  })
  it('separates current avatar, hides defaults during cropping and lets users replace or cancel the crop', async () => {
    const signature = vi.spyOn(fileApi, 'signature')
    const user = await mount()
    expect(screen.getByRole('region', { name: '当前头像' })).toBeInTheDocument()
    await user.upload(screen.getByLabelText('头像图片文件'), new File(['one'], 'one.png', { type: 'image/png' }))
    expect(screen.getByRole('dialog', { name: '剪裁头像' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '默认一' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重新上传' })).toBeEnabled()
    await user.upload(screen.getByLabelText('头像图片文件'), new File(['two'], 'two.png', { type: 'image/png' }))
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:local-preview'))
    await user.click(screen.getByRole('button', { name: '取消' }))
    expect(screen.getByRole('dialog', { name: '选择头像' })).toBeInTheDocument()
    expect(signature).not.toHaveBeenCalled()
  })
  it('does not request a signature if crop generation fails or exceeds the upload limit', async () => {
    const signature = vi.spyOn(fileApi, 'signature')
    const user = await mount()
    await user.upload(screen.getByLabelText('头像图片文件'), new File(['png'], 'photo.png', { type: 'image/png' }))
    vi.mocked(crop.createAvatarFile).mockRejectedValueOnce(new Error('裁剪失败'))
    await user.click(screen.getByRole('button', { name: '确认' }))
    await waitFor(() => expect(screen.getByRole('button', { name: '确认' })).toBeEnabled())
    vi.mocked(crop.createAvatarFile).mockResolvedValueOnce(new File(['too-large'], 'avatar.png', { type: 'image/png' }))
    await user.click(screen.getByRole('button', { name: '确认' }))
    await waitFor(() => expect(screen.getByRole('button', { name: '确认' })).toBeEnabled())
    expect(signature).not.toHaveBeenCalled()
  })
})
