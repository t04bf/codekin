import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { createResumeEditorStore } from '@/store/resumeEditorStore'
import { resumeApi, type ResumeDetail, type ResumeUpdate, type ResumeTemplate } from '@/api/resume'
import { ApiBusinessError } from '@/api/http'
import { useAuthStore } from '@/features/auth/session'
import example from '../../../../../contracts/resume/example.json'
import catalog from './data/templates.json'
import { createResumeSync, remoteDraft, resumeFingerprint } from './resumeSync'
import { installCloudDetail } from './useCloudResumes'
import { fromResumeDetail } from './documentAdapter'

const template = catalog as unknown as ResumeTemplate[]
const initial = { ...example, id: '1234567890123456789', title: '后端简历', templateId: template[0].id, templateKey: 'classic', templateVersion: 1,
  version: '1', createdAt: '2026-10-06T00:00:00Z', updatedAt: '2026-10-06T00:00:00Z' } as ResumeDetail
const id = initial.id
const active: ReturnType<typeof createResumeSync>[] = []
function response(request: ResumeUpdate, version = String(BigInt(request.expectedVersion) + 1n)): ResumeDetail {
  return { ...initial, ...request, templateKey: template.find(t => t.id === request.templateId)!.templateKey, templateVersion: 1, version }
}
async function setup() {
  const draft = createResumeEditorStore()
  draft.store.getState().addResume(remoteDraft(structuredClone(initial)))
  const saved = vi.fn()
  const sync = createResumeSync(draft, id, '42', () => template, saved)
  active.push(sync)
  return { draft, sync, saved }
}
beforeEach(() => { useAuthStore.setState({ userId: '42' }); vi.useFakeTimers() })
afterEach(() => { active.splice(0).forEach(sync => sync.stop()); vi.restoreAllMocks(); vi.useRealTimers(); useAuthStore.getState().clear() })

