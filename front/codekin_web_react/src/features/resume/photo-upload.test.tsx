import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { fileApi } from '@/api/file'
import { useAuthStore } from '@/features/auth/session'
import * as images from '@/utils/imageUtils'
import { useResumePhotoUpload } from './useResumePhotoUpload'

const file = new File(['png'], 'portrait.png', { type: 'image/png' })
const restriction = { code: 'RESUME_PHOTO', extensions: ['png'], contentTypes: ['image/png'], maxBytes: 5, maxWidth: 100, maxHeight: 100, maxPixels: 10000 }
const signature = { fileId: 'photo-file', uploadUrl: 'https://storage.example/upload', method: 'POST' as const, fields: {}, expiresAt: '', confirmationExpiresAt: '' }

beforeEach(() => {
  useAuthStore.setState({ userId: '42' })
  vi.spyOn(fileApi, 'uploadTypes').mockResolvedValue([restriction])
  vi.spyOn(images, 'compressImage').mockResolvedValue('data:image/png;base64,local-preview')
  vi.spyOn(fileApi, 'signature').mockResolvedValue(signature)
  vi.spyOn(fileApi, 'directUpload').mockImplementation(async (_signature, _file, progress) => { progress(100) })
  vi.spyOn(fileApi, 'complete').mockResolvedValue({ fileId: 'photo-file', typeCode: 'RESUME_PHOTO', size: 3, contentType: 'image/png' })
})
afterEach(() => { vi.restoreAllMocks(); useAuthStore.getState().clear() })

function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return renderHook(() => useResumePhotoUpload(true), { wrapper: ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider> })
}

describe('resume photo direct upload', () => {
  it('sends the original file in signature → storage → complete order and returns a local preview', async () => {
    const calls: string[] = []
    vi.mocked(fileApi.signature).mockImplementation(async () => { calls.push('signature'); return signature })
    vi.mocked(fileApi.directUpload).mockImplementation(async (_signature, _file, progress) => { calls.push('storage'); progress(100) })
    vi.mocked(fileApi.complete).mockImplementation(async () => { calls.push('complete'); return { fileId: 'photo-file', typeCode: 'RESUME_PHOTO', size: 3, contentType: 'image/png' } })
    const { result } = mount()
    await waitFor(() => expect(result.current.policy.data).toEqual(restriction))
    let preview: { preview: string; fileId: string } | undefined
    await act(async () => { preview = await result.current.upload(file) })
    expect(calls).toEqual(['signature', 'storage', 'complete'])
    expect(fileApi.signature).toHaveBeenCalledWith('RESUME_PHOTO', file)
    expect(fileApi.directUpload).toHaveBeenCalledWith(signature, file, expect.any(Function))
    expect(fileApi.complete).toHaveBeenCalledWith('photo-file')
    expect(preview).toEqual({ preview: 'data:image/png;base64,local-preview', fileId: 'photo-file' })
    expect(result.current.progress).toBe(100)
  })

  it('uses server policy to reject unsupported and oversized files before creating an upload', async () => {
    const { result } = mount()
    await waitFor(() => expect(result.current.policy.data).toBeDefined())
    await act(async () => {
      await expect(result.current.upload(new File(['png'], 'portrait.gif', { type: 'image/gif' }))).rejects.toThrow('请选择支持的图片格式')
      await expect(result.current.upload(new File(['too-large'], 'portrait.png', { type: 'image/png' }))).rejects.toThrow('图片大小必须')
    })
    expect(images.compressImage).not.toHaveBeenCalled()
    expect(fileApi.signature).not.toHaveBeenCalled()
  })

  it('does not complete a failed storage upload and permits a retry', async () => {
    vi.mocked(fileApi.directUpload).mockRejectedValueOnce(new Error('storage failed'))
    const { result } = mount()
    await waitFor(() => expect(result.current.policy.data).toBeDefined())
    await act(async () => { await expect(result.current.upload(file)).rejects.toThrow('storage failed') })
    expect(fileApi.complete).not.toHaveBeenCalled()
    expect(result.current.isUploading).toBe(false)
    await act(async () => { expect(await result.current.upload(file)).toEqual({ preview: 'data:image/png;base64,local-preview', fileId: 'photo-file' }) })
    expect(fileApi.complete).toHaveBeenCalledTimes(1)
  })

  it('does not return a preview for failed or mismatched confirmation', async () => {
    vi.mocked(fileApi.complete).mockRejectedValueOnce(new Error('invalid image'))
    const { result } = mount()
    await waitFor(() => expect(result.current.policy.data).toBeDefined())
    await act(async () => { await expect(result.current.upload(file)).rejects.toThrow('invalid image') })
    vi.mocked(fileApi.complete).mockResolvedValue({ fileId: 'photo-file', typeCode: 'AVATAR', size: 3, contentType: 'image/png' })
    await act(async () => { await expect(result.current.upload(file)).rejects.toThrow('证件照确认结果不正确') })
  })

  it('blocks duplicate uploads and stops confirmation if the account changes mid-upload', async () => {
    let finish!: () => void
    vi.mocked(fileApi.directUpload).mockImplementation(() => new Promise<void>(resolve => { finish = resolve }))
    const { result } = mount()
    await waitFor(() => expect(result.current.policy.data).toBeDefined())
    let pending!: Promise<{ preview: string; fileId: string } | undefined>
    act(() => { pending = result.current.upload(file) })
    const failure = expect(pending).rejects.toThrow('账号已变更')
    await waitFor(() => expect(fileApi.directUpload).toHaveBeenCalledTimes(1))
    await act(async () => { expect(await result.current.upload(file)).toBeUndefined() })
    expect(fileApi.signature).toHaveBeenCalledTimes(1)
    await act(async () => { useAuthStore.setState({ userId: '43' }); finish(); await failure })
    expect(fileApi.complete).not.toHaveBeenCalled()
  })

  it('does not request policy or sign uploads for an anonymous editor', async () => {
    useAuthStore.getState().clear()
    const { result } = mount()
    expect(fileApi.uploadTypes).not.toHaveBeenCalled()
    await act(async () => { await expect(result.current.upload(file)).rejects.toThrow('请登录后上传证件照') })
    expect(fileApi.signature).not.toHaveBeenCalled()
  })
})
