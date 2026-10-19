import { type ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { resumeApi, type ResumeDetail, type ResumeTemplate } from '@/api/resume'
import { useAuthStore } from '@/features/auth/session'
import { ResumeEditorProvider, useResumeEditorContext } from '@/store/useResumeStore'
import { useCreateCloudResume } from './useCloudResumes'
import example from '../../../../../contracts/resume/example.json'
import catalog from './data/templates.json'

const templates = catalog as unknown as ResumeTemplate[]
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}><ResumeEditorProvider>{children}</ResumeEditorProvider></QueryClientProvider>
  }
  return renderHook(() => ({ editor: useResumeEditorContext(), create: useCreateCloudResume() }), { wrapper: Wrapper })
}
beforeEach(() => {
  useAuthStore.setState({ userId: '42' })
  vi.spyOn(resumeApi, 'templates').mockResolvedValue(templates)
})
afterEach(() => { vi.restoreAllMocks(); useAuthStore.getState().clear() })

describe('server-only resume creation', () => {
  it('registers only the server record after creation succeeds', async () => {
    let finish!: (detail: ResumeDetail) => void
    const create = vi.spyOn(resumeApi, 'create').mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const hook = mount()
    await waitFor(() => expect(hook.result.current?.create.isIdle).toBe(true))
    await waitFor(() => expect(resumeApi.templates).toHaveBeenCalled())
    let pending!: Promise<ResumeDetail>
    act(() => { pending = hook.result.current.create.mutateAsync({ templateKey: 'modern', blank: true }) })
    await waitFor(() => expect(create).toHaveBeenCalledTimes(1))
    expect(hook.result.current.editor.store.getState().resumes).toEqual({})
    const request = create.mock.calls[0][0]
    expect(request.templateId).toBe(templates.find(item => item.templateKey === 'modern')!.id)
    const saved = { ...request, id: '1234567890123456789', templateKey: 'modern', templateVersion: 1, version: '1',
      createdAt: '2026-10-06T00:00:00Z', updatedAt: '2026-10-06T00:00:00Z' } as ResumeDetail
    await act(async () => { finish(saved); await pending })
    expect(Object.keys(hook.result.current.editor.store.getState().resumes)).toEqual([saved.id])
    hook.unmount()
    const fresh = mount()
    await waitFor(() => expect(fresh.result.current?.editor).toBeDefined())
    expect(fresh.result.current.editor.store.getState().resumes).toEqual({})
  })

  it('does not leave a local record or automatically retry a failed POST', async () => {
    const create = vi.spyOn(resumeApi, 'create').mockRejectedValue(new Error('Network Error'))
    const hook = mount()
    await waitFor(() => expect(hook.result.current?.create.isIdle).toBe(true))
    // Wait for the template query to settle before the mutation.
    await waitFor(() => expect(resumeApi.templates).toHaveBeenCalled())
    await act(async () => { await expect(hook.result.current.create.mutateAsync({})).rejects.toThrow('Network Error') })
    expect(create).toHaveBeenCalledTimes(1)
    expect(hook.result.current.editor.store.getState().resumes).toEqual({})
  })

  it('reads the saved server document before copying rather than an unsaved memory edit', async () => {
    const original = { ...example, id: '1234567890123456789', title: 'Server title', templateId: templates[0].id,
      templateKey: 'classic', templateVersion: 1, version: '1', createdAt: '', updatedAt: '' } as ResumeDetail
    const detail = vi.spyOn(resumeApi, 'detail').mockResolvedValue(original)
    const create = vi.spyOn(resumeApi, 'create').mockImplementation(async request => ({ ...original, ...request, id: '1234567890123456790' }))
    const hook = mount()
    await waitFor(() => expect(hook.result.current?.create.isIdle).toBe(true))
    await waitFor(() => expect(resumeApi.templates).toHaveBeenCalled())
    act(() => { hook.result.current.editor.store.getState().createResume('classic'); hook.result.current.editor.store.getState().updateBasicInfo({ name: 'Unsaved memory' }) })
    await act(async () => { await hook.result.current.create.mutateAsync({ sourceId: original.id }) })
    expect(detail).toHaveBeenCalledWith(original.id)
    expect(create.mock.calls[0][0].content.basic.name).toBe(original.content.basic.name)
    expect(create.mock.calls[0][0].title).toBe('Server title（副本）')
  })
})
