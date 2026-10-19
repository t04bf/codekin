import { describe, it, expect, vi } from 'vitest'
import { createResumeEditorStore } from '@/store/resumeEditorStore'
import { initialResumeState } from '@/config/initialResumeData'
import { DEFAULT_TEMPLATES } from '@/config'

describe('in-memory resume editing', () => {
  it('keeps content, custom fields and images when switching through all templates', async () => {
    const draft = createResumeEditorStore()
    draft.store.getState().createResume('classic')
    draft.store.getState().updateBasicInfo({ name: '后端开发者', customFields: [{ id: 'website', label: '开源主页', value: 'https://github.com/codekin', icon: 'Globe', visible: true }] })
    draft.store.getState().createCustomSection({ id: 'custom-1', title: '开源贡献', icon: '➕', enabled: true, order: 5 })
    const before = structuredClone(draft.store.getState().activeResume!)
    for (const template of DEFAULT_TEMPLATES) {
      draft.store.getState().setTemplate(template.id)
      const resume = draft.store.getState().activeResume!
      expect(resume.templateId).toBe(template.id)
      expect(resume.basic.name).toBe(before.basic.name)
      expect(resume.basic.photo).toBe(before.basic.photo)
      expect(resume.basic.customFields).toEqual(before.basic.customFields)
      expect(resume.customData).toEqual(before.customData)
      expect(resume.projects).toEqual(before.projects)
      expect(resume.globalSettings.pagePadding).toBe(template.spacing.contentPadding)
    }
  })

  it('removes a custom module in one undoable operation', async () => {
    const draft = createResumeEditorStore()
    draft.store.getState().createResume('classic')
    draft.store.getState().createCustomSection({ id: 'custom-1', title: '开源贡献', icon: '➕', enabled: true, order: 5 })
    draft.store.getState().removeSection('custom-1')
    expect(draft.store.getState().activeResume!.customData['custom-1']).toBeUndefined()
    expect(draft.store.getState().activeResume!.activeSection).not.toBe('custom-1')
    draft.store.getState().undo()
    expect(draft.store.getState().activeResume!.customData['custom-1']).toHaveLength(1)
    expect(draft.store.getState().activeResume!.menuSections.some(section => section.id === 'custom-1')).toBe(true)
  })

  it('creates independent samples with the selected template and color', async () => {
    const { store } = createResumeEditorStore()
    const first = store.getState().createResume('modern', false, '#3b82f6')
    const second = store.getState().createResume('classic')
    store.getState().setActiveResume(first)
    store.getState().updateBasicInfo({ name: '后端工程师' })
    expect(store.getState().resumes[first].globalSettings).toMatchObject({ themeColor: '#3b82f6', pagePadding: 0 })
    expect(store.getState().resumes[second].basic.name).toBe('codekin')
    expect(initialResumeState.basic.name).toBe('codekin')
  })

  it('does not read or write browser storage and starts empty in a new session', () => {
    const reads = vi.spyOn(Storage.prototype, 'getItem')
    const writes = vi.spyOn(Storage.prototype, 'setItem')
    const removes = vi.spyOn(Storage.prototype, 'removeItem')
    const open = vi.fn(() => { throw new Error('IndexedDB must not be used') })
    vi.stubGlobal('indexedDB', { open })
    try {
      const first = createResumeEditorStore()
      first.store.getState().createResume('classic')
      first.store.getState().updateBasicInfo({ name: 'Unsaved' })
      const fresh = createResumeEditorStore()
      expect(fresh.store.getState().resumes).toEqual({})
      expect(fresh.store.getState().activeResume).toBeNull()
      expect(reads).not.toHaveBeenCalled()
      expect(writes).not.toHaveBeenCalled()
      expect(removes).not.toHaveBeenCalled()
      expect(open).not.toHaveBeenCalled()
    } finally { vi.restoreAllMocks(); vi.unstubAllGlobals() }
  })

  it('supports custom sections, visibility, reorder and undo/redo', async () => {
    const draft = createResumeEditorStore()
    draft.store.getState().createResume('classic')
    draft.store.getState().createCustomSection({ id: 'custom-1', title: '开源贡献', icon: '➕', enabled: true, order: 5 })
    draft.store.getState().addCustomItem('custom-1')
    const id = draft.store.getState().activeResume!.customData['custom-1'][0].id
    draft.store.getState().updateCustomItem('custom-1', id, { title: '开源工具', description: '<p>后端工具</p>' })
    draft.store.getState().toggleSectionVisibility('skills')
    expect(draft.store.getState().activeResume!.menuSections.find(s => s.id === 'skills')?.enabled).toBe(false)
    draft.store.getState().undo()
    expect(draft.store.getState().activeResume!.menuSections.find(s => s.id === 'skills')?.enabled).toBe(true)
    draft.store.getState().redo()
    expect(draft.store.getState().activeResume!.menuSections.find(s => s.id === 'skills')?.enabled).toBe(false)
    draft.store.getState().reorderSections([...draft.store.getState().activeResume!.menuSections].reverse())
    expect(draft.store.getState().activeResume!.menuSections[0].id).toBe('basic')
    expect(draft.store.getState().activeResume!.customData['custom-1'][0].title).toBe('开源工具')
  })

  it('does not erase the active resume when deleting another resume', async () => {
    const { store } = createResumeEditorStore()
    const first = store.getState().createResume('classic')
    const second = store.getState().createResume('classic')
    store.getState().deleteResume(store.getState().resumes[first])
    expect(store.getState().activeResumeId).toBe(second)
    expect(store.getState().resumes[first]).toBeUndefined()
  })

})