describe('cloud editor save lifecycle', () => {
  it('saves one-page mode in layout and restores it from the server detail', async () => {
    const save = vi.spyOn(resumeApi, 'save').mockImplementation(async (_id, request) => response(request))
    const { draft, sync } = await setup()
    draft.store.getState().updateGlobalSettings({ autoOnePage: true })
    await sync.flush()
    expect(save.mock.calls[0][1].layout.globalSettings.autoOnePage).toBe(true)
    expect(fromResumeDetail(response(save.mock.calls[0][1])).globalSettings.autoOnePage).toBe(true)
    draft.store.getState().updateGlobalSettings({ autoOnePage: false })
    await sync.flush()
    expect(save.mock.calls[1][1].layout.globalSettings.autoOnePage).toBe(false)
  })

  it('does not save unchanged documents when JSON keys arrive in a different order', async () => {
    const save = vi.spyOn(resumeApi, 'save')
    const { draft, sync } = await setup()
    const basic = draft.store.getState().activeResume!.basic
    draft.store.getState().updateBasicInfo(Object.fromEntries(Object.entries(basic).reverse()))
    await vi.advanceTimersByTimeAsync(1000)
    expect(save).not.toHaveBeenCalled()
    expect(sync.status.getState().phase).toBe('saved')
  })
  it('debounces business changes while selection and dragging do not save', async () => {
    const save = vi.spyOn(resumeApi, 'save').mockImplementation(async (_id, request) => response(request))
    const { draft, sync } = await setup()
    draft.store.getState().setActiveSection('skills'); draft.store.getState().setDraggingProjectId('x')
    await vi.advanceTimersByTimeAsync(1000)
    expect(save).not.toHaveBeenCalled()
    draft.store.getState().updateBasicInfo({ name: 'first' }); draft.store.getState().updateBasicInfo({ name: 'last' })
    await vi.advanceTimersByTimeAsync(900)
    expect(save).toHaveBeenCalledTimes(1)
    expect(save.mock.calls[0][1].content.basic.name).toBe('last')
    expect(draft.store.getState().resumes[id].remote?.version).toBe('2')
    expect(sync.status.getState().phase).toBe('saved')
  })
  it('serializes saves and sends edits made during a request with the returned version', async () => {
    let finish!: () => void
    const save = vi.spyOn(resumeApi, 'save').mockImplementationOnce((_id, request) => new Promise(resolve => { finish = () => resolve(response(request)) }))
      .mockImplementation(async (_id, request) => response(request, '3'))
    const { draft, sync } = await setup()
    draft.store.getState().updateBasicInfo({ name: 'first' })
    const flushing = sync.flush()
    await vi.advanceTimersByTimeAsync(0)
    draft.store.getState().updateBasicInfo({ name: 'second' })
    expect(save).toHaveBeenCalledTimes(1)
    finish(); await flushing
    expect(save).toHaveBeenCalledTimes(2)
    expect(save.mock.calls[1][1].expectedVersion).toBe('2')
    expect(save.mock.calls[1][1].content.basic.name).toBe('second')
    expect(draft.store.getState().activeResume?.basic.name).toBe('second')
    expect(draft.store.getState().resumes[id].remote?.version).toBe('3')
  })
  it('undo and redo keep the current server version and save restored content', async () => {
    const save = vi.spyOn(resumeApi, 'save').mockImplementation(async (_id, request) => response(request))
    const { draft, sync } = await setup()
    draft.store.getState().updateBasicInfo({ name: 'edited' }); await sync.flush()
    draft.store.getState().undo()
    expect(draft.store.getState().resumes[id].remote?.version).toBe('2')
    await sync.flush()
    expect(save.mock.calls[1][1].expectedVersion).toBe('2')
    expect(save.mock.calls[1][1].content.basic.name).toBe(initial.content.basic.name)
    draft.store.getState().redo(); await sync.flush()
    expect(save.mock.calls[2][1].expectedVersion).toBe('3')
    expect(save.mock.calls[2][1].content.basic.name).toBe('edited')
    expect(sync.status.getState().phase).toBe('saved')
  })
  it('keeps in memory and reuses identical mutation payload after an uncertain network failure', async () => {
    const save = vi.spyOn(resumeApi, 'save').mockRejectedValueOnce(new Error('Network Error')).mockImplementation(async (_id, request) => response(request))
    const { draft, sync } = await setup()
    draft.store.getState().updateBasicInfo({ name: 'pending' }); await sync.flush()
    expect(sync.status.getState().phase).toBe('error')
    const request = structuredClone(save.mock.calls[0][1])
    expect(draft.store.getState().resumes[id].remote?.pending?.request).toEqual(request)
    draft.store.getState().updateBasicInfo({ name: 'next-edit' })
    await sync.flush()
    expect(save.mock.calls[1][1]).toEqual(request)
    expect(save.mock.calls[2][1].expectedVersion).toBe('2')
    expect(save.mock.calls[2][1].mutationId).not.toBe(request.mutationId)
    expect(draft.store.getState().resumes[id].remote?.pending).toBeUndefined()
  })
  it('pauses on version conflicts and preserves edits until explicit reload', async () => {
    const save = vi.spyOn(resumeApi, 'save').mockRejectedValue(new ApiBusinessError('RESUME_VERSION_CONFLICT', '版本冲突'))
    const { draft, sync } = await setup()
    draft.store.getState().updateBasicInfo({ name: 'local edit' }); await sync.flush()
    draft.store.getState().updateBasicInfo({ name: 'keep this' }); await vi.advanceTimersByTimeAsync(2000); await sync.flush()
    expect(save).toHaveBeenCalledTimes(1); expect(sync.status.getState().phase).toBe('conflict')
    installCloudDetail(draft, { ...initial, version: '2' })
    expect(draft.store.getState().activeResume?.basic.name).toBe('keep this')
    sync.stop(); installCloudDetail(draft, { ...initial, version: '2' }, true)
    expect(draft.store.getState().resumes[id].remote?.version).toBe('2')
    expect(draft.store.getState().activeResume?.basic.name).toBe(initial.content.basic.name)
  })
  it('ignores late responses after account changes', async () => {
    let finish!: () => void
    vi.spyOn(resumeApi, 'save').mockImplementation((_id, request) => new Promise(resolve => { finish = () => resolve(response(request)) }))
    const { draft, sync, saved } = await setup()
    draft.store.getState().updateBasicInfo({ name: 'pending' }); const pending = sync.flush()
    await vi.advanceTimersByTimeAsync(0); useAuthStore.setState({ userId: '43' }); finish(); await pending
    expect(saved).not.toHaveBeenCalled(); expect(draft.store.getState().resumes[id].remote?.version).toBe('1')
  })
  it('reuses a pending request after remount and ignores the stopped controller response', async () => {
    let finish!: () => void
    const save = vi.spyOn(resumeApi, 'save').mockImplementationOnce((_id, request) => new Promise(resolve => { finish = () => resolve(response(request)) }))
      .mockImplementation(async (_id, request) => response(request))
    const { draft, sync, saved } = await setup()
    draft.store.getState().updateBasicInfo({ name: 'before leaving' })
    const oldRequest = sync.flush(); await vi.advanceTimersByTimeAsync(0)
    sync.stop()
    const next = createResumeSync(draft, id, '42', () => template, vi.fn()); active.push(next)
    draft.store.getState().updateBasicInfo({ name: 'after returning' })
    await next.flush()
    expect(save.mock.calls[1][1]).toEqual(save.mock.calls[0][1])
    expect(draft.store.getState().resumes[id].remote?.version).toBe('3')
    finish(); await oldRequest
    expect(saved).not.toHaveBeenCalled()
    expect(draft.store.getState().resumes[id].remote?.version).toBe('3')
    expect(next.status.getState().phase).toBe('saved')
  })
  it('resolves switched templates by server record ID and excludes preview/credential values', async () => {
    const save = vi.spyOn(resumeApi, 'save').mockImplementation(async (_id, request) => response(request))
    const { draft, sync } = await setup()
    draft.store.getState().setTemplate('modern')
    draft.store.getState().updateBasicInfo({ photoFileId: '99', photo: 'data:image/png;base64,x', githubKey: 'secret' })
    await sync.flush()
    const request = save.mock.calls[0][1]
    expect(request.templateId).toBe(template.find(item => item.templateKey === 'modern')!.id)
    expect(request.content.basic.photo).toBe(''); expect(JSON.stringify(request)).not.toContain('secret')
    expect(resumeFingerprint(draft.store.getState().resumes[id])).toBe(draft.store.getState().resumes[id].remote?.savedFingerprint)
  })
  it('refreshes photo URLs without replacing dirty fields or resetting undo history', async () => {
    const { draft } = await setup()
    draft.store.getState().updateBasicInfo({ name: 'unsaved', photoFileId: '99', photo: 'old-url' })
    const before = draft.store.getState().history[id].length
    const detail = structuredClone(initial); detail.content.basic.photoFileId = '99'; detail.content.basic.photo = ''; detail.photoUrl = 'https://signed.example/new'
    installCloudDetail(draft, detail)
    expect(draft.store.getState().activeResume?.basic.name).toBe('unsaved')
    expect(draft.store.getState().activeResume?.basic.photo).toBe(detail.photoUrl)
    expect(draft.store.getState().history[id]).toHaveLength(before)
  })
})
