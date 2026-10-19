import { ApiBusinessError } from '@/api/http'
import { resumeApi, type ResumeDetail } from '@/api/resume'
import { privateHttp } from '@/api/private'

/** Image bytes are prepared locally; the renderer never follows a user-provided URL. */
export async function preparePdfImages(detail: ResumeDetail): Promise<Record<string, string>> {
  const images: Record<string, string> = {}
  const visible = new Set(detail.layout.menuSections.filter(section => section.enabled).map(section => section.id))
  const jobs: [string, string][] = []
  const photo = detail.photoUrl || detail.content.basic.photo
  if (visible.has('basic') && detail.layout.basic.photoConfig.visible && photo) jobs.push(['photo', photo])
  if (visible.has('certificates')) detail.content.certificates.forEach(item => jobs.push([`certificate:${item.id}`, item.url]))
  let total = 0
  await Promise.all(jobs.map(async ([key, url]) => {
    const response = await fetch(url, { signal: AbortSignal.timeout(15000), credentials: 'omit' })
    if (!response.ok) throw new Error('简历图片读取失败，请重新上传后重试')
    const blob = await response.blob()
    if (!/^image\/(png|jpeg|webp)$/.test(blob.type) || blob.size > 5 * 1024 * 1024) throw new Error('导出图片必须是 5MB 以内的 PNG、JPEG 或 WebP')
    total += blob.size
    if (total > 15 * 1024 * 1024) throw new Error('简历图片总大小超过 15MB，请缩小图片后重试')
    images[key] = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result))
      reader.onerror = () => reject(new Error('简历图片读取失败'))
      reader.readAsDataURL(blob)
    })
  }))
  return images
}

export async function downloadResumePdf(id: string, expectedVersion: string) {
  const detail = await resumeApi.detail(id)
  if (detail.version !== expectedVersion) throw new ApiBusinessError('RESUME_VERSION_CONFLICT', '简历已在其他页面更新，请重新读取后导出')
  const images = await preparePdfImages(detail)
  const response = await privateHttp.post<Blob>(`/api/users/me/resumes/${encodeURIComponent(id)}/pdf`, { expectedVersion, images }, { responseType: 'blob', timeout: 60000 })
  if (!String(response.headers['content-type'] || '').includes('application/pdf')) {
    const error = JSON.parse(await response.data.text())
    throw new ApiBusinessError(error.code || 'PDF_EXPORT_FAILED', error.message || 'PDF 导出失败，请重试')
  }
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = `${(detail.title || '简历').replace(/[\\/:*?"<>|]/g, '_')}.pdf`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
