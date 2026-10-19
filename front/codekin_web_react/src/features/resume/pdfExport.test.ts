import { afterEach, describe, expect, it, vi } from 'vitest'
import { Blob as NodeBlob } from 'node:buffer'
import { privateHttp } from '@/api/private'
import { resumeApi, type ResumeDetail } from '@/api/resume'
import example from '../../../../../contracts/resume/example.json'
import { downloadResumePdf, preparePdfImages } from './pdfExport'

function detail(): ResumeDetail {
  return structuredClone({ ...example, id: '42', title: '后端简历', templateId: '1', templateKey: 'classic',
    templateVersion: 1, version: '3', createdAt: '', updatedAt: '' }) as ResumeDetail
}
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('PDF download', () => {
  it('downloads a PDF from the authenticated endpoint with the confirmed version', async () => {
    const saved = detail()
    saved.layout.basic.photoConfig.visible = false
    vi.spyOn(resumeApi, 'detail').mockResolvedValue(saved)
    const post = vi.spyOn(privateHttp, 'post').mockResolvedValue({ data: new NodeBlob(['%PDF-1.7'], { type: 'application/pdf' }), headers: { 'content-type': 'application/pdf' } })
    URL.createObjectURL = vi.fn(() => 'blob:pdf')
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    await downloadResumePdf('42', '3')
    expect(post).toHaveBeenCalledWith('/api/users/me/resumes/42/pdf', { expectedVersion: '3', images: {} }, expect.objectContaining({ responseType: 'blob' }))
    expect(click).toHaveBeenCalledOnce()
    expect(URL.createObjectURL).toHaveBeenCalledOnce()
  })
  it('does not export another saved version', async () => {
    vi.spyOn(resumeApi, 'detail').mockResolvedValue(detail())
    const post = vi.spyOn(privateHttp, 'post')
    await expect(downloadResumePdf('42', '2')).rejects.toMatchObject({ code: 'RESUME_VERSION_CONFLICT' })
    expect(post).not.toHaveBeenCalled()
  })
  it('handles business JSON instead of downloading it as a PDF', async () => {
    const saved = detail(); saved.layout.basic.photoConfig.visible = false
    vi.spyOn(resumeApi, 'detail').mockResolvedValue(saved)
    vi.spyOn(privateHttp, 'post').mockResolvedValue({ data: new NodeBlob([JSON.stringify({ code: 'RESUME_PDF_BUSY', message: '当前导出较多' })]), headers: { 'content-type': 'application/json' } })
    await expect(downloadResumePdf('42', '3')).rejects.toMatchObject({ code: 'RESUME_PDF_BUSY', message: '当前导出较多' })
  })
  it('skips hidden photo and certificate modules and fails on unreadable visible images', async () => {
    const saved = detail(); saved.layout.basic.photoConfig.visible = false
    const fetch = vi.fn().mockResolvedValue({ ok: false }); vi.stubGlobal('fetch', fetch)
    expect(await preparePdfImages(saved)).toEqual({})
    expect(fetch).not.toHaveBeenCalled()
    saved.layout.basic.photoConfig.visible = true
    saved.photoUrl = 'https://signed.example/photo'
    await expect(preparePdfImages(saved)).rejects.toThrow('简历图片读取失败')
  })
})
