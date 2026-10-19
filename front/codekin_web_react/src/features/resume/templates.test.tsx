// @vitest-environment node
import { describe, it, expect } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import Template from '@/components/templates'
import { DEFAULT_TEMPLATES } from '@/config'
import { createTemplatePreviewData } from '@/lib/templatePreview'
import { NextIntlClientProvider } from '@/i18n/compat/client'
import messages from '@/i18n/locales/zh.json'
import catalog from './data/templates.json'

describe('original resume templates', () => {
 it('registers nine unique layouts', () => {
   expect(DEFAULT_TEMPLATES).toHaveLength(9)
   expect(new Set(DEFAULT_TEMPLATES.map(t => t.id)).size).toBe(9)
 })
 it('maps every seeded template version to an existing renderer', () => {
   expect(catalog.map(item => item.templateKey).sort()).toEqual(DEFAULT_TEMPLATES.map(item => item.id).sort())
   expect(new Set(catalog.map(item => `${item.templateKey}@${item.templateVersion}`)).size).toBe(9)
 })
 it('seed defaults match the actual renderers and editor settings', () => {
   expect(catalog).toHaveLength(DEFAULT_TEMPLATES.length)
   for (const template of DEFAULT_TEMPLATES) {
     const seeded = catalog.find(item => item.templateKey === template.id)!
     const preview = createTemplatePreviewData(template, 'zh')
     expect(seeded.templateVersion).toBe(1)
     expect(seeded.schemaVersion).toBe(1)
     expect(seeded.name).toBe(template.name)
     expect(seeded.defaultLayout.basic.layout).toBe(preview.basic.layout)
     expect(seeded.defaultLayout.globalSettings).toMatchObject(preview.globalSettings)
   }
 })
 for (const template of DEFAULT_TEMPLATES) {
   it(`${template.id}: sample, custom fields and visibility render`, () => {
     const data = structuredClone(createTemplatePreviewData(template, 'zh'))
     data.basic.customFields.push({ id: 'test-link', label: '作品集', value: 'https://example.com/portfolio', valueType: 'link', visible: true })
     data.menuSections.push({ id: 'custom-test', title: '自定义成果', icon: 'Award', enabled: true, order: 99 })
     data.customData['custom-test'] = [{ id: '1', title: '成果一', subtitle: '', dateRange: '', description: '<p>自定义详细内容</p>', visible: true }, { id: '2', title: '不可见测试内容', subtitle: '', dateRange: '', description: '', visible: false }]
     const html = renderToStaticMarkup(<NextIntlClientProvider locale="zh" messages={messages}><Template data={data} template={template}/></NextIntlClientProvider>)
     expect(html).toContain('codekin')
     expect(html).toContain('example.com/portfolio')
     expect(html).toContain('自定义成果')
     expect(html).toContain('自定义详细内容')
     expect(html).not.toContain('不可见测试内容')
   })
 }
})
