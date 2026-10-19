import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/features/auth/session'
import { useResumeEditorContext } from '@/store/useResumeStore'
import { resumeApi, type ResumeDetail } from '@/api/resume'
import { remoteDraft, resumeFingerprint } from './resumeSync'
import { toResumeDocument } from './documentAdapter'
import { createInitialResume } from '@/store/resumeEditorStore'

export const resumeKeys = {
  owner: (owner: string) => ['resumes', owner] as const,
  lists: (owner: string) => ['resumes', owner, 'list'] as const,
  list: (owner: string, page: number) => ['resumes', owner, 'list', page] as const,
  detail: (owner: string, id: string) => ['resumes', owner, 'detail', id] as const,
  templates: (owner: string) => ['resumes', owner, 'templates'] as const,
}
export function useResumeTemplates() {
  const owner = useAuthStore(state => state.userId)
  return useQuery({ queryKey: resumeKeys.templates(owner), enabled: Boolean(owner), retry: false,
    queryFn: ({ signal }) => resumeApi.templates(signal), staleTime: 5 * 60_000 })
}
export function useCloudResumes(page: number) {
  const owner = useAuthStore(state => state.userId)
  return useQuery({ queryKey: resumeKeys.list(owner, page), enabled: Boolean(owner), retry: false,
    queryFn: ({ signal }) => resumeApi.list(page, 20, signal) })
}
export function useCreateCloudResume() {
  const owner = useAuthStore(state => state.userId), draft = useResumeEditorContext(), client = useQueryClient()
  const templates = useResumeTemplates()
  return useMutation({ retry: false, mutationFn: async (input: { templateKey?: string; blank?: boolean; color?: string; sourceId?: string }) => {
    if (!owner) throw new Error('请登录后创建简历')
    if (!templates.data) throw new Error('模板尚未加载，请稍后重试')
    const source = input.sourceId ? remoteDraft(await resumeApi.detail(input.sourceId))
      : createInitialResume(input.templateKey || 'classic', input.blank, input.color)
    if (useAuthStore.getState().userId !== owner) throw new Error('账号已变更，请重新操作')
    const template = templates.data.find(item => item.templateKey === source.templateId && item.templateVersion === 1 && item.schemaVersion === 1)
    if (!template) throw new Error('该模板暂不可用')
    const saved = await resumeApi.create({ ...toResumeDocument(source), title: input.sourceId ? `${source.title}（副本）`.slice(0, 120) : source.title,
      templateId: template.id, mutationId: crypto.randomUUID(), checkpoint: false })
    if (useAuthStore.getState().userId !== owner) throw new Error('账号已变更，请重新操作')
    const restored = remoteDraft(saved)
    if (saved.content.basic.photoFileId) restored.basic.photo = source.basic.photo
    draft.store.getState().addResume(restored)
    client.setQueryData(resumeKeys.detail(owner, saved.id), saved)
    return saved
  }, onSuccess: () => { void client.invalidateQueries({ queryKey: resumeKeys.lists(owner) }) },
    onError: () => { void client.invalidateQueries({ queryKey: resumeKeys.lists(owner) }) } })
}
export function useDeleteCloudResume() {
  const owner = useAuthStore(state => state.userId), draft = useResumeEditorContext(), client = useQueryClient()
  return useMutation({ retry: false, mutationFn: ({ id, version }: { id: string; version: string }) => {
    if (useAuthStore.getState().userId !== owner) throw new Error('账号已变更，请重新操作')
    return resumeApi.delete(id, version)
  },
    onSuccess: (_data, input) => {
      if (useAuthStore.getState().userId !== owner) return
      const local = draft.store.getState().resumes[input.id]
      if (local) draft.store.getState().deleteResume(local)
      client.removeQueries({ queryKey: resumeKeys.detail(owner, input.id) })
      void client.invalidateQueries({ queryKey: resumeKeys.lists(owner) })
    }, onError: () => { void client.invalidateQueries({ queryKey: resumeKeys.lists(owner) }) } })
}
export function installCloudDetail(draft: ReturnType<typeof useResumeEditorContext>, detail: ResumeDetail, force = false) {
  const local = draft.store.getState().resumes[detail.id]
  const restored = remoteDraft(detail)
  if (!force && local?.remote && (local.remote.pending || local.remote.savedFingerprint !== resumeFingerprint(local) || local.remote.version === detail.version)) {
    // 未保存改动优先保留；版本不一致会在 CAS 保存时明确报冲突。
    if (local.basic.photoFileId === detail.content.basic.photoFileId && detail.photoUrl) {
      restored.basic = { ...local.basic, photo: detail.photoUrl }
      draft.store.setState(state => ({ resumes: { ...state.resumes, [detail.id]: { ...local, basic: restored.basic } } }))
    }
    draft.store.getState().setActiveResume(detail.id)
  } else {
    if (!restored.basic.photo && local?.basic.photoFileId && local.basic.photoFileId === detail.content.basic.photoFileId) restored.basic.photo = local.basic.photo
    draft.store.getState().addResume(restored)
  }
}
