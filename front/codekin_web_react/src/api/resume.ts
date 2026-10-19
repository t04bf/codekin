import { privateHttp } from './private'
import { readData, type ApiEnvelope } from './http'
import type { ResumeDocument } from './resume-document.generated'

export interface ResumeSummary {
  id: string
  title: string
  templateId: string
  templateKey: string | null
  templateVersion: number | null
  schemaVersion: number
  version: string
  createdAt: string
  updatedAt: string
}
export interface ResumeDetail extends ResumeSummary {
  content: ResumeDocument['content']
  layout: ResumeDocument['layout']
  photoUrl?: string | null
  photoUrlExpiresAt?: string | null
}
export interface ResumePage {
  items: ResumeDetail[]
  page: number
  pageSize: number
  hasMore: boolean
}
export interface ResumeTemplate {
  id: string
  templateKey: string
  templateVersion: number
  name: string
  description: string | null
  schemaVersion: 1
  defaultContent: ResumeDocument['content']
  defaultLayout: ResumeDocument['layout']
}
export interface ResumeCreate extends ResumeDocument {
  title: string
  templateId: string
  mutationId: string
  checkpoint?: boolean
}
export interface ResumeUpdate extends ResumeCreate {
  expectedVersion: string
}
const base = '/api/users/me/resumes'

/** 当前用户简历存取接口，身份由登录态决定。 */
export const resumeApi = {
  templates(signal?: AbortSignal) {
    return privateHttp.get<ApiEnvelope<ResumeTemplate[]>>(`${base}/templates`, { signal }).then(readData)
  },
  list(page = 1, pageSize = 20, signal?: AbortSignal) {
    return privateHttp.get<ApiEnvelope<ResumePage>>(base, { params: { page, pageSize }, signal }).then(readData)
  },
  detail(id: string, signal?: AbortSignal) {
    return privateHttp.get<ApiEnvelope<ResumeDetail>>(`${base}/${encodeURIComponent(id)}`, { signal }).then(readData)
  },
  create(request: ResumeCreate) {
    return privateHttp.post<ApiEnvelope<ResumeDetail>>(base, request).then(readData)
  },
  save(id: string, request: ResumeUpdate) {
    return privateHttp.put<ApiEnvelope<ResumeDetail>>(`${base}/${encodeURIComponent(id)}`, request).then(readData)
  },
  delete(id: string, expectedVersion: string) {
    return privateHttp.delete<ApiEnvelope<void>>(`${base}/${encodeURIComponent(id)}`, { params: { expectedVersion } }).then(readData)
  },
}
