import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { readFile } from 'node:fs/promises'

const server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom' })
try {
  const { default: Renderer } = await server.ssrLoadModule('/src/features/resume/components/ResumeRenderer.vue')
  const { resumeTemplates, createPreviewResumeContent, createBlankResumeContent, getResumeTemplate } = await server.ssrLoadModule('/src/features/resume/templates.ts')
  const { arrangeSections, safeResumeLink } = await server.ssrLoadModule('/src/features/resume/rendering.ts')
  const content = createPreviewResumeContent()
  content.basic.photoFileId = 'photo-test'
  content.basic.customFields.push({ id: 'private-field', label: '不公开', type: 'TEXT', value: 'HIDDEN_BASIC_SECRET', visible: false })
  content.sections.push({ id: 'custom', type: 'CUSTOM', title: '开源贡献', visible: true,
    fieldDefinitions: [{ id: 'repo', label: '仓库', type: 'LINK' }, { id: 'private', label: '隐藏字段', type: 'TEXT', visible: false }],
    items: [{ id: 'custom-item', title: '自定义贡献', values: { repo: 'https://example.com/repo', private: 'HIDDEN_VALUE_SECRET' } }, { id: 'hidden-item', title: 'HIDDEN_ITEM_SECRET', visible: false }] })
  content.sections.push({ id: 'hidden-section', type: 'CUSTOM', title: 'HIDDEN_SECTION_SECRET', visible: false, items: [] })
  content.sections[0].content = { type: 'doc', content: [{ type: 'paragraph', content: [
    { type: 'text', text: '<img src=x onerror=alert(1)>', marks: [{ type: 'link', attrs: { href: 'javascript:alert(1)' } }] },
    { type: 'text', text: '安全链接', marks: [{ type: 'bold' }, { type: 'link', attrs: { href: 'https://example.com/safe' } }] },
  ] }] }
  content.sections.find(section => section.id === 'certificates').items[0].fileId = 'certificate-test'
  assert.equal(resumeTemplates.length, 9)
  for (const template of resumeTemplates) {
    const html = await renderToString(createSSRApp({ render: () => h(Renderer, { templateKey: template.templateKey, templateVersion: 1, content,
      assets: { 'photo-test': '/test-photo.png', 'certificate-test': 'https://example.com/certificate.png' } }) }))
    assert.ok(html.includes(`data-template="${template.templateKey}@1"`))
    for (const text of ['开源贡献', '自定义贡献', 'https://example.com/repo', '/test-photo.png', 'https://example.com/certificate.png', '安全链接']) assert.ok(html.includes(text), `${template.templateKey}: missing ${text}`)
    for (const text of ['HIDDEN_BASIC_SECRET', 'HIDDEN_VALUE_SECRET', 'HIDDEN_ITEM_SECRET', 'HIDDEN_SECTION_SECRET', 'href="javascript:', '<img src=x']) assert.ok(!html.includes(text), `${template.templateKey}: leaked ${text}`)
    assert.ok(html.includes('&lt;img'))
    const blankHtml = await renderToString(createSSRApp({ render: () => h(Renderer, { templateKey: template.templateKey, templateVersion: 1, content: createBlankResumeContent() }) }))
    assert.ok(!blankHtml.includes('林知远'))
  }
  assert.throws(() => getResumeTemplate('classic', 999), /不支持/)
  assert.equal(safeResumeLink('javascript:alert(1)'), undefined)
  assert.equal(safeResumeLink('data:text/html,test'), undefined)
  const layout = structuredClone(resumeTemplates.find(t => t.templateKey === 'modern').defaultLayout)
  layout.columns[0].sectionIds.push('education', 'missing')
  const columns = arrangeSections(content, layout)
  assert.equal(columns.sidebar.filter(s => s.id === 'education').length, 1)
  assert.ok(columns.main.some(s => s.id === 'custom'))
  const first = createBlankResumeContent()
  first.basic.name = 'changed'
  assert.equal(createBlankResumeContent().basic.name, '')
  const sql = await readFile('../../deploy/sql/20261005_resume_templates.sql', 'utf8')
  const jsonLiterals = [...sql.matchAll(/CONVERT\(X'([0-9a-f]+)' USING utf8mb4\)/g)].map(match => Buffer.from(match[1], 'hex').toString('utf8')).filter(text => text.startsWith('{')).map(text => JSON.parse(text))
  assert.equal(jsonLiterals.length, 18)
  for (let index = 0; index < resumeTemplates.length; index++) {
    assert.deepEqual(jsonLiterals[index * 2], createBlankResumeContent())
    assert.deepEqual(jsonLiterals[index * 2 + 1], resumeTemplates[index].defaultLayout)
  }
  assert.equal((sql.match(/INSERT INTO resume_template/g) ?? []).length, 9)
  assert.ok(!sql.includes('CREATE TABLE IF NOT EXISTS resume_export'))
  console.log('PASS: nine templates, blank defaults, custom sections, hidden data, safe rich text, images, layout ordering, version lookup and SQL JSON roundtrip.')
} finally { await server.close() }
