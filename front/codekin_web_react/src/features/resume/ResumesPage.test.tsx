import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { resumeApi, type ResumeDetail, type ResumeTemplate } from '@/api/resume'
import { useAuthStore } from '@/features/auth/session'
import { NextIntlClientProvider } from '@/i18n/compat/client'
import messages from '@/i18n/locales/zh.json'
import { ResumeEditorContext } from '@/store/resumeEditorContext'
import { createResumeEditorStore } from '@/store/resumeEditorStore'
import { fromResumeDetail } from './documentAdapter'
import ResumesPage from './ResumesPage'
import catalog from './data/templates.json'

const templates = catalog as unknown as ResumeTemplate[]
const originalResizeObserver = globalThis.ResizeObserver
function record(id: string, name: string): ResumeDetail {
  const template = templates[0]
  const content = structuredClone(template.defaultContent)
  content.basic.name = name
  content.basic.photo = ''
  content.basic.photoFileId = '99'
  content.skillContent = '<p>服务端保存的后端技能</p>'
  return { id, title: `简历 ${id}`, templateId: template.id, templateKey: template.templateKey,
    templateVersion: 1, schemaVersion: 1, version: '1', content, layout: structuredClone(template.defaultLayout),
    photoUrl: 'https://signed.example/photo.png', photoUrlExpiresAt: '2026-10-11T12:00:00Z',
    createdAt: '2026-10-11T00:00:00Z', updatedAt: '2026-10-11T00:00:00Z' }
}
function mount(editor = createResumeEditorStore()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = render(<QueryClientProvider client={client}><MemoryRouter>
    <NextIntlClientProvider locale="zh" messages={messages}><ResumeEditorContext.Provider value={editor}>
      <ResumesPage/>
    </ResumeEditorContext.Provider></NextIntlClientProvider>
  </MemoryRouter></QueryClientProvider>)
  return { ...view, editor, client }
}
beforeEach(() => {
  useAuthStore.setState({ userId: '42' })
  vi.spyOn(resumeApi, 'templates').mockResolvedValue(templates)
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
})
afterEach(() => { vi.restoreAllMocks(); vi.stubGlobal('ResizeObserver', originalResizeObserver); useAuthStore.getState().clear() })

describe('server-backed resume list previews', () => {
  it('renders saved content and private photos on first load and a fresh remount, without per-card detail requests', async () => {
    const list = vi.spyOn(resumeApi, 'list').mockResolvedValue({
      items: [record('1001', '首次进入即可预览'), record('1002', '第二份服务端简历')],
      page: 1, pageSize: 20, hasMore: false,
    })
    const detail = vi.spyOn(resumeApi, 'detail')
    const first = mount()
    expect(await screen.findByText('首次进入即可预览')).toBeInTheDocument()
    expect(screen.getByText('第二份服务端简历')).toBeInTheDocument()
    expect(screen.getAllByText('服务端保存的后端技能')).toHaveLength(2)
    expect([...first.container.querySelectorAll('.resume-preview img')].map(img => img.getAttribute('src')))
      .toEqual(['https://signed.example/photo.png', 'https://signed.example/photo.png'])
    expect(first.editor.store.getState().resumes).toEqual({})
    expect(list).toHaveBeenCalledTimes(1)
    expect(detail).not.toHaveBeenCalled()
    first.unmount()
    first.client.clear()
    const fresh = mount()
    expect(await screen.findByText('首次进入即可预览')).toBeInTheDocument()
    expect(list).toHaveBeenCalledTimes(2)
    expect(detail).not.toHaveBeenCalled()
    expect(fresh.editor.store.getState().resumes).toEqual({})
  })

  it('uses the newly saved server document instead of stale editor memory', async () => {
    const saved = record('1001', '已保存的新姓名')
    const editor = createResumeEditorStore()
    const stale = fromResumeDetail(saved)
    stale.basic.name = '旧内存姓名'
    editor.store.getState().addResume(stale)
    vi.spyOn(resumeApi, 'list').mockResolvedValue({ items: [saved], page: 1, pageSize: 20, hasMore: false })
    mount(editor)
    expect(await screen.findByText('已保存的新姓名')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('旧内存姓名')).not.toBeInTheDocument())
    expect(editor.store.getState().resumes[saved.id].basic.name).toBe('旧内存姓名')
  })
})
