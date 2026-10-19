import { createStore } from 'zustand/vanilla'
import { resumeApi, type ResumeDetail, type ResumeTemplate } from '@/api/resume'
import { ApiBusinessError } from '@/api/http'
import { useAuthStore } from '@/features/auth/session'
import type { ResumeData } from '@/types/resume'
import type { ResumeEditorStore } from '@/store/resumeEditorStore'
import { fromResumeDetail, toResumeDocument } from './documentAdapter'

export function resumeFingerprint(resume: ResumeData) {
  // JSON 对象的键顺序不表示编辑变化；MySQL 读取可能重排键。
  return JSON.stringify({ title: resume.title.trim() || '未命名简历', templateKey: resume.templateId, ...toResumeDocument(resume) },
    (_key, value) => value && typeof value === 'object' && !Array.isArray(value)
      ? Object.fromEntries(Object.entries(value).sort(([left], [right]) => left.localeCompare(right))) : value)
}

export function remoteDraft(detail: ResumeDetail): ResumeData {
  const resume = fromResumeDetail(detail)
  resume.remote = { version: detail.version, templateId: detail.templateId, templateVersion: detail.templateVersion!, savedFingerprint: resumeFingerprint(resume) }
  return resume
}

export interface SyncStatus {
  phase: 'saved' | 'dirty' | 'saving' | 'error' | 'conflict'
  message: string
}

/** 编辑立即生效；发送串行、合并后续修改，网络重试保留同一载荷和 mutationId。 */
export function createResumeSync(draft: ResumeEditorStore, id: string, owner: string,
  templates: () => ResumeTemplate[], onSaved: (saved: ResumeDetail) => void) {
  const status = createStore<SyncStatus>(() => ({ phase: 'saved', message: '' }))
  let timer: ReturnType<typeof setTimeout> | undefined
  let running: Promise<void> | undefined
  let closed = false
  const current = () => draft.store.getState().resumes[id]
  const ownsSession = () => useAuthStore.getState().userId === owner
  const fingerprint = () => resumeFingerprint(current())
  function setRemote(remote: NonNullable<ResumeData['remote']>, updatedAt?: string) {
    draft.store.setState(state => {
      const existing = state.resumes[id]
      if (!existing || !ownsSession()) return state
      const updated = { ...existing, remote, ...(updatedAt ? { updatedAt } : {}) }
      return { resumes: { ...state.resumes, [id]: updated }, activeResume: state.activeResumeId === id ? updated : state.activeResume }
    })
  }
  function observe() {
    if (closed || !ownsSession() || !current()?.remote || status.getState().phase === 'conflict') return
    try {
      const dirty = Boolean(current().remote!.pending) || fingerprint() !== current().remote!.savedFingerprint
      if (!dirty) { if (!running) status.setState({ phase: 'saved', message: '' }); return }
      if (!running && status.getState().phase !== 'error') {
        status.setState({ phase: 'dirty', message: '' })
        clearTimeout(timer); timer = setTimeout(() => { void flush() }, 800)
      }
    } catch (error) { status.setState({ phase: 'error', message: error instanceof Error ? error.message : '简历格式不正确' }) }
  }
  async function send() {
    while (!closed && ownsSession() && current()?.remote && status.getState().phase !== 'conflict') {
      const resume = current(), remote = resume.remote!
      const latestFingerprint = resumeFingerprint(resume)
      if (!remote.pending && latestFingerprint === remote.savedFingerprint) { status.setState({ phase: 'saved', message: '' }); break }
      const selected = resume.templateId
      const templateId = selected === JSON.parse(remote.savedFingerprint).templateKey ? remote.templateId
        : templates().find(template => template.templateKey === selected && template.templateVersion === 1 && template.schemaVersion === 1)?.id
      if (!templateId) throw new Error('该模板暂不可用，请重新选择')
      const pending = remote.pending ?? { fingerprint: latestFingerprint, request: {
        ...toResumeDocument(resume), title: resume.title.trim() || '未命名简历', templateId,
        expectedVersion: remote.version, mutationId: crypto.randomUUID(), checkpoint: false,
      } }
      setRemote({ ...remote, pending })
      // 待重试请求只留在当前页面内存，刷新后重新读取服务端。
      if (closed || !ownsSession()) return
      status.setState({ phase: 'saving', message: '' })
      const saved = await resumeApi.save(id, pending.request)
      if (closed || !ownsSession() || !current()) return
      const unchanged = resumeFingerprint(current()) === pending.fingerprint
      setRemote({ version: saved.version, templateId: saved.templateId, templateVersion: saved.templateVersion!,
        savedFingerprint: pending.fingerprint }, unchanged ? saved.updatedAt : undefined)
      onSaved(saved)
    }
  }
  function flush() {
    clearTimeout(timer)
    if (running) return running
    if (closed || !ownsSession() || !current()?.remote || status.getState().phase === 'conflict') return Promise.resolve()
    running = send().catch(error => {
      if (closed || !ownsSession()) return
      const conflict = error instanceof ApiBusinessError && error.code === 'RESUME_VERSION_CONFLICT'
      // 业务拒绝表示未提交；网络失败则保留载荷，不能换新 ID 重发。
      if (error instanceof ApiBusinessError && !conflict && current()?.remote) {
        const { pending: _pending, ...remote } = current().remote!
        setRemote(remote)
      }
      status.setState({ phase: conflict ? 'conflict' : 'error', message: error instanceof Error ? error.message : '保存失败，请重试' })
    }).finally(() => { running = undefined })
    return running
  }
  const unsubscribe = draft.store.subscribe(observe)
  observe()
  return { status, flush, stop() { closed = true; clearTimeout(timer); unsubscribe() } }
}
