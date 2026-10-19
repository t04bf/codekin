import { describe, expect, it } from 'vitest'
import Ajv from 'ajv/dist/2020'
import schema from '../../../../../contracts/resume/resume-document.schema.json'
import example from '../../../../../contracts/resume/example.json'
import templates from './data/templates.json'
import { initialResumeState } from '@/config/initialResumeData'
import type { ResumeData } from '@/types/resume'
import type { ResumeDetail } from '@/api/resume'
import { toResumeDocument, fromResumeDetail } from './documentAdapter'

const validate = new Ajv().compile(schema)
function draft(): ResumeData {
  return structuredClone({ ...initialResumeState, id: 'draft-id', title: '后端工程师', templateId: 'classic',
    createdAt: '2026-10-05T00:00:00Z', updatedAt: '2026-10-05T00:00:00Z', activeSection: 'skills' })
}
function saved(document: ReturnType<typeof toResumeDocument>): ResumeDetail {
  return { ...document, id: '1234567890123456789', title: '后端工程师', templateId: templates[0].id,
    templateKey: 'classic', templateVersion: 1, version: '1', createdAt: '2026-10-05T00:00:00Z', updatedAt: '2026-10-05T00:00:00Z' }
}

describe('shared resume JSON contract', () => {
  it('validates real editor sample, repository example and all template defaults', () => {
    for (const doc of [example, toResumeDocument(draft()), ...templates.map(template => ({
      schemaVersion: template.schemaVersion, content: template.defaultContent, layout: template.defaultLayout,
    }))]) {
      expect(validate(doc), JSON.stringify(validate.errors)).toBe(true)
    }
  })
  it('round trips custom fields, custom modules, HTML, ordering, visibility and settings', () => {
    const resume = draft()
    resume.basic.customFields.push({ id: 'website', label: '主页', value: 'https://example.com', visible: false, valueType: 'link' })
    resume.menuSections.push({ id: 'custom-1', title: '开源贡献', icon: 'Code', enabled: false, order: 99 })
    resume.customData['custom-1'] = [{ id: 'item-1', title: '项目', subtitle: '维护者', dateRange: '2025', description: '<p><strong>贡献代码</strong></p>', visible: false }]
    resume.basic.githubKey = 'never-send-secret'
    resume.draggingProjectId = 'p1'
    const document = toResumeDocument(resume)
    expect(validate(document), JSON.stringify(validate.errors)).toBe(true)
    const restored = fromResumeDetail(saved(document))
    expect(toResumeDocument(restored)).toEqual(document)
    expect(restored.basic.githubKey).toBe('')
    expect(restored.activeSection).toBe('basic')
    expect(restored.draggingProjectId).toBeNull()
    expect(JSON.stringify(document)).not.toContain('never-send-secret')
    expect(document).not.toHaveProperty('id')
    expect(document).not.toHaveProperty('activeSection')
  })
  it('stores confirmed file ID instead of local preview or expiring photo URL', () => {
    const resume = draft()
    resume.basic.photo = 'data:image/png;base64,local-preview'
    expect(() => toResumeDocument(resume)).toThrow('重新上传')
    resume.basic.photoFileId = '1234567890123456789'
    const document = toResumeDocument(resume)
    expect(document.content.basic.photo).toBe('')
    expect(document.content.basic.photoFileId).toBe('1234567890123456789')
    expect(validate(document)).toBe(true)
    const restored = fromResumeDetail(saved(document), 'https://storage.example/temporary')
    expect(restored.basic.photo).toBe('https://storage.example/temporary')
    expect(toResumeDocument(restored).content.basic.photo).toBe('')
  })
  it('explicitly rejects unsupported document or renderer versions', () => {
    const detail = saved(toResumeDocument(draft()))
    expect(() => fromResumeDetail({ ...detail, schemaVersion: 99 })).toThrow('版本暂不支持')
    expect(() => fromResumeDetail({ ...detail, templateVersion: 99 })).toThrow('版本暂不支持')
  })
})
