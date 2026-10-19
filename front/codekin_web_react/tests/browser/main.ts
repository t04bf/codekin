// Dedicated MSW browser fixture. Never imported by the production entry point.
import { delay, http, HttpResponse } from 'msw'
import { setupWorker } from 'msw/browser'
import { profileFixture } from '../../src/test/fixtures'
import catalog from '../../src/features/resume/data/templates.json'
import type { ResumeDetail, ResumeCreate, ResumeUpdate } from '../../src/api/resume'
import { useAuthStore } from '../../src/features/auth/session'
let profile = { ...profileFixture }
const uploads = new Map<string, { typeCode: string; size: number; contentType: string; uploaded: boolean; previewUrl?: string }>()
const ok = (data: unknown = null) => HttpResponse.json({ code: '0', message: '', data })
// Test-only virtual server database; production resume state is never written to browser storage.
const dbKey = 'fixture.resume.database'
const records: Record<string, ResumeDetail> = JSON.parse(localStorage.getItem(dbKey) || '{}')
const requests = new Map<string, { mutationId: string; body: string }>()
const persistResumes = () => localStorage.setItem(dbKey, JSON.stringify(records))
const failure = (code: string, message: string) => HttpResponse.json({ code, message, data: null })
// Optional latency for checking loading transitions; never used by production.
const latency = Math.min(8000, Math.max(0, Number(new URLSearchParams(location.search).get('latency')) || 0))
const worker = setupWorker(
  http.get('/api/users/me/resumes/templates', () => ok(catalog)),
  http.get('/api/users/me/resumes', async ({ request }) => {
    if (latency) await delay(latency)
    const query = new URL(request.url).searchParams
    const page = Number(query.get('page') || 1), pageSize = Number(query.get('pageSize') || 20)
    const items = Object.values(records).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    return ok({ page, pageSize, hasMore: items.length > page * pageSize, items: items.slice((page - 1) * pageSize, page * pageSize)
      .map(record => ({ ...record, photoUrl: record.content.basic.photoFileId ? '/upload-sample.png' : null,
        photoUrlExpiresAt: record.content.basic.photoFileId ? new Date(Date.now() + 7200_000).toISOString() : null })) })
  }),
  http.get('/api/users/me/resumes/:id', async ({ params }) => {
    if (latency) await delay(latency)
    const record = records[String(params.id)]
    if (!record) return failure('RESUME_NOT_FOUND', '简历不存在或无权访问')
    return ok({ ...record, photoUrl: record.content.basic.photoFileId ? '/upload-sample.png' : null,
      photoUrlExpiresAt: record.content.basic.photoFileId ? new Date(Date.now() + 7200_000).toISOString() : null })
  }),
  http.post('/api/users/me/resumes', async ({ request }) => {
    const body = await request.json() as ResumeCreate
    const template = catalog.find(item => item.id === body.templateId)
    if (!template) return failure('RESUME_TEMPLATE_UNAVAILABLE', '模板不可用')
    const id = String(BigInt(Date.now()) * 1000n + BigInt(Math.floor(Math.random() * 1000)))
    const now = new Date().toISOString()
    const saved: ResumeDetail = { id, title: body.title, templateId: template.id, templateKey: template.templateKey,
      templateVersion: 1, schemaVersion: 1, version: '1', content: body.content, layout: body.layout, createdAt: now, updatedAt: now }
    records[id] = saved; persistResumes(); return ok(saved)
  }),
  http.put('/api/users/me/resumes/:id', async ({ params, request }) => {
    const id = String(params.id), previous = records[id], body = await request.json() as ResumeUpdate
    if (!previous) return failure('RESUME_NOT_FOUND', '简历不存在或无权访问')
    const last = requests.get(id)
    if (last?.mutationId === body.mutationId && last.body === JSON.stringify(body)) return ok(previous)
    if (body.expectedVersion !== previous.version) return failure('RESUME_VERSION_CONFLICT', '简历已在其他页面更新，请重新读取后保存')
    const template = catalog.find(item => item.id === body.templateId)!
    const saved: ResumeDetail = { ...previous, content: body.content, layout: body.layout, title: body.title,
      templateId: template.id, templateKey: template.templateKey, version: String(BigInt(previous.version) + 1n), updatedAt: new Date().toISOString() }
    requests.set(id, { mutationId: body.mutationId, body: JSON.stringify(body) }); records[id] = saved; persistResumes(); return ok(saved)
  }),
  http.delete('/api/users/me/resumes/:id', ({ params, request }) => {
    const id = String(params.id), record = records[id]
    if (record && record.version !== new URL(request.url).searchParams.get('expectedVersion')) return failure('RESUME_VERSION_CONFLICT', '简历已更新，请刷新后再删除')
    delete records[id]; persistResumes(); return ok()
  }),
  http.get('/api/users/me', async () => { if (latency) await delay(latency); return ok(profile) }),
  http.put('/api/users/me/profile', async ({ request }) => { profile = { ...profile, ...await request.json() as object }; return ok() }),
  http.get('/api/users/avatar/defaults', () => ok([{ name: '示例头像', objectKey: 'defaults/sample.png', url: '/avatar.png' }])),
  http.get('/api/files/upload-types', () => ok([
    { code: 'AVATAR', maxBytes: 1024 * 1024, extensions: ['png', 'jpg'], contentTypes: ['image/png', 'image/jpeg'], maxWidth: 2048, maxHeight: 2048, maxPixels: 4_194_304 },
    { code: 'RESUME_PHOTO', maxBytes: 5 * 1024 * 1024, extensions: ['png', 'jpg', 'jpeg', 'webp'], contentTypes: ['image/png', 'image/jpeg', 'image/webp'], maxWidth: 4096, maxHeight: 4096, maxPixels: 12_000_000 },
  ])),
  http.put('/api/users/me/avatar/default', async ({ request }) => { const body = await request.json() as { objectKey: string }; profile = { ...profile, avatarKey: body.objectKey, avatarUrl: '/avatar.png' }; return ok() }),
  http.post('/api/files/upload/signature', async ({ request }) => {
    const body = await request.json() as { typeCode: string; size: number; contentType: string }
    if (!['AVATAR', 'RESUME_PHOTO'].includes(body.typeCode)) return HttpResponse.json({ code: 'FILE_INVALID_TYPE', message: '不支持的用途' })
    const fileId = String(BigInt(Date.now()) * 1000n + BigInt(Math.floor(Math.random() * 1000)))
    uploads.set(fileId, { ...body, uploaded: false })
    return ok({ fileId, uploadUrl: 'http://127.0.0.1:5175/storage', method: 'POST', fields: { key: fileId, 'Content-Type': body.contentType, policy: 'fixture-policy' }, expiresAt: '', confirmationExpiresAt: '' })
  }),
  http.post('/storage', async ({ request }) => {
    const form = await request.formData()
    const fileId = form.get('key') as string
    const record = uploads.get(fileId)
    const image = form.get('file')
    if (request.headers.has('Authorization') || !record || !(image instanceof File) || image.size !== record.size || image.type !== record.contentType || form.get('policy') !== 'fixture-policy' || form.get('Content-Type') !== record.contentType || [...form.keys()].at(-1) !== 'file') {
      return new HttpResponse(null, { status: 400 })
    }
    record.uploaded = true
    record.previewUrl = URL.createObjectURL(image)
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('/api/files/:fileId/complete', ({ params }) => {
    const record = uploads.get(String(params.fileId))
    if (!record?.uploaded) return HttpResponse.json({ code: 'FILE_NOT_READY', message: '文件尚未完成上传' })
    return ok({ fileId: params.fileId, size: record.size, contentType: 'image/png', typeCode: record.typeCode })
  }),
  http.put('/api/users/me/avatar', async ({ request }) => {
    const body = await request.json() as { fileId: string }
    const record = uploads.get(body.fileId)
    if (!record?.uploaded || record.typeCode !== 'AVATAR' || !record.previewUrl) return HttpResponse.json({ code: 'FILE_NOT_READY', message: '头像尚未完成确认' })
    profile = { ...profile, avatarKey: body.fileId, avatarUrl: record.previewUrl }
    return ok()
  }),
  http.post('/api/users/me/email/code/send', () => ok()),
  http.post('/api/users/me/phone/sms/send', () => ok()),
  http.post('/api/users/me/email/bind', async ({ request }) => { const body = await request.json() as { email: string }; profile = { ...profile, email: body.email }; return ok() }),
  http.post('/api/users/me/phone/bind', async ({ request }) => { const body = await request.json() as { phone: string }; profile = { ...profile, phone: `+86${body.phone}` }; return ok() }),
  http.put('/api/users/me/password', () => ok()),
  http.post('/api/auth/logout', () => ok()),
)
await worker.start({ quiet: true, onUnhandledRequest(request, print) { if (new URL(request.url).pathname.startsWith('/api/')) print.error() } })
useAuthStore.getState().accept({ userId: profile.userId, accessToken: 'fixture-access', refreshToken: 'fixture-refresh', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600_000).toISOString(), refreshExpiresAt: '', sessionExpiresAt: '' })
await import('../../src/main')
